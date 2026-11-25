package com.proyecto.docente.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.proyecto.docente.client.FlowableRestClient;
import com.proyecto.docente.client.FlowableRestException;
import com.proyecto.docente.model.TareaVista;

/**
 * Operaciones sobre las tres tareas del carril docente del proceso.
 * Usa la API REST de Flowable, igual que las demas apps del proyecto.
 */
@Service
public class TareaService {

	/** taskDefinitionKey de 'Definir docente que desarrola el curso' (BPMN). */
	public static final String DEFINIR_DOCENTE = "sid-D9626FE6-AE69-44AF-9389-E320BC348EDB";

	/** taskDefinitionKey de 'Desarrollar curso' (BPMN). */
	public static final String DESARROLLAR_CURSO = "sid-895BA48F-B50A-4C10-9369-4CFEA6D67AC2";

	/** taskDefinitionKey de 'Elaborar informe de resultados' (BPMN). */
	public static final String ELABORAR_INFORME = "sid-EDDAC05F-B007-4E01-8223-713A621E1188";

	private static final List<String> CLAVES_DOCENTE = List.of(DEFINIR_DOCENTE, DESARROLLAR_CURSO, ELABORAR_INFORME);

	private static final Logger log = LoggerFactory.getLogger(TareaService.class);

	private final FlowableRestClient cliente;

	public TareaService(FlowableRestClient cliente) {
		this.cliente = cliente;
	}

	/** Tareas de las tres claves ya reclamadas por el usuario. */
	public List<TareaVista> tareasAsignadas(String usuario) {
		return mapear(cliente.tareas(builder -> builder.queryParam("assignee", usuario)));
	}

	/** Tareas sin asignar que el usuario puede reclamar por usuario o grupo. */
	public List<TareaVista> tareasReclamables(String usuario) {
		return mapear(cliente.tareas(builder -> {
			builder.queryParam("unassigned", "true");
			builder.queryParam("candidateUser", usuario);
		}));
	}

	/** Devuelve una tarea del carril docente ya preparada para la vista. */
	public TareaVista obtenerTarea(String tareaId) {
		return TareaVista.desde(buscarTarea(tareaId));
	}

	/**
	 * Reclama la tarea para el usuario. La validacion previa es necesaria porque
	 * el REST de Flowable no controla los candidatos al hacer claim.
	 */
	public void reclamar(String tareaId, String usuario) {
		Map<String, Object> tarea = buscarTarea(tareaId);
		if (tarea.get("assignee") != null) {
			throw new TareaException("La tarea ya fue reclamada por '" + tarea.get("assignee") + "'.");
		}
		boolean habilitado = cliente.tareas(builder -> {
			builder.queryParam("unassigned", "true");
			builder.queryParam("candidateUser", usuario);
		}).stream().anyMatch(tareaCandidata -> tareaId.equals(tareaCandidata.get("id")));
		if (!habilitado) {
			throw new TareaException("No tenes permiso para reclamar esta tarea.");
		}
		try {
			cliente.reclamar(tareaId, usuario);
		}
		catch (FlowableRestException ex) {
			throw new TareaException("Otro docente reclamo la tarea primero.");
		}
	}

	/** Completa una tarea sin variables (ej: 'Desarrollar curso'). */
	public void completar(String tareaId, String usuario) {
		completar(tareaId, usuario, Map.of());
	}

	/**
	 * Completa la tarea solo si esta reclamada por el usuario. Las variables se
	 * usan para 'Definir docente' (docenteSeleccionado) y 'Elaborar informe'
	 * (informe).
	 */
	public void completar(String tareaId, String usuario, Map<String, Object> variables) {
		Map<String, Object> tarea = buscarTarea(tareaId);
		if (!usuario.equals(tarea.get("assignee"))) {
			throw new TareaException("Solo podes completar una tarea reclamada por vos.");
		}
		try {
			cliente.completar(tareaId, variables == null ? Map.of() : variables);
		}
		catch (FlowableRestException ex) {
			log.error("Fallo al completar la tarea {}", tareaId, ex);
			throw new TareaException("No se pudo completar la tarea. Revisa el log de la aplicacion.");
		}
	}

	private Map<String, Object> buscarTarea(String tareaId) {
		Map<String, Object> tarea = cliente.tarea(tareaId);
		if (tarea == null) {
			throw new TareaException("La tarea no existe o ya fue completada.");
		}
		if (!CLAVES_DOCENTE.contains(tarea.get("taskDefinitionKey"))) {
			throw new TareaException("La tarea no corresponde al equipo docente.");
		}
		return tarea;
	}

	private List<TareaVista> mapear(List<Map<String, Object>> tareas) {
		return tareas.stream()
				.filter(tarea -> CLAVES_DOCENTE.contains(tarea.get("taskDefinitionKey")))
				.map(TareaVista::desde)
				.toList();
	}

}
