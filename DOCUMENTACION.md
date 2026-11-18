# Documentación del proyecto — Gestión de cursos con Flowable

> Manual de puesta en marcha + mapa rápido del repositorio.
> Redactado sobre el estado actual del proyecto (el que hay en el repo y en Docker ahora mismo).
> Tono mitad formal, mitad "acá está lo que hay que saber" — está para que otra persona pueda levantar todo sin tener que reconstruir la historia de los README.

---

## 1. De qué va esto, en dos párrafos

Es un sistema para gestionar solicitudes de capacitación de empresas. El corazón es un proceso BPMN (`Institucion de capacitacion`) que corre en **Flowable 6.8.0** dentro de Docker, con **PostgreSQL** como base del motor y una base de datos aparte (`institucion`) con los datos de dominio: empresas, cursos, propuestas y cuentas.

Alrededor del motor hay **tres aplicaciones web Spring Boot** que atienden a tres perfiles distintos:

| App | Puerto | Cómo habla con Flowable | Para quién es |
| --- | --- | --- | --- |
| `web-app` | 8081 | Cliente **REST** (`/flowable-ui/process-api`) | Clientes (formulario de solicitud) y empleados (panel de simulación y tareas) |
| `admin-app` | 8082 | **API Java embebida**, conecta por JDBC a la base `flowable` | Parte administrativa ("Preparar materiales") |
| `docente-app` | 8083 | **API Java embebida**, conecta por JDBC a la base `flowable` | Equipo docente (definir docente, desarrollar curso, informe) |

Dos matices que conviene tener claros desde el principio:

- Hay **dos bases de datos**: `flowable` (tablas `ACT_*` del motor) e `institucion` (datos del negocio). La primera la crea el compose; la segunda se carga a mano con `Database.sql`.
- `web-app` **no** depende del módulo `shared` y no ejecuta listeners. `admin-app` y `docente-app` sí: sus motores embebidos resuelven las clases `flowable.listeners.*` por nombre, por eso necesitan el JAR de `shared` instalado en el repositorio local de Maven.

---

## 2. Requisitos previos

 | Herramienta | Versión / detalle |
 | --- | --- |
 | Docker + Docker Compose | Docker 29.x / Compose v5.x en la máquina de desarrollo. Compose baja las imágenes; **no hay que descargar Flowable a mano**. |
 | JDK | 17 o superior (acá hay JDK 21; `shared` compila con target 11, las apps con target 17). |
 | Maven | 3.9+ o los wrappers `mvnw` / `mvnw.cmd` incluidos en cada app (primera build con red). |
 | `psql` | No hace falta en el host: se ejecuta dentro del contenedor con `docker exec`. |
 | Navegador | Para Flowable UI (8080) y las tres apps. |

Credenciales por defecto (viven en `.env`, y los listeners/las apps las asumen):

```dotenv
POSTGRES_USER=flowable
POSTGRES_PASSWORD=flow123
POSTGRES_DB=flowable
FLOWABLE_ADMIN_USER=admin
FLOWABLE_ADMIN_PASSWORD=test
```

---

## 3. Paso a paso para levantar todo

### Paso 0 — Pararse en la raíz del repo

```powershell
Set-Location C:\Users\joaqu\eclipse-workspace\proyecto-bpmn\Proyecto-Cursos-Gestion
```

Todos los comandos de abajo asumen esa ubicación salvo que se indique lo contrario.

### Paso 1 — Descargar y levantar Flowable + PostgreSQL

No hay que bajar ningún WAR ni ZIP: `docker-compose.yml` usa `flowable/flowable-ui:latest` y `postgres:16-alpine`, y Docker las descarga solas la primera vez.

```powershell
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml ps
docker compose -f docker-compose.yml logs -f flowable-ui   # solo si querés mirar el arranque
```

Qué levanta el compose:

- `flowable-postgres`: base `flowable`, usuario `flowable`, password `flow123`, volumen `postgres-data` (los datos sobreviven a `docker compose down`). nombre de red `flowable-net`.
- `flowable-ui`: IDM + Modeler + Task + Admin + Process/REST API en <http://localhost:8080/flowable-ui> (usuario `admin` / password `test`). El hostname del Postgres **dentro de la red Docker** es `postgres`, no `localhost`.
- El nombre del proyecto Compose es `joaqu` (está declarado en el archivo), así que no hace falta pasar `-p`, aunque lo pasaré en algún comando por claridad.

