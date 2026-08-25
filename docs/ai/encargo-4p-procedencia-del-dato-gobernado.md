# Encargo — Microcorte **4.P · Procedencia granular del dato gobernado**

**Congelado por CONTROL el 2026-08-25**, por decisión del titular.

**BASE_SHA:** `6196aad6419188b79c0039c07ea1d03fa8b6e227` — que **deja de ser
`SHA_FINAL_CORTE_4`** y pasa a **`CANDIDATO_PRE_PROCEDENCIA`**. El cierre
definitivo de Corte 4 **queda cancelado** hasta que este microcorte cierre.

**No se abre el Corte 5. No se abre I0.**

---

## 1. El problema, en una línea

Hoy la procedencia se registra **del acto**, no **del dato**. Un solo `PUT` puede
cambiar `tipo_acceso` (visita), `zonificacion` (certificado) y `vigilancia` (lo
dijo el propietario): **un evento del `PUT` no explica las tres.**

Y **una edición o un borrado destruyen el pasado**, lo que contradice la tesis de
BROX de conservar historia y no sólo estado.

---

## 2. PREFLIGHT DE CONTROL — medido, sólo lectura. **Verifícalo, no lo copies.**

### 2.1 La dimensión «canal» YA EXISTE, y es rica

`service/soporte/Procedencia.java` (**V59**) ya separa exactamente lo que el
titular pide separar:

```java
record Procedencia(String canal, String agente, String modelo, String modeloVersion,
                   String conversacionId, String turnoId, String mensajeId,
                   String peticion, String herramienta)
```

- `canal` ∈ **SPA · WHATSAPP · API · SISTEMA** (validado; «UI» se traduce a SPA).
- `agente == null` **es información**: la persona lo pidió ella misma.
- **`deAgente()` ya EXIGE** agente + conversación + turno, y lanza si faltan:
  *«sin eso el hecho queda escrito sin poder responder quién lo decidió»*.
- Su javadoc ya dice la tesis del titular con otras palabras: **«un asistente no
  es un canal»**.

`evento_dominio` **persiste todo eso**: `canal, agente, agente_modelo,
agente_modelo_version, conversacion_id, turno_id, mensaje_id, peticion,
herramienta, id_persona_rol, ocurrido_en`.

> **Conclusión: la mitad «canal/origen» del contrato del titular NO hay que
> inventarla — hay que ENGANCHARLA al valor.** Y para `INFERIDO`, «quién, regla,
> modelo y versión» **ya viajan**; lo único que no existe es **`confianza`**.

### 2.2 La dimensión «naturaleza» NO EXISTE

`DECLARADO` / `OBSERVADO` / `INFERIDO` **no aparece en ninguna parte** del
backend. Es la mitad que falta, y es la que el usuario a veces tendrá que aportar.

### 2.3 La granularidad es de OPERACIÓN, no de valor

`PropiedadUniversalServiceImpl.anotarEvento:1925` sella la procedencia sobre un
`EventoDominio` con una carga útil que, para la edición, es **`{"idPropiedad":N}`**.
No dice qué clave cambió, ni su valor, ni de dónde salió.

### 2.4 **El sujeto ENCARGO está PEOR, y más de lo que se suponía**

Tipos de evento que existen hoy en `dev`:

```
PROPIEDAD_REGISTRADA · PROPIEDAD_EDITADA · ENCARGO_ABIERTO
```

**No hay ningún evento para la escritura de una condición del ENCARGO.** Pactar
una condición **no deja ni el rastro de operación** que sí deja editar una
propiedad. La asimetría que el titular teme **ya existe**.

Además: `atributo_encargo` tiene **0 filas** en `dev` — el camino está sin
ejercitar contra datos reales, así que **el gate no puede apoyarse en la cartera**
y tendrá que construir el caso.

### 2.5 El borrado es FÍSICO, y el multivalor se reescribe entero

- `AtributosGobernados.retirar:404` → `valores.deleteByIdPropiedadAndClave(...)`
- `AtributosDeEncargo.retirar:238` → `valores.deleteByIdCaptacionAndClave(...)`
- Multivalor: `multivalores.borrarDe(ancla)` y luego un `save` por opción
  (`PropiedadUniversalServiceImpl:845-849`, y `:602-606` para el encargo).

