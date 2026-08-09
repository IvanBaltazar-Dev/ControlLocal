# D-S0-17 — Las 18 operaciones `BROKER, ADMIN`, decididas una por una

**Fecha: 2026-08-04** · **APROBADA TAL CUAL el 2026-08-05** · **Estado: IMPLEMENTADA Y VERIFICADA.**
**Regla que la ordena:** *gobernar no es operar*.

Hasta aquí estas 18 operaciones llevaban el gate `hasRole('BROKER') or hasRole('ADMIN')`, y el ADMIN
entraba a todas **por herencia**: era un broker con un booleano. Al separar `TENANT_ADMIN` de
`BROKER`, cada fila **había que decidirla a propósito**. Este documento es esa decisión, fila por
fila, y **no copia los permisos anteriores**.

> **Aprobada sin cambios el 2026-08-05** y ejecutada en el mismo bloque. Las 8 filas que cambian de
> dueño están en el código, en la matriz operación→rol (que un test compara contra los
> `@PreAuthorize` y rompe el build si divergen) y en `verificacion/e2e-s0-roles.ps1` —**48/48
> comprobaciones en verde**—, que es el escenario A2 del Plan S0 puesto sobre estas filas.
>
> **Alcance de la tanda:** solo `TENANT_ADMIN`. `PLATFORM_ADMIN` sigue sin emitirse: la columna de
> abajo dice `no` en las 18 y su mecanismo —`concesion_acceso_tenant`, con motivo, vigencia ≤ 24 h y
> evento por request— **no entra aquí**. Reservar el valor en el vocabulario no es concederlo.

---

## 1. Criterios de clasificación

| Clase | Definición | Quién debería entrar |
|---|---|---|
| **Operación comercial** | Produce o modifica un hecho del negocio (decidir, cerrar, evaluar, aprobar) | `BROKER` |
| **Supervisión** | Ver el trabajo del equipo para dirigirlo; no produce hechos | `BROKER` + `TENANT_ADMIN` |
| **Gobierno del tenant** | Cuentas, membresías, organigrama, configuración | `TENANT_ADMIN` |
| **Administración de plataforma** | Transversal a organizaciones | `PLATFORM_ADMIN` |
| **Acceso excepcional** | Necesita concesión vigente, motivo y caducidad | `PLATFORM_ADMIN` **con concesión** |

**Regla general para `PLATFORM_ADMIN`, y vale para las 18:** **no entra a ninguna**. Un administrador
de plataforma **no opera información comercial de los tenants**; si lo necesita, pasa por
`concesion_acceso_tenant` (motivo obligatorio, vigencia ≤ 24 h, evento por cada request y
notificación al `TENANT_ADMIN` afectado). Por eso la columna es `no` en todas las filas y la
excepción está en el mecanismo, no en el gate.

---

## 2. La tabla

