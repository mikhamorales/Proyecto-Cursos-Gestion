package com.proyecto.admin.service;

/**
 * Error de negocio al operar una tarea (no existe, ya reclamada, sin permiso,
 * etc.). El mensaje es apto para mostrarse en la interfaz.
 */
public class TareaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TareaException(String mensaje) {
		super(mensaje);
	}

}
