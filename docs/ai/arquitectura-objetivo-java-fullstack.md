# Arquitectura Objetivo — Spring Boot + Angular (rev. tras revisión adversarial)

> Documento 5 de 7 · Fase doc-first. **Revisado** tras una revisión adversarial multi-agente (5 lentes) que lo declaró "con huecos". Esta versión incorpora las correcciones y **dos decisiones nuevas**: **PostgreSQL v2 + sincronización** durante el Strangler, y **Party-Role real (con vigencia)**.
> Alcance 1ª ola = **paridad** (alquiler de locales) sobre fundación extensible a la plataforma (compra/venta, multi-inmueble, IA).
> Cambios vs v1: Party-Role real (no @OneToOne-por-rol); contrato **contract-first congelado** como seam; **matriz operación→rol** como entregable (RouteAccess es página→rol, no operación→rol); familia touchpoint `interaccion`+`visita` modelada; `OperacionComercial` con condiciones por composición; auditoría por **aspecto + test de cobertura**; unicidad parcial con **índices parciales nativos de Postgres**; tabla de trazabilidad 32→destino.

## 1. Principios rectores
1. **Capas estrictas** (heredar el blindaje ArchUnit): `web → service → persistence → domain`; la web solo llama al service.
2. **Persistencia híbrida (CQRS-lite)** sobre **PostgreSQL**: JPA para escritura/dominio; **SQL nativo/proyecciones** para lectura pesada. Toda consulta nativa recibe el **scope del actor** como parámetro obligatorio del WHERE.
3. **Party-Role real** (party + roles con vigencia); composición sobre herencia profunda.
4. **Contrato REST contract-first CONGELADO** como frontera del Strangler; el rediseño de la forma del cable (enums legibles, Party-Role) va **después** del corte de cada módulo.
5. **Trazabilidad y autorización con enforcement por test** (no por disciplina manual).
6. **Heredar lo bueno** (indexado, contrato minimalista, patrón `evaluacion_solicitud`, diseño polimórfico de historial) y **preservar invariantes** (ver §13).

## 2. Estructura backend (Maven multi-módulo)
```
controllocal-domain       Entidades JPA + enums + reglas + eventos de dominio + interfaz Transicionable
controllocal-persistence  Repositorios Spring Data + paquete query (SQL nativo/proyecciones, con scope)
controllocal-service      Casos de uso @Transactional, auditoría (aspecto), alcance por rol
controllocal-web          @RestControllers + DTOs + OpenAPI + @ControllerAdvice + Spring Security
controllocal-app          Arranque Spring Boot (fat jar) + config
```
- DI de Spring (reemplaza `new XxxImpl()`); fat jar (reemplaza WAR+GlassFish).
- **ArchUnit heredado** + dos tests de cobertura nuevos: (a) todo `@RequestMapping` tiene `@PreAuthorize`; (b) toda transición de estado emite auditoría (§7/§8).

## 3. Modelo de dominio — Party-Role real
```
Persona (party)         id, tipoPersona N/J, documento, nombre/razónSocial, contacto, estado, consentimientos, auditColumns
PersonaRol              id, persona_id, tipoRol ∈ {PROPIETARIO, CLIENTE, USUARIO_INTERNO, BROKER, AGENTE}, vigenciaDesde, vigenciaHasta
  └─ detalle por rol (composición, @OneToOne a PersonaRol o atributos en la fila):
     Credenciales (rol USUARIO_INTERNO): nombreUsuario, contrasenaHash, estadoAdministrativo
     DetalleBroker (rol BROKER): codigo, zona, esAdministrador
     DetalleAgente (rol AGENTE): codigo, zonaAsignada, estadoOperativo
```
- **Roles acumulables con vigencia**: una persona puede ser propietario y cliente; el histórico de roles queda registrado (vigencia) → RC-002.
- **Sin cadena de 3 niveles**: `BROKER`/`AGENTE` son roles **de la Persona** (no de UsuarioInterno). El acceso al sistema requiere el rol `USUARIO_INTERNO` (credenciales); Broker/Agente lo **implican**.
- **Identidad del actor = `Persona.id`** (única real). En cada evento de auditoría se guarda `(persona_id, tipoRol)` para no perder el rol operativo con el que se filtra (RC-001).
- **Mapeo JPA**: navegar **desde el rol** (lado propietario, `@ManyToOne`/`@OneToOne` LAZY con `@JoinColumn`), nunca asociaciones inversas opcionales en `Persona` (evita la trampa *eager*/N+1). Para lecturas de listas: **read-DTOs desnormalizados** (nombre/rol aplanados) en el paquete query.
- **Integridad rol↔atributo**: constraints + validación service (p. ej. un rol `USUARIO_INTERNO` activo por persona). El **admin único** = índice único parcial nativo de Postgres `WHERE esAdministrador` (ver §6).
- **Migración de FKs**: en v2 (Postgres nuevo) las ~10 FK a `id_agente`/`id_broker` pasan a referenciar `Persona`/`PersonaRol`; el `id_usuario` del historial pasa a `persona_id`. Es un rediseño limpio (BD nueva), con **backfill** desde la MySQL vieja vía el pipeline de sync.

