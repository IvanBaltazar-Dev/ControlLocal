# Convención definitiva de listados paginados

Estado: **vigente desde 2026-07-30**.  
Primera aplicación: pantalla y recurso de **Locales comerciales**.

Este documento define el patrón que deben reutilizar Prospecciones, Captaciones,
Oportunidades, Interacciones y las siguientes bandejas. No se vuelve a crear un
barrido de páginas, filtrado global en memoria ni una paginación propia por
vertical.

## 1. Flujo único

```text
Controles compartidos
  → query parameters de la URL
  → servicio HTTP del módulo
  → endpoint paginado
  → PageResponse
  → tabla + paginación + estados compartidos
```

La URL es la única fuente que dispara lecturas. Un control solo escribe query
parameters; no llama al servicio directamente. La pantalla observa la URL con
`distinctUntilChanged` + `switchMap`, por lo que un cambio cancela las
solicitudes anteriores y un mismo estado no se solicita dos veces.

Página y KPI se leen en paralelo con `forkJoin` y se publican juntos. Si una
petición falla, no se conserva la mitad de la respuesta.

Piezas comunes:

- `core/api/api.types.ts`: `PageResponse` 1-based y `ApiError`;
- `core/api/api.client.ts`: parámetros, traducción uniforme y `get$` cancelable;
- `cl-barra-filtros`, `cl-filtro-select`, `cl-kpi`, `cl-paginacion`;
- `cl-estado-listado`: carga, error recuperable y vacío.

## 2. Contrato de Locales

### `GET /locales`

Todos los parámetros son opcionales y aditivos. Omitir `texto` y `estado`
conserva la llamada anterior.

| Parámetro | Significado |
|---|---|
| `page` / `pagina` | Página 1-based. `page` tiene precedencia. Valor por defecto: 1. |
| `page_size` / `tamano` | Filas por página. Tope del cable: 100. Valor por defecto: 10. |
| `texto` | Contiene, sin distinguir mayúsculas, en código, dirección, distrito, **rubro** o propietario. |
| `estado` | Código exacto `D`, `N` o `I`. |

Respuesta:

```json
{
  "items": [],
  "totalRecords": 0,
  "page": 1,
  "pageSize": 20
}
```

Orden: `propiedad.id_propiedad ASC`, clave única y estable. Una página fuera de
rango responde `items: []` y conserva el conteo total.

Alcance: todos los roles autenticados; cartera completa del tenant de la sesión,
sin un recorte adicional por rol. La matriz operación–rol es la fuente de verdad.

Implementación: `LocalListado` proyecta únicamente el cable de lectura; no
devuelve entidades completas. Propietario y detalle se resuelven en el mismo
`SELECT`; portada y estado de publicación, en dos consultas por lote para los
ids de la página. El contenido y su conteo corren con aislamiento
`REPEATABLE_READ`.

Cuando llega `texto`, el listado **no** resuelve el filtro con un `OR`: pasa por
el conjunto de candidatos de la sección 5. Sin `texto` sigue el camino directo,
que ya sirve un índice.

### `GET /locales/resumen`

Parámetro opcional: `texto`, con la misma normalización y campos del listado.
No recibe `estado` deliberadamente: devuelve el desglose que alimenta el selector
y los KPI.

```json
{
  "total": 42,
  "disponibles": 31,
  "noDisponibles": 11,
  "inactivos": 0
}
```

Los cuatro valores salen de un `GROUP BY` en PostgreSQL; el total es la suma de
los tres estados. No se cuentan filas descargadas por Angular.

## 3. Estado de URL del SPA

Forma canónica:

```text
/locales?texto=camana&estado=N&page=1
```

- escribir texto, elegir estado o pulsar un KPI reinicia `page=1`;
- KPI y select escriben el mismo parámetro `estado`;
- pulsar el KPI activo elimina `estado`;
- paginar solo cambia `page`;
- limpiar escribe texto/estado vacíos en una sola navegación;
- recargar, volver, avanzar y compartir el enlace restauran el estado;
- si una mutación deja la página fuera de rango, el SPA reemplaza la URL por la
  última página válida.

## 4. Rendimiento y verificación

Índices del camino caliente:

