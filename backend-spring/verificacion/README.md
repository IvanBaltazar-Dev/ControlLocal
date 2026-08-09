# Verificación E2E aislada del backend v2

Scripts que comprueban lo que un test unitario no puede: el comportamiento del API
contra PostgreSQL real. Toda suite que escribe se ejecuta exclusivamente mediante
`Invoke-E2E.ps1`: crea una base vacía con identificador de corrida, Flyway la
reconstruye, el API usa un puerto aleatorio y un `finally` elimina contenedores,
redes, volúmenes y la propia base.

**La limpieza es tirar la base entera, no borrar filas** (2026-08-03). Las suites ya
no retiran su fixture con `delete`: el entorno es exclusivo de la corrida y muere
completo. Medido en la corrida de firma de F3 (`20260803093503-7523`, 100.000 filas
por tabla): el borrado manual costaba **1.014 s — 16 min 54 s— de los 1.657 s de la
corrida entera, el 61 %**, más que sus doce secciones de comprobación juntas; el
`compose down -v` que lo hace de verdad tarda **1,6–3,4 s**. Lo que queda en su lugar es
un check de milisegundos: que la base sea de verdad la efímera de la corrida, que es la
única condición que hace seguro tirarla entera.

Comprobado, no supuesto: con el cambio aplicado, `f4-solicitud` **116/116** y
`demanda-busqueda` **69/69** —mismo número de checks que antes— **en una sola invocación
de 625 s**, cuando la corrida anterior de `demanda-busqueda` sola tardaba 1.657 s. Sin
recursos residuales en ninguna de las dos.

> **Prohibido ejecutar directamente los `e2e-*.ps1` o los SQL contra la base de
> desarrollo.** Cada script PowerShell lleva un guard que falla antes del primer
> login/INSERT. La base manual `controllocal_dev` y el API `:8090` no participan.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite estabilizacion-alquiler
```

**Un entorno por suite** (2026-08-03). `-Suite` acepta varias y ahora **cada una levanta
y destruye el suyo**: base, API, puerto y contenedores propios. Eso arregla de paso el
problema que tenía la lista compartida —el limitador de peticiones es parte del contrato
congelado, así que a partir de la tercera suite los logins recibían **429** y la corrida
moría sin un solo check en rojo—: con un API por suite el contador arranca de cero.
El precio es el arranque del entorno de la segunda suite en adelante; una invocación de
una sola suite, que es el caso normal, no paga nada.
Y con `-File` no se pueden pasar listas —PowerShell entrega la coma como un único
string y el resto como posicionales—: si necesitas varias, usa
`powershell -Command "& '…\Invoke-E2E.ps1' -Suite a,b"`.

## `e2e-estabilizacion-alquiler.ps1` — contrato económico transversal

Es la prueba focal de esta estabilización. Demuestra sobre una base recién creada
por Flyway: condición económica explícita, conservación de moneda, vigencias y cierre,
cascada completa de `POST /contratos`, exclusión de anuladas en KPI, igualdad de filtros
entre tabla/resumen, saldos por movimiento y detección de contratos sin liquidación.
El wrapper comprueba después que no quede ningún recurso ni base E2E residual.

No ejecuta ni encadena las suites acumulativas.

## `e2e-locales-listado.ps1` — listado filtrado con más de 1.000 filas

Fija el patrón definitivo de bandejas sobre datos reales: crea temporalmente
**1.005 locales** en el tenant de legado y 7 con los mismos códigos en una
segunda organización; prueba filtros, `PageResponse`, página vacía, orden estable,
KPI del resumen, aislamiento e índices V11, y siempre retira el fixture.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite locales-listado
```

Última corrida verde: **18/18** (2026-08-01). El fixture se retiró por completo.

