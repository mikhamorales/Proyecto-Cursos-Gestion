package com.proyecto.web.model;

import java.util.List;

/**
 * Cuerpo del POST /runtime/process-instances de Flowable.
 *
 * <p>Para procesos que inician con un evento de mensaje se debe indicar
 * {@code message} (nombre del mensaje BPMN) en lugar de
 * {@code processDefinitionKey}.</p>
 */
public record StartProcessRequest(String message, List<VariableRequest> variables) {
}
