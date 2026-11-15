package com.proyecto.admin.model;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.flowable.task.api.Task;

/**
 * Vista de una tarea para Thymeleaf. Evita exponer directamente el objeto Task
 * de Flowable y formatea la fecha en el servidor.
 */
public class TareaVista {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZoneId.systemDefault());

	private final String id;

	private final String nombre;

	private final String asignadoA;

	private final String fechaCreacion;

	public TareaVista(String id, String nombre, String asignadoA, String fechaCreacion) {
		this.id = id;
		this.nombre = nombre;
		this.asignadoA = asignadoA;
		this.fechaCreacion = fechaCreacion;
	}

	public static TareaVista desde(Task tarea) {
		return new TareaVista(tarea.getId(), tarea.getName(), tarea.getAssignee(), formatear(tarea.getCreateTime()));
	}

	private static String formatear(Date fecha) {
		return fecha == null ? "" : FORMATO.format(fecha.toInstant());
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

}
