# Contrato congelado — F6 alertas y F7 tareas

> **El "congelado" del título es histórico.** El contrato se descongeló el
> 2026-08-09 (`decision-contrato-v2-descongelado.md`): DTOs, endpoints, estados y
> errores pueden cambiar con razón funcional y con sus pruebas.
>
> Este documento **describe el comportamiento vigente** y se sigue actualizando
> —no es historia—, pero la autoridad son **las pruebas y OpenAPI**, no este
> texto. Si discrepan, manda la suite.
>
> **Y las rutas `backend-java/...` que este documento cita ya no existen.** El
> stack legado se borró del árbol el 2026-08-08; esos nombres se conservan
> porque explican de dónde salió cada campo, no porque se puedan abrir. Lo
> vigente vive en `backend-spring/.../web/controlador/` y en su suite.

Fuente de verdad: `backend-java/` (`AlertasRest` 107 líneas, `TareasRest` 88) + sus BL
(`AlertaBusinessLogicImpl` 90, **`TareaBusinessLogicImpl` 649**) y los **nueve puntos del flujo
comercial que emiten alertas**. Este documento **congela** el cable antes de implementarlo en
`backend-spring/`, igual que se hizo con F2, F3 y F4.

Van juntas porque son la misma pieza vista desde dos lados: **la alerta avisa, la tarea manda a
hacer**. Las dos cuelgan siempre de un **agente**, las dos se leen desde el dashboard y las dos
cierran deudas abiertas de verticales ya cortadas — F6 desbloquea los **ocho** `TODO(F6-alertas)`
repartidos por los services y F7 el efecto 7 de la cascada de F4.

---

## 1. Vocabulario (enums del cable)

Todos viajan con el **NOMBRE** del enum, no como CHAR(1). Es la vertical que más rompe la
convención — aquí no hay ni un código de una letra.

| Enum | Valores |
|---|---|
| `EstadoAlerta` | `ACTIVA`, `ATENDIDA`, `DESCARTADA` |
| `Severidad` | `INFO`, `MEDIA`, `ALTA` |
| `EstadoTarea` | `PENDIENTE`, `EN_PROCESO`, `COMPLETADA`, `VENCIDA`, `CANCELADA` |
| `Prioridad` | `ALTA`, `MEDIA`, `BAJA` |
| `TipoAlerta` | 16 valores — ver §4 |
| `TipoTarea` | `SEGUIMIENTO`, `LLAMADA`, `VISITA`, `ENVIO_INFO`, `RECONTACTO`, `REPORTE_PROPIETARIO`, `ENVIAR_REVISION`, `SUBIR_DOCUMENTOS`, `REGISTRAR_CAPTACION`, `REGISTRAR_INTERACCION`, `PROPONER_OPORTUNIDAD`, `OTRO` |
| `TipoEntidad` (en tarea) | `PROSPECCION`, `CAPTACION`, `OPORTUNIDAD`, `INTERACCION`, `VISITA`, `SOLICITUD_ALQUILER`, `INMUEBLE`, `PUBLICACION`, `CONTRATO_ALQUILER`, `CLIENTE_INTERESADO`, `PROPIETARIO`, `REQUERIMIENTO` |

**La regla que ordena todo el módulo**: una alerta (y una tarea) **siempre se ata a un AGENTE**,
nunca a un broker. El agente la ve como propia y su broker supervisor la ve a través de la
supervisión. Es decir: **el destinatario lo decide el TIPO, no una columna**. `CAPTACION_CREADA`
cuelga del agente pero está escrita *para* el broker; `CAPTACION_REVISADA` cuelga del mismo agente
y está escrita *para* él. No hay `id_destinatario` y no hay que inventarlo.

---

## 2. `/alertas`

| Ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| GET `` | sesión (alcance §6) | — | `PageResponse<AlertaResponse>` (**`tamano` por defecto 20**) |
| POST `{id}/atender` | sesión con acceso | — | `AtenderAlertaResponse` |
| PATCH `{id}/atender` | sesión con acceso | — | idéntico al POST |

**Los dos verbos existen y hacen lo mismo.** No es un descuido que se pueda limpiar mientras el
contrato esté congelado: el Blazor usa uno y algún cliente el otro.

`AtenderAlertaResponse` es un sobre de **un solo booleano**: `{"atendida": true}`.

