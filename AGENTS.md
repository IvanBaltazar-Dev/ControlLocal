# AGENTS.md

## BROX — Protocolo obligatorio de trabajo

BROX trabaja con tres responsabilidades distintas. No se mezclan.

### BROX — CONTROL
- Decide qué corte entra.
- Revisa repositorio, bases y evidencia.
- Detecta decisiones pendientes.
- Congela el alcance.
- NO programa.
- NO modifica el repositorio durante el corte.

### BROX — CONSTRUCTOR
- Es el único agente autorizado a modificar el repositorio durante un corte.
- Implementa exactamente el alcance autorizado.
- Puede modificar migraciones nuevas, backend, frontend, pruebas y evidencia según el corte.
- Debe entregar candidato limpio, pruebas, evidencia y commit.

### BROX — AUDITOR
- Intenta demostrar que el candidato del Constructor es incorrecto.
- Revisa diff, bases, conservación de datos, permisos, regresiones y pruebas.
- NO modifica código durante la primera auditoría.
- Un rechazo vuelve al Constructor; no convierte al Auditor en segundo escritor.

### Regla de continuidad
Un fallo técnico reproducible que no requiere una nueva decisión funcional vuelve directamente al Constructor para reparación, pruebas y nueva auditoría. CONTROL vuelve al titular únicamente cuando aparece una decisión funcional, de alcance o de riesgo que no puede deducirse del contrato existente.

This file provides canonical guidance to every BROX code agent working in this repository.

## What this is

ControlLocal manages and audits the commercial process of renting out commercial premises for a real-estate brokerage. The domain flow is: `Propietario` → `LocalComercial` → `Captacion` (agent captures, broker reviews) → `OportunidadComercial` → `Interaccion`/`Visita` → `SolicitudAlquiler` → `Documentos` → `EvaluacionSolicitud` (broker) → `ContratoAlquiler`/`Comision`. `OportunidadComercial` is the hub entity that keeps traceability even when a lead never files a formal request. Three roles: **broker administrador**, **broker supervisor**, **agente inmobiliario** — the broker supervises/decides, the agent registers/operates.

The domain vocabulary (entity, enum, and method names) is Spanish; keep it Spanish when adding code. Comments in the codebase are Spanish.

## Repository layout

**The legacy stack was DELETED on 2026-08-08.** `backend-java/` (Jakarta REST → GlassFish), `frontend-csharp/` (Blazor) and `database/` (MySQL) are gone from the working tree — they never ran in production and never will, so there was nothing to migrate and nothing to keep in step with. The code stays in git history if it is ever needed (`git checkout <commit> -- backend-java/`).

**What this changes, and it is not cosmetic**: the FROZEN REST contract existed *only* so the two backends could coexist. With no v1 there is nothing to be byte-compatible with, and **the freeze was lifted on 2026-08-09** (`docs/ai/decision-contrato-v2-descongelado.md`) — see the unfrozen-contract bullet further down for what that permits and what it demands. The `contrato-congelado-*.md` filenames survive as historical names; the freeze does not.

