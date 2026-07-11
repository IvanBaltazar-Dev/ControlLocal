# Estado Actual de ControlLocal

> Documento 1 de 7 · Fase **doc-first** de la migración Blazor/.NET → Java Fullstack.
> Alcance: retrato *as-is* del sistema (qué existe hoy y cómo está cableado). Los inventarios exhaustivos van en `inventario-backend-java.md` (Doc 3) e `inventario-frontend-blazor.md` (Doc 2). El destino y el plan van en Docs 4–5; los riesgos en Doc 6.
> Elaborado en modo solo-lectura, sin secretos, verificado con Serena (LSP java+csharp) sobre el código real.

---

## 1. Propósito y dominio

ControlLocal gestiona y **audita** el proceso comercial de alquiler de locales comerciales de una corredora inmobiliaria. Cadena de valor:

`Propietario → LocalComercial → Captacion` (agente capta, broker revisa) `→ OportunidadComercial → Interaccion/Visita → SolicitudAlquiler → Documentos → EvaluacionSolicitud` (broker) `→ ContratoAlquiler/Comision`.

- `OportunidadComercial` es la **entidad-hub**: conserva trazabilidad aunque el interesado nunca llegue a solicitud formal.
- Tres roles: **broker administrador**, **broker supervisor**, **agente inmobiliario**. El broker supervisa/decide; el agente registra/opera.
- Vocabulario de dominio en español (entidades, enums, métodos). Comentarios en español.

## 2. Arquitectura actual (as-is)

Cuatro piezas: backend Java por capas, API Jakarta REST, BD MySQL y frontend Blazor.

```
Frontend (Blazor Server, C#/.NET 10)
        │  HTTP/JSON + JWT
        ▼
REST (Jakarta JAX-RS, WAR en GlassFish)  ──►  BL (reglas, validaciones, transacciones)
                                                     │
                                                     ▼
                                            DAO (JDBC puro, sin ORM)
                                                     │
                                                     ▼
                                         DBManager (com.controllocal.config)  ──►  MySQL/RDS
```

- **Backend**: reactor Maven (Java 21), 5 módulos en orden de dependencia `model → dao → db-manager → bl → rest`. Wiring **manual** (`new XxxImpl()`), sin contenedor DI. ~26 recursos REST. Base API: `http://localhost:8080/controllocal/Api`.
- **Capas blindadas por test**: `ArquitecturaCapasTest` (ArchUnit) **rompe el build** si REST depende de `com.controllocal.dao..` o de `com.controllocal.config..`. Es un activo a preservar en la migración.
- **Frontend**: Blazor Server (render InteractiveServer), ~55 páginas `.razor` por rol/feature. Consume **solo** REST (no toca BD). Un `Http<Name>Service : I<Name>Service` por área, todo vía `Services/Api/ApiClient.cs` (JWT en `ApiSession`).
- **BD**: MySQL en AWS RDS. Esquema íntegro en 3 scripts (`00_recreate` → `01_create_schema` → `02_seed`), **sin framework de migraciones**. **32 tablas** (ver Doc 3 / Doc 7-esquema).
- **Despliegue real**: IntelliJ compila y despliega el WAR a GlassFish; el frontend corre con `dotnet run` (puerto 5232). `JAVA_HOME` debe ser JDK 21+ (el del sistema puede ser 17 y rompe el build por CLI).

## 3. Seguridad y comunicación actual (línea base RC-001 / RC-005)

- **Autenticación**: JWT propio (`rest/seguridad/`: `JwtAuthFilter`, `TokenService`), hashing propio (`PasswordHasher`). Endpoints públicos: `GET /salud`, `POST /auth/login`.
- **Autorización**: filtro JWT en backend + **gating por rol en el frontend**, data-driven en `Services/RouteAccess.cs` (`PublicPages`, `RolesByPage`, `IsPublic/CanAccess`). ⚠️ Parte del control de acceso vive **en el cliente**.
- **Comunicación**: CORS (`CorsFilter`), rate limiting (`RateLimiter`), cabeceras de seguridad en `Program.cs`. Los documentos se sirven por un **proxy** `/documento` en el frontend que descarga del backend sin exponer el JWT al navegador.
- **Sesión**: el JWT se guarda en `ApiSession` + navegador (localStorage vía JS interop en `AppState`/`BrowserSession`); un 401 fuerza `SignOut` completo.

## 4. Estado funcional por área (qué está operativo hoy)

