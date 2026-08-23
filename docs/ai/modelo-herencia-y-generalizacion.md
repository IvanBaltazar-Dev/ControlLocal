# Modelo de dominio: herencia, generalización y subtipado (análisis para rediseño de BD)

> **HISTÓRICO — NO GOBIERNA EL ROADMAP ACTUAL.** (banner añadido 2026-08-22)
> Es anexo de `estado-actual-control-local.md`, que ya es histórico, y su
> evidencia sale de dos cosas que **ya no existen**: las clases de
> `backend-java/` y el esquema MySQL `01_create_schema_controllocal.sql`, ambos
> borrados del árbol el 2026-08-08.
>
> **Se conserva por su punto B**, que resultó ser el acierto del documento: la
> generalización `Propiedad` sobre `LocalComercial` es exactamente lo que
> D-E4-1 (`decision-modelo-universal-propiedad-operacion.md`) construyó después.
> Lo vigente sobre el modelo está ahí y en `decision-autoridad-de-cada-dato.md`;
> aquí sólo está el **porqué** original.

> Anexo del Doc 1 (`estado-actual-control-local.md`). Alimenta el inventario backend (Doc 3), la arquitectura objetivo (Doc 5) y los riesgos (Doc 6).
> Foco: los tres puntos de modelado pedidos — (A) línea de herencia desde `Persona`, (B) generalización `Propiedad`/`Inmueble` sobre `LocalComercial`, (C) subtipado/partición de `InteraccionComercial`.
> Evidencia obtenida en solo-lectura con Serena (LSP java) + esquema `01_create_schema_controllocal.sql`. Se citan clases y tablas reales.

---

## A. Jerarquía de personas — estrategia mixta e inconsistente

### A.1 Evidencia (clase Java vs tabla)

| Concepto | Clase Java | ¿Hereda? (Java) | Tabla | PK | Vínculo al "padre" (DB) |
|---|---|---|---|---|---|
| Persona | `Persona` | — (base) | `persona` | `id_persona` | discriminador `tipo_persona` ∈ {N,J} |
| Propietario | `Propietario` | **NO** — campo `persona` (composición) + getters delegados | `propietario` | `id_propietario` | `id_persona` **UNIQUE** FK → persona |
| Cliente interesado | `ClienteInteresado` | **NO** — campo `persona` | `cliente_interesado` | `id_cliente` | `id_persona` **UNIQUE** FK → persona |
| Usuario interno | `UsuarioInterno` | **NO** — campo `persona` + getters delegados (`getNombres`, `getCorreo`…) | `usuario_interno` | `id_usuario` | `id_persona` **UNIQUE** FK → persona |
| Broker | `Broker extends UsuarioInterno` | **SÍ** (herencia) | `broker` | `id_broker` | `id_usuario` **UNIQUE** FK → usuario_interno (ON DELETE CASCADE) |
| Agente | `AgenteInmobiliario extends UsuarioInterno` | **SÍ** (herencia) | `agente_inmobiliario` | `id_agente` | `id_usuario` **UNIQUE** FK → usuario_interno (ON DELETE CASCADE) |

### A.2 Topología real

```
Persona  (id_persona, tipo_persona ∈ {N,J})
  │  (COMPOSICIÓN: campo `persona` + delegación manual de getters; DB: id_persona UNIQUE FK)
  ├── Propietario          (id_propietario)      has-a Persona
  ├── ClienteInteresado    (id_cliente)          has-a Persona
  └── UsuarioInterno       (id_usuario, rol∈{B,A})  has-a Persona
         │  (HERENCIA en Java: extends; DB: id_usuario UNIQUE FK)
         ├── Broker              (id_broker)      is-a UsuarioInterno
         └── AgenteInmobiliario  (id_agente)      is-a UsuarioInterno
```

### A.3 Diagnóstico
- **Dos estrategias distintas en la misma jerarquía**: `Persona → {Propietario, Cliente, UsuarioInterno}` es **composición/delegación** (has-a); `UsuarioInterno → {Broker, Agente}` es **herencia** (is-a). No hay una regla única.
- **En la BD no hay herencia real en ninguna parte**: siempre es **asociación 1:1** (surrogate PK propia + FK `UNIQUE`), nunca **PK compartida** (donde la PK del hijo *sería* la FK al padre). Es "table-per-type por asociación".
- **La "línea de herencia desde Persona" está simulada, no modelada**: `Propietario.getNumeroDocumento()` delega en `persona.getNumeroDocumento()`; no hay polimorfismo real.
- **Identidad fragmentada**: un broker tiene 3 identificadores (`id_persona`, `id_usuario`, `id_broker`). Reconstruir un broker completo = `persona ⋈ usuario_interno ⋈ broker` (2 joins). Un propietario completo = `persona ⋈ propietario`.

