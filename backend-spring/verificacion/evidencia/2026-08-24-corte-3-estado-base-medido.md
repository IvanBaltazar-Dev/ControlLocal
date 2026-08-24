# Estado medido de `BASE_SHA` antes del Corte 3 — y cuatro defectos que ya estaban

**Medido el 2026-08-24 por el AUDITOR**, en fase de preparación, **antes de que
existiera CANDIDATE_SHA**. Registrado por CONTROL porque el agente no tenía
herramienta de escritura en su sesión.

`BASE_SHA` = `099a72332c621b20ad7a96f427f3e4369108877b` (V79).
Base: `controllocal_dev`, contenedor `controllocal-postgres-v2`.

**Para qué sirve este documento:** para que, cuando llegue el candidato, se pueda
distinguir **lo que el Corte 3 rompió** de **lo que ya estaba roto**. Sin esta
foto, cualquier hallazgo posterior admite la excusa «eso ya venía así».

---

## 1. El gate `.sql`, corrido entero por primera vez

```
 en verde | en rojo | total
       67 |       1 |    68
psql: ERROR: GATE EN ROJO: 1 comprobaciones fallaron      (exit code 3)
```

La única roja es la **16 — `M2 el catalogo del sistema tiene 25 atributos`**,
que es exactamente la que `3.a` viene a arreglar.

**Esto cierra la pregunta que el encargo dejaba abierta** («cuántas
comprobaciones estaban rojas y nadie miraba»): **una**. No había deuda escondida
detrás del censo. Lo deteriorado no era el fichero: era el hábito de no
ejecutarlo.

**Consecuencia operativa:** cualquier comprobación distinta de la 16 que salga
roja tras el candidato **es regresión del Corte 3**, sin excusa heredada posible.

`psql` devuelve **exit code 3** con el gate en rojo. Es el código que el paso
nuevo de `Verificar-Cierre.ps1` tiene que comprobar.

---

## 2. La foto del catálogo

| | |
|---|---|
| `catalogo_atributo WHERE del_sistema` | **51** (PROPIEDAD 25 + ENCARGO 26) |
| `del_sistema AND NOT activo` | **0** |
| claves de tenant (`organizacion_id IS NOT NULL`) | **0** |
| `max(orden)` | PROPIEDAD **250** · ENCARGO 325 |
| `catalogo_atributo_tipo` | 96 — ALT/`requerido=t` **10**, OPC/`requerido=f` **86** |
| `catalogo_atributo_operacion` | 112, **todas OPC** (esta tabla no tiene columna `requerido`) |
| `catalogo_atributo_opcion` | 35 (13 del lado PROPIEDAD) |
| **Filas `PUB` en todo el catálogo del sistema** | **0 por tipo · 0 por operación** |
| `atributo_propiedad` | **74** filas, 9 claves |
| `atributo_propiedad_opcion` | **0** filas |
| Flyway | cabeza 79 · `success=t` |

**Publicabilidad: 26 de 26, ninguna bloqueada.**
Corpus por tipo: **L 21 · O 2 · C 1 · D 1 · T 1**.

> El Corte 3 es un corte de vivienda sobre un corpus con **dos** viviendas. No
> invalida el corte —las claves son nuevas y no dependen del corpus—, pero sí
> dice cuánto puede demostrar la evidencia: la ida y vuelta se prueba, la
> utilidad estadística no.

Guardas heredadas comprobadas verdes: 2.4 (`requerido` espejo de ALT), 2.5 (sin
cruce por sujeto), opciones densas `1..N` y `UPPER_SNAKE` ASCII, cero mojibake.
`banos.ayuda` vacía. `Verificar-Cierre.ps1`: ASCII puro, 6 973 bytes, sin BOM;
inventario de **20** clases, coincidente con `GateDeCierreTest`.

Jar de partida: `controllocal-app-2.0.0-SNAPSHOT.jar` · **75 455 248 bytes** ·
**2026-08-24 06:14:25**.

---

## 3. Los cuatro defectos que ya estaban en `BASE_SHA`

### D-BASE-1 · El suelo no cazaba la única retirada que el sistema permite

