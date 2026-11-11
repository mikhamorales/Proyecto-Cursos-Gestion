package com.proyecto.web.model;

import java.util.List;

/**
 * Instancia del proceso que esta esperando uno o mas mensajes.
 */
public record InstanciaSimulable(
        String processInstanceId,
        String empresa,
        String tematica,
        List<AccionSimulable> acciones) {
}