## 4. Propiedad + OperacionComercial
- **`Propiedad`** (generaliza `local_comercial`): atributos comunes de inmueble — metraje, dirección, `distrito` FK única (MEJ-13), geo, antigüedad, ambientes, `tipoInmueble` (catálogo único, MEJ-14/18), uso, estado, **`precioReferencial`**, **`zonificacion`** (genéricos, conforme al anexo §B.1), `propietario`.
- **Detalle por tipo (composición)**: `DetalleLocalComercial` (rubroPermitido **obligatorio**, cargaElectricaKw, aptoLicencia). *Corrección de la revisión*: `rubro_permitido` es NOT NULL para locales → el detalle es **obligatorio para tipo LOCAL** (validación service por tipo), no "opcional". Extensible a departamento/casa/oficina/terreno con sus detalles.
- **`OperacionComercial`** (Alquiler/Venta/Compra): *corrección* — la columna vestigial es **`motivo_operacion`** en `captacion` (no `tipo_operacion`; ese está en `tipo_documento_requerido`). Promover a tipo real, pero **NO basta un discriminador**: la cadena de cierre es **alquiler-específica** (`solicitud_alquiler` con plazo/meses_garantia/adelanto, `contrato_alquiler`, `documento_solicitud`). Venta/Compra difieren estructuralmente (precio de venta, arras, sin garantía/adelanto) → **condiciones por composición**: `CondicionesAlquiler` / `CondicionesVenta` colgando de `Solicitud`/`Contrato`. El **punto de extensión vive en solicitud/contrato**, no solo en captación. **1ª ola: solo Alquiler activo.**
- `Publicacion` (MEJ-04): se conserva; se decide en F2 explotar (multicanal/pauta) o simplificar.

## 5. Familia "contacto comercial" y proceso comercial
- **`interaccion_comercial` (polimórfica por `contexto`)** — *añadido tras la revisión*. Contexto ∈ {OPORTUNIDAD, PROSPECCION, CAPTACION, CLIENTE}, 4 FK nullable con "exactamente una", y `resultado` con **4 dominios** según contexto. **Mapeo JPA**: tabla única con discriminador `contexto` (single-table) + validación service del "exactamente una FK" y del dominio de `resultado` por contexto (JPA no impone el CHECK condicional nativo → validación + constraint DB). **`visita`** se modela como **hermana** en la familia `ContactoComercial` (comparte el dominio de `resultado`/seguimiento). Consultas por `(contexto, id_*, fecha_hora)` vía paquete query, reutilizando los índices compuestos ya existentes.
- **Proceso comercial (Prospeccion/Captacion/Oportunidad/Solicitud)** — *corrección*: **NO** un `@MappedSuperclass` de columnas (el esquema diverge: captación no tiene `fecha_registro` sino `fecha_captacion` DATE + vigencia + broker_revisor; `solicitud.fecha_registro` es DATE vs DATETIME en otras; cada una es una máquina de estados distinta). En su lugar: **interfaz `Transicionable`** (comportamiento) + **aspecto de auditoría**; máquinas de estado e invariantes **por entidad** en el service; lecturas cross-proceso vía **UNION nativo**. En v2 se normalizan nombres/tipos de fecha.

## 6. Persistencia híbrida (CQRS-lite) sobre PostgreSQL — RC-003
| Lado | Cómo |
|---|---|
| Escritura/dominio | Entidades JPA + `JpaRepository`; invariantes en service; `@Transactional` |
| Lectura simple | Derived queries / `Page<T>` (paginación en SQL) |
| **Lectura pesada** | Paquete `query`: SQL nativo/proyecciones a read-DTOs, **con scope del actor en el WHERE** |

