# D-E4-1 · Modelo universal: Propiedad × Operación

**Qué congela:** el modelo de dominio con el que BROX deja de ser un sistema de
alquiler de locales y pasa a ser un sistema inmobiliario — venta y alquiler,
siete tipos de propiedad, varios titulares — **sin excepciones especiales**.

**Estado:** propuesta congelable, **corregida el 2026-08-21 (V76)**: la
titularidad dejó de ser condición del registro y pasó a serlo del encargo. Ver
§3.2 bis.

El gate está **en verde**:

```bash
node docs/ai/modelo/gate-modelo-universal.js
```

> 165 comprobaciones, todas verdes.
> Los ocho casos se representan sin excepciones: el modelo se puede congelar.

**Nada de E3 antes de esto.** La negociación depende de que el importe tenga
unidad y base; con «renta mensual» cocinada en el modelo, E3 nace torcida.

**Relacionado:** `decision-motor-de-registro.md` (lo que se pregunta),
`decision-kairos-contrato-de-acciones.md` (quién más lo ejecuta),
`decision-modelos-de-comision.md` y `diccionario-modelo-economico.md` (que ya
resolvieron el número con su unidad).

---

## 1. La tesis, en una frase

> **Una propiedad no tiene operación. Un titular *encarga* una operación sobre
> una propiedad, y ese encargo es el que lleva la operación, la condición
> económica, el plazo, la comisión y su propio histórico.**

Todo lo demás sale de ahí sin casos especiales:

| Pregunta | Respuesta que da el modelo |
|---|---|
| ¿En venta **y** alquiler a la vez? | **Dos encargos vivos.** Ni un booleano, ni una operación `AMBAS`, ni una fila duplicada |
| ¿Y el precio de venta contra la renta? | Cuelgan de encargos distintos: no se mezclan ni se pisan |
| ¿Qué expediente abre el cierre? | Lo elige la **operación del encargo**, nunca el tipo de propiedad |
| ¿Y si deja de alquilar y pasa a vender? | Se cierra un encargo y se abre otro. Las dos historias se conservan |
| ¿Un terreno se alquila? | Sí. La operación no depende del tipo — el tipo solo **ordena las preguntas** |

**El contrapunto que hay que entender:** si la operación viviera en la
propiedad, «venta y alquiler» obligaría a inventar algo — y ese algo se
propagaría a la búsqueda, al matcher, al precio, al expediente y a la comisión.
El gate lo comprueba explícitamente (`B.1`).

---

## 2. La sorpresa: media casa ya está construida

Antes de escribir una línea de migración, el gate leyó el esquema real. Lo que
encontró cambia el tamaño del trabajo:

| Pieza | Estado real | Evidencia |
|---|---|---|
| **`propiedad` ya generaliza** | ✅ desde V4 | `tipo_inmueble ∈ {L,O,D,C,T,X}`, `uso ∈ {C,V,I,M}` |
| **La operación ya existe en el encargo** | ✅ | `captacion.motivo_operacion ∈ {A,V}`, **validado en el setter de la entidad** |
| **La condición económica ya cuelga del encargo** | ✅ desde V15 | `condicion_economica_captacion`: importe, moneda, tipo y base de comisión, IGV |
| **El número ya lleva su unidad** | ✅ | *«ninguna capa puede inferir su significado por magnitud»* — comentario de la propia entidad |
| **Los documentos ya discriminan por operación** | ✅ | `tipo_documento_requerido.tipo_operacion` |
| **Party-Role para personas** | ✅ | `persona_rol` + `detalle_*` |
| **Histórico económico por hitos** | ✅ E0 | `precio_propiedad` (U/P/O) + backfill V45 |

> **`motivo_operacion` con `{A,V}` ya validado es el hallazgo que ordena todo.**
> Alguien ya vio venir la venta. Este documento no inventa la operación: la
> **nombra**, la hace obligatoria y le cuelga lo que le corresponde.

**Lo que falta de verdad son seis cosas**, no un modelo nuevo:

1. **Multi-titular** — `propiedad.id_rol_propietario` es 1:1 `NOT NULL`.
2. **Atributos gobernados** — hoy `detalle_local_comercial`, una tabla por tipo.
3. **El histórico económico no sabe de qué encargo es** — con dos operaciones
   vivas, las series se mezclan.
