package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.Tarea;
import com.proyecto.web.model.TareaVista;
import com.proyecto.web.model.VariableRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combina las tareas propias y las disponibles para el grupo en una sola vista.
 */
@Service
public class TareaService {

    public static final String TAREA_EVALUAR_RECURSOS = "Evaluar recursos";

    private final FlowableRestClient flowableRestClient;

    public TareaService(FlowableRestClient flowableRestClient) {
        this.flowableRestClient = flowableRestClient;
    }

    public List<TareaVista> listarTareas(String usuario, String clave) {
        Map<String, TareaVista> porId = new LinkedHashMap<>();

        for (Tarea tarea : flowableRestClient.listarTareasAsignadas(usuario, clave)) {
            porId.put(tarea.id(), aVista(tarea, true, false));
        }

        for (Tarea tarea : flowableRestClient.listarTareasCandidatas(usuario, clave)) {
            if (porId.containsKey(tarea.id()) || tarea.assignee() != null) {
                continue;
            }
            porId.put(tarea.id(), aVista(tarea, false, true));
        }

        return List.copyOf(porId.values());
    }

    public void reclamar(String taskId, String usuario, String clave) {
        flowableRestClient.reclamarTarea(taskId, usuario, clave);
    }

    public void completar(String taskId, String usuario, String clave,
            List<VariableRequest> variables) {
        flowableRestClient.completarTarea(taskId, variables, usuario, clave);
    }

    public Tarea obtener(String taskId, String usuario, String clave) {
        return flowableRestClient.obtenerTarea(taskId, usuario, clave);
    }

    private TareaVista aVista(Tarea tarea, boolean propia, boolean reclamable) {
        return new TareaVista(
                tarea.id(),
                tarea.name(),
                tarea.processInstanceId(),
                propia,
                reclamable,
                TAREA_EVALUAR_RECURSOS.equals(tarea.name()));
    }
}
