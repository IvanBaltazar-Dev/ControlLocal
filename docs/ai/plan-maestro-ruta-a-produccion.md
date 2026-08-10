# Plan maestro — ruta a producción (7 fases)

> **HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL.**
> Describe el mundo de la migración: v1 sobre GlassFish, SPA Blazor, contrato
> congelado y corte del legado. Ese stack se borró el 2026-08-08 y el contrato se
> descongeló el 2026-08-09. Se conserva porque explica **por qué** las cosas son
> como son, no **qué** hacer ahora.
>
> El orden vigente sale solo de `mapa-ejecucion-brox.md` (dónde estamos) y
> `checklist-captura-moat-e-inteligencia-inmobiliaria.md` (qué falta para cerrar
> la etapa).

**Fecha de corte: 2026-08-04.** Este documento **apunta el plan acordado** de siete fases y, para
cada punto, dice **si ya está hecho, si está planificado sin ejecutar, si está pendiente o si es
futuro**, con la evidencia en el repositorio.

- ¿Qué falta de la *migración* (backend/frontend)? → `checklist-migracion.md`
- ¿Foto legible del proyecto? → `mapa-estado-y-pendientes.md`
- ¿Detalle de una fase de seguridad? → `plan-s0-seguridad-identidad-gobierno.md`
- ¿Diseño de MFA y recuperación de emergencia (Bloque 6)? → `plan-s0-6-mfa-y-break-glass.md`
- ¿Inventario de tecnologías, costes y alcance de E5? → `informe-tecnologias-dependencias-y-alcance-e5.md`

Este documento es **el índice de orden superior**: ordena las siete fases y las cruza con lo que ya
está cerrado. No sustituye a ninguno de los anteriores; los enlaza.

---

## 0. Leyenda de etiquetas

| Etiqueta | Significa |
|---|---|
| ✅ **HECHO** | Implementado **y verificado** (test, script E2E o gate que rompe el build) |
| 🟡 **PARCIAL** | Una parte está cerrada y otra no; la fila dice exactamente cuál es cada una |
| 📋 **PLANIFICADO** | Diseñado y escrito en un documento aprobado o en propuesta; **cero código** |
| ⬜ **PENDIENTE** | Identificado y diagnosticado, **sin plan de ejecución todavía** |
| 🔮 **FUTURO** | Deliberadamente después del corte del legado; no se toca antes |
| 🔒 **BLOQUEADO** | No depende de trabajo, sino de una decisión o de otra fase |

> **La regla que sigue mandando sobre todo lo demás:** mientras GlassFish siga vivo, el **contrato
> REST está congelado**. Eso parte la Fase 3 en dos (§3), y es el punto donde el orden propuesto
> necesita un ajuste.

---

## 1. Las siete fases de un vistazo

| Fase | Título | Estado global | Dónde vive el detalle |
|---|---|---|---|
| **1** | Seguridad e identidad | 🟡 **EN EJECUCIÓN** — contención, sesiones, auditoría, bloqueo, contraseñas **y gobierno de roles** cerrados; falta MFA y break-glass | `plan-s0-seguridad-identidad-gobierno.md` (= "S0") |
| **2** | Protección operativa mínima | ⬜ **PENDIENTE** (3 ítems son horas de trabajo) | `informe-…-alcance-e5.md` §1, §8.2 |
| **3** | Funcionalidades y reglas de negocio | 🟡 **PARCIAL** — hay que partirla en dos | `diagnostico-estados-valores-económicos…`, `checklist-migracion.md` §1 |
| **4** | Corte definitivo del legado | 📋 **PLANIFICADO** (E5 con alcance ya corregido) | `informe-…-alcance-e5.md` §6, `checklist-migracion.md` §4 |
| **5** | Arquitectura productiva | ⬜ **PENDIENTE** — bloqueada por una decisión | `informe-…-alcance-e5.md` §1.3, §8.1 |
| **6** | Multiempresa definitivo | 🟡 **PARCIAL** — el núcleo ya está | `arquitectura-multitenancy-colaboracion.md`, `plan-migracion-v6-tenancy.md` |
| **7** | WhatsApp, KAIROS e IA | 🔮 **FUTURO** — diseñado, nada construido | `diseno-contacto-comercial-ai-ready.md` |

**Punto de partida real (actualizado al 2026-08-05, todo verificado):** backend Spring **cerrado**
(27 recursos, ~155 operaciones en matriz con test de cobertura), **31 migraciones** aplicadas, SPA
Angular en **49 de ~52 pantallas**, **16 scripts E2E** contra PostgreSQL real —**951 comprobaciones
en verde de una sola invocación**— y **4 gates que rompen el build** (capas, transiciones auditadas,
tenancy y matriz operación→rol). Cero dependencias de pago, cero servicios externos.

**Lo que ha cambiado de sitio:** la deuda ya no es "de operación y de identidad" a partes iguales.
Persistencia, contención, sesiones, auditoría, bloqueo y contraseñas están cerrados; **lo que queda
de identidad es gobierno de roles, y no depende de trabajo sino de una decisión** (D-S0-17).

---

## 1 bis. Orden de ejecución vinculante — BLOQUES 0 a 11

**Decidido el 2026-08-04.** Las siete fases dicen *qué*; esta tabla dice **en qué orden se ejecuta**
y es la que manda. Cada bloque deja el sistema entero y desplegable.

