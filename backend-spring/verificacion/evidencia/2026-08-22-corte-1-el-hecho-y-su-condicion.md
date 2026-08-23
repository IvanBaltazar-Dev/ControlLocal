# El hecho llega donde llega su condición · Corte 1, mitad de sujeto · V78

**Cerrado el 2026-08-22.** Migración `V78__el_hecho_llega_donde_llega_su_condicion.sql`.

---

## La pregunta

El Corte 1 se abrió con una pregunta distinta a la que el plan de catálogo traía.
No *«¿qué le falta a cada clave para describir bien el inmueble?»*, sino:

> ¿Esta clave describe **un hecho del inmueble**, o estaba bloqueada porque
> confundíamos una característica del inmueble con una condición del encargo?

Tiene sentido preguntarla ahora y no antes: hasta `V73` el catálogo tenía un solo
sujeto y la pregunta no era contestable —todo era de la propiedad por
construcción—, y hasta `V77` el ENCARGO estaba medio mudo, así que una condición
sin sitio habría parecido un hecho mal colocado.

---

## Preflight

Medido contra PostgreSQL real, las dos bases en `V77`: `controllocal_dev`
(26 propiedades, cartera de uso) y `controllocal_repositorios` (1 915, la de
integración). **19 claves PROPIEDAD · 26 ENCARGO · 66 filas de aplicabilidad por
tipo · 112 por tipo × operación.**

Tres hechos del preflight mandaron sobre todo lo demás:

| Hallazgo | Consecuencia |
|---|---|
| `tg_catalogo_sistema_inmutable` **prohíbe cambiar el `tipo_dato`** de una clave del sistema | Las cuatro conversiones que el plan pedía están bloqueadas por una invariante deliberada, no por falta de trabajo |
| `requerido` es **espejo** de `exigencia` y hoy son coherentes al 100 % (0 de 66) | Cualquier fila nueva escribe las dos, o rompe en silencio una coherencia que nada en el esquema ata |
| El SPA **no nombra ninguna** de las 19 claves | Ampliar aplicabilidad no toca Angular: la definición viaja del Core |

---

## La clasificación de las 19

| clave | cat. | veredicto |
|---|---|---|
| `metraje_total` · `antiguedad_anios` · `metraje_construido` · `ambientes` · `piso` · `dormitorios` · `banos` · `pisos_edificacion` · `frente` · `carga_electrica_kw` · `altura_libre` · `apto_licencia_funcionamiento` · `zonificacion` · `area_terreno` | **A** (14) | Hecho puro del inmueble, bien colocado |
| `estacionamientos` · `rubro_permitido` | **C** (2) | Mitad de un par cuyo gemelo comercial ya existe, en el otro sujeto y con el mismo alcance |
| `amoblado` · `cuota_mantenimiento` · `servicios_disponibles` | **D** (3) | Es hecho de la PROPIEDAD, pero el esquema no lo representa bien |
| — | **B** (0) | **Ninguna es una condición disfrazada** |

**Que no haya ni una B es el resultado, no la falta de él.** `V73`, `V74` y `V77`
ya habían sacado del sujeto PROPIEDAD todo lo que se negocia. Lo que quedaba no
era una clave mal colocada.

---

## Lo que sí quedaba: la cobertura del par

Vivir en sujetos distintos no basta. Falta una condición que nadie estaba
mirando:

> **El hecho tiene que llegar donde llega su condición.** Si la condición aplica
> a un tipo donde el hecho no aplica, en ese tipo el pacto es *la única casilla*
> donde cabe el hecho.

La pregunta corrida como SQL sobre los ocho pares deliberados, **antes** de V78:

```
 hecho                | condicion                | tipo sin hecho
----------------------+--------------------------+----------------
 amoblado             | se_ofrece_amoblado       | O
 cuota_mantenimiento  | mantenimiento_a_cargo_de | A
 cuota_mantenimiento  | mantenimiento_a_cargo_de | C
(3 rows)
```

Tres, y ninguno es un descuido. `V74` amplió `se_ofrece_amoblado` a **OFICINA**
a propósito —«una oficina amoblada es un producto real y se anuncia como
tal»— sin ampliar `amoblado`. `V77` llevó `mantenimiento_a_cargo_de` a
**ALMACÉN** y **CASA** —parque logístico y condominio cobran mantenimiento— sin
ampliar `cuota_mantenimiento`. Las dos migraciones hicieron lo correcto en su
lado; nadie miraba el otro.

---

## Lo que hace V78

Tres filas en `catalogo_atributo_tipo`, `OPC`, con `requerido = false`:

| clave | tipo | valores existentes en ese tipo (dev / repositorios) |
|---|---|---|
| `amoblado` | **O** | 0 / 0 — sus 512 están en C (175) y D (337) |
| `cuota_mantenimiento` | **A** | 0 / 0 — sus 784 están en D (302), L (386) y O (96) |
| `cuota_mantenimiento` | **C** | 0 / 0 — ídem |

