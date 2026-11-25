package com.proyecto.analisis.model;

import java.util.List;

/** Datos que consume la unica pantalla de la app. */
public class ResumenAnalisis {

	private final List<ResultadoVista> resultados;
	private final List<RamaVista> ramas;
	private final List<DistribucionVista> distribucion;
	private final int totalInstancias;
	private final int instanciasPreparacion;
	private final int totalTareas;

	public ResumenAnalisis(List<ResultadoVista> resultados, List<RamaVista> ramas,
			List<DistribucionVista> distribucion, int totalInstancias,
			int instanciasPreparacion, int totalTareas) {
		this.resultados = resultados;
		this.ramas = ramas;
		this.distribucion = distribucion;
		this.totalInstancias = totalInstancias;
		this.instanciasPreparacion = instanciasPreparacion;
		this.totalTareas = totalTareas;
	}

	public static ResumenAnalisis vacio() {
		return new ResumenAnalisis(List.of(), List.of(), List.of(), 0, 0, 0);
	}

	public List<ResultadoVista> getResultados() {
		return resultados;
	}

	public List<RamaVista> getRamas() {
		return ramas;
	}

	public List<DistribucionVista> getDistribucion() {
		return distribucion;
	}

	public int getTotalInstancias() {
		return totalInstancias;
	}

	public int getInstanciasPreparacion() {
		return instanciasPreparacion;
	}

	public int getTotalTareas() {
		return totalTareas;
	}
}