| Bloque | Contenido | Fase | Estado | Puerta de entrada |
|---|---|---|---|---|
| **0** | **Autorización de datos personales** — una sola vez, en el alta (**D-27**) | 3 | ✅ **CERRADO** (2026-08-05): altas de cliente y propietario, página pública, canal retirado de la pantalla y **constancia visible en las dos fichas** | — |
| **1** | **Persistencia y recuperación**: volumen del almacén, backups, restauración verificada, guía operativa | 2 | ✅ **CERRADO** (2026-08-04) — `backend-spring/operacion/` (`respaldo.ps1`, `restaurar-verificar.ps1`, `EVIDENCIA.md`) | — |
| **2** | **Contención S0.1**: arranque fallido, perfiles `dev`/`test`/`prod`, seeds fuera de producción | 1 | ✅ **CERRADO** (2026-08-04) — `ComprobacionVariablesObligatorias` + `ValidadorConfiguracionSeguridad` (9 comprobaciones), `application-{dev,test,prod}.yml` | Bloque 1 cerrado |
| **3** | **Seguridad de sesiones, auditoría y bloqueo de accesos**: `sesiones_invalidas_desde`, logout real, `evento_seguridad`, bloqueo por cuenta e IP | 1 | ✅ **CERRADO** (2026-08-05) — V29 + V30, las 4 entregas verificadas; regresión completa en verde (tabla en `backend-spring/README.md`) | Bloque 2 |
| **4** | **Contraseñas y recuperación**: cambio real, temporales, invitación, token de un solo uso, **+ las 2 pantallas Angular** | 1 | ✅ **CERRADO** (2026-08-05) — V31, `s0-contrasenas` 59/59 y regresión de 16 suites (951 comprobaciones, 0 fallos). Cierra H-02 y H-08 | Bloque 3 |
| **5** | **Roles y gobierno**: `TENANT_ADMIN`, membresías | 1, 6 | ✅ **CERRADO (2026-08-05)** — matriz D-S0-17 **aprobada tal cual** y ejecutada: V32–V35, 26 operaciones regateadas, `usuario_organizacion` como fuente de verdad, `e2e-s0-roles` **48/48**. `PLATFORM_ADMIN` queda **fuera** por decisión de alcance: su mecanismo es la concesión temporal, no un gate | Matriz aprobada |
| **6** | **MFA administrativo** + recuperación de emergencia | 1 | ✅ **CERRADO (2026-08-06)** — `plan-s0-6-mfa-y-break-glass.md`. **V37 y V38 hechas y verificadas**: `e2e-s0-mfa` **89/89** y `e2e-s0-emergencia` **30/30**. **No hay cuenta break-glass ni se emite `PLATFORM_ADMIN`**: la emergencia es una **concesión técnica** (30 min, doble aprobación estructural, tres identidades registradas, un tenant, una acción por tipo, sin datos comerciales). 21 decisiones (D-S0-22…37 y D-S0-44…53). **Pendiente de ACTIVACIÓN, no del bloque**: designar los dos custodios reales y el canal externo — sin ellos `prod` no arranca con la bandera encendida | Bloques 1 y 4 verificados |
| **7** | **Reglas de negocio aditivas**: ciclo legal del contrato, disponibilidad, comisión, estados sin productor | 3 | ✅ **CERRADO (2026-08-08)**. El **ciclo contractual** se cerró el 2026-08-06 (`f4-solicitud` **127/127**): finalizar y rescindir fallaban con 409 —la tarea de revisión usaba un `entidad_tipo` fuera del `CHECK`—, repetir una transición terminal respondía 200 sin hacer nada, y rescindir/anular pasaron de AGENTE a BROKER. **La disponibilidad NO se libera sola**: queda tarea de revisión humana, y es la decisión, no una carencia. Los dos puntos que figuraban pendientes se revisaron el **2026-08-08** y resultaron estarlo solo en el papel: **la comisión ya incluye el pago al agente** (`ComisionMovimiento.PAGO_AGENTE` con saldo propio y endpoint en la matriz) y H-07 está resuelto por construcción; y los **estados sin productor** quedan cerrados por **D-B7-1** (`decision-estados-sin-productor.md`), que corrige la lista —4 de 10 filas ya no eran ciertas—, decide los seis restantes y deja `EstadosSinProductorTest` vigilándolos. **Lo único que se traslada, con dueño**: solicitud `D` Desistida (ítem accionable nº 1, aditivo) y prospección `E` / oportunidad `X`, que exigen descongelar el contrato → Bloque B | — (no rompe el contrato) |
| **8** | **Almacenamiento S3 compatible + retirada del legado** | 4 | 🟡 **CASI CERRADO (2026-08-08)**: `AlmacenS3` construido y verificado en vivo, conciliación y migración de binarios hechas, y el **legado eliminado del repositorio** — sin backfill, porque no había datos que migrar. **Queda descongelar el contrato** (4.7 y 4.8), que es lo que el borrado desbloquea | Bloques 1–7 |
| **9** | **Arquitectura productiva inicial** — **una sola instancia** | 5 | ⬜ Pendiente | Bloque 8 |
| **10** | **Multiempresa definitiva** + RLS | 6 | 🟡 Núcleo hecho | Bloque 8 |
| **11** | **KAIROS, WhatsApp e IA** | 7 | 🔮 **No iniciar** | Todo lo anterior |

> **Aviso de vocabulario (2026-08-05).** El Bloque 3 se llamaba *"Revocación, auditoría y bloqueo"*
> y se renombró para quitar una ambigüedad real: **"revocación" ahí significa siempre *invalidar
> sesiones de usuario***, nunca la autorización de datos personales. La autorización de D-27 queda
> cerrada como **constancia única registrada durante el alta** y **no tendrá flujo de revocación** —
> ni pantalla, ni endpoint, ni procedimiento.

> **Tres puertas, y ninguna se salta:**
> 1. ✅ **Abierta el 2026-08-05**: la matriz `matriz-d-s0-17-operaciones-broker-admin.md` fue **aprobada tal cual** y el Bloque 5 se ejecutó con ella delante.
> 2. **No se inicia MFA** hasta que almacenamiento y recuperación estén **verificados** (no
>    "configurados": verificados, con la prueba corrida).
> 3. **No se inicia IA, WhatsApp ni varias instancias** durante estos bloques.

---

## 1 ter. BLOQUE 0 — Autorización de datos personales (D-27)

**Decisión completa: [`decision-autorizacion-datos-personales.md`](decision-autorizacion-datos-personales.md).**
Resumen de lo que fija, porque cambia pantallas y modelo:

- **Una sola casilla** en el alta de cliente/propietario, más un **enlace al aviso**. Dos elementos y
  ni uno más. El agente **no teclea ni elige** canal, fechas, versiones legales ni finalidades
  técnicas: todo eso lo pone el sistema. *(El desplegable de canal existió y se retiró el 2026-08-05:
  pedía describir la pantalla en la que el agente ya estaba. El canal se sigue registrando —la
  columna es `NOT NULL`— con el valor técnico `FORMULARIO_BROX`, sellado por el backend.)*
- **Sin autorización no hay alta.** No se crea la persona, ni el rol, ni una fila marcada como "no
  autorizó". Solo queda un **evento técnico anónimo**. Es una **divergencia deliberada** de la v1
  (que sí creaba la persona con el booleano en `false`) y **no afecta al Blazor**, que habla con
  GlassFish.
- **El alta es transaccional**: `persona + rol + contacto + autorización`, o no se persiste nada.
- **Se reutiliza el modelo de V6, y esa decisión está CERRADA** (confirmada el 2026-08-05, D-27 §3.2).
  Las cuatro tablas de consentimiento ya existían aplicadas y no las usaba nadie; **V28 no crea
  ninguna tabla**: solo añade dos columnas (`registrada_por`, `motivo_revocacion`), la bandera
  `cambio_material` y ajusta el catálogo de finalidades. No hay ninguna alternativa abierta ni
  ninguna tabla paralela pendiente de decidir.
- **IA**: solo datos operativos autorizados **dentro del tenant**; analítica transversal **agregada o
  disociada**; **ningún entrenamiento** con conversaciones, documentos o fichas identificables.

---

## 2. Fase 1 — Seguridad e identidad

**Estado: 🟡 EN EJECUCIÓN — 8 de 12 puntos cerrados (2026-08-05).** El diagnóstico está **aceptado**
(`diagnostico-autenticacion-y-gobierno-de-accesos.md`, 19 hallazgos H-01…H-19), el plan de ejecución
(`plan-s0-seguridad-identidad-gobierno.md`, "S0") está aprobado y **S0.1 y S0.2 están hechos y
verificados**: hay perfiles `dev`/`test`/`prod`, arranque fallido ante configuración insegura y
**migraciones hasta V30** aplicadas.