**Atender comprueba VISIBILIDAD, no propiedad**: la alerta debe estar en la lista que ese usuario
vería. Si no lo está → **404** `Alerta`, no 403. Un broker puede atender la alerta de cualquiera
de sus agentes; un agente solo las suyas.

**AlertaResponse**: `id, tipo, severidad, entidadTipo, entidadId, idAgente, agenteNombre, mensaje,
estado, fechaGeneracion, fechaResolucion, ruta`.

`ruta` es **derivada**, no columna: la calcula `Dtos.ruta(alerta)` para que la campana navegue
directo al origen del aviso.

### El efecto lateral del GET que hay que replicar

`GET /alertas` **escribe**. Antes de leer, materializa las alertas de recontacto vencido
(`prospecciones.sincronizarRecontacto()`), pero **como mucho una vez cada 5 minutos** — un
`volatile long` estático guarda la última corrida— y **tragándose cualquier excepción**: si el
barrido falla, la campana igual responde.

Es un planificador de pobre: no hay `@Scheduled` en la v1. El barrido recorre las prospecciones
en proceso con `fecha_recontacto` de hace ≥ 7 días y crea una alerta `SIN_RESPUESTA` por cada una
que no tenga ya una activa.

> **Ojo**: la tarea `RECONTACTO` de la bandeja **no** depende de este barrido — la deriva
> `TareaBusinessLogic` por su cuenta. Son dos caminos independientes hacia el mismo hecho.

---

## 3. `/tareas`

| Ruta | Rol | Respuesta |
|---|---|---|
| GET `` | **AGENTE** | `List<TareaResponse>` — **lista pelada, sin sobre de paginación**, máx. 10 |
| GET `pendientes` | **AGENTE** | `PageResponse<TareaResponse>` (**`tamano` por defecto 5**) |
| POST `{id}/cancelar` | **AGENTE** | **204 No Content**, sin cuerpo |

**No hay alta manual.** Las tareas se **derivan** del estado del flujo (§5). El agente solo las
resuelve trabajando (la tarea se auto-completa) o las cancela.

**TareaResponse**: `id, tipo, entidadTipo, entidadId, entidadCodigo, rutaResolver, descripcion,
estado, prioridad, fechaProgramada, diasSinAccion, fechaVencimiento`.

Los cuatro últimos campos —`entidadCodigo`, `rutaResolver`, `diasSinAccion`, `fechaVencimiento`—
**no están en la tabla**: se derivan al leer (§5.3).

Cancelar exige que la tarea sea **del agente** → _"La tarea no pertenece al agente."_; si no
existe → _"Tarea no encontrada."_. El DAO hace un **soft-cancel** (`UPDATE estado='CANCELADA'`),
nunca borra.

---

## 4. Los NUEVE puntos que emiten alertas (el mapa de cableado)

Esto es lo que de verdad cuesta portar: no es el recurso REST, son los avisos repartidos por todo
el flujo. Cada fila es un `TODO(F6-alertas)` que hoy espera en la v2.