Estado resultante: `amoblado` → **C, D, O** · `cuota_mantenimiento` → **A, C, D,
L, O**, las cinco `OPC/false`.

**Ni una columna, ni un trigger, ni un `tipo_dato`, ni un valor movido.** Las
tres entran OPC porque entrar como PUB dejaría sin poder publicarse, de golpe, a
toda casa, todo almacén y toda oficina que no tenga el dato — que son todas,
porque el dato acaba de nacer.

### Las guardas de la migración

Cuentan invariantes sobre el estado resultante, no cifras escritas a mano:

1. **Las tres filas entraron.** Si una clave no estuviera en el catálogo, el
   `INSERT ... SELECT` no insertaría nada y la migración terminaría «bien».
2. **La consulta de huecos da cero** sobre los ocho pares.
3. **No se perdió aplicabilidad.** Una `TEMP TABLE` fotografía las 66 filas
   antes del `INSERT` y la guarda comprueba que las 66 siguen ahí *con su misma
   exigencia y su mismo `requerido`* — un conteo final habría coincidido igual
   borrando una y añadiendo dos. Se verificó que la guarda **muerde**: borrando
   `dormitorios/D` a propósito, aborta nombrando la fila.
4. **`requerido` sigue siendo espejo de `exigencia`.**
5. **El enrutamiento por sujeto sigue intacto en las dos direcciones.**

---

## Pruebas

Tres, y ninguna repite a otra.

| Prueba | Dónde | Qué afirma |
|---|---|---|
| `elHechoCabeDondeSePactaSuCondicion` | `SujetoDelDatoIntegrationTest` | Los tres casos por el **caso de uso**: OFICINA con `amoblado`, ALMACÉN y CASA con `cuota_mantenimiento`. Cada uno recorre alta → pactar la condición gemela → **leer los dos a la vez, distintos y en sitios distintos** → editar el hecho (no mueve el pacto) → editar el pacto (no reescribe el hecho) |
| `ningunHechoLlegaMenosLejosQueSuCondicion` | `SujetoDelDatoIntegrationTest` | La invariante nueva, en SQL y sobre el catálogo real: **cero huecos** en los ocho pares |
| `requeridoEsEspejoDeExigencia` | `CatalogoQueHablaIntegrationTest` | Las dos columnas no pueden divergir |

Los pares se declararon **una sola vez**, en `PARES_DELIBERADOS`: las dos
comprobaciones que los recorren —*no comparten sujeto* (V77) y *el hecho no
llega menos lejos* (V78)— con dos copias de la lista podrían acabar mirando
cosas distintas sin que nada fallara.

**No se duplicó lo que V77 ya prueba.** Que el cruce se *rechace* en las dos
direcciones ya lo cubre `elParNoSePuedeCruzar`; V78 prueba lo contrario, que es
lo que añade: que los dos **convivan**.

Y las pruebas no son vacuas: el mecanismo de aplicabilidad sigue rechazando lo
que está fuera —`El atributo "amoblado" no aplica a una propiedad de tipo T`—,
verificado contra el trigger real.

---

## Lo que V78 NO hace, y por qué

| Cambio | Por qué se detiene | Dónde va |
|---|---|---|
| `cuota_mantenimiento` DECIMAL → **IMPORTE** | Bloqueado dos veces: `tg_catalogo_sistema_inmutable`, y 784 filas sin moneda de las que no hay fuente para deducirla sin inventarla | Cuando la moneda se **declare** |
| `rubro_permitido` → LISTA_MULTIPLE · `zonificacion` → LISTA · `banos` → ENTERO | El mismo trigger, y cada una necesita un vocabulario que no existe | Cortes de catálogo 3 y 5 |
| **`servicios_disponibles`** | Es hecho de la PROPIEDAD y está bien colocado. Lo que le falta es **vocabulario**: declarada LISTA y sin una sola opción sembrada, el trigger no valida nada y `MotorDeCaptura.controlDe` la degrada a TEXTO | Corte de evolución del catálogo. **No** se le inventa vocabulario aquí |
| Ampliar `banos` a L,O,A · `zonificacion` a O · `pisos_edificacion` a D,O · `frente` a C | No las justifica la pregunta del sujeto. Son **profundidad**, y mezclarlas haría imposible decir qué corrigió qué | Corte 1, mitad de profundidad |
| Los cuatro hechos que faltan (`mascotas_reglamento`, `nivel_implementacion`, `estado_ocupacion`, `lote_minimo_normativo`) | Claves nuevas, con su corte ya asignado (3, 4, 5, 5). La invariante **no** se le exige a un hecho que no ha nacido | Cortes 3–5 |

---

## Gate de cierre

