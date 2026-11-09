package flowable.listeners;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AutenticacionFlowable {

    public static boolean validarCredenciales(
            String usuario, String password) throws Exception {

        String credenciales = usuario + ":" + password;

        String basicAuth = Base64.getEncoder().encodeToString(
                credenciales.getBytes(StandardCharsets.UTF_8));

        String usuarioCodificado = URLEncoder.encode(
                usuario, StandardCharsets.UTF_8);

        /*String url =
                "http://localhost:8080/flowable-ui/process-api"
                + "/runtime/tasks?assignee="
                + usuarioCodificado
                + "&size=1";
		*/
        
        String url = "http://localhost:8080/flowable-ui/api/idm/users/"+ usuarioCodificado;
        
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + basicAuth)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        switch (response.statusCode()) {
            case 200:
                return true;

            case 401:
                return false;

            case 403:
                throw new IllegalStateException(
                        "El usuario no tiene permiso de acceso a la API REST.");

            default:
                throw new IllegalStateException(
                        "Error al consultar Flowable. HTTP "
                        + response.statusCode());
        }
    }
}