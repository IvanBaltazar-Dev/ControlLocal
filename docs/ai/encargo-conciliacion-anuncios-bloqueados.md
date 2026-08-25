# Encargo — Conciliación de los cuatro anuncios vivos sobre propiedades bloqueadas

> **HISTÓRICO — CERRADO.** La conciliación de los cuatro anuncios forma parte
> del cierre definitivo de Corte 4 publicado en `795ffbf`.

**Congelado por CONTROL el 2026-08-24**, por decisión del titular.

**BASE_SHA:** `93154ca` — árbol limpio, dev en `V82`.

**No se abre el Corte 5. No se abre I0.**

---

## 1. Qué hay que resolver

Cuatro publicaciones **`PUBLICADO`** sobre propiedades que **hoy no son
publicables** por faltarles `tipo_acceso` (`PUB` en `L` desde `V82`):

| propiedad | anuncio | encargo | operación | canal | importe |
|---|---|---|---|---|---|
| `LOC-D018` | 4 | 9 | alquiler | FACEBOOK | PEN 3 840 |
| `LOC-D024` | 8 | 5 | alquiler | FACEBOOK | PEN 5 920 |
| `LOC-D027` | 9 | 2 | alquiler | WEB_PROPIA | PEN 6 440 |
| `PROP-0022` | 12 | 608 | **venta** | URBANIA | **USD 315 000** |

**Se resuelve UNA POR UNA. No se acepta una decisión masiva.**

**No se acepta** dejarlas «prioridad de visita» ni «marcadas» mientras sigan
`PUBLICADO` y no publicables.

---

## 2. El procedimiento, por publicación

1. **Identifica la propiedad y su deuda `PUB` exacta** — agregando **todas** las
   claves `ALT`/`PUB` faltantes, **sin filtrar antes por `tipo_acceso`**.
2. **Busca si `tipo_acceso` ya consta de forma explícita y trazable** en
   información legítima que ya exista.
3. **Si existe evidencia inequívoca**: conserva ese hecho **en la autoridad
   actual**, por la vía normal de edición, **sin inferirlo**; y comprueba que la
   publicación vuelve a ser publicable.
4. **Si no existe evidencia suficiente**: **no inventes `tipo_acceso`**. Cierra
   la publicación **por el mecanismo de dominio existente**, conservando
   **encargo, publicación e histórico**.
5. **Sin migración, sin excepción legacy, sin código nuevo.** Si descubres que el
   dominio actual **no permite** hacer correctamente la conciliación,
   **DETENTE Y REPÓRTALO**.

---

## 3. Qué cuenta como evidencia — y qué no

`tipo_acceso` responde a **cómo se entra al local**: a pie de calle, esquina,
galería interior, pasaje comercial, centro comercial, interior de edificio,
mercado.

**Es evidencia** un dato que lo **diga**: `nombre_edificio_galeria` con el nombre
de una galería o centro comercial, un `interior_unidad` que sitúe el local dentro
de algo, una nota de visita que describa el acceso, una foto de fachada, un texto
del propietario que lo declare.

**NO es evidencia, y usarlo sería inventar el dato:**

- **La dirección.** «Av. Pardo 1120» no dice si el local da a la calle o está en
  el tercer piso de un edificio.
- **El distrito o la zona.** Miraflores no implica pie de calle; Lima Cercado no
  implica galería.
- **El rubro o la actividad.** «Agencia bancaria» **no** prueba pie de calle
  —hay agencias dentro de centros comerciales—; «bodega» no prueba nada;
  «academia preuniversitaria» tampoco.
- **El importe o el metraje.**
- **Que sea «lo más frecuente».** Es exactamente la regla que el North Star
  prohíbe.

> **La prueba de fuego:** si para llegar al valor hace falta la palabra
> «normalmente», «suele» o «casi siempre», **no es evidencia**.

### 3.1 Lo que CONTROL ya midió — verifícalo, no lo copies

