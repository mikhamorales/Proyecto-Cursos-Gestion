package com.proyecto.docente.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.proyecto.docente.client.FlowableRestClient;

/**
 * Valida credenciales contra el IDM de Flowable por REST y exige pertenencia
 * al grupo 'docentes'. El jefe y los demas integrantes se configuran por
 * propiedades, igual que RolService en web-app.
 */
@Service
public class AutenticacionService {

	/** Unico grupo habilitado para operar las tareas del equipo docente. */
	public static final String GRUPO_DOCENTES = "docentes";

	private final FlowableRestClient cliente;
	private final String jefeDocente;
	private final List<String> docentes;

	public AutenticacionService(FlowableRestClient cliente,
			@Value("${flowable.rol.jefe-docente:jefeDocente}") String jefeDocente,
			@Value("${flowable.rol.docentes:docente1,docente2}") String docentes) {
		this.cliente = cliente;
		this.jefeDocente = jefeDocente;
		this.docentes = Arrays.stream(docentes.split(","))
				.map(String::trim)
				.filter(nombre -> !nombre.isEmpty())
				.toList();
	}

	/**
	 * Devuelve true solo si el password es correcto y el usuario pertenece al
	 * grupo 'docentes'. Un usuario valido de otro grupo queda rechazado.
	 */
	public boolean esDocente(String usuario, String contrasena) {
		if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty()) {
			return false;
		}
		if (!cliente.credencialesValidas(usuario, contrasena)) {
			return false;
		}
		return perteneceAlGrupo(usuario, GRUPO_DOCENTES);
	}

	public boolean perteneceAlGrupo(String usuario, String grupoId) {
		return cliente.gruposDe(usuario).contains(grupoId);
	}

	public boolean esJefeDocente(String usuario) {
		return usuario != null && usuario.equals(jefeDocente);
	}

	/** Los tres integrantes del equipo, con el jefe incluido. */
	public List<String> listarDocentes() {
		return Stream.concat(Stream.of(jefeDocente), docentes.stream())
				.filter(nombre -> nombre != null && !nombre.isBlank())
				.distinct()
				.toList();
	}

}
