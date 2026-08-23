# Mapa pantalla ↔ dominio ↔ backend

> **MEDIDO EL 2026-08-17 Y NO RE-MEDIDO DESDE ENTONCES.** (anotado 2026-08-22)
> Después de esta foto entraron E2.2–E2.6 y los cortes 0A–0E y 1 (V71…V78), que
> movieron datos de Angular al dominio (`estadoRitmo`, `dependeDeMi`, `senales[]`,
> el catálogo gobernado y el sujeto del dato). **Varias filas
> `DERIVADO_FRONTEND` ya no lo están**, y este documento no lo refleja.
>
> Úsalo como método —la pregunta «¿hecho, interpretación o presentación?»— y no
> como inventario. Antes de apoyarte en una fila concreta, compruébala.

**Qué responde:** de dónde sale cada cosa que BROX enseña. Qué es un **hecho**
del backend, qué es una **interpretación** que ya hace el dominio, qué es
**presentación** — y qué está hoy **derivado en Angular** y no debería.

**Hecho el 2026-08-17** sobre `frontend-angular/src/app` y
`docs/ai/matriz-operacion-rol.md`. Acompaña a `auditoria-ui-brox.md`.

**Para qué sirve:** es la entrada de la Fase F. Cada ficha de contrato
pantalla ↔ backend sale de una fila de aquí, y la regla de cierre es la misma:
si aparece `DERIVADO_FRONTEND` para riesgo, ritmo, prioridad, vencimiento,
clasificación o recomendación, **la justificación normal es ninguna**.

---

## 1. Las cuatro etiquetas

| Etiqueta | Qué es | Quién manda |
|---|---|---|
| `HECHO` | un dato que ocurrió y está guardado: una fecha, un importe, un estado | backend |
| `DERIVADO_BACKEND` | una conclusión calculada sobre hechos: días de espera, nivel de atención, ritmo | backend |
| `PRESENTACION` | cómo se ve: el color de un badge, el texto de un código, el orden de las columnas | Angular |
| `DERIVADO_FRONTEND` | una conclusión calculada en la pantalla | **hay que justificarlo o moverlo** |

---

## 2. Los recursos del backend y quién los consume

29 servicios en `core/api/`. Un recurso puede alimentar varias pantallas; lo que
importa es que **ninguna pantalla invente un recurso propio**.

| Recurso | Servicio | Pantallas que lo consumen |
|---|---|---|
| `/dashboard` | `dashboard` | Inicio |
| `/tareas` | `tareas` | Inicio (bandeja del agente) |
| `/indicadores/resumen` | `indicadores`, `dashboard` | Indicadores, Inicio |
| `/indicadores/avance` | `indicadores` | Reportes |
| `/locales` | `locales` | Locales, detalle, formulario, ficha, oportunidad-form, captación-form/detalle/review |
| `/propietarios` | `propietarios` | Propietarios, detalle, formulario, ficha, local-form |
| `/prospecciones` | `prospecciones` | Prospecciones, detalle, captación ×3, interacción-form |
| `/captaciones` | `captaciones` | Captaciones, detalle, formulario, review, ficha, pendientes, reasignaciones, cartera del equipo, solicitud-detail, interacción-form, oportunidad-form |
| `/clientes` | `clientes` | Clientes, detalle, formulario, bitácora, oportunidad-form, interacción-form |
| `/requerimientos` | `requerimientos` | **solo dentro de cliente-detail** |
| `/oportunidades` | `oportunidades` | Oportunidades, detalle, formulario, visita-form, solicitud-form, bitácora, interacción-form |
| `/visitas` | `visitas` | Visitas, formulario, oportunidad-detail |
| `/interacciones` | `interacciones` | Interacciones, detalle, formulario, bitácora, oportunidad-detail |
| `/solicitudes` | `solicitudes` | Solicitudes, detalle, formulario, revisar, documentos, evaluación |
| `/evaluaciones` | `evaluaciones` | **solo evaluacion-solicitud** (no hay listado) |
| `/contratos` | `contratos` | Cierres exitosos, Comisiones, solicitud-detail |
| `/seguimiento-comercial` | `seguimiento` | Seguimiento |
| `/agentes` · `/brokers` | `agentes`, `brokers`, `personal` | Agentes, Brokers, fichas, Mi equipo, bandejas con filtro por agente |
| `/asignaciones` | `asignaciones` | Asignaciones |
| `/accesos` · `/perfil/mfa` | `seguridad`, `mfa` | Seguridad, Perfil, Enrolar MFA |
| `/perfil` | `perfil` | Perfil |
| `/documentos` | `documentos` | Documentos de solicitud, visor |
| `/alertas` | `alertas` | Campana (chrome global) |
| `/aviso-privacidad` | `aviso-privacidad` | Privacidad |
| — | `coincidencias`, `ficha-comercial` | cliente-detail, propietario-detail, ficha |