4. **El requerimiento habla alquiler comercial** — `renta_min/max`, `rubro`
   obligatorio, un solo `tipo_inmueble`.
5. **La compraventa no tiene expediente.**
6. **PostGIS y el outbox de eventos** no existen.

---

## 3. El modelo

El contrato completo, **como dato ejecutable**, está en
[`docs/ai/modelo/modelo-universal.js`](modelo/modelo-universal.js). Aquí va su
forma:

```
                    Persona ──(persona_rol)── PROPIETARIO
                                                   │
                                          Titularidad (cuota, representante, vigencia)
                                                   │
    AtributoPropiedad ─────────────────────── Propiedad ─────────── Publicación
   (catálogo gobernado)                       la COSA física              │
                                                   │                       │
                                                   │                       │
                                            ┌──────┴──────┐               │
                                       Encargo         Encargo ───────────┘
                                      (ALQUILER)        (VENTA)
                                            │                │
                                  CondiciónEconómica  CondiciónEconómica
                                  HistóricoEconómico  HistóricoEconómico
                                            │                │
                                     Oportunidad ← Requerimiento ← Cliente
                                            │
                                       Expediente (ALQUILER | COMPRAVENTA)
                                            │
                                     Contrato → Comisión
```

### 3.1 Las piezas, con su estado

| Entidad | Tabla | Estado | Qué cambia |
|---|---|---|---|
| **Propiedad** | `propiedad` | AMPLIA | pierde precio, moneda, propietario único y disponibilidad; gana `ubicacion` PostGIS |
| **Titularidad** | `titularidad_propiedad` | **NUEVA** | cuota, representante, vigencia |
| **Encargo** | `captacion` | RENOMBRA | `motivo_operacion` pasa a `operacion` y deja de tener `DEFAULT` |
| **CondiciónEconómica** | `condicion_economica_captacion` | EXISTE | nada |
| **HistóricoEconómico** | `precio_propiedad` | AMPLIA | gana `id_encargo` y `operacion` |
| **AtributoPropiedad** | `atributo_propiedad` | **NUEVA** | sustituye a `detalle_local_comercial` |
| **CatálogoAtributo** | `catalogo_atributo` | **NUEVA** | tipo, unidad, a qué tipos aplica, obligatoriedad |
| **Publicación** | `publicacion` | AMPLIA | gana `id_encargo` |
| **Requerimiento** | `requerimiento_cliente` | AMPLIA | `operacion_buscada`, tipos en lista, presupuesto, radio |
| **CriterioRequerimiento** | `criterio_requerimiento` | **NUEVA** | indispensable vs deseable |
| **Expediente** | `solicitud_alquiler` | RENOMBRA | gana `tipo` y condiciones de compraventa |
| **DocumentoRequerido** | `tipo_documento_requerido` | AMPLIA | gana `tipo_propiedad` |
| **EventoDominio** | `evento_dominio` | **NUEVA** | outbox transaccional |

### 3.2 Por qué `Titularidad` y no un segundo `id_propietario`

Una copropiedad no es «dos dueños»: es **cuotas, un representante y una
vigencia**. Una venta no borra al titular anterior — le pone fecha de fin. Sin
eso, el histórico de propiedad se pierde en el primer cierre, y es
exactamente el dato que un sistema inmobiliario no puede perder.

**Invariantes** (comprobadas en `B.3`): las cuotas vigentes suman 100 y hay
exactamente un representante.

### 3.2 bis · Una Propiedad puede no tener titular, ni encargo, ni prospección

**Añadido el 2026-08-21 (V76).**

> Una Propiedad representa un inmueble **conocido por BROX**, no necesariamente
> una oferta gestionada por BROX. Su existencia, procedencia e historia
> observada son independientes de Prospecciones y Encargos. Los hechos
> comerciales solo nacen cuando existe la relación comercial que los autoriza.

Este documento decía «toda propiedad tiene al menos un titular vigente» y lo
comprobaba el gate. Era cierto mientras registrar y encargar fueran el mismo
acto, y dejó de serlo en V75, cuando el alta admitió cero operaciones. La
consecuencia práctica de mantenerlo era mala: para poder anotar un inmueble
visto en un portal había que **inventar un propietario**, y esa persona falsa
queda dentro de la cartera, cuenta en los listados y no se puede distinguir de
una real.

Qué cambia, exactamente:

