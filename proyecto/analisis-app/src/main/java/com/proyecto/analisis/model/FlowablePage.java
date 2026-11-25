package com.proyecto.analisis.model;

import java.util.ArrayList;
import java.util.List;

/** Forma comun de las respuestas paginadas de la API REST de Flowable. */
public class FlowablePage<T> {

	private List<T> data = new ArrayList<>();
	private long total;

	public List<T> getData() {
		return data;
	}

	public void setData(List<T> data) {
		this.data = data == null ? new ArrayList<>() : data;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}
}