**Lo que queda ya no está bloqueado por ninguna decisión:** 1.1 y 1.2 se cerraron con el Bloque 5;
lo que falta es MFA (1.3), break-glass (1.11), el hash progresivo (1.7-1.8) y las sesiones
definitivas (1.12), que son post-corte.

| # | Punto del plan | Estado | Evidencia / dónde se ejecuta |
|---|---|---|---|
| 1.1 | Separar `AGENTE` / `BROKER` / `TENANT_ADMIN` | ✅ **HECHO y verificado** (Bloque 5, 2026-08-05) | Hoy el admin **es un booleano** (`detalle_broker.es_administrador`) y no existe figura sobre el tenant (H-19). Exige `persona_rol` tipo `ADMIN` (V29) + backfill (V31). **Restricción R1**: el token solo admite 3 roles hasta el corte → la banda real se resuelve en servidor |
| 1.2 | Cuenta operativa y administrativa separadas | ✅ **HECHO y verificado** (Bloque 5, 2026-08-05) | Regla *"gobernar no es operar"* (D-S0-7). D-S0-17 aprobada tal cual: de 18 filas compartidas, **8 cambian de dueño** — el admin pierde decidir, cerrar, conformar y evaluar; el broker pierde crear y editar agentes. Más las 8 ya `ADMIN` puro, **26 operaciones** regateadas |
| 1.3 | MFA obligatorio para cuentas administrativas | ✅ **BLOQUE 6 CERRADO (2026-08-06)**. Cerrado y verificado: enrolamiento TOTP · QR y clave manual · confirmación y códigos de respaldo · login en dos pasos · **anti-replay del primer OTP** (D-S0-44) · bloqueo e invalidación de sesiones · SPA de enrolamiento · **códigos de error estables** (D-S0-45). `e2e-s0-mfa` **68/68**. **reautenticación reforzada** (regenerar códigos · reemplazar autenticador) y **recuperación de nivel 2** (padrón de gobierno, revocación con motivo y elevación, aviso persistente). `e2e-s0-mfa` **89/89**. **Documentación de custodios CERRADA** y **V38 construida** (2026-08-06): concesión de un solo uso con doble aprobación estructural, tres identidades en la fila, custodios en configuración, conector local, consumo atómico y cierre automático; apagada por defecto y con arranque bloqueado en `prod` sin hashes reales ni canal externo. Simulacro completo verde **por el cable** (`e2e-s0-emergencia` 30/30). **Lo pendiente es de ACTIVACIÓN, no del bloque** (D-S0-53): designar los dos custodios reales y construir el canal externo; sin ellos `prod` no arranca con la bandera encendida | TOTP RFC 6238, sin proveedor externo. El login está congelado → endpoint nuevo `POST /auth/login-mfa`. **Requiere D-S0-19** |
| 1.4 | Cambio de contraseña | ✅ **HECHO y verificado** (Bloque 4, 2026-08-05) | `POST /perfil/contrasena` aditivo. Exige la contraseña **actual** e invalida **todas** las sesiones, incluida la que llama. H-02 cerrado, y con él la pantalla Angular que dependía de él |
| 1.5 | Restablecimiento administrativo seguro | ✅ **HECHO y verificado** (Bloque 4, 2026-08-05) | `POST /accesos/{idPersona}/invitacion` con token de un solo uso: **el administrador nunca ve ni fija la clave ajena**, la define el titular al canjear. D-S0-18 aplicada — un BROKER **no** invita, ni a su equipo. Hoy lo ejerce ADMIN; pasa a `TENANT_ADMIN` en el Bloque 5 sin tocar esquema ni contrato |
| 1.6 | Contraseñas temporales con cambio obligatorio | ✅ **HECHO y verificado** (Bloque 4, 2026-08-05) | `debe_cambiar_contrasena` (V31) + sesión **capada**: solo pasan `GET /perfil`, `POST /perfil/contrasena` y el logout —encerrar a alguien sin salida sería un fallo—. El resto es 403 con `codigo: CAMBIO_CONTRASENA_REQUERIDO`. La temporal **la genera el sistema**, no la elige quien la pide |
| 1.7 | Eliminar contraseñas conocidas y compartidas | 📋 S0.1 §1.4–1.5 | H-03/H-16: **21 cuentas con 3 hashes**, sembradas por V3 (migración versionada). V28 en location `prod` las neutraliza **sin tocar V3** y sin desactivar `admin@controllocal.test` |
| 1.8 | Secreto de sesión obligatorio, sin fallback inseguro | 📋 S0.1 §1.1–1.3 | **H-01, la crítica**: hoy cae en silencio a un literal del código, **el mismo en los dos backends**. Hay que blindar **también `backend-java`** o se firma por el otro lado (R4). **Requiere D-S0-20** (aceptar que un despliegue mal configurado no arranque) |
| 1.9 | Auditoría de accesos y acciones administrativas | ✅ **HECHO y verificado** (Bloque 3, 2026-08-05) | `evento_seguridad` **append-only** (V30) con los 22 tipos en el `CHECK` y **un único punto de escritura** (`EventosSeguridad`, transacción propia por evento). H-06 cerrado: login, fallo, bloqueo, logout y sesiones invalidadas dejan rastro, y la higiene de secretos está verificada por test y por E2E. **Retirar `UPDATE`/`DELETE` al usuario de la aplicación queda para el Bloque 9** |
| 1.10 | Bloqueo por intentos fallidos **por cuenta y por IP** | ✅ **HECHO y verificado** (Bloque 3, 2026-08-05) | `intento_acceso` en PostgreSQL (V30) + `BloqueoAccesos`: cuenta e IP, ventana deslizante de 15 min, escalado 5/10/15/20 y **hash SHA-256** del identificador (la tabla no es un padrón de cuentas probadas). `IpDelCliente` solo cree `X-Forwarded-For` si la conexión viene de un proxy declarado. **El `LimitadorIntentos` en memoria se retiró del árbol.** Sin Redis y sin dependencias nuevas. H-07 cerrado |
| 1.11 | Recuperación de emergencia | 📋 **DISEÑADO en detalle (2026-08-06)**, sin implementar. **Deja de ser break-glass**: concesión técnica temporal, no una cuenta (D-S0-26 rediseñada) | Cuenta `PLATFORM_ADMIN` inactiva en operación normal, activación fuera del producto, evento de severidad máxima y caducidad automática (D-S0-15). Hoy: **H-04, sin salida salvo SQL** |
| 1.12 | Sesiones administrativas cortas y diferenciadas | 🟡 **PARCIAL / 🔮 el resto** | *Ya aplicable*: revocación por `sesiones_invalidas_desde` comparada contra el `iat` que **el token ya lleva** (D-S0-12) y `POST /auth/logout` real. *Post-corte*: refresh en cookie `HttpOnly`, 2 h para gobierno y `sudo mode` (S0.7). **Aviso escrito: al pasar a cookies hay que reactivar CSRF** |