- V11: `(organizacion_id, id_propiedad)` y trigramas GIN sobre `lower(...)` de
  código, dirección, distrito y nombre de persona;
- V22: `(organizacion_id, estado_registro, id_propiedad, disponibilidad_comercial)`,
  que restituye el índice que V11 había creado sobre la columna `estado` y que
  desapareció al partirla en V15–V17;
- V23: trigrama GIN sobre `lower(rubro_permitido)`, el cuarto campo buscable.

Dos gates:

- `e2e-locales-listado.ps1` — páginas, filtros, KPI, orden, aislamiento e
  índices sobre 1.005 filas. Última corrida: **18/18** (2026-08-01).
- `e2e-locales-busqueda.ps1` — el gate de RC-003 para el texto libre: **100.000
  locales** en cartera sesgada, medidos por HTTP. Impone p95 ≤ 1 s y peor en
  régimen < 2 s, y comprueba además que conteo y página miran el mismo conjunto,
  que no hay duplicados y que el rubro entra.

Medido el 2026-08-02 sobre 100.000 locales en cartera sesgada (p50/p95 en ms, en
régimen; corrida que firma el gate):

| Escenario | Antes (`OR`) | Ahora (conjunto de candidatos) |
|---|---|---|
| texto con ~20 coincidencias | 1.240 · peor 2.952 | **63 / 81** |
| texto sin coincidencias | — | 55 / 71 |
| texto por rubro | (no se buscaba) | 65 / 70 |
| texto + estado | 181 | 72 / 78 |
| texto por propietario | 545 | 321 / 374 |
| texto que casa con todo | 344 | 445 / 520 |
| texto medianamente selectivo | 396 | 837 / 944 |
| texto que casa con todo, página profunda | 437 | 730 / 824 |
| *(referencia)* sin texto, página 1 · profunda | — | 116 / 150 · 642 / 796 |

El caso que más dolía —un término selectivo, que es lo que la gente escribe—
cae **20 veces**. El conteo deja de ser un `Seq Scan` de 383 ms fijos: cada rama
se resuelve con `Bitmap Index Scan` sobre su trigrama (decenas de *buffers*, no
1.862). Lo que sube es el término que casa con casi todo, y es intrínseco: si el
conjunto son 100.000 filas hay que deduplicarlas y ordenarlas (`HashAggregate` +
`top-N`), donde el `OR` entraba por la clave primaria y paraba a las diez.

Dos avisos para quien vuelva a medir:

- **Hay varianza de máquina.** El mismo escenario «medianamente selectivo» dio
  227/271 en una corrida y 837/944 en otra sobre el mismo banco. Conviene juzgar
  por el gate completo, no por una cifra suelta.
- **La página profunda no la arregla la búsqueda.** Con texto cuesta 824 ms de
  p95 y **sin texto ya cuesta 796**. Por eso el gate le exige a esa página no
  añadir más de un 30 % sobre su propia referencia sin texto, y el objetivo de
  p95 ≤ 1 s se le exige a la página 1, que es lo que hace un usuario.
  **Lo que se paga NO es lo mismo en los dos caminos**, y conviene no repetir la
  frase corta: *sin texto* sí domina el `OFFSET` (707 → 1.716 ms de la página 1
  a la profunda); *con texto* la curva es plana (1.309 → 1.298) y lo caro es
  construir y deduplicar el conjunto de candidatos **dos veces por llamada**
  —conteo y página—, mientras que el salto en sí cuesta ~110 ms de ~900. Si
  algún día se quiere la última página por debajo del segundo, la palanca sigue
  siendo cambiar `OFFSET` por paginación por clave —que es lo que arregla el
  camino llano—, no materializar una proyección de búsqueda. Medido el
  2026-08-03; detalle en `docs/ai/diagnostico-pico-rc003-gate-f3.md` §3.

**Sobre las llamadas en frío.** La primera petición de cada escenario cuesta
bastante más (se han visto picos de 2,5–6,6 s) por el JIT del camino de consulta,
la caché de planes vacía y las páginas aún fuera del *buffer*: se reproduce igual
con 30.000 filas que con 100.000 —salió peor con 30.000—, así que no depende del
volumen. El gate la mide y la informa aparte (columna `Frio`), le exige el límite
de RC-003, y aplica el objetivo interno al régimen.

