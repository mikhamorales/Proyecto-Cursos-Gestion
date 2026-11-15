package com.proyecto.admin.controller;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.proyecto.admin.config.SesionConstantes;
import com.proyecto.admin.service.AutenticacionService;

/**
 * Login por sesion contra el IDM de Flowable. No usa Spring Security: el
 * alcance es una intranet local y el requisito es simple.
 */
@Controller
public class LoginController {

	private final AutenticacionService autenticacionService;

	public LoginController(AutenticacionService autenticacionService) {
		this.autenticacionService = autenticacionService;
	}

	@GetMapping("/login")
	public String formulario(HttpSession sesion) {
		if (sesion.getAttribute(SesionConstantes.USUARIO) != null) {
			return "redirect:/";
		}
		return "login";
	}

	@PostMapping("/login")
	public String ingresar(@RequestParam("usuario") String usuario, @RequestParam("password") String contrasena,
			HttpSession sesion, Model modelo) {
		if (autenticacionService.esAdministrativo(usuario, contrasena)) {
			sesion.setAttribute(SesionConstantes.USUARIO, usuario);
			return "redirect:/";
		}
		modelo.addAttribute("error",
				"Usuario o contrasena incorrectos, o el usuario no pertenece al grupo administracion.");
		modelo.addAttribute("usuario", usuario);
		return "login";
	}

	@PostMapping("/logout")
	public String salir(HttpSession sesion) {
		sesion.invalidate();
		return "redirect:/login";
	}

}
