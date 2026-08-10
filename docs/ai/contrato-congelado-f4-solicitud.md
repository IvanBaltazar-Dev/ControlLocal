# Contrato congelado — F4: Solicitud, documentos, evaluación, contrato y comisión

> **El "congelado" del título es histórico.** El contrato se descongeló el
> 2026-08-09 (`decision-contrato-v2-descongelado.md`): DTOs, endpoints, estados y
> errores pueden cambiar con razón funcional y con sus pruebas.
>
> Este documento **describe el comportamiento vigente** y se sigue actualizando
> —no es historia—, pero la autoridad son **las pruebas y OpenAPI**, no este
> texto. Si discrepan, manda la suite.

Fuente de verdad: `backend-java/` (`SolicitudesRest` 633 líneas, `EvaluacionRest` 85,
`ContratosRest` 312) + sus BL. Este documento **congela** el cable antes de implementarlo en
`backend-spring/`, igual que se hizo con F2 y F3.

F4 es la vertical que **cierra el ciclo**: hoy `POST /oportunidades/{id}/cierre-exitoso` responde
400 a propósito porque el cierre exitoso no lo produce un botón, sino el **contrato** registrado
sobre una solicitud aprobada (§6).

---

## 1. Vocabulario (enums del cable)

| Enum | Códigos |
|---|---|
| `EstadoSolicitudAlquiler` | `G` Registrada, `E` En revisión, `O` Observada, `A` Aprobada, `R` Rechazada, `D` Desistida, `C` Cerrada |
| `EstadoDocumentoSolicitud` | `R` Registrado, `O` Observado, `V` Validado |
| `ResultadoRevisionDocumento` | `P` Pendiente, `C` Conforme, `O` Observado |
| `ResultadoEvaluacionSolicitud` | `A` Aprobada, `R` Rechazada, `O` Observada |
| `TipoEvaluacionSolicitud` | `P` Preliminar, `O` Observación, `F` Final |
| `EstadoContrato` | `P` En proceso, `D` Firmado, `V` Vigente, `R` Renovado, `F` Finalizado, `S` Rescindido, `A` Anulado |
| `TipoDocumentoSolicitud` | `I` identidad (catálogo 1), `R` ficha RUC (2), `V` vigencia de poder (3), `P` poder de representación (4), `E` sustento económico (5), `G` garantía (6), `D` declaración jurada (7), `O` otro (8) |

**Dos enums rompen la convención CHAR(1)** y viajan con el **nombre**, como ya pasaba en F3:

- `EstadoComision`: `PENDIENTE`, `PARCIAL`, `COBRADA`, `ANULADA`
- `FormaPago`: `TRANSFERENCIA`, `DEPOSITO_BANCARIO`, `EFECTIVO`, `CHEQUE`, `OTRO`

**El checklist "X/Y" de las pantallas cuenta 6 tipos, no 8**: identidad, ficha RUC, vigencia de
poder, sustento económico, garantía y declaración jurada. `PODER_REPRESENTACION` y `OTRO`
**no** suman al indicador aunque se puedan subir. Un tipo cuenta como entregado si su documento
está `REGISTRADO` **o** `VALIDADO` (un `OBSERVADO` deja de contar).

---

## 2. `/solicitudes`

| Ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| GET `` | sesión (alcance §7) | — | `PageResponse<SolicitudResponse>`; filtros `idOportunidad`, `idCaptacion` |
| GET `{id}` | sesión con acceso | — | `SolicitudResponse` |
| GET `codigo/{codigo}` | sesión con acceso | — | `SolicitudResponse` |
| POST `` | **AGENTE** | `SolicitudRequest` | 201 + `SolicitudResponse` |
| POST `{id}/reenviar` | **AGENTE** con acceso | — | `SolicitudResponse` |

**Alta**: exige `idOportunidad` — _"Los datos de la solicitud son obligatorios."_. Si no viene
`codigoSolicitud`, el backend genera `SOL-yyMMddHHmmss` (**ojo: formato distinto de los
correlativos `PRO-####`/`CAP-####`/`OP-…` de F2/F3 — aquí es una marca de tiempo, no un
contador por organización**). La BL resuelve y valida la oportunidad y su captación:
_"Oportunidad comercial no encontrada para solicitud."_ / _"Captacion no encontrada para
solicitud."_ / _"Agente no encontrado para solicitud."_.

**El alta tiene dos precondiciones y un efecto lateral que no se ven en el REST** (están en
`SolicitudAlquilerBusinessLogicImpl.registrar`, y son fáciles de perder al portar):

1. la **captación debe estar ACTIVA** y la **oportunidad ABIERTA**;
2. al crear la solicitud, la oportunidad **transiciona a `S` (Solicitud creada)** —
   `oportunidad.marcarSolicitudCreada()`. En la v2 eso **debe pasar por `Transiciones`**, con lo
   que queda auditado gratis.

Además el alta **no comprueba que la oportunidad sea del agente** que registra: solo fija al
agente actual como responsable. Es el cable real; se replica y se anota.

**Reenviar a evaluación**: solo desde `REGISTRADA` u `OBSERVADA` →
_"Solo una solicitud registrada u observada puede enviarse a evaluacion."_. Deja la solicitud
`EN_REVISION`; es como el agente subsana una observación del broker. **Exige además que el agente
responsable tenga un broker supervisor activo** →
_"El agente responsable no tiene broker supervisor activo."_ (si no, no habría quién evalúe).