> **Estuvo rota desde la expansión del modelo económico (detectado y corregido el 2026-08-01,
> cayó a 5/18).** Dos causas independientes, ninguna del listado en sí:
>
> 1. El fixture insertaba `propiedad.estado`, columna que V15–V17 partieron en `estado_registro`
>    (A/I) + `disponibilidad_comercial` (D/R/A/T), así que los 1.005 locales no llegaban a crearse
>    y todo lo demás fallaba por arrastre. Ahora inserta el **par** que
>    `PropiedadRepository.ESTADO_LEGADO` vuelve a leer como D/N/I. Ojo al traducir: `'N'` no es un
>    valor legal de `disponibilidad_comercial` —`ck_propiedad_disponibilidad` solo admite D/R/A/T—,
>    un local activo pero no disponible se guarda por su causa (aquí `'A'`, alquilado).
> 2. `ix_propiedad_org_estado_id` —el índice del camino caliente que creó V11— **desapareció con
>    la columna `estado`**: al eliminarla, PostgreSQL eliminó el índice, y el listado paginado que
>    RC-003 dejó bajo 3 s se quedó sin él. Lo recrea **V22** como
>    `ix_propiedad_org_estado_registro_id_disp`, sobre `(organizacion_id, estado_registro,
>    id_propiedad, disponibilidad_comercial)`. No es un rename: el orden se eligió midiendo con
>    `EXPLAIN ANALYZE` sobre 30.000 filas, y el razonamiento está en la cabecera de la migración.

### Medición extremo a extremo del objetivo de RC-003 (2026-08-01)

Cuatro pasadas en entornos efímeros —30.000 y 100.000 locales cargados y medidos por HTTP con el
API real, no solo con `EXPLAIN`—. **El objetivo de 3 s se cumple**, y el margen más estrecho **no
está en el filtro de estado sino en la búsqueda por texto**:

| Escenario (100.000 locales) | Mediana | Peor observado |
| --- | --- | --- |
| `GET /locales` página 1 · página profunda | 132 ms · 730 ms | 785 ms |
| `estado=D` · `estado=N` · `estado=I` | 104 · 131 · 59 ms | 138 ms |
| `/locales/resumen` (KPI) | 177 ms | 188 ms |
| **`texto=` con término muy selectivo** | **1.240 ms** | **2.952 ms** |
| `texto=` con término que casa con todo | 344 ms | 522 ms |

Con 30.000 locales nada pasa de **186 ms**.

Tres cosas que solo se ven midiendo la consulta **con sus dos joins**, y que conviene saber antes
de volver a tocar esto:

- **El índice de V22 no entra en la cartera equilibrada.** Con los disponibles al 7 %, el plan es
  idéntico con y sin él: el planner recorre `propiedad_pkey` —que ya da el orden— y descarta 93
  filas antes de juntar 10. Medido con la consulta simplificada (sin joins) sí aparece un *Index
  Only Scan*, pero eso es un espejismo: el listado necesita la fila para unir con el propietario,
  así que index-only no es posible.
- **Sí entra, y decide, en la cartera sesgada**, que es la de una corredora madura: con los
  disponibles al 0,1 % y al final de la tabla, `Index Scan` con `Index Cond` **18,7 ms / 611
  buffers**, frente a `propiedad_pkey` descartando 99.900 filas por `Filter`, **138,2 ms / 2.093
  buffers**. 7,4× más rápido. El índice es un seguro contra ese sesgo, no una mejora del caso medio.
- **El texto libre cuesta en el CONTEO, no en la página.** El `OR` cruza `propiedad` y `persona`,
  así que PostgreSQL no puede combinar los trigramas de V11 y cae a `Seq Scan` de las 100.002 filas
  con el `LIKE` como *Join Filter*: 383 ms fijos en el `count` del `PageResponse`. Y hay un efecto
  contraintuitivo: **cuanto más selectivo es el término, más caro** (1.240 ms con 19 coincidencias
  frente a 344 ms con 100.000), porque la página entra por la PK y la recorre hasta reunir 10.

## `e2e-locales-busqueda.ps1` — el gate de RC-003 para el texto libre

Carga **100.000 locales** en cartera sesgada (la mayoría alquilada, los
disponibles al 0,5 %) y mide el listado **por HTTP**, que es lo único que
demuestra el objetivo: un `EXPLAIN` sobre una consulta simplificada no vale,
porque el planner elige otro plan cuando la consulta lleva sus joins.

