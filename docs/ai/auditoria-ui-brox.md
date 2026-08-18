# Auditoría transversal de la interfaz de BROX

**Qué responde:** qué hay hoy en el SPA, qué se repite, qué se contradice y qué
hay que hacer con cada cosa — antes de rediseñar ningún componente.

**Hecha el 2026-08-17** recorriendo `frontend-angular/src/app` entero: 64 rutas,
57 carpetas de pantalla, 29 servicios, `core/auth/acceso.ts`, `layout/shell.*` y
`styles.scss`, contrastado con `docs/ai/matriz-operacion-rol.md`.

**Es medición, no opinión.** Los recuentos salen de recorrer los archivos; lo
que va a ojo —propósito, duplicidad, veredicto— está marcado como tal. El script
que recolecta está descrito en §9 para que cualquiera repita la cuenta.

**Compañeros:** `mapa-pantalla-dominio-backend.md` (qué dato sale de dónde) y
`decision-sidebar-brox.md` (la navegación que sale de aquí).

---

## 1. Lo que hay que saber si solo se leen diez líneas

| # | Hallazgo | Medida | Veredicto |
|---|---|---|---|
| 1 | **El menú tiene colas de trabajo como si fueran secciones.** «Captaciones por revisar» y «Solicitudes por revisar» son la misma lista que su bandeja, filtrada por estado | 2 entradas de 26 | **REUBICAR** al Inicio |
| 2 | **La barra superior dice `Panel` en las 57 pantallas.** Es un literal en `shell.html`, no un título | 1 literal | **REDISEÑAR** |
| 3 | **51 pantallas se pintan su propia miga de pan** porque el shell no da contexto | 51 de 57 | **UNIFICAR** en `BroxPageHeader` |
| 4 | **No hay componente de cabecera ni de tabla: hay clases CSS.** `.cl-cabecera` en 44 pantallas, `.cl-tabla` en 35, y cada una monta su `<table>` a mano | 35 tablas | **UNIFICAR** |
| 5 | **Tres paletas conviven.** 14 tokens en `:root` y **60 hex distintos** cocinados dentro de 37 pantallas | 60 hex | **UNIFICAR** en tokens |
| 6 | **Angular decide qué significa cada estado.** 167 comparaciones `estado === 'X'` en 26 pantallas y 16 pantallas que eligen color con un ternario | 167 | **BACKEND_FALTANTE** |
| 7 | **El SPA no tiene móvil.** `shell.scss` no tiene una sola media query: el armazón es `grid 15.5rem 1fr` a cualquier ancho | 0 de 26 | **REDISEÑAR** |
| 8 | **Cuatro pares de pantallas son el mismo objeto con otro alcance o filtro** | 4 pares | **UNIFICAR** |
| 9 | **Tres superficies compiten por «qué tengo pendiente»**: Dashboard, Seguimiento e Indicadores | 3 | **UNIFICAR** |
| 10 | **La navegación coincide con la matriz de permisos**, salvo dos entradas que la endurecen a propósito y no lo documentan | 2 de 26 | **CONSERVAR** documentando |

**La conclusión que ordena el resto:** BROX ya tiene un sistema de diseño **a
nivel de CSS** (`styles.scss` con `.cl-*`) y ninguno **a nivel de componente**.
Por eso la anatomía no se puede imponer: una clase se copia, se olvida o se
sobreescribe, y eso es exactamente lo que ha pasado 60 veces con el color y 51
con la miga de pan.

---

## 2. A1 · Inventario pantalla → propósito → rol → backend

57 carpetas de `features/`, con lo que consume y lo que arrastra. **Rol** es
quién la alcanza hoy (guard del router o `MODULOS`); **Servicios** son los
recursos REST que inyecta.

