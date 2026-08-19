# Diagnóstico E2.6 — contraste, medias propias, metas, cierre del mes y pie

**Qué responde:** qué dato existe de verdad detrás de cada cosa que E2.6 promete,
con cuánta muestra, quién es hoy su autoridad y qué hay que decidir antes de
escribir la primera consulta.

**Método:** PostgreSQL real (`controllocal-postgres-v2`, esquema en v62), API viva
en `localhost:8090`, más el código de `backend-spring`, `frontend-angular` y la
maqueta de `docs/ai/prototipos/`. Ninguna cifra de este documento está estimada.

**Fecha de la medición:** 2026-08-19.
**Estado:** medición cerrada. **No se ha escrito ni una línea de E2.6.**

---

## 0. El resumen en cinco frases

1. **El contraste por rango no tiene muestra en ninguna celda.** La mejor
   combinación zona × metraje del corpus limpio tiene **4 observaciones**;
   catorce de las diecisiete celdas tienen **una**.
2. **Las tres medias propias no son calculables hoy**, y no por poca muestra:
   por falta del hecho. Cero visitas realizadas, cero interacciones colgadas de
   una prospección, y cuatro contratos con fechas incoherentes.
3. **Las metas no existen en ningún sitio.** Ni columna, ni tabla, ni constante,
   ni fixture del SPA. Cero productores.
4. **«Puede cerrarse este mes» sigue sin definición**, y el dato que la
   sostendría existe y está poblado: `solicitud_alquiler.monto_propuesto`.
5. **Hay dos huecos estructurales que ningún documento menciona**: el periodo del
   API es una ventana móvil —no un mes con días transcurridos—, así que
   `metaEsperadaAHoy` no se puede derivar; y los cuatro KPI canónicos **tienen
   dos juegos de nombres distintos** en los dos documentos que gobiernan.

---

## 1. La matriz

| # | Dato requerido | Fuente real | Cardinalidad / muestra | Autoridad actual | Hueco | Decisión necesaria |
|---|---|---|---|---|---|---|
| 1 | Rango de renta por zona | `precio_propiedad` (hitos `U`/`P`/`C`) × `propiedad.distrito`, `.metraje` | 22 hitos limpios · mejor celda **4** obs. | `PrecioPropiedad` (dominio) | muestra insuficiente en **todas** las celdas | qué hitos forman el rango, y **qué se dice cuando N es bajo** |
| 2 | Renta publicada (pedida) | `precio_propiedad.hito='P'` | **0 filas** (con 5 publicaciones vivas) | `PublicacionServiceImpl:238` | el productor existe, no ha corrido nunca | si el rango puede apoyarse en `U` mientras `P` esté vacío |
| 3 | Media de propuestas por visita | `visita` × `solicitud_alquiler` | 8 visitas, **todas `estado='P'`, `resultado` NULL** → **0 visitas realizadas** | `Visita` (dominio) | no hay visita con desenlace; «propuesta» del embudo de demanda **no tiene productor** | qué es una «propuesta»: ¿`solicitud`, o el hito `O` que E3 aún no escribe? |
| 4 | Media de días hasta contrato | `contrato_alquiler.fecha_cierre` − origen | 4 contratos · **`fecha_cierre` es 1 día ANTERIOR a la oportunidad** · las 4 filas con fechas idénticas | `ContratoAlquiler` | el origen del cómputo no está decidido y el dato daría **−1** | desde qué hito se cuenta: oportunidad, solicitud o visita |
| 5 | Plazo real de recontacto | `interaccion_comercial` consecutivas por objeto | **0** interacciones con `id_prospeccion` · los 23 pares de `CLIENTE` están a **0 días** | `PoliticaComercial.RECONTACTO` (=7, es el *umbral*, no la media) | el hecho «volví a contactar» no se registra contra la prospección | si la serie sale de `interaccion_comercial` o de `prospeccion.fecha_recontacto` |
| 6 | `metaPeriodo` por KPI | — | **0 productores** en BD, backend, SPA y seed | **ninguna** | hueco estructural completo | dónde vive la meta, quién la fija, y su prorrateo |
| 7 | `metaEsperadaAHoy` | requiere periodo con inicio/fin/transcurridos | `Periodo` del API es **ventana móvil** (`hoy − dias + 1`) | `IndicadorServiceImpl:865` | con ventana móvil, `transcurridos == dias` siempre → el semáforo no significa nada | si `/indicadores/resumen` gana un periodo de calendario |
| 8 | `estadoRitmo` (🟢🟠🔴⚪) | algoritmo completo ya escrito **en la maqueta** | `ritmoDe()`, duplicado en `inicio.html:1678` y `indicadores.html:1253` | maqueta (JS), **no** el dominio | 5 constantes de ritmo fuera de `PoliticaComercial` | subir el algoritmo y sus 5 umbrales al dominio |
| 9 | Los 4 KPI: numeradores | `prospeccion.fecha_contacto`, `captacion.estado='A'`, `solicitud_alquiler`, `contrato_alquiler` | mes en curso: **19 · 5 · 5 · 4** | `IndicadorServiceImpl` | **ninguno de los cuatro viaja hoy en el cable** con ese nombre | qué estado exacto cuenta cada uno |
| 10 | «Puede cerrarse este mes» | `solicitud_alquiler.monto_propuesto` + `moneda` + `estado` | **1 solicitud viva** (`E`, S/ 9 000) | ninguna: la maqueta lo tiene **hardcodeado** (9 300 / 41 200) | sin definición escrita | qué estados suman, y contra qué fecha |
| 11 | `generadoEn` | — | **0 productores** | **ninguna** | el «hace 2 min» de la maqueta se calcula contra un fixture | dónde nace: ¿`IndicadoresResponse`, `DashboardResponse`, o los dos? |
| 12 | Nombres canónicos del pie | dos juegos distintos, ver §6 | 4 nombres × 2 documentos | **empatada**: D-E2-1 vs D-E2-2 | contradicción viva entre los dos documentos que gobiernan | fijar los cuatro nombres, letra por letra, en un solo sitio |

