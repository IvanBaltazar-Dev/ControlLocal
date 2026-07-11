# Inventario Frontend Blazor (as-is) — para migración a SPA Angular/React

> Documento 2 de 7 · Fase doc-first. Inventario detallado del frontend actual `frontend-csharp/ControlLocal.Web/` (Blazor Server, .NET 10, render InteractiveServer), con foco en **qué hay que reconstruir en la SPA** y en el **acoplamiento con el backend REST** (la frontera que se conserva).
> Destino: SPA **Angular o React (por decidir)** consumiendo el nuevo backend **Spring Boot**. Verificado con Serena (Roslyn C#).
> 🔎 = observación/mejora; IDs **MEJ-xx** continúan el registro vivo (sin consolidar aún, por indicación).

---

## 1. Estructura

```
ControlLocal.Web/
├── Program.cs                 DI (todo Scoped), middleware seguridad, proxy /documento(s), DataProtection
├── Components/
│   ├── Pages/*.razor          ~57 páginas por feature/rol
│   ├── Layout/                chrome (topbar, sidebar, layout)
│   └── Shared/                componentes reutilizables (filtros, tablas, KPIs…)
├── Services/
│   ├── *.cs                   AppState, RouteAccess, Auth, Navigation, Exportacion, CrashLog, TextoFiltro…
│   └── Api/                   ApiClient, ApiSession, ServiciosApi.cs (mega, 26 servicios), IndicadorService, RemoteCache
├── Models/*/                  ~37 DTOs de vista por dominio + Shared (Paginador, EnumOption, ColoresEstado…)
├── Data/                      CrashLog, TiposContenido
└── wwwroot/                   estáticos (imágenes por ruta plana, NO @Assets)
```

Render **InteractiveServer**: la UI vive en un **circuito SignalR** por usuario (estado en servidor, no en navegador). Esto es lo que **más cambia** al pasar a SPA (estado en cliente).

## 2. Páginas y matriz de autorización (RouteAccess) — línea base RC-001

Autorización **decidida en el cliente** (`Services/RouteAccess.cs`: `PublicPages`, `RolesByPage`, `IsPublic/CanAccess`). Roles: **Admin** = broker administrador, **Broker** = broker supervisor, **Agente** = agente inmobiliario.

| Acceso | Páginas |
|---|---|
| **Público** (sin sesión) | Login, Recover, Error, NotFound, AccesoDenegado |
| **Los 3 roles** | Dashboard, Profile, CambiarContrasena, Reportes, Oportunidades, OportunidadDetail, Clientes, ClienteDetail, ClienteContactoDetail, Owners, OwnerDetail, Locales, LocalDetail, FichaPropiedad, Prospecciones, ProspeccionDetail, Captaciones, CaptacionDetail, Interacciones, InteraccionDetail, Visitas, Solicitudes, SolicitudDetail, PropiedadesAlquiladas, Comisiones |
| **Solo Admin** | Brokers, BrokerProfile, BrokerForm, BrokerNuevo, Reasignar (agente↔broker), Catalogs, HistorialReasignaciones |
| **Solo Broker** | Agents, AgenteDetail, AgenteForm, PropiedadesEquipo, BandejaCaptaciones, CaptacionReview, Evaluacion, SolicitudesRevisar, ReasignarCaptaciones, HistorialReasignacionesCaptaciones |
| **Admin + Broker** | SeguimientoComercial, Cierre |
| **Solo Agente** (crear/operar) | OportunidadForm, ClienteForm, OwnerForm, LocalForm, CaptacionForm, InteraccionForm, VisitaForm, SolicitudForm, Documentos |

🔎 **Patrón de dominio confirmado**: el **Agente** es el único con formularios de alta/operación; **Broker/Admin** ven y **deciden** (revisar, evaluar, reasignar, cerrar). 🔎 **MEJ-25**: esta matriz es **solo cliente**; el backend no la valida por operación → moverla server-side (RC-001, refuerza MEJ-06).

## 3. Servicios `Http*Service` ↔ backend (la frontera / seam)

26 servicios en `Services/Api/ServiciosApi.cs` + `HttpIndicadorService` (IndicadorService.cs) + `HttpAuthService` (ApiClient.cs). Todos reciben `ApiClient` por constructor primario; varios se **componen** entre sí. Muchos llevan **caché de lista en cliente** (`RemoteCache _cache`).

| Servicio (interfaz) | Área REST | Operaciones clave (→ endpoints) | Caché | Compone |
|---|---|---|---|---|
| HttpAuthService (IAuthService) | /auth | LoginAsync, CerrarSesion | — | — |
| HttpPerfilService | /perfil | Obtener, ActualizarTelefono, ActualizarFoto | — | — |
| HttpPropietarioService (IPropietarioService) | /propietarios | All/Refrescar/ById/Agregar/Actualizar | ✅ | — |
| HttpClienteService (IClienteService) | /clientes | All/Refrescar/ById/Obtener/Agregar/Actualizar | ✅ | — |
| HttpFichaComercialService | /fichas (cliente/propietario) | ClienteAsync, PropietarioAsync, *SectionAsync | — | — |
| HttpCoincidenciaCarteraService | matching cartera | PropiedadesParaCliente, ClientesParaCaptacion/Prospeccion | — | — |
| HttpRequerimientoService | /requerimientos | ListarPorCliente, Crear, Actualizar, CambiarEstado | — | — |
| HttpLocalService (ILocalService) | /locales | All/Refrescar/ById/Obtener/Agregar/Actualizar | ✅ | Prospeccion |
| HttpPrecioLocalService | /locales precios | ListarPorLocal, Registrar | — | — |
| HttpPublicacionService | /locales publicaciones | ListarPorLocal, Crear, Actualizar, CambiarEstado | — | — |
| HttpProspeccionService (IProspeccionService) | /prospecciones | Contactar, RegistrarReunion, EntregarPropuesta, RegistrarSeguimiento, Rechazar, Descartar, **Captar**, MarcarCaptado, ListarPagina/Contar, ContarRecontactar | ✅ | — |
| HttpCaptacionService (ICaptacionService) | /captaciones | Agregar, Actualizar, **Cerrar**, ResolverBandeja, ReasignarBandeja, ListarReasignables, Descargar*Pdf (contrato/ficha/captación) | ✅ (_captaciones,_bandeja) | — |
| HttpReasignacionCaptacionService | /reasignaciones-captacion | HistorialAsync | — | — |
| HttpAgenteService (IAgenteService) | /agentes | All/ById/AgentesDelBroker/Agregar/Actualizar/Desactivar | ✅ | — |
| HttpBrokerService (IBrokerService) | /brokers | All/ByCodigo/Agregar/Actualizar | ✅ | — |
| HttpAssignmentService (IAssignmentService) | /asignaciones | Agents, Brokers, Historial, **ReasignarAgente** | — | — |
| HttpOportunidadService (IOportunidadService) | /oportunidades | Crear, **CerrarNoContinua**, **CerrarExitosa**, ListarPagina/PorCaptacion/PorCliente | ✅ | — |
| HttpInteraccionService (IInteraccionService) | /interacciones | Agregar, ListarPorOportunidad/Prospeccion/Captacion/Cliente/**Contexto**, ListarPagina, Actualizar | ✅ | — |
| HttpVisitaService (IVisitaService) | /visitas | **Programar/Reprogramar/Cancelar/MarcarRealizada/MarcarNoRealizada/RegistrarResultado**, ListarProximas/Mes/PorOportunidad | ✅ | Oportunidad |
| HttpSolicitudService (ISolicitudService) | /solicitudes | Agregar, **ReenviarAEvaluacion**, **Evaluar**, ListarEvaluaciones, ByCodigo, ListarPagina/PorCaptacion/PorOportunidad | ✅ | Oportunidad |
| HttpDocumentoSolicitudService (IDocumentoSolicitudService) | /solicitudes/{}/documentos | Listar, **Subir**, **Revisar**, ConformarTodos, EstadoAlmacen | — | — |
| HttpEvaluacion (dentro de Solicitud) | /evaluaciones | EvaluarAsync, ListarEvaluacionesAsync | — | — |
| HttpContratoService (IContratoService) | /contratos | Registrar, **AsignarComision**, **RegistrarCobro**, ByOportunidad | ✅ | — |
| HttpTareaService (ITareaService) | /tareas | Bandeja, BandejaPagina, Cancelar | — | — |
| HttpDashboardService (IDashboardService) | /dashboard | CargarAsync (KPIs + bandeja) | — | — |
| HttpIndicadorService (IIndicadorService) | /indicadores | (indicadores/reportes) | — | — |
| HttpReportePropietarioService | /reportes-propietario | ListarPorCaptacion, Crear, Preview, DescargarPdf | — | — |
| HttpFotoLocalService | /locales fotos | Listar, Subir, Eliminar, DescargarBytes | — | — |
| HttpAlertaService (INotificacionService) | /alertas | MisNotificaciones, NoLeidas, MarcarLeida/Todas, Crear, RefrescarBackendSiHaceFalta | ✅ (TTL) | AppState, NotificacionStore |

🔎 **Las operaciones del cliente espejan las máquinas de estado del backend** (Prospeccion: contactar→…→captar; Visita: programar→realizada→resultado; Solicitud: reenviar→evaluar; Captacion: cerrar/resolver). **No hay lógica de negocio duplicada** — el cliente solo invoca endpoints; las reglas viven en BL. Buena señal para la migración: la SPA re-consume los mismos endpoints. 🔎 **MEJ-29**: `ServiciosApi.cs` es un **mega-archivo (~3.400 líneas, 26 servicios)** → en SPA, un servicio/módulo por dominio.

## 4. Capa de datos del frontend (duplicación a eliminar)

- **Doble DTO**: DTOs de **transporte** (`*Api`: `PropietarioApi`, `CaptacionApi`, `OportunidadApi`, `SolicitudApi`…) que reflejan el JSON del backend, **+** DTOs de **vista** (`Models/*/*.cs`, ~37) que consume la UI, **+ métodos `Mapear`** entre ambos. 🔎 **MEJ-26**: en la SPA, **generar tipos desde OpenAPI** del backend Spring y eliminar el mapeo manual (RC-005: contrato formal).
- **`Codigos`** (traductor): convierte los **códigos CHAR(1)** del backend (`EstadoCaptacion`, `EstadoOportunidad`, `TipoPersona`, `EstadoDocumento`…) a etiquetas legibles en cliente. 🔎 **MEJ-27**: ligado a MEJ-16/17 (homogeneizar enums en BD); con enums legibles/catálogo, esta traducción cliente se reduce.
- **`RemoteCache`** (`_cache`) en ~12 servicios: caché de listas por circuito con invalidación en mutaciones. 🔎 **MEJ-28**: re-arquitecturar como **estado de la SPA** (Angular signals/services o React Query/SWR) con invalidación por mutación.
- Shared: `Paginador`, `EnumOption`, `ColoresEstado`, `PeruInputRules`, `BreadcrumbItem` — utilidades de UI a reimplementar en TS.

## 5. Sesión, estado y cliente REST (línea base RC-005)

- **`ApiClient`** (Services/Api): cliente REST central. `LoginAsync`, `Get/Post/Put/Delete/Patch(+<T>)`, **paginación** (`GetPaginaAsync`/`GetTodasPaginasAsync`), `GetBytes/PostBytes`. Adjunta el **JWT** desde `ApiSession` (Bearer). `SocketsHttpHandler` con `PooledConnectionLifetime=30s` (mitiga conexiones keep-alive que GlassFish cerraba).
- **`ApiSession`** (Scoped): guarda el `Token` (JWT) del circuito.
- **`AppState`** (Scoped): `Role/CurrentUser/IsAuthenticated`; `SignIn/SignOut(+Async)`, `EnsureInitializedAsync`; **persistencia de sesión al navegador vía JS interop** (`BrowserSession`, `Guardar/LimpiarSesionAsync`). Un 401 dispara `SignOut` completo.
- **`NotificacionStore`** (Singleton) + vista Scoped por usuario: notificaciones in-app (campana en Topbar), alimentadas por `HttpAlertaService` desde `/alertas`.
- 🔎 En SPA: JWT en almacenamiento del navegador (o cookie httpOnly), interceptor HTTP que añade el Bearer y maneja 401→logout, y store de notificaciones (polling o SSE/WebSocket).

## 6. Mecanismos Blazor-específicos → equivalente en SPA (riesgo de migración)

| Mecanismo Blazor (actual) | Equivalente SPA (Angular/React) | Nota |
|---|---|---|
| Circuito **InteractiveServer** (estado en servidor + SignalR) | Estado en cliente (signals/stores) + REST | Cambio de paradigma mayor |
| `AppState` Scoped por circuito | Store global (NgRx/signals · Redux/Context/Zustand) | Sesión + rol + navegación |
| `RouteAccess` (guard por rol en cliente) | Route guards (Angular) / route wrappers (React) **+ validación server-side** | RC-001 (MEJ-25) |
| **JS interop** (`BrowserSession`, foco, scroll) | APIs del navegador nativas en TS | Directo |
| Proxy **`/documento` y `/documentos`** en `Program.cs` (descarga del backend sin exponer JWT, fija cabeceras para previsualización inline) | Endpoint en el backend Spring o proxy del BFF; visor con URL firmada | Reubicar en backend |
| **DataProtection keys** (temp dir) | N/A (no aplica sin Blazor) | Eliminar |
| Middleware de cabeceras de seguridad (`X-Frame-Options`, CSP…) | Config del backend/hosting | Reubicar |
| `wwwroot` estáticos (imágenes por ruta plana, **no `@Assets`**) | assets de la SPA (Vite/Angular CLI) | El caveat `@Assets` desaparece |
| `Exportacion`/`FichaPropiedadPdf`/`ReporteIndicadores` (helpers de export cliente) | El PDF ya se genera en backend (Jasper); la SPA solo descarga | Simplifica |
| `CrashLog` (captura de crashes del circuito) | Error boundary + logging cliente | Reimplementar |

## 7. Mapa de reconstrucción para la SPA (resumen)
1. **Contrato REST estable** (OpenAPI del backend Spring) → tipos TS autogenerados (elimina `*Api`+`Mapear`, **MEJ-26**).
2. **~57 pantallas** por rol (matriz §2) → rutas + guards; el Agente concentra los formularios de alta.
3. **28 servicios** → clientes HTTP por dominio (§3), reusando las mismas operaciones/endpoints.
4. **Estado/sesión** → store + interceptor JWT + 401→logout (§5); caché de listas → estado SPA (**MEJ-28**).
5. **Autorización** → guards en cliente **respaldados por validación server-side** (**MEJ-25**, RC-001).
6. **Notificaciones** → polling/SSE contra `/alertas`.
7. **Documentos/PDF** → descarga desde backend; reubicar el proxy `/documento` (§6).

## 8. Nuevas mejoras (frontend) — registro vivo
- **MEJ-25** — Autorización solo-cliente (`RouteAccess`): validar permisos por operación en el backend (RC-001).
- **MEJ-26** — Doble DTO + `Mapear` manual: generar tipos desde OpenAPI en la SPA (RC-005).
- **MEJ-27** — `Codigos` traduce CHAR(1)→etiqueta en cliente: se reduce al homogeneizar enums (MEJ-16/17).
- **MEJ-28** — Caché de lista en cliente (`RemoteCache`): re-arquitecturar como estado SPA con invalidación por mutación.
- **MEJ-29** — `ServiciosApi.cs` mega-archivo: dividir por dominio en la SPA.

---

### Estado
Doc 2 ✅ (frontend detallado). Pendientes: Doc 4 (plan, con MEJ-01…MEJ-29 consolidados y priorizados + secuencia por RC), Doc 5 (arquitectura objetivo Spring Boot + SPA), Doc 6 (riesgos), Doc 7 (seguridad-no-leer). Preguntas abiertas para el plan: frontend Angular vs React; persistencia Spring (JPA vs JDBC) — condiciona la estrategia de herencia de `Persona`.
