package com.proyecto.docente.config;

import java.sql.SQLException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Reemplaza el stacktrace crudo por un mensaje claro cuando el arranque falla
 * porque no se puede abrir conexion contra el Postgres del motor Flowable.
 * Registrado en META-INF/spring.factories.
 */
public class ConexionBaseDeDatosFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
		String description = "No se pudo conectar a la base de datos del motor Flowable.";
		String action = "Revisa que el docker este levantado y que el contenedor 'flowable-postgres' "
				+ "este escuchando en localhost:5432. Verifica tambien url/usuario/password "
				+ "en application.properties.";
		return new FailureAnalysis(description, action, cause);
	}

}
