package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.DefinirDocenteForm;
import com.proyecto.web.model.Tarea;
import com.proyecto.web.service.DocenteService;
import com.proyecto.web.service.RolService;
import com.proyecto.web.service.TareaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Pantallas del equipo docente: listado de tareas, seleccion del docente que
 * desarrollara el curso y finalizacion del dictado.
 */
@Controller
public class DocenteController {

    private final DocenteService docenteService;
    private final TareaService tareaService;
    private final RolService rolService;

    public DocenteController(DocenteService docenteService, TareaService tareaService,
            RolService rolService) {
        this.docenteService = docenteService;
        this.tareaService = tareaService;
        this.rolService = rolService;
    }

    @GetMapping("/docentes")
    public String listarTareas(HttpSession session, Model model) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        String clave = clave(session);

        model.addAttribute("usuario", usuario);
        model.addAttribute("esJefe", rolService.esJefeDocente(usuario));
        try {
            model.addAttribute("tareas", docenteService.listarTareasDocente(usuario, clave));
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            model.addAttribute("errorMessage",
                    "No se pudieron consultar las tareas: " + e.getMessage());
            model.addAttribute("tareas", List.of());
        }
        return "docentes";
    }

    @GetMapping("/docentes/asignar/{taskId}")
    public String mostrarAsignacion(@PathVariable String taskId, HttpSession session,
            Model model, RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null || !rolService.esJefeDocente(usuario)) {
            return "redirect:/";
        }
        try {
            Tarea tarea = tareaService.obtener(taskId, usuario, clave(session));
            if (!DocenteService.esDefinirDocente(tarea.name())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "La tarea indicada no corresponde a la definicion de docente.");
                return "redirect:/docentes";
            }
            model.addAttribute("taskId", taskId);
            model.addAttribute("tarea", tarea);
            model.addAttribute("docentes", docenteService.listarDocentes());
            model.addAttribute("definirDocenteForm", new DefinirDocenteForm());
            return "asignar-docente";
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo obtener la tarea: " + e.getMessage());
            return "redirect:/docentes";
        }
    }

    @PostMapping("/docentes/asignar/{taskId}")
    public String asignar(@PathVariable String taskId,
            @ModelAttribute("definirDocenteForm") DefinirDocenteForm form,
            HttpSession session, RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null || !rolService.esJefeDocente(usuario)) {
            return "redirect:/";
        }
        try {
            docenteService.definirDocente(taskId, usuario, clave(session), form.getDocente());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Docente asignado: " + form.getDocente());
            return "redirect:/docentes";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/docentes/asignar/" + taskId;
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo asignar el docente: " + e.getMessage());
            return "redirect:/docentes";
        }
    }

    @PostMapping("/docentes/reclamar")
    public String reclamar(@RequestParam String taskId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        try {
            tareaService.reclamar(taskId, usuario, clave(session));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tarea reclamada correctamente.");
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo reclamar la tarea: " + e.getMessage());
        }
        return "redirect:/docentes";
    }

    @PostMapping("/docentes/completar")
    public String completar(@RequestParam String taskId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        try {
            String clave = clave(session);
            Tarea tarea = tareaService.obtener(taskId, usuario, clave);
            if (DocenteService.esDefinirDocente(tarea.name())) {
                return "redirect:/docentes/asignar/" + taskId;
            }
            tareaService.completar(taskId, usuario, clave, List.of());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tarea completada correctamente.");
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo completar la tarea: " + e.getMessage());
        }
        return "redirect:/docentes";
    }

    /**
     * Devuelve el usuario de la sesion si esta completo y pertenece al equipo
     * docente (incluido el jefe).
     */
    private String usuarioAutorizado(HttpSession session) {
        Object usuario = session.getAttribute("usuario");
        Object clave = session.getAttribute("password");
        if (usuario instanceof String nombre && clave instanceof String
                && rolService.esDocente(nombre)) {
            return nombre;
        }
        return null;
    }

    private static String clave(HttpSession session) {
        return (String) session.getAttribute("password");
    }
}
