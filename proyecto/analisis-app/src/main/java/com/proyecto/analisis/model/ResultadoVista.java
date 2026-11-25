package com.proyecto.analisis.model;

/** Fila de la tabla de resultados (y de su barra). */
public class ResultadoVista {

	private final String etiqueta;
	private final int cantidad;
	private final String porcentajeTexto;
	private final double ancho;
	private final boolean total;

	public ResultadoVista(String etiqueta, int cantidad, String porcentajeTexto, double ancho, boolean total) {
		this.etiqueta = etiqueta;
		this.cantidad = cantidad;
		this.porcentajeTexto = porcentajeTexto;
		this.ancho = ancho;
		this.total = total;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public int getCantidad() {
		return cantidad;
	}

	public String getPorcentajeTexto() {
		return porcentajeTexto;
	}

	public double getAncho() {
		return ancho;
	}

	public boolean isTotal() {
		return total;
	}
}
