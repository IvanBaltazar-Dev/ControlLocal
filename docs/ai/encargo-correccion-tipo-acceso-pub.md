# Encargo — Corrección post-Corte 4 · `tipo_acceso` pasa a `PUB` — `V82`

**Congelado por CONTROL el 2026-08-24**, por decisión del titular tomada tras el
cierre del Corte 4.

**BASE_SHA:** `96a4d65` — rama `feat/modelo-universal-y-autoridad-del-dato`,
árbol limpio, Corte 4 cerrado y auditado.

**No es el Corte 5.** El Corte 5 · Terreno **no se abre** hasta que esto cierre.

---

## 1. La decisión

> **`tipo_acceso` queda con exigencia definitiva `PUB`, no `ALT`.**

Semántica exigida para un `LOCAL` **sin** `tipo_acceso`:

| | |
|---|---|
| REGISTRAR | **SÍ** |
| EDITAR | **SÍ** |
| conservarse como propiedad conocida/observada | **SÍ** |
| usarse para investigación, precios, zonificación e inteligencia | **SÍ** |
| **PUBLICAR** | **NO** |

Con `tipo_acceso`: publicar **SÍ**, salvo que falte otro requisito.

**Razón del titular:** BROX debe poder conocer inmuebles que todavía no gestiona
o de los que aún no conoce toda la profundidad comercial. `tipo_acceso` es
bastante importante para impedir **una publicación**, no para impedir que el
inmueble **exista** en BROX.

---

## 2. Esto no fuerza el modelo: el modelo ya lo distinguía

Medido por CONTROL en el preflight, y es la razón de que la corrección sea de una
línea y no de un rediseño:

- **`Exigencia.bloqueaAlta()` mira sólo `ALT`.** El javadoc de
  `CatalogoAtributo.esRequeridoPara` (`:374`) lo dice con todas las letras:
  *«Se pregunta asi y NO comparando contra un nivel: basta que un consumidor lea
  "lo que no sea OPC" para que el alta empiece a exigir de golpe todo lo que solo
  debia exigir el anuncio.»*
- **`Exigencia.bloqueaPublicacion()` mira `ALT` y `PUB`** (`:385`).
- **Son dos consultas separadas a propósito.**
  `AtributoPropiedadRepository:88` — `clavesObligatoriasQueFaltan` filtra
  `exigencia = 'ALT'`; `clavesQueImpidenPublicar` filtra
  `exigencia in ('ALT','PUB')`. Su javadoc: *«las dos preguntas se hacen en
  momentos distintos y basta que una acabe respondiendo la otra para que el alta
  empiece a exigir de golpe todo lo que solo debia exigir el anuncio.»*
- La cadena del alta es `registrar:231` → `exigirObligatorios` →
  `AtributosGobernados.faltantesEntre:464` → `obligatoriasDe:70` →
  `esRequeridoPara` → **`bloqueaAlta()`**. `editar:437` no la llama.

**`V72` ya construyó exactamente el nivel que el titular pide.** El Corte 4 usó
`ALT` porque yo describí mal su efecto; la corrección devuelve la clave al nivel
que el modelo tenía previsto para este caso.

---

## 3. Preflight, ya medido por CONTROL — verifícalo, no lo copies

| comprobación | medido |
|---|---|
| HEAD | `96a4d65`, árbol limpio |
| Flyway en `controllocal_dev` | **81 aplicada, `success = t`** |
| siguiente versión libre | **`V82`** (existen V80, V81; V8/V9 son antiguas) |
| `tipo_acceso` hoy | **una sola fila**: `L` · `ALT` · `requerido = t` |
| censo `catalogo_atributo_tipo` | **ALT/t = 11 · OPC/f = 224 · PUB = 0** (total 235) |
| valores de `tipo_acceso` en `atributo_propiedad` | **0** |
| `ck_catalogo_exigencia` | `CHECK (exigencia IN ('ALT','PUB','OPC'))` — **`PUB` admitido** |
| triggers en `catalogo_atributo_tipo` | **ninguno de usuario** — nada bloquea el `UPDATE` |
| `gate-modelo-universal.sql` | **no contiene ninguna aserción de `PUB` ni de `exigencia`** que este cambio pueda romper |

