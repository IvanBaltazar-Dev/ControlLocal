# Diagnóstico del pico de RC-003 en el gate de búsqueda de F3

**Fecha:** 2026-08-03 · **Estado:** **CERRADO. F3 FIRMADO** con la corrida 4
(`20260803093503-7523`) tras aplicar la opción C. Ver §9.

Este documento existe porque el gate `e2e-demanda-busqueda.ps1` falló dos
corridas consecutivas en un único control, y la regla acordada era clara: si el
pico se reproduce, **no se firma y se investiga**; y **no se toca el umbral para
aprobarlo**. El pico se reprodujo. Esto es lo que se encontró.

---

## 1. Las dos corridas de firma

Mismo árbol de trabajo, mismo artefacto
(`controllocal-app-2.0.0-SNAPSHOT.jar`, 2026-08-02 16:40), banco completo de
100.000 filas por tabla, entorno E2E aislado y efímero en las dos, máquina en
reposo, sin builds ni suites en paralelo.

| | Corrida 1 | Corrida 2 |
|---|---|---|
| Identificador | `20260803064802-4596` | `20260803070905-9444` |
| Resultado | **68 OK / 1 FALLA** | **68 OK / 1 FALLA** |
| Control que falla | §10, criterio 3 | §10, criterio 3 |
| Escenario | `VIS casa TODO - profunda` | `VIS casa TODO - profunda` |
| p50 / p95 / **peor** | 1.322 / 1.726 / **3.357 ms** | 1.435 / 1.977 / **3.309 ms** |
| Clasificación | 9 discriminantes / 9 no discriminantes | 9 / 9 |
| Semántica, conteo/página, KPI, planes, guardas, limpieza | verde | verde |

El clasificador corregido —el que decide si un escenario es discriminante por la
cardinalidad del **término aislado**, con clave `(módulo, término)`, y ya no por
el `totalRecords` de una respuesta que mezcla texto con otros filtros— funcionó
correctamente en las dos: 18 escenarios, 18 con su medida aislada localizada, 9
y 9. Ese arreglo **no está en discusión**; lo que sigue es sobre el criterio 3.

**Evidencia cruda conservada, sin editar**, en `backend-spring/verificacion/evidencia/`:

| Fichero | Qué contiene |
|---|---|
| `2026-08-03-f3-corrida-1.log` | corrida de firma 1 completa |
| `2026-08-03-f3-corrida-2.log` | corrida de firma 2 completa |
| `2026-08-03-f3-corrida-3-instrumentada.log` | corrida 3, con log de pausas de JVM activo |
| `2026-08-03-f3-diagnostico-1-profunda.log` | series alternadas, banco asentado, curva de `OFFSET`, planes |
| `2026-08-03-f3-diagnostico-2-atribucion.log` | tres caminos: Windows, dentro de Docker, sólo PostgreSQL |
| `2026-08-03-f3-diagnostico-3-transporte-corto.log` | 3.000 llamadas a `/salud` por camino |
| `2026-08-03-f3-jvm-pausas.log` | `-Xlog:gc,safepoint` de la corrida 3 |
| `2026-08-03-f3-postgres-sentencias.log` | sentencias > 1.500 ms de la corrida 3 |

---

## 2. El pico no es proporcional: es una suma constante

Puesto en fila, el exceso sobre la mediana de cada escenario es el mismo número
siempre, en escenarios de coste base muy distinto:

| Corrida | Escenario | p50 | Peor | Exceso |
|---|---|---|---|---|
| 1 | `VIS casa TODO - profunda` | 1.322 | 3.357 | **+2.035** |
| 1 | `INT sin texto - pagina 1` | 96 | 2.144 | **+2.048** |
| 2 | `VIS casa TODO - profunda` | 1.435 | 3.309 | **+1.874** |
| 2 | `INT sin texto - pagina 1` | 105 | 2.159 | **+2.054** |
| 3 | `INT casa TODO - profunda` | 528 | 2.442 | **+1.914** |
| diag. 1 | `VIS profunda`, banco asentado | 1.326 | 3.324 | **+1.998** |

Una consulta cuya mediana son **96 ms** no llega a 2.144 ms por coste de
consulta, por plan ni por `OFFSET`. Algo añade **~2 segundos** a una llamada
suelta, y el escenario sobre el que cae decide si el gate pasa o no: en la
corrida 3 cayó sobre uno de línea base barata, se quedó en 2.442 ms y **el gate
pasó (69 OK / 0 FALLAS) sin que el producto hubiera cambiado en nada**.

