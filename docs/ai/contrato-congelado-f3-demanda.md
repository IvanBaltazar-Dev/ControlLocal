# Contrato congelado F3 — Demanda (cliente, requerimiento, oportunidad, visita, interacción)

> Ingeniería inversa del cable REAL de `ClientesRest`, `RequerimientosRest`, `OportunidadesRest`,
> `VisitasRest`, `InteraccionesRest` y `CoincidenciaCarteraSupport` (backend Jakarta), hecha el
> 2026-07-27 para implementar la vertical F3 en `backend-spring/`.
> Regla del Strangler: rutas, forma, códigos de estado, mensajes y semántica se replican
> BYTE a BYTE; el rediseño llega tras el corte del módulo.

## 1. Alcance de la vertical F3

**Entra**: el lado de la DEMANDA completo — cliente interesado, su perfil de búsqueda
(requerimiento), la oportunidad comercial como entidad-hub, las visitas y la bitácora de
interacciones —, más el **matching de cartera** (`coincidencias`), que quedó diferido de F2.

**Se difirió durante F3 y ya quedó resuelto**:
- `GET /clientes/{id}/ficha-comercial[/{section}]` → `FichaComercialSupport` agrega datos de
  solicitudes/contratos. Se cortó y verificó en **E3 (2026-07-29)**; su contrato separado es
  `contrato-congelado-e3-ficha-comercial.md`.
- `POST /oportunidades/{id}/cierre-exitoso` **existe pero SIEMPRE responde 400** (ver §4): el
  cierre exitoso lo produce la solicitud aprobada (F4). Se replica el 400 tal cual.
- Las alertas/tareas derivadas (`PROPONER_OPORTUNIDAD`) → módulo transversal F6.

**Ojo con los códigos de enum**: F3 rompe la convención CHAR(1) de F2. `EstadoRequerimiento`,
`TipoInmuebleComercial` y buena parte de `ResultadoInteraccion` emiten el **nombre completo**
(`getCodigo()` devuelve `name()`), y `ResultadoInteraccion` es **mixto**: conviven códigos de
1 carácter (`P`, `I`, `N`, `S`, `D`) con códigos-palabra (`CONTACTADO`, `VISITA_AGENDADA`…).
No "normalizar" nada de esto durante la convivencia.

## 2. `/clientes`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope) | `?pagina=1&tamano=10` | `PageResponse<ClienteResponse>` |
| GET `{id}` | sesión con acceso | — | `ClienteResponse` |
| POST `` | **AGENTE** | `ClienteRequest` | 201 + `ClienteResponse` |
| PUT `{id}` | **AGENTE** | `ClienteRequest` | `ClienteResponse` |
| DELETE `{id}` | **AGENTE** | — | 204 (baja lógica) / 404 |
| GET `{id}/coincidencias` | sesión con acceso | `?page&pagina&page_size&tamano` (default 6) | `CoincidenciasResponse` (ver §7) |
| GET `{id}/ficha-comercial[/{section}]` | sesión con acceso | aliases de paginación | **CORTADO EN E3** |

**Alcance por rol — la rareza a preservar**: el cliente es **catálogo compartido**.
`ADMIN` y `AGENTE` ven y paginan **todos** los clientes (`contar()` + `listarPagina()` en SQL);
solo el **BROKER** queda acotado, y su conjunto se deriva de las oportunidades y solicitudes de
sus agentes y de sus captaciones (ids ordenados desc, paginados en memoria sobre esos ids).
Un agente puede editar cualquier cliente: no hay regla de pertenencia.

**ClienteRequest**: `tipoPersona, tipoDocumento, numeroDocumento, nombre, telefono, correo,
rubroComercial, consentimientoContacto, consentimientoUsoDato, estado`.
**ClienteResponse**: `id, tipoPersona, tipoDocumento, numeroDocumento, nombre, telefono, correo,
rubroComercial, estado, consentimientoContacto, consentimientoUsoDato, fechaCreacion`.

`PUT` solo toca `telefono/correo/nombre` (vía `actualizarDatos`), `rubroComercial`, los dos
consentimientos y —si llega `estado` no vacío— el estado de la persona. **No** cambia documento.

