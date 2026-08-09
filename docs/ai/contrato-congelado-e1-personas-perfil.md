# Contrato congelado E1 — personas internas, asignaciones y perfil

Estado: **IMPLEMENTADO, CONGELADO y VERIFICADO** (2026-07-29).

Fuente de verdad del cable: `AgentesRest`, `BrokersRest`, `AsignacionesRest`,
`PerfilRest` y sus DTOs en `backend-java/`. Durante el Strangler se conservan
rutas, métodos, formas JSON, códigos de estado y rarezas del legado. Las
mejoras de producto se difieren hasta retirar GlassFish.

## 1. Decisiones de E1

1. `/perfil` replica únicamente el contrato existente: consulta, edición de
   teléfono y foto. **No se añade cambio de contraseña**; la pantalla Blazor
   actual es un mock sin llamada HTTP.
2. Agentes y brokers conservan las validaciones efectivamente ejecutadas por
   la v1. Se reutilizan de `service/soporte/Personas` los vocabularios y la
   construcción Party-Role, pero no se aplica automáticamente la validación
   completa DNI/RUC de propietarios si la v1 no la ejecutaba.
3. `supervision_agente` sigue siendo la fuente de la asignación vigente. El
   historial se conserva en una tabla-evento `reasignacion_agente_broker`,
   como exige la arquitectura objetivo: anterior, nuevo, autorizador, motivo
   y fecha-hora son datos históricos, no inferencias.
4. Toda consulta filtra primero por `organizacion_id`. Las unicidades de
   documento, correo, usuario y códigos son por organización.
5. Las listas y contadores bajan a SQL. No se reproducen `listarTodos()` ni
   los N+1 del legado cuando la respuesta puede mantenerse idéntica.
6. D-20 sigue vigente durante la convivencia: el JWT no cambia y el login
   solo busca credenciales en la organización configurada (`BROX_LEGACY`).
   La selección funcional de tenant se difiere hasta retirar GlassFish.

## 2. `/agentes`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `/agentes?pagina&tamano` | BROKER, ADMIN | — | `PageResponse<AgenteResponse>` |
| POST `/agentes` | BROKER, ADMIN | `AgenteRequest` | 201 + `AgenteResponse` |
| PUT `/agentes/{id}` | BROKER, ADMIN | `AgenteRequest` | `AgenteResponse` |

**No existe DELETE**: la baja es administrativa, un PUT con `estado='I'`.

### Extensiones aditivas (2026-08-03)

La v1 no tenía GET individual ni búsqueda; se añadieron porque sin ellas la
ficha del agente habría que armarla combinando páginas de cuatro bandejas —lo
que además da números falsos, porque cada listado pagina— y el catálogo solo se
podría filtrar en el navegador, sobre las diez filas cargadas.

| Método y ruta | Rol | Response |
|---|---|---|
| GET `/agentes/{id}` | BROKER, ADMIN | `AgenteFichaResponse` |
| GET `/agentes/resumen?texto&estado&estadoOperativo` | BROKER, ADMIN | `AgentesResumenResponse` |

Y `GET /agentes` acepta ahora `texto`, `estado`, `estadoOperativo` y `zona`.
**Los cuatro son aditivos**: omitidos, la respuesta es exactamente la del cable
v1, orden por id descendente incluido.

Reglas que fija la ficha:

- **Alcance**: el BROKER solo abre la de los agentes que supervisa **hoy**;
  fuera de su equipo responde **403**. El ADMIN alcanza el tenant. Se comprueba
  una sola vez, al entrar: las consultas de dentro ya no vuelven a filtrar por
  rol, o la ficha mostraría números distintos de los que el agente ve en sus
  bandejas.
- **Los cierres y el dinero salen de la atribución histórica de V27**
  (`contrato_alquiler.id_rol_agente_cierre`), no de la cadena solicitud→agente:
  un agente que cambió de equipo conserva su historia, que es justo lo que una
  ficha de persona tiene que mostrar.
- **Cuatro magnitudes de comisión, separadas y por moneda**, porque responden
  preguntas distintas: `generada` (bruto pactado), `cobrada` (lo que entró,
  descontando reversiones), `asignadaAgente` (lo que el broker le adjudicó) y
  `pagadaAgente`. Los dos saldos —`pendienteCobro` y `pendientePagoAgente`— son
  diferencias derivadas y **nunca negativas**: un cobro de más no se publica
  como pendiente negativo. PEN y USD nunca se suman.
