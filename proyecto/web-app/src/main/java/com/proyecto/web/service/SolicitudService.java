package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import com.proyecto.web.model.SolicitudForm;
import com.proyecto.web.model.StartProcessRequest;
import com.proyecto.web.model.VariableRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolicitudService {

    private final FlowableRestClient flowableRestClient;
    private final String processDefinitionKey;

    public SolicitudService(FlowableRestClient flowableRestClient,
            @Value("${flowable.process-definition-key:process}") String processDefinitionKey) {
        this.flowableRestClient = flowableRestClient;
        this.processDefinitionKey = processDefinitionKey;
    }

    public String registrarSolicitud(SolicitudForm form) {
        List<VariableRequest> variables = List.of(
                new VariableRequest("empresa", form.getEmpresa()),
                new VariableRequest("contactoNombre", form.getContactoNombre()),
                new VariableRequest("contactoEmail", form.getContactoEmail()),
                new VariableRequest("contactoTelefono", form.getContactoTelefono()),
                new VariableRequest("tematica", form.getTematica()),
                new VariableRequest("descripcion", form.getDescripcion()),
                new VariableRequest("cantidadParticipantes", form.getCantidadParticipantes()),
                new VariableRequest("fechasPreferidas", form.getFechasPreferidas()));
        StartProcessRequest request = new StartProcessRequest(processDefinitionKey, variables);
        return flowableRestClient.startProcess(request);
    }
}
