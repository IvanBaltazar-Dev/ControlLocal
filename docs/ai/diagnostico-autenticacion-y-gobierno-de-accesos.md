# Diagnóstico — autenticación, cuentas administrativas y gobierno de accesos

**Fecha:** 2026-08-04 · **Alcance:** `backend-spring/` (v2) + `frontend-angular/`, con referencias a
`backend-java/` (v1) donde el contrato congelado los ata.
**Naturaleza:** diagnóstico **de solo lectura**. No se modificó, desactivó ni eliminó ninguna cuenta,
tabla, migración ni configuración. Toda afirmación de este documento está respaldada por el archivo
y la línea que se citan, o por una consulta a la BD de desarrollo (`controllocal_dev`).

> Nota de método: `docs/ai/seguridad-no-leer.md` existe en el repositorio y **no se abrió**, por la
> convención declarada en `CLAUDE.md`. Si ese documento contiene decisiones de seguridad ya tomadas,
> este diagnóstico puede solaparse o contradecirlo sin saberlo.

---

## 0. Resumen ejecutivo

**El "administrador" no es un rol del modelo: es un booleano.** `es_administrador` en
`detalle_broker` convierte a un broker en ADMIN en el momento del login. No existe `tipo_rol =
'ADMIN'` —el `CHECK` de `persona_rol` ni siquiera lo admite— y por eso el administrador **hereda
todo lo de un broker** y suma seis operaciones de gobierno.

Existe **exactamente una cuenta administrativa**, sembrada por una **migración Flyway versionada**
(no por un script aparte, ni por registro manual), con **contraseña conocida y publicada** en el
repositorio, y **de la que dependen las cuatro operaciones de asignación y el alta de brokers**.

Los tres hallazgos que mandan sobre el resto:

| # | Hallazgo | Severidad |
|---|---|---|
| **H-01** | **El secreto de firma del JWT cae a un valor fijo escrito en el código** —el mismo en los dos backends— cuando `API_TOKEN_SECRET` no está configurado o mide < 32 caracteres, **en silencio**. Quien tenga el repositorio puede fabricar un token ADMIN válido. | **CRÍTICA** |
| **H-02** | **No existe ninguna forma de cambiar una contraseña**: no hay endpoint, y el `PUT` de brokers/agentes **ignora el campo `contrasena`**. Las claves sembradas (`Admin2026`, `Broker2026`, `Agente2026`) solo se pueden rotar por SQL directo. | **CRÍTICA** |
| **H-03** | **Desactivar al administrador deja el sistema sin gobierno y sin salida**: solo un ADMIN puede crear o promover a otro ADMIN, así que la recuperación exige SQL directo. No hay acceso de emergencia. | **ALTA** |

---

## 1. Cuentas administrativas

### 1.1 Cuáles existen

Una sola, comprobada contra la BD de desarrollo:

```
nombre_usuario           | estado | codigo_broker | es_administrador | organizacion
admin@controllocal.test  | A      | BRK-ADM-001   | true             | BROX_LEGACY
```

- **Persona**: «Broker Administrador ControlLocal», documento `00000000`, correo
  `admin@controllocal.test`, teléfono `999999999`.
- **Roles en `persona_rol`**: `USUARIO_INTERNO` (la credencial) + `BROKER` (el rol operativo).
  **No hay rol ADMIN**: la banda ADMIN se deriva del flag.
- El resto del padrón: **5 brokers** (`BRK-001..005`) y **15 agentes** (`AGE-001..015`), ninguno
  administrador.

### 1.2 Cómo fueron creadas

**Seed dentro de una migración Flyway versionada**:
`backend-spring/controllocal-app/src/main/resources/db/migration/V3__seed_identidad_base.sql`.

No es un script aparte que alguien decide correr: es **V3 del historial de Flyway**, así que se
aplica **en toda base nueva**, incluida cualquier futura de producción, salvo que se excluya
explícitamente. El archivo lo dice sin rodeos en su cabecera (líneas 3-6):

```sql
-- 1 broker administrador (BRK-ADM-001) + 5 brokers (BRK-001..005) + 15 agentes (AGE-001..015).
-- Credenciales dev: admin Admin2026 / brokers Broker2026 / agentes Agente2026.
```

El alta ocurre en cuatro pasos dentro de esa misma migración: `persona` → `persona_rol
USUARIO_INTERNO` + `credencial_usuario` → `persona_rol BROKER` + `detalle_broker` (con
`es_administrador = TRUE`) → `supervision_agente` inicial.

La v1 tiene su equivalente en `database/02_seed_base_data.sql`, con **los mismos hashes**: el
formato PBKDF2 es portable entre los dos backends y el seed se reutilizó tal cual.

### 1.3 Roles asignados y permisos efectivos