Comprueba a la vez la semántica de la búsqueda por conjunto de candidatos
(`docs/ai/contrato-listados-paginados.md` §5): que el **rubro** entra, que
conteo y página miran el mismo conjunto, que paginar el conjunto entero devuelve
exactamente el total, que casar por varias ramas no duplica la fila y que el KPI
del resumen cuadra con la lista.

Umbrales que impone:

- p95 del texto libre en **página 1** ≤ 1 s;
- peor observado en régimen < 2 s;
- límite absoluto RC-003 < 3 s, exigido **también a la llamada en frío**;
- en la página profunda, la búsqueda no añade más del 30 % sobre el coste que el
  `OFFSET` ya cobra sin ella.

La **primera llamada de cada escenario se informa aparte** (columna `Frio`) y no
entra en el percentil: en frío se paga el JIT del camino de consulta, la caché de
planes vacía y las páginas fuera del *buffer*. Se ve igual con 30.000 filas que
con 100.000 —de hecho salió peor con 30.000—, así que no depende del volumen.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite locales-busqueda
```

Última corrida: **21/21** (2026-08-02), con p95 máximo de 944 ms en página 1 y
peor observado de 1.040 ms. El banco se retira al terminar.

## `e2e-demanda-busqueda.ps1` — el gate de las tres bandejas de F3

Mismo patrón, sobre **100.000 filas por tabla** en oportunidades, visitas e
interacciones. Su historia está en `docs/ai/contrato-listados-paginados.md` §5.

**Evidencia oficial vigente: 2026-08-03, 68 OK / 1 FALLA**, en dos corridas
consecutivas con la máquina en reposo y el banco retirado por completo.

Verde todo lo estructural: semántica de las seis ramas, conteo = página, KPI del
mismo conjunto, planes por trigrama sin `Seq Scan` de tablas grandes, el
contraste del `OR` prohibido cayendo a `Seq Scan`, y las tres guardas estáticas.
Búsqueda discriminante entre **56 y 179 ms** de p95.

**La falla es real y está abierta**: `VIS casa TODO - profunda` mide **3.357 y
3.309 ms** de peor observado en las dos corridas, por encima de RC-003. No es
varianza —fue la primera hipótesis y las dos corridas la descartan—: la misma
página **sin texto** es estable (peor 1.769) y las otras dos bandejas en la misma
posición quedan en 1.272 y 939 ms. Es una cola estable en ~3,3 s sobre un p50 de
~1,4 s, o sea un segundo modo. La palanca es la deuda ya registrada —sustituir
`OFFSET` por cursor/keyset en las tres bandejas, todas o ninguna—. **No se baja
el umbral para ponerlo en verde.**

En esta corrida se corrigió además el **clasificador de escenarios**: juzgaba
"discriminante" por el `totalRecords` de la respuesta, que mide el efecto de
todos los filtros juntos. Ahora cada escenario declara su término y se clasifica
por la cardinalidad de ese término aislado, con la clave `(módulo, término)`.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite demanda-busqueda
```

## `e2e-solicitudes-busqueda.ps1` — el gate de la bandeja de F4

Mismo patrón que los de locales y demanda, sobre **100.000 solicitudes** (con su
propiedad, captación y oportunidad detrás). Es la bandeja con **más ramas de
búsqueda del sistema**: cinco —código de solicitud, código de oportunidad,
dirección y distrito de la propiedad, nombre del cliente y nombre del agente—.

Además de lo que comprueban los otros dos, añade lo propio de F4:

- que `estado=PENDIENTES` sea **exactamente** `enRevision + observadas`, y que se
  normalice desde minúsculas (no es un estado, es el cubo de la cola del broker);
- que el `/resumen` **ignore** `estado`, `distrito` e `idAgente` —son lo que
  devuelve— y sí comparta el texto con la tabla;
- que **sin filtros** se conserve el orden congelado por id descendente, que es
  lo que prueba que la extensión es aditiva.