**SolicitudRequest**: `codigoSolicitud, fechaRegistro, montoPropuesto, plazoTentativo,
observaciones, fechaVigenciaOferta, idOportunidad, plazoMeses, fechaInicio, formaPago,
mesesGarantia, mesesAdelanto`.

**SolicitudResponse**: lo anterior resuelto + `id, estado, fechaActualizacionEstado,
codigoOportunidad, idCliente, clienteNombre, idCaptacion, codigoCaptacion, direccionLocal,
distritoLocal, idAgente, agenteNombre` y los dos contadores del checklist
`documentosEntregados` / `documentosRequeridos` (este último **siempre 6**).

---

## 3. Documentos de la solicitud

| Ruta | Rol | Sentido |
|---|---|---|
| GET `{id}/documentos` | sesión con acceso | lista |
| POST `{id}/documentos` | **AGENTE** | alta con `contenidoBase64` (o solo metadatos) |
| POST `{id}/documentos/archivo` | **AGENTE** | alta con cuerpo `application/octet-stream` (`?tipoDocumento&nombreArchivo`) |
| POST `{id}/documentos/chunk` | **AGENTE** | alta por trozos base64 acumulados por `uploadId` |
| POST `{id}/documentos/local` | **AGENTE** | alta por handoff: llega el NOMBRE de un temporal en `~/controllocal/uploads-tmp` |
| PATCH `{id}/documentos/{idDoc}/revisar` | **BROKER/ADMIN** | Conforme u Observado |
| PATCH `{id}/documentos/conformar` | **BROKER/ADMIN** | deja conformes en bloque los pendientes |

**Las cuatro vías de subida existen por un bug del cliente**: el `SocketsHttpHandler` de .NET 10
rompe contra GlassFish con cuerpos grandes, así que el frontend Blazor sube por trozos o por
handoff local. **Decisión pendiente para la v2** (§8): con Angular basta la vía base64/octet-stream;
`chunk` y `local` solo hacen falta mientras el Blazor siga vivo. `local` además lee del disco del
servidor — no sobrevive a un despliegue en contenedor.

**Validaciones comunes**: `tipoDocumento` obligatorio (_"El tipo de documento es obligatorio."_ /
_"Tipo de documento invalido: {x}"_), `nombreArchivo` obligatorio, extensión en
`.pdf .png .jpg .jpeg` (_"Tipo de archivo no permitido ({ext})."_), tamaño máximo **5 MB**
(_"El archivo supera el maximo de 5 MB."_), no vacío (_"El archivo esta vacio."_), base64 válido
(_"El contenido del archivo (base64) es invalido."_). El fallo del almacén responde **502**:
_"No se pudo guardar el documento en el almacen: {detalle}"_.

