# web-app — Cliente REST de Flowable

Aplicación web del proyecto de gestión de cursos. Es un cliente HTTP de la API REST de Flowable: **no** incluye Flowable, **no** depende del módulo `shared` y **no** ejecuta el motor. El motor corre por fuera, en Docker (`flowable-ui`).

La pantalla principal (`/`) tiene dos paneles:

- **Izquierda:** formulario de solicitud de capacitación para clientes. Al enviarlo se inicia una instancia del proceso en Flowable.
- **Derecha:** login de empleados. Se valida contra Flowable con autenticación básica; si es correcto, redirige a `/empleados`.

## Requisitos previos

- JDK 17
- Maven 3.9+ o el Maven Wrapper incluido (`./mvnw`)
- Docker con Flowable y PostgreSQL corriendo (ver sección siguiente)

## Configuración

`src/main/resources/application.properties`:

| Propiedad | Valor | Descripción |
|---|---|---|
| `spring.application.name` | `web-app` | Nombre de la aplicación |
| `server.port` | `8081` | Puerto de la web (distinto al de Flowable) |
| `flowable.rest.base-url` | `http://localhost:8080/flowable-ui/process-api` | Base de la API de proceso (iniciar instancias, tareas) |
| `flowable.rest.ui-base-url` | `http://localhost:8080/flowable-ui/api` | Base de identidad/UI (validar credenciales) |
| `flowable.rest.username` | `admin` | Usuario de la API REST |
| `flowable.rest.password` | `test` | Clave de la API REST |
| `flowable.process-definition-key` | `process` | Clave del proceso BPMN que inicia el formulario |
| `flowable.admin.username` | `admin` | Usuario que ve el panel de simulación de mensajes en `/empleados` |
| `flowable.rol.jefe-administrativo` | `jefeAdmin` | Usuario con rol de jefe administrativo |
| `flowable.rol.administrativos` | `admin1,admin2` | Usuarios con rol administrativo (separados por coma) |

Ajustá URL, puerto y credenciales a lo que use el `docker-compose.yml` del equipo.

## Cómo iniciar la app

Con el wrapper:

```bash
cd proyecto/web-app
./mvnw spring-boot:run
```

Alternativa con jar:

```bash
./mvnw clean package
java -jar target/web-app-0.0.1-SNAPSHOT.jar
```

## Cómo probar la pantalla

Abrí http://localhost:8081.

### Panel izquierdo: solicitud de capacitación

Campos obligatorios:

| Grupo | Campo | Variable de proceso |
|---|---|---|
| Empresa | nombre | `empresa` |
| Contacto | nombre | `contactoNombre` |
| Contacto | email | `contactoEmail` |
| Contacto | teléfono | `contactoTelefono` |
| Capacitación | temática | `tematica` |
| Capacitación | descripción | `descripcion` |
| Participantes | cantidad (entero > 0) | `cantidadParticipantes` |
| Fechas | descripción de fechas preferidas | `fechasPreferidas` |

Al enviar:

1. Se validan los campos con `@Valid`; si hay errores, se muestran junto a cada campo y **no** se llama a Flowable.
2. Se hace `POST {flowable.rest.base-url}/runtime/process-instances` con `processDefinitionKey` y las variables de la tabla.
3. Se muestra un mensaje de éxito con el id de la instancia, o un mensaje amigable si Flowable no responde.

> No se envía `cantidadActivas`: la calcula el proceso.
>
> Para que el formulario cree instancias, el proceso `process` debe estar desplegado en Flowable. Si todavía no lo está, Flowable devuelve un error HTTP y la pantalla lo muestra sin stacktrace.

### Panel derecho: login de empleados

- Ingresá un usuario y contraseña válidos de Flowable (por ejemplo, `admin` / `test`).
- Se valida con `GET {flowable.rest.ui-base-url}/idm/users/{usuario}` usando autenticación básica.
- Credenciales correctas (HTTP 200): redirige según el usuario. El usuario de `flowable.admin.username` va a `/empleados` (panel de simulación); `jefeAdmin`, `admin1` y `admin2` van a `/administrativos`; cualquier otro usuario va a `/empleados`.
- Credenciales incorrectas (HTTP 401/403): muestra "Usuario o contraseña incorrectos" en el panel.
- Flowable apagado: muestra un mensaje amigable, sin stacktrace.

