package com.proyecto.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Resuelve el rol administrativo a partir del nombre de usuario, que es como
 * se identifican los empleados en este proyecto (no se usan grupos de Flowable).
 */
@Component
public class RolService {

    private final String jefeAdministrativo;
    private final List<String> administrativos;

    public RolService(
            @Value("${flowable.rol.jefe-administrativo:jefeAdmin}") String jefeAdministrativo,
            @Value("${flowable.rol.administrativos:admin1,admin2}") String administrativos) {
        this.jefeAdministrativo = jefeAdministrativo;
        this.administrativos = Arrays.stream(administrativos.split(","))
                .map(String::trim)
                .filter(nombre -> !nombre.isEmpty())
                .toList();
    }

    public boolean esJefeAdministrativo(String usuario) {
        return usuario != null && usuario.equals(jefeAdministrativo);
    }

    public boolean esAdministrativo(String usuario) {
        if (usuario == null) {
            return false;
        }
        return esJefeAdministrativo(usuario) || administrativos.contains(usuario);
    }
}
