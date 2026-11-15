package com.proyecto.admin.config;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Exige una sesion iniciada para acceder a cualquier ruta que no sea el login.
 * Si no hay usuario en sesion, redirige a /login.
 */
@Component
public class AutenticacionInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		HttpSession sesion = request.getSession(false);
		if (sesion != null && sesion.getAttribute(SesionConstantes.USUARIO) != null) {
			return true;
		}
		response.sendRedirect(request.getContextPath() + "/login");
		return false;
	}

}
