package com.proyecto.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient apuntando a las dos bases de Flowable:
 * - process-api: operaciones de proceso (iniciar instancias, tareas, etc.)
 * - api: identidad/UI (validar credenciales)
 */
@Configuration
public class FlowableRestConfig {

    @Bean
    public RestClient flowableProcessRestClient(
            @Value("${flowable.rest.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient flowableUiRestClient(
            @Value("${flowable.rest.ui-base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