---

## 2. Contraste de renta — la medición que manda

### 2.1 Qué hitos hay de verdad

`precio_propiedad` admite siete códigos (`E R U P O A C`). Solo **tres tienen
nombre en el dominio** (`HITO_ESPERADO`, `HITO_AUTORIZADO`, `HITO_PUBLICADO`) y
solo **dos tienen productor vivo**:

| Hito | Significado | Productor | Filas |
|---|---|---|---|
| `U` autorizado | lo que el propietario autoriza pedir | `LocalComercialServiceImpl`, `PropiedadUniversalServiceImpl` | **200** |
| `P` publicado | la renta que sale al mercado | `PublicacionServiceImpl:238` | **0** |
| `E` esperado | expectativa previa | ninguno — seed del 2026-08-06 | 2 |
| `C` contrato | renta firmada | ninguno — seed del 2026-08-09 | 4 |
| `R`, `A`, `O` | sin nombre en el dominio | ninguno | 0 |

**Hay 5 publicaciones en estado publicado y cero hitos `P`.** El productor existe
desde E0.2 pero nunca ha corrido: las publicaciones son anteriores. Es decir, el
dato que el contraste querría —lo que realmente se está pidiendo— **no está en
la serie**.

### 2.2 El corpus está contaminado por las pruebas

De las 183 propiedades, **162 se crearon el 2026-08-18 y el 2026-08-19**, junto
con 120 captaciones `P` y 184 hitos de precio. Son residuo de las corridas de
`PropiedadUniversalIntegrationTest` contra la base de desarrollo.

Lo que hace visible el residuo, y por qué no se puede ignorar:

```
Miraflores, 100–199 m², operación alquiler, PEN
  monto  filas
   4500      1
   5480      1
   7000     42   <- creadas el 18 y 19 de agosto
   7500     21   <- creadas el 18 y 19 de agosto
   8500      1
```

Con el residuo, esa celda parece tener **66 observaciones**. Son **cinco montos
distintos**, y 63 de las 66 son dos valores repetidos por un caso de prueba. Un
percentil sobre eso no describe nada.

