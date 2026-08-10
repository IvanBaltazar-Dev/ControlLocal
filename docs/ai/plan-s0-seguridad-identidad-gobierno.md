# Plan S0 — Seguridad, identidad y gobierno de accesos

> **HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL.**
> Describe el mundo de la migración: v1 sobre GlassFish, SPA Blazor, contrato
> congelado y corte del legado. Ese stack se borró el 2026-08-08 y el contrato se
> descongeló el 2026-08-09. Se conserva porque explica **por qué** las cosas son
> como son, no **qué** hacer ahora.
>
> El orden vigente sale solo de `mapa-ejecucion-brox.md` (dónde estamos) y
> `checklist-captura-moat-e-inteligencia-inmobiliaria.md` (qué falta para cerrar
> la etapa).

**Estado:** **APROBADO (2026-08-04) y EN EJECUCIÓN.** Las decisiones que lo bloqueaban están
tomadas (§10).

> **Ejecutado y verificado a 2026-08-05 — Bloque 3 del plan maestro, "Seguridad de sesiones,
> auditoría y bloqueo de accesos":**
> **§4.7 / §5.2** invalidación de sesiones (V29) y logout con efecto en servidor ·
> **§6.3** auditoría `evento_seguridad` append-only (V30) ·
> **§4.8** bloqueo por **cuenta e IP** sobre PostgreSQL (V30), que **sustituye y retira** el
> `LimitadorIntentos` en memoria.
> Evidencia: `backend-spring/verificacion/e2e-s0-sesiones.ps1` y `e2e-s0-bloqueo.ps1`, más la
> regresión completa de las 13 suites históricas
> (`verificacion/evidencia/2026-08-05-regresion-bloque3.log`).
>
> **Ejecutado y verificado a 2026-08-05 — Bloque 4 del plan maestro, "Contraseñas y
> recuperación":**
> **§4.1** las tres columnas restantes de `credencial_usuario` (V31) ·
> **§4.2** `POST /perfil/contrasena` ·
> **§4.3** `token_acceso` de un solo uso + `POST /auth/recuperacion` y su canje, con el puerto
> `NotificadorIdentidad` y la implementación `NotificadorFueraDeBanda` ·
> **§4.4** invitación y contraseña temporal, **solo ADMIN** ·
> **§4.5** política, historial `credencial_password` y **sesión capada**.
> Evidencia: `verificacion/e2e-s0-contrasenas.ps1` **59/59** y la regresión de las 16 suites
> (**951 comprobaciones, 0 fallos**), más las **dos pantallas Angular** que faltaban.
>
> **Sigue sin implementarse** todo §2, §3 (identidad y gobierno, bloqueados por D-S0-17), **§4.6**
> (migración progresiva del hash, que el propio plan deja para el final por ser lo menos grave) y
> §6.1–§6.2 (MFA y break-glass).

**Base técnica:** `docs/ai/diagnostico-autenticacion-y-gobierno-de-accesos.md` (aceptado).
**Posición en el camino:** S0 se ejecuta **después del BLOQUE 1 (persistencia y backups)** y
**antes** de E5 → almacenamiento definitivo → corte. Orden completo y vinculante:
`plan-maestro-ruta-a-produccion.md` §2.
**Restricción explícita del encargo:** *no se elimina ni se desactiva `admin@controllocal.test`
durante la transición.*

> **Decisiones del 2026-08-04 que este plan ya incorpora** (detalle en §10):
> **D-S0-20** arranque fallido: **SÍ**, con la lista de comprobaciones ampliada (§1.2) ·
> **D-S0-19** MFA obligatorio para gobierno **desde el día uno** (§6.1) ·
> **D-S0-18** invitar/activar/suspender es **solo de `TENANT_ADMIN`** (§4.4) ·
> **D-S0-11** el correo **deja de bloquear**: se implementa el puerto `NotificadorIdentidad` y la
> implementación concreta se elige con la infraestructura productiva (§4.3) ·
> **D-S0-17** propuesta fila por fila en `matriz-d-s0-17-operaciones-broker-admin.md`,
> **pendiente de aprobación** y **bloqueante** para todo §2/§3.
>
> **Novedad de alcance:** este plan gana una sección de **bloqueo por cuenta e IP** (§4.8), que
> antes no existía, y la migración de S0 incorpora los ajustes de **D-27** (autorización de datos
> personales, `decision-autorizacion-datos-personales.md`).

`docs/ai/seguridad-no-leer.md` está vacío: no hay decisiones previas que respetar ni que
contradecir.

Las decisiones nuevas de este bloque se numeran **D-S0-n** (el repositorio va por D-26; el prefijo
por bloque es la convención de F4/E4).

---

## 0. Las cinco restricciones que mandan sobre todo el diseño

Antes de proponer nada, esto es lo que **no** se puede mover mientras GlassFish siga vivo. Todo el
plan está construido alrededor de estas cinco.

| # | Restricción | De dónde sale | Consecuencia de diseño |
|---|---|---|---|
| **R1** | **El token solo admite tres roles**: `TokenService.ROLES = {AGENTE, BROKER, ADMIN}`, validado en los **dos** backends. | `TokenService:31,107` (v2) y su gemelo en v1 | `TENANT_ADMIN`/`PLATFORM_ADMIN` **no pueden viajar en el token** hasta el corte. El rol de gobierno se resuelve **en el servidor**, igual que el tenant (D-20). |
| **R2** | **`idDominio` debe ser > 0** y es `persona_rol.id` del rol operativo. | `TokenService:49,107` | Un administrador que **no sea broker** necesita igualmente un `persona_rol` propio. Por eso `ADMIN` tiene que ser un **rol de verdad en `persona_rol`**, no solo una fila de membresía. |
| **R3** | **`LoginResponse` está congelado** (byte-compatible con la v1). | Regla del contrato congelado | El rol efectivo y las capacidades se exponen por un **endpoint aditivo nuevo**, no añadiendo campos al login. |
| **R4** | **El SSO entre backends depende del secreto compartido.** | `TokenService.SECRETO_DEV` en ambos | Rotar el secreto **rota los dos backends a la vez** o rompe la convivencia. Es una operación coordinada, no un cambio de variable. |
| **R5** | **V3 ya está aplicada** y Flyway no permite editar una migración aplicada. | `CLAUDE.md`, historial de Flyway | Las credenciales del seed se invalidan con una **migración nueva condicionada al entorno**, nunca editando V3. |

**Tensión que hay que resolver explícitamente** (y que este plan resuelve en §1.4): el encargo pide
*invalidar las credenciales conocidas del seed* y a la vez *no desactivar al administrador actual*.
`admin@controllocal.test` **es** una de esas credenciales. La salida: se invalida su **contraseña**,
nunca su **cuenta** — la cuenta sigue activa, con cambio obligatorio en el próximo ingreso y una
clave temporal entregada fuera de banda. Invalidar la clave no es desactivar al usuario.

---

## 1. S0.1 — Contención inmediata

**Objetivo:** que un despliegue mal configurado **no arranque**, en vez de arrancar inseguro y en
silencio. Sin cambios de esquema salvo la neutralización del seed. Es la tanda más barata y la que
más riesgo retira.

### 1.1 Eliminar el fallback silencioso del secreto JWT

**Hoy:** `TokenService` cae a `SECRETO_DEV` cuando `API_TOKEN_SECRET` falta o mide < 32 caracteres,
sin avisar (H-01).

**Propuesta (D-S0-1):** el fallback **sobrevive solo en el perfil `dev`**, y deja de ser silencioso.

```
perfil dev   → sin secreto configurado: usa el fallback y emite un WARN explícito en cada arranque
perfil prod  → sin secreto, o < 32 caracteres, o IGUAL al fallback conocido → el contexto NO arranca
```

- Se introducen perfiles Spring (`dev` por defecto, `prod` explícito). Hoy no hay ninguno.
- La comparación contra el fallback se hace por **hash del secreto**, no por el literal, para no
  volver a escribirlo en una segunda clase.
- **`backend-java` recibe el mismo tratamiento**: si solo se blinda la v2, un atacante firma contra
  la v1 y el token vale en las dos (R4).

### 1.2 Arranque fallido ante configuración de seguridad inválida