**Cómo se ordena dentro de la fase** (§8 del Plan S0, por riesgo retirado): `S0.1 contención` →
`S0.2 revocación + auditoría` → `S0.3 contraseñas` → `S0.4 identidad y gobierno` →
`S0.5 administración segura` → `S0.6 hash progresivo` → `S0.7 sesiones definitivas` (post-corte).
**Gobierno va después de contraseñas a propósito**: repartir administradores sin poder darles clave
propia obliga a compartir la del seed.

**Gate de la fase:** los 9 escenarios A1–A9 del §7 del Plan S0, con su suite nueva
`verificacion/e2e-s0-seguridad.ps1`, más la regresión de `e2e-personas.ps1` y `e2e-v6.ps1`.

---

## 3. Fase 2 — Protección operativa mínima

**Estado: ⬜ PENDIENTE.** Y es la fase con mejor relación riesgo-retirado / esfuerzo del documento:
los tres primeros ítems son **horas**, no semanas.

| # | Punto del plan | Estado | Evidencia |
|---|---|---|---|
| 2.1 | Copias automáticas de PostgreSQL | ⬜ **No existe ninguna** | Volumen `controllocal_pg_data` sin política ni verificación. **Es el único riesgo del inventario cuyo daño es irreversible** |
| 2.2 | Prueba real de restauración | ⬜ No existe | Sin ella, "hay backup" es una creencia, no un hecho |
| 2.3 | Volumen persistente para archivos | ⬜ **No existe — comprobado** | `docker-compose.yml` monta volumen para Postgres **pero no para `./almacen-dev`**: los binarios viven en la capa de escritura del contenedor y **se pierden al recrearlo**. Es una línea de compose |
| 2.4 | Separar `dev` / `test` / futura `prod` | 🟡 PARCIAL | **`test` ya es sofisticado**: `Invoke-E2E.ps1` levanta entorno efímero por suite (proyecto Compose propio, base `controllocal_e2e_<runId>`, `down -v` en `finally`). **No hay perfiles Spring** (los introduce S0.1) y **no existen staging ni producción** |
| 2.5 | CI (backend, Angular, integración y arquitectura) | ⬜ **`.github/workflows/` existe y está vacío** | Los ingredientes ya están: reactor + SPA + 13 scripts + 4 gates estructurales. Falta la automatización. **Trampa a codificar en el pipeline:** los 16 tests de integración **se saltan en silencio** sin `TEST_DB_URL` |
| 2.6 | Variables secretas fuera del repositorio | 🟡 PARCIAL | `.gitignore` cubre `.env`, `*.properties` sensibles y `appsettings.Local.json`. Pero queda **fallback en código** (H-01) y `POSTGRES_PASSWORD: controllocal` en el compose (H-17). Sin bóveda ni rotación. Opciones OSS comparadas: SOPS+age, OpenBao, Docker secrets |

**Ajuste recomendado al orden:** 2.1, 2.2 y 2.3 **van antes que S0.1**, o en paralelo. La razón está
en el propio encargo: *no tiene sentido construir MFA si un error de servidor puede borrar
documentos*. Hoy ese escenario no es hipotético — **basta un `docker compose down` sin `-v` mal
hecho, o un recreate del contenedor, para perder el almacén entero**.

---

## 4. Fase 3 — Funcionalidades y reglas de negocio

**Estado: 🟡 PARCIAL — el Bloque A está CERRADO (2026-08-08), queda el B.** Los diez puntos no son
del mismo tipo: unos son
**funcionalidad que no existe en ninguno de los dos sistemas** (aditiva, se puede construir ya) y
otros son **bugs de la v1 replicados a propósito** (tocarlos rompe el contrato congelado, y por eso
el checklist los pone en el paso 8, después del corte).

### 4.1 — Bloque A: aditivo, **ejecutable durante la convivencia** — ✅ **CERRADO (2026-08-08)**

| # | Punto | Estado | Nota |
|---|---|---|---|
| 3.1 | Contratos: finalización, rescisión, anulación, renovación | ✅ **HECHO (2026-08-06, Bloque 7)** | Los siete estados `P/D/V/R/F/S/A` tienen productor: nacimiento en `EN_PROCESO` y transiciones a firmado, vigente, renovado, finalizado, rescindido y anulado. `f4-solicitud` **127/127**. Verificado de nuevo el 2026-08-08 en `decision-estados-sin-productor.md` §1 |
| 3.2 | Recuperación de la disponibilidad del inmueble | ✅ **HECHO (2026-08-06, Bloque 7)** con decisión explícita | La decisión #6 se resolvió así: **la disponibilidad NO se libera sola**. Terminar un contrato crea una **tarea de revisión humana**, porque reabrir el local automáticamente lo publicaría sin que nadie confirme que está libre. "Reservado" **no** se añade como estado |
| 3.3 | Comisión: generada / cobrada / reparto / **pago al agente** | ✅ **HECHO** | ✅ V15–V20 separaron condición económica, movimientos de comisión y auditoría de disponibilidad. ✅ **El pago al agente está construido** (verificado 2026-08-08): `ComisionMovimiento.PAGO_AGENTE`, saldo propio con validación de que un pago no supere lo pendiente, y `POST /contratos/{id}/comision/movimientos` en la matriz. El estado `PARCIAL` lo calcula el saldo. ✅ **H-07 resuelto por construcción**: "por liquidar" es un **conteo** (`long`) y la pantalla no mezcla conteos con importes — los tres KPI son conteos y cada uno lleva su pie aclaratorio |
| 3.4 | Monedas: eliminar contradicciones PEN/USD | ✅ **HECHO en el modelo** / 🔒 resto congelado | V15–V17 hacen `PEN`/`USD` **obligatorias** donde hay importe, la comisión **hereda la moneda de la renta final** y no quedan constantes USD/PEN en Angular. Lo que sigue vivo es la **réplica del cable v1** (comisión forzada a USD), que solo se retira al descongelar el contrato |
| 3.9 | Estados existentes sin operación productora | ✅ **CERRADO (2026-08-08)** — **D-B7-1** | `decision-estados-sin-productor.md`. La lista de §2.1 del diagnóstico **estaba desactualizada**: 4 de sus 10 filas ya tenían productor (contrato `P/R/F/S/A`, comisión `PARCIAL`, evaluación `P`, resultado `S`). **Quedan seis, todos decididos**: prospección `E` y oportunidad `X` esperan al descongelado; captación `V` y tarea `V` necesitan planificador (Bloque 9); tarea `E` y alerta `D` se descartan por falta de caso de uso; **solicitud `D` es el único implementable hoy** y queda como ítem accionable nº 1. El requisito vinculante —que la UI no prometa acciones inexistentes— **ya se cumplía**: los desplegables de acción usan catálogos restringidos (`ESTADO_CONTRATO_AL_CERRAR`). Vigilado por `EstadosSinProductorTest` |
| 3.10 | Flujo de cierre completo y auditado | ✅ **HECHO** | ✅ La cascada de F4 cierra de punta a punta (contrato + comisión + oportunidad exitosa + solicitud y captación cerradas + local no disponible), verificada efecto por efecto, y **V27** añadió la atribución histórica del cierre. ✅ Lo que faltaba era 3.1 y 3.2, cerrados en el Bloque 7 (2026-08-06) |