## 3. `/requerimientos`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `cliente/{idCliente}` | sesión | — | `List<RequerimientoResponse>` |
| POST `` | **AGENTE** | `RequerimientoRequest` | 201 + `RequerimientoResponse` |
| PUT `{id}` | **AGENTE** | `RequerimientoRequest` | `RequerimientoResponse` |
| POST `{id}/estado` | **AGENTE** | `EstadoRequerimientoRequest{estado}` | `RequerimientoResponse` |

**Estados** (`EstadoRequerimiento`): ~~`ACTIVO`, `PAUSADO`, `CERRADO`~~ → **`A`, `P`, `C`**.
Solo los `A` alimentan el matching (§7).

> **Corrección (2026-08-02).** Esta línea se escribió antes de la normalización V15–V20, que
> llevó `requerimiento_cliente.estado` a la convención de una letra del resto del sistema
> (V18 hizo el backfill). El cable emite y acepta `A`/`P`/`C`; la fuente de verdad de los
> códigos de estado es `docs/ai/matriz-codigos-estado.md`, no este documento. El SPA los
> traduce a texto natural en pantalla y envía siempre la letra.

**RequerimientoRequest/Response**: `idCliente, rubro, tipoInmueble, rentaMin, rentaMax, moneda,
metrajeMin, metrajeMax, frenteMinimo, estado, observaciones, distritos: List<String>`
(+ `id, fechaCreacion, fechaActualizacion` en la respuesta). `distritos` viaja como **nombres**,
no ids. `tipoInmueble` usa `TipoInmuebleComercial`: `LOCAL_COMERCIAL, OFICINA, DEPOSITO_ALMACEN,
STAND_MODULO, TERRENO_COMERCIAL, OTRO`.

**Validaciones** (mensajes exactos): "Los datos del requerimiento son obligatorios." ·
"El cliente del requerimiento es obligatorio." · "El rubro del requerimiento es obligatorio." ·
"El estado del requerimiento es obligatorio." · "Estado de requerimiento no valido: {x}".
El POST verifica que el cliente exista (404 `Cliente`). El PUT conserva el cliente actual si el
request no trae `idCliente`.

## 4. `/oportunidades`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope) | `?pagina&tamano&idCaptacion&idCliente&query` | `PageResponse<OportunidadResponse>` |
| GET `{id}` | sesión con acceso | — | `OportunidadResponse` |
| POST `` | **AGENTE** | `OportunidadRequest` | 201 + `OportunidadResponse` |
| POST `{id}/no-continuidad` | **AGENTE** con acceso | `NoContinuidadRequest{razon, observaciones}` | `OportunidadResponse` |
| POST `{id}/cierre-exitoso` | **AGENTE** con acceso | — | **400 SIEMPRE** (ver abajo) |

**Máquina de estados** (`EstadoOportunidadComercial`): `A` Abierta → `S` Solicitud creada →
`F` Finalizada exitosa | `X` Finalizada no favorable; `A` → `N` No continúa.
El alta abre en `A` y fija `fechaRegistro`/`fechaPrimeraConsulta`. `no-continuidad` registra un
`MotivoNoContinuidad` (razón + observaciones + agente) y lleva a `N`.
`S`, `F` y `X` los produce la vertical de solicitudes (F4), no F3.

**El 400 deliberado**: `POST {id}/cierre-exitoso` valida rol y acceso y luego **siempre** lanza
`400` con el mensaje exacto _"El cierre exitoso se registra desde la solicitud aprobada para crear
el contrato de alquiler."_. Es cable real que el frontend consume: se replica, no se "arregla".

**Regla de alta**: la captación indicada debe ser **del agente que registra** (se comprueba con
`listarPorAgente`), si no → 403. Validaciones: "Los datos de la oportunidad son obligatorios." ·
"Selecciona un cliente interesado." · "Selecciona una captacion activa."

**Alcance por rol**: AGENTE = las suyas; ADMIN = todas; BROKER = las de **sus captaciones**
(`listarPorBroker`), no las de sus agentes. Cualquier otro rol → 403.

**OportunidadRequest**: `codigoOportunidad, idCliente, idCaptacion, observaciones,
idPublicacionOrigen`. Si `codigoOportunidad` viene vacío se autogenera como
`"OP-" + yyMMddHHmmss`.
**OportunidadResponse**: `id, codigoOportunidad, idCliente, clienteNombre, idCaptacion,
codigoCaptacion, direccionLocal, distritoLocal, idAgente, agenteNombre, estado, fechaRegistro,
motivoCierre, observaciones, fechaCierre, fechaActualizacion, idPublicacionOrigen`.

