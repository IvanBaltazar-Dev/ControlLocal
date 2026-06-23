# Como Probar ControlLocal

Esta guia sirve para levantar el proyecto desde cero y comprobar que backend, base de datos y frontend trabajan juntos.

## Mapa De Ejecucion

```text
Frontend Blazor
http://localhost:5232
        |
        | HTTP/JSON + JWT
        v
API Jakarta REST en GlassFish
http://localhost:8080/controllocal/Api
        |
        | JDBC directo
        v
MySQL / AWS RDS / Aurora MySQL
base: controllocal
```

## Prerrequisitos

| Herramienta | Uso |
| --- | --- |
| Java 21 o superior | Compilar y ejecutar los modulos Maven. |
| Maven 3.9 o superior | Construir el backend Java. |
| GlassFish compatible con Jakarta EE | Desplegar el WAR `controllocal-rest`. |
| MySQL 8, AWS RDS MySQL o Aurora MySQL | Persistencia de datos. |
| .NET SDK compatible con `net10.0` | Ejecutar el frontend Blazor. |
| Visual Studio, IntelliJ o IDE similar | Opcional, pero recomendado para desarrollo. |

## 1. Preparar La Base De Datos

Ejecuta los scripts SQL en este orden:

1. `database/00_recreate_database_controllocal.sql`
2. `database/01_create_schema_controllocal.sql`
3. `database/02_seed_base_data.sql`
4. `database/03_seed_demo_data.sql` si quieres datos amplios para probar pantallas.

Notas importantes:

- El script `00` es destructivo: elimina y vuelve a crear la base `controllocal`.
- El script `01` crea el esquema completo con claves, restricciones e indices.
- El script `02` carga catalogos obligatorios y usuarios demo.
- El script `03` carga escenarios operativos completos para navegar el sistema.

La documentacion especifica de base de datos esta en [database/README.md](database/README.md).

## 2. Crear Configuracion Privada

Los archivos reales de configuracion no deben subirse a Git. Crea copias locales desde los ejemplos.

### API

Archivo esperado:

```text
config/api.properties
```

Ejemplo:

```properties
api.cors.origin=http://localhost:5232
api.token.secret=ControlLocal-development-token-secret-2026
```

Si GlassFish no se inicia desde la raiz del repo, pasa la ruta con:

```text
-Dapi.config.path=D:/ruta/privada/api.properties
```

### Base De Datos

Archivo esperado:

```text
backend-java/controllocal-db-manager/src/main/resources/db.properties
```

Ejemplo:

```properties
db.host=your-rds-endpoint.rds.amazonaws.com
db.port=3306
db.name=controllocal
db.user=your_user
db.password=your_password
db.ssl=true
```

Tambien puedes usar una ruta externa:

```text
-Ddb.config.path=D:/ruta/privada/db.properties
```

### Frontend

Archivo recomendado:

```text
frontend-csharp/ControlLocal.Web/appsettings.json
```

Puedes partir de `appsettings.example.json`:

```json
{
  "Api": {
    "BaseUrl": "http://localhost:8080/controllocal/Api",
    "TimeoutSeconds": 15
  }
}
```

## 3. Compilar El Backend Java

Desde `backend-java/`:

```bash
mvn clean install
```

Para construir solo el WAR REST con sus dependencias:

```bash
mvn -pl controllocal-rest -am package
```

Artefacto esperado:

```text
backend-java/controllocal-rest/target/controllocal.war
```

## 4. Desplegar La API En GlassFish

En IntelliJ o el IDE que uses:

1. Abre `backend-java/pom.xml` como proyecto Maven.
2. Configura GlassFish con el artefacto `controllocal-rest:war exploded` o despliega `controllocal.war`.
3. Agrega en las opciones de VM las rutas de configuracion si corresponde:

```text
-Ddb.config.path=D:/ruta/privada/db.properties
-Dapi.config.path=D:/ruta/privada/api.properties
```

4. Inicia GlassFish.

El context root esta definido como `controllocal`, y la raiz JAX-RS es `Api`. Por eso la URL base es:

```text
http://localhost:8080/controllocal/Api
```

No uses `/webresources/*`; este proyecto expone REST bajo `/controllocal/Api`.

## 5. Probar Salud Del API

