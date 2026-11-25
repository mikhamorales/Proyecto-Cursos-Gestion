# docente-app

Aplicacion web del **equipo docente** para el proyecto de gestion de cursos.
Es Spring Boot 2.7.18 + Thymeleaf y opera las tres tareas del carril docente
del BPMN a traves de la **API REST de Flowable** (`process-api`), igual que
`analisis-app`. No arranca un motor embebido ni abre conexion JDBC: el motor
del docker hace el trabajo y esta app solo consume su API.

| taskDefinitionKey | Tarea | Quien la opera | Accion de esta app |
| --- | --- | --- | --- |
| `sid-D9626FE6-AE69-44AF-9389-E320BC348EDB` | Definir docente que desarrollara el curso | `jefeDocente` | Elige algun integrante del equipo y completa la tarea con la variable `docenteSeleccionado`. |
| `sid-895BA48F-B50A-4C10-9369-4CFEA6D67AC2` | Desarrollar curso | El docente elegido | Boton "Registrar finalizacion" (completa la tarea sin variables). |
| `sid-EDDAC05F-B007-4E01-8223-713A621E1188` | Elaborar informe de resultados | `jefeDocente` | Formulario de texto que guarda la variable `informe` y completa la tarea. |

Las variables del proceso que se muestran en la tabla son `empresa` y
`tematica`, leidas de `includeProcessVariables=true`. No se sube ni se genera
ningun PDF: el informe es solo texto.

## Arranque

Requisitos previos:

1. Docker levantado con `flowable-postgres` y `flowable-ui`. La app solo
   necesita que `flowable-ui` responda en `http://localhost:8080/flowable-ui`;
   **no hace falta publicar el puerto 5432 de Postgres**.
2. El proceso desplegado en el motor y los usuarios del grupo IDM `docentes`
   creados (ver `proyecto/shared/README.md`).

No hace falta instalar el JAR de listeners: los ejecuta el motor del docker.

Arrancar en el puerto **8083**:

```bash
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

Luego abrir `http://localhost:8083/`.

## Configuracion

Los valores por defecto estan en `src/main/resources/application.properties` y
se pueden sobrescribir con variables de entorno:

| Variable de entorno | Valor por defecto | Uso |
| --- | --- | --- |
| `FLOWABLE_REST_URL` | `http://localhost:8080/flowable-ui/process-api` | Base de la API REST del motor. |
| `FLOWABLE_REST_USER` | `admin` | Usuario de servicio usado para consultar/operar tareas. |
| `FLOWABLE_REST_PASSWORD` | `test` | Password de ese usuario. |

El login, en cambio, valida las credenciales **del docente** contra la misma
API (llamada `GET /identity/users/{usuario}` con su Basic Auth) y despues
comprueba que pertenezca al grupo `docentes`. El nombre del jefe y la lista de
docentes se configuran en `flowable.rol.jefe-docente` y `flowable.rol.docentes`.

## Usuarios de prueba

Del grupo IDM `docentes` (clave = usuario, ver `proyecto/shared/README.md`):

- `jefeDocente` — unico usuario que puede definir docentes y cargar informes.
- `docente1`, `docente2` — integrantes que pueden reclamar y completar tareas.

## Estructura

```
src/main/java/com/proyecto/docente/
├── DocenteAppApplication.java          # arranque
├── client/
│   ├── FlowableRestClient.java         # consultas y acciones via process-api
│   └── FlowableRestException.java      # error de comunicacion con el motor
├── config/
│   ├── AutenticacionInterceptor.java   # exige sesion en todas las rutas privadas
│   ├── FlowableRestFailureAnalyzer.java# mensaje claro si el motor no responde
│   ├── FlowableStartupLogger.java      # comprueba la API REST al arrancar
│   ├── SesionConstantes.java
│   └── WebConfig.java                  # login y recursos estaticos publicos
├── controller/
│   ├── LoginController.java            # login/logout por sesion
│   ├── HomeController.java             # listado "Mis tareas" y "Para reclamar"
│   └── TareaController.java            # asignar/definir/reclamar/completar/informe
├── model/
│   ├── TareaVista.java                 # DTO con empresa, tematica y flags de tipo
│   ├── DefinirDocenteForm.java
│   └── InformeForm.java
└── service/
    ├── AutenticacionService.java       # password + grupo + rol de jefe
    ├── TareaService.java               # consultas, claim y complete por REST
    ├── DocenteService.java             # definir docente y guardar informe
    └── TareaException.java             # errores de negocio para el usuario
src/main/resources/
├── application.properties
├── META-INF/spring.factories
├── static/css/estilos.css
└── templates/{index,login,asignar-docente,elaborar-informe}.html
```

## Reglas importantes

- Las acciones toman siempre el usuario de la **sesion HTTP**, nunca de un
  parametro del formulario.
- Los botones usan POST + redirect (PRG); recargar la pagina no repite la
  operacion.
- `TareaService` filtra solo por los tres `taskDefinitionKey` del carril
  docente. Para reclamar comprueba antes que la tarea este sin asignar y que
  el usuario sea candidato directo o del grupo `docentes` (el parametro
  `candidateUser` de la API REST cubre ambos casos).
- `Definir docente` y `Elaborar informe` requieren ademas el rol de jefe; la
  interfaz oculta sus acciones para otros usuarios y el servicio lo vuelve a
  validar.
- Si el motor no responde, el arranque termina con un mensaje claro y la pagina
  principal muestra el error en lugar de un HTTP 500.

## Incidencia conocida del BPMN

En `proyecto/shared/src/resources/Institucion_de_capacitacion.bpmn20.xml`, la
tarea *Desarrollar curso* tiene actualmente:

```xml
flowable:assignee="&quot;${docenteSeleccionado}&quot;"
```

Eso puede dejar el `assignee` entre comillas literales y la tarea no aparecer
en "Mis tareas". Si ocurre, corregir el BPMN a:

```xml
flowable:assignee="${docenteSeleccionado}"
```

y volver a desplegarlo en el motor.

## Tests

```bash
mvn -f proyecto/docente-app/pom.xml test
```

Los tests son unitarios con un mock del cliente REST: no necesitan Docker.
Comprueban login por grupo, validacion de jefe al definir docente, guardado de
la variable `docenteSeleccionado`, rechazo de informe vacio y renderizado de
las tres acciones en la pagina principal.
