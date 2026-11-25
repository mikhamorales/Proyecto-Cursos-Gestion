package com.proyecto.analisis.model;

/** Fila de la tabla de duraciones de las ramas de preparacion. */
public class RamaVista {

	private final String nombre;
	private final int casos;
	private final String promedioTexto;
	private final double ancho;

	public RamaVista(String nombre, int casos, String promedioTexto, double ancho) {
		this.nombre = nombre;
		this.casos = casos;
		this.promedioTexto = promedioTexto;
		this.ancho = ancho;
	}

	public String getNombre() {
		return nombre;
	}

	public int getCasos() {
		return casos;
	}

	public String getPromedioTexto() {
		return promedioTexto;
	}

	public double getAncho() {
		return ancho;
	}
}
