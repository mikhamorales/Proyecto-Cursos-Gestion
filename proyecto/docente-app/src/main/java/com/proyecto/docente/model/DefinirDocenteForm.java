package com.proyecto.docente.model;

/**
 * Formulario para elegir al docente que desarrollara el curso.
 * En la vista se enlaza con @ModelAttribute y el radio button name="docente".
 */
public class DefinirDocenteForm {

	private String docente;

	public DefinirDocenteForm() {
	}

	public String getDocente() {
		return docente;
	}

	public void setDocente(String docente) {
		this.docente = docente;
	}

}
