package com.proyecto.docente.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.proyecto.docente.service.TareaService;

/**
 * Vista de una tarea para Thymeleaf. Evita exponer el JSON de Flowable y
 * formatea la fecha en el servidor. Los datos del curso (empresa y tematica)
 * se leen de las variables de proceso que devuelve la API REST.
 */
public class TareaVista {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZoneId.systemDefault());

	private final String id;
	private final String nombre;
	private final String asignadoA;
	private final String fechaCreacion;
	private final String processInstanceId;
	private final String empresa;
	private final String tematica;
	private final String taskDefinitionKey;

	public TareaVista(String id, String nombre, String asignadoA, String fechaCreacion,
			String processInstanceId, String empresa, String tematica, String taskDefinitionKey) {
		this.id = id;
		this.nombre = nombre;
		this.asignadoA = asignadoA;
		this.fechaCreacion = fechaCreacion;
		this.processInstanceId = processInstanceId;
		this.empresa = empresa;
		this.tematica = tematica;
		this.taskDefinitionKey = taskDefinitionKey;
	}

	/** Convierte una tarea devuelta por GET /runtime/tasks. */
	public static TareaVista desde(Map<String, Object> tarea) {
		Map<String, Object> variables = variablesDe(tarea);
		return new TareaVista(
				texto(tarea.get("id")),
				texto(tarea.get("name")),
				texto(tarea.get("assignee")),
				formatear(tarea.get("createTime")),
				texto(tarea.get("processInstanceId")),
				texto(variables.get("empresa")),
				texto(variables.get("tematica")),
				texto(tarea.get("taskDefinitionKey")));
	}

	private static Map<String, Object> variablesDe(Map<String, Object> tarea) {
		Object valores = tarea.get("variables");
		if (!(valores instanceof List<?> lista)) {
			return Map.of();
		}
		Map<String, Object> resultado = new HashMap<>();
		for (Object item : lista) {
			if (item instanceof Map<?, ?> variable) {
				Object nombre = variable.get("name");
				if (nombre != null) {
					resultado.put(nombre.toString(), variable.get("value"));
				}
			}
		}
		return resultado;
	}

	private static String formatear(Object fecha) {
		if (fecha == null) {
			return "";
		}
		try {
			return FORMATO.format(Instant.parse(fecha.toString()));
		}
		catch (Exception ignorado) {
			return fecha.toString();
		}
	}

	private static String texto(Object valor) {
		return valor == null ? "" : valor.toString();
	}

	public String getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getAsignadoA() {
		return asignadoA;
	}

	public String getFechaCreacion() {
		return fechaCreacion;
	}

	public String getProcessInstanceId() {
		return processInstanceId;
	}

	public String getEmpresa() {
		return empresa;
	}

	public String getTematica() {
		return tematica;
	}

	public String getTaskDefinitionKey() {
		return taskDefinitionKey;
	}

	public boolean isDefinirDocente() {
		return TareaService.DEFINIR_DOCENTE.equals(taskDefinitionKey);
	}

	public boolean isDesarrollo() {
		return TareaService.DESARROLLAR_CURSO.equals(taskDefinitionKey);
	}

	public boolean isInforme() {
		return TareaService.ELABORAR_INFORME.equals(taskDefinitionKey);
	}

}
