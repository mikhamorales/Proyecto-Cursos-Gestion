package flowable.listeners;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * Execution Listener (apartado 3.4).
 *
 * Se ejecuta UNA sola vez, cuando termina la instancia del proceso.
 * Lee la variable de proceso "resultado" (que se fija en cada camino de cierre)
 * y actualiza el estado del curso en la base de datos de la institución.
 *
 * Valores posibles de "resultado" (coinciden con el CHECK de la tabla curso):
 *   FINALIZADO | CANCELADO | RECHAZADO | CERRADO_POR_FALTA_DE_RESPUESTA | NO_VIABLE
 */
public class ActualizarEstado implements ExecutionListener {

    private static final long serialVersionUID = 1L;

    // --- Ajustar a tu entorno ---
    private static final String URL  = "jdbc:postgresql://localhost:5432/institucion";
    private static final String USER = "postgres";
    private static final String PASS = "postgres";

    @Override
    public void notify(DelegateExecution execution) {
        String resultado = (String) execution.getVariable("resultado");
        String processInstanceId = execution.getProcessInstanceId();

        if (resultado == null) {
            System.err.println("[ActualizarEstadoCurso] La instancia " + processInstanceId
                    + " terminó sin variable 'resultado'. No se actualiza la BD.");
            return;
        }

        String sql = "UPDATE curso SET estado = ?, fecha_cierre = now() "
                   + "WHERE process_instance_id = ?";

        try (Connection con = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, resultado);
            ps.setString(2, processInstanceId);
            int filas = ps.executeUpdate();

            if (filas == 0) {
                System.err.println("[ActualizarEstadoCurso] No hay curso con process_instance_id = "
                        + processInstanceId);
            } else {
                System.out.println("[ActualizarEstadoCurso] Curso de la instancia "
                        + processInstanceId + " -> " + resultado);
            }
        } catch (Exception e) {
            // No relanzamos: un error de BD no debe impedir que la instancia termine.
            System.err.println("[ActualizarEstadoCurso] Error al actualizar la BD: " + e.getMessage());
        }
    }
}