---

## 3. Qué se descartó, y con qué medida

### El `OFFSET` — descartado

La curva de profundidad por HTTP es **plana**: misma consulta, mismo término.

| Página | offset | p50 |
|---|---|---|
| 1 | 0 | 1.309 ms |
| 2.500 | 24.990 | 1.206 ms |
| 5.000 | 49.990 | 1.256 ms |
| 7.500 | 74.990 | 1.244 ms |
| 10.000 | 99.990 | 1.298 ms |

Y en SQL, la misma consulta con `offset 0` usa `top-N heapsort` de 25 kB y tarda
**797 ms**; con `offset 99990` pasa a `external merge Disk: 2160kB` y tarda
**907 ms**. El salto cuesta **~110 ms de ~900**.

> **Corrección pendiente de documentación.** `backend-spring/README.md` y la §5
> de `docs/ai/contrato-listados-paginados.md` afirman que en la página profunda
> «lo que se paga es el `OFFSET` recorriendo las ~99.990 entradas de índice que
> salta». Para el camino **con texto** eso es falso: lo que se paga es
> **construir y deduplicar el conjunto de candidatos, dos veces por llamada**
> (conteo 678 ms + página 907 ms). Para el camino **sin texto** sí es cierto
> (707 ms en la página 1 contra 1.716 en la profunda). La frase hay que
> separarla por camino.

### La espera de disco — descartada

Todos los buffers son `shared hit`. Los ficheros temporales del `external merge`
y del `HashAggregate` suman `I/O Timings: temp read=0.830 write=3.901` ms.
PostgreSQL corre sobre `tmpfs` en este entorno: el derrame a «disco» es RAM.

### El plan — descartado

`Seq Scan` + `Hash Join` sobre las tablas grandes, que es **el plan correcto**
para un término que casa con el 100 % del banco (es lo que el criterio 2
reconoce explícitamente), y `Bitmap Index Scan` por el trigrama en la rama
selectiva. Plan personalizado forzado por `PlanDeConsulta`; planificación 20-22
ms. No hay plan genérico ni degradación entre llamadas.

### El mantenimiento posterior a la carga — descartado

Con el banco **asentado** —`vacuum (analyze)` de las nueve tablas, `checkpoint`
explícito y espera activa hasta que no quedara ningún *worker* de autovacuum— el
pico volvió a salir, incluso algo mayor (3.324 ms). No es autovacuum ni son los
*checkpoints* posteriores a la carga masiva.

### PostgreSQL — descartado

Corrida 3 con `log_min_duration_statement = 1500`. En **toda** la corrida sólo
seis sentencias superaron 1,5 s: **cinco son los `INSERT` de la carga del
banco** (07:58–08:01) y la sexta es la **limpieza final**. Durante la fase de
medición completa —§6 y §7, de 08:01 a 08:16— **PostgreSQL no ejecutó una sola
sentencia por encima de 1.500 ms**, mientras el cliente registraba 2.442.

### La JVM — descartada

Misma corrida con `-Xlog:gc,safepoint`. **433 pausas registradas; la más larga,
18,8 ms.** El heap se mueve en 156 MB. No hay ninguna pausa de parada del mundo
capaz de explicar 2 segundos.

### El modelo «una llamada de cada N» — descartado

3.000 llamadas a `/salud` —que no consulta la base— desde Windows: p50 **20 ms**,
p99 44 ms, y **una sola** por encima de 500 ms, que además fue la **N=1** del
proceso (coste de arranque del cliente HTTP de .NET). Desde dentro de la red de
Docker, 3.000 llamadas con **peor caso de 10 ms**. Si el evento ocurriera una vez
cada ~250 llamadas, en 3.000 habrían salido ~12. Salieron cero.

Eso deja el otro modelo: **el evento es periódico en el tiempo**. El gate mide
durante ~10 minutos y ve ~1,7 eventos; es decir, aproximadamente **uno cada 5-6
minutos**, con independencia de cuántas llamadas quepan en ese rato.

---

## 4. Dónde nace el parón

Prueba de 15 minutos golpeando `/salud` una vez por segundo **por los dos
caminos a la vez**, con marca de tiempo en las dos series
(`e2e-diagnostico-transporte-largo.ps1`, ventana 08:23:33 → 08:38:47 UTC):