| Ruta | Pantalla | Propósito | Rol | Dominio | Objeto | Servicios (recursos backend) | Acción principal | Estado principal | KPI | Componentes repetidos | Deuda visual/semántica | Problema | Duplicidad |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/acceso-denegado` | acceso-denegado | Destino del guard | TODOS | — | — | — | Volver | — | No | — | 1 hex | — | — |
| `/agentes/:id` | agente-detail | Ficha del agente | BROKER · ADMIN | PERSONAS | Agente | agentes | Editar | activo | No | tabla, vacío/carga | 9 hex | — | — |
| `/agentes/nuevo · /:id/editar` | agente-form | Alta y edición de agente | ADMIN | PERSONAS | Agente | agentes | Guardar | — | No | filtros, vacío/carga | — | — | broker-form es gemela |
| `/agentes` | agentes | Padrón de agentes | BROKER · ADMIN | PERSONAS | Agente | agentes | Alta | activo | Sí | tabla, filtros, paginación, KPI, confirmación, vacío/carga | 4 hex · 4 `estado===` | — | Mi equipo |
| `/asignaciones` | asignaciones | Organigrama e historial de asignación | ADMIN | PERSONAS | Asignación | asignaciones | Reasignar | — | No | tabla, filtros, tabs, timeline, vacío/carga | 7 hex | — | reasignaciones-captacion |
| `/captaciones/pendientes` | bandeja-captaciones | Cola de encargos por revisar | BROKER · ADMIN | OFERTA | Captación | captaciones, personal | Ir a revisar | Estado de captación | Sí | tabla, filtros, paginación, KPI, vacío/carga | 3 `estado===` · 1 ternarios de color | Cola en el menú, no en el Inicio | **Captaciones filtrada** |
| `/brokers/:id` | broker-detail | Ficha del broker + su equipo | TODOS | PERSONAS | Broker | agentes, brokers | Editar | activo | No | tabla, vacío/carga | 5 hex | — | **mi-equipo** |
| `/brokers/nuevo · /:id/editar` | broker-form | Alta y edición de broker | ADMIN | PERSONAS | Broker | brokers | Guardar | — | No | filtros, vacío/carga | — | — | **agente-form** |
| `/brokers` | brokers | Padrón de brokers | TODOS | PERSONAS | Broker | brokers | Alta | activo | No | tabla, paginación, vacío/carga | 4 hex | Visible para el agente sin razón operativa | agentes |
| `/cambiar-contrasena` | cambiar-contrasena | Cambio obligado de contraseña | TODOS | — | — | contrasenas | Guardar | — | No | — | 4 hex | Fuera del shell a propósito | — |
| `/captaciones/:codigo` | captacion-detail | Expediente del encargo | TODOS | OFERTA | Captación | captaciones, locales, prospecciones | Cerrar / decidir | Estado de captación | No | confirmación, vacío/carga | 2 hex · 10 `estado===` | — | ficha-propiedad |
| `/captaciones/nueva · /:codigo/editar` | captacion-form | Alta y edición de encargo | AGENTE | OFERTA | Captación | captaciones, locales, prospecciones | Guardar | — | No | filtros, vacío/carga | 2 hex · 1 `estado===` | — | — |
| `/captaciones/:codigo/revisar` | captacion-review | Decisión del broker sobre un encargo | BROKER | OFERTA | Captación | captaciones, locales, personal, prospecciones | Aprobar / observar | Estado de captación | No | filtros, confirmación, vacío/carga | 3 hex · 2 `estado===` | — | — |
| `/captaciones` | captaciones | Bandeja general de encargos | TODOS | OFERTA | Captación | captaciones, personal | Ir a la ficha | Estado de captación | Sí | tabla, filtros, paginación, KPI, vacío/carga | 12 `estado===` · 1 ternarios de color | — | Por revisar es la misma lista filtrada |
| `/catalogos` | catalogos | Consulta de los códigos del dominio | TODOS | GESTIÓN | — | — | — | — | No | tabla, tabs, timeline | 5 hex | Sin endpoint: es una constante del SPA | — |
| `/clientes/:id/contacto` | cliente-contacto-detail | Bitácora de contacto del cliente | TODOS | DEMANDA | Interacción | clientes, interacciones, oportunidades | Registrar contacto | Resultado | No | tabla, vacío/carga | 2 hex · 5 `estado===` · 1 ternarios de color | — | **Interacciones** |
| `/clientes/:id` | cliente-detail | Expediente del cliente + requerimientos | AGENTE | DEMANDA | Cliente | clientes, coincidencias, ficha-comercial, requerimientos | Editar requerimiento | Estado de requerimiento | No | tabla, filtros, paginación, tabs, vacío/carga | 1 hex · 3 `estado===` · 3 ternarios de color | 555 líneas: es cuatro pantallas | Requerimientos no tiene pantalla propia |
| `/clientes/nuevo · /:id/editar` | cliente-form | Alta y edición de cliente | AGENTE | DEMANDA | Cliente | clientes | Guardar | — | No | filtros, vacío/carga | — | — | — |
| `/clientes` | clientes | Catálogo de clientes/interesados | TODOS | DEMANDA | Cliente | clientes | Alta | activo | Sí | tabla, filtros, paginación, KPI, confirmación, timeline, vacío/carga | 3 `estado===` · 4 ternarios de color | — | — |
| `/comisiones` | comisiones | Liquidación de comisiones | TODOS | CIERRE | Comisión | contratos | Asignar / cobrar | Estado de comisión | Sí | tabla, filtros, paginación, KPI, vacío/carga | 2 ternarios de color | — | **propiedades-alquiladas** |
| `/` | dashboard | Home: señales del día + bandeja de tareas del agente | TODOS | GESTIÓN | — | dashboard, indicadores, tareas | Abrir la tarea | nivelAtencion (del backend) | Sí | tabla, filtros, KPI, drawer, confirmación, vacío/carga | 4 hex | Radar/foco de D-E2-1 sin construir | Se solapa con Seguimiento e Indicadores |
| `/solicitudes/:codigo/documentos` | documentos-solicitud | Expediente documental | TODOS | CIERRE | Documento | solicitudes | Conformar / observar | Estado de documento | No | tabla, filtros, confirmación, vacío/carga | 2 hex · 8 `estado===` | — | — |
| `/enrolar-mfa` | enrolar-mfa | Alta de segundo factor | TODOS | — | — | mfa | Confirmar | — | No | — | 4 hex | Fuera del shell a propósito | — |
| `/solicitudes/:codigo/evaluar` | evaluacion-solicitud | Decisión del broker sobre la solicitud | BROKER | CIERRE | Evaluación | evaluaciones, solicitudes | Aprobar / rechazar | Estado de evaluación | No | tabla, confirmación, timeline, vacío/carga | 2 hex · 5 `estado===` | Sin listado de evaluaciones | — |
| `/captaciones/:codigo/ficha` | ficha-propiedad | Ficha comercial de la captación | TODOS | OFERTA | Captación | captaciones, locales, propietarios | Subir fotos | Estado de captación | No | vacío/carga | 1 hex · 4 `estado===` · 3 ternarios de color | Se solapa con el detalle del local | local-detail |
| `/indicadores` | indicadores | Rendimiento comercial del ámbito | TODOS | GESTIÓN | — | indicadores | Cambiar periodo | — | Sí | tabla, filtros, KPI, timeline, vacío/carga | — | Los KPI no traen meta ni ritmo | Reportes repite parte |
| `/interacciones/:id` | interaccion-detail | Detalle de una interacción | TODOS | DEMANDA | Interacción | interacciones | Editar | Resultado | No | filtros, vacío/carga | 1 ternarios de color | — | — |
| `/interacciones/nueva` | interaccion-form | Registrar interacción | AGENTE | DEMANDA | Interacción | captaciones, clientes, interacciones, oportunidades, prospecciones | Guardar | — | No | filtros, vacío/carga | — | Depende de 5 servicios | — |
| `/interacciones` | interacciones | Bandeja de interacciones | TODOS | DEMANDA | Interacción | interacciones | Ir al detalle | Resultado | No | tabla, filtros, paginación, tabs, timeline, vacío/carga | 1 ternarios de color | — | **cliente-contacto-detail** |
| `/locales/:id` | local-detail | Expediente del inmueble | TODOS | OFERTA | LocalComercial | locales, prospecciones | Editar / publicar | estadoLocal + publicación | No | tabla, filtros, vacío/carga | 1 hex · 12 `estado===` · 4 ternarios de color | Cuatro paneles compiten por la atención | Ficha de propiedad |
| `/locales/nuevo · /locales/:id/editar` | local-form | Alta y edición de inmueble | AGENTE | OFERTA | LocalComercial | locales, propietarios | Guardar | — | No | filtros, vacío/carga | 35 hex | Paleta propia de 35 hex | — |
| `/locales` | locales | Cartera de inmuebles de la organización | TODOS | OFERTA | LocalComercial | locales | Alta de local | estadoLocal (D/N/I) | Sí | tabla, filtros, paginación, KPI, vacío/carga | 5 hex · 3 `estado===` | — | Cartera del equipo es el mismo objeto |
| `/login` | login | Entrar | PÚBLICO | — | — | — | Entrar | — | No | — | 4 hex | — | — |
| `/mi-equipo` | mi-equipo | Equipo del broker con sesión | BROKER | PERSONAS | Agente | agentes, brokers | Ir al agente | activo | Sí | tabla, vacío/carga | 9 hex | — | **broker-detail** |
| `/oportunidades/:id` | oportunidad-detail | Expediente de la oportunidad | TODOS | DEMANDA | Oportunidad | interacciones, oportunidades, visitas | Cerrar / no continuidad | Estado de oportunidad | No | tabla, filtros, confirmación, vacío/carga | 1 hex · 15 `estado===` | 15 comparaciones de estado | — |
| `/oportunidades/nueva` | oportunidad-form | Alta de oportunidad | AGENTE | DEMANDA | Oportunidad | captaciones, clientes, locales, oportunidades | Guardar | — | No | filtros, vacío/carga | 3 hex | — | — |
| `/oportunidades` | oportunidades | Bandeja de oportunidades | TODOS | DEMANDA | Oportunidad | oportunidades | Ir al detalle | Estado de oportunidad | Sí | tabla, filtros, paginación, KPI, vacío/carga | 7 `estado===` | — | — |
| `/perfil` | perfil | Mi cuenta, contraseña y MFA | TODOS | GESTIÓN | Perfil | mfa, perfil | Cambiar contraseña | — | No | confirmación, vacío/carga | 4 hex | — | — |
| `/privacidad` | privacidad | Aviso de privacidad (D-27) | PÚBLICO | — | — | aviso-privacidad | — | — | No | — | 2 hex | — | — |
| `/propiedades-alquiladas` | propiedades-alquiladas | Contratos firmados | TODOS | CIERRE | Contrato | contratos | Exportar | Estado de contrato | Sí | tabla, filtros, paginación, KPI, vacío/carga | 9 `estado===` · 3 ternarios de color | Se llama «Cierres exitosos» | Comisiones (mismo recurso) |
| `/propiedades-equipo` | propiedades-equipo | Cartera de los agentes supervisados | BROKER · ADMIN | OFERTA | LocalComercial | captaciones | Ir a la captación | Estado de captación | Sí | tabla, filtros, paginación, KPI, vacío/carga | 4 `estado===` · 2 ternarios de color | — | **Locales con otro alcance** |
| `/propietarios/:id` | propietario-detail | Expediente del propietario | TODOS | OFERTA | Propietario | ficha-comercial, propietarios | Editar | — | No | tabla, paginación, tabs, vacío/carga | 3 hex · 1 `estado===` | — | — |
| `/propietarios/nuevo · /:id/editar` | propietario-form | Alta y edición de propietario | AGENTE | OFERTA | Propietario | propietarios | Guardar | — | No | filtros, vacío/carga | — | — | — |
| `/propietarios` | propietarios | Catálogo de propietarios | TODOS | OFERTA | Propietario | propietarios | Alta | activo | Sí | tabla, filtros, paginación, KPI, confirmación, vacío/carga | 3 hex · 5 `estado===` | — | — |
| `/prospecciones/:id` | prospeccion-detail | Expediente de la prospección | TODOS | OFERTA | Prospección | prospecciones | Descartar / captar | Estado de prospección | No | confirmación, vacío/carga | 2 hex · 9 `estado===` | — | — |
| `/prospecciones` | prospecciones | Prospectos y su recontacto | TODOS | OFERTA | Prospección | personal, prospecciones | Registrar contacto | Estado de prospección | Sí | tabla, filtros, paginación, KPI, vacío/carga | 3 hex · 11 `estado===` · 1 ternarios de color | — | Seguimiento repite el vencimiento |
| `/captaciones/reasignaciones` | reasignaciones-captacion | Reasignar encargos entre agentes | BROKER · ADMIN | OFERTA | Captación | captaciones, personal | Reasignar | — | Sí | tabla, filtros, paginación, KPI, confirmación, timeline, vacío/carga | 2 hex | — | Asignaciones (organigrama) |
| `/recuperar` | recuperar-acceso | Recuperar acceso | PÚBLICO | — | — | contrasenas | Canjear token | — | No | — | 4 hex | — | — |
| `/reportes` | reportes | Avance comercial por propiedad (RF-017) | TODOS | GESTIÓN | Captación | indicadores | — | Estado de captación | Sí | tabla, KPI, vacío/carga | 1 ternarios de color | Sin acción: solo mira | Indicadores + Cartera del equipo |
| `/seguimiento-comercial` | seguimiento-comercial | Recontactos y actividad pendiente | TODOS | GESTIÓN | Prospección/Cliente | seguimiento | Ir al expediente | Vencido / al día | Sí | tabla, filtros, paginación, KPI, vacío/carga | — | Tercera lista de pendientes | Dashboard e Inicio |
| `/seguridad` | seguridad | Padrón de cuentas y avisos | ADMIN | PERSONAS | Acceso | mfa, seguridad | Revocar MFA | Estado de cuenta | No | tabla, confirmación, vacío/carga | 2 hex | — | — |
| `/solicitudes/:codigo` | solicitud-detail | Expediente de la solicitud | TODOS | CIERRE | Solicitud | captaciones, contratos, solicitudes | Reenviar | Estado de solicitud | No | tabla, filtros, confirmación, timeline, vacío/carga | 1 hex · 11 `estado===` | — | — |
| `/solicitudes/nueva` | solicitud-form | Alta de solicitud | AGENTE | CIERRE | Solicitud | oportunidades, solicitudes | Guardar | — | No | filtros, vacío/carga | 3 hex | — | — |
| `/solicitudes` | solicitudes | Bandeja de solicitudes de alquiler | TODOS | CIERRE | Solicitud | solicitudes | Ir al detalle | Estado de solicitud | Sí | tabla, filtros, paginación, KPI, vacío/carga | 10 `estado===` | — | Por revisar es la misma lista |
| `/solicitudes/revisar` | solicitudes-revisar | Cola de solicitudes por evaluar | BROKER | CIERRE | Solicitud | solicitudes | Ir a evaluar | Estado de solicitud | Sí | tabla, filtros, paginación, KPI, vacío/carga | 1 `estado===` · 1 ternarios de color | Cola en el menú, no en el Inicio | **Solicitudes filtrada** |
| `/visitas/nueva` | visita-form | Programar visita | AGENTE | DEMANDA | Visita | oportunidades, visitas | Guardar | — | No | filtros, vacío/carga | — | — | — |
| `/visitas` | visitas | Agenda de visitas | TODOS | DEMANDA | Visita | visitas | Realizar / reprogramar | Estado de visita | Sí | tabla, filtros, paginación, KPI, confirmación, vacío/carga | 3 hex · 9 `estado===` · 1 ternarios de color | 511 líneas de TS | — |

### 2.1 Lo que la tabla enseña de un vistazo

| Anatomía | Pantallas | Lectura |
|---|---|---|
| Estado de carga/vacío compartido (`cl-estado-listado`) | **50 de 57** | Lo único ya normalizado de verdad |
| Filtros | 37 | 17 usan `cl-barra-filtros`; **20 lo hacen a mano** |
| Tabla | 35 | ninguna comparte componente |
| Paginación (`cl-paginacion`) | 20 | consistente |
| KPI (`cl-tarjeta-kpi`) | 19 | consistente en forma, no en semántica (§6) |
| Confirmación (`cl-dialogo-confirmacion`) | 15 | consistente |
| Pestañas | **5** | y cuatro de ellas con marcado propio |
| Panel lateral (`cl-panel-lateral`) | **1** | solo el dashboard |

> **20 pantallas filtran a mano teniendo `cl-barra-filtros`.** No es que falte
> el componente: es que no hay nada que obligue a usarlo.

### 2.2 Las cuatro parejas que son el mismo objeto

| Pareja | Qué las separa de verdad | Veredicto |
|---|---|---|
| `Locales` ↔ `Cartera del equipo` | solo el **alcance**, que ya resuelve el backend | **UNIFICAR** en un filtro |
| `Captaciones` ↔ `Captaciones por revisar` | solo el **estado** por el que se filtra | **UNIFICAR** + la cola al Inicio |
| `Solicitudes` ↔ `Solicitudes por revisar` | solo el **estado** | **UNIFICAR** + la cola al Inicio |
| `Cierres exitosos` ↔ `Comisiones` | el **momento** del mismo contrato: firmado y cobrado | **CONSERVAR** separadas, **RENOMBRAR** la primera a *Contratos* |

Y dos más que no son bandejas pero cuentan lo mismo:

- `captacion-detail` ↔ `ficha-propiedad`: **dos expedientes del mismo encargo**.
- `interacciones` ↔ `cliente-contacto-detail`: **dos superficies de la misma
  conversación**.

### 2.3 Tres pantallas responden «qué tengo pendiente»

`Dashboard` (bandeja de tareas), `Seguimiento` (recontactos vencidos) e
`Indicadores` (señales con `nivelAtencion`). Las tres leen del mismo motor y
ninguna dice de cuál de las otras dos se distingue.

**Veredicto: UNIFICAR** bajo la frontera que D-E2-1 ya fija — el **Inicio**
decide *qué resolver ahora* (hasta cinco asuntos), **Seguimiento** es la lista
completa con su plazo, **Indicadores** es la superficie analítica. Si esa
frontera no se respeta, sobra una.

---

## 3. A2 · El armazón, auditado como una sola unidad

`layout/shell.html` son 77 líneas y `layout/shell.scss` 177. Todo lo que sigue
sale de ahí.

| Pieza | Qué hay hoy | Problema | Veredicto |
|---|---|---|---|
| **Ancho del lateral** | `15.5rem` fijo | no colapsa ni en pantalla pequeña | **REDISEÑAR** |
| **Marca** | recuadro `CL` + «ControlLocal» | el producto se llama **BROX** en toda la documentación de E2 y en los dos prototipos | **RENOMBRAR** |
| **Iconografía del menú** | ninguna: un `<span class="punto">` de 6 px igual para las 26 entradas | el menú no se puede leer de un vistazo; en colapsado no quedaría nada | **REDISEÑAR** |
| **Agrupadores** | `Panel · Oferta · Proceso · Demanda · Cierre · Gestión` | `Proceso` mezcla oferta (captaciones) con supervisión (cartera del equipo); `Gestión` acumula 9 entradas | **REUBICAR** |
| **Estado activo** | fondo dorado 14 % **+** texto blanco **+** punto dorado | tres señales para una cosa (§B4 del encargo pide una) | **REDISEÑAR** |
| **Fondo del lateral** | degradado `#0e3a4c → #0a2a38` | el único degradado del producto; los hex están fuera de tokens | **CONSERVAR** el oscuro, **UNIFICAR** a token plano |
| **Pie del lateral** | «backend v2 · Spring + Angular» | detalle de implementación en la cara del usuario | **ELIMINAR** |
| **Título de la barra superior** | `<span class="titulo">Panel</span>` **literal** | dice «Panel» en las 57 pantallas | **REDISEÑAR** |
| **Miga de pan** | no existe en el shell; **51 pantallas** se pintan una `.miga` | el contexto se escribe 51 veces | **UNIFICAR** |
| **Campana** | `cl-campana` en el shell | correcto: es chrome global | **CONSERVAR** |
| **Usuario** | avatar + nombre + rol + botón `Salir` | «Salir» compite visualmente con la acción de la página | **REUBICAR** al pie del lateral |
| **Selector de organización** | no existe | el modelo ya es multi-tenant | **BACKEND_FALTANTE** (no bloquea) |
| **Carga y error globales** | no hay; cada pantalla los resuelve con `cl-estado-listado` | lo de dentro está bien; **falta el de navegación** | **CONSERVAR** + añadir el global |
| **Modales** | `cl-dialogo-confirmacion` en 15 pantallas | consistente | **CONSERVAR** |
| **Drawer** | `cl-panel-lateral`, **1 pantalla** | patrón sin adoptar | **CONSERVAR** y extender |
| **Navegación móvil** | **no existe** | 0 media queries en `shell.scss` | **REDISEÑAR** |

