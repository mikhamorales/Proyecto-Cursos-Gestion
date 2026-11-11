package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.service.AuthService;
import com.proyecto.web.service.SimulacionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class LoginController {

    private final AuthService authService;
    private final SimulacionService simulacionService;
    private final String adminUsername;

    public LoginController(AuthService authService, SimulacionService simulacionService,
            @Value("${flowable.admin.username:admin}") String adminUsername) {
        this.authService = authService;
        this.simulacionService = simulacionService;
        this.adminUsername = adminUsername;
    }

    @PostMapping("/login")
    public String login(@RequestParam String usuario, @RequestParam String password,
            HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            if (authService.validarCredenciales(usuario, password)) {
                session.setAttribute("usuario", usuario);
                // Se conserva la clave para autenticar las proximas llamadas al motor
                // con las credenciales del empleado logueado (proyecto academico).
                session.setAttribute("password", password);
                return "redirect:/empleados";
            }
            redirectAttributes.addFlashAttribute("loginError", "Usuario o contrasena incorrectos");
            return "redirect:/";
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("loginError",
                    "No se pudo conectar con Flowable. Intenta nuevamente mas tarde.");
            return "redirect:/";
        }
    }

    @GetMapping("/empleados")
    public String empleados(HttpSession session, Model model) {
        Object usuario = session.getAttribute("usuario");
        if (usuario == null) {
            return "redirect:/";
        }
        model.addAttribute("usuario", usuario);

        boolean esAdmin = adminUsername.equals(usuario);
        model.addAttribute("esAdmin", esAdmin);
        if (esAdmin) {
            try {
                model.addAttribute("instancias", simulacionService.listarInstanciasSimulables());
            } catch (FlowableRestClient.FlowableUnavailableException e) {
                model.addAttribute("errorMessage",
                        "No se pudo consultar Flowable. Intenta nuevamente mas tarde.");
                model.addAttribute("instancias", List.of());
            }
        }
        return "empleados";
    }

    @PostMapping("/empleados/simular")
    public String simular(@RequestParam String executionId, @RequestParam String messageName,
            HttpSession session, RedirectAttributes redirectAttributes) {
        Object usuario = session.getAttribute("usuario");
        if (usuario == null || !adminUsername.equals(usuario)) {
            return "redirect:/";
        }
        try {
            simulacionService.simular(executionId, messageName);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mensaje enviado: " + messageName);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (FlowableRestClient.FlowableUnavailableException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se pudo enviar el mensaje: " + e.getMessage());
        }
        return "redirect:/empleados";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