El rol de sesión se **deriva en el login**, no se lee de una tabla de roles
(`AutenticacionServiceImpl.resolverIdentidad`, líneas 57-80):

```java
var rolBroker = roles.buscarVigente(persona.getId(), TipoRol.BROKER);
if (rolBroker.isPresent()) {
    ...
    detalle.isEsAdministrador() ? "ADMIN" : "BROKER"
```

Reparto de las **150 operaciones** declaradas en `docs/ai/matriz-operacion-rol.md` (recuento sobre
el propio documento, que `MatrizOperacionRolTest` mantiene sincronizado con el código):

| Gate | Operaciones | El ADMIN… |
|---|---|---|
| `TODOS` (autenticado, sin gate) | 60 | entra, con alcance de **todo su tenant** |
| `AGENTE` | 59 | **no entra** (403) |
| `BROKER, ADMIN` | 18 | entra |
| `ADMIN` | **6** | es el único que entra |
| `BROKER` (sin ADMIN) | 3 | **no entra** (403) |
| `PUBLICO` | 3 | — |

**Las 6 operaciones exclusivas del administrador:**

| Método | Ruta | Dónde está el gate |
|---|---|---|
| POST | `/brokers` | `BrokersController:61` `@PreAuthorize("hasRole('ADMIN')")` |
| PUT | `/brokers/{id}` | `BrokersController:71` |
| GET | `/asignaciones/agentes` | `AsignacionesController:21` (**a nivel de clase**) |
| GET | `/asignaciones/brokers` | ídem |
| GET | `/asignaciones/historial` | ídem |
| POST | `/asignaciones/reasignar` | ídem |

**Tres asimetrías que conviene tener presentes** (todas deliberadas y verificadas por test):

1. **`/tareas` es el único recurso sin acceso de ADMIN** — la bandeja es estrictamente personal del
   agente. Al administrador le responde 403.
2. **Las tres operaciones de comisión son `BROKER` sin `ADMIN`**
   (`ContratosController`: `asignar`, `cobro`, `movimientos`). Funciona porque el filtro JWT publica
   **una sola authority** `ROLE_<rol>` (`FiltroAutenticacionJwt:52`), así que ADMIN no "contiene" a
   BROKER. Es cable heredado de la v1.
3. **El administrador no produce**: `IndicadorServiceImpl` lo **excluye** de la tabla de desempeño
   por broker (contrato E4 §3.8) porque es gobierno, no producción.

Además, el flag da un poder de segundo orden: `Alcances.de(actor)` devuelve `global = true` **solo**
para ADMIN, y ese booleano recorre el `WHERE` de prácticamente todas las consultas con alcance. En
la práctica, el ADMIN lee **toda la operación de su organización** en los 60 endpoints sin gate.

### 1.4 Tenant: acotado, no global

**No es una cuenta global.** `credencial_usuario` hereda `organizacion_id` (V6) y `Alcances`
filtra **primero por tenant y después por rol**:

```java
// Alcances.java:63-70
public Alcance de(Actor actor) {
    long idOrganizacion = actor.idOrganizacion();
    if (actor.esAdmin()) {
        return new Alcance(idOrganizacion, true, List.of());
    }
```

Y el "uno solo" pasó de global a **por organización** en V6 (líneas 81-83):

```sql
-- V1: CREATE UNIQUE INDEX uq_broker_admin_unico ON detalle_broker ((es_administrador)) WHERE es_administrador;
DROP INDEX uq_broker_admin_unico;                       -- V6
CREATE UNIQUE INDEX uq_broker_admin_unico ON detalle_broker (organizacion_id) WHERE es_administrador;
```

Es decir: **un administrador por corredora**, y el modelo ya lo soporta. Lo que **no** existe todavía
es una figura por encima del tenant (administrador de plataforma): hoy nadie puede ver ni gobernar
dos organizaciones.

**El tenant lo resuelve el backend, nunca el cliente** (D-20): `FiltroAutenticacionJwt:50` publica
`SesionDeRequest = claims del token + OrganizacionService.idOrganizacionActual()`. Y mientras dure la
convivencia con GlassFish, `idOrganizacionActual()` devuelve **siempre `BROX_LEGACY`**
(`OrganizacionServiceImpl:24-38`, memoizado en un `volatile`): **el login de cualquier otro tenant
está cerrado por construcción**.

### 1.5 Dónde aparece

