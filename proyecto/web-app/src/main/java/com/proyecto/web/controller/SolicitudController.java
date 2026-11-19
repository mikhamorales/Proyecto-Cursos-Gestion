package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.SolicitudForm;
import com.proyecto.web.service.SolicitudService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SolicitudController {

    private static final Logger log = LoggerFactory.getLogger(SolicitudController.class);

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
            if (e.getCause() instanceof RestClientResponseException respuesta) {
                HttpStatus status = HttpStatus.resolve(respuesta.getStatusCode().value());
                String detalle = respuesta.getResponseBodyAsString();
                log.error("Flowable rechazo la solicitud (HTTP {}): {}",
                        respuesta.getStatusCode().value(), detalle, e);
                throw new ResponseStatusException(
                        status == null ? HttpStatus.BAD_GATEWAY : status,
                        "Flowable rechazo la solicitud: " + detalle, e);
            }
            log.error("No se pudo registrar la solicitud en Flowable", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
    }
}