| Camino | N | p50 | p99 | Peor | ≥ 500 ms |
|---|---|---|---|---|---|
| **W** — Windows → puerto publicado | 900 | 23 ms | 82 ms | **2.074 ms** | **4** |
| **D** — dentro de la red de Docker | 900 | 0 ms | 0 ms | **10 ms** | **0** |

Los cuatro parones de W:

| Llamada | Hora UTC | ms |
|---|---|---|
| **200** | 08:26:54 | 2.074 |
| **400** | 08:30:17 | 2.042 |
| **600** | 08:33:40 | 2.074 |
| **800** | 08:37:04 | 2.065 |

**Cada 200 llamadas exactas, con ~2,05 s clavados, y ninguna coincidencia en el
camino de dentro de Docker.** Pausas de JVM en esa ventana: 55, la mayor 59,5 ms.

### El mecanismo

- El API corre sobre **Tomcat embebido** (`spring-boot-starter-web`) sin
  sobrescribir `server.tomcat.max-keep-alive-requests`, cuyo valor por defecto
  es **100**: pasadas 100 peticiones, Tomcat cierra la conexión.
- `Invoke-WebRequest` de PowerShell 5.1 mantiene **2** conexiones agrupadas
  (`ServicePointManager.DefaultConnectionLimit`).
- 2 conexiones × 100 peticiones = **una renovación de conexiones cada 200
  peticiones del cliente**, que es exactamente el período medido.
- Rehacer esa conexión TCP **a través del proxy de puertos de Docker Desktop en
  Windows** cuesta ~2 segundos. Desde dentro de la red de Docker no hay proxy y
  el mismo ciclo no cuesta nada medible.

### Por qué golpeó dos veces al mismo escenario

La secuencia de peticiones del gate es **determinista**. Si el parón cae en la
petición nº 200, 400 y 600 de la corrida, cae **siempre en el mismo punto de la
secuencia de escenarios** — y por eso las corridas 1 y 2 fallaron las dos en
`VIS casa TODO - profunda`. No era una propiedad de esa consulta: era su
posición en la lista. En la corrida 3 la secuencia se desplazó ligeramente y el
parón cayó sobre `INT casa TODO - profunda`, que al tener línea base más barata
se quedó en 2.442 ms y **dejó pasar el gate**.

### Conclusión

Los ~2 segundos **no los produce el producto**. Se producen en el camino entre
el cliente de PowerShell en Windows y el contenedor, en un tramo —el proxy de
puertos de Docker Desktop— que **no existe en ningún despliegue real**. El gate,
en ese control, está midiendo su propio instrumento.

### Confirmación por predicción falsable

Si el disparador es la renovación de la conexión, quitarla debe hacer
desaparecer las pausas. Misma prueba, misma ventana de 15 minutos, mismos dos
caminos, cambiando **sólo** `SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS` a `-1`:

| | Con el valor por defecto (100) | Con keep-alive ilimitado |
|---|---|---|
| W — p50 | 23 ms | 28 ms |
| W — p99 | 82 ms | 83 ms |
| W — **peor** | **2.074 ms** | **201 ms** |
| W — pausas ≥ 500 ms | **4** (llamadas 200, 400, 600, 800) | **0** |
| D — peor | 10 ms | 0 ms |
| Pausa de JVM más larga | 59,5 ms | 47,4 ms |

Predicción confirmada. El mecanismo queda probado de extremo a extremo.

---

## 5. El defecto del criterio 3

Con independencia de dónde nazcan esos 2 segundos, el control que falla tiene un
problema propio de diseño estadístico:

- **El criterio 3 juzga por el PEOR de 120 observaciones** (6 escenarios × 20
  llamadas) y exige que **todas** estén bajo 3.000 ms.
- **Los criterios 1 y 2 juzgan por p95**, que es robusto a un valor suelto.
- El escenario `VIS casa TODO - profunda` está **en los dos conjuntos**: el
  criterio 2 lo evalúa contra RC-003 leyendo su **p95** (1.977 ms → verde) y el
  criterio 3 lo evalúa contra **el mismo RC-003** leyendo su **peor** (3.309 ms →
  rojo). **El gate se contradice sobre la misma medición.**

Con un evento de ~2 s cada 5-6 minutos y una fase de medición de ~10 minutos, la
probabilidad de que alguna de las 120 observaciones del criterio 3 lo reciba
ronda el **35-40 % por corrida**. Es exactamente lo observado: falla, falla,
pasa.