Dos cosas del método que conviene leer antes de copiarlo:

- **"Discriminante" se clasifica por el TÉRMINO, no por el `totalRecords` de la
  respuesta.** Con otro filtro activo ese número mide el efecto conjunto:
  `texto=Calle&estado=PENDIENTES` devuelve 28.572 filas —bajo el umbral— pero su
  texto casa con las 100.000, así que es criterio 2. Cada escenario declara su
  término y se juzga con la medida de ese término **sin otros filtros**.
- **El `/resumen` va bajo RC-003, no bajo el objetivo de 1.000 ms**: dos de sus
  tres consultas recorren el alcance entero a propósito (son las opciones de los
  selectores, no la búsqueda). El objetivo se informa, pero no tumba el gate.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite solicitudes-busqueda
```

Última corrida: **48/48** (2026-08-02, máquina en reposo). Término discriminante
entre **32 y 147 ms** de p95, término que casa con todo 1.332, página profunda
1.445 y `/resumen` 444. El banco se retira al terminar.

> **Ojo al medir**: dos entornos E2E simultáneos falsean los percentiles. Una
> corrida con una build de Angular en paralelo dio 1.381 ms en el escenario que
> en reposo da 444. Comprobar `docker ps` antes de firmar números.

## `e2e-sonda-transporte.ps1` — ¿se puede medir rendimiento ahora mismo?

**No es un gate funcional**: no comprueba ni una regla de negocio. Responde a la
única pregunta que envenena cualquier medición hecha desde esta máquina: *¿está
el entorno metiendo pausas que no vienen del producto?*

Golpea `/salud` —que no consulta la base— una vez por segundo durante 5 minutos
por los **dos caminos a la vez**: desde Windows contra el puerto publicado, y
desde dentro de la red de Docker. Cualquier llamada por encima de **500 ms**
sobre un trabajo de milisegundos es una pausa del entorno, y la sonda **termina
en error** si encuentra alguna. Informa además de la separación entre pausas:
un período regular es la firma de un artefacto, no de la carga.

Existe por lo que costó el 2026-08-03. El gate de F3 falló dos corridas seguidas
en `VIS casa TODO - profunda` y la culpa no era de esa consulta: el **proxy de
puertos de Docker Desktop** añadía ~2,05 s cada 200 peticiones, al renovarse el
par de conexiones que el cliente mantiene (Tomcat cierra a las 100, PowerShell
agrupa 2). Como la secuencia del gate es determinista, caía siempre en el mismo
sitio y parecía un segundo modo de esa consulta. Cinco diagnósticos para llegar
ahí; esta sonda lo dice en cinco minutos. Historia completa en
`docs/ai/diagnostico-pico-rc003-gate-f3.md`.

| Camino | Entorno sano | Entorno con el artefacto |
|---|---|---|
| Windows → puerto publicado | p50 28 ms, peor 201, **0 pausas** | peor **2.074**, **4 pausas** (llamadas 200, 400, 600, 800) |
| Dentro de Docker | p50 0 ms, peor 10 ms, **0 pausas** | **0 pausas** |

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite sonda-transporte
```

## `e2e-v6.ps1` — flujo F2 completo sobre el núcleo multi-tenant

Recorre oferta + prospección + captación con las tres bandas de rol y comprueba,
fila por fila, que todo lo que se crea nace con el tenant de legado. Cubre los
criterios **#1, #2, #5, #6 y #9** del gate de `docs/ai/plan-migracion-v6-tenancy.md`.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite v6
```

Última corrida: **46/46** (2026-08-01). Las corridas solo
pueden crear datos dentro de la base efímera.

## `e2e-f3-demanda.ps1` — vertical F3 Demanda completa

Recorre cliente → requerimiento → coincidencias → oportunidad → visita →
interacción con las tres bandas de rol, contra el contrato de
`docs/ai/contrato-congelado-f3-demanda.md`. Además de los caminos felices fija
los mensajes exactos del cable, las **dos** reglas de alcance de broker
(oportunidades/visitas por captación, interacciones por agente supervisado), la
auditoría de cada transición y dos regresiones concretas: el orden de
validación de `POST /interacciones` y el 405 del método equivocado.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f3-demanda
```

