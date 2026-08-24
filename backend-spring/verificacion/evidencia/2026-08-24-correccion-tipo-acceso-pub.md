# Evidencia — Corrección post-Corte 4 · `tipo_acceso` pasa a `PUB` — `V82`

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `96a4d65` (el encargo se congeló en `89f2bad`, sólo documentación)
**Encargo que gobierna:** `docs/ai/encargo-correccion-tipo-acceso-pub.md`
**Migración:** `V82__tipo_acceso_impide_publicar_no_registrar.sql` (única)
**Origen:** §9 de la evidencia del Corte 4 — el hallazgo que se devolvió como
decisión en lugar de resolverlo.

---

## 1. Preflight — verificado, no copiado

| comprobación | medido |
|---|---|
| HEAD `89f2bad`, árbol limpio; `96a4d65..89f2bad` = **sólo docs** | ✅ |
| Flyway en `controllocal_dev` | **81 · `success = t`** |
| `V82` libre | sólo existen `V80`, `V81` |
| `tipo_acceso` | **una sola fila**: `L` · `ALT` · `requerido = t` |
| censo `catalogo_atributo_tipo` | **ALT/t 11 · OPC/f 224 · PUB 0** (total 235) |
| valores de `tipo_acceso` en `atributo_propiedad` (dev) | **0** |
| `ck_catalogo_exigencia` | admite `PUB` |
| triggers de usuario en `catalogo_atributo_tipo` | **ninguno** |
| publicabilidad | **5 de 26**, 21 bloqueadas, **causa única `tipo_acceso`** |

La causa se midió **agregando todas las claves `ALT`/`PUB` faltantes**, sin
nombrar ninguna: la consulta devuelve `n_causas_distintas = 1`. No se filtró por
`tipo_acceso` en ningún momento.

**Y el modelo ya distinguía**, verificado en el código y no asumido:

```java
Exigencia.bloqueaAlta()         -> return this == ALT;
Exigencia.bloqueaPublicacion()  -> return this == ALT || this == PUB;
```

`CatalogoAtributo.esRequeridoPara` pregunta por `bloqueaAlta()`, y su javadoc lo
explica: *«basta que un consumidor lea "lo que no sea OPC" para que el alta
empiece a exigir de golpe todo lo que solo debia exigir el anuncio.»*

**No hubo `STOP`, y no se tocó una línea de Java de producción.**

---

## 2. El cambio

Una fila, dos columnas, una sentencia, por clave y nunca por id literal:

```
tipo_acceso / L :  exigencia  ALT  → PUB
                   requerido  true → false
```

Las dos juntas porque el **guard 2.4 de `V78`** exige que `requerido` sea espejo
exacto de `exigencia = 'ALT'` en todo el catálogo.

| | antes | predicho | **medido después** |
|---|---|---|---|
| `ALT` / `requerido = true` | 11 | 10 | **10** ✅ |
| `PUB` / `requerido = false` | 0 | 1 | **1** ✅ |
| `OPC` / `requerido = false` | 224 | 224 | **224** ✅ |
| total (catálogo del sistema) | 235 | 235 | **235** ✅ |
| valores de `tipo_acceso` | 0 | 0 | **0** ✅ |

### 2.1 · Lo que NO se movió, que es la prueba

```
 total | bloqueadas | publicables | todas_las_causas
-------+------------+-------------+------------------
    26 |         21 |           5 | tipo_acceso
```

**Idéntico a antes de `V82`.** Las mismas 21, la misma causa única. La corrección
no buscaba hacerlas publicables — buscaba poder **registrar** un local sin
conocer todavía el dato.

---

## 3. Los cuatro escenarios, como prueba permanente

No se demostraron a mano contra la cartera de desarrollo: se escribieron en
`CatalogoQueHablaIntegrationTest`, que corre en la corrida de cierre contra
`TEST_DB_URL`. Es un fichero que **ya existía**, así que **no toca el inventario
de las 20 clases** y `GateDeCierreTest` y `Verificar-Cierre.ps1` siguen
coincidiendo sin tocarlos.

`tipoAccesoImpidePublicarNoRegistrar()` — sobre la clave **real**, no sobre una
marcada al vuelo:

| # | Acción | Resultado |
|---|---|---|
| 0 | El nivel es el decidido | `exigencia = PUB`, `requerido = false` |
| **1** | Registrar un `LOCAL` **sin** `tipo_acceso` | **alta aceptada**, y el dato queda `null` |
| **2** | Leer, editar `aforo_itse`, releer | `tipo_acceso` **sigue ausente**; dirección y titulares intactos |
| **3** | Publicarlo | **rechazo**, con el rótulo «Tipo de acceso» en el mensaje |
| **4** | Declarar `A_PIE_DE_CALLE` | **publica** |

**La prueba de causa no nombra la clave.** Agrega todas las `ALT`/`PUB`
faltantes de esa propiedad y exige que el resultado sea exactamente
`["tipo_acceso"]`. Una consulta que filtrara por la clave y luego «descubriera»
que la causa es esa clave no demostraría nada.