El criterio 1 arrastra la misma fragilidad en su control de `peor < 2.000 ms`
sobre 9 escenarios discriminantes de mediana ~100 ms: un solo evento lo tumbaría
igual. Todavía no ha ocurrido.

---

## 6. Lo que NO se ha hecho

- **No se ha tocado ningún umbral.** `RC003` sigue en 3.000 ms, el objetivo
  discriminante en 1.000 y el peor en régimen en 2.000.
- **No se ha tocado ninguna consulta del producto** ni ningún índice.
- **No se ha borrado ni reescrito la evidencia** de la primera corrida.
- **No se ha firmado F3.**

Los únicos cambios en el árbol son de instrumentación de pruebas: cuatro guiones
de diagnóstico nuevos en `verificacion/`, sus entradas en el `ValidateSet` de
`Invoke-E2E.ps1`, y un `JAVA_TOOL_OPTIONS` **vacío por defecto** en
`docker-compose.e2e.yml` que permite encender el log de pausas de la JVM sin
tocar el artefacto. El gate `e2e-demanda-busqueda.ps1` está **intacto**.

---

## 7. Opciones de resolución

Ninguna de las cuatro baja el umbral: RC-003 sigue siendo 3.000 ms en todas.

### A. Medir el tiempo desde dentro de la red de Docker

El bucle de medición corre en un contenedor de la misma red; las comprobaciones
semánticas siguen en Windows. Elimina el artefacto **de raíz** y sin tocar la
configuración del sistema medido: es lo que mide un cliente real.

- *A favor:* es la medición fiel. Cero parones en 900 llamadas por ese camino.
- *En contra:* hay que rehacer `Medir` en los **tres** gates de búsqueda
  (locales, demanda, solicitudes) y los números históricos de los ya firmados
  dejan de ser comparables con los nuevos.

### B. Confirmar antes de fallar

Se conserva `Peor` y el límite de 3.000 ms. Cuando un escenario lo supera, el
gate **vuelve a medir ese escenario** y sólo falla si el exceso se reproduce.

- *A favor:* es exactamente la regla humana que ya se aplica —«córrelo otra vez;
  si se reproduce es real, si no es un atípico»— pero automatizada. No pierde
  sensibilidad ante una regresión de verdad, que sí se reproduce. Barato.
- *En contra:* alarga la corrida cuando salta, y no elimina el artefacto: lo
  tolera.

### C. Neutralizar el artefacto sólo en el entorno E2E

`SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS: -1` en `docker-compose.e2e.yml`
únicamente. Sin renovación de conexiones no hay reconexión que pagar.

- *A favor:* una línea, no toca ningún criterio y beneficia a todas las suites.
- *En contra:* el entorno de prueba deja de ser idéntico a producción en ese
  ajuste, y el gate dejaría de ver un hipotético problema real de reconexión.

### D. Criterio 3 por p95

Alinearlo con los criterios 1 y 2 y resolver la contradicción de la §5.

- *A favor:* coherencia interna; un solo estadístico para el mismo límite.
- *En contra:* es la opción más cercana a «cambiar el control para que pase», y
  pierde la garantía de que **ninguna** llamada suelta supere RC-003.

## 8. Decisión tomada: opción C

Se elimina el artefacto en su origen, **sólo en el entorno E2E**:

```yaml
# backend-spring/docker-compose.e2e.yml, servicio api-e2e
SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS: ${CONTROLLOCAL_E2E_KEEPALIVE:--1}
```

Cuatro cosas que este cambio **no** es, y que conviene tener por escrito porque
se parece a hacer trampa y no lo es:

1. **No optimiza ninguna consulta.** Los planes, los índices y el SQL del
   producto quedan exactamente como estaban.
2. **No cambia código funcional** ni el contrato congelado. Es una variable de
   entorno del contenedor de pruebas.
3. **Elimina una renovación de conexión artificial**, propia del puerto
   publicado de Docker Desktop en Windows. Desde dentro de la red de Docker esa
   renovación nunca costó nada medible (900 llamadas, peor caso 10 ms).
4. **El despliegue real deberá fijar el keep-alive de forma explícita**, en
   Tomcat **y** en su proxy o balanceador. No puede heredar esta decisión ni dar
   por bueno el valor por defecto: aquí se ha tomado para un entorno de prueba
   sobre Docker Desktop, y el criterio en producción depende del balanceador que
   se ponga delante.

