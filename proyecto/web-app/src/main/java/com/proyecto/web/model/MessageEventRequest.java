package com.proyecto.web.model;

import java.util.List;

/**
 * Cuerpo del PUT /runtime/executions/{id} para disparar un mensaje.
 */
public record MessageEventRequest(String action, String messageName, List<VariableRequest> variables) {
}
