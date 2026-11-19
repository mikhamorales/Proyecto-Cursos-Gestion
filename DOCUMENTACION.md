# Documentación del proyecto — Gestión de cursos con Flowable

> Manual de puesta en marcha + mapa rápido del repositorio.
> Está pensado para que cualquier persona, en Windows, Linux, macOS o WSL, pueda levantar el entorno completo sin reconstruir la historia de los README. Tono mitad formal, mitad práctico.

---

## 1. Explicación general

Es un sistema para gestionar solicitudes de capacitación de empresas. El corazón es un proceso BPMN (`Institucion de capacitacion`) que corre en **Flowable 6.8.0** dentro de Docker, con **PostgreSQL** como base del motor y una base de datos aparte (`institucion`) con los datos de negocio: empresas, cursos, propuestas y cuentas de acceso.

Alrededor del motor hay **tres aplicaciones web Spring Boot** para tres perfiles distintos:

| App | Puerto | Cómo habla con Flowable | Para quién es |
| --- | --- | --- | --- |
| `web-app` | 8081 | Cliente **REST** (`/flowable-ui/process-api`) | Clientes (formulario de solicitud) y empleados (simulación de mensajes y tareas) |
| `admin-app` | 8082 | **API Java embebida**, conecta por JDBC a la base `flowable` | Parte administrativa ("Preparar materiales") |
| `docente-app` | 8083 | **API Java embebida**, conecta por JDBC a la base `flowable` | Equipo docente (definir docente, desarrollar curso, informe) |

Dos matices importantes:

- Hay **dos bases de datos**: `flowable` (tablas `ACT_*` del motor) e `institucion` (datos del dominio). La primera la crea el compose; la segunda se carga a mano con `Database.sql`.
- `web-app` **no** depende del módulo `shared` y no ejecuta listeners. `admin-app` y `docente-app` sí: sus motores embebidos resuelven las clases `flowable.listeners.*` por nombre, por eso necesitan el JAR de `shared` instalado en el repositorio local de Maven.

---

## 2. Requisitos previos

| Herramienta | Versión / detalle |
| --- | --- |
| Docker + Docker Compose | Compose v2 o superior (el comando es `docker compose`). Docker descarga las imágenes; **no hace falta bajar Flowable a mano**. |
| JDK | 17 o superior (los módulos compilan con target 11 o 17; funciona bien con JDK 17 o 21). |
| Maven | 3.9+ o los wrappers `./mvnw` / `mvnw.cmd` incluidos en cada app (primera build con red). |
| `psql` | No hace falta en el host: se ejecuta dentro del contenedor con `docker exec`. |
| Navegador | Para Flowable UI y las tres apps. |
| PowerShell (solo para el script E2E) | Viene con Windows; en Linux/macOS se puede instalar o, en su defecto, usar los comandos `curl` del recetario. |

Valores por defecto de credenciales (definidos en `.env` y asumidos por la configuración y los listeners):

```dotenv
POSTGRES_USER=flowable
POSTGRES_PASSWORD=flow123
POSTGRES_DB=flowable
FLOWABLE_ADMIN_USER=admin
FLOWABLE_ADMIN_PASSWORD=test
```

---

## 3. Puesta en marcha rápida

Los comandos funcionan igual en bash, zsh, Git Bash o PowerShell, salvo donde se indica lo contrario. Se asume que estás parado en la raíz del repositorio.

### Paso 0 — Ubicarse en la raíz del repo

```bash
cd Proyecto-Cursos-Gestion
```

Usá siempre `-f docker-compose.yml` en los comandos de Compose: así te asegurás de usar la configuración del repositorio (la que publica el puerto 5432 que necesitan `admin-app` y `docente-app`), aunque tengas otro `docker-compose.yml` en otro directorio.

### Paso 1 — Descargar y levantar Flowable + PostgreSQL

No hay que descargar ningún WAR ni ZIP: el compose usa `flowable/flowable-ui:latest` y `postgres:16-alpine`, y Docker las baja solas la primera vez.

