# Encargo — Corte 5 · Terreno y ocupación transversal

**Estado:** 🟡 **EN CURSO — subtanda 5A**
**Congelado por el titular el 2026-08-25**, con las decisiones D-1…D-7 de §2.

> **Sobre el prerequisito de I0.** Este documento decía «Prerequisito: cierre de
> I0 y congelación de este documento por el titular». **Ya no aplica**: el
> titular congeló el corte el 2026-08-25 e **I0 dejó de bloquearlo**. Se deja
> escrito, y no borrado, porque durante un día sí fue la condición vigente.

El nombre operativo es **Corte 5 · Terreno**, pero una parte del corte es
transversal: `estado_ocupacion` debe cubrir los siete tipos porque su condición
`entrega_desocupado` ya se pacta en todos ellos.

## 1. Lo que ya está decidido

La decisión [D-C5-1](decision-estado-ocupacion-en-los-siete.md) manda:

- `estado_ocupacion` nace en A, C, D, L, O, T y X;
- `aplica_todos = false`, con filas explícitas por tipo;
- es `LISTA`, inicialmente `OPC` en los siete;
- vocabulario: `DESOCUPADO`, `OCUPADO_POR_EL_PROPIETARIO`,
  `OCUPADO_POR_INQUILINO`, `OCUPADO_POR_TERCEROS_SIN_TITULO`;
- `CON_EDIFICACION_A_DEMOLER` no pertenece a esta clave;
- `entrega_desocupado` no se estrecha ni se modifica.

> **Matiz de 2026-08-25:** el §5 de esa decisión —el argumento con el que
> justificaba `OPC`— quedó **derogado por medición**: la superficie que reporta
> las `PUB` de la PROPIEDAD **sí existe** desde `35cf09c`. La conclusión `OPC`
> **se mantiene**, pero por **D-1** del titular, no por aquel argumento.

`lote_minimo_normativo` es el hecho del par
`acepta_venta_fraccionada` y sólo aplica a T. **Va en 5B**, no en 5A.

## 2. Las decisiones del titular — D-1…D-7 · 2026-08-25

Congeladas. **No se reabren dentro del Corte 5.**

| # | Decisión |
|---|---|
| **D-1** | **Exigencia.** `agua_desague` y `energia_electrica` son **`PUB` en `T`**. `estado_ocupacion` es **`OPC` en los siete**. **No se eleva nada más a `PUB`** en este corte. |
| **D-2** | **`gas` conserva su concepto** —su clave, su `tipo_dato` y su aplicabilidad— y **gana** `CON_FACTIBILIDAD_APROBADA` como opción adicional. `RED_EN_LA_VIA` = infraestructura física disponible; `CON_FACTIBILIDAD_APROBADA` = aprobación o documento de la concesionaria. **`gas` NO se extiende a `X`** en este corte. |
| **D-3** | **`condicion_terreno` es `PUB`, no `ALT`.** *«BROX debe poder registrar un terreno aunque todavía no se conozca o confirme su condición. No asumir que URBANO_HABILITADO / EN_PROCESO / RUSTICO puede determinarse visualmente.»* Enunciada por el titular el **2026-08-25**. Consecuencia: la clave **pertenece a 5B**, no a 5A, y `auditoria-profundidad-inmobiliaria.md:242` —única `ALT` que la auditoría proponía para el Corte 5— **queda derogada en su eje de exigencia**. `ALT` tiene **dos** puertas (bloquea el alta *y* bloquea publicar); `PUB` sólo la segunda. |
| **D-4** | **5A y 5B son subtandas secuenciales del MISMO Corte 5.** 5A es la de este encargo. **5B no se abre** hasta que 5A esté auditado. |
| **D-5** | **No se modifica `aplica_todos`** de `antiguedad_anios`, `estacionamientos` ni `metraje_total`. A cambio, la **doble autoridad de aplicabilidad** queda anotada como deuda estructural en §2.3 bis de `pendientes-brox.md`, a resolver antes de declarar el Core estable. |
| **D-6** | **`manzana_lote` queda fuera del Corte 5.** Es una decisión estructural pendiente y **no se crea como atributo provisional**. |
| **D-7** | **`area_terreno` se retira de `T`** —no se sube a `PUB`, como proponía la auditoría—: `metraje_total` es la superficie canónica de un terreno. La retirada, con su migración de datos, va en **5B**. |