**Ojo con el compose de la home.** En esta máquina también existe `C:\Users\joaqu\docker-compose.yml`: es casi el mismo, pero con dos diferencias importantes:

| | Compose del repo (raíz) | Compose de la home |
| --- | --- | --- |
| Postgres | publica `5432:5432` en el host | solo `expose: 5432` (no accesible desde el host) |
| Mount del JAR | `./proyecto/shared/lib/...` | `./eclipse-workspace/proyecto-bpmn/.../lib/...` |
| Flowable | `8080:8080` | `127.0.0.1:8080:8080` |

Los dos declaran `name: joaqu`, así que no pelean por el puerto 8080, pero **si querés arrancar `admin-app` o `docente-app`, necesitás el compose de la raíz** (o agregarle `ports: ["5432:5432"]` al de la home), porque esas apps se conectan a `jdbc:postgresql://localhost:5432/flowable`. Con el compose de la home actual, `web-app` funciona igual (usa REST), pero las dos apps embebidas no pueden conectar. Recomendación: usar siempre el compose del repo con `-f docker-compose.yml`.

> En WSL, el mount del JAR tiene que ser **relativo al compose** (`./proyecto/...` desde la raíz, `./eclipse-workspace/...` desde la home). No escribas `C:/Users/...` en `source`: WSL lo interpreta como ruta relativa y Docker termina quejándose con `no such file or directory` o `invalid volume specification`.

### Paso 2 — Crear la base de datos `institucion`

El compose crea la base `flowable`, pero **no** crea `institucion`. Esa base se genera con el script `proyecto/shared/Database.sql`, que además mete datos de prueba (3 empresas, 4 cursos, propuestas y la vista `v_cursos_empresa`).

Es **destructivo**: empieza con `DROP DATABASE IF EXISTS institucion`. Si ya hay datos que querés conservar, no lo corras.

Desde `proyecto/shared`, en **PowerShell**:

```powershell
Set-Location proyecto\shared
Get-Content Database.sql -Raw | docker exec -i flowable-postgres psql -U flowable -d postgres -v ON_ERROR_STOP=1
docker exec flowable-postgres psql -U flowable -d institucion -c '\dt'
```

Equivalente en **bash / WSL** (PowerShell no soporta el `<` de entrada):

```bash
cd proyecto/shared
docker exec -i flowable-postgres psql -U flowable -d postgres -v ON_ERROR_STOP=1 < Database.sql
docker exec flowable-postgres psql -U flowable -d institucion -c '\dt'
```

Tenés que ver `empresa`, `cuenta_empresa`, `curso` y `propuesta`. El script usa `-d postgres` para poder hacer `DROP/CREATE DATABASE`; por eso no se conecta directamente a `institucion`.

### Paso 3 — Compilar el JAR de listeners, copiarlo e instalarlo

Los listeners (`flowable.listeners.*`) están en el módulo `shared`. El JAR se usa en **dos lugares distintos**, no confundir:

1. **Dentro del contenedor de Flowable**: se monta en `/app/WEB-INF/lib/` para que el motor pueda ejecutar los listeners del BPMN.
2. **En el repositorio local de Maven** (`~/.m2`): lo necesitan `admin-app` y `docente-app` porque sus motores embebidos también ejecutan listeners.

Desde la raíz del repo:

```powershell
# 1) compilar
mvn -f proyecto\shared\pom.xml clean package -DskipTests

# 2) copiar el JAR al lugar que monta el compose
Copy-Item proyecto\shared\target\proyecto-0.0.1-SNAPSHOT.jar `
          proyecto\shared\lib\proyecto-0.0.1-SNAPSHOT.jar -Force

# 3) instalarlo en el .m2 local (para admin-app y docente-app)
mvn -f proyecto\shared\pom.xml install -DskipTests