### 2.3 El corpus limpio, que es el que cuenta

Excluyendo lo creado a partir del 2026-08-18 quedan **22 hitos de alquiler**
sobre 22 propiedades. Repartidos por zona × banda de metraje:

| Celda | Observaciones |
|---|---|
| Miraflores · 100–199 m² | **4** |
| Miraflores · 50–99 m² | 2 |
| Surquillo · 50–99 m² | 2 |
| Otras **catorce** celdas | **1 cada una** |

> **No hay una sola celda que sostenga un rango.** «en el 68 % del rango de
> Miraflores · 3 200–5 100» no es reproducible con estos datos: con cuatro
> observaciones el mínimo y el máximo son dos puntos sueltos, no un rango.

Esto es exactamente el salto que el producto prohíbe —objeto, portafolio y
mercado son escalas distintas—, y aquí ni siquiera hay portafolio: hay objetos.

### 2.4 Dos defectos menores que estorban al agrupar por zona

- `propiedad.distrito` es texto libre **además** de `id_distrito`. Conviven
  `Lima` y `Lima Cercado` como distritos distintos.
- **3 propiedades no tienen `id_distrito`** (`Lima Cercado` ×2, `Callao` ×1). Si
  la zona se agrupa por el texto, esas tres se agrupan mal; si se agrupa por el
  id, desaparecen del rango sin avisar.

---

## 3. Las tres medias propias

### 3.1 Propuestas por visita — falta el hecho, no la muestra

Las 8 visitas de la organización están **todas** en `estado='P'` con `resultado`
NULL. **Cero visitas realizadas.** El denominador de «1 propuesta cada 3 visitas»
es cero.

Y el numerador es peor: **«propuesta» no existe como hecho del embudo de
demanda.** Lo único que se llama así en el dominio es
`Prospeccion.PROPUESTA_ENTREGADA = "E"`, que es una propuesta **al propietario**
en el embudo de oferta — otro embudo, otra persona. Después de una visita, lo
que el modelo tiene es `solicitud_alquiler`; la oferta del interesado es el hito
`O`, **que E3 todavía no escribe y que E0 dejó bloqueado con tres cuestiones
abiertas**.

### 3.2 Días hasta contrato — el dato daría un número negativo

```
contrato  oportunidad  solicitud  fecha_cierre  días desde oportunidad
   1      2026-08-09   2026-08-05   2026-08-08            -1
   2      2026-08-09   2026-08-05   2026-08-08            -1
   3      2026-08-09   2026-08-05   2026-08-08            -1
   4      2026-08-09   2026-08-05   2026-08-08            -1
```

Los cuatro contratos son clones con las mismas fechas, y la oportunidad se
registra **después** del cierre. Medido desde la solicitud da 3 días, N=4, las
cuatro idénticas.

### 3.3 Plazo real de recontacto — el hecho no se registra contra el objeto

- **0** interacciones tienen `id_prospeccion`, con 63 prospecciones en la base.
- Las 24 de contexto `CLIENTE` no tienen ninguno de los tres FK de objeto: solo
  `id_rol_cliente`. Sus 23 pares consecutivos están a **0 días**.
- Las 21 prospecciones con `fecha_contacto` dan 0,2 días de registro a contacto y
  0,7 de contacto a propuesta: todas nacieron en el mismo instante del seed.

Medido tal cual, «el plazo real de tu casa» sería **0 días** contra una política
de **7**. Y hay que separar dos cosas que hoy se confunden:
`prospeccion.fecha_recontacto` es el recontacto **previsto** (lo que consume
`PoliticaComercial.limiteDeRecontacto`), no el realizado.

### 3.4 Y las medias no son por agente

De los **15 agentes** de la organización, **uno solo tiene producción**:

```
rol   contactados  captados  solicitudes  contratos
 28        21          5          6           4
 29..42     0          0          0           0     (catorce agentes)
```

Cualquier «tu media» de los catorce restantes es una división por cero. Y el
pulso del equipo del broker leería 14 agentes fuera de ritmo, que es un artefacto
del seed, no un hecho del negocio.

