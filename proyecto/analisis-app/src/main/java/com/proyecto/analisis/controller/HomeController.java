package com.proyecto.analisis.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;

import com.proyecto.analisis.model.ResumenAnalisis;
import com.proyecto.analisis.service.AnalisisService;

/** Unica pantalla: los tres informes del punto 4.3. */
@Controller
public class HomeController {

	private static final Logger log = LoggerFactory.getLogger(HomeController.class);

	private final AnalisisService analisisService;

	public HomeController(AnalisisService analisisService) {
		this.analisisService = analisisService;
	}

	@GetMapping("/")
	public String inicio(Model model) {
		try {
			model.addAttribute("resumen", analisisService.construirResumen());
		}
		catch (RestClientException ex) {
			log.warn("No se pudo consultar el historial de Flowable: {}", ex.getMessage());
			model.addAttribute("error", "No se pudo consultar el historial de Flowable. "
					+ "Revise que la API REST este disponible y que las credenciales sean validas.");
			model.addAttribute("resumen", ResumenAnalisis.vacio());
		}
		return "index";
	}
}
