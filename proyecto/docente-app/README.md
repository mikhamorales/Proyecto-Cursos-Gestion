# docente-app

Aplicacion web del **equipo docente** para el proyecto de gestion de cursos.
Es equivalente a `admin-app` (Spring Boot 2.7.18 + Thymeleaf + Flowable 6.8.0
embebido mediante API Java), pero centrada en las tres tareas del carril
docente del BPMN:

| taskDefinitionKey | Tarea | Quien la opera | Accion de esta app |
| --- | --- | --- | --- |
| `sid-D9626FE6-AE69-44AF-9389-E320BC348EDB` | Definir docente que desarrollara el curso | `jefeDocente` | Elige algun integrante del equipo y completa la tarea con la variable `docenteSeleccionado`. |
| `sid-895BA48F-B50A-4C10-9369-4CFEA6D67AC2` | Desarrollar curso | El docente elegido | Boton "Registrar finalizacion" (completa la tarea sin variables). |
| `sid-EDDAC05F-B007-4E01-8223-713A621E1188` | Elaborar informe de resultados | `jefeDocente` | Formulario de texto que guarda la variable `informe` y completa la tarea. |

Las variables del proceso que se muestran en la tabla son `empresa` y
`tematica`. No se sube ni se genera ningun PDF: el informe es solo texto,
igual que la interfaz simple de `web-app`.

## Arranque

Requisitos previos:

1. Docker/Postgres del motor levantado segun `proyecto/shared` (base
   `flowable`, usuario `flowable`, password `flow123`, puerto `5432`).
2. Instalar el jar compartido con los listeners del BPMN:

desde la raiz del proyecto ejecutar:
   ```bash
   mvn -f proyecto/shared/pom.xml install
   ```

3. Arrancar esta app en el puerto **8083**:

```bash
./mvnw test        # compila y corre tests
./mvnw package     # genera el jar en target/
./mvnw spring-boot:run
```
## Usuarios de prueba

Del grupo IDM `docentes` (clave = usuario, ver `proyecto/shared/README.md`):

- `jefeDocente` — unico usuario que puede definir docentes y cargar informes.
- `docente1`, `docente2` — integrantes que pueden reclamar y completar tareas.

El login valida `IdentityService.checkPassword` **y** la pertenencia al grupo
`docentes`. El nombre del jefe y la lista de docentes se pueden cambiar en
`application.properties` (`flowable.rol.jefe-docente` y `flowable.rol.docentes`).

## Estructura

```
src/main/java/com/proyecto/docente/
├── DocenteAppApplication.java          # arranque y salida limpia si cae la BD
├── config/
│   ├── AutenticacionInterceptor.java   # exige sesion en todas las rutas privadas
│   ├── ConexionBaseDeDatosFailureAnalyzer.java
│   ├── FlowableStartupLogger.java      # comprueba engine/TaskService al arrancar
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
    ├── TareaService.java               # consultas, claim y complete
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
  el usuario sea candidato directo (`taskCandidateUser`) o del grupo
  `docentes` (`taskCandidateGroup`).
- `Definir docente` y `Elaborar informe` requieren ademas el rol de jefe; la
  interfaz oculta sus acciones para otros usuarios y el servicio lo vuelve a
  validar.
- A diferencia de `admin-app`, no se guarda la contrasena en la sesion: la API
  Java de Flowable usa el `ProcessEngine` configurado contra la BD del motor.

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

Los tests son unitarios con mocks de Flowable: no necesitan Docker ni el jar
de `shared` instalado. Comprueban login por grupo, validacion de jefe al
definir docente, guardado de la variable `docenteSeleccionado` y rechazo de
informe vacio.
