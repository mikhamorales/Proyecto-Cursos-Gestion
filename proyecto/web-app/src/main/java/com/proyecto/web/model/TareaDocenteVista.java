package com.proyecto.web.model;

/**
 * Tarea preparada para la pantalla del equipo docente.
 *
 * @param propia                 asignada directamente al usuario logueado
 * @param reclamable             disponible para el grupo y todavia sin asignar
 * @param requiereDefinirDocente la tarea necesita elegir un docente
 * @param requiereDesarrollo     la tarea corresponde al dictado del curso
 */
public record TareaDocenteVista(
        String id,
        String nombre,
        String processInstanceId,
        String empresa,
        String tematica,
        boolean propia,
        boolean reclamable,
        boolean requiereDefinirDocente,
        boolean requiereDesarrollo) {
}