**Propuesta (D-S0-2):** un `ValidadorDeConfiguracionDeSeguridad` (`ApplicationListener` sobre el
contexto ya refrescado) que en perfil `prod` **lanza y detiene el arranque** si:

| Comprobación | Motivo |
|---|---|
| secreto ausente, < 32 caracteres o igual al de desarrollo | H-01 |
| `CORS_ORIGENES` contiene `localhost` o `*` | superficie de desarrollo abierta |
| Swagger / `/v3/api-docs` accesibles sin token | H-13 |
| contraseña de BD igual al defecto (`controllocal`) | H-17 |
| existe alguna credencial con **hash del seed conocido** | H-03 — es la red que hace verificable a §1.4 |
| existe alguna organización **sin administrador activo** | invariante de §2.6 |
| **el directorio del almacén no está configurado, no existe o no es escribible** | 2026-08-04: sin volumen persistente, los binarios se pierden al recrear el contenedor. Un `prod` que arranca sin almacén persistente **pierde documentos en silencio** |
| **el perfil activo es `prod` pero hay valores de desarrollo** (URL de BD apuntando a `localhost`, usuario `controllocal`, `ddl-auto` distinto de `validate`) | Evita que un `prod` se levante con la configuración de la máquina del desarrollador |

El mensaje de error **nombra la variable que falta**, no "configuración inválida". Un arranque que
falla y no dice qué falta se resuelve desactivando la comprobación.

### 1.3 Rotación del secreto y manejo por entorno

**Propuesta (D-S0-3):**

1. **Secreto por entorno**, nunca en el repositorio: variable de entorno inyectada por el
   orquestador; en local, `.env` (ya gitignorado). Longitud mínima 32, generado con `openssl rand`.
2. **Rotación coordinada** (R4): mientras convivan los dos backends, la rotación es una ventana
   sincronizada — se configura el mismo secreto nuevo en GlassFish y en Spring, y se reinician los
   dos. **Todas las sesiones vivas caen**: es aceptable con 30 min de vida, y se comunica.
3. **Rotación sin corte (opcional, D-S0-3b):** aceptar **dos** secretos en validación (actual +
   anterior) durante una ventana, emitiendo solo con el nuevo. Es ~15 líneas en `validar()` y
   convierte la rotación en una operación rutinaria. Se propone adoptarlo: sin él, rotar duele y
   por eso no se rota.
4. Tras el corte de GlassFish, el secreto queda **solo** en el backend Spring y la rotación deja de
   ser coordinada.

### 1.4 Invalidar las credenciales del seed sin tocar V3

**Propuesta (D-S0-4):** migración nueva **`V900__neutraliza_credenciales_semilla.sql`**, ubicada en
una **location de Flyway exclusiva del perfil productivo**:

```
db/migration           → esquema (todos los entornos)          V28, V29, V30…
db/migration-dev       → seed de desarrollo y E2E (dev/test)   R__seed_dev   ← §1.5
db/migration-prod      → endurecimiento productivo (solo prod) V900+         ← rango reservado
```

> **Por qué V900 y no V28**: Flyway **funde todas las locations en una sola línea de versiones**.
> Si `migration-prod` usara V28, la siguiente migración de esquema no podría llamarse V28 y cada
> entorno tendría un historial distinto. El rango alto reservado evita la colisión para siempre.

V900 hace **una sola cosa**: reemplaza el `contrasena_hash` de toda credencial que coincida con los
tres hashes del seed por un **centinela no verificable**:

```sql
UPDATE credencial_usuario
   SET contrasena_hash = 'invalidado$' || encode(gen_random_bytes(24), 'base64'),
       debe_cambiar_contrasena = TRUE           -- columna nueva de §4
 WHERE contrasena_hash IN (<los tres hashes del seed, literales>);
```

Por qué funciona sin borrar nada: `PasswordHasher.verificar` **exige el prefijo `pbkdf2$`** y
devuelve `false` ante cualquier otro formato. Un hash que empieza por `invalidado$` **no valida
jamás** y no hay forma de derivarlo. La cuenta **sigue activa** (`estado_administrativo = 'A'`),
su identidad y su historial quedan intactos.

**El administrador queda cubierto por la excepción del encargo:** no se desactiva. Se le entrega una
**contraseña temporal fuera de banda** (variable de entorno del arranque productivo,
`ADMIN_BOOTSTRAP_PASSWORD`, consumida una sola vez) y entra con **cambio obligatorio** (§4.4). Si esa
variable no está y hay credenciales neutralizadas, el arranque falla (§1.2): nadie se queda sin
acceso por descuido.

### 1.5 Separar datos de desarrollo/E2E de las migraciones productivas

**Propuesta (D-S0-5):**

- **V3 no se toca** (R5): queda como historia aplicada.
- El seed **futuro** vive en `db/migration-dev`, activo solo con el perfil `dev`. Una base
  productiva nueva **no nace con las 21 cuentas**.
- Producción arranca con un **bootstrap de primer administrador**: si la organización no tiene
  ninguno, se crea desde variables de entorno (`ADMIN_BOOTSTRAP_USUARIO`, `…_PASSWORD`,
  `…_CORREO`), con cambio obligatorio y **auditado como evento de bootstrap**. Es la única alta de
  administrador que no exige otro administrador — y por eso es la que hay que vigilar.
- Los **13 scripts de `verificacion/`** siguen usando las claves del seed: corren contra bases
  efímeras en perfil `dev`, donde V900 no se aplica. **No hay que reescribirlos.**

> **Riesgo asumido:** desde S0.1 las bases de desarrollo y las productivas dejan de ser idénticas.
> Se mitiga con el gate de §1.2 (una base productiva con hashes de seed no arranca) y con que el
> esquema —lo que de verdad tiene que ser idéntico— sigue en una sola location.

---

## 2. S0.2 — Nuevo modelo de identidad y gobierno — ✅ **HECHO (2026-08-05)**

> **Ejecutado en el Bloque 5** con la matriz D-S0-17 aprobada tal cual. Lo que sigue describe el
> diseño; lo que se construyó coincide, con **tres correcciones** que solo se ven tocando el
> esquema real:
>
> 1. **El `CHECK` de `usuario_organizacion.rol` que §2.4 pedía ampliar no existía**: V6 solo
>    restringía `estado`. La columna nació como texto libre, así que V33 la **crea** con el
>    vocabulario final en vez de relajarla.
> 2. **El backfill de V6 nunca funcionó.** Unía el rol `USUARIO_INTERNO` con el rol `BROKER` —dos
>    filas que por construcción no pueden compartir id—, así que las 21 cuentas quedaron como
>    `AGENTE`. V33 no "actualiza la membresía del administrador": **reconstruye las tres bandas**.
> 3. **`uq_broker_admin_unico` NO se retira todavía** (§2.5). Ya no limita nada real: un
>    `TENANT_ADMIN` se define por su membresía y no necesita el booleano. Lo único que ese índice
>    restringe es cuántos brokers cargan la marca **que lee GlassFish**, así que muere con la
>    columna, en V36.

### 2.1 Las cuatro capas que hoy están confundidas

| Capa | Qué responde | Hoy | Propuesta |
|---|---|---|---|
| **Identidad** | quién es esta persona | `persona` | igual (no se toca) |
| **Cuenta de acceso** | con qué entra al sistema | `credencial_usuario` (rol `USUARIO_INTERNO`) | igual, + control de contraseña y sesiones (§4, §5) |
| **Membresía organizacional** | a qué tenant pertenece y **con qué banda** | `usuario_organizacion` **existe y está poblada pero el código NO la usa** (H-14) | **pasa a ser la fuente de verdad** |
| **Rol operativo de negocio** | qué hace en el proceso comercial | `detalle_broker` / `detalle_agente` | igual, **separado del gobierno** |

**El error estructural de hoy** es que la cuarta capa decide la tercera: ser administrador es un
booleano del detalle de broker. Todo S0.2 consiste en cortar esa dependencia.

### 2.2 `ADMIN` como rol real (D-S0-6) — ✅ V32

Dos cambios acoplados, y **los dos hacen falta** por R2:

1. **`persona_rol` admite el tipo `ADMIN`**: se amplía el `CHECK` de `V1` (`PROPIETARIO`, `CLIENTE`,
   `USUARIO_INTERNO`, `BROKER`, `AGENTE` + **`ADMIN`**) y el enum `TipoRol`.
   Ese `persona_rol.id` es el que viaja como **`idDominio`** en el token: satisface R2 sin tocar el
   formato, y un administrador **deja de necesitar un `detalle_broker`** para existir.