**Que el escenario 1 fallaba con `ALT` no es una suposición**: está medido en el
Corte 4, donde 30 tests de integración murieron con
`Faltan atributos obligatorios de LOCAL: tipo_acceso` al intentar **registrar**.

---

## 4. La consecuencia de §7, medida — y el encargo la predijo mal

El encargo §7 dice que **los 7 con encargo vivo siguen avisando** y los 14 dejan
de avisarlo. **Medido, eso es falso: no avisa ninguno.**

| superficie | qué la alimenta | ¿nombra `tipo_acceso`? |
|---|---|---|
| `PropiedadResponse.atributosQueFaltan` | `clavesObligatoriasQueFaltan` → **sólo `ALT`** | **No**, desde `V82` |
| `EncargoResponse.faltanParaPublicar` | `AtributosDeEncargo.faltantesDeEncargoParaPublicar` → sujeto **ENCARGO** | **No**, y **nunca pudo** |

La segunda fila es el punto, y **no es un accidente de datos: es estructural**.
`faltanEnElEncargo` llama a `condiciones.faltantesDeEncargoParaPublicar`, que
consulta `catalogo_atributo_operacion` — la tabla del sujeto **ENCARGO**. Y el
**guard 2.5 de `V78`** garantiza que **ninguna clave de la PROPIEDAD tiene fila
ahí**. Comprobado:

```
B: puede una clave de PROPIEDAD salir en faltanParaPublicar?
   -> NO (ninguna clave PROPIEDAD tiene fila de operacion)
```

Medido sobre la cartera, con la API real (`LOC-D012`, que **sí** tiene encargo
vivo):

```
LOC-D012 | encargos vivos: 1
   atributosQueFaltan  : []
   faltanParaPublicar  : []
```

Y sin embargo el bloqueo es real:

```
POST /encargos/13/publicaciones  ->  HTTP 400
{"error":"Todavia no se puede publicar: de la ficha del inmueble falta Tipo de
          acceso. Se puede registrar sin ese dato, pero no anunciarlo."}
publicaciones creadas: 0
```

### Las dos cifras, corregidas

| | antes de `V82` | después de `V82` |
|---|---|---|
| locales que **avisan** de que les falta `tipo_acceso` | **21** (todos, vía `atributosQueFaltan`) | **0** |
| locales **bloqueados** para publicar | 21 | **21** |

**No es 7 / 14. Es 21 → 0.** El aviso no se movió al encargo: **desapareció**.
La barrera sigue intacta; lo que se perdió es que alguien la vea.

> **No lo he arreglado, y es deliberado.** El encargo dice que construir esa
> superficie es un corte propio, y estoy de acuerdo: hoy no existe ningún sitio
> donde una clave `PUB` de la PROPIEDAD se reporte, y crearlo es superficie nueva.
> Lo que sí hice fue **dejarlo escrito en una prueba**
> (`elBloqueoNoSeAnunciaEnNingunaSuperficie`) que afirma el hueco tal como es hoy:
> el día que alguien construya el aviso, **ese test se pondrá rojo** y será la
> señal de que ya se puede afirmar lo contrario.

`publicacionGestionable.permitida = true` en esos encargos **no lo causa `V82`**:
`gestionDePublicacion` sólo mira si el encargo está vivo, y ya era `true` antes.
Se anota para que no se le atribuya a esta corrección.

---

## 5. Un fallo mío, y lo que enseñó sobre las cifras de una migración

La primera versión de `V82` **se aplicó bien en `controllocal_dev` y reventó la
corrida de tests**, tumbando el contexto de Spring entero (33 de 33 en error):

```
ERROR: V82: se esperaban 235 filas de aplicabilidad y hay 4596
```

**Causa:** mis guardas de censo contaban `catalogo_atributo_tipo` **entera**. La
tabla incluye las claves que cada organización define para sí, y
`controllocal_repositorios` —la base de integración— acumula **4361 filas de
tenant** dejadas por las suites. Estaba midiendo **el uso del producto**, no la
invariante. Es exactamente el modo de fallo que el Corte 3.a vino a arreglar en
el gate `.sql`, cometido otra vez y en otro sitio.

Lo mismo con la guarda de valores: afirmaba «cero valores de `tipo_acceso`».
Cierto en dev; en la base de integración hay **125**, registrados por los
*fixtures* que yo mismo añadí en el Corte 4 — y son legítimos, porque un guion
que publica tiene que declarar el dato.

**Corregido a la invariante de verdad:**

| guarda | antes (foto de una base) | ahora (invariante) |
|---|---|---|
| censo total | `count(*) = 235` sobre toda la tabla | `= 235` **sobre `organizacion_id IS NULL`** |
| censo OPC | `= 224` sobre toda la tabla | `= 224` **sobre el catálogo del sistema** |
| valores | «hay **cero**» | «el número **no cambia**» (foto previa) |

Y la foto de comparación pasó a indexarse por **`id_catalogo_atributo`** en vez
de por `clave`: dos organizaciones pueden tener la misma clave, y un `JOIN` por
clave multiplicaría filas.

**Verificado en las dos bases**, que es lo que hace afirmables las cifras:

