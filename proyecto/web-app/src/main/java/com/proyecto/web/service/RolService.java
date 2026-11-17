package com.proyecto.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resuelve los roles del personal a partir del nombre de usuario, que es como
 * se identifican los empleados en este proyecto (no se usan grupos de Flowable).
 */
@Component
public class RolService {

    private final String jefeAdministrativo;
    private final List<String> administrativos;
    private final String jefeDocente;
    private final List<String> docentes;

    public RolService(
            @Value("${flowable.rol.jefe-administrativo:jefeAdmin}") String jefeAdministrativo,
            @Value("${flowable.rol.administrativos:admin1,admin2}") String administrativos,
            @Value("${flowable.rol.jefe-docente:jefeDocente}") String jefeDocente,
            @Value("${flowable.rol.docentes:docente1,docente2}") String docentes) {
        this.jefeAdministrativo = jefeAdministrativo;
        this.administrativos = separar(administrativos);
        this.jefeDocente = jefeDocente;
        this.docentes = separar(docentes);
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

    public boolean esJefeDocente(String usuario) {
        return usuario != null && usuario.equals(jefeDocente);
    }

    /**
     * Un docente puede ser el jefe del equipo o cualquiera de sus integrantes.
     */
    public boolean esDocente(String usuario) {
        if (usuario == null) {
            return false;
        }
        return esJefeDocente(usuario) || docentes.contains(usuario);
    }

    /**
     * Devuelve los docentes que pueden ser seleccionados, incluido el jefe.
     */
    public List<String> listarDocentes() {
        return Stream.concat(Stream.of(jefeDocente), docentes.stream())
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .toList();
    }

    private static List<String> separar(String nombres) {
        return Arrays.stream(nombres.split(","))
                .map(String::trim)
                .filter(nombre -> !nombre.isEmpty())
                .toList();
    }
}
