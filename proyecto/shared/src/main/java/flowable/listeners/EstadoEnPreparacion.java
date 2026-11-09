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
        try (Connection c = InstitucionDb.get();
             PreparedStatement ps = c.prepareStatement(SQL)) {
            ps.setString(1, piid);
            if (ps.executeUpdate() == 0) {
                throw new FlowableException(
                    "No hay un curso vinculado a la instancia " + piid + " (curso.process_instance_id)");
            }
        } catch (SQLException e) {
            throw new FlowableException("Error actualizando el estado EN_PREPARACION", e);
        }
    }
}