### A.4 Consecuencias por requisito
- **RC-002 (auditabilidad)**: las acciones referencian `id_agente`/`id_usuario`; rastrear "qué persona hizo qué" exige joins y el rol vive aparte (`usuario_interno.rol`). Complica el "quién" del historial.
- **RC-003 (rendimiento)**: consultas de cartera/seguimiento que muestran nombre/persona pagan joins de 2–3 tablas por fila.
- **Integridad**: `usuario_interno.rol ∈ {B,A}` **no está atado** a la existencia de la fila `broker`/`agente` correspondiente → posible incoherencia (rol='B' sin fila broker, o ambas).
- **Regla de negocio latente**: `propietario.id_persona UNIQUE` y `cliente_interesado.id_persona UNIQUE` permiten que **una misma persona sea propietario y cliente** (filas en ambas tablas) — bien; pero no hay un concepto explícito de "persona con múltiples roles".

### A.5 Opciones de rediseño (decidir en Doc 5)
1. **Shared-PK table-per-type (is-a real)** — la PK del hijo *es* la FK al padre (`propietario.id_persona` PK+FK; `usuario_interno.id_persona` PK+FK; `broker.id_usuario` PK+FK). Ventaja: una sola identidad por nivel, joins triviales, is-a limpio. Coste: rehacer PKs y todas las FKs entrantes.
2. **Single-table con discriminador** — una tabla `persona` con `tipo_persona` + rol + columnas nullable por subtipo. Ventaja: cero joins. Coste: columnas dispersas, checks por subtipo.
3. **Patrón Party–Role** — `Persona`(=party) + tabla de **roles** (propietario, cliente, usuario, broker, agente) que una persona puede acumular. Ventaja: natural en real estate (una persona puede ser dueño *y* cliente *y* usuario); modela explícitamente los roles y su vigencia. Coste: más tablas de rol, consultas por rol.
4. **Mantener asociación pero unificarla y blindarla** — dejar 1:1 por FK, pero (a) misma convención en toda la jerarquía, (b) trigger/constraint que ligue `rol` con la existencia del subtipo. Cambio mínimo, menos limpio.

> Recomendación preliminar (a validar): **Party–Role (opción 3)** encaja con RC-002 (auditar por persona/rol) y con la realidad del negocio; si se busca cambio menor, **shared-PK (opción 1)** al menos vuelve la herencia real y consistente.

---

## B. `LocalComercial` sin generalización `Propiedad`/`Inmueble`

### B.1 Evidencia
- `LocalComercial` **no tiene superclase**; **no existe** clase `Propiedad`/`Inmueble` base (grep del módulo model: solo `LocalComercial`, `PrecioLocal`, `Publicacion`, `Distrito`, `FotoLocal` en `inmueble`).
- La tabla/entidad ya mezcla **dos familias de atributos**:
  - **Genéricos de cualquier inmueble**: `metraje`, `direccion`, `id_distrito`, `tipo_inmueble`, `uso`, `ambientes`, `antiguedad_anios`, `zona_urbanizacion`, `geo_lat`, `geo_long`, `frente`, `zonificacion`, `numero_estacionamientos`, `cuota_mantenimiento`.
  - **Específicos de local comercial**: `rubro_permitido`, `precio_referencial`, `apto_licencia_funcionamiento`, `carga_electrica_kw`.
- Ya hay **discriminador** `tipo_inmueble CHAR(1)` con CHECK ∈ {'L','O','D','C','T','X'} (respaldado por enums `TipoInmueble` **y** `TipoInmuebleComercial` — **dos enums que se solapan**), y `uso CHAR(1)` ∈ {'C','V','I','M'} (`UsoInmueble`). Es decir, el esquema **ya anticipó varios tipos de inmueble** metidos en una sola tabla-gorda.
- **Redundancia de distrito**: coexisten `distrito VARCHAR(100)` (nombre denormalizado) **y** `id_distrito BIGINT` FK → `distrito`. Doble fuente de verdad.

### B.2 Diagnóstico
- `LocalComercial` es, de facto, **"una Propiedad especializada como local comercial"**, pero modelada como tabla única con discriminador y **sin generalización explícita**. La abstracción `Propiedad`/`Inmueble` que pides **falta** aunque sus atributos ya están presentes.
- Extraer `Propiedad`/`Inmueble` (atributos genéricos + `tipo_inmueble` + geo + físicos) con `LocalComercial` (y futuros `Oficina`, `Deposito`, `Terreno`) como especializaciones deja el modelo extensible y limpio.

### B.3 Consecuencias por requisito
- **RC-004 (operabilidad)**: hoy solo hay "locales"; una generalización habilita catálogos multi-tipo sin duplicar formularios.
- **Integridad/claridad**: dos enums de tipo (`TipoInmueble` vs `TipoInmuebleComercial`) generan ambigüedad de dominio; `distrito` string vs FK puede divergir.

---

## C. `InteraccionComercial` — polimórfica por `contexto` (subtipar/particionar)

