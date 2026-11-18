package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.AccionSimulable;
import com.proyecto.web.model.EventSubscription;
import com.proyecto.web.model.InstanciaSimulable;
import com.proyecto.web.model.ProcessVariable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulacionServiceTest {

    @Test
    void etiquetasYAccionesPeligrosasUsanLosNombresRealesDeFlowable() {
        FlowableRestClient client = mock(FlowableRestClient.class);
        when(client.listMessageSubscriptions()).thenReturn(List.of(
                suscripcion("Aprobacion", "exec-aprobacion"),
                suscripcion("Rechazo", "exec-rechazo"),
                suscripcion("Modificacion", "exec-modificacion"),
                suscripcion("Cancelacion", "exec-cancelacion")));
        when(client.getProcessInstanceVariables("pi-1")).thenReturn(List.of(
                new ProcessVariable("empresa", "Acme"),
                new ProcessVariable("tematica", "Excel")));

        List<InstanciaSimulable> instancias = new SimulacionService(client).listarInstanciasSimulables();

        assertEquals(1, instancias.size());
        InstanciaSimulable instancia = instancias.get(0);
        assertEquals("Acme", instancia.empresa());
        assertEquals("Excel", instancia.tematica());

        Map<String, AccionSimulable> acciones = instancia.acciones().stream()
                .collect(Collectors.toMap(AccionSimulable::eventName, Function.identity()));

        assertEquals("Aceptar", acciones.get("Aprobacion").etiqueta());
        assertFalse(acciones.get("Aprobacion").peligro());

        assertEquals("Rechazar", acciones.get("Rechazo").etiqueta());
        assertTrue(acciones.get("Rechazo").peligro());

        assertEquals("Enviar modificaciones", acciones.get("Modificacion").etiqueta());
        assertFalse(acciones.get("Modificacion").peligro());

        assertEquals("Cancelar curso", acciones.get("Cancelacion").etiqueta());
        assertTrue(acciones.get("Cancelacion").peligro());
    }

    private static EventSubscription suscripcion(String eventName, String executionId) {
        return new EventSubscription("id-" + eventName, "message", eventName,
                "activity-" + eventName, executionId, "pi-1");
    }
}