Última corrida: **103/103** (2026-08-02). El fixture completo vive y
muere dentro de la base efímera de su corrida.

Las 14 comprobaciones nuevas cubren la extensión aditiva de la bandeja de clientes: las **tres
ramas del texto** (nombre, documento y —la que cruza tabla— rubro), los filtros de tipo y rubro,
que un código inexistente devuelva vacío en vez de ignorarse, que el resumen cuente el mismo
conjunto que la lista y traiga los rubros del selector, y que la baja lógica mueva al cliente de
cubo y la reactivación sea el PUT con `estado='A'`, no un endpoint nuevo.

## `e2e-f4-solicitud.ps1` — vertical F4 completa (la que cierra el ciclo)

Recorre solicitud → documentos → evaluación → contrato → comisión con **cinco actores**
(dos agentes y dos brokers de equipos distintos, más el admin), contra el contrato de
`docs/ai/contrato-congelado-f4-solicitud.md`. Lo que fija y ningún test de service puede:

- las **tres vías de subida** de binarios (base64, octet-stream y por trozos) y que la cuarta
  de la v1, `documentos/local`, **no existe** (D-F4-1);
- la **cascada de siete efectos** del cierre, comprobada efecto por efecto sobre la BD —incluidas
  las **cuatro filas** de `historial_estado` que la v1 no dejaba (MEJ-01) y la baja de las
  publicaciones—;
- las **dos reglas de alcance distintas** (solicitudes por agente, contratos por captación) y
  **D-F4-5**, el hueco del cable al revisar un documento suelto;
- los gates de comisión, que son de **BROKER sin ADMIN**, y que el agente **no ve** el reparto;
- el invariante del almacén: tantos binarios como documentos (un alta rechazada no deja huérfanos).

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f4-solicitud
```

Última corrida: **116/116** (2026-08-01). Crea datos por corrida (1 local, 1 cliente, 1
oportunidad, 1 solicitud con 7 documentos y 1 contrato) y **consume la cartera que toca**: el
local queda NO DISPONIBLE y su captación cerrada, que es justo lo que se verifica.

> **Ojo al correrlo dos veces seguidas**: hace 5 logins y el limitador del contrato congelado
> permite 10 por minuto y por IP. Dos corridas en el mismo minuto dan **429**; espera la ventana.

## `e2e-f6-f7-alertas-tareas.ps1` — la campana y la bandeja

Lo que verifica no es el CRUD de dos recursos —eso es trivial— sino que **F6/F7 quedaron
cableadas en las verticales ya cortadas**: las **once emisiones** repartidas por captación,
solicitud, documentos, evaluación, contrato y comisión, y los **siete disparadores** de la
bandeja. En concreto fija:

- que **el GET escribe**: `/alertas` materializa el barrido de recontacto (con su throttle) y
  `/tareas` deriva y reconcilia en cada lectura;
- los **dos verbos** de `atender` y que la segunda llamada responde `false` sin ser un error;
- que **cancelar una tarea la mata**: el reconcile no la vuelve a crear aunque el disparador siga
  vigente;
- las **severidades derivadas** (rechazar ALTA, observar MEDIA, aprobar INFO) y los mensajes
  literales, incluido el `": " + observacion`;
- el **efecto 7** de la cascada de F4 y los **dos bugs congelados**: la alerta de modificación
  sensible con el tipo equivocado y `CAPTACION_CREADA`, que el camino normal nunca emite;
- que `/tareas` es el **único recurso sin acceso de ADMIN**.

```bash
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite f6-f7-alertas-tareas
```

Última corrida: **68/68** (2026-08-01). Crea datos por corrida (2 locales, 1 cliente, 1
oportunidad, 1 solicitud y 1 contrato) y **consume la cartera que toca**, igual que el de F4.

> Mismo aviso del limitador que el de F4: hace 4 logins y el límite son 10 por minuto y por IP.

## `e2e-personas.ps1` — bloque E1 completo

Recorre `/propietarios`, `/brokers`, `/agentes`, `/perfil` y `/asignaciones`. Además de CRUD,
mensajes y gates de rol, comprueba:

- altas Party-Role completas (`PERSONA` + `USUARIO_INTERNO` + rol operativo + credencial);
- paginación y contadores SQL de propietarios, agentes, brokers y equipos;
- supervisión inicial y alcance de escritura del broker;
- teléfono y foto de perfil contra el almacén real;
- cierre de la supervisión anterior, apertura de la nueva y evento histórico V10 en una sola
  transacción;
- aislamiento E1: crea otra organización, confirma que su credencial no autentica mientras D-20
  mantiene el tenant legado y que su broker no aparece por `/brokers`; el fixture se elimina en
  `finally`.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite personas
```

