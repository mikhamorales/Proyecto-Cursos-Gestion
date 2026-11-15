package com.proyecto.admin.controller;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.admin.config.SesionConstantes;
import com.proyecto.admin.service.TareaException;
import com.proyecto.admin.service.TareaService;

/**
 * Acciones sobre una tarea. Se usa POST + redirect (PRG) para que recargar la
 * pagina no repita la operacion.
 */
@Controller
@RequestMapping("/tareas")
public class TareaController {

	private final TareaService tareaService;

	public TareaController(TareaService tareaService) {
		this.tareaService = tareaService;
	}

	@PostMapping("/{id}/reclamar")
	public String reclamar(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion) {
		String usuario = (String) sesion.getAttribute(SesionConstantes.USUARIO);
		try {
			tareaService.reclamar(tareaId, usuario);
			redireccion.addFlashAttribute("mensaje", "Tarea reclamada correctamente.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

	@PostMapping("/{id}/completar")
	public String completar(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion) {
		String usuario = (String) sesion.getAttribute(SesionConstantes.USUARIO);
		try {
			tareaService.completar(tareaId, usuario);
			redireccion.addFlashAttribute("mensaje", "Tarea completada. El proceso continuo.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

}
