package flowable.listeners;

import java.util.List;

import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;

public class AsignarTarea implements TaskListener {

    private static final long serialVersionUID = 1L;

    // ID exacto del grupo que aparece en el BPMN: administracion.
    public static final String GRUPO_ADMINISTRACION = "administracion";

    @Override
    public void notify(DelegateTask task) {

        System.out.println("***** TASK LISTENER AsignarTarea *****");
        System.out.println("Instancia: " + task.getProcessInstanceId());
        System.out.println("Tarea: " + task.getName() + " | ID: " + task.getId());
        System.out.println("Asignacion inicial: " + task.getAssignee());


        // Conservar una asignacion previa definida en el modelo.
        if (task.getAssignee() != null && !task.getAssignee().trim().isEmpty()) {
            System.out.println("Se conserva el usuario asignado.");
            return;
        }

        // Reutilizar el motor que esta ejecutando el listener.
        // No crear otro motor ni otra conexion a la BD dentro de notify().
        ProcessEngineConfigurationImpl configuracion =
                CommandContextUtil.getProcessEngineConfiguration();
        IdentityService identityService = configuracion.getIdentityService();
        TaskService taskService = configuracion.getTaskService();

        List<User> administrativos = identityService.createUserQuery()
                .memberOfGroup(GRUPO_ALUMNOS)
                .orderByUserId().asc().list();

       /* if (administrativos.isEmpty()) {
            throw new IllegalStateException(
                    "No hay usuarios en el grupo con ID: " + GRUPO_ALUMNOS);
        }*/
        
        
        String usuarioElegido = null;
        long menorCantidad = Long.MAX_VALUE;

        for (User administrativo : administrativos) {
            // Cuenta las tareas pendientes asignadas en todo el motor.
            // No cuenta tareas sin asignar en las que el administrativo sea candidato.
            long pendientes = taskService.createTaskQuery()
                    .taskAssignee(administrativo.getId()).count();

            System.out.println("Alumno: " + administrativo.getId()
                    + " | Tareas pendientes asignadas: " + pendientes);

            // Como los administrativos estan ordenados por ID, en caso de empate
            // se mantiene el primero de ese orden.
            if (pendientes < menorCantidad) {
                menorCantidad = pendientes;
                usuarioElegido = administrativo.getId();
            }
        }

        taskService.setAssignee(task.getId(), usuarioElegido);
        //task.setAssignee(usuarioElegido); si usan esto asigna pero la UI de flowable no la muestra para asignar sino para reclamar

        System.out.println("Asignacion final: " + task.getAssignee());
        System.out.println("Tenia cantidad de asignadas: " + menorCantidad);
        System.out.println("*************************************");
    }
}