# ControlLocal backend v2 — Spring Boot (Strangler)

Backend nuevo de la migración Java Fullstack (Doc 4/5 de `docs/ai/`). Convive con el
backend Jakarta (`backend-java/` + GlassFish + MySQL) detrás del **contrato REST congelado**;
cada módulo se corta cuando su implementación Spring responde igual que la vieja.

## Módulos (capas estrictas, blindadas por ArchUnit)

| Módulo | Capa |
|---|---|
| `controllocal-domain` | Entidades JPA + enums + contratos (`Transicionable`) |
| `controllocal-persistence` | Repositorios Spring Data + paquete `query` (SQL nativo con scope, CQRS-lite) |
| `controllocal-service` | Casos de uso `@Transactional`, reglas, excepciones de negocio |
| `controllocal-web` | `@RestController` + DTOs congelados + Spring Security/JWT + OpenAPI |
| `controllocal-app` | Arranque (fat jar), `application.yml`, migraciones Flyway, tests de arquitectura |

## Arranque en desarrollo

```powershell
# 1) Compilar y probar (JDK 21+)
mvn clean install             # desde backend-spring/

# 2) PostgreSQL `controllocal_dev` (5433) + API (8090) en Docker.
#    Flyway migra la base al arrancar; el contenedor monta el jar empaquetado.
docker compose up -d
```

- Base URL: `http://localhost:8090/controllocal/Api` (misma base que GlassFish, otro puerto).
- **Si el API arranca con `FATAL: database "controllocal_dev" does not exist`**, el volumen es
  anterior al renombrado: `POSTGRES_DB` solo actúa al inicializar un directorio de datos **vacío**,
  así que un volumen creado cuando la base se llamaba `controllocal` conserva ese nombre para
  siempre. Se arregla sin perder datos ni historial de Flyway —parar el API, renombrar, arrancar—:
  `docker exec controllocal-postgres-v2 psql -U controllocal -d postgres -c "ALTER DATABASE controllocal RENAME TO controllocal_dev;"`.
  Recrear el volumen también funciona, pero borra la base de desarrollo.
- Públicos: `GET /salud`, `POST /auth/login`, Swagger UI en `/controllocal/Api/swagger-ui.html`.
- Credenciales seed (paridad con la BD v1): `admin@controllocal.test`/Admin2026,
  `rsalas`/Broker2026 (BRK-001..005), `vmora`/Agente2026 (AGE-001..015).

## Decisiones que hay que conocer

- **Contrato congelado**: DTOs, mensajes de error (`{"error": ...}`) y formato del token
  son byte-compatibles con el backend Jakarta. El token HS256 usa el MISMO secreto de
  fallback dev ⇒ SSO entre backends durante la convivencia. No cambiar sin cortar eso.
- **Semántica v2 de los ids de sesión**: `idUsuario` = `persona.id` (identidad única del
  actor, Party-Role); `idDominio` = `persona_rol.id` del rol operativo (BROKER/AGENTE).
  El mapeo de ids v1↔v2 para la sincronización se define en el Doc 6 (riesgos/backfill).
- **Contraseñas**: PBKDF2 `pbkdf2$iter$sal$hash`, mismo formato que la v1 ⇒ el backfill
  de credenciales no re-hashea.
- **Seguridad de sesiones, auditoría y bloqueo (Bloque 3, 2026-08-05)** — tres piezas que
  **no tocan el cable** y que conviene entender antes de tocar `auth`:
  - **La sesión se puede matar sin tocar el token.** El JWT ya lleva `iat`, así que basta
    comparar ese `iat` con `credencial_usuario.sesiones_invalidas_desde` (V29) para tumbar
    **todas** las sesiones vivas de una cuenta. `POST /auth/logout` (aditivo, 204) la sella.
    **Sin caché a propósito**: un TTL de 30–60 s dejaría viva una sesión ya revocada, que es
    justo el fallo que la pieza cierra. Borde aceptado: `iat` tiene precisión de segundo.
  - **Auditoría `evento_seguridad` (V30), append-only y con un único punto de escritura**
    (`EventosSeguridad`), igual que `Transiciones` es el único que muta estados. Cada evento
    va en **su propia transacción**: un login fallido debe quedar registrado aunque la
    operación que lo provocó termine lanzando. La higiene **descarta** las claves sospechosas
    en vez de enmascararlas — un `"***"` confirmaría que el campo existía.
  - **Bloqueo por cuenta e IP (`intento_acceso`, V30)**, que **sustituye y retira** el
    `LimitadorIntentos` en memoria: contaba 10/min **solo por IP**, un atacante con 50 IPs lo
    esquivaba y un reinicio lo borraba. Ahora cuenta en PostgreSQL, por cuenta **exista o no
    la cuenta** (si solo contaran las reales, el bloqueo sería un oráculo del padrón), con
    ventana de 15 min y escalado 5/10/15/20. Guarda **SHA-256** del identificador, nunca el
    usuario en claro. `X-Forwarded-For` solo se cree si la conexión viene de un proxy
    declarado en `controllocal.seguridad.proxies-de-confianza`.
  - Lo que **no** cambió: 401/403/429 siguen con su cuerpo congelado. Lo que cambia es
    **cuándo** se emite cada uno — el bloqueo se evalúa **antes** de comprobar la contraseña,
    para que el tiempo de respuesta no delate si la cuenta existe.
- **Contraseñas y recuperación (Bloque 4, 2026-08-05, V31)** — cierra H-02 y H-08. Cinco cosas
  que hay que saber antes de tocar nada de esto:
  - **Nadie fija la contraseña de otra persona.** Ni el administrador, ni el broker, ni el
    sistema. Solo hay dos caminos: el titular la cambia **sabiendo la anterior**, o la define al
    canjear un **token de un solo uso**. La contraseña temporal es la única excepción, y por eso
    **la genera el sistema** (quien la pide no la elige) y nace **capada**.
  - **Todo cambio pasa por `ContrasenaServiceImpl.fijarContrasena`**, igual que todo cambio de
    estado pasa por `Transiciones`. Son cinco efectos que no pueden desparejarse: política,
    archivo del hash saliente, hash nuevo + fecha, descapar la cuenta e **invalidar todas las
    sesiones**. Repartidos por tres métodos, uno se olvida.
  - **La política pide longitud, no teatro.** Mínimo 12 y nada de exigir mayúscula + dígito +
    símbolo: esa regla fabrica `Clave2026!`, que es **exactamente** el patrón del seed que este
    bloque viene a retirar. Tampoco hay rotación periódica, por lo mismo.
  - **Nada de lo público revela si una cuenta existe.** `POST /auth/recuperacion` responde 202
    siempre, y el canje da **el mismo error** para un token caducado, usado, reemplazado o
    inventado. El bloqueo de ese endpoint cuenta **solo por IP**: contarlo por cuenta permitiría
    bloquear la cuenta ajena pidiendo su recuperación en bucle.
  - **La sesión capada existe pero solo alcanza tres cosas**: `GET /perfil`,
    `POST /perfil/contrasena` y `POST /auth/logout` —encerrar a alguien en una sesión de la que
    no puede salir sería un fallo—. El resto es 403 con `codigo: CAMBIO_CONTRASENA_REQUERIDO`,
    un campo **aditivo** de `ErrorResponse` que Jackson omite en todos los demás errores, así que
    el contrato congelado sigue viajando byte a byte.
  - **Invitar es gobierno, no supervisión (D-S0-18):** un BROKER no invita ni a su propio equipo.
    Desde el Bloque 5 lo ejerce **`TENANT_ADMIN`**, sin que hiciera falta tocar esquema ni
    contrato.
  - **Limitación dicha sin adornos:** no hay transporte de correo (D-S0-11). El token que emite
    la recuperación **no llega a nadie**; el camino que funciona hoy es la invitación, que emite
    el gobierno del tenant y entrega a mano. El endpoint público existe para que el día que haya
    SMTP no haya que tocar ni contrato ni esquema.
- **Roles y gobierno (Bloque 5, 2026-08-05, V32–V35)** — *gobernar no es operar* (D-S0-7,
  matriz D-S0-17 aprobada tal cual). Cinco cosas que hay que saber antes de tocar permisos:
  - **La banda ya no sale del token.** El token sigue diciendo `ADMIN` porque su formato está
    congelado y solo admite tres valores mientras GlassFish conviva (R1). La banda **efectiva**
    (`AGENTE | BROKER | TENANT_ADMIN`) la resuelve el servidor desde `usuario_organizacion` **en
    cada petición** y la publica `FiltroAutenticacionJwt` como authority. No cuesta una consulta
    extra: es una columna más de la lectura que el filtro ya hacía. El SPA la pide con
    `GET /sesion`.
  - **Bajar es inmediato; subir exige volver a entrar.** El gobierno solo se concede si la
    membresía **y** el token coinciden, porque el `idDominio` del token es el `persona_rol` con el
    que se firma todo — en un administrador es su rol de gobierno, y solo el login sabe elegirlo.
    Cambiar una membresía invalida las sesiones de esa cuenta, así que la promoción reautentica
    igualmente.
  - **`TENANT_ADMIN` no es el `ADMIN` de antes.** Ve todo su tenant y gobierna cuentas y
    organigrama, pero **no firma ningún hecho del negocio**: no aprueba captaciones (fila 5), no
    las cierra (7), no conforma documentos (9, 10) y no evalúa solicitudes (13). El **BROKER**
    decide y firma, pero **no crea ni edita agentes** (17, 18) ni invita (D-S0-18).
  - **Ojo al tocar código que use `actor.idRolOperativo()`**: en un `TENANT_ADMIN` ese id es su rol
    de gobierno, **no** un `detalle_broker`. Buscarlo entre los brokers compila y falla en
    ejecución con *"Broker no encontrado"*. Ya pasó en tres sitios (`/agentes`, `validarAdministrador`
    y la reasignación); si aparece un cuarto, la respuesta es acotar la búsqueda a `esBroker()`.
  - **`usuario_organizacion` es ahora fuente de verdad, y hay que mantenerla.** Toda alta de
    usuario interno crea su membresía (`UsuariosInternos`). El backfill de V6 **estaba roto** —unía
    el rol interno con el de broker, dos filas que no pueden compartir id, y dejó las 21 cuentas
    como `AGENTE`—; nadie lo notó porque nadie la leía. V33 lo repara.