**Consultas concretas al paquete query** (*enumeradas tras la revisión*, no categorías): matching oferta↔demanda (`CoincidenciaCartera`: `propiedad × requerimiento_cliente × requerimiento_distrito`), `Dashboard`/`Indicadores` (KPIs+bandeja), `SeguimientoComercial`, reportes propietario (Jasper), y alertas temporales ("día-8 sin recontacto" sobre prospección). Cada una define su read-DTO y los índices que reutiliza.
- **Unicidad parcial NATIVA en Postgres** (resuelve MEJ-21, elimina las 5 columnas `GENERATED STORED`): `CREATE UNIQUE INDEX ... WHERE estado='ACTIVA'` (captación activa por local), `WHERE esAdministrador` (broker admin único), `WHERE tipoEvaluacion='FINAL'` (evaluación final), etc. El invariante duro vive en la BD, no en validación service.
- Se **erradica `listarTodos`** en rutas calientes (MEJ-05); se hereda el indexado compuesto (recreado en v2).

**Cierre de RC-003 para el listado de locales (2026-08-02).** El objetivo de 3 s
se cerró *con margen operativo*, no rozando el límite: p95 del texto libre **944
ms** en página 1 y peor observado **1.040 ms** sobre 100.000 locales medidos por
HTTP. Tres piezas lo sostienen, y las tres son norma para lo que venga:

1. **Búsqueda por conjunto de candidatos** (`docs/ai/contrato-listados-paginados.md`
   §5): ningún listado resuelve el texto libre con un `OR` que cruce tablas —no
   lo puede servir ningún índice y degenera en `Seq Scan`—; se usa una rama
   indexable por tabla unidas con `UNION`, el mismo conjunto para conteo, página
   y KPI, y la proyección completa se carga solo para los ids de la página.
2. **Un índice por campo buscable**, sobre la expresión `lower(campo)` (V11, V23).
   Y cuidado al renombrar columnas: al partir `propiedad.estado` en V15–V17
   desapareció con ella el índice del camino caliente que había creado V11, sin
   que nada lo avisara. Lo recuperó V22.
3. **Un gate que lo mide de verdad**: `e2e-locales-busqueda.ps1`, 100.000 filas
   por HTTP. Un `EXPLAIN` sobre una consulta simplificada no vale como prueba —el
   planner elige otro plan cuando la consulta lleva sus joins, y esa diferencia
   ya nos hizo dar por buena una justificación equivocada.

Lo que **no** hizo falta: materializar una proyección de búsqueda. Se evaluará
solo si un módulo no llega al objetivo con el patrón anterior, y con medición
delante. El único número que sigue por encima del segundo es la **última página**
(`OFFSET` recorriendo 99.990 entradas), y ahí la palanca es la paginación por
clave, no la búsqueda: sin texto esa misma página ya cuesta lo mismo.

## 7. Trazabilidad / auditoría (pilar RC-002)
- **Fuente de verdad ÚNICA**: un **aspecto** (o `@EntityListener`/`@PostUpdate`) sobre las transiciones —**no** llamadas manuales (que reproducen la causa de MEJ-01: hoy solo 1/9 se cablea)—. `estadoAnterior` se **captura antes de mutar** (en la carga). **Test de cobertura** (estilo ArchUnit) que falla si una transición de las entidades auditables no emite historial.
- **Catálogo `entidad_tipo` maestro (superset)** con dos subconjuntos: **transicionables/auditables** (con máquina de estado) vs **referenciables** (tarea/alerta pueden apuntar a cualquiera). *Corrección*: "mismo vocabulario" era falso (tarea ya usa 12 valores). El alcance de auditoría se **amplía explícitamente** más allá de los 9 actuales para cubrir eventos sensibles: `evaluacion`, `comision_liquidacion` (transiciones financieras), `documento_solicitud`, y datos personales/consentimientos (`persona`/`propietario`/`cliente`) si RC-001 lo exige.
- **Reasignaciones = eventos de actor, no transiciones de estado** (*corrección*): `reasignacion_captacion` (agente→agente) y `reasignacion_agente_broker` (broker→broker) cambian un **FK de actor**, no `estado`. Se conservan como **tablas-evento** (ya capturan anterior/nuevo/autorizador/motivo) y se **integran al timeline** de lectura, junto a `evaluacion_solicitud`. El timeline unifica: transiciones (historial) + eventos (reasignaciones/evaluaciones).
- **Actor** = `Persona.id` (+ `tipoRol` en el evento). `motivo/observacion` **obligatorio en transiciones sensibles** (MEJ-03). **Actor de sistema** (jobs que generan alertas/tareas) contemplado (persona técnica o nullable con marca).
- **Lectura**: `GET /entidades/{tipo}/{id}/historial` + timeline Angular, **con el mismo alcance por rol** que la entidad auditada (un agente no ve historial ajeno).