En `local`, el temporal debe ser un nombre simple: si trae `/`, `\` o `..` →
_"Nombre de archivo temporal invalido."_ (y se resuelve contra la carpeta acordada, nunca contra
una ruta arbitraria).

El documento nace **Registrado / revisión Pendiente**. La carpeta del almacén es el
`codigoSolicitud`, o `SOL-{id}` si la solicitud no tuviera código.

**Revisión**: `resultado` obligatorio (_"El resultado de la revision es obligatorio."_); si es
`OBSERVADO` la observación es obligatoria (_"La observacion del documento es obligatoria."_);
el documento debe pertenecer a la solicitud (_"El documento no pertenece a la solicitud
indicada."_). Al observar, la v1 emite alerta real al agente responsable → en la v2 eso es del
módulo **alertas (F6)**, así que aquí queda anotado, no implementado.

**Asimetría de alcance del cable (D-F4-5) — CERRADA el 2026-07-29**: en la v1, `revisar` un
documento suelto **solo exige el ROL** BROKER/ADMIN —no comprueba que la solicitud esté en el
alcance del broker—, mientras que `conformar` en bloque **sí** llama a `obtenerConAcceso` y la
evaluación **sí** exige supervisión. Es decir, en la v1 un broker puede revisar documentos del
equipo de otro.

> **Decisión de equipo: se cierra el hueco antes del corte.** Que sea el único de tres
> operaciones hermanas sin comprobación delata un olvido de la v1, no una regla. La v2 llama a
> `acceso.conAcceso(idSolicitud, actor)` al principio de `revisar`, así que una petición que la
> v1 respondía con **200** ahora responde **403** (404 si la solicitud no es del tenant). Es una
> **divergencia deliberada y acotada** del contrato congelado: el Blazor no la alcanza por
> navegación —sus listados ya vienen filtrados por alcance—, hace falta escribir los dos ids a
> mano. Cubierta por dos tests en `DocumentoSolicitudServiceImplTest` y por el E2E.

**El estado se DERIVA del resultado, y solo "conforme" valida**: `C` ⇒ `VALIDADO`; cualquier otro
resultado ⇒ `OBSERVADO`. Conformar además **borra** la observación previa (`actualizarRevision`
pisa el campo con el que le pasen, que al conformar es `null`).

**DocumentoSolicitudResponse**: `id, idSolicitud, tipoDocumento, tipoNombre, nombreArchivo,
rutaArchivo, fechaEntrega, estado, resultadoRevision, observaciones`.

---

## 4. `/evaluaciones`

| Ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| GET `` | **BROKER/ADMIN** | — | `PageResponse<EvaluacionResponse>` |
| GET `{id}` | **BROKER/ADMIN** | — | `EvaluacionResponse` |
| POST `` | **BROKER/ADMIN** | `EvaluacionRequest` | 201 + `EvaluacionResponse` |
| GET `/solicitudes/{id}/evaluaciones` | sesión con acceso | — | `List<EvaluacionResponse>` (historial) |

**El broker NO elige el tipo de evaluación: se deriva del resultado.** `OBSERVADA` ⇒
`OBSERVACION`; `APROBADA`/`RECHAZADA` ⇒ `FINAL`. El campo `tipoEvaluacion` del request se
ignora/pisa.

> **Matiz que cuesta caro al portar**: se ignora su **valor**, no su **presencia**. `aEntidad`
> parsea `tipoEvaluacion` con `enumDesde` _antes_ de que la BL lo pise, así que mandarlo vacío o
> inválido es un **400** — _"Valor invalido para tipo de evaluacion: {x}"_ — aunque el valor no
> vaya a usarse. Y el parseo del **tipo va antes** que el del resultado: con los dos mal, gana el
> mensaje del tipo. Estos dos campos comparan el código **exacto**
> (`CodigoEnum.fromCodigo`, sin normalizar la caja), a diferencia de los gates de comisión de §5,
> que sí hacen `valueOf(trim().toUpperCase())`.

**Solo puede existir una evaluación FINAL por solicitud** →
_"Solo puede existir una evaluacion final por solicitud."_.

**El broker debe supervisar al agente responsable** (el admin no) →
_"El broker no supervisa al agente responsable de esta solicitud."_. Otros mensajes:
_"Los datos de la evaluacion son obligatorios."_, _"Solicitud no encontrada para evaluacion."_,
_"Broker responsable no encontrado."_.

**La evaluación mueve la solicitud** en la misma transacción: `APROBADA` → `aprobar()`,
`RECHAZADA` → `rechazar()`, `OBSERVADA` → `solicitarAjustes()` (vuelve a `OBSERVADA`, que es
justo el estado desde el que el agente puede `reenviar`).

**EvaluacionRequest**: `tipoEvaluacion, resultado, observaciones, idSolicitud`.
**EvaluacionResponse**: `id, fechaEvaluacion, resultado, observaciones, idBroker, brokerNombre,
tipoEvaluacion, idSolicitud`.

> **Deuda de la v1 que la v2 debe cerrar sin tocar el cable**: `GET /evaluaciones` pagina **en
> memoria** (`listarTodos()` + `subList`), y `GET {id}` filtra la lista completa. En la v2 baja a
> SQL con LIMIT/OFFSET (MEJ-05 / RC-003), respuesta idéntica.

---

## 5. `/contratos` y la comisión

| Ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| GET `` | sesión (alcance §7) | — | `PageResponse<ContratoResponse>` (**`tamano` por defecto 100**, no 10) |
| GET `oportunidad/{idOportunidad}` | sesión con acceso | — | `ContratoResponse` |
| POST `` | **AGENTE** | `ContratoRequest` | 201 + `ContratoResponse` |
| POST `{idContrato}/comision/asignar` | **BROKER** (no ADMIN) | `ComisionAsignarRequest` | `ContratoResponse` |
| POST `{idContrato}/comision/cobro` | **BROKER** (no ADMIN) | `ComisionCobroRequest` | `ContratoResponse` |

**Alta**: `idSolicitud` obligatorio y > 0 →
_"Selecciona la solicitud aprobada que se va a alquilar."_. La solicitud debe ser del agente
(403). `estadoContrato` solo puede ser **Firmado o Vigente** (por defecto `VIGENTE`); un código
que no existe en el enum da _"Estado de contrato invalido."_ y uno válido pero que no es de cierre
da _"El cierre solo admite los estados Firmado o Vigente."_. `fechaCierre` no puede ser futura →
_"La fecha de cierre no puede ser futura."_ (por defecto hoy).

> **Corregido 2026-07-27**: este documento citaba aquí _"El contrato solo puede cerrarse como
> Firmado o Vigente."_, que es el texto de la **BL** y **nunca llega al cable**: `ContratosRest`
> valida el estado antes de invocarla, así que los mensajes de arriba son los que ve el cliente.

**Los dos gates de comisión son del BROKER supervisor, no del ADMIN** (el admin solo lee):
_"Indica el monto del agente."_ / _"Indica el estado del cobro (Cobrada o Anulada)."_; si no
existe la liquidación → 404 _"Liquidacion de comision"_. Al asignar el monto del agente, el de la
empresa **se calcula solo**.

**Los montos de la comisión solo viajan a ADMIN/BROKER**: `ContratoResponse` se arma con un flag
de visibilidad y el AGENTE no ve `montoAgente`/`montoEmpresa`.

---

## 6. La cascada del cierre — el corazón de F4

`POST /contratos` es la operación más pesada del sistema: una sola transacción que toca **siete**
entidades. Precondiciones:

1. Solicitud en `APROBADA` → _"Solo se puede registrar el alquiler de una solicitud aprobada."_
2. Oportunidad en `ABIERTA` o `SOLICITUD_CREADA` →
   _"La oportunidad ya esta cerrada; no admite un nuevo contrato."_
3. Sin contrato previo para esa oportunidad →
   _"Esta operacion ya tiene un contrato de alquiler registrado."_

Efectos, en orden:

| # | Efecto |
|---|---|
| 1 | Crea el **contrato** (vínculo + formalización; las condiciones del trato viven en la solicitud) |
| 2 | Crea la **comisión** `PENDIENTE`: bruta = `comisionPactada` de la captación × `montoPropuesto` de la solicitud, moneda **USD fija**; `montoAgente`/`montoEmpresa`/`fechaCobro`/`formaPago` quedan NULL |
| 3 | **Oportunidad** → `cerrarExitosa()` (`F`) ← *esto es lo que hoy falta y por eso `cierre-exitoso` responde 400* |
| 4 | **Solicitud** → `CERRADA` (no se reabre) |
| 5 | **Captación** → cerrada, con `fechaFinVigencia` = fecha de cierre |
| 6 | **Local** → `NO_DISPONIBLE`, se registra precio con hito `C` (cerrado real) y se dan de baja sus publicaciones |
| 7 | Se resuelven las **tareas** abiertas de la operación y se emite **alerta** al broker |

Los efectos 6 (publicaciones/precios) y 7 (tareas/alertas) cruzan a módulos ya migrados y a
módulos que **no existen todavía** en la v2 — ver §8.

### La atribución del cierre se congela (V27, 2026-08-03)

Además de los siete efectos, el alta **graba a quién se le atribuye el alquiler**:
`id_rol_agente_cierre`, `id_rol_broker_cierre`, `id_captacion`, `id_propiedad` e `id_rol_cliente`
en la propia fila del contrato.

Por qué hacía falta: hasta V27 esos cinco datos **no se guardaban**. Se releían de la cadena
vigente (`contrato → solicitud → agente`, `contrato → oportunidad → captación → agente/propiedad`)
en cada consulta, así que una reasignación posterior **reescribía la historia**: un alquiler
cerrado hace meses pasaba a atribuirse según el organigrama de hoy. Un cierre es un hecho
consumado; quién lo cerró no cambia porque después cambie el equipo.

Reglas:

- El agente es el de la **solicitud** y, en su defecto, el de la oportunidad — la misma precedencia
  que ya usaban la ficha y E4.
- El broker es el supervisor **vigente al cerrar**. Si el agente no tenía supervisor queda **NULL**
  y no se rellena después: es información que no existe, y ponerle el supervisor de turno sería
  inventarla.
- La **renovación hereda** la atribución del contrato que renueva (es el mismo alquiler
  continuando) y resuelve el broker al renovar.

Lo que **no** cambia, y conviene no confundir:

- **El cable es idéntico.** `agenteId` y `agenteNombre` ya salían con estos valores; ahora salen
  del snapshot en vez de la cadena. Para toda fila existente el backfill dejó el mismo valor, así
  que la respuesta no se mueve — se moverá el día que alguien reasigne, que es de lo que se trata.
  No hay campos nuevos, ni endpoint nuevo, ni fila nueva en la matriz operación→rol.
- **El alcance no se toca.** El BROKER sigue alcanzando contratos por **captación supervisada hoy**,
  como declara la matriz. El snapshot es trazabilidad del hecho, no un permiso.
- **El inmueble no se reactiva** al finalizar o rescindir. Eso está decidido (la matriz lo dice
  explícitamente: dejan **tarea** de revisión) y cambiarlo sí sería una decisión funcional.
- **Comisión generada / cobrada / pagada ya estaban separadas** desde V15 y no se tocan:
  `comision_liquidacion.monto_bruto` es lo generado, y `comision_movimiento` (`C` cobro, `P` pago
  al agente, `A` ajuste, `R` reversión) es la evidencia de lo cobrado y lo pagado, con sus saldos
  ya publicados en la respuesta (`montoCobrado`, `saldoCobro`, `montoPagadoAgente`,
  `saldoPagoAgente`).

---

## 7. Alcance por rol (¡otra vez dos reglas distintas!)

| Recurso | AGENTE | BROKER | ADMIN |
|---|---|---|---|
| Solicitudes | las suyas (`solicitud.id_agente`) | **por agente supervisado** | todo |
| Evaluaciones | **sin acceso** (403) | las suyas | todo |
| Contratos | los suyos (vía `solicitud.id_agente`) | **por captación supervisada** | todo |

Como en F3, **no unificar**: solicitudes alcanzan por agente y contratos por captación. Un BROKER
sin agentes supervisados obtiene lista vacía (no un 403).

---

## 8. Mapa de implementación en `backend-spring/`

1. **V8__solicitud_documentos_contrato.sql**: `solicitud_alquiler`, `tipo_documento_requerido`
   (catálogo, 8 filas con los ids 1..8 del enum), `documento_solicitud`, `evaluacion_solicitud`,
   `contrato_alquiler`, `comision_liquidacion`. Todas privadas del tenant ⇒ heredan
   `EntidadDeOrganizacion` **salvo `tipo_documento_requerido`**, que es catálogo global (como
   `distrito`) y debe declararse así en `ArquitecturaTenancyTest`. Único parcial: **un contrato
   por oportunidad**.
2. `SolicitudAlquiler` y `ContratoAlquiler` implementan `Transicionable` (nuevos códigos en
   `entidad_tipo`) ⇒ sus transiciones vía `Transiciones` y auditadas gratis. `DocumentoSolicitud`
   también tiene estado propio (R/O/V) — evaluar si entra como transicionable o como evento.
   `EvaluacionSolicitud` **no** es transicionable: es un evento con resultado.
3. Services: `SolicitudService`, `DocumentoSolicitudService`, `EvaluacionService`,
   `ContratoService`, `ComisionService`. La cascada de §6 vive en `ContratoService` y **debe pasar
   por `Transiciones`** para cada una de las cuatro entidades que cambia de estado.
4. Controllers con las rutas exactas de §2–§5. Recordar el `tamano` por defecto **100** de
   contratos y que los dos gates de comisión son `hasRole('BROKER')` **sin** ADMIN.
5. Almacén: portar `web/almacen/` (ya existe `AlmacenDisco` de F2) y reusar
   `/documentos/contenido` para servir los binarios.
6. `GET /evaluaciones` baja a SQL (deuda §4).

**Decisiones — RESUELTAS (2026-07-27)**:

- **D-F4-1 · vías de subida — DECIDIDA POR EL EQUIPO**: se portan **base64 + octet-stream +
  chunk**; **`documentos/local` NO se porta**. Razón: `chunk` funciona perfectamente dentro del
  contenedor (solo acumula JSON en memoria), pero `local` lee del disco del servidor
  (`~/controllocal/uploads-tmp`), lo cual es imposible con el API en Docker. Se porta todo lo que
  puede funcionar y se deja fuera únicamente lo que no.
- **D-F4-2 · efecto 7 de la cascada**: se dejan `TODO(F6-alertas)` y `TODO(F7-tareas)` como en
  `LocalComercialServiceImpl`, sin romper la transacción. Se cierra al migrar F6/F7.
- **D-F4-3 · moneda USD de la comisión**: **se congela el bug**. Es lo coherente con la regla de
  contrato: los bugs que hoy se replican a propósito se arreglan recién al retirar el legado.
- **D-F4-4 · código `SOL-yyMMddHHmmss`**: se mantiene el formato del cable y el único es **por
  organización** (`uq_solicitud_codigo (organizacion_id, codigo_solicitud)`, ya en V8). No es un
  correlativo como `PRO-####`/`CAP-####`: es una marca de tiempo.