2. **`usuario_organizacion.rol` pasa a ser la banda autoritativa**, con el vocabulario ampliado:

| Valor | Qué es | Alcance |
|---|---|---|
| `AGENTE` | opera lo suyo | su rol operativo |
| `BROKER` | supervisa su equipo | agentes supervisados hoy |
| `TENANT_ADMIN` | **gobierna una organización** | todo el tenant, **sin semántica comercial** |
| `PLATFORM_ADMIN` | **gobierna la plataforma** | varios tenants, **por concesión explícita y temporal** (§6.4) |

`ADMIN` (el valor actual de la tabla) se mantiene como **alias de compatibilidad** hasta que termine
la migración de datos, y se retira en la fase final (§3.4).

### 2.3 `TENANT_ADMIN` no hereda la semántica de `BROKER` (D-S0-7) — ✅ hecho

Hoy el administrador **es** un broker y eso se cuela por tres sitios distintos:

| Fuga actual | Cómo queda |
|---|---|
| `Actor.tipoRolOperativo()` traduce `ADMIN → "BROKER"` en auditoría (H-09) | audita **`TENANT_ADMIN`**; `historial_estado.tipo_rol_actor` amplía su vocabulario |
| `BrokerServiceImpl.validarAdministrador` exige un `detalle_broker` con el flag | pasa a comprobar **membresía `TENANT_ADMIN` activa**; deja de leer `detalle_broker` |
| Un `TENANT_ADMIN` podría caer en consultas de supervisión por tener rol de broker | un `TENANT_ADMIN` **no tiene** `detalle_broker` salvo que además sea broker de verdad, y entonces son **dos membresías/roles distintos**, explícitos |

**Regla que queda escrita:** *gobernar no es operar*. Un `TENANT_ADMIN` no supervisa agentes, no
capta, no liquida comisiones. Si una persona hace las dos cosas, tiene **dos roles** y se ve en la
auditoría cuál usó.

**Consecuencia visible y deseada:** hoy el ADMIN entra a 78 operaciones (60 sin gate + 18 de
BROKER+ADMIN) por herencia. Con la separación, cada una de esas 18 filas **se decide a propósito**:
o es gobierno (`TENANT_ADMIN` entra) o es operación comercial (no entra). **Esa revisión fila por
fila es parte del trabajo de S0.2** y su resultado es una versión nueva de la matriz.

### 2.4 `usuario_organizacion` como fuente de membresía (D-S0-8) — ✅ hecho

La tabla ya existe, está poblada correctamente por V6 y su diseño anticipa exactamente esto (D-22:
`id_usuario` apunta hoy al `persona_rol` del usuario interno y mañana a la cuenta global). Lo que
falta es **usarla**:

- Repositorio `UsuarioOrganizacionRepository` (hoy no existe).
- El **login** deja de derivar la banda de `detalle_broker` y la lee de la membresía activa.
- `SesionDeRequest` gana el **rol efectivo** (además del rol del token, que sigue congelado).
- Ampliaciones de esquema necesarias: `CHECK` del rol con los valores nuevos, e índice por
  `(organizacion_id, rol)` para el invariante de §2.6.

### 2.5 Varios administradores por organización

Se retira `uq_broker_admin_unico` (§3.4) y el límite pasa a ser **ninguno**: una organización puede
tener los `TENANT_ADMIN` que necesite. Es un requisito de continuidad operativa: con uno solo, un
olvido de contraseña es una caída de gobierno (H-04).

### 2.6 Una organización nunca sin administrador (D-S0-9) — ✅ V34

**"Al menos uno" no se puede expresar con un índice único.** Se necesitan las dos capas:

1. **Guarda de aplicación**: bajar, desactivar o degradar a un `TENANT_ADMIN` cuenta primero cuántos
   quedarían activos; si el resultado es 0 → `ReglaNegocioException`
   («*Una organización no puede quedarse sin administrador*»), con test dedicado.
2. **Red en la base**: `TRIGGER … CONSTRAINT … DEFERRABLE INITIALLY DEFERRED` sobre
   `usuario_organizacion` que al final de la transacción cuente los `TENANT_ADMIN` activos de esa
   organización y falle si son 0. Deferred a propósito: permite un intercambio (alta del nuevo, baja
   del viejo) dentro de la misma transacción.

La guarda de aplicación da el **mensaje**; el trigger da la **garantía** aunque alguien escriba por
SQL. Ninguna de las dos sobra.

---

## 3. S0.3 — Migración (esquema y datos)

Migraciones nuevas a partir de **V28** (el máximo aplicado es V27). Orden pensado para que
**cada paso deje el sistema funcionando**: expandir → convivir → contraer, el mismo patrón de
V15–V20.

### Reglas de numeración (2026-08-04)

1. **No se edita ninguna migración aplicada.** V1–V27 son historia; todo cambio es una migración
   nueva. Flyway valida el checksum y un `repair` para tapar una edición sería falsear el historial.
2. **Las locations no pueden compartir número.** Flyway funde `db/migration` y `db/migration-prod`
   en una sola línea de versiones: dos migraciones `V28` en locations distintas **rompen el
   arranque**. Por eso las migraciones exclusivas de producción usan un **rango reservado
   (`V900+`)** que nunca colisionará con la línea principal.
3. **Cada migración deja el sistema entero**: expandir → convivir → contraer, el patrón de V15–V20.

| Migración | Location | Qué hace | Reversible |
|---|---|---|---|
| **V28** | `migration` | **D-27 autorización de datos**: alta de la finalidad `GESTION_COMERCIAL`, desactivación (`estado='I'`) de las otras cuatro de V6, y columnas `registrada_por` y `motivo_revocacion` en `autorizacion_tratamiento_evento`. Ver `decision-autorizacion-datos-personales.md` | sí |
| **V29** ✅ | `migration` | **Invalidación de sesiones** (§4.7): **solo** `credencial_usuario.sesiones_invalidas_desde`. Aplicada el 2026-08-05 | sí |
| **V30** ✅ | `migration` | **Auditoría y bloqueo**: `evento_seguridad` (append-only) e **`intento_acceso`** (§4.8, bloqueo por cuenta e IP). Aplicada el 2026-08-05 | sí |
| **V31** | `migration` | **Contraseñas** (§4.1–4.5): columnas `debe_cambiar_contrasena`, `password_actualizada_en` y `algoritmo_hash` en `credencial_usuario`, más `token_acceso` (§4.3) e historial `credencial_password` (§4.5) | sí |
| **V32** ✅ | `migration` | **Expandir identidad**: `persona_rol.CHECK` admite `ADMIN` e índice `(organizacion_id, rol)`. *El `CHECK` de `usuario_organizacion.rol` no existía y lo crea V33.* Aplicada el 2026-08-05 | sí |
| **V33** ✅ | `migration` | **Backfill de gobierno**: crea el `persona_rol` de tipo `ADMIN` de la **misma persona** y **reconstruye las tres bandas** —el backfill de V6 estaba roto y las dejó todas en `AGENTE`—. Fija el vocabulario con un `CHECK` nuevo. **No se crea ninguna persona nueva.** Aplicada el 2026-08-05 | sí |
| **V34** ✅ | `migration` | Invariante "≥ 1 administrador" (trigger deferred, §2.6), **después** del backfill, cuando ya se cumple. `concesion_acceso_tenant` **se separa**: pertenece a `PLATFORM_ADMIN`, que queda fuera de esta tanda (D-30). Aplicada el 2026-08-05 | sí |
| **V35** ✅ | `migration` | **Actor de la reasignación**: `reasignacion_captacion.id_rol_broker` pasa a opcional y entran `id_persona_actor` y `tipo_rol_actor`. Sin esto la fila 6 de D-S0-17 era inaplicable — un administrador sin `detalle_broker` no cabía en el evento. Aplicada el 2026-08-05 | sí |
| **V37** | `migration` | **MFA**: `factor_autenticacion` con secreto cifrado en reposo y códigos de recuperación hasheados (§6.1) | sí |
| **V36** | `migration` | **Contraer**: elimina `uq_broker_admin_unico` y la columna `detalle_broker.es_administrador`. **Solo cuando ningún código la lea** → **diferida al corte** | no |
| **V900** | `migration-prod` | Neutraliza los hashes del seed (§1.4). **Solo perfil `prod`** | no (a propósito) |
| **R\_\_seed_dev** | `migration-dev` | Seed de desarrollo y E2E. **Solo perfil `dev`/`test`**; una base productiva no nace con las 21 cuentas | n/a |

