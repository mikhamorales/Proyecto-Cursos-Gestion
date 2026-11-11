package com.proyecto.web.model;

/**
 * Suscripcion a un evento de runtime de Flowable. Para los mensajes,
 * {@code eventName} es el nombre que hay que enviar para disparar el evento.
 */
public record EventSubscription(
        String id,
        String eventType,
        String eventName,
        String activityId,
        String executionId,
        String processInstanceId) {
}