### 4.2 — Bloque B: bugs replicados — ✅ **CERRADO (2026-08-08)**

> Los cinco puntos están corregidos y verificados. El contrato quedó descongelado y con él cayeron
> cuatro bugs de indicadores, el corte silencioso de la bandeja, dos vías de subida muertas, el
> aviso que no llegaba y el endpoint público sin autenticación (H-12). E2E: `f4-solicitud` 125/125,
> `e4-dashboard` 120/120 y `f6-f7-alertas-tareas` 68/68.


| # | Punto | Estado | Nota |
|---|---|---|---|
| 3.5 | Alerta de captación que casi nunca se genera | ✅ **CORREGIDO (2026-08-08)** | `ProspeccionServiceImpl.captar` construía la captación a mano en vez de pasar por `CaptacionServiceImpl.registrar`, que es donde vive el aviso. Como captar desde una prospección es el camino **normal**, el broker prácticamente nunca se enteraba: la captación quedaba `PENDIENTE_REVISION` sin que nadie lo supiera. Ahora emite el **mismo** tipo y severidad que el otro camino (para quien la recibe es el mismo hecho) con un texto propio del origen. Verificado por el cable: `f6-f7-alertas-tareas` **68/68**, con el check que afirmaba el hueco reescrito |
| 3.6 | Embudo e indicadores: conteos y periodos inconsistentes | ✅ **LOS CUATRO CORREGIDOS (2026-08-08)** | ✅ El `100 %` fijo ahora sigue a la base (0 si no hay oportunidades). ✅ *"Visita realizada"* **ya solo cuenta las REALIZADAS** — antes sumaba canceladas e inflaba la conversión justo donde se mide si el equipo trabaja. ✅ `captacionesPendientes` **retirado de la respuesta**: duplicaba `captacionesPorRevisar` y nadie lo pintaba. ✅ **Fallback del operativo retirado**: si la ventana venía vacía se usaba *todo el historial*, así que "últimos 7 días" pasaba a significar "desde siempre" sin avisar, y recontactos vencidos de hace un año salían como si fueran de esta semana |
| 3.7 | Tareas: límite silencioso que oculta tareas | ✅ **CORREGIDO (2026-08-08)** | La bandeja devolvía 10 y descartaba el resto **sin dejar rastro**: se veía igual con 10 tareas que con 40. Ahora devuelve todas, con el mismo orden por prioridad |
| 3.8 | Subidas de archivos duplicadas por el Blazor | ✅ **BORRADAS (2026-08-08)** | Queda **una sola vía**: `POST {id}/documentos/archivo` (octet-stream), la que usa el SPA. Las de JSON con base64 y por trozos existían por un bug del `SocketsHttpHandler` de .NET 10 y murieron con el Blazor. Verificado en vivo: **405 y 404**. Con ellas se fue el búfer en memoria de cargas por partes, que **no liberaba una carga abandonada hasta reiniciar el proceso** |

> **La consecuencia práctica del reparto:** el Bloque A se puede empezar **ya** —son endpoints
> aditivos y estados nuevos, no cambian ninguna respuesta existente—, y el Bloque B se ejecuta
> **dentro de la Fase 4**, en el paso "descongelar el contrato". Meterlos en la misma tanda obliga a
> esperar al corte para todo, sin necesidad.

---

## 5. Fase 4 — Corte definitivo del legado

> ## ⚠️ Esta fase cambió de naturaleza el 2026-08-08
>
> **El stack v1 se ELIMINÓ del repositorio.** No hubo corte: no había nada que cortar. `backend-java`,
> `frontend-csharp` y `database` nunca corrieron en producción y nunca lo iban a hacer, así que **no
> había datos que migrar ni sistema del que desengancharse**. El código queda en el historial de git.
>
> Consecuencias, punto por punto:
>
> - **4.2 (backfill) queda CANCELADO.** Se llegó a diseñar e implementar el bloque de identidad
>   antes de darse cuenta; se retiró entero. Migrar datos de demo a una base que se recrea desde
>   Flyway es trabajo sin destinatario.
> - **4.4, 4.5 y 4.6 (apagar GlassFish, MySQL y Blazor) están HECHOS por borrado**, sin ventana de
>   observación ni conciliación: no había tráfico ni datos que conciliar.
> - **4.1 (paridad) pierde su referencia.** E5 comparaba v2 contra v1; sin v1 no hay con qué
>   comparar. Lo que sobrevive es la pregunta útil —¿el negocio queda cubierto y correcto?— pero ya
>   no como una comparación.
> - **4.7 y 4.8 quedan DESBLOQUEADOS y son lo siguiente.** Es el premio real de esto: el contrato
>   estaba congelado solo para convivir con v1, y esa congelación está obligando a replicar cuatro
>   bugs conocidos, un endpoint público sin autenticación, tres vías de subida y la comisión forzada
>   a USD. Ver §4.2.
>
> **Se acordó hacerlo en dos tandas** (2026-08-08): primero el borrado —mecánico y verificable, y ya
> hecho: 583 pruebas de backend y 529 de Angular siguen verdes— y después el descongelado, bug por
> bug y con la SPA delante. Mezclarlos haría imposible atribuir un fallo.

**Estado: 🟡 el borrado está HECHO; queda el descongelado (4.7 y 4.8).**

