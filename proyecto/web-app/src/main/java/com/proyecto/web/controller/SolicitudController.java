package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.SolicitudForm;
import com.proyecto.web.service.SolicitudService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SolicitudController {

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("solicitudForm")) {
            model.addAttribute("solicitudForm", new SolicitudForm());
        }
        return "index";
    }

    @PostMapping("/solicitudes")
    public String registrar(@Valid @ModelAttribute("solicitudForm") SolicitudForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "index";
        }
        try {
            String id = solicitudService.registrarSolicitud(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Solicitud registrada correctamente. ID de instancia: " + id);
            return "redirect:/";
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            model.addAttribute("errorMessage",
                    "No se pudo registrar la solicitud: " + e.getMessage());
            return "index";
        }
    }
}
