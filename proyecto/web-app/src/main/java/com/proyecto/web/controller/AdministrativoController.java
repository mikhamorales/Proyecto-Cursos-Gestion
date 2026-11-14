package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.RecursosForm;
import com.proyecto.web.model.Tarea;
import com.proyecto.web.model.VariableRequest;
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
 * Pantallas del personal administrativo: listado de tareas, reclamo y
 * completado. Todas las llamadas al motor usan las credenciales del empleado
 * logueado, guardadas en la sesion.
 */
@Controller
public class AdministrativoController {

    private final TareaService tareaService;
    private final RolService rolService;

    public AdministrativoController(TareaService tareaService, RolService rolService) {
        this.tareaService = tareaService;
        this.rolService = rolService;
    }

    @GetMapping("/administrativos")
    public String listarTareas(HttpSession session, Model model) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        String clave = clave(session);

        model.addAttribute("usuario", usuario);
        model.addAttribute("esJefe", rolService.esJefeAdministrativo(usuario));
        try {
            model.addAttribute("tareas", tareaService.listarTareas(usuario, clave));
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            model.addAttribute("errorMessage",
                    "No se pudieron consultar las tareas: " + e.getMessage());
            model.addAttribute("tareas", List.of());
        }
        return "administrativos";
    }

    @PostMapping("/administrativos/reclamar")
    public String reclamar(@RequestParam String taskId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        try {
            tareaService.reclamar(taskId, usuario, clave(session));
            redirectAttributes.addFlashAttribute("successMessage", "Tarea reclamada correctamente.");
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo reclamar la tarea: " + e.getMessage());
        }
        return "redirect:/administrativos";
    }

    @PostMapping("/administrativos/completar")
    public String completar(@RequestParam String taskId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        String clave = clave(session);
        try {
            Tarea tarea = tareaService.obtener(taskId, usuario, clave);
            if (TareaService.TAREA_EVALUAR_RECURSOS.equals(tarea.name())) {
                return "redirect:/administrativos/recursos/" + taskId;
            }
            tareaService.completar(taskId, usuario, clave, List.of());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tarea completada: " + texto(tarea.name()));
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo completar la tarea: " + e.getMessage());
        }
        return "redirect:/administrativos";
    }

    @GetMapping("/administrativos/recursos/{taskId}")
    public String mostrarRecursos(@PathVariable String taskId, HttpSession session, Model model) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        model.addAttribute("taskId", taskId);
        model.addAttribute("recursosForm", new RecursosForm());
        return "evaluar-recursos";
    }

    @PostMapping("/administrativos/recursos/{taskId}")
    public String completarRecursos(@PathVariable String taskId,
            @ModelAttribute("recursosForm") RecursosForm form, HttpSession session,
            Model model, RedirectAttributes redirectAttributes) {
        String usuario = usuarioAutorizado(session);
        if (usuario == null) {
            return "redirect:/";
        }
        if (!form.alMenosUnoSeleccionado()) {
            model.addAttribute("taskId", taskId);
            model.addAttribute("errorMessage", "Selecciona al menos un recurso.");
            return "evaluar-recursos";
        }

        List<VariableRequest> variables = List.of(
                new VariableRequest("aulaFisica", form.isAulaFisica()),
                new VariableRequest("aulaVirtual", form.isAulaVirtual()),
                new VariableRequest("equipamientoEspecial", form.isEquipamientoEspecial()));
        try {
            tareaService.completar(taskId, usuario, clave(session), variables);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Recursos evaluados correctamente.");
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo completar la tarea: " + e.getMessage());
        }
        return "redirect:/administrativos";
    }

    /**
     * Devuelve el usuario de la sesion si esta completo y tiene rol administrativo.
     */
    private String usuarioAutorizado(HttpSession session) {
        Object usuario = session.getAttribute("usuario");
        Object clave = session.getAttribute("password");
        if (usuario instanceof String nombre && clave instanceof String
                && rolService.esAdministrativo(nombre)) {
            return nombre;
        }
        return null;
    }

    private static String clave(HttpSession session) {
        return (String) session.getAttribute("password");
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
