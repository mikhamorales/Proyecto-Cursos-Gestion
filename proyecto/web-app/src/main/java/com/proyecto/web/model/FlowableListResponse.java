package com.proyecto.web.model;

import java.util.List;

/**
 * Envoltura de las respuestas de listado de Flowable ({@code {"data": [...]}}).
 */
public record FlowableListResponse<T>(List<T> data) {
}
