package com.proyecto.docente.model;

/**
 * Formulario de la tarea 'Elaborar informe de resultados' del jefe docente.
 * Se guarda el texto como variable de proceso 'informe'. La generacion o el
 * adjunto de un PDF queda fuera de esta app, igual que en web-app.
 */
public class InformeForm {

	private String contenido;

	public InformeForm() {
	}

	public String getContenido() {
		return contenido;
	}

	public void setContenido(String contenido) {
		this.contenido = contenido;
	}

}
