# Como ejecutar ControlLocal desde los IDE

## Arquitectura

```text
Frontend Blazor en Visual Studio
        |
        | HTTP/JSON
        v
API Jakarta REST en GlassFish
        |
        | JDBC/MySQL con TLS
        v
Amazon Aurora MySQL (RDS)
```

Durante el desarrollo, Visual Studio y GlassFish se ejecutan localmente. La base
de datos puede estar en Aurora. Cuando se configure EC2, el WAR de Jakarta se
desplegara alli y el frontend solo cambiara la URL base del API.

## 1. Configurar Aurora MySQL

Aurora debe tener:

- una base de datos llamada `controllocal`;
- un usuario propio para la aplicacion, no el usuario maestro;
- el esquema de `database/01_create_schema_controllocal_v3.sql`;
- los datos iniciales de `database/02_seed_initial_data.sql`;
- acceso al puerto 3306 limitado por Security Groups.

Para desarrollo desde una PC local, Aurora debe ser alcanzable mediante la red
autorizada por el docente o la institucion. No se debe abrir el puerto 3306 a
todo Internet.

## 2. Configurar la conexion desde `controllocal-db-manager`

La conexion se configura en el archivo privado:

```text
backend-java/controllocal-db-manager/src/main/resources/db.properties
```

Contenido esperado:

```properties
db.host=ENDPOINT-DE-AURORA
db.port=3306
db.name=controllocal
db.username=USUARIO_DE_APLICACION
db.password=CONTRASENA
db.sslMode=REQUIRED
db.serverTimezone=UTC
db.allowPublicKeyRetrieval=false
db.pool.max=10
```

`db.allowPublicKeyRetrieval` debe terminar en `false`. La configuracion CORS no
va pegada en esa linea.

## 3. Configurar CORS y JWT

La configuracion privada del API esta en:

```text
backend-java/controllocal-rest/src/main/resources/api.properties
```

```properties
api.environment=development
api.cors.origin=http://localhost:5232
api.token.secret=SECRETO_DE_AL_MENOS_32_CARACTERES
```

`db.properties` y `api.properties` estan ignorados por Git. Los archivos
`*.example.properties` documentan el formato sin publicar credenciales.

No es necesario ingresar a `http://localhost:4848` ni agregar opciones JVM.

## 4. Compilar y desplegar Jakarta

Desde IntelliJ:

1. Abrir `backend-java/pom.xml` como proyecto Maven.
2. En la configuracion GlassFish mantener `controllocal-rest:war exploded`.
3. Ejecutar GlassFish con `Run`.

El archivo `WEB-INF/glassfish-web.xml` fija el context root `controllocal`, por
lo que no es necesario marcar `Use custom context root` en IntelliJ.

Comprobar en el navegador:

```text
http://localhost:8080/controllocal/Api/salud
```

No se usa `/webresources/*`. La raiz REST es `/controllocal/Api`.

## 5. Ejecutar el frontend en Visual Studio

1. Abrir `frontend-csharp/ControlLocal.Web/ControlLocal.Web.csproj`.
2. Seleccionar el perfil `ControlLocal API`.
3. Ejecutar con `F5`.
4. Abrir `http://localhost:5232/login`.

La barra lateral debe indicar `MODO: API REST`.

Credenciales de los datos iniciales:

```text
Usuario: admin@controllocal.test
Contrasena: Admin123*
```

## 6. Cambio futuro a EC2

Cuando Jakarta se despliegue en EC2, cambiar `Api:BaseUrl` en la configuracion
del frontend:

```json
{
  "Api": {
    "Enabled": true,
    "BaseUrl": "https://api.dominio.example/controllocal/Api",
    "TimeoutSeconds": 15
  }
}
```

En EC2, el Security Group de Aurora debe aceptar 3306 solamente desde el
Security Group de EC2. El API debe publicarse por HTTPS mediante un balanceador
o proxy inverso; GlassFish no debe exponerse directamente a Internet.