Confirma además, contra la base: **26 en el censo · 21 `L` bloqueadas para
publicar · 5 publicables · causa única `tipo_acceso` · 0 valores inventados**.

---

## 4. El cambio de producción

**Exactamente uno**, sobre **una fila**:

```
tipo_acceso / L :  exigencia  ALT  → PUB
                   requerido  true → false
```

**Las dos columnas, en la misma sentencia.** No es opcional: el **guard 2.4 de
`V78`** exige que `requerido` sea espejo exacto de `exigencia = 'ALT'` en **todo**
el catálogo. Cambiar sólo `exigencia` deja el guard roto.

Nada más. **No** se toca ninguna otra exigencia, ningún vocabulario, ninguna
opción, ninguna aplicabilidad, ningún otro tipo de propiedad.

---

## 5. Lo prohibido

- **No modificar `V81`.** Está cerrada y auditada; debe quedar **byte por byte
  intacta**. Migración **nueva**, `V82`.
- **No rellenar los 21 locales.** Sigue siendo la regla que hace aceptable el
  bloqueo: se desbloquea el hecho verificado, no el relleno.
- **No inferir datos históricos.**
- **No tocar Angular** salvo causa demostrada — y si aparece, se para y se
  pregunta antes.
- **No abrir el Corte 5.**
- **No cambiar `exigirPublicable` ni ninguna consulta de gobierno.** La
  corrección es de **dato del catálogo**, no de código. Si crees que hace falta
  tocar Java, **para y devuelve `STOP`**.

---

## 6. Efecto esperado del censo — y es deliberado que no cambie

| | antes | después |
|---|---|---|
| propiedades publicables | **5 de 26** | **5 de 26** |
| las 21 `L` bloqueadas | sí | **sí, las mismas** |
| `ALT` / `requerido = true` | 11 | **10** |
| `PUB` / `requerido = false` | 0 | **1** |
| `OPC` / `requerido = false` | 224 | **224** |
| total `catalogo_atributo_tipo` | 235 | **235** |
| valores de `tipo_acceso` | 0 | **0** |

**La corrección NO busca hacer publicables los 21.** Busca recuperar
exclusivamente la capacidad de **registrar** un local sin conocer todavía
`tipo_acceso`. Que la publicabilidad no se mueva es la prueba de que el cambio
hizo lo que debía y sólo eso.

---

## 7. Consecuencia medida que el titular debe conocer, y no es un defecto

**`tipo_acceso` dejará de aparecer en `PropiedadResponse.atributosQueFaltan`.**

Esa lista se alimenta de `obligatoriasQueFaltan` → `clavesObligatoriasQueFaltan`,
que filtra **`ALT` solamente**. La superficie que **sí** seguirá nombrándolo es
`EncargoResponse.faltanParaPublicar`
(`PropiedadUniversalDtos:280`), alimentada por
`faltantesDePropiedadParaPublicar` → `clavesQueImpidenPublicar`, que mira
**ALT y PUB**.

Traducido a la cartera:

- **Los 7 locales con encargo vivo** (`LOC-D012`, `LOC-D018`, `LOC-D024`,
  `LOC-D025`, `LOC-D027`, `PROP-0022`, `PROP-0026`) **siguen avisando** de que
  les falta `tipo_acceso`, dentro de su encargo.
- **Los 14 sin encargo vivo dejan de avisarlo.** No se puede publicar sin
  encargo, así que no se pierde ninguna barrera — pero **sí se pierde el aviso**.

Esto es coherente con la instrucción del titular de que los 7 con encargo vivo
son la prioridad de enriquecimiento: son justamente los que conservan el aviso
visible. **Mídelo y escríbelo en la evidencia con las dos cifras (7 avisan / 14
no).** No lo arregles: si el titular quiere que los 14 también avisen, eso es una
superficie nueva y por tanto **un corte propio**.

---

## 8. Prueba obligatoria — los cuatro escenarios

| # | Acción | Esperado |
|---|---|---|
| **1** | Registrar un `LOCAL` **sin** `tipo_acceso` | **alta aceptada** |
| **2** | Leerlo, editar otro dato, volver a leer | `tipo_acceso` **sigue ausente**, el resto **idéntico** |
| **3** | Intentar publicar ese `LOCAL` | **rechazo**, por faltante de `tipo_acceso` |
| **4** | Declarar un `tipo_acceso` válido | **desaparece ese bloqueo específico** de publicación |