| Superficie | Qué muestra |
|---|---|
| `GET /brokers`, `GET /brokers/{id}` | `esAdministrador: true` en `BrokerResponse` |
| `GET /asignaciones/brokers` | `esAdministrador` en `AsignacionBrokerResponse` |
| SPA `brokers` | insignia "Administrador" en la fila (`brokers.html:50`) |
| SPA `broker-detail` | insignia en la cabecera (`broker-detail.html:8`) |
| SPA `broker-form` | casilla `esAdministrador`, **deshabilitada al editar** y bloqueada si ya existe uno (`broker-form.ts:65,165,179`) |
| SPA `asignaciones` | insignia, y **se lo excluye de la lista de brokers reasignables** (`asignaciones.ts:84`) |
| Dashboard / Indicadores | **no aparece** en "Desempeño por broker": está excluido a propósito |
| Login | `LoginResponse.rol = "ADMIN"`, y el SPA dibuja el menú con `acceso.ts` |

### 1.6 Credenciales conocidas, predeterminadas y compartidas

**Las tres cosas a la vez.** Verificado en la BD de desarrollo:

```
hash (prefijo)               | cuentas
pbkdf2$100000$3263AxQO/Xv2Fa |     15   ← los 15 agentes, MISMA sal y MISMO hash
pbkdf2$100000$Kj4WmHhqD//I1l |      5   ← los 5 brokers, ídem
pbkdf2$100000$uy2GnOLWMudcye |      1   ← el administrador
```

Compartir el hash **con la misma sal** significa literalmente **la misma contraseña**: 15 agentes
comparten `Agente2026` y 5 brokers comparten `Broker2026`. Para el propósito del seed (datos de
prueba) es cómodo; en producción sería una única credencial para veinte personas.

Las claves en claro están escritas en **20 archivos versionados**: `CLAUDE.md`, `AGENTS.md`,
`backend-spring/README.md`, la propia `V3`, `database/02_seed_base_data.sql`, `PasswordHasherTest` y
los **13 scripts de `verificacion/`**.

### 1.7 De qué depende el sistema

| Depende | Consecuencia si no hay ADMIN operativo |
|---|---|
| `POST /brokers` | **no se pueden crear brokers** |
| `PUT /brokers/{id}` | no se pueden editar ni desactivar brokers |
| `/asignaciones/*` (4 ops) | **no se puede reasignar un agente a otro broker**, ni ver el organigrama ni su historial |
| Lectura global del tenant | nadie ve la operación completa; cada broker queda con su equipo |

Lo que **sigue funcionando** sin administrador: todo el ciclo comercial (prospección → captación →
oportunidad → solicitud → contrato), el alta de agentes (`POST /agentes` es `BROKER, ADMIN`) y la
liquidación de comisiones (que es `BROKER` *sin* ADMIN).

### 1.8 Qué pasaría si se desactiva o se elimina

**Si se desactiva** (`credencial_usuario.estado_administrativo = 'I'`, que es lo que hace
`PUT /brokers/{id}` con `estado`):

1. El login lo rechaza: `CredencialUsuario.autenticable()` exige `estado = ACTIVO` **y** rol vigente.
2. Las 6 operaciones de gobierno quedan **inalcanzables para todo el mundo**.
3. **No hay salida por producto**: crear otro administrador exige `POST /brokers`, que exige ADMIN.
   `BrokerServiceImpl.validarAdministrador` además comprueba que la credencial del actor esté en
   `'A'`, así que ni siquiera un ADMIN suspendido puede rehabilitarse.
   → **La única recuperación es `UPDATE` directo sobre la base.** No existe acceso de emergencia,
   ni cuenta de rotura de cristal, ni comando de administración.

**Si se elimina** (`DELETE` de la fila): no hay endpoint que lo permita —el sistema **no borra
usuarios, solo los desactiva**—, y por SQL chocaría con las referencias históricas: `persona` es
destino de FK desde `historial_estado.id_actor`, y el administrador figura además como actor en
`reasignacion_agente_broker` (V10). En la práctica **no es eliminable sin romper la trazabilidad**.

---

## 2. Estado de cada pieza

### 2.1 Login y validación de credenciales

- **Endpoint**: `POST /auth/login`, público (`ConfiguracionSeguridad:54`).
- **Flujo**: `AuthController` → `AutenticacionServiceImpl.autenticar` →
  `CredencialUsuarioRepository.buscarActivaPorNombreUsuario(idOrganizacion, usuario)` →
  `PasswordHasher.verificar`.
- **Condiciones para entrar** (las cuatro): credencial del **tenant actual**, `estado_administrativo
  = 'A'`, rol `USUARIO_INTERNO` **vigente** (`vigencia_hasta is null`), `persona.estado = 'A'`, y
  además un **rol operativo vigente** (BROKER o AGENTE); sin él, `CredencialesInvalidasException`.
- **Mensaje único** `Credenciales invalidas.` (401) para usuario inexistente, clave incorrecta y
  cuenta suspendida: no filtra cuáles existen. **Correcto.**
- La contraseña viaja como `char[]` y se **limpia en el `finally`** (`AuthController:64-66`).