---

## 9. Estado de ejecución

**En curso (2026-07-27): contrato + V8 + entidades + repos + los CINCO services, con tests de
comportamiento. Faltan controllers/DTOs, el almacén y el E2E.**

- [x] Contrato congelado (este documento), extraído de `SolicitudesRest` + `EvaluacionRest` +
      `ContratosRest` y sus BL.
- [x] **`V8__solicitud_documentos_contrato.sql`**: las 6 tablas (`tipo_documento_requerido` como
      catálogo GLOBAL con los ids 1..8 del cable; el resto tenant-scoped con FK compuestas de V6)
      + seed DEMO (una solicitud `EN_REVISION` con 4 documentos ⇒ la bandeja muestra 4/6).
      Validada sobre una copia y **aplicada** en la BD de desarrollo (Flyway v8).
      Cuatro reglas del cable bajaron a la BD y se comprobó que muerden:
      un contrato/solicitud **por oportunidad**, **una sola evaluación FINAL** por solicitud
      (único parcial nativo, en vez de la columna generada de MySQL), el **tipo derivado del
      resultado** (`O`⇒`O`, `A`/`R`⇒`F`) y "documento observado ⇒ observación obligatoria"
      (MEJ-03, como en captaciones).
- [x] **D-F4-1 … D-F4-4 resueltas** (arriba, en §8).
- [x] **Entidades JPA**: `SolicitudAlquiler`, `TipoDocumentoRequerido`, `DocumentoSolicitud`,
      `EvaluacionSolicitud`, `ContratoAlquiler`, `ComisionLiquidacion`. Las **cuatro con máquina
      de estados son `Transicionable`** (solicitud, documento, contrato y comisión) ⇒ se auditan
      solas vía `Transiciones`, coherente con `entidad_tipo.auditable = TRUE` de V2;
      `EvaluacionSolicitud` **no** lo es: es un evento con resultado, y lo que transiciona es la
      solicitud que ese evento mueve. `TipoDocumentoRequerido` es el único **global**
      (declarado en `ArquitecturaTenancyTest`).
