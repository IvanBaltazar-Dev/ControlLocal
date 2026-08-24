# Evidencia — Corte corto · la deuda de publicación de la PROPIEDAD, visible

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `9348ae9` (encargo congelado en `f5194f4`, ampliado en `a1d3f21`; ambos sólo documentación)
**Encargo:** `docs/ai/encargo-senal-pub-visible.md`, incluida la **ampliación §8**
**Migración:** **ninguna.** Este corte no toca el esquema.

---

## 1. Preflight — verificado, no copiado

| # | comprobación | medido |
|---|---|---|
| 1 | HEAD `f5194f4`, árbol limpio; `9348ae9..f5194f4` = **sólo docs** | ✅ |
| 2 | Flyway en dev | **82 · `success = t`** |
| 3 | baseline: `b57bd8e..9348ae9` = **sólo docs** | ✅ |
| 4 | censo bloqueado | **21 de 26** |
| 5 | **todas** las claves ALT/PUB faltantes, agregadas **sin filtrar por ninguna** | **`tipo_acceso(PUB)`** y nada más |
| 6 | causa única | **1 clave distinta**, y las 21 son todas `L` |
| 7 | publicables | **5 de 26** |

**No hubo `STOP`.**

---

## 2. Lo que se añadió — y de dónde sale

### 2.1 · `PropiedadResponse.faltanParaPublicar`

`List<AtributoQueFaltaResponse>`, simétrico con `EncargoResponse.faltanParaPublicar`:
**cada sujeto reporta su propia deuda bajo el mismo nombre**, y la ruta
desambigua sin repetir el sujeto en el campo.

Sale de **`gobierno.faltantesDePropiedadParaPublicar(idOrganizacion, propiedad)`,
tal cual, sin filtrar** — el **mismo método** que usa
`PublicacionServiceImpl.exigirPublicable` para decidir el rechazo.

**Devuelve `ALT` y `PUB`, y eso es deliberado.** Son dos preguntas distintas con
dos respuestas verdaderas:

| pregunta | campo | qué lleva |
|---|---|---|
| ¿qué impide el **alta**? | `atributosQueFaltan` | `ALT` |
| ¿qué impide **publicar**? | `faltanParaPublicar` | `ALT` **y** `PUB` |

Una clave `ALT` ausente sale en las dos porque bloquea las dos cosas. **Filtrar la
nueva a sólo-`PUB`** crearía un segundo criterio de publicabilidad —uno que decide
y otro que cuenta— y además **mentiría**: diría que sólo falta X para publicar
cuando también falta Y.

### 2.2 · `publicacionGestionable.permitida` deja de contradecirse

Era `static` y sólo recibía el encargo, así que sólo sabía responder «¿está
vivo?». Con `tipo_acceso` en `PUB` desde `V82`, las 21 bloqueadas decían
`permitida = true` y el `POST` devolvía 400.

Ahora se **deriva de las dos listas que la ficha ya calculó**, sin una consulta
más:

```
permitida = esVivo(encargo)
         && faltanParaPublicar(PROPIEDAD).isEmpty()
         && faltanParaPublicar(ESTE encargo).isEmpty()
```

Las listas salen de `faltantesDePropiedadParaPublicar` y
`faltantesDeEncargoParaPublicar`, **los mismos métodos de `exigirPublicable`**.
No puede haber dos verdades: es literalmente la misma salida.

`fichaDeEncargo` recibe la deuda de la PROPIEDAD —una sola para todos sus
encargos— y calcula la del ENCARGO **una vez**, usándola para el campo y para la
capacidad. Pedirla dos veces abriría la puerta a que las dos respuestas se
separaran.

### 2.3 · El inventario, verificado contra el código

Comprobé las ocho validaciones de `crearEnEncargo` una a una antes de escribir:

| # | validación | ¿en `permitida`? |
|---|---|---|
| 1 | `encargoDelTenant` → 404 | No — un encargo ajeno no llega a la ficha |
| 2 | `!Captacion.esVivo(...)` | **Sí, ya estaba** |
| 3 | `faltantesDePropiedadParaPublicar` | **Sí — hueco cerrado** |
| 4 | `faltantesDeEncargoParaPublicar` | **Sí — hueco cerrado** |
| 5 | `construir` · canal obligatorio | No — **payload** |
| 6 | `construir` · estado ∈ `Publicacion.ESTADOS` | No — **payload** |
| 7 | `construir` · moneda válida | No — **payload** |
| 8 | `registrarImportePublicado` | No — no valida, escribe |