### 3.1 Alturas y arranques

- El contenido arranca con `padding: 1.5rem` uniforme — **esto sí es coherente**.
- La cabecera de página no: **44** pantallas usan `.cl-cabecera`, **7** una
  `.cabecera` propia (los seis formularios y `locales`) y **6** abren
  directamente con `<h1>` (las de fuera del shell y `acceso-denegado`).
- Las de fuera del shell (`login`, `recuperar`, `cambiar-contrasena`,
  `enrolar-mfa`) repiten el mismo SCSS con el degradado `#0e3a4c → #071e29`, y
  dos de ellas son **el mismo archivo byte a byte** (§7.2). **UNIFICAR**.

---

## 4. A3 · Permisos y navegación, auditados juntos

**El SPA ya hace lo correcto de fondo:** `core/auth/acceso.ts` declara los 26
módulos con sus roles, `rolGuard` lee ese mismo mapa y el menú se dibuja de ahí.
No hay una lista escrita a mano en el HTML. Eso se **CONSERVA**.

### 4.1 Contraste con la matriz operación → rol

De las 26 entradas, **24 coinciden** con la columna Roles de su operación de
entrada. Las dos que no:

| Entrada | Menú | Matriz | Lectura |
|---|---|---|---|
| **Solicitudes por revisar** | `BROKER` | `GET /solicitudes` es `TODOS` | El menú es **más estricto a propósito**: la pantalla existe para llegar a `POST /evaluaciones`, que sí es `BROKER`. Correcto, pero la razón vive en un comentario y no en el dato |
| **Mi equipo** | `BROKER` | `GET /brokers/{id}/agentes` es `TODOS` | Igual: no es restricción de acceso sino de **sentido** (un agente no tiene equipo) |