**Un borrado no deja nada. Un cambio de `LISTA_MULTIPLE` destruye el conjunto
anterior sin rastro.** Ésta es la prueba dura del microcorte.

### 2.6 Lo que ya está hilado, y ahorra trabajo

`MotorDeCapturaImpl` **ya recibe y propaga `Procedencia`** (`:89`, `:370`, `:759`
`procedenciaDelAlta`), y `ProcedenciaDeCabeceras` la construye desde las
cabeceras HTTP. **El camino de captura está listo**; lo que falta es que la
procedencia **llegue al valor**, no que se invente su transporte.

### 2.7 Estado de los datos

`atributo_propiedad` **76** filas en `dev` · `atributo_encargo` **0** ·
multivalores en `atributo_propiedad_opcion` / `atributo_encargo_opcion` ·
`historial_estado` existe y es append-only **pero su vocabulario de
`entidad_tipo` no incluye atributos**.

### 2.8 Lo que este preflight NO agotó — te toca

Migraciones que escriban atributos · *seeds* · cualquier `@Modifying` sobre las
cuatro tablas · el camino de importación si existe · y **el inventario completo de
productores**, incluyendo los que no pasen por `AtributosGobernados` /
`AtributosDeEncargo`. **Barre con `rg` o control positivo.**

---

## 3. Semántica congelada — dos dimensiones, no una

**No se mezclan.** Son ejes independientes:

| eje | valores | quién lo sabe |
|---|---|---|
| **naturaleza** | `DECLARADO` · `OBSERVADO` · `INFERIDO` | a veces sólo el consumidor |
| **canal/origen** | `SPA` · `WHATSAPP` · `API` · `SISTEMA` (+ agente/modelo/versión) | **el Core, siempre** |

```
naturaleza=DECLARADO · canal=KAIROS   → alguien lo declaró y KAIROS lo capturó
naturaleza=OBSERVADO · canal=SPA      → un profesional afirma haberlo visto
naturaleza=INFERIDO  · canal=KAIROS · modelo=vision-brox-v3 · confianza=0.81
```

**Un `INFERIDO` no se convierte en hecho confirmado en silencio.**

### 3.1 El contrato conceptual mínimo

```
VALOR GOBERNADO
├── sujeto PROPIEDAD|ENCARGO · agregado · clave · valor/valores
├── naturaleza · canal/origen · actor · rol · registradoEn
├── observadoEn      si se conoce
├── evidenciaRef     si existe
├── inferidoPor · reglaModelo · version   sólo INFERIDO
└── confianza        cuando aplique
```

**Esto NO obliga a que sean columnas de `atributo_propiedad`.** La forma la
decides **después** del inventario.

### 3.2 La preferencia arquitectónica del titular

```
atributo_propiedad / atributo_encargo   =  ESTADO VIGENTE
rastro de procedencia gobernada         =  APPEND-ONLY
```

para que **una edición o un borrado no destruyan el pasado**. Si tu inventario
concluye otra forma, **arguméntala y pregunta antes de implementarla**.

### 3.3 El legado NO se rellena

Un dato antiguo sin fuente demostrable **no es** `DECLARADO`, **ni** `OBSERVADO`,
**ni** `INFERIDO`. **No elijas uno para llenar la columna.**

> «Desconocida por legado» **no es una cuarta clase de evidencia**: significa que
> no podemos clasificar honestamente aquella escritura histórica.

Cómo se representa, lo decide el diseño. Qué significa, está congelado aquí.

### 3.4 Fricción mínima — el Core deriva lo que ya sabe

**Prohibido** añadir al formulario un bloque de procedencia por campo. El Core
deriva **actor, rol, canal, timestamp, organización** por su cuenta; el consumidor
sólo aporta **lo que el Core no puede saber** —típicamente la diferencia entre
*«me lo dijo el propietario»* y *«lo observé»*—, y **sólo cuando haga falta**.
KAIROS lo obtiene del contexto conversacional.

### 3.5 Una sola semántica para Web y KAIROS