`MotivoNoContinuidadTipo` (razón): `P` Precio, `U` Ubicación, `C` Condiciones del contrato,
`L` Local no adecuado, `N` Cliente no responde, `E` Encontró otra opción, `O` Otro.

## 5. `/visitas`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope) | `?pagina&tamano&idOportunidad&estado&distrito&query` | `PageResponse<VisitaResponse>` |
| GET `proximas` | sesión (scope) | `?tamano=8` (**tope duro 8**) | `PageResponse<VisitaResponse>` (`total`=items, page=1) |
| GET `mes` | sesión (scope) | `?anio&mes` | `PageResponse<VisitaResponse>` (sin paginar) |
| GET `{id}` | sesión con acceso | — | `VisitaResponse` |
| POST `` | **AGENTE** dueño de la oportunidad | `VisitaRequest` | 201 + `VisitaResponse` |
| PATCH `{id}/reprogramar` | **AGENTE** con acceso | `ReprogramarVisitaRequest{fechaVisita, horaVisita}` | `VisitaResponse` |
| PATCH `{id}/cancelar` | **AGENTE** con acceso | `CancelarVisitaRequest{motivo}` | `VisitaResponse` |
| PATCH `{id}/realizar` | **AGENTE** con acceso | — | `VisitaResponse` |
| PATCH `{id}/no-realizada` | **AGENTE** con acceso | `NoRealizadaVisitaRequest{motivo}` | `VisitaResponse` |
| PATCH `{id}/resultado` | **AGENTE** con acceso | `ResultadoVisitaRequest` | `VisitaResponse` |

**Máquina de estados** (`EstadoVisita`): `P` Programada → `G` Reprogramada (reprogramar, desde
P o G) → `R` Realizada | `N` No realizada | `C` Cancelada.
Guardas del modelo, con **mensajes exactos**:
- `realizar` / `no-realizada` solo desde P o G: _"Solo una visita programada o reprogramada puede
  marcarse como realizada."_ / _"…como no realizada."_
- `resultado` exige `R` Realizada: _"Primero debe marcar la visita como realizada."_, y es
  **irrepetible**: _"La visita ya tiene un resultado registrado."_
- `cancelar` y `no-realizada` escriben el motivo en `observaciones` y **limpian el desenlace**.

**Ojo con el alta**: `POST /visitas` exige que la oportunidad sea **del propio agente**
(comparación directa con `idDominio`, sin alcance de broker) → 403 en caso contrario. Además, si
el refetch posterior falla, la v1 **devuelve la entidad recién creada** en vez de propagar un 500
(defensa deliberada para que el alta no parezca no persistida).
`GET mes` valida `2000 ≤ anio ≤ 2100` y `1 ≤ mes ≤ 12`: _"El mes solicitado no es valido."_

**ResultadoVisitaRequest**: `resultado, observaciones, razonNoContinuidad, nivelInteres,
objecionPrincipal, opinionPrecio, proximaAccion` (los cuatro últimos, opcionales por código).
**VisitaResponse**: `id, idOportunidad, codigoOportunidad, fechaVisita, horaVisita, observaciones,
estado, resultado, idCliente, clienteNombre, idCaptacion, codigoCaptacion, direccionLocal,
distritoLocal, idAgente, agenteNombre, nivelInteres, objecionPrincipal, opinionPrecio,
proximaAccion`.

Enums del desenlace: `ObjecionVisita` `P/U/E/C/O` · `OpinionPrecio` `A/J/B` ·
`ProximaAccionVisita` `V/O/S/D`.

**Alcance por rol**: AGENTE = suyas; ADMIN = todas; BROKER = las de sus captaciones. En `{id}` el
broker resuelve la captación de la visita **o**, si no la tiene, la de su oportunidad.

## 6. `/interacciones`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `` | sesión (scope) | `?pagina&tamano=50&contexto&idOportunidad&idProspeccion&idCaptacion&idCliente&grupo&resultado&canal&q` | `PageResponse<InteraccionResponse>` |
| GET `{id}` | sesión con acceso | — | `InteraccionResponse` |
| POST `` | **AGENTE** | `InteraccionRequest` | 201 + `InteraccionResponse` |
| PUT `{id}` | **AGENTE** con acceso | `InteraccionRequest` (solo `resultado` y `observaciones`) | `InteraccionResponse` |

