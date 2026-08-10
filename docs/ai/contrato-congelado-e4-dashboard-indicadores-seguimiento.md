# Contrato congelado E4 — dashboard, indicadores y seguimiento comercial

> **El "congelado" del título es histórico.** El contrato se descongeló el
> 2026-08-09 (`decision-contrato-v2-descongelado.md`): DTOs, endpoints, estados y
> errores pueden cambiar con razón funcional y con sus pruebas.
>
> Este documento **describe el comportamiento vigente** y se sigue actualizando
> —no es historia—, pero la autoridad son **las pruebas y OpenAPI**, no este
> texto. Si discrepan, manda la suite.

**Estado:** CONGELADO, CORTADO Y VERIFICADO (2026-07-29)
**Fuente de verdad:** `backend-java/controllocal-rest/.../DashboardRest.java` (62),
`IndicadoresRest.java` (813) y `SeguimientoComercialRest.java` (684), mas
`Dtos.java` §"Indicadores".
**Verificacion:** `verificacion/e2e-e4-dashboard.ps1` **115/115** y las dos suites
dedicadas de service (`IndicadorServiceImplTest` 27, `SeguimientoComercialServiceImplTest`
17). Reactor completo: **388 pruebas**.

E4 es el ultimo bloque de backend: los tres recursos que **agregan sobre las
verticales ya cortadas**. Por eso van al final — no leen nada nuevo, releen todo
lo anterior.

## 1. Alcance del corte

| Metodo | Ruta | Que es |
|---|---|---|
| GET | `/dashboard` | home por rol: resumen + primera pagina de la bandeja |
| GET | `/indicadores/resumen` | KPIs, series, donut, embudo, desempeno y operativo |
| GET | `/indicadores/avance` | RF-017: avance comercial por propiedad (acumulado) |
| GET | `/seguimiento-comercial` | vista transversal de las 5 etapas del proceso |

**Fuera de alcance de la migración (D-F5-1, 2026-07-30):**
`GET /indicadores/reporte/pdf`. Es un Jasper (`reporte_indicadores.jasper`) y
sigue la misma suerte que los 4 PDF de F2 y el de reportes-propietario: **no se
porta**, y ya no es un diferido a F5/F8. El JSON del que se alimenta
—`/resumen`— sí queda cortado, así que cuando se diseñe la nueva página de
reportes no hará falta consulta nueva. Ver
`decision-reportes-pdf-fuera-de-alcance.md`.

**Ninguno crea tablas**: E4 no lleva migracion Flyway. Agrega lecturas sobre
V4–V10.

**Autorizacion**: los tres recursos v1 no llevan `@RolesAllowed`; basta estar
autenticado. ADMIN, BROKER y AGENTE entran a los tres. La unica pieza con rol es
la bandeja embebida en `/dashboard`, que sale de `/tareas` (solo AGENTE).

## 2. Alcance por rol

Los tres recursos resuelven el alcance por su cuenta y **no coinciden entre si**.
Hay dos reglas distintas y conviene no unificarlas:

**Indicadores (`/resumen` y `/avance`) — solo por AGENTE.**
`agentesEnAlcance(usuario)` devuelve:

- ADMIN → `null` = sin filtro de rol (en la v2: dentro del tenant),
- AGENTE → `{ su propio id }`,
- BROKER → los agentes que supervisa hoy.

Y **todas** las fuentes se filtran por `agenteResponsable ∈ alcance`. La
captacion no participa del alcance en este recurso: un broker **no** ve la
oportunidad de un agente ajeno aunque cuelgue de una captacion que el reviso.

**Seguimiento comercial — por AGENTE _o_ por CAPTACION (union).**
Una fila entra si su agente esta en el equipo **o** si su captacion pertenece a
un agente del equipo. Es la union, no un `switch` entre las dos ramas como en
`/oportunidades`.

**Contratos: el alcance es indirecto.** El contrato no tiene agente propio. La
v1 lo resuelve por su solicitud y, en su defecto, por su oportunidad — y lo hace
sobre las listas **ya acotadas**: el DAO entrega la solicitud/oportunidad del
contrato "shallow" (solo el id) y `enriquecerContrato` solo la completa si esa
solicitud/oportunidad esta en el alcance. El efecto neto, que hay que replicar:

> un contrato entra si `solicitud.agente ∈ alcance` **o**
> `oportunidad.agente ∈ alcance`; y el agente con el que se agrupa es el de la
> **solicitud** cuando esta en alcance, y si no, el de la **oportunidad**.

