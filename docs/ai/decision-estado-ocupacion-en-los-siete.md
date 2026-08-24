# D-C5-1 · `estado_ocupacion` nace en los siete tipos, y su vocabulario no es el que propuso la auditoría

**Resuelto por CONTROL el 2026-08-24**, después de cerrar `V82` y **antes** de
abrir el Corte 5, por encargo del titular.

**Qué resuelve:** la contradicción entre el hecho `estado_ocupacion` y su
condición `entrega_desocupado`, que habría hecho **fallar la migración del Corte
5 en su propia guarda**.

---

## 1. La contradicción, medida

La auditoría se contradice a sí misma, en dos tablas del mismo documento:

| | dónde | qué dice |
|---|---|---|
| la **condición** `entrega_desocupado` | `auditoria-profundidad-inmobiliaria.md:339` | `aplica_a` = **todos**, nivel PUB, operación **VENTA** |
| el **hecho** `estado_ocupacion` | `auditoria-profundidad-inmobiliaria.md:250` | `aplica_a` = **T, C** |

Y la condición **ya está sembrada** desde `V77`. Medido contra
`controllocal_dev`:

```
entrega_desocupado → catalogo_atributo_operacion:
    A/V · C/V · D/V · L/V · O/V · T/V · X/V     (los siete tipos, venta)
estado_ocupacion → no existe todavía (0 filas)
```

**El guard 2.2 de `V78`** (`:138-163`) recorre los ocho pares deliberados y
prohíbe que un hecho **existente** llegue menos lejos que su condición:

```sql
WHERE h.clave = par.hecho AND h.activo AND NOT h.aplica_todos
  AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t ...)
```

Mientras el hecho no existe, el par **no participa** —«no se le exige cobertura a
algo que no ha nacido»—. **El día que el Corte 5 lo siembre en T y C, el guard
lanza**:

> El hecho "estado_ocupacion" no llega a **A, D, L, O, X** y su condición
> "entrega_desocupado" sí: ahí el pacto sería el único sitio donde cabe el hecho.

---

## 2. Resolución: los siete, con `aplica_todos = false`

**`estado_ocupacion` se siembra en A, C, D, L, O, T y X.**

No es una concesión al guard. Es lo correcto en el dominio: **si un inmueble está
ocupado importa en toda venta**, no sólo en terreno y casa. Un departamento
vendido con inquilino dentro es un producto distinto —una inversión con renta en
curso— y un local ocupado por un tercero sin título es un problema legal que
cambia el precio. La ficha estrecha de la auditoría era el error, no el guard.

**Lo que NO se hace:**

- **No se estrecha `entrega_desocupado` a T y C.** Sería retirar una captura que
  ya existe y está pactada, sin reemplazo — prohibido por el North Star.
- **No se usa `aplica_todos = true`** para esquivar el guard. La convención de
  los cortes es aplicabilidad explícita, y `aplica_todos` además arrastraría al
  tipo `X`, cuya continuidad está en revisión (§2.6 de `pendientes-brox.md`).
  Que `X` reciba la clave es correcto **por el guard** —`entrega_desocupado` se
  pacta en `X/V`—, y de paso le devuelve una pregunta propia al tipo que se
  estaba quedando sin ninguna.

---

## 3. El vocabulario propuesto es de terreno, y no sirve para los siete

Éste es el hallazgo que la contradicción tapaba. La auditoría propone:

```
LIBRE_Y_DESOCUPADO · CON_EDIFICACION_A_DEMOLER · OCUPADO_POR_TERCEROS ·
EN_USO_POR_EL_PROPIETARIO
```

**`CON_EDIFICACION_A_DEMOLER` no es un estado de ocupación.** Es un estado de la
**edificación**, y mezclarlo aquí obliga a elegir entre dos cosas que pueden ser
ciertas a la vez: un terreno puede tener una casa vieja encima **y** estar
ocupado por terceros. Además `T` ya tiene dónde registrarlo —
`edificacion_existente` (DECIMAL m², §3.7 de la auditoría), donde *«declarar 0 no
es lo mismo que no saberlo»*.