# 4) recrear SOLO Flowable para que tome el JAR
docker compose -f docker-compose.yml -p joaqu up -d --no-deps --force-recreate flowable-ui
```

El JAR ya está versionado en `proyecto/shared/lib/`, o sea que en una máquina limpia con el repo clonado *debería* alcanzar con el paso 4. Pero si cambiaste código de listeners, hay que regenerarlo y copiarlo: si el contenedor se recrea sin ese mount, al iniciar una instancia el motor devuelve HTTP 500 y en `docker compose logs flowable-ui` aparece `ClassNotFoundException: flowable.listeners.ContarActivas`.

### Paso 4 — Desplegar el BPMN y el DMN en el motor

Los archivos viven en `proyecto/shared/src/resources/` y **no** se empaquetan en el JAR: se despliegan a mano (por REST o desde el Modeler).

```powershell
# Proceso
curl.exe -X POST -u admin:test `
  -F "file=@proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml" `
  "http://localhost:8080/flowable-ui/process-api/repository/deployments"

# Tabla de decisión (decision key: viabilidad)
curl.exe -X POST -u admin:test `
  -F "file=@proyecto/shared/src/resources/Viabilidad.dmn" `
  "http://localhost:8080/flowable-ui/process-api/repository/deployments"
```

Para chequear la versión desplegada:

```powershell
curl.exe -s -u admin:test "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?key=process"
```

En el entorno actual está desplegada la versión **`process:10`** (la corrección del `assignee` de "Desarrollar curso", sin comillas). En una instalación limpia, alcanza con desplegar el XML del repo: las instancias nuevas siempre usan la última versión; las viejas quedan ligadas a su `processDefinitionId` y no se migran.

Dos detalles del modelo que impactan en las pruebas:

- El proceso arranca con un **evento de inicio por mensaje** llamado `Solicitud de curso` (configurado en `web-app` como `flowable.process-start-message`).
- Entre "Definir docente" y "Desarrollar curso" hay un timer de **2 minutos** (`PT2M`): la tarea del docente aparece recién cuando vence.
- La regla del DMN es `cursosEnPreparacion >= 5` → `viable=false` (no `> 5`).

### Paso 5 — Usuarios y grupos en el IDM de Flowable

En la UI de Flowable (`http://localhost:8080/flowable-ui`, app **IDM**) crear estos usuarios, todos con password igual al usuario:

| Usuario | Rol | Grupo IDM |
| --- | --- | --- |
| `jefeAdmin` | Jefe administrativo | `administracion` |
| `admin1`, `admin2` | Administrativos | `administracion` |
| `jefeDocente` | Jefe docente | `docentes` |
| `docente1`, `docente2` | Docentes | `docentes` |

Crear también los grupos `administracion` y `docentes` y asociar cada usuario al suyo. El usuario `admin` / `test` ya lo crea el compose y es la cuenta de servicio que usa `web-app` para la API REST (y quien ve el panel de simulación de mensajes).

> `web-app` valida el login de empleados contra usuarios reales del IDM; `admin-app` exige pertenencia al grupo `administracion` y `docente-app` al grupo `docentes`. Que un usuario esté en `application.properties` no lo crea en Flowable.

### Paso 6 — Poms y arranque de las tres apps

Cada módulo tiene su propio `pom.xml` (no hay pom padre):

| Módulo | Parent / dependencias clave | Para qué sirve |
| --- | --- | --- |
| `shared/pom.xml` | Java 11, `flowable-engine` 6.8.0, driver PostgreSQL, JUnit | Compila los listeners y genera `proyecto-0.0.1-SNAPSHOT.jar`. No es Spring Boot. |
| `web-app/pom.xml` | Spring Boot **4.1.2**, Thymeleaf, WebMVC, Validation | Cliente REST puro. **No** depende de `shared`. |
| `admin-app/pom.xml` | Spring Boot **2.7.18**, `flowable-spring-boot-starter` 6.8.0, dependencia de `proyecto.app:proyecto` | Motor embebido para tareas administrativas. |
| `docente-app/pom.xml` | Igual que `admin-app` | Motor embebido para tareas docentes. |

¿Por qué 2.7 en dos apps y 4.1 en la otra? Porque el starter de Flowable 6.8.0 está construido sobre Spring Boot 2.7 / `javax`; con Spring Boot 4.x (`jakarta`) no arranca. `web-app` no incluye Flowable, así que puede usar 4.1.2 sin problema.

Arranque (desde la raíz, cada bloque en su propio directorio):

```powershell
# web-app -> http://localhost:8081
Set-Location proyecto\web-app
.\mvnw.cmd spring-boot:run
# alternativa: .\mvnw.cmd clean package ; java -jar target\web-app-0.0.1-SNAPSHOT.jar

# admin-app -> http://localhost:8082  (requiere el JAR instalado y 5432 publicado)
Set-Location ..\admin-app
.\mvnw.cmd spring-boot:run

# docente-app -> http://localhost:8083 (idem)
Set-Location ..\docente-app
.\mvnw.cmd spring-boot:run
```