- **Ciclo jurídico del contrato (Bloque 7, 2026-08-06)** — tres cosas que conviene saber antes de
  tocarlo:
  - **El grafo se valida en `MaquinasEstado`, no en la entidad.** `ContratoAlquiler.transicionarA`
    es una asignación sin comprobaciones y **quien se detiene ahí concluye que no se valida nada**:
    la máquina (`P→{D,A}`, `D→{V,A}`, `V→{F,S,R}`, terminales cerrados) la declara
    `MaquinasEstado` y la aplica `Transiciones` antes de mutar. Está fijada por `CicloContratoTest`
    para que la respuesta no dependa de por dónde entre quien lo lea. **No dupliques el grafo en la
    entidad**: serían dos fuentes de verdad de la misma regla.
  - **Repetir una transición se rechaza en el CASO DE USO, no en `Transiciones`.** Aquélla ignora
    en silencio una transición al mismo estado —idempotencia deliberada para captación, solicitud,
    oportunidad y publicación— y eso hacía que rescindir dos veces respondiera 200 sin cambiar
    nada. Bajar el rechazo a `Transiciones` cambiaría de golpe las otras cuatro entidades.
  - **Terminar un contrato NO libera el inmueble**, y es intencional: deja una tarea de revisión
    para que una persona confirme si puede volver al mercado. Ciclo jurídico y disponibilidad
    comercial son ciclos distintos. Esa tarea **nunca se creaba**: se insertaba con
    `entidad_tipo = "PROPIEDAD"` y el `CHECK` declara `INMUEBLE`, así que finalizar y rescindir
    fallaban siempre con 409. Usa `Tarea.ENTIDAD_INMUEBLE`, no la cadena.
  - **Rescindir y anular son de BROKER**, no de AGENTE (el resto del ciclo sí es del agente): «el
    broker decide, el agente registra». Un `TENANT_ADMIN` sin rol operativo recibe 403.
- **Segundo factor y elevación (Bloque 6, 2026-08-06, V37)** — TOTP para el gobierno del tenant.
  **V38 (recuperación de emergencia) NO está construida**, y este bloque **no emite
  `PLATFORM_ADMIN`** ni crea ninguna cuenta. Seis cosas que hay que saber antes de tocarlo:
  - **La sesión del `TENANT_ADMIN` nace CAPADA.** V37 marca `debe_enrolar_mfa` a todo administrador
    vigente, así que hasta enrolar solo alcanza `GET /perfil`, `GET|POST /perfil/mfa`,
    `POST /perfil/mfa/confirmar` y el logout. **Toda suite E2E que actúe como administrador tiene
    que enrolar primero** — para eso está `Connect-ControlLocalE2E` en `verificacion/e2e-context.ps1`.
    El SPA **ya tiene la pantalla** (`/enrolar-mfa`, fuera del shell como el cambio de contraseña):
    cada carga pide un secreto nuevo y avisa de que el anterior murió, el QR se dibuja **en el
    navegador** a partir de la `uri` (`qrcode`, sin red), y al confirmar se enseñan los códigos de
    respaldo, se exige confirmar que se guardaron y se vuelve al login.
  - **Confirmar el enrolamiento CONSUME su código** (corregido 2026-08-06). Antes no: `confirmar`
    validaba el TOTP y no lo sellaba, porque `consumirPaso` solo mira factores `ACTIVO` y ahí el
    factor todavía es `PENDIENTE`. El resultado era que **el primer código —el que acaba de estar en
    pantalla junto al QR— seguía sirviendo para entrar durante el resto de su ventana**. Ahora el
    paso se sella en el mismo `save` que activa el factor; la carrera la cierra el índice parcial
    `uq_factor_activo_por_credencial`. **Consecuencia aceptada**: tras enrolar hay que esperar hasta
    30 s al paso siguiente para volver a entrar, y por eso el flujo del SPA cierra la sesión y manda
    al login en vez de intentar renovarla por detrás.
  - **El aviso de gobierno se LEE, no se escribe como alerta** (D-S0-49). `GET /seguridad/avisos`
    (solo `TENANT_ADMIN`) proyecta `evento_seguridad`, que es append-only y de un solo escritor, así
    que **no se puede atender ni descartar** — que es justo lo que haría quien acabara de revocar un
    factor sin permiso. En `alerta` no cabía: cuelga siempre de un AGENTE y su `CHECK` de tipos es la
    lista congelada de hechos comerciales.
  - **`GET /accesos` existe para no tocar dos DTO congelados** (D-S0-50). Las fichas de agente y
    broker identifican por `persona_rol.id`; todas las operaciones de `/accesos` hablan de la
    **persona**. Este endpoint aditivo publica la correspondencia (y el estado del factor) en vez de
    añadir `idPersona` a `AgenteResponse` y `BrokerResponse`.
  - **Los errores de MFA llevan `codigo` estable** además del `error` visible
    (`ErrorMfaException`): `MFA_CODIGO_INVALIDO`, `MFA_CODIGO_REUTILIZADO`, `MFA_DESAFIO_INVALIDO`,
    `MFA_DESAFIO_VENCIDO`, `MFA_DESAFIO_CONSUMIDO`, `MFA_LIMITE_INTENTOS` y
    `MFA_ENROLAMIENTO_INVALIDO`. El SPA decide por ahí y **nunca** por la cadena en español. El
    mensaje **no** se especializa cuando hacerlo delataría algo: desafío inexistente, reemplazado o
    cuenta sin factor comparten texto y comparten `MFA_DESAFIO_INVALIDO`.
  - **El login pasa a dos pasos y `/auth/login` responde 401 a las cuentas con MFA.** El camino
    nuevo es `POST /auth/mfa/desafio` → 200 con el `LoginResponse` congelado si no hay factor, 202
    con un desafío si lo hay; y `POST /auth/mfa/verificar` lo canjea. El cliente usa **un solo
    camino** y no adivina.
  - **Validar un código es CONSUMIRLO, y es atómico.** El anti-replay es un `UPDATE` condicional
    sobre `ultimo_paso` y el veredicto es cuántas filas afectó. Leer, comparar y luego escribir deja
    pasar el mismo código a dos peticiones simultáneas. **Solo se admiten los pasos `t` y `t-1`**: el
    futuro no, porque sellarlo tumbaría el código actual.
  - **Lo que cuenta fallos no puede viajar en la transacción que falla.** El contador de intentos
    del desafío se registra en transacción propia (`REQUIRES_NEW`), igual que `EventosSeguridad` y
    `BloqueoAccesos`. Se descubrió por E2E: con el contador dentro, el `rollback` lo borraba y el
    límite de cinco intentos no contaba nada.
  - **`MFA_CLAVE_CIFRADO` es un problema de DISPONIBILIDAD, no de confidencialidad.** Perderla deja
    a todos los administradores sin segundo factor. Va versionada (`version_clave`), admite clave
    actual + anterior durante una rotación, y su respaldo va **cifrado y aparte del dump** — un
    respaldo que lleva la base y su clave no está cifrado, está acompañado. En `prod` su ausencia
    detiene el arranque.
  - **El invariante subió de listón**: de "≥ 1 membresía `TENANT_ADMIN`" a **"≥ 1 administrador
    OPERATIVO"** (membresía + credencial activa + factor ACTIVO + sin cambios obligatorios
    pendientes). Lo vigilan **tres triggers diferidos** y una guarda de aplicación. Es seguro en el
    arranque gracias a `organizacion.mfa_gobierno_exigido`: mientras sea falso rige la regla de V34,
    porque entre el despliegue y el primer enrolamiento **no hay ningún administrador operativo** y
    exigirlo dejaría el sistema tapiado.
- **BD v2 = PostgreSQL + Flyway** (`controllocal-app/src/main/resources/db/migration/`):
  V1 identidad Party-Role (+ integridad rol↔detalle por FK compuesta, únicos parciales
  nativos), V2 auditoría universal (`entidad_tipo` + `historial_estado`), V3 seed,
  V4 oferta (distrito + `propiedad`/`detalle_local_comercial` + fotos + precios +
  publicación, con 3 propietarios y 2 locales DEMO de desarrollo), V5 proceso
  (prospección + captación + reasignación), V6 núcleo multi-tenant, V7 demanda,
  V8 solicitud/documentos/contrato, V9 alertas/tareas/reportes, V10 evento de
  reasignación agente→broker, V11 índices de búsqueda, V12–V14 estabilización económica
  inicial, V15–V20 normalización económica/contractual final, backfill controlado,
  restricciones, códigos de estado y auditoría de disponibilidad, V21 encargo obligatorio,
  V22–V26 índices de las bandejas y sus búsquedas, **V27 atribución histórica del cierre**,
  **V28 autorización de datos personales** (D-27), **V29 invalidación de sesiones** (D-S0-12) y
  **V30 auditoría de seguridad + bloqueo de accesos** (D-S0-21).
  El esquema lo posee Flyway; Hibernate solo `validate`.
