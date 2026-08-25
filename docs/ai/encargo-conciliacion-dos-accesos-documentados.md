# Encargo — Microcorte final de Corte 4 · los dos accesos que ya constaban

**Congelado por CONTROL el 2026-08-25**, por decisión del titular.

**BASE_SHA:** `4fcffff2507ab6ee6cac6575d391ca24abf53167` — **publicado**
(`origin/feat/… == HEAD`, 0 por delante), árbol limpio, dev en `V82`.

**No se abre el Corte 5. No se abre I0.** I0 se abre **sólo** cuando el SHA de
este microcorte esté publicado.

---

## 1. Qué hay que hacer

**Conservar dos hechos que ya constan por escrito. No descubrir ninguno.**

| propiedad | valor | sustento textual, literal |
|---|---|---|
| **`LOC-D001`** | **`A_PIE_DE_CALLE`** | `descripcion` = «Local **a pie de calle** con vitrina» |
| **`LOC-0002`** | **`GALERIA_INTERIOR`** | `descripcion` = «Local **en galeria** del centro, alto transito peatonal.» |

**`LOC-0001` permanece SIN `tipo_acceso`.** Su `descripcion` dice «Local comercial
**en esquina**, primera linea de avenida.» y su `zona_urbanizacion` dice **«Centro
comercial de Miraflores»**: **dos opciones excluyentes** del mismo vocabulario
(`ESQUINA_A_CALLE` y `CENTRO_COMERCIAL`) sobre el mismo registro. `tipo_acceso` es
LISTA de **valor único**, así que elegir sería inferir. **No se toca.**

**Ningún otro inmueble se rellena.**

Vocabulario vigente, medido: `A_PIE_DE_CALLE · ESQUINA_A_CALLE · GALERIA_INTERIOR
· PASAJE_COMERCIAL · CENTRO_COMERCIAL · INTERIOR_DE_EDIFICIO · MERCADO`.

---

## 2. Por qué esto NO es inferir, y dónde está el límite

La regla del proyecto es **no inventar un dato que no se sabe**. Aquí el dato
**ya está escrito** en el registro, con la palabra del vocabulario: «a pie de
calle» **es** `A_PIE_DE_CALLE`. Lo que se hace es **moverlo a la autoridad que
corresponde** —de un texto libre a la clave gobernada—, no deducirlo.

**El límite, y no se cruza:** si para llegar al valor hiciera falta interpretar,
elegir entre dos, o apoyarse en lo frecuente, **no se escribe**. `LOC-0002` es el
caso más cercano al límite y **entra igualmente**: «en galería» corresponde
literalmente a `GALERIA_INTERIOR`. Se anota, eso sí, que en Lima «galería» nombra
a veces lo que este vocabulario clasificaría `CENTRO_COMERCIAL` — **y si al leer
el registro completo esa duda se vuelve real, se para y se pregunta.**

---

## 3. Cómo se escribe

**Por el mecanismo normal de edición**, el mismo que usaría un broker. **Sin
migración, sin SQL a mano, sin código nuevo.**

**Si el mecanismo normal no permite conservar esos hechos, DETENTE Y REPÓRTALO.**

### 3.1 Procedencia — medido, y hay que decir qué pasa

`atributo_propiedad` **no tiene columna de procedencia**: sus columnas son
`id_atributo_propiedad, organizacion_id, id_propiedad, clave, valor_texto,
valor_numero, valor_booleano, fecha_creacion, fecha_actualizacion, valor_fecha,
valor_moneda`.

El North Star pide que **todo dato lleve su procedencia**. Aquí no hay dónde
ponerla en la fila. **Comprueba si el sistema la registra en otro sitio**
—historial, auditoría, outbox— y **dilo en la evidencia**:

- si la registra, **nómbrala**;
- si **no** la registra en ninguna parte, **escríbelo como deuda**: estos dos
  valores quedarán indistinguibles de uno capturado en visita, y **la única
  constancia de que salieron de la descripción será el documento de evidencia**.

**No inventes un mecanismo de procedencia.** Sólo mide y declara.

---

## 4. Lo que hay que medir después

| # | medición | esperado |
|---|---|---|
| 1 | valores nuevos de `tipo_acceso` | **exactamente 2** |
| 2 | `LOC-0001` | **sigue vacío** |
| 3 | ningún otro `tipo_acceso` añadido | total = **2**, y son los dos nombrados |
| 4 | hitos `P` | **3 → 3**, ninguno nuevo |
| 5 | publicaciones | **ninguna cambió de estado** por efecto lateral (`C=9 · P=3` antes) |
| 6 | `PUBLICADO` con deuda `PUB` conocida | **0** |
| 7 | **bloqueados antes / después** | antes = **21**. **Mide el después de verdad; NO asumas que baja a 19** |

**El punto 7 es el que importa.** Si alguna de las dos tuviera otra deuda `ALT`
faltante, seguiría bloqueada aunque le escribas `tipo_acceso`. **Mide, no restes.**
Y la cifra sale de agregar **todas** las claves ALT/PUB faltantes **sin filtrar
por ninguna**.

---

## 5. Prohibido

Rellenar cualquier otro inmueble · tocar `LOC-0001` · inferir desde el rubro, la
dirección, el distrito o el caso frecuente · migración · SQL directo · código
nuevo · Angular · `V81`/`V82` · Corte 5 · I0.

---

## 6. Evidencia

`backend-spring/verificacion/evidencia/2026-08-25-dos-accesos-documentados.md`

Con, **por propiedad**: el texto fuente **citado literal**, el valor escrito, y
**por qué la correspondencia es literal y no interpretativa**. Más `LOC-0001` con
**las dos frases que se contradicen** y la razón de dejarla vacía. Más las siete
mediciones de §4 y lo que resulte de §3.1.

---

## 7. Cierre

Gate `.sql` verde **dentro** de `Verificar-Cierre.ps1` · suite completa ·
`ng test` y `ng build --configuration production` · **commit único** · árbol
limpio.

**No hagas push.** Lo intenta CONTROL; si las credenciales no lo permiten, lo
lanza el titular.

## 8. Protocolo

**`STOP — DECISIÓN REQUERIDA POR CONTROL`** si el mecanismo normal no permite
conservar los hechos, o **si al leer el registro completo dudas de que alguna
correspondencia sea literal**. Ante la duda, para. Inventar un dato es el único
error que este proyecto no perdona — y en este microcorte el riesgo va justo en
esa dirección.

```
LISTO PARA AUDITORÍA
BASE_SHA=4fcffff
CANDIDATE_SHA=<sha>
```
