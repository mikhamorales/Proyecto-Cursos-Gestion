package com.proyecto.docente.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Cliente de la API REST (process-api) del motor Flowable del docker.
 * Reemplaza al ProcessEngine embebido: la app lee y escribe tareas por HTTP,
 * sin abrir conexion JDBC contra Postgres.
 */
@Component
public class FlowableRestClient {

	private static final int TAMANO_PAGINA = 100;
	private static final ParameterizedTypeReference<Map<String, Object>> TIPO_MAPA =
			new ParameterizedTypeReference<Map<String, Object>>() { };

	private final RestTemplate rest = new RestTemplate();
	private final String baseUrl;
	private final String usuarioServicio;
	private final String passwordServicio;

	public FlowableRestClient(
			@Value("${flowable.rest.base-url}") String baseUrl,
			@Value("${flowable.rest.username}") String usuarioServicio,
			@Value("${flowable.rest.password}") String passwordServicio) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.usuarioServicio = usuarioServicio;
		this.passwordServicio = passwordServicio;
	}

	/**
	 * Valida las credenciales de un docente haciendo una llamada minima con su
	 * propio usuario. Un password incorrecto da 401/403.
	 */
	public boolean credencialesValidas(String usuario, String password) {
		if (usuario == null || usuario.isBlank() || password == null || password.isEmpty()) {
			return false;
		}
		try {
			URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
					.path("/identity/users/{usuario}")
					.buildAndExpand(usuario)
					.toUri();
			rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(cabeceras(usuario, password)), TIPO_MAPA);
			return true;
		}
		catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
			return false;
		}
		catch (RestClientException ex) {
			throw new FlowableRestException("No se pudo conectar con la API REST de Flowable.", ex);
		}
	}

	/** IDs de los grupos IDM a los que pertenece el usuario. */
	public List<String> gruposDe(String usuario) {
		URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
				.path("/identity/groups")
				.queryParam("member", usuario)
				.queryParam("size", TAMANO_PAGINA)
				.build()
				.toUri();
		Map<String, Object> pagina = get(uri);
		List<String> grupos = new ArrayList<>();
		for (Map<String, Object> grupo : datos(pagina)) {
			Object id = grupo.get("id");
			if (id != null) {
				grupos.add(id.toString());
			}
		}
		return grupos;
	}

	/** Tareas activas que cumplen los filtros indicados, con sus variables. */
	public List<Map<String, Object>> tareas(Consumer<UriComponentsBuilder> filtros) {
		List<Map<String, Object>> todas = new ArrayList<>();
		int inicio = 0;
		while (true) {
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
					.path("/runtime/tasks")
					.queryParam("includeProcessVariables", "true")
					.queryParam("sort", "createTime")
					.queryParam("order", "desc")
					.queryParam("start", inicio)
					.queryParam("size", TAMANO_PAGINA);
			filtros.accept(builder);
			List<Map<String, Object>> pagina = datos(get(builder.build().toUri()));
			todas.addAll(pagina);
			if (pagina.size() < TAMANO_PAGINA) {
				break;
			}
			inicio += TAMANO_PAGINA;
		}
		return todas;
	}

	/** Una tarea activa por ID, o null si ya no existe. */
	public Map<String, Object> tarea(String tareaId) {
		URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
				.path("/runtime/tasks/{id}")
				.buildAndExpand(tareaId)
				.toUri();
		try {
			return rest.exchange(uri, HttpMethod.GET, entidadServicio(), TIPO_MAPA).getBody();
		}
		catch (HttpClientErrorException.NotFound ex) {
			return null;
		}
		catch (RestClientException ex) {
			throw new FlowableRestException("No se pudo consultar la tarea en Flowable.", ex);
		}
	}

	public void reclamar(String tareaId, String usuario) {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("action", "claim");
		cuerpo.put("assignee", usuario);
		accion(tareaId, cuerpo, "No se pudo reclamar la tarea en Flowable.");
	}

	public void completar(String tareaId, Map<String, Object> variables) {
		List<Map<String, Object>> listaVariables = new ArrayList<>();
		variables.forEach((nombre, valor) -> {
			Map<String, Object> variable = new HashMap<>();
			variable.put("name", nombre);
			variable.put("value", valor == null ? "" : valor.toString());
			variable.put("type", "string");
			listaVariables.add(variable);
		});
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("action", "complete");
		cuerpo.put("variables", listaVariables);
		accion(tareaId, cuerpo, "No se pudo completar la tarea en Flowable.");
	}

	/** Comprobacion usada al arrancar la aplicacion. */
	public long totalTareasActivas() {
		URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
				.path("/runtime/tasks")
				.queryParam("size", 1)
				.build()
				.toUri();
		Map<String, Object> pagina = get(uri);
		Object total = pagina.get("total");
		return total instanceof Number numero ? numero.longValue() : 0L;
	}

	private void accion(String tareaId, Map<String, Object> cuerpo, String mensaje) {
		URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
				.path("/runtime/tasks/{id}")
				.buildAndExpand(tareaId)
				.toUri();
		try {
			rest.exchange(uri, HttpMethod.POST, new HttpEntity<>(cuerpo, cabecerasServicio()), TIPO_MAPA);
		}
		catch (RestClientException ex) {
			throw new FlowableRestException(mensaje, ex);
		}
	}

	private Map<String, Object> get(URI uri) {
		try {
			Map<String, Object> respuesta = rest.exchange(uri, HttpMethod.GET, entidadServicio(), TIPO_MAPA).getBody();
			return respuesta == null ? Map.of() : respuesta;
		}
		catch (RestClientException ex) {
			throw new FlowableRestException("No se pudo conectar con la API REST de Flowable.", ex);
		}
	}

	private HttpEntity<Map<String, Object>> entidadServicio() {
		return new HttpEntity<>(cabecerasServicio());
	}

	private HttpHeaders cabecerasServicio() {
		return cabeceras(usuarioServicio, passwordServicio);
	}

	private HttpHeaders cabeceras(String usuario, String password) {
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.setBasicAuth(usuario, password);
		cabeceras.setContentType(MediaType.APPLICATION_JSON);
		cabeceras.setAccept(List.of(MediaType.APPLICATION_JSON));
		return cabeceras;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> datos(Map<String, Object> pagina) {
		Object datos = pagina.get("data");
		return datos instanceof List<?> lista ? (List<Map<String, Object>>) lista : List.of();
	}

}