| # | Operación | Finalidad | BROKER | TENANT_ADMIN | PLATFORM_ADMIN | Justificación |
|---|---|---|---|---|---|---|
| 1 | `GET /captaciones/pendientes` | Bandeja de revisión: las P/O del equipo | **sí** (su equipo) | **sí** (todo el tenant, **solo lectura**) | no | **Supervisión.** Ver la cola no produce ningún hecho; un dueño de organización debe poder ver el atasco sin poder decidirlo |
| 2 | `GET /captaciones/reasignables` | Las ACTIVAS del alcance | **sí** | **sí** | no | **Supervisión.** Es el insumo de la reasignación (fila 6), que sí conserva |
| 3 | `GET /captaciones/propiedades-equipo` | Cartera por inmueble | **sí** | **sí** | no | **Supervisión.** Extensión aditiva de solo lectura |
| 4 | `GET /captaciones/propiedades-equipo/resumen` | KPI de la anterior | **sí** | **sí** | no | Ídem fila 3; separarlas sería incoherente |
| 5 | `POST /captaciones/{id}/decision` | **Aprobar / observar / rechazar** | **sí** | **NO** ⬅ *cambia* | no | **Operación comercial.** Es el juicio profesional sobre un encargo: quién decide si una captación entra a cartera es el broker, no quien administra cuentas. **Hoy el ADMIN puede y dejará de poder** |
| 6 | `POST /captaciones/{id}/reasignar` | Cambiar el agente responsable | **sí** (dentro de su equipo) | **sí** (entre equipos) | no | **Las dos cosas a la vez.** Reasignar dentro del equipo es supervisión; reasignar **entre** equipos es organigrama, y eso es gobierno. Mismo endpoint, **dos alcances distintos** |
| 7 | `POST /captaciones/{id}/cierre` | Cerrar una captación activa | **sí** | **NO** ⬅ *cambia* | no | **Operación comercial.** Cerrar un encargo tiene efecto sobre disponibilidad y cartera |
| 8 | `GET /captaciones/reasignaciones` | Historial auditado de reasignaciones | **sí** | **sí** | no | **Supervisión + rastro.** Es el registro de lo que decide la fila 6, que el admin conserva |
| 9 | `PATCH /solicitudes/{id}/documentos/{idDoc}/revisar` | Observar/conformar **un** documento | **sí** | **NO** ⬅ *cambia* | no | **Operación comercial.** Juicio sobre un expediente. Mantiene el alcance de equipo que cerró D-F4-5 |
| 10 | `PATCH /solicitudes/{id}/documentos/conformar` | Conformidad en bloque | **sí** | **NO** ⬅ *cambia* | no | Ídem fila 9; separarlas abriría el hueco que D-F4-5 cerró |
| 11 | `GET /evaluaciones` | Listado de evaluaciones firmadas | **sí** (las suyas) | **sí** (el tenant, lectura) | no | **Supervisión.** Auditar qué se aprobó es exactamente lo que un administrador debe poder hacer |
| 12 | `GET /evaluaciones/{id}` | Detalle de una evaluación | **sí** | **sí** | no | Ídem fila 11 |
| 13 | `POST /evaluaciones` | **Aprobar / rechazar / observar** una solicitud | **sí** | **NO** ⬅ *cambia* | no | **Operación comercial, y la más sensible de las 18.** Es la decisión que desemboca en contrato y comisión. Firmarla es responsabilidad profesional del broker |
| 14 | `GET /agentes` | Catálogo de agentes | **sí** (supervisados) | **sí** (tenant) | no | **Supervisión + gobierno.** El broker necesita su equipo; el admin, el padrón |
| 15 | `GET /agentes/resumen` | Cubos y zonas del catálogo | **sí** | **sí** | no | Ídem fila 14 |
| 16 | `GET /agentes/{id}` | Ficha del agente (incluye **dinero**) | **sí** (su equipo) | **sí** (tenant) | no | **Supervisión.** Ojo: expone las cuatro magnitudes de comisión. El admin las ve porque gobierna la organización; el broker, solo de su equipo (403 fuera) |
| 17 | `POST /agentes` | **Alta de agente** (persona + credencial + supervisión) | **NO** ⬅ *cambia* | **sí** | no | **Gobierno del tenant** y aplicación directa de **D-S0-18**: un broker no crea cuentas. ⚠️ **Tiene consecuencia técnica**, ver §3.1 |
| 18 | `PUT /agentes/{id}` | Edición de agente | **NO** ⬅ *cambia* | **sí** | no | **Gobierno.** Hoy el PUT ya descarta en silencio documento, usuario, contraseña y código: lo que queda editable es identidad administrativa |

**Resultado:** de 18 filas, **8 cambian**. Seis dejan de ser alcanzables por el administrador
(5, 7, 9, 10, 13 y la mitad de la 6) y dos dejan de serlo por el broker (17, 18).

---

## 3. Consecuencias que hay que aceptar antes de aprobar

### 3.1 La fila 17 obliga a cambiar el alta de agente (no es solo un gate) — ✅ **HECHO**

`POST /agentes` **creaba la supervisión inicial con el broker de la sesión**. Y era peor de lo
documentado: `AgenteServiceImpl` **rechazaba explícitamente** al administrador con una
`ReglaNegocioException` («*el broker administrador no registra agentes operativos*»), así que la
fila 17 no era "un gate que admite a alguien que no puede completar la operación" sino **una regla
de negocio que había que invertir**.

Al pasar el alta a `TENANT_ADMIN`, **el broker supervisor viaja en el request**: campo nuevo
`idBrokerSupervisor` en `AgenteRequest`, aditivo (ningún consumidor lo enviaba) y **obligatorio**,
porque quien gobierna no supervisa a nadie de quien deducirlo. Se validó que sin él el alta responde
400 y que con él el agente queda bajo el broker indicado.

**La fila 6 escondía el mismo problema, y también se resolvió.** Reasignar lo conservan los dos
roles, pero el evento `reasignacion_captacion` exigía `id_rol_broker NOT NULL` contra
`detalle_broker`: un administrador **sin** detalle de broker —justo lo que este bloque hace
posible— moría con *"Broker no encontrado"*. **V35** hace la columna opcional y añade
`id_persona_actor` + `tipo_rol_actor`, de modo que el rastro dice quién reasignó y con qué banda.
El contrato no cambia de forma: `idBroker` sigue saliendo cuando hay broker detrás y el JSON omite
nulos.

### 3.2 Un `TENANT_ADMIN` que además opera necesita dos roles