Última corrida: **99/99** (2026-08-01). Crea un propietario (que termina inactivo), un broker,
un agente, una foto y una reasignación por corrida. El fixture de la segunda organización sí se
retira. Hace **6 intentos de login**, así que no debe repetirse dentro de la misma ventana de un
minuto: el límite congelado es 10.

## `e2e-reportes-propietario.ps1` — bloque E2

Recurso anidado completo (`GET`, `GET /preview` y `POST`) contra
`docs/ai/contrato-congelado-e2-reportes-propietario.md`: los tres agregados derivados de
actividad real, que el `POST` **ignora** esos valores si el cliente los manda, los gates por
rol/equipo/tenant y el efecto observable sobre la cadencia de 15 días de `/tareas`.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite reportes-propietario
```

Última corrida: **50/50** (2026-08-01). Crea un local y una captación con el reloj vencido, más
un fixture de segunda organización que se retira en `finally`.

## `e2e-ficha-comercial.ps1` — bloque E3

Los cuatro GET de ficha comercial de `/clientes` y `/propietarios` contra
`docs/ai/contrato-congelado-e3-ficha-comercial.md`: las 11 secciones, la carga inicial parcial,
los aliases de paginación (`page`/`pagina`, `page_size`/`tamano`), el tope de 8 filas y la
privacidad por rol/equipo/tenant.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite ficha-comercial
```

Última corrida: **61/61** (2026-08-01).

## `e2e-e4-dashboard.ps1` — bloque E4 (el que cierra el backend)

Los cuatro endpoints agregadores (`/indicadores/resumen`, `/indicadores/avance`, `/dashboard` y
`/seguimiento-comercial`) contra
`docs/ai/contrato-congelado-e4-dashboard-indicadores-seguimiento.md`.

**Es el único script que no compara valores absolutos**, y no puede: son agregados sobre una BD
con seed. Toma una foto **antes**, crea un fixture identificable y comprueba **cuánto se movió
cada indicador**. Lo que fija y ningún test de service puede:

- que el **donut sea exclusivo de verdad**: cuando el cierre real ocurre, la captación pasa de
  *En evaluacion* a *Alquilada* sin contarse dos veces — y sigue contando como *Alquilada*
  aunque la cascada deje la captación CERRADA;
- que **`conversionPorPeriodo` nunca supere 100** con datos reales (es la regresión que la
  cohorte vino a arreglar);
- las **dos reglas de alcance distintas** de E4: el agente ajeno no ve nada, el broker ve a su
  equipo, y en el seguimiento el **AGENTE no alcanza por captación** aunque la captación sea suya;
- que la bandeja del `/dashboard` viaje **vacía** para BROKER y ADMIN, con el `pageSize` pedido;
- los aliases del seguimiento, el **techo de 8** y que `counts` y `options` miran conjuntos
  distintos;
- el aislamiento de tenant en los tres recursos, **incluidas las `options`**, que es donde más
  fácil se filtraría un valor de otra corredora.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite e4-dashboard
