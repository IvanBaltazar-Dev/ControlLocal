# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

ControlLocal manages and audits the commercial process of renting out commercial premises for a real-estate brokerage. The domain flow is: `Propietario` → `LocalComercial` → `Captacion` (agent captures, broker reviews) → `OportunidadComercial` → `Interaccion`/`Visita` → `SolicitudAlquiler` → `Documentos` → `EvaluacionSolicitud` (broker) → `ContratoAlquiler`/`Comision`. `OportunidadComercial` is the hub entity that keeps traceability even when a lead never files a formal request. Three roles: **broker administrador**, **broker supervisor**, **agente inmobiliario** — the broker supervises/decides, the agent registers/operates.

The domain vocabulary (entity, enum, and method names) is Spanish; keep it Spanish when adding code. Comments in the codebase are Spanish.

## Repository layout

**Migration in progress (Strangler)**: the stack is being rewritten to Spring Boot + Angular + PostgreSQL behind the FROZEN REST contract. `backend-spring/` + `frontend-angular/` are the new stack; `backend-java/` + `frontend-csharp/` remain the legacy stack until each module is cut over. Plan and target architecture live in `docs/ai/plan-migracion-java-fullstack.md` and `docs/ai/arquitectura-objetivo-java-fullstack.md`; current migration status and vertical-specific decisions live in `backend-spring/README.md`.

- `backend-spring/` — **new** backend: Spring Boot 3.5 Maven reactor (`controllocal-domain` → `controllocal-persistence` → `controllocal-service` → `controllocal-web` → `controllocal-app`), PostgreSQL v2 (Party-Role) migrated by Flyway, JWT byte-compatible with the legacy backend. Base URL `http://localhost:8090/controllocal/Api`.
- `frontend-angular/` — **new** frontend: Angular 20 SPA (standalone, signals; `core/` auth + `features/` + `layout/`), consumes the frozen contract at port 8090.
- `backend-java/` — legacy Maven reactor (Java 21, Jakarta REST + JDBC → GlassFish). Modules: `controllocal-model` → `controllocal-dao` → `controllocal-db-manager` → `controllocal-bl` → `controllocal-rest`.
- `frontend-csharp/ControlLocal.Web/` — legacy Blazor Server app (.NET 10) being replaced screen-by-screen.
- `database/` — legacy MySQL schema, three scripts: `00_recreate…`, `01_create_schema…`, `02_seed_base_data.sql` (MySQL / RDS). The new PostgreSQL v2 schema lives in `backend-spring/controllocal-app/src/main/resources/db/migration/` (Flyway owns it; never edit applied migrations).
- `docs/ai/` — migration docs (estado actual, inventarios, plan, arquitectura objetivo). **`docs/ai/checklist-migracion.md` is the one to open first**: everything still missing plus the suggested order of work and the reasoning behind it (its section 0). `docs/ai/matriz-operacion-rol.md` is the source of truth for **who can call what and where scope is decided** — read it before building any role-aware screen; it is enforced by a test. `docs/ai/seguridad-no-leer.md` is named "do not read" by convention — leave it alone.

### New stack: build & run (backend-spring + frontend-angular)

```powershell
# Backend: build + tests (needs JDK 21+ on JAVA_HOME)
mvn -f backend-spring/pom.xml clean install

# Run EVERYTHING for dev: PostgreSQL v2 (5433) + API in a container (8090)
docker compose -f backend-spring/docker-compose.yml up -d

# Frontend dev server (4200); login with the seed users below
npm --prefix frontend-angular start
```

- **Java servers cannot be launched from Codex shell sessions on this machine** (harness-descended JVMs fail to create the Windows NIO selector loopback pipe → "Unable to establish loopback connection"). Run the API **inside Docker** (`docker compose … up -d api`, wired into `.Codex/launch.json` as `api-v2`) or from IntelliJ. Node servers are unaffected.
- After changing Flyway migrations, **repackage the app jar** (`mvn -pl controllocal-app install -DskipTests` from `backend-spring/`) — Flyway reads the classpath, not the source tree — and restart the `api` container. The `api` container mounts the jar from `controllocal-app/target/`, so after any `mvn install` a `docker restart controllocal-api-v2` is enough to pick up the new code.
- The E2E scripts in `backend-spring/verificacion/` are written for **Windows PowerShell 5.1** (`powershell -File …`); `pwsh` is not installed on this machine.
- **Serena is configured for the target stack: `languages: [java, typescript]`** (`.serena/project.yml`). Do **not** add `csharp` back — its server needs the .NET 10 runtime, which isn't installed, and one failed server takes down the whole manager (Java symbols included). Do **not** use `angular` either: it requires `npm install` at the repo root, but the SPA lives in `frontend-angular/`. Build output (`target/`, `node_modules/`, `dist/`, `.angular/`, `bin/`, `obj/`) is in `ignored_paths`; the legacy trees stay indexed on purpose because `backend-java/` is the source of truth for the frozen contract. Config changes only take effect when the Serena MCP server restarts.
- Postgres v2 uses `VARCHAR(1)` for the legacy 1-char codes (`CHAR(1)` is `bpchar` in Postgres and breaks Hibernate `ddl-auto: validate`).
- Seed credentials (parity with legacy): `admin@controllocal.test`/Admin2026, `rsalas`…`sramirez`/Broker2026, `vmora`…`rgomez`/Agente2026.
- Frozen-contract rule: DTO shapes, error bodies (`{"error": …}`), status codes, exact 401/403/429 messages, and the HS256 token format/secret are byte-compatible with `backend-java`; JSON omits nulls (Jackson `non_null` = Yasson parity). Don't change any of it until the legacy backend is retired for that module.
- Four gates break the build: strict layers (`app → web → service → persistence → domain`), "all state transitions go through `service/soporte/Transiciones`", "every private entity carries the tenant discriminator", and the **operation→role matrix** — `MatrizOperacionRolTest` parses `docs/ai/matriz-operacion-rol.md` and fails if it drifts from the controllers, so **a new endpoint needs a row there** (method, path, roles, and where scope is decided).
- **PDF reports are out of scope for the migration** (D-F5-1, `docs/ai/decision-reportes-pdf-fuera-de-alcance.md`): the v1's 5 Jasper endpoints are not ported and no replacement technology is chosen yet. Don't add "Exportar PDF" buttons to the Angular SPA.

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
