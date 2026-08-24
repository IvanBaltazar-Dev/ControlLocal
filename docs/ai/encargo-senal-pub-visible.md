# Encargo — Corte corto · la deuda de publicación de la PROPIEDAD, visible

**Congelado por CONTROL el 2026-08-24**, por decisión del titular tras cerrar
`V82`.

**BASE_SHA:** `9348ae9` — rama `feat/modelo-universal-y-autoridad-del-dato`,
árbol limpio, dev en `V82`.

**El Corte 5 · Terreno NO se abre hasta que esto cierre.**

---

## 1. Qué arregla

BROX **ya bloquea bien**: falta una clave `PUB` de la PROPIEDAD y la publicación
se rechaza. Lo que falta es que **el usuario pueda saber por qué**.

`V82` dejó el caso real medido: **21 locales sin `tipo_acceso`, los 21
bloqueados, 0 con señal visible.** `tipo_acceso` no puede aparecer en
`encargos[].faltanParaPublicar` porque ese campo es del sujeto **ENCARGO** y el
guard 2.5 de `V78` impide que una clave de PROPIEDAD tenga fila en
`catalogo_atributo_operacion`.

**Este corte añade explicabilidad. No relaja ni endurece la regla.**

---

## 2. La separación que hay que conservar — es el contrato del corte

| exigencia faltante | ¿bloquea alta? | ¿bloquea publicar? | dónde se informa |
|---|---|---|---|
| **ALT** | **sí** | sí | `atributosQueFaltan` (**sin tocar**) |
| **PUB** | **no** | **sí** | **superficie nueva de este corte** |
| **OPC** | no | no | **en ninguna** — futura capa de profundidad |

**Prohibido:** reutilizar `atributosQueFaltan` para `PUB`; mover claves `PUB` a
`ALT` para hacerlas visibles; meter claves `OPC`; cambiar cualquier exigencia;
tocar el Corte 5; modificar `V81` o `V82`.

**Este corte no lleva migración.** Si crees que necesita una, **para y devuelve
`STOP`**.

---

## 3. Preflight, ya medido por CONTROL — verifícalo, no lo copies

| # | comprobación | medido |
|---|---|---|
| 1 | HEAD y árbol | `9348ae9`, limpio |
| 2 | Flyway en dev | **82**, `success = t` |
| 3 | baseline | gate 69/69 y suite verde en `b57bd8e`; desde ahí **sólo cambiaron documentos** — reconfírmalo |
| 4 | censo bloqueado | **21 de 26** |
| 5 | **todas** las claves ALT/PUB faltantes, agregadas **sin filtrar por ninguna clave** | **`tipo_acceso(PUB)`**, y nada más |
| 6 | causa única | **sí**: 1 clave distinta, y las 21 son todas `L` |
| 7 | dónde va | §4 |

Publicables: **5 de 26**, antes y después.

---

## 4. Dónde va, medido en el contrato

**`PublicacionServiceImpl.exigirPublicable:186-196` ya tiene la forma correcta**
y es la prueba de que la solución no inventa nada:

```java
List<String> deLaFicha = gobierno.rotulosDe(..., 
        gobierno.faltantesDePropiedadParaPublicar(actor.idOrganizacion(), propiedad));
List<String> delEncargo = ...condiciones.faltantesDeEncargoParaPublicar(...);
```

y su mensaje ya distingue *«de la ficha del inmueble falta …; y de las
condiciones de este encargo falta …»*.

**El read model sólo tiene que reflejar esa misma partición.**

### 4.1 El nombre — decidido por CONTROL tras medir el contrato

> **`PropiedadResponse.faltanParaPublicar`**

Simétrico con `EncargoResponse.faltanParaPublicar` (`PropiedadUniversalDtos:280`):
**cada sujeto reporta su propia deuda, bajo el mismo nombre.** Eso *es* la
separación de sujetos hecha visible, y por la ruta nunca hay ambigüedad —
`propiedad.faltanParaPublicar` frente a `propiedad.encargos[i].faltanParaPublicar`.

**Se descartó `faltanParaPublicarPropiedad`** (que el titular ofrecía como
ejemplo) porque el contrato **no repite el nombre del sujeto en el campo**:
`EncargoResponse` no lo llama `faltanParaPublicarEncargo`.

**Se descartó `atributosQueFaltanParaPublicar`**: se parece demasiado a
`atributosQueFaltan`, con el que va a convivir **en el mismo DTO**, y ahí es
justo donde el titular prohíbe la mezcla. Dos nombres parecidos en campos
adyacentes invitan al error que este corte viene a evitar.

Tipo: `List<AtributoQueFaltaResponse>` — el mismo que ya usan los otros dos, con
`clave` y `rotulo`.

### 4.2 De dónde sale la lista — y esto no es negociable

**De `gobierno.faltantesDePropiedadParaPublicar(idOrganizacion, propiedad)`, tal
cual, sin filtrar.** Es el **mismo método** que usa `exigirPublicable`. El
titular lo exigió con esas palabras: *la lista visible debe salir del mismo
criterio de dominio que decide publicabilidad, no de una segunda matriz escrita a
mano.*

