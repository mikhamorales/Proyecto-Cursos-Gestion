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
    private final String startMessage;

    public SolicitudService(FlowableRestClient flowableRestClient,
            @Value("${flowable.process-start-message:Solicitud de curso}") String startMessage) {
        this.flowableRestClient = flowableRestClient;
        this.startMessage = startMessage;
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
        StartProcessRequest request = new StartProcessRequest(startMessage, variables);
        return flowableRestClient.startProcess(request);
    }
}