- `backend-spring/` — the backend: Spring Boot 3.5 Maven reactor (`controllocal-domain` → `controllocal-persistence` → `controllocal-service` → `controllocal-web` → `controllocal-app`), PostgreSQL v2 (Party-Role) migrated by Flyway. Base URL `http://localhost:8090/controllocal/Api`. Flyway owns the schema; **never edit an applied migration**.
- `frontend-angular/` — the frontend: Angular 20 SPA (standalone, signals; `core/` auth + `features/` + `layout/`), consumes the API at port 8090.
- `docs/ai/` — **only three documents govern what to do next; everything else is reference or history.**
  1. **`docs/ai/mapa-ejecucion-brox.md` — the cover page. Open this first, always.** Answers "where are we, what did we close, what's next" in one table: stages E0…E9, each ✅ CERRADA / 🟡 EN CURSO / ⬜, with what you can go and verify. It is updated when a stage closes; if it says a stage is closed, the repository is coherent.
  2. `docs/ai/checklist-captura-moat-e-inteligencia-inmobiliaria.md` — what is still missing to close the current stage. A stage closes with **gate + tests + evidence**, and not before.
  3. `docs/ai/decision-*.md` (D-E…) — specific functional decisions, each one self-contained.

  **What travels, and why it is not "version everything"** (`N41`/`N42`, 2026-09-02).
  `docs/ai/` is ignored with a whitelist, and the rule for the whitelist is one line:
  **a VIGENTE decision that governs code must travel** — a clone that does not carry it
  leaves the rule with no written authority, and the gate that enforces it with nothing to
  point at (`AutoridadDeLaPropiedadTest#laAutoridadQueGobiernaViajaConElCodigo` reads the file
  for exactly this reason). **History, audits and dated evidence may stay ignored**: they
  explain the *why*, they do not decide anything, and a clone can reproduce the close without
  them. And **`docs/ai/pendientes-brox.md` is an inventory, never an authority**: it says what
  remains, so a rule that lives *only* there is a rule nobody is bound by — move it to the
  decision that owns it.

  Next to those, `docs/ai/pendientes-brox.md` is the **transversal inventory of
  everything still open** — measured against the repo and the live databases on
  2026-08-22, not copied from the other docs. It does not say what to do next
  (the map does); it says what remains, including the parts that belong to no
  stage: production, multi-tenancy, UI, E2E coverage, and which documents have
  gone stale. Read it when you need the whole picture rather than the next step.

  Above those three sits `docs/ai/north-star-brox.md` — the **strategic frame**
  (SIVAN's North Star document, plus how it already binds the code). It does not
  say what to do next; it says **what counts as an advance**. Read it when a
  decision has two reasonable answers. Its non-negotiables show up as rules you
  will hit anyway: data accumulates (never retire a datum before its replacement
  exists), an unknown value is declared FALTANTE and never guessed, Web and
  KAIROS receive the *same* Core definition, and every datum carries its
  procedencia.
- **The migration-era docs no longer govern.** `plan-maestro-ruta-a-produccion.md`, `checklist-migracion.md`, `plan-migracion-*`, `estado-actual-control-local.md`, `mapa-estado-y-pendientes.md`, `arquitectura-objetivo-java-fullstack.md`, `inventario-backend-java.md`, `inventario-frontend-blazor.md`, `informe-tecnologias-…-e5.md`, `plan-s0-*` and `uat-rc1-guion-manual.md` all carry a `HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL` banner. Keep them for the *why*, never for the *what next*. **Careful with the lettering**: those docs use the migration's E1…E5 (personas, reportes, ficha, dashboard, corte), which is **not** the current E0…E9 of the execution map. Same letters, different plan.
- Still authoritative reference: `docs/ai/matriz-operacion-rol.md` is the source of truth for **who can call what and where scope is decided** — read it before building any role-aware screen; it is enforced by a test. The `contrato-congelado-*.md` family still **describes** current wire behaviour and is kept up to date, but the freeze is gone and the authority is the tests plus OpenAPI. `docs/ai/seguridad-no-leer.md` is named "do not read" by convention — leave it alone.

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
- After changing Flyway migrations, **repackage the app jar** (`mvn -pl controllocal-app clean install -DskipTests` from `backend-spring/`) — Flyway reads the classpath, not the source tree — and restart the `api` container. The `api` container mounts the jar from `controllocal-app/target/`, so after that a `docker restart controllocal-api-v2` picks up the new code.
- **`mvn -pl controllocal-app install` WITHOUT `clean` does not rebuild the fat jar.** Maven reports `BUILD SUCCESS` and `Installing …app.jar`, but the file on disk keeps its old timestamp *and its old byte size* — so the container serves the previous code and a brand-new endpoint answers 404. Cost 15 minutes on 2026-08-19. The tell is the size: compare `ls -l controllocal-app/target/*.jar` before and after. Always `clean install` that module.
- **Jackson is configured `NON_NULL`: a null field does NOT travel.** In Angular it arrives as `undefined`, not `null`, so `x === null` silently misses it — the broker read "19 de undefined" on 2026-08-19. Declare nullable wire fields optional (`campo?: number | null`) and compare with `== null`.
- **Integration tests can no longer write to `controllocal_dev`.** `BaseDeDatosDePruebas` validates `TEST_DB_URL` from `@DynamicPropertySource` and denies by default; `AislamientoDePruebasTest` breaks the build if a test bypasses it. This exists because on 2026-08-18 the suite left 162 properties, 120 captaciones and the whole outbox inside the dev cartera, and the Inicio header said "125 cosas necesitan tu atención".
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
- **A closing run is `verificacion/Verificar-Cierre.ps1`, not `mvn clean install`.** The integration tests carry `@EnabledIfEnvironmentVariable(TEST_DB_URL)`, so without that variable JUnit **skips them in silence** and Maven ends green. (This bullet used to say "the 37 integration tests"; the number kept growing — 29 *classes* on 2026-09-02 — and nothing verified it, so it is gone. Count them with `rg -l EnabledIfEnvironmentVariable backend-spring/controllocal-app/src/test/java/com/controllocal/integracion`.) That is how V31/V37/V38 shipped three full-word `estado` columns, breaking the unitary-code invariant, with the build green for a whole security block. The script demands `TEST_DB_URL`, sets `CONTROLLOCAL_CIERRE=1` (which turns the silent skip into a failure inside the reactor) and **checks the output to prove the integration tests ran**, not merely that they didn't fail.
- **Gates break the build, and they live in one place**: `backend-spring/controllocal-app/src/test/java/com/controllocal/arquitectura/` (23 classes on 2026-09-02 — the directory is the authority, not this number). The founding four are strict layers (`app → web → service → persistence → domain`), "all state transitions go through `service/soporte/Transiciones`", "every private entity carries the tenant discriminator", and the **operation→role matrix** — `MatrizOperacionRolTest` parses `docs/ai/matriz-operacion-rol.md` and fails if it drifts from the controllers, so **a new endpoint needs a row there** (method, path, roles, where scope is decided, and since 2026-09-02 a `{autoridad: …}` token naming the guard that decides it). Since P0 there is also `AutoridadDeLaPropiedadTest`: no new write of a propiedad or an encargo without its authority.
- **Security is enforced in three places you must not bypass** (blocks 3 and 4 of the roadmap, closed 2026-08-05, migrations V29–V31): `FiltroAutenticacionJwt` checks *per request* whether the session was revoked (`sesiones_invalidas_desde` vs the token's `iat`) and whether it is *capped* by a temporary password; `service/soporte/EventosSeguridad` is the **only** writer of `evento_seguridad` (append-only, one transaction per event); and `ContrasenaServiceImpl.fijarContrasena` is the **only** place a password changes — it validates the policy, archives the old hash, stamps the date, uncaps the account and invalidates every session, five effects that must not be split. Adding a `POST` that touches credentials means going through those, not around them.
- **Nobody sets another person's password.** The titular changes it knowing the previous one, or defines it by redeeming a one-time token. The system-generated temporary password is the only exception and it forces a change on first use. Don't add an endpoint where an admin picks someone else's password.
- **`ng test` hangs silently without `CHROME_BIN`.** There is no Chrome or Edge in `Program Files` on this machine, so Karma waits forever for a browser to connect and prints **nothing at all** — no error, no timeout, no Chrome process. Two things are needed: the browser from the puppeteer cache, and the project's own launcher name (`EdgeHeadlessCI`, not `ChromeHeadless` — `karma.conf.js` only registers that one, and a wrong name fails with `Cannot load browser`). Cost 20 minutes on 2026-08-07:
  ```bash
  CHROME_BIN="$USERPROFILE/.cache/puppeteer/chrome/win64-150.0.7871.24/chrome-win64/chrome.exe" npx ng test --watch=false --browsers=EdgeHeadlessCI
  ```
- **`ng test` does NOT check the production budgets, so a style change can be 653/653 green and still not build.** The specs compile under the *development* configuration, which carries no `budgets`; `anyComponentStyle` only bites in `ng build --configuration production`. That is how the production build shipped broken for four commits on `feat/modelo-universal-y-autoridad-del-dato` while the suite stayed green (`c33d49a` → `bc3de56`, fixed in `4b2e301`). **When you touch a component stylesheet or `angular.json`, run the production build too** — the tests alone do not cover it. The current ceiling and why it is 16 kB: `docs/ai/decision-presupuesto-de-estilos-de-componente.md`.
- **Angular scopes `@keyframes` names per component, so an animation does NOT cross a component boundary.** Under emulated encapsulation the compiler rewrites both the `@keyframes` name and the `animation-name` that uses it, prefixing them with the content attribute of the component that *declares* the keyframes. A rule that uses a keyframes block declared in **another** component's stylesheet comes out with the bare name, matches nothing, and **the animation silently does not run** — no compile error, no warning, and `animation-name` still reads as a name rather than `none`, so a "not none" assertion does not catch it. Moving a rule with `animation`/`animation-name` into a child component means **moving or duplicating its `@keyframes` with it**. This bit when `.ant-fila` went from `radar.scss` to `radar-antecedentes.scss` on 2026-08-21: the four expediente rows lost their staggered entrance (`opacity` stuck at 1, `transform: none`) and only a `getComputedStyle` comparison found it. Note that older Angular did *not* scope keyframes, so comments in the codebase claiming otherwise are stale — `radar.scss` had one and it is what caused the bug.
- **A vocabulary conversion has to reach PL/pgSQL function bodies too.** V40 narrowed three `estado` columns to unitary codes and updated CHECKs, partial indexes and defaults — everything `pg_constraint` and `pg_indexes` expose — but left `exigir_administrador_operativo()` comparing `fa.estado = 'ACTIVO'`. The condition stopped matching any row, the trigger concluded the org had no operational admin, and **every MFA enrolment returned 409**, taking down `s0-mfa` and `f4-solicitud`. Neither javac nor Hibernate reads a function body; only an E2E goes through it. V44 fixed it and `OcupacionInmuebleIntegrationTest` now greps `pg_proc.prosrc` so it cannot happen again.
- **Don't run `ng build` or `ng test` while an E2E suite is running.** The search suites (`locales-busqueda`, `demanda-busqueda`, `solicitudes-busqueda`) assert p95/worst-case latency on the same machine that compiles, so a parallel frontend build makes them fail on timing alone — a full regression on 2026-08-06 was re-run for exactly that (evidence in `verificacion/evidencia/2026-08-06-locales-busqueda-reejecucion-aislada.log`).
- **The entity-reference columns are named inconsistently across tables — check before writing SQL.** `historial_estado` uses `id_entidad`; `tarea` uses `entidad_id`. Both have `entidad_tipo`, and its vocabulary is constrained per table (`ck_tarea_tipo_entidad` says `INMUEBLE`, not `PROPIEDAD`). Guessing here cost three ten-minute E2E runs on 2026-08-06.
- **E2E scripts must be pure ASCII.** PowerShell 5.1 reads a `.ps1` without BOM as ANSI, so one em dash or `ñ` inside a *comment* is enough to break parsing of the whole script — with an error pointing at a line that is perfectly fine. Cost four runs to diagnose on 2026-08-06.
- **`grep -iF` aborts with SIGABRT on this machine and writes NOTHING to stderr — a sweep silently reports "no matches".** GNU grep 3.0 under Git Bash: `-i` combined with `-F` dies with exit code 134 (`128 + 6`) on any pattern, while `-i` alone, `-F` alone, `-iw` and `-iE` all work. The abort is invisible in the two idioms people actually sweep with, because both discard grep's exit status: `grep -riF "x" src | wc -l` prints `0` and `$?` is `wc`'s zero, and `n=$(grep -riF "x" src); [ -z "$n" ] && echo clean` prints `clean`. The `Aborted` line comes from bash, not grep, and only in a bare simple command. This is how a false claim survived a "the tree is clean" sweep on 2026-08-24 with the offending line on screen. **A sweep whose zero has not been checked against a positive control is not a sweep**: search first for a string you know is present and require a hit, or use `rg`, which is unaffected. Full measurement: `backend-spring/verificacion/evidencia/2026-08-24-el-barrido-que-no-llegaba-a-mirar.md`.
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
