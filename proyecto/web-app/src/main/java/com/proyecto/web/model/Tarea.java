package com.proyecto.web.model;

/**
 * Tarea de runtime devuelta por Flowable. Solo se usan los campos necesarios;
 * el resto de la respuesta (createTime, processDefinitionId, etc.) se ignora.
 */
public record Tarea(
        String id,
        String name,
        String assignee,
        String taskDefinitionKey,
        String processInstanceId) {
}