```bash
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml ps
docker compose -f docker-compose.yml logs -f flowable-ui   # opcional: mirar el arranque
```

Qué se levanta:

- `flowable-postgres`: base `flowable`, usuario `flowable`, password `flow123`, volumen persistente `postgres-data`. El nombre del servicio dentro de la red Docker es `postgres`, no `localhost`.
- `flowable-ui`: IDM + Modeler + Task + Admin + Process/REST API en <http://localhost:8080/flowable-ui> (usuario `admin` / password `test`).
- Red interna `flowable-net`. El compose fija un nombre de proyecto para que dos instancias no peleen por el puerto 8080.

> Si ejecutás Compose desde WSL, las rutas de los volúmenes se resuelven relativas al archivo `docker-compose.yml`. Usá siempre rutas relativas (`./proyecto/...` desde la raíz del repo); no escribas letras de unidad ni rutas absolutas del host en `source`, porque Docker dentro de WSL las interpreta mal (`no such file or directory` / `invalid volume specification`).

Para apagar sin borrar datos: `docker compose -f docker-compose.yml down`. Para borrar también el volumen de Postgres: `docker compose -f docker-compose.yml down -v` (destructivo).

### Paso 2 — Crear la base de datos `institucion`

El compose crea la base `flowable`, pero **no** crea `institucion`. Esa base se genera con `proyecto/shared/Database.sql`, que además carga datos de prueba (empresas, cursos, propuestas y la vista `v_cursos_empresa`).

Es **destructivo**: empieza con `DROP DATABASE IF EXISTS institucion`.

En **Linux / macOS / Git Bash / WSL**:

```bash
cd proyecto/shared
docker exec -i flowable-postgres psql -U flowable -d postgres -v ON_ERROR_STOP=1 < Database.sql
docker exec flowable-postgres psql -U flowable -d institucion -c '\dt'
```

En **PowerShell** (no soporta el operador `<` de entrada):

```powershell
cd proyecto/shared
Get-Content Database.sql -Raw | docker exec -i flowable-postgres psql -U flowable -d postgres -v ON_ERROR_STOP=1
docker exec flowable-postgres psql -U flowable -d institucion -c '\dt'
```

Tenés que ver `empresa`, `cuenta_empresa`, `curso` y `propuesta`. El script se conecta a `-d postgres` porque necesita hacer `DROP/CREATE DATABASE`; por eso no arranca directamente contra `institucion`.

### Paso 3 — Compilar el JAR de listeners, copiarlo e instalarlo

Los listeners (`flowable.listeners.*`) viven en el módulo `shared`. El JAR se usa en **dos lugares distintos**:

1. **Dentro del contenedor de Flowable**: se monta en `/app/WEB-INF/lib/` para que el motor ejecute los listeners del BPMN.
2. **En el repositorio local de Maven**: lo necesitan `admin-app` y `docente-app`, cuyos motores embebidos también ejecutan listeners.

El JAR ya está versionado en `proyecto/shared/lib/`, así que con el repo clonado alcanza con recrear Flowable. Si cambiaste código de listeners, regeneralo:

**Linux / macOS / Git Bash / WSL:**

```bash
mvn -f proyecto/shared/pom.xml clean package -DskipTests
cp proyecto/shared/target/proyecto-0.0.1-SNAPSHOT.jar proyecto/shared/lib/proyecto-0.0.1-SNAPSHOT.jar
mvn -f proyecto/shared/pom.xml install -DskipTests
docker compose -f docker-compose.yml up -d --no-deps --force-recreate flowable-ui
```

**PowerShell:**

```powershell
mvn -f proyecto/shared/pom.xml clean package -DskipTests
Copy-Item proyecto/shared/target/proyecto-0.0.1-SNAPSHOT.jar proyecto/shared/lib/proyecto-0.0.1-SNAPSHOT.jar -Force
mvn -f proyecto/shared/pom.xml install -DskipTests
docker compose -f docker-compose.yml up -d --no-deps --force-recreate flowable-ui
```

