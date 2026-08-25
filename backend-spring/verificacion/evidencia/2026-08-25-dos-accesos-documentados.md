# Evidencia — Los dos accesos que ya constaban, conservados

**Fecha:** 2026-08-25
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `4fcffff` (publicado; encargo congelado en `0cd0717`, sólo documentación)
**Encargo:** `docs/ai/encargo-conciliacion-dos-accesos-documentados.md`
**Migración:** **ninguna.** Sin SQL directo, sin código nuevo, sin Angular.

**Resultado: dos valores escritos, `LOC-0001` intacta, y las bloqueadas pasan de
21 a 19 — medido, no restado.**

---

## 1. Preflight

`4fcffff..0cd0717` = **sólo documentación**. Árbol limpio, Flyway en **82**.

Estado de partida, medido:

```
valores de tipo_acceso : 0
hitos P                : 3
publicacion            : C = 9  ·  P = 3
bloqueadas             : 21 de 26   (causa única: tipo_acceso(PUB))
```

La deuda de las tres candidatas, agregando **todas** sus claves `ALT`/`PUB`
faltantes sin nombrar ninguna en la consulta: **las tres tienen exactamente una**,
`tipo_acceso(PUB)`. Ninguna arrastra otra deuda escondida.

---

## 2. `LOC-D001` → `A_PIE_DE_CALLE`

**Texto fuente, literal** (`propiedad.descripcion`):

> «Local **a pie de calle** con vitrina»

**Valor escrito:** `A_PIE_DE_CALLE`.

**Por qué es literal y no interpretativo:** la descripción contiene **la frase
exacta del vocabulario**. `A_PIE_DE_CALLE` se lee «A pie de calle»; el texto dice
«a pie de calle». No hay que elegir entre opciones, ni interpretar, ni apoyarse en
lo frecuente: se transcribe.

**Registro completo revisado**, y nada lo contradice: `nombre_edificio_galeria`,
`interior_unidad`, `piso` y `referencia_interna` **vacíos**; `zona_urbanizacion` =
«Miraflores Centro» (un barrio, no un continente comercial); sus otros atributos
son `rubro_permitido = Restaurante`, `ambientes`, `antiguedad_anios`. Su historial
—diez entradas de prospección, solicitud y comisión— **no menciona el acceso**.

---

## 3. `LOC-0002` → `GALERIA_INTERIOR`

**Texto fuente, literal** (`propiedad.descripcion`):

> «Local **en galeria** del centro, alto transito peatonal.»

**Valor escrito:** `GALERIA_INTERIOR`.

**Por qué es literal:** «en galería» es la frase del vocabulario
(`GALERIA_INTERIOR` = «Galería interior»). «del centro» sitúa la galería —Jr.
Camaná 615, Lima Cercado, el centro histórico—, no la reclasifica.

### 3.1 · La duda que el encargo mandaba examinar, y por qué NO se volvió real

El encargo advierte que en Lima «galería» nombra a veces lo que este vocabulario
clasificaría `CENTRO_COMERCIAL`, y ordena parar si al leer el registro completo la
duda se vuelve real. **La examiné, y no se vuelve real, por una razón medida en el
propio dato:**

**Este conjunto de datos distingue los dos términos, y los usa aparte.** Cuando
quiere decir centro comercial, lo dice con esas palabras — `LOC-0001` lleva
`zona_urbanizacion` = **«Centro comercial de Miraflores»**. `LOC-0002` **no
contiene la expresión «centro comercial» en ningún campo**: ni descripción, ni
zona (vacía), ni edificio, ni interior, ni referencia, ni ninguno de sus
atributos, ni su historial.

Es decir: no estoy eligiendo entre dos lecturas de una palabra ambigua. Estoy
transcribiendo el término que el registro usa **en un registro donde el otro
término también existe y se aplica a otra propiedad**. Si el dato hubiera querido
decir centro comercial, este mismo corpus demuestra que sabía cómo decirlo.