## 5. Búsqueda por conjunto de candidatos (obligatoria desde 2026-08-02)

**Ningún listado vuelve a resolver el texto libre con un `OR` que cruce tablas.**
Es la regla que deja esta estabilización, y no es estética: un `OR` sobre
columnas de tablas distintas no lo puede servir ningún índice —PostgreSQL no
combina índices de tablas diferentes en un `BitmapOr`— y degenera en `Seq Scan`
con el `LIKE` como *Join Filter*. El conteo del `PageResponse` paga ese barrido
entero en **cada** petición, gane o no la caché.

El patrón, tal como quedó en `PropiedadRepository`:

1. **Una rama por tabla.** Cada rama consulta una sola tabla y solo sus campos
   buscables, de modo que su índice de trigramas sí entra. En locales son tres:
   `propiedad` (código, dirección, distrito), `detalle_local_comercial` (rubro) y
   `persona` a través de `persona_rol` (nombre o razón social del propietario).
2. **`UNION`, nunca `UNION ALL`.** Un local puede casar por varias ramas a la
   vez; el `UNION` lo deduplica en la base, no en Java.
3. **Tenant, alcance, filtros activos y normalización del texto viajan en TODAS
   las ramas.** El conjunto tiene que quedar cerrado antes de unirse: si un
   filtro se aplica después, el conteo y la página pueden discrepar.
4. **El mismo conjunto sirve al conteo y a la página**, y también al KPI del
   resumen. Son tres consultas sobre el mismo `UNION`, no tres criterios.
5. **Orden estable y paginación en la base.** `ORDER BY id` sobre el conjunto,
   con `LIMIT`/`OFFSET`. Nunca se sube a Java la lista completa de ids.
6. **La proyección completa se carga después**, solo para los ids de la página
   ya resuelta (`buscarPorIds`). Es un acceso por clave sobre ≤ `tamano` filas.

Dos límites deliberados:

- **Una consulta por módulo, no un generador universal.** Los campos buscables
  cambian por pantalla; lo que se comparte es la *forma* (ramas + `UNION` +
  conjunto único), la normalización y las reglas de alcance, no un armador
  dinámico de SQL que nadie pueda leer ni explicar en un plan.
- **Nada materializado mientras esto alcance.** No hay tabla ni columna de
  búsqueda que mantener sincronizada: cada rama lee el dato vivo, así que editar
  el nombre del propietario o el rubro cambia el resultado en la misma
  transacción. Solo si un módulo no llegara al objetivo con este patrón se
  evaluaría una proyección `propiedad_busqueda` por agregado —una sola,
  reutilizable por las pantallas cuyo objeto central sea el local—, y esa
  decisión se toma **con medición**, no por anticipado.

Antes de dar por cerrada una bandeja con búsqueda: índice de trigramas sobre la
expresión `lower(campo)` de **cada** campo buscable nuevo (V11 y V23 son el
precedente) y una corrida del gate de rendimiento con el banco de 100.000.

**Aplicado a Demanda F3 (2026-08-02).** Las tres bandejas de la vertical nacieron
con el `OR` cruzado y se corrigieron el mismo día, antes de cerrarlas:

| Bandeja | Ramas | Campos buscables |
|---|---|---|
| `GET /oportunidades` | 4 | código de la oportunidad · código de la captación · dirección de la propiedad · nombre del cliente |
| `GET /visitas` | 2 | código de la oportunidad · dirección y distrito de la propiedad |
| `GET /interacciones` | 5 | observaciones · código de prospección · código de captación · nombre del cliente · nombre del agente |

Tres cosas que quedaron claras al portarlo y que valen para la siguiente vertical:

- **El listado se parte en dos caminos, no en uno con `if`.** Sin texto sigue el
  JPQL de siempre (su `WHERE` ya es indexable); con texto entra el conjunto de
  candidatos. El `query` desapareció de la firma del método JPQL a propósito: si
  no está, nadie puede volver a colarlo en el `OR`.
- **El alcance también viaja en cada rama, y el de estas tres no es el mismo**
  —oportunidades y visitas alcanzan por captación, interacciones por agente
  responsable—, así que el `COMUN` de cada repositorio es distinto. No hay
  fragmento compartido entre módulos: se comparte la forma, no el SQL.