### 3.1 `admin@controllocal.test` conserva identidad e historial (D-S0-10)

Es el punto que el encargo protege, así que se detalla:

- **La misma `persona`** (`id_persona` intacto). Todo `historial_estado.id_actor` y todo evento de
  `reasignacion_agente_broker` que lo nombran **siguen apuntando a la misma fila**.
- **La misma `credencial_usuario`** y el mismo `nombre_usuario`: no cambia cómo entra.
- **Gana** un `persona_rol` de tipo `ADMIN` (su nuevo `idDominio`) y su membresía pasa de `ADMIN` a
  `TENANT_ADMIN`.
- **Conserva su `persona_rol` de BROKER y su `detalle_broker`** durante toda la transición. No se
  borra nada: es lo que permite volver atrás y lo que mantiene coherente su historia como broker.
- Al final (V36) desaparece solo el **booleano**, no el rol de broker ni la persona.

**Qué cambia para él en la práctica:** su `idDominio` pasa a ser el del rol `ADMIN`, así que **su
token actual deja de servir** tras la migración → tiene que volver a entrar. Se planifica en ventana
y se avisa. Es un efecto de una sola vez.

### 3.2 Sustituir la derivación desde `es_administrador` — ✅ hecho

Un único punto: `AutenticacionServiceImpl.resolverIdentidad` (líneas 57-80). Pasa de

> *broker vigente + `detalle.esAdministrador()` → "ADMIN"*

a

> *membresía activa en la organización → banda efectiva; y el `persona_rol` correspondiente a esa
> banda da el `idDominio`.*

**Durante la convivencia el token sigue diciendo `ADMIN`** (R1). La banda real viaja en
`SesionDeRequest` y la consumen `Actor`, `Alcances` y los `@PreAuthorize`.

### 3.3 Código acoplado (no es solo SQL) — ✅ hecho

> **Lo que la tabla de abajo no anticipaba, y apareció al ejecutarlo:** el `idRolOperativo` de un
> `TENANT_ADMIN` deja de ser un rol de broker, así que **todo código que lo usara para buscar en
> `detalle_broker` se rompe**. Eran tres sitios y ninguno es un gate: `AgenteServiceImpl`
> (validaba el broker de la sesión en las cinco operaciones de `/agentes`),
> `BrokerServiceImpl.validarAdministrador` (exigía un `detalle_broker` con el flag, que era la
> fuga misma) y `CaptacionServiceImpl.reasignar` (grababa el autor del evento). El compilador no
> los señala: fallan en ejecución con *"Broker no encontrado"*.

| Pieza | Cambio |
|---|---|
| `AutenticacionServiceImpl` | resuelve por membresía (§3.2) |
| `SesionDeRequest` / `SesionActual` | transportan el **rol efectivo** además del rol del token |
| `Actor` | `rolGobierno()` nuevo; `tipoRolOperativo()` deja de mentir (§2.3) |
| `Alcances` | `global = true` pasa a depender de `TENANT_ADMIN`; entra la rama de `PLATFORM_ADMIN` con concesión vigente (§6.4) |
| `@PreAuthorize` | `hasRole('ADMIN')` → `hasRole('TENANT_ADMIN')` en las 6 operaciones de gobierno; el filtro publica la authority efectiva |
| `BrokerServiceImpl` | `validarAdministrador` mira membresía, no `detalle_broker` |
| **`matriz-operacion-rol.md`** | vocabulario nuevo + **revisión fila por fila de las 18 de BROKER+ADMIN** (§2.3). `MatrizOperacionRolTest` rompe el build si no se actualiza: es el gate, no un recordatorio |
| **Frontend** | `RolSesion` gana los valores nuevos; `ETIQUETA_ROL`; `acceso.ts`; y el SPA lee el rol efectivo del endpoint aditivo de R3 |
| Auditoría | `historial_estado.tipo_rol_actor` admite `TENANT_ADMIN`/`PLATFORM_ADMIN` |

### 3.4 Compatibilidad con el legado

| Frente | Durante la convivencia | Después del corte |
|---|---|---|
| **Token** | formato intacto; `rol` sigue siendo `ADMIN`; `idDominio` > 0 garantizado por el rol `ADMIN` real | roles reales en el token; `LoginResponse` puede crecer |
| **Blazor / v1** | no se entera: el flujo `es_administrador` sigue existiendo hasta V36, y GlassFish no lee `usuario_organizacion` | se retira |
| **`LoginResponse`** | intacto (R3). El rol efectivo se pide con **`GET /sesion`** (aditivo, con su fila en la matriz) | se puede fusionar en el login |
| **Secreto** | rotación coordinada (§1.3) | solo Spring |
| **V36** | **no se ejecuta** mientras GlassFish esté vivo: la v1 lee `es_administrador` | se ejecuta en el corte |

> **Regla de corte de S0**: `V36` es la única migración de este plan que **queda bloqueada** hasta
> que muera el legado. Todo lo demás es aplicable durante la convivencia.

---

## 4. S0.4 — Contraseñas y recuperación

Hoy no existe **ninguna** de estas operaciones (H-02, H-08).

### 4.1 Columnas nuevas en `credencial_usuario`

| Columna | Para qué | Estado |
|---|---|---|
| `sesiones_invalidas_desde TIMESTAMPTZ` | **revocación sin tocar el token** (§4.7) | ✅ **V29 aplicada** (2026-08-05) |
| `debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE` | contraseña temporal (§4.4) | 📋 su bloque |
| `password_actualizada_en TIMESTAMPTZ` | caducidad y auditoría | 📋 su bloque |
| `algoritmo_hash VARCHAR(20) NOT NULL DEFAULT 'pbkdf2'` | migración progresiva del hash (§4.6) | 📋 su bloque |

> **V29 quedó deliberadamente estrecha** (2026-08-05): trae **solo** la columna de invalidación.
> Las tres de contraseñas entran con el bloque que las usa, y la expansión de identidad
> (`persona_rol` con `ADMIN`, `usuario_organizacion`) con el de gobierno — meterlas en V29 habría
> atado el bloque de sesiones a **D-S0-17**, que no está aprobada.

### 4.2 Cambio de contraseña autenticado — ✅ **HECHO (2026-08-05)**

`POST /perfil/contrasena` — **aditivo**, con su fila en la matriz, para cualquier sesión.
Exige **contraseña actual** + nueva (evita que una sesión robada cambie la clave), aplica la
política (§4.5), guarda el hash nuevo, marca `password_actualizada_en`, **invalida las demás
sesiones** (§4.7) y emite evento de auditoría.

### 4.3 Recuperación con token de un solo uso — ✅ **HECHO (2026-08-05)**

Tabla **`token_acceso`** (V31): `id`, `id_credencial`, `tipo` (`RECUPERACION` | `INVITACION`),
`hash_token`, `expira_en`, `usado_en`, `creado_por`, `motivo`, `organizacion_id`.

Reglas, todas verificables:

- Se guarda **el hash del token**, nunca el token (misma lógica que una contraseña).
- **Un solo uso**: `usado_en` se sella dentro de la misma transacción que cambia la clave.
- **Vigencia corta** (30 min propuestos) y **un token activo por credencial**: emitir uno nuevo
  invalida el anterior.
- `POST /auth/recuperacion` responde **siempre 202**, exista o no el usuario: no revela el padrón.
- **Un administrador nunca ve, fija ni recupera la contraseña de otra persona.** El token es el
  único camino, y el titular define su clave al canjearlo.

**D-S0-11 resuelta (2026-08-04): el correo deja de bloquear S0.** Se implementa un **puerto
desacoplado**, no un proveedor:

```java
public interface NotificadorIdentidad {
    void enviarRecuperacion(DestinoNotificacion destino, TokenEmitido token);
    void enviarInvitacion(DestinoNotificacion destino, TokenEmitido token);
}
```