- Los conteos de captaciones, oportunidades y solicitudes llegan **por estado y
  con su descripción**, no como un total suelto, y cuentan **toda** la
  trayectoria del agente: una captación cerrada o rechazada también es trabajo
  suyo.
- Sin supervisión vigente, `supervision` viaja **ausente**. No se rellena con el
  supervisor de turno: es información que no existe.

En el resumen, `estado` es el **administrativo** (vive en la credencial) y
`estadoOperativo` el del agente: son dos máquinas distintas y por eso hay dos
cubos. Un agente activo puede estar de vacaciones. `zonas` recorre el **alcance
completo** —son las opciones del selector— así que el resumen **no** acepta el
filtro `zona`, que es justo el que acotaría lo que devuelve.

`AgenteRequest`: `nombre, tipoPersona, tipoDocumento, numeroDocumento,
telefono, correo, usuario, contrasena, zona, codigoAgente, estado,
estadoOperativo`.

`AgenteResponse`: `id, codigoAgente, nombre, tipoPersona, tipoDocumento,
numeroDocumento, telefono, correo, usuario, zona, fechaIngreso,
estadoAdministrativo, estadoOperativo, captacionesActivas,
operacionesActivas`.

Reglas del cable:

- La lista usa página 1 y tamaño 50 por defecto. ADMIN ve todos; BROKER solo
  sus agentes supervisados.
- `captacionesActivas` cuenta estados `P`, `O`, `A`.
  `operacionesActivas` cuenta estados `A`, `S`.
- POST exige nombre, usuario y contraseña:
  `"Nombre, usuario y contrasena del agente son obligatorios."`.
- Defaults de alta: persona natural (`N`), DNI (`D`), estado administrativo
  activo (`A`), operativo disponible (`D`), fecha de ingreso de hoy y código
  `AGE-%03d` si no llega.
- El alta crea atómicamente una persona, roles `USUARIO_INTERNO` y `AGENTE`,
  credencial, detalle de agente y supervisión vigente por el broker en sesión.
- Aunque el gate admite ADMIN, el administrador no puede ser supervisor de
  un agente operativo:
  `"El broker administrador no registra agentes operativos; debe hacerlo el broker responsable del equipo."`.
- PUT solo cambia nombre si no está vacío, teléfono/correo si llegan, estado
  administrativo si llega, estado operativo si llega y zona si llega. No
  cambia documento, tipos, usuario, contraseña, código ni fecha.
- Un BROKER solo actualiza agentes que supervisa. ADMIN actualiza cualquiera.
- POST y PUT responden los dos contadores en `0`; solo GET de lista calcula
  los valores reales.

## 3. `/brokers`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `/brokers?pagina&tamano` | cualquier sesión | — | `PageResponse<BrokerResponse>` |
| GET `/brokers/{id}` | cualquier sesión | — | `BrokerResponse` |
| GET `/brokers/{id}/agentes` | cualquier sesión | — | `List<AgenteResponse>` |
| POST `/brokers` | ADMIN | `BrokerRequest` | 201 + `BrokerResponse` |
| PUT `/brokers/{id}` | ADMIN | `BrokerRequest` | `BrokerResponse` |

No existe DELETE.

`BrokerRequest`: `nombre, tipoPersona, tipoDocumento, numeroDocumento,
telefono, correo, usuario, contrasena, zona, codigoBroker, estado,
esAdministrador`.

`BrokerResponse`: `id, codigoBroker, nombre, tipoPersona, tipoDocumento,
numeroDocumento, telefono, correo, usuario, zona, fechaDesignacion,
estadoAdministrativo, esAdministrador, agentesACargo`.

Reglas del cable:

- La lista usa página 1 y tamaño 50 por defecto y es global dentro del tenant.
- `agentesACargo` cuenta supervisiones vigentes.
- POST exige nombre, usuario y contraseña:
  `"Nombre, usuario y contrasena del broker son obligatorios."`.
- Defaults: persona natural (`N`), DNI (`D`), estado activo, fecha de
  designación de hoy y código `BRK-%03d`.
- El alta crea atómicamente persona, roles `USUARIO_INTERNO` y `BROKER`,
  credencial y detalle.
- Solo puede existir un broker administrador por organización:
  `"Solo debe existir un broker administrador."`.
- PUT cambia nombre si no está vacío, teléfono/correo si llegan, estado si
  llega y zona si llega. No cambia documento, tipos, usuario, contraseña,
  código, fecha ni `esAdministrador`.
