package com.proyecto.docente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion del equipo docente.
 * Opera sobre el motor del docker mediante su API REST; si el motor no responde,
 * el FailureAnalyzer imprime un mensaje claro en lugar de un stacktrace.
 */
@SpringBootApplication
public class DocenteAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocenteAppApplication.class, args);
	}

}