**Registro completo revisado**, nada lo contradice: campos de ubicación vacíos,
atributos `rubro_permitido = Comercio minorista`, `ambientes`,
`antiguedad_anios`, `carga_electrica_kw`, `apto_licencia_funcionamiento`; su
prospección dice «Primer contacto hecho; propietario pidio llamar de nuevo» y su
historial habla de una solicitud rechazada. **Ninguno menciona el acceso.**

---

## 4. `LOC-0001` — NO se toca, y ésta es la razón

Su registro contiene **dos afirmaciones que apuntan a dos valores excluyentes del
mismo vocabulario**:

> `descripcion` = «Local comercial **en esquina**, primera linea de avenida.»
> `zona_urbanizacion` = «**Centro comercial** de Miraflores»

| frase | valor al que apunta |
|---|---|
| «en esquina» | `ESQUINA_A_CALLE` |
| «Centro comercial de Miraflores» | `CENTRO_COMERCIAL` |

`tipo_acceso` es una `LISTA` de **valor único**: sólo cabe uno. Elegir cuál de las
dos frases manda **sería inferir**, y ninguna de las dos es más autoritativa que
la otra — una está en la descripción y la otra en la zona, y ambas las escribió la
misma siembra.

**Se queda vacía**, y sigue bloqueada para publicar con causa `tipo_acceso(PUB)`.
Se desbloqueará cuando alguien la visite y diga cuál de las dos es —que es
exactamente para lo que existe la exigencia.

---

## 5. Procedencia — **B · SÓLO PARCIAL**

> **PROCEDENCIA = B (sólo parcial).** Hay rastro del **acto** —quién, cuándo, por
> qué canal, sobre qué propiedad— y **no lo hay de la fuente del dato**: la carga
> útil del evento es `{"idPropiedad": 3}` y no nombra el atributo, su valor ni de
> dónde salió. No es **A** porque, sin este documento, nadie puede reconstruir que
> los dos valores se transcribieron de la `descripcion` en vez de capturarse en
> una visita. No es **C** porque el acto sí deja huella verificable en
> `evento_dominio` y en `comando_idempotente`. La evidencia de por qué está abajo.

`atributo_propiedad` **no tiene columna de procedencia**. Confirmado: sus once
columnas son `id_atributo_propiedad, organizacion_id, id_propiedad, clave,
valor_texto, valor_numero, valor_booleano, fecha_creacion, fecha_actualizacion,
valor_fecha, valor_moneda`.

**Comprobé si el sistema la registra en otro sitio, y la respuesta es «a medias».**
`PropiedadUniversalServiceImpl.editar` emite un evento de dominio en la misma
transacción, sellado con la procedencia. Medido empíricamente tras la primera
escritura:

```
evento_dominio: 10 → 11
tipo=PROPIEDAD_EDITADA · entidad=PROPIEDAD · entidad_id=3
canal=SPA · agente=(vacío) · carga_util={"idPropiedad":3}
```

**Lo que SÍ queda registrado:** que la propiedad 3 fue editada, **por qué canal**
(`SPA`), **por qué actor** y **cuándo**. También en `comando_idempotente`, con la
misma procedencia.

**Lo que NO queda registrado, y es la deuda:** la carga útil del evento es
`{"idPropiedad": 3}` — **no dice qué atributo cambió, ni su valor, ni de dónde
salió**. Así que estos dos valores son, en la base, **indistinguibles de uno
capturado en una visita**. La única constancia de que se transcribieron de la
`descripcion` es **este documento**.

> **Queda declarado como deuda, no resuelto.** No se inventó ningún mecanismo de
> procedencia: el North Star pide que todo dato la lleve, y hoy el modelo la lleva
> **para el acto de edición**, no **para el dato editado**. Cerrar eso es un corte
> propio y una decisión de CONTROL.

---

## 6. Cómo se escribió