**Sin pantalla propia:** `/requerimientos` y `/evaluaciones` existen en el cable
y solo se alcanzan dentro de otra pantalla. La estructura objetivo del sidebar
los pide como entradas — es **pantalla faltante**, no endpoint faltante.

---

## 3. Los objetos del dominio, superficie a superficie

Un objeto se enseña en tres profundidades y **cada una tiene su regla**
(Fase E): la bandeja decide a cuál entrar, el expediente cuenta el caso, la
pantalla operativa ejecuta.

| Objeto | Bandeja | Expediente | Operativa | Estado que manda |
|---|---|---|---|---|
| **LocalComercial** | `/locales`, `/propiedades-equipo` | `/locales/:id` | `/locales/nuevo`, `/:id/editar` | `estadoLocal` D/N/I + `estadoPublicacion` |
| **Propietario** | `/propietarios` | `/propietarios/:id` | formulario | activo |
| **Prospección** | `/prospecciones` | `/prospecciones/:id` | descartar · marcar captado | estado de prospección |
| **Captación** | `/captaciones`, `/captaciones/pendientes` | `/captaciones/:codigo`, `/:codigo/ficha` | `/nueva`, `/:codigo/editar`, `/:codigo/revisar`, `/reasignaciones` | estado de captación |
| **Cliente** | `/clientes` | `/clientes/:id`, `/:id/contacto` | formulario | activo |
| **Requerimiento** | **falta** | dentro de `/clientes/:id` | dentro de `/clientes/:id` | `A`/`P`/`C` |
| **Oportunidad** | `/oportunidades` | `/oportunidades/:id` | `/nueva`, no-continuidad | estado de oportunidad |
| **Visita** | `/visitas` | — | `/visitas/nueva`, realizar/reprogramar/cancelar | estado de visita |
| **Interacción** | `/interacciones` | `/interacciones/:id` | `/nueva` | resultado |
| **Solicitud** | `/solicitudes`, `/solicitudes/revisar` | `/solicitudes/:codigo` | `/nueva`, `/:codigo/documentos` | estado de solicitud |
| **Evaluación** | **falta** | — | `/solicitudes/:codigo/evaluar` | estado de evaluación |
| **Contrato** | `/propiedades-alquiladas` | dentro de solicitud-detail | — | estado de contrato |
| **Comisión** | `/comisiones` | — | asignar · cobrar · movimientos | estado de comisión |

**Dos huecos y un desequilibrio:**

- **Requerimiento** y **Evaluación** no tienen bandeja. El requerimiento es el
  objeto que abre la demanda: hoy solo se ve dentro de la ficha del cliente.
- **Visita** y **Comisión** no tienen expediente: se operan desde la bandeja.
  Para la visita puede bastar; para la comisión, con movimientos y cobros, no.
- **Captación** tiene **dos** expedientes (`/:codigo` y `/:codigo/ficha`) que se
  solapan. Es la duplicidad número 1 a resolver.

---

## 4. Lo que hoy deriva Angular y no debería

Ordenado por daño. Cada fila es un `DERIVADO_FRONTEND` sin justificación válida.

| # | Qué se deriva en la pantalla | Dónde | Debería venir como | Tanda |
|---|---|---|---|---|
| 1 | **El color de un estado** (`? 'bien' : 'aviso' : 'mal'`) | 19 pantallas, cada una el suyo | `DERIVADO_BACKEND`: un `nivelAtencion` por objeto, como ya hace `/dashboard` | 3 |
| 2 | **Qué transiciones ofrecer** según el estado | `local-detail` (12 `estado===`), `oportunidad-detail` (15), `captaciones` (12), `solicitud-detail` (11), `prospecciones` (11) | `DERIVADO_BACKEND`: **acciones permitidas** por objeto y sesión | 3 + capacidades |
| 3 | **El ritmo de un KPI** (verde/ámbar/rojo) | Indicadores, y el pie del Inicio del prototipo | `DERIVADO_BACKEND`: `estadoRitmo` | 5 |
| 4 | **Si algo está vencido** | Seguimiento, prospecciones, visitas | `DERIVADO_BACKEND`: el plazo vive en `PoliticaComercial` | 1 |
| 5 | **Qué es urgente y en qué orden** | Inicio (hoy `/dashboard` ya trae `prioridad`; el resto no) | `DERIVADO_BACKEND`: política de despacho | 1 |
| 6 | **De quién depende una operación** | no se deriva: **no existe** | `DERIVADO_BACKEND`: `DEPENDE_DE_MI` | 1 |
| 7 | **Qué puede hacer este rol aquí** | ternarios de rol repartidos por las plantillas | `DERIVADO_BACKEND`: capacidades por sesión; y mientras tanto, política central en el SPA | 4 |