### 2.2 Tokens y sesiones

`TokenService` (backend-spring) — JWT HS256 **artesanal** (no hay librería JWT), byte-compatible con
el `TokenService` de la v1.

| Aspecto | Estado |
|---|---|
| **Creación** | claims `sub`, `rol`, `idUsuario`, `idDominio`, `iat`, `exp` |
| **Duración** | **30 min** (`DURACION_SEGUNDOS = 30 * 60`), fija, no configurable |
| **Renovación** | **no existe**. Ni refresh token, ni renovación por actividad: a los 30 min el siguiente request da 401 y el SPA cierra sesión |
| **Revocación** | **no existe**. Stateless puro: sin `jti`, sin lista negra, sin versión de credencial. Un token filtrado vale hasta su `exp` aunque se desactive la cuenta |
| **Validación** | firma comparada con `MessageDigest.isEqual` (tiempo constante), `alg`/`typ` verificados, rol en la lista blanca, `exp` comprobado |
| **Tenant** | **no viaja en el token** (D-20); lo resuelve el backend en cada request |

**El secreto (H-01).** `TokenService:37-46`:

```java
private static final String SECRETO_DEV = "ControlLocal-dev-fallback-token-secret-0001";
...
this.secreto = (configurado != null && configurado.length() >= 32)
        ? configurado.getBytes(...) : SECRETO_DEV.getBytes(...);
```

El mismo literal está en `backend-java/.../seguridad/TokenService.java:115-116`. Es intencional
—habilita el SSO entre backends durante la convivencia— pero tiene tres consecuencias:

1. Si `API_TOKEN_SECRET` no se define, **el sistema arranca igual, sin avisar**, firmando con un
   secreto público.
2. Una clave de **31 caracteres** se descarta en silencio y también cae al fallback.
3. Cualquiera con acceso al repositorio puede **fabricar un token ADMIN válido** para un despliegue
   mal configurado.

El `docker-compose.yml` de desarrollo **no define** `API_TOKEN_SECRET`, así que el entorno local
funciona hoy con el secreto público. Para desarrollo es correcto; el riesgo es que **el defecto
inseguro es también el silencioso**.

### 2.3 Cierre de sesión

**No existe endpoint de logout.** Es una consecuencia directa de que no haya revocación: el cierre es
**puramente del cliente** (`AuthService.cerrarSesion`, `auth.service.ts:43-51`): borra
`localStorage`, limpia la señal y navega a `/login`. El token sigue siendo válido en el servidor
hasta su `exp`.

El interceptor convierte **cualquier 401 del API** en cierre completo e idempotente
(`auth.interceptor.ts:20-22`), que es la lección que ya se había pagado en el Blazor.

### 2.4 Almacenamiento del token en el frontend

`localStorage`, clave `controllocal.sesion.v2`, con **la sesión entera** (token, rol, nombre,
`idUsuario`, `idDominio`, `expiraEn`).

- Al arrancar se **descarta la sesión vencida o corrupta** (`leerSesionGuardada`).
- El interceptor **solo adjunta el token a URLs del API** y nunca al login
  (`auth.interceptor.ts:10-16`): no se filtra a terceros.
- **Riesgo real**: `localStorage` es legible por cualquier script de la página → un XSS entrega el
  token. Con 30 min de vida y sin revocación, la ventana de abuso es de hasta media hora.

### 2.5 Contraseñas y hash

`PasswordHasher` — **PBKDF2-HMAC-SHA256, 100 000 iteraciones, sal de 16 bytes aleatoria
(`SecureRandom`), hash de 32 bytes**, formato `pbkdf2$iter$sal$hash`, comparación en tiempo
constante y rechazo de cualquier formato desconocido (nunca compara texto plano).

**El algoritmo está bien.** Lo que falta alrededor:

- **Ninguna política de contraseña**: `UsuariosInternos` solo exige que no esté vacía. Sin longitud
  mínima, sin complejidad, sin lista de claves comunes.
- **Sin caducidad, sin historial, sin obligación de cambio al primer ingreso.**
- Sin *pepper* ni parámetros configurables (100 000 iteraciones fijas en código).

### 2.6 Cambio de contraseña (H-02)

**No existe, por ninguna vía.**

- `PATCH /perfil` acepta **un solo campo**: `PerfilRequest(String telefono)`. La pantalla Blazor de
  cambio de contraseña era una maqueta sin backend.
- `PUT /brokers/{id}` y `PUT /agentes/{id}` **reciben `contrasena` en el DTO y la ignoran**:
  `BrokerServiceImpl.actualizar` (líneas 141-161) y `AgenteServiceImpl.actualizar` (líneas 403-427)
  actualizan nombre, teléfono, correo, estado y zona — **la contraseña no se toca**. Solo se usa en el
  alta.

