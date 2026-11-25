package com.proyecto.docente.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.proyecto.docente.client.FlowableRestClient;

/**
 * Verifica al arrancar que la API REST del motor responde y puede consultar
 * tareas. Si no responde, el FailureAnalyzer muestra el mensaje de error.
 */
@Component
public class FlowableStartupLogger implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FlowableStartupLogger.class);

	private final FlowableRestClient cliente;

	public FlowableStartupLogger(FlowableRestClient cliente) {
		this.cliente = cliente;
	}

	@Override
	public void run(ApplicationArguments args) {
		long tareasActivas = cliente.totalTareasActivas();
		log.info("***** Flowable REST listo: {} tarea(s) activa(s) en el motor *****", tareasActivas);
	}

}
