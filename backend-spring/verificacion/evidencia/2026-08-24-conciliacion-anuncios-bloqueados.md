# Evidencia — Conciliación de los cuatro anuncios vivos sobre propiedades bloqueadas

**Fecha:** 2026-08-24
**Rama:** `feat/modelo-universal-y-autoridad-del-dato`
**BASE_SHA:** `93154ca` (encargo congelado en `16b1bbc`, sólo documentación)
**Encargo:** `docs/ai/encargo-conciliacion-anuncios-bloqueados.md`
**Migración:** **ninguna.** Sin código nuevo, sin Angular.

**Resultado: los cuatro anuncios se cerraron. No se escribió `tipo_acceso` en
ninguna propiedad, porque en ninguna de las cuatro existe evidencia de cómo se
entra.**

---

## 1. Preflight

`93154ca..16b1bbc` = **sólo documentación**. Árbol limpio, Flyway en **82**.

### 1.1 · Los cuatro, identificados con su deuda exacta

Agregando **todas** las claves `ALT`/`PUB` faltantes, **sin filtrar por
`tipo_acceso`** — la consulta no nombra ninguna clave:

| anuncio | propiedad | encargo | op | canal | importe | deuda medida |
|---|---|---|---|---|---|---|
| 4 | `LOC-D018` | 9 | alquiler | FACEBOOK | PEN 3 840 | `tipo_acceso(PUB)` |
| 8 | `LOC-D024` | 5 | alquiler | FACEBOOK | PEN 5 920 | `tipo_acceso(PUB)` |
| 9 | `LOC-D027` | 2 | alquiler | WEB_PROPIA | PEN 6 440 | `tipo_acceso(PUB)` |
| 12 | `PROP-0022` | 608 | **venta** | URBANIA | **USD 315 000** | `tipo_acceso(PUB)` |

**Y las tres que NO se tocan**, porque la misma consulta las da publicables:
anuncios **2** y **11** (`LOC-D014`) y **5** (`LOC-D019`). Medido el porqué:
**las dos son `O` (oficina)**, y `tipo_acceso` sólo aplica a `L`. No es que
tengan el dato — es que no les hace falta.

Censo de partida: `publicacion` = **5 cerradas · 7 publicadas**; hitos `P` = **3**.

---

## 2. La búsqueda de evidencia — dónde se buscó, y qué se encontró

El encargo pedía buscar **de verdad** antes de concluir que no hay nada. Se buscó
en tres niveles.

### 2.1 · Los campos de ubicación y descripción

| | `LOC-D018` | `LOC-D024` | `LOC-D027` | `PROP-0022` |
|---|---|---|---|---|
| `nombre_edificio_galeria` | — | — | — | — |
| `interior_unidad` | — | — | — | — |
| `piso` | — | — | — | — |
| `referencia_interna` | — | — | — | — |
| `zona_urbanizacion` | Lima Cercado | Lince | Miraflores | — |
| descripción | «Bodega en Lima Cercado, listo para operar» | «Academia preuniversitaria en Lince…» | «Agencia bancaria en Miraflores…» | **vacía** |

Las tres descripciones son **plantilla generada** —`«{rubro} en {distrito}, listo
para operar»`—. Ninguna dice cómo se entra.

### 2.2 · Todo lo demás asociado a las cuatro propiedades y sus encargos

Buscado y **encontrado vacío o irrelevante** en: `atributo_propiedad`,
`foto_propiedad`, `observacion_mercado`, `prospeccion`, `captacion`
(observaciones, revisión y motivo de cierre), `oportunidad_comercial`,
`interaccion_comercial` (observaciones y transcripción), `visita`,
`documento_solicitud`, `solicitud_alquiler`, `tarea`, `alerta`,
`motivo_no_continuidad`, `historial_estado`, `condicion_compraventa`,
`titularidad_propiedad`, y los campos de `publicacion`.

Lo único que hay es **texto de plantilla**:

```
atributos        : sólo rubro_permitido, antiguedad_anios, ambientes
                   (PROP-0022: NINGUNO)
fotos            : 0 en las cuatro
prospección      : observaciones NULL en las tres que la tienen
captación        : «Encargo con condiciones acordadas con el propietario»
                   «Encargo conforme»
oportunidad      : «Interes generado desde la cartera publicada»
interacción      : «Seguimiento comercial del cliente»
visita           : «Visita coordinada con el cliente» (resultado sin registrar)
historial        : «Contacto inicial…», «Propuesta entregada…», «Documento conforme.»
título anuncio   : «Publicacion 12 / 18 / 21 / 3259» (generado)
URL              : demo.test (PROP-0022: vacía)
```

**Ninguno describe el acceso.**

### 2.3 · El barrido de vocabulario sobre TODO el esquema

Para no depender de recordar dónde puede haber una nota, se recorrieron **todas**
las columnas de texto libre de la base —`text` y `varchar(≥30)`, excluyendo
catálogos, auditoría y tablas de sistema— buscando el vocabulario del acceso:

