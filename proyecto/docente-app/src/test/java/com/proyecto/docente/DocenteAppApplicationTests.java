package com.proyecto.docente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.idm.api.GroupQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.proyecto.docente.service.AutenticacionService;
import com.proyecto.docente.service.DocenteService;
import com.proyecto.docente.service.TareaException;
import com.proyecto.docente.service.TareaService;

/**
 * Test unitario simple (no @SpringBootTest): no necesita Docker ni el jar de
 * shared, porque todos los servicios de Flowable se simulan con mocks.
 */
class DocenteAppApplicationTests {

	private TaskService taskService;
	private RuntimeService runtimeService;
	private IdentityService identityService;
	private TareaService servicioTareas;
	private AutenticacionService autenticacion;
	private DocenteService servicioDocente;

	@BeforeEach
	void configurar() {
		taskService = Mockito.mock(TaskService.class);
		runtimeService = Mockito.mock(RuntimeService.class);
		identityService = Mockito.mock(IdentityService.class);
		servicioTareas = new TareaService(taskService, runtimeService);
		autenticacion = new AutenticacionService(identityService, "jefeDocente", "docente1,docente2");
		servicioDocente = new DocenteService(servicioTareas, autenticacion);
	}

	@Test
	void loginExigePasswordYGrupoDocentes() {
		GroupQuery grupoValido = Mockito.mock(GroupQuery.class);
		when(identityService.checkPassword("docente1", "docente1")).thenReturn(true);
		when(identityService.createGroupQuery()).thenReturn(grupoValido);
		when(grupoValido.groupMember("docente1")).thenReturn(grupoValido);
		when(grupoValido.groupId("docentes")).thenReturn(grupoValido);
		when(grupoValido.count()).thenReturn(1L);

		assertTrue(autenticacion.esDocente("docente1", "docente1"));
		assertFalse(autenticacion.esDocente("docente1", "mal"));
		assertEquals(List.of("jefeDocente", "docente1", "docente2"), autenticacion.listarDocentes());
		assertTrue(autenticacion.esJefeDocente("jefeDocente"));
	}

	@Test
	void definirDocenteValidaAlJefeYGuardaLaVariable() {
		Task tarea = tarea(TareaService.DEFINIR_DOCENTE, "jefeDocente");
		prepararConsultaPorId(tarea);

		assertThrows(TareaException.class,
				() -> servicioDocente.definirDocente(tarea.getId(), "docente1", "docente1"));

		TaskQuery consulta = Mockito.mock(TaskQuery.class);
		when(taskService.createTaskQuery()).thenReturn(consulta);
		when(consulta.taskId(tarea.getId())).thenReturn(consulta);
		when(consulta.singleResult()).thenReturn(tarea);

		servicioDocente.definirDocente(tarea.getId(), "jefeDocente", "docente1");
		Mockito.verify(taskService).complete(tarea.getId(), Map.of("docenteSeleccionado", "docente1"));
	}

	@Test
	void guardarInformeRechazaTextoVacio() {
		assertThrows(TareaException.class,
				() -> servicioDocente.guardarInforme("tarea-1", "jefeDocente", "   "));
	}

	private Task tarea(String taskDefinitionKey, String assignee) {
		Task tarea = Mockito.mock(Task.class);
		when(tarea.getId()).thenReturn("tarea-1");
		when(tarea.getName()).thenReturn("Tarea de prueba");
		when(tarea.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
		when(tarea.getAssignee()).thenReturn(assignee);
		when(tarea.getProcessInstanceId()).thenReturn("proceso-1");
		return tarea;
	}

	private void prepararConsultaPorId(Task tarea) {
		TaskQuery consulta = Mockito.mock(TaskQuery.class);
		when(taskService.createTaskQuery()).thenReturn(consulta);
		when(consulta.taskId(tarea.getId())).thenReturn(consulta);
		when(consulta.singleResult()).thenReturn(tarea);
	}

}
