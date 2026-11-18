package com.proyecto.docente.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el interceptor de autenticacion y deja publicos el login y los
 * recursos estaticos.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final AutenticacionInterceptor autenticacionInterceptor;

	public WebConfig(AutenticacionInterceptor autenticacionInterceptor) {
		this.autenticacionInterceptor = autenticacionInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(autenticacionInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/img/**");
	}

}
