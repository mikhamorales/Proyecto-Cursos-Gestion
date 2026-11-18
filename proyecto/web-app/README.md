# web-app — Cliente REST de Flowable

Aplicación web del proyecto de gestión de cursos. Es un cliente HTTP de la API REST de Flowable: **no** incluye Flowable, **no** depende del módulo `shared` y **no** ejecuta el motor. El motor corre por fuera, en Docker (`flowable-ui`).

La pantalla principal (`/`) tiene dos paneles:

- **Izquierda:** formulario de solicitud de capacitación para clientes. Al enviarlo se inicia una instancia del proceso en Flowable.
- **Derecha:** login de empleados. Se valida contra Flowable con autenticación básica; si es correcto, redirige a `/empleados`.

## Requisitos previos

- JDK 17
- Maven 3.9+ o el Maven Wrapper incluido (`./mvnw`)
- Docker con Flowable y PostgreSQL corriendo (ver sección siguiente)

## Preparar la base de datos de la institución

La base `institucion` vive en el PostgreSQL del contenedor y **no** la crea Flowable: se carga a mano con el script `proyecto/shared/Database.sql`. Sin esa base, el panel de administración y los listeners del proceso no tienen dónde leer ni escribir.

Requisito: el contenedor `flowable-postgres` tiene que estar arriba (`docker compose ps`).

Desde el directorio `proyecto/shared/`, ejecutá:

```bash
docker exec -i flowable-postgres psql -U flowable -d postgres -v ON_ERROR_STOP=1 < Database.sql
```

Qué hace cada parte:

- `docker exec -i flowable-postgres` corre `psql` **dentro** del contenedor. No hace falta tener `psql` instalado en el host.
- `-U flowable -d postgres` se conecta como el usuario `flowable` a la base `postgres` (la de mantenimiento). El script hace `DROP DATABASE institucion` y `CREATE DATABASE institucion`, por eso hay que arrancar desde `postgres` y no desde `institucion`.
- `-v ON_ERROR_STOP=1` corta al primer error en vez de seguir a ciegas.
- `< Database.sql` le pasa el script por stdin (por eso el `-i`). Si preferís no cambiar de carpeta, usá la ruta completa: `... < /ruta/a/proyecto/shared/Database.sql`.

> **Es destructivo**: el script arranca con `DROP DATABASE IF EXISTS institucion`, así que borra y recrea todo (tablas, datos de prueba y la vista `v_cursos_empresa`). No lo corras si ya tenés datos que querés conservar.

Para verificar que quedó bien:

```bash
docker exec flowable-postgres psql -U flowable -d institucion -c '\dt'
```

Deberías ver las tablas `empresa`, `cuenta_empresa`, `curso` y `propuesta`.

La conexión desde el host (pgAdmin, DBeaver, etc.) es `localhost:5432`, base `institucion`, usuario `flowable`, contraseña `flow123` (los valores del `.env` del compose). Los listeners del proceso, en cambio, usan `jdbc:postgresql://postgres:5432/institucion`: el hostname `postgres` es el nombre del servicio **dentro** de la red de Docker.

## Configuración

`src/main/resources/application.properties`:

| Propiedad | Valor | Descripción |
|---|---|---|
| `spring.application.name` | `web-app` | Nombre de la aplicación |
| `server.port` | `8081` | Puerto de la web (distinto al de Flowable) |
| `flowable.rest.base-url` | `http://localhost:8080/flowable-ui/process-api` | Base de la API de proceso (iniciar instancias, tareas, validación de credenciales) |
| `flowable.rest.ui-base-url` | `http://localhost:8080/flowable-ui/api` | Base de identidad/UI (reservada para usos futuros) |
| `flowable.rest.username` | `admin` | Usuario de la API REST (cuenta de servicio) |
| `flowable.rest.password` | `test` | Clave de la API REST (cuenta de servicio) |
| `flowable.process-start-message` | `Solicitud de curso` | Nombre del mensaje BPMN que dispara el inicio del proceso (evento de inicio por mensaje) |
| `flowable.admin.username` | `admin` | Usuario que ve el panel de simulación de mensajes en `/empleados` |
| `flowable.rol.jefe-administrativo` | `jefeAdmin` | Usuario con rol de jefe administrativo |
| `flowable.rol.administrativos` | `admin1,admin2` | Usuarios con rol administrativo (separados por coma) |
| `flowable.rol.jefe-docente` | `jefeDocente` | Usuario con rol de jefe del equipo docente |
| `flowable.rol.docentes` | `docente1,docente2` | Docentes que pueden desarrollar cursos (separados por coma); el jefe se agrega automáticamente |