Configuración relevante de cada `application.properties`:

- `web-app`: `server.port=8081`, `flowable.rest.base-url=http://localhost:8080/flowable-ui/process-api`, credenciales de servicio `admin/test`, mensaje de inicio `Solicitud de curso` y mapeo de roles por nombre de usuario (`jefeAdmin`, `admin1,admin2`, `jefeDocente`, `docente1,docente2`).
- `admin-app` / `docente-app`: `spring.datasource.url=jdbc:postgresql://localhost:5432/flowable`, usuario `flowable`, password `flow123`; `flowable.database-schema-update=false`, `flowable.async-executor-activate=false` y `flowable.check-process-definitions=false` (el esquema y los despliegues los gestiona el motor de Docker, estas apps solo consultan y operan tareas). `docente-app` además define `flowable.rol.jefe-docente` y `flowable.rol.docentes`.

Si `admin-app` o `docente-app` cortan el arranque con un mensaje de "no pudo conectarse a la base del motor", lo más probable es que estés con el compose de la home (5432 no publicado). Paso 1, opción compose del repo.

### Paso 7 — Probar que todo anduvo

Tests unitarios (no necesitan Docker; los de `shared` sí necesitan el JAR de Flowable ya resuelto por Maven):

```powershell
mvn -f proyecto\web-app\pom.xml test -q
mvn -f proyecto\shared\pom.xml test -q
mvn -f proyecto\admin-app\pom.xml test -q
mvn -f proyecto\docente-app\pom.xml test -q
```

Prueba end-to-end (necesita Docker arriba, el proceso desplegado y el JAR montado):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-escenarios-flowable.ps1
```

Cubre 5 escenarios — aprobado, rechazado, modificado, umbral del DMN (4 vs 5 cursos) y cierre por 3 recordatorios — y limpia las instancias/filas que crea. El final esperado es `RESULTADO: TODOS LOS ESCENARIOS PASARON`.

Para simular la respuesta de la empresa a mano (mensajes `Aprobacion`, `Rechazo`, `Modificacion`, `Cancelacion` y el `trigger` de "Recibir nomina"), están los comandos `curl` en `comandos-mensajes-empresa.txt`. Recordatorio: el ID de la URL es un **executionId**, no el `processInstanceId`.

### Problemas típicos (chequeo rápido)

| Síntoma | Causa probable |
| --- | --- |
| `address already in use` en 8080 | Dos compose con distinto proyecto peleando el puerto. Usá el `name: joaqu` del repo. |
| HTTP 500 al enviar una solicitud + `ClassNotFoundException: flowable.listeners...` | Falta el mount del JAR o no se recreó `flowable-ui` después de copiarlo. |
| `admin-app` / `docente-app` no conectan | 5432 no publicado en el host (compose de la home) o credenciales distintas a `.env`. |
| El login de empleados falla | El usuario no existe en el IDM de Flowable o la password no es la esperada. |
| La tarea "Desarrollar curso" no aparece | Falta que venza el timer `PT2M`, o la instancia es de una versión vieja con el `assignee` entre comillas. |
| `Parent activity for boundary compensation event could not be found` | Instancia de un modelo anterior (el rediseño quitó la compensación BPMN). Crear una solicitud nueva. |
| DMN da `viable=true` con 5 cursos | Estás usando una definición vieja; el XML actual tiene la regla `>= 5`. |

---

## 4. Cómo está organizado el proyecto

Árbol simplificado (sin `target/`, que son artefactos de compilación):

```text
Proyecto-Cursos-Gestion/
├── docker-compose.yml              # Postgres + Flowable UI
├── .env                            # credenciales del compose
├── comandos-mensajes-empresa.txt   # recetario curl para mensajes/despliegue
├── scripts/
│   └── test-escenarios-flowable.ps1
└── proyecto/
    ├── shared/                     # listeners + BPMN + DMN + SQL + JAR
    ├── web-app/                    # cliente REST (8081)
    ├── admin-app/                  # motor embebido, rol administrativo (8082)
    └── docente-app/                # motor embebido, rol docente (8083)
```

### Inventario archivo por archivo

(sección en preparación)