- [x] **Repositorios** con tenant y scope en el WHERE. Ojo con las **dos reglas de broker**:
      solicitudes alcanzan **por agente supervisado**; contratos, **por captación** (parámetro
      `porAgente`, igual que en oportunidades). `EvaluacionSolicitudRepository` ya baja a SQL la
      paginación que la v1 hacía en memoria.
- [x] Verificado: `mvn clean install` verde (121 tests) y el API arranca con `ddl-auto: validate`,
      lo que prueba el mapeo de las 6 entidades y el parseo de todas las consultas nuevas.
- [x] `SolicitudService` (interfaz) con los records espejo del contrato.
- [x] **`SolicitudServiceImpl`** — los 5 casos de uso de la solicitud. `mvn clean install`
      verde: **121/121**, incluidos los tres gates de ArchUnit (capas, tenancy y el de
      auditoría, que exige que toda transición pase por `Transiciones`).
      Lo que quedó implementado y por qué:
  - **Las dos reglas invisibles del §2 están puestas y comentadas en su sitio**: el alta exige
    captación ACTIVA + oportunidad ABIERTA y transiciona la oportunidad a `S`; el reenvío exige
    broker supervisor activo. La transición de la oportunidad va por `Transiciones`, así que
    —a diferencia de la v1— **deja fila en `historial_estado`** (mismo MEJ-01 que en F3).
  - **El alta no comprueba que la oportunidad sea del agente**, solo lo fija como responsable
    (cable real, replicado). El **tenant sí acota**: `buscarFicha` lleva la organización.
  - **Alcance por AGENTE** (§7), no por captación. Un BROKER sin supervisados obtiene
    **lista vacía**, no 403.
  - **`plazoTentativo` es derivado**: si vienen `plazoMeses > 0`, `"N meses"` pisa el
    `plazoTentativo` que mande el cliente. Es lo que hace `Dtos.SolicitudRequest.aEntidad`
    y se pierde con facilidad al portar.
  - **Checklist "X/Y" sin N+1**: una sola lectura (`porSolicitudes`) para toda la página; el
    id de la solicitud se lee del proxy LAZY sin inicializarlo.
  - Nueva consulta en `SupervisionAgenteRepository`: **`tieneSupervisorActivo`**.
  - **`agente ACTIVO` traducido a Party-Role**: en la v1 era
    `AgenteInmobiliario.estadoAdministrativo`; en la v2 no existe esa columna, así que se
    mapea a la **vigencia de su `persona_rol`** (`PersonaRol.estaVigente()`). `DISPONIBLE`
    sigue siendo `detalle_agente.estado_operativo = 'D'`.

  **Dos decisiones que tomé al portar** — la primera, RESUELTA el 2026-07-29:
  1. ~~**Dos mensajes que NO existen en el cable v1**~~ → **RESUELTA. La premisa era falsa.**
     Se decía que la v1 "simplemente reventaba contra el único de la BD" con un 500. No: su
     `ApiExceptionMapper` atrapa la `SQLIntegrityConstraintViolationException` y responde
     **409** con el mensaje genérico *"Ya existe un registro con esos datos: un dato único está
     duplicado."* —el propio comentario del código dice "antes caía al 500"—, y el
     `ManejadorErroresApi` de la v2 ya porta ese mapeo con la misma cadena literal.
     Es decir, los dos `if` no añadían un mensaje donde no había ninguno: **convertían un 409 en
     un 400**, que es justo la divergencia de código de estado que el contrato prohíbe.
     Lo hecho:
     - **`existeDeOportunidad` se quitó entero**: era código muerto —este mismo E2E demostró que
       la precondición "oportunidad ABIERTA" corta antes— y lo defiende `uq_solicitud_oportunidad`.
     - **`existeCodigo` se conserva pero lanza `ConflictoException` → 409**, no `ReglaNegocioException`
       → 400. Adelantarse a la BD solo sirve para nombrar el código en conflicto en vez del
       genérico; el índice único sigue siendo el guardián real y es quien cubre la carrera entre
       dos altas simultáneas. El texto de un 409 no está congelado (sí lo están los de 401/403/429).
  2. **`formaPago` se valida en el service** con el mensaje del cable
     (`"Valor invalido para forma de pago: {x}"`), que en la v1 vive en el DTO web. Al escribir
     los DTOs, **no duplicar la validación con otro mensaje**.