## 8. Seguridad y autorización (RC-001)
- **Spring Security + JWT**: `/auth/login` emite el token con **authorities** derivadas del rol: `ROLE_ADMIN` (rol BROKER + `esAdministrador`), `ROLE_BROKER`, `ROLE_AGENTE` — *corrección*: la BD solo tiene `rol {B,A}`+`esAdministrador`; hay que **mapear las 3 bandas** a authorities o un `hasRole('BROKER')` no distingue admin de supervisor.
- **Matriz operación→rol como ENTREGABLE** (*corrección clave*): `RouteAccess` es **página→rol** (~57 páginas), **no** operación→rol; la matriz que necesita `@PreAuthorize` **no existe** y hay que **construirla** cruzando (página→rol) × (pantalla→endpoint) + las reglas de la BL (`validarAlcanceBroker`, "solo el agente da altas"), sobre ~26 recursos / ~150 operaciones. **Test**: todo endpoint debe tener regla; falla el build si falta.
- **Alcance por fila (row-level)**: además del gating por rol, el filtrado por ámbito va en el **WHERE** de cada consulta (incl. las nativas del paquete query, que se saltan el service): broker→**sus** agentes, agente→**lo suyo** (self-scope, *omitido en v1*), admin→global. El `scope` es **parámetro obligatorio** de cada query nativa; tests de seguridad de lectura verifican que nadie lee cartera ajena por la ruta nativa.

## 9. Contrato REST — contract-first CONGELADO (RC-005)
- *Corrección de la revisión* (springdoc code-first rompía el seam): durante el Strangler se **congela byte-a-byte la forma actual del cable** (CHAR(1), DTOs `*Api` que hoy consume Blazor) como frontera. En **F0** se ingenieriza el **OpenAPI del contrato ACTUAL**; Spring se implementa **contra** ese contrato; **test de conformidad** en CI (respuestas Spring vs GlassFish grabadas) antes de cada corte.
- El **rediseño de la forma** (enums legibles, tipos Party-Role, cliente Angular generado) se aplica **DESPUÉS** de retirar el backend viejo de cada módulo.
- **Versionado**: durante el Strangler el **path es idéntico (sin versión)** para no romper el ruteo del proxy; el versionado, si hace falta, va **por header** y post-corte, con diff de schema en CI.
- **Protección de la comunicación** (*añadido*): TLS en el borde + **(m)TLS** entre el reverse-proxy y los backends viejo/nuevo; el endpoint de documentos reubicado usa **URL firmada de vida corta** (o token interno) en vez de exponer el JWT.

## 10. Frontend Angular y convivencia de frontends
- **Angular** desde F0 (`core/shared/features/api/state`), consumiendo el **contrato congelado**. `features/` cubre **todos** los dominios (incl. clientes, requerimientos, interacciones, visitas — *faltaban en v1*).
- **Convivencia Blazor+Angular** (*añadido*): Blazor sigue hasta F7. Modelo elegido: **proxy por ruta + token/SSO compartido** para que un usuario transite entre pantallas migradas (Angular) y no migradas (Blazor) sin re-login; la sesión durante la transición vive en un **token compartido** (no en el circuito Blazor). Alternativa (si se prefiere Angular-only): clientes escritos a mano para los endpoints legacy de GlassFish.
- **Notificaciones** (*decisión pendiente acotada*): **polling** contra `/alertas` durante el Strangler (simplicidad); SSE/WebSocket post-corte. Store de notificaciones destino con invalidación por mutación (reemplaza `NotificacionStore` + `RemoteCache`).
- **Documentos/PDF**: descarga desde backend (URL firmada, §9).

## 11. Almacenamiento y activos (heredar)
- Almacén híbrido **S3/disco** → servicio Spring (reutiliza el enfoque; Spring Cloud AWS o el SigV4 existente). **Unificar la "clave de almacén"** (MEJ-22: `foto_clave`/`clave`/`ruta_archivo`).

