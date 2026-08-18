# D-E4-3 · Quién es la autoridad de cada dato de la propiedad

**Qué decide:** para cada dato que `/captura/definicion` publica, **dónde vive
de verdad**. Exactamente una autoridad por clave.

**Estado:** **CERRADA el 2026-08-18**, los once pasos. Una sola autoridad
por clave, las seis columnas espejo retiradas del esquema y del agregado (V62), y
835 pruebas verdes con las 37 de integración ejecutadas de verdad. **Angular no
necesitó ningún cambio**, que es la prueba de que el contrato lógico no se movió.

**Por qué existe:** siete conceptos viven hoy **a la vez** como columna de
`propiedad` y como fila de `atributo_propiedad`. Solo `metraje` se sincroniza,
así que registrar un departamento por el modelo universal deja
`propiedad.ambientes` y `propiedad.cuota_mantenimiento` en NULL mientras el
atributo tiene el valor — y las pantallas que leen la columna los muestran en
blanco. Comprobado contra el servidor, no deducido:

```
COLUMNA   metraje=90.00   ambientes=NULL   cuota_mant=NULL
ATRIBUTO  metraje_total=90  ambientes=5  cuota_mantenimiento=350  dormitorios=2
```

---

## 1. La regla

> **Cada clave publicada tiene UNA autoridad persistente declarada.**
> Cero autoridades = campo fantasma: se pide y no se guarda.
> Más de una = doble verdad: se guarda dos veces y divergen.

Y su consecuencia, que es lo que impide el atajo:

> **Sincronizar las columnas espejo NO es la solución.** `guardar atributo →
> copiar columna` deja el sistema con aspecto de arreglado y con dos verdades
> dentro. Solo vale como puente dentro del mismo corte, con fecha de muerte y
> con un gate que falle si divergen.

---

## 2. La clasificación, y en qué se apoya

### La regla

> **ESTRUCTURAL** cuando el concepto es **transversal al tipo de propiedad** y
> forma parte estable de la **identidad, la integridad, la búsqueda primaria o
> los invariantes centrales** del agregado.
>
> **ATRIBUTO GOBERNADO** cuando su **aplicabilidad y su semántica dependen del
> tipo**, y puede evolucionar por catálogo.

**No es «si participa en una decisión, es estructural».** Esa formulación es
más simple y es una trampa: el día que el matcher empiece a cruzar por
`zonificacion`, alguien concluiría que hay que crearle una columna. Un atributo
gobernado puede entrar perfectamente en filtros, en matching y en agregados sin
dejar de ser un atributo — lo que decide no es *si se usa*, sino *si su
aplicabilidad depende del tipo*.

La evidencia de abajo mide **quién lo lee y para qué**, que es lo que permite
aplicar la regla sin discutirla.

| Concepto | Autoridad | Evidencia |
|---|---|---|
| **`metraje`** | **CANONICO_ESTRUCTURAL** | Participa en la **detección de duplicados** (`areaAproximada`, `LocalComercialServiceImpl:201`), viaja en la proyección de captación como `areaM2` (`CaptacionRepository:114`), está en la del listado (`PropiedadRepository:103`) y tiene **23 usos en Angular**. Es transversal de verdad. |
| `ambientes` | ATRIBUTO_GOBERNADO | Un solo uso en persistencia: la columna del SELECT de la ficha. No filtra, no busca, no cruza. |
| `frente` | ATRIBUTO_GOBERNADO | **La evidencia de esta fila estaba mal y se corrigió el 2026-08-18.** No era «solo proyección»: entra en el **puntaje del matcher** (`CoincidenciaCartera.evalFrente`) y viaja en la fila de coincidencia. La **clasificación no cambia** —participar en el matching no vuelve estructural a un atributo, que es justo lo que dice la regla de arriba— pero el trabajo sí: era un tercer lector que migrar, y el más callado de todos. Ver §4 quater. |
| `zonificacion` | ATRIBUTO_GOBERNADO | Ídem: solo proyección. |
| `cuota_mantenimiento` | ATRIBUTO_GOBERNADO | **Cero** usos en la capa de persistencia. |
| `numero_estacionamientos` | ATRIBUTO_GOBERNADO | **Cero** usos. |
| `antiguedad_anios` | ATRIBUTO_GOBERNADO | **Cero** usos. |

