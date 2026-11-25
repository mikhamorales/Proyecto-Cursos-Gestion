# analisis-app

Aplicacion web de **solo lectura** que muestra el analisis de las ejecuciones
del proceso de gestion de cursos (requisito 4.3). Al abrir `http://localhost:8084/`
se ven tres informes calculados a partir del **historial de Flowable**:

| Seccion | Que muestra |
| --- | --- |
| Resultados de las solicitudes | Cantidad y porcentaje de instancias terminadas con cada resultado: curso finalizado, cancelacion, rechazo de la propuesta, solicitud no viable y cierre por falta de respuesta. El porcentaje se calcula sobre el total de instancias terminadas. |
| Analisis de la preparacion paralela | Duracion promedio de las ramas de inscripciones, materiales y recursos, medidas desde la activacion hasta la finalizacion. Solo se consideran preparaciones que llegaron a la compuerta *Preparacion completa*; se excluyen las interrumpidas por cancelacion. |
| Distribucion de tareas | Cantidad de tareas de usuario completadas por cada administrativo y docente. Se excluyen tareas pendientes e interrumpidas/canceladas. |

Cada seccion incluye una tabla y una barra proporcional para leer los valores
de un vistazo. No se escriben datos ni se modifica el motor.

## Como funciona

La app consulta la API REST de historial de Flowable (`process-api`) con
autenticacion basica:

- `history/historic-process-instances`: instancias terminadas del proceso.
- `history/historic-variable-instances`: variable `resultado` de cada instancia.
- `history/historic-activity-instances`: actividades para medir las ramas.
- `history/historic-task-instances`: tareas completadas y su `assignee`.

Las consultas son paginadas. Las instancias borradas administrativamente
(`deleteReason` en la instancia historica) y las tareas con un motivo de fin
distinto de vacio no se cuentan. Los resultados no reconocidos se agrupan en
`Sin clasificar`, de modo que los porcentajes siempre suman el total de
instancias terminadas.

La duracion de cada rama se calcula como `max(fin) - min(inicio)` de las
actividades configuradas para esa rama. En la rama de recursos eso incluye
todas las acciones seleccionadas (aula fisica, aula virtual y equipamiento),
por lo que termina cuando la ultima de ellas finaliza.

## Requisitos

- JDK 17 o superior y Maven 3.9+ (o el wrapper de Maven si se agrega al modulo).
- Un motor Flowable 6.8 con la API REST publicada y el historial habilitado.
- Un usuario con permisos de lectura sobre esa API. Las credenciales por
  defecto son las mismas que usa `web-app` para su cliente REST.

No hace falta base de datos propia, ni instalar el JAR de listeners: esta app
no arranca un motor embebido.

## Configuracion

Todos los valores estan en `src/main/resources/application.properties` y se
pueden sobrescribir con variables de entorno:

| Variable de entorno | Valor por defecto | Uso |
| --- | --- | --- |
| `FLOWABLE_REST_URL` | `http://localhost:8080/flowable-ui/process-api` | Base de la API REST del motor. |
| `FLOWABLE_REST_USER` | `admin` | Usuario de servicio. |
| `FLOWABLE_REST_PASSWORD` | `test` | Password de ese usuario. |
| `FLOWABLE_PROCESS_KEY` | `process` | Clave de la definicion del proceso. |

El puerto de la app (`server.port`, 8084 por defecto) y las listas de usuarios
y de nombres de actividades se ajustan en el mismo archivo. Si el BPMN se
redespliega con otros nombres de actividad, hay que actualizar las propiedades
`analisis.preparacion.*` para que las mediciones sigan funcionando.

## Arranque

Desde la raiz del repositorio:

```bash
mvn -f proyecto/analisis-app/pom.xml test
mvn -f proyecto/analisis-app/pom.xml spring-boot:run
```

Tambien se puede generar el JAR y ejecutarlo:

```bash
mvn -f proyecto/analisis-app/pom.xml package
java -jar proyecto/analisis-app/target/analisis-app-0.0.1-SNAPSHOT.jar
```

Luego abrir `http://localhost:8084/`. No hay login: la pagina solo consulta y
muestra datos, igual que un panel de reportes interno.

## Estructura

```text
src/main/java/com/proyecto/analisis/
├── AnalisisAppApplication.java        # arranque de la aplicacion
├── client/FlowableHistoryClient.java  # consultas REST paginadas al historial
├── config/RestConfig.java             # Basic Auth para cada llamada
├── controller/HomeController.java     # GET / y manejo de errores de conexion
├── model/                             # DTOs del historial y de las vistas
└── service/AnalisisService.java       # calculo de los tres informes
src/main/resources/
├── application.properties
├── static/css/estilos.css
└── templates/index.html
src/test/java/com/proyecto/analisis/
├── controller/HomeControllerVistaTest.java  # renderizado de la pagina
└── service/AnalisisServiceTest.java         # calculo de los informes
```

## Tests

Los tests no necesitan Docker ni un motor levantado: usan un cliente simulado
y `MockMvc` para la vista. Cubren el calculo de porcentajes, la exclusion de
preparaciones canceladas, las duraciones promedio, el conteo de tareas
completadas y el renderizado de las tres secciones.

Si la pagina muestra el mensaje de error de conexion, revisar que la API REST
responda y que las credenciales configuradas sean validas. Si las tablas
aparecen vacias, revisar que el proceso este desplegado y que existan
instancias terminadas en el historial.
