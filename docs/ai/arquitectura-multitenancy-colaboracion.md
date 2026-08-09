# Arquitectura multi-tenant + red colaborativa (ControlLocal Network)

> Resuelve el **bloqueante de multi-tenancy** que abrió `diseno-contacto-comercial-ai-ready.md` §12.1.
> Decisión estratégica del usuario del **2026-07-19**, alineada al North Star de SIVAN.
> Este documento **entiende y registra** la decisión, y **anota su impacto sobre el código ya
> construido** (token JWT congelado, Party-Role, `Alcances`, esquema V1–V5). No hay implementación aún.
> Relacionados: `diseno-contacto-comercial-ai-ready.md`, `arquitectura-objetivo-java-fullstack.md`,
> `plan-migracion-java-fullstack.md`, North Star (`F:\Products_SIVAN\…\02_North_Star.docx`).

---

## 1. La decisión en una frase

ControlLocal será un **SaaS multi-tenant con aislamiento estricto por organización**, sobre el cual
opera una **red colaborativa opcional, gobernada y auditable** (modelo tipo MLS), más una **capa de
inteligencia agregada y anonimizada**. No es "CRM aislado" ni "plataforma abierta": es el punto medio.

```
Núcleo privado por corredora  +  Red colaborativa por objeto  +  Inteligencia agregada anónima
   (aislamiento = confianza)      (colaboración = velocidad)       (agregado = moat SIVAN)
```

Encaja con el North Star: el moat no es acumular conversaciones privadas, es **coordinar operaciones
reales y aprender de sus desenlaces sin destruir la confianza de quien aporta el dato**.

---

## 2. El principio: aislamiento operativo + colaboración por objeto

Las organizaciones quedan **completamente aisladas**. Lo que se comparte **no es el tenant**, son
**objetos concretos con permiso explícito y reversible**. La competencia nunca ve el CRM ajeno:
ve proyecciones autorizadas.

**Regla de oro**: la colaboración **nunca** otorga acceso a las tablas privadas de otra organización.
Se hace mediante **proyecciones** que exponen solo los campos autorizados. (Detalle §6.)

---

## 3. Impacto sobre lo YA construido (lo que hay que conciliar)

Aquí es donde esta decisión toca código real. Cuatro puntos, ordenados por gravedad:

### 3.1 🔴 El token JWT está congelado y NO lleva organización

`TokenService` emite claims `{usuario, rol, idUsuario, idDominio, exp}` con secreto HS256
**byte-compatible con el backend Jakarta** (SSO durante la convivencia Strangler). Añadir
`idOrganizacion` al token **rompe esa compatibilidad**, y el backend viejo **no tiene tenancy**.

Consecuencia dura: **el multi-tenancy real solo puede activarse en módulos ya cortados a Spring**, o
asumiendo un **tenant único durante la convivencia**. No se puede tener a la vez "token byte-compatible
con GlassFish" y "token con organización". Esto es una decisión de secuenciación, no un detalle
(ver Pregunta 3).

### 3.2 🟡 Party-Role ya es el lugar natural del tenant — pero abre una tensión

`persona` (identidad Party) y `persona_rol` (rol operativo con vigencia) ya existen. El encaje limpio:

- **`persona` = identidad global** (un humano, su documento). NO lleva `organizacion_id`.
- **`persona_rol` = el rol dentro de una organización.** AQUÍ vive `organizacion_id`.

Esto modela con naturalidad que **una persona sea cliente de la corredora A y propietario con la
corredora B** — cada relación es un `persona_rol` en su tenant. Es elegante y ya está medio construido.

**Pero** genera una tensión real con el aislamiento (§4): si `persona` es una fila global compartida y
el teléfono vive ahí, la corredora B podría ver el contacto de un cliente que capturó A. El usuario
pidió a la vez **dedup global de identidad** ("mismo documento → fusión") **y** **aislamiento del
teléfono** ("compartir teléfono: aceptación baja"). Esos dos objetivos chocan justo en `persona`.
Es la decisión más profunda de todo el diseño → Pregunta 1.

### 3.3 🟡 `Alcances` gana una dimensión previa al rol

Hoy `Alcances` resuelve global/agente/broker (RC-001). Con tenancy, **todo empieza con un filtro por
`organizacion_id` ANTES del filtro por rol**. El alcance pasa de 1 eje a 2: `(tenant, rol)`. Cada query
del paquete `query` hereda el predicado de tenant. Conviene que sea infraestructura (§7), no que cada
query lo repita y una se olvide — que es exactamente el tipo de fuga que no se puede permitir aquí.

### 3.4 🟢 Visibilidad de colaboración ≠ estado de publicación existente

Ya existe `publicacion` con estados (borrador/publicado). El **nivel de visibilidad**
(INTERNA / RED_COLABORATIVA / PÚBLICA, §5) es un **eje distinto** y no debe fundirse con aquel: una
propiedad puede estar "publicada" internamente sin estar "en la red". Son dos columnas, no una.