```
galer | pasaje | centro comercial | mercado | pie de calle | esquina | interior
stand | modulo | tienda | puerta | fachada | ingreso | acceso | planta baja | sotano
```

**Control positivo incluido en el propio patrón** (`Bodega`, que se sabe presente):
el barrido devolvió aciertos, así que sus ceros valen.

**Aciertos totales: 7 columnas.** Revisados uno a uno:

| dónde | qué dice | ¿es de las cuatro? |
|---|---|---|
| `propiedad.descripcion` | `LOC-0001` «Local comercial **en esquina**…» | **No** |
| `propiedad.descripcion` | `LOC-0002` «Local **en galeria** del centro…» | **No** |
| `propiedad.descripcion` | `LOC-D001` «Local **a pie de calle** con vitrina» | **No** |
| `propiedad.descripcion` | `LOC-D011` «Tienda por departamento…» (es el rubro) | **No** |
| `propiedad.zona_urbanizacion` | `LOC-0001` «Centro comercial de Miraflores» | **No** |
| `atributo_propiedad.valor_texto` | `LOC-D011` `rubro_permitido = Tienda por departamento` | **No** |
| `requerimiento_cliente.observaciones` | lo que **busca un cliente**, no lo que es un local | **No** |
| `historial_estado.motivo` | «Entra al **mercado**: el propietario aceptó…» — otra acepción | **No** |
| `documento_solicitud` (nombre/ruta) | nombres de fichero | **No** |

**Ninguno de los aciertos pertenece a las cuatro.** El barrido **sí sabe
encontrar** descripciones de acceso cuando existen —las encontró en tres
propiedades— y **no encontró ninguna** en `LOC-D018`, `LOC-D024`, `LOC-D027` ni
`PROP-0022`.

### 2.4 · Lo que había y NO se usó, porque no es evidencia

- **`LOC-D027` · «Agencia bancaria en Miraflores».** El rubro **no** prueba pie
  de calle: hay agencias dentro de centros comerciales. Es exactamente el ejemplo
  que el encargo prohíbe.
- **`LOC-D024` · «Academia preuniversitaria», Av. Arequipa 3120.** Una avenida
  principal **no** dice si el local da a la calle o está en un piso alto.
- **`LOC-D018` · «Bodega», Jr. Huallaga 320, Lima Cercado.** Es el caso más
  tentador **y el más peligroso**: Jr. Huallaga está en la zona de Mesa Redonda,
  que es literalmente el ejemplo con el que se justificó exigir `tipo_acceso`.
  Ahí una bodega puede estar a pie de calle **o** ser un puesto dentro de una
  galería, y **la diferencia es todo el precio por m²**. Adivinar aquí sería más
  dañino, no menos.
- **`PROP-0022` · nada en absoluto.** Sin descripción, sin un solo atributo, sin
  prospección ni historial. Es el que menos se sabe **y el de mayor importe**
  (USD 315 000).

> **La prueba de fuego del encargo:** para llegar al valor en cualquiera de los
> cuatro haría falta decir «normalmente», «suele» o «casi siempre». **Luego no es
> evidencia.**

---

## 3. La decisión, una por una

**Las cuatro caen en la rama 4 del procedimiento** —«si no existe evidencia
suficiente, no inventes: cierra la publicación»—, pero se resolvieron **de una en
una**, con su búsqueda y su razón propias.

### 3.1 · `LOC-D018` · anuncio 4 · encargo 9 · PEN 3 840

- **Deuda:** `tipo_acceso(PUB)`, causa única.
- **Buscado:** los 16 orígenes de §2.2 más el barrido de §2.3. Tiene prospección
  (observaciones `NULL`), historial de 5 pasos, una visita, una interacción, un
  documento — **todo plantilla**.
- **Decisión: CERRAR.** No hay nada que diga cómo se entra, y es el caso donde
  suponerlo sería peor: **Mesa Redonda es el ejemplo canónico de que el mismo
  metraje vale cosas distintas según el acceso.**
- **Antes:** `P`, sin fecha de baja · **Después:** `C`, baja 2026-08-25.

### 3.2 · `LOC-D024` · anuncio 8 · encargo 5 · PEN 5 920

- **Deuda:** `tipo_acceso(PUB)`, causa única.
- **Buscado:** ídem. Tiene visita e interacción, ambas con texto de plantilla.
- **Decisión: CERRAR.** «Academia preuniversitaria en Av. Arequipa» no dice si se
  entra desde la avenida o desde un pasaje interior.
- **Antes:** `P` · **Después:** `C`, baja 2026-08-25.

### 3.3 · `LOC-D027` · anuncio 9 · encargo 2 · PEN 6 440

- **Deuda:** `tipo_acceso(PUB)`, causa única.
- **Buscado:** ídem. Sin visita ni interacción; sólo historial de plantilla.
- **Decisión: CERRAR.** El rubro «Agencia bancaria» es justamente el que el
  encargo nombra como **no** probatorio.
- **Antes:** `P` · **Después:** `C`, baja 2026-08-25.

