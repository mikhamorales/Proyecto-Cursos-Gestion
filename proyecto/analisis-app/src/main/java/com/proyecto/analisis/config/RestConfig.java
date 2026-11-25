package com.proyecto.analisis.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Arma las cabeceras de autenticacion para la API REST de Flowable. */
@Component
public class RestConfig {

	private final String username;
	private final String password;

	public RestConfig(
			@Value("${flowable.rest.username}") String username,
			@Value("${flowable.rest.password}") String password) {
		this.username = username;
		this.password = password;
	}

	public HttpHeaders headers() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		return headers;
	}
}