| # | Dónde (v2) | Tipo | Severidad | Entidad | Mensaje del cable |
|---|---|---|---|---|---|
| 1 | `ProspeccionServiceImpl` (barrido) | `SIN_RESPUESTA` | `MEDIA` | PROSPECCION | `Recontacta o evalua descartar la prospeccion {codigo}.` |
| 2 | `CaptacionServiceImpl.registrar` | `CAPTACION_CREADA` | `MEDIA` | CAPTACION | `El agente registro la captacion {codigo} para tu revision.` |
| 3 | `CaptacionServiceImpl.decidir` | `CAPTACION_REVISADA` | **derivada** | CAPTACION | `Tu captacion {codigo} fue {aprobada\|observada\|rechazada}{detalle}` |
| 4 | `CaptacionServiceImpl.cerrar` | `CAPTACION_CERRADA` | `MEDIA` | CAPTACION | `Tu captacion {codigo} fue cerrada: {motivo}` |
| 5 | `SolicitudServiceImpl.reenviarAEvaluacion` | `SOLICITUD_REENVIADA` | `MEDIA` | SOLICITUD_ALQUILER | `La solicitud {codigo} fue enviada a evaluacion del broker supervisor.` |
| 6 | `DocumentoSolicitudServiceImpl.registrar` | `SOLICITUD_DOCUMENTO` | `MEDIA` | SOLICITUD_ALQUILER | `El agente actualizo "{archivo}" en la solicitud {codigo} mientras esta en evaluacion.` |
| 7 | `DocumentoSolicitudServiceImpl.revisar` | `SOLICITUD_DOCUMENTO_REVISADO` | `MEDIA` | SOLICITUD_ALQUILER | `El broker observo el documento "{tipo}" de la solicitud {codigo}{detalle}` |
| 8 | `EvaluacionServiceImpl.registrar` | `SOLICITUD_EVALUADA` | **derivada** | SOLICITUD_ALQUILER | `La solicitud {codigo} fue evaluada con resultado {descripcion}.` |
| 9 | `ContratoServiceImpl.registrar` (efecto 7) | `OPORTUNIDAD_CERRADA` | `INFO` | OPORTUNIDAD | `El agente concreto el alquiler de la oportunidad {codigo}.` |
| 10 | `ComisionServiceImpl.asignarMontoAgente` | `COMISION_ASIGNADA` | `INFO` | CONTRATO_ALQUILER | `Tu comision de la operacion {codigo} esta lista para cobro.` |
| 11 | `ComisionServiceImpl.registrarCobro` | `COMISION_COBRADA` | `INFO` | CONTRATO_ALQUILER | `Tu comision de la operacion {codigo} fue cobrada.` |

Son **once emisiones** en **nueve** sitios de código (captación aporta tres). Detalles que se
pierden al portar:

- **Severidades derivadas**: la revisión de captación es `ALTA` si RECHAZADA, `MEDIA` si OBSERVADA,
  `INFO` en el resto. La evaluación de solicitud: `ALTA` si RECHAZADA, si no `MEDIA`/`INFO`.