**La prueba de causa debe agregar TODAS las claves `ALT`/`PUB` faltantes.**
**Está prohibido filtrar por `tipo_acceso` antes de concluir que ésa es la
causa.** Una consulta que filtra por la clave y luego «descubre» que la causa es
esa clave no demuestra nada — es el ataque E2 del Corte 4 y el Auditor lo
repetirá.

---

## 9. Regresión que hay que demostrar

- Las **21 propiedades históricas siguen sin `tipo_acceso`**.
- Las 21 **siguen sin poder publicarse**.
- Los **7 encargos vivos** siguen siendo prioridad de enriquecimiento.
- Un `LOCAL` **nuevo sin `tipo_acceso` sí puede registrarse**.
- **Ninguna otra clave** cambia `ALT`/`PUB`/`OPC`.
- **Ningún otro tipo de propiedad** cambia.
- **`V81` byte por byte intacta** (`git diff 96a4d65..CAND -- …V81…` vacío;
  checksum de Flyway sin alterar).
- **`ConservacionDeLaEdicion` sigue verde**, con sus siete casos y su test de
  completitud.

**Ojo con las pruebas que fijaron el censo del Corte 4.** Si alguna afirma «11
`ALT`» o «exactamente una fila `ALT` nueva», este cambio la pone roja **con
razón**, y actualizarla es parte del trabajo — **no** es relajar nada. Búscalas
antes de correr el cierre. Barrido con **control positivo** o con `rg`: `grep -iF`
aborta en esta máquina sin escribir en stderr.

Los *fixtures* de `lib-alta-inmueble.ps1` **se dejan como están**: registrar con
`tipo_acceso` sigue siendo válido, y las suites que **publican** lo siguen
necesitando.

---

## 10. Cómo se escribe `V82`

Mecánica de siempre. `UPDATE` explícito por clave —nunca por `id` literal— y
cierre con un bloque `DO $$ … END $$;` que afirme:

1. `tipo_acceso`/`L` tiene **`exigencia = 'PUB'` y `requerido = false`**;
2. es **la única** fila `PUB` del catálogo del sistema;
3. quedan **exactamente 10** filas `ALT`, todas con `requerido = true`, y son
   **las diez heredadas** (`metraje_total` ×7, `dormitorios` ×2, `zonificacion`);
4. el total de `catalogo_atributo_tipo` sigue en **235**, y `OPC/false` en **224**;
5. **cero** valores de `tipo_acceso` en `atributo_propiedad`;
6. ninguna otra fila del catálogo cambió de exigencia — compáralo contra el
   conjunto esperado, no contra un recuento suelto.

---

## 11. Alcance documental autorizado

1. `docs/ai/mapa-ejecucion-brox.md` — la corrección y su porqué, bajo el Corte 4.
2. `docs/ai/pendientes-brox.md` — §2.5 ter: la decisión **queda tomada**; en su
   lugar, la consecuencia de visibilidad de §7 si merece quedar anotada.
3. `docs/ai/auditoria-profundidad-inmobiliaria.md` §6, si nombra la exigencia.
4. Evidencia: `backend-spring/verificacion/evidencia/2026-08-24-correccion-tipo-acceso-pub.md`.

Nada más. **Cero ficheros de `frontend-angular/`.**

---

## 12. Cierre

Gate `.sql` en verde **dentro** de `Verificar-Cierre.ps1` · los cuatro escenarios
demostrados · la prueba de causa **sin filtrar por la clave** · la regresión de §9
· `5 de 26` sin moverse · las dos cifras de §7 (7 avisan / 14 no) · una sola
corrida de cierre con `TEST_DB_URL` · build de producción de Angular · diff
limpio · commit en la rama, **sin push**.

## 13. Protocolo

Si el preflight contradice este encargo, **`STOP — DECISIÓN REQUERIDA POR
CONTROL`** antes de tocar un archivo, con la medición que lo contradice.

Al terminar:

```
LISTO PARA AUDITORÍA
BASE_SHA=96a4d65
CANDIDATE_SHA=<sha>
```
