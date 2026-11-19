package com.proyecto.web.model;

import java.util.List;

/**
 * Instancia del proceso que esta esperando uno o mas mensajes, o una tarea de recepcion.
 */
public record InstanciaSimulable(
        String processInstanceId,
        String empresa,
        String tematica,
        List<AccionSimulable> acciones,
        boolean esperandoNomina) {
}
