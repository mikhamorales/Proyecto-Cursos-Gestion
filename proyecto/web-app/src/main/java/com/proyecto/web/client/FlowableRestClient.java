package com.proyecto.web.client;

import com.proyecto.web.model.StartProcessRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP hacia Flowable. Centraliza las llamadas REST y traduce los
 * errores de transporte a una excepcion de dominio amigable.
 */
@Component
public class FlowableRestClient {

    private final RestClient processClient;
    private final RestClient uiClient;
    private final String username;
    private final String password;

    public FlowableRestClient(
            @Qualifier("flowableProcessRestClient") RestClient processClient,
            @Qualifier("flowableUiRestClient") RestClient uiClient,
            @Value("${flowable.rest.username}") String username,
            @Value("${flowable.rest.password}") String password) {
        this.processClient = processClient;
        this.uiClient = uiClient;
        this.username = username;
        this.password = password;
    }

    /**
     * Inicia una instancia del proceso con las variables indicadas.
     *
     * @return id de la instancia creada.
     */
    public String startProcess(StartProcessRequest request) {
        try {
            ProcessInstanceResponse response = processClient.post()
                    .uri("/runtime/process-instances")
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ProcessInstanceResponse.class);
            if (response == null || response.id() == null) {
                throw new FlowableUnavailableException("Flowable no devolvio el id de la instancia.");
            }
            return response.id();
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la solicitud (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    /**
     * Valida usuario y clave contra Flowable con autenticacion basica.
     *
     * @return true si Flowable responde 200; false si responde 401/403.
     */
    public boolean validateCredentials(String usuario, String clave) {
        try {
            uiClient.get()
                    .uri("/idm/users/{usuario}", usuario)
                    .headers(headers -> headers.setBasicAuth(usuario, clave))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                return false;
            }
            throw new FlowableUnavailableException(
                    "Flowable respondio con error (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    public record ProcessInstanceResponse(String id) {
    }

    public static class FlowableUnavailableException extends RuntimeException {
        public FlowableUnavailableException(String message) {
            super(message);
        }

        public FlowableUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