### 3.4 · `PROP-0022` · anuncio 12 · encargo 608 · **venta, USD 315 000**

- **Deuda:** `tipo_acceso(PUB)`, causa única.
- **Buscado:** ídem, más lo específico de una venta —`condicion_compraventa`—:
  **vacío**. Esta propiedad **no tiene descripción, ni un solo atributo escrito,
  ni prospección, ni historial de estado**.
- **Decisión: CERRAR.** Es el anuncio de mayor importe y del que menos se sabe.
  Precisamente por eso no se inventa nada.
- **Antes:** `P` · **Después:** `C`, baja 2026-08-25.
- **Su hito `P` de USD 315 000 del 2026-08-21 NO se toca**: es el registro de lo
  que el mercado vio, y borrarlo sería falsificar la serie.

---

## 4. El mecanismo, verificado antes de usarlo

`PUBLICADO → CERRADO` **pasa libre**, y es asimetría deliberada —retirar del
mercado nunca puede estar bloqueado porque falte un dato:

```java
// cambiarEstado
if (Publicacion.ESTADO_PUBLICADO.equals(estado)) {
    exigirPublicable(propiedadDe(actual, actor), encargoDe(actual, actor), actor);
}
```

Y **no escribe hito `P`**, porque `registrarImportePublicado` sale antes:

```java
if (!Publicacion.ESTADO_PUBLICADO.equals(publicacion.getEstado()) || ...) {
    return;
}
```

Ejecutado por el endpoint de dominio,
`POST /encargos/{idEncargo}/publicaciones/{idPublicacion}/estado` con
`{"estado":"C"}`, **una llamada por anuncio**, las cuatro **HTTP 200**. No se
tocó la base por SQL.

---

## 5. Las cinco afirmaciones, medidas al terminar

| # | afirmación | medición |
|---|---|---|
| **1** | 0 publicaciones `PUBLICADO` con faltantes `PUB` conocidos | **0** |
| **2** | 4/4 anuncios explicados | **4/4**, §3, uno por uno |
| **3** | 0 datos inventados | `atributo_propiedad` con `tipo_acceso` = **0** |
| **4** | 0 pérdida histórica | **12** publicaciones (igual), **19** encargos, **31** hitos |
| **5** | 0 hitos `P` artificiales | **3** antes → **3** después |

**Censo de `publicacion` por estado:**

```
ANTES     C = 5   ·   P = 7        (total 12)
DESPUÉS   C = 9   ·   P = 3        (total 12)
```

`+4` cerradas, `−4` publicadas, **total intacto**: no se borró ninguna.

**Nada se perdió en los cuatro.** Conservan encargo, importe, moneda, canal,
código de origen, URL y **su fecha de publicación original**; lo único que cambia
es `estado` y la `fecha_baja`. Sus **encargos siguen vivos** (`CAP-0002`,
`CAP-0005`, `CAP-0009` activos; `ENC-0014` pendiente) y su histórico de precios
está entero, incluido el hito `P` de `PROP-0022`.

**Las 3 que siguen `PUBLICADO`** son los anuncios 2, 5 y 11, sobre `LOC-D014` y
`LOC-D019`, **ambas oficinas** — publicables, y por tanto fuera de este encargo.

---

## 6. Lo que este trabajo deja anotado, y NO tocó

- **Los cuatro locales siguen sin `tipo_acceso`**, siguen no publicables y siguen
  en la lista de trabajo de campo. Lo que cambió es que **ya no están anunciados
  al mercado mientras no lo están**.
- **Tres de los otros 17 locales bloqueados SÍ parecen tener evidencia explícita**
  en su descripción —`LOC-0001` «en esquina», `LOC-0002` «en galeria»,
  `LOC-D001` «a pie de calle»—. **No se tocaron**: no tienen anuncio vivo, así que
  están fuera de este encargo. Se anota porque es material útil para el
  enriquecimiento, y porque **decidirlo es de CONTROL, no mío**.
- No se abrió el Corte 5 ni I0; no hay migración, código nuevo ni cambios en
  Angular.

---

## 7. La corrida de cierre

| Paso | Resultado |
|---|---|
| Gate del modelo universal | **69 en verde, 0 en rojo** · `ROLLBACK` |
| Reactor completo contra PostgreSQL real | **BUILD SUCCESS** — `720 · 48 · 405`, **`Skipped: 0`** |
| Los 20 de integración | **20 de 20 ejecutados** |
| Suites E2E | **5 de 5**, **419 `OK` / 0 `FALLA`** |
| | **`== CIERRE VERDE ==`**, salida **0** |

```
ng test                            671 SUCCESS
ng build --configuration production   NG_BUILD_EXIT=0
```

**405 en `aplicacion`, idéntico**: esta conciliación es **operación de datos por
la puerta del dominio**, no cambio de código. No se añadió ni se movió una sola
prueba, y no había ninguna que añadir — lo que se ejercitó fue el mecanismo que
ya existía y que el microcorte anterior dejó verificado.