---

## 4. Metas — el hueco es completo

Se buscó en los cuatro sitios donde podría esconderse:

| Dónde | Qué se buscó | Resultado |
|---|---|---|
| PostgreSQL | columnas `%meta%`, `%objetivo%`, `%cuota%` | **0**. Los tres aciertos son de otro dominio: `evento_seguridad.id_objetivo`, `titularidad_propiedad.cuota`, `borrador_captura.entidad_objetivo_id` |
| `backend-spring` | `meta` en Java | **0**. Los aciertos son `metadata` y `objetivo` en otro sentido |
| `frontend-angular` | `meta`/`objetivo` en Indicadores y core | **0** |
| seed / migraciones | — | **0** |

El único sitio del repositorio donde hay metas es la **maqueta**, como fixture
literal (`indicadores.html:1093`): ocho agentes con cuatro metas cada uno.

**Consecuencia:** hoy `metaPeriodo`, `metaEsperadaAHoy`, `porcentajeMeta`,
`faltante` y `estadoRitmo` no tienen ni un productor. Los cinco nacen en E2.6 o
no nacen.

### 4.1 Los numeradores sí existen

Es la buena noticia de este bloque. Los cuatro KPI son calculables hoy contra el
mes en curso:

```
prospecciones contactadas   19
captaciones activadas        5
solicitudes ingresadas       5
contratos firmados           4
```

Lo que falta es el denominador y el periodo, no el hecho.

### 4.2 Lo que el cable emite hoy, verificado en vivo

`GET /indicadores/resumen?periodo=1m` como agente devuelve 26 campos. **Ninguno
de los cuatro KPI canónicos viaja con ese nombre**, y no hay meta ni ritmo:

```
ambito "Mi actividad" · captacionesPorRevisar 120 · captacionesTotales 133
captacionesActivas 5 · oportunidadesActivas 4 · interacciones 31 · visitas 8
cierres 4 · cierresCohorte 4 · conversionPropia 3 · pendientesDeAtencion 125
```

Dos observaciones sobre esa salida:

- `pendientesDeAtencion: 125` y `captacionesPorRevisar: 120` son el residuo de
  pruebas del §2.2. La cabecera de decisión de E2.1 está diciendo hoy «125 cosas
  necesitan tu atención».
- Como broker, `desempeno` atribuye a Valentina Mora **133 captaciones** — la
  organización entera, residuo incluido.

---

## 5. Los dos huecos estructurales que ningún documento menciona

### 5.1 El periodo del API es una ventana móvil, y el ritmo necesita un calendario

`IndicadorServiceImpl:865` define el periodo como número de días, y el rango como
`inicio = hoy − dias + 1`. No hay inicio de mes, ni fin, ni días transcurridos.

La fórmula que D-E2-2 §4 exige es `metaEsperadaAHoy = metaPeriodo × transcurridos
÷ dias`. En una ventana móvil **`transcurridos` es siempre igual a `dias`**, así
que `metaEsperadaAHoy` sería siempre igual a `metaPeriodo` y el semáforo diría
🔴 todos los días hasta el último. **El ritmo es incompatible con el periodo
actual del cable.**

La maqueta ya trabaja con lo correcto —«mes en curso, día 26 de 30»—, pero el
backend no tiene ese concepto.

### 5.2 La maqueta tiene una segunda copia de la política, y ya divergió

`docs/ai/prototipos/*.html` lleva su propio bloque `POLITICA` con diez umbrales.
Comparado con `PoliticaComercial.java`:

| Umbral | Maqueta | Backend | |
|---|---|---|---|
| recontacto | 7 días | 7 días | coincide |
| reporte al propietario | **10 días** | **15 días** | **divergen** |
| plazo cobro comisión | 15 días | no existe | solo maqueta |
| tiempo publicado | 90 días | no existe | solo maqueta |
| renta sin ajustar | 60 días | no existe | solo maqueta |
| `llega` 1,0 · `cerca` 0,85 · `arranquePc` 0,15 · `volumenMinimo` 3 · `muestraMinima` 5 | sí | no existen | **los cinco umbrales del ritmo** |

