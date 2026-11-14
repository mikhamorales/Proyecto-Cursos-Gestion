package com.proyecto.web.client;

import com.proyecto.web.model.EventSubscription;
import com.proyecto.web.model.FlowableListResponse;
import com.proyecto.web.model.MessageEventRequest;
import com.proyecto.web.model.ProcessVariable;
import com.proyecto.web.model.StartProcessRequest;
import com.proyecto.web.model.Tarea;
import com.proyecto.web.model.TaskActionRequest;
import com.proyecto.web.model.VariableRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

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

    /**
     * Lista las suscripciones a eventos de tipo mensaje.
     */
    public List<EventSubscription> listMessageSubscriptions() {
        try {
            FlowableListResponse<EventSubscription> response = processClient.get()
                    .uri("/runtime/event-subscriptions?eventType=message")
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .retrieve()
                    .body(new ParameterizedTypeReference<FlowableListResponse<EventSubscription>>() {
                    });
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la consulta de mensajes (HTTP "
                            + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    /**
     * Devuelve las variables de una instancia de proceso.
     */
    public List<ProcessVariable> getProcessInstanceVariables(String processInstanceId) {
        try {
            ProcessVariable[] variables = processClient.get()
                    .uri("/runtime/process-instances/{id}/variables", processInstanceId)
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .retrieve()
                    .body(ProcessVariable[].class);
            return variables == null ? List.of() : List.of(variables);
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la consulta de variables (HTTP "
                            + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    /**
     * Envia un mensaje a una ejecucion para disparar un evento de recepcion.
     */
    public void sendMessage(String executionId, String messageName, List<VariableRequest> variables) {
        try {
            processClient.put()
                    .uri("/runtime/executions/{id}", executionId)
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MessageEventRequest("messageEventReceived", messageName, variables))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo el mensaje (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    /**
     * Lista las tareas asignadas directamente al usuario, usando sus propias
     * credenciales contra la API de proceso.
     */
    public List<Tarea> listarTareasAsignadas(String usuario, String clave) {
        return listarTareas("/runtime/tasks?assignee={usuario}", usuario, clave, "asignadas");
    }

    /**
     * Lista las tareas de las que el usuario es candidato (directo o por grupo).
     * Incluye tareas sin asignar y tareas que puede tomar su grupo.
     */
    public List<Tarea> listarTareasCandidatas(String usuario, String clave) {
        return listarTareas("/runtime/tasks?candidateUser={usuario}", usuario, clave, "del grupo");
    }

    /**
     * Obtiene una tarea puntual con las credenciales del empleado logueado.
     */
    public Tarea obtenerTarea(String taskId, String usuario, String clave) {
        try {
            Tarea tarea = processClient.get()
                    .uri("/runtime/tasks/{id}", taskId)
                    .headers(headers -> headers.setBasicAuth(usuario, clave))
                    .retrieve()
                    .body(Tarea.class);
            if (tarea == null) {
                throw new FlowableUnavailableException("Flowable no devolvio la tarea solicitada.");
            }
            return tarea;
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la consulta de la tarea (HTTP "
                            + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    /**
     * Reclama una tarea de grupo para el usuario logueado.
     */
    public void reclamarTarea(String taskId, String usuario, String clave) {
        ejecutarAccionTarea(taskId, new TaskActionRequest("claim", usuario, null),
                usuario, clave, "reclamar");
    }

    /**
     * Completa una tarea enviando las variables indicadas (puede ser vacio).
     */
    public void completarTarea(String taskId, List<VariableRequest> variables,
            String usuario, String clave) {
        ejecutarAccionTarea(taskId, new TaskActionRequest("complete", null, variables),
                usuario, clave, "completar");
    }

    private List<Tarea> listarTareas(String uri, String usuario, String clave, String descripcion) {
        try {
            FlowableListResponse<Tarea> response = processClient.get()
                    .uri(uri, usuario)
                    .headers(headers -> headers.setBasicAuth(usuario, clave))
                    .retrieve()
                    .body(new ParameterizedTypeReference<FlowableListResponse<Tarea>>() {
                    });
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la consulta de tareas " + descripcion + " (HTTP "
                            + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            throw new FlowableUnavailableException("No se pudo conectar con Flowable.", e);
        }
    }

    private void ejecutarAccionTarea(String taskId, TaskActionRequest request,
            String usuario, String clave, String accion) {
        try {
            processClient.post()
                    .uri("/runtime/tasks/{id}", taskId)
                    .headers(headers -> headers.setBasicAuth(usuario, clave))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new FlowableUnavailableException(
                    "Flowable rechazo la accion de " + accion + " la tarea (HTTP "
                            + e.getStatusCode().value() + ").", e);
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
