# Borrador — Corte 4 · Comercial (L, O, A)

> **ESTO NO ESTÁ CONGELADO.** Es trabajo adelantado por CONTROL mientras el
> Corte 3 se construye. **No lo ejecute nadie.** Se convierte en encargo cuando
> el Corte 3 cierre y se resuelva la única decisión de producto que lleva (§6).
>
> Medido el **2026-08-24** contra `controllocal_dev` (contenedor
> `controllocal-postgres-v2`, 26 propiedades) y contra `BASE_SHA`
> `099a72332c621b20ad7a96f427f3e4369108877b`.

---

## 1. Qué es el Corte 4

Lo que la auditoría dejó para los tipos **comerciales** —local (`L`), oficina
(`O`), almacén/industrial (`A`)—, más lo que el Corte 3 excluyó explícitamente
por no ser vivienda. Migración prevista: **`V81`** (`V80` la ocupa el Corte 3).

`orden`: continúa donde deje el Corte 3. Si `V80` llega a **550**, este empieza en
**560**, de diez en diez.

---

## 2. De dónde sale cada bloque

| Fuente | Qué aporta | Estado |
|---|---|---|
| `auditoria-…-inmobiliaria.md` §3.3 | `nivel_implementacion` (L,O,A) — el hecho del par `se_entrega_implementado` | ✅ medido |
| §3.4 | lo que el Corte 3 dejó fuera por ser exclusivo de O/L/A: `recepcion_edificio`, `horario_acceso_edificio`, `fibra_optica`, `certificacion_sostenible` | ✅ |
| §3.5 | **las instalaciones**, menos las dos que son reemplazo de `servicios_disponibles` (§5) | ⚠️ ver §4 |
| §3.7 | el bloque comercial y logístico entero — es el grueso del corte | ⚠️ ver §6 |

---

## 3. El par huérfano que este corte cierra, con su aplicabilidad medida

El guard 2.2 de `V78` **ignora `tipo_operacion`**: toma el conjunto DISTINCT de
`tipo_propiedad` de las filas de la condición y exige que el hecho los cubra
todos (`V78:150-157`). Medido hoy:

```
se_entrega_implementado   sujeto=ENCARGO   A/A/OPC   L/A/OPC   O/A/OPC
```

→ **`nivel_implementacion` debe nacer cubriendo A, L y O.** La auditoría §3.3
dice «L,O,A» — **coincide**. Aquí no hay corrección que hacer, a diferencia de
`mascotas_reglamento` en el Corte 3.

Vocabulario de §3.3: `CASCO_OBRA_GRIS · PLANTA_LIBRE · IMPLEMENTADO_PARCIAL ·
IMPLEMENTADO_COMPLETO`.

> **`uso` / `uso_admitido_por_titular` no es un huérfano.** Se comprobó:
> `uso` vive como **columna** de `propiedad`, no como clave del catálogo, y
> `SujetoDelDatoIntegrationTest` lo dice explícitamente en su comentario
> (`:103-107`). Por eso está en el espejo Java y **no** en la lista SQL del
> guard. No hay nada que sembrar.

---

## 4. Una incoherencia del plan que este corte hereda, y cómo se resuelve

§3.5 (instalaciones) mezcla tipos: `gas` aplica a **D,C,L,O,A,T** y
`agua_caliente` a **D,C** — es decir, **vivienda**. El Corte 3 las excluyó
expresamente (§8 de su encargo: «`agua_caliente` y el resto de §3.5 son del
Corte 4»).

**No se reabre el Corte 3.** El encargo está congelado y un corte cerrado no se
reabre. La consecuencia se acepta y se escribe: **el Corte 4 termina también las
instalaciones de la vivienda**, no sólo las comerciales. Queda registrado como
consecuencia, no como hueco silencioso.

Reparto propuesto de §3.5:

| Clave | aplica_a | Corte |
|---|---|---|
| `gas` | D,C,L,O,A,T | **4** |
| `agua_caliente` | D,C | **4** |
| `suministro_electrico` | L,O,A | **4** |
| `respaldo_electrico` | D,O,L,A | **4** |
| `aire_acondicionado` | O,L | **4** |
| `medidor_servicios` | L,O,A | **4** |
| `sistema_contra_incendios` | A,L,O | **4** |
| `extraccion_humos` | L | **4** |
| `agua_desague` | T,A | **5** — reemplazo de `servicios_disponibles` |
| `energia_electrica` | T | **5** — ídem |

Las dos de abajo van al Corte 5 porque **allí** es donde `servicios_disponibles`
pasa a `activo = false` y donde entra la guarda «ninguna LISTA sin vocabulario»
extendida a PROPIEDAD. Separarlas antes deja un agujero de captura.

---

## 5. Lo que este borrador deja MEDIDO para el Corte 5 — y es un hallazgo

**El Corte 5 fallará en su propia migración si se ejecuta como está escrito.**