Ajustá URL, puerto y credenciales a lo que use el `docker-compose.yml` del equipo.

## Cómo iniciar la app

Con el wrapper:

```bash
cd proyecto/web-app
./mvnw[.cmd] spring-boot:run
```

[] solo si se usa Windows.


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
2. Se hace `POST {flowable.rest.base-url}/runtime/process-instances` con el nombre del mensaje de inicio (`flowable.process-start-message`) y las variables de la tabla, porque el proceso arranca con un **evento de inicio por mensaje**.
3. Se muestra un mensaje de éxito con el id de la instancia, o un mensaje amigable si Flowable no responde.

> No se envía `cantidadActivas`: la calcula el proceso.
>
> Para que el formulario cree instancias, el proceso (y sus definiciones de mensaje) debe estar desplegado en Flowable. Si no lo está, Flowable devuelve un error HTTP y la pantalla lo muestra sin stacktrace.

### Panel derecho: login de empleados

- Ingresá un usuario y contraseña válidos de Flowable (por ejemplo, `admin` / `test`, o los usuarios creados en el IDM según `proyecto/shared/README.md`).
- Se valida haciendo una consulta liviana a la API de proceso (`GET {flowable.rest.base-url}/repository/process-definitions?latest=true&size=1`) con autenticación básica del usuario.
- Credenciales correctas (HTTP 200): redirige según el usuario. El usuario de `flowable.admin.username` va a `/empleados` (panel de simulación); `jefeAdmin`, `admin1` y `admin2` van a `/administrativos`; `jefeDocente`, `docente1` y `docente2` van a `/docentes`; cualquier otro usuario válido va a `/empleados`.
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
>
> La tabla muestra las etiquetas del panel; el `messageName` enviado es siempre el `eventName` real que devuelve Flowable (`Aprobacion`, `Rechazo`, `Modificacion`, `Cancelacion`). Los comandos `curl` equivalentes están en `comandos-mensajes-empresa.txt` (raíz del repo), incluido el disparo de **"Recibir nomina"**, que es una `receiveTask` y se dispara con `{"action":"trigger"}`, no con un mensaje.

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

### Pantallas del equipo docente (`/docentes`)

Disponibles para `jefeDocente` y para los docentes configurados en `flowable.rol.docentes` (`docente1`, `docente2`). El login redirige a `/docentes`.

La pantalla muestra dos secciones:

| Sección | Contenido | Acción |
|---|---|---|
| Mis tareas | Tareas asignadas directamente al usuario (`assignee`) | **Asignar docente** si es "Definir docente..."; **Completar** en el resto |
| Tareas del grupo Docentes | Tareas sin asignar disponibles para el usuario (`candidateUser`) | **Reclamar** |

- El jefe docente ve la tarea "Definir docente..." y al completarla envía la variable `docenteSeleccionado` con el usuario elegido. Puede seleccionarse a sí mismo.
- El docente elegido debe ver "Desarrollar curso" en **Mis tareas** y puede registrar la finalización del dictado.
- "Elaborar informe de resultados" aparece como tarea candidata del jefe y se puede reclamar y completar desde esta pantalla (sin carga de PDF).
- **Requisito pendiente en el BPMN:** la tarea "Desarrollar curso" hoy tiene `flowable:candidateGroups="docentes"`. Para que quede asignada automáticamente al docente elegido, debe cambiarse a `flowable:assignee="${docenteSeleccionado}"`. Sin ese cambio, la tarea aparece en **Tareas del grupo Docentes** y cualquier docente puede reclamarla.

## Cambios en el BPMN (issue3)

Para que las pantallas de administración funcionen hubo que ajustar el modelo. El archivo vive en `proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml` y se despliega a mano al motor (no se empaqueta en el jar de `shared`).