Consecuencia: **las contraseñas del seed no se pueden rotar desde el producto**. Un administrador no
puede resetear la clave de un agente que la olvidó, y nadie puede cambiar la suya. La única vía es
`UPDATE credencial_usuario SET contrasena_hash = ...` con un hash generado a mano.

### 2.7 Recuperación de acceso

**No existe**: sin endpoint, sin token de un solo uso, sin envío de correo, sin preguntas de
recuperación. `Recover.razor` del Blazor era una maqueta y por eso no se portó al SPA.

### 2.8 Bloqueo por intentos fallidos

`LimitadorIntentos` — ventana fija de 60 s, **10 intentos por IP**, en memoria del proceso, y solo se
aplica a `POST /auth/login`. Al superarlo, 429 con el mensaje congelado.

Cuatro limitaciones que importan para producción:

1. **Limita por IP, no por cuenta**: no hay bloqueo de la cuenta atacada ni contador de fallos
   persistido. Un atacante distribuido prueba 10 claves por minuto **por cada IP**.
2. **`request.getRemoteAddr()`** — detrás de un balanceador o CDN, **todos los usuarios comparten la
   IP del proxy** y se estorban entre sí (no lee `X-Forwarded-For`).
3. **Por proceso**: con dos instancias, el límite efectivo se duplica.
4. **Cuenta intentos, no fallos**: un login correcto también consume cupo.

### 2.9 Activación y desactivación de usuarios

- **Se desactiva, no se borra**: `estado_administrativo` `'A'|'I'` en `credencial_usuario`, vía
  `PUT /brokers/{id}` (ADMIN) y `PUT /agentes/{id}` (BROKER, ADMIN).
- Un agente desactivado **no puede entrar**, pero **sus tokens vigentes siguen funcionando** hasta el
  `exp` (§2.2). La desactivación no es inmediata: tarda hasta 30 minutos en surtir efecto.
- El agente tiene **dos máquinas de estado distintas** que no hay que confundir: la administrativa
  (credencial, decide si entra) y la operativa (`detalle_agente.estado_operativo`, decide si se le
  asigna trabajo).
- No hay baja de personas ni anonimización: `persona.estado` existe y el login lo exige `'A'`, pero
  ningún endpoint lo cambia.

### 2.10 Aislamiento entre tenants

- **Discriminador en toda tabla privada**: `organizacion_id NOT NULL sin DEFAULT`, y
  `ArquitecturaTenancyTest` rompe el build si una entidad nueva no hereda `EntidadDeOrganizacion`.
- **Filtro en la aplicación** (D-24): el tenant es parámetro obligatorio del `WHERE`.
  **RLS de PostgreSQL NO está activado** — es la decisión declarada, no un olvido.
- **El cliente no elige tenant**: lo resuelve `FiltroAutenticacionJwt` desde el backend.
- **El login está cerrado a `BROX_LEGACY`** mientras dure la convivencia (D-20).
- **Comprobado por E2E** (`e2e-personas.ps1`, líneas ~700-745): se crea una segunda organización con
  su propio administrador, y se verifica que (a) su credencial recibe **401**, (b) no aparece en
  `/brokers`, y (c) su id responde **404** al ADMIN del tenant legado.

**Lo que el aislamiento no cubre todavía**: sin RLS, cualquier consulta futura que olvide el
parámetro de organización cruza la frontera sin que nada la detenga. La red la sostiene la disciplina
del código y sus tests, no el motor.

### 2.11 Auditoría

| Qué | ¿Se audita? | Dónde |
|---|---|---|
| Transiciones de estado del negocio | **Sí** | `historial_estado` (actor = persona + tipo de rol + motivo), único punto de escritura `Transiciones`, blindado por `ArquitecturaAuditoriaTest` |
| Reasignación agente ↔ broker | **Sí** | `reasignacion_agente_broker` (V10): anterior, nuevo, administrador, motivo y fecha-hora |
| **Login correcto** | **No** | — |
| **Login fallido** | **No** | no hay ni un `log`: `AuthController` y `AutenticacionServiceImpl` no tienen logger |
| **429 por límite de intentos** | **No** | — |
| **Alta de usuario / credencial** | **No** | `credencial_usuario` no es auditable |
| **Cambio de contraseña** | n/a | no existe la operación |
| **Activación / desactivación** | **No** | el cambio de `estado_administrativo` no deja rastro |
| **Alta o edición de broker** | **No** | — |

Y una distorsión propia del modelo actual: **las operaciones del ADMIN se auditan como BROKER.**
`Actor.tipoRolOperativo()` traduce `ADMIN → "BROKER"` porque en auditoría se registra el rol
*operativo*. El resultado es que **`historial_estado` no permite distinguir una acción de gobierno de
una acción de un broker cualquiera** — solo por el id de la persona.

