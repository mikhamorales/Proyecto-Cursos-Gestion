package com.proyecto.web.model;

/**
 * Variable de proceso devuelta por Flowable. Solo se usan {@code name} y {@code value};
 * los campos extra de la respuesta (type, scope) se ignoran.
 */
public record ProcessVariable(String name, Object value) {
}