| Antes | Desde V76 |
|---|---|
| `propiedad.id_rol_propietario` `NOT NULL` | nullable, con `ck_propiedad_titular_completo`: o van los dos campos o ninguno |
| El alta exige ≥ 1 titular | El alta lo **pregunta** y no bloquea |
| — | **Encargar** exige ≥ 1 titularidad vigente, en `TitularParaEncargar`, por los tres caminos que abren captación |
| Invariante M1 «toda propiedad tiene titular vigente» | «ningún encargo vivo cuelga de una propiedad sin titular» |
| — | `propiedad.origen_incorporacion`: `OPERACION`, `OBSERVACION` o `SEMILLA` |
| — | `observacion_mercado`: serie append-only de precios **vistos**, separada del histórico del encargo |

Y la frontera que no se cruza:

> BROX nunca convierte una observación de mercado en un hecho comercial ni
> inventa una relación para poder conservar conocimiento.

Por eso `observacion_mercado` **no** escribe `precio_propiedad`, no toca
`propiedad.precio_referencial`, no cambia la disponibilidad y no abre nada: un
precio observado no lo autorizó ningún propietario, y proyectarlo sobre la
propiedad lo haría indistinguible de uno pactado. El gate de la base lo sostiene
además desde el otro lado — `tg_precio_exige_encargo` rechaza un hito de precio
sin encargo y el mensaje remite a esta tabla.

### 3.3 Por qué atributos gobernados y no EAV libre

El riesgo de un modelo dinámico es que cada quien invente su clave: entonces ni
la búsqueda ni el matcher pueden comparar dos propiedades. Cada atributo declara
**tipo, unidad y a qué tipos aplica**:

```js
{ clave: "carga_electrica_kw", tipo: "DECIMAL", unidad: "kW",
  aplica: ["LOCAL_COMERCIAL", "OFICINA", "ALMACEN"] }
{ clave: "dormitorios", tipo: "ENTERO",
  aplica: ["DEPARTAMENTO", "CASA"], requeridoPara: ["DEPARTAMENTO", "CASA"] }
```

19 atributos del sistema para arrancar. Una organización puede añadir los suyos;
**no puede borrar los del sistema ni redefinir su tipo**. El gate comprueba que
ningún caso usa un atributo fuera del catálogo (`A.1`) ni uno que no aplique a
su tipo.

### 3.4 Por qué el requerimiento tiene que hablar el mismo idioma

Si la propiedad dice `dormitorios` y el requerimiento dice `habitaciones`, el
matcher necesita una tabla de traducción — y esa tabla es donde mueren los
matchers. **Los criterios del requerimiento usan el mismo catálogo** que los
atributos de la propiedad, y por eso `B.7` y `B.8` pueden decidir un match con
lo declarado, sin implementar el matcher.

Y la distinción que hace útil el resultado:

| Peso | Qué hace |
|---|---|
| `INDISPENSABLE` | si no se cumple, **descarta** |
| `DESEABLE` | si no se cumple, solo **baja el puntaje** |

Es lo que permite decir *«encaja en 4 de 5; falta confirmar la potencia»* en vez
de un porcentaje sin explicación — el hallazgo que los prototipos ya enseñan.

### 3.5 Por qué dos expedientes y no uno

Una compraventa tiene arras, minuta, escritura y bloqueo registral; no tiene
garantía ni adelanto. Meterla en `SolicitudAlquiler` llenaría la tabla de
columnas nulas y de `if (esVenta)`.

Pero **el 80 % de la maquinaria es la misma**: documentos, revisión, evaluación
del broker, comisión, trazabilidad. Por eso es **un expediente con tipo**, y el
tipo **se deriva de la operación del encargo** — no se elige a mano (`B.5`).

---

## 4. El gate, y qué comprueba de verdad

Ocho casos, y ninguno puede necesitar una excepción:

| Caso | Por qué está |
|---|---|
| Local + alquiler | es lo que el sistema hace hoy: no puede romperse |
| Departamento + venta | lo que hoy **no cabe** |
| Casa + alquiler | vivienda con operación de alquiler |
| Terreno + venta | el extremo: sin construcción ni ambientes |
| **Venta y alquiler simultáneos** | **el caso que decide el modelo** |
| Copropiedad (3 titulares) | hoy imposible por diseño |
| Cambio de intención | comprueba que la historia se conserva |
| Almacén + alquiler | tipo nuevo: comprueba que añadir uno no toca el modelo |