| # | Punto | Estado | Nota |
|---|---|---|---|
| 4.1 | Validar paridad real | 📋 **Alcance ya redefinido** | *"Paridad módulo a módulo"* era una definición errónea: **convertía cada mejora en un fallo**. La nueva: **E5 valida que el negocio queda cubierto y correcto**, con **12 ejes** por módulo y **clasificación obligatoria** de cada diferencia en 6 categorías (solo "regresión" abre defecto). Las **12 mejoras que no son regresiones** están declaradas por escrito para que nadie "arregle hacia atrás" |
| 4.2 | Migrar datos definitivos | ❌ **CANCELADO (2026-08-08)** | No hay datos que migrar: la v1 nunca corrió en producción y su contenido era seed de demo. Se diseñó el mapa de ids y se implementó el bloque de identidad antes de verlo; se retiró entero. Ya no bloquea a E5 |
| 4.3 | Resolver almacenamiento compartido | 🟡 **PARCIAL — la preparación ya está hecha** | Opciones OSS comparadas (disco+volumen, NFS, MinIO **AGPL**, SeaweedFS **Apache-2.0**, Garage, Ceph). ✅ **Las dos cosas que convenían adelantar están hechas (2026-08-05)**: las claves nuevas cuelgan de `tenant/{organizacionId}/` y `AlmacenDocumentos` sigue siendo la única frontera. Se hizo antes de migrar para **mover los binarios una sola vez**. ✅ **`AlmacenS3` construido y verificado en vivo (2026-08-08)**: AWS SDK v2, endpoint y `path-style` configurables, selección por `ALMACEN_PROVEEDOR=DISCO\|S3`, MinIO en el compose bajo perfil `s3`, y el arranque de `prod` bloqueado ante bucket ausente, endpoint sin TLS o credenciales del compose. Comprobado por el cable: la foto sube, aterriza en el bucket (`tenant/1/perfiles/…`), vuelve intacta como `image/png` y **no hay disco en ese contenedor**; una clave ausente da 404, no 500. ✅ **Conciliación y migración construidas y verificadas (2026-08-08)**: `InventarioDeClaves` + `MigracionAlmacen`, con modos `conciliar` (solo lectura) y `migrar` (copia idempotente, verificada releyendo, **sin borrar el origen**), códigos de salida 0/2/1 y runbook en `backend-spring/operacion/README.md` §7 bis. Probado contra los datos reales de dev: **encontró 4 referencias rotas del seed** —filas de `documento_solicitud` que apuntan a binarios que nunca existieron— y migró el resto con verificación byte a byte. ⬜ **Queda solo la ejecución real**, que depende de tener el bucket productivo (Bloque 9). **El proveedor por defecto sigue siendo `DISCO` a propósito** — cambiarlo antes de mover los archivos dejaría el almacén vacío |
| 4.4 | Apagar GlassFish | ✅ **HECHO por borrado (2026-08-08)** | Sin ventana de observación: no había tráfico. `backend-java/` fuera del árbol |
| 4.5 | Apagar MySQL | ✅ **HECHO por borrado (2026-08-08)** | Sin conciliación: no había datos. `database/` fuera del árbol |
| 4.6 | Retirar Blazor y Bootstrap | ⬜ PENDIENTE | Bootstrap 5.3.3 **solo lo carga el Blazor** (desde CDN jsdelivr); el SPA no usa ninguna librería de UI. Desaparece al borrar el proyecto. **Ya no faltan pantallas**: las 2 de identidad (cambio de contraseña y recuperación de acceso) se cerraron el **2026-08-05** con el Bloque 4, que les dio endpoint real. Lo que queda antes de apagar es la paridad de E5 (4.1) |
| 4.7 | Eliminar el JWT compartido con el legado | 🟡 **DESBLOQUEADO** | Ya no hay con quién compartirlo: rotar el secreto pasa a ser un cambio de configuración. Habilita S0.7 |
| 4.8 | Descongelar el contrato REST | ✅ **HECHO (2026-08-08)** | ✅ **H-12 cerrado y verificado en vivo**: `GET /documentos/contenido` exige token (401 sin él, 200 con él); por ahí se descargaban documentos de identidad sin autenticación. ✅ El **Bloque B entero** de la Fase 3: 3.5, 3.6, 3.7 y 3.8. Tres suites E2E verdes tras reescribir los checks que afirmaban los bugs |

**El orden interno cambió**: el backfill se canceló y los apagados ya están hechos, así que lo que
queda es **descongelar (4.8) → Bloque B de la Fase 3**. La única regla que sigue en pie es hacerlo
**después** del borrado y no a la vez, para poder atribuir cualquier fallo de la SPA.

---

## 6. Fase 5 — Arquitectura productiva

**Estado: ⬜ PENDIENTE, y hay una decisión que la bloquea entera.**

```
Cloudflare → NGINX → Angular + Spring Boot → PostgreSQL + almacenamiento compartido
                                    ↓
              Prometheus + Grafana + Loki + Alertmanager
```

| # | Punto | Estado |
|---|---|---|
| 5.1 | HTTPS y dominio definitivo | ⬜ No existe dominio en el repositorio |
| 5.2 | Cloudflare Tunnel o firewall de origen | ⬜ Pendiente |
| 5.3 | NGINX como reverse proxy | ⬜ **No existe ninguno**. Es además donde caben el rate limiting y las cabeceras de seguridad |
| 5.4 | Panel de métricas y alertas | ⬜ **No existen**: sin Actuator, sin Micrometer, sin healthcheck más allá de `GET /salud` |
| 5.5 | Backups externos | ⬜ Depende de la Fase 2 |
| 5.6 | Logs centralizados | ⬜ Hoy: stdout de Spring Boot |
| 5.7 | Primera instancia productiva | 🔒 **Bloqueada por la decisión de §6.1** |
| 5.8 | Staging separado | ⬜ No existe |
| 5.9 | Segunda instancia solo si la carga lo justifica | 🔮 **Correcto, y está respaldado** |

### 6.1 La decisión que bloquea a todas las demás

**Dónde va a vivir producción.** Un nodo o varios cambia la respuesta a almacenamiento, caché, rate
limiting y sesiones. La recomendación escrita: **para una corredora, un solo nodo bien operado
alcanza de sobra** — y conviene **decidirlo explícitamente** en vez de dejarlo abierto.

### 6.2 Por qué "no empezar con réplicas" es la lectura correcta

Ejecutado hoy en dos instancias, el sistema falla de **cinco** formas, y está inventariado:

| Componente | Efecto con N instancias | Gravedad |
|---|---|---|
| `AlmacenDisco` | Un archivo subido por A **no existe para B** | **Bloqueante** |
| Buffer de subida por trozos | Una carga partida entre instancias falla | Alta |
| `LimitadorIntentos` | El límite de login pasa a **10 × N por minuto** | Alta (seguridad) |
| `ultimaSyncRecontacto` | El barrido corre hasta **N veces cada 5 min** | Media |
| `sesiones_invalidas_desde` (propuesto en S0) | Con caché local, la revocación tarda **hasta el TTL en cada réplica** | A tener en cuenta |

JWT stateless y Flyway (lock de BD) sí escalan bien. **La conclusión del encargo coincide con el
inventario: multi-instancia después, no antes.**

---

## 7. Fase 6 — Multiempresa definitivo

**Estado: 🟡 PARCIAL — el núcleo transversal ya está construido y verificado.** Lo que falta es la
capa de cuenta global y el refuerzo en base de datos.

