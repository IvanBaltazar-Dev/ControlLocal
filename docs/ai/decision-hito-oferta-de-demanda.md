# D-E0-03 · El hito `O`: la oferta del lado de la demanda

**Fecha:** 2026-08-10
**Etapa:** E0 · Histórico económico — decisión funcional cerrada
**Implementación:** E3 (Negociación y economía real). **No se implementa en E0.**

---

## La decisión

> Toda **propuesta monetaria explícita** realizada por el interesado sobre una
> oportunidad se conserva como un hito `O` **append-only**. La registra el agente
> desde la oportunidad —con accesos rápidos desde otros flujos si se requieren—
> y contiene como mínimo: **monto ofertado, moneda, fecha, actor registrador,
> procedencia `DECLARADO` y snapshot del precio pedido aplicable en ese
> instante**. No requiere formalidad documental. Comentarios, presupuestos o
> rangos que no constituyen una propuesta concreta **no** generan `O`. Todas las
> ofertas se conservan; los agregados se derivan.

---

## Las cuatro respuestas

### 1 · Quién la registra

**El agente, desde la oportunidad.** La oferta pertenece a la relación
**interesado ↔ inmueble**, no a la visita: la visita es solo el momento en que a
menudo aparece la cifra.

Más adelante puede haber un atajo "Registrar oferta" al cerrar una visita, pero
**invoca exactamente el mismo caso de uso**. No se crea una segunda forma de
guardar ofertas.

### 2 · Qué cuenta como oferta

Cualquier importe **explícito propuesto por el interesado**. No exige carta,
firma ni LOI — esperar formalidad capturaría tres ofertas al año y el dato no
serviría.

Pero tiene que haber **cifra + moneda** y haber sido planteada **como
propuesta**. La frontera, con ejemplos:

| Es `O` | **No** es `O` |
|---|---|
| "te ofrezco 1.650" | "está caro" |
| "pago 1.500 si incluyes el mantenimiento" | "pagaría menos" |
| "mi propuesta es 1.700" | "mi presupuesto ronda los 1.500" |

Lo de la derecha son **objeciones o señales de demanda** y viajan por su propio
canal (`Visita.objecion_principal`, requerimiento del cliente). Mezclarlas con
`O` contaminaría el precio ofertado con presupuestos y comentarios, y la brecha
de negociación dejaría de ser medible.

**Procedencia: `DECLARADO`**, aunque quien teclee sea el agente. La procedencia
describe el origen de la evidencia —lo dijo el interesado—, no quién la
transcribe. El **actor registrador** se guarda aparte.

### 3 · Se guardan todas; nunca se sobrescribe

La serie queda:

```
U(1900) → U(1850) → P(1850) → O(1450) → O(1550) → O(1650) → C(1700)
```

De ahí se derivan: **mayor oferta de renta**, primera oferta, última oferta,
número de movimientos y distancia hasta el cierre.

> **Vocabulario: "mayor oferta de renta", NUNCA "mejor oferta".**
>
> US$ 1.700 con dos meses de gracia puede ser económicamente **peor** que
> US$ 1.650 sin ella. Mientras E0/E3 capturen solo el monto mensual, "mejor"
> promete una comparación que el modelo no sabe hacer. El paquete económico
> completo (plazo, gracia, garantía, adelanto) vive en el contrato y solo
> entonces podrá compararse de verdad.

### 4 · Se congela el precio pedido

Sí. Aunque pudiera derivarse del histórico con un join temporal, aquí es
**evidencia comercial**: debemos poder afirmar años después *"el interesado
ofreció 1.650 cuando se le estaba pidiendo 1.900"* sin depender de que el
histórico no se haya corregido entretanto.

---

## Regla adicional: `O` es solo lado-demanda

**Una contraoferta del propietario NO se registra como otro `O`.** La evolución
de la postura económica del propietario ya vive en los hitos del lado de oferta
(`U`, y `P` si la cambia en el anuncio).

Cuando se modele la negociación completa se podrán **relacionar** ambas
posiciones; hoy no se **mezclan**. Sin esta regla, la serie `O` dejaría de ser
"lo que el mercado está dispuesto a pagar" y pasaría a ser un revuelto de dos
mesas distintas.

---

## Dos cuestiones abiertas, a resolver ANTES de que E3 escriba la primera fila

Ninguna cambia la decisión funcional; las dos cambian el dato que se persiste, y
por eso no pueden descubrirse después.

### A · ¿Contra qué precio pedido se congela el snapshot?

Existen dos candidatos y **no son lo mismo**:

- **`U`** — lo que el propietario autoriza, en privado;
- **`P`** — lo que el mercado ve.

El interesado ofreció contra **lo que vio**, que es `P`. Congelar `U` registraría
una comparación que ese cliente nunca hizo, y la brecha de negociación mezclaría
dos referencias distintas.

**Propuesta:** el snapshot es el **último `P` vigente**; si el local no está
publicado (oferta directa, sin anuncio), cae al **último `U`** y se deja
constancia de **cuál de los dos** se usó.

### B · `O` necesita la referencia a la oportunidad

`PrecioPropiedad` cuelga de **propiedad**. Con dos interesados en el mismo local
—normal en comercial— la serie interleava dos negociaciones distintas y **"mayor
oferta de renta" deja de significar nada**: sería el máximo entre personas que no
compiten en la misma mesa.

Es la misma forma que la limitación de `P` por canal, con una diferencia que la
agrava: dos portales suelen mostrar el mismo precio; **dos interesados casi nunca
ofrecen lo mismo**.

**Propuesta:** `O` no puede vivir en `PrecioPropiedad` sin discriminar la
oportunidad. Las opciones son una columna `id_oportunidad` anulable en
`precio_propiedad` —anulable porque `U`/`P`/`C` no la tienen— o una tabla propia
de ofertas. Se decide en E3, pero **antes de la primera escritura**.

### C · Menor: dónde consta lo declarado

`DECLARADO` no distingue entre *"el agente transcribe lo que le dijeron por
teléfono"* y *"KAIROS conserva el mensaje donde el cliente escribió 1.650"*. Los
dos son declarados; solo uno tiene texto original.

Para `INFERIDO` ya se guarda **quién infirió**; el análogo para `DECLARADO` es
**dónde consta**. No hace falta resolverlo ahora, pero conviene que E6 no lo
tenga que re-litigar.

---

## Estado de E0

| | | |
|---|---|---|
| 0.1 | `U` inicial + backfill (16 rescatados) | CERRADO |
| 0.2 | hito `P` de renta publicada | CERRADO |
| 0.3 | esta decisión | **CERRADO** |

**E0 cerrado.** Sigue E1 · instrumentación y umbrales.
