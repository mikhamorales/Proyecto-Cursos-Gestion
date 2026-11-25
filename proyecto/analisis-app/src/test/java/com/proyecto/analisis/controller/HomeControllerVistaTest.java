package com.proyecto.analisis.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.analisis.model.DistribucionVista;
import com.proyecto.analisis.model.RamaVista;
import com.proyecto.analisis.model.ResultadoVista;
import com.proyecto.analisis.model.ResumenAnalisis;
import com.proyecto.analisis.service.AnalisisService;

@WebMvcTest(HomeController.class)
class HomeControllerVistaTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AnalisisService analisisService;

	@Test
	void renderizaLasTresSecciones() throws Exception {
		ResumenAnalisis resumen = new ResumenAnalisis(
				List.of(new ResultadoVista("Curso finalizado", 1, "100,0 %", 100, false),
						new ResultadoVista("Total", 1, "100,0 %", 100, true)),
				List.of(new RamaVista("Inscripciones", 1, "5 min", 100)),
				List.of(new DistribucionVista("Administrativo", "admin1", 1, 100, false)),
				1, 1, 1);
		when(analisisService.construirResumen()).thenReturn(resumen);

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(content().string(containsString("Resultados de las solicitudes")))
				.andExpect(content().string(containsString("Analisis de la preparacion paralela")))
				.andExpect(content().string(containsString("Distribucion de tareas completadas")))
				.andExpect(content().string(containsString("width: 100.0%")));
	}
}