- **Los roles van como literal `bigint[]` y se comparan con `= any(...)`**, no
  como `IN (:roles)`. El parámetro se repite en cada rama del `UNION` y la
  expansión de colecciones en consultas nativas repetidas es frágil; el literal
  se liga una sola vez. De ahí `Alcance.paramRolesArray()`.

Índices: **V25** (`ix_oportunidad_codigo_trgm`, `ix_captacion_codigo_trgm`,
`ix_prospeccion_codigo_trgm`, `ix_interaccion_observaciones_trgm` y tres de
recorrido por tenant). La dirección y el distrito de la propiedad y el nombre de
la persona ya los cubría V11: no se duplican.

**Aplicado a Cierre F4 (2026-08-02).** La bandeja de solicitudes nació ya con el
patrón —es la primera que no hubo que corregir— y estrena el récord de ramas:

| Bandeja | Ramas | Campos buscables |
|---|---|---|
| `GET /solicitudes` | 5 | código de la solicitud · código de la oportunidad · dirección **y distrito** de la propiedad · nombre del cliente · **nombre del agente** |

Índices: **V26** (`ix_solicitud_codigo_trgm` y `ix_solicitud_org_id`). Las otras
cuatro ramas ya estaban cubiertas —el código de la oportunidad por V25, la
dirección, el distrito y el nombre de la persona por V11—, así que la migración
es de dos índices, no de cinco. Antes de escribir una migración de búsqueda,
mirar qué trigramas existen ya: aquí tres de las cinco ramas no necesitaban nada.

Dos cosas que aporta y que valen para las bandejas que quedan:

- **Un cubo puede ser un valor del filtro, no un filtro nuevo.** La cola del
  broker es `{E, O}` y viaja como `estado=PENDIENTES` —igual que `GESTION` en
  prospecciones—, de modo que se pagina en la base con una sola consulta en vez
  de dos listados unidos en el cliente. El resumen lo devuelve **ya sumado**
  para que la pantalla no lo calcule por su cuenta y se desincronice del filtro.
- **El resumen puede devolver las opciones de los filtros que acota.** Aquí trae
  distritos **y agentes**, así que los dos selectores son data-driven sin una
  llamada extra — y el filtro por agente solo se ofrece a quien supervisa.
  Esas dos listas son del **alcance**, no del resultado filtrado, igual que los
  rubros de `/clientes/resumen` y los distritos de `/visitas/resumen`
  (`/contratos/resumen` es la excepción: allí sí viajan con el texto). Tiene un
  precio medible y conviene conocerlo: **recorren el alcance entero**, así que
  el `/resumen` cuesta ~700 ms sobre 100.000 filas aunque el término sea muy
  selectivo y la lista responda en ~180. Por eso el gate juzga el `/resumen`
  bajo **RC-003** y deja el objetivo de 1.000 ms como dato informativo: dos de
  sus tres consultas no son la búsqueda, son un listado sin filtro.

Su gate es `e2e-solicitudes-busqueda.ps1` (misma forma que los dos anteriores,
más la comprobación de que `PENDIENTES` es exactamente `enRevision + observadas`
y de que el resumen ignora los tres filtros que devuelve). **Firmado el
2026-08-02: 48/48 sobre 100.000 filas**, con la máquina en reposo:

| Escenario | p50 / p95 (ms) | Casa |
|---|---|---|
| Término selectivo (código de solicitud) | 72 / **82** | 20 |
| Término selectivo (código de operación) | 65 / **71** | 20 |
| Por nombre de cliente | 66 / **80** | 502 |
| Por nombre de agente | 60 / **68** | 200 |
| Medianamente selectivo | 90 / **147** | 100 |
| Sin coincidencias | 30 / **32** | 0 |
| Casa con TODO *(criterio 2)* | 1.144 / 1.332 | 100.000 |
| Casa con TODO, página profunda *(criterio 3)* | 1.181 / 1.445 | 100.000 |
| Sin texto, página 1 *(referencia)* | — / 789 | 100.001 |
| `/resumen` con texto | 327 / 444 | — |