- **Tareas de grupo reclamables**: el `taskListener` `AsignarTarea` (que asigna por round-robin) quedó **solo** en "Confeccionar lista definitiva". Antes estaba en las 6 tareas de administración y las preasignaba, así que nunca aparecían en **Tareas del grupo** para reclamar.
- **Ramas condicionales de recursos**: el gateway inclusivo que sigue a "Evaluar recursos" ahora tiene tres `conditionExpression` (`${aulaFisica == true}`, `${aulaVirtual == true}`, `${equipamientoEspecial == true}`). Así solo se activan las ramas de los recursos marcados; sin condiciones, el gateway activaba las tres siempre.
- El proceso quedó desplegado como `process:4` (`process:4:47a392b3-dfb6-11f1-8fb8-223850ed8974`).

> `AsignarTarea` sigue funcionando en "Confeccionar lista definitiva": al crearse la tarea se asigna a un administrativo.

## Compensación de recursos (arreglado en el BPMN, requiere redespliegue)

Al completar "Evaluar recursos" (y las tareas de reserva de recursos), el motor respondía 500 con `Parent activity for boundary compensation event could not be found`. Causa: los boundary events de compensación estaban cableados a sus handlers ("Liberar aula", "Liberar aula virtual", "Liberar equipamiento") con `<sequenceFlow>` en vez de `<association>`.

Fix **aplicado** en `Institucion_de_capacitacion.bpmn20.xml`: los 3 boundary events ahora usan `<association associationDirection="One">` hacia sus handlers y ya no llevan `cancelActivity="false"`. Las `<association>` van **después de todos los `<sequenceFlow>`** dentro de `<process>`: en el XSD de BPMN 2.0 son `artifact`, no `flowElement`, y si se dejan en medio del flujo Flowable rechaza el despliegue con `cvc-complex-type.2.4.a: ... One of 'artifact' is expected`.

> **Importante:** editar el XML no cambia el motor. Hay que **volver a desplegar** el modelo en Flowable y probar con una **instancia nueva**: cada instancia queda ligada a su `processDefinitionId` original y sigue usando el modelo viejo. En este entorno el modelo corregido quedó desplegado como `process:3` (el `process:2` anterior era una copia sin el fix). El camino de cancelación (`Cancelacion`) también dispara esta compensación y fue verificado.

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
    │   │   ├── controller/        # SolicitudController, LoginController, AdministrativoController, DocenteController
    │   │   ├── service/           # SolicitudService, AuthService, SimulacionService, RolService, TareaService, DocenteService
    │   │   ├── client/            # FlowableRestClient
    │   │   ├── model/             # SolicitudForm, VariableRequest, StartProcessRequest,
    │   │   │                      # EventSubscription, ProcessVariable, MessageEventRequest,
    │   │   │                      # AccionSimulable, InstanciaSimulable, FlowableListResponse,
    │   │   │                      # Tarea, TareaVista, RecursosForm, TaskActionRequest,
    │   │   │                      # DefinirDocenteForm, TareaDocenteVista
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
    │           ├── evaluar-recursos.html
    │           ├── docentes.html
    │           └── asignar-docente.html
    └── test/java/com/proyecto/web/
        └── WebApplicationTests.java
```

Cada paquete tiene un `package-info.java` con su propósito, para que Git conserve la carpeta.

## Problemas comunes

- **Puerto 8081 ocupado**: cambiá `server.port` en `application.properties`.
- **Flowable no responde**: verificá que Docker y el contenedor estén arriba (`docker compose ps`).
- **El formulario devuelve error HTTP**: confirmá que el proceso esté desplegado en Flowable y que acepte el mensaje de inicio configurado (por defecto `Solicitud de curso`). Podés verificar despliegues en `GET /flowable-ui/process-api/repository/process-definitions?key=process`.
- **No puedo iniciar sesión con un usuario esperado** (por ejemplo `admin1`, `admin2`, `docente1`): la app valida contra usuarios existentes en el IDM de Flowable. Verificá que el usuario esté creado en Flowable con la contraseña correcta y los grupos/roles que corresponden al proceso (ver `proyecto/shared/README.md`). Si el usuario no existe en el motor, el login será rechazado aunque esté mencionado en la configuración de la app.
