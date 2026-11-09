package com.proyecto.web.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datos del formulario de solicitud de capacitacion.
 */
public class SolicitudForm {

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    private String empresa;

    @NotBlank(message = "El nombre de contacto es obligatorio")
    private String contactoNombre;

    @NotBlank(message = "El email de contacto es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String contactoEmail;

    @NotBlank(message = "El telefono de contacto es obligatorio")
    private String contactoTelefono;

    @NotBlank(message = "La tematica es obligatoria")
    private String tematica;

    @NotBlank(message = "La descripcion es obligatoria")
    private String descripcion;

    @NotNull(message = "La cantidad de participantes es obligatoria")
    @Min(value = 1, message = "La cantidad de participantes debe ser mayor a 0")
    private Integer cantidadParticipantes;

    @NotBlank(message = "La descripcion de fechas es obligatoria")
    private String fechasPreferidas;

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public String getContactoNombre() {
        return contactoNombre;
    }

    public void setContactoNombre(String contactoNombre) {
        this.contactoNombre = contactoNombre;
    }

    public String getContactoEmail() {
        return contactoEmail;
    }

    public void setContactoEmail(String contactoEmail) {
        this.contactoEmail = contactoEmail;
    }

    public String getContactoTelefono() {
        return contactoTelefono;
    }

    public void setContactoTelefono(String contactoTelefono) {
        this.contactoTelefono = contactoTelefono;
    }

    public String getTematica() {
        return tematica;
    }

    public void setTematica(String tematica) {
        this.tematica = tematica;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getCantidadParticipantes() {
        return cantidadParticipantes;
    }

    public void setCantidadParticipantes(Integer cantidadParticipantes) {
        this.cantidadParticipantes = cantidadParticipantes;
    }

    public String getFechasPreferidas() {
        return fechasPreferidas;
    }

    public void setFechasPreferidas(String fechasPreferidas) {
        this.fechasPreferidas = fechasPreferidas;
    }
}