**Una corrección del método que vale para todas las bandejas**: clasificar
"discriminante" por el `totalRecords` de la respuesta deja de ser válido en
cuanto hay otro filtro activo. `texto=Calle&estado=PENDIENTES` devuelve 28.572
filas —por debajo del umbral— pero **su texto casa con las 100.000**, así que lo
que mide es construir el conjunto entero: es criterio 2, no 1. Juzgarlo con el
objetivo de 1.000 ms sería exigirle a un listado sin filtro lo que se le pide a
una búsqueda.

Cada escenario **declara su término** y se clasifica por la cardinalidad de ese
término **aislado**, con la clave `(módulo, término)` —el mismo texto no casa lo
mismo en cada bandeja—. **Aplicado también al gate de F3 el 2026-08-03**, que
arrastraba el defecto: sus tres escenarios `texto + X` usan términos que casan
con el 100 % del banco (`Avenida`, `Contacto`) y estaban clasificados como
discriminantes. Ahí no producía rojos —pasaban por margen—, pero se les estaba
midiendo con la vara equivocada; el reparto correcto es **9 discriminantes y 9
no discriminantes**, no 12 y 6. Los dos gates llevan además una comprobación
que **falla si un escenario usa un término sin medida aislada**, para que añadir
un `texto + X` nuevo no reintroduzca el defecto en silencio.

Dos tropiezos del propio script, por si se repiten al escribir el siguiente:

- **`date - bigint` no existe en PostgreSQL.** `row_number()` devuelve bigint,
  así que un `current_date - (n % 300)` falla y deja el banco vacío en silencio;
  el gate lo delata porque comprueba el tamaño del banco antes de medir.
