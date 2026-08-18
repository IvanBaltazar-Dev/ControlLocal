# Auditoría de residuos semánticos del SPA

**Abierta:** 2026-08-18, durante la corrección del Corte 2.
**Por qué existe:** el menú pasó a decir «Propiedades» y al entrar el producto
seguía diciendo «LOCALES COMERCIALES / NUEVO», «Registrar local comercial»,
«Propietario del local». Eso no es generalizar: es renombrar el menú.

**La regla que gobierna la clasificación:** si BROX dice *Propiedades*, el
producto tiene que comportarse como Propiedades. Y su contraria, que importa
igual: **no sustituir «local» por «propiedad» a ciegas**, porque produce
formularios absurdos —un terreno no tiene rubro ni galería, y una casa en venta
no tiene renta—.

---

## 1. Qué se buscó, y qué se encontró

Recuento sobre `frontend-angular/src/app`, sin ficheros `.spec.ts`.

| Familia | Ocurrencias | Ficheros |
|---|---|---|
| `local` / `locales` (palabra) | **302** | 108 |
| `rubro` | 111 | 30 |
| `renta` | 117 | — |
| `alquiler` | 65 | — |
| `arrendamiento` | 1 | — |
| «local comercial» literal | 4 | — |
| Texto **visible** con semántica antigua (solo `.html`) | **45 líneas** | — |
| «renta» en texto **visible** | 42 líneas | — |

**Los seis ficheros que concentran el problema:**

| Fichero | Ocurrencias |
|---|---|
| `features/local-form/local-form.ts` | 49 |
| `features/ficha-propiedad/ficha-propiedad.ts` | 40 |
| `core/api/locales.service.ts` | 36 |
| `features/local-detail/local-detail.ts` | 29 |
| `features/locales/locales.ts` | 25 |
| `features/captacion-form/captacion-form.ts` | 22 |

---

## 2. Las cuatro categorías

Cada aparición cae en una, y **la categoría decide qué se hace con ella**.

### A · Específica de LOCAL — se conserva

`rubro`, `rubroPermitido`, `apto para licencia de funcionamiento`,
`carga eléctrica`, `nombre del edificio o galería`, `altura libre`.

Son atributos reales de un local, una oficina o un almacén. **No se
generalizan**: se muestran cuando el tipo de propiedad los tiene, y el catálogo
(`catalogo_atributo`, V48) ya sabe a qué tipos aplica cada uno. La pantalla
pregunta al catálogo; no lleva la lista escrita.

### B · Específica de ALQUILER — se conserva, condicionada a la operación

`renta`, `renta mensual`, `meses de garantía`, `meses de adelanto`,
`plazo del contrato`.

Existen y son correctas **cuando la operación es ALQUILER**. En una venta no se
piden, y llamar «renta» al precio de venta es el error que
`OperacionInmobiliaria.nombreDelImporte()` ya evita en el backend.

### C · Concepto general — pasa a PROPIEDAD

«el local», «los locales», «datos del local», «Registrar local comercial»,
«Propietario del local», «Dirección exacta y distrito del local comercial»,
`/locales`, `LocalesService`, `local-form`, `local-detail`.

Aquí «local» significaba «el inmueble», y con siete tipos en cartera eso ya no
se sostiene.

### D · Deuda histórica — se elimina

«Gestión comercial de locales» (subtítulo del login), «Catálogos del sistema»,
el literal `Panel` del cascarón.

---

## 3. Lo que ya se hizo en esta pasada

| Cambio | Categoría | Estado |
|---|---|---|
| Ruta canónica `/propiedades` + 4 redirects temporales desde `/locales` | C | ✅ |
| Menú: Locales→**Propiedades**, Dashboard→**Inicio**, Cierres exitosos→**Contratos** | C | ✅ |
| Literal `Panel` del cascarón | D | ✅ eliminado |
| Pantalla «Catálogos del sistema» (entrada, ruta y componente) | D | ✅ eliminada |
| Cuenta al menú de la identidad, sin pie duplicado en el lateral | — | ✅ |
| Tamaño de página único (8) y rango siempre visible | — | ✅ |
| Cinco entradas de cola/alcance fuera del menú, con su gate intacto | — | ✅ |

**Suite Angular: 565/565.**

---

## 4. Lo que falta, y es lo que de verdad generaliza

### 4.1 El alta (`local-form`, 49 ocurrencias) — lo más importante

Hoy `+ Registrar → Propiedad` termina en un formulario de local comercial. Debe
arrancar por lo que determina todo lo demás:

```
Nueva propiedad
Registra la propiedad y define cómo será gestionada comercialmente.

  ¿Qué operación realizarás?     Venta · Alquiler · Venta y alquiler
  ¿Qué tipo de propiedad es?     Local · Departamento · Casa · Terreno
```

Y **a partir de ahí** el resto de pasos. Los campos salen de
`GET /captura` —que ya responde qué aplica y qué falta para un tipo dado— y no
de condiciones escritas en la pantalla. Es el mismo motor que consume KAIROS,
así que hacerlo aquí lo arregla en los dos sitios.

> **Ya está probado que el backend lo sostiene:** para un DEPARTAMENTO en VENTA
> devuelve `["titulares","direccion","metraje_total","dormitorios"]` — no pide
> zonificación ni rubro. Lo que falta es que la pantalla se lo pregunte.

### 4.2 Ficha y detalle (`ficha-propiedad` 40, `local-detail` 29)

Mismo criterio: el bloque de rubro/galería solo para L/O/A; el de renta y
garantías solo cuando el encargo es de alquiler; el importe se rotula según la
operación.

### 4.3 Identificadores de código

`locales.service.ts` → `propiedades.service.ts`, `local-form` → `propiedad-form`,
`local-detail` → `propiedad-detail`, `LocalesService` → `PropiedadesService`.
Es mecánico, pero **va después** de 4.1 y 4.2: renombrar ficheros antes de
arreglar su contenido solo mueve el problema de sitio.

### 4.4 Las dos uniones pendientes

`Interacciones` dentro del expediente y `Reportes` como pestaña de
`Indicadores`. Son las dos entradas que mantienen las cuentas en 15/17/19 en vez
de 13/15/16.

### 4.5 Deuda menor

Subtítulo del login («Gestión comercial de locales»), y el patrón de progreso
por pasos —que hoy solo tiene el alta de propiedad— extendido al resto de altas.

---

## 5. Criterio de cierre

**No vale un grep + replace.** El cierre es funcional:

- registrar un **terreno en venta** sin que la pantalla pida rubro, dormitorios,
  galería ni renta;
- registrar un **departamento en alquiler** y que pida dormitorios y baños pero
  no rubro;
- registrar un **local en venta y alquiler** y que abra **dos encargos** con dos
  precios y dos históricos;
- y que ninguna de las tres pantallas mencione «local» salvo cuando el tipo
  elegido sea, efectivamente, un local.
