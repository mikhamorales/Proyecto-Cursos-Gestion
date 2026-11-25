package com.proyecto.analisis.service;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.proyecto.analisis.client.FlowableHistoryClient;
import com.proyecto.analisis.model.DistribucionVista;
import com.proyecto.analisis.model.HistoricActivityInstance;
import com.proyecto.analisis.model.HistoricProcessInstance;
import com.proyecto.analisis.model.HistoricTaskInstance;
import com.proyecto.analisis.model.RamaVista;
import com.proyecto.analisis.model.ResultadoVista;
import com.proyecto.analisis.model.ResumenAnalisis;

/**
 * Calcula los tres informes del punto 4.3 a partir del historial de Flowable.
 * No escribe nada: todas las consultas van por la API REST de historial.
 */
@Service
public class AnalisisService {

	private static final String SIN_CLASIFICAR = "Sin clasificar";
	private static final String SIN_ROL = "Sin rol definido";
	private static final String ADMINISTRATIVO = "Administrativo";
	private static final String DOCENTE = "Docente";

	/** Etiquetas en el orden que pide el enunciado. */
	private static final Map<String, String> RESULTADOS = new LinkedHashMap<>();
	static {
		RESULTADOS.put("FINALIZADO", "Curso finalizado");
		RESULTADOS.put("CANCELADO", "Cancelacion");
		RESULTADOS.put("RECHAZADO", "Rechazo de la propuesta");
		RESULTADOS.put("NO_VIABLE", "Solicitud no viable");
		RESULTADOS.put("CERRADO_POR_FALTA_DE_RESPUESTA", "Cierre por falta de respuesta");
	}

	private static final List<String> ORDEN_ROLES = List.of(ADMINISTRATIVO, DOCENTE, SIN_ROL);

	private final FlowableHistoryClient client;
	private final String actividadFin;
	private final List<String> inscripciones;
	private final List<String> materiales;
	private final List<String> recursos;
	private final String[] administrativos;
	private final String[] docentes;

	public AnalisisService(
			FlowableHistoryClient client,
			@Value("${analisis.preparacion.fin}") String actividadFin,
			@Value("${analisis.preparacion.inscripciones}") String[] inscripciones,
			@Value("${analisis.preparacion.materiales}") String[] materiales,
			@Value("${analisis.preparacion.recursos}") String[] recursos,
			@Value("${analisis.roles.administrativos}") String[] administrativos,
			@Value("${analisis.roles.docentes}") String[] docentes) {
		this.client = client;
		this.actividadFin = actividadFin;
		this.inscripciones = lista(inscripciones);
		this.materiales = lista(materiales);
		this.recursos = lista(recursos);
		this.administrativos = administrativos;
		this.docentes = docentes;
	}

	public ResumenAnalisis construirResumen() {
		List<HistoricProcessInstance> instancias = client.instanciasTerminadas().stream()
				.filter(instancia -> esVacio(instancia.getDeleteReason()))
				.toList();

		Map<String, String> resultadosPorInstancia = new HashMap<>();
		Map<String, List<HistoricActivityInstance>> actividadesPorInstancia = new HashMap<>();
		for (HistoricProcessInstance instancia : instancias) {
			resultadosPorInstancia.put(instancia.getId(), client.resultado(instancia.getId()));
			actividadesPorInstancia.put(instancia.getId(), client.actividades(instancia.getId()));
		}

		List<ResultadoVista> resultados = resumenResultados(resultadosPorInstancia);
		List<RamaVista> ramas = resumenRamas(resultadosPorInstancia, actividadesPorInstancia);
		List<DistribucionVista> distribucion = resumenTareas();

		return new ResumenAnalisis(
				resultados,
				ramas,
				distribucion,
				instancias.size(),
				contarPreparacionesCompletas(resultadosPorInstancia, actividadesPorInstancia),
				distribucion.stream().filter(fila -> !fila.isTotal()).mapToInt(DistribucionVista::getCantidad).sum());
	}