En navegador o PowerShell:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/controllocal/Api/salud"
```

Resultado esperado: respuesta JSON de salud sin requerir token.

Si falla:

- Revisa que GlassFish este iniciado.
- Revisa que el context root sea `controllocal`.
- Revisa que `RestApplication` este activo bajo `Api`.
- Revisa logs de GlassFish por errores de configuracion.

## 6. Probar Login REST

Credenciales cargadas por `database/02_seed_base_data.sql`:

| Rol | Usuario | Contrasena |
| --- | --- | --- |
| Admin | `admin@controllocal.test` | `Admin2026` |
| Broker | `rsalas` | `Broker2026` |
| Broker | `psoto` | `Broker2026` |
| Agente | `vmora` | `Agente2026` |
| Agente | `jruiz` | `Agente2026` |
| Agente | `ltorres` | `Agente2026` |
| Agente | `creyes` | `Agente2026` |

Ejemplo PowerShell:

```powershell
$body = @{
  usuario = "admin@controllocal.test"
  contrasena = "Admin2026"
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/controllocal/Api/auth/login" `
  -ContentType "application/json" `
  -Body $body

$login
```

La respuesta debe incluir:

- `token`
- `expiraEnSegundos`
- `rol`
- `idUsuario`
- `idDominio`
- `nombre`
- `usuario`
- `expiraEn`

El token dura 30 minutos. Para llamar endpoints privados:

```powershell
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/controllocal/Api/captaciones" `
  -Headers $headers
```

## 7. Ejecutar El Frontend Blazor

Desde `frontend-csharp/ControlLocal.Web/`:

```bash
dotnet run --launch-profile "ControlLocal API"
```

URL esperada:

```text
http://localhost:5232/login
```

Tambien puedes abrir el proyecto en Visual Studio:

1. Abre `frontend-csharp/ControlLocal.Web/ControlLocal.Web.csproj`.
2. Selecciona el perfil `ControlLocal API`.
3. Ejecuta con `F5`.
4. Inicia sesion con un usuario demo.

La aplicacion debe consumir:

```text
http://localhost:8080/controllocal/Api
```

## 8. Prueba Funcional Minima

### Como Agente

1. Inicia sesion con `vmora` / `Agente2026`.
2. Abre `Propietarios` y confirma que se listan datos.
3. Abre `Locales comerciales` y registra o revisa un local.
4. Abre `Captaciones` y crea una captacion.
5. Confirma que la operacion se maneja como alquiler.
6. Abre `Clientes interesados` y registra o revisa un cliente.
7. Crea una `Oportunidad comercial` vinculando cliente y captacion activa.
8. Programa una visita sobre la oportunidad.

### Como Broker

1. Inicia sesion con `rsalas` / `Broker2026`.
2. Abre `Captaciones por revisar`.
3. Aprueba, observa o rechaza una captacion.
4. Abre `Solicitudes por evaluar`.
5. Evalua una solicitud como aprobada, observada o rechazada.

### Validar Cierre De Visitas

1. Ingresa como agente.
2. Programa una visita sobre una oportunidad abierta.
3. Registra resultado `INTERESADO` o `SEGUIMIENTO`.
4. La visita debe quedar `REALIZADA` y la oportunidad debe seguir abierta.
5. Registra otra visita con resultado `NO_INTERESADO` o `DESCARTADO`.
6. Indica el motivo de no continuidad.
7. La visita debe quedar `REALIZADA` y la oportunidad debe quedar `NO_CONTINUA`.

## 9. Pruebas De Compilacion Y Tests

Prueba rapida de compilacion Java:

```bash
cd backend-java
mvn clean test-compile
```

Pruebas unitarias del modelo:

```bash
cd backend-java
mvn -pl controllocal-model test
```

Pruebas DAO o de integracion:

```bash
cd backend-java
mvn -pl controllocal-dao test
```

Estas pruebas requieren que la configuracion de BD apunte a una base accesible y preparada.

Compilacion frontend:

```bash
cd frontend-csharp/ControlLocal.Web
dotnet build
```

## 10. Fallas Comunes

| Sintoma | Causa probable | Que revisar |
| --- | --- | --- |
| `404` en `/controllocal/Api/salud` | Context root o raiz REST incorrecta | GlassFish, `glassfish-web.xml`, `RestApplication`. |
| `401 Token requerido` | Endpoint privado sin JWT | Hacer login y enviar `Authorization: Bearer <token>`. |
| `401 Token invalido o expirado` | Token vencido o secreto distinto tras reinicio | Hacer login otra vez; configurar `api.token.secret`. |
| Error de CORS | Origen frontend no autorizado | `api.cors.origin=http://localhost:5232`. |
| Error JDBC | Credenciales, host o SSL incorrectos | `db.properties`, Security Group, puerto 3306. |
| Blazor no carga datos | API apagada o `Api:BaseUrl` incorrecto | `appsettings.json` o perfil `ControlLocal API`. |

## 11. Cambio Futuro A EC2

Cuando el API se despliegue fuera de local:

1. Publica la API por HTTPS.
2. No expongas GlassFish directamente a Internet.
3. Usa un balanceador o proxy inverso.
4. Permite 3306 en Aurora/RDS solo desde el Security Group de EC2.
5. Cambia `Api:BaseUrl` en el frontend:

```json
{
  "Api": {
    "BaseUrl": "https://api.dominio.example/controllocal/Api",
    "TimeoutSeconds": 15
  }
}
```
