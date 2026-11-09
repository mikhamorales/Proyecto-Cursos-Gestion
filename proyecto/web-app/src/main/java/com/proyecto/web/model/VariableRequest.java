package com.proyecto.web.model;

/**
 * Variable de proceso enviada a la API REST de Flowable.
 */
public record VariableRequest(String name, Object value) {
}
