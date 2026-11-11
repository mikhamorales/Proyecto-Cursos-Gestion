package flowable.listeners;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.flowable.common.engine.api.FlowableException;

/**
 * Acceso centralizado a la BD de la institucion (el esquema que crea Database.sql).
 *
 * Los listeners que tocan la BD piden aca su conexion JDBC en vez de duplicar
 * la configuracion de conexion en cada clase.
 */
public final class Database {

    // --- Ajustar a tu entorno ---
    // Motor y BD corren en el mismo docker-compose (red flowable-net):
    // el hostname del PostgreSQL es el nombre del servicio ("postgres"), NO localhost,
    // porque este codigo corre dentro del contenedor del motor.
    private static final String URL  = "jdbc:postgresql://postgres:5432/institucion";
    private static final String USER = "flowable";
    private static final String PASS = "flow123";

    private Database() {
    }

    /**
     * Abre una conexion nueva a la BD de la institucion.
     * Quien la pide es responsable de cerrarla (try-with-resources).
     */
    public static Connection get() {
        try {
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (SQLException e) {
            throw new FlowableException(
                    "No se pudo conectar a la BD de la institucion (" + URL + ")", e);
        }
    }
}
