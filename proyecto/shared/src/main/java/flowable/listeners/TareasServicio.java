package flowable.listeners;

public class TareaRealizada implements JavaDelegate {

    private static final long serialVersionUID = 1L;

    @Override
    public void execute(DelegateExecution execution) {
        String nombre = execution.getCurrentFlowElement() != null
                && execution.getCurrentFlowElement().getName() != null
                        ? execution.getCurrentFlowElement().getName()
                        : execution.getCurrentActivityId();

        System.out.println("Tarea '" + nombre + "' realizada");
    }
}