```
                       dev      repositorios
sistema total          235          235
sistema OPC/false      224          224
filas de tenant          0         4361
valores tipo_acceso      0          125
```

Como la primera versión ya se había aplicado en dev, **se deshizo su efecto y se
borró su fila de `flyway_schema_history`** antes de reaplicar la corregida —
`V82` no estaba en ningún commit, así que no hay checksum publicado que romper.
`V81` **no se tocó en ningún momento**.

---

## 6. Que las guardas de `V82` muerden

Tres roturas deliberadas dentro de transacciones que terminan en `ROLLBACK`:

| Rotura simulada | Resultado |
|---|---|
| Cambiar sólo `exigencia`, olvidando `requerido` | ❌ **2.4 (espejo) aborta**: `tipo_acceso/L` |
| Promover además otra clave (`aforo_itse`) sin decirlo | ❌ **2.5 aborta**: `aforo_itse/L: OPC -> PUB` |
| …y la misma rotura vista desde la otra guarda | ❌ **2.2 aborta**: `PUB de mas: aforo_itse/L` |

La 2.5 es la que importa: compara **fila a fila contra la foto previa**, no un
total. Un recuento cuadra igual si una baja y otra sube.

---

## 7. Regresión de §9 del encargo

| exigido | medido |
|---|---|
| Las 21 históricas siguen **sin** `tipo_acceso` | ✅ 0 valores en dev |
| Las 21 siguen **sin poder publicarse** | ✅ 21 bloqueadas, causa única |
| Un `LOCAL` nuevo **sí puede registrarse** | ✅ escenario 1, en test permanente |
| **Ninguna otra clave** cambia `ALT`/`PUB`/`OPC` | ✅ guarda 2.5, fila a fila |
| **Ningún otro tipo** de propiedad cambia | ✅ el `UPDATE` acota `tipo_propiedad = 'L'` |
| **`V81` byte por byte intacta** | ✅ `git diff 96a4d65..HEAD -- …V81…` **vacío** |
| `ConservacionDeLaEdicion` sigue verde | ✅ 48/48 en la corrida de cierre |

**Barrido con control positivo** (`rg`, y `grep -iF` no se usó): ninguna prueba
afirmaba «11 `ALT`» ni «exactamente una fila `ALT` nueva» fuera del bloque `DO`
de `V81`, que ya está aplicado y **no se re-ejecuta**. Las aserciones de
`CatalogoQueHablaIntegrationTest` sobre `requerido`/`exigencia` son del espejo
(sigue cierto) o sobre `declaratoria_fabrica` y las seis de `V79` (no tocadas).
`CatalogoProductoresTest.FILAS_MINIMAS = 112` es un **suelo**.

Los *fixtures* de `lib-alta-inmueble.ps1` **se dejaron como estaban**: registrar
con `tipo_acceso` sigue siendo válido y las suites que publican lo necesitan.

---

## 8. La corrida de cierre

**Una sola** con `TEST_DB_URL`, **sin nada más compilando**.

| Paso | Resultado |
|---|---|
| **1.** Requisitos | OK |
| **2.** Gate del modelo universal | **69 en verde, 0 en rojo, 69 total** · `ROLLBACK` |
| **3.** Reactor completo contra PostgreSQL real | **BUILD SUCCESS**, los seis módulos |
| **4.** Los 20 de integración se **ejecutaron** | **20 de 20** |
| **5.** Suites E2E | **5 de 5** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
dominio        Tests run: 720, Failures: 0, Errors: 0, Skipped: 0
persistencia   Tests run:  48, Failures: 0, Errors: 0, Skipped: 0
aplicacion     Tests run: 396, Failures: 0, Errors: 0, Skipped: 0
```

`aplicacion` pasa de 394 a **396**: los dos tests nuevos de §3 y §4.
`Skipped: 0` en los tres demuestra que los de integración **corrieron**.
`ConservacionDeLaEdicionIntegrationTest`: **48/48**, con sus siete casos y su
test de completitud.

**419 comprobaciones `OK` y cero `FALLA`** en las cinco suites. Las tres que
publican un local siguen pasando: sus *fixtures* registran `tipo_acceso`, que
sigue siendo un valor perfectamente escribible.

### Build de producción de Angular

```
Output location: D:\init\ControlLocal\frontend-angular\dist\controllocal-web
NG_BUILD_EXIT=0
```

**Cero ficheros de `frontend-angular/` tocados.**

---

## 9. Lo que esta corrección deja abierto

| | |
|---|---|
| **El aviso perdido: 21 → 0** | §4. Ninguna superficie reporta hoy una `PUB` de la PROPIEDAD. **Corte propio**, y el hueco queda fijado en un test |
| Los 21 locales siguen fuera del mercado | Se desbloquean visitándolos. **7 tienen encargo vivo** y son los urgentes |
| La promoción `OPC → PUB` del resto | Las catorce que propone la auditoría siguen siendo propuesta |
| Que `publicacionGestionable.permitida` diga `true` sobre un encargo que no puede publicar | **Preexistente**, no lo causa `V82`. Anotado para que no se le atribuya |