- **En un here-string `@"…"@` de PowerShell la comilla invertida escapa.** Un
  `` `n `` dentro de un comentario SQL se convierte en un salto de línea y parte
  la sentencia. Los here-strings de SQL con interpolación no deben llevar
  comillas invertidas ni siquiera en los comentarios.

### El gate de F3 y los dos defectos que destapó (2026-08-02)

`e2e-demanda-busqueda.ps1` mide las tres bandejas sobre **100.000 filas por
tabla** con la misma forma que el de locales, y añade dos comprobaciones que
aquél no tenía: **lee los planes** —cada rama por su trigrama, ninguna tabla
grande recorrida entera— y ejecuta el **`OR` cruzado prohibido sobre el mismo
banco** para dejar el contraste medido en vez de afirmado.

La primera corrida dio **p95 de 10.885 ms**. Las dos causas, y las dos son
lecciones portables:

**1. El plan genérico del *prepared statement*.** El texto viaja como parámetro
dentro de un `LIKE`. El driver JDBC convierte la sentencia en *prepared
statement* del servidor tras **cinco** ejecuciones y PostgreSQL empieza a
reutilizar un plan construido **sin mirar los valores**: para un `LIKE`
parametrizado asume selectividad de "casi nada" y elige `Nested Loop` con
100.000 iteraciones donde el plan personalizado hace `HashAggregate` sobre
recorridos completos. Medido sobre la misma consulta y los mismos parámetros:

| | Conteo | Página |
|---|---|---|
| Plan personalizado | 245–400 ms | 341–434 ms |
| Plan genérico | 771–845 ms | 790–960 ms |

> **El síntoma es contraintuitivo y por eso cuesta reconocerlo: la llamada en
> frío sale MÁS RÁPIDA que el régimen** (986 ms contra 3.253 de p50). Las
> primeras ejecuciones todavía planifican con los valores delante. Si una
> medida muestra eso, sospecha del plan antes que del índice.

Se corrige con `SET LOCAL plan_cache_mode = 'force_custom_plan'` **dentro de la
transacción de búsqueda** (`persistence/repositorio/PlanDeConsulta`), no en la
conexión ni desactivando los *prepared statements*: eso le cobraría la
replanificación —unos 7 ms— a todas las consultas del sistema.

**2. Joins muertos en el listado sin texto.** Al mudar el `LIKE` a las ramas del
`UNION`, los joins que solo existían para servirlo se quedaron en el `DESDE`:
oportunidades unía **ocho** tablas para contar cuando le bastan cuatro, e
interacciones **diez** cuando le basta una. El conteo los seguía pagando —521 /
1.043 ms de p95 en un listado sin buscar nada—. Al sacar un predicado de una
consulta, hay que sacar también lo que solo estaba ahí por él.

**Resultados comparativos** (100.000 filas por tabla, p50 / p95 en ms):

| Escenario | Original | Tras los dos arreglos |
|---|---|---|
| OPO selectivo por código (100 filas) | 2.408 / **5.552** | **126 / 139** |
| OPO ~20 coincidencias | 97 / 118 | 104 / 149 |
| OPO por cliente | 85 / 102 | 98 / 116 |
| OPO casa con TODO | 2.629 / 3.585 | 948 / 1.253 |
| OPO sin texto *(referencia)* | 521 / 1.043 | **235 / 371** |
| VIS ~20 coincidencias | 643 / 1.297 | **48 / 53** |
| VIS por distrito (16.667) | 1.404 / 1.917 | **449 / 520** |
| VIS casa con TODO | 3.253 / **10.885** | 1.299 / 1.594 |
| INT ~20 coincidencias | 1.209 / 1.535 | **62 / 112** |
| INT casa con TODO | 824 / 2.000 | **439 / 583** |
| INT texto + canal | 138 / 177 | 153 / 232 |

El caso que importa —un término que **discrimina**, que es lo que la gente
escribe— queda entre **48 y 232 ms** en las tres bandejas, y el peor de ellos
cae **40 veces**.

### Los tres criterios del gate (decisión del 2026-08-02)

El objetivo único de «p95 ≤ 1.000 ms para todo texto libre» resultó ser
**demasiado grueso**: mete en el mismo saco una búsqueda y un listado sin
filtro. Queda partido en tres criterios, y **cada escenario se juzga por el suyo**.

**Decidido y cerrado: NO se construye una proyección materializada para visitas.**
La única desviación corresponde a un término que casa con el 100 % del banco;
ahí PostgreSQL elige `Seq Scan` **correctamente**, porque usar el índice sería
más caro. No corresponde introducir duplicación de datos ni mecanismos de
sincronización para optimizar un caso que, funcionalmente, **equivale a listar
sin filtro**.

**Criterio 1 — búsqueda DISCRIMINANTE.** El término acota de verdad el conjunto.
Es lo que escribe un usuario y es donde el patrón tiene que rendir. Mantiene
todo lo exigido hasta ahora:

- p95 **< 1.000 ms**;
- cada rama entra por **su trigrama**;
- **ningún recorrido completo** de las tablas grandes;
- el **mismo conjunto de candidatos** sirve al conteo, a la página y al KPI.

Medido: entre **48 y 232 ms** en las tres bandejas.

**Criterio 2 — búsqueda NO DISCRIMINANTE.** El término casa con prácticamente
todo el banco. Ahí **se permite `Seq Scan`** —es el plan correcto— y el
escenario **no se juzga contra el objetivo de 1.000 ms**: funcionalmente
equivale a listar sin filtro. Rige **RC-003 (< 3.000 ms)**, y la comparación
contra la misma página sin texto se **registra**.

> **La comparación cambia de signo según la profundidad, así que el criterio
> NO se declara cumplido con una sola de las dos.** Hay que registrar ambas:
>
> | Visitas, término no discriminante | Con texto | Sin texto | Lectura |
> |---|---|---|---|
> | **Página 1** | 1.594 ms | 803 ms | la búsqueda cuesta **más**: el `UNION` construye y deduplica un conjunto de candidatos que el listado llano no tiene que tocar |
> | **Página profunda** | 1.551 ms | 2.099 ms | la búsqueda cuesta **menos**: el camino de candidatos evita el `OFFSET` sobre el join de cinco tablas |
>
> Por eso el gate **exige RC-003 a cada escenario no discriminante** —el límite
> que aplica sin ambigüedad en las dos profundidades— y **registra las dos
> razones como evidencia, nunca como umbral**. Un gate que solo mirara la
> página profunda daría por bueno el patrón por el motivo equivocado.

**Criterio 3 — paginación PROFUNDA.** Se mantiene **fuera** del gate de búsqueda
y bajo **RC-003 (< 3.000 ms)**. El pico medido en su día fue de 2.485 ms.

> **Reabierto el 2026-08-03: este criterio YA NO PASA en visitas.** Dos corridas
> consecutivas del gate corregido, con la máquina en reposo, miden el mismo
> pico en `VIS casa TODO - profunda`: **3.357 y 3.309 ms**, por encima de
> RC-003. No es varianza —esa fue la primera hipótesis y las dos corridas la
> descartan—: la misma página **sin texto** es estable (p50 1.599, p95 1.721,
> peor 1.769), así que el pico pertenece a *página profunda + texto* y solo en
> visitas. `OPO` e `INT` en la misma posición quedan en 1.272 y 939 ms.
>
> Lo que la distribución dice: p50 ~1.4 s y p95 ~1.9 s con una **cola estable en
> ~3,3 s**, es decir un segundo modo, no un pico aleatorio. La deuda ya
> registrada —sustituir `OFFSET` por cursor/keyset en las tres bandejas— deja de
> ser una mejora opcional para este escenario y pasa a ser lo que hay que hacer
> para que el criterio 3 vuelva a cumplirse. **No se baja el umbral**: un
> `> 3 s` reproducible no se convierte en verde cambiando la regla.
>
> Alcance de lo que sí está sano, para no confundir el diagnóstico: la búsqueda
> **discriminante** —lo que la gente escribe— mide entre **56 y 179 ms** de p95
> en las tres bandejas, y los planes siguen entrando por sus trigramas.

Deja registrada una **deuda técnica posterior**: sustituir `OFFSET` por
paginación por **cursor o keyset**, con alcance en las **tres** bandejas
—oportunidades, visitas e interacciones—. **No bloquea F3 y no debe
implementarse parcialmente**: media migración deja dos bandejas paginando de una
forma y una de otra, que es peor que ninguna. No se resuelve con una proyección
materializada: duplicar datos no arregla un `OFFSET`.

> **El `OFFSET` sólo domina el coste en el camino SIN texto.** Medido el
> 2026-08-03 sobre visitas y 100.000 filas (`docs/ai/diagnostico-pico-rc003-gate-f3.md`):
>
> | | Página 1 | Página profunda |
> |---|---|---|
> | **Sin texto** | 707 ms | 1.716 ms · aquí sí lo paga el `OFFSET` |
> | **Con texto** | 1.309 ms | 1.298 ms · **la curva es plana** |
>
> En el camino de candidatos lo que se paga es **construir y deduplicar el
> conjunto, dos veces por llamada** (conteo 678 ms + página 907 ms). El salto en
> sí cuesta ~110 ms de ~900: con `offset 0` el plan usa `top-N heapsort` de
> 25 kB y tarda 797 ms; con `offset 99990` pasa a `external merge` de 2.160 kB y
> tarda 907 ms. Al abordar la deuda, esperar la mejora **en el listado llano**,
> no en la búsqueda.

### Regresiones obligatorias

Cinco cosas que no pueden perderse al tocar esto, y que el gate vigila:

1. **`SET LOCAL plan_cache_mode` solo dentro de la transacción de búsqueda.**
   Nunca en la conexión del pool ni con `prepareThreshold=0`: eso le cobraría la
   replanificación a todas las consultas del sistema.
2. **Las guardas que impiden volver al `OR` entre tablas** (las tres firmas JPQL
   de listado no reciben el texto).
3. **La verificación de los índices trigrama por cada rama.**
4. **Los cuatro perfiles de término**: selectivo, medio, sin coincidencias y que
   casa con todo.
5. **Los p50/p95 y los planes del banco de 100.000** como evidencia del cierre.

Dos avisos de operación:

- **El gate se mide con la máquina en reposo.** Una corrida con una limpieza de
  100.000 filas en marcha en otro contenedor dio picos de 3.697 ms que no son
  del código. Los percentiles aguantan; el «peor observado» no.
- **La primera corrida de cada término es fría** y se informa aparte: paga el
  JIT, la caché de planes vacía y las páginas fuera del *buffer*.

## 6. Regla para las siguientes verticales

Cada nueva bandeja aporta únicamente:

1. su tipo de filtros de URL;
2. su servicio de módulo;
3. su endpoint/proyección y resumen con alcance;
4. el mapeo de filas y los códigos de KPI;
5. si tiene texto libre, sus ramas de búsqueda según la sección 5.

No aporta otro cliente HTTP, traductor de errores, barra de filtros, selector,
paginador, componente de estados ni método para descargar todas las páginas.
