# admin-app

App Spring Boot para el rol administrativo. Corre local a la intranet y usa la
**API Java de Flowable embebida** (sin REST) contra el mismo Postgres del motor
que corre en Docker.

Incluye el login contra el IDM de Flowable y la operacion de la tarea
**"Preparar materiales"** del proceso de cursos: listar las tareas asignadas al
usuario, listar las reclamables por su grupo, reclamarlas y completarlas.

## Requisitos

- JDK 17 o superior (se compila con el JDK 21 del host, target 17).
- El docker del motor levantado (`flowable-postgres` en `localhost:5432` y
  `flowable-persistente` en `localhost:8080`).
- Maven (se incluye el wrapper `mvnw`; en la primera build necesita red para
  bajar Spring Boot 2.7.18 y los starters de Flowable).
- El jar de `shared` instalado en el repositorio local de Maven (ver
  "Listeners del BPMN").

## Por que Spring Boot 2.7 y no 4.x

`web-app` usa Spring Boot 4.1.2, pero el starter de Flowable **6.8.0** (la
version que corre en el motor del docker) esta construido sobre Spring Boot
2.7 / `javax`. Con Spring Boot 4.x (`jakarta`) no arranca. Por eso `admin-app`
fija **Spring Boot 2.7.18 + Flowable 6.8.0**, alineado con el motor.

## Configuracion de la conexion

Todo vive en `src/main/resources/application.properties` (nada hardcodeado en
el codigo):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/flowable
spring.datasource.username=flowable
spring.datasource.password=flow123
```

Puntos importantes:

- La base es **`flowable`** (donde estan las tablas `ACT_*` del motor), **no**
  la base `institucion` (datos de dominio del proyecto).
- `flowable.database-schema-update=false`: el esquema lo gestiona el motor del
  docker; esta app no crea ni modifica tablas.
- `flowable.async-executor-activate=false`: esta app no ejecuta jobs del motor,
  solo consulta y opera tareas.

## Funcionalidad (issue5)

- **Login** en `/login` contra `IdentityService.checkPassword` (IDM de Flowable)
  y exigiendo pertenencia al grupo **`administracion`**. Las contrasenas del
  entorno de prueba son iguales al usuario (`admin1`/`admin1`, etc.).
- La sesion se guarda en `HttpSession`; un `HandlerInterceptor` redirige a
  `/login` cualquier ruta sin sesion. No se usa Spring Security.
- **Home** en `/`: lista las tareas de "Preparar materiales"
  (`taskDefinitionKey` `sid-E0F8C31B-8D21-4CBC-A2A7-C7213EA7FBFB`) asignadas al
  usuario y las que puede reclamar por sus grupos.
- **Reclamar** (`POST /tareas/{id}/reclamar`) y **completar**
  (`POST /tareas/{id}/completar`). Completar exige que la tarea este reclamada
  por el usuario de la sesion.
- **Logout** (`POST /logout`) invalida la sesion.

## Listeners del BPMN

El motor embebido de esta app **ejecuta los listeners del proceso**, no el motor
del docker. Al completar una tarea, Flowable resuelve por nombre la clase del
`taskListener` (`flowable.listeners.ListenerGenerico`); si no esta en el
classpath, `complete()` falla con *Could not load class*.

Por eso `admin-app` depende del jar de `shared`. Instalarlo una vez:

```bash
mvn -f proyecto/shared/pom.xml install
```

Solo se ejecuta `ListenerGenerico` en esta tarea (no toca la base de datos).
Si en el futuro se operan tareas cuyos listeners usen `Database.get()`, habra
que revisar que esa conexion apunte al Postgres accesible desde el host.

## Compilar y ejecutar

Desde `proyecto/admin-app`:

```bash
./mvnw test        # compila y corre tests
./mvnw package     # genera el jar en target/
./mvnw spring-boot:run
```

La app corre en **http://localhost:8082/** (distinto del motor en `8080` y de
`web-app` en `8081`).

Al arrancar, en el log se ve que el engine de Flowable esta listo:

```
***** Flowable listo: engine='...' version='6.8.0' *****
***** TaskService OK: N tarea(s) pendiente(s) en el motor *****
```

## Si el Docker esta caido

La app corta el arranque con un mensaje claro indicando que no pudo conectarse
a la base del motor (en vez de un stacktrace crudo), y termina con codigo 1.
