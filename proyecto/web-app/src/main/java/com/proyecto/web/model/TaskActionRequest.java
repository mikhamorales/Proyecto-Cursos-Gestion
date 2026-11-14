package com.proyecto.web.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Cuerpo del POST /runtime/tasks/{id} para reclamar o completar una tarea.
 * Los campos nulos se omiten para que cada accion envie solo lo que necesita.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskActionRequest(String action, String assignee, List<VariableRequest> variables) {
}