## 3. Subtanda 5A — alcance exacto · migración `V84`

Sujeto **PROPIEDAD** en todo. Cinco cambios de catálogo y una guarda.

1. **`estado_ocupacion`** — LISTA, A,C,D,L,O,T,X con `aplica_todos = false`,
   **OPC** en los siete, `requerido = false`. Vocabulario de §1.
2. **`agua_desague`** — LISTA · **`PUB` en `T`**, `OPC` en `A`. Opciones:
   `CONECTADO`, `CON_FACTIBILIDAD_APROBADA`, `SIN_SERVICIO`.
3. **`energia_electrica`** — LISTA · **`PUB` en `T`**. Mismas tres opciones.
4. **`gas`** — se le añade **sólo** `CON_FACTIBILIDAD_APROBADA` y se le reescribe
   la `ayuda` para separarla de `RED_EN_LA_VIA` (D-2).
5. **`servicios_disponibles` → `activo = false`.** Nunca `DELETE`.
6. **La guarda «ninguna LISTA/LISTA_MULTIPLE activa de sujeto PROPIEDAD sin
   vocabulario»**, que hoy sólo cubre ENCARGO y las claves del corte en curso.

**El orden dentro de `V84` es parte del encargo**, no una preferencia de estilo:
invertir 5 y 6 pierde el legado, e invertir 6 y 7 aborta la migración contra su
propia clave.

1. foto del estado previo en tabla temporal explícita, con `DROP` al final —
   **no `ON COMMIT DROP`**, que no sobrevive a cómo Flyway envuelve la
   transacción;
2. nacen `agua_desague` y `energia_electrica` **con** sus opciones;
3. nacen sus filas de aplicabilidad, `requerido = false`;
4. nace `estado_ocupacion` con su vocabulario, y `gas` gana su opción;
5. **sólo entonces**: reparto de lo recuperable de `servicios_disponibles`, con
   linaje de procedencia (`V83`); lo ambiguo queda **FALTANTE** y **se cuenta**;
6. **sólo entonces**: `servicios_disponibles → activo = false`;
7. **sólo entonces**: se extiende la guarda de vocabulario;
8. bloque `DO $$` de aserciones;
9. comparación contra la foto, por **conjuntos**, no por totales.

### 3.1 Invariantes que la migración debe demostrar

- el par `estado_ocupacion` / `entrega_desocupado` queda **cubierto en los
  siete** (aserción propia, exigida por D-C5-1 §7: no basta con que la clave
  exista);
- `requerido` sigue siendo **espejo exacto** de `exigencia = 'ALT'` en todo el
  catálogo;
- enrutamiento por sujeto: PROPIEDAD **no puede** tener filas en
  `catalogo_atributo_operacion`;
- códigos de opción `^[A-Z][A-Z0-9_]*$`;
- **ningún valor de `servicios_disponibles` queda sin destino ni sin declararse
  FALTANTE** — y esto se escribe **como invariante, nunca como la cifra 0**: en
  `controllocal_dev` hay 0 filas, pero en `controllocal_repositorios` (la base de
  pruebas) **sí las hay**, porque un fixture las escribe en cada corrida. Una
  aserción `= 0` pasaría en `dev` y mentiría en pruebas;
- nada de lo que había se perdió.

### 3.2 Lo que 5A **no** toca

Todo 5B (parámetros urbanísticos, `condicion_terreno`, `fondo`,
`tipo_via_acceso`, `situacion_registral`, `lote_minimo_normativo`,
`edificacion_existente`, y la retirada de `area_terreno` en `T`),
`manzana_lote` (D-6), `aplica_todos` de cualquier clave (D-5), la extensión de
`gas` a `X` (D-2) y **Angular**: el SPA no conoce claves —
`MotorDeCaptura.controlDe` deriva el control del vocabulario—, así que si
apareciera necesidad de tocarlo, es `STOP — DECISIÓN REQUERIDA POR CONTROL`.

## 4. `servicios_disponibles` y sus reemplazos — la contradicción, resuelta

`servicios_disponibles` no se retira hasta que existan sus reemplazos, se haya
medido qué valores son recuperables y la captura nueva pueda recibir lo que la
antigua recibía.