**Veredicto: CONSERVAR**, con un cambio de forma — el módulo debe declarar
**por qué** endurece (`operacionDeEntrada` y `operacionDeSalida`), para que la
prueba de navegación (Fase G) pueda distinguir «más estricto a propósito» de
«desajustado».

### 4.2 Rutas visibles, rutas alcanzables

- **13 rutas** llevan gate de rol en el router; todas son formularios o colas de
  decisión, y todas cuadran con la matriz. **CONSERVAR**.
- **37 de 64 rutas no están en el menú** — detalles y formularios. Es correcto
  (se llega desde su listado), pero significa que **la navegación contextual es
  el único camino**: sin miga de pan en el shell, volver depende de que cada
  pantalla se pinte la suya. Es la causa raíz del hallazgo 3.
- **Ninguna ruta visible acaba en 403** por sí sola: el guard usa el mismo mapa
  que el menú. Lo que sí ocurre es que **entradas visibles llegan vacías**: el
  dashboard de un BROKER pinta la bandeja del agente, que sale de `/tareas`
  —solo del agente— y llega vacía. No es un 403, pero es una promesa incumplida.
  **BACKEND_FALTANTE** (es la Tanda 4).

### 4.3 Las cinco preguntas del encargo, por entrada

Respondidas en `decision-sidebar-brox.md` §3, una fila por entrada. El resumen:

- **18 entradas** para el agente, **24** para broker y administrador.
- **9 de 26** cambian por **alcance**, no por permiso — el backend ya lo resuelve
  y el menú no debería duplicarlo.
- **4 entradas no tienen razón de negocio para ser primarias**: Catálogos
  (consulta de códigos), Brokers (para un agente), Reportes (solapa Indicadores)
  y Cartera del equipo (es Locales con otro alcance).

### 4.4 La regla que falta

Hoy **el permiso de una acción se decide dentro de cada componente**. Ejemplo
real: `esAgente()`, `soloLectura` y comparaciones de rol repartidas por las
fichas. No es catastrófico —el backend manda igual— pero hace imposible
responder «¿qué puede hacer un broker aquí?» sin leer la plantilla.

**Veredicto: BACKEND_FALTANTE + REDISEÑAR.** Una política central de
capacidades (`core/auth/capacidades.ts`) que responda `puede('CAPTACION_DECIDIR')`
a partir de la sesión **y**, cuando exista, de las capacidades que devuelva el
backend. Prohibido el ternario de rol suelto en la plantilla.

---

## 5. A4 · El sistema visual

### 5.1 Lo que hay

`styles.scss` declara **14 tokens** en `:root` y **20 primitivas** `.cl-*`
(cabecera, tarjeta, tabla, badge, botón, pestañas, formulario, campo…).