Es la **bitácora polimórfica** del sistema: una interacción cuelga de UNA de cuatro entidades,
según `contexto` ∈ `OPORTUNIDAD | PROSPECCION | CAPTACION | CLIENTE`.

**Derivación del contexto** en el POST: si no viene, se infiere por el id presente en este orden
—`idProspeccion` → PROSPECCION, `idCaptacion` → CAPTACION, `idCliente` → CLIENTE, si no
OPORTUNIDAD—; se normaliza a mayúsculas y se valida:
_"Contexto de interaccion invalido: {x}"_. Luego exige el id de esa entidad:
_"La prospeccion de la interaccion es obligatoria."_ / _"La captacion…"_ /
_"El cliente interesado…"_ / _"La oportunidad…"_.

**Allow-list de `resultado` por contexto** (se replica tal cual; mensaje
_"Resultado no valido para {contexto}: {codigo}"_):

| Contexto | Resultados permitidos |
|---|---|
| PROSPECCION | `CONTACTADO, REUNION_AGENDADA, PROPUESTA_ENVIADA, ACEPTA_CAPTAR, NO_ACEPTA, RECONTACTAR` |
| CAPTACION | `DOCS_SOLICITADOS, CONDICIONES_AJUSTADAS, PUBLICACION_COORDINADA, PROPIETARIO_OBSERVA, LISTO_PARA_PUBLICAR, PAUSAR_GESTION` |
| CLIENTE | `BUSQUEDA_LEVANTADA, PROPUESTA_ENVIADA, REQUIERE_OPCIONES, NO_RESPONDE, SEGUIMIENTO, DESCARTADO` |
| OPORTUNIDAD (default) | `INTERESADO, VISITA_AGENDADA, OFERTA_SOLICITADA, NEGOCIANDO, NO_INTERESADO, DESCARTADO` |

`canalContacto` es **obligatorio** en el alta (`CanalContacto`: `L` Llamada, `W` WhatsApp,
`E` Email, `P` Presencial, `R` Reunión, `T` Portal, `O` Otro):
_"El canal de contacto es obligatorio."_ / _"Canal de contacto invalido: {x}"_.

**Filtros y orden**: solo se admite **un** filtro de entidad a la vez, si no →
_"Filtra por una sola entidad de interaccion."_. `grupo` parte el universo en dos:
`PROPIETARIO` = contexto PROSPECCION o CAPTACION; cualquier otro valor (≠ `TODAS`) = el
complemento. Orden: `fechaHora` desc (nulls last) y luego `idInteraccion` desc.
La búsqueda `q` cae sobre código de prospección/captación, nombre de cliente, nombre de agente
y observaciones — **antes** de paginar.

**Alcance por rol**: ADMIN = todo; BROKER = las de sus agentes supervisados; AGENTE = las suyas.
Se filtra por el **agente responsable de la interacción**, no por la entidad colgada.

**InteraccionRequest**: `contexto, idOportunidad, idProspeccion, idCaptacion, idCliente,
canalContacto, resultado, observaciones, transcripcionNota`.
**InteraccionResponse**: `id, contexto, idOportunidad, idProspeccion, idCaptacion, idCliente,
idPropietario, codigoProspeccion, fechaHora, canalContacto, resultado, observaciones,
transcripcionNota, clienteNombre, propietarioNombre, personaTipo, personaNombre, codigoCaptacion,
agenteNombre`.

## 7. Coincidencias (matching de cartera) — la deuda de F2

Tres entradas, misma forma de respuesta:

| Ruta | Sentido |
|---|---|
| GET `/clientes/{id}/coincidencias` | cliente → propiedades (captaciones activas con local disponible) |
| GET `/captaciones/{idOrCodigo}/coincidencias` | captación → clientes (accionable: "Proponer") |
| GET `/prospecciones/{id}/coincidencias` | prospección → clientes (señal temprana; accionable **solo** si la prospección ya tiene captación) |

Paginación: `?page|pagina` y `?page_size|tamano`, **default 6, máximo 24**.