| Área | Backend REST | Frontend | Notas |
|---|---|---|---|
| Autenticación / perfil | `AuthRest`, `PerfilRest`, `SeguridadRest` | Login, Profile, CambiarContrasena | Operativo (JWT). |
| Propietarios / prospección | `PropietariosRest`, `ProspeccionesRest` | Owners*, Prospecciones* | Operativo. |
| Locales / **publicación** / precios | `LocalesRest` | Locales*, FichaPropiedad | Publicación expuesta **vía LocalesRest** (ver §5). |
| Captaciones + reasignación | `CaptacionesRest`, `ReasignacionesCaptacionRest`, `AsignacionesRest` | Captaciones*, BandejaCaptaciones, Review, Reasignar* | Máquina de estados completa (aprobar/observar/rechazar/reasignar/cerrar). Invariante: **una captación activa por local**. |
| Oportunidades / interacciones / visitas | `OportunidadesRest`, `InteraccionesRest`, `VisitasRest`, `SeguimientoComercialRest` | Oportunidades*, Interacciones*, Visitas* | Operativo; cierre vía `MotivoNoContinuidad`. |
| Solicitudes / documentos / evaluación | `SolicitudesRest`, `DocumentosRest`, `EvaluacionRest` | Solicitudes*, Documentos, Evaluacion | Documentos con almacén híbrido S3/disco. Evaluación = broker. |
| Contratos / comisiones | `ContratosRest` | Comisiones, Cierre | Cierre de contrato cierra publicaciones del local. |
| Dashboard / indicadores / reportes | `DashboardRest`, `IndicadoresRest`, `ReportesPropietarioRest` | Dashboard, Reportes | Reportes PDF con JasperReports. |
| Tareas / alertas / requerimientos | `TareasRest`, `AlertasRest`, `RequerimientosRest` | (bandeja en Dashboard), notificaciones | `NotificacionStore` Singleton in-app. |

## 5. Trazabilidad: hallazgo central (línea base RC-002)

El usuario señaló `historial_estado` y `publicacion` como no funcionales. La verificación con Serena matiza y **confirma el problema de fondo**:

### 5.1 `historial_estado` — infraestructura completa pero trazabilidad *parcial y write-only*
- Existen **tabla + modelo + `HistorialEstadoDAO`** (`registrar(...)`, `listarPorEntidad(...)`) + `HistorialEstadoDAOImpl` (INSERT real). Diseño **polimórfico** correcto: `entidad_tipo`/`entidad_id`, `estado_anterior`/`estado_nuevo`, `id_usuario`, `fecha_evento`, `observacion`, con índices `(entidad_tipo, entidad_id)` y `(fecha_evento)`.
- El `CHECK ck_historial_tipo_entidad` contempla **9 tipos**: `PROSPECCION, CAPTACION, OPORTUNIDAD, INTERACCION, VISITA, SOLICITUD_ALQUILER, INMUEBLE, PUBLICACION, CONTRATO_ALQUILER`.
- **Realidad del cableado**: el único que invoca `historialEstadoDAO.registrar(...)` es `LocalComercialBusinessLogicImpl`, y **solo con `entidad_tipo="INMUEBLE"`** (cambios de estado del local). → **1 de 9** tipos se registra.
- Las transiciones sensibles de RC-002 (**captaciones, reasignaciones, visitas, solicitudes, evaluaciones**) **no escriben historial**.
- **Ningún endpoint REST lee `historial_estado`** → ni siquiera el historial de INMUEBLE que sí se graba es consultable/visible. Es **dato muerto de escritura**.
- **Diagnóstico**: la trazabilidad como capacidad transversal **no está entregada**; existe el esqueleto pero no el músculo. Éste es el corazón del trabajo de trazabilidad pedido.

### 5.2 `publicacion` — sí funciona, pero *infrautilizada*
- Está cableada de punta a punta: modelo + `PublicacionDAO/Impl` + `PublicacionBusinessLogic` (`crear/actualizar/cambiarEstado/listarPorInmueble/sincronizar`) y **expuesta vía `LocalesRest`** (no hay `PublicacionRest` propio; por eso el `HttpPublicacionService` del frontend resuelve contra `/locales/...`).
- Uso real actual: publicación "web" **auto-generada/sincronizada** como placeholder del local, y **cerrada** automáticamente al firmar contrato (`ContratoAlquilerBusinessLogicImpl.cerrarPublicaciones`). `OportunidadComercial.publicacionOrigen` la referencia como origen del lead.
- Su **esquema rico** (`canal`/`CanalPublicacion`, `inversionPauta`, `versionAnuncio`, `urlPublicacion`, `codigoOrigen`, `rentaPublicada`, `moneda`) fue diseñado para **gestión multicanal de anuncios con versionado y gasto en pauta**, pero hoy no hay feature/UI que lo explote. → No es "muerto", es **subaprovechado**.