```
--cl-fondo  --cl-superficie  --cl-borde  --cl-tinta  --cl-tinta-suave
--cl-primario  --cl-primario-claro  --cl-acento  --cl-exito  --cl-error
--cl-radio  --cl-sombra
```

Faltan por completo: **escala de espaciado, tipografía, z-index, movimiento y
breakpoints**. Cada pantalla los inventa.

### 5.2 Lo que se ha escapado

| Valor | Distintos fuera de token | Dónde duele |
|---|---|---|
| **Color** | **60 hex** en 37 pantallas | `local-form` sola tiene **35** |
| **Radio** | 21 declaraciones distintas | hay `--cl-radio` y casi nadie lo usa |
| **Sombra** | 12 distintas | hay `--cl-sombra` |
| **Breakpoint** | **9 distintos** en 26 media queries | `46rem`, `44rem`, `60rem`, `64rem`, `720px`, `760px`, `1050px`, `1100px` |

**Tres paletas conviven**, y se distinguen a simple vista:

1. **Petróleo + dorado** (`#0e3a4c`, `#d9a441`) — la del producto, en `:root`.
2. **Slate + blue-500 de Tailwind** (`#e2e8f0`, `#2563eb`, `#f8fafc`, `#94a3b8`,
   `#dbeafe`, `#15803d`, `#b45309`) — en las 9 pantallas de personas y
   administración (`agentes`, `brokers`, `mi-equipo`, `asignaciones`, `perfil`,
   `seguridad`, `catalogos`, `agente-detail`, `broker-detail`). Nadie decidió
   esto: se copió de una pantalla a otra.
3. **Cálida de BROX** (`#2f2923`, `#e1dcd4`, `#766d64`…) — solo en `local-form`,
   que es la única migrada al lenguaje nuevo.