| # | Punto | Estado | Nota |
|---|---|---|---|
| 6.0 | *(ya hecho)* Núcleo multi-tenant | ✅ **HECHO** | **V6**: discriminador `organizacion_id` en 14 tablas, **todas las unicidades globales reescritas por-organización**, gate que rompe el build si una entidad privada no lo lleva, y aislamiento verificado (`e2e-v6.ps1` 46/46 + fixture de segundo tenant en `e2e-personas.ps1`) |
| 6.1 | `cuenta_acceso` global | 📋 Diseñado (D-22), post-corte | Hoy `credencial_usuario` es tenant-scoped **transitoriamente**, por diseño |
| 6.2 | `usuario_organizacion` por empresa | 🟡 **La tabla EXISTE y está poblada… y el código NO la usa** | H-14: dos fuentes de verdad, una muerta. **S0.4 la convierte en la fuente autoritativa** → este punto se adelanta a la Fase 1 |
| 6.3 | Selección de organización con varias membresías | ⬜ Post-corte | Requiere 6.1 |
| 6.4 | `TENANT_ADMIN` limitado a su empresa | 📋 **Se entrega en S0.4** | Ver §2, ítem 1.1 |
| 6.5 | `PLATFORM_ADMIN` separado | 📋 **Se entrega en S0.5** | Con `concesion_acceso_tenant`: **no ve ningún tenant por defecto** |
| 6.6 | PostgreSQL **RLS** | ⬜ Post-corte (D-24) | Hoy el aislamiento es **discriminador + filtro en la aplicación**, reforzado por un gate. Funciona; lo robusto es RLS |
| 6.7 | Acceso temporal y auditado de soporte | 📋 S0 §6.4 (D-S0-16) | Motivo obligatorio, **vigencia máxima 24 h**, evento por cada request bajo concesión y notificación a los `TENANT_ADMIN` afectados |
| 6.8 | Ningún acceso transversal automático de SIVAN | 📋 **Decisión ya escrita** | *IA sin abrir datos entre organizaciones*: la inteligencia agregada se construye sobre proyecciones anónimas, no sobre acceso cruzado |

---

## 8. Fase 7 — WhatsApp, KAIROS e IA

**Estado: 🔮 FUTURO — diseñado en detalle, cero código.** El documento de diseño ya declara qué **no**
se construye ahora, y la secuenciación lo pone después de la base operativa.

| # | Punto | Estado | Nota de diseño ya tomada |
|---|---|---|---|
| 7.1 | Webhook validado de WhatsApp | 🔮 | — |
| 7.2 | Idempotencia por identificador de mensaje | 🔮 | — |
| 7.3 | Cola de trabajos | 🔮 | Hoy **no hay ninguna infraestructura de colas ni caché** en el stack |
| 7.4 | Conversaciones separadas de los hechos comerciales | 🔮 **Diseñado (D-10)** | **Dos tablas, dos velocidades**: el mensaje crudo nunca se mezcla con el contacto comercial |
| 7.5 | Clasificación estructurada | 🔮 Diseñado | Es el dato estructurado —no el texto— el que alimenta la inteligencia |
| 7.6 | Ejecución de acciones mediante casos de uso de BROX | 🔮 | Depende de que los casos de uso estén cerrados y auditados (Fase 3) |
| 7.7 | Auditoría de modelo, versión, confianza y acción | 🔮 | Encaja con `evento_seguridad` de la Fase 1 |
| 7.8 | Límites de consumo por organización | 🔮 | Requiere la Fase 6 completa |
| 7.9 | Revisión humana para decisiones sensibles | 🔮 | — |

**Contexto que conviene no perder:** ControlLocal es el **producto-cuña** que captura el dato del
moat; la Fase 0 de esa visión era *fijar el esquema AI-ready*, y buena parte de ese esquema
(estados como ordinal, etiquetas N:M, negociación con cadena) **todavía no está construida**.

---

## 9. Planes que YA completamos (no vuelven a abrirse)

Esto es lo que el plan de siete fases **no menciona porque ya está cerrado**. Se apunta aquí para
que nadie lo reabra por descuido y para que se vea sobre qué base se apoyan las siete fases.

| Plan / etapa | Cierre | Evidencia |
|---|---|---|
| ✅ **E0 — tests y decisiones base** | 2026-07 | — |
| ✅ **F4 — solicitud → contrato → comisión** | 2026-07-28 | Cascada de 7 efectos verificada contra BD real; `e2e-f4-solicitud.ps1` **116/116** |
| ✅ **F6 + F7 — alertas y tareas** | 2026-07-28 | 11 emisiones cableadas + motor de 7 disparadores; **no queda ni un `TODO(F6/F7)`** |
| ✅ **E1 — personas y perfil** | 2026-07-29 | 5 recursos; `e2e-personas.ps1` **122/122**; V10 |
| ✅ **E2 — reportes al propietario** | 2026-07-29 | **50/50** |
| ✅ **E3 — ficha comercial** | 2026-07-29 | 11 secciones; **60/60** |
| ✅ **E4 — dashboard, indicadores y seguimiento** | 2026-07-29 | **115/115**. Con esto **el backend queda cerrado: 26/26 recursos** |
| ✅ **Matriz operación→rol** | 2026-07-30 | ~150 operaciones con **test que rompe el build** si el documento y el código divergen |
| ✅ **D-F5-1 — reportes PDF fuera de alcance** | 2026-07-30 | Los 5 endpoints Jasper **no se portan**; ninguna pantalla lleva "Exportar PDF" |
| ✅ **V6 — núcleo multi-tenant** | — | Ver §7, ítem 6.0 |
| ✅ **Normalización económica y contractual (V13–V20)** | 2026-08-01 | Estados de 1 carácter con `CHECK`, monedas obligatorias, vigencias/cierre, movimientos de comisión y auditoría de disponibilidad |
| ✅ **RC-003 — rendimiento de listados** | 2026-08-02/03 | Patrón obligatorio de **conjunto de candidatos** (ramas indexables + `UNION`). Gates firmados sobre **100.000 filas**: locales 21/21, demanda 69/69, solicitudes 48/48 |
| ✅ **Verticales del SPA: Oferta, F2, F3, F4, Personas, Comercial/gestión** | 2026-08-01…04 | **47 de ~52 pantallas**, consumiendo los 26 recursos; transversales cerrados (HTTP, filtros, archivos, visor Blob, rol/navegación, 401, formato) |
| ✅ **Diagnósticos que sostienen este plan** | 2026-08-01/04 | Estados y valores económicos · autenticación y gobierno · tecnologías, dependencias y alcance de E5 |

**Lo que quedó explícitamente diferido y no es deuda olvidada:** almacén S3 real (no bloquea ninguna
pantalla), buffer de subida abandonado (el endpoint **muere** con el Blazor) y `Descripciones`
duplicado en E3 (limpieza; no se toca un módulo cerrado durante la convivencia).

---

## 10. Ajustes al orden propuesto (lo único que cambiaría)

El orden de las siete fases es correcto. Cuatro correcciones, todas menores y todas con motivo:

1. **Fase 2 (ítems 2.1–2.3) va delante de la Fase 1**, no en paralelo difuso. Backups, prueba de
   restauración y volumen del almacén son **horas** y retiran el **único riesgo irreversible** del
   inventario. Hoy, recrear el contenedor del API **borra los documentos**.
2. **La Fase 3 se parte en dos** (§4.1 y §4.2). El Bloque A —ciclo jurídico del contrato,
   disponibilidad, pago al agente, estados sin productor— es **aditivo y se puede construir durante
   la convivencia**. El Bloque B —alerta de captación, embudo, límite de tareas, subidas
   duplicadas— **exige el contrato descongelado**, así que pertenece a la Fase 4.
3. **Dos puntos de la Fase 6 se adelantan a la Fase 1**: `usuario_organizacion` como fuente de
   verdad (6.2) y `TENANT_ADMIN`/`PLATFORM_ADMIN` (6.4/6.5) son **parte de S0.4/S0.5**. No son
   trabajo duplicado: son el mismo trabajo, y llega antes.