**CoincidenciasResponse**: `origen, total, page, pageSize, items`.
**CoincidenciaResponse**: `tipo, id, codigo, titulo, subtitulo, distrito, renta, area, frente,
puntaje, cumple: List<String>, noCumple: List<String>, clienteId, captacionId, proponerRuta`.

**Regla de puntaje** (`bl/support/CoincidenciaCartera`, lógica pura y sin estado — conviene
portarla igual, a `service/soporte/`): se evalúan **6 criterios** —distrito, rubro, tipo de
inmueble, renta, área y frente—; cada uno da CUMPLE / NO_CUMPLE / **NO_APLICA** (dato faltante en
el requerimiento o en el local). El puntaje es `round(100 * cumplidos / aplicables)`, o `0` si no
aplica ninguno. Cada criterio aporta además una frase legible a `cumple`/`noCumple`.
Detalles que hay que calcar: distrito y rubro comparan **normalizados** (sin acentos, minúsculas)
y el rubro acepta coincidencia por inclusión en cualquier dirección; el tipo solo se evalúa si
hay equivalencia 1:1 (`LOCAL_COMERCIAL→LOCAL`, `OFICINA→OFICINA`, `TERRENO_COMERCIAL→TERRENO`;
depósito/stand/otro ⇒ NO_APLICA). Solo entran requerimientos en estado `ACTIVO`, se conserva la
**mejor** evaluación por cliente/local, se descarta `puntaje <= 0` y se ordena por puntaje desc.

## 8. Mapa de implementación en `backend-spring/` (para la sesión que lo construya)

1. **V7__demanda.sql**: `cliente_interesado` (rol CLIENTE del Party-Role, no tabla de persona
   nueva), `requerimiento_cliente` + `requerimiento_distrito` (N:M con el catálogo `distrito`),
   `oportunidad_comercial`, `visita`, `interaccion_comercial` (polimórfica: 4 FK opcionales +
   `contexto`, con CHECK de "exactamente una") y `motivo_no_continuidad`.
   **Todas privadas del tenant** ⇒ heredan `EntidadDeOrganizacion` (V6); las unicidades de código
   (`codigo_oportunidad`) van por `(organizacion_id, codigo)`.
2. Entidades `OportunidadComercial` y `Visita` implementan `Transicionable` (nuevos códigos en
   `entidad_tipo`) ⇒ **todas** sus transiciones vía `Transiciones`, que ya emite `historial_estado`
   con actor y motivo. Es la mejora MEJ-01 gratis sobre la v1, sin tocar el cable.
   La interacción NO es transicionable (es un evento, como `reasignacion_captacion`).
3. Services: `ClienteService` (ojo: catálogo compartido, alcance solo para BROKER),
   `RequerimientoService`, `OportunidadService` (alta con captación propia; no-continuidad;
   el 400 fijo de cierre-exitoso), `VisitaService` (máquina + desenlace) e
   `InteraccionService` (contexto polimórfico + allow-list de resultado).
4. `soporte/CoincidenciaCartera` portado tal cual desde `bl/support` (lógica pura ⇒ test unitario
   directo, sin BD) y expuesto en las tres rutas de §7.
5. Controllers con las rutas EXACTAS de §2–§7 y los gates `@PreAuthorize` equivalentes a los
   `exigirRol` v1. Recordar los **PATCH** de visitas (F2 solo usó GET/POST/PUT/DELETE).
6. Alcance por rol vía `Alcances`, con la salvedad de que aquí el BROKER se resuelve **por
   captación** en oportunidades y visitas, y **por agente supervisado** en interacciones. No
   unificar: son dos reglas distintas del cable real.
7. Seed DEMO: 2 clientes con requerimiento ACTIVO sobre los locales de V4, 1 oportunidad abierta
   y 1 visita programada, para que las bandejas y el matching tengan con qué responder.

## 9. Estado de ejecución

**COMPLETA y verificada E2E (2026-07-27).** La vertical responde entera contra la BD real.

- [x] Contrato congelado (este documento)
- [x] **V7__demanda_cliente_oportunidad.sql**: las 7 tablas, todas tenant-scoped, con las FK
      compuestas de V6, el CHECK polimórfico de la interacción, el CHECK "desenlace solo si la
      visita se realizó" y el seed DEMO. Validada sobre una copia antes de aplicarse; **aplicada**
      en la BD de desarrollo (Flyway v7).