Es la misma forma exacta del incidente que E1 vino a cerrar: la regla escrita en
dos sitios, coordinada por nada, y una ya no cuadra. Además `ritmoDe()` está
duplicada **entre los dos archivos de la maqueta**.

---

## 6. La contradicción entre los dos documentos que gobiernan

D-E2-1 §6.2 obliga a que el pie use los cuatro nombres «letra por letra, los
mismos que use la pantalla de Indicadores», y dice que hay una comprobación que
lo exige. Pero los dos documentos escriben nombres distintos:

| | D-E2-2 §1 | D-E2-1 §6.2 y la maqueta |
|---|---|---|
| 1 | Prospección efectiva | **Propietarios contactados** |
| 2 | Captaciones activadas | **Locales captados** |
| 3 | Solicitudes generadas | **Solicitudes ingresadas** |
| 4 | Contratos firmados | Contratos firmados |

`indicadores.html` usa **los dos juegos**: el de D-E2-2 una vez cada uno, y el de
D-E2-1 en los KPI reales. La única comprobación que existe
(`pruebas-nucleo.js:90`) fija el juego de D-E2-1.

**Hay que elegir uno y corregir el otro documento.** Si no, el gate de E2.6 no
puede escribirse: exigiría dos verdades.

### 6.1 Y tres comprobaciones que los documentos dan por hechas no existen

Se buscaron en el repositorio y no están:

- la que rechaza las palabras «sector», «mercado nacional», «industria» y
  «benchmark» (D-E2-1 §10.3.2);
- la que exige que la lectura de BROX **no repita literalmente** ninguno de los
  cuatro renglones del expediente;
- la que verifica que el pie **no repite ninguna cifra del foco**.

Las 75 comprobaciones de `pruebas-nucleo.js` cubren nombres, ritmo, embudo y
foco, pero corren contra fixtures, no contra el backend.

### 6.2 El contrato del KPI tampoco cuadra

D-E2-2 §2 lista siete campos. `ritmoDe()` emite diez y **omite
`variacionComparable`**, que es uno de los siete:

| | D-E2-2 | maqueta |
|---|---|---|
| `actual`, `metaPeriodo`, `metaEsperadaAHoy`, `porcentajeMeta`, `faltante`, `estadoRitmo` | sí | sí |
| `variacionComparable` | **sí** | **no** |
| `proyeccionCierre`, `porcentajeProyectado`, `sinCadencia`, `arranque`, `voz` | **no** | **sí** |

---

## 7. «Puede cerrarse este mes» — lo que hay para sostenerla

Sigue sin definición: D-E2-2 §13 la deja abierta y D-E2-1 §6.2.1 la marca como
pendiente. En la maqueta es una constante (`CIERRE_POSIBLE`: 9 300 para el
agente, 41 200 para el broker, con la serie de seis meses inventada).

Los hechos que existen hoy y que podrían sostenerla, todos poblados:

| Hecho | Columna | Estado |
|---|---|---|
| importe de la operación | `solicitud_alquiler.monto_propuesto` + `moneda` | 6/6 pobladas, PEN |
| fecha prevista de inicio | `solicitud_alquiler.fecha_inicio_contrato` | 6/6 pobladas |
| etapa | `solicitud_alquiler.estado` | `C` 4 · `E` 1 · `R` 1 |
| vigencia de la oferta | `solicitud_alquiler.fecha_vigencia_oferta` | existe |
| decisión del broker | `evaluacion_solicitud.resultado` | 6 filas |
| etapa de la oportunidad | `oportunidad_comercial.estado` | `A` 3 · `F` 4 · `S` 1 |

Con el corte más natural —solicitudes vivas, ni contratadas ni rechazadas— la
cifra real de la organización hoy es **1 operación · S/ 9 000**, no «3
operaciones · US$ 9 300».

Y hay un detalle de unidad: la maqueta rotula en **US$** y el dato está en
**PEN**. La cifra necesita moneda declarada, no un símbolo fijo en la plantilla.

---