- **Implementación de S0**: `NotificadorFueraDeBanda` — **no envía nada**; devuelve el token al
  administrador **una sola vez** para que lo entregue por su cuenta, y deja el evento de auditoría.
  Cubre el 100 % de la operación normal de una corredora, donde administrador y agente se conocen.
- **Implementación futura**: SMTP contra un **relay autenticado** (institucional o de terceros),
  decidida **cuando exista infraestructura productiva y dominio propio**. Autohospedar correo queda
  descartado por entregabilidad (ver `informe-tecnologias-dependencias-y-alcance-e5.md` §8.3).
- El diseño de `token_acceso` y del canje **no cambia** con la implementación: lo único que se
  difiere es el **transporte**.

### 4.4 Restablecimiento administrativo por invitación temporal — ✅ **HECHO (2026-08-05)**

`POST /agentes/{id}/invitacion` y `POST /brokers/{id}/invitacion` (aditivas) generan un token
`INVITACION`. **El administrador nunca ve ni fija una contraseña ajena**: el usuario la define al
canjear el token. Eso evita el patrón "el jefe conoce la clave del empleado", que es lo que hoy hace
inevitable el seed compartido.

> **D-S0-18 resuelta (2026-08-04): solo `TENANT_ADMIN`.** Un broker ordinario **no invita, no activa
> y no suspende** usuarios de la organización, ni siquiera de su propio equipo. Invitar, activar,
> suspender y administrar membresías son **gobierno del tenant**. Delegar una invitación acotada a
> brokers queda como evolución posible, **no se implementa ahora**. Esto es coherente con las filas
> 17 y 18 de `matriz-d-s0-17-operaciones-broker-admin.md`, donde el alta y la edición de agentes
> también pasan a `TENANT_ADMIN`.

Alternativa para el arranque sin correo: **contraseña temporal** de un solo uso, mostrada una vez al
administrador, con `debe_cambiar_contrasena = TRUE`.

### 4.5 Política de contraseñas y cambio obligatorio — ✅ **HECHO (2026-08-05)**

- Mínimo **12 caracteres**, sin tope bajo; se rechazan las de una lista corta de claves comunes y
  las que contengan el nombre de usuario. **No** se exige rotación periódica (empuja a `Clave2026!`,
  que es exactamente el patrón del seed actual).
- Con `debe_cambiar_contrasena = TRUE`, **la sesión existe pero está capada**: el filtro deja pasar
  solo `GET /sesion` y `POST /perfil/contrasena`, y responde **403 con un código distinguible** en
  todo lo demás. El SPA lo traduce en una pantalla de cambio obligatorio.
- `credencial_password` (V31) guarda los **últimos N hashes** para impedir reutilización inmediata.

### 4.6 Migración progresiva del hash — ⬜ **pendiente a propósito**

`algoritmo_hash` permite convivir dos algoritmos. Estrategia **al validar**: si la credencial usa el
algoritmo viejo y la contraseña es correcta, se **re-hashea con el nuevo en el mismo login** y se
actualiza la columna. La migración ocurre sola, sin pedirle nada al usuario y sin ventana.

Candidato de destino: **Argon2id** (o PBKDF2 con más iteraciones si se prefiere no sumar
dependencia). PBKDF2-SHA256 con 100 000 iteraciones **no es una urgencia** — es lo menos grave del
diagnóstico —, así que esto puede ir al final de S0 sin bloquear nada.

### 4.7 Invalidación de sesiones tras cambios sensibles (D-S0-12) — ✅ **HECHO (2026-08-05)**

**El hallazgo clave de este plan**: se puede revocar **sin tocar el formato del token**.

El JWT ya lleva `iat` (instante de emisión) en la carga. Basta con que
`FiltroAutenticacionJwt` compare:

```
si  token.iat  <  credencial.sesiones_invalidas_desde   →   401 "Token invalido o expirado."
```

Con eso, escribir `sesiones_invalidas_desde = now()` **mata todas las sesiones vivas de esa cuenta al
instante**, y cierra H-05 durante la convivencia. Se dispara en: cambio de contraseña,
restablecimiento, desactivación de la cuenta, cambio de rol o de membresía, y logout real (§5.2).

**Coste:** una lectura por request. El plan admitía mitigarlo con una caché en memoria de
`(idCredencial → sesiones_invalidas_desde)` y TTL corto (30–60 s).

> **Ejecutado SIN caché (2026-08-05), y es una decisión, no un olvido.** Un TTL de 30–60 s abre una
> ventana en la que una sesión ya revocada sigue viva, que es exactamente el fallo que la pieza
> viene a cerrar. La consulta es una proyección de **una columna** por clave primaria; si algún día
> la sonda de transporte la señala, la caché es la palanca — no el punto de partida.
>
> **Borde conocido y aceptado:** `iat` tiene precisión de **segundo**, así que un login dentro del
> mismo segundo que un logout nace invalidado. Falla del lado seguro (pide entrar otra vez) y con
> tokens de 30 min no tiene consecuencia práctica. El E2E lo documenta esperando 1 s.

`TokenService.Sesion` expone `emitidoEn` — cambio interno, **no** del contrato.

### 4.8 Bloqueo por intentos fallidos — por **cuenta** y por **IP** (D-S0-21) — ✅ **HECHO (2026-08-05)**

**Sección nueva (2026-08-04).** El plan original no la tenía: daba por bueno el `LimitadorIntentos`
existente, y no alcanza.

> **Lo que se ejecutó:** `intento_acceso` (V30) + `BloqueoAccesos` con las cinco dimensiones,
> `IpDelCliente` con lista blanca de proxies, y el **`LimitadorIntentos` en memoria retirado del
> árbol** — no conviven dos limitadores. Umbrales configurables por entorno
> (`LOGIN_MAX_FALLOS_CUENTA` / `LOGIN_MAX_FALLOS_IP`), que es lo que permite a la suite `s0-bloqueo`
> bajarlos para provocar el bloqueo sin que las otras 14 se bloqueen a sí mismas.
>
> **Lo que NO entró, y es correcto que no entre aquí:** el desbloqueo explícito por `TENANT_ADMIN`
> (necesita el rol, que está en el bloque de gobierno) y el desbloqueo por canje de recuperación
> (necesita el token de un solo uso, que está en el bloque de contraseñas). Hoy el desbloqueo es
> **por caducidad de la ventana**, y los eventos `CUENTA_DESBLOQUEADA` ya están admitidos por el
> `CHECK` para no exigir otra migración cuando lleguen.
>
> **Alcance del bloqueo:** hoy cubre `/auth/login`. El canje de recuperación y el segundo factor
> —igual de atacables, como dice la tabla de abajo— se enganchan a `BloqueoAccesos` en sus
> respectivos bloques; la pieza ya está y no hay que rediseñarla.

**Lo que hay hoy y por qué no basta** (H-07):

| Límite actual | Problema |
|---|---|
| 10 intentos/min **por IP** | Un atacante con 50 IPs prueba 500/min contra **una sola cuenta** |
| Contador **en memoria del proceso** | Con N instancias el límite efectivo es **10 × N**; y un reinicio lo borra |
| Lee `request.getRemoteAddr()` | **Detrás de un proxy todos comparten cupo**: una sola IP para todo el tráfico. La Fase 5 introduce NGINX, así que esto pasa de latente a real |
| Solo cubre `/auth/login` | El futuro canje de recuperación y el segundo factor son igual de atacables |

**Diseño propuesto — cinco dimensiones:**

| Dimensión | Regla |
|---|---|
| **Cuenta** | Contador por `nombre_usuario` normalizado, **exista o no la cuenta** (si solo contaran las existentes, el propio bloqueo revelaría el padrón) |
| **IP** | Contador por IP **real**, leída de `X-Forwarded-For` **solo si viene de un proxy de confianza declarado**; si no, `getRemoteAddr()`. Confiar en la cabecera sin lista blanca es peor que no leerla |
| **Ventana temporal** | Ventana deslizante de 15 min; el contador de la cuenta se limpia con un **login correcto** |
| **Progresividad** | 5 fallos → espera 1 min · 10 → 5 min · 15 → 15 min · 20 → **bloqueo administrativo** que exige desbloqueo explícito o recuperación. Sin escalado, o molesta al usuario legítimo o no frena al atacante |
| **Desbloqueo seguro** | Por caducidad de la ventana, por canje de recuperación válido, o por acción de `TENANT_ADMIN` **auditada**. Nunca automático tras un cambio de IP |

