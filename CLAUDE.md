# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ControlLocal manages and audits the commercial process of renting out commercial premises for a real-estate brokerage. The domain flow is: `Propietario` → `LocalComercial` → `Captacion` (agent captures, broker reviews) → `OportunidadComercial` → `Interaccion`/`Visita` → `SolicitudAlquiler` → `Documentos` → `EvaluacionSolicitud` (broker) → `ContratoAlquiler`/`Comision`. `OportunidadComercial` is the hub entity that keeps traceability even when a lead never files a formal request. Three roles: **broker administrador**, **broker supervisor**, **agente inmobiliario** — the broker supervises/decides, the agent registers/operates.

The domain vocabulary (entity, enum, and method names) is Spanish; keep it Spanish when adding code. Comments in the codebase are Spanish.

## Repository layout

**The legacy stack was DELETED on 2026-08-08.** `backend-java/` (Jakarta REST → GlassFish), `frontend-csharp/` (Blazor) and `database/` (MySQL) are gone from the working tree — they never ran in production and never will, so there was nothing to migrate and nothing to keep in step with. The code stays in git history if it is ever needed (`git checkout <commit> -- backend-java/`).

**What this changes, and it is not cosmetic**: the FROZEN REST contract existed *only* so the two backends could coexist. With no v1 there is nothing to be byte-compatible with, so the freeze is due to be lifted — see `docs/ai/plan-maestro-ruta-a-produccion.md` Fase 4. **Until that second batch lands, treat the contract as still frozen**: the SPA consumes today's shapes, and unfreezing is a deliberate, tested change, not a licence to edit responses ad hoc.

- `backend-spring/` — the backend: Spring Boot 3.5 Maven reactor (`controllocal-domain` → `controllocal-persistence` → `controllocal-service` → `controllocal-web` → `controllocal-app`), PostgreSQL v2 (Party-Role) migrated by Flyway. Base URL `http://localhost:8090/controllocal/Api`. Flyway owns the schema; **never edit an applied migration**.
- `frontend-angular/` — the frontend: Angular 20 SPA (standalone, signals; `core/` auth + `features/` + `layout/`), consumes the API at port 8090.
- `docs/ai/` — migration docs (estado actual, inventarios, plan, arquitectura objetivo) and the frozen REST contracts per vertical (`contrato-congelado-f2…`, `-f3-demanda`, `-f4-solicitud`). **`docs/ai/plan-maestro-ruta-a-produccion.md` is the top-level roadmap**: the 7 agreed phases to production (security/identity → operational protection → business rules → legacy cutover → production architecture → multi-tenancy → AI), each item labelled done / partial / planned / pending / future, plus the list of plans already closed. **`docs/ai/checklist-migracion.md` is the one to open first for the migration itself** (it is phase 4 of that roadmap): everything still missing (backend *and* frontend) plus the suggested order of work and the reasoning behind it (its section 0). `docs/ai/matriz-operacion-rol.md` is the source of truth for **who can call what and where scope is decided** — read it before building any role-aware screen; it is enforced by a test. `docs/ai/seguridad-no-leer.md` is named "do not read" by convention — leave it alone.

### New stack: build & run (backend-spring + frontend-angular)

```powershell
# Backend: build + tests (needs JDK 21+ on JAVA_HOME)
mvn -f backend-spring/pom.xml clean install

# Run EVERYTHING for dev: PostgreSQL v2 (5433) + API in a container (8090)
docker compose -f backend-spring/docker-compose.yml up -d

# Frontend dev server (4200); login with the seed users below
npm --prefix frontend-angular start

# Optional: S3-compatible storage (MinIO on 9000, console 9001) + its bucket.
# Behind the `s3` profile, so a plain `up -d` does NOT start it.
docker compose -f backend-spring/docker-compose.yml --profile s3 up -d minio minio-init
```

