# Encargo — Corte corto · la deuda de publicación de la PROPIEDAD, visible

> **HISTÓRICO — CERRADO.** Este corte dejó visible la deuda de publicación y
> quedó incluido en el cierre definitivo de Corte 4 publicado en `795ffbf`.

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

## 8. AMPLIACIÓN DEL 2026-08-24 — `permitida` entra al corte

**Decisión del titular.** Lo que §8 declaraba fuera de alcance **entra**: no se
acepta cerrar con una capacidad que se contradice a sí misma.

```
PropiedadResponse.faltanParaPublicar = [tipo_acceso]
publicacionGestionable.permitida     = true          ← incoherente
POST publicar                        = HTTP 400 por tipo_acceso
```

Deben quedar coherentes **en este mismo corte**: lo que falta para publicar → si
publicar está permitido → lo que el comando valida al final.

### 8.1 Cómo se calcula HOY — medido

`PropiedadUniversalServiceImpl.gestionDePublicacion:1443`:

```java
private static GestionDePublicacion gestionDePublicacion(Captacion encargo) {
    if (Captacion.esVivo(encargo.estadoActual())) return new GestionDePublicacion(true, null);
    return new GestionDePublicacion(false, "El encargo " + ... + " ya no esta vigente.");
}
```

**Es `static` y recibe sólo el encargo.** No ve la propiedad ni el catálogo — por
eso dice `true`. No es un descuido: es una capacidad que nació respondiendo a
*«¿el encargo sigue vivo?»* y nunca se amplió.

### 8.2 EL INVENTARIO QUE EL TITULAR EXIGIÓ ANTES DE AUTORIZAR

Todo lo que `PublicacionServiceImpl.crearEnEncargo:85-99` valida, en orden, y si
pertenece o no a `permitida`:

| # | validación | de qué depende | ¿en `permitida`? |
|---|---|---|---|
| 1 | `encargoDelTenant` → 404 | tenant / existencia | **No** — un encargo ajeno no aparece en la ficha |
| 2 | `!Captacion.esVivo(estadoActual())` → «no está vigente» | **estado del encargo** | **Sí — ya está** |
| 3 | `exigirPublicable` · `gobierno.faltantesDePropiedadParaPublicar` (ALT+PUB de PROPIEDAD) | **estado del dato** | **Sí — HUECO** |
| 4 | `exigirPublicable` · `condiciones.faltantesDeEncargoParaPublicar` (ENCARGO) | **estado del dato** | **Sí — HUECO** |
| 5 | `construir:261` · canal obligatorio | **el payload** | **No** |
| 6 | `construir:264` · estado ∈ `Publicacion.ESTADOS` | **el payload** | **No** |
| 7 | `construir:265` · moneda válida | **el payload** | **No** |
| 8 | `registrarImportePublicado:417` | no valida — escribe hito o retorna | **No** |

**Son exactamente dos huecos: el 3 y el 4.** Y **tres validaciones de payload**
—5, 6 y 7— que **no se pueden plegar** dentro de `permitida`, porque dependen de
datos que **no existen cuando se lee la ficha**.

### 8.3 Qué puede prometer `permitida`, y qué no

**Puede** prometer: *no hay impedimento conocido — el encargo está vivo y no
falta ningún dato de catálogo que bloquee la publicación.*

**No puede** prometer que publicar vaya a funcionar: un `POST` con canal vacío,
estado inválido o moneda mala seguirá siendo rechazado, y eso **está bien**.

> El titular lo pidió con estas palabras: *«No convertir `permitida` en una
> promesa más amplia de lo que realmente puede demostrar.»* El inventario dice
> exactamente hasta dónde llega. **Escríbelo en el javadoc del método**, con las
> tres que quedan fuera nombradas.

### 8.4 Cómo se implementa — sin segunda verdad

**`permitida` se deriva de las DOS listas que la ficha YA calcula**, no de una
tercera consulta:

