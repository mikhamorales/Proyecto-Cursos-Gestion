package com.proyecto.docente.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.proyecto.docente.model.TareaVista;

/**
 * Logica propia del equipo docente: definir quien desarrolla el curso y cargar
 * el informe de resultados. El jefe es el unico que puede hacerlo.
 */
@Service
public class DocenteService {

	private final TareaService tareaService;
	private final AutenticacionService autenticacionService;

	public DocenteService(TareaService tareaService, AutenticacionService autenticacionService) {
		this.tareaService = tareaService;
		this.autenticacionService = autenticacionService;
	}

	/** Los tres integrantes del equipo, jefe incluido. */
	public List<String> listarDocentes() {
		return autenticacionService.listarDocentes();
	}

	/**
	 * Completa 'Definir docente' guardando al elegido en la variable
	 * 'docenteSeleccionado'. El BPMN asignara 'Desarrollar curso' a ese usuario.
	 */
	public void definirDocente(String tareaId, String usuario, String docente) {
		verificarJefe(usuario);
		if (docente == null || !listarDocentes().contains(docente)) {
			throw new TareaException("El docente seleccionado no pertenece al equipo docente.");
		}
		TareaVista tarea = tareaService.obtenerTarea(tareaId);
		if (!tarea.isDefinirDocente()) {
			throw new TareaException("La tarea no corresponde a 'Definir docente'.");
		}
		tareaService.completar(tareaId, usuario, Map.of("docenteSeleccionado", docente));
	}

	/** Guarda el texto del informe en la variable 'informe' y completa la tarea. */
	public void guardarInforme(String tareaId, String usuario, String contenido) {
		verificarJefe(usuario);
		if (contenido == null || contenido.isBlank()) {
			throw new TareaException("Escribi el contenido del informe antes de completar la tarea.");
		}
		TareaVista tarea = tareaService.obtenerTarea(tareaId);
		if (!tarea.isInforme()) {
			throw new TareaException("La tarea no corresponde a 'Elaborar informe de resultados'.");
		}
		tareaService.completar(tareaId, usuario, Map.of("informe", contenido.trim()));
	}

	private void verificarJefe(String usuario) {
		if (!autenticacionService.esJefeDocente(usuario)) {
			throw new TareaException("Solo el jefe del equipo docente puede realizar esta accion.");
		}
	}

}