- **Puente tipado de estados**: cada entidad conserva un único atributo `String` persistido y
  consultable para no romper JPQL histórico. El accessor `@Transient` deriva el enum mediante
  `EstadosDominio`, y los métodos de dominio traducen enum→código al escribir. El REST sigue
  exponiendo un carácter. `CatalogoEstadosTest` compara códigos, documentación y conversión;
  `RepositorioEstadosIntegrationTest` levanta todo Spring y ejecuta JPQL, métodos derivados,
  proyecciones y KPI contra PostgreSQL real. Matriz completa en
  `docs/ai/matriz-codigos-estado.md`.
  Ese gate **se salta en silencio** (`@EnabledIfEnvironmentVariable`) si no hay `TEST_DB_URL`:
  para que corra de verdad, la sesión de build necesita `JAVA_HOME` en un JDK 21+ y
  `TEST_DB_URL=jdbc:postgresql://localhost:5433/controllocal_repositorios` (usuario y clave
  `controllocal`, ya en el compose de desarrollo). Comprueba en la salida de Maven que aparece
  `Tests run: … in …RepositorioEstadosIntegrationTest`; si no aparece, no se validó nada.
- **Bases locales separadas**: `docker-compose.yml` usa sólo `controllocal_dev`; **cada suite**
  crea `controllocal_e2e_<run_id>` en un PostgreSQL efímero propio y lo elimina en `finally`. No
  se ejecutan scripts E2E directamente contra desarrollo.
  **La limpieza es tirar la base entera, no borrar filas** (2026-08-03). Las suites de búsqueda
  retiraban su banco de 100.000 filas/tabla con nueve `delete … LIKE`: medido en la corrida de
  firma de F3, **1.014 s de los 1.657 s de la corrida — el 61 %**, para vaciar tablas que mueren
  con el contenedor. Ahora el entorno es **por suite** (lo que además elimina el 429 que mataba
  las corridas multi-suite, porque cada una estrena API) y el teardown tarda **1,6–3,4 s**.
  Comprobado: `f4-solicitud` 116/116 + `demanda-busqueda` 69/69 —mismos checks— en **625 s** de
  una sola invocación, sin recursos residuales.
- **Módulo Locales Comerciales (F2-oferta, primera vertical)**: contrato congelado de
  `LocalesRest` implementado en `/locales` (CRUD + precios + publicaciones + fotos
  base64) y `/documentos/contenido` (público, clave-capability) para servir binarios.
  `propiedad` generaliza `local_comercial` (MEJ-12/31) con el rubro en
  `detalle_local_comercial`; el propietario del cable (`idPropietario`) es el
  `persona_rol.id` del rol PROPIETARIO. Jackson emite `non_null` para calcar el
  JSON-B de la v1 (las propiedades nulas no viajan).
  La extensión aditiva de rendimiento del listado está fijada en
  `docs/ai/contrato-listados-paginados.md`: `GET /locales` acepta `texto`/`estado`,
  pagina y cuenta en SQL con proyección, y `GET /locales/resumen` calcula los KPI
  en PostgreSQL. V11 agrega los índices compuestos y de trigramas del camino caliente.
- **Auditoría de transiciones**: `service/soporte/Transiciones` es el ÚNICO punto que
  muta el estado de un `Transicionable` y emite `historial_estado`;
  `ArquitecturaAuditoriaTest` (ArchUnit) rompe el build si alguien llama
  `transicionarA` por su cuenta.
- **Módulo Prospección + Captación (F2-proceso, verificado E2E)**: contrato
  congelado de `ProspeccionesRest`/`CaptacionesRest`
  (`docs/ai/contrato-congelado-f2-prospeccion-captacion.md`) implementado en
  `/prospecciones`, `/captaciones` y `/captaciones/reasignaciones`. V5 = tablas
  del proceso + índice único parcial "una captación ACTIVA por local" + seed
  DEMO (PRO-0001/0002, CAP-0001 pendiente para la bandeja del broker). Máquinas
  de estado sobre `Transiciones` (cada transición emite `historial_estado` con
  actor y motivo — MEJ-01), alcance por rol vía `Alcances` (RC-001), observación
  obligatoria al observar/rechazar (MEJ-03), reenvío `O→P` al editar una
  observada y reasignación como **tabla-evento de actor** (no transición).
  Cable real a respetar: la v1 **nunca emite el estado `E`** — entregar
  propuesta deja `S` y la marca es `fechaPropuesta` + `resultadoPropuesta='P'`.
  Al cortar las bandejas Angular se corrigieron dos omisiones de paridad del
  listado: `estado=GESTION` agrupa `{P,C,R,E,S}` e `idBrokerSupervisor` filtra
  por el equipo seleccionado. `GET /captaciones` y `GET /captaciones/pendientes`
  añadieron `estado`, `idAgente` y `q` como filtros opcionales para sus bandejas;
  omitidos conservan el cable v1.
  `coincidencias` llegó con F3; los 4 PDF Jasper quedaron **fuera del alcance**
  de la migración (D-F5-1) y no se portan.