Este documento afirmaba, hasta el 2026-08-25, que **«algunos documentos dicen que
`gas` nace en Corte 5 con un tercer estado»**. **Es falso y se corrige**: ningún
documento dice que `gas` nazca en el Corte 5 — `auditoria-profundidad-inmobiliaria.md`
dice literalmente lo contrario, que **«`gas`… ya existe desde `V81`»**. Medido:
`gas` es la clave `362`, LISTA, cinco opciones, aplicable a A,C,D,L,O,T en `OPC`.

**La contradicción real era otra**: tres documentos pedían para `gas` un estado
`CON_FACTIBILIDAD_APROBADA` que **su vocabulario no tenía**. No era una segunda
definición compitiendo con la primera: era una opción que faltaba. **La resuelve
D-2**: `gas` conserva todo lo suyo y **gana** esa opción. No se migra a otro
concepto, no se retira ningún valor existente y no se inserta una segunda
definición.

## 5. Reglas de implementación

- No inventar valores ni rellenar el legado por frecuencia. **`SIN_SERVICIO` no
  es la traducción de «no lo mencionó».**
- Crear vocabulario **antes** de retirar una LISTA muda.
- Migrar sólo lo recuperable; lo ambiguo queda FALTANTE **y se cuenta**.
- `requerido` debe seguir siendo espejo exacto de `exigencia = ALT`.
- Ningún campo `PUB` se introduce sin una superficie que informe el faltante —
  y esa superficie **existe** desde `35cf09c`: `PropiedadResponse.faltanParaPublicar`.
- No retirar `servicios_disponibles` antes de que sus reemplazos estén activos.
- Web y KAIROS deben recibir la misma definición del Core.
- La conservación debe cubrir alta, edición, lectura y reescritura de
  `LISTA_MULTIPLE` cuando la migración toque ese almacenamiento.

## 6. Cierre exigido de 5A

- gate SQL dentro de `Verificar-Cierre.ps1`, **validado por sabotaje en las dos
  direcciones** y con la evidencia guardada;
- pruebas de aplicabilidad de los siete tipos;
- el par hecho/condición cubierto en los siete;
- vocabulario de las tres claves de servicios y de la opción nueva de `gas`;
- `ConservacionDeLaEdicionIntegrationTest` extendido —obligatorio desde `V81`
  para todo corte—, con su fixture de TERRENO **reescrito**, no borrado;
- `CatalogoQueHablaIntegrationTest.serviciosDisponiblesNoSeRompio()`
  **reescrito** para afirmar que la clave está retirada y sustituida: hoy afirma
  que sigue aceptando texto libre y que sigue con 0 opciones, y tras 5A eso es
  falso **por diseño**. No se borra: se perdería la constancia de por qué existió;
- procedencia: que el reparto del legado deje linaje;
- evidencia con medición **antes/después**, incluido que el terreno `PROP-0024`
  pasa de publicable a bloqueado por dos claves `PUB` — **es el efecto buscado**;
- auditoría adversarial independiente y SHA candidato.

**El corte no lo cierra quien lo implementa.** Y 5B no se abre hasta entonces
(D-4); Corte 6 y Corte 7, después de 5B.

## 7. El hueco de D-3, cerrado

Este documento decía, hasta el 2026-08-25: *«**D-3 no ha llegado.** CONTROL
enunció D-1, D-2, D-4, D-5, D-6 y D-7, y pidió incorporar “las siete decisiones
D-1…D-7”. **D-3 no se infiere**: queda su hueco marcado en la tabla de §2 y el
encargo se completa cuando CONTROL la enuncie.»*

**CONTROL la enunció el mismo 2026-08-25** y está transcrita literalmente en §2.
La negativa a inferirla fue correcta y se deja escrita: durante unas horas el
hueco fue el estado real del encargo, y borrarlo haría parecer que nunca faltó.

**Lo que D-3 cambia del reparto 5A/5B, dicho aquí para que no se busque:**
`condicion_terreno` era la **única** clave que la auditoría proponía en `ALT`
(`auditoria-profundidad-inmobiliaria.md:242`). Al bajarla a `PUB` deja de ser
una clave que impida **registrar** un terreno, y con ello deja de haber ninguna
razón para adelantarla: **entra en 5B con el resto de parámetros urbanísticos**.
5A no la toca.