## 3. `GET /indicadores/resumen`

### 3.1 Periodo

`?periodo=` se normaliza (minusculas, trim) contra esta tabla; **cualquier otro
valor, incluido ausente o vacio, cae en 6 meses**:

| Entrada | Codigo | Dias |
|---|---|---|
| `7`, `7d`, `semana` | `7d` | 7 |
| `15`, `15d` | `15d` | 15 |
| `1m`, `30`, `30d`, `mes` | `1m` | 30 |
| `3m`, `90`, `90d` | `3m` | 90 |
| `1y`, `12m`, `365`, `365d`, `ano`, `anio` | `1y` | 365 |
| *(resto)* | `6m` | 180 |

La ventana es `[hoy - (dias - 1), hoy]`, ambos inclusive, **sobre la fecha
propia de cada entidad**: captacion → `fechaCaptacion`, oportunidad →
`fechaRegistro`, solicitud → `fechaRegistro`, visita → `fechaVisita`,
interaccion → `fechaHora`, contrato → `fechaCierre`, prospeccion →
`fechaRegistro`. Las que llevan hora se comparan por su fecha.

### 3.2 Campos que NO dependen del periodo

Se calculan sobre todo el alcance, sin ventana, porque alimentan los pills
accionables del menu:

- `captacionesPorRevisar` = captaciones en estado `P`
- `captacionesPendientes` = **el mismo numero** que `captacionesPorRevisar`
- `captacionesObservadas` = estado `O`
- `captacionesActivas` = estado `A`
- `solicitudesPorEvaluar` = solicitudes en `E` u `O`
- `oportunidadesActivas` = oportunidades en `A` o `S`
- `propiedadesEquipo` = propiedades **distintas** de las captaciones `A`
- `etapas` (el donut) — ver §3.4

### 3.3 Campos del periodo

- `captacionesTotales` = captaciones de la ventana
- `interacciones`, `visitas` = filas de la ventana
- `cierres` = contratos con `fechaCierre` en la ventana
- `cierresCohorte` = captaciones **de la ventana** que ya tienen contrato
- `conversionPropia` = `porcentaje(cierresCohorte, captacionesTotales)`, y
  **`null` si `captacionesTotales == 0`** (E2.0) — ver §3.11
- `captacionesSalud`, `embudo`, `desempeno` — ver abajo

`porcentaje(parte, total)` = `0` si `total <= 0`; si no,
`min(100, round(parte * 100.0 / total))`. El redondeo es *half-up* de
`Math.round` sobre `double`.

### 3.4 `etapas` — el donut es una particion EXCLUSIVA

Recorre **todas** las captaciones del alcance (sin ventana) y cada una cae en
**una sola** etapa, en este orden de precedencia:

1. tiene contrato → `Alquilada` (cuenta aunque la captacion ya figure `C`);
2. si su estado **no** es `A` → no cuenta en ninguna etapa (las que siguen en
   aprobacion del broker no son embudo);
3. tiene solicitud en `E`, `O` o `A` → `En evaluacion`;
4. tiene alguna solicitud → `Con solicitud`;
5. tiene alguna oportunidad → `Clientes interesados`;
6. si no → `Captacion activa`.

Nombres exactos y orden del array: `Captacion activa`, `Clientes interesados`,
`Con solicitud`, `En evaluacion`, `Alquilada`.

La captacion de un contrato se resuelve por `solicitud.captacion` y, si falta,
por `oportunidad.captacion` — con el matiz "shallow" del §2: solo cuentan los
contratos cuya solicitud/oportunidad esta en el alcance.

### 3.5 `captacionesSalud` — 4 cubos, SI acotados al periodo

`Activas` (`A`), `Por revisar` (`P`), `Observadas` (`O`),
`Bloqueadas/cerradas` (`R`, `V` o `C`). No son exclusivos con `etapas`: son otra
lectura de las mismas captaciones, esta vez las nacidas en la ventana.

### 3.6 `embudo` — sobre las oportunidades del periodo

| Etapa | Valor | Porcentaje |
|---|---|---|
| `Oportunidades activas` | oportunidades de la ventana (`base`) | **100 fijo** |
| `Con visita realizada` | oportunidades de la ventana con **alguna visita de la ventana** | `porcentaje(v, base)` |
| `Con solicitud creada` | oportunidades de la ventana en `S` o `F` | `porcentaje(v, base)` |
| `Cerradas exitosas` | oportunidades de la ventana en `F` | `porcentaje(v, base)` |