Si el contenedor se recrea sin ese mount, al iniciar una instancia el motor devuelve HTTP 500 y en los logs aparece `ClassNotFoundException: flowable.listeners.ContarActivas`.

### Paso 4 — Desplegar el BPMN y el DMN en el motor

Los archivos están en `proyecto/shared/src/resources/` y **no** se empaquetan en el JAR: se despliegan a mano por REST (o desde el Modeler de la UI).

**Linux / macOS / Git Bash / WSL:**

```bash
curl -u admin:test -F "file=@proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml" \
  "http://localhost:8080/flowable-ui/process-api/repository/deployments"

curl -u admin:test -F "file=@proyecto/shared/src/resources/Viabilidad.dmn" \
  "http://localhost:8080/flowable-ui/process-api/repository/deployments"
```

**Windows (PowerShell):**

```powershell
curl.exe -X POST -u admin:test -F "file=@proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml" "http://localhost:8080/flowable-ui/process-api/repository/deployments"
curl.exe -X POST -u admin:test -F "file=@proyecto/shared/src/resources/Viabilidad.dmn" "http://localhost:8080/flowable-ui/process-api/repository/deployments"
```

Usá `curl.exe` en Windows para no caer en el alias `Invoke-WebRequest` de PowerShell.

Verificar la definición desplegada:

```bash
curl -u admin:test "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?key=process"
```

El modelo corregido quedó como versión **`process:10`** en el entorno donde ya se desplegó (arreglo del `assignee` de "Desarrollar curso"). En una instalación limpia alcanza con desplegar el XML del repo: las instancias nuevas siempre toman la última versión; las viejas quedan ligadas a su `processDefinitionId` y no se migran.

Detalles del modelo que impactan en las pruebas:

- El proceso arranca con un **evento de inicio por mensaje** llamado `Solicitud de curso` (configurado en `web-app`).
- Entre "Definir docente" y "Desarrollar curso" hay un timer de **2 minutos** (`PT2M`).
- La regla del DMN es `cursosEnPreparacion >= 5` → `viable=false`.

### Paso 5 — Usuarios y grupos en el IDM de Flowable

