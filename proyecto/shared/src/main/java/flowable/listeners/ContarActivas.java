package flowable.listeners;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * Execution Listener (evento "start") de la decision task "Evaluar viabilidad".
 * Cuenta los cursos en EN_PREPARACION (sin contar el de esta instancia) y deja el
 * resultado en la variable "cursosEnPreparacion", que es la entrada de la tabla DMN.
 */
public class ContarActivas implements ExecutionListener {

    // Para contar todos los cursos "en ejecución", cambiar por:
    // estado IN ('EN_PREPARACION','CONFIRMADO','EN_DESARROLLO','EN_INFORME')
    private static final String SQL =
            "SELECT COUNT(*) FROM curso "
          + "WHERE estado = 'EN_PREPARACION' "
          + "AND process_instance_id IS DISTINCT FROM ?";

    @Override
    public void notify(DelegateExecution execution) {
        try (Connection c = InstitucionDb.get();
             PreparedStatement ps = c.prepareStatement(SQL)) {
            ps.setString(1, execution.getProcessInstanceId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                execution.setVariable("cursosEnPreparacion", rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new FlowableException("Error contando cursos en preparación", e);
        }
    }
}