### 2.12 Pruebas automatizadas

**Lo que hay:**

| Prueba | Cubre |
|---|---|
| `TokenServiceTest` (5) | ida y vuelta, token manipulado, firma de otro secreto, basura, emisión con datos inválidos |
| `AutenticacionServiceImplTest` (**1**) | **solo** que el login busca en la organización actual |
| `PasswordHasherTest` | formato y verificación del hash |
| `MatrizOperacionRolTest` (4) | cobertura de las 150 operaciones, sin filas muertas, roles == `@PreAuthorize`, `PUBLICO` == `permitAll` en los dos sentidos |
| `ArquitecturaTenancyTest` | toda entidad privada lleva discriminador |
| `acceso.spec.ts` (SPA) | el menú por rol coincide con la matriz |
| E2E `e2e-personas.ps1`, `e2e-v6.ps1` | login de los tres roles, 403 por rol, aislamiento de un segundo tenant |

**Lo que no está cubierto por ninguna prueba:**

- Login con **credencial desactivada** (`estado = 'I'`) o con **rol operativo cerrado**.
- **Token expirado** contra un endpoint real (el 401 y su mensaje).
- **Límite de intentos** (el 429): ningún test lo ejercita; los scripts lo *esquivan* con pausas.
- **Cierre de sesión** y comportamiento del token tras desactivar la cuenta.
- El escenario de **bloqueo por pérdida del administrador**.
- Que el `PUT` **ignore** la contraseña (comportamiento actual no fijado por test: cambiarlo no
  rompería nada).

---

## 3. Hallazgos por severidad

### CRÍTICA

| ID | Hallazgo | Evidencia | Clasificación |
|---|---|---|---|
| **H-01** | Secreto JWT con **fallback fijo, público y silencioso**, compartido por los dos backends. Permite falsificar tokens ADMIN si el despliegue no configura `API_TOKEN_SECRET` (o lo pone con < 32 caracteres). | `TokenService.java:37-46`; `backend-java/.../TokenService.java:115-123`; `application.yml` `controllocal.token.secreto: ${API_TOKEN_SECRET:}` | **Configuración temporal de desarrollo** que se vuelve **riesgo real de producción** por ser el defecto silencioso |
| **H-02** | **No existe cambio de contraseña**. El `PUT` recibe `contrasena` y la ignora; `/perfil` solo acepta teléfono. Las claves del seed no son rotables desde el producto. | `BrokerServiceImpl:141-161`; `AgenteServiceImpl:403-427`; `PerfilRequest.java` | **Diseño vigente** (hueco heredado de la v1) → riesgo real |
| **H-03** | **Credenciales predeterminadas conocidas y compartidas**: `Admin2026`; 5 brokers con la misma clave; 15 agentes con la misma clave. Publicadas en 20 archivos versionados. | Consulta a `credencial_usuario` (3 hashes para 21 cuentas); `V3__seed_identidad_base.sql:4` | **Datos de prueba** → riesgo real **si V3 se aplica en producción** |

### ALTA

| ID | Hallazgo | Evidencia | Clasificación |
|---|---|---|---|
| **H-04** | **Sin acceso de emergencia**: desactivado el único ADMIN, las 6 operaciones de gobierno quedan inalcanzables y solo un ADMIN puede crear otro. Recuperación únicamente por SQL. | `BrokersController:61,71`; `BrokerServiceImpl.validarAdministrador:202-215` | **Diseño vigente** → riesgo real |
| **H-05** | **Tokens irrevocables**: desactivar una cuenta no invalida sus sesiones; hay hasta 30 min de acceso tras la baja. No hay `jti`, lista negra ni versión de credencial. | `TokenService` (sin revocación); `AuthService.cerrarSesion` (cliente) | **Diseño vigente** (stateless) → riesgo real |
| **H-06** | **Sin auditoría de accesos**: no se registra login correcto, fallido, 429, alta de credencial ni activación/desactivación. Un ataque de fuerza bruta no deja rastro. | `AuthController`/`AutenticacionServiceImpl` sin logger; `historial_estado` solo cubre transiciones de negocio | **Diseño vigente** → riesgo real |
| **H-07** | **Bloqueo solo por IP y por proceso**, sin bloqueo de cuenta y sin leer `X-Forwarded-For`: detrás de un proxy todos comparten cupo y un atacante distribuido lo evade. | `LimitadorIntentos.java`; `AuthController:41` (`request.getRemoteAddr()`) | **Comportamiento heredado** de la v1 → riesgo real |
| **H-08** | **No existe recuperación de acceso.** Un usuario que olvida su clave depende de una intervención manual en base de datos. | Sin endpoint; `Recover.razor` era maqueta | **Diseño vigente** (hueco heredado) |