**Bug congelado:** *"Con visita realizada"* no mira el estado de la visita —
cuenta oportunidades con visita de cualquier estado, incluida una `CANCELADA`.
El nombre miente y se replica.

**Bug congelado:** la primera fila lleva `100` aunque `base` sea `0`.

### 3.7 Series

Dos series con las **mismas** etiquetas:

- `periodo.dias <= 31` → un cubo por dia desde `inicio` hasta `hoy`, etiqueta
  `dd/MM` con cero a la izquierda;
- si no → un cubo por mes desde `YearMonth(inicio)` hasta `YearMonth(hoy)`,
  etiqueta `Mmm YY` con `Ene Feb Mar Abr May Jun Jul Ago Sep Oct Nov Dic` y los
  **dos ultimos digitos** del ano (`Jul 26`).

Campos: `mesesEtiquetas` (las etiquetas), `cierresPorMes` (contratos por cubo),
`captacionesPorPeriodo` (captaciones por cubo) y `conversionPorPeriodo`, que es
elemento a elemento `porcentaje(cierresCohorteEnElCubo, captacionesEnElCubo)`.

`conversionPorPeriodo` se calcula sobre la **cohorte**: el numerador son las
captaciones de la ventana que ya cerraron, agrupadas por su `fechaCaptacion`
—no por la fecha del contrato—. Por eso nunca pasa de 100 %.

### 3.8 `desempeno` — top 8

- **ADMIN → por broker.** Recorre los brokers del tenant **excluyendo al
  administrador** (que es un broker con `esAdministrador`, y no es productor).
  Para cada uno, su equipo vigente; `captaciones` = las de la ventana de ese
  equipo, `cierres` = los contratos de la ventana de ese equipo. Se **omite** el
  broker sin equipo y el que tiene 0 y 0.
- **BROKER / AGENTE → por agente**, sobre los agentes del alcance, con la misma
  regla de omision.
- Orden: `cierres` descendente (estable, sin desempate). Corte: **8 filas**.
- `conversion` = `porcentaje(cierres, captaciones)`.
- `nombre` = `nombresORazonSocial` de la persona; `—` (guion largo) si falta.

### 3.9 `operativo`

~~La fuente de prospecciones es `prosPeriodo.isEmpty() ? pros : prosPeriodo`~~ —
ese fallback se retiro el **2026-08-08** al descongelar el contrato: si la
ventana no tenia ni una prospeccion, "ultimos 7 dias" pasaba a significar "desde
siempre" sin avisar. Un periodo vacio ahora se informa vacio. Visitas y
solicitudes siguen siendo las del alcance completo (sin ventana), y eso si es
del contrato.

- `recontactosVencidos` / `recontactosAlDia`: solo prospecciones con
  `fechaRecontacto` y estado distinto de `T` y `D`. Vencida si
  `fechaRecontacto <= hoy - recontacto.dias`; si no, al dia. **El plazo lo fija
  `PoliticaComercial.RECONTACTO`** (E1, 2026-08-10) y es literalmente el mismo
  objeto que usan la bandeja de F7 y la campana de F6: antes eran cuatro copias
  del numero 7 coordinadas por un comentario.
- `diasPromedioSinSeguimiento` = `round(sum(dias de atraso) / vencidos)`, `0` si
  no hay vencidos.
- `visitasPendientes` = visitas en `P` o `G`.
- `solicitudesSinCierre` = solicitudes en `A` (aprobada y todavia sin contrato).
- `conversionProspeccionCaptacion` = `porcentaje(prospecciones en T, total de la
  fuente)` — el denominador incluye **todos** los estados de esa misma fuente.

### 3.10 Escalares de plantilla

- `ambito` = `Reportes globales` (ADMIN) · `Reportes de equipo` (BROKER) ·
  `Mi actividad` (AGENTE).
- `agentesActivos` = tamano del alcance; para ADMIN, el total de agentes del
  tenant.
- `brokersActivos` = para ADMIN, los brokers **no administradores**; para
  BROKER, `1`; para AGENTE, `0`.

### 3.11 Forma de la respuesta

`IndicadoresResponse`, en este orden de campos:

```
ambito, captacionesPorRevisar, solicitudesPorEvaluar, captacionesTotales,
captacionesActivas, captacionesObservadas,
oportunidadesActivas, interacciones, visitas, cierres, cierresCohorte,
conversionPropia, agentesActivos, brokersActivos, propiedadesEquipo,
mesesEtiquetas[], cierresPorMes[], conversionPorPeriodo[],
captacionesPorPeriodo[], etapas[{nombre,valor}], captacionesSalud[{nombre,valor}],
embudo[{etapa,valor,porcentaje}], desempeno[{nombre,captaciones,cierres,conversion}],
operativo{recontactosVencidos,recontactosAlDia,diasPromedioSinSeguimiento,
          visitasPendientes,solicitudesSinCierre,conversionProspeccionCaptacion},
senales[{concepto,valor,nivelAtencion,requiereAtencion,prioridad}],
pendientesDeAtencion
```

Los numericos viajan siempre y las listas tambien, aunque vacias. **La unica
excepcion es `conversionPropia`, que es nulable a proposito desde E2.0**
(2026-08-10): sin captaciones en el periodo no hay tasa que calcular. Emitir `0`
hacia indistinguible "medi doce y no cerre ninguna" de "no habia nada que
medir", y el dashboard resolvia esa ambiguedad **mostrando la conversion del
agente que mas cerro** como si fuera propia. `null` significa *no calculable*, y
la pantalla lo dice con un guion.

`pendientesDeAtencion` (E2.1) es **cuantas cosas reclaman atencion ahora mismo**:
la suma de las senales pendientes que cuentan unidades. No se puede derivar
sumando `senales` en el cliente —`DEMORA_DE_SEGUIMIENTO` vale dias— y por eso lo
suma el dominio.

`captacionesPendientes` **se retiro el 2026-08-08**: repetia
`captacionesPorRevisar` con otro nombre porque la v1 lo emitia asi (D-E4-3), y
nadie lo pintaba.

### 3.12 `senales` — el hecho ya interpretado (E1, 2026-08-10)

No trae ningun numero nuevo: trae **la lectura** de los que ya viajaban. Antes,
el cliente recibia `diasPromedioSinSeguimiento: 9` y tenia que saber por su
cuenta que mas de 7 es preocupante; era la cuarta copia del plazo, esta vez en
`dashboard.ts`. La regla que lo cierra es **R-07**: el dominio decide *cuando*
algo pasa a ser ALTO, la pantalla solo decide *como se ve* un ALTO.

| Campo | Que es |
|---|---|
| `concepto` | clave estable del dominio, **no rotulo**: `SOLICITUD_POR_EVALUAR`, `RECONTACTO_VENCIDO`, `CAPTACION_POR_REVISAR`, `SOLICITUD_APROBADA_SIN_CIERRE`, `DEMORA_DE_SEGUIMIENTO`, `VISITA_PENDIENTE`, `CIERRE_REGISTRADO`, `COBERTURA_DE_AGENTES` |
| `valor` | el hecho, sin interpretar; sigue viajando porque al usuario le importa cuantos son |
| `nivelAtencion` | `ALTO` · `MEDIO` · `INFORMATIVO` · `SIN_PENDIENTES` |
| `requiereAtencion` | atajo de lo anterior: solo `ALTO` y `MEDIO` |
| `prioridad` | 1 se atiende primero. **Un unico orden para los tres roles** |

Tres formas del cable que conviene no perder:

- **Viaja completa y en orden de `prioridad`.** Los conceptos en cero tambien:
  un cero clasificado es informacion ("no hay nada atrasado"), y omitirlo
  obligaria al cliente a distinguir "no vino" de "no hay".
- **`INFORMATIVO` no baja a `SIN_PENDIENTES` cuando el valor es 0.** Cero visitas
  agendadas no es "todo al dia": es cero.
- **`DEMORA_DE_SEGUIMIENTO` no se clasifica por conteo** sino contra el propio
  plazo de recontacto: preocupa cuando el atraso medio ya supera la ventana
  entera que se daba para volver a llamar.

Que subconjunto ve cada rol y con que palabras lo rotula **es del SPA**, no del
contrato: el backend no emite texto para mostrar.

## 4. `GET /indicadores/avance` (RF-017)

Lectura **acumulada**, sin periodo: una fila por captacion **`A`** del alcance.

Por captacion:

- `oportunidadesTotales` / `oportunidadesAbiertas` (`A`) /
  `cerradasExitosas` (`F`) / `cerradasNoFavorables` (`X`) /
  `cerradasNoContinuidad` (`N`);
- `oportunidadesConVisita` = oportunidades de la captacion con alguna visita;
- `visitasProgramadas` = visitas en `P` o `G`; `visitasConcretadas` = en `R`;
- `solicitudesRecibidas` = solicitudes de la captacion **o** de sus
  oportunidades;
- `oportunidadesConSolicitud` = `max(` oportunidades con solicitud enlazada `,`
  oportunidades en `S` o `F` `)` — el respaldo por estado esta puesto a
  proposito, por si la solicitud no quedo enlazada;
- `interesados` = clientes distintos entre oportunidades y solicitudes;
- `interacciones` = interacciones de la captacion **o** de sus oportunidades;
- `tasaOportVisita` / `tasaOportSolicitud` = `porcentaje(..., totalOps)`;
- `motivoNoContinuidad` = la razon **mas frecuente** entre las oportunidades de
  la captacion (descripcion del motivo); `""` si no hay ninguna;
- `direccion`, `distrito`, `codigoCaptacion` = `""` cuando faltan (no null);
- `estadoComercial` = la **descripcion** del estado de la captacion.

Orden: `oportunidadesAbiertas` desc, luego `interacciones` desc. **Sin tope.**

Agregados de cabecera: la suma de las columnas del detalle, salvo
`interesados`, que es el conteo de clientes **distintos a nivel global** (no la
suma de las filas), y las dos tasas, que se calculan sobre
`oportunidadesTotales` global.

`ambito` = `Avance comercial global` · `Avance comercial del equipo` ·
`Mi avance comercial`.

## 5. `GET /dashboard`

Compone, sin logica propia:

- `indicadores` = exactamente el `/indicadores/resumen` del mismo `periodo`;
- `bandeja` = sobre `PageResponse` con la **primera pagina** de `/tareas`.

Parametros: `periodo` (igual que arriba) y `tamano`, **por defecto 5**,
normalizado a `[1, 100]`.

**Solo el AGENTE tiene bandeja.** Para BROKER y ADMIN, `bandeja` viaja con
`items: []`, `totalRecords: 0`, `page: 1` y el `pageSize` pedido. No es un 403:
es una bandeja vacia.

La bandeja se toma de la fuente de tareas y se recorta a `tamano`. ~~ya cortada
en 10 por el service~~ — el tope se retiro el 2026-08-08 (D-F7-2), asi que
`totalRecords` es el **total real** de tareas abiertas del agente y puede ser
30 o 50. El SPA pide `tamano=5` para la tarjeta de la home y trae el resto con
`GET /tareas` cuando se abre el panel lateral de la bandeja.

## 6. `GET /seguimiento-comercial`

### 6.1 Parametros y aliases

Cada filtro acepta hasta cuatro nombres y **gana el primero no vacio** en este
orden:

| Filtro | Orden de precedencia |
|---|---|
| proceso | `process__eq`, `proceso__eq`, `tipo` |
| busqueda | `query__contains`, `q__contains`, `busqueda__contains`, `q` |
| agente | `agent__eq`, `agente__eq`, `agente` |
| propietario | `owner__eq`, `propietario__eq`, `propietario` |
| estado | `state__eq`, `estado__eq`, `estado` |
| distrito | `district__eq`, `distrito__eq`, `distrito` |

Paginacion: `page` gana a `pagina` (defecto `1`, minimo `1`); `page_size` gana a
`tamano` (defecto `8`) y el tamano efectivo es **`min(8, clamp(valor, 1, 100))`**
— o sea, 8 es el techo, no solo el defecto.

`tipo` por defecto es `Todos`. La comparacion de proceso es exacta sobre el
texto normalizado (trim + minuscula); los demas filtros son *contains* sobre el
texto normalizado.

La busqueda libre casa contra `proceso`, `codigo`, `cliente`, `local`,
`distrito`, `agente`, `propietario` y `estado`.

### 6.2 Las cinco filas