### 3.5 🟢 El consentimiento booleano actual es insuficiente

`persona.consentimiento_uso_dato` es un `BOOLEAN`. El modelo pide **tres permisos separados**
(`uso_operativo`, `uso_analitico_agregado`, `uso_mejora_modelos`, §8) + finalidad + fecha + versión de
términos. Hay que modelar consentimiento **granular y versionado**, no un flag. Aceptar el SaaS ≠
aceptar el entrenamiento.

### 3.6 🔴 Todos los índices ÚNICOS de V1 son globales — hay que reescribirlos por-tenant

Verificado en `V1__identidad_party_role.sql`. Con D-18 (misma persona real en dos corredoras), cada
unique global **rompe el modelo**: la corredora B no podría registrar a alguien que ya registró A, y el
propio fallo del `INSERT` **le revelaría que la persona existe en otra org** (fuga por canal lateral).

| Constraint actual (global) | Debe pasar a |
|---|---|
| `uq_persona_documento (tipo_documento, numero_documento)` | `(organizacion_id, tipo_documento, numero_documento)` |
| `uq_persona_correo (correo)` | por-tenant, y el correo se muda a `persona_contacto` (§4.1) |
| `credencial_usuario.nombre_usuario UNIQUE` | login único **por org** (o global con la org como sufijo) → **Pregunta A** |
| `detalle_broker.codigo_broker`, `detalle_agente.codigo_agente` | por-org (cada corredora numera sus BRK-/AGE- desde 001) |
| `uq_broker_admin_unico WHERE es_administrador` (**un admin GLOBAL**) | **un admin por org** → `(organizacion_id) WHERE es_administrador` |
| `uq_persona_rol_vigente (id_persona, tipo_rol)` | intra-persona, ya queda dentro del tenant vía `persona` |

`uq_broker_admin_unico` es el más sutil: hoy garantiza "un solo administrador en todo el sistema";
bajo multi-tenant debe ser "un administrador por organización". Es un cambio de significado, no solo de
columnas.

### 3.7 🔴 D-22 separa la CUENTA DE ACCESO (global) de la PERSONA (por-tenant) — hay que partir `credencial_usuario`

D-18 dice `persona` por-tenant; D-22 dice que el **usuario que hace login tiene una única cuenta global**
(correo/teléfono/IdP) con **membresías por organización**. No se contradicen, pero obligan a **separar dos
conceptos que hoy están fundidos**:

- Hoy `credencial_usuario` cuelga de `persona_rol` (PK compartida `id_persona_rol`), que bajo D-18 es
  **por-tenant**. Login atado a una identidad por-tenant ⇒ incompatible con "una sola cuenta global".
- Bajo D-22 hay que distinguir:
  - **`cuenta_acceso`** (global): correo/login/IdP + hash de contraseña. Autentica el "quién eres".
  - **`usuario_organizacion`** (membresía por org): rol operativo, nombre visible, estado, y el vínculo a
    la `persona`/`persona_rol` **por-tenant** de esa organización. Autoriza el "qué puedes hacer aquí".

Flujo: 1 sola organización ⇒ BROX entra directo; N organizaciones ⇒ el usuario **selecciona el contexto
activo**. **La existencia de la cuenta en otra organización nunca se revela entre tenants** (coherente con
D-18).

**Encaje temporal (con D-20)**: durante la convivencia (tenant único), `credencial_usuario` **se deja como
está**. La cuenta global + membresías **es exactamente el "nuevo contrato de autenticación" que D-20 emite
al retirar GlassFish** — no antes, porque cambia el token. Es decir, D-22 se materializa en el corte, no en
la V6.

> Matiz importante: esto aplica a **usuarios internos** (broker/agente que hace login). Los
> **clientes/propietarios** (que no autentican) siguen siendo `persona` pura por-tenant (D-18). Queda una
> decisión: ¿el usuario interno tiene además una `persona` por-tenant (para aparecer en operaciones de esa
> org), o su identidad operativa es solo cuenta-global + membresía? → **Pregunta E**.

---

## 4. Modelo de identidad: RESUELTO — aislamiento máximo + bóveda de red (D-18)

**Decisión (D-18): `persona` pertenece EXCLUSIVAMENTE a una organización. No existe persona comercial
global compartida.** La misma persona real es `persona 184` en la corredora A y `persona 927` en la B;
para BROX son dos relaciones comerciales independientes y **ninguna organización puede descubrir que la
persona también existe en otra**. Es la variante más fuerte del aislamiento (era la opción B de la
pregunta, reforzada).

El dedup/matching entre organizaciones **no vive en el dominio operativo**, sino en una **bóveda de
identidad de red separada, propiedad de SIVAN** (no de BROX):