```

Última corrida: **120/120** (2026-08-01). Crea dos locales, dos captaciones, una prospección con
recontacto vencido, oportunidad, visita, interacción, solicitud y contrato; el `finally` los
retira y el propio script comprueba que no queda residuo. **Ojo con el orden de borrado**: crear
un local con `estadoPublicacion='P'` deja una publicación, y `psql -c` corre todo el bloque en una
sola transacción — un `DELETE` fuera de orden revierte la limpieza entera.

**La solicitud y el cierre entran por el API, no por SQL** (cambio del 2026-08-01). Sembrar el
contrato a mano dejó de ser legal —`ck_contrato_formalizado_completo` exige el snapshot (inicio,
fin, renta y moneda) para los estados D/V— y además mentía: dejaba la captación abierta y el local
publicado, un mundo que el cierre real no produce. Ahora el fixture registra la solicitud con
`POST /solicitudes` (+ `/reenviar` para dejarla en evaluación), el broker la aprueba con
`POST /evaluaciones` y el agente cierra con `POST /contratos`, la cascada de siete efectos. Dos
consecuencias en el propio script: la limpieza retira antes la **liquidación de comisión** y la
**evaluación** (no caen por cascada), y la línea base del ADMIN para el aislamiento de tenant se
vuelve a tomar **después** del cierre, porque la cascada cierra la captación del fixture.

Un detalle del cable que este script fija y conviene no "arreglar": en `operativo`, la fuente de
prospecciones **cae a todas las del alcance cuando la ventana no tuvo ninguna**. Por eso el
recontacto vencido no se comprueba como delta (la foto previa viene del fallback y la posterior de
la ventana) sino por el valor resultante.

## `e2e-s0-sesiones.ps1` — la sesión se puede matar (D-S0-12)

Bloque 3, primera entrega. Demuestra lo que hasta V29 **no se podía demostrar**: que un token
**bien firmado y sin expirar** deja de servir en cuanto la cuenta cierra sesión. Hasta entonces
"cerrar sesión" era un `localStorage.removeItem` y el token seguía vivo hasta caducar.

Comprueba, en este orden: que el token lleva `iat` —la pieza sobre la que se apoya todo, y que si
desapareciera rompería la invalidación **en silencio**—, que sigue **sin** llevar la organización
(el contrato del token no se toca), que `POST /auth/logout` responde 204 y sella
`sesiones_invalidas_desde`, que el **mismo** token pasa a 401 con el mensaje congelado
—*"Token invalido o expirado."*, el mismo que un token corrupto: no se dice **por qué** dejó de
valer—, que un login posterior sí funciona y que la sesión de otra cuenta no se toca.

Borde documentado: `iat` tiene precisión de segundo, así que el script espera 1 s antes de volver
a entrar. Última corrida: **11/11**.

## `e2e-s0-bloqueo.ps1` — bloqueo por cuenta e IP y auditoría (D-S0-21 + §6.3)

Bloque 3, segunda y tercera entrega. **Es la única suite que corre con umbrales bajos**
(`CUENTA=3`, `IP=50`, que le fija `Invoke-E2E.ps1`): necesita provocar el bloqueo a propósito. El
resto corre con umbrales altos para no bloquearse a sí mismo — aunque el contador **solo cuenta
fallos**, así que los logins correctos de las demás suites no consumen cupo de todas formas.

Lo que demuestra:

- la dimensión **cuenta** existe y frena lo que el limitador anterior no frenaba: fuerza bruta
  contra **una sola cuenta** desde muchas IPs;
- un usuario **inexistente** se cuenta igual y acaba en 429 con el **mismo cuerpo** — si solo
  contaran las cuentas reales, el propio bloqueo sería un oráculo del padrón;
- la cuenta bloqueada rebota **aunque la contraseña sea correcta**: el bloqueo se evalúa antes de
  comprobar el hash, para que el tiempo de respuesta no delate nada;
- `intento_acceso` **no guarda el usuario en claro** (SHA-256, 64 hex) y cuenta en las dos
  dimensiones;
- la auditoría registra los tres desenlaces (`LOGIN_OK`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO_429`),
  el fallido anónimo se registra **sin persona**, el motivo nombra la **dimensión y nunca la
  cuenta**, y ni una contraseña del fixture aparece en `detalle_json` ni en `motivo`;
