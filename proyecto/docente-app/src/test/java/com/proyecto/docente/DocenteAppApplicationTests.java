package com.proyecto.docente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.proyecto.docente.client.FlowableRestClient;
import com.proyecto.docente.service.AutenticacionService;
import com.proyecto.docente.service.DocenteService;
import com.proyecto.docente.service.TareaException;
import com.proyecto.docente.service.TareaService;

/**
 * Test unitario simple (no @SpringBootTest): no necesita Docker ni el motor,
 * porque el cliente REST se simula con un mock.
 */
class DocenteAppApplicationTests {

	private FlowableRestClient cliente;
	private TareaService servicioTareas;
	private AutenticacionService autenticacion;
	private DocenteService servicioDocente;

	@BeforeEach
	void configurar() {
		cliente = Mockito.mock(FlowableRestClient.class);
		servicioTareas = new TareaService(cliente);
		autenticacion = new AutenticacionService(cliente, "jefeDocente", "docente1,docente2");
		servicioDocente = new DocenteService(servicioTareas, autenticacion);
	}

	@Test
	void loginExigePasswordYGrupoDocentes() {
		when(cliente.credencialesValidas("docente1", "docente1")).thenReturn(true);
		when(cliente.gruposDe("docente1")).thenReturn(List.of("docentes"));

		assertTrue(autenticacion.esDocente("docente1", "docente1"));
		assertFalse(autenticacion.esDocente("docente1", "mal"));
		assertEquals(List.of("jefeDocente", "docente1", "docente2"), autenticacion.listarDocentes());
		assertTrue(autenticacion.esJefeDocente("jefeDocente"));
	}

	@Test
	void definirDocenteValidaAlJefeYGuardaLaVariable() {
		when(cliente.tarea("tarea-1")).thenReturn(tarea(TareaService.DEFINIR_DOCENTE, "jefeDocente"));

		assertThrows(TareaException.class,
				() -> servicioDocente.definirDocente("tarea-1", "docente1", "docente1"));

		servicioDocente.definirDocente("tarea-1", "jefeDocente", "docente1");
		verify(cliente).completar("tarea-1", Map.of("docenteSeleccionado", "docente1"));
	}

	@Test
	void guardarInformeRechazaTextoVacio() {
		assertThrows(TareaException.class,
				() -> servicioDocente.guardarInforme("tarea-1", "jefeDocente", "   "));
	}

	private Map<String, Object> tarea(String taskDefinitionKey, String assignee) {
		Map<String, Object> tarea = new HashMap<>();
		tarea.put("id", "tarea-1");
		tarea.put("name", "Tarea de prueba");
		tarea.put("taskDefinitionKey", taskDefinitionKey);
		tarea.put("assignee", assignee);
		tarea.put("processInstanceId", "proceso-1");
		return tarea;
	}

}
