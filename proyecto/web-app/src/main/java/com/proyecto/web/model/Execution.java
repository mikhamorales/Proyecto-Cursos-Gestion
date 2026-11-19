package com.proyecto.web.model;

/**
 * Ejecucion de runtime devuelta por Flowable. Solo se usan {@code id},
 * {@code activityId} y {@code processInstanceId}; los campos extra de la
 * respuesta se ignoran.
 */
public record Execution(String id, String activityId, String processInstanceId) {
}
