package com.proyecto.docente.model;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import org.flowable.task.api.Task;

import com.proyecto.docente.service.TareaService;

/**
 * Vista de una tarea para Thymeleaf. Evita exponer el objeto Task de Flowable
 * y formatea la fecha en el servidor. Los datos del curso (empresa y tematica)
 * se leen de las variables de la instancia en TareaService.
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

	public static TareaVista desde(Task tarea, Map<String, Object> variables) {
		return new TareaVista(tarea.getId(), tarea.getName(), tarea.getAssignee(),
				formatear(tarea.getCreateTime()), tarea.getProcessInstanceId(),
				texto(variables.get("empresa")), texto(variables.get("tematica")),
				tarea.getTaskDefinitionKey());
	}

	private static String formatear(Date fecha) {
		return fecha == null ? "" : FORMATO.format(fecha.toInstant());
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