`estado_ocupacion` es el hecho del par `entrega_desocupado`. Medido hoy:

```
entrega_desocupado   sujeto=ENCARGO   A/V  C/V  D/V  L/V  O/V  T/V  X/V   (los SIETE tipos)
```

La auditoría §3.8 planea `estado_ocupacion` para **T,C**. Con el guard 2.2
—que ignora la operación y compara sólo `tipo_propiedad`— el hecho tendría que
cubrir **A, C, D, L, O, T y X**. Sembrado en T,C, `V8x` **lanza excepción** y la
migración no aplica.

Es el mismo error de forma que la medición corrigió en el Corte 3 con
`mascotas_reglamento` (D → C,D), pero mayor: **siete tipos frente a dos**. Y
arrastra el tipo `X`, que `pendientes-brox.md` §2.6 declara sin auditar.

> **Esto es una corrección de documento, no una decisión de producto**: la
> aplicabilidad la manda la condición ya sembrada, no el plan. Se corrige
> `auditoria-profundidad-inmobiliaria.md` §3.8 cuando el Corte 5 se congele.
> **Anotado aquí para que no se descubra a mitad de la migración.**

`lote_minimo_normativo` ← `acepta_venta_fraccionada` cubre sólo **T**: sin
conflicto.

---

## 6. La decisión de producto que este corte SÍ necesita — y que CONTROL no toma solo

La auditoría §3.7 propone **`tipo_acceso` en `L` como `ALT`** — *«único
obligatorio nuevo de L»*. Y §3.7/§3.4 proponen **`PUB` en catorce claves más**
(`clase_edificio`, `metraje_arrendable`, `banos_comunes_piso`, `aforo_itse`,
`certificado_itse`, `area_libre`, `profundidad_patio_maniobras`,
`acceso_vehiculo_maximo`, `muelles_carga`/`tipo_muelle`, `puertas_ingreso` y sus
dos medidas, `capacidad_portante_piso`, `area_oficinas`, `recepcion_edificio`).

**Hoy el sistema tiene cero claves `PUB`** (medido: `catalogo_atributo_tipo` ALT
10 / OPC 86; `catalogo_atributo_operacion` OPC 112). Y `PUB` **no informa: sólo
prohíbe** — cuelga de `exigirPublicable`, que lanza y sale como HTTP 400, y no
existe superficie del cable que reporte una PUB de la PROPIEDAD.

De las 26 propiedades reales, **21 son locales (`L`)**. Un `tipo_acceso` en ALT
las marca a las 21 como incompletas; en PUB, las saca del mercado hasta que
alguien vaya y mire.

**Las tres salidas, y ninguna es obvia:**

| | Qué implica |
|---|---|
| **a. Todo OPC**, como los cortes 2 y 3 | Coherente con lo hecho. El dato entra pero nada lo exige, y la promoción queda para un corte de exigencias con corpus |
| **b. `tipo_acceso` ALT, el resto OPC** | Es la propuesta literal de la auditoría. Marca 21 de 26 propiedades como incompletas, sin sacarlas del mercado |
| **c. Escalonar PUB** sobre parte del bloque logístico (`A`) | El almacén es donde el dato faltante rompe operaciones ya negociadas — y hoy hay **cero** propiedades `A` en el corpus, así que no afecta a nadie vivo |

**CONTROL no elige.** Es cambio de exigencia y afecta a la publicabilidad del
inventario real: decisión reservada. Se pregunta al congelar el Corte 4.

---

## 7. Reglas que no cambian

Las mismas del Corte 3 (§7 de su encargo): sin defectos, nada inferido, nada
retroactivo; `catalogo_atributo_operacion` no se toca desde una clave de
PROPIEDAD; `requerido` espejo exacto de `ALT`; vocabulario obligatorio en toda
LISTA nueva; `clean install` del `controllocal-app` tras tocar migraciones; los
cuatro gates del build; Angular no se toca.

Y, ya vigente desde `3.a`: **`Verificar-Cierre.ps1` ejecuta el gate `.sql`**, con
su suelo de claves del sistema y la invariante de aplicabilidad. Este corte lo
volverá a subir — y con el suelo, ya no hace falta tocarlo.

---

## 8. Lo que falta antes de congelar esto

1. Que el Corte 3 cierre y se sepa en qué `orden` acabó.
2. La decisión de §6 (exigencias del bloque comercial).
3. Redactar las tablas clave-por-clave de §3.5 y §3.7 con tipo, unidad,
   `valor_minimo`/`longitud_maxima` y vocabulario, al nivel de detalle del
   encargo del Corte 3 — la auditoría los tiene, falta transcribirlos y
   contrastar cada aplicabilidad contra la base.
4. Decidir si `en_esquina` (L,O,A) y `via_de_acceso` (A,T) entran aquí o esperan
   al Corte 5 por su lado `T`.
