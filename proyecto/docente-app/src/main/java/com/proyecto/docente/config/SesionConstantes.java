package com.proyecto.docente.config;

/**
 * Nombres de los atributos guardados en la sesion HTTP.
 * No se guarda la contrasena: la API Java de Flowable usa el ProcessEngine.
 */
public final class SesionConstantes {

	/** Id del usuario autenticado (ej: jefeDocente, docente1, docente2). */
	public static final String USUARIO = "usuarioId";

	private SesionConstantes() {
	}

}