### Lo que sí puede quedarse en Angular

| Qué | Por qué |
|---|---|
| `codigos.ts`: `'D' → 'Disponible'` | **PRESENTACION** pura. El cable viaja con el código y la descripción es de pantalla. Ya está centralizado y se conserva |
| `TONO_POR_NIVEL` del dashboard | **PRESENTACION**: el dominio dice el nivel, la pantalla elige el color. Es el patrón bueno |
| Rampas de color de los gráficos | **PRESENTACION** con criterio documentado (secuencial para etapas, categórico validado contra daltonismo) |
| Formato de fecha, moneda y unidades | **PRESENTACION** |
| Orden y visibilidad de columnas | **PRESENTACION** |

---

## 5. El Inicio compuesto, contra lo que ya existe

`GET /inicio` (Fase D6) no se construye de cero. Esto es lo que ya hay y lo que
falta, pieza a pieza:

| Pieza de la respuesta | Hoy | Falta |
|---|---|---|
| `generadoEn` | — | trivial, y lo pide la cabecera del Radar |
| `actor` | `GET /sesion` | ámbito y capacidades |
| `foco[]` | `GET /tareas` (7 disparadores, con dedup y auto-resolución) | `DEPENDE_DE_MI`, `lado`, `paso`, política de 6 criterios, `motivoDelPrimero`, `asuntoId` |
| `radar` | — | `comoEsta[]`, `lectura`, `recomendacion`, `paraQue`, `accion`, expediente |
| `hallazgos[]` | `CoincidenciaCartera` **dentro** de la cola | sacarlo de la cola y darle objeto propio |
| `agenda` | disperso en visitas, captaciones y solicitudes | vista única con `entidadTipo`/`entidadId` |
| `pulso` | — | `GROUP BY estadoRitmo` sobre los agentes |
| `kpis[]` | `GET /indicadores/resumen` con `senales[]` | metas, `metaEsperadaAHoy`, `proyeccionCierre`, `estadoRitmo` |
| `accesosRapidos[]` | — | por rol; hoy son rutas escritas en la pantalla |

**El diagnóstico completo y el orden de las cinco tandas están en
`estado-backend-para-el-inicio.md`.** Este mapa no lo repite: solo enseña que
`GET /inicio` es una **composición** de piezas que en su mayoría ya existen, y
que lo que falta es la capa de interpretación, no el motor.

---

## 6. Reconciliación por lectura, no tiempo real

Nada de lo anterior necesita WebSocket. El motor de la bandeja **reconcilia en
cada `GET`**: un refetch al montar y otro al volver a la pestaña cubren lo que
en una herramienta de trabajo se llama «tiempo real». La única pieza que
conviene añadir es `generadoEn`, para que la pantalla pueda decir «hace 2 min»
en vez de fingir que está viva.

---

## 7. Cómo se usa este mapa en la Fase F

Para cada pantalla importante, una ficha con esta forma — y **cada dato
etiquetado**:

```
Pantalla:            Expediente de la captación
Actor:               AGENTE (opera) · BROKER (decide) · ADMIN (lee)
Pregunta:            ¿en qué punto está este encargo y qué falta?
Objeto:              Captacion (código)
Hechos:              fechaFirma, plazoDias, renta, documentos[]      HECHO
Interpretaciones:    diasParaVencer, nivelAtencion, accionesPermitidas
                                                                     DERIVADO_BACKEND
Presentación:        color del badge, formato de fecha               PRESENTACION
Acciones:            editar (AGENTE) · decidir (BROKER)
Endpoints:           GET /captaciones/codigo/{codigo} · POST /{id}/decision
Permisos:            matriz, filas de captación
Estados vacíos:      sin documentos · sin visitas
Errores:             403 fuera de alcance · 404 código inexistente
```

La ficha se rechaza si algo de riesgo, ritmo, prioridad, vencimiento,
clasificación o recomendación aparece como `DERIVADO_FRONTEND`.