### MEDIA

| ID | Hallazgo | Evidencia | Clasificación |
|---|---|---|---|
| **H-09** | **El ADMIN audita como BROKER**: `historial_estado` no distingue gobierno de operación. | `Actor.tipoRolOperativo()` | **Diseño vigente** deliberado, con costo de trazabilidad |
| **H-10** | **Token en `localStorage`**: un XSS entrega una sesión válida de hasta 30 min. | `auth.service.ts:8,79` | **Diseño vigente** → riesgo real |
| **H-11** | **Sin política de contraseñas**: solo se exige "no vacía". | `UsuariosInternos.vacio(...)` | **Diseño vigente** |
| **H-12** | **`GET /documentos/contenido` es público** y su clave es la ruta física, de 32 bits, sin caducidad ni revocación, en el query string. Ya declarado como deuda; el SPA **no** se apoya en él (usa Blob con token). | `ConfiguracionSeguridad:57`; `checklist-migracion.md` §1 | **Comportamiento heredado** de la v1 → riesgo real |
| **H-13** | **Swagger y `/v3/api-docs` públicos sin token.** | `ConfiguracionSeguridad:59` | **Configuración temporal** (declarada RC-005 "en esta fase") |
| **H-14** | **`usuario_organizacion` está creada y rellenada por V6 pero el código no la usa**: no tiene repositorio ni lectura. La membresía real vive en `credencial_usuario` + `detalle_*`. Dos fuentes de verdad, una muerta. | `UsuarioOrganizacion.java` sin repositorio; backfill en `V6:159-173` | **Diseño vigente incompleto** — relevante para el modelo de gobierno |
| **H-15** | **Cobertura de pruebas de autenticación mínima**: `AutenticacionServiceImplTest` tiene **un** test. Sin pruebas de credencial desactivada, token expirado, 429 ni logout. | `controllocal-service/src/test/.../AutenticacionServiceImplTest.java` | **Diseño vigente** |

### BAJA / observaciones

| ID | Hallazgo | Clasificación |
|---|---|---|
| **H-16** | El seed vive en una **migración versionada (V3)**, no en un script opcional: cualquier base nueva nace con las 21 cuentas de prueba. Separar seed de esquema es una decisión pendiente. | Datos de prueba / diseño |
| **H-17** | `POSTGRES_PASSWORD: controllocal` en `docker-compose.yml`, con comentario "solo desarrollo local". | Configuración temporal |
| **H-18** | JWT implementado a mano (sin librería). Correcto hoy —compara en tiempo constante y valida `alg`—, pero es superficie propia a mantener. | Diseño vigente |
| **H-19** | No hay figura **por encima del tenant**: el modelo soporta un admin **por organización**, no un administrador de plataforma. | Diseño vigente (límite conocido) |

---

## 4. Clasificación transversal

**Diseño vigente y deliberado** (documentado, con test o decisión escrita):
tenant primero y rol después (`Alcances`); ADMIN acotado a su organización; un administrador por
organización (V6); `/tareas` sin ADMIN; comisiones `BROKER` sin ADMIN; ADMIN excluido del desempeño;
sesión stateless; aislamiento por discriminador + filtro de aplicación con RLS aplazado (D-24);
login cerrado a `BROX_LEGACY` (D-20).

**Comportamiento heredado del sistema v1** (congelado a propósito hasta el corte):
token HS256 con formato y secreto compartidos (SSO entre backends); mensajes exactos de 401/403/429;
límite de intentos por IP; `/documentos/contenido` público; ausencia de cambio de contraseña y de
recuperación.

**Configuración temporal de desarrollo** (debe cambiar antes de producción):
fallback del secreto JWT; `POSTGRES_PASSWORD` y `DB_PASSWORD` en el compose; Swagger abierto;
CORS a `http://localhost:4200`.

**Datos de prueba**: las 21 cuentas de V3 con sus tres contraseñas compartidas, los propietarios y
locales DEMO, y los fixtures que crean y borran los scripts de `verificacion/`.

**Riesgos reales para producción** (los que hay que resolver sí o sí antes del corte):
H-01, H-02, H-03, H-04, H-05, H-06, H-07, H-08, H-10, H-12.

---

## 5. Preguntas abiertas para el modelo de gobierno

No se implementa nada todavía; se dejan planteadas porque condicionan el diseño:

1. **¿ADMIN pasa a ser un rol de verdad?** Hoy es un booleano en el detalle de broker, con dos
   consecuencias: el administrador **es** un broker (y arrastra su semántica) y la auditoría no
   distingue sus actos. Convertirlo en `tipo_rol` toca el `CHECK` de `persona_rol`, el login, la
   matriz y `Actor`.
