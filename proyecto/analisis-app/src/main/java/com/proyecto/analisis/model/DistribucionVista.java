package com.proyecto.analisis.model;

/** Fila de la tabla de tareas completadas por usuario. */
public class DistribucionVista {

	private final String rol;
	private final String usuario;
	private final int cantidad;
	private final double ancho;
	private final boolean total;

	public DistribucionVista(String rol, String usuario, int cantidad, double ancho, boolean total) {
		this.rol = rol;
		this.usuario = usuario;
		this.cantidad = cantidad;
		this.ancho = ancho;
		this.total = total;
	}

	public String getRol() {
		return rol;
	}

	public String getUsuario() {
		return usuario;
	}

	public int getCantidad() {
		return cantidad;
	}

	public double getAncho() {
		return ancho;
	}

	public boolean isTotal() {
		return total;
	}
}