## 12. Estrategia de convivencia: PostgreSQL v2 + sync
- **BD nueva PostgreSQL** con el esquema v2 (Party-Role, Propiedad, OperacionComercial, enums unificados, auditoría universal, índices parciales). La MySQL vieja sigue sirviendo los módulos no migrados (GlassFish).
- **Sincronización** (CDC/ETL) por módulo migrado, para mantener consistencia mientras conviven. **Riesgo alto → detalle y mitigaciones en Doc 6**: FKs cruzadas entre dos bases (referencias sin FK real), **atomicidad** de operaciones cross-entidad que cruzan la frontera (`Captar` prospección→captación; "cerrar contrato cierra publicaciones") → **sagas/compensación**, y mantenimiento del pipeline de sync.
- **Corte por módulo** detrás del contrato congelado (§9); cada corte se verifica con el test de conformidad antes de enrutar la ruta al backend nuevo.
- **`motivo_operacion` fijo en 'A'** hasta retirar el backend viejo del módulo (el CHECK viejo rechaza otros valores durante la convivencia).

## 13. Trazabilidad 32 tablas → destino (para no perder ninguna)
Resumen (detalle por fase en Doc 4). Marcadas ⚠️ las **invariantes a preservar**.

| Grupo | Tablas | Destino |
|---|---|---|
| Personas | persona, usuario_interno, broker, agente, propietario, cliente_interesado, **broker_agente** | Party-Role (§3); `broker_agente` = **supervisión con vigencia** (base del alcance de §8) ⚠️ |
| Inmueble | distrito, local_comercial, foto_local, publicacion, precio_local | Propiedad + DetalleLocal (§4); `precio_local` histórico (heredar); `foto_local`→almacén |
| Captación | captacion, prospeccion, reasignacion_captacion, reasignacion_agente_broker | Proceso comercial (§5); reasignaciones = **eventos de actor** (§7) ⚠️ |
| Demanda | oportunidad_comercial, interaccion_comercial, visita, motivo_no_continuidad | Hub + familia ContactoComercial (§5); `motivo_no_continuidad` = polimorfismo "≤1 FK" → N `@ManyToOne` nullable + check ⚠️ |
| Formalización | solicitud_alquiler, tipo_documento_requerido, documento_solicitud, evaluacion_solicitud | CondicionesAlquiler (§4); `evaluacion` = **tabla-historial** integrada al timeline |
| Cierre | contrato_alquiler, comision_liquidacion, reporte_propietario | Contrato minimalista; `comision` **sin UNIQUE por contrato = admite split de pagos** ⚠️ |
| Requerimientos | requerimiento_cliente, requerimiento_distrito | Matching de cartera; `requerimiento↔distrito` **M:N** ⚠️ |
| Transversal | historial_estado, tarea, alerta | Auditoría universal (§7); catálogo `entidad_tipo` maestro |

## 14. Mapeo old → new
| Actual | Destino |
|---|---|
| `XxxRest` Jakarta | `@RestController` (contra contrato congelado) |
| `new XxxImpl()` | DI Spring `@Service` |
| `XxxDAOImpl` (JDBC) | `JpaRepository` + paquete `query` (SQL nativo, con scope) |
| `TransactionRunner` | `@Transactional` |
| `ApiExceptionMapper` | `@ControllerAdvice` · `PageResponse`→`Page<T>` |
| `JwtAuthFilter`/`TokenService` | Spring Security + JWT (authorities 3 roles) |
| columnas `GENERATED STORED` | **índices únicos parciales de Postgres** |
| MySQL (RDS) | **PostgreSQL v2 + sync** con la MySQL vieja |
| WAR + GlassFish | fat jar Spring Boot · `ArquitecturaCapasTest`→ArchUnit + tests de cobertura (auth/auditoría) |
| Frontend Blazor `Http*`/DTOs | Angular + cliente OpenAPI (post-corte); durante convivencia, contrato congelado |

---

### Estado
Doc 5 ✅ **revisado** (incorpora las 2 decisiones + las correcciones de la revisión adversarial). Quedan para **Doc 6 (riesgos)**: el detalle de la sincronización Postgres↔MySQL, sagas para cruces transaccionales, el pipeline de conformidad de contrato, y el plan de backfill de identidad (usuario→persona). Luego Doc 7 (seguridad-no-leer) + actualizar `CLAUDE.md`.