**Veredicto: UNIFICAR.** Y el orden importa: primero la clasificación
primitivo → semántico → componente que pide la Fase C, después la migración. La
paleta 2 es la que hay que borrar; la 3 es la dirección a la que va todo.

### 5.3 Excepciones que sí están justificadas

- El **degradado del lateral** (identidad).
- Las **rampas de datos** del dashboard e indicadores: `RAMPA_ETAPAS`, el par
  categórico validado contra daltonismo. Son decisiones de visualización con su
  razonamiento escrito. **CONSERVAR** — pero como tokens de gráfico, no como
  constantes dentro del componente.

---

## 6. A5 · Semántica de estado: dónde se decide qué significa

### 6.1 La medida

| Qué | Cuánto | Dónde |
|---|---|---|
| `estado === 'X'` / `codigo === 'X'` | **167** | 26 pantallas |
| Umbral numérico en TS | 43 pantallas | mayoría son validaciones de formulario |
| Ternario que elige color (`? 'bien' : 'mal'`) | 16 pantallas | badges y KPI |
| Mapeo propio de estado → tono | **19 pantallas** | cada una el suyo |

### 6.2 Clasificación

| Clase | Ejemplo real | ¿Puede vivir en Angular? |
|---|---|---|
| **Traducción visual** | `codigos.ts`: `'D' → 'Disponible'` | **Sí.** Es presentación, y está bien centralizado |
| **Hecho** | `estado === 'A'` para saber si mostrar el botón de cierre | **Sí, con reparo:** es leer un hecho, no juzgarlo |
| **Regla de negocio** | qué transiciones ofrece `local-detail` según el estado | **No** — es la máquina de estados del dominio |
| **Umbral** | el `> 7` del recontacto, que llegó a estar en cuatro sitios | **No** — vive en `PoliticaComercial` |
| **Severidad** | `? 'bien' : 'aviso' : 'mal'` en 19 pantallas | **No** — es juicio |
| **Ritmo** | verde/ámbar/rojo de un KPI | **No** — D-E2-2 regla 8 |

### 6.3 El precedente bueno, y hay que copiarlo

**El dashboard ya lo resolvió.** Consume `nivelAtencion` (`ALTO`, `MEDIO`,
`INFORMATIVO`, `SIN_PENDIENTES`) del backend y la pantalla solo elige el color:

```ts
const TONO_POR_NIVEL: Record<NivelAtencion, TonoKpi> = {
  ALTO: 'rojo', MEDIO: 'ambar', INFORMATIVO: 'azul', SIN_PENDIENTES: 'verde',
};
```

Su propio comentario cuenta de dónde viene: antes eran «ocho ternarios que
además se contradecían entre roles» y «un `> 7` que era la cuarta copia del
plazo de recontacto».

**Veredicto: BACKEND_FALTANTE.** Ese patrón —el dominio clasifica, la pantalla
colorea— es el que tienen que seguir las otras 18 pantallas. Y es exactamente lo
que el núcleo de los prototipos ya demuestra con `estadoRitmo`.

---

## 7. A6 · Componentes repetidos

Por prioridad del encargo, con lo medido:

| # | Componente | Hoy | Misma semántica? | Veredicto |
|---|---|---|---|---|
| 1 | **Encabezado de página** | clase `.cl-cabecera` (44) + 7 propias + 6 sin | **Sí**: miga, título, subtítulo, acciones | **UNIFICAR** → `BroxPageHeader` |
| 2 | **Métricas/KPI** | `cl-tarjeta-kpi` (19), 6 tonos | Forma sí, **semántica no** (§6) | **UNIFICAR** semántica |
| 3 | **Filtros** | `cl-barra-filtros` (17) + 20 a mano | **Sí** | **UNIFICAR** |
| 4 | **Tablas** | **35 `<table>` a mano** sobre `.cl-tabla` | Sí en las bandejas; no en las de detalle | **UNIFICAR** las de bandeja |
| 5 | **Estados vacíos** | `cl-estado-listado` (50) | **Sí** | **CONSERVAR** |
| 6 | **Badges** | `.cl-badge` con `bien/aviso/mal` + 19 mapeos propios | Forma sí, semántica no | **UNIFICAR** → `BroxStatus` |
| 7 | **Timeline** | ≥ 8 pantallas, marcado propio cada una | **Sí** | **UNIFICAR** |
| 8 | **Tarjeta de persona/inmueble** | repetida en detalles y buscadores | Sí | **UNIFICAR** |
| 9 | **Panel de acciones** | disperso en cada cabecera | Sí | **UNIFICAR** → `BroxActionBar` |
| 10 | **Tabs** | 5 pantallas, 4 marcados | Sí | **UNIFICAR** |
| 11 | **Modal de confirmación** | `cl-dialogo-confirmacion` (15) | **Sí** | **CONSERVAR** |
| 12 | **Drawer** | `cl-panel-lateral` (1) | Sí | **CONSERVAR** y extender |
| 13 | **Paginación** | `cl-paginacion` (20) | **Sí** | **CONSERVAR** |
| 14 | **Buscador** | dentro de cada barra de filtros | Sí | **UNIFICAR** con 3 |
| 15 | **Selector** | `cl-filtro-select` (15) | **Sí** | **CONSERVAR** |
| 16 | **Feedback éxito/error** | `.cl-aviso` / `.cl-ok`, clases | Sí | **UNIFICAR** |