- [x] **Los otros cuatro services** —`DocumentoSolicitudService`, `EvaluacionService`,
      `ContratoService` (con la cascada de §6) y `ComisionService`—, todos sobre `Transiciones`.
      `mvn clean install` verde: **192/192**, con los tres gates de ArchUnit. El API arranca en
      Docker con `ddl-auto: validate`, que es lo que prueba el mapeo y el parseo de las
      consultas (ArchUnit no las mira).
      Lo que hay que saber para tocarlos:
  - **La cascada de §6 deja CUATRO filas en `historial_estado`** —oportunidad `A|S→F`,
    solicitud `A→C`, captación `A→C`, propiedad `D→N`—, todas con el mismo motivo y el
    actor. La v1 movía esos cuatro estados a mano y no auditaba ninguno (MEJ-01). El
    contrato **nace** con `iniciar()`, así que él no suma fila.
  - **Alcance de contratos por CAPTACIÓN** (§7), no por agente: el `porAgente` del repositorio
    distingue las dos ramas. No unificar con solicitudes.
  - **`ComisionService` no tiene alcance**: la liquidación cuelga 1:1 del contrato y quien
    autoriza es `ContratoService`, que es el que conoce la regla por captación. Es el mismo
    reparto que hacía `ContratosRest` con su BL.
  - **Los mensajes del REST v1 van en `ContratoService`, los de la BL en `ComisionService`**,
    porque el cable los emite en ese orden: `"Indica el monto del agente."` corta **antes** de
    buscar el contrato, y `"El monto del agente debe ser cero o positivo."` es la regla de la
    BL. Al escribir los DTOs, **no duplicar los dos primeros**.
  - **La frontera con la web**: `DocumentoSolicitudService` **no ve binarios**. base64,
    octet-stream, trozos, extensión, tamaño y almacén son del controlador —igual que en las
    fotos de F2—; al service solo llega el metadato con `rutaArchivo` = clave del almacén.
  - **Dos columnas pasaron a asignarse en la aplicación**: `documento_solicitud.fecha_entrega` y
    `evaluacion_solicitud.fecha_evaluacion` estaban como `insertable = false` confiando en el
    `DEFAULT now()`; así, la respuesta del **POST** viajaba sin fecha (Hibernate no relee la fila
    insertada). Ahora las fija el caso de uso, como hacía la v1, y el DEFAULT queda de red.
  - **`ContratoAlquilerRepository.buscar` pasó a fetch join** (todo a-uno, la paginación sigue
    en SQL): con joins normales, cada fila de `GET /contratos` —que por defecto trae **100**—
    disparaba las lazy de cliente, propiedad, propietario y agente. La v1 ya resolvía ese
    listado en 2 consultas y no había que perder eso.