La sesión guarda `usuario` y `password` para autenticar las próximas llamadas al motor con las credenciales del empleado logueado. La contraseña **no** se registra en logs.

### Panel de simulación de mensajes (solo admin)

Si el usuario logueado coincide con `flowable.admin.username` (por defecto `admin`), `/empleados` muestra además una tabla con las instancias del proceso que están esperando un mensaje. Cada fila agrupa las suscripciones por `processInstanceId` y muestra un botón por mensaje disponible:

| Mensaje | Botón | Efecto |
|---|---|---|
| `MModificacion` | Enviar modificaciones | Vuelve a la tarea "Revisar propuesta" |
| `MRechazo` | Rechazar | Deriva a "Archivar solicitud" y termina en `RECHAZADO` |
| `MAceptacion` | Aceptar | Entra al subproceso "Preparar curso" |
| `MCancelacion` | Cancelar curso | Compensa recursos, notifica el cierre y termina en `CANCELADO` |

El envío hace `PUT /runtime/executions/{executionId}` con `{"action":"messageEventReceived","messageName":"..."}`. Antes de enviar se valida que la suscripción siga existiendo; si ya no está, se muestra "La instancia ya no espera ese mensaje.".

> El panel necesita que el BPMN (con los `messageEventDefinition`) y el DMN `viabilidad` estén desplegados en el motor. Los archivos viven en `proyecto/shared/src/resources/` y se despliegan a mano; no se empaquetan en el jar de `shared`.

### Pantallas de administración (`/administrativos`)

Disponibles para los usuarios con rol administrativo: `jefeAdmin` (jefe administrativo) y `admin1` / `admin2` (administrativos). El rol se determina por el nombre de usuario; no se usan grupos de Flowable. La sesión guarda el usuario y la contraseña, y **todas** las llamadas de tareas usan esas credenciales (no la cuenta de servicio `admin`).

La pantalla muestra dos secciones:

| Sección | Contenido | Botón |
|---|---|---|
| Mis tareas | Tareas asignadas directamente al usuario (`assignee`) | **Completar** |
| Tareas del grupo | Tareas sin asignar disponibles para el grupo (`candidateUser`) | **Reclamar** |

- **Reclamar** hace `POST /runtime/tasks/{id}` con `{"action":"claim","assignee":"<usuario>"}`. Solo aparece en tareas del grupo.
- **Completar** hace `POST /runtime/tasks/{id}` con `{"action":"complete","variables":[...]}`. En las tareas comunes no envía variables.
- Si Flowable no responde, se muestra un mensaje amigable y se vuelve al listado, sin stacktrace.

Tareas administrativas soportadas: "Archivar solicitud" (jefe administrativo), "Confeccionar lista definitiva", "Evaluar recursos" y "Enviar informe a la empresa y archivar expediente" (jefe administrativo). Las tareas asignadas directamente no se pueden reclamar.

#### Evaluar recursos

Al presionar **Completar** en la tarea "Evaluar recursos", se abre `/administrativos/recursos/{taskId}`, con tres casillas: **Aula física**, **Aula virtual** y **Equipamiento especial**.

- El botón **Completar** queda deshabilitado hasta marcar al menos una opción (JavaScript en la página).
- El servidor vuelve a validar; si no hay ninguna marcada, muestra "Selecciona al menos un recurso." y no llama al motor.
- Al completar, envía las variables booleanas `aulaFisica`, `aulaVirtual` y `equipamientoEspecial`. El gateway inclusivo del proceso activa las ramas correspondientes.

#### Cómo probarlo

1. Con el motor y el proceso desplegados, iniciá sesión con `jefeAdmin` / `jefeAdmin`, `admin1` / `admin1` o `admin2` / `admin2` (usuarios de prueba del motor).
2. El login redirige a `/administrativos`. La sección **Tareas del grupo** debe mostrar las tareas sin asignar; **Mis tareas**, las asignadas al usuario.
3. Reclamá una tarea de grupo y verificá que se mueva a **Mis tareas** (el estado también se refleja en la UI de Flowable).
4. Completá una tarea común y confirmá que desaparece. Para "Evaluar recursos", comprobá que el botón arranca deshabilitado y que solo se habilita al marcar una opción.
5. Cerrá sesión con el botón **Cerrar sesión** (`POST /logout`).

