package com.proyecto.admin.service;

import java.util.List;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.proyecto.admin.model.TareaVista;

/**
 * Operaciones sobre la tarea "Preparar materiales" del proceso de cursos.
 */
@Service
public class TareaService {

	/**
	 * taskDefinitionKey de "Preparar materiales" (dentro del subproceso "Preparar
	 * curso") en Institucion_de_capacitacion.bpmn20.xml.
	 */
	public static final String TAREA_PREPARAR_MATERIALES = "sid-E0F8C31B-8D21-4CBC-A2A7-C7213EA7FBFB";

	private static final Logger log = LoggerFactory.getLogger(TareaService.class);

	private final TaskService taskService;

	private final AutenticacionService autenticacionService;

	public TareaService(TaskService taskService, AutenticacionService autenticacionService) {
		this.taskService = taskService;
		this.autenticacionService = autenticacionService;
	}

	/** Tareas de "Preparar materiales" ya reclamadas por el usuario. */
	public List<TareaVista> tareasAsignadas(String usuario) {
		return taskService.createTaskQuery()
				.taskAssignee(usuario)
				.taskDefinitionKey(TAREA_PREPARAR_MATERIALES)
				.orderByTaskCreateTime().desc()
				.list()
				.stream()
				.map(TareaVista::desde)
				.toList();
	}

	/**
	 * Tareas de "Preparar materiales" sin reclamar cuyos grupos candidatos
	 * incluyen alguno de los grupos del usuario.
	 */
	public List<TareaVista> tareasReclamables(String usuario) {
		List<String> grupos = autenticacionService.gruposDe(usuario);
		if (grupos.isEmpty()) {
			return List.of();
		}
		return taskService.createTaskQuery()
				.taskDefinitionKey(TAREA_PREPARAR_MATERIALES)
				.taskCandidateGroupIn(grupos)
				.taskUnassigned()
				.orderByTaskCreateTime().desc()
				.list()
				.stream()
				.map(TareaVista::desde)
				.toList();
	}

	/**
	 * Reclama la tarea para el usuario y valida antes que sea una tarea de
	 * "Preparar materiales" sin asignar y que el usuario pertenezca a uno de sus
	 * grupos candidatos. La validacion previa es necesaria porque
	 * TaskService.claim() no controla los grupos candidatos.
	 */
	public void reclamar(String tareaId, String usuario) {
		Task tarea = buscarTarea(tareaId);
		if (tarea.getAssignee() != null) {
			throw new TareaException("La tarea ya fue reclamada por '" + tarea.getAssignee() + "'.");
		}
		List<String> grupos = autenticacionService.gruposDe(usuario);
		long habilitado = grupos.isEmpty() ? 0
				: taskService.createTaskQuery().taskId(tareaId).taskCandidateGroupIn(grupos).count();
		if (habilitado == 0) {
			throw new TareaException("No tenes permiso para reclamar esta tarea.");
		}
		try {
			taskService.claim(tareaId, usuario);
		}
		catch (FlowableTaskAlreadyClaimedException ex) {
			throw new TareaException("Otro usuario reclamo la tarea primero.");
		}
	}

	/**
	 * Completa la tarea solo si esta reclamada por el usuario. No envia
	 * variables: el BPMN no las requiere para esta tarea.
	 */
	public void completar(String tareaId, String usuario) {
		Task tarea = buscarTarea(tareaId);
		if (!usuario.equals(tarea.getAssignee())) {
			throw new TareaException("Solo podes completar una tarea reclamada por vos.");
		}
		try {
			taskService.complete(tareaId);
		}
		catch (FlowableException ex) {
			// Incluye errores de clase de listener faltante o tarea ya cerrada.
			log.error("Fallo al completar la tarea {}", tareaId, ex);
			throw new TareaException("No se pudo completar la tarea. Revisa el log de la aplicacion.");
		}
	}

	private Task buscarTarea(String tareaId) {
		Task tarea = taskService.createTaskQuery().taskId(tareaId).singleResult();
		if (tarea == null) {
			throw new TareaException("La tarea no existe o ya fue completada.");
		}
		if (!TAREA_PREPARAR_MATERIALES.equals(tarea.getTaskDefinitionKey())) {
			throw new TareaException("La tarea no corresponde a \"Preparar materiales\".");
		}
		return tarea;
	}

}
