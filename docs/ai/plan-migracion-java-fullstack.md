# Plan de Migración — Spring Boot + Angular (Strangler)

> Documento 4 de 7 · Fase doc-first. Plan basado en las decisiones fijadas y en los inventarios (Docs 1-3 backend/BD, Doc 2 frontend, anexo de modelado). Consolida y prioriza el registro de mejoras **MEJ-01…MEJ-32**.
> Arquitectura objetivo detallada → `arquitectura-objetivo-java-fullstack.md` (Doc 5). Riesgos → `riesgos-migracion.md` (Doc 6).

## 1. Decisiones fijadas (base del plan)
| Tema | Decisión |
|---|---|
| Backend | **Spring Boot (Maven, Java)**, por capas (controller → service → repository → entity) |
| Persistencia | **JPA/Hibernate HÍBRIDO**: JPA para dominio/escritura; **SQL nativo/proyecciones** para reportes, dashboards, búsqueda y analítica (CQRS-lite). Evitar herencia profunda |
| Personas | **Party–Role** (Persona + roles acumulables) |
| Migración | **Strangler / incremental** detrás del contrato REST |
| Frontend | **Angular** (SPA) |
| Contrato | REST + **OpenAPI** como frontera estable SPA↔backend |

## 2. Visión de producto y alcance de la 1ª ola
**Visión** (dirección): plataforma inmobiliaria con **compra/venta/alquiler**, múltiples tipos de inmueble, contratos, comisiones, roles, trazabilidad e **IA**.

**Principio rector**: la BD y el dominio se **rediseñan para ser extensibles** a esa visión; la **1ª ola migra a paridad funcional** del flujo actual (alquiler de locales) sobre el stack nuevo. Compra/venta, multi-tipo completo e IA quedan **habilitados por el diseño** y se activan en oleadas siguientes.
> ⚠️ *Supuesto a confirmar*: ¿la 1ª ola es **paridad** (recomendado, menor riesgo con Strangler) o ya incorpora **compra/venta / multi-inmueble** desde el arranque? El resto del plan asume paridad + fundación extensible.

## 3. Registro de mejoras consolidado y priorizado
Prioridad: **P0** = fundacional (antes/durante el esqueleto) · **P1** = durante la migración del dominio · **P2** = mejora/roadmap. (Origen detallado en Docs 1/3 §7-9 y anexo.)

| MEJ | Tema | RC | Prioridad | Fase |
|---|---|---|---|---|
| 09,10,11 → **Party-Role** | Jerarquía de personas consistente + integridad rol↔subtipo | RC-001/002 | **P0** | F0/F1 |
| 24 | Identidad única del actor (para auditar el "quién") | RC-002 | **P0** | F0/F1 |
| 01,02,03 | Trazabilidad transversal: registrar 9+ entidades + **leer** historial + actor/rol/motivo | RC-002 | **P0** | F0 → F6 |
| 19 | Unificar vocabulario polimórfico `(entidad_tipo,entidad_id)` (historial/tarea/alerta) | RC-002 | P1 | F6 |
| 06,25 | **Autorización server-side** (hoy solo cliente `RouteAccess`) | RC-001 | **P0** | F1 |
| 07,26 | **Contrato OpenAPI** + tipos TS generados (elimina doble DTO/`Mapear`) | RC-005 | **P0** | F0 |
| 05,32 | **Persistencia híbrida**: JPA escritura + SQL nativo lectura/analítica; erradicar `listarTodos` | RC-003 | **P0/P1** | F0 → todas |
| 12,31 | **Generalizar Propiedad/Inmueble** (multi-tipo) | plataforma | P1 | F2 |
| 30 | **OperacionComercial (Alquiler/Venta/Compra)** — promover `tipo_operacion` (corrige MEJ-20: no borrar) | plataforma | P1 | F2/F5 |
| 13,14,18 | Distrito redundante; unificar `tipo_inmueble` (CHAR vs VARCHAR) + enums duplicados | RC-003/int. | P1 | F2 |
| 16,17,27 | Homogeneizar estados/discriminadores CHAR(1)↔VARCHAR → enums/catálogo (reduce `Codigos` cliente) | RC-004 | P1 | F1/F6 |
| 22 | Unificar "clave de almacén" (`foto_clave`/`clave`/`ruta_archivo`) | int. | P2 | F4 |
| 15 | Subtipar/particionar `interaccion` por `contexto` + familia `interaccion`+`visita` (índices ya OK) | RC-003 | P2 | F3 |
| 21 | Columnas GENERATED STORED (unicidad parcial) → constraints/estrategia JPA portable | int. | P1 | F0 |
| 28,29 | Caché de lista cliente → estado Angular; partir `ServiciosApi.cs` por dominio | RC-004 | P1 | F1→F5 |
| 04 | Explotar o simplificar `publicacion` (esquema rico infrautilizado) | RC-004 | P2 | F2 |

**Fortalezas a HEREDAR** (no romper): indexado compuesto `(actor,estado,fecha)`; contrato minimalista sin duplicación; condiciones del trato en `solicitud`; `evaluacion_solicitud` como patrón de tabla-historial; unicidad por columna generada (portar a constraints); diseño polimórfico de `historial_estado`.