> Las tareas "Solicitar aula virtual", "Contratar equipamiento", "Liberar equipamiento" y "Elaborar informe de resultados" se realizan desde la UI de Flowable; este sitio no las implementa. "Enviar informe a la empresa y archivar expediente" por ahora solo permite completar la tarea (sin contenido de informe).

## Cambios en el BPMN (issue3)

Para que las pantallas de administración funcionen hubo que ajustar el modelo. El archivo vive en `proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml` y se despliega a mano al motor (no se empaqueta en el jar de `shared`).

- **Tareas de grupo reclamables**: el `taskListener` `AsignarTarea` (que asigna por round-robin) quedó **solo** en "Confeccionar lista definitiva". Antes estaba en las 6 tareas de administración y las preasignaba, así que nunca aparecían en **Tareas del grupo** para reclamar.
- **Ramas condicionales de recursos**: el gateway inclusivo que sigue a "Evaluar recursos" ahora tiene tres `conditionExpression` (`${aulaFisica == true}`, `${aulaVirtual == true}`, `${equipamientoEspecial == true}`). Así solo se activan las ramas de los recursos marcados; sin condiciones, el gateway activaba las tres siempre.
- El proceso quedó desplegado como `process:4` (`process:4:47a392b3-dfb6-11f1-8fb8-223850ed8974`).

> `AsignarTarea` sigue funcionando en "Confeccionar lista definitiva": al crearse la tarea se asigna a un administrativo.

## Pendiente conocido (futura issue)

Al completar cualquier tarea que tenga un **boundary event de compensación** ("Reservar aula", "Solicitar aula virtual", "Contratar equipamiento"), el motor responde 500 con `Parent activity for boundary compensation event could not be found`. Causa: esos boundary events están cableados a sus handlers ("Liberar aula", "Liberar aula virtual", "Liberar equipamiento") con `<sequenceFlow>` en vez de `<association>`, y Flowable resuelve el handler buscando **associations** (no hay ninguna en el archivo). Es **preexistente** —la versión `v3` falla igual— y quedó fuera del alcance de issue3.

Fix probado pero **no aplicado**: agregar 3 `<association associationDirection="One" sourceRef="<boundaryEvent>" targetRef="<handler>">`, borrar los 3 `<sequenceFlow>` que hoy van del boundary al handler y quitarles `cancelActivity="false"`. Con eso "Solicitar aula virtual" completa 200 y el proceso avanza. Conviene revisar también el camino de cancelación (`MCancelacion`), que dispara compensación.

## Estructura de carpetas

```
web-app/
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/proyecto/web/
    │   │   ├── WebApplication.java
    │   │   ├── controller/        # SolicitudController, LoginController, AdministrativoController
    │   │   ├── service/           # SolicitudService, AuthService, SimulacionService, RolService, TareaService
    │   │   ├── client/            # FlowableRestClient
    │   │   ├── model/             # SolicitudForm, VariableRequest, StartProcessRequest,
    │   │   │                      # EventSubscription, ProcessVariable, MessageEventRequest,
    │   │   │                      # AccionSimulable, InstanciaSimulable, FlowableListResponse,
    │   │   │                      # Tarea, TareaVista, RecursosForm, TaskActionRequest
    │   │   └── config/            # FlowableRestConfig
    │   └── resources/
    │       ├── application.properties
    │       ├── static/
    │       │   ├── css/estilos.css
    │       │   └── js/
    │       └── templates/
    │           ├── index.html
    │           ├── empleados.html
    │           ├── administrativos.html
    │           └── evaluar-recursos.html
    └── test/java/com/proyecto/web/
        └── WebApplicationTests.java
```

Cada paquete tiene un `package-info.java` con su propósito, para que Git conserve la carpeta.

## Problemas comunes

- **Puerto 8081 ocupado**: cambiá `server.port` en `application.properties`.
- **Flowable no responde**: verificá que Docker y el contenedor estén arriba (`docker compose ps`).
- **El formulario devuelve error HTTP**: confirmá que el proceso `process` esté desplegado en Flowable (`GET /flowable-ui/process-api/repository/process-definitions?key=process`).
