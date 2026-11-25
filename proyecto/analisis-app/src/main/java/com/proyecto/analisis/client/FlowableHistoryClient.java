package com.proyecto.analisis.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.analisis.config.RestConfig;
import com.proyecto.analisis.model.FlowablePage;
import com.proyecto.analisis.model.HistoricActivityInstance;
import com.proyecto.analisis.model.HistoricProcessInstance;
import com.proyecto.analisis.model.HistoricTaskInstance;

/**
 * Consultas de solo lectura al historial de Flowable por API REST.
 * Es la misma informacion que exporta el script exportar_history.py.
 */
@Component
public class FlowableHistoryClient {

	private static final int TAMANO_PAGINA = 100;
	private static final ParameterizedTypeReference<FlowablePage<Map<String, Object>>> TIPO_PAGINA =
			new ParameterizedTypeReference<FlowablePage<Map<String, Object>>>() { };

	private final RestTemplate rest = new RestTemplate();
	private final ObjectMapper objectMapper;
	private final RestConfig restConfig;
	private final String baseUrl;
	private final String processKey;

	public FlowableHistoryClient(
			ObjectMapper objectMapper,
			RestConfig restConfig,
			@Value("${flowable.rest.base-url}") String baseUrl,
			@Value("${flowable.process-key}") String processKey) {
		this.objectMapper = objectMapper;
		this.restConfig = restConfig;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.processKey = processKey;
	}

	public List<HistoricProcessInstance> instanciasTerminadas() {
		Map<String, String> filtros = new HashMap<>();
		filtros.put("processDefinitionKey", processKey);
		filtros.put("finished", "true");
		return convertir(paginas("history/historic-process-instances", filtros), HistoricProcessInstance.class);
	}

	public List<HistoricActivityInstance> actividades(String instanciaId) {
		Map<String, String> filtros = new HashMap<>();
		filtros.put("processInstanceId", instanciaId);
		return convertir(paginas("history/historic-activity-instances", filtros), HistoricActivityInstance.class);
	}

	public List<HistoricTaskInstance> tareasTerminadas() {
		Map<String, String> filtros = new HashMap<>();
		filtros.put("processDefinitionKey", processKey);
		filtros.put("finished", "true");
		return convertir(paginas("history/historic-task-instances", filtros), HistoricTaskInstance.class);
	}

	/** Ultimo valor de la variable 'resultado' de una instancia terminada. */
	public String resultado(String instanciaId) {
		Map<String, String> filtros = new HashMap<>();
		filtros.put("processInstanceId", instanciaId);
		filtros.put("variableName", "resultado");

		String valor = "";
		for (Map<String, Object> item : paginas("history/historic-variable-instances", filtros)) {
			Object bloque = item.get("variable");
			@SuppressWarnings("unchecked")
			Map<String, Object> variable = bloque instanceof Map
					? (Map<String, Object>) bloque
					: item;
			Object candidato = variable.get("value");
			if (candidato != null && !candidato.toString().isBlank()) {
				valor = candidato.toString();
			}
		}
		return valor;
	}

	private <T> List<T> convertir(List<Map<String, Object>> registros, Class<T> tipo) {
		List<T> resultados = new ArrayList<>();
		for (Map<String, Object> registro : registros) {
			resultados.add(objectMapper.convertValue(registro, tipo));
		}
		return resultados;
	}

	/** Recupera todas las paginas de un recurso, con un limite por llamada. */
	private List<Map<String, Object>> paginas(String recurso, Map<String, String> filtros) {
		List<Map<String, Object>> todos = new ArrayList<>();
		int inicio = 0;

		while (true) {
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).path("/" + recurso);
			filtros.forEach(builder::queryParam);
			builder.queryParam("start", inicio).queryParam("size", TAMANO_PAGINA);

			URI uri = builder.build().toUri();
			HttpEntity<Void> entidad = new HttpEntity<>(restConfig.headers());
			ResponseEntity<FlowablePage<Map<String, Object>>> respuesta =
					rest.exchange(uri, HttpMethod.GET, entidad, TIPO_PAGINA);
			FlowablePage<Map<String, Object>> pagina = respuesta.getBody();

			if (pagina == null || pagina.getData().isEmpty()) {
				break;
			}
			todos.addAll(pagina.getData());
			inicio += pagina.getData().size();

			if ((pagina.getTotal() > 0 && inicio >= pagina.getTotal())
					|| pagina.getData().size() < TAMANO_PAGINA) {
				break;
			}
		}
		return todos;
	}
}