- Las tres deudas de la vertical locales quedaron **cerradas con F2**
  (`GET /locales/mis-locales`, regla "el agente solo edita locales de sus
  captaciones", prospección inicial automática al crear local). Sigue pendiente
  la alerta "Modificación comercial sensible, revisar" → módulo **alertas** (F6).
- **Núcleo multi-tenant (V6, verificado E2E)**: toda tabla privada lleva
  `organizacion_id` NOT NULL **sin DEFAULT** y hoy opera como tenant único
  (`BROX_LEGACY`). Plan y gate en `docs/ai/plan-migracion-v6-tenancy.md`.
  Las piezas a conocer:
  - `domain/comun/EntidadDeOrganizacion` (`@MappedSuperclass`) es el
    discriminador; `ArquitecturaTenancyTest` rompe el build si una entidad
    nueva no lo hereda y no está declarada global (distrito, organización y
    los catálogos de consentimiento).
  - `Actor` gana `idOrganizacion`. **Lo resuelve el backend, nunca el cliente**:
    `FiltroAutenticacionJwt` publica el principal `SesionDeRequest` = claims del
    token (CONGELADOS) + organización de `OrganizacionService`. El token no
    cambia, así que el SSO con GlassFish sigue intacto.
  - `Alcances` filtra por organización **antes** que por rol; el tenant es
    parámetro obligatorio del WHERE de las consultas con scope.
  - Los correlativos `PRO-####`/`CAP-####` se numeran **por organización**
    (el código es único dentro del tenant, no global).
  - Aislamiento = discriminador + filtro en la app (D-24); RLS se activa al
    habilitar multi-tenant real. Las colecciones hijas de un local (fotos,
    precios, publicaciones) se alcanzan solo por el id del padre, que sí va
    filtrado.
  - Scripts de verificación manual en `verificacion/` (E2E 46 checks, prueba de
    dos organizaciones, rollback).
- **F3 Demanda (COMPLETA, verificada E2E)**: cliente interesado, requerimiento,
  oportunidad, visita, interacción y el matching de cartera. Contrato congelado
  en `docs/ai/contrato-congelado-f3-demanda.md` (con el estado de ejecución y
  los hallazgos al final). V7 + entidades + repositorios + services +
  controllers + tests, con **89/89 en `verificacion/e2e-f3-demanda.ps1`**.
  Lo que hay que saber para tocarla:
  - El **cliente es un ROL** de persona (`detalle_cliente` sobre `persona_rol`
    con `tipo_rol='CLIENTE'`), igual que agente/broker/propietario. En el cable,
    `idCliente` = `persona_rol.id`.
  - `interaccion_comercial` es **polimórfica**: cuelga de una de cuatro
    entidades según `contexto`, y ahora lo garantiza un CHECK de la BD (la v1
    solo lo validaba en REST).
  - **Dos reglas de alcance de broker distintas**, que no hay que unificar:
    oportunidades y visitas alcanzan por la **captación** de su equipo;
    interacciones, por **agente supervisado**.
  - F3 rompe la convención CHAR(1): `EstadoRequerimiento`, `TipoInmuebleComercial`
    y parte de `ResultadoInteraccion` viajan con el **nombre** del enum.
  - El **matching tiene "vista personal"**: para un actor no-ADMIN la demanda
    propia son los clientes que YA tienen oportunidad del equipo, así que un
    cliente recién creado no sale todavía en `captación → clientes` aunque case
    al 100 % (el ADMIN sí lo ve). En el sobre de coincidencias, el `id` de
    `cliente → propiedades` es el de la **captación**, y `proponerRuta` viaja
    como cadena **vacía** cuando no es accionable.
  - El catálogo de clientes es compartido para ADMIN y AGENTE; el **BROKER** es
    el único rol con alcance.
- **Convención de auditoría que hay que tener presente al escribir tests**:
  `Transiciones.iniciar()` fija el estado de nacimiento y **no** escribe
  `historial_estado` (nacer no es transicionar); solo `aplicar()` audita. Una
  oportunidad recién registrada tiene 0 filas; una visita
  programar→reprogramar→realizar tiene 2.
- **Matriz operación→rol (CERRADA, 2026-07-30)**: `docs/ai/matriz-operacion-rol.md`
  declara las **146 operaciones** del backend con sus roles y —lo importante—
  **dónde se decide el alcance**, y `MatrizOperacionRolTest` la vigila. El
  documento es la fuente de verdad y **rompe el build** si diverge del código,
  así que no puede quedar desactualizado. Lo que hay que saber al tocarla:
  - **62 de las 146 operaciones no llevan gate de rol** (3 públicas + 59
    autenticadas). No es un olvido: en la v1 el control de esas operaciones es de
    *alcance*, no de *acceso*. Por eso la columna **Alcance** es obligatoria
    cuando los roles son `TODOS`, y el test la exige no vacía.
  - Dos gates están **a nivel de clase** (`/agentes`, `/asignaciones`); la tabla
    lista siempre los roles **efectivos**.
  - El orden de la lista de roles **no importa**: `BROKER, ADMIN` y
    `ADMIN, BROKER` son el mismo permiso y el test compara conjuntos.
  - Las filas `PUBLICO` se contrastan contra las rutas `permitAll` leídas del
    propio `ConfiguracionSeguridad.java`, en los dos sentidos: abrir una ruta sin
    declararla también rompe el build.
- La migración Angular cerró **F4 Cierre** (2026-08-02), **Personas 11/11** (2026-08-03) y el
  **bloque Comercial/gestión** (2026-08-04), con `Perfil` y `Catálogos`. Con F4 el SPA recorre el
  ciclo entero del negocio, de la prospección al contrato; con Personas gestiona quién lo opera; y
  con el bloque comercial se lee a sí mismo.
- **Bloque Comercial/gestión (2026-08-04): Dashboard, Indicadores, Seguimiento, Comisiones y
  Reportes, más la campana de alertas.** No necesitó backend nuevo —E4, F6 y F7 ya estaban
  cortados— y con él **el SPA consume los 26 recursos**. Lo que hay que saber:
  - **El dashboard ES la home (`/`)**, no una pantalla aparte, y **la bandeja de tareas vive
    dentro**, no en una página-silo: es lo primero que ve el agente al entrar. Por eso
    desaparecieron del menú las entradas `Inicio`, `Dashboard` y `Mis tareas` como módulos
    separados.
  - **Una sola llamada**: `GET /dashboard` compone indicadores + bandeja. Eso resuelve de paso la
    ambigüedad que el Blazor manejaba a mano —allí una bandeja vacía podía ser "todo al día" o
    "falló la llamada"—: si la respuesta llegó, la bandeja es autoritativa. Para BROKER y ADMIN
    llega **vacía por contrato** y se les muestra su centro de control, no un "no hay tareas".
  - **`core/navegacion-legado.ts` traduce las rutas que vienen DENTRO del cable.** Alertas
    (`ruta`) y tareas (`rutaResolver`) viajan con rutas del Blazor (`solicitud-detail/12`,
    `visitas?focus=3`, `owner-detail/7`) y están congeladas, así que se adapta el SPA. Caso raro
    resuelto: **las alertas de solicitud llevan el id numérico** y la ficha del SPA enruta por
    código, así que el id se resuelve con una llamada **al pulsar**, no al listar. Lo que no se
    sabe traducir devuelve `null` y el aviso se muestra **sin enlace**, igual que hace el cable
    con los tipos que no enruta (D-F6-4).
  - **Ninguna pantalla lleva "Exportar PDF"** (D-F5-1). `Reportes` es RF-017 —el avance por
    propiedad, cuyo endpoint **ningún `.razor` consumía**— y exporta **CSV**, que es dato y no
    maquetación.
  - **Las tres operaciones de comisión son de BROKER sin ADMIN**, así que al administrador se le
    muestra la lectura sin botones; y `montoAgente`/`montoEmpresa` **no le llegan al agente**, por
    lo que esas columnas no se pintan vacías: no se pintan.
  - Gráficos: **conteos y porcentajes van en gráficos separados** (nunca dos escalas en un marco),
    la paleta de dos series está **validada** contra daltonismo y contraste —el petróleo de marca
    no pasaba, lee como gris— y los estados usan la paleta de estado reservada, siempre con
    etiqueta y valor visibles.
  - Verde: Angular **469 pruebas** (397 + 72) y `ng build` limpio; verificado en navegador contra
    el API en Docker con AGENTE y BROKER.
- **Personas necesitó tres extensiones aditivas del backend**, y la razón de las tres es la
  misma: con paginación real, lo que no calcula la base no se puede calcular en el cliente.
  - **`GET /agentes/{id}`** — ficha completa del agente en UNA llamada: identidad, supervisión
    vigente, captaciones/oportunidades/solicitudes **por estado**, cierres y las **cuatro
    magnitudes de comisión** (generada, cobrada, asignada, pagada) por moneda, con los dos saldos
    derivados y nunca negativos. Armarla combinando páginas de cuatro bandejas habría dado
    números falsos.
    **Es el uso práctico de V27**: los cierres y el dinero se filtran por
    `contrato_alquiler.id_rol_agente_cierre`, así que un agente que cambió de equipo conserva su
    historia en vez de perderla al reorganizar el organigrama.
    Alcance: el BROKER solo abre la de los agentes que supervisa hoy (403 fuera de su equipo);
    se comprueba **una sola vez al entrar**, y las consultas de dentro ya no filtran por rol —si
    lo hicieran, la ficha daría números distintos de los que el agente ve en sus bandejas—.
  - **Filtros aditivos** en `GET /agentes` (`texto`, `estado`, `estadoOperativo`, `zona`) y en
    `GET /propietarios` (`texto`, `estado`). El estado administrativo se filtra por la
    **credencial**, no por el agente: son dos máquinas distintas y por eso el resumen trae dos
    cubos.
  - **`/agentes/resumen` y `/propietarios/resumen`**, calculados en la base sobre el MISMO
    conjunto que pagina la lista. Ninguno acepta el filtro que devuelve como cubo.
  - De paso, la paginación del catálogo de propietarios para el **BROKER bajó a SQL**: antes se
    cortaba en memoria su lista completa de ids, lo que con un filtro de texto habría filtrado
    solo la página visible.
  - Verde: reactor **464** (431 servicios + 5 web + 28 aplicación **sin saltos** contra
    PostgreSQL real, que es lo que compila el JPQL nuevo), matriz **150 operaciones**,
    `e2e-personas.ps1` **122/122** y Angular **397/397**.

## Estado de la migración y siguiente paso

**Estado actual: el backend está CERRADO (2026-07-30).** 26 de 26 recursos REST migrados
(100 %), la matriz operación→rol cubierta por test y los reportes PDF **fuera del alcance**
de la migración (D-F5-1). Lo que queda es el SPA Angular y el corte.

> **"Cerrado" es sobre la paridad con la v1, no una prohibición de añadir.** Al migrar
> `PropiedadesEquipo` (2026-08-01) se agregaron **dos operaciones aditivas**,
> `GET /captaciones/propiedades-equipo` y su `/resumen`, porque esa pantalla mira la cartera
> **por inmueble** y deduplicar por propiedad no se puede hacer sobre una página de
> captaciones: el Blazor las descargaba todas. `PropiedadesAlquiladas` (2026-08-01) añadió
> `GET /contratos/resumen` y cuatro filtros opcionales en `/contratos` (`texto`, `distrito`,
> `idAgente`, `orden`) por la misma razón: **sumar la comisión de toda la cartera no se puede
> hacer sobre una página**, y el tope del recurso son 100 filas. Mismo criterio que en su día
> con `/locales/resumen`. El corte de **F3 en el SPA (2026-08-02)** añadió las dos últimas:
> `estado` en `GET /oportunidades` + `GET /oportunidades/resumen`, y `GET /visitas/resumen`
> (los cinco cubos de la agenda **y los distritos del alcance**, para que su selector sea
> data-driven sin descargar la agenda). Las dos cuentan con un solo `group by` sobre el MISMO
> conjunto que pagina la lista, y ninguna acepta el filtro que devuelve como cubo.
> El corte de **F4 en el SPA (2026-08-02)** añadió la última: `idAgente`, `estado`, `distrito` y
> `texto` en `GET /solicitudes` + `GET /solicitudes/resumen` (los siete cubos por estado **y** los
> distritos y agentes del alcance). Ahí aparece además `estado=PENDIENTES`, que **no es un
> estado**: es el cubo `{E, O}` de la cola del broker resuelto en la base, como `GESTION` en
> prospecciones, para que esa cola salga en una sola consulta paginada.
> El **Bloque 5 (2026-08-05)** añadió `GET /sesion` y un campo: la banda efectiva del actor **no
> cabe en el token** —su formato solo admite `AGENTE|BROKER|ADMIN` y `LoginResponse` está
> congelado (R1/R3)—, así que se pide aparte; y `POST /agentes` gana `idBrokerSupervisor`, porque
> el alta pasó a ser de gobierno y quien gobierna no supervisa a nadie de quien deducirlo.
> Toda operación nueva **necesita su fila en la matriz** o rompe el
> build; los filtros son aditivos y, omitidos, el cable responde exactamente igual que antes
> —incluido el orden congelado por id descendente—.

| Vertical | Estado |
|---|---|
| F0 identidad + auth (V1–V3) | cortada, verificada |
| Locales comerciales (F2-oferta, V4/V11) | cortada, listado definitivo 18/18 E2E |
| Prospección + captación (F2-proceso, V5) | cortada, verificada |
| Núcleo multi-tenant (V6) | aplicado, 46/46 E2E |
| **F3 Demanda (V7)** | **cortada, 89/89 E2E (2026-07-27)** |
| **F4 Solicitud → contrato (V8)** | **cortada, 116/116 E2E (2026-07-28)** |
| **F6 alertas + F7 tareas (V9)** | **cortadas, E2E verde (2026-07-28)** |
| **E1 personas + perfil (V10)** | **cortada, 99/99 E2E (2026-07-29)** |
| **E2 reportes-propietario** | **cortada, 50/50 E2E (2026-07-29)** |
| **E3 ficha comercial** | **cortada, 60/60 E2E (2026-07-29)** |
| **E4 dashboard + indicadores + seguimiento** | **cortada, 115/115 E2E (2026-07-29)** |
| **Matriz operación→rol (150 operaciones)** | **cubierta por test; 146 al cerrar, +4 aditivas de Personas (2026-08-03)** |
| **Bloque 3 — sesiones, auditoría y bloqueo (V29+V30)** | **CERRADO (2026-08-05)**: `s0-sesiones` 11/11, `s0-bloqueo` 21/21 y **regresión completa de las 13 suites históricas** (§ siguiente). Detalle en «Decisiones que hay que conocer» |
| **Bloque 4 — contraseñas y recuperación (V31)** | **CERRADO (2026-08-05)**: `s0-contrasenas` 59/59 y **regresión de las 16 suites, 951 comprobaciones, 0 fallos**. Cierra H-02 y H-08. Incluye las **2 pantallas Angular** que faltaban |
| **Bloque 5 — roles y gobierno (V32–V35)** | **CERRADO (2026-08-05)**: matriz **D-S0-17 aprobada tal cual** y ejecutada. `usuario_organizacion` pasa a ser la **fuente de verdad de la banda** (H-14 cerrado) y `detalle_broker.es_administrador` deja de decidir nada. **26 operaciones regateadas**, `GET /sesion` aditivo, `e2e-s0-roles` **48/48**. `PLATFORM_ADMIN` queda **fuera** por alcance (D-30) |
| **Bloque 6 — MFA administrativo y recuperación (V37 + V38)** | ✅ **CERRADO (2026-08-06)**. Cerrado y verificado: enrolamiento TOTP, QR y clave manual, confirmación y códigos de respaldo, login en dos pasos, **anti-replay del primer OTP**, bloqueo e invalidación de sesiones, SPA de enrolamiento, **códigos de error estables**, **reautenticación reforzada** (regenerar códigos y reemplazar autenticador) y **recuperación de nivel 2** con padrón de gobierno y aviso persistente. Debajo: anti-replay atómico, tres contadores de intentos, códigos con identificador + 80 bits y hash lento, elevación de 5 min, e invariante de **administrador OPERATIVO**. `e2e-s0-mfa` **89/89** + 23 unitarios + `PadronDeGobiernoIntegrationTest`. **Documentación cerrada** y **V38 construida (2026-08-06)**: concesión de un solo uso, doble aprobación estructural, **tres identidades** en la fila (custodio A, custodio B y operador, con `CHECK` de que difieren — D-S0-52), custodios en **configuración** y no en tabla (D-S0-51), conector de gestión ligado a `127.0.0.1` fuera de la API del producto, consumo atómico, caducidad comprobada en cada uso y cierre automático. **Apagada por defecto**; en `prod` encenderla sin los dos hashes o sin canal externo **detiene el arranque**. **Simulacro completo verde por el cable**: `e2e-s0-emergencia` **30/30**, más `SimulacroRecuperacionIntegrationTest`. **Lo que falta NO es del bloque, es de la activación** (D-S0-53): designar a los dos custodios reales y construir el canal externo. Sin ellos, `prod` no arranca con la bandera encendida — a propósito |
| **Normalización económica/contractual (V13–V20)** | **aplicada, repositorios 4/4 + 18/18 E2E aislado (2026-08-01)** |
| ~~F5/F8 reportes Jasper~~ | **fuera de alcance (D-F5-1)** — no se portan |
| **F3 Demanda en el SPA (9 pantallas)** | **TERMINADA (2026-08-02) y FIRMADA (2026-08-03): gate `e2e-demanda-busqueda.ps1` 69/69** sobre 100.000 filas/tabla con los tres criterios. El rojo del criterio 3 (3,3 s) **no era del producto**: lo ponía el proxy de puertos de Docker Desktop renovando conexión cada 200 peticiones. Con el artefacto retirado, el peor caso del escenario que fallaba cae de **3.357 a 1.577 ms** sin tocar consulta, índice ni umbral — ver `docs/ai/diagnostico-pico-rc003-gate-f3.md` §9 |
| **F4 Cierre en el SPA (6 pantallas)** | **TERMINADA (2026-08-02)** — bandeja, cola del broker, alta, expediente + cierre del alquiler, documentos y evaluación; gate `e2e-solicitudes-busqueda.ps1` **48/48 sobre 100.000 filas** |
| **Bloque Comercial/gestión en el SPA (5 pantallas + campana)** | **TERMINADO (2026-08-04)** — Dashboard (home, con la bandeja F7 dentro), Indicadores, Seguimiento, Comisiones y Reportes (RF-017), más la campana de alertas F6 en el shell. Angular **469/469** |
| Frontend Angular (≈52 pantallas) | **en curso — 49 pantallas: Oferta, F2, F3, F4, Personas (11/11) y Comercial/gestión (5) COMPLETAS + Perfil y Catálogos, y transversales de auth/archivos/formato/campana**. **Las dos de identidad se cerraron el 2026-08-05** (cambio de contraseña y recuperación de acceso, que en la v1 eran mocks sin endpoint): viven **fuera del shell** porque la de cambio tiene que funcionar con la sesión capada, cuando el armazón no se puede pintar. Queda la paridad final. Angular **497/497** |

### Regresión del cierre del Bloque 5 (2026-08-05)

**18 suites en una sola invocación, 1.003 comprobaciones, 0 fallos** y cero recursos residuales.
Log en `verificacion/evidencia/2026-08-05-regresion-bloque5.log`.

| Suite | Resultado | Suite | Resultado |
|---|---|---|---|
| **`s0-roles`** (nueva) | **48/48** | `f4-solicitud` | **116/116** |
| `personas` | 122 → **126/126** | `f6-f7-alertas-tareas` | **68/68** |
| `s0-contrasenas` | **59/59** | `reportes-propietario` | **50/50** |
| `s0-bloqueo` | **21/21** | `ficha-comercial` | **61/61** |
| `s0-sesiones` | **11/11** | `e4-dashboard` | **120/120** |
| `v6` | **46/46** | `estabilizacion-alquiler` | **18/18** |
| `f3-demanda` | **103/103** | `locales-listado` / `-busqueda` | **18/18** · **21/21** |
| `demanda-busqueda` | **69/69** | `solicitudes-busqueda` | **48/48** |

**Lo que enseñó la primera pasada, y conviene no volver a confundir:**

- **Tres comprobaciones cayeron y las tres eran correctas.** `personas` fijaba *"solo debe existir un
  broker administrador"* (regla retirada por §2.5), *"el ADMIN no registra agentes"* (fila 17,
  invertida) y *"un broker no modifica agentes de otro equipo"* (fila 18, ahora 403). Un test que
  falla tras cambiar una regla **es la señal**; se reescribe para fijar la nueva, no se ablanda.