**Exactamente dos huecos, 3 y 4**, y **tres validaciones de payload que no se
pliegan**. Está escrito en el javadoc del método, con las tres nombradas:
`permitida` promete *«no hay impedimento conocido»*, **no** que publicar vaya a
funcionar. Un `POST` con canal vacío seguirá dando 400 con `permitida = true`, y
está bien.

### 2.4 · `motivo` dice cuál de los tres, sin repetir las listas

| caso | motivo |
|---|---|
| encargo cerrado | «El encargo CAP-00xx ya no esta vigente.» |
| falta dato de la ficha | «Faltan datos de la ficha del inmueble.» |
| falta condición del encargo | «Faltan condiciones de este encargo.» |
| los dos | «Faltan datos de la ficha del inmueble y condiciones de este encargo.» |

**No repite las listas** —ya viajan, con su rótulo del catálogo— y **no nombra ni
una clave a mano**.

---

## 3. Angular — medido: **ya obedecía**, así que sólo se le añadió la prueba

`bloque-encargo.ts:81`:

```ts
protected readonly gestionable = computed(
  () => this.puedeEditar() && (this.encargo().publicacionGestionable?.permitida ?? false),
);
```

y `motivoNoPublicable` muestra el `motivo` del Core tal cual. **No hay
`estado === 'A'`, ni conteo de faltantes, ni interpretación de `ALT`/`PUB`/`OPC`.**
Así que —según §8.8— no se añadió lógica de dominio: sólo el test que comprueba
que refleja el `false` nuevo.

Lo que **sí** cambió en la plantilla, y es una corrección de verdad: el aviso
«Falta … **para poder publicarla**» leía `atributosQueFaltan`, que sólo lleva
`ALT`. Con `tipo_acceso` en `PUB` esa frase **no aparecía** en ninguna de las 21.
Ahora lee `faltanParaPublicar`, que es la lista de la que habla la frase.

**Cero ficheros `.scss` tocados.**

---

## 4. Las pruebas

### 4.1 · Los cinco pasos de §5 y las cinco de §8.6

En `CatalogoQueHablaIntegrationTest` —fichero que **ya existía**, así que el
inventario de las 20 clases no se toca:

| test | cubre |
|---|---|
| `laPropiedadReportaSuDeudaDePublicacion` | §5 completo: alta permitida · la ficha informa con **rótulo** · publicar rechazado · completar el dato lo quita de la lista · publicar continúa. Y las dos superficies antiguas siguen sin nombrarlo |
| `lasDosListasNoSeMezclan` | §8.6-5 y §8.6-4: una `ALT` ausente sale **en las dos listas**; una `OPC` ausente **en ninguna** |
| `pubNoBloqueaElAlta` | §6: `PUB` no empieza a bloquear el alta |
| `permitidaSigueALaDeudaDeLaPropiedad` | §8.6-1 y §8.6-3: lista → `permitida = false` → comando rechaza **por la misma causa**; y al completar el dato **los tres cambian a la vez** |
| `laOpcAusenteNoBloquea` | §8.6-4: una `OPC` ausente **no** vuelve `permitida = false` |

**37 de 37 en verde** en esa clase.

### 4.2 · Que el guardián muerde — inyección deliberada

Se inyectó **el error exacto que el encargo prohíbe**: filtrar
`faltanParaPublicar` a sólo-`PUB`, quitando las que ya salen en la lista del alta.

```
AssertionFailedError: y tambien impide publicar: omitirla aqui prometeria que
basta con completar las PUB ==> expected: <true> but was: <false>
  CatalogoQueHablaIntegrationTest.lasDosListasNoSeMezclan:1025
```

Inyección revertida; el fichero volvió a su copia previa y se verificó que no
queda rastro.

### 4.3 · Angular

**671 de 671 en verde** (668 antes, +3): el fixture nuevo, el caso de la clave
`PUB` que se avisa aunque no esté entre las del alta, el caso inverso —sin
faltantes de publicación no se inventa aviso aunque el alta tenga pendientes— y
el del encargo vivo con la ficha incompleta que **tampoco ofrece el botón**.