- **`{detalle}`** es `": " + observacion` cuando hay observación y `"."` cuando no. Literal.
- **Documento subido (#6) solo avisa si la solicitud está EN_REVISION**: el aviso es "cambió el
  expediente mientras lo evaluabas", no "subió un documento".
- **Comisión asignada (#10) solo en la PRIMERA asignación**, no en cada reajuste, y **nunca expone
  el monto neto**. Es una regla de privacidad, no una optimización.
- **Todas las emisiones son best-effort**: la de captación se salta si falta agente o id, y ninguna
  debe tumbar la operación principal.
- **`CAPTACION_CREADA` casi nunca se emite, y es cable**: el camino normal para crear una captación
  es `POST /prospecciones/{id}/captar`, que la inserta **directamente por el DAO**, saltándose el
  alta que emite la alerta. Solo el `POST /captaciones` directo avisa. O sea: en la práctica el
  broker **no recibe aviso** de las captaciones que le llegan a la bandeja. Se replica tal cual (la
  v1 hace lo mismo) y queda anotado como candidato a arreglar al levantar el contrato.
- Los **cuatro tipos declarados que nadie emite** (`SIN_AVANCE`, `OFERTA_POR_VENCER`,
  `CONTRATO_POR_VENCER`, `VISITA_PROXIMA`, `CAPTACION_VENCIDA`) existen en el CHECK de la BD y en
  el enum pero **no tienen emisor en la v1**. Se portan al enum por paridad y se dejan sin emisor.

---

## 5. La bandeja: derivación y reconciliación (el corazón de F7)

`GET /tareas` **no lee una tabla: la reconcilia**. Cada llamada hace, en este orden:

1. **Carga los datos del agente UNA vez** (prospecciones, solicitudes, oportunidades, captaciones,
   visitas, contratos, requerimientos de sus clientes y último reporte por captación) e indexa por
   id. Es lo que evita el N+1 en el enriquecimiento.
2. **Deriva** qué tareas *deberían* existir (§5.1).
3. **Lee** las que existen y calcula dos conjuntos de claves (`entidadTipo:entidadId`).
4. **Escribe, en una transacción**: crea las que faltan y **auto-completa** las que ya no aplican.
5. **Lee otra vez**, enriquece y ordena. ~~y **corta en 10**~~ — el corte se retiró el 2026-08-08
   (ver D-F7-2 en §7): devuelve todas las tareas abiertas del agente.

### 5.1 Los siete disparadores

> **Los cuatro plazos en negrita ya no están escritos aquí ni en el service.**
> Desde E1 (2026-08-10) salen de `PoliticaComercial` —`recontacto.dias`,
> `visita.dias-de-aviso`, `reporte-propietario.dias` y
> `coincidencia.puntaje-minimo`—, que es el mismo objeto que consultan el
> indicador de E4 y la campana de F6. Antes eran copias sueltas coordinadas por
> un comentario, y el plazo de recontacto llegó a estar en cinco sitios. Si
> cambian, cambian en los tres sitios a la vez; los números de esta tabla son
> los vigentes, no la fuente.

| # | Dispara cuando | Tipo | Prioridad |
|---|---|---|---|
| 1 | Prospección en proceso con `fechaRecontacto` de hace ≥ **7 días** | `RECONTACTO` | ALTA |
| 2 | Solicitud `APROBADA` (aún sin cierre) | `SEGUIMIENTO` | ALTA |
| 3 | Comisión `PENDIENTE` **con `montoAgente` ya asignado** | `SEGUIMIENTO` | MEDIA |
| 4 | Solicitud `OBSERVADA` | `SUBIR_DOCUMENTOS` | ALTA |
| 5 | Visita `NO_REALIZADA`, o PROGRAMADA/REPROGRAMADA a ≤ **3 días** | `VISITA` | ALTA si vencida o no realizada; MEDIA si próxima |
| 6 | Captación ACTIVA cuyo último reporte al propietario supera **15 días** | `REPORTE_PROPIETARIO` | MEDIA |
| 7 | Requerimiento ACTIVO con captación propia compatible (puntaje ≥ **60**) y sin oportunidad para ese par | `PROPONER_OPORTUNIDAD` | MEDIA |

Constantes del cable: `DIAS_RECONTACTO=7`, `DIAS_VISITA_PROXIMA=3`, `DIAS_REPORTE=15`,
`UMBRAL_PROPUESTA=60`. (`MAX_BANDEJA=10` era de la v1 y ya no existe: ver D-F7-2 en §7.)

### 5.2 El reconcile, con sus dos trampas

- **Dedup por `entidadTipo:entidadId`**, no por tipo de tarea. Dos disparadores sobre la misma
  entidad no producen dos tareas.
- **Trampa 1 — `CANCELADA` bloquea para siempre.** Las claves que bloquean la creación son
  `PENDIENTE`, `EN_PROCESO` **y `CANCELADA`**. Es decir: cancelar una tarea no la pospone, la
  **mata definitivamente** para esa entidad — el disparador nunca la vuelve a crear. Es cable real
  y hay que replicarlo, pero conviene saberlo: es lo que hace que "cancelar" sea una decisión
  fuerte y no un "más tarde".
- **Trampa 2 — solo se auto-resuelven seis `entidad_tipo`.** `ENTIDADES_AUTO` = PROSPECCION,
  SOLICITUD_ALQUILER, CONTRATO_ALQUILER, VISITA, CAPTACION, REQUERIMIENTO. Una tarea sobre
  cualquier otra entidad **nunca se cierra sola**.

### 5.3 Enriquecimiento (los cuatro campos derivados)

- **`entidadCodigo`** y **`rutaResolver`**: la ruta lleva directo al origen. Las pantallas de
  detalle enrutan por **código** (solicitud, captación) o por **id** (prospección); las visitas
  con deep-link `visitas?focus={id}`; contrato/comisión caen en `comisiones`;
  `PROPONER_OPORTUNIDAD` abre **la ficha del cliente**, no el requerimiento.
- **`fechaVencimiento`**: sale de la entidad de origen cuando ésta impone plazo (recontacto de la
  prospección, vigencia de la oferta, fecha de visita, siguiente reporte).
- **`diasSinAccion`**: días desde una **fecha base que NO es la de creación de la tarea**, sino la
  del plazo real de la entidad (`fechaRecontacto`, `fechaActualizacionEstado`, `fechaVisita`).
  Es el detalle que más fácil se porta mal: usar la fecha de la tarea da siempre 0.
- Todo el enriquecimiento es **best-effort**: envuelto en try/catch, nunca tumba la bandeja.

### 5.4 Orden y tope

Primero **prioridad** (ALTA=0, MEDIA=1, BAJA=2), luego **más días sin acción**. Se corta en **10**
y el resto **se descarta en silencio** — el cliente no recibe ninguna señal de que había más.

---

## 6. Alcance por rol

| Recurso | AGENTE | BROKER | ADMIN |
|---|---|---|---|
| `GET /alertas` | las suyas | las de sus **agentes supervisados** | todas |
| `POST/PATCH /alertas/{id}/atender` | las suyas | las de su equipo | todas |
| `/tareas` (las tres rutas) | **solo AGENTE** | 403 | 403 |

La bandeja es **estrictamente personal**: ni el broker ni el admin la ven. Es coherente con lo que
es —una lista de acciones que hacer, no un tablero de control—, y con que `/tareas` sea el único
recurso del sistema sin acceso de ADMIN.

---

## 7. Mapa de implementación en `backend-spring/`

1. **`V9__alertas_tareas.sql`**: `alerta`, `tarea` y **`reporte_propietario`**. Las tres privadas
   del tenant ⇒ heredan `EntidadDeOrganizacion`. La tercera entra aquí aunque su REST siga
   pendiente: el disparador 6 de la bandeja necesita **una** lectura suya (último reporte por
   captación) y sin la tabla ese disparador no existe, lo que sería una divergencia observable en
   `GET /tareas`.
2. **`Alerta` y `Tarea` NO son `Transicionable`**. No es una omisión: `entidad_tipo` ya las declara
   con `auditable = FALSE` desde V2. Auditar cada reconcile inundaría `historial_estado` con ruido
   operativo. Sus estados se mueven con setters normales.
3. Services: `AlertaService` (corto) y `TareaService` (el motor de derivación + reconcile).
   `TareaService` **lee de otros services** —prospecciones, solicitudes, oportunidades,
   captaciones, visitas, contratos, comisiones, requerimientos— igual que su BL v1.
4. Controllers con las rutas de §2–§3. Recordar: `tamano` por defecto **20** en alertas y **5** en
   `/tareas/pendientes`; `GET /tareas` devuelve **lista pelada**; cancelar responde **204**.
5. Cablear los **nueve sitios** de §4, quitando sus `TODO(F6-alertas)`.
6. `GET /alertas` baja la paginación a SQL (deuda §2, misma que se cerró en `/evaluaciones`).

**Decisiones a tomar antes de escribir código**:

- **D-F6-1 · paginación en memoria**: la v1 trae toda la lista y corta con `subList`. En la v2 baja
  a SQL con LIMIT/OFFSET, respuesta idéntica (MEJ-05 / RC-003, ya aplicado en `/evaluaciones`).
  **Propuesta: bajar a SQL.** Es la misma mejora invisible que ya se aprobó dos veces.
- **D-F6-2 · el barrido dentro del GET**: replicar el throttle de 5 minutos tal cual (con estado
  en el bean, que en Spring es singleton = mismas semánticas que el `static` de la v1), o moverlo a
  un `@Scheduled`. **Propuesta: replicarlo.** Un `@Scheduled` cambia *cuándo* aparecen las alertas
  y eso sí es observable; además el planificador se decide mejor cuando el legado ya no exista.
- **D-F6-3 · alcance de `atender`**: el cable devuelve **404 cuando la alerta existe pero no es
  visible**, no 403. Se replica (es el patrón "no confirmes que existe").
- **D-F7-1 · `reporte_propietario`**: crear la tabla en V9 e implementar **solo** la consulta que
  la bandeja necesita, dejando `/captaciones/{id}/reportes-propietario` para su turno.
  **Propuesta: sí**, es lo que mantiene `GET /tareas` byte-compatible al menor costo.
- **D-F7-2 · el tope silencioso de 10** — ~~se replica~~ **RETIRADO el 2026-08-08** al descongelar
  el contrato (`decision-contrato-v2-descongelado.md`), que era justo la revisión que quedaba
  anotada aquí. `bandejaDe` ya no corta y `totalRecords` es el total real de tareas abiertas. La
  consecuencia para el SPA: la home no puede volcar la lista entera en la tarjeta —el dashboard se
  descuadra a partir de ~10 filas—, así que compone las **5 primeras** y el resto se recorre en un
  panel lateral con su propio scroll.
- **D-F7-3 · `GET` que escribe**: `GET /tareas` reconcilia y por tanto muta. Se replica; es la
  única forma de que la bandeja esté al día sin planificador.
- **D-F6-4 · `INMUEBLE` vs `PROPIEDAD` — decidida al escribir V9**: el `entidad_tipo` de alertas y
  tareas **no es** el registro de auditoría `entidad_tipo`, aunque se llamen igual. Son dos
  vocabularios distintos que **no coinciden**: la v1 emite `INMUEBLE` —en la alerta de
  modificación sensible de F2 y en el efecto 7 de F4, que resuelve las tareas del local— mientras
  que la v2 renombró esa entidad a `PROPIEDAD` (MEJ-12/31). Poner una FK contra el registro de
  auditoría, que es lo primero que uno hace, **rompe esas dos emisiones**. V9 usa un CHECK con el
  vocabulario del cable, `INMUEBLE` incluido. Al retirar el contrato congelado se unifican.
  > Detalle relacionado: `ruta` devuelve **null** para `INMUEBLE` (cae en el `default`), así que
  > esa alerta se muestra sin enlace. Es cable real, no un bug que haya que tapar.
- **D-F6-5 · la alerta de "modificación comercial sensible" viaja con el tipo equivocado**: la v1
  la emite como `SOLICITUD_EVALUADA` con un comentario que lo admite —*"por las restricciones del
  CHECK ck_alerta_tipo"*—, porque no existe un tipo que le encaje. Es un bug del cable, así que se
  **congela** (misma regla que la moneda USD de la comisión, D-F4-3): se replica tal cual y se
  arregla cuando se levante el contrato, añadiendo el tipo que falta.
- **D-F6-6 · atender dos veces devuelve `false`**: el UPDATE lleva `AND estado = 'ACTIVA'`, así que
  la segunda llamada no toca nada y el sobre responde `{"atendida": false}` — no es un error. Se
  replica.

---

## 8. Estado de ejecución

- [x] Contrato congelado (este documento), extraído de `AlertasRest` + `TareasRest` + sus BL y de
      los nueve puntos de emisión del flujo.
- [x] **`V9__alertas_tareas.sql`**: `alerta`, `tarea` y `reporte_propietario`. Validada sobre una
      copia y aplicada (Flyway v9). Tres invariantes bajaron a la BD y se comprobó que muerden:
      *atendida ⇒ con fecha de resolución*, *completada ⇒ con fecha* y **una sola tarea ABIERTA por
      (agente, entidad)** — el único parcial que deja fuera a `CANCELADA` a propósito, porque si
      entrara chocaría con la pendiente de la misma entidad.
- [x] **Entidades y repositorios**. `Alerta` y `Tarea` **no** son `Transicionable` (§7 punto 2).
      Las consultas de derivación viven **en el repositorio de cada agregado** y devuelven el
      read-DTO `CandidatoTarea`, no entidades enteras.
- [x] **`AlertaService` + `TareaService`**, controllers y DTOs. `mvn clean install` verde:
      **192/192** con los tres gates de ArchUnit; el API arranca con `ddl-auto: validate`, que es
      lo que prueba el mapeo de las 3 entidades y el parseo de las consultas nuevas.
- [x] **Los nueve puntos cableados** y sus `TODO(F6-alertas)` retirados, incluido el efecto 7 de la
      cascada de F4 (`TODO(F7-tareas)`) y la **deuda vieja de F2** (*"Modificación comercial
      sensible, revisar"*), que llevaba abierta desde la primera vertical.
- [x] **`verificacion/e2e-f6-f7-alertas-tareas.ps1`** — recorre las once emisiones y los siete
      disparadores contra la BD real.

**Lo que cambió respecto de la v1 y conviene saber**:

- **La derivación de la bandeja baja a SQL.** La v1 cargaba en memoria TODAS las prospecciones,
  solicitudes, oportunidades, captaciones, visitas y contratos del agente y filtraba en Java; aquí
  cada disparador pregunta por lo suyo y su condición va en el WHERE (MEJ-05 / RC-003). El conjunto
  de tareas es el mismo. La única parte que sigue en memoria es el disparador 7, porque
  `CoincidenciaCartera.evaluar` necesita el requerimiento y la propiedad enteros.
- **El buffer de la campana ya no hace `subList`**: `GET /alertas` pagina en SQL (D-F6-1).
- **Mejora invisible en el barrido**: el sweep de recontacto lleva un tope de 500 por pasada, que la
  v1 no tenía, y va acotado a la organización.