```
permitida = esVivo(encargo)
         && faltanParaPublicar(PROPIEDAD).isEmpty()
         && faltanParaPublicar(ESTE encargo).isEmpty()
```

Esas listas salen de `faltantesDePropiedadParaPublicar` y
`faltantesDeEncargoParaPublicar` — **los mismos métodos que usa
`exigirPublicable`**. Por eso no puede haber dos verdades: es literalmente la
misma salida.

`gestionDePublicacion` **deja de ser `static`** o pasa a recibir las dos listas.
La de la PROPIEDAD es una sola para todos los encargos; la del ENCARGO es de cada
uno.

**Prohibido, y cada uno es rechazo:**

- `if (clave == "tipo_acceso")` en cualquier capa.
- `if (exigencia == "PUB")` en Angular.
- Contar faltantes en el frontend.
- Una segunda consulta con otra interpretación de ALT/PUB.
- Duplicar matrices de claves.

### 8.5 `motivo` debe decir cuál de los tres

Ya existe y hoy sólo lleva «el encargo ya no está vigente». Debe distinguir
**encargo cerrado**, **falta dato de la ficha** y **falta condición del encargo**,
**sin repetir las listas** —que ya viajan— y **sin nombrar claves a mano**: los
rótulos salen del catálogo, como en `exigirPublicable:203-207`.

### 8.6 Las cinco pruebas de la ampliación

| # | escenario | esperado |
|---|---|---|
| **1** | encargo vivo · falta `tipo_acceso` | `faltanParaPublicar` de la **propiedad** lo contiene · `permitida = false` · el comando rechaza **por la misma causa** |
| **2** | propiedad completa · falta una condición ALT/PUB del **encargo** | está en `encargos[].faltanParaPublicar` · `permitida = false` · el comando rechaza |
| **3** | ambos sujetos completos | las dos listas vacías · `permitida = true` salvo impedimento canónico ya existente · publicar continúa |
| **4** | una clave **OPC** ausente | **no** aparece como bloqueante · **no** vuelve `permitida = false` |
| **5** | una clave **ALT** ausente | sigue en `atributosQueFaltan` (bloquea alta) **y** en `faltanParaPublicar` (bloquea publicar). Dos preguntas, **un solo criterio** |

### 8.7 Regresión real sobre las 26

Repetir el censo **después** del cambio. Las mismas **21 `L`** deben: seguir
bloqueadas · mostrar `tipo_acceso` como causa · tener **`permitida = false`** en
la acción sobre su encargo vivo. **Las 5 que pasan no pueden volverse `false` por
accidente.**

### 8.8 Angular — medido, y sí entra

`propiedad-detail.spec.ts:42` monta hoy `publicacionGestionable: { permitida:
true, motivo: null }`. **Mide si la pantalla obedece ese campo** para habilitar
la acción de publicar:

- **Si ya lo obedece**: no añadas lógica de dominio; comprueba con un test que
  refleja el `false` del Core.
- **Si lo ignora o habilita el botón por otra regla** —un `estado === 'A'`, un
  encargo vivo mirado a mano—: **eso entra en este corte**, porque si no, el
  contrato corregido no llega al usuario.

La interfaz **explica la causa con los faltantes que publica el Core**; nunca los
reinterpreta.

### 8.9 Sigue prohibido

No modificar exigencias · no backfill · no `V81`/`V82` · no Corte 5 · no ampliar
a enriquecimiento OPC · **este corte sigue sin migración**.

### 8.10 El cierre se mueve

**Sólo se congela `CANDIDATE_SHA` cuando queden coherentes:**

```
regla de publicación → faltanParaPublicar → permitida → acción visible de Publicar
```

Y el Auditor intentará **demostrar que existe alguna propiedad con
`permitida = true` cuyo comando es rechazado exclusivamente por faltantes ALT/PUB
conocidos**. **Si encuentra una, el corte no cierra.**

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