## 6. Deuda / brechas frente a los requisitos del producto final (RC)

| RC | Requisito | Estado actual (as-is) | Brecha principal |
|---|---|---|---|
| **RC-001** | Acceso/operaciones restringidas · confidencialidad e integridad | JWT + filtro backend, pero **autorización repartida** (gating en cliente `RouteAccess`) | Centralizar autorización server-side; que el backend sea la única fuente de verdad de permisos. |
| **RC-002** | Operaciones sensibles · responsabilidad y **auditabilidad** | `historial_estado` **1/9 entidades, write-only, sin lectura** | Trazabilidad transversal real (registrar todas las transiciones + consultarlas). **Núcleo del proyecto.** |
| **RC-003** | Consultas de cartera/seguimiento/reportes · comportamiento temporal | Paginación SQL parcial (`listarPagina/contar` conviven con `listarTodos` legado) | Erradicar listados completos en memoria; paginar/agregar en SQL; índices. |
| **RC-004** | Interfaces frecuentes broker/agente · operabilidad | UI Blazor rica pero atada al circuito Server | Preservar UX al reconstruir en Java; bandejas/atajos por rol. |
| **RC-005** | Comunicación front C# ↔ back Java · interoperabilidad y protección | REST/JSON + JWT + CORS + proxy documentos | Al pasar a Java-fullstack cambia el "front C#"; formalizar contrato de API y protección (ver Doc 5). |

## 7. Registro de mejoras detectadas (vivo — se amplía en Docs 4–6)

> Semilla del backlog de mejoras para el rediseño de BD y del sistema. IDs estables para referenciarlas luego.

- **MEJ-01 (trazabilidad, RC-002)** — Activar `historial_estado` para **las 9 entidades** vía un punto único (p. ej. registrar en `TransactionRunner`/una capa de auditoría en BL), no solo INMUEBLE. Heredar el diseño polimórfico actual (es bueno).
- **MEJ-02 (trazabilidad, RC-002)** — Exponer lectura de historial (REST + UI): timeline por entidad (`listarPorEntidad` ya existe en el DAO, falta BL+REST+pantalla).
- **MEJ-03 (trazabilidad, RC-002)** — Registrar **actor + rol + motivo/observación** en cada transición (el modelo ya tiene `id_usuario` y `observacion`; falta poblarlos siempre).
- **MEJ-04 (publicación, RC-004)** — Explotar el esquema de `publicacion` (multicanal, versión de anuncio, inversión en pauta) con feature/UI real, o simplificar el esquema si el negocio no lo requiere. Decidir en Doc 4.
- **MEJ-05 (rendimiento, RC-003)** — Eliminar `listarTodos` en rutas calientes; garantizar paginación/agregación en SQL + índices para cartera/seguimiento/reportes.
- **MEJ-06 (seguridad, RC-001)** — Mover la autorización por rol a server-side (hoy `RouteAccess` decide en el cliente); el backend debe validar permisos por operación.
- **MEJ-07 (comunicación, RC-005)** — Formalizar el contrato de API (versionado/DTOs estables) para desacoplar el nuevo front Java del backend y proteger la comunicación.
- **MEJ-08 (integridad/auditoría, RC-002)** — Estandarizar columnas de auditoría (`fecha_creacion`/`fecha_actualizacion`/usuario) y estados como enums consistentes en todas las tablas al rediseñar la BD (varias ya las tienen; homogeneizar).
- **MEJ-09 … MEJ-16 (modelado / herencia / generalización)** — Jerarquía `Persona` (estrategia mixta e inconsistente), generalización `Propiedad`/`Inmueble` sobre `LocalComercial`, subtipado/partición de `InteraccionComercial`, enums de tipo duplicados, distrito redundante y discriminadores `CHAR(1)`. **Detalle, evidencia y opciones de rediseño** en el anexo [`modelo-herencia-y-generalizacion.md`](modelo-herencia-y-generalizacion.md).

---

### Estado de este documento
Doc 1 ✅ redactado. Pendientes: Doc 2 (inventario frontend), Doc 3 (inventario backend), Doc 4 (plan), Doc 5 (arquitectura objetivo), Doc 6 (riesgos), Doc 7 (seguridad-no-leer). `CLAUDE.md` se actualizará con el foco de migración al cierre de los inventarios.
