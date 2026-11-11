package com.proyecto.web.model;

/**
 * Boton de simulacion disponible para una instancia: un mensaje que puede recibir.
 */
public record AccionSimulable(String eventName, String executionId, String etiqueta, boolean peligro) {
}