**Dónde vive el contador: PostgreSQL, no memoria y no Redis.**

```
intento_acceso (id, organizacion_id NULL, clave_tipo, clave_valor_hash,
                ocurrido_en, exito, ip, agente_usuario)
```

- `clave_tipo` ∈ `{CUENTA, IP}`; `clave_valor_hash` guarda el **hash** del identificador, no el
  usuario en claro — la tabla no debe convertirse en un padrón de nombres de usuario probados.
- Con **una sola instancia**, Postgres es fuente compartida suficiente y **no añade infraestructura**.
  El encargo lo fija: *"No introducir Redis sin necesidad demostrada."*
- Coste: dos consultas indexadas por intento de login. Se mide con la sonda de transporte antes y
  después; si algún día molesta, la palanca es una caché local **de solo lectura**, nunca el
  contador.
- Purga por retención (30 días) en la misma tarea que recicla la auditoría.

**Reglas de respuesta, que son parte del diseño y no un detalle:**

- **El mensaje no revela si la cuenta existe.** Credencial inválida, cuenta inexistente y cuenta
  bloqueada responden con el mismo cuerpo. Hoy el 401 y el 429 ya están congelados por contrato
  (`"Credenciales invalidas."` / el mensaje de rate limit), así que **esto no cambia el cable**:
  cambia cuándo se emite cada uno.
- **Un bloqueo por cuenta responde 429**, igual que el de IP. Un código distinto sería un oráculo.
- Cada decisión emite su evento (`LOGIN_FALLIDO`, `LOGIN_BLOQUEADO_429`, `CUENTA_BLOQUEADA`,
  `CUENTA_DESBLOQUEADA`) en `evento_seguridad` (§6.3).

**Interacción con los E2E:** los 13 scripts de `verificacion/` hacen 3–4 logins cada uno y ya hay una
regla operativa de esperar un minuto entre corridas. Con bloqueo **por cuenta**, dos corridas
seguidas del mismo script pueden bloquear al usuario del fixture. **Mitigación obligatoria**: en
perfil `test` el umbral por cuenta es configurable y los scripts usan usuarios de fixture propios,
nunca la cuenta del administrador.

> **Cómo quedó resuelto (2026-08-05).** La mitigación real es más simple que la prevista, y conviene
> saber por qué: **el contador solo cuenta fallos**, así que los logins correctos que encadenan las
> 15 suites **no consumen cupo**. Los umbrales altos que `Invoke-E2E.ps1` fija por defecto
> (`CUENTA=100`, `IP=200`) son una **segunda red**, no la razón por la que las suites pasan. La
> única que los baja —a 3 y 50— es `s0-bloqueo`, porque necesita provocar el bloqueo. Y como cada
> suite corre en su propio entorno efímero, el contador arranca de cero en cada una: la regla
> operativa de "esperar un minuto entre corridas" **deja de existir**.

---

## 5. S0.5 — Sesiones

### 5.1 Estrategia temporal (mientras conviva GlassFish)

No se cambia el mecanismo: sería romper el SSO (R1, R4). Se **endurece lo que hay**:

| Medida | Efecto |
|---|---|
| Revocación por `sesiones_invalidas_desde` (§4.7) | logout real y baja inmediata **sin tocar el token** |
| Logout con efecto en servidor (§5.2) | deja de ser solo `localStorage.removeItem` |
| Rotación de secreto con dos claves (§1.3b) | rotar deja de ser un corte |
| Duración de 30 min sin renovación | **se mantiene**: sin refresh, es lo que acota el daño de H-10 |

### 5.2 Logout real (aditivo, aplicable ya) — ✅ **HECHO (2026-08-05)**

`POST /auth/logout`: escribe `sesiones_invalidas_desde = now()` para esa credencial y responde 204.
El SPA lo llama **antes** de limpiar `localStorage`. Efecto colateral aceptado y que hay que
documentar en pantalla: cierra **todas** las sesiones de esa cuenta, no solo la del navegador
actual. Sesiones individuales requieren `jti`, que no cabe en el token congelado.

> Con su fila en `matriz-operacion-rol.md` (roles: TODOS) y alcance implícito **no discutible**:
> solo puede cerrar **su propia** cuenta, porque la persona sale del token y no del cuerpo.
> Responde 204 incluso si la persona no tuviera credencial — devolver 404 ahí sería un oráculo.

### 5.3 Mecanismo definitivo tras el corte (propuesta D-S0-13)

| Pieza | Propuesta | Por qué |
|---|---|---|
| **Acceso** | JWT corto, **10 min**, con `jti` y `id_sesion` | el 401 deja de ser una molestia porque hay refresh |
| **Refresco** | token opaco rotatorio en cookie **`HttpOnly; Secure; SameSite=Strict`** | **fuera del alcance de JavaScript**: cierra H-10, que `localStorage` no puede cerrar |
| **Estado** | tabla `sesion` (id, credencial, emitida, último uso, ip, agente, revocada) | revocación **por sesión**, y "cerrar sesión en otros dispositivos" |
| **Inactividad** | **30 min** sin uso → sesión muerta | |
| **Duración absoluta** | **12 h**, sin excepción | acota una sesión secuestrada |
| **Detección de robo** | reutilizar un refresh ya rotado **revoca toda la familia** | patrón estándar de rotación |

> **Aviso que no hay que perder:** al pasar a cookies, **CSRF deja de ser irrelevante**. Hoy
> `csrf.disable()` es correcto porque el token va en `Authorization`; con cookies **hay que
> reactivar CSRF** (double-submit o `SameSite=Strict` + verificación de origen). Es el error clásico
> de esta migración.

Para roles de gobierno: duración absoluta **más corta (2 h)** y reautenticación para operaciones
sensibles (`sudo mode`).

---

## 6. S0.6 — Administración segura

### 6.1 MFA obligatorio para roles administrativos (D-S0-14)

> **Desarrollado y corregido el 2026-08-06 en
> [`plan-s0-6-mfa-y-break-glass.md`](plan-s0-6-mfa-y-break-glass.md).** Lo de abajo es el esbozo;
> cuatro puntos cambiaron al concretarlo y **manda el documento nuevo**: el login usa un **desafío
> en dos llamadas** (no un `login-mfa` de un solo cuerpo); los códigos de respaldo llevan
> **identificador + 80 bits con hash lento y sal**; el límite de intentos es **acumulado por
> cuenta**, no solo por desafío; y el anti-replay es una **actualización atómica**, no una
> comparación.

- **TOTP** (RFC 6238) compatible con cualquier aplicación autenticadora estándar, **sin depender de
  un proveedor externo para generar códigos** y sin SMS.
- Tabla `factor_autenticacion`: credencial, tipo, secreto **cifrado en reposo**, alta, último uso,
  códigos de recuperación (hasheados, un solo uso).
- **D-S0-19 resuelta (2026-08-04): obligatorio desde el primer día** para `TENANT_ADMIN` y
  `PLATFORM_ADMIN`. Para BROKER y AGENTE: **la arquitectura queda preparada y la activación es
  posible, pero no obligatoria**. El riesgo de dejar fuera al único administrador se cubre con el
  break-glass (§6.2) y con los códigos de recuperación, no relajando la regla.
- **Requisitos de la implementación**, todos verificables: QR mostrado **una sola vez**;
  confirmación obligatoria del primer código antes de activar; códigos de recuperación
  **almacenados con hash**; regenerar códigos **invalida los anteriores**; y auditoría de
  activación, uso, fallo y consumo de código de recuperación.
- **El secreto TOTP nunca entra en el JWT** (ni el secreto, ni un indicador que permita derivarlo).
- **Problema de contrato (R1/R3):** el login es de **un solo paso** y su respuesta está congelada. No
  hay forma de devolver "falta el segundo factor" sin romperlo.
  → **Propuesta:** endpoint **`POST /auth/login-mfa`** (aditivo, nuevo): mismo cuerpo + `codigo`. El
  `POST /auth/login` clásico **rechaza con 401** a las cuentas con MFA activo. Duro pero honesto:
  una cuenta con MFA no puede entrar por el camino viejo, y el Blazor —que no la va a usar— no se
  entera. El SPA usa el nuevo.