- [x] **D-F4-5 RESUELTA (2026-07-29): se cierra el hueco.** `PATCH {id}/documentos/{idDoc}/revisar`
      no comprobaba el alcance sobre la solicitud en el cable v1 (solo el rol), mientras que
      `documentos/conformar` y la evaluación sí. Decisión de equipo: **taparlo antes del corte**,
      porque ser el único de tres hermanas sin comprobación delata un olvido de la v1, no una
      regla. La v2 llama a `acceso.conAcceso(idSolicitud, actor)` y la petición de un broker ajeno
      pasa de **200 a 403**. Divergencia deliberada y acotada; el Blazor no la alcanza por
      navegación. Actualizados el test que fijaba el hueco y el check del E2E que lo daba por
      abierto.
- [x] **Controllers + DTOs congelados y almacén** — `SolicitudesController` (con las tres vías de
      subida), `EvaluacionesController` y `ContratosController`, más los 11 DTOs espejo y
      `web/almacen/NombresArchivo`. Los binarios reusan el `AlmacenDisco` de F2 y se sirven por
      `/documentos/contenido`. `mvn clean install` verde: **192/192** con los tres gates de
      ArchUnit —incluido el que prohíbe que la web toque dominio o persistencia—.
      Lo que hay que saber para tocarlos:
  - **Los dos detalles que se pierden al portar están puestos**: el `tamano` por defecto de
    `/contratos` es **100** (no 10) y los dos gates de comisión son `hasRole('BROKER')` **sin**
    ADMIN. Esto último funciona porque el filtro JWT publica **una sola** authority
    (`ROLE_<rol>`): un ADMIN no lleva `ROLE_BROKER`.
  - **Una reordenación deliberada respecto de la v1**: la v1 validaba el código del tipo de
    documento en el REST, *antes* de escribir en el almacén. En la v2 el vocabulario vive en el
    service (la web no ve el dominio — regla de capas), así que el tipo se valida al registrar y,
    si falla, el controlador **borra el binario recién subido**. El cable es idéntico (mismo 400,
    mismo mensaje) y el E2E comprueba el invariante: tantos binarios en el almacén como
    documentos en el expediente. Es el mismo patrón de las fotos de F2.
  - **Mejora invisible en la subida por trozos**: la v1 indexaba el buffer solo por `uploadId`, con
    lo que dos cargas podían pisarse entre tenants. La v2 antepone organización y solicitud a la
    clave. El cliente manda el mismo `uploadId` y recibe lo mismo.
  - `POST {id}/documentos/local` **no existe** (D-F4-1); el E2E lo fija con un 404/405.
- [x] **`verificacion/e2e-f4-solicitud.ps1`** — lo que los tests de service no cubren: los gates de
      rol, los códigos HTTP, la forma exacta del JSON y las tres vías de subida contra la BD real.
      **116/116**, con la cascada del §6 verificada efecto por efecto (incluidas las cuatro filas
      de `historial_estado` y la baja de las publicaciones).
      Dos hallazgos de la corrida, que ya están anotados en el propio script:
  - **El mensaje "Ya existe una solicitud para la oportunidad comercial." es inalcanzable por el
    camino normal**: el alta mueve la oportunidad a `S`, y en el segundo intento la precondición
    *"La oportunidad comercial debe estar ABIERTA."* corta antes. Es el orden del cable v1
    (validaciones de negocio primero, único de la BD después), así que la paridad se mantiene —
    pero de las dos decisiones a confirmar del §9, ésta pierde casi todo su peso: lo que protege
    de verdad es el índice único por oportunidad.
  - **Sin `Content-Type` el recurso responde 415**, igual que el `@Consumes(JSON)` de la v1
    (JAX-RS asume octet-stream cuando falta la cabecera). Queda fijado como regresión.
- [ ] Tests que **siguen faltando**, todos de `SolicitudServiceImpl` (los otros cuatro services
      ya los tienen): captación no ACTIVA, oportunidad no ABIERTA, oportunidad ajena (**debe
      pasar**: es el cable), reenvío desde un estado que no sea G/O, reenvío sin supervisor
      activo, alcance de BROKER por agente supervisado, BROKER sin equipo → lista vacía, y el
      contador X/6 con un documento OBSERVADO (deja de contar) y con uno de tipo `P`/`O` (no
      suma).
- [ ] Al cerrar F4: quitar el 400 fijo de `POST /oportunidades/{id}/cierre-exitoso`… **no**.
      Se queda: el cable v1 responde 400 ahí para siempre y el cierre lo produce el contrato.