| `proceso` | `codigo` | `estado` | `ultimoHito` | `ruta` | `rutaRevision` | `icono` | `tono` | `fechaOrden` |
|---|---|---|---|---|---|---|---|---|
| `Prospeccion` | codigo de prospeccion, o el del local | descripcion del estado | codigo de la captacion si ya la hay; si no `Propuesta entregada <fecha>` / `Reunion <fecha>` / `Contacto <fecha>` / `Prospecto` | `prospeccion-detail/{id}` | `""` | `store` | `blue` | 1ª no nula de propuesta, reunion, contacto |
| `Captacion` | codigo de captacion | descripcion | `Vigente hasta <dd MMM yyyy>` o `Captada el <dd MMM yyyy>` o `-` | `captacion-detail/{codigo}` | `captacion-review/{codigo}` **solo si esta en `P`** | `pin` | `blue` | 1ª no nula de captacion, inicio de vigencia |
| `Oportunidad` | codigo | descripcion | `fechaActualizacion` o `fechaRegistro`, ISO | `oportunidad-detail/{id}` | `""` | `target` | `info` | esa misma fecha |
| `Solicitud` | codigo | descripcion | `fechaActualizacionEstado`, ISO | `solicitud-detail/{codigo}` | `evaluacion/{codigo}` **solo si esta en `E`** | `fileText` | `gray` | `fechaActualizacionEstado`, si no `fechaRegistro` |
| `Cierre` | codigo de la oportunidad, o el de la solicitud | descripcion del estado del contrato, o `Alquilado` | `fechaCierre` ISO (`""` si falta) | `solicitud-detail/{codigo}`, o `propiedades-alquiladas` | `""` | `checkCircle` | `green` | `fechaCierre` |

- `cliente` solo lo llevan oportunidad, solicitud y cierre; prospeccion y
  captacion mandan `-`.
- `monto` solo lo llevan solicitud y cierre (el `montoPropuesto` de la
  solicitud, en texto plano); el resto manda `""`.
- Cualquier texto ausente viaja como `-` (no null, no vacio): direccion,
  distrito, agente, propietario, cliente, estado.
- El **cierre se arma desde su solicitud**, no desde el contrato: cliente,
  agente, local, propietario, codigo y monto salen de ella. Un contrato cuya
  solicitud no esta en el alcance **desaparece de la lista**.

### 6.3 El propietario se resuelve por mapa

Solo la captacion trae el propietario cargado. Oportunidad, solicitud y cierre
lo resuelven por `id_local` contra un mapa armado con **todas** las captaciones
del tenant; si tampoco esta ahi, la fila manda `-` y `propietarioId` ausente.

Consecuencia congelada: **una fila puede mostrar un propietario que el actor no
alcanza**, porque el mapa no lleva filtro de rol. Se replica (sigue siendo del
mismo tenant).

### 6.4 Orden

`fechaOrden` descendente, luego `proceso` ascendente y luego `codigo`
ascendente. Los tres criterios son estables y no dependen del rol.

**Rareza congelada (verificada, contraintuitiva):** las filas **sin** fecha
**encabezan** la lista, no la cierran. El comparador del cable es
`comparing(fechaOrden, nullsLast(natural)).reversed()`, y ese `.reversed()`
invierte tambien el tratamiento de los nulos: el `nullsLast` de dentro se vuelve
*nulls-first*. Es facil "arreglarlo" sin darse cuenta.

### 6.5 Respuesta

```
items[], totalRecords, page, pageSize,
counts{todos,prospeccion,captacion,oportunidad,solicitud,cierre},
options{agentes[],propietarios[],estados[],distritos[]}
```

- `counts` se calcula sobre las filas que pasan **todos los filtros menos el de
  proceso** — por eso los KPI siguen siendo clicables sin perder el contexto.
- `options` se calcula sobre **todas** las filas visibles, **sin ningun filtro
  aplicado**: descarta nulos, vacios y `-`, deduplica y ordena
  *case-insensitive*.

## 7. Decisiones de E4

- **D-E4-1 — El PDF de indicadores se difiere.** `GET /indicadores/reporte/pdf`
  no se porta en E4; se va con F5/F8 junto al resto de Jasper. El `/resumen` que
  lo alimenta si queda cortado, asi que retomarlo es mapear y renderizar.
  **Superada por D-F5-1 (2026-07-30)**: ya no se difiere, queda FUERA DEL
  ALCANCE de la migracion junto con los otros cuatro PDF. La nueva pagina de
  reportes se disenara desde cero y recien entonces se elegira con que se
  imprime.
