package com.proyecto.docente.controller;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.proyecto.docente.client.FlowableRestException;
import com.proyecto.docente.config.SesionConstantes;
import com.proyecto.docente.service.AutenticacionService;
import com.proyecto.docente.service.TareaService;

@Controller
public class HomeController {

	private final TareaService tareaService;
	private final AutenticacionService autenticacionService;

	public HomeController(TareaService tareaService, AutenticacionService autenticacionService) {
		this.tareaService = tareaService;
		this.autenticacionService = autenticacionService;
	}

	@GetMapping("/")
	public String index(HttpSession sesion, Model modelo) {
		String usuario = (String) sesion.getAttribute(SesionConstantes.USUARIO);
		if (usuario == null) {
			return "redirect:/login";
		}
		modelo.addAttribute("usuario", usuario);
		modelo.addAttribute("esJefe", autenticacionService.esJefeDocente(usuario));
		try {
			modelo.addAttribute("asignadas", tareaService.tareasAsignadas(usuario));
			modelo.addAttribute("reclamables", tareaService.tareasReclamables(usuario));
		}
		catch (FlowableRestException ex) {
			modelo.addAttribute("error", ex.getMessage());
			modelo.addAttribute("asignadas", List.of());
			modelo.addAttribute("reclamables", List.of());
		}
		return "index";
	}

}
