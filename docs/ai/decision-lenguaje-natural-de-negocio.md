# D-E1-01 · BROX habla como habla una inmobiliaria peruana

**Fecha:** 2026-08-10
**Etapa:** E1 · Instrumentación y políticas
**Alcance:** transversal y permanente. Aplica a todo texto que vea un usuario,
desde hoy en adelante.

---

## La decisión

> **Todo lo que el usuario lee está escrito en el idioma de una operación
> inmobiliaria real: natural, profesional, claro y del rubro.** El modelo interno
> puede —y debe— seguir siendo preciso; lo que no puede es asomarse a la
> pantalla. Dominio exacto por dentro, lenguaje natural por fuera.

No es una capa de traducción ni una tabla de sustituciones. Es escribir la frase
como la diría un agente, un broker o un administrador de una corredora en Perú.

---

## 1 · Lo que no aparece frente al usuario

Ni lenguaje técnico, ni nombres de columnas, ni nombres de enums, ni términos de
arquitectura, ni traducciones literales de conceptos de software, ni jerga
jurídica cuando no hace falta, ni expresiones que nadie del rubro usaría
trabajando.

Ejemplos concretos, con lo que se dice en su lugar **según el contexto** —no como
reemplazo mecánico:

| No se escribe | Se escribe, cuando eso es lo que pasa |
|---|---|
| período de gracia | meses sin pago de renta |
| snapshot del precio | precio que se estaba ofreciendo |
| hito económico | historial de precios |
| entidad en riesgo | necesita atención |
| severidad crítica | prioridad alta |
| estado terminal | negociación cerrada |
| lado demanda | oferta del interesado |
| procedencia declarada | informado por el interesado |
| cadencia de recontacto | volver a contactar |

Tampoco se cae al otro lado: **no es lenguaje informal**. "Ya aprobadas, falta
firmar" es del rubro; "ojo con estos" no.

## 2 · Lo que el dominio conserva por dentro

Esta regla **no empobrece el modelo**. Dentro del código siguen existiendo, y
deben existir, nombres exactos:

```
NivelAtencion.ALTO        PoliticaComercial.RECONTACTO
Concepto.RECONTACTO_VENCIDO        PrecioReferenciaTipo.PUBLICADO
```

Son claves, no rótulos. La frontera es la pantalla: `nivelAtencion: "ALTO"` viaja
por el cable como dato, y la interfaz lo pinta en rojo con la palabra que
corresponda al concepto que esté mostrando. **El backend no emite texto para
mostrar**; el que ya emite (`ambito`) es herencia de la v1, no un patrón a
extender.

## 3 · Dinero y negociación: no concluir lo que no se puede demostrar

> **"Mayor oferta de renta", nunca "mejor oferta".**

US$ 1.700 con dos meses sin pago puede ser económicamente **peor** que US$ 1.650
sin ellos. Mientras el sistema capture solo el monto mensual, "mejor" promete una
comparación que el modelo no sabe hacer. El paquete económico completo —plazo,
gracia, garantía, adelanto— vive en el contrato, y solo entonces podrá compararse
de verdad (ver `decision-hito-oferta-de-demanda.md`).

La disciplina, de aquí en adelante, para **precio, renta, comisión, condiciones
ofrecidas, negociación, cierre, rentabilidad, oportunidad y prioridad**:

1. Si el sistema conoce solo un importe → **muestra el importe**.
2. Si conoce una comparación objetiva → **puede compararlos**.
3. Si no conoce todas las condiciones económicas → **no dice cuál es "mejor"**.

Vale también para superlativos de conveniencia: "la mejor propiedad", "el cliente
más rentable", "la oportunidad más prometedora". Si detrás hay un solo campo
ordenado, se dice por qué campo está ordenado.

## 4 · Qué se hizo en E1

El barrido de esta tanda cubre **los textos que E1 tocó**: el tablero y —porque
muestra exactamente los mismos números— el panel de seguimiento de la pantalla
de reportes. Dejar el mismo dato con dos nombres en dos pantallas hace dudar de
si mide lo mismo, así que aquí el barrido no era opcional.

De paso cayó un texto que ya era **falso**: la nota del panel de reportes seguía
prometiendo que "si el periodo no tuvo prospecciones, la tasa se calcula sobre
todas las del alcance". Ese fallback se retiró el 2026-08-08; la nota llevaba dos
días describiendo un comportamiento que el backend ya no tiene.

| Antes | Ahora |
|---|---|
| Recontactos vencidos · "riesgo de enfriamiento" | Prospectos sin contactar a tiempo · "se enfrían si nadie los llama" |
| Días sin seguimiento · "promedio de lo vencido" | Atraso promedio · "días desde que debiste llamarlos" |
| Aprobadas sin cierre · "ingreso comprometido" | Aprobadas sin contrato · "ya aprobadas, falta firmar" |
| Prospección → captación · "disciplina de captación" | Prospectos captados · "de los trabajados en el periodo" |
| Seguimiento en riesgo | Prospectos sin contactar a tiempo |
| Cierres sin formalizar | Aprobadas sin contrato |
| Cierres registrados · "Alquileres formalizados" | Alquileres firmados · "Contratos cerrados y su comisión" |
| Cobertura de agentes | Agentes en operación · "Quién supervisa a quién" |
| Riesgo operativo / Disciplina comercial | Lo que necesita atención / Cómo va tu seguimiento |
| Disciplina operativa (reportes) | Cómo va el seguimiento |
| Días promedio sin seguimiento | Atraso promedio (días) |
| Recontactos al día | Prospectos contactados a tiempo |
| "Captaciones esperando tu decisión" | "No se pueden ofrecer hasta que las apruebes" |
| "Explica por qué se mueve este expediente" | "Explica por qué esta captación cambia de agente" |

El patrón que se repite: donde había una etiqueta abstracta, ahora hay **la
consecuencia**. "No se pueden ofrecer hasta que las apruebes" dice por qué
importa; "esperando tu decisión" no.

## 5 · Lo que queda por barrer

El barrido es **por módulo**, igual que el resto del descongelado: se limpia la
pantalla que se toca, no el sistema entero de una vez. Lo detectado y **no**
corregido en E1, para que no se re-descubra:

- `captacion-review.html`, `captacion-detail.html` — "Trazabilidad" como título
  de sección y dentro de un diálogo de confirmación.
- `interaccion-form.html`, `interaccion-detail.html` — "la entidad que le da
  sentido", "la interacción cuelga de una sola entidad". Es vocabulario de modelo
  de datos en mitad de un formulario.
- Campana de alertas — el eje `severidad` (`ALTA`/`MEDIA`/`BAJA`) llega del cable
  y se muestra casi tal cual.

Ninguno es urgente; todos son deuda de la misma regla.

## 6 · Cómo se sostiene

No hay test que juzgue si una frase suena natural, y no conviene inventarlo: un
diccionario de palabras prohibidas produciría sustituciones mecánicas, que es
exactamente lo que esta decisión evita. Lo que sí hay:

- **La revisión de cada tanda incluye leer los textos nuevos en voz alta.** Si
  suena a documentación técnica, no entra.
- **El backend no emite rótulos**, así que el vocabulario visible está en un solo
  sitio por pantalla y se puede revisar de un vistazo.
- Esta tabla de la §1 crece cuando aparezca un término nuevo que haya que
  desterrar.
