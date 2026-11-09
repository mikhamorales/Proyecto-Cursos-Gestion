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
- Credenciales correctas (HTTP 200): redirige a `/empleados`, que muestra el usuario y un botón para cerrar sesión.
- Credenciales incorrectas (HTTP 401/403): muestra "Usuario o contraseña incorrectos" en el panel.
- Flowable apagado: muestra un mensaje amigable, sin stacktrace.

La sesión guarda `usuario` y `password` para autenticar las próximas llamadas al motor con las credenciales del empleado logueado. La contraseña **no** se registra en logs.

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
    │   │   ├── controller/        # SolicitudController, LoginController
    │   │   ├── service/           # SolicitudService, AuthService
    │   │   ├── client/            # FlowableRestClient
    │   │   ├── model/             # SolicitudForm, VariableRequest, StartProcessRequest
    │   │   └── config/            # FlowableRestConfig
    │   └── resources/
    │       ├── application.properties
    │       ├── static/
    │       │   ├── css/estilos.css
    │       │   └── js/
    │       └── templates/
    │           ├── index.html
    │           └── empleados.html
    └── test/java/com/proyecto/web/
        └── WebApplicationTests.java
```

Cada paquete tiene un `package-info.java` con su propósito, para que Git conserve la carpeta.

## Problemas comunes

- **Puerto 8081 ocupado**: cambiá `server.port` en `application.properties`.
- **Flowable no responde**: verificá que Docker y el contenedor estén arriba (`docker compose ps`).
- **Credenciales incorrectas**: revisá `flowable.rest.username` / `flowable.rest.password` contra el `docker-compose.yml`.
- **El formulario devuelve error HTTP**: confirmá que el proceso `process` esté desplegado en Flowable (`GET /flowable-ui/process-api/repository/process-definitions?key=process`).
