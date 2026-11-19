package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.AccionSimulable;
import com.proyecto.web.model.EventSubscription;
import com.proyecto.web.model.Execution;
import com.proyecto.web.model.InstanciaSimulable;
import com.proyecto.web.model.ProcessVariable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulacionServiceTest {

    private static final String ACTIVIDAD_RECIBIR_NOMINA = "sid-982375C3-4EF8-435A-A291-BF9A1933A05C";

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

    @Test
    void listarInstanciasSimulablesMarcaLaEsperaEnRecibirNomina() {
        FlowableRestClient client = mock(FlowableRestClient.class);
        when(client.listMessageSubscriptions()).thenReturn(List.of());
        when(client.listExecutions(null, ACTIVIDAD_RECIBIR_NOMINA)).thenReturn(List.of(
                ejecucion("exec-nomina", "pi-1")));
        when(client.getProcessInstanceVariables("pi-1")).thenReturn(List.of(
                new ProcessVariable("empresa", "Acme"),
                new ProcessVariable("tematica", "Excel")));

        List<InstanciaSimulable> instancias = new SimulacionService(client).listarInstanciasSimulables();

        assertEquals(1, instancias.size());
        InstanciaSimulable instancia = instancias.get(0);
        assertEquals("pi-1", instancia.processInstanceId());
        assertEquals("Acme", instancia.empresa());
        assertTrue(instancia.esperandoNomina());
        assertTrue(instancia.acciones().isEmpty());
    }

    @Test
    void recibirNominaSenalizaLaEjecucionDetenidaEnLaActividad() {
        FlowableRestClient client = mock(FlowableRestClient.class);
        when(client.listExecutions("pi-1", ACTIVIDAD_RECIBIR_NOMINA)).thenReturn(List.of(
                ejecucion("exec-nomina", "pi-1")));

        new SimulacionService(client).recibirNomina("pi-1");

        verify(client).signalExecution("exec-nomina");
    }

    @Test
    void recibirNominaSinEjecucionNoSenalizaYExplicaElError() {
        FlowableRestClient client = mock(FlowableRestClient.class);
        when(client.listExecutions("pi-inexistente", ACTIVIDAD_RECIBIR_NOMINA))
                .thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SimulacionService(client).recibirNomina("pi-inexistente"));

        assertTrue(error.getMessage().contains("no existe"));
        verify(client, never()).signalExecution(anyString());
    }

    private static EventSubscription suscripcion(String eventName, String executionId) {
        return new EventSubscription("id-" + eventName, "message", eventName,
                "activity-" + eventName, executionId, "pi-1");
    }

    private static Execution ejecucion(String executionId, String processInstanceId) {
        return new Execution(executionId, ACTIVIDAD_RECIBIR_NOMINA, processInstanceId);
    }
}