```
identidad_red         (id, estado, creado_en)
identidad_red_token   (id, identidad_red_id, tipo_token, token_hmac, version_clave, estado)
persona_identidad_red (id, organizacion_id, persona_id, identidad_red_id,
                       finalidad, estado_autorizacion, creado_en, revocado_en)
```

- Los tokens son **HMAC-SHA-256(clave_privada_SIVAN, valor_normalizado)**, **no hash simple** — un
  DNI/teléfono es predecible y un hash pelado sería reversible por fuerza bruta. La clave secreta lo
  impide y `version_clave` permite rotación.
- La bóveda **solo responde** *"¿existen coincidencias autorizadas para esta búsqueda/propiedad?"*.
  **Nunca** responde *"¿en qué corredoras aparece esta persona?"*, *"¿cuántas propiedades tiene?"*,
  *"¿qué conversaciones mantuvo?"* ni *"¿cuál es su teléfono en otra organización?"*.
- La bóveda **no es accesible** desde los índices RAG, desde las consultas normales de BROX ni como
  contexto directo de KAIROS. Una coincidencia **no modifica** las personas locales ni revela
  participación cruzada: solo puede generar un `match_colaborativo`, una comprobación de duplicidad
  autorizada o un dato analítico disociado, con finalidad registrada.

Consecuencia sobre el dedup del bot (**cierra la Pregunta 6 anterior**): KAIROS deduplica **solo dentro
del tenant receptor**; nunca busca personas de otras organizaciones para vincularlas al CRM local.

### 4.1 Estructura de persona bajo D-18

`organizacion_id` se **denormaliza en cada tabla de identidad** (no solo en `persona`), para que la
política de aislamiento (§7) sea directa sobre cada tabla sin joins:

```
persona          (id, organizacion_id, nombres, apellidos, estado_identidad, origen_creacion, …)
persona_rol      (id, organizacion_id, persona_id, tipo_rol, …)
persona_contacto (id, organizacion_id, persona_id, tipo, valor_cifrado, valor_normalizado, verificado)
```

`persona_contacto` separa el contacto (teléfono/correo) en su propia tabla, **cifrado** en reposo, con
`valor_normalizado` para el matching intra-tenant. Reemplaza los campos `telefono`/`correo` sueltos de
`persona` en la v1.

> ⚠️ **Esto cambia el significado de `persona` en el Party-Role ya construido.** Hoy (V1) `persona` es
> la identidad Party **global** y el Doc 5 la trata como "identidad única del actor". Bajo D-18 la
> unicidad es **por organización**, no global. No rompe el token (`idUsuario = persona.id` sigue siendo
> válido dentro del tenant), pero hay que matizar el Doc 5: Party-Role sigue vigente **dentro** del
> tenant. Ver §3.6.

---

## 5. Tres niveles de visibilidad por propiedad

En vez de `privada = true/false`, una política explícita por propiedad:

| Nivel | Quién la ve | Cuándo |
|---|---|---|
| **INTERNA** | Solo la organización captadora | Propietario exige confidencialidad; documentación en validación; negociación avanzada; sin autorización de colaboración |
| **RED_COLABORATIVA** | Organizaciones autorizadas de la red | Autorizada para colaborar: se exponen tipo, distrito (precisión configurable), área, precio autorizado, características, disponibilidad, **estado** documental (no el archivo), agente captador |
| **PÚBLICA** | Portales, landing, integraciones | Autorizada para difusión pública |

Transición `INTERNA → RED_COLABORATIVA → PÚBLICA`. **Compartir en la red NO implica publicar en
internet** — son escalones independientes.

**Nunca** se comparte automáticamente: teléfono del propietario, DNI/RUC, documentos registrales
completos, conversaciones, notas internas, urgencia privada, piso mínimo de negociación, comisión
interna.

---

## 6. Capa colaborativa: proyecciones, no acceso cruzado

El aporte mayor del modelo: se conecta **oferta ↔ demanda** sin revelar identidades. La demanda viaja
como **tarjeta anónima**:

```
BUSCA: Local comercial · Cercado de Lima o La Victoria · 80–140 m² ·
       hasta USD 3,000/mes · primer piso · giro farmacia · para septiembre
   (sin nombre, teléfono, correo, empresa, conversaciones ni documentos)
```

El sistema encuentra compatibilidad y avisa a ambos agentes; **la identidad del interesado permanece
bajo custodia de su agente**, incluso tras el match (la visita se coordina por el flujo de
ControlLocal). Esto neutraliza el miedo central del mercado: *que colaborar signifique perder al
cliente*.

Tablas de la capa (todas son **proyecciones CQRS derivadas de las privadas, no fuente de verdad**):