### 7.1 Los formularios son **dos familias**, no una

Diez pantallas de alta/edición, partidas en dos por el marcado:

| Familia | Pantallas | Marcado |
|---|---|---|
| **Con las primitivas** | `interaccion-form`, `oportunidad-form`, `solicitud-form`, `visita-form` | `.cl-formulario`, `.cl-seccion`, `.cl-campo`, `.cl-grid` |
| **Con marcado propio** | `agente-form`, `broker-form`, `cliente-form`, `propietario-form`, `captacion-form`, `local-form` | `.cabecera` y `.campo` propios |

Y son **las mismas seis** que se pintan su propia cabecera (§3.1). No es
casualidad: son las que se escribieron antes de que existieran las primitivas.

**Veredicto: UNIFICAR**, y empezar por aquí — un formulario es la pantalla
más mecánica de migrar y son seis de golpe. `agente-form` y `broker-form`
además son gemelos casi literales (227 y 220 líneas): ahí hay una fusión, no
solo una migración.

### 7.2 Las cuatro pantallas de acceso

`login`, `recuperar-acceso`, `cambiar-contrasena` y `enrolar-mfa` viven fuera
del shell a propósito (sesión capada). Pero su SCSS se copió:
**`recuperar-acceso.scss` y `cambiar-contrasena.scss` son byte a byte el mismo
archivo** (192 líneas), `login.scss` tiene las mismas 192 con variaciones y
`enrolar-mfa.scss` son 319 con el mismo degradado.

**Veredicto: UNIFICAR** en un armazón de acceso. Es la deuda más barata del
inventario: cuatro archivos, un componente.

---

## 8. Veredictos consolidados

| Veredicto | Cuántos | Los principales |
|---|---|---|
| **CONSERVAR** | 8 | `acceso.ts` como fuente del menú · `cl-estado-listado` · paginación · confirmación · `cl-filtro-select` · guards · rampas de datos justificadas · formularios |
| **UNIFICAR** | 13 | PageHeader · tabla de bandeja · filtros · badges · timeline · KPI · tokens de color/radio/sombra/breakpoint · armazón de acceso · **las 6 pantallas de formulario con marcado propio** · `agente-form`+`broker-form` · las 4 parejas duplicadas |
| **RENOMBRAR** | 4 | `ControlLocal` → **BROX** · `Dashboard` → **Inicio** · `Cierres exitosos` → **Contratos** · sección `Proceso` (se disuelve) |
| **REUBICAR** | 6 | las 2 colas al Inicio · Catálogos a Configuración · Reasignaciones a Organización · `Salir` al pie del lateral · Mi equipo dentro de Agentes |
| **ELIMINAR** | 2 | pie «backend v2 · Spring + Angular» · paleta slate/blue de las 9 pantallas de administración |
| **REDISEÑAR** | 5 | título de la barra superior · estado activo del menú · iconografía · móvil (drawer) · política de capacidades |
| **BACKEND_FALTANTE** | 5 | `estadoRitmo` y clasificación por asunto · `DEPENDE_DE_MI` · cola del broker · capacidades por sesión · `GET /inicio` compuesto |

---

## 9. Cómo se ha medido, y qué NO se ha auditado

**Medido** recorriendo el árbol y contando: rutas y guards (`app.routes.ts`),
módulos y roles (`core/auth/acceso.ts`), servicios y endpoints
(`core/api/*.service.ts`), y por pantalla — líneas, servicios inyectados,
primitivas `.cl-*`, `<table>`, filtros, paginación, KPI, tabs, drawer,
confirmación, timeline, hex sueltos, radios, sombras, `estado ===`, umbrales y
ternarios de color. El cruce con la matriz compara la columna Roles de la
operación de entrada de cada módulo.

**No se ha auditado, y hay que decirlo:**

- **Accesibilidad.** Ni contraste real, ni foco, ni lectores. Es un frente
  propio y no cabía aquí.
- **Rendimiento percibido.** Hay evidencia previa de p95 en las suites de
  búsqueda, pero no se ha medido pintado ni tamaño de bundle.
- **Textos.** El encargo pide vocabulario funcional único; este documento marca
  dónde chocan los nombres, pero **el glosario no está escrito**. Debería salir
  del dominio (`LocalComercial`, `Captacion`, `OportunidadComercial`…) y hay al
  menos un choque abierto: la estructura objetivo dice **Propiedades** y el
  dominio dice **Local**. Se decide en `decision-sidebar-brox.md` §5.
- **Las pantallas de seguridad** (`seguridad`, `enrolar-mfa`, `perfil`) se han
  inventariado, pero su flujo no se ha revisado: tocan `seguridad-no-leer.md`.

---

## 10. Qué sigue

Este documento cierra el **Corte 1** junto con `mapa-pantalla-dominio-backend.md`
y `decision-sidebar-brox.md`. El Corte 2 (sidebar + shell + tokens) empieza por
los hallazgos 1, 2, 3, 5 y 7, que son los que se arreglan **una vez** y se notan
en las 57 pantallas.
