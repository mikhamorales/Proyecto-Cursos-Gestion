package flowable.listeners;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * Execution Listener (evento "end") de la actividad "Habilitar acceso de la empresa".
 * Pasa el curso vinculado a esta instancia al estado EN_PREPARACION.
 */
public class EstadoEnPreparacion implements ExecutionListener {

    private static final String SQL =
            "UPDATE curso SET estado = 'EN_PREPARACION' WHERE process_instance_id = ?";

    @Override
    public void notify(DelegateExecution execution) {
        String piid = execution.getProcessInstanceId();
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(SQL)) {
            ps.setString(1, piid);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                // La solicitud puede no tener una fila en curso todavia (el formulario
                // web solo inicia la instancia). No se corta el proceso por eso.
                System.err.println("[EstadoEnPreparacion] No hay curso vinculado a la instancia "
                        + piid + " (curso.process_instance_id). No se actualiza el estado.");
            } else {
                System.out.println("[EstadoEnPreparacion] Curso de la instancia "
                        + piid + " -> EN_PREPARACION");
            }
        } catch (SQLException e) {
            throw new FlowableException("Error actualizando el estado EN_PREPARACION", e);
        }
    }
}