```
publicacion_colaborativa  (organizacion_propietaria_id, captacion_id, nivel_visibilidad,
                           campos_autorizados, estado, vigencia, autorizado_por_propietario)
necesidad_colaborativa    (organizacion_solicitante_id, cliente_id_privado,
                           perfil_busqueda_anonimizado, nivel_confidencialidad,
                           consentimiento_comparticion, estado)
match_colaborativo        (publicacion_id, necesidad_id, score, evidencia, estado)
acuerdo_colaboracion      (organizacion_captadora_id, organizacion_demandante_id,
                           reglas_operativas, responsable_propietario, responsable_comprador, estado)
```

La red consulta **estas** proyecciones; el `cliente_id_privado` de `necesidad_colaborativa` nunca sale
del tenant dueño. Conviene inspirar los campos de propiedad/ubicación/estado en estándares
interoperables (RESO) para no inventar un lenguaje imposible de integrar después — sin certificar hoy.

---

## 7. Aislamiento técnico (recomendación)

Con datos personales + competidores en la misma base, el filtro por tenant **no debería depender de que
cada query lo recuerde**. Opciones (→ Pregunta 2):

- **Row-Level Security (RLS) de PostgreSQL** *(recomendado)*: la BD impone el predicado de tenant; un
  bug de query o un `WHERE` olvidado **no puede** filtrar datos entre corredoras. El `organizacion_id`
  de sesión se fija por `SET app.current_org`. Aislamiento por defecto, a prueba de errores de código.
- **Discriminador `organizacion_id` + filtro en el service** (`Alcances`): más simple, pero cada fuga
  es un bug silencioso de privacidad. Aceptable solo con tests de cobertura exhaustivos.
- **Schema-per-tenant**: aislamiento fuerte, pero migraciones Flyway y operación se multiplican por
  tenant; excesivo para arrancar.

RLS es el punto dulce: modelo lógico único, aislamiento impuesto por la base.

---

## 8. IA sin abrir datos entre organizaciones

Separar tres tecnologías que suelen confundirse:

1. **RAG (contexto)** — tres índices con filtro obligatorio por tenant/autorización en cada
   recuperación: **RAG privado** (org activa), **RAG de red** (objetos autorizados), **RAG público**
   (normativa). Nunca recupera conversaciones privadas de otra corredora.
2. **Modelos predictivos (pronóstico)** — sobre **datos estructurados disociados** de todas las
   organizaciones: probabilidad de cierre, tiempo de alquiler, rango de precio, caída, calidad del
   interesado. Aprende del agregado sin que nadie consulte filas ajenas.
3. **LLM (explicación + ejecución)** — consume el pronóstico y lo explica/acciona. No es el que calcula
   precios ni probabilidades.

Flujo: **modelo predictivo calcula → RAG aporta evidencia → LLM explica y ejecuta**.

**Consentimiento en 3 permisos** (reemplaza el booleano actual, §3.5): `uso_operativo` (prestar el
SaaS), `uso_analitico_agregado` (benchmarks), `uso_mejora_modelos` (entrenamiento con datos
disociados). Para los primeros años basta un **repositorio central de características disociadas**;
aprendizaje federado / clean rooms / cómputo confidencial se evalúan solo si entran actores grandes
que prohíban extraer datos.

**Devolución de inteligencia** a cada corredora sin ver filas ajenas (precio estimado/m², tiempo
esperado, demanda en la red, búsquedas compatibles, objeción frecuente, recomendación). Con guardas
anti-deducción: mínimo de observaciones, agrupación por zona/tipo/periodo, sin agentes/organizaciones
identificables, retraso temporal, sin pipelines ni comisiones ajenas.

---

## 9. Cumplimiento (Perú) — a tratar como requisito, no nota al pie

- **Ley 29733 (Protección de Datos Personales)**: transferir datos a otra organización exige
  consentimiento, información al titular y limitación a la finalidad; hay derechos frente a decisiones
  automatizadas con efecto significativo. **Pero** la preparación/ejecución de una relación contractual
  está **exceptuada** de solicitar consentimiento (base jurídica distinta). → modelo D-25.
- **Ley 29080 (Registro del Agente Inmobiliario, MVCS)**: la red debería verificar que el agente esté
  identificado y, cuando corresponda, registrado.
- **Libre competencia (Indecopi)**: prohibido coordinar precios, comisiones o reparto de
  clientes/zonas. BROX facilita coincidencias y eficiencia; **no** es espacio para pactar precios
  mínimos ni repartir mercado. Es un límite de producto, no solo legal.
- **Publicidad/prospección**: KAIROS opera en WhatsApp, pero responder una consulta **no** habilita
  campañas posteriores — la prospección comercial exige consentimiento expreso y revocable aparte.

### 9.1 Modelo de finalidades y bases jurídicas (D-25) — reemplaza el booleano

