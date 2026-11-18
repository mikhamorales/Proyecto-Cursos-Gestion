package com.proyecto.docente.controller;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.docente.config.SesionConstantes;
import com.proyecto.docente.model.DefinirDocenteForm;
import com.proyecto.docente.model.InformeForm;
import com.proyecto.docente.model.TareaVista;
import com.proyecto.docente.service.AutenticacionService;
import com.proyecto.docente.service.DocenteService;
import com.proyecto.docente.service.TareaException;
import com.proyecto.docente.service.TareaService;

/**
 * Acciones sobre las tareas del carril docente. Igual que en admin-app se usa
 * POST + redirect (PRG) para que recargar la pagina no repita la operacion.
 * El usuario siempre se toma de la sesion, nunca de un parametro del form.
 */
@Controller
@RequestMapping("/tareas")
public class TareaController {

	private final TareaService tareaService;
	private final DocenteService docenteService;
	private final AutenticacionService autenticacionService;

	public TareaController(TareaService tareaService, DocenteService docenteService,
			AutenticacionService autenticacionService) {
		this.tareaService = tareaService;
		this.docenteService = docenteService;
		this.autenticacionService = autenticacionService;
	}

	/** Pantalla para elegir al docente que desarrollara el curso (solo jefe). */
	@GetMapping("/{id}/asignar")
	public String asignar(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion, Model modelo) {
		String usuario = usuario(sesion);
		if (!autenticacionService.esJefeDocente(usuario)) {
			redireccion.addFlashAttribute("error", "Solo el jefe del equipo docente puede definir el docente.");
			return "redirect:/";
		}
		try {
			TareaVista tarea = tareaService.obtenerTarea(tareaId);
			if (!tarea.isDefinirDocente()) {
				throw new TareaException("La tarea no corresponde a 'Definir docente'.");
			}
			modelo.addAttribute("taskId", tareaId);
			modelo.addAttribute("tarea", tarea);
			modelo.addAttribute("docentes", docenteService.listarDocentes());
			modelo.addAttribute("definirDocenteForm", new DefinirDocenteForm());
			return "asignar-docente";
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
			return "redirect:/";
		}
	}

	/** Guarda al docente elegido y completa 'Definir docente' (solo jefe). */
	@PostMapping("/{id}/definir-docente")
	public String definirDocente(@PathVariable("id") String tareaId, HttpSession sesion,
			@ModelAttribute("definirDocenteForm") DefinirDocenteForm form, RedirectAttributes redireccion) {
		String usuario = usuario(sesion);
		try {
			docenteService.definirDocente(tareaId, usuario, form.getDocente());
			redireccion.addFlashAttribute("mensaje", "Docente definido correctamente.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

	/** Reclama una tarea pendiente del equipo docente. */
	@PostMapping("/{id}/reclamar")
	public String reclamar(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion) {
		String usuario = usuario(sesion);
		try {
			tareaService.reclamar(tareaId, usuario);
			redireccion.addFlashAttribute("mensaje", "Tarea reclamada correctamente.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

	/**
	 * Completa una tarea simple ('Desarrollar curso'). Las otras dos tareas
	 * tienen su propio formulario, al que se redirige antes de completarlas.
	 */
	@PostMapping("/{id}/completar")
	public String completar(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion) {
		String usuario = usuario(sesion);
		try {
			TareaVista tarea = tareaService.obtenerTarea(tareaId);
			if (tarea.isDefinirDocente()) {
				return "redirect:/tareas/" + tareaId + "/asignar";
			}
			if (tarea.isInforme()) {
				return "redirect:/tareas/" + tareaId + "/informe";
			}
			tareaService.completar(tareaId, usuario);
			redireccion.addFlashAttribute("mensaje", "Tarea completada. El proceso continuo.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

	/** Pantalla para redactar el informe de resultados (solo jefe). */
	@GetMapping("/{id}/informe")
	public String informe(@PathVariable("id") String tareaId, HttpSession sesion,
			RedirectAttributes redireccion, Model modelo) {
		String usuario = usuario(sesion);
		if (!autenticacionService.esJefeDocente(usuario)) {
			redireccion.addFlashAttribute("error", "Solo el jefe del equipo docente puede elaborar el informe.");
			return "redirect:/";
		}
		try {
			TareaVista tarea = tareaService.obtenerTarea(tareaId);
			if (!tarea.isInforme()) {
				throw new TareaException("La tarea no corresponde a 'Elaborar informe de resultados'.");
			}
			modelo.addAttribute("taskId", tareaId);
			modelo.addAttribute("tarea", tarea);
			modelo.addAttribute("esJefe", true);
			modelo.addAttribute("informeForm", new InformeForm());
			return "elaborar-informe";
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
			return "redirect:/";
		}
	}

	/** Guarda el texto del informe y completa la tarea (solo jefe). */
	@PostMapping("/{id}/informe")
	public String completarInforme(@PathVariable("id") String tareaId, HttpSession sesion,
			@ModelAttribute("informeForm") InformeForm form, RedirectAttributes redireccion) {
		String usuario = usuario(sesion);
		try {
			docenteService.guardarInforme(tareaId, usuario, form.getContenido());
			redireccion.addFlashAttribute("mensaje", "Informe guardado. El proceso continuo.");
		}
		catch (TareaException ex) {
			redireccion.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/";
	}

	private String usuario(HttpSession sesion) {
		return (String) sesion.getAttribute(SesionConstantes.USUARIO);
	}

}