**Uno de siete es estructural.** Y no por tamaño ni por antigüedad: porque es el
único que otra parte del sistema *usa para decidir algo*.

---

## 3. Cómo se declara la autoridad

`catalogo_atributo` gana **dos** columnas, y las dos hacen falta:

```
destino = ATRIBUTO      campo_estructural = NULL      el valor vive en atributo_propiedad
destino = ESTRUCTURAL   campo_estructural = METRAJE   el valor vive en el campo canónico METRAJE
```

con dos CHECK que impiden el estado a medias:

```sql
destino = 'ATRIBUTO'    →  campo_estructural IS NULL
destino = 'ESTRUCTURAL' →  campo_estructural IS NOT NULL
```

**Por qué no basta `destino`.** Con solo `ESTRUCTURAL`, alguien tendría que
escribir en Java `si clave == "metraje_total" → propiedad.metraje`, y eso es la
misma matriz de antes escondida en otro sitio. `campo_estructural` dice **qué
concepto representa**, y la persistencia sabe cómo guardar ese concepto: añadir
un segundo estructural mañana no toca ningún `if`.

**Y por qué el valor es `METRAJE` y no `propiedad.metraje`.** El catálogo no
debe conocer la topología física de PostgreSQL. `METRAJE` es un concepto del
dominio; que hoy viva en una columna llamada `metraje` de una tabla llamada
`propiedad` es asunto de la capa de persistencia, y cambiarlo no debería tocar
una fila de catálogo.

Con eso el gate se vuelve encadenable de verdad:

```
clave publicada → autoridad declarada → destino lógico → un mecanismo de persistencia
```

Con eso, `metraje_total` **sigue publicándose** en la definición de captura
—porque es una pregunta legítima para los siete tipos— pero declara que su
valor se escribe en `propiedad.metraje` y **no** en `atributo_propiedad`. Deja
de haber dos filas para el mismo hecho.

Es preferible a sacar `metraje_total` del catálogo: si saliera, el motor de
captura perdería su rótulo, su unidad, su obligatoriedad por tipo y su orden, y
habría que reescribirlos a mano en otro sitio — que es volver a tener dos
fuentes, ahora de la *definición* en vez del *valor*.

---

## 3 bis. La medición previa, hecha el 2026-08-18

**No se decide quién gana por haber declarado una autoridad.** Primero se mide,
porque una divergencia real no la arbitra una decisión de diseño.

| Concepto | solo columna | solo atributo | iguales | **divergentes** |
|---|---|---|---|---|
| metraje | 0 | 0 | 23 | **0** |
| ambientes | 0 | **1** | 21 | **0** |
| antiguedad_anios | 0 | 0 | 21 | **0** |
| cuota_mantenimiento | 0 | **1** | 1 | **0** |
| numero_estacionamientos | 0 | 0 | 1 | **0** |
| frente | 0 | 0 | 0 | **0** |
| zonificacion | 0 | 0 | 1 | **0** |

**Cero divergencias: la migración no está bloqueada.** No hay ninguna fila en
la que columna y atributo digan cosas distintas, así que no hay que arbitrar
nada.

> **Pero los dos `solo_atributo` no son deuda histórica: son la fuga en
> marcha.** Las dos filas son de la propiedad 1034, registrada por
> `/propiedades` mientras se hacía esta auditoría. Cada alta por el modelo
> universal añade una fila que las pantallas actuales no leen.
>
> Por eso **cortar la escritura va antes que el backfill**: el backfill de hoy
> son dos filas, y mañana serán las que se hayan acumulado.

---

## 4. La secuencia, y ningún paso se salta

El orden completo, con la corrección operativa de que **cortar la fuga va
inmediatamente después del gate** y antes de tocar dato histórico:

| # | Paso | Estado |
|---|---|---|
| 1 | D-E4-3 endurecido con `campo_estructural` y la regla estable | ✅ |
| 2 | Migración: `destino` + `campo_estructural` y clasificación del catálogo (V60) | ✅ |
| 3 | **Gate de autoridad** — una y solo una por clave publicada | ✅ |
| 4 | **Cambiar escritores y cortar la fuga** (camino universal) | ✅ |
| 5 | Consolidar `metraje` y retirar su copia de `atributo_propiedad` (V61) | ✅ |
| 6 | Backfill histórico de los seis (V61) | ✅ |
| 7 | Migrar lectores con carga por página — **y el escritor de `/locales`** | ✅ |
| 8 | Comprobar **0 lectores y 0 escritores** antiguos | ✅ |
| 9 | Eliminar físicamente las seis columnas (V62) | ✅ |
| 10 | **E2E de ida y vuelta** en verde | ✅ |
| 11 | Angular sin conocimiento de almacenamiento | ✅ — no hizo falta tocarlo; ver §7 |