> ## ⚠️ CORREGIDO EL 2026-08-04 — D-27 acota este modelo para la primera versión
>
> **Lo que sigue describe el destino, no lo que se despliega.** La decisión vigente es
> **D-27** (`decision-autorizacion-datos-personales.md`): *la autorización se pide **una sola vez**,
> en el formulario de alta, y no puede dominar la interfaz ni convertirse en una máquina de estados
> visible para el agente.*
>
> **Qué cambia respecto de lo escrito abajo:**
>
> | Punto de D-25 | Estado en la primera versión |
> |---|---|
> | Cinco finalidades (`OPERACION_SERVICIO`, `ANALITICA_AGREGADA`, `MEJORA_MODELOS`, `RED_COLABORATIVA`, `PROSPECCION_COMERCIAL`) | **Una sola activa: `GESTION_COMERCIAL`.** Las cinco de V6 quedan `estado = 'I'` |
> | Cuatro bases jurídicas | **Tres**: `CONSENTIMIENTO` · `RELACION_CONTRACTUAL` · `OTRA_BASE_LEGAL` (agrupa obligación legal y otras excepciones) |
> | Subdivisión de `RED_COLABORATIVA` en tres autorizaciones | **No se implementa.** Ninguna pantalla la ofrece |
> | KAIROS pidiendo `MEJORA_MODELOS` conversacionalmente | **No se pide, porque no se usa.** No hay entrenamiento con datos identificables |
> | Registro append-only + evidencia + versión de aviso | **Se conserva tal cual**: es lo que ya creó V6 y lo que D-27 reutiliza |
>
> **Lo que NO cambia y sigue siendo obligatorio:** no fingir consentimiento cuando la base es
> contractual, el registro append-only, la evidencia con canal, y que negar algo opcional **nunca**
> pueda impedir usar el producto.
>
> **Hallazgo que conviene conocer antes de tocar esto:** las cuatro tablas de este modelo
> —`finalidad_tratamiento`, `aviso_privacidad_version`, `evidencia_autorizacion`,
> `autorizacion_tratamiento_evento`— **ya existen aplicadas desde V6, con sus entidades de dominio, y
> ningún repositorio ni service las usa**. Lo que está en producción son **dos booleanos**
> (`persona.consentimiento_uso_dato` y `detalle_cliente.consentimiento_contacto`), los dos dentro
> del contrato congelado.

El error a evitar es modelar todo como "consentimiento". Se registra la tupla **finalidad + base
jurídica + estado + versión del aviso + evidencia + vigencia**. Cuando el tratamiento se sustenta en
relación contractual u otra excepción legal, **no se finge un consentimiento** — se registra la base
jurídica aplicable con estado `NO_REQUERIDA`.

**Catálogo extensible de finalidades** (no tres booleanos):

```
finalidad_tratamiento (codigo, nombre, descripcion, requiere_consentimiento, permite_revocacion, nivel, estado)
```
Set inicial: `OPERACION_SERVICIO` · `ANALITICA_AGREGADA` · `MEJORA_MODELOS` · `RED_COLABORATIVA` ·
`PROSPECCION_COMERCIAL`. La red y la prospección se separan de la analítica porque son finalidades
distintas (compartir ≠ analizar; responder ≠ hacer campaña). `RED_COLABORATIVA` probablemente se
subdivide: `COMPARTIR_PROPIEDAD_EN_RED`, `COMPARTIR_PERFIL_BUSQUEDA_ANONIMIZADO`,
`REVELAR_IDENTIDAD_PARA_MATCH` (autorizar demanda anónima ≠ autorizar revelar identidad).

**Registro append-only** (no se destruye el estado anterior):

```
autorizacion_tratamiento_evento (id, persona_id, finalidad_codigo, evento, base_juridica,
                                 version_aviso, ocurrido_en, evidencia_id)
   eventos: INFORMADO · OTORGADO · RECHAZADO · REVOCADO · REOTORGADO · EXPIRADO · CAMBIO_BASE_JURIDICA
aviso_privacidad_version (id, version, contenido_hash, contenido, vigente_desde, vigente_hasta)
evidencia_autorizacion   (id, canal, mensaje_id, ip, user_agent, phone_number_id,
                         whatsapp_message_id, fecha_hora, texto_mostrado, respuesta_recibida)
```
Una **vista/proyección** deriva el estado vigente por `(persona, finalidad)`. Bases jurídicas iniciales:
`CONSENTIMIENTO` · `RELACION_CONTRACTUAL` · `OBLIGACION_LEGAL` · `OTRA_EXCEPCION_LEGAL`.

**Reglas duras:**
- Negar `MEJORA_MODELOS` **no** puede impedir usar BROX, hablar con KAIROS, agendar visita ni operar
  (prohibido condicionar el servicio a datos no indispensables).