- **Java servers cannot be launched from Claude Code shell sessions on this machine** (harness-descended JVMs fail to create the Windows NIO selector loopback pipe → "Unable to establish loopback connection"). Run the API **inside Docker** (`docker compose … up -d api`, wired into `.claude/launch.json` as `api-v2`) or from IntelliJ. Node servers are unaffected.
- After changing Flyway migrations, **repackage the app jar** (`mvn -pl controllocal-app install -DskipTests` from `backend-spring/`) — Flyway reads the classpath, not the source tree — and restart the `api` container. The `api` container mounts the jar from `controllocal-app/target/`, so after any `mvn install` a `docker restart controllocal-api-v2` is enough to pick up the new code.
- **Binary storage has two providers** (`AlmacenDocumentos` is the only frontier): `ALMACEN_PROVEEDOR=DISCO` (the default, `matchIfMissing`) or `S3`. `AlmacenS3` speaks **generic S3** — endpoint and `path-style` are configurable — so it works against MinIO, SeaweedFS, Garage, Ceph RGW or AWS; the production server is **not** chosen yet (that's Bloque 9). **Don't flip the default to S3 until the binaries are migrated**: the files live in the `controllocal_almacen` volume, not in any bucket, so switching first shows an empty store. Both providers must serve a given key **identically** (same key shape, same content-type) while the contract is frozen — that's why `claveNueva` and `contentTypeDe` live on the shared interface.
- **Migrating binaries between providers**: `MigracionAlmacen` (modes `conciliar` / `migrar`), runbook in `backend-spring/operacion/README.md` §7 bis. Exit codes are **0 = clean, 2 = findings, 1 = didn't start** — 2 and not 1 so a script can tell "the report found breakage" from "the tool never booted". The three key columns are **named differently** (`persona.foto_clave`, `foto_propiedad.clave`, `documento_solicitud.ruta_archivo` — the last one is a storage key despite the name), and two lookalikes must NOT be migrated (`evento_seguridad.clave_valor_hash`, `comision_movimiento.clave_idempotencia`). The list lives explicitly in `InventarioDeClaves`; a new key column has to be registered there.
- **`mvn -pl X` without `-am` compiles against the INSTALLED jar of its dependencies**, not your edited sources — so edits to `controllocal-web` are invisible to a `-pl controllocal-app` build and you get "cannot find symbol" for a method you just wrote. Use `-am`, or `install` the dependency first.
- **`mvn test-compile` can print BUILD SUCCESS over stale test classes.** After changing a record/signature used by tests, the incremental compiler may skip them entirely and report success; only `mvn clean test-compile` surfaces the real errors. Cost a false "it compiles" on 2026-08-08.
- **`docker run` from Git Bash mangles container-side paths.** MSYS rewrites `-v host:/app/app.jar` and `--entrypoint /bin/sh` into `C:/Program Files/Git/...`, and the container fails with a nonsense path ("Unable to access jarfile C:/Program Files/Git/app/app.jar"). Prefix the command with `MSYS_NO_PATHCONV=1`. `docker compose` is unaffected because the paths live in the YAML.
- The E2E scripts in `backend-spring/verificacion/` are written for **Windows PowerShell 5.1** (`powershell -File …`); `pwsh` is not installed on this machine.
- **Serena is configured `languages: [java, typescript]`** (`.serena/project.yml`). Do **not** add `csharp` — its server needs the .NET 10 runtime, which isn't installed, and one failed server takes down the whole manager (Java symbols included). Do **not** use `angular` either: it requires `npm install` at the repo root, but the SPA lives in `frontend-angular/`. Build output (`target/`, `node_modules/`, `dist/`, `.angular/`) is in `ignored_paths`. Config changes only take effect when the Serena MCP server restarts.
- **Spring profiles (2026-08-04): `dev` (default), `test`, `prod`.** `spring.datasource.*`, the Flyway locations, the JWT secret and `ALMACEN_DIR` live in `application-{profile}.yml`, **not** in `application.yml`. `prod` has **no defaults** — a missing var stops the boot with a message naming it (`ComprobacionVariablesObligatorias`), and `ValidadorConfiguracionSeguridad` then blocks nine insecure configurations (D-S0-20). The E2E compose still runs under `dev`.
- **Binaries live in the named volume `controllocal_almacen`** mounted at `/var/lib/controllocal/almacen`. Before this, the store fell in `./almacen-dev` inside the container's writable layer and **a `--force-recreate` deleted every photo and document**. Backup/restore tooling and the operational guide are in `backend-spring/operacion/` (`respaldo.ps1`, `restaurar-verificar.ps1`, `README.md`, `EVIDENCIA.md`).
- Postgres v2 uses `VARCHAR(1)` for the legacy 1-char codes (`CHAR(1)` is `bpchar` in Postgres and breaks Hibernate `ddl-auto: validate`).
- Seed credentials: `admin@controllocal.test`/Admin2026, `rsalas`…`sramirez`/Broker2026, `vmora`…`rgomez`/Agente2026. **Published in the repo on purpose** — `ValidadorConfiguracionSeguridad` refuses to boot `prod` while any of them is still live.
- **THE CONTRACT IS UNFROZEN (2026-08-09).** `docs/ai/decision-contrato-v2-descongelado.md`. DTOs, endpoints, names, states, errors, permissions, models, flows and inherited behaviours **may all change** — on two conditions: a functional or architectural reason, and **the change ships with its tests**. The old contract is no longer the authority; **OpenAPI and the tests are the executable contract**. The development rule is now `product need → domain rule → v2 contract → backend → frontend → tests`, never `copy v1 → keep compatibility`. A strange v1 behaviour is **not** replicated by default, and no new code is changed merely to match the legacy.
- **Evolve module by module, closing one before opening the next** — no sweeping cross-cutting refactor. The cycle is in the decision doc §6. The reference point for detecting regressions is the tag **`baseline-v2-pre-descongelado`**, whose evidence is `backend-spring/verificacion/evidencia/2026-08-09-baseline-v2-pre-descongelado.md`.
- **A closing run is `verificacion/Verificar-Cierre.ps1`, not `mvn clean install`.** The 37 integration tests carry `@EnabledIfEnvironmentVariable(TEST_DB_URL)`, so without that variable JUnit **skips them in silence** and Maven ends green. That is how V31/V37/V38 shipped three full-word `estado` columns, breaking the unitary-code invariant, with the build green for a whole security block. The script demands `TEST_DB_URL`, sets `CONTROLLOCAL_CIERRE=1` (which turns the silent skip into a failure inside the reactor) and **checks the output to prove the integration tests ran**, not merely that they didn't fail.
- Four gates break the build: strict layers (`app → web → service → persistence → domain`), "all state transitions go through `service/soporte/Transiciones`", "every private entity carries the tenant discriminator", and the **operation→role matrix** — `MatrizOperacionRolTest` parses `docs/ai/matriz-operacion-rol.md` and fails if it drifts from the controllers, so **a new endpoint needs a row there** (method, path, roles, and where scope is decided).
- **Security is enforced in three places you must not bypass** (blocks 3 and 4 of the roadmap, closed 2026-08-05, migrations V29–V31): `FiltroAutenticacionJwt` checks *per request* whether the session was revoked (`sesiones_invalidas_desde` vs the token's `iat`) and whether it is *capped* by a temporary password; `service/soporte/EventosSeguridad` is the **only** writer of `evento_seguridad` (append-only, one transaction per event); and `ContrasenaServiceImpl.fijarContrasena` is the **only** place a password changes — it validates the policy, archives the old hash, stamps the date, uncaps the account and invalidates every session, five effects that must not be split. Adding a `POST` that touches credentials means going through those, not around them.
- **Nobody sets another person's password.** The titular changes it knowing the previous one, or defines it by redeeming a one-time token. The system-generated temporary password is the only exception and it forces a change on first use. Don't add an endpoint where an admin picks someone else's password.
- **`ng test` hangs silently without `CHROME_BIN`.** There is no Chrome or Edge in `Program Files` on this machine, so Karma waits forever for a browser to connect and prints **nothing at all** — no error, no timeout, no Chrome process. Two things are needed: the browser from the puppeteer cache, and the project's own launcher name (`EdgeHeadlessCI`, not `ChromeHeadless` — `karma.conf.js` only registers that one, and a wrong name fails with `Cannot load browser`). Cost 20 minutes on 2026-08-07:
  ```bash
  CHROME_BIN="$USERPROFILE/.cache/puppeteer/chrome/win64-150.0.7871.24/chrome-win64/chrome.exe" npx ng test --watch=false --browsers=EdgeHeadlessCI
  ```
- **A vocabulary conversion has to reach PL/pgSQL function bodies too.** V40 narrowed three `estado` columns to unitary codes and updated CHECKs, partial indexes and defaults — everything `pg_constraint` and `pg_indexes` expose — but left `exigir_administrador_operativo()` comparing `fa.estado = 'ACTIVO'`. The condition stopped matching any row, the trigger concluded the org had no operational admin, and **every MFA enrolment returned 409**, taking down `s0-mfa` and `f4-solicitud`. Neither javac nor Hibernate reads a function body; only an E2E goes through it. V44 fixed it and `OcupacionInmuebleIntegrationTest` now greps `pg_proc.prosrc` so it cannot happen again.
- **Don't run `ng build` or `ng test` while an E2E suite is running.** The search suites (`locales-busqueda`, `demanda-busqueda`, `solicitudes-busqueda`) assert p95/worst-case latency on the same machine that compiles, so a parallel frontend build makes them fail on timing alone — a full regression on 2026-08-06 was re-run for exactly that (evidence in `verificacion/evidencia/2026-08-06-locales-busqueda-reejecucion-aislada.log`).
- **The entity-reference columns are named inconsistently across tables — check before writing SQL.** `historial_estado` uses `id_entidad`; `tarea` uses `entidad_id`. Both have `entidad_tipo`, and its vocabulary is constrained per table (`ck_tarea_tipo_entidad` says `INMUEBLE`, not `PROPIEDAD`). Guessing here cost three ten-minute E2E runs on 2026-08-06.
- **E2E scripts must be pure ASCII.** PowerShell 5.1 reads a `.ps1` without BOM as ANSI, so one em dash or `ñ` inside a *comment* is enough to break parsing of the whole script — with an error pointing at a line that is perfectly fine. Cost four runs to diagnose on 2026-08-06.
- **Never pass a JSON body as an argument to a native executable from PowerShell 5.1** (`docker exec … --post-data '{"a":1}'`): the double quotes are stripped in transit and the API answers 400 for an unreadable body. Send it base64-encoded and decode it inside the container (`e2e-s0-emergencia.ps1` does this). Same class of trap as the `2>&1` one below.
- **The E2E scripts must be invoked without `2>&1` or `2>$null`**: in PowerShell 5.1 those redirections turn `docker compose` progress (which goes to stderr) into terminating errors and the environment dies before the suite starts. Capture with `Start-Transcript` instead. Same reason `powershell -File` can't take the `-Suite` array — pass a comma-separated string and split it inside.
- **PDF reports are out of scope for the migration** (D-F5-1, `docs/ai/decision-reportes-pdf-fuera-de-alcance.md`): the v1's 5 Jasper endpoints are not ported and no replacement technology is chosen yet. Don't add "Exportar PDF" buttons to the Angular SPA.

## Build, run, test

Everything lives in the "New stack" section above — there is only one stack now. Two things worth
repeating because they cost time:

- The machine's default `JAVA_HOME` points at **JDK 17**, which breaks the build (`release=21`).
  Export a JDK 21+ for the session first.
- The database is **PostgreSQL only**, owned by Flyway. There is no `database/*.sql` to run: the
  schema is whatever the migrations say, and a fresh `docker compose up` builds it from scratch.

## Architecture

The layering, the wiring conventions and the four build gates are all described in the "New stack"
section above and enforced by tests — `app → web → service → persistence → domain`, state
transitions only through `service/soporte/Transiciones`, tenant discriminator on every private
entity, and the operation→role matrix. Adding an endpoint means adding its row to
`docs/ai/matriz-operacion-rol.md` or the build fails.

## Private configuration (never commit)

The three legacy config files (`api.properties`, `db.properties`, `appsettings.json`) went away with
their stacks. Configuration is now environment variables per Spring profile: `dev` has defaults,
`prod` has **none** and the boot stops naming whatever is missing.

> **Still open, and deleting the code did NOT close it**: those three files reached
> `github.com/IvanBaltazar-Dev/ControlLocal` in commit `2832a9b`, publishing the JWT signing secret
> and the RDS credentials. **Git history keeps them.** The JWT secret is the one that matters —
> `backend-spring` reuses it — and now that GlassFish is gone, rotating it is a plain config change
> with no compatibility to preserve.

## Commits

Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