2. **¿Quién gobierna por encima del tenant?** Un administrador de plataforma no cabe en el modelo
   actual: `uq_broker_admin_unico` es por organización y `Alcances` nunca levanta la frontera. Habría
   que decidir si vive en `usuario_organizacion` (hoy muerta, H-14) o en una tabla nueva.
3. **¿Cuál es el acceso de emergencia?** Cuenta de rotura de cristal, comando de administración
   fuera del API, o promoción por operación de base con registro. Hoy no hay ninguno.
4. **¿Cómo se rota una contraseña?** Es prerrequisito de cualquier gobierno: sin cambio de clave, las
   credenciales compartidas del seed no se pueden retirar.
5. **¿La revocación de sesión entra ahora o después del corte?** Cambia la naturaleza del token
   (deja de ser puramente stateless) y hoy el formato está congelado por el SSO con GlassFish.
6. **¿El seed se separa del esquema?** Mientras V3 sea una migración versionada, toda base nueva
   nace con las cuentas de prueba.

---

## Anexo — evidencia

**Backend (`backend-spring/`)**

| Pieza | Archivo |
|---|---|
| Login | `controllocal-web/.../controlador/AuthController.java` |
| Autenticación | `controllocal-service/.../impl/AutenticacionServiceImpl.java` |
| Token JWT | `controllocal-web/.../seguridad/TokenService.java` |
| Filtro y principal | `.../seguridad/FiltroAutenticacionJwt.java`, `.../SesionDeRequest.java`, `.../SesionActual.java` |
| Configuración de seguridad | `.../seguridad/ConfiguracionSeguridad.java` |
| Límite de intentos | `.../seguridad/LimitadorIntentos.java` |
| Hash de contraseñas | `controllocal-service/.../soporte/PasswordHasher.java` |
| Alcance por rol y tenant | `controllocal-service/.../soporte/Alcances.java`, `.../Actor.java` |
| Resolución de tenant | `controllocal-service/.../impl/OrganizacionServiceImpl.java` |
| Gobierno de brokers | `controllocal-service/.../impl/BrokerServiceImpl.java`, `controllocal-web/.../BrokersController.java` |
| Asignaciones (ADMIN) | `controllocal-web/.../AsignacionesController.java` |
| Entidades de identidad | `controllocal-domain/.../persona/{CredencialUsuario,DetalleBroker,DetalleAgente,PersonaRol}.java` |
| Membresía sin uso | `controllocal-domain/.../organizacion/UsuarioOrganizacion.java` |

**Endpoints**: `POST /auth/login` (público) · `GET /salud` (público) · `GET /documentos/contenido`
(público) · 6 de ADMIN (`/brokers` POST/PUT, `/asignaciones/*`) · 18 de BROKER+ADMIN · 3 de BROKER ·
59 de AGENTE · 60 sin gate.

**Tablas y migraciones**: `V1__identidad_party_role.sql` (`persona`, `persona_rol`,
`credencial_usuario`, `detalle_broker` con `es_administrador` y su único parcial, `detalle_agente`,
`supervision_agente`) · `V2__auditoria_universal.sql` (`historial_estado`, `entidad_tipo`) ·
`V3__seed_identidad_base.sql` (las 21 cuentas) · `V6__nucleo_multitenant.sql` (`organizacion_id`,
únicos por tenant, admin por organización, `usuario_organizacion`) ·
`V10__reasignacion_agente_broker.sql`.

**Seeds y scripts**: `V3__seed_identidad_base.sql` (v2) · `database/02_seed_base_data.sql` (v1) ·
13 scripts en `backend-spring/verificacion/`.

**Configuración**: `controllocal-app/src/main/resources/application.yml` ·
`backend-spring/docker-compose.yml` · `backend-java/.../api.properties.example` (gitignorado el real).

**Matriz de roles**: `docs/ai/matriz-operacion-rol.md`, vigilada por
`controllocal-app/src/test/java/com/controllocal/arquitectura/MatrizOperacionRolTest.java`.

**Pruebas**: `TokenServiceTest` · `AutenticacionServiceImplTest` · `PasswordHasherTest` ·
`MatrizOperacionRolTest` · `ArquitecturaTenancyTest` · `frontend-angular/.../acceso.spec.ts`,
`auth.service.spec.ts`, `auth.interceptor.spec.ts`, `rol.guard.spec.ts` ·
`verificacion/e2e-personas.ps1`, `verificacion/e2e-v6.ps1`.

**Frontend (`frontend-angular/`)**: `core/auth/auth.service.ts` (almacenamiento y cierre) ·
`auth.interceptor.ts` (token y 401) · `auth.guard.ts` · `rol.guard.ts` · `acceso.ts` (menú por rol) ·
`features/broker-form`, `features/brokers`, `features/broker-detail`, `features/asignaciones`.
