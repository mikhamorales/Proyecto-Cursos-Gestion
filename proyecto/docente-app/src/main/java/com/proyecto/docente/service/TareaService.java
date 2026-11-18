package com.proyecto.docente.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.proyecto.docente.model.TareaVista;

/**
 * Operaciones sobre las tres tareas del carril docente del proceso de cursos.
 * Usa la API Java de Flowable embebida, igual que admin-app.
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

	private final TaskService taskService;
	private final RuntimeService runtimeService;

	public TareaService(TaskService taskService, RuntimeService runtimeService) {
		this.taskService = taskService;
		this.runtimeService = runtimeService;
	}

	/** Tareas de las tres claves ya reclamadas por el usuario (o asignadas directamente). */
	public List<TareaVista> tareasAsignadas(String usuario) {
		return mapear(taskService.createTaskQuery()
				.taskAssignee(usuario)
				.taskDefinitionKeys(CLAVES_DOCENTE)
				.orderByTaskCreateTime().desc()
				.list());
	}

	/**
	 * Tareas sin asignar cuyo candidato es el usuario o el grupo 'docentes'.
	 * 'Elaborar informe' usa candidateUsers, las demas usan candidateGroups
	 * segun el BPMN, por eso se contemplan ambos casos.
	 */
	public List<TareaVista> tareasReclamables(String usuario) {
		return mapear(taskService.createTaskQuery()
				.taskUnassigned()
				.taskDefinitionKeys(CLAVES_DOCENTE)
				.or()
					.taskCandidateUser(usuario)
					.taskCandidateGroup(AutenticacionService.GRUPO_DOCENTES)
				.endOr()
				.orderByTaskCreateTime().desc()
				.list());
	}

	/** Devuelve una tarea del carril docente ya preparada para la vista. */
	public TareaVista obtenerTarea(String tareaId) {
		return mapear(List.of(buscarTarea(tareaId))).get(0);
	}

	/**
	 * Reclama la tarea para el usuario. La validacion previa es necesaria porque
	 * TaskService.claim() no controla los candidatos.
	 */
	public void reclamar(String tareaId, String usuario) {
		Task tarea = buscarTarea(tareaId);
		if (tarea.getAssignee() != null) {
			throw new TareaException("La tarea ya fue reclamada por '" + tarea.getAssignee() + "'.");
		}
		long habilitado = taskService.createTaskQuery()
				.taskId(tareaId)
				.taskUnassigned()
				.or()
					.taskCandidateUser(usuario)
					.taskCandidateGroup(AutenticacionService.GRUPO_DOCENTES)
				.endOr()
				.count();
		if (habilitado == 0) {
			throw new TareaException("No tenes permiso para reclamar esta tarea.");
		}
		try {
			taskService.claim(tareaId, usuario);
		}
		catch (FlowableTaskAlreadyClaimedException ex) {
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
		Task tarea = buscarTarea(tareaId);
		if (!usuario.equals(tarea.getAssignee())) {
			throw new TareaException("Solo podes completar una tarea reclamada por vos.");
		}
		try {
			taskService.complete(tareaId, variables == null ? Map.of() : variables);
		}
		catch (FlowableException ex) {
			log.error("Fallo al completar la tarea {}", tareaId, ex);
			throw new TareaException("No se pudo completar la tarea. Revisa el log de la aplicacion.");
		}
	}

	private Task buscarTarea(String tareaId) {
		Task tarea = taskService.createTaskQuery().taskId(tareaId).singleResult();
		if (tarea == null) {
			throw new TareaException("La tarea no existe o ya fue completada.");
		}
		if (!CLAVES_DOCENTE.contains(tarea.getTaskDefinitionKey())) {
			throw new TareaException("La tarea no corresponde al equipo docente.");
		}
		return tarea;
	}

	private List<TareaVista> mapear(List<Task> tareas) {
		Map<String, Map<String, Object>> cacheVariables = new HashMap<>();
		return tareas.stream()
				.map(tarea -> TareaVista.desde(tarea, variablesDe(tarea.getProcessInstanceId(), cacheVariables)))
				.toList();
	}

	private Map<String, Object> variablesDe(String processInstanceId, Map<String, Map<String, Object>> cache) {
		if (processInstanceId == null) {
			return Map.of();
		}
		return cache.computeIfAbsent(processInstanceId, runtimeService::getVariables);
	}

}
