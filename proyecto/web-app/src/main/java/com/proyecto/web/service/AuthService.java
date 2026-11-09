package com.proyecto.web.service;

import com.proyecto.web.client.FlowableRestClient;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final FlowableRestClient flowableRestClient;

    public AuthService(FlowableRestClient flowableRestClient) {
        this.flowableRestClient = flowableRestClient;
    }

    public boolean validarCredenciales(String usuario, String password) {
        return flowableRestClient.validateCredentials(usuario, password);
    }
}
