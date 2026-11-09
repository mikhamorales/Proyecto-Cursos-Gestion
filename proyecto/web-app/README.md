# web-app — Cliente REST de Flowable

Aplicación web del proyecto de gestión de cursos. Es un cliente HTTP de la API REST de Flowable: **no** incluye Flowable, **no** depende del módulo `shared` y **no** ejecuta el motor. El motor corre por fuera, en Docker (`flowable-ui`).

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
| `flowable.rest.base-url` | `http://localhost:8080/flowable-ui/process-api` | Base de la API REST de Flowable |
| `flowable.rest.username` | `admin` | Usuario de la API REST |
| `flowable.rest.password` | `test` | Clave de la API REST |

Ajustá URL, puerto y credenciales a lo que use el `docker-compose.yml` del equipo. Todavía no hay código que consuma estas propiedades; se usan en issues posteriores.

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

## Cómo verificar

Abrí http://localhost:8081 y deberías ver la página de inicio ("Web casera Flowable: esqueleto funcionando").

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
    │   │   ├── controller/        # Controladores MVC (pantallas)
    │   │   ├── service/           # Lógica de la aplicación
    │   │   ├── client/            # Cliente HTTP hacia la API REST de Flowable
    │   │   ├── model/             # DTOs (instancias, tareas, variables)
    │   │   └── config/            # Configuración de beans
    │   └── resources/
    │       ├── application.properties
    │       ├── static/
    │       │   ├── css/
    │       │   └── js/
    │       └── templates/
    │           └── index.html
    └── test/java/com/proyecto/web/
        └── WebApplicationTests.java
```

Cada paquete vacío tiene un `package-info.java` con su propósito, para que Git conserve la carpeta.

## Problemas comunes

- **Puerto 8081 ocupado**: cambiá `server.port` en `application.properties`.
- **Flowable no responde**: verificá que Docker y el contenedor estén arriba (`docker compose ps`).
- **Credenciales incorrectas**: revisá `flowable.rest.username` / `flowable.rest.password` contra el `docker-compose.yml`.
