package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.AccionSimulable;
import com.proyecto.web.model.EventSubscription;
import com.proyecto.web.model.InstanciaSimulable;
import com.proyecto.web.model.ProcessVariable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica del panel de administracion: descubre que instancias esperan mensajes
 * y dispara la simulacion de cada mensaje contra Flowable.
 *
 * El gateway basado en eventos del proceso crea varias suscripciones para la
 * misma instancia (Modificaciones, Rechazo y Aceptacion; mas adelante Cancelacion),
 * por eso se agrupa por {@code processInstanceId}.
 */
@Service
public class SimulacionService {

    private final FlowableRestClient flowableRestClient;

    public SimulacionService(FlowableRestClient flowableRestClient) {
        this.flowableRestClient = flowableRestClient;
    }

    /**
     * Devuelve las instancias que pueden recibir algun mensaje, con las acciones
     * disponibles para cada una.
     */
    public List<InstanciaSimulable> listarInstanciasSimulables() {
        List<EventSubscription> suscripciones = flowableRestClient.listMessageSubscriptions();

        Map<String, List<EventSubscription>> porInstancia = new LinkedHashMap<>();
        for (EventSubscription suscripcion : suscripciones) {
            if (suscripcion.processInstanceId() == null || suscripcion.executionId() == null) {
                continue;
            }
            porInstancia.computeIfAbsent(suscripcion.processInstanceId(), clave -> new ArrayList<>())
                    .add(suscripcion);
        }

        List<InstanciaSimulable> instancias = new ArrayList<>();
        for (Map.Entry<String, List<EventSubscription>> entrada : porInstancia.entrySet()) {
            String processInstanceId = entrada.getKey();
            Map<String, Object> variables = variablesPorNombre(processInstanceId);

            List<AccionSimulable> acciones = entrada.getValue().stream()
                    .map(suscripcion -> new AccionSimulable(
                            suscripcion.eventName(),
                            suscripcion.executionId(),
                            etiqueta(suscripcion.eventName()),
                            esPeligrosa(suscripcion.eventName())))
                    .toList();

            instancias.add(new InstanciaSimulable(
                    processInstanceId,
                    texto(variables.get("empresa")),
                    texto(variables.get("tematica")),
                    acciones));
        }
        return instancias;
    }

    /**
     * Envia el mensaje indicado a la ejecucion, validando que siga esperandolo.
     */
    public void simular(String executionId, String messageName) {
        boolean disponible = flowableRestClient.listMessageSubscriptions().stream()
                .anyMatch(suscripcion -> executionId.equals(suscripcion.executionId())
                        && messageName.equals(suscripcion.eventName()));
        if (!disponible) {
            throw new IllegalArgumentException("La instancia ya no espera ese mensaje.");
        }
        flowableRestClient.sendMessage(executionId, messageName, List.of());
    }

    private Map<String, Object> variablesPorNombre(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        for (ProcessVariable variable : flowableRestClient.getProcessInstanceVariables(processInstanceId)) {
            variables.put(variable.name(), variable.value());
        }
        return variables;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String etiqueta(String eventName) {
        return switch (eventName) {
            case "MModificacion" -> "Enviar modificaciones";
            case "MRechazo" -> "Rechazar";
            case "MAceptacion" -> "Aceptar";
            case "MCancelacion" -> "Cancelar curso";
            default -> eventName;
        };
    }

    private static boolean esPeligrosa(String eventName) {
        return "MRechazo".equals(eventName) || "MCancelacion".equals(eventName);
    }
}