**Angular queda fuera hasta que el paso 10 esté verde.** A esta altura no se
está arreglando un formulario: se está arreglando la semántica de persistencia
de `Propiedad`, y dejar que una interfaz nueva empiece a depender de ella antes
de terminarla obliga a rehacer las dos.

### Para `metraje`, además

Antes de borrar **una sola fila** `metraje_total` de `atributo_propiedad`, las
cuatro categorías tienen que estar medidas:

| Columna | Atributo | Qué se hace |
|---|---|---|
| tiene | no tiene | correcto, nada que hacer |
| no tiene | tiene | **recuperar** atributo → estructural antes de borrar |
| iguales | iguales | eliminar la copia, después |
| **distintos** | | **BLOQUEAR** la migración |

Medido el 2026-08-18: 23 iguales, 0 en las otras tres. Vía libre.

---

## 4 bis. La frontera de `deVarias(ids)` — dónde se equivocará el siguiente

Hidratar por página es correcto y **no es N+1**: son dos consultas por página.

```
1. SELECT la página de propiedades   (LIMIT/OFFSET)
2. SELECT atributos WHERE id_propiedad IN (...)
```

**Pero solo vale para MOSTRAR.** En cuanto el atributo entre en un filtro o en
un orden, tiene que ir en SQL **antes** del `LIMIT/OFFSET`:

| Necesidad | Cómo |
|---|---|
| mostrar `ambientes` en la ficha | `deVarias(ids)` sobre lo ya paginado |
| filtrar `ambientes >= 4` | JOIN o subconsulta sobre `atributo_propiedad`, antes de paginar |
| ordenar por `cuota_mantenimiento` | ídem |
| buscar `zonificacion = 'CZ'` | ídem |

Lo que **nunca** se hace es «pagino 8, cargo atributos, filtro 3, muestro 5»:
rompe el conteo, rompe las páginas y rompe el orden — y lo hace en silencio,
porque la pantalla enseña cinco filas sin decir que faltan tres.

---

## 4 ter. La simetría de lectura, que es la lección de esta tanda

> **Si el escritor enruta por autoridad, el lector también. Y lo hace la MISMA
> capa que conoce `destino`, no cada caso de uso por su cuenta.**

No estaba escrito, y por eso pasó: al mover `metraje` a su campo canónico se
movió el escritor y no el lector. El dato seguía guardándose bien y **dejó de
poder leerse por el API** — desapareció de la lista de atributos de la ficha
universal. Es el fallo que la regla del trazado persigue, cometido al arreglar
otro.

La corrección no es «arreglar ese sitio»: arreglado ese, el siguiente cambio de
autoridad rompe el siguiente lector. Lo que cierra la clase de fallo es que la
resolución viva en un solo componente:

```
clave lógica  ->  autoridad declarada  ->  valor
```

`LectorPorAutoridad` (service/soporte) es ese componente, simétrico de
`AtributosGobernados.enrutar` + `EscritorEstructural`. Para el consumidor sólo
existe esto:

```
metraje_total       = 90
ambientes           = 5
cuota_mantenimiento = 350
```

sin saber que el primero sale de `propiedad.metraje` y los otros dos de
`atributo_propiedad`. **La autoridad física cambia, el contrato lógico no.**

**Nombrar la clave no es saber dónde vive.** Un consumidor con un campo llamado
`ambientes` tiene que pedir `ambientes` por su nombre; no hay otra forma. Lo que
D-E4-3 prohíbe es lo otro: que el consumidor decida, **a partir de la clave**, en
qué tabla buscarla. Si mañana `ambientes` se promoviera a estructural, ni una
línea de `LocalComercialServiceImpl` cambiaría.

Y una consecuencia operativa, porque acaba de demostrar su valor: **el
round-trip se ejecuta después de CADA cambio de autoridad**, no sólo al final.
El E2E formal puede cerrar la tanda, pero el humo `crear → leer → editar otra
cosa → releer` acompaña a los pasos 7-9. Encontró un error que ni el compilador,
ni los gates de esquema, ni las pruebas de persistencia podían ver.