- **El fixture de segunda organización insertaba `rol = 'ADMIN'`**, banda que V33 retiró del
  vocabulario. Lo detectó el `CHECK` nuevo, que es justo para lo que está.
- **Un p95 de `locales-busqueda` (1.276 ms sobre un umbral de 1.000) NO era del producto**: es
  contención de correr 18 suites seguidas con los contenedores de dev levantados. En limpio pasa, y
  el `left join` que el Bloque 5 añade a la consulta por petición mide **0,49 ms** (`EXPLAIN
  ANALYZE`). Mismo patrón que el falso rojo del gate de F3 — antes de culpar a un cambio, medir.

### Regresión completa del 2026-08-05 (cierre del Bloque 3)

**Las 15 suites en una sola invocación, cada una con su entorno efímero**, 28 minutos de reloj y
**cero recursos residuales**. Log completo en
`verificacion/evidencia/2026-08-05-regresion-bloque3.log`.

| Suite | Resultado | Suite | Resultado |
|---|---|---|---|
| `s0-sesiones` | **11/11** | `reportes-propietario` | **50/50** |
| `s0-bloqueo` | **21/21** | `ficha-comercial` | **61/61** |
| `v6` | **46/46** | `e4-dashboard` | 119/120 → **120/120** |
| `f3-demanda` | **103/103** | `estabilizacion-alquiler` | **18/18** |
| `f4-solicitud` | 114/116 → **116/116** | `locales-listado` | **18/18** |
| `f6-f7-alertas-tareas` | **68/68** | `locales-busqueda` | **21/21** |
| `personas` | **122/122** | `demanda-busqueda` | **69/69** |
| | | `solicitudes-busqueda` | **48/48** |

**Los tres checks en rojo no eran del Bloque 3 ni del producto: eran dos suites que se habían
quedado atrás**, y es exactamente para lo que sirve correrlas todas.

- **`f4-solicitud` (2 checks).** Las claves del almacén ganaron el prefijo `tenant/{organizacionId}/`
  ese mismo día (preparación para S3, que se hace **antes** de mover binarios para no moverlos dos
  veces), y la suite seguía esperando la ruta vieja. El segundo check era peor que el primero:
  contaba binarios en un directorio que ya no existe, sacaba 0 y acusaba de huérfanos a documentos
  que estaban bien. Ahora la carpeta se **deriva de la clave que devuelve el API** en vez de
  reconstruirse a mano.
- **`e4-dashboard` (1 check).** Esa suite todavía borra su fixture a mano, y desde **D-27/V28** el
  alta de cliente y propietario deja además su constancia de autorización. El `delete from persona`
  chocaba contra la FK, **abortaba la transacción entera** y no se borraba nada — por eso el residuo
  salía `2|2|1` y el síntoma aparecía lejos de la causa.

**La evidencia histórica anterior (2026-07-30)** era: v6 46/46, f3 89/89, f4 116/116, f6-f7 68/68,
personas 99/99, reportes-propietario 50/50, ficha-comercial 60/60, e4-dashboard 115/115 y
locales-listado 18/18. Los números que subieron lo hicieron porque las suites crecieron con sus
verticales, no porque cambiara nada del contrato.

### Regresión del cierre del Bloque 4 (mismo día)

El Bloque 4 toca `FiltroAutenticacionJwt`, que está en el camino de **todas** las peticiones
autenticadas, así que la regresión se repitió entera con la suite nueva dentro:
**16/16 suites, 951 comprobaciones, 0 fallos**, sin recursos residuales. Log en
`verificacion/evidencia/2026-08-05-regresion-bloque4.log`. La única suite nueva es
`s0-contrasenas` (**59/59**); las quince anteriores repiten su marca exacta.

Dos cosas que el filtro gana y que valía la pena verificar así:

- ahora resuelve **dos** preguntas por petición (¿sesión revocada? ¿cuenta capada?) con **una sola
  consulta** — son dos columnas de la misma fila, y separarlas habría duplicado la lectura en el
  camino caliente;
- la **revocación gana al capado**: un token muerto responde 401 y no llega al 403 de "cambia tu
  contraseña", que admitiría que la sesión existe.

