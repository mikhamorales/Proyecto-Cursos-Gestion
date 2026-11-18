package com.proyecto.docente.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.docente.config.SesionConstantes;
import com.proyecto.docente.model.TareaVista;
import com.proyecto.docente.service.AutenticacionService;
import com.proyecto.docente.service.TareaService;

/**
 * Regresion del 500 posterior al login: la plantilla index debe resolver las
 * propiedades booleanas de TareaVista (definirDocente, informe, desarrollo).
 * No levanta Flowable ni la base: solo la capa web con los servicios simulados.
 */
@WebMvcTest(controllers = HomeController.class)
class HomeControllerVistaTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TareaService tareaService;

	@MockBean
	private AutenticacionService autenticacionService;

	@Test
	void inicioConSesionDeJefeRenderizaLasAccionesDeLasTresTareas() throws Exception {
		when(autenticacionService.esJefeDocente("jefeDocente")).thenReturn(true);
		when(tareaService.tareasAsignadas("jefeDocente")).thenReturn(List.of(
				vista(TareaService.DEFINIR_DOCENTE, "Definir docente"),
				vista(TareaService.ELABORAR_INFORME, "Elaborar informe"),
				vista(TareaService.DESARROLLAR_CURSO, "Desarrollar curso")));
		when(tareaService.tareasReclamables("jefeDocente")).thenReturn(List.of());

		MockHttpSession sesion = new MockHttpSession();
		sesion.setAttribute(SesionConstantes.USUARIO, "jefeDocente");

		mockMvc.perform(get("/").session(sesion))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(content().string(containsString("Asignar docente")))
				.andExpect(content().string(containsString("Cargar informe")))
				.andExpect(content().string(containsString("Registrar finalizacion")));
	}

	private TareaVista vista(String taskDefinitionKey, String nombre) {
		return new TareaVista("tarea-" + taskDefinitionKey, nombre, "jefeDocente",
				"18/11/2026 01:00", "instancia-1", "Empresa", "Tematica", taskDefinitionKey);
	}

}