- el logout deja **dos** eventos y no uno: `LOGOUT` y `SESIONES_INVALIDADAS` son hechos distintos.

Última corrida: **21/21**.

## `e2e-s0-contrasenas.ps1` — contraseñas y recuperación (§4.2–§4.5)

Bloque 4. Cierra las dos carencias más básicas del diagnóstico: **H-02** (no existía *ninguna*
forma de cambiar una contraseña — el `PUT` de brokers y agentes **ignoraba** el campo en silencio)
y **H-08** (no existía *ninguna* forma de recuperar el acceso salvo entrar a la base a mano).

Lo que demuestra, en doce secciones:

- el cambio exige la contraseña **actual** (sin eso, una sesión robada se queda con la cuenta) y
  **mata todas las sesiones, incluida la que llama**;
- la política pide **longitud** y rechaza la clave común y la que lleva el nombre de usuario, pero
  **acepta una frase larga en minúsculas** — exigir mayúscula + dígito + símbolo fabrica
  `Clave2026!`, que es exactamente el patrón del seed que este bloque retira;
- no se puede volver a una de las últimas contraseñas, y el historial guarda **hashes**;
- la recuperación responde **202 siempre** —usuario real, inexistente o cuerpo vacío— y solo la
  cuenta real emite token;
- **ni el agente ni el broker pueden invitar**, ni siquiera a su propio equipo (D-S0-18): es
  gobierno del tenant;
- en la base vive el **SHA-256** del token, nunca el token; emitir uno nuevo mata el anterior; el
  canje sirve **una sola vez**; una persona de otro tenant responde **404, no 403**;
- la contraseña temporal la **genera el sistema**, no la elige el administrador, y deja la sesión
  **capada de verdad**: cualquier operación responde 403 con `CAMBIO_CONTRASENA_REQUERIDO`, pero
  `GET /perfil` y el cambio sí pasan;
- la auditoría registra los seis eventos del bloque y **no filtra ni el token ni la temporal**.

Dos trampas que esta suite se comió al escribirse y que conviene no repetir:

1. **El orden de las validaciones.** Intentar "volver a la contraseña del fixture" para probar la
   regla de reutilización no la prueba: `Agente2026` tiene 10 caracteres y la corta antes la regla
   de longitud. La suite hace **dos** cambios y vuelve al primero, que sí es largo.
2. **`-notmatch` de PowerShell ignora mayúsculas.** Comprobar que la temporal no lleva `[Il1O0]`
   con `-notmatch` también casa con la `o` y la `i` minúsculas —que sí están en el alfabeto, porque
   solo son ambiguas frente a caracteres ya retirados—. Va con `-cnotmatch`.

Última corrida: **59/59**.

## `v6-dos-organizaciones.sql` — criterio #7 del gate

Crea dos organizaciones técnicas con los **mismos** códigos comerciales
(`AGE-001`, `BRK-001`, `LOC-0001`, `CAP-0001`, `PRO-0001`, login `jperez`),
comprueba que conviven, que dentro de cada organización la unicidad sigue
vigente, que sigue habiendo un solo administrador por organización y que las
FK compuestas rechazan un cruce de tenant.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite v6-dos-organizaciones
```

Termina en `ROLLBACK`: **no deja nada** en la base. Última corrida: 5/5 (2026-08-01).

## `v6-rollback.sql` — evidencia histórica archivada

Este script documenta el antiguo gate V6→V5, pero **no es una suite ejecutable sobre V20**:
V7–V20 dependen de tenancy y pretender retirar V6 debajo de ellas produciría un esquema
imposible. Se retiró del `ValidateSet` del wrapper. La reversibilidad actual se demuestra con
backup/restore completo de PostgreSQL y reconstrucción desde cero por las 20 migraciones, no
modificando el historial de Flyway.
