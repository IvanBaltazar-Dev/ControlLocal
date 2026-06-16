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
AWS RDS MySQL o Aurora MySQL
```

Durante el desarrollo, Visual Studio y GlassFish se ejecutan localmente. La base
de datos configurada actualmente usa un endpoint de AWS RDS compatible con
MySQL. El mismo acceso JDBC funciona con Aurora MySQL.

## 1. Configurar AWS RDS MySQL o Aurora MySQL

La instancia o el cluster debe tener:

- una base de datos llamada `controllocal`;
- un usuario propio para la aplicacion, no el usuario maestro;
- el esquema de `database/01_create_schema_controllocal.sql`;
- los catalogos de `database/02_seed_catalogs.sql`;
- los usuarios de `database/03_seed_initial_users.sql`;
- acceso al puerto 3306 limitado por Security Groups.

Como la base inicial esta vacia, ejecuta los scripts `00` a `03` en orden. Para
contar con informacion de prueba, ejecuta tambien
`database/04_seed_demo_data.sql`.

Si la base ya existia antes de restringir el sistema a alquiler comercial,
ejecuta una vez `database/05_restrict_alquiler_comercial.sql`.

Para habilitar el flujo separado de visitas en una base ya creada, ejecuta
tambien una vez `database/06_visita_flujo_estados.sql`.

Para desarrollo desde una PC local, AWS debe ser alcanzable mediante la red
autorizada por el docente o la institucion. No se debe abrir el puerto 3306 a
todo Internet.

## 2. Configurar JDBC directo

La aplicacion abre conexiones exclusivamente con `DriverManager`. No usa JNDI,
`DataSource` ni recursos JDBC administrados por GlassFish.

La configuracion privada se edita desde el modulo `db-manager`, pero Maven la
excluye expresamente del JAR y del WAR:

```text
backend-java/controllocal-db-manager/src/main/resources/db.properties
```

```properties
db.host=your-rds-endpoint.rds.amazonaws.com
db.port=3306
db.name=your_database
db.user=your_user
db.password=your_password
db.ssl=true
```

Para usar otra ubicacion, inicia Java o GlassFish con:

```text
-Ddb.config.path=D:/ruta/privada/db.properties
```

## 3. Configurar CORS y JWT

La configuracion privada del API tambien esta fuera del WAR:

```text
config/api.properties
```

```properties
api.environment=development
api.cors.origin=http://localhost:5232
api.token.secret=your_secret_with_at_least_32_characters
```

Su ruta puede cambiarse con:

```text
-Dapi.config.path=D:/ruta/privada/api.properties
```

Ambos archivos privados estan ignorados por Git. El archivo
`config/db.properties.example` documenta el formato sin publicar credenciales.

## 4. Compilar y desplegar Jakarta

Desde IntelliJ:

1. Abrir `backend-java/pom.xml` como proyecto Maven.
2. En la configuracion GlassFish mantener `controllocal-rest:war exploded`.
3. Agregar `-Ddb.config.path` y `-Dapi.config.path` a las opciones de la JVM
   cuando GlassFish no se inicie desde la raiz del repositorio.
4. Ejecutar GlassFish con `Run`.

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

Las credenciales iniciales se provisionan de forma privada antes de ejecutar
`database/03_seed_initial_users.sql`. No se publican en el repositorio.

### Validar alquiler comercial y cierre de visitas

1. Ingresa como agente y abre `Nueva captacion`.
2. Confirma que la operacion se muestra fija como `Alquiler comercial`.
3. Programa una visita sobre una oportunidad abierta.
4. Registra un resultado `Interesado` o `Seguimiento`: la visita debe quedar
   `Realizada` y la oportunidad debe seguir abierta.
5. Registra otra visita con resultado `No interesado` o `Descartado`, indicando
   el motivo: la visita debe quedar `Realizada` y la oportunidad `No continua`.
6. Recarga el navegador. Ambos estados deben conservarse porque oportunidades
   y visitas se leen y actualizan mediante el API REST.

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