**Es un defecto del encargo que CONTROL escribió**, no del Constructor: el
encargo justificaba el suelo diciendo que caza «que alguien retire una clave del
sistema». Medido, no lo hacía:

- `DELETE` sobre una clave del sistema **ya está prohibido** por
  `proteger_catalogo_del_sistema()` (`restrict_violation`). El suelo no aporta
  nada ahí.
- La retirada que el trigger **sí** permite —y que su propio mensaje recomienda:
  *«Para retirarlo de las preguntas, ponlo activo = false»*— **no baja el
  conteo**: `WHERE del_sistema` no filtra `activo`. Se podrían desactivar las 51
  y seguiría verde.

**Resuelto por enmienda de CONTROL** el mismo día:
`count(*) FILTER (WHERE activo) >= 51`. Límite honesto que queda escrito: con 81
claves, un suelo de 51 tolera 30 retiradas.

### D-BASE-2 · Una `LISTA` sin vocabulario no falla: se degrada en silencio a texto libre

Tres confirmaciones independientes:

1. `MotorDeCaptura.controlDe` emite `SELECTOR` sólo `if (opciones != null && !opciones.isEmpty())`; sin opciones cae al `default` → caja de texto.
2. `exigir_atributo_gobernado()` **condiciona** la validación de pertenencia a que el vocabulario exista (`AND EXISTS (SELECT 1 FROM catalogo_atributo_opcion ...)`).
3. `LISTA_MULTIPLE` es peor: `controlDe` devuelve `SELECTOR_MULTIPLE` **incondicionalmente**, y `e2e-editor-universal.ps1:162` lo **excluye del fixture** (`control -notin @('IMPORTE','SELECTOR_MULTIPLE','TITULARES')`).

Hoy se manifiesta en `servicios_disponibles` (deuda ya declarada,
`pendientes-brox.md` §2.3). **Riesgo vivo para el Corte 3**: sus 7 LISTA y 2
LISTA_MULTIPLE pueden repetirlo sin que nada se ponga rojo, y `vigilancia` y
`areas_comunes` no las tocaría ninguna suite E2E. Lo único que lo atrapa es la
aserción del `DO $$` de `V80`.

### D-BASE-3 · El gate de conservación no crece con el catálogo

`ConservacionDeLaEdicionIntegrationTest` lleva sus casos por tipo escritos a mano
(`:141-190`): 10 claves para DEPARTAMENTO, 11 para CASA, **todas anteriores a
`V79`**. Las claves nuevas **no entran solas**. Sólo `e2e-editor-universal.ps1`
deriva del contrato, y con la exclusión de D-BASE-2.

**Enmendado por CONTROL**: el Constructor extiende esas listas dentro del Corte 3.
No es fichero nuevo, así que no toca el inventario de las 20.

### D-BASE-4 · `area_minima_arrendable.unidad = 'm2'`, sin acento

Las otras cuatro claves de área llevan `m²`. A `V77` se le olvidó reponerlo en el
`UPDATE` posterior — exactamente la clase de olvido que ese `UPDATE` existe para
evitar. **Fuera del encargo congelado del Corte 3**; recogido en
`docs/ai/borrador-corte-4-comercial.md`.

---

## 4. Las cifras que el candidato debe cuadrar

Derivadas de las 30 claves y contrastadas contra la auditoría, no contra el
`INSERT` del Constructor:

| | antes | después de `V80` |
|---|---|---|
| `catalogo_atributo del_sistema` | 51 | **81** |
| …de los cuales PROPIEDAD | 25 | **55** |
| `catalogo_atributo_tipo` | 96 | **164** (+68) |
| `catalogo_atributo_operacion` | 112 | **112** (sin tocar) |
| `catalogo_atributo_opcion` | 35 | **84** (+49) |
| `atributo_propiedad` / `_opcion` | 74 / 0 | **74 / 0** |
| exigencias | ALT 10 · OPC 86 | ALT 10 · **OPC 154** |
| propiedades publicables | 26 / 26 | **26 / 26** |

Reparto por tipo de las 68 filas de aplicabilidad:
**D 28 · C 16 · O 13 · L 7 · A 4 · T 0 · X 0**.

`orden` esperado: **260 … 550**, de diez en diez.