Por el **mecanismo normal de edición**, el mismo que usaría un broker:
`PUT /propiedades/{id}` (rol `AGENTE`, fila 122 de la matriz), una llamada por
propiedad, ambas **HTTP 200**. **Cero SQL directo.**

```
PUT /propiedades/3  {"atributos":[{"clave":"tipo_acceso","valor":"A_PIE_DE_CALLE"}]}    → 200
PUT /propiedades/2  {"atributos":[{"clave":"tipo_acceso","valor":"GALERIA_INTERIOR"}]}  → 200
```

El mecanismo permitió conservar los dos hechos sin ninguna excepción, así que no
hubo `STOP` por esa vía.

---

## 7. Las siete mediciones

| # | medición | esperado | **medido** |
|---|---|---|---|
| 1 | valores nuevos de `tipo_acceso` | exactamente 2 | **2** ✅ |
| 2 | `LOC-0001` | sigue vacío | **vacío** ✅ |
| 3 | ningún otro añadido | total 2, y son los dos nombrados | **`LOC-0002` + `LOC-D001`**, nada más ✅ |
| 4 | hitos `P` | 3 → 3 | **3 → 3** ✅ |
| 5 | publicaciones | ninguna cambió de estado | **`C=9 · P=3`**, idéntico ✅ |
| 6 | `PUBLICADO` con deuda `PUB` | 0 | **0** ✅ |
| 7 | **bloqueados antes / después** | 21 → *medir* | **21 → 19** ✅ |

### 7.1 · El punto 7, medido y no restado

La cifra sale de **agregar todas las claves `ALT`/`PUB` faltantes sin filtrar por
ninguna**, la misma consulta antes y después. Y no me quedé en el número: comprobé
**cuáles** son las siete publicables, para descartar que otra propiedad se hubiera
movido por efecto lateral.

```
PUBLICABLES DESPUÉS (7)
  LOC-0002   L   ← se desbloquea en este microcorte
  LOC-D001   L   ← se desbloquea en este microcorte
  LOC-D014   O   ya lo era (tipo_acceso no aplica a oficina)
  LOC-D019   O   ya lo era
  PROP-0023  D   ya lo era
  PROP-0024  T   ya lo era
  PROP-0025  C   ya lo era
```

**Las dos que entran son exactamente las dos que se escribieron.** Que el 19
coincida con la resta ingenua es un resultado, no un supuesto: si alguna hubiera
arrastrado otra deuda `ALT`, habría seguido bloqueada — por eso se midió su deuda
completa **antes** (§1) y el conjunto publicable **después**.

`LOC-0001` **sigue bloqueada**, con causa `tipo_acceso(PUB)`, que es lo decidido.

---

## 7.2 · Las afirmaciones de cierre, en la forma que fijó el titular

```
LOC-D001
  → valor respaldado literalmente ................................. SÍ  (§2)

LOC-0002
  → valor respaldado literalmente ................................. SÍ  (§3)
  → registro completo no introdujo ambigüedad ..................... SÍ  (§3.1)

LOC-0001
  → sigue sin valor ............................................... SÍ  (§4)

ningún otro tipo_acceso añadido ................................... SÍ  (total = 2)
ningún P nuevo .................................................... SÍ  (3 → 3)
ninguna publicación alterada incidentalmente ...................... SÍ  (C=9 · P=3)

bloqueados antes  = 21
bloqueados después = 19   ← MEDIDO (§7.1), con el conjunto publicable enumerado

PROCEDENCIA = B · sólo parcial ..................................... §5
```

## 8. Lo que este microcorte NO hizo

- **No rellenó ningún otro inmueble.** Quedan **19** bloqueados, y los otros 17
  siguen esperando visita.
- **No tocó `LOC-0001`**, que era la trampa.
- **No infirió** desde rubro, dirección, distrito ni caso frecuente. En los dos
  casos escritos, la frase del vocabulario **estaba en el texto**.
- **No hubo migración, SQL directo, código nuevo, Angular, `V81`/`V82`, Corte 5
  ni I0.**