- Alta del factor con verificación inmediata (no se activa hasta validar un código).

### 6.2 Break-glass (D-S0-15) — ⛔ **SUPERADA el 2026-08-06**

> **Este diseño quedó descartado.** El detalle vigente vive en
> [`plan-s0-6-mfa-y-break-glass.md`](plan-s0-6-mfa-y-break-glass.md) §8, y lo que cambia es la
> naturaleza del mecanismo, no un parámetro: **deja de ser una cuenta y pasa a ser una concesión
> técnica de recuperación** — temporal (30 min), acotada a un tenant, a una persona objetivo y a
> tres acciones, sin sesión y sin acceso a datos comerciales.
>
> Los cuatro motivos, porque explican el rediseño: una cuenta permanente mezcla administración de
> plataforma, recuperación técnica, acceso extraordinario y gobierno de tenants en una sola
> identidad privilegiada creada por anticipado; una contraseña partida **sigue siendo una
> contraseña permanente y reutilizable**, y **no prueba técnicamente que participaron dos
> custodios**; el Bloque 6 **no emite `PLATFORM_ADMIN`** (D-S0-29 rechazada); y el mínimo
> privilegio pide no crear la capacidad antes de necesitarla.
>
> Lo que sigue se conserva **como registro de lo que se propuso**, no como plan.

- **Una** cuenta `PLATFORM_ADMIN` de emergencia por instalación, **`estado = 'I'` en operación
  normal**: no entra.
- Activación **fuera del producto**: procedimiento operativo (secreto en custodia partida, dos
  personas) que la habilita por una ventana corta.
- **Cada uso genera evento de auditoría de severidad máxima**, con motivo obligatorio, y **notifica
  a todos los `TENANT_ADMIN`**.
- Caduca sola: al vencer la ventana vuelve a `'I'` (tarea de reconciliación, no confianza en que
  alguien la apague).
- **Nunca** se usa para trabajo cotidiano; su uso en un mes sin incidente es, por definición, un
  hallazgo.

### 6.3 Auditoría de seguridad (V30) — ✅ **HECHO (2026-08-05)**

Tabla **`evento_seguridad`**, **append-only** (sin `UPDATE` ni `DELETE`; se revoca el privilegio al
usuario de la aplicación):

```
id, fecha, tipo, resultado, id_credencial, id_persona, organizacion_id,
rol_efectivo, ip, agente_usuario, id_objetivo, motivo, detalle_json
```