En la app **IDM** de Flowable (<http://localhost:8080/flowable-ui>) crear estos usuarios, todos con password igual al usuario:

| Usuario | Rol | Grupo IDM |
| --- | --- | --- |
| `jefeAdmin` | Jefe administrativo | `administracion` |
| `admin1`, `admin2` | Administrativos | `administracion` |
| `jefeDocente` | Jefe docente | `docentes` |
| `docente1`, `docente2` | Docentes | `docentes` |

Crear también los grupos `administracion` y `docentes` y asociar cada usuario al suyo. El usuario `admin` / `test` ya lo crea el compose y es la cuenta de servicio usada por `web-app` para la API REST (y quien ve el panel de simulación de mensajes).

> Figurar en `application.properties` no crea al usuario en Flowable: `web-app` valida credenciales contra el IDM, `admin-app` exige el grupo `administracion` y `docente-app` el grupo `docentes`.

### Paso 6 — Poms y arranque de las tres apps

Cada módulo tiene su propio `pom.xml` (no hay pom padre):

| Módulo | Parent / dependencias clave | Función |
| --- | --- | --- |
| `shared/pom.xml` | Java 11, `flowable-engine` 6.8.0, driver PostgreSQL, JUnit | Compila los listeners y genera `proyecto-0.0.1-SNAPSHOT.jar`. No es Spring Boot. |
| `web-app/pom.xml` | Spring Boot **4.1.2**, Thymeleaf, WebMVC, Validation | Cliente REST puro. **No** depende de `shared`. |
| `admin-app/pom.xml` | Spring Boot **2.7.18**, `flowable-spring-boot-starter` 6.8.0, dependencia de `proyecto.app:proyecto` | Motor embebido para tareas administrativas. |
| `docente-app/pom.xml` | Igual que `admin-app` | Motor embebido para tareas docentes. |

¿Por qué 2.7 en dos apps y 4.1 en la otra? El starter de Flowable 6.8.0 está construido sobre Spring Boot 2.7 / `javax`; con Spring Boot 4.x (`jakarta`) no arranca. `web-app` no incluye Flowable, así que puede usar 4.1.2 sin problema.

Arranque con Maven instalado (funciona en cualquier sistema operativo):

```bash
mvn -f proyecto/web-app/pom.xml spring-boot:run    # http://localhost:8081
mvn -f proyecto/admin-app/pom.xml spring-boot:run  # http://localhost:8082
mvn -f proyecto/docente-app/pom.xml spring-boot:run # http://localhost:8083
```

O con los wrappers incluidos:

```bash
# Linux / macOS / Git Bash / WSL
cd proyecto/web-app && ./mvnw spring-boot:run
cd ../admin-app && ./mvnw spring-boot:run
cd ../docente-app && ./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
cd proyecto\web-app; .\mvnw.cmd spring-boot:run
cd ..\admin-app; .\mvnw.cmd spring-boot:run
cd ..\docente-app; .\mvnw.cmd spring-boot:run
```

Configuración relevante de cada `application.properties`:

- `web-app`: `server.port=8081`, `flowable.rest.base-url=http://localhost:8080/flowable-ui/process-api`, credenciales de servicio `admin/test`, mensaje de inicio `Solicitud de curso` y mapeo de roles por nombre de usuario.
- `admin-app` / `docente-app`: `spring.datasource.url=jdbc:postgresql://localhost:5432/flowable`, usuario `flowable`, password `flow123`; `flowable.database-schema-update=false`, `flowable.async-executor-activate=false` y `flowable.check-process-definitions=false` (esquema y despliegues los gestiona el motor de Docker; estas apps solo consultan y operan tareas).
- `docente-app` además define `flowable.rol.jefe-docente` y `flowable.rol.docentes`.

Si `admin-app` o `docente-app` cortan el arranque con un mensaje de "no pudo conectarse a la base del motor", revisá que el compose que estés usando publique el puerto 5432 en el host y que las credenciales coincidan con `.env`.

### Paso 7 — Probar que todo funcione

Tests unitarios (no necesitan Docker):

```bash
mvn -f proyecto/web-app/pom.xml test -q
mvn -f proyecto/shared/pom.xml test -q
mvn -f proyecto/admin-app/pom.xml test -q
mvn -f proyecto/docente-app/pom.xml test -q
```

Prueba end-to-end (necesita Docker arriba, el proceso desplegado y el JAR montado). El script está en PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-escenarios-flowable.ps1
```

En Linux/macOS podés instalar PowerShell para correrlo, o reproducir los pasos con los `curl` de `comandos-mensajes-empresa.txt`. Cubre 5 escenarios — aprobado, rechazado, modificado, umbral del DMN (4 vs 5 cursos) y cierre por 3 recordatorios — y limpia las instancias/filas que crea. El final esperado es `RESULTADO: TODOS LOS ESCENARIOS PASARON`.

Para simular la respuesta de la empresa a mano, están los comandos en `comandos-mensajes-empresa.txt`. Recordatorio: `Aprobacion`, `Rechazo`, `Modificacion` y `Cancelacion` son mensajes; "Recibir nomina" es una `receiveTask` que se dispara con `trigger`. El ID de la URL es siempre un **executionId**, no el `processInstanceId`.

### Problemas típicos

| Síntoma | Causa probable |
| --- | --- |
| `address already in use` en 8080 | Dos proyectos Compose peleando el puerto; usá siempre `-f docker-compose.yml` desde la raíz del repo. |
| HTTP 500 al enviar una solicitud + `ClassNotFoundException: flowable.listeners...` | Falta el mount del JAR o no se recreó `flowable-ui` después de copiarlo. |
| `admin-app` / `docente-app` no conectan | El compose usado no publica 5432, o las credenciales difieren de `.env`. |
| El login de empleados falla | El usuario no existe en el IDM de Flowable o la password no es la esperada. |
| "Desarrollar curso" no aparece | No venció el timer `PT2M`, o la instancia es de una versión vieja con el `assignee` entre comillas. |
| `Parent activity for boundary compensation event could not be found` | Instancia de un modelo anterior (el rediseño quitó la compensación BPMN); crear una solicitud nueva. |
| El DMN da `viable=true` con 5 cursos | Definición vieja desplegada; el XML actual usa la regla `>= 5`. |

---

## 4. Organización del proyecto e inventario de archivos

Árbol simplificado (sin `target/`, que son artefactos de compilación):

```text
Proyecto-Cursos-Gestion/
├── docker-compose.yml
├── .env
├── comandos-mensajes-empresa.txt
├── scripts/
│   └── test-escenarios-flowable.ps1
└── proyecto/
    ├── shared/        # listeners + BPMN + DMN + SQL + JAR
    ├── web-app/       # cliente REST (8081)
    ├── admin-app/     # motor embebido, rol administrativo (8082)
    └── docente-app/   # motor embebido, rol docente (8083)
```

### 4.1 Raíz del repositorio

| Archivo | Función |
| --- | --- |
| `docker-compose.yml` | Define PostgreSQL y Flowable UI, la red interna, el volumen de datos y el mount del JAR de listeners. |
| `.env` | Credenciales por defecto que consume el compose. |
| `comandos-mensajes-empresa.txt` | Recetario de `curl` para consultar suscripciones, enviar mensajes, disparar "Recibir nomina" y desplegar el modelo. |
| `scripts/test-escenarios-flowable.ps1` | Pruebas end-to-end de los 5 escenarios del BPMN contra Flowable y la base `institucion`. |
| `.gitignore` | Ignora compilados y JAR, con excepción del JAR de listeners en `shared/lib`. |
| `.project` | Metadatos de Eclipse (no afectan la ejecución). |

### 4.2 `proyecto/shared` — el núcleo del proceso

| Archivo | Función |
| --- | --- |
| `pom.xml` | Compila los listeners; depende de `flowable-engine` 6.8.0 y del driver PostgreSQL. Genera `proyecto-0.0.1-SNAPSHOT.jar`. |
| `README.md` | Lista los usuarios y grupos que deben existir en el IDM de Flowable. |
| `Database.sql` | Crea la base `institucion` con `empresa`, `cuenta_empresa`, `curso`, `propuesta`, datos de prueba y la vista `v_cursos_empresa`. |
| `src/resources/Institucion_de_capacitacion.bpmn20.xml` | El proceso BPMN: tareas, gateways, timers, mensajes y referencias a los listeners. Es el archivo que se despliega en el motor. |
| `src/resources/Viabilidad.dmn` | Tabla de decisión `viabilidad`: entrada `cursosEnPreparacion`, salida `viable`, regla `>= 5 → false`. |
| `lib/proyecto-0.0.1-SNAPSHOT.jar` | JAR versionado que el compose monta en el classpath de Flowable UI. |
| `src/main/java/flowable/listeners/Database.java` | Conexión JDBC centralizada a la base `institucion` (`jdbc:postgresql://postgres:5432/institucion`). |
| `src/main/java/flowable/listeners/ContarActivas.java` | Cuenta los cursos `EN_PREPARACION` (excluye la instancia actual) y setea la variable `cursosEnPreparacion` para el DMN. |
| `src/main/java/flowable/listeners/EstadoEnPreparacion.java` | Al habilitar el acceso de la empresa, pasa el curso vinculado al estado `EN_PREPARACION`. |
| `src/main/java/flowable/listeners/ActualizarEstado.java` | Al terminar la instancia, escribe en `curso.estado` y `fecha_cierre` según la variable `resultado`. |
| `src/main/java/flowable/listeners/AsignarTarea.java` | Task listener que reparte "Confeccionar lista definitiva" entre los administrativos con menos tareas asignadas. |
| `src/main/java/flowable/listeners/ListenerGenerico.java` | Listener de logging para eventos `create`/`assignment`/`complete` y `start`/`end`. |
| `src/main/java/flowable/listeners/TareaRealizada.java` | `JavaDelegate` genérico para service tasks: solo imprime en consola que la tarea se ejecutó. |
| `src/main/java/flowable/listeners/AutenticacionFlowable.java` | Utilidad que valida usuario/contraseña contra la API de identidad de Flowable por HTTP Basic. |
| `src/main/java/App.java` y `src/test/java/proyecto/app/AppTest.java` | Esqueleto "Hello World" y su test mínimo; no participan en el proceso. |

### 4.3 `proyecto/web-app` — cliente REST

**Archivos de configuración y arranque**

| Archivo | Función |
| --- | --- |
| `pom.xml` | Spring Boot 4.1.2 + Thymeleaf/WebMVC/Validation. Sin dependencia de `shared`. |
| `README.md` | Documentación detallada de pantallas, configuración y cambio de versiones del BPMN. |
| `src/main/resources/application.properties` | Puertos, URLs de la API REST, credenciales de servicio, mensaje de inicio y mapeo de roles. |
| `src/main/java/com/proyecto/web/WebApplication.java` | Punto de entrada de Spring Boot. |
| `src/main/java/com/proyecto/web/config/FlowableRestConfig.java` | Define los dos `RestClient`: uno para `process-api` y otro para la API de UI/identidad. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper para compilar sin Maven instalado. |
| `.gitignore`, `.gitattributes` | Ajustes de Git del módulo. |

**Controladores (rutas web)**

| Archivo | Función |
| --- | --- |
| `controller/SolicitudController.java` | `GET /` (formulario de solicitud) y `POST /solicitudes` (inicia la instancia en Flowable). |
| `controller/LoginController.java` | `POST /login`, `/empleados` (panel de simulación), envío de mensajes, `POST /empleados/recibir-nomina` y `/logout`. |
| `controller/AdministrativoController.java` | `/administrativos`: listar, reclamar y completar tareas; pantalla aparte para "Evaluar recursos". |
| `controller/DocenteController.java` | `/docentes`: listar, reclamar, completar, asignar docente y cargar informe. |

**Servicios y cliente HTTP**

| Archivo | Función |
| --- | --- |
| `client/FlowableRestClient.java` | Envoltorio de la API REST: iniciar instancias, validar credenciales, consultar tareas/suscripciones/variables, reclamar, completar, enviar mensajes y hacer `trigger`. |
| `service/SolicitudService.java` | Convierte el formulario en variables de proceso y dispara el mensaje de inicio. |
| `service/AuthService.java` | Delegada del login al cliente REST. |
| `service/TareaService.java` | Junta tareas propias y candidatas; reclamar/completar con las credenciales de la sesión. |
| `service/RolService.java` | Resuelve jefe/administrativos/docentes a partir del nombre de usuario configurado. |
| `service/SimulacionService.java` | Arma el panel de mensajes, valida que la suscripción siga viva y dispara mensajes o la recepción de nómina. |
| `service/DocenteService.java` | Vista docente y operación "Definir docente" (variable `docenteSeleccionado`). |

**Modelos y vistas** (DTOs y formularios; se listan por su rol, sin entrar en detalle)

| Archivo | Función |
| --- | --- |
| `model/SolicitudForm.java` | Formulario de solicitud con validaciones (`@NotBlank`, `@Email`, `@Min`). |
| `model/RecursosForm.java` | Casillas de "Evaluar recursos" (`aulaFisica`, `aulaVirtual`, `equipamientoEspecial`). |
| `model/DefinirDocenteForm.java` | Docente elegido en la pantalla de asignación. |
| `model/Tarea.java`, `model/TareaVista.java`, `model/TareaDocenteVista.java` | Representación de tareas de Flowable y de las vistas adaptadas a cada pantalla. |
| `model/VariableRequest.java`, `model/ProcessVariable.java`, `model/StartProcessRequest.java`, `model/TaskActionRequest.java`, `model/MessageEventRequest.java`, `model/ExecutionActionRequest.java` | Cuerpos de las llamadas REST y variables de proceso. |
| `model/EventSubscription.java`, `model/Execution.java`, `model/FlowableListResponse.java`, `model/AccionSimulable.java`, `model/InstanciaSimulable.java` | Respuestas de Flowable usadas por el panel de simulación. |

**Plantillas y recursos**

| Archivo | Función |
| --- | --- |
| `resources/templates/index.html` | Plantilla correspondiente a la pantalla principal (solicitud + login). |
| `resources/templates/empleados.html` | Plantilla correspondiente al panel de simulación de mensajes. |
| `resources/templates/administrativos.html` | Plantilla correspondiente a las tareas administrativas. |
| `resources/templates/evaluar-recursos.html` | Plantilla correspondiente al formulario de recursos. |
| `resources/templates/docentes.html` | Plantilla correspondiente a las tareas del equipo docente. |
| `resources/templates/asignar-docente.html` | Plantilla correspondiente a la elección de docente. |
| `resources/static/css/estilos.css` | Estilos compartidos de las pantallas. |
| `resources/static/css/.gitkeep`, `resources/static/js/.gitkeep` | Marcadores para conservar las carpetas vacías. |

**Tests**

| Archivo | Función |
| --- | --- |
| `src/test/java/com/proyecto/web/WebApplicationTests.java` | Test de contexto de la aplicación. |
| `src/test/java/com/proyecto/web/service/SimulacionServiceTest.java` | Verifica nombres de mensajes y marcado de acciones peligrosas en el panel. |

Los `package-info.java` de `client`, `config`, `controller`, `model` y `service` solo documentan el propósito de cada paquete.

### 4.4 `proyecto/admin-app` — rol administrativo (motor embebido)

| Archivo | Función |
| --- | --- |
| `pom.xml` | Spring Boot 2.7.18 + starter de Flowable 6.8.0 + dependencia del JAR de `shared`. |
| `README.md` | Explica el porqué de Spring Boot 2.7, la conexión a la BD y la tarea soportada. |
| `src/main/resources/application.properties` | Puerto 8082, JDBC a la base `flowable`, flags para no migrar esquema ni ejecutar jobs. |
| `src/main/resources/META-INF/spring.factories` | Registra el `FailureAnalyzer` de conexión a la base. |
| `src/main/java/com/proyecto/admin/AdminAppApplication.java` | Arranque; sale con código 1 con mensaje claro si falla la conexión a Postgres. |
| `config/AutenticacionInterceptor.java` | Redirige a `/login` cualquier ruta privada sin sesión. |
| `config/WebConfig.java` | Registra el interceptor y deja públicos login, error y recursos estáticos. |
| `config/SesionConstantes.java` | Nombre del atributo de sesión con el usuario autenticado. |
| `config/FlowableStartupLogger.java` | Verifica al arrancar que `ProcessEngine` y `TaskService` responden. |
| `config/ConexionBaseDeDatosFailureAnalyzer.java` | Convierte el error de conexión en un mensaje accionable. |
| `controller/LoginController.java` | Login/logout por sesión contra el IDM; exige grupo `administracion`. |
| `controller/HomeController.java` | `GET /`: lista tareas "Preparar materiales" asignadas y reclamables. |
| `controller/TareaController.java` | `POST /tareas/{id}/reclamar` y `/tareas/{id}/completar` (POST + redirect). |
| `service/AutenticacionService.java` | `IdentityService.checkPassword` + pertenencia al grupo `administracion`. |
| `service/TareaService.java` | Consultas, validación de candidatos, `claim` y `complete` sobre "Preparar materiales". |
| `service/TareaException.java` | Errores de negocio mostrables en la interfaz. |
| `model/TareaVista.java` | DTO de tarea con fecha formateada para Thymeleaf. |
| `resources/templates/index.html` | Plantilla correspondiente al listado de tareas administrativas. |
| `resources/templates/login.html` | Plantilla correspondiente al login. |
| `resources/static/css/estilos.css` | Estilos de las pantallas. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`, `.gitignore`, `.gitattributes` | Wrapper y ajustes de Git del módulo (no hay tests en este módulo). |

### 4.5 `proyecto/docente-app` — rol docente (motor embebido)

| Archivo | Función |
| --- | --- |
| `pom.xml` | Igual estructura que `admin-app`, con las mismas versiones. |
| `README.md` | Tabla de las tres tareas docentes, arranque, reglas y estructura. |
| `src/main/resources/application.properties` | Puerto 8083, JDBC a `flowable` y roles `jefeDocente` / `docente1,docente2`. |
| `src/main/resources/META-INF/spring.factories` | Registra el `FailureAnalyzer` de conexión. |
| `src/main/java/com/proyecto/docente/DocenteAppApplication.java` | Arranque y salida limpia si cae la base del motor. |
| `config/AutenticacionInterceptor.java` | Exige sesión en rutas privadas; el permiso de jefe se valida en los servicios. |
| `config/WebConfig.java` | Registra el interceptor y excluye login y recursos estáticos. |
| `config/SesionConstantes.java` | Nombre del atributo de sesión (acá no se guarda la contraseña). |
| `config/FlowableStartupLogger.java` | Comprueba `ProcessEngine` y `TaskService` al arrancar. |
| `config/ConexionBaseDeDatosFailureAnalyzer.java` | Mensaje claro si Postgres no responde. |
| `controller/LoginController.java` | Login/logout contra IDM; exige grupo `docentes`. |
| `controller/HomeController.java` | `GET /`: tareas asignadas y reclamables, con flag `esJefe`. |
| `controller/TareaController.java` | Rutas para definir docente, reclamar, completar "Desarrollar curso" y cargar informe. |
| `service/AutenticacionService.java` | Password + grupo `docentes` + detección del jefe. |
| `service/TareaService.java` | Consultas y operaciones sobre los tres `taskDefinitionKey` del carril docente. |
| `service/DocenteService.java` | Guarda `docenteSeleccionado` y el texto del `informe`; solo lo puede hacer el jefe. |
| `service/TareaException.java` | Errores de negocio para la interfaz. |
| `model/TareaVista.java` | DTO con empresa, temática y flags `isDefinirDocente`, `isDesarrollo`, `isInforme`. |
| `model/DefinirDocenteForm.java` | Formulario para elegir al docente. |
| `model/InformeForm.java` | Formulario de texto del informe de resultados (sin PDF). |
| `resources/templates/index.html` | Plantilla correspondiente al listado de tareas docentes. |
| `resources/templates/login.html` | Plantilla correspondiente al login. |
| `resources/templates/asignar-docente.html` | Plantilla correspondiente a la definición del docente. |
| `resources/templates/elaborar-informe.html` | Plantilla correspondiente al informe de resultados. |
| `resources/static/css/estilos.css` | Estilos de las pantallas. |
| `src/test/java/com/proyecto/docente/DocenteAppApplicationTests.java` | Test de contexto de la aplicación. |
| `src/test/java/com/proyecto/docente/controller/HomeControllerVistaTest.java` | Verifica que la plantilla principal renderice sin errores. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`, `.gitignore`, `.gitattributes` | Wrapper y ajustes de Git del módulo. |

---

## 5. Ideas finales

- Si algo no arranca, el orden de chequeo que suele ahorrar tiempo es: **`docker compose ps` → puerto 5432 publicado → base `institucion` cargada → JAR montado y `flowable-ui` recreado → BPMN/DMN desplegados → usuarios/grupos en IDM**.
- Los README de cada módulo tienen más detalle histórico (cambios del BPMN, versiones `process:*`, decisiones de diseño). Este documento es la guía operativa corta; esa documentación es el respaldo.
- El proyecto deliberadamente mezcla dos estilos de integración con Flowable (REST en `web-app`, API embebida en las otras dos apps) para cubrir los dos casos: despliegue independiente del motor y operación de tareas desde una intranet con acceso directo a la base del motor.