- **D-E4-2 — Agregar en SQL, no en memoria.** La v1 carga las tablas enteras y
  agrega en Java (es la causa del incidente de ~50 s de RC-003). La v2 baja el
  scope y la ventana al `WHERE` y lee **proyecciones estrechas** (id, estado,
  fecha, id de agente, id de captacion) en vez de grafos de entidades. El
  resultado es identico; lo que cambia es cuanto se lee para llegar a el.
- **D-E4-3 — Los bugs del cable se replican.** El `100` fijo del embudo, la
  "visita realizada" que no mira el estado, `captacionesPendientes` duplicando a
  `captacionesPorRevisar` y el fallback de prospecciones del §3.9 se portan tal
  cual. Se levantan en el paso 8 del checklist, no antes.
- **D-E4-4 — El alcance de indicadores NO se unifica con el de seguimiento.**
  Son dos reglas distintas del mismo dominio (§2) y unificarlas cambiaria
  numeros que hoy la pantalla muestra.
- **D-E4-5 — El tenant va primero, como en toda la v2.** Donde la v1 dice "el
  admin ve todo", la v2 dice "el admin ve todo lo de su organizacion". Es la
  misma divergencia deliberada de D-24 que ya llevan las verticales anteriores.

## 8. Gate de corte — CUMPLIDO

- [x] Los tres recursos responden con las formas de §3, §4, §5 y §6.
- [x] Los tres roles obtienen su alcance y su `ambito`.
- [x] El donut es exclusivo (una captacion pasa de *En evaluacion* a *Alquilada*
      sin duplicarse) y `conversionPorPeriodo` nunca supera 100.
- [x] `/dashboard` devuelve bandeja vacia para BROKER y ADMIN, y la del agente
      para AGENTE.
- [x] `/seguimiento-comercial` respeta el techo de 8, los aliases y la
      independencia de `counts` y `options`.
- [x] Un id de otra organizacion no aparece en ninguna de las tres respuestas —
      ni en las `options`, que es donde mas facil se filtraria.

## 9. Estado de ejecucion y hallazgos

Cortado el 2026-07-29. No hizo falta migracion (no hay V11): E4 solo agrega
lecturas sobre V4–V10.

**Piezas nuevas:**

- `persistence/query/`: nueve proyecciones estrechas
  (`IndicadorCaptacion`, `IndicadorOportunidad`, `IndicadorSolicitud`,
  `IndicadorContrato`, `IndicadorVisita`, `IndicadorInteraccion`,
  `IndicadorProspeccion`, `MotivoPorCaptacion`, `SupervisionVigente`).
- `IndicadorService`/`IndicadorServiceImpl` y
  `SeguimientoComercialService`/`SeguimientoComercialServiceImpl`.
- `service/soporte/Descripciones`: las descripciones congeladas de los codigos de
  estado, que en el seguimiento son **texto de cable** (la pantalla las usa como
  etiqueta *y* como valor de filtro).
- `IndicadoresController`, `DashboardController`, `SeguimientoComercialController`
  y sus 11 DTOs.

**Lo que costo mas de lo que este documento anticipaba:**

- **El alcance indirecto del contrato** (§2). En la v1 es un efecto colateral de
  que el DAO devuelve la solicitud "shallow" y `enriquecerContrato` solo la
  completa si esta en el alcance. Al portarlo hay que volverlo una regla
  explicita, y es la unica consulta de E4 **sin filtro de rol en el WHERE**: las
  dos ramas heredadas viajan y el filtro se aplica arriba.
- **El AGENTE no alcanza por captacion en el seguimiento.** La primera version de
  la consulta hacia `ag.id in :roles or capAg.id in :roles` para todos, y eso
  le mostraba al agente oportunidades de otros sobre captaciones suyas. La v1
  no: `captacionesBroker` esta vacio para el AGENTE. Hoy lo separa el parametro
  `porCaptacion`, y el filtro autoritativo se repite fila por fila igual que en
  la v1.

**Dos refinamientos deliberados** (hacen determinista lo que la v1 dejaba al
orden del DAO o de un `HashMap`, sin cambiar la regla observable):

- el desempate del `desempeno` es `cierres desc, captaciones desc, nombre asc`;
- el "motivo principal" del avance desempata por codigo de razon ascendente
  (mismo criterio que el breakdown de E2).

**Deuda menor que queda anotada:** las descripciones de estado viven ahora en
`Descripciones`, pero `FichaComercialServiceImpl` (E3) conserva su copia privada
de las mismas tablas. Unificarlas es limpieza de despues del corte: E3 esta
verificada y no se toca durante la convivencia.