El contrato lógico —`AtributoRequest` con la procedencia relevante,
`AtributoFicha` con la procedencia actual— **se decide midiendo el cable
existente primero**. Lo que no se acepta: **un vocabulario para Web y otro para
KAIROS.**

---

## 4. Prueba de aceptación: `LOC-D001` y `LOC-0002`

Los dos valores rescatados **no pueden quedar como filas sin genealogía**. Debe
quedar registrado que salieron de una **transcripción de dato existente**, con la
`descripcion` histórica como fuente.

**Y aquí no se miente:** no sabemos quién originó esa descripción. Así que
**no se les atribuye `DECLARADO`** si el sistema no puede demostrar que hubo una
declaración de contraparte. **Se conserva lo demostrable y se declara desconocido
lo que no lo sea.**

---

## 5. `evento_dominio` NO se sustituye

Sigue siendo el **outbox** y sigue enlazando la operación. **No se convierte en un
sustituto ambiguo del linaje por atributo.** Son dos preguntas distintas:

```
evento_dominio        → quién y cómo ocurrió una operación
procedencia granular  → de dónde salió ESTA afirmación concreta
```

---

## 6. Las 12 invariantes que el Auditor intentará refutar

1. Toda escritura gobernada nueva deja **procedencia granular**, en **PROPIEDAD y
   ENCARGO**.
2. La procedencia se registra **en la misma transacción** que el valor.
3. **Editar** un valor **no destruye** la procedencia ni la historia anterior.
4. **Borrar** un atributo **no destruye** su historia.
5. `INFERIDO` **no puede existir** sin quién/regla/modelo y versión; **confianza**
   cuando corresponda.
6. Un dato **legado** sin procedencia demostrable **no recibe procedencia
   inventada**.
7. `LOC-D001` y `LOC-0002` quedan con **la fuente demostrable**, **sin inventar**
   quién originó el texto.
8. **Web y KAIROS consumen la misma semántica** del Core.
9. Lo que el Core ya sabe (**actor, canal**) **no lo teclea el usuario**.
10. **Ningún valor actual se pierde ni cambia** por incorporar procedencia.
11. Los bloqueados **siguen en 19**; **ningún `P` nuevo**; **ninguna publicación
    cambia**.
12. `evento_dominio` **sigue siendo outbox** y no sustituye el linaje por atributo.

Más el **multivalor**: cambiar un `LISTA_MULTIPLE` debe conservar el conjunto
anterior.

---

## 6 bis. DECISIÓN DEL TITULAR (2026-08-25) · quién declara `naturaleza` en el SPA

> **Opcional por valor. Ni obligatorio, ni uno para todo el guardado.**

El SPA deja `naturaleza` **sin declarar por defecto** —`NULL` honesto— y permite
**marcarla en los valores donde el profesional quiera**. Cada valor lleva **su**
naturaleza o ninguna.

**Se descartó «un control por operación»** —una sola pregunta al guardar, aplicada
a todos los valores— y la razón es la que abrió este microcorte: un mismo `PUT`
puede cambiar `tipo_acceso` (visita), `zonificacion` (certificado) y `vigilancia`
(lo dijo el propietario). **Una respuesta única estamparía una naturaleza falsa en
dos de los tres**, que es exactamente lo que 4.P viene a impedir.

**Se descartó «nada en el SPA»** porque dejaba el eje vacío en la práctica y
repetía el patrón que el titular ya rechazó: *backend correcto, usuario ciego*.

**Consecuencias que esto fija:**

- **Angular SÍ se toca** en este microcorte: un control **discreto y opcional por
  fila**, nunca un bloque de procedencia por campo (§3.4 sigue vigente).
- **La UI habla llano**: «lo observé» / «me lo dijeron», no `OBSERVADO` /
  `DECLARADO`. El vocabulario del enum viaja por el cable, **no se le enseña al
  usuario**.
- **No marcar es la opción por defecto y no cuesta nada.** Un guardado sin marcar
  ninguna es válido y no debe pedir confirmación.

## 6 ter. REGLAS CONGELADAS POR EL TITULAR (2026-08-25)