Si en una corredora pequeña la misma persona gobierna **y** capta, tendrá **dos membresías y dos
`persona_rol`** explícitos, y la auditoría dirá cuál usó. Lo que desaparece es el atajo actual: ser
administrador **ya no regala** las capacidades de broker.

### 3.3 Hay que revisar el SPA, no solo el backend

`core/auth/acceso.ts` deriva el menú de esta matriz. Con los cambios, el menú del `TENANT_ADMIN`
pierde bandeja de revisión, evaluaciones y cierre de captación, y gana la administración de agentes.
**`acceso.spec.ts` fija los menús por rol, así que los tests dirán si algo quedó a medias.**

### 3.4 El gate automático avisa, pero no decide

`MatrizOperacionRolTest` compara `matriz-operacion-rol.md` contra los `@PreAuthorize` y **rompe el
build** si divergen. Eso garantiza que la tabla no se quede vieja; **no** garantiza que la decisión
sea correcta. Por eso esta aprobación es funcional y previa.

---

## 4. Qué se tocó (2026-08-05)

Fueron **26 operaciones**, no 18: a las compartidas se suman las 8 que ya eran `ADMIN` puro
(`/asignaciones` ×4, `/accesos` ×2, `POST` y `PUT /brokers`), que pasan a `TENANT_ADMIN` por el
mismo motivo.

| Pieza | Cambio |
|---|---|
| **V32** | `persona_rol` admite el tipo `ADMIN`; índice `(organizacion_id, rol)`. *El plan pedía además ampliar un `CHECK` de `usuario_organizacion.rol` que **no existía**: V6 solo restringía `estado`* |
| **V33** | Reconstruye las bandas de `usuario_organizacion` y crea el `persona_rol` de gobierno. **Repara un backfill roto de V6** (§4.1) |
| **V34** | Invariante "≥ 1 `TENANT_ADMIN` activo" como `CONSTRAINT TRIGGER … DEFERRABLE INITIALLY DEFERRED` |
| **V35** | Actor de la reasignación: `id_rol_broker` opcional + `id_persona_actor` y `tipo_rol_actor` (§3.1) |
| `@PreAuthorize` de 26 operaciones | `BROKER, ADMIN` → `BROKER` / `BROKER, TENANT_ADMIN` / `TENANT_ADMIN` según la tabla |
| `AutenticacionServiceImpl` | La banda sale de la **membresía**, no de `detalle_broker.es_administrador` |
| `FiltroAutenticacionJwt` + `EstadoDeAcceso` | Publica la **authority efectiva** por petición, sin una consulta extra: es una columna más de la lectura que ya hacía |
| `Actor` | `rolEfectivo`, `esTenantAdmin()`, y `tipoRolOperativo()` **deja de traducir `ADMIN → BROKER`** (H-09) |
| `Alcances` | `global = true` pasa a depender de `TENANT_ADMIN` |
| `UsuariosInternos` | Toda alta de usuario interno **crea su membresía**: la tabla dejó de ser una que nadie mantiene |
| `AgenteRequest` + `AgenteServiceImpl` | Broker supervisor explícito y obligatorio (§3.1) |
| `GET /sesion` | Endpoint aditivo: publica la banda efectiva, que no cabe en el token congelado (R3) |
| `matriz-operacion-rol.md` | Vocabulario nuevo + las 26 filas + la fila de `/sesion`. `MatrizOperacionRolTest` **4/4** |
| SPA (`sesion.model.ts`, `acceso.ts`, `app.routes.ts`, 10 pantallas) | Banda efectiva leída de `GET /sesion`; menú, rutas y botones por rol. **499/499** |
| `verificacion/e2e-s0-roles.ps1` | Escenario A2 sobre estas filas. **48/48** |

### 4.1 Hallazgo: el backfill de V6 nunca funcionó

V6 pobló `usuario_organizacion` uniendo `detalle_broker.id_persona_rol` con
`credencial_usuario.id_persona_rol`. Son filas **distintas por construcción** —
`ck_credencial_tipo_rol` fuerza `USUARIO_INTERNO`, `ck_detalle_broker_tipo_rol` fuerza `BROKER`, y
`uq_persona_rol_id_tipo` impide que un id sea las dos cosas—, así que el `LEFT JOIN` estaba siempre
vacío y el `CASE` caía siempre al `ELSE`: **las 21 cuentas quedaron como `AGENTE`**, administrador y
brokers incluidos.

Nadie lo detectó porque **nadie leía la tabla** (H-14), que es exactamente el riesgo de una fuente de
verdad que no se usa. Comprobado contra la base real antes de escribir V33: 21 filas, las 21
`AGENTE`, frente a 6 brokers reales de los que 1 era administrador. V33 reconstruye las tres bandas
desde el rol operativo vigente de la misma persona, y el resultado (15 + 5 + 1) lo fija la suite.
