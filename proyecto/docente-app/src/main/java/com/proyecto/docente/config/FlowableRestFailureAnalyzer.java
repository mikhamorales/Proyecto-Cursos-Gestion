package com.proyecto.docente.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import com.proyecto.docente.client.FlowableRestException;

/**
 * Reemplaza el stacktrace crudo por un mensaje claro cuando el arranque falla
 * porque la API REST del motor Flowable no responde.
 * Registrado en META-INF/spring.factories.
 */
public class FlowableRestFailureAnalyzer extends AbstractFailureAnalyzer<FlowableRestException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, FlowableRestException cause) {
		String description = "No se pudo conectar con la API REST del motor Flowable.";
		String action = "Revisa que el contenedor 'flowable-ui' este levantado en localhost:8080 "
				+ "y que la URL/usuario/password de application.properties sean correctos.";
		return new FailureAnalysis(description, action, cause);
	}

}