**El override vive únicamente en `docker-compose.e2e.yml`.** No está en
`docker-compose.yml` ni en ningún `application*.yml`, y no debe llevarse ahí
automáticamente.

### Sonda de transporte permanente

Queda `verificacion/e2e-sonda-transporte.ps1`, **separada del gate funcional**:
golpea `/salud` una vez por segundo por los dos caminos a la vez durante 5
minutos y **termina en error** si encuentra cualquier llamada por encima de 500
ms sobre un trabajo de milisegundos. Informa además de la separación entre
pausas, porque un período regular es la firma de un artefacto del entorno y no
de la carga.

Sirve para lo que hoy no teníamos: **detectar que el entorno volvió a meter
pausas periódicas antes de creerse los percentiles de un gate de rendimiento**.

```powershell
powershell -File backend-spring/verificacion/Invoke-E2E.ps1 -Suite sonda-transporte
```

### Lo que se descartó, y por qué

- **A (medir desde dentro de la red de Docker).** Es la medición más fiel, pero
  obliga a rehacer `Medir` en los tres gates de búsqueda y rompe la
  comparabilidad con los números ya firmados de locales y solicitudes. Queda
  como deuda razonable si algún día se tocan los tres a la vez.
- **B (confirmar antes de fallar).** No hacía falta una vez identificado el
  origen: tolerar el artefacto es peor que quitarlo.
- **D (criterio 3 por p95).** Habría hecho pasar el gate sin entender por qué.

### Lo que sigue abierto

- La **contradicción de la §5** sigue ahí: el criterio 2 juzga RC-003 por p95 y
  el criterio 3 por el peor de 120 observaciones. Con el artefacto eliminado ya
  no la dispara nada conocido, pero el criterio 1 mantiene la misma fragilidad
  latente en su control de `peor < 2.000 ms`. Decisión consciente de dejarlo.
- ~~La **frase sobre el coste del `OFFSET`**~~ ✅ corregida el 2026-08-03 en la §5
  de `docs/ai/contrato-listados-paginados.md` y en `backend-spring/README.md`:
  ahora está separada por camino.

---

## 9. La corrida de firma (2026-08-03)

Ejecutada en el Docker local de la máquina (engine 29.6.2, contexto
`desktop-linux`), con las imágenes del entorno E2E refrescadas, el **stack de
desarrollo parado** —Docker Desktop lo había vuelto a levantar solo al arrancar
el engine, y es justo lo que contaminó una de las medidas del 2026-08-03— y el
gate **sin un solo cambio** respecto de las corridas 1 y 2.

**Sonda de transporte previa**, que es ahora la regla antes de creerse cualquier
percentil en esta máquina:

| Camino | N | p50 | p99 | Peor | Pausas ≥ 500 ms |
|---|---|---|---|---|---|
| W — Windows → puerto publicado | 300 | 34 ms | 108 ms | **255 ms** | **0** |

Es el perfil sano que predecía la §4 (28 ms p50, 201 ms peor, cero pausas), no
el enfermo (cuatro pausas de ~2.070 ms). El arreglo del keep-alive aguanta
también en este Docker.

**Gate `e2e-demanda-busqueda.ps1`, corrida `20260803093503-7523`:**

| | Corrida 1 | Corrida 2 | **Corrida 4 (firma)** |
|---|---|---|---|
| Resultado | 68 OK / 1 FALLA | 68 OK / 1 FALLA | **69 OK / 0 FALLAS** |
| §10 criterio 3 | **rojo** | **rojo** | **verde** |
| `VIS casa TODO - profunda` p50/p95/**peor** | 1.322/1.726/**3.357** | 1.435/1.977/**3.309** | 1.383/1.521/**1.577** |
| Clasificación | 9 / 9 | 9 / 9 | 9 / 9 |

El peor caso del escenario que tumbó las dos corridas cae de **3.357 a 1.577
ms** sin que ninguna consulta, índice ni umbral haya cambiado: lo único que se
retiró fue la renovación de conexión del proxy de puertos. La predicción
falsable de la §4 queda confirmada por segunda vez, ahora sobre el gate
completo y no sobre `/salud`.

Evidencia cruda en `backend-spring/verificacion/evidencia/`:
`2026-08-03-f3-sonda-previa-firma.log` y `2026-08-03-f3-corrida-4-firma.log`.
