package com.proyecto.docente.client;

/** Error de comunicacion con la API REST del motor Flowable. */
public class FlowableRestException extends RuntimeException {

	public FlowableRestException(String mensaje) {
		super(mensaje);
	}

	public FlowableRestException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

}