---

## 10. F4 en el SPA Angular — CERRADA (2026-08-02)

Seis pantallas, no siete: `Solicitudes`, `SolicitudesRevisar`, `SolicitudForm`,
`SolicitudDetail` (expediente **+ cierre del alquiler**), `Documentos` y `Evaluación`.

**`Cierre.razor` no se porta porque ya está migrada.** Pese a su nombre, esa pantalla del Blazor
no cierra un alquiler: cierra una **captación**, y esa acción vive en `CaptacionDetail` desde el
corte de F2. Portarla como página-silo obligaría a elegir en un desplegable la captación que ya
se está mirando. El cierre de F4 —registrar el contrato— está donde el legado lo tenía: dentro
del expediente de la solicitud.

### Una extensión aditiva del backend

`GET /solicitudes` gana `idAgente`, `estado`, `distrito` y `texto`, y estrena
`GET /solicitudes/resumen`. **Omitidos los cuatro, el cable responde byte a byte como la v1**,
incluido el orden congelado por id descendente. La razón es la de siempre: las dos bandejas
filtran y cuentan, y los KPI no se pueden derivar de una página de diez filas — el Blazor
descargaba todas las solicitudes del alcance y filtraba, contaba y derivaba las listas de
distritos y agentes en memoria.

- **`estado=PENDIENTES` no es un estado.** Es el cubo `{E, O}` de la cola del broker, resuelto en
  el repositorio como `GESTION` en prospecciones, para que esa cola salga en **una** consulta
  paginada. El resumen lo devuelve ya sumado (`pendientes`), así la pantalla no lo recalcula.
- **El resumen no acepta `estado`, `distrito` ni `idAgente`**: son justo lo que devuelve. Sí
  comparte `texto` con la tabla, y cuenta sobre el MISMO conjunto de candidatos.
- **Cinco ramas de búsqueda** (§5 de `contrato-listados-paginados.md`): código de solicitud,
  código de oportunidad, dirección y distrito de la propiedad, nombre del cliente y nombre del
  agente. **V26** añade el trigrama del código de la solicitud y el índice de recorrido por
  tenant; las otras cuatro ramas ya estaban cubiertas por V11 y V25.
- Fila nueva en `matriz-operacion-rol.md` (146 operaciones) y gate
  `verificacion/e2e-solicitudes-busqueda.ps1`, **48/48 sobre 100.000 filas** (2026-08-02):
  discriminante 32–147 ms de p95, no discriminante y paginación profunda bajo RC-003, planes
  por trigrama sin `Seq Scan` de tablas grandes y el `OR` prohibido cayendo a `Seq Scan` sobre
  el mismo banco.

### Decisiones de pantalla que conviene no deshacer

1. **El alta y el envío a evaluación son dos pasos.** El Blazor los encadenaba en un botón,
   subiendo documentos por el medio; cuando algo fallaba a mitad dejaba la solicitud creada sin
   que el usuario supiera en qué punto estaba. Aquí nace REGISTRADA, el agente completa el
   expediente y desde ahí la envía.
2. **Cargar un documento y reenviar la solicitud no son la misma condición.** El broker puede
   observar un documento suelto sin devolver la solicitud entera —que sigue en `E`—; si cargar
   dependiera de poder reenviar, el agente no podría subsanarlo. Se carga mientras la solicitud
   no esté resuelta; se reenvía solo desde `G`/`O`.
3. **El checklist dibuja seis filas, no las ocho del cable.** `PODER_REPRESENTACION` y `OTRO` se
   pueden subir pero no cuentan: pedirlos en una lista de "requeridos" mentiría sobre el avance.
   Si hay varios documentos del mismo tipo, gana el último — es el que el broker verá.
4. **Una sola vía de subida: octet-stream.** base64 infla un tercio el cuerpo y la subida por
   trozos existe por un bug del cliente .NET que muere con el Blazor. `documentos/local` no
   existe (D-F4-1).
5. **No se aprueba con documentos observados sin resolver.** Es regla de la casa, no del backend,
   y evita aprobar una solicitud cuya propia revisión dijo que estaba mal. La pantalla ofrece las
   dos salidas: validarlos u observar la solicitud entera.
6. **El tipo de evaluación no se ofrece en pantalla** porque lo deriva el resultado. El servicio
   lo calcula y lo manda igual, porque el request lo exige **presente** aunque lo pise (§4).
7. **La comisión del cierre es una estimación** con la fórmula del backend, y si la captación no
   se pudo leer **no se inventa un número**. La liquidación real la escribe la cascada.
8. **El desembolso inicial se muestra concepto por concepto**: garantía y adelanto son del
   propietario —la garantía además se devuelve—, la comisión es de la inmobiliaria. Sumarlos en
   un único número es lo que hace creer al cliente que paga tres meses al propietario.
9. **El 404 de `GET /contratos/oportunidad/{id}` es el caso normal** mientras la operación sigue
   viva. Tratarlo como error llenaría de rojo todos los expedientes abiertos.
10. **`SolicitudesRevisar` lleva gate de rol aunque su listado no lo tenga.** Lo que es de
    BROKER/ADMIN es `POST /evaluaciones`, la decisión a la que conduce cada fila.