4. **La Fase 1 termina el frontend como efecto colateral.** Las 2 pantallas que faltan —cambiar
   contraseña y recuperar acceso— **no se portaron porque la v1 no tiene endpoint**: eran maquetas.
   S0.3 las dota de backend real, y ahí se construyen.

---

## 11. Decisiones — registro con fecha

### 11.1 bis. Tomadas el **2026-08-05**

| ID | Decisión | Dónde vive |
|---|---|---|
| **D-S0-17** | **Aprobada tal cual**: de las 18 operaciones compartidas, **8 cambian de dueño**. El `TENANT_ADMIN` ve su tenant y gobierna cuentas, pero **no firma ningún hecho del negocio**; el BROKER decide y firma, pero **no administra cuentas** | `matriz-d-s0-17-operaciones-broker-admin.md` |
| **D-30** | **Alcance del Bloque 5: solo `TENANT_ADMIN`.** `PLATFORM_ADMIN` queda reservado en el vocabulario y **sin emitir**: no entra a ninguna de las 18, y su mecanismo es una concesión temporal por tenant, no un gate. Construirlo con el resto habría duplicado la tanda sin retirar riesgo | §1 bis, bloque 5 |
| **D-31** | **Varios administradores por organización, sin límite.** Se conserva `uq_broker_admin_unico` hasta V36 porque solo restringe el **booleano heredado que lee GlassFish**; el segundo administrador gobierna por su membresía y no carga la marca | §2.5 del Plan S0 |

### 11.1. Tomadas el **2026-08-04**

| ID | Decisión | Dónde vive |
|---|---|---|
| **D-27** | **Autorización de datos personales: una sola vez, en el alta.** Casilla + enlace; sin autorización no se persiste nada identificable; alta transaccional; se reutiliza el modelo de V6 | `decision-autorizacion-datos-personales.md` |
| **D-S0-20** | **Arranque fallido: SÍ.** Un `prod` mal configurado **no levanta**, con la lista ampliada (se suman almacén no persistente y valores de desarrollo en `prod`) | Plan S0 §1.2 |
| **D-S0-19** | **MFA obligatorio desde el día uno** para `TENANT_ADMIN` y `PLATFORM_ADMIN`; preparado pero opcional para BROKER/AGENTE | Plan S0 §6.1 |
| **D-S0-18** | **Invitar, activar, suspender y administrar membresías es solo de `TENANT_ADMIN`.** Un broker no lo hace, ni sobre su equipo | Plan S0 §4.4 |
| **D-S0-11** | **El correo deja de bloquear**: puerto `NotificadorIdentidad` + entrega fuera de banda; el transporte real se elige con la infraestructura productiva | Plan S0 §4.3 |
| **D-S0-21** | **Bloqueo por intentos fallidos por cuenta e IP** entra en el alcance de S0, sobre **PostgreSQL** y **sin Redis** | Plan S0 §4.8 *(sección nueva)* |
| **D-28** | **Primera producción con una sola instancia.** No se activan réplicas mientras existan los cinco componentes incompatibles | §6.2 de este documento |
| **D-29** | **Numeración de migraciones**: nada edita una migración aplicada; `migration-prod` usa el **rango reservado V900+** para no colisionar con la línea principal | Plan S0 §3 |

### 11.2 Abiertas

| ID | Decisión | Bloquea |
|---|---|---|
| **§6.1** | **¿Dónde vive producción — un nodo o varios?** | Almacenamiento definitivo, caché, rate limiting y sesiones |
| **Bloque 7** | Grafo legal del contrato, reapertura de disponibilidad, semántica de KPI y destino de cada estado sin productor | Todo el bloque de reglas de negocio |

**Sobre el correo:** autohospedar es la peor opción disponible (entrega mal sin reputación, obliga a
operar antispam/DKIM/rebotes y el puerto 25 suele estar bloqueado). Por eso D-S0-11 lo resuelve con
un **puerto**, no con un proveedor: el diseño de `token_acceso` no cambia; lo único que se difiere es
el transporte.

---

## 12. Entregable inmediato — estado

Lista literal del encargo, en su orden, con lo que hay:

| # | Entregable | Estado |
|---|---|---|
| 1 | Actualización documental con la decisión de autorización sencilla | ✅ **HECHO** — D-27 + 6 documentos actualizados |
| 2 | Volumen persistente para documentos | ✅ **HECHO Y VERIFICADO** — §12.1 |
| 3 | Backup automático de PostgreSQL | ✅ **HECHO** — `backend-spring/operacion/respaldo.ps1` |
| 4 | Restauración automatizada y verificada | ✅ **HECHO Y VERIFICADO** — `restaurar-verificar.ps1`, 7 comprobaciones |
| 5 | Perfiles `dev`, `test` y `prod` | ✅ **HECHO** |
| 6 | Arranque fallido ante configuración productiva insegura | ✅ **HECHO** — `ValidadorConfiguracionSeguridad` + tests |
| 7 | Sección completa de bloqueo por cuenta e IP en S0 | ✅ **HECHO** — Plan S0 §4.8 (D-S0-21) |
| 8 | Matriz D-S0-17 para las 18 operaciones compartidas | ✅ **HECHO, aprobado y ejecutado** (2026-08-05) |
| 9 | Plan de migraciones S0 sin modificar migraciones anteriores | ✅ **HECHO** — Plan S0 §3 (D-29) |
| 10 | Evidencia de pruebas | ✅ **HECHO** — `backend-spring/operacion/EVIDENCIA.md` |

### 12.1 Criterio de cierre del bloque

| Condición del encargo | Estado |
|---|---|
| Un archivo **sobrevive a la recreación de la API** | ✅ verificado con `docker compose up -d --force-recreate api` |
| Una base completa **puede restaurarse automáticamente** | ✅ verificado sobre base nueva, con Flyway reconociendo el historial |
| Producción **no arranca con secretos inseguros** | ✅ verificado por test de contexto |
| Las credenciales conocidas del seed **no pueden llegar a producción** | ✅ comprobación de arranque + seed fuera de la location productiva |
| La autorización se registra **una sola vez** durante el alta | ✅ implementado y verificado (Bloque 0 cerrado el 2026-08-05) |
| Sin autorización **no queda una persona identificable** | ✅ verificado end-to-end: tras un alta rechazada, `SELECT count(*) FROM persona` devuelve 0 |
| La ficha de cliente y la de propietario **muestran quién autorizó y cuándo** | ✅ `GET /clientes/{id}/autorizacion` y `/propietarios/{id}/autorizacion`, con un solo componente compartido |
| Existe una **matriz aprobada** de permisos administrativos | ✅ **aprobada el 2026-08-05 e implementada**, con `e2e-s0-roles.ps1` 48/48 |
| Las decisiones están reflejadas en el plan maestro y documentos relacionados | ✅ §11.1 |
| Las pruebas automatizadas demuestran cada condición | 🟡 las de infraestructura sí; las de D-27 llegan con su implementación |