Tipos mínimos: `LOGIN_OK`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO_429`, `MFA_OK`, `MFA_FALLIDO`,
`LOGOUT`, `SESIONES_INVALIDADAS`, `PASSWORD_CAMBIADA`, `PASSWORD_RESTABLECIDA`,
`INVITACION_EMITIDA`, `INVITACION_CANJEADA`, `RECUPERACION_EMITIDA`, `RECUPERACION_CANJEADA`,
`CUENTA_ACTIVADA`, `CUENTA_DESACTIVADA`, `ROL_OTORGADO`, `ROL_REVOCADO`,
`ACCESO_TENANT_CONCEDIDO`, `ACCESO_TENANT_USADO`, `BREAK_GLASS_ACTIVADO`.

**Regla de higiene:** ni contraseñas, ni hashes, ni tokens, ni secretos MFA en `detalle_json`.
Un test lo comprueba sobre la lista de campos permitidos.

Se separa de `historial_estado` a propósito: aquello audita **transiciones de negocio**; esto audita
**accesos y privilegios**. Mezclarlos volvería inmanejables las dos consultas.

> **Cómo quedó ejecutado (2026-08-05).** Los 22 tipos entran en el `CHECK` desde V30 —incluidos los
> de MFA, invitaciones y break-glass, que **todavía no se emiten**— para que añadir esos flujos no
> exija otra migración. Se escribe por **un solo sitio**, `EventosSeguridad`, por el mismo motivo
> por el que `Transiciones` es el único que muta estados.
>
> Dos decisiones que hacen útil a la tabla y que no son evidentes:
> - **cada evento va en su propia transacción** (`REQUIRES_NEW`): un login fallido tiene que quedar
>   registrado **aunque** la operación que lo provocó termine lanzando; un evento que se va con el
>   rollback de lo que audita no audita nada;
> - la higiene **descarta** la clave sospechosa en vez de enmascararla — un `"***"` confirmaría que
>   el campo existía y no aporta nada. La lista negra se compara por contención, así que
>   `contrasenaNueva` cae por `contrasena`.
>
> **Lo append-only es de la aplicación, no del motor todavía:** V30 crea la tabla y los índices;
> **retirar `UPDATE`/`DELETE` al usuario de la aplicación es tarea del despliegue productivo**,
> porque en dev ese mismo rol es dueño del esquema. Queda anotado para el Bloque 9.

### 6.4 Acceso excepcional a otros tenants (D-S0-16)

Tabla **`concesion_acceso_tenant`**: `id_usuario`, `organizacion_id`, `motivo` (obligatorio),
`vigencia_desde`, `vigencia_hasta` (**obligatoria**, tope 24 h), `concedida_por`, `revocada_en`.

- Un `PLATFORM_ADMIN` **no ve ningún tenant por defecto**: necesita una concesión vigente. Esto
  contradice la lectura intuitiva de "administrador de plataforma", y es a propósito.
- `Alcances` la consulta al resolver el tenant; **cada request** bajo concesión emite
  `ACCESO_TENANT_USADO`.
- Se notifica a los `TENANT_ADMIN` de la organización afectada, al conceder y al usar.

---

## 7. S0.7 — Pruebas y criterios de aceptación

**S0 está terminado cuando los nueve escenarios pasan**, cada uno con su prueba automatizada. No son
ilustrativos: son el gate.

| # | Escenario | Criterio verificable | Nivel |
|---|---|---|---|
| **A1** | **Pérdida del último administrador** | Bajar, desactivar o degradar al último `TENANT_ADMIN` activo → error de negocio con mensaje propio **y** el trigger falla si se intenta por SQL. Con dos administradores, bajar uno funciona. | service + E2E + SQL |
| **A2** | **Escalamiento de privilegios** | Un AGENTE y un BROKER reciben **403** en las 6 operaciones de gobierno; ningún endpoint permite auto-otorgarse `TENANT_ADMIN`; un BROKER no puede invitar fuera de su equipo; `MatrizOperacionRolTest` sigue verde. | web + E2E |
| **A3** | **Usuario desactivado con sesión activa** | Token válido emitido **antes** de la baja → el siguiente request da **401**, no 200. (Hoy daría 200 durante 30 min.) | E2E |
| **A4** | **Token o sesión revocados** | Tras `POST /auth/logout` o cambio de contraseña, el token anterior da **401** en el mismo instante (o dentro del TTL declarado, que para gobierno es 0). | E2E |
| **A5** | **Recuperación vencida o reutilizada** | Un token de recuperación **caducado** → 400; **ya usado** → 400; **emitir uno nuevo invalida el anterior**; el token nunca aparece en la respuesta de solicitud. | service + E2E |
| **A6** | **Credenciales del seed rechazadas** | En perfil productivo, `Admin2026`, `Broker2026` y `Agente2026` responden **401**; y **el contexto no arranca** si queda algún hash del seed. En perfil `dev` siguen funcionando (los 13 scripts de `verificacion/` no se tocan). | arranque + E2E |
| **A7** | **Aislamiento entre organizaciones** | Se extiende el fixture de dos tenants que ya existe (`e2e-personas.ps1`): un `TENANT_ADMIN` de A no ve ni resuelve nada de B (**404**); un `PLATFORM_ADMIN` **sin concesión** tampoco; **con** concesión vigente sí, y queda registrado; **vencida** la concesión, vuelve el 404. | E2E |
| **A8** | **Ausencia del secreto en producción** | Perfil `prod` sin `API_TOKEN_SECRET`, con uno de 31 caracteres, o con el literal de desarrollo → **el arranque falla** y el mensaje nombra la variable. Perfil `dev` arranca con WARN. | test de contexto |
| **A9** | **Auditoría completa de operaciones administrativas** | Cada uno de los ~20 tipos de evento se emite en su caso; login fallido y 429 quedan registrados; **ninguna fila contiene secretos** (lista blanca de campos verificada por test); la tabla rechaza `UPDATE`/`DELETE`. | service + SQL |

**Regresión obligatoria** (S0 no puede romper lo ya verificado): reactor **469+**, Angular
**469+**, y los scripts de `verificacion/` que tocan identidad y tenant —`e2e-personas.ps1`
(122/122), `e2e-v6.ps1` (46/46)— vuelven a pasar **con sus cifras actuales**.

**Suite nueva propuesta:** `verificacion/e2e-s0-seguridad.ps1`, cubriendo A1–A9 de punta a punta
contra PostgreSQL real, con el patrón de base efímera por corrida que ya usa `Invoke-E2E.ps1`.

---

## 8. Orden de ejecución

Ordenado por **riesgo retirado por unidad de trabajo**, y de forma que cada fase deje el sistema
entero y desplegable.

| Fase | Contenido | Depende de | Bloquea al corte | Estado |
|---|---|---|---|---|
| **S0.1** Contención | §1 entera: fallback, arranque fallido, rotación, V900, separación del seed | — | **sí** | ✅ hecho (Bloque 2) |
| **S0.2** Sesiones, auditoría y bloqueo | §4.7 (`sesiones_invalidas_desde`, V29), §5.2 (logout real), §6.3 (`evento_seguridad`) y §4.8 (`intento_acceso`), V30 | S0.1 | **sí** | ✅ **hecho y verificado (2026-08-05, Bloque 3)** |
| **S0.3** Contraseñas | §4.1–4.5: cambio, invitación, recuperación, temporal, política. **V31** | S0.2 (invalidación y auditoría) | **sí** | ✅ **hecho y verificado (2026-08-05, Bloque 4)** |
| **S0.4** Identidad y gobierno | §2 y §3: rol `ADMIN` real, membresía, `TENANT_ADMIN`/`PLATFORM_ADMIN`, matriz, frontend. V32, V33, V34 | S0.3 | **sí** | 🔒 bloqueada por D-S0-17 (Bloque 5) |
| **S0.5** Administración segura | §6.1 MFA, §6.2 break-glass, §6.4 acceso excepcional | S0.4 | **no** (deseable) |
| **S0.6** Hash progresivo | §4.6 | S0.3 | **no** |
| **S0.7** Sesiones definitivas | §5.3 — cookies, refresh, CSRF | **el corte de GlassFish** | n/a (es *después*) |
| **V36** | Retirar `es_administrador` y su índice | corte del legado | n/a | ⬜ diferida |

**Por qué contención va primera y sola:** retira los dos riesgos críticos (H-01, H-03) sin depender
de ningún modelo nuevo. Si S0 se detuviera ahí, el sistema ya estaría en otra categoría de riesgo.

**Por qué gobierno va después de contraseñas:** repartir administradores (§2.5) sin poder darles una
contraseña propia obliga a compartir la del seed. Al revés no funciona.

---

## 9. Riesgos del propio plan

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| **Rotar el secreto tira todas las sesiones de los dos backends** | alta | medio | ventana anunciada + validación con dos secretos (§1.3b) |
| **La revocación por `iat` añade una lectura por request** | alta | bajo | caché con TTL corto; TTL 0 solo para gobierno; medir con la sonda de transporte antes y después |
| **La migración deja al admin sin sesión** (cambia su `idDominio`) | **cierta** | bajo | está previsto (§3.1): ventana + aviso; vuelve a entrar |
| **La revisión de las 18 filas BROKER+ADMIN cambia permisos hoy vigentes** | alta | **alto** | es una **decisión funcional**, no técnica: se lista fila por fila y se aprueba antes de tocar código |
| **Perfil `prod` mal aplicado deja V900 sin ejecutar** | media | alto | el gate de arranque (§1.2) detecta hashes del seed y no arranca |
| **Dev y prod divergen** (seed en otra location) | cierta | medio | el **esquema** sigue en una sola location; solo divergen los datos |
| **MFA por un endpoint aparte confunde a los clientes** | media | medio | el login clásico **rechaza explícitamente** a las cuentas con MFA; se documenta en el contrato |
| **Sin proveedor de correo, recuperación e invitación quedan a medias** | **cierta** | alto | D-S0-11 (§10): decidir proveedor o aceptar el modo "temporal entregada por el administrador" |
| **`token_acceso` mal implementado = puerta trasera** | baja | **crítico** | hash del token, un solo uso transaccional, vigencia corta, y A5 como gate |
| **Cookies sin CSRF tras el corte** | media | **crítico** | escrito en §5.3 como aviso explícito; test de CSRF en la suite de S0.7 |

---

## 10. Decisiones — estado al 2026-08-04

| ID | Decisión | Estado |
|---|---|---|
| **D-S0-20** | Perfil `prod` con **arranque fallido** ante configuración insegura | ✅ **SÍ**, y con la lista ampliada de §1.2 (se suman almacén no persistente y valores de desarrollo en `prod`) |
| **D-S0-19** | MFA obligatorio para gobierno | ✅ **Desde el primer día** para `TENANT_ADMIN` y `PLATFORM_ADMIN`; preparado pero opcional para BROKER/AGENTE (§6.1) |
| **D-S0-18** | ¿Puede un BROKER invitar/activar a sus agentes? | ✅ **NO.** Invitar, activar, suspender y administrar membresías son **solo de `TENANT_ADMIN`** (§4.4) |
| **D-S0-11** | Proveedor de correo | ✅ **Deja de bloquear**: puerto `NotificadorIdentidad` + implementación fuera de banda; el transporte real se elige con la infraestructura productiva (§4.3) |
| **D-S0-21** | Bloqueo por intentos fallidos **por cuenta y por IP** | ✅ **Entra en el alcance de S0** con sección propia (§4.8), sobre PostgreSQL y **sin Redis** |
| **D-27** | Autorización de datos personales | ✅ **Aprobada** — una sola vez en el alta; `decision-autorizacion-datos-personales.md`. Afecta a la migración de S0 (§3) |
| **D-S0-17** | Las 18 operaciones `BROKER+ADMIN` | ⏳ **PROPUESTA fila por fila** en `matriz-d-s0-17-operaciones-broker-admin.md` — **8 de 18 cambian**. **Bloquea §2, §3 y todo S0.4** hasta que se apruebe |

> **Única puerta que sigue cerrada: D-S0-17.** El encargo lo fija expresamente: *"No iniciar
> implementación de roles administrativos hasta aprobar la matriz D-S0-17."* Todo lo demás de S0.1,
> S0.2 y S0.3 puede ejecutarse ya.

---

## Anexo — resumen de entregables

**Migraciones nuevas:** V28 (D-27 autorización), **V29 y V30 ya aplicadas**, V31 (contraseñas), V32–V34 (identidad y gobierno), V35 (MFA) y **V900 solo-prod**; **V36 diferida al corte**. Ninguna edita una migración aplicada.

**Tablas nuevas:** `evento_seguridad`, `concesion_acceso_tenant`, `token_acceso`,
`credencial_password`, `factor_autenticacion`, y `sesion` (solo en S0.7, post-corte).

**Endpoints nuevos (todos aditivos, todos con fila en la matriz):**
`GET /sesion` · `POST /perfil/contrasena` · `POST /auth/logout` · `POST /auth/recuperacion` ·
`POST /auth/recuperacion/canje` · `POST /agentes/{id}/invitacion` · `POST /brokers/{id}/invitacion` ·
`POST /auth/login-mfa` · `POST /perfil/mfa` (+ verificación) ·
`POST /plataforma/acceso-tenant` (concesión) — sujeto a D-S0-17.

**Documentos a crear o actualizar:** este plan · `matriz-operacion-rol.md` (vocabulario nuevo +
revisión de 18 filas) · `contrato-transversales-frontend.md` (cambio obligatorio de contraseña,
rol efectivo, logout real) · `checklist-migracion.md` y `mapa-estado-y-pendientes.md` (S0 delante de
E5) · `backend-spring/README.md` (estado vivo).

**Lo que este plan NO hace** (y conviene decirlo): no activa RLS —sigue siendo decisión posterior al
corte (D-24)—, no retira `GET /documentos/contenido` público (H-12, atado al Blazor), no introduce
cuenta global multi-organización (D-22 sigue siendo del corte) y no toca el almacén S3, que es E5.
