package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.ProcessVariable;
import com.proyecto.web.model.Tarea;
import com.proyecto.web.model.TareaDocenteVista;
import com.proyecto.web.model.TareaVista;
import com.proyecto.web.model.VariableRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica de las tareas del equipo docente. Reutiliza el listado de tareas de
 * {@link TareaService} y lo completa con datos del curso (empresa y tematica).
 */
@Service
public class DocenteService {

    private final TareaService tareaService;
    private final RolService rolService;
    private final FlowableRestClient flowableRestClient;

    public DocenteService(TareaService tareaService, RolService rolService,
            FlowableRestClient flowableRestClient) {
        this.tareaService = tareaService;
        this.rolService = rolService;
        this.flowableRestClient = flowableRestClient;
    }

    public List<TareaDocenteVista> listarTareasDocente(String usuario, String clave) {
        Map<String, Map<String, Object>> variablesPorInstancia = new HashMap<>();
        List<TareaDocenteVista> tareas = new ArrayList<>();

        for (TareaVista tarea : tareaService.listarTareas(usuario, clave)) {
            Map<String, Object> variables = variablesPorInstancia.computeIfAbsent(
                    tarea.processInstanceId(), this::variablesDe);
            tareas.add(new TareaDocenteVista(
                    tarea.id(),
                    tarea.nombre(),
                    tarea.processInstanceId(),
                    texto(variables.get("empresa")),
                    texto(variables.get("tematica")),
                    tarea.propia(),
                    tarea.reclamable(),
                    esDefinirDocente(tarea.nombre()),
                    TareaService.TAREA_DESARROLLAR_CURSO.equals(tarea.nombre())));
        }
        return List.copyOf(tareas);
    }

    public List<String> listarDocentes() {
        return rolService.listarDocentes();
    }

    /**
     * Completa la tarea "Definir docente" guardando al docente elegido en la
     * variable {@code docenteSeleccionado}.
     */
    public void definirDocente(String taskId, String usuario, String clave, String docente) {
        if (docente == null || !rolService.esDocente(docente)) {
            throw new IllegalArgumentException(
                    "El docente seleccionado no pertenece al equipo docente.");
        }

        Tarea tarea = tareaService.obtener(taskId, usuario, clave);
        if (!esDefinirDocente(tarea.name())) {
            throw new IllegalArgumentException(
                    "La tarea no corresponde a la definicion de docente.");
        }

        tareaService.completar(taskId, usuario, clave,
                List.of(new VariableRequest("docenteSeleccionado", docente)));
    }

    private Map<String, Object> variablesDe(String processInstanceId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (processInstanceId == null) {
            return variables;
        }
        for (ProcessVariable variable : flowableRestClient.getProcessInstanceVariables(processInstanceId)) {
            variables.put(variable.name(), variable.value());
        }
        return variables;
    }

    /**
     * En el modelo actual la tarea se llama "Definir docente que desarrola el
     * curso"; se compara por prefijo para que siga funcionando si se corrige el
     * texto en el BPMN.
     */
    public static boolean esDefinirDocente(String nombre) {
        return nombre != null && nombre.startsWith("Definir docente");
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
