# Contrato congelado F2 — Prospección + Captación

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

> Ingeniería inversa del cable REAL de `ProspeccionesRest.java` y `CaptacionesRest.java`
> (backend Jakarta) hecha el 2026-07-14, para implementar la vertical F2 en `backend-spring/`.
> Regla del Strangler: forma, códigos de estado CHAR(1), mensajes y semántica se replican
> BYTE a BYTE; el rediseño de la forma llega tras el corte del módulo.

## 1. Alcance de la vertical F2

**Entra**: máquinas de estado de prospección y captación completas (sobre
`service/soporte/Transiciones` ⇒ emiten `historial_estado` automáticamente, cosa que la
v1 NO hacía — mejora MEJ-01 sin cambiar el cable), scoping por rol, y el cierre de las
3 deudas del módulo Locales (`GET /locales/mis-locales`, regla "el agente solo edita
locales de sus captaciones", prospección inicial al crear local).

**Se difiere, con stub explícito 501 o ausencia documentada**:
- `GET {id}/coincidencias` (ambos recursos) → módulo requerimientos/matching (F3). **Cortado.**

**Fuera del alcance de la migración (D-F5-1, 2026-07-30)**:
- Los 4 PDF Jasper de captación (`contrato-exclusividad`, `ficha-captacion`,
  `ficha-propiedad`, `reportes-propietario/{id}`). Ya no son un diferido a F5/F8: **no se
  portan**. Ver `decision-reportes-pdf-fuera-de-alcance.md`.

## 2. `/prospecciones`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope por rol) | `?pagina=1&tamano&estado&distrito&idCaptacion&idLocal&idAgente&idBrokerSupervisor&q&orden` | `PageResponse<ProspeccionResponse>` (orden por defecto: id descendente; `orden=ultimo_contacto` cambia el criterio) |
| GET `recontactar` | sesión | `?dias=7&pagina&tamanio` | `PageResponse<ProspeccionResponse>` (recontacto vencido) |
| GET `{id}` | sesión con acceso | — | `ProspeccionResponse` |
| POST `` | **AGENTE** | `ProspeccionRequest{idLocal, observaciones}` | 201 + `ProspeccionResponse` |
| POST `{id}/contactar` | **AGENTE** dueño | — | `ProspeccionResponse` |
| POST `{id}/reunion` | **AGENTE** dueño | — | `ProspeccionResponse` |
| POST `{id}/propuesta` | **AGENTE** dueño | — | `ProspeccionResponse` |
| POST `{id}/seguimiento` | **AGENTE** dueño | — | `ProspeccionResponse` (reinicia reloj de recontacto, 7 días) |
| POST `{id}/rechazar` | **AGENTE** dueño | `RechazoProspeccionRequest{motivo}` | `ProspeccionResponse` |
| POST `{id}/descartar` | **AGENTE** dueño | `RechazoProspeccionRequest{motivo}` | `ProspeccionResponse` |
| POST `{id}/captar` | **AGENTE** dueño | `CaptarProspeccionRequest{operacion, importe, moneda, comisionPactada, …}` — **cambió el 2026-08-21 (V75)** | `ProspeccionResponse` (crea captación P) |
| POST `{id}/marcar-captado` | **AGENTE** dueño | `MarcarProspeccionCaptadaRequest{idCaptacion, codigoCaptacion}` | `ProspeccionResponse` |
| GET `{id}/coincidencias` | — | — | **DIFERIDO a F3** |

**Máquina de estados** (`EstadoProspeccion`, códigos del cable):
`P` Prospecto → `C` Contactado → `R` Reunión → `S` En seguimiento → `T` Captado;
`D` Descartado (desde cualquier estado activo, vía rechazar/descartar).
Eventos: contactar→C, reunion→R, propuesta→S, seguimiento→S, captar→T, rechazar/descartar→D.
**Ojo (cable real, verificado en `Prospeccion.entregarPropuesta()` v1)**: el estado
`E` Propuesta entregada existe en el enum y en el CHECK de la BD, pero la v1 NUNCA
lo emite — entregar la propuesta deja `S` y la marca de la propuesta es
`fechaPropuesta` + `resultadoPropuesta='P'`. La v2 replica eso (guardia en
`ProspeccionServiceImplTest.laPropuestaEntregadaQuedaEnSeguimientoComoElCableV1`).

**ProspeccionResponse** (25 campos, se emite `non_null`):
`id, codigoProspeccion, localId, localCodigo, direccion, distrito, areaM2, rubro,
precioReferencial, propietarioNombre, idAgente, agenteNombre, estado, resultadoPropuesta,
fechaContacto, fechaReunion, fechaPropuesta, fechaRecontacto, observaciones, idCaptacion,
captacionCodigo, disponibilidad`.

## 3. `/captaciones`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope) | congelado: `?pagina&tamano`; aditivo Angular: `estado&idAgente&q` | `PageResponse<CaptacionResponse>` |
| GET `pendientes` | **BROKER/ADMIN** (gate del filtro v1) | congelado: paginación; aditivo Angular: `estado&idAgente&q` | `PageResponse<CaptacionResponse>` |
| GET `reasignables` | BROKER/ADMIN | paginación | `PageResponse<CaptacionResponse>` |
| GET `{id}` · GET `codigo/{codigo}` | sesión con acceso | — | `CaptacionResponse` |
| POST `` | **AGENTE** | `CaptacionRequest` | 201 + `CaptacionResponse` |
| PUT `{id}` | **AGENTE** dueño | `CaptacionRequest` | `CaptacionResponse` (si estaba `O` Observada ⇒ **reenvío a `P` Pendiente**) |
| POST `{id}/decision` | **BROKER/ADMIN** | `DecisionRequest{accion, observacion}` | `CaptacionResponse` (accion ⇒ A/O/R) |
| POST `{id}/reasignar` | **BROKER/ADMIN** | `ReasignacionRequest{idAgenteNuevo, motivo}` | `CaptacionResponse` (evento de actor → tabla-evento + timeline, NO transición) |
| POST `{id}/cierre` | **BROKER/ADMIN** | `CierreRequest{motivo}` | `CaptacionResponse` |
| GET `{codigo}/…/pdf` (4 rutas) | — | — | **FUERA DE ALCANCE (D-F5-1)**: no se portan |
| GET `{idOrCodigo}/coincidencias` | — | — | cortado con F3 |

**Máquina de estados** (`EstadoCaptacion`): `P` Pendiente de revisión → decisión broker →
`A` Activa | `O` Observada | `R` Rechazada; `O` →(PUT actualizar)→ `P`;
`A` → `C` Cerrada (cierre) | `V` Vencida (vigencia). Invariante v1 a preservar:
**una sola captación ACTIVA por local** (en v2 = índice único parcial `WHERE estado='A'`).

**CaptacionRequest**: `codigoCaptacion, fechaCaptacion, fechaInicioVigencia, fechaFinVigencia,
comisionPactada, observaciones, idLocal, idAgente, motivoOperacion, urgencia, exclusividad`.
**CaptacionResponse** añade: `estado, observacionRevision, fechaRevision, direccionLocal,
distritoLocal, areaM2, rubro, propietarioNombre, agenteNombre, idBrokerRevisor, fotoPortadaClave`.
`motivoOperacion` viaja fijo `'A'` durante la convivencia (semilla de OperacionComercial).
**ReasignacionCaptacionResponse**: `idReasignacion, idCaptacion, codigoCaptacion, direccionLocal,
idAgenteAnterior/agenteAnteriorNombre, idAgenteNuevo/agenteNuevoNombre, idBroker/brokerNombre,
fechaCambio, motivo`.

### Hallazgos al cortar las bandejas Angular (2026-08-01)

- La implementación v2 del listado había omitido dos comportamientos del cable v1:
  `estado=GESTION` es el cubo activo `{P,C,R,E,S}` —no un estado persistido— e
  `idBrokerSupervisor` acota al equipo del broker seleccionado. Ambos se aplican ahora en SQL y
  tienen guardas en `ProspeccionServiceImplTest`.
- `GET /captaciones` conserva su respuesta congelada si solo recibe paginación. Para que la
  bandeja Angular no descargue toda la cartera como el Blazor, admite tres filtros **aditivos y
  opcionales** (`estado`, `idAgente`, `q`); filtro, alcance, orden y paginación bajan al mismo
  `WHERE`.
- En el SPA, **Datos del local** nombra el registro técnico-operativo (`/locales/:id`) y
  **Resumen comercial** la vista ligada a la captación (`/captaciones/:codigo/ficha`): galería,
  condiciones pactadas, comisión y responsables. Se retiraron los rótulos ambiguos “Ver local” y
  “Ficha propiedad” de esas acciones.
- `ProspeccionDetail` no espera el estado `E`: después de `propuesta` habilita seguimiento y alta
  de captación sobre el `S` real. BROKER/ADMIN leen el mismo expediente, pero solo AGENTE ve las
  transiciones. Interacciones y coincidencias no se simulan aquí: sus pantallas se cortan con F3.
- `CaptacionForm` cubre alta libre, alta desde una prospección S/E y edición P/O. La edición de una
  observada presenta `observacionRevision` y el PUT real la devuelve a P. En el alta desde
  prospección primero se crea la captación completa y luego se llama `marcar-captado`; si el segundo
  paso falla, el formulario retiene el id/código creado para reintentar solo el vínculo y no duplicar.

### D-F2-1 — El periodo del encargo es obligatorio siempre (decisión de equipo, 2026-08-01)

La v1 solo exigía `fechaInicioVigencia`/`fechaFinVigencia` para **activar**: `POST /prospecciones/{id}/captar`
creaba el borrador PENDIENTE sin periodo (`ProspeccionBusinessLogicImpl.captar`) y la semilla V5 sembró
así CAP-0001, que se veía en la bandeja con el periodo en blanco. `POST`/`PUT /captaciones` sí lo exigían
desde el principio —`validarEncargo`, 400 “El inicio y fin del encargo son obligatorios.”—, igual que el
formulario Angular.

Se cierra el hueco en la v2: `captar` completa el encargo con el defecto de la casa (**fecha de captación
+ 6 meses**, el mismo que propone el formulario) y `V21__encargo_captacion_obligatorio.sql` rellena las
filas históricas y pone **NOT NULL** en ambas columnas. El agente puede corregir el plazo con
`PUT /captaciones/{id}` mientras la captación siga P u O.

**Es una divergencia de DATOS, no de contrato**: `CaptarProspeccionRequest{comisionPactada}`,
`ProspeccionResponse`, los códigos de estado y los mensajes no cambian. `ck_captacion_activa_completa`
sigue vigente: cubre además exclusividad y condición económica. Los fixtures SQL que insertaban
captaciones a mano (`e2e-e4-dashboard`, `e2e-reportes-propietario`, `v6-dos-organizaciones`) viajan ahora
con el periodo; en el gate de FK compuesta importa especialmente, porque sin él la inserción moriría por
`not_null_violation` antes de llegar a la FK que ese gate mide.

## 4. Mapa de implementación en `backend-spring/` (para la sesión que lo construya)

1. **V5__proceso_prospeccion_captacion.sql**: tablas `prospeccion` (1:1 local, agente=persona_rol,
   fechas de hitos, recontacto) y `captacion` (+ `reasignacion_captacion` como tabla-evento),
   índice único parcial "captación activa por local", índices `(agente, estado, fecha)` heredados.
2. Entidades `Prospeccion`/`Captacion` implementan `Transicionable` (entidadTipo PROSPECCION /
   CAPTACION, ya sembrados en `entidad_tipo`); TODAS las transiciones vía `Transiciones` (el
   ArchUnit de auditoría lo fuerza).
3. Services: `ProspeccionService` (eventos de la máquina + reloj de recontacto 7 días vía
   `soporte/Fechas`), `CaptacionService` (decisión con observación obligatoria en O/R — MEJ-03,
   reasignación como evento de actor, cierre), reglas de scope (agente=self, broker=sus agentes
   vía `supervision_agente`, admin=global).
4. Controllers con las rutas EXACTAS de arriba (gates `@PreAuthorize` equivalentes a los
   `exigirRol` v1); coincidencias y PDFs ausentes (los consume solo Blazor, que sigue en GlassFish).
5. Cerrar las 3 deudas de Locales anotadas en `backend-spring/README.md` §deudas.
6. Seed DEMO: 2-3 prospecciones y 1 captación pendiente sobre los locales DEMO de V4.
