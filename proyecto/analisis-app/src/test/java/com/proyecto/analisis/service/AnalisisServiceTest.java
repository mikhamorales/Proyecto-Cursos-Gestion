package com.proyecto.analisis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.analisis.client.FlowableHistoryClient;
import com.proyecto.analisis.model.DistribucionVista;
import com.proyecto.analisis.model.HistoricActivityInstance;
import com.proyecto.analisis.model.HistoricProcessInstance;
import com.proyecto.analisis.model.HistoricTaskInstance;
import com.proyecto.analisis.model.RamaVista;
import com.proyecto.analisis.model.ResultadoVista;
import com.proyecto.analisis.model.ResumenAnalisis;

class AnalisisServiceTest {

	private FlowableHistoryClient client;
	private AnalisisService service;

	@BeforeEach
	void configurar() {
		client = mock(FlowableHistoryClient.class);
		service = new AnalisisService(
				client,
				"Preparacion completa",
				new String[] { "Recibir nomina", "Confeccionar lista definitiva" },
				new String[] { "Preparar materiales" },
				new String[] { "Evaluar recursos", "Reservar aula", "Solicitar aula virtual", "Contratar equipamiento" },
				new String[] { "jefeAdmin", "admin1" },
				new String[] { "jefeDocente", "docente1" });

		HistoricProcessInstance terminada = instancia("i1", null);
		HistoricProcessInstance cancelada = instancia("i2", null);
		HistoricProcessInstance borrada = instancia("i3", "process instance deleted");
		when(client.instanciasTerminadas()).thenReturn(List.of(terminada, cancelada, borrada));
		when(client.resultado("i1")).thenReturn("FINALIZADO");
		when(client.resultado("i2")).thenReturn("CANCELADO");

		when(client.actividades("i1")).thenReturn(List.of(
				actividad("Preparacion completa", "2026-01-01T01:30:00.000+0000", "2026-01-01T01:30:00.000+0000"),
				actividad("Recibir nomina", "2026-01-01T00:00:00.000+0000", "2026-01-01T00:10:00.000+0000"),
				actividad("Confeccionar lista definitiva", "2026-01-01T00:10:00.000+0000", "2026-01-01T00:30:00.000+0000"),
				actividad("Preparar materiales", "2026-01-01T00:00:00.000+0000", "2026-01-01T01:00:00.000+0000"),
				actividad("Evaluar recursos", "2026-01-01T00:00:00.000+0000", "2026-01-01T00:10:00.000+0000"),
				actividad("Reservar aula", "2026-01-01T00:10:00.000+0000", "2026-01-01T00:20:00.000+0000"),
				actividad("Solicitar aula virtual", "2026-01-01T00:20:00.000+0000", "2026-01-01T00:30:00.000+0000"),
				actividad("Liberar aula", "2026-01-01T01:30:00.000+0000", "2026-01-01T01:40:00.000+0000")));
		when(client.actividades("i2")).thenReturn(List.of(
				actividad("Preparacion completa", "2026-01-01T00:00:00.000+0000", "2026-01-01T00:05:00.000+0000"),
				actividad("Preparar materiales", "2026-01-01T00:00:00.000+0000", "2026-01-01T00:05:00.000+0000")));

		when(client.tareasTerminadas()).thenReturn(List.of(
				tarea("admin1", null),
				tarea("admin1", null),
				tarea("docente1", null),
				tarea("otro", null),
				tarea("jefeAdmin", "boundary event"),
				tarea("", null)));
	}

	@Test
	void calculaResultadosSobreInstanciasTerminadas() {
		ResumenAnalisis resumen = service.construirResumen();

		assertEquals(2, resumen.getTotalInstancias());
		ResultadoVista finalizado = resultado(resumen, "Curso finalizado");
		assertEquals(1, finalizado.getCantidad());
		assertEquals("50,0 %", finalizado.getPorcentajeTexto());

		assertEquals(1, resultado(resumen, "Cancelacion").getCantidad());
		assertEquals(0, resultado(resumen, "Rechazo de la propuesta").getCantidad());
		assertEquals(0, resultado(resumen, "Solicitud no viable").getCantidad());
		assertEquals(0, resultado(resumen, "Cierre por falta de respuesta").getCantidad());

		ResultadoVista total = resultado(resumen, "Total");
		assertTrue(total.isTotal());
		assertEquals(2, total.getCantidad());
		assertEquals("100,0 %", total.getPorcentajeTexto());
		verify(client, never()).resultado("i3");
	}