**Al cerrar F4 en el SPA (2026-08-02)** se reejecutaron los dos que la extensión aditiva podía
romper: `e2e-f4-solicitud.ps1` **116/116** —el contrato congelado sigue intacto, cascada de siete
efectos incluida— y el nuevo `e2e-solicitudes-busqueda.ps1` **48/48** sobre 100.000 filas. El
reactor pasa **453 pruebas** —incluidos los 4 de la matriz operación→rol y, con `TEST_DB_URL`
apuntando a `controllocal_repositorios`, los 16 de integración que sin esa variable **se saltan
en silencio**: son los que compilan de verdad el SQL nativo nuevo— y Angular **385/385**.

**Dos decisiones que estaban abiertas se cerraron el 2026-07-29** y cambian comportamiento:
- **D-F4-5 tapada**: `PATCH /solicitudes/{id}/documentos/{idDoc}/revisar` ahora comprueba el
  alcance del broker, igual que `conformar` en bloque y la evaluación. Donde la v1 responde **200**,
  la v2 responde **403**. Divergencia deliberada del contrato congelado; el Blazor no la alcanza por
  navegación.
- **Los dos "mensajes inventados" de solicitudes**: la premisa era falsa —la v1 no devuelve 500 al
  duplicar, su `ApiExceptionMapper` responde **409**—, así que los dos `if` convertían un 409 en un
  400. Se quitó entero el de oportunidad (código muerto) y el de código lanza la nueva
  **`ConflictoException` → 409**. Regla que queda: **el texto de un 409 no está congelado, el código
  sí** (solo 401/403/429 tienen mensaje exacto).

**E1 personas + perfil**: contrato y rarezas en
`docs/ai/contrato-congelado-e1-personas-perfil.md`. Lo esencial:

- El propietario es el único rol sin tabla de detalle; el catálogo es compartido para ADMIN/AGENTE,
  el BROKER queda acotado por propiedades y `cantidadLocales` es un contador con alcance resuelto
  por UNION nativo. POST/PUT responden ese contador en 0 por paridad.
- Agentes y brokers crean atómicamente persona + `USUARIO_INTERNO` + rol operativo + credencial;
  `UsuariosInternos` concentra ese armado. El alta de agente crea además su supervisión inicial.
- Los contadores de agentes/brokers se calculan en SQL por lote. POST/PUT de agente y
  `/brokers/{id}/agentes` conservan la rareza del cable y responden contadores comerciales en 0.
- `supervision_agente` es la relación vigente. V10 `reasignacion_agente_broker` conserva el evento
  histórico completo. Al cambiar supervisor se fuerza el UPDATE de la fila anterior antes del
  INSERT para respetar el índice único parcial dentro de la misma transacción.
- `/perfil` solo cubre teléfono y foto; no hay endpoint de contraseña en la v1. La foto valida
  extensión (no magic bytes) y guarda la clave opaca en `persona.foto_clave`.
- D-20 sigue intacta: el token congelado no lleva tenant y, durante la convivencia, el login solo
  busca credenciales en `BROX_LEGACY`. El E2E crea una segunda organización y demuestra que su
  credencial no obtiene sesión ni su broker aparece por los endpoints E1.

**E2 reportes-propietario**: contrato congelado en
`docs/ai/contrato-congelado-e2-reportes-propietario.md`. La tabla y entidad ya existían desde V9;
no se añadió V11. Lo esencial:

- `GET` lista, `GET /preview` deriva el avance y `POST` registra. El PDF Jasper por código de
  captación **no se porta** (D-F5-1): el avance sigue disponible por JSON, lo que sale del
  alcance es su impresión.
- Consultas, visitas realizadas y objeciones se agregan en SQL por captación, periodo y
  organización. El POST ignora esos tres valores si el cliente intenta enviarlos manualmente.
- ADMIN ve el tenant, BROKER el equipo vigente y AGENTE lo suyo; solo el AGENTE responsable
  registra. El E2E crea un segundo tenant y comprueba 404 antes de retirarlo.
- Registrar un reporte reinicia el reloj de 15 días de F7: la siguiente reconciliación completa
  la tarea `REPORTE_PROPIETARIO` vencida.
- `verificacion/e2e-reportes-propietario.ps1` pasa **50/50** y la suite dedicada del service,
  **11/11**.

**E3 ficha comercial**: contrato congelado en
`docs/ai/contrato-congelado-e3-ficha-comercial.md`. No añade migración: agrega una lectura
transversal sobre V4/V5/V7/V8. Lo esencial:

- Corta los cuatro GET de `/clientes/{id}/ficha-comercial[/{section}]` y
  `/propietarios/{id}/ficha-comercial[/{section}]`, con 8 y 7 secciones respectivamente.
- Conserva la carga inicial parcial, `cantidadLocales=0` en la cabecera de propietario,
  los aliases `page`/`pagina` y `page_size`/`tamano`, y el tope de 8 filas.
- AGENTE ve solo su historia y sin nombre de agente; BROKER ve su equipo o captaciones que revisa;
  ADMIN ve el tenant. Los ids de otro tenant responden 404.
- `verificacion/e2e-ficha-comercial.ps1` pasa **60/60** y la suite dedicada del service,
  **12/12**.

**E4 dashboard + indicadores + seguimiento**: contrato congelado en
`docs/ai/contrato-congelado-e4-dashboard-indicadores-seguimiento.md`. Cierra el backend.
Tampoco añade migración: son tres lecturas que **agregan** sobre V4–V10. Lo esencial:

- Corta `GET /dashboard`, `/indicadores/resumen`, `/indicadores/avance` (RF-017) y
  `/seguimiento-comercial`. El `GET /indicadores/reporte/pdf` de la v1 queda **fuera del
  alcance** con el resto de Jasper (D-E4-1, superada por D-F5-1: ya no es un diferido); el
  JSON que lo alimenta ya está cortado, así que la futura página de reportes no necesita
  consulta nueva.
- **Los tres recursos no llevan gate de rol**: los tres roles entran y lo que cambia es el
  alcance y el `ambito`. La única pieza con rol es la bandeja embebida en `/dashboard`, que
  sale de `/tareas` (solo AGENTE): para BROKER y ADMIN **no es un 403, es bandeja vacía**.
- **Dos reglas de alcance distintas que NO se unifican** (D-E4-4): indicadores alcanza
  **solo por agente responsable** —la captación no amplía el de nadie ahí—; seguimiento
  alcanza por la **unión** de agente propio y agente de la captación, y esa segunda rama
  existe **solo para el BROKER** (al AGENTE no le suma nada).
- **El contrato no tiene agente ni captación propios**: los hereda de su solicitud y, en su
  defecto, de su oportunidad. En la v1 eso sale de que el DAO entrega la solicitud
  "shallow"; aquí es una regla explícita y es la única consulta de E4 sin filtro de rol en
  el WHERE.
- **La v1 cargaba seis tablas completas en cada carga del dashboard**; la v2 baja el scope y
  la ventana al WHERE y lee **proyecciones estrechas** del paquete `query` (D-E4-2). Misma
  respuesta, otra cantidad de lectura.
- Rarezas del cable que se replican y que están fijadas por test (D-E4-3): el `100` fijo de
  la primera fila del embudo, *"Con visita realizada"* que **no mira el estado** de la
  visita, `captacionesPendientes` duplicando a `captacionesPorRevisar`, el donut que **no**
  depende del periodo (la salud sí) y el operativo que **cae a todas las prospecciones**
  cuando la ventana no tuvo ninguna.
- Y la más fácil de "arreglar" por error: en el seguimiento, las filas **sin fecha
  encabezan** la lista, porque el `.reversed()` del comparador invierte también el
  `nullsLast`.
- `verificacion/e2e-e4-dashboard.ps1` pasa **115/115** (compara deltas sobre el seed, no
  valores absolutos) y las suites dedicadas, **27/27** y **17/17**.

**Todo lo que falta para completar la migración —backend y frontend— y el ORDEN de
trabajo sugerido (con el porqué de cada paso) están en
`docs/ai/checklist-migracion.md`. Empieza por su sección 0.**

**F4 COMPLETA** (solicitud de alquiler → documentos → evaluación del broker →
contrato/comisión). Es la que cierra el ciclo, y ya lo cierra: la cascada de
siete efectos del contrato es lo que finaliza la oportunidad como exitosa
—por eso `POST /oportunidades/{id}/cierre-exitoso` responde 400 para siempre:
el cierre no lo produce un botón—. Contrato congelado en
`docs/ai/contrato-congelado-f4-solicitud.md`, con las **cuatro decisiones
D-F4-1…4 resueltas**. Hecho de punta a punta: **V8 aplicada** (6 tablas,
`tipo_documento_requerido` como catálogo global con los ids 1..8 del cable),
las 6 entidades, los 6 repositorios, los **cinco services** sobre
`Transiciones`, los **tres controllers** con sus 11 DTOs congelados y el
almacén de binarios. `mvn clean install` verde: **192/192** con los tres gates
de ArchUnit, y **116/116 en `verificacion/e2e-f4-solicitud.ps1`**.

`SolicitudServiceImpl` tiene **32 tests de comportamiento**; los cinco services de F4 cuentan con
cobertura dedicada además del E2E.

Lo que conviene tener presente al tocar F4:

- Registrar la solicitud **transiciona la oportunidad a `S`** y exige captación
  ACTIVA + oportunidad ABIERTA; el alta **no** comprueba que la oportunidad sea
  del agente (cable real).
- Reenviar a evaluación exige que el agente **tenga broker supervisor activo**.
- El **tipo de evaluación se deriva del resultado** (el broker no lo elige) y
  solo cabe **una FINAL por solicitud**; ambas reglas ya están en la BD. Pero
  el request **exige `tipoEvaluacion` presente y válido** aunque luego lo pise:
  se ignora su valor, no su presencia.
- **Otra vez dos reglas de alcance distintas**: solicitudes y sus documentos
  alcanzan **por agente**; contratos, **por captación**. No unificar.
- El cierre (`POST /contratos`) es **una transacción con siete efectos** y deja
  **cuatro filas** en `historial_estado` —oportunidad, solicitud, captación y
  local—, donde la v1 no dejaba ninguna. Alertas y tareas del efecto 7 están
  cableadas desde F6/F7.