Una sola corrida, `verificacion/Verificar-Cierre.ps1` + Angular + build de
producción.

| | Resultado |
|---|---|
| Reactor completo contra PostgreSQL real | **717 + 48 + 374**, 0 fallos, **0 skipped** |
| Suites de integración ejecutadas (no saltadas) | **22 / 22**, comprobado por el paso 3 del script |
| `SujetoDelDatoIntegrationTest` | **30 / 30** (28 → 30) |
| `CatalogoQueHablaIntegrationTest` | **20 / 20** (19 → 20) |
| `ConservacionDeLaEdicionIntegrationTest` (gate del 0A) | **47 / 47** — ampliar aplicabilidad no lo movió |
| Angular | **668 / 668** |
| Build de producción | **verde** (sólo warnings de presupuesto preexistentes) |
| Suites E2E | **5 / 5** · 65 + 41 + 125 + 18 + 147 = **396 casos, 0 fallas** |
| `git diff --check` | limpio |

La cadena completa de migraciones —V1…V78— corrió sobre **base recién creada**
cinco veces, una por suite E2E, sin incidencias.

### SQL de cierre · idéntico en `controllocal_dev` y `controllocal_repositorios`

```
consulta de huecos sobre los 8 pares .... 0 filas   (antes: 3)
claves PROPIEDAD ........................ 19
claves ENCARGO .......................... 26
pares en el mismo sujeto ................ 0
cruces de sujeto ........................ 0
requerido <> (exigencia='ALT') .......... 0
filas de aplicabilidad por tipo ......... 69   (66 -> 69, las 66 intactas)
filas por tipo x operacion .............. 112  (sin cambio)

amoblado ................................ C, D, O   todas OPC/false
cuota_mantenimiento ..................... A, C, D, L, O   todas OPC/false
```

---

## Criterio de salida

> Cada dato sabe si describe al inmueble o al encargo, y las 19 claves de
> PROPIEDAD ya no están bloqueadas por una confusión entre hecho y condición.

Se cumple, y con el matiz que la medición añadió: **ninguna de las 19 lo estaba
por confusión de sujeto**. Lo que sí las bloqueaba, en tres casos concretos, era
que el hecho no llegaba tan lejos como su condición — y eso ya no puede volver a
pasar sin que un gate lo diga.

---

## Deudas descubiertas, que son de otro corte

Ninguna bloquea V78; todas quedan dichas.

1. **`servicios_disponibles` es una LISTA muda.** `tipo_dato='LISTA'` con cero
   opciones sembradas: el trigger sólo valida pertenencia *si la clave tiene
   vocabulario*, así que acepta cualquier cadena, y `controlDe` devuelve TEXTO
   porque deriva el control de si hay opciones. El dato entra, pero no compara
   con nada. **Corte de evolución del catálogo.** Su reemplazo (`agua_desague`,
   `energia_electrica`, `gas`, cada uno con «con factibilidad aprobada») está
   previsto para el Corte 5, y hasta entonces no se retira: dejaría varios
   cortes en los que BROX deja de capturar un hecho que hoy captura.

2. **La guarda de «listas sin vocabulario» sólo mira el ENCARGO.** V77 la
   escribió para su lado (`c.sujeto = 'ENCARGO'`) y la PROPIEDAD se quedó sin
   ella — que es exactamente por qué `servicios_disponibles` sobrevivió mudo.
   Extenderla exige antes darle vocabulario a esa clave, o la guarda nace
   fallando; van juntas, en el mismo corte.

3. **`apto_licencia_funcionamiento` no tiene definición formal.** La aptitud
   para una licencia municipal depende del uso o rubro que se pretenda, no sólo
   del inmueble: un local apto para una bodega puede no serlo para un
   restaurante. No apareció como cruce PROPIEDAD/ENCARGO ni como hueco de par
   —por eso no detuvo este corte—, pero merece definirse antes de que alguien
   lo use para descartar candidatos.

4. **`controllocal_repositorios` acumula residuo de pruebas.** 726 claves de
   catálogo con `organizacion_id` (las `zz_*` que las pruebas siembran por
   caso) y sus valores en `atributo_propiedad`. Las pruebas retiran la clave con
   `activo = false` pero no borran sus filas. No afecta al catálogo del sistema
   —69 filas de aplicabilidad y 112 de tipo × operación, idénticas a `dev`—,
   pero infla cualquier conteo que no filtre por `organizacion_id IS NULL`.

5. **Sigue pendiente la mitad de profundidad del Corte 1**: `banos` a L,O,A ·
   `zonificacion` a O · `pisos_edificacion` a D,O · `frente` a C ·
   `interiorUnidad`/`nombreEdificioGaleria` a A. Están medidas e inertes en
   `auditoria-profundidad-inmobiliaria.md`; lo que falta es decidir su
   exigencia, que es una decisión de negocio.
