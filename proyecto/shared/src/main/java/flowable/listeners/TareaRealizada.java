package flowable.listeners;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

/**
 * Implementacion generica para cualquier Service Task del BPMN.
 *
 * No hace logica de negocio: solo deja constancia en consola de que la
 * actividad se ejecuto, usando el nombre que la tarea tiene en el modelo.
 *
 * Uso en el BPMN (atributo flowable:class):
 *
 *   <serviceTask id="tarea1" name="Reservar aula"
 *                flowable:class="flowable.listeners.TareaRealizada" />
 *
 * El mensaje que imprime es: "Tarea 'Reservar aula' realizada".
 * Si la tarea no tiene nombre en el modelo, usa el id de la actividad.
 */
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