- **La web es la que toca los binarios**: `DocumentoSolicitudService` solo
  recibe metadatos con `rutaArchivo` = clave del almacén, igual que las fotos
  de F2. De las **cuatro vías de subida** de la v1 se portan tres (base64,
  octet-stream y por trozos); `documentos/local` **no** (D-F4-1: leía del disco
  del servidor y no sobrevive al API en contenedor).
- **El tipo de documento se valida al REGISTRAR, no antes de subir**: la web no
  ve el dominio (regla de capas), así que si el código del tipo es inválido el
  controlador **borra el binario recién subido**. Mismo 400 y mismo mensaje que
  la v1; el E2E fija el invariante "tantos binarios como documentos".
- **D-F4-5 está cerrada**: revisar un documento suelto comprueba el alcance del
  broker y responde 403 fuera del equipo. Es una divergencia deliberada respecto
  de la v1, cubierta por test y E2E; ver §9 del contrato.
- **Dos gates fáciles de romper**: el `tamano` por defecto de `/contratos` es
  **100**, no 10; y los dos de comisión son `hasRole('BROKER')` **sin** ADMIN
  (funciona porque el filtro JWT publica una sola authority, `ROLE_<rol>`).
- **La atribución del cierre se congela (V27, 2026-08-03)**. Hasta aquí el
  contrato guardaba el vínculo y el snapshot económico, pero **no a quién se le
  atribuye el alquiler**: agente, captación, inmueble y cliente se releían de la
  cadena vigente en cada consulta, así que una reasignación posterior
  **reescribía la historia** de un cierre de hace meses. Ahora el alta graba
  `id_rol_agente_cierre`, `id_rol_broker_cierre`, `id_captacion`, `id_propiedad`
  e `id_rol_cliente` en la propia fila. Lo que hay que saber:
  - **El cable no cambia.** `agenteId`/`agenteNombre` ya salían con esos
    valores; ahora salen del snapshot. El backfill dejó lo mismo en toda fila
    existente, así que la respuesta solo se moverá el día que alguien reasigne.
    Sin campos nuevos, sin endpoint nuevo, sin fila nueva en la matriz.
  - **El alcance NO se toca**: el BROKER sigue alcanzando por captación
    supervisada **hoy**. El snapshot es trazabilidad, no permiso.
  - El broker atribuido es el supervisor **vigente al cerrar**; sin supervisor
    queda **NULL** y no se rellena después con el de turno.
  - La **renovación hereda** la atribución del contrato que renueva.
  - **No reactiva el inmueble** al finalizar/rescindir: eso está decidido en la
    matriz (dejan *tarea* de revisión) y cambiarlo sería decisión funcional.
  - **Comisión generada/cobrada/pagada ya estaban separadas** desde V15
    (`monto_bruto` + `comision_movimiento` C/P/A/R con saldos publicados); V27
    no las toca. Detalle en `docs/ai/contrato-congelado-f4-solicitud.md` §6.
  - Verificado, no supuesto: `ContratoServiceImplTest` **30/30** (los 27 de la
    cascada de siete efectos siguen verdes, más 3 de atribución), reactor
    **423/423** + web 5/5, y `verificacion/e2e-f4-solicitud.ps1` **116/116**
    contra PostgreSQL real con V27 aplicada por Flyway.

- **F6 alertas + F7 tareas (V9, cortadas)**: la campana y la bandeja del
  agente. Contrato congelado en
  `docs/ai/contrato-congelado-f6-f7-alertas-tareas.md`. Con esto se retiraron
  **todos** los `TODO(F6-alertas)`/`TODO(F7-tareas)` del backend, incluida la
  deuda de F2 que llevaba abierta desde la primera vertical. Lo que hay que
  saber para tocarlas:
  - **No hay columna de destinatario.** Una alerta se ata SIEMPRE a un AGENTE;
    su broker la ve por la supervisión, así que **quién la lee lo decide el
    TIPO**. `CAPTACION_CREADA` cuelga del agente pero está escrita para el
    broker. No inventar un `idDestinatario`.
  - **`Alerta` y `Tarea` NO son `Transicionable`** y no es un olvido:
    `entidad_tipo` las declara `auditable = FALSE` desde V2, porque la bandeja
    se reconcilia en cada lectura y auditarla inundaría `historial_estado`.
  - **`GET /tareas` ESCRIBE**: deriva, reconcilia y recién entonces devuelve.
    Es la única forma de tener la bandeja al día sin planificador. Lo mismo
    `GET /alertas`, que materializa el barrido de recontacto como mucho una vez
    cada 5 minutos.
  - **Cancelar una tarea la mata para siempre**: `CANCELADA` bloquea que el
    reconcile la vuelva a crear para esa entidad. No es "más tarde".
  - **`entidad_tipo` de alertas/tareas NO es el registro de auditoría** aunque
    se llame igual: el cable emite `INMUEBLE` donde la v2 tiene `PROPIEDAD`
    (D-F6-4). Por eso V9 usa un CHECK y no una FK — con FK se caen la alerta de
    modificación sensible y el efecto 7 del cierre.
  - Dos bugs congelados que se replican: la alerta de modificación sensible
    viaja con el tipo `SOLICITUD_EVALUADA` (no hay uno que encaje, D-F6-5) y
    **`CAPTACION_CREADA` casi nunca se emite**, porque `captar` crea la
    captación saltándose el alta que avisa.
  - La bandeja corta en **10** y descarta el resto **en silencio**.

**Retirado del alcance (D-F5-1, 2026-07-30)**: los **5 endpoints PDF** de la v1 —los 4 de
captación (contrato de exclusividad, ficha de captación, ficha de propiedad y reporte al
propietario) y `GET /indicadores/reporte/pdf`— **no se portan**. No es un diferido: la nueva
funcionalidad de reportes se diseñará desde cero a partir de la nueva página de reportes, y
recién entonces se elegirá con qué se imprime. Detalle, consecuencias y lo que ya está resuelto
en `docs/ai/decision-reportes-pdf-fuera-de-alcance.md`. Dos cosas de ahí que conviene tener a
mano al retomarlo: **la capa web no puede leer entidades** (los mappers de la v1 lo hacen y por
eso no son portables — hay que componer desde DTOs de service), y **el contenedor Alpine no
tiene fuentes**, así que cualquier motor que dibuje texto con `java.awt` necesita fuentes
embebidas.

Deudas abiertas conocidas:

- **Paginación por cursor/keyset en las tres bandejas de F3** (`/oportunidades`, `/visitas`,
  `/interacciones`). **No se implementa parcialmente**: o las tres o ninguna.
  El `OFFSET` domina el coste en el listado **sin texto** (707 → 1.716 ms de la página 1 a la
  profunda). En el camino **con texto** la curva es plana (1.309 → 1.298) y lo que se paga es
  construir y deduplicar el conjunto de candidatos **dos veces por llamada**; el salto en sí
  cuesta ~110 ms de ~900. Esperar la mejora en el listado llano, no en la búsqueda.
  (La §5 de `docs/ai/contrato-listados-paginados.md` decía que en la página profunda se paga
  el `OFFSET` **en los dos caminos**; era falso para el camino con texto y quedó corregido
  el 2026-08-03.)
  > **Los 3,3 s del 2026-08-03 NO eran esta deuda.** Queda escrito porque la hipótesis inicial
  > —"una cola estable en ~3,3 s, un segundo modo"— era **falsa** y costó cinco diagnósticos
  > descartarla. El pico lo ponía el **proxy de puertos de Docker Desktop**: Tomcat cierra la
  > conexión tras 100 peticiones y el cliente de PowerShell agrupa 2, así que el par se renovaba
  > **cada 200 peticiones** y rehacer esa conexión desde Windows costaba **~2,05 s** clavados.
  > Como la secuencia del gate es determinista, caía siempre en el mismo escenario y parecía
  > propio de esa consulta. Medido: 4 pausas de ~2.070 ms en 900 llamadas contra el puerto
  > publicado, **0** en 900 desde dentro de la red de Docker, y **0** al fijar
  > `SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1` **sólo en el entorno E2E**. PostgreSQL no ejecutó
  > ni una sentencia > 1,5 s en toda la medición y la pausa de JVM más larga fueron 18,8 ms.
  > Análisis completo y evidencia en `docs/ai/diagnostico-pico-rc003-gate-f3.md`.
  > **Antes de creerse los percentiles de cualquier gate de rendimiento en esta máquina**, correr
  > `Invoke-E2E.ps1 -Suite sonda-transporte`: detecta si el entorno volvió a meter pausas.
  **Decidido y cerrado: no se resuelve con una proyección materializada** — duplicar datos y
  sincronizarlos no arregla un `OFFSET`. Al abordarlo: el cursor tiene que ser la **tupla completa
  del orden congelado** de cada bandeja (oportunidades `id desc`; visitas `fecha_visita desc, id
  desc`; interacciones `fecha_hora desc, id desc`), o habrá filas repetidas o saltadas entre
  páginas; `idsPorTexto` pagina igual y hay que tratarlo a la vez; y como el `PageResponse`
  congelado expone `page`/`pageSize`/`totalRecords`, un cursor es una **extensión aditiva** con su
  fila en la matriz, no un cambio del cable.

