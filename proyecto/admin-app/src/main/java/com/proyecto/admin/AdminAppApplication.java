package com.proyecto.admin;

import java.sql.SQLException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

@SpringBootApplication
public class AdminAppApplication {

	public static void main(String[] args) {
		try {
			SpringApplication.run(AdminAppApplication.class, args);
		}
		catch (Throwable ex) {
			// Ante un fallo de conexion contra el Postgres, el
			// ConexionBaseDeDatosFailureAnalyzer ya imprime un mensaje claro.
			// Salimos con codigo 1 sin volcar el stacktrace crudo.
			if (esErrorDeConexion(ex)) {
				System.exit(1);
			}
			if (ex instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (ex instanceof Error error) {
				throw error;
			}
			throw new RuntimeException("Fallo el arranque de admin-app", ex);
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
