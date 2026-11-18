package com.proyecto.docente.config;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verifica al arrancar que el ProcessEngine y el TaskService se inyectan
 * correctamente y que pueden consultar la BD del motor.
 */
@Component
public class FlowableStartupLogger implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FlowableStartupLogger.class);

	private final ProcessEngine processEngine;

	private final TaskService taskService;

	public FlowableStartupLogger(ProcessEngine processEngine, TaskService taskService) {
		this.processEngine = processEngine;
		this.taskService = taskService;
	}

	@Override
	public void run(ApplicationArguments args) {
		long tareasPendientes = taskService.createTaskQuery().count();
		log.info("***** Flowable listo: engine='{}' version='{}' *****", processEngine.getName(),
				ProcessEngine.VERSION);
		log.info("***** TaskService OK: {} tarea(s) pendiente(s) en el motor *****", tareasPendientes);
	}

}
