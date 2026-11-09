package com.proyecto.web.controller;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {

    private final AuthService authService;

    public LoginController(AuthService authService) {
        this.authService = authService;
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
        return "empleados";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