---

## 5. La regresión real sobre las 26 — y el ataque del Auditor

Medido contra la **API viva**, leyendo las 26 fichas y agregando **todas** las
claves `ALT`/`PUB` faltantes, **sin filtrar por ninguna**:

```
propiedades                 : 26
bloqueadas (lista no vacia) : 21
publicables                 : 5
causas distintas            : tipo_acceso   (n=1)
encargos vivos examinados   : 13
  con permitida = false     : 8
  con permitida = true      : 5

*** permitida = true con faltantes ALT/PUB conocidos ***
    NINGUNA — 0 casos
```

| | ANTES | DESPUÉS |
|---|---|---|
| bloqueadas para publicar | **21** | **21** |
| **con causa visible** | **0** | **21** |
| publicables | **5 / 26** | **5 / 26** |
| encargos vivos con `permitida = false` | **0** | **8** |

Los **8** son 7 propiedades: `PROP-0022` aparece dos veces porque tiene **dos**
encargos vivos (alquiler y venta), y los dos quedan `false`. Las otras 14
bloqueadas no tienen encargo vivo, así que no exponen capacidad alguna.

**Las 5 publicables no se volvieron `false` por accidente**: `LOC-D014`,
`LOC-D019`, `PROP-0023`, `PROP-0024`, `PROP-0025` conservan `permitida = true`
con las dos listas vacías.

**El objetivo explícito del Auditor —una propiedad con `permitida = true` cuyo
comando sea rechazado exclusivamente por faltantes `ALT`/`PUB` conocidos— no
existe: cero casos.** Y no puede existir por construcción, porque `permitida` se
deriva de las mismas dos listas que `exigirPublicable` consulta para rechazar.

---

## 6. Lo que no se tocó

- **Ninguna exigencia**, ningún vocabulario, ninguna aplicabilidad.
- **`V81` y `V82` intactas**; **este corte no lleva migración**.
- **Cero backfill**: los 21 siguen sin `tipo_acceso` (0 valores en dev).
- Ninguna clave cambia de sujeto: las del ENCARGO siguen en
  `encargos[].faltanParaPublicar`, las de la PROPIEDAD en la suya.
- **Sin fila nueva en `matriz-operacion-rol.md`**: no hay endpoint nuevo.
- El Corte 5 **no se abrió**.

---

## 7. La corrida de cierre

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
aplicacion     Tests run: 400, Failures: 0, Errors: 0, Skipped: 0
```

`aplicacion` pasa de 396 a **400**: los cuatro tests nuevos de §4.1 (uno sustituye
al marcador de deuda de `V82`, que ya no describía la realidad).
`Skipped: 0` en los tres demuestra que los de integración **corrieron**.

**419 comprobaciones `OK` y cero `FALLA`** en las cinco suites.

### Angular

```
ng test                            671 SUCCESS  (668 antes, +3)
ng build --configuration production   NG_BUILD_EXIT=0
```

El *build* de producción se corrió **aunque no se tocó ningún `.scss`**, y **no en
paralelo** con las suites E2E.

---

## 8. La cadena, coherente de punta a punta

```
regla de publicación   exigirPublicable → faltantesDePropiedadParaPublicar
        ↓                                 faltantesDeEncargoParaPublicar
faltanParaPublicar     los MISMOS métodos, sin filtrar
        ↓
permitida              derivada de esas dos listas, no de una tercera consulta
        ↓
acción visible         la pantalla obedece `permitida` y muestra `motivo`
```

Ningún eslabón reinterpreta al anterior. No hay `if (clave == "tipo_acceso")` en
ninguna capa, no hay `if (exigencia == "PUB")` en Angular, no se cuenta nada en el
frontend y no hay una segunda consulta con otra lectura de `ALT`/`PUB`.

---

## 9. Lo que queda abierto

| | |
|---|---|
| Los 21 locales siguen fuera del mercado | Se desbloquean visitándolos; **7 tienen encargo vivo** y son los urgentes |
| Que `permitida` no cubra canal, estado y moneda | **Deliberado y escrito** en su javadoc: dependen del payload, que no existe al leer la ficha |
| La promoción `OPC → PUB` del resto del catálogo | Sigue siendo propuesta de la auditoría |