### C.1 Evidencia
- `InteraccionComercial` (una sola clase/tabla) con **discriminador `contexto`** ∈ {OPORTUNIDAD, PROSPECCION, CAPTACION, CLIENTE} y **4 FKs nullable** (`id_oportunidad`, `id_prospeccion`, `id_captacion`, `id_cliente`) + `id_agente`.
- CHECK `ck_interaccion_entidad` obliga a que **exactamente una** FK esté presente según el `contexto`.
- **`resultado VARCHAR(30)` depende del contexto**: el CHECK define **4 conjuntos de resultados distintos** (uno por contexto). O sea, cuatro máquinas de resultado conviviendo en una columna.
- **Índices** (corrección tras leer el DDL completo): **sí existen** compuestos `(contexto, id_oportunidad, fecha_hora)` y equivalentes por prospección/captación/cliente, más `(contexto)`, `(fecha_hora)` y uno por FK. → Las consultas por contexto+entidad **ya están servidas por índice**; no es un problema de índices faltantes.
- **`visita` es casi-hermana**: comparte el dominio de `interaccion_comercial.resultado` ("¿debemos darle seguimiento?") y añade campos propios (`nivel_interes`, `objecion_principal`, `opinion_precio`, `proxima_accion`). Forma parte de la familia "punto de contacto".

### C.2 Diagnóstico
- Diseño **single-table polymorphic**: una tabla para 4 tipos semánticamente distintos, con `resultado` de **4 dominios** en una sola columna. Correcto y **bien indexado** (compuestos por contexto). El caso a favor de subtipar es de **claridad de modelo** y **escala física**, no de índices faltantes.
- Tu propuesta (dividir por tipo) es sólida para **RC-003**. Opciones:
  1. **Partición por `contexto`** (MySQL `PARTITION BY LIST/COLUMNS(contexto)`) — poda de particiones por contexto, mantiene una sola entidad lógica. Cambio de menor impacto en la app.
  2. **Tablas por contexto** (`interaccion_oportunidad`, `_prospeccion`, `_captacion`, `_cliente`) — separación física total; DAOs/consultas por tipo. Más limpio, más refactor.
  3. **Índices compuestos**: ya existen `(contexto, id_*, fecha_hora)`; margen adicional sería tuning fino, no un hueco.
- Considerar **formalizar la familia "touchpoint"**: `interaccion` + `visita` como subtipos de un "contacto comercial" (o al menos documentar el parentesco que hoy es implícito vía dominio compartido de `resultado`).

---

## D. Nuevas mejoras detectadas (se integran al registro vivo del Doc 1)

- **MEJ-09 (herencia personas, RC-002/RC-003)** — Unificar la estrategia de la jerarquía de personas (hoy composición para {Propietario, Cliente, UsuarioInterno} vs herencia para {Broker, Agente}; en BD todo es 1:1 por FK). Elegir entre §A.5.
- **MEJ-10 (Party–Role)** — Modelar explícitamente que una persona puede acumular roles (propietario/cliente/usuario), con vigencia, para auditoría y realidad del negocio.
- **MEJ-11 (integridad rol↔subtipo)** — Ligar `usuario_interno.rol` con la existencia de la fila `broker`/`agente` (evitar estados incoherentes).
- **MEJ-12 (generalización Propiedad)** — Extraer `Propiedad`/`Inmueble` genérico; `LocalComercial` pasa a especialización; habilitar futuros tipos (oficina, depósito, terreno).
- **MEJ-13 (distrito redundante)** — Eliminar la duplicidad `local_comercial.distrito` (VARCHAR) vs `id_distrito` (FK): una sola fuente (FK a `distrito`).
- **MEJ-14 (enums de tipo)** — Unificar/eliminar el solape `TipoInmueble` vs `TipoInmuebleComercial`.
- **MEJ-15 (subtipar interacciones, RC-003)** — Evaluar particionar/dividir `interaccion_comercial` por `contexto` por **claridad de modelo y escala** (los índices compuestos `(contexto, id_*, fecha_hora)` **ya existen**); formalizar la familia `interaccion`+`visita`.
- **MEJ-16 (discriminadores)** — Homogeneizar los `CHAR(1)`+CHECK (persona, estados, tipos) hacia `ENUM`/tablas de catálogo para legibilidad e integridad.

---

## E. Decisiones que necesito de ti (antes del Doc 5 / rediseño)
1. **Jerarquía de personas**: ¿Party–Role (§A.5.3), shared-PK is-a (§A.5.1), single-table (§A.5.2) o mínimo cambio (§A.5.4)?
2. **Propiedad**: ¿extraemos `Propiedad`/`Inmueble` genérico con `LocalComercial` como especialización, y qué tipos futuros contemplar?
3. **Interacciones**: ¿partición por `contexto`, tablas separadas, o solo índices? ¿`visita` se integra a la familia o se deja aparte?