## 8. Lo que hay que decidir antes de escribir la primera consulta

Ordenado por lo que bloquea a lo demás.

1. **Los cuatro nombres canónicos.** Elegir un juego y corregir el documento que
   pierda. Bloquea el gate entero.
2. **El periodo de calendario.** Si `/indicadores/resumen` gana inicio, fin y
   transcurridos, o si el ritmo vive en otro recurso. Sin esto no hay
   `metaEsperadaAHoy` ni semáforo.
3. **Dónde viven las metas**, quién las fija y cómo se prorratean. Es tabla
   nueva; conviene que nazca con `organizacion_id` y vigencia, porque la meta del
   equipo es la suma de las vigentes.
4. **Qué estado exacto cuenta cada KPI.** En particular: `prospeccion.estado='D'`
   (descartado) sale de la escalera ordinal pero **sí tuvo contacto** —los 3
   descartados tienen `fecha_contacto`—, así que la autoridad del numerador es la
   fecha, no el estado.
5. **La definición escrita de «puede cerrarse este mes»**: qué estados suman,
   contra qué fecha, y en qué moneda.
6. **Qué se dice cuando no hay muestra.** Esta es la decisión de producto de la
   tanda, y con los datos del §2.3 es la que más se va a ejecutar. Propuesta:
   por debajo de un N mínimo el contraste **no se dibuja** y se dice «sin
   referencia interna suficiente», con el mismo criterio con el que E2.0 decidió
   que sin muestra la conversión es `null` y no 0.
7. **De dónde sale la media propia**, sabiendo que el hecho no está: si se
   registra el desenlace de la visita, si «propuesta» pasa a ser `solicitud`
   mientras `O` no exista, y si el recontacto realizado se ata a la prospección.
8. **Dónde nace `generadoEn`** y quién lo emite, para no repetir el caso de
   `ambito`: un solo dueño, y el otro recurso lo lee.

---

## 9. Recomendación de orden

**El contraste por rango no se implementa en esta tanda tal como está escrito.**
No por falta de tiempo: porque con 4 observaciones en la mejor celda, cualquier
rango que se pinte es una afirmación que el dato no sostiene, y el producto
existe precisamente para no dar ese salto. Se implementa **el camino de la
degradación** —el que dice que no hay referencia suficiente— y se deja el rango
detrás de un N mínimo, listo para encenderse cuando la cartera lo tenga.

Lo que sí tiene dato y se puede cerrar entero en E2.6: los cuatro KPI con su
meta, el periodo de calendario, el ritmo subido al dominio, `generadoEn`, el pie
canónico y la definición de «puede cerrarse este mes».

**Y una limpieza previa que no es opcional:** los 162 registros de prueba del
2026-08-18/19 tienen que salir de la base de desarrollo. Mientras estén, la
pantalla dice «125 cosas necesitan tu atención» y ninguna subtanda de E2 se puede
evaluar a ojo en `localhost:4200`, que es el requisito propio de la etapa.

---

## 10. Cómo reproducir esta medición

```bash
# Corpus de precios: hito x operacion x moneda, y el residuo de pruebas
docker exec -i controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -c \
  "SELECT hito, operacion, moneda, count(*), min(fecha_creacion)::date, max(fecha_creacion)::date
   FROM precio_propiedad GROUP BY 1,2,3 ORDER BY 1,2;"

# La celda zona x metraje con mas muestra, sin residuo
docker exec -i controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -c \
  "SELECT pr.distrito, count(*), count(distinct pp.monto) FROM precio_propiedad pp
   JOIN propiedad pr ON pr.id_propiedad=pp.id_propiedad
   WHERE pp.operacion='A' AND pp.fecha_creacion < '2026-08-18'
   GROUP BY 1 ORDER BY 2 DESC;"

# Metas: cero productores en la base
docker exec -i controllocal-postgres-v2 psql -U controllocal -d controllocal_dev -c \
  "SELECT table_name||'.'||column_name FROM information_schema.columns
   WHERE table_schema='public' AND (column_name ILIKE '%meta%' OR column_name ILIKE '%objetivo%');"
```
