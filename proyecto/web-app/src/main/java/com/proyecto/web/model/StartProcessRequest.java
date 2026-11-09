package com.proyecto.web.model;

import java.util.List;

/**
 * Cuerpo del POST /runtime/process-instances de Flowable.
 */
public record StartProcessRequest(String processDefinitionKey, List<VariableRequest> variables) {
}
