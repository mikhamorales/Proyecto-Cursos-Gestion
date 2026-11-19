package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.AccionSimulable;
import com.proyecto.web.model.EventSubscription;
import com.proyecto.web.model.Execution;
import com.proyecto.web.model.InstanciaSimulable;
import com.proyecto.web.model.ProcessVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logica del panel de administracion: descubre que instancias esperan mensajes
 * y dispara la simulacion de cada mensaje contra Flowable.
 *
 * El gateway basado en eventos del proceso crea varias suscripciones para la
 * misma instancia (Modificaciones, Rechazo y Aceptacion; mas adelante Cancelacion),
 * por eso se agrupa por {@code processInstanceId}.
 *
 * <p>Ademas se detectan las instancias detenidas en la tarea de recepcion
 * "Recibir nomina", que no genera suscripcion a eventos ni aparece en
 * {@code /runtime/tasks}; se ubican consultando ejecuciones por actividad.</p>
 */
@Service
public class SimulacionService {

    private static final Logger log = LoggerFactory.getLogger(SimulacionService.class);

    private static final String ACTIVIDAD_RECIBIR_NOMINA = "sid-982375C3-4EF8-435A-A291-BF9A1933A05C";

    private final FlowableRestClient flowableRestClient;

    public SimulacionService(FlowableRestClient flowableRestClient) {
        this.flowableRestClient = flowableRestClient;
    }

    /**
     * Devuelve las instancias que pueden recibir algun mensaje o continuar la
     * tarea de recepcion, con las acciones disponibles para cada una.
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

        Map<String, Execution> nominaPorInstancia = new LinkedHashMap<>();
        for (Execution ejecucion : flowableRestClient.listExecutions(null, ACTIVIDAD_RECIBIR_NOMINA)) {
            if (ejecucion.processInstanceId() == null || ejecucion.id() == null) {
                continue;
            }
            nominaPorInstancia.merge(ejecucion.processInstanceId(), ejecucion,
                    (actual, candidata) -> candidata.id().compareTo(actual.id()) < 0 ? candidata : actual);
        }

        Set<String> identificadores = new LinkedHashSet<>(porInstancia.keySet());
        identificadores.addAll(nominaPorInstancia.keySet());

        List<InstanciaSimulable> instancias = new ArrayList<>();
        for (String processInstanceId : identificadores) {
            Map<String, Object> variables = variablesPorNombre(processInstanceId);

            List<AccionSimulable> acciones = porInstancia.getOrDefault(processInstanceId, List.of()).stream()
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
                    acciones,
                    nominaPorInstancia.containsKey(processInstanceId)));
        }
        return instancias;
    }

    /**
     * Senaliza la ejecucion detenida en "Recibir nomina" de la instancia indicada.
     */
    public void recibirNomina(String processInstanceId) {
        List<Execution> ejecuciones = flowableRestClient.listExecutions(
                processInstanceId, ACTIVIDAD_RECIBIR_NOMINA);
        if (ejecuciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "La instancia no existe o ya no esta esperando en 'Recibir nomina'.");
        }
        if (ejecuciones.size() > 1) {
            log.warn("Se encontraron {} ejecuciones en 'Recibir nomina' para la instancia {}; "
                    + "se senaliza la de menor id.", ejecuciones.size(), processInstanceId);
        }

        List<Execution> candidatas = ejecuciones.stream()
                .filter(ejecucion -> ACTIVIDAD_RECIBIR_NOMINA.equals(ejecucion.activityId()))
                .sorted(Comparator.comparing(Execution::id))
                .toList();
        Execution ejecucion = candidatas.isEmpty()
                ? ejecuciones.stream().sorted(Comparator.comparing(Execution::id)).findFirst().orElseThrow()
                : candidatas.get(0);
        flowableRestClient.signalExecution(ejecucion.id());
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
            case "Modificacion" -> "Enviar modificaciones";
            case "Rechazo" -> "Rechazar";
            case "Aprobacion" -> "Aceptar";
            case "Cancelacion" -> "Cancelar curso";
            default -> eventName;
        };
    }

    private static boolean esPeligrosa(String eventName) {
        return "Rechazo".equals(eventName) || "Cancelacion".equals(eventName);
    }
}
