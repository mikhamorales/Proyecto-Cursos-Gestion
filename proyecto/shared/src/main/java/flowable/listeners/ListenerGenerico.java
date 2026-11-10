package flowable.listeners;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

public class ListenerGenerico implements ExecutionListener, TaskListener {

    // Tareas de usuario: create / assignment / complete
    @Override
    public void notify(DelegateTask task) {
        String t = task.getName();
        switch (task.getEventName()) {
            case "create":
                System.out.println("Tarea '" + t + "' inicia"); break;
            case "assignment":
                System.out.println("Tarea '" + t + "' fue tomada por " + task.getAssignee()); break;
            case "complete":
                System.out.println("Tarea '" + t + "' se completó por " + task.getAssignee()); break;
        }
    }

    // Resto de actividades: start / end
    @Override
    public void notify(DelegateExecution ex) {
        String n = ex.getCurrentFlowElement() != null && ex.getCurrentFlowElement().getName() != null
                ? ex.getCurrentFlowElement().getName() : ex.getCurrentActivityId();
        if ("start".equals(ex.getEventName())) System.out.println("Tarea '" + n + "' inicia");
        if ("end".equals(ex.getEventName()))   System.out.println("Tarea '" + n + "' se completó");
    }
}