- [x] Entidades JPA: `DetalleCliente`, `RequerimientoCliente`, `OportunidadComercial`,
      `MotivoNoContinuidad`, `Visita`, `InteraccionComercial`. `OportunidadComercial` y `Visita`
      son `Transicionable` ⇒ sus transiciones se auditarán solas vía `Transiciones`.
      Todas heredan `EntidadDeOrganizacion` (lo exige `ArquitecturaTenancyTest`).
- [x] Repositorios con el tenant y el scope en el WHERE. **Ojo con las dos reglas de broker**:
      oportunidades y visitas alcanzan **por la captación** del equipo (parámetro `porAgente`
      distingue las ramas); interacciones, **por agente responsable**.
- [x] Services: `ClienteService`, `RequerimientoService`, `OportunidadService`, `VisitaService`,
      `InteraccionService`, `CoincidenciaService` + `soporte/CoincidenciaCartera` (lógica pura
      portada de `bl/support`) y `soporte/Vocabulario` (los códigos del cable compartidos por
      visitas e interacciones).
- [x] Controllers + DTOs congelados de los 5 recursos y las 3 rutas de coincidencias.
- [x] Tests de service con los mensajes del cable: `OportunidadServiceImplTest` (16),
      `VisitaServiceImplTest` (23), `InteraccionServiceImplTest` (17) y `CoincidenciaCarteraTest` (8).
- [x] Verificado: `mvn clean install` verde (**121 tests** en el reactor: 110 de servicios,
      5 de web, 6 de arquitectura) y **E2E `verificacion/e2e-f3-demanda.ps1` en 89/89** contra
      la BD real, con las tres bandas de rol. El E2E de F2 (`e2e-v6.ps1`) sigue en 46/46.

### Lo que el E2E dejó fijado (y sorprende al leerlo)

1. **`Transiciones.iniciar()` no escribe `historial_estado`**: nacer no es transicionar, así que
   una oportunidad recién registrada tiene **0** filas de auditoría y solo los `aplicar()`
   posteriores cuentan. Misma convención que F2 (donde prospección y captación cuentan 3 = sus
   tres `aplicar`). Una visita completa (programar → reprogramar → realizar) deja **2**.
2. **Matching con "vista personal"** (`CoincidenciaServiceImpl.idsClientesDelActor`): para un
   actor no-ADMIN la demanda propia son los clientes que **ya tienen oportunidad del equipo**.
   Un cliente recién creado, aunque su requerimiento case al 100 %, **no** aparece todavía en
   `captación → clientes` para el AGENTE; el ADMIN sí lo ve porque va sin restricción. La
   dirección inversa (`cliente → propiedades`) no tiene esa restricción.
3. **El `id` del sobre de coincidencias no es lo que parece**: en `cliente → propiedades` el
   `id` es el de la **captación** (la oferta viaja por su captación), no el de la propiedad; en
   `captación/prospección → clientes` es el del cliente. `proponerRuta` viaja como **cadena
   vacía** —no `null`— cuando la coincidencia no es accionable.
4. El catálogo de clientes es compartido para **ADMIN y AGENTE**; el **BROKER** es el único rol
   con alcance (los clientes de su equipo).

### Dos desvíos del contrato corregidos al verificar (2026-07-27)

- **Orden de validación en `POST /interacciones`**: la v2 resolvía el agente **antes** de exigir
  el id de la entidad del contexto, así que un request sin ids respondía _"Agente no encontrado
  para interaccion."_ en vez de _"La oportunidad de la interaccion es obligatoria."_. En la v1 el
  id lo valida el REST (`InteraccionesRest`) y el agente lo valida después el BL
  (`InteraccionComercialBusinessLogicImpl`): ese es el orden bueno. Corregido en
  `InteraccionServiceImpl.registrar` y blindado con test unitario + check E2E.
- **Método HTTP equivocado devolvía 500**: el mapper Jakarta conserva el estado de
  `WebApplicationException` (405/415); el `ManejadorErroresApi` de Spring no tenía handler y todo
  caía en el catch-all. Añadidos los handlers de `HttpRequestMethodNotSupportedException` (405) y
  `HttpMediaTypeNotSupportedException` (415), con el cuerpo `{"error": ...}` de siempre.