Extendido tal cual a un departamento, el vocabulario ofrece «con edificación a
demoler» como opción de un piso 12. Eso es una lista muda con pasos extra.

**Vocabulario resuelto**, válido para los siete:

| código | rótulo | por qué existe separado |
|---|---|---|
| `DESOCUPADO` | Desocupado | entrega inmediata |
| `OCUPADO_POR_EL_PROPIETARIO` | Ocupado por el propietario | se desocupa al cierre, y la fecha se pacta |
| `OCUPADO_POR_INQUILINO` | Ocupado por inquilino | **es otro producto**: se vende con renta en curso, y el comprador hereda el contrato |
| `OCUPADO_POR_TERCEROS_SIN_TITULO` | Ocupado por terceros sin título | riesgo legal, no una fecha de mudanza |

`CON_EDIFICACION_A_DEMOLER` **se retira de esta clave** y su información queda en
`edificacion_existente`, que el Corte 5 siembra igualmente para `T`.

---

## 4. No duplica `disponibilidad_comercial`

Comprobado antes de resolver, para que el Corte 5 no cree un sinónimo:

```
propiedad.disponibilidad_comercial  CHECK IN ('D','R','A','T')
dev: D=22 · A=4
```

Son **cosas distintas y las dos hacen falta**: `disponibilidad_comercial` es el
estado **comercial** del inmueble dentro de BROX (disponible, reservado,
alquilado, transferido); `estado_ocupacion` es el estado **físico** de quién está
dentro. Un inmueble puede estar `D` —disponible para vender— y a la vez ocupado
por su propietario hasta la firma. Colapsarlas perdería una de las dos.

---

## 5. La exigencia queda en `OPC`, y no es cautela: es lo que acaba de aprenderse

La auditoría propone `estado_ocupacion` en **PUB** para `T`.

**Se siembra `OPC` en los siete**, y la promoción a PUB queda pendiente de que
exista la superficie que reporta las `PUB` faltantes.

Razón, medida hace horas: `V82` dejó demostrado que **hoy ninguna superficie de
lectura reporta una clave `PUB` de la PROPIEDAD** —`atributosQueFaltan` sólo
lleva `ALT`, y `faltanParaPublicar` es del sujeto ENCARGO y por el guard 2.5 de
`V78` no puede llevar una clave de la PROPIEDAD—. Sembrar una `PUB` ahora
repetiría exactamente el problema que `V82` vino a corregir: un bloqueo real que
**sólo se descubre chocando contra él**.

Esa promoción va **después** del corte que expone las `PUB` faltantes junto a las
`ALT` (§2.5 ter de `pendientes-brox.md`), no antes.

---

## 6. Qué queda pendiente del titular

Nada bloquea el Corte 5 con esta resolución. Pero dos cosas son suyas cuando
llegue el momento:

1. **Si `estado_ocupacion` debe subir a `PUB`** —y en qué tipos— una vez exista
   el aviso. La auditoría lo pedía para `T`.
2. **Si `OCUPADO_POR_INQUILINO` debe abrir un dato más** (fecha de fin de
   contrato). Hoy no se propone: sería inventar una captura que nadie pidió, y el
   dato vive en el contrato.

---

## 7. Lo que el Corte 5 debe llevar por esto

- `estado_ocupacion`, LISTA, **A, C, D, L, O, T, X**, `aplica_todos = false`,
  **OPC** en los siete, con los cuatro códigos de §3.
- **Ninguna modificación de `entrega_desocupado`.**
- Una aserción propia en el bloque `DO $$`: que el par
  `estado_ocupacion` / `entrega_desocupado` **queda cubierto en los siete**, y no
  sólo que la clave existe.