- KAIROS obtiene autorizaciones con **mensaje expreso e inequívoco** ("¿Autorizas que usemos información
  disociada de esta conversación para mejorar los modelos?" → "Sí, autorizo"). **El silencio o seguir
  conversando NO es consentimiento**; la carga de la prueba recae en quien trata.
- `MEJORA_MODELOS` nunca usa por defecto audios crudos, documentos, DNI, teléfonos, correos ni
  conversaciones completas; la autorización indica qué categorías se usan y si se disocian.
- **Efecto técnico de una revocación** (`MEJORA_MODELOS → REVOCADA`): excluir nuevos mensajes del
  pipeline, cancelar jobs pendientes de esa persona, registrar fecha efectiva, evaluar borrado del
  dataset identificable/pseudonimizado, conservar solo lo que sostenga otra finalidad válida. Plazo de
  adecuación diligente (≤10 días). **No** implica borrar la operación si `OPERACION_SERVICIO` sigue
  sustentada por relación contractual.
- `ANALITICA_AGREGADA`: distinguir estadística **irreversiblemente anonimizada** (riesgo menor) de datos
  **pseudonimizados** con identificador que permita reconstruir recorridos (siguen siendo personales, no
  se tratan como anónimos).

> ⚠️ Los textos definitivos de los avisos deben ser revisados por un **especialista peruano en
> protección de datos** antes de publicarse. Este modelo es la estructura, no el contenido legal.

---

## 10. Secuenciación (fases del usuario × Strangler)

| Fase | Contenido |
|---|---|
| **1** | SaaS aislado + captura normalizada del dato (multi-tenant núcleo) |
| **2** | Propiedades colaborativas entre 3–5 organizaciones de confianza |
| **3** | Demandas anónimas + matching entre organizaciones |
| **4** | Benchmarks + modelos predictivos agregados |
| **5** | ControlLocal Network como estándar de colaboración |

**Regla de oro de esquema (igual que con particionado y AI-ready)**: `organizacion_id` entra al
**esquema ahora** (aunque exista un solo tenant "SIVAN"), porque retrofittear tenant a una base poblada
es de las migraciones más caras. La **capa colaborativa se difiere** (Fase 2+). Preparar el modelo hoy;
construir la feature después. **Choca con el token congelado (§3.1)** → hay que decidir el timing
respecto al corte del Strangler (Pregunta 3).

---

## 10 bis. Arquitectura de componentes (nomenclatura del producto)

El sistema tiene **tres componentes con responsabilidades separadas** + la bóveda:

```
SIVAN  (la empresa / la inteligencia de la red)
├── BROX    — el SaaS multi-tenant: CRM, operaciones privadas, red colaborativa autorizada
│            (= lo que el código llama hoy `controllocal` / com.controllocal)
├── KAIROS  — el agente conversacional de WhatsApp: crea personas, califica, da seguimiento,
│            sugiere colaboración; cambia de contexto de organización según el phone_number_id receptor
└── Bóveda de identidad de red  — tokens HMAC, coincidencias entre organizaciones,
             sin acceso directo de tenants, sin exposición a RAG ni LLM
```

Fórmula: **BROX protege la operación de cada organización · KAIROS ejecuta la conversación · SIVAN
conecta la inteligencia de la red sin fusionar las bases privadas.**

**Branding (D-23, resuelto)**: **BROX es la marca y la arquitectura objetivo; ControlLocal es el legado
congelado.** Toda documentación, módulo, repositorio y contrato externo **nuevo** usa BROX. El código en
GlassFish conserva sus paquetes/artefactos `controllocal` temporalmente para no romper compatibilidad;
**no hay refactor transversal** — cada componente adopta BROX cuando se reemplaza o migra, y al retirar el
último módulo legado se eliminan los alias.

**D-23 resuelve la regla**: artefactos/componentes **nuevos o sustituidos** → namespace BROX; **tablas
nuevas** → nombres de dominio sin prefijo de marca (`organizacion`, `contacto_comercial`); los
identificadores `controllocal` de GlassFish quedan **congelados** y no se usan para nada nuevo.

> Micro-criterio restante (implementación): el reactor `backend-spring` ya construido nació
> `com.controllocal` y es el vehículo Strangler. Para **no mezclar `com.brox.*` y `com.controllocal.*`
> en el mismo Maven**, se mantiene `controllocal` en ese reactor hasta un rename atómico; los artefactos
> genuinamente separables (KAIROS, bóveda de identidad de red, servicios de la red colaborativa) nacen
> directamente como **BROX/SIVAN** fuera de él. Las tablas, en cualquier caso, van sin prefijo (D-23).

---

## 11. Decisiones registradas

**D-16 — Núcleo SaaS multi-tenant (responde el bloqueante).** BROX es multi-tenant con aislamiento
obligatorio de personas, conversaciones, documentos, operaciones, notas y configuración por
organización. `organizacion_id` entra al esquema desde el diseño.

**D-18 — Identidad entre organizaciones** *(redacción del usuario)*: `persona` pertenece exclusivamente
a una organización; no existe persona comercial global compartida. KAIROS puede crear automáticamente
persona + rol al recibir una conversación de WhatsApp no vinculada, pero búsqueda, vinculación y
deduplicación operan **solo dentro de la organización receptora**. SIVAN mantiene una **bóveda de
identidad de red separada** (referencias opacas + tokens HMAC) para detectar coincidencias autorizadas
sin exponer ni fusionar registros locales. Una coincidencia no modifica las personas locales ni revela
participación cruzada: solo genera `match_colaborativo`, comprobación de duplicidad autorizada o dato
analítico disociado, con finalidad registrada. La bóveda no es accesible desde RAG, ni desde consultas
normales de BROX, ni como contexto de KAIROS; revelar identidad/contacto exige un flujo de colaboración
autorizado, mínimo necesario y auditable. *(Nota: no hay D-19 en el registro; numeración del usuario.)*

**D-20 — Entrada progresiva del multi-tenancy** *(redacción del usuario)*: BROX incorpora desde ya la
entidad `organizacion` y `organizacion_id` obligatorio en todas las entidades privadas; los datos
existentes se asignan a una **organización de legado** y el sistema opera temporalmente como tenant
único. Mientras haya módulos en GlassFish, **el contrato del token no se modifica**; la organización
activa la resuelve el backend por configuración del tenant legado, **sin confiar en valores del
cliente**. Los módulos ya migrados a Spring usan un **contexto explícito de organización** desde su
creación (aunque solo exista una). El multi-tenancy funcional (alta de organizaciones, membresías,
selección de tenant, nuevo contrato de auth) se activa **solo tras retirar el último módulo de
GlassFish** y pasar las pruebas de aislamiento; ahí se elimina el fallback al tenant legado y toda
solicitud debe resolver una organización válida por membresía activa.

**D-21 — Resolución de organización en WhatsApp** *(redacción del usuario)*: cada canal de WhatsApp es
una entidad `canal_whatsapp` asociada obligatoriamente a una organización; cada `phone_number_id`
pertenece a una sola organización (una org puede tener varios números). Los mensajes entrantes se
asignan al tenant **exclusivamente por el `phone_number_id` destinatario** que da la plataforma de
WhatsApp — KAIROS **no** infiere la organización por el texto, el teléfono del remitente ni
coincidencias semánticas. SIVAN puede operar un canal compartido (demos, captación propia, servicios de
red); ahí la derivación a una organización exige una **referencia explícita y verificable** (id de
propiedad/campaña/sesión firmado), y sin referencia válida la conversación **permanece en SIVAN** y no
entra al dominio privado de otra org. **Decisión final: no usar número compartido como ruta principal** —
un número/canal propio por organización, un solo backend BROX y una sola instancia KAIROS que cambia de
contexto según el `phone_number_id` receptor.

**D-22 — Identidad de acceso y membresía** *(redacción del usuario)*: BROX mantiene una **identidad de
autenticación global por usuario** — correo, teléfono verificado o IdP, único en la plataforma y
**no dependiente de organización**. Pertenencia, rol, nombre visible, estado y permisos se definen en
**`usuario_organizacion`**; una misma cuenta puede pertenecer a varias organizaciones **sin duplicar
credenciales**. Tras autenticar, BROX resuelve la organización activa: una sola membresía ⇒ automática;
varias ⇒ el usuario elige, y **toda selección la valida el backend**. **Ninguna organización conoce las
membresías, roles ni actividad del usuario en otros tenants; una invitación a una cuenta existente crea
solo una nueva membresía y no revela relaciones anteriores.** Durante la coexistencia con GlassFish se
mantiene el contrato de auth legado y el `nombre_usuario` actual **puede conservarse como alias**; la
identidad global y la selección explícita de organización se activan **plenamente con el nuevo backend**
(no en la V6; §3.7). Decisión final: **cuenta global en BROX; perfil, rol y permisos por organización.**

**D-23 — Transición de identidad técnica ControlLocal → BROX** *(redacción del usuario)*: BROX es desde
ahora el **único nombre comercial** y el **nombre técnico objetivo**. ControlLocal queda limitado a
identificar **componentes legados** que deban mantenerse por compatibilidad. **No hay renombrado
transversal**: los paquetes, artefactos, configuraciones y contratos requeridos por GlassFish conservan
temporalmente sus identificadores, que se consideran **congelados** y **no deben usarse para módulos o
integraciones nuevos**. Todo componente nuevo o completamente sustituido usa **namespaces y artefactos
BROX bajo la estructura de SIVAN**. Las **tablas nuevas emplean nombres propios del dominio, sin prefijos
de marca** (`organizacion`, `contacto_comercial`, no `brox_*`). La adopción técnica es progresiva por
componente; al retirar el último módulo legado se eliminan alias, configuraciones y referencias
residuales a ControlLocal.

**D-24 — Aislamiento progresivo**: discriminador `organizacion_id` + filtro en `Alcances` **ahora**
(tenant único); **RLS de PostgreSQL se activa al habilitar multi-tenant** (antes del primer segundo
tenant). Coherente con D-20 y con el aislamiento máximo de D-18.

**D-26 — Identidad operativa del usuario interno** *(redacción del usuario)*: la identidad operativa del
usuario interno es **`usuario` (global) + `usuario_organizacion`**; BROX **no crea automáticamente una
`persona`** por cada usuario. Auditoría (creado/modificado por) referencia `usuario_id`; asignación
operativa referencia `usuario_organizacion_id`; los actores del dominio (cliente/propietario/contacto)
son `persona_id` + `persona_rol`. Una membresía **puede** vincularse **opcionalmente** con una `persona`
del **mismo tenant** cuando el usuario también participa como actor del dominio (p. ej. un agente que
alquila para sí); esa FK verifica que la persona pertenece a la misma organización, y el vínculo no altera
permisos ni sustituye a `usuario_organizacion`. Resuelve la Pregunta E. *(D-19 permanece "no
emitido/reservado" para no alterar la trazabilidad del registro.)*

**D-25 — Gestión granular de finalidades y bases jurídicas** (§9.1): el booleano
`consentimiento_uso_dato` se sustituye por un modelo versionado y auditable **finalidad + base jurídica +
estado + versión + evidencia + vigencia**, con catálogo extensible de finalidades, registro append-only y
efectos técnicos de revocación. El tratamiento operativo indispensable se distingue de las finalidades
opcionales; no se finge consentimiento cuando aplica una excepción legal. KAIROS pide autorización con
mensaje expreso, nunca por silencio. *(Numeración del usuario; D-19 sigue sin asignar.)*

**D-17 — Modelo híbrido de colaboración inmobiliaria** *(redacción del usuario, literal)*:

> ControlLocal conservará un núcleo SaaS multi-tenant con aislamiento obligatorio de personas,
> conversaciones, documentos, operaciones, notas y configuraciones de cada organización. Sobre dicho
> núcleo operará una red colaborativa en la que cada organización podrá compartir, de manera expresa y
> reversible, propiedades, perfiles anonimizados de búsqueda y determinados estados operativos.
> La colaboración no otorgará acceso directo a las tablas privadas de otra organización. Se realizará
> mediante proyecciones autorizadas que expongan únicamente los campos requeridos para identificar
> coincidencias, coordinar visitas y desarrollar operaciones compartidas.
> La identidad y los datos de contacto de clientes y propietarios permanecerán privados salvo
> autorización y finalidad específicas. Los modelos de inteligencia podrán utilizar información
> estructurada disociada y agregada de distintas organizaciones, de acuerdo con los permisos
> contractuales correspondientes, sin permitir la reconstrucción de registros individuales ni la
> identificación de la organización de origen.
> ControlLocal podrá sugerir o activar colaboración automáticamente cuando la propiedad cuente con
> autorización previa y se cumplan reglas configuradas. La plataforma no permitirá coordinar precios,
> comisiones, reparto de clientes o zonas ni compartir información comercial sensible no necesaria para
> una operación concreta.

---

## 12. Estado de las preguntas

**Resueltas:** identidad (D-18, §4) · timing vs Strangler (D-20, §11) · enrutamiento WhatsApp (D-21,
§11) · dedup del bot = intra-tenant (§4).

**Resueltas en esta ronda:** login (D-22, identidad global + membresías) · branding (D-23) · aislamiento
(D-24, discriminador→RLS) · consentimiento (D-25, finalidades+bases jurídicas).

**Todas resueltas.** E → D-26 (identidad operativa = `usuario` + `usuario_organizacion`, sin `persona`
automática; vínculo opcional a persona del mismo tenant). F → D-23 (artefactos nuevos → BROX, tablas →
nombres de dominio, reactor `controllocal` congelado sin mezclar paquetes hasta un rename atómico).

El diseño de tenancy queda cerrado. La ejecución vive en `plan-migracion-v6-tenancy.md`.

**Deuda de implementación que ya generan estas decisiones** (planificar, no bloquean):
- **Migración V6**: `organizacion` + org de legado + `organizacion_id` en V1–V5 (`persona`,
  `persona_rol`, `propiedad`, `captacion`, `prospeccion`, detalles, supervisión…), uniques globales →
  por-tenant (§3.6), y las tablas de consentimiento D-25 (§9.1).
- **Separar cuenta de acceso de persona** (D-22/§3.7): `cuenta_acceso` global + `usuario_organizacion`;
  se materializa en el corte de GlassFish, no en la V6.
- `Actor`/`Alcances` ganan el eje `organizacion` (constante = legado mientras sea tenant único);
  activar RLS antes del segundo tenant (D-24).
- F0/Locales/F2 se construyeron sin contexto de organización → añadir el contexto explícito de D-20.
- **Bóveda de identidad de red** (D-18) y **canal_whatsapp** (D-21) son artefactos nuevos → nacen como
  BROX/SIVAN, fuera del reactor `controllocal`.