	/** 4.3.1 - cantidad y porcentaje sobre el total de instancias terminadas. */
	private List<ResultadoVista> resumenResultados(Map<String, String> resultadosPorInstancia) {
		Map<String, Integer> cantidades = new LinkedHashMap<>();
		RESULTADOS.values().forEach(etiqueta -> cantidades.put(etiqueta, 0));
		cantidades.put(SIN_CLASIFICAR, 0);

		for (String valor : resultadosPorInstancia.values()) {
			String etiqueta = RESULTADOS.getOrDefault(valor == null ? "" : valor.trim(), SIN_CLASIFICAR);
			cantidades.put(etiqueta, cantidades.get(etiqueta) + 1);
		}

		int total = resultadosPorInstancia.size();
		List<ResultadoVista> filas = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : cantidades.entrySet()) {
			double porcentaje = total == 0 ? 0 : Math.round(1000.0 * entry.getValue() / total) / 10.0;
			filas.add(new ResultadoVista(
					entry.getKey(), entry.getValue(), textoPorcentaje(porcentaje), porcentaje, false));
		}
		filas.add(new ResultadoVista(
				"Total", total, textoPorcentaje(total == 0 ? 0 : 100), total == 0 ? 0 : 100, true));
		return filas;
	}

	/** 4.3.2 - duracion promedio de cada rama de la preparacion paralela. */
	private List<RamaVista> resumenRamas(Map<String, String> resultadosPorInstancia,
			Map<String, List<HistoricActivityInstance>> actividadesPorInstancia) {
		Set<String> preparacionesCompletas = new HashSet<>();
		for (Map.Entry<String, List<HistoricActivityInstance>> entry : actividadesPorInstancia.entrySet()) {
			String instanciaId = entry.getKey();
			if ("CANCELADO".equals(resultadosPorInstancia.get(instanciaId))) {
				continue;
			}
			boolean llegoAlFin = entry.getValue().stream()
					.anyMatch(actividad -> coincide(actividad, Set.of(actividadFin))
							&& !esVacio(actividad.getEndTime()));
			if (llegoAlFin) {
				preparacionesCompletas.add(instanciaId);
			}
		}

		List<List<String>> ramas = List.of(inscripciones, materiales, recursos);
		List<String> nombres = List.of("Inscripciones", "Materiales", "Recursos");
		List<List<Long>> duraciones = new ArrayList<>();
		for (List<String> nombresActividades : ramas) {
			List<Long> mediciones = new ArrayList<>();
			for (String instanciaId : preparacionesCompletas) {
				Instant inicio = null;
				Instant fin = null;
				for (HistoricActivityInstance actividad : actividadesPorInstancia.getOrDefault(instanciaId, List.of())) {
					if (!coincide(actividad, nombresActividades)) {
						continue;
					}
					Instant inicioActividad = fecha(actividad.getStartTime());
					Instant finActividad = fecha(actividad.getEndTime());
					if (inicioActividad != null && (inicio == null || inicioActividad.isBefore(inicio))) {
						inicio = inicioActividad;
					}
					if (finActividad != null && (fin == null || finActividad.isAfter(fin))) {
						fin = finActividad;
					}
				}
				if (inicio != null && fin != null && !fin.isBefore(inicio)) {
					mediciones.add(Duration.between(inicio, fin).toMillis());
				}
			}
			duraciones.add(mediciones);
		}

		long maximo = 0;
		for (List<Long> mediciones : duraciones) {
			maximo = Math.max(maximo, promedio(mediciones));
		}

		List<RamaVista> filas = new ArrayList<>();
		for (int i = 0; i < ramas.size(); i++) {
			List<Long> mediciones = duraciones.get(i);
			long promedio = promedio(mediciones);
			filas.add(new RamaVista(
					nombres.get(i), mediciones.size(), textoDuracion(promedio), ancho(promedio, maximo)));
		}
		return filas;
	}

	/** 4.3.3 - tareas de usuario completadas por cada administrativo y docente. */
	private List<DistribucionVista> resumenTareas() {
		Map<String, String> roles = new LinkedHashMap<>();
		for (String usuario : administrativos) {
			if (!esVacio(usuario)) {
				roles.put(usuario.trim(), ADMINISTRATIVO);
			}
		}
		for (String usuario : docentes) {
			if (!esVacio(usuario)) {
				roles.putIfAbsent(usuario.trim(), DOCENTE);
			}
		}

		Map<String, Integer> conteos = new LinkedHashMap<>();
		for (String usuario : roles.keySet()) {
			conteos.put(clave(roles.get(usuario), usuario), 0);
		}
		for (HistoricTaskInstance tarea : client.tareasTerminadas()) {
			if (!esVacio(tarea.getDeleteReason()) || esVacio(tarea.getAssignee())
					|| esVacio(tarea.getEndTime())) {
				continue;
			}
			String usuario = tarea.getAssignee().trim();
			String rol = roles.getOrDefault(usuario, SIN_ROL);
			String clave = clave(rol, usuario);
			conteos.put(clave, conteos.getOrDefault(clave, 0) + 1);
		}

		long maximo = conteos.values().stream().mapToLong(Integer::longValue).max().orElse(0);
		List<DistribucionVista> filas = new ArrayList<>();
		for (String rol : ORDEN_ROLES) {
			List<Map.Entry<String, Integer>> usuarios = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : conteos.entrySet()) {
				if (entry.getKey().startsWith(rol + "|")) {
					usuarios.add(Map.entry(entry.getKey().substring(rol.length() + 1), entry.getValue()));
				}
			}
			usuarios.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> -e.getValue())
					.thenComparing(Map.Entry::getKey));

			int totalRol = 0;
			for (Map.Entry<String, Integer> usuario : usuarios) {
				filas.add(new DistribucionVista(rol, usuario.getKey(), usuario.getValue(),
						ancho(usuario.getValue(), maximo), false));
				totalRol += usuario.getValue();
			}
			if (!usuarios.isEmpty()) {
				filas.add(new DistribucionVista("Total " + rol.toLowerCase(Locale.ROOT), "", totalRol, 0, true));
			}
		}
		return filas;
	}

	private int contarPreparacionesCompletas(Map<String, String> resultadosPorInstancia,
			Map<String, List<HistoricActivityInstance>> actividadesPorInstancia) {
		int cantidad = 0;
		for (Map.Entry<String, List<HistoricActivityInstance>> entry : actividadesPorInstancia.entrySet()) {
			if ("CANCELADO".equals(resultadosPorInstancia.get(entry.getKey()))) {
				continue;
			}
			if (entry.getValue().stream().anyMatch(actividad -> coincide(actividad, Set.of(actividadFin))
					&& !esVacio(actividad.getEndTime()))) {
				cantidad++;
			}
		}
		return cantidad;
	}

	private boolean coincide(HistoricActivityInstance actividad, Collection<String> nombres) {
		return nombres.contains(nombre(actividad))
				|| (!esVacio(actividad.getActivityId()) && nombres.contains(actividad.getActivityId()));
	}

	private String nombre(HistoricActivityInstance actividad) {
		return esVacio(actividad.getActivityName())
				? (actividad.getActivityId() == null ? "" : actividad.getActivityId())
				: actividad.getActivityName().trim();
	}

	private List<String> lista(String[] valores) {
		List<String> resultado = new ArrayList<>();
		if (valores == null) {
			return resultado;
		}
		for (String valor : valores) {
			if (!esVacio(valor)) {
				resultado.add(valor.trim());
			}
		}
		return resultado;
	}

	private String clave(String rol, String usuario) {
		return rol + "|" + usuario;
	}

	private long promedio(List<Long> valores) {
		if (valores.isEmpty()) {
			return 0;
		}
		return Math.round(valores.stream().mapToLong(Long::longValue).average().orElse(0));
	}

	private double ancho(long valor, long maximo) {
		if (maximo <= 0 || valor <= 0) {
			return 0;
		}
		return Math.max(2.0, 100.0 * valor / maximo);
	}

	private String textoPorcentaje(double porcentaje) {
		return String.format(Locale.ROOT, "%.1f", porcentaje).replace('.', ',') + " %";
	}

	private String textoDuracion(long milisegundos) {
		long minutos = Math.max(0, Math.round(milisegundos / 60000.0));
		long dias = minutos / 1440;
		long resto = minutos % 1440;
		long horas = resto / 60;
		minutos = resto % 60;
		if (dias > 0) {
			return dias + " d " + horas + " h " + minutos + " min";
		}
		if (horas > 0) {
			return horas + " h " + minutos + " min";
		}
		return minutos + " min";
	}

	private Instant fecha(String valor) {
		if (esVacio(valor)) {
			return null;
		}
		String texto = valor.trim();
		try {
			return OffsetDateTime.parse(texto, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
		}
		catch (Exception ignorado) {
			// Se prueba con los otros formatos que usa Flowable.
		}
		try {
			return Instant.parse(texto);
		}
		catch (Exception ignorado) {
			// Se prueba con SimpleDateFormat.
		}
		for (String patron : List.of("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ",
				"yyyy-MM-dd HH:mm:ss")) {
			try {
				return new SimpleDateFormat(patron, Locale.ROOT).parse(texto).toInstant();
			}
			catch (Exception ignorado) {
				// Siguiente formato.
			}
		}
		return null;
	}

	private boolean esVacio(Object valor) {
		if (valor == null) {
			return true;
		}
		String texto = valor.toString().trim().toLowerCase(Locale.ROOT);
		return texto.isEmpty() || texto.equals("nan") || texto.equals("none");
	}
}