| | `LOC-D018` | `LOC-D024` | `LOC-D027` | `PROP-0022` |
|---|---|---|---|---|
| `nombre_edificio_galeria` | — | — | — | — |
| `interior_unidad` | — | — | — | — |
| `piso` | — | — | — | — |
| `referencia_interna` | — | — | — | — |
| descripción | «Bodega en Lima Cercado, listo para operar» | «Academia preuniversitaria en Lince…» | «Agencia bancaria en Miraflores…» | **vacía** |
| dirección | Jr. Huallaga 320 | Av. Arequipa 3120 | Av. Pardo 1120 | Av. La Marina 2450 |
| fotos | **0** | **0** | **0** | **0** |
| atributos escritos | `ambientes`, `antiguedad_anios`, `rubro_permitido` | ídem | ídem | **ninguno** |
| título del anuncio | «Publicacion 12» (generado) | «Publicacion 18» | «Publicacion 21» | «Publicacion 3259» |

**Ojo con `LOC-D018`:** Jr. Huallaga 320 está **en la zona de Mesa Redonda**, que
es literalmente el ejemplo con el que se justificó exigir `tipo_acceso` —«40 m² a
pie de calle en Miraflores frente a 40 m² en el interior de una galería de Mesa
Redonda»—. Es el caso donde adivinar sería **más** dañino, no menos.

**Lo que CONTROL no ha mirado y tú sí debes:** interacciones, visitas,
prospecciones, documentos, historial de estado, y cualquier nota libre asociada a
esas cuatro propiedades o a sus encargos. **Busca de verdad antes de concluir que
no hay nada** — con `rg`/SQL y **control positivo**.

---

## 4. El mecanismo de cierre, ya medido

`cambiarEstado:308` sólo llama a `exigirPublicable` **cuando el estado pedido es
`PUBLICADO`**. `PUBLICADO → CERRADO` **pasa libre**, y es asimetría deliberada:
**retirar del mercado nunca puede estar bloqueado porque falte un dato.**

Endpoint: `POST /encargos/{idEncargo}/publicaciones/{idPublicacion}/estado` con
`{"estado":"C"}`.

**No escribe hito `P`**: `registrarImportePublicado:418` retorna sin hacer nada
salvo que el estado sea `PUBLICADO`. Verifícalo, es la clave de la afirmación
«cero hitos `P` artificiales».

**Recuerda el gotcha de PS 5.1**: nunca pases un JSON como argumento a un
ejecutable nativo — las comillas se pierden y la API responde 400. Codifica en
base64 y decodifica dentro del contenedor, como hace `e2e-s0-emergencia.ps1`.

---

## 5. Las cinco afirmaciones que hay que dejar verdes

Medidas **globalmente** al terminar:

1. **0** publicaciones `PUBLICADO` con faltantes `PUB` conocidos.
2. **4/4** anuncios **explicados** — cada uno con su razón escrita.
3. **0** datos inventados.
4. **0** pérdida histórica — encargo, publicación e histórico intactos.
5. **0** hitos `P` artificiales por esta remediación.

El Auditor **intentará refutar las cinco**.

---

## 6. Evidencia

`backend-spring/verificacion/evidencia/2026-08-24-conciliacion-anuncios-bloqueados.md`

Debe llevar, **por publicación**: la deuda `PUB` exacta · **dónde se buscó**
evidencia y qué se encontró · la decisión y su razón · el antes/después del
estado.

Y globalmente: el censo de `publicacion` por estado antes y después, el conteo de
hitos `P` antes y después, y las cinco afirmaciones de §5 con su medición.

---

## 7. Prohibido

Inventar `tipo_acceso` · inferirlo del rubro, la dirección, el distrito o el caso
frecuente · borrar publicaciones o encargos · tocar el histórico de precios ·
migración · excepción legacy · código nuevo · Angular · `V81`/`V82` · Corte 5 ·
I0.

---

## 8. Cierre

Gate `.sql` verde **dentro** de `Verificar-Cierre.ps1` · la suite completa ·
`ng test` y `ng build --configuration production` · evidencia · **commit único**.

**No hagas push.** El push lo hace CONTROL al cerrar formalmente.

## 9. Protocolo

**`STOP — DECISIÓN REQUERIDA POR CONTROL`** si el dominio no permite conciliar
correctamente, o si encuentras evidencia y dudas de si es inequívoca. **Ante la
duda sobre una evidencia, para y pregunta: inventar un dato es el único error que
este proyecto no perdona.**

```
LISTO PARA AUDITORÍA
BASE_SHA=93154ca
CANDIDATE_SHA=<sha>
```