Más tres requerimientos del lado demanda que tienen que casar con los de oferta
—y **no casar con los que no deben**: una compra no puede casar con un alquiler.

### 4.1 El gate ya encontró tres errores míos

No es decorativo. En su primera ejecución tumbó el contrato:

- `Requerimiento.zonas` estaba declarado como columna y es una **relación N:M**
  (`requerimiento_distrito`).
- `Expediente.condicionesAlquiler` estaba como una columna y son **cinco**.
- La comprobación de «lo que hay que quitar» miraba el nombre del campo Java en
  vez de **la columna del esquema**, que es donde vive la verdad.

Los tres están corregidos. Es lo que se gana escribiendo el contrato como dato
en vez de como prosa.

---

## 5. La migración: cinco pasos, ninguno destructivo

**Los locales actuales no se borran ni se reconstruyen.** Se migran
conceptualmente a `tipo = LOCAL_COMERCIAL` y `operacion = ALQUILER`, que es
literalmente lo que ya son.

| # | Paso | Riesgo | Reversible |
|---|---|---|---|
| **M1** | `titularidad_propiedad` + backfill de un titular al 100 % desde `propiedad.id_rol_propietario` | bajo | sí |
| **M2** | `catalogo_atributo` + `atributo_propiedad` + backfill desde `detalle_local_comercial` y las columnas de subtipo de `propiedad` | medio | sí |
| **M3** | `precio_propiedad.id_encargo` + `operacion`; backfill por la captación activa de cada propiedad; los huérfanos quedan con `NULL` y `operacion = ALQUILER` | bajo | sí |
| **M4** | `operacion` obligatoria en el encargo; `precio_referencial`/`moneda_referencial` pasan a la condición económica del encargo activo | **alto** — toca el cable | sí, con vista |
| **M5** | `expediente.tipo` + condiciones de compraventa + `tipo_documento_requerido.tipo_propiedad` | medio | sí |

**PostGIS va aparte (M0), y primero**, porque no depende de nada: `CREATE
EXTENSION postgis`, columna `ubicacion geography(Point,4326)` poblada desde
`geo_lat`/`geo_long`, índice GiST. Las dos columnas viejas **se conservan**
mientras el cable las use.

### 5.1 La regla que no se rompe

`docs/ai/decision-contrato-v2-descongelado.md` sigue mandando: **cada cambio
viaja con sus pruebas**, y `Verificar-Cierre.ps1` es el gate — no `mvn clean
install`, que se salta las 37 pruebas de integración en silencio.

Y una advertencia concreta de este repositorio: **una conversión de vocabulario
tiene que llegar también a los cuerpos PL/pgSQL**. V40 narrowed tres columnas
`estado` y dejó `exigir_administrador_operativo()` comparando `'ACTIVO'`: cada
alta de MFA devolvió 409 hasta V44. Ni javac ni Hibernate leen un cuerpo de
función.

---

## 6. Qué NO entra aquí

| Fuera | Por qué |
|---|---|
| **Neo4j como dependencia** | PostgreSQL sigue siendo la verdad. El grafo será una **proyección reconstruible**, y `evento_dominio` es lo único que hay que tener ahora porque es imposible reconstruirlo después |
| **El matcher** | el modelo declara lo necesario para decidir un match; implementarlo es la etapa siguiente |
| **La negociación (E3)** | depende de esto, no al revés |
| **Renombrar en el frontend** | el rótulo *Propiedades* es una decisión de navegación (D-E3-1 §5); la entidad no se toca |

---

## 7. Criterios de aceptación

1. `node docs/ai/modelo/gate-modelo-universal.js` en verde — **ya lo está**.
2. Los ocho casos se representan **sin** una columna, tabla o rama que exista
   solo para uno de ellos.
3. Ninguna entidad fuera de `Encargo` declara operación.
4. Ninguna entidad fuera de `CondiciónEconómica` e `HistóricoEconómico` declara
   importe.
5. Un tipo de propiedad nuevo se añade **tocando el catálogo**, no el modelo.
6. Los documentos exigidos por un expediente se **derivan** de
   `(operación, tipoPropiedad)`.
7. Toda migración es reversible y ninguna borra historia.
8. `Verificar-Cierre.ps1` en verde con `TEST_DB_URL` presente.
