package com.proyecto.docente;

import java.sql.SQLException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/**
 * Punto de entrada de la aplicacion del equipo docente.
 * Igual que admin-app: si el Postgres del motor esta caido, el FailureAnalyzer
 * imprime el mensaje claro y aqui se termina con codigo 1 sin stacktrace.
 */
@SpringBootApplication
public class DocenteAppApplication {

	public static void main(String[] args) {
		try {
			SpringApplication.run(DocenteAppApplication.class, args);
		}
		catch (Throwable ex) {
			if (esErrorDeConexion(ex)) {
				System.exit(1);
			}
			if (ex instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (ex instanceof Error error) {
				throw error;
			}
			throw new RuntimeException("Fallo el arranque de docente-app", ex);
		}
	}

	private static boolean esErrorDeConexion(Throwable ex) {
		Throwable actual = ex;
		while (actual != null) {
			if (actual instanceof SQLException || actual instanceof CannotGetJdbcConnectionException) {
				return true;
			}
			actual = actual.getCause();
		}
		return false;
	}

}