	@Test
	void mideSoloPreparacionesCompletasNoCanceladas() {
		ResumenAnalisis resumen = service.construirResumen();

		assertEquals(1, resumen.getInstanciasPreparacion());
		RamaVista inscripciones = rama(resumen, "Inscripciones");
		RamaVista materiales = rama(resumen, "Materiales");
		RamaVista recursos = rama(resumen, "Recursos");

		assertEquals(1, inscripciones.getCasos());
		assertEquals("30 min", inscripciones.getPromedioTexto());
		assertEquals(1, materiales.getCasos());
		assertEquals("1 h 0 min", materiales.getPromedioTexto());
		assertEquals(1, recursos.getCasos());
		assertEquals("30 min", recursos.getPromedioTexto());
		assertEquals(100.0, materiales.getAncho());
		assertTrue(inscripciones.getAncho() > 0 && inscripciones.getAncho() < 100);
	}

	@Test
	void cuentaSolicitudesTareasCompletadas() {
		ResumenAnalisis resumen = service.construirResumen();

		assertEquals(4, resumen.getTotalTareas());
		assertEquals(2, tarea(resumen, "Administrativo", "admin1").getCantidad());
		assertEquals(1, tarea(resumen, "Docente", "docente1").getCantidad());
		assertEquals(0, tarea(resumen, "Administrativo", "jefeAdmin").getCantidad());
		assertEquals(1, tarea(resumen, "Sin rol definido", "otro").getCantidad());
		assertEquals(2, totalRol(resumen, "Total administrativo"));
		assertEquals(1, totalRol(resumen, "Total docente"));
	}

	private static HistoricProcessInstance instancia(String id, String deleteReason) {
		HistoricProcessInstance instancia = new HistoricProcessInstance();
		instancia.setId(id);
		instancia.setDeleteReason(deleteReason);
		return instancia;
	}

	private static HistoricActivityInstance actividad(String nombre, String inicio, String fin) {
		HistoricActivityInstance actividad = new HistoricActivityInstance();
		actividad.setActivityName(nombre);
		actividad.setActivityId(nombre);
		actividad.setProcessInstanceId("i1");
		actividad.setStartTime(inicio);
		actividad.setEndTime(fin);
		return actividad;
	}

	private static HistoricTaskInstance tarea(String usuario, String deleteReason) {
		HistoricTaskInstance tarea = new HistoricTaskInstance();
		tarea.setAssignee(usuario);
		tarea.setDeleteReason(deleteReason);
		tarea.setEndTime("2026-01-01T00:00:00.000+0000");
		return tarea;
	}

	private static ResultadoVista resultado(ResumenAnalisis resumen, String etiqueta) {
		return resumen.getResultados().stream()
				.filter(fila -> fila.getEtiqueta().equals(etiqueta))
				.findFirst()
				.orElseThrow();
	}

	private static RamaVista rama(ResumenAnalisis resumen, String nombre) {
		return resumen.getRamas().stream()
				.filter(fila -> fila.getNombre().equals(nombre))
				.findFirst()
				.orElseThrow();
	}

	private static DistribucionVista tarea(ResumenAnalisis resumen, String rol, String usuario) {
		return resumen.getDistribucion().stream()
				.filter(fila -> fila.getRol().equals(rol) && fila.getUsuario().equals(usuario))
				.findFirst()
				.orElseThrow();
	}

	private static int totalRol(ResumenAnalisis resumen, String etiqueta) {
		return resumen.getDistribucion().stream()
				.filter(DistribucionVista::isTotal)
				.filter(fila -> fila.getRol().equals(etiqueta))
				.findFirst()
				.orElseThrow()
				.getCantidad();
	}
}