---

## 4 quater. Lo que la búsqueda del paso 8 encontró, y no era lo previsto

El plan decía «migrar esos dos lectores». La búsqueda global dijo otra cosa, y
por eso el paso 8 va **antes** de dar por cerrado el 7 y no después:

| Hallazgo | Por qué importaba |
|---|---|
| **`LocalComercialServiceImpl:534-542` seguía ESCRIBIENDO las seis columnas** | El paso 4 cortó la fuga sólo en el camino universal. `/locales` seguía siendo una isla coherente consigo misma: escribía columna y leía columna. **Migrar sólo su lector habría convertido cada PUT en pérdida silenciosa** — el mismo fallo que se acababa de arreglar, con el espejo puesto. Lector y escritor tenían que entrar en el mismo cambio. |
| **`CoincidenciaCartera.evalFrente` leía `propiedad.getFrente()`** | Tercer lector, y el más callado: leer una columna vacía no falla. Convierte el criterio del frente en «no aplica» y **mueve el puntaje de cada coincidencia sin avisar a nadie**. Tres sitios lo llamaban, uno dentro de dos bucles anidados, así que además había que hidratar por lote antes de entrar. |
| **El `DROP COLUMN` se llevaba cuatro CHECK de rango** | `ambientes > 0`, y `>= 0` en antigüedad, estacionamientos y mantenimiento, todos de V4. `atributo_propiedad` no tenía con qué sustituirlos: su trigger valida el **tipo** del valor, no su rango. Borrar sin más habría cambiado «ambientes > 0» por «ambientes cualquier cosa» en silencio: la misma pérdida callada, esta vez en el invariante en vez de en el dato. V62 los muda a `catalogo_atributo.valor_minimo` **antes** del DROP, para que el invariante no deje de existir en ningún momento. |

El rango se declara en el **catálogo** y no en el código por la misma razón que
el tipo de dato: la clave la puede añadir un tenant, y su rango es parte de lo
que la define.

> **Deuda declarada:** el mínimo lo impone hoy sólo el trigger, así que un valor
> fuera de rango sale como error de PostgreSQL a mitad de transacción. Subirlo a
> `AtributosGobernados` daría el mensaje que se entiende, igual que ya pasa con
> el tipo. No es urgente: es exactamente el comportamiento que había antes.

---

## 4 quinquies. El coste de la hidratación, medido

Ningún camino pasó de N+1. Consultas **por página**, no por fila:

| Camino | Antes | Ahora |
|---|---|---|
| `GET /locales` (listado) | 3 (página + portadas + publicación) | **4** — una más, `deVarias(ids)` |
| `GET /locales/{id}` | 1 ficha + 2 | 1 ficha + 2 + **1** |
| `/mis-locales` | página + 2 lotes | página + 2 lotes + **1** |
| matcher (cartera → cliente) | 1 por candidata | **1 para toda la cartera candidata**, antes del bucle |

`metraje` **se queda en la proyección** del listado, y no por comodidad: es el
único estructural, y un listado tiene que poder ordenar y filtrar por él — y eso
sólo se hace en SQL, antes del `LIMIT`. El día que alguna de las seis entre en un
filtro o un orden, la solución **no** es devolverla a la proyección sino unir
contra `atributo_propiedad` antes de paginar (§4 bis). Hoy ninguna se filtra ni
se ordena.

---

## 5. Los dos gates que impiden la recaída

**Gate de autoridad** — recorre todas las claves que `/captura/definicion`
publica para los siete tipos × dos operaciones y exige exactamente una
autoridad por clave. Cero es un campo fantasma; dos es una doble verdad. Es el
gate que habría hecho imposible este estado.

**E2E de ida y vuelta** — `crear → leer → editar sin tocar el campo → guardar →
releer`, y el valor tiene que ser idéntico. Habría cazado los dos fallos de esta
tanda: `exclusividad`, que se escribía y no se devolvía, y estos siete, que se
guardan en un sitio y se leen de otro.

> El segundo es el más barato de escribir y el que más cubre: **casi todo lo que
> se pierde, se pierde al no tocarlo.** Un campo que el usuario edita se nota
> roto enseguida; uno que solo pasa por el formulario sin que nadie lo mire
> desaparece en silencio.

**Los dos están escritos**, en `AutoridadDelDatoIntegrationTest`:

| Test | Qué impide |
|---|---|
| `unaAutoridadPorClave` | cero autoridades (campo fantasma) o dos (doble verdad) |
| `laDeclaracionEstaCompleta` | un ESTRUCTURAL sin concepto, o un concepto con pinta de nombre físico |
| `laDeudaDeColumnasEspejoNoCrece` | **cambió de sentido**: la lista está vacía, así que ya no vigila que la deuda encoja sino que **no vuelva** |
| `elLectorEnrutaComoElEscritor` | que un cambio de autoridad saque un valor de la respuesta del API |
| `localesIdaYVuelta` | crear → leer por ficha → leer por listado → **editar sólo el precio** → releer los seis |
| `vaciarUnGobernadoLoRetira` | que un campo vaciado se quede con el valor viejo pegado (PUT manda el objeto entero) |
| `elRangoDeV4SobrevivioAlDrop` | que el `DROP COLUMN` se lleve un invariante sin reemplazo |
| `metrajeNoTieneCopia` / `loQueFaltaMiraLasDosAutoridades` | la copia de `metraje`, y medir lo que falta en la tabla equivocada |

---

## 6. Evidencia del cierre (2026-08-18)

```
V62 aplicada       columnas espejo restantes = 0
                   rangos mudados al catálogo = 5 (ambientes 1; los otros cuatro 0)
arranque           ControlLocalApplication levanta con ddl-auto: validate contra
                   el esquema sin las seis columnas
suite              833 pruebas, 0 fallos, 0 errores, 0 SKIPPED
                   (631 unitarias + 43 arquitectura + 159 app, con TEST_DB_URL
                    puesto: las 37 de integración se ejecutaron de verdad)
```

El **0 skipped** es la mitad de la evidencia: sin `TEST_DB_URL` las de
integración se saltan en silencio y Maven termina en verde. Así fue como V31,
V37 y V38 pasaron tres columnas `estado` de palabra completa con el build verde
durante un bloque entero.

**Lo que la búsqueda del paso 8 devolvió**, y es lo que autorizó el DROP:

```
getters/setters de las seis en código de producción ....... 0
las seis en JPQL o SQL nativo ............................. 0
las seis en scripts de verificación u operación ........... 0
referencias restantes ..... nombres de campo de LocalRequest/LocalResponse
                            (contrato lógico: se queda, es justo lo que no cambia)
```

### Un fallo ajeno que estas ejecuciones destaparon

`NucleoUniversalIntegrationTest.elOutboxGuardaYSeConsume` comprobaba «recién
escrito, está pendiente» mirando si el evento aparecía en la ventana de los **50
más antiguos**. La ventana está ordenada por id ascendente a propósito —un
consumidor drena el outbox en orden— así que el evento nuevo es siempre el
último de la cola. Mientras la tabla tuvo menos de 50 filas el test pasó; al
llegar a 52 pendientes (nadie drena el outbox en dev) empezó a fallar **por su
propio éxito**, sin relación con lo que pretendía probar.

Ahora pregunta por **su** id, y a la tabla: `marcarProyectados` es un update
masivo de JPQL que va directo a la base y **no refresca la entidad ya cargada**,
así que `findById` habría devuelto el objeto viejo y el test habría dicho que el
marcado no funcionó cuando sí funcionó. La ventana se sigue comprobando, pero
por lo que de verdad garantiza: un tope y un orden.

---

## 7. Paso 11 — Angular, y por qué no hubo migración

> **Regla, ahora con gate:** el SPA puede conocer la **clave lógica**
> (`ambientes`) y el **tipo de dato funcional** (entero, decimal, texto).
> **Nunca la autoridad física.**

Nombrar `ambientes` en un formulario no viola nada: es lenguaje inmobiliario, y
un consumidor no tiene otra forma de pedir un dato que por su nombre. Lo que
violaría la arquitectura es decidir que `ambientes` pertenece a
`atributo_propiedad`.

### La búsqueda dirigida, y qué salió