**Consecuencia que hay que entender antes de escribir código:** ese método
devuelve **ALT y PUB** (y también las ESTRUCTURAL cuyo campo canónico está
vacío). Eso es **correcto y deliberado**:

- `atributosQueFaltan` responde **«¿qué impide el alta?»** → ALT.
- `faltanParaPublicar` responde **«¿qué impide publicar?»** → ALT + PUB.

Una clave ALT ausente aparece **en las dos**, porque bloquea las dos cosas. **Eso
no es mezclar: son dos preguntas distintas con dos respuestas verdaderas.**
Filtrar la nueva lista a sólo-PUB **crearía el segundo criterio** que el titular
prohíbe, y además mentiría: diría que sólo falta X para publicar cuando también
falta Y.

**No filtres. No inventes. Usa el método del dominio.**

### 4.3 Angular

**Aquí sí se toca**, y es lo único de este corte que lo hace. **Angular
representa; no reconstruye la regla**: lee el campo nuevo y lo muestra. Cero
lógica de exigencias en el SPA, cero listas de claves escritas a mano, cero
interpretación de `ALT`/`PUB`/`OPC`.

Recuerda el gotcha del repositorio: **`ng test` no comprueba los presupuestos de
producción**. Si tocas un `.scss`, corre también
`ng build --configuration production`, y no lo hagas mientras corre una suite E2E.

---

## 5. Prueba obligatoria

Sobre un `LOCAL` registrado **sin** `tipo_acceso`:

| # | acción | esperado |
|---|---|---|
| 1 | alta | **permitida** |
| 2 | ficha | **informa que falta `tipo_acceso` para publicación**, con su rótulo |
| 3 | intento de publicación | **rechazado** |
| 4 | completar `tipo_acceso` | **ese faltante desaparece** de la lista |
| 5 | publicación | **deja de estar bloqueada por esa clave** |

---

## 6. Pruebas de no mezcla — todas explícitas

- **ALT sigue bloqueando el alta.**
- **PUB no empieza a bloquear el alta** — registrar un `LOCAL` sin `tipo_acceso`
  sigue funcionando.
- **OPC no aparece** como faltante en ninguna de las dos listas.
- Las claves del **ENCARGO** permanecen en `encargos[].faltanParaPublicar`.
- Las claves de la **PROPIEDAD** permanecen en la superficie de PROPIEDAD.
- **Ninguna clave cambia de sujeto. Ninguna exigencia cambia.**
- **Los 21 siguen sin `tipo_acceso`: cero backfill.**

Y una que se deduce de §4.2 y conviene fijar: **una clave ALT ausente aparece en
las dos listas**, y eso es lo correcto.

---

## 7. Evidencia — hay que demostrar el antes y el después

```
ANTES      21 bloqueadas para publicar · 0 con señal visible
DESPUÉS    21 bloqueadas para publicar · 21 con causa visible (tipo_acceso)
PUBLICABILIDAD   5/26 antes  ·  5/26 después
```

La causa se mide **agregando todas las claves ALT/PUB faltantes, sin filtrar
antes por `tipo_acceso`**. Filtrar por la clave y luego «descubrir» que la causa
es esa clave no demuestra nada.

---

## 8. Observación medida, y **fuera de alcance**

`publicacionGestionable.permitida` vale **`true`** en las 21 bloqueadas: sólo
mira si el encargo está vivo. Es **preexistente** —el Auditor ya lo dejó dicho en
`V82`, para que no se le atribuyera a esa migración— y **no se toca aquí**.

Se anota porque tiene efecto en pantalla: si el SPA habilita el botón con ese
campo, seguirá habilitado y seguirá dando 400. **Que la lista nueva se muestre
igualmente**, con independencia de `permitida`. Si el titular quiere que el botón
refleje el bloqueo, es **decisión suya y corte propio**.

---

## 9. Alcance documental autorizado

1. `docs/ai/mapa-ejecucion-brox.md` — el corte y su cierre.
2. `docs/ai/pendientes-brox.md` — §2.5 ter: la deuda «el bloqueo es real y nadie
   lo anuncia» **queda saldada**; §2.5 quater sigue abierta (es otra cosa).
3. Evidencia: `backend-spring/verificacion/evidencia/2026-08-24-senal-pub-visible.md`.

**Sin fila nueva en `matriz-operacion-rol.md`**: no hay endpoint nuevo. Si
acabaras necesitando uno, **para y pregunta**.

---

## 10. Cierre

Gate `.sql` en verde **dentro** de `Verificar-Cierre.ps1` · los cinco pasos de §5
· las siete no-mezclas de §6 · el antes/después de §7 · **5/26 sin moverse** ·
una sola corrida de cierre con `TEST_DB_URL` · `ng test` **y**
`ng build --configuration production` · **commit único** · sin push.

## 11. Protocolo

`STOP — DECISIÓN REQUERIDA POR CONTROL` si el preflight contradice el encargo,
antes de tocar un archivo.

```
LISTO PARA AUDITORÍA
BASE_SHA=9348ae9
CANDIDATE_SHA=<sha>
```
