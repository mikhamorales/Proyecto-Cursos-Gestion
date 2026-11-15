package com.proyecto.admin.controller;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.proyecto.admin.config.SesionConstantes;
import com.proyecto.admin.service.TareaService;

@Controller
public class HomeController {

	private final TareaService tareaService;

	public HomeController(TareaService tareaService) {
		this.tareaService = tareaService;
	}

	@GetMapping("/")
	public String index(HttpSession sesion, Model modelo) {
		String usuario = (String) sesion.getAttribute(SesionConstantes.USUARIO);
		modelo.addAttribute("usuario", usuario);
		modelo.addAttribute("asignadas", tareaService.tareasAsignadas(usuario));
		modelo.addAttribute("reclamables", tareaService.tareasReclamables(usuario));
		return "index";
	}

}