- **Frontend Angular: es todo lo que queda del camino crítico.** El backend está cerrado
  (26/26 + matriz de roles); el SPA ya tiene 29 pantallas de negocio y **385 pruebas verdes**.
  Oferta, F2, **F3 Demanda** y **F4 Cierre** están completas: bandejas, seguimiento,
  alta/subsanación, expediente, revisión, reasignaciones auditadas, bitácora del cliente,
  oportunidades, agenda de visitas con sus cinco operaciones, interacciones con su bitácora
  polimórfica y **el cierre del alquiler**. Siguen Personas, identidad/perfil y gestión.
  Lo que hay que saber de **F4** antes de tocarla:
  - **`Cierre.razor` del Blazor no es de F4**: es "cerrar una captación", y esa acción ya vive en
    `CaptacionDetail` desde F2. Por eso la vertical cierra con **6** pantallas, no con 7. El
    cierre de F4 —registrar el contrato— vive dentro de `SolicitudDetail`, como en el legado.
  - **`estado=PENDIENTES` no es un estado**: es el cubo `{E, O}` de la cola del broker, resuelto
    en el repositorio como `GESTION` en prospecciones. El resumen lo devuelve ya sumado
    (`pendientes = enRevision + observadas`) para que la pantalla no lo calcule por su cuenta.
  - **La bandeja busca por conjunto de candidatos con CINCO ramas** —código de solicitud, código
    de oportunidad, dirección y distrito de la propiedad, nombre del cliente y nombre del
    agente—, una más que ninguna anterior. **V26** pone el trigrama del código de la solicitud
    (el resto ya los daban V11 y V25) y el índice de recorrido por tenant. Gate:
    `verificacion/e2e-solicitudes-busqueda.ps1`, **48/48 sobre 100.000 filas** con los tres
    criterios de la §5: discriminante **32–147 ms** de p95, no discriminante bajo RC-003 con su
    referencia sin texto registrada en las dos profundidades, y paginación profunda 1.445.
  - **Clasificar "discriminante" por el `totalRecords` de la respuesta es un error**, y el gate
    de F3 lo arrastra: con otro filtro activo, ese número mide el efecto conjunto. Aquí
    `texto=Calle&estado=PENDIENTES` devuelve 28.572 filas —bajo el umbral— pero su texto casa
    con las 100.000, así que el trabajo que mide es el de construir el conjunto entero. Se
    clasifica por el término, con su medida sin otros filtros.
  - **El `/resumen` no se juzga con el objetivo de 1.000 ms**: dos de sus tres consultas
    (`distritosDisponibles`, `agentesDisponibles`) recorren el **alcance completo** a propósito
    —ofrecen las opciones del alcance, como `/clientes/resumen` y `/visitas/resumen`—, así que
    su coste es el de listar sin filtro y el límite que aplica es RC-003.
    **Cifra canónica: 444 ms de p95**, la de la corrida de firma (48/48, máquina en reposo),
    contra 82 ms de la lista con el mismo término. Es la única que vale: las otras dos medidas
    que circularon —694 y 1.381 ms— son de corridas **que no firmaron** (la primera falló en el
    clasificador y tenía el entorno de desarrollo levantado; la segunda se midió con una build
    de Angular en paralelo).
  - **El alta y el envío a evaluación son dos pasos** en el SPA, donde el Blazor los encadenaba:
    la solicitud nace REGISTRADA, se completa el expediente y desde ahí se envía.
  - **Cargar un documento y reenviar la solicitud NO son la misma condición.** El broker puede
    observar un documento suelto sin devolver la solicitud —que sigue en `E`—, así que se carga
    mientras la solicitud no esté resuelta y se reenvía solo desde `G`/`O`.
  - **De las cuatro vías de subida el SPA usa una sola**, octet-stream: base64 infla un tercio y
    la subida por trozos existe por un bug del cliente .NET que muere con el Blazor.
  - **El 404 de `GET /contratos/oportunidad/{id}` es el caso normal** mientras la operación sigue
    viva: quien lo llame tiene que tratarlo como "todavía no", no como error.
  - **La extensión aditiva no tocó el contrato congelado**, y está comprobado, no supuesto:
    `verificacion/e2e-f4-solicitud.ps1` vuelve a pasar **116/116** después de los cambios,
    incluida la cascada de siete efectos verificada efecto por efecto.
  Lo que hay que saber de F3 antes de tocarla:
  - **Tres reglas de alcance distintas conviven y no se unifican**: oportunidades y visitas
    alcanzan **por captación**; interacciones, **por agente supervisado**; el catálogo de clientes
    es compartido y solo acota al BROKER. El SPA no las replica —las impone el backend—, pero sí
    las explica en pantalla para no prometer listas que llegarán vacías.
  - **No hay botón de "cerrar exitosa" en ninguna pantalla** y es correcto:
    `POST /oportunidades/{id}/cierre-exitoso` responde 400 siempre porque ese cierre lo produce la
    cascada de `POST /contratos`.
  - **`realizar` y el desenlace de la visita son dos pasos**, y el segundo es irrepetible; un
    resultado de no continuidad cierra la oportunidad, así que la pantalla exige la razón
    tipificada en vez de dejar que la explique el 400.
  - **`grupo` en interacciones parte el universo en dos**, no filtra por contexto, y el catálogo
    de `resultado` depende del contexto: el filtro se acota a la pestaña y se limpia al cambiarla.
  - **Las tres bandejas buscan por conjunto de candidatos** (§5 de
    `docs/ai/contrato-listados-paginados.md`): una rama por tabla, `UNION`, el mismo conjunto para
    conteo/página/KPI e **V25** con sus trigramas. El `query` **no existe** en la firma del método
    JPQL de listado: sin texto va por ahí, con texto va por las ramas nativas. En esas consultas
    los roles viajan como literal `bigint[]` (`Alcance.paramRolesArray()`), no como `IN (:roles)`.
    Gate firmado sobre **100.000 filas por tabla** (`e2e-demanda-busqueda.ps1`), con los **tres
    criterios** de la §5: discriminante < 1.000 ms (medido 48–232), no discriminante bajo RC-003
    —ahí `Seq Scan` es el plan correcto y el caso equivale a listar sin filtro— y paginación
    profunda bajo RC-003, con la sustitución de `OFFSET` por cursor/keyset como tarea posterior.
  - **`PlanDeConsulta` no es un adorno.** El texto viaja como parámetro dentro de un `LIKE`; tras
    cinco ejecuciones el driver lo vuelve *prepared statement* del servidor y PostgreSQL pasa a un
    **plan genérico** que elige `Nested Loop` con 100.000 iteraciones: el doble de coste. Se
    corrige con `SET LOCAL plan_cache_mode` **dentro de la transacción de búsqueda** — nunca en la
    conexión del pool ni con `prepareThreshold=0`, que se lo cobrarían a todo el sistema. Síntoma
    para reconocerlo: la llamada **en frío sale más rápida que el régimen**.
  - **Al sacar un predicado de una consulta, saca también sus joins.** Al mudar el texto al
    `UNION` quedaron ocho tablas unidas para contar oportunidades donde bastan cuatro; el conteo
    las seguía pagando (1.043 → 371 ms de p95).
  - **`EstadoRequerimiento` usa la letra** (`A`/`P`/`C`), no el nombre del enum: el §3 del contrato
    F3 es anterior a la normalización V15–V20 y quedó desactualizado. Manda
    `docs/ai/matriz-codigos-estado.md`.
  - Gotcha de Angular que salió aquí: un `computed()` que lee `FormControl.value` **no es
    reactivo**. Si una regla de habilitación depende de lo escrito, hay que reflejar el formulario
    con `toSignal(valueChanges)`.
  Lo que conviene saber antes de tocar la vertical de oferta:
  - **La ficha de una propiedad se arma encadenando ids**: la captación trae `idLocal` y el
    local trae `idPropietario`. La v1 descargaba las tres bandejas y emparejaba el local por
    dirección y el propietario por nombre; eso no se replica, porque además de cargar de más
    puede acertarle al registro equivocado.
  - `GET /captaciones/codigo/{codigo}` responde **403, no 404**, cuando la captación existe
    pero es de otro equipo. El SPA lo explica como alcance y no filtra ningún dato.
  - **La cartera del equipo se lee POR INMUEBLE**, con `GET /captaciones/propiedades-equipo`.
    Deduplica con `DISTINCT ON (id_propiedad)` quedándose con la captación de vigencia más
    lejana (sin fecha va al final; el id desempata). Son las dos únicas consultas **nativas**
    del recurso, porque JPQL no tiene `DISTINCT ON`. El `/resumen` cuenta **inmuebles
    distintos** y trae los distritos disponibles; no acepta `distrito` a propósito.
  - **`comisionPactada` es un PORCENTAJE sobre la renta mensual, no un importe**
    (`ComisionServiceImpl.bruta()` = `renta × pactada / 100`), así que `100.00` es un mes de
    alquiler y `4250.00` serían 42,5 meses. La columna no tiene tope superior. V12 corrige el
    dato sembrado de `CAP-0001`, que estaba puesto como si fuera un importe en soles. Semántica,
    presentación en lenguaje natural y los modelos de comisión que faltan por soportar están en
    `docs/ai/decision-modelos-de-comision.md`.
  - **La prospección de un local se pide con `GET /prospecciones?idLocal=`**, no descargando la
    bandeja para filtrarla en memoria como hacía el Blazor. Ese recurso pagina con
    `pagina`/`tamano` y **no** acepta los alias `page`/`page_size`.
  - El **alta de publicación exige `rentaPublicada` numérica**: la columna es NOT NULL y
    `PublicacionServiceImpl.crear` la escribe tal cual, así que un `null` no da un 400 del
    contrato sino un error de la BD. El SPA la trata como **campo obligatorio del formulario**
    —no la rellena con `0`, que sería un dato falso— y comprueba que sea finita antes de
    enviar. En la **edición** el service solo la pisa si llega con valor, y el `estado` se
    ignora: pausar/publicar/cerrar es `POST …/estado`.
- Diferidas al post-corte a propósito, no son trabajo pendiente: el **almacén S3 real** (hoy
  solo `AlmacenDisco`, y ninguna pantalla nota la diferencia), el **buffer de la subida por
  trozos** (no se arregla: el endpoint se elimina entero cuando muera el Blazor) y el
  **`Descripciones` duplicado** entre `service/soporte` (E4) y `FichaComercialServiceImpl`
  (E3), que no se toca durante la convivencia porque E3 está verificada con 60/60.