- `/{id}/agentes` devuelve agentes sin contadores comerciales (ambos en 0).

## 4. `/asignaciones`

Todas las operaciones exigen ADMIN.

| Método y ruta | Request | Response |
|---|---|---|
| GET `/asignaciones/agentes` | — | `List<AsignacionAgenteResponse>` |
| GET `/asignaciones/brokers` | — | `List<AsignacionBrokerResponse>` |
| GET `/asignaciones/historial` | — | `List<BrokerAgenteResponse>` |
| POST `/asignaciones/reasignar` | `ReasignarAgenteRequest` | `BrokerAgenteResponse` |

`ReasignarAgenteRequest`: `idAgente, idBrokerDestino, motivo`.

`AsignacionAgenteResponse`: `idAgente, nombre, numeroDocumento,
estadoAdministrativo, estadoOperativo, brokerActual`.

`AsignacionBrokerResponse`: `idBroker, nombre, zona,
estadoAdministrativo, esAdministrador, agentesACargo`.

`BrokerAgenteResponse`: `id, idAgente, agenteNombre, idBrokerAnterior,
brokerAnteriorNombre, idBrokerNuevo, brokerNuevoNombre,
idBrokerAdministrador, brokerAdministradorNombre, fechaCambio, motivo`.

Reglas del cable:

- Agente y broker destino son obligatorios:
  `"El agente y el broker destino son obligatorios."`.
- El motivo no vacío es obligatorio:
  `"El motivo de reasignacion de agente es obligatorio."`.
- El broker administrador no puede ser destino:
  `"El broker administrador no requiere asignacion de agentes para supervisar."`.
- El agente debe estar administrativo ACTIVO y operativo DISPONIBLE.
- Reasignar al supervisor actual responde:
  `"El agente ya esta asignado a ese broker supervisor."`.
- La operación cierra la supervisión anterior, crea la nueva y registra el
  evento histórico en la misma transacción.
- El historial se ordena por `id` descendente.

## 5. `/perfil`

Todas las operaciones admiten cualquier sesión autenticada.

| Método y ruta | Request | Response |
|---|---|---|
| GET `/perfil` | — | `PerfilResponse` |
| PATCH `/perfil` | `PerfilRequest` | `PerfilResponse` |
| POST `/perfil/foto` | `FotoRequest` | `FotoResponse` |

DTOs:

- `PerfilRequest`: `telefono`.
- `PerfilResponse`: `nombre, correo, telefono, fotoClave`.
- `FotoRequest`: `nombreArchivo, contenidoBase64`.
- `FotoResponse`: `clave`.

Reglas del cable:

- PATCH solo actúa si llega `telefono`; lo recorta y exige entre 6 y 15
  dígitos, contando únicamente caracteres numéricos:
  `"Ingresa un telefono valido de entre 6 y 15 digitos."`.
- Foto obligatoria:
  `"La foto es obligatoria."`.
- Solo extensión `.png`, `.jpg` o `.jpeg`:
  `"Solo se permiten imagenes PNG o JPG."`.
- Base64 inválido, archivo vacío y máximo de 5 MB conservan los mensajes de
  la v1.
- La v1 valida extensión, no firma binaria. No se añade aquí la validación de
  magic bytes que usa `/locales`.
- La clave opaca se guarda en `persona.foto_clave`.
- Un error del almacén responde 502.

## 6. Gate de corte — cerrado

- [x] Tests de comportamiento para cada service nuevo.
- [x] Reactor completo verde: **321 pruebas** al cortar; **464** tras las
      extensiones aditivas del 2026-08-03 (431 de servicios + 5 web + 28 de
      aplicación, estas últimas **sin saltos** contra PostgreSQL real, que es lo
      que compila de verdad el JPQL nuevo).
- [x] `verificacion/e2e-personas.ps1` ampliado con una sección por recurso:
      **99/99** al cortar, **122/122** con las extensiones (ficha del agente,
      coherencia del dinero, búsqueda y cubos de agentes y propietarios).
- [x] Aislamiento real mientras existe el fixture de una segunda organización:
      su credencial no autentica en el tenant legado, `/brokers` no mezcla sus
      filas y un GET directo responde 404.
- [x] La reasignación prueba atómicamente supervisión vigente + tabla-evento
      V10. El E2E detectó y corrigió el orden de flush necesario para que el
      UPDATE de la supervisión anterior ocurra antes del INSERT de la nueva.
- [x] Gates de rol explícitos y filtros tenant en las consultas nuevas.