## 4. Cómo se aborda cada requisito (RC)
| RC | Enfoque en el nuevo stack |
|---|---|
| **RC-001** confidencialidad/integridad | Spring Security + JWT; **autorización por operación en el backend** (`@PreAuthorize`/checks en service), la matriz `RouteAccess` se replica como guards Angular **respaldados** por el servidor |
| **RC-002** auditabilidad | Party-Role (actor único) + auditoría en capa service/@EntityListener → `historial_estado` para **todas** las transiciones; endpoint + timeline en UI |
| **RC-003** comportamiento temporal | **SQL nativo/proyecciones** para cartera/seguimiento/reportes/dashboards; erradicar `listarTodos`; reutilizar el buen indexado |
| **RC-004** operabilidad | Angular con guards/stores; bandejas por rol; enums legibles (menos traducción cliente) |
| **RC-005** interoperabilidad/protección | **OpenAPI** como contrato versionado; tipos TS generados; HTTPS + interceptor JWT + manejo 401 |

## 5. Estrategia Strangler (mecánica)
- **Frontera = contrato REST**. El nuevo backend Spring implementa **los mismos endpoints** (documentados en OpenAPI); Angular los consume conforme se liberan.
- **Convivencia**: un reverse-proxy/gateway enruta cada ruta al backend viejo (GlassFish) o nuevo (Spring) según esté migrada; corte **por módulo/pantalla**.
- **BD**: se construye el **esquema v2** (Party-Role, Propiedad, OperacionComercial, auditoría). Durante la transición, o bien (a) v2 como BD nueva con sincronización por módulo, o bien (b) cambios **aditivos** sobre la actual + vistas de compatibilidad. Decisión de detalle en Doc 5/6 (es el punto más delicado del Strangler).
- **Verificación**: cada módulo migrado se valida contra el comportamiento del actual (mismos endpoints, mismos datos) antes del corte.

## 6. Fases (siguiendo el flujo de dominio)
| Fase | Contenido | MEJ clave |
|---|---|---|
| **F0 Fundación** | OpenAPI del contrato actual; esqueleto Spring (capas, JPA híbrido, Spring Security/JWT, manejo de errores, paginación); **BD v2** (Party-Role, Propiedad, OperacionComercial, auditoría); shell Angular (auth, guards, interceptor, layout) | 07,26,24,09-11,21,32 |
| **F1 Identidad + acceso** | Personas/roles (Party-Role), login/perfil, catálogos (distritos, tipos), **autorización server-side** | 06,25,16/17 |
| **F2 Oferta** | Propiedad/LocalComercial (generalizada), publicación, precios, fotos; prospección; **captación** (máquina de estados + auditoría); OperacionComercial | 12/31,30,13/14/18,04 |
| **F3 Demanda** | Cliente, oportunidad, interacción, visita; requerimientos + matching de cartera | 15 |
| **F4 Formalización** | Solicitud, documentos, evaluación (patrón `evaluacion_solicitud`) | 22 |
| **F5 Cierre** | Contrato, comisión, reportes propietario (SQL nativo/Jasper equivalente) | 05/32 |
| **F6 Transversal** | Trazabilidad universal (9+ entidades), tareas, alertas, dashboard/indicadores (SQL nativo) | 01/02/03,19 |
| **F7 Corte** | Retirar Blazor + GlassFish; consolidar; hardening | — |

Cada fase = definir/implementar endpoints Spring → migrar pantallas Angular → verificar contra el actual → corte.

## 7. Trazabilidad — plan concreto (pilar RC-002)
1. **Party-Role** aporta un `id` de persona/actor único → se acaba la fricción broker/agente→usuario (MEJ-24).
2. **Hook de auditoría** en la capa service (o `@EntityListener`/Envers) dentro de la transacción, tras persistir el cambio de estado, con `(entidadTipo, id, estadoAnterior, estadoNuevo, actor, motivo)` — generalizando el patrón que hoy solo hace `LocalComercial`.
3. **Lectura**: endpoint de historial por entidad (`listarPorEntidad` ya existe en el DAO) + **timeline** en Angular.
4. Vocabulario `entidad_tipo` **unificado** (MEJ-19) y con integridad.

## 8. Entregables restantes
- **Doc 5** `arquitectura-objetivo-java-fullstack.md`: modelo Party-Role detallado, jerarquía Propiedad + OperacionComercial, capas Spring + persistencia híbrida, diseño de auditoría, estructura Angular, contrato OpenAPI.
- **Doc 6** `riesgos-migracion.md`: riesgos del Strangler, BD v2 y convivencia, JPA vs rendimiento, corte.
- **Doc 7** `seguridad-no-leer.md`: índice de rutas/artefactos sensibles a excluir (sin valores).

---

### Estado
Doc 4 ✅ (plan + MEJ-01…MEJ-32 consolidadas/priorizadas + fases + RC). Supuesto de alcance de 1ª ola pendiente de tu confirmación (§2). Siguiente: Doc 5 (arquitectura objetivo) — donde aterrizo Party-Role, Propiedad/Operación y la persistencia híbrida en diseño concreto.
