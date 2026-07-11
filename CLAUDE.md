# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ControlLocal manages and audits the commercial process of renting out commercial premises for a real-estate brokerage. The domain flow is: `Propietario` → `LocalComercial` → `Captacion` (agent captures, broker reviews) → `OportunidadComercial` → `Interaccion`/`Visita` → `SolicitudAlquiler` → `Documentos` → `EvaluacionSolicitud` (broker) → `ContratoAlquiler`/`Comision`. `OportunidadComercial` is the hub entity that keeps traceability even when a lead never files a formal request. Three roles: **broker administrador**, **broker supervisor**, **agente inmobiliario** — the broker supervises/decides, the agent registers/operates.

The domain vocabulary (entity, enum, and method names) is Spanish; keep it Spanish when adding code. Comments in the codebase are Spanish.

## Repository layout

- `backend-java/` — Maven multi-module reactor (Java 21). Modules build in dependency order: `controllocal-model` → `controllocal-dao` → `controllocal-db-manager` → `controllocal-bl` → `controllocal-rest`.
- `frontend-csharp/ControlLocal.Web/` — Blazor Server app (.NET 10, InteractiveServer render mode) that consumes the REST API.
- `database/` — the entire schema is three scripts: `00_recreate_database_controllocal.sql`, `01_create_schema_controllocal.sql`, `02_seed_base_data.sql` (MySQL / RDS).
- `docs/ai/*.md` currently exist but are **empty placeholders** (0 bytes). `docs/ai/seguridad-no-leer.md` is named "do not read" by convention — leave it alone.

## Build, run, test

### Backend (Java)

The build targets `maven.compiler.release=21`. The machine's default `JAVA_HOME` may point at an older JDK (e.g. 17) which **breaks the build** — point it at a JDK 21+ first. In practice the day-to-day loop is **IntelliJ building + deploying to GlassFish**, not CLI Maven; use Maven for verification/CI.

```powershell
# From backend-java/ — ensure JAVA_HOME is JDK 21+ for the session:
$env:JAVA_HOME = "C:\Path\To\jdk-21-or-newer"
mvn -f backend-java/pom.xml clean install          # full reactor (modules are unpublished, so install)
mvn -f backend-java/pom.xml -pl controllocal-rest -am package   # produce the deployable WAR
```

- Deploy `controllocal-rest/target/*.war` to **GlassFish**. `context-root` is `/controllocal` (`glassfish-web.xml`) and the JAX-RS `@ApplicationPath` is `Api` (`RestApplication.java`), so the API base URL is `http://localhost:8080/controllocal/Api`. Public endpoints: `GET /salud`, `POST /auth/login`.

**Tests** are JUnit 5. Note that most DAO tests are **live-DB integration tests** (`*IntegrationTest`, `*ManualTest`) and need a running MySQL configured via `db.properties`; the ArchUnit test and model tests run standalone.

```powershell
mvn -f backend-java/pom.xml test                                          # all modules
mvn -f backend-java/pom.xml -pl controllocal-rest -am test -Dtest=ArquitecturaCapasTest   # single test class
```

### Frontend (Blazor)

Requires the backend API reachable at `Api__BaseUrl` (default `http://localhost:8080/controllocal/Api`, set in `Properties/launchSettings.json`).

```powershell
# From frontend-csharp/ControlLocal.Web/
dotnet run          # serves http://localhost:5232, opens /login
dotnet watch        # hot reload
dotnet build
```

### Database

Recreate by running the three scripts in order (`00` drops/recreates, `01` schema, `02` seed). There is no numbered-migration system — extend `01` (schema) / `02` (seed) and keep `00`→`01`→`02` runnable end-to-end rather than adding new `.sql` files.

## Architecture

### Strict layering (enforced by a test)

`Frontend → REST → BL → DAO → DBManager`. `controllocal-rest/.../arquitectura/ArquitecturaCapasTest.java` (ArchUnit) **fails the build** if the REST layer depends on `com.controllocal.dao..` or on the DB-connection package `com.controllocal.config..`. REST must only talk to BL; only BL touches DAOs; only DAOs (via `com.controllocal.config.DBManager`) touch the DB. Keep the DB connection out of REST.

### Java package/wiring conventions

- Interfaces live in `com.controllocal.bl` and `com.controllocal.dao`; implementations in the `.impl` sub-package (`XxxBusinessLogicImpl`, `XxxDAOImpl`); shared helpers in `com.controllocal.bl.support`.
- **Wiring is manual instantiation, not DI.** A REST resource holds `private final XxxBusinessLogic x = new XxxBusinessLogicImpl();`. A BL impl's no-arg constructor calls `new XxxDAOImpl()`. Additional constructors that accept DAO/BL interfaces exist **only for test injection** — follow that pattern (default ctor for production, interface-accepting ctor for tests) when adding a class.
- `controllocal-model` holds pure entities + enums grouped by domain: `comercial`, `inmueble`, `persona`, `usuario`, each with an `enums` sub-package.
- REST cross-cutting: JWT auth (`seguridad/JwtAuthFilter`, `TokenService`), CORS, rate limiting; errors go through `http/ApiExceptionMapper`; pagination via `http/PageResponse`; DTOs in `dto/Dtos.java`. Document storage is hybrid S3-or-disk under `rest/almacen/`; PDF reports use JasperReports under `rest/reports/` (`.jrxml`/`.jasper` templates in `resources/reports/`).

### Frontend conventions

- Pages are `.razor` files under `Components/Pages/` (feature/role oriented); `Components/Layout/` and `Components/Shared/` hold chrome and reusable bits.
- Each backend module is consumed through an `Http<Name>Service` implementing an `I<Name>Service` interface, all registered **Scoped** in `Program.cs` and going through `Services/Api/ApiClient.cs` (which carries the JWT via `ApiSession`). When adding an API-backed feature, add the interface + `Http*` impl and register it in `Program.cs`.
- Blazor Server state is per-circuit: `AppState` (active role + navigation) is Scoped; `NotificacionStore` is a Singleton with a Scoped per-user view. Route authorization is in `Services/RouteAccess.cs`.
- Do **not** use `@Assets` for images — they break under interactive server render and are served no-cache; reference images by plain path.

## Private configuration (never commit)

These are gitignored and must be created from the `.example` siblings:

- `backend-java/controllocal-rest/src/main/resources/api.properties` and `aws.properties`
- `backend-java/controllocal-db-manager/src/main/resources/db.properties` (keys: `db.host/port/name/user/password/ssl`)
- `frontend-csharp/ControlLocal.Web/appsettings.json`

Do not edit or version these `*.properties`/`appsettings.json` files with real credentials.

## Commits

Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
