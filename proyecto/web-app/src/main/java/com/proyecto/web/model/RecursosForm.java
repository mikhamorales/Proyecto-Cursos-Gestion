package com.proyecto.web.model;

/**
 * Recursos seleccionados al completar la tarea "Evaluar recursos".
 * Al menos uno debe estar marcado para que el motor active una rama.
 */
public class RecursosForm {

    private boolean aulaFisica;
    private boolean aulaVirtual;
    private boolean equipamientoEspecial;

    public boolean isAulaFisica() {
        return aulaFisica;
    }

    public void setAulaFisica(boolean aulaFisica) {
        this.aulaFisica = aulaFisica;
    }

    public boolean isAulaVirtual() {
        return aulaVirtual;
    }

    public void setAulaVirtual(boolean aulaVirtual) {
        this.aulaVirtual = aulaVirtual;
    }

    public boolean isEquipamientoEspecial() {
        return equipamientoEspecial;
    }

    public void setEquipamientoEspecial(boolean equipamientoEspecial) {
        this.equipamientoEspecial = equipamientoEspecial;
    }

    public boolean alMenosUnoSeleccionado() {
        return aulaFisica || aulaVirtual || equipamientoEspecial;
    }
}