Estas diez no se reinterpretan en el diseño. Se cumplen o se devuelve `STOP`.

1. **`naturaleza` pertenece a CADA VALOR gobernado, nunca a toda la operación.**
2. Valores admitidos conceptualmente: **`DECLARADO` · `OBSERVADO` · `INFERIDO`**.
3. **El Core JAMÁS deduce `naturaleza`** a partir del canal, el actor, el
   *endpoint* ni el tipo de usuario. *(Esto es una prohibición ejecutable: tiene
   que existir un test que falle si alguien la deriva.)*
4. Si el productor **conoce** la naturaleza, **puede enviarla por valor**.
5. Si **no la conoce, queda ausente**. **No se inventa una categoría.**
6. **`INFERIDO` exige agente/regla/modelo, versión y confianza.**
7. **`canal` / `agente` / `conversación` siguen viniendo de `Procedencia`** y son
   **independientes** de `naturaleza`. Dos ejes, nunca uno.
8. **Una misma operación puede escribir valores con naturalezas diferentes.**
   *(Prueba obligatoria, no comentario.)*
9. **El SPA no queda obligado a pedirla para cada campo.**
10. **KAIROS debe poder aportar la misma metadata por valor** cuando el contexto
    conversacional sí la determine — **la misma semántica, no una paralela**.

## 6 quater. PARADA OBLIGATORIA ANTES DE IMPLEMENTAR

**No se escribe una línea** hasta presentar, y que CONTROL apruebe:

**(a) Las TRES alternativas de almacenamiento**, cada una con su **impacto sobre
las 12 invariantes de §6** — una tabla, invariante por invariante, diciendo si la
alternativa la **sostiene**, la **debilita** o la **imposibilita**:

- **A1** · columnas en `atributo_propiedad` / `atributo_encargo`;
- **A2** · reutilizar `historial_estado`;
- **A3** · tabla propia append-only.

Con **medición**, no con preferencia. Si una es inviable, **demuéstralo con el
dato que la hace inviable**, no con un juicio.

**(b) La demostración de que el modelo cubre las CINCO superficies de escritura**,
una por una y con el caso concreto:

| # | superficie | qué hay que demostrar |
|---|---|---|
| 1 | PROPIEDAD escalar (alta y edición) | el valor y su naturaleza quedan, y **editar no pisa el anterior** |
| 2 | PROPIEDAD **multivalor** (`LISTA_MULTIPLE`) | se conserva **el conjunto anterior entero**, no la diferencia |
| 3 | PROPIEDAD **retirada** (borrado) | queda **quién, cuándo y qué valor se quitó**, con la fila vigente ausente |
| 4 | ENCARGO (escalar, multivalor y retirada) | **simétrico**, y desde el primer commit |
| 5 | **`ESTRUCTURAL` — campo fijo** (`metraje_total`, `piso`, `partida_registral`, `oficina_registral`) | **no crean fila** en `atributo_propiedad`: hay que demostrar que su linaje **igualmente queda** |

## 7. Prohibido

Empezar añadiendo una columna antes del inventario · resolver **sólo**
`atributo_propiedad` · inventar procedencia para el legado · convertir `INFERIDO`
en hecho · UI pesada por campo · vocabularios distintos para Web y KAIROS ·
tocar `V81`/`V82` · abrir Corte 5 o I0.

---

## 8. Cómo se trabaja

1. **Inventario exhaustivo, sólo lectura**, y me lo devuelves **antes de diseñar**.
2. **Propuesta de modelo** con su porqué. **Espera mi visto bueno.**
3. Implementación con migración si el diseño la exige (**la siguiente libre es
   `V83`** — confírmalo).
4. Corrida de cierre completa, incluida Angular si se toca el cable.

**`STOP — DECISIÓN REQUERIDA POR CONTROL`** en cuanto el inventario contradiga
algo de §2, o cuando el diseño tenga dos salidas razonables.

```
INVENTARIO LISTO   (paso 1)
PROPUESTA LISTA    (paso 2)
LISTO PARA AUDITORÍA · BASE_SHA=6196aad · CANDIDATE_SHA=<sha>
```
