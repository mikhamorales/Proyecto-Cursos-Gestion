package com.proyecto.web.model;

/**
 * Tarea preparada para mostrar en pantalla, con las acciones que admite.
 *
 * @param propia          asignada directamente al usuario logueado
 * @param reclamable      disponible para el grupo y todavia sin asignar
 * @param requiereRecursos la tarea "Evaluar recursos" necesita una pantalla aparte
 */
public record TareaVista(
        String id,
        String nombre,
        String processInstanceId,
        boolean propia,
        boolean reclamable,
        boolean requiereRecursos) {
}