| # | Qué se buscó | Resultado |
|---|---|---|
| 1 | consumidores de las seis claves | 8 ficheros, **todos legítimos**: DTO tipado (`frente: number \| null`), plantillas de presentación y controles de formulario |
| 2 | conversiones o parsing especial | **ninguno**. Ni un `String` donde debería ir un número, ni un fallback entre representación vieja y nueva |
| 3 | defaults `\|\| 0`, `Number(...)`, `parseFloat(...)` | **ninguno sobre las seis**. Los `?? null` de los numéricos conservan «no se sabe»; el `?? ''` de `zonificacion` es la exigencia de un input de texto y `textoOpcional()` lo devuelve a null al enviar |
| 4 | validaciones de aplicabilidad por tipo | **ninguna**. Los únicos `tipoInmueble` del SPA son etiquetas de presentación |
| 5 | formularios que crean que vacío = «no modificar» | **ninguno**. `local-form` manda el objeto entero con sus nulos, que es exactamente la semántica que el backend implementó en `fijar()` |
| 6 | tests que congelaran la representación anterior | **ninguno**. Los fixtures usan números, no cadenas |

Y el gate en sí — vocabulario de almacenamiento en el SPA — devolvió **cero**:
ni `atributo_propiedad`, ni `catalogo_atributo`, ni `campo_estructural`, ni
`valor_numero`/`valor_texto`.

**Por eso no hubo migración de campos: no había nada que migrar.** El SPA nunca
supo dónde vivían esos valores, y por eso mover la autoridad no le llegó. Es el
resultado que la decisión prometía, comprobado desde el otro extremo.

### El gate, y muerde

`FronteraDeAutoridadEnElSpaTest` (en el módulo `controllocal-app`, junto a los
otros gates estructurales) hace dos cosas:

| Test | Qué impide |
|---|---|
| `elSpaNoSabeDondeSeGuardaNada` | que un nombre de tabla o de columna llegue a un `.ts` o un `.html` |
| `elContratoLogicoNoSeMovio` | lo contrario: que las seis **desaparezcan** del contrato del SPA. Si alguien «migra» el frontend detrás del backend, es que el contrato lógico se movió también |

Vive en el backend a propósito: ahí está la autoridad, así que ahí es donde se
puede romper, y así corre dentro del gate de cierre. Comprobado inyectando una
violación real: falla nombrando fichero, línea y motivo.

### La deuda que sí queda declarada

Cuatro mínimos viven **dos veces**: en `catalogo_atributo.valor_minimo` (que V62
creó) y en el formulario del SPA.

| Regla | Catálogo | `local-form` |
|---|---|---|
| `ambientes` | `valor_minimo = 1` | `enteroOpcional(1)` + `min="1"` |
| `antiguedad_anios` | `0` | `enteroOpcional(0)` + `min="0"` |
| `estacionamientos` | `0` | `enteroOpcional(0)` + `min="0"` |
| `cuota_mantenimiento` / `frente` | `0` | `Validators.min(0)` + `min="0"` |

**No es conocimiento de almacenamiento**, así que no bloquea el cierre del paso
11 — pero sí es una mini-autoridad paralela: un rango no es ni la clave ni el
tipo, es una **regla**, y la regla ya tiene dueño. `step="1"` frente a
`step="0.01"` sí es legítimo: eso es el tipo de dato funcional, que el SPA puede
conocer.

Cerrarlo bien es hacer que la aplicabilidad, el rango, la obligatoriedad y la
unidad **viajen desde el catálogo** como contrato, y eso es la normalización del
alta de propiedades — no esta tanda. Se anota aquí para que el día que se haga,
se haga completo y no campo a campo.

> Mismo motivo por el que el 400 de «`frente` no aplica a una OFICINA» **no** se
> replica con un `if (tipo === "OFICINA")` en Angular: es mejor producto que
> aceptar el dato en silencio, pero la regla la publica el catálogo, no la
> reimplementa el formulario.

---

## 8. Y aquí se para

Esta decisión queda **cerrada**. Lo que habilita —ampliar a casa, departamento,
terreno u oficina sin añadir una columna por cada tipo— es real, pero **no se
sigue normalizando propiedades ahora**: el trabajo vigente es E2 (dashboard y
normalización transversal del SPA), y una victoria técnica que se convierte en
túnel deja de ser una victoria.

La conexión con lo que sigue es directa y conviene no perderla: la auditoría del
SPA encontró **167 decisiones de estado en Angular** y dejó como regla que
riesgo, clasificación, vencimiento y prioridad vuelvan al backend. Sería
incoherente consolidar la autoridad del dato y volver a introducir una autoridad
paralela en los formularios — que es exactamente lo que describe la deuda de
arriba.

