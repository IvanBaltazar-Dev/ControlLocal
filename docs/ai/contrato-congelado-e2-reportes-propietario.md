# Contrato congelado E2 — reportes periódicos al propietario

> **El "congelado" del título es histórico.** El contrato se descongeló el
> 2026-08-09 (`decision-contrato-v2-descongelado.md`): DTOs, endpoints, estados y
> errores pueden cambiar con razón funcional y con sus pruebas.
>
> Este documento **describe el comportamiento vigente** y se sigue actualizando
> —no es historia—, pero la autoridad son **las pruebas y OpenAPI**, no este
> texto. Si discrepan, manda la suite.
>
> **Y las rutas `backend-java/...` que este documento cita ya no existen.** El
> stack legado se borró del árbol el 2026-08-08; esos nombres se conservan
> porque explican de dónde salió cada campo, no porque se puedan abrir. Lo
> vigente vive en `backend-spring/.../web/controlador/` y en su suite.

Estado: **IMPLEMENTADO, CONGELADO y VERIFICADO** (2026-07-29).

Fuente de verdad del cable: `ReportesPropietarioRest`,
`ReportePropietarioBusinessLogicImpl`, `AvanceCaptacionBusinessLogicImpl` y
`ReportePropietarioDAOImpl` de `backend-java/`. Durante el Strangler se
conservan rutas, métodos, formas JSON, códigos de estado, mensajes y rarezas
observables. El PDF Jasper de cada reporte no forma parte de E2 y, desde
**D-F5-1 (2026-07-30), tampoco de la migración**: no se porta.

## 1. Alcance

La ruta base es:

`/captaciones/{idCaptacion}/reportes-propietario`

| Método y ruta | Rol | Request | Response |
|---|---|---|---|
| GET `/captaciones/{id}/reportes-propietario` | cualquier sesión con alcance | — | `List<ReporteResponse>` |
| GET `/captaciones/{id}/reportes-propietario/preview?desde&hasta` | cualquier sesión con alcance | — | `PreviewResponse` |
| POST `/captaciones/{id}/reportes-propietario` | AGENTE dueño de la captación | `ReporteRequest` | 201 + `ReporteResponse` |

No existen PUT ni DELETE.

**Fuera del alcance de la migración** (D-F5-1, ya no es un diferido a F5/F8):

`GET /captaciones/{codigo}/reportes-propietario/{idReporte}/pdf`

El avance —consultas, visitas y objeciones agregadas en SQL— sigue disponible por JSON; lo que
no se porta es su impresión. Ver `decision-reportes-pdf-fuera-de-alcance.md`.

## 2. DTOs congelados

`ReporteRequest`:

- `periodoInicio: LocalDate`
- `periodoFin: LocalDate`
- `consultasReportadas: Integer`
- `visitasReportadas: Integer`
- `objecionesFrecuentes: String`
- `ajustesRecomendados: String`
- `canalEnvio: String`

`PreviewResponse`:

- `consultas: int`
- `visitas: int`
- `objeciones: String`

`ReporteResponse`:

- `id: Long`
- `idCaptacion: Long`
- `idAgente: Long`
- `fechaReporte: LocalDate`
- `periodoInicio: LocalDate`
- `periodoFin: LocalDate`
- `consultasReportadas: Integer`
- `visitasReportadas: Integer`
- `objecionesFrecuentes: String`
- `ajustesRecomendados: String`
- `canalEnvio: String`
- `fechaCreacion: LocalDateTime`

Jackson conserva la regla global `non_null`: los campos nulos no viajan.

## 3. Alcance por rol

La organización se filtra antes que el rol:

- ADMIN ve cualquier captación de su organización.
- BROKER ve captaciones de agentes que supervisa actualmente.
- AGENTE ve únicamente sus propias captaciones.
- Una captación inexistente en la organización responde 404.
- Una captación existente pero fuera del alcance responde 403.
- POST exige AGENTE y además que sea el agente responsable.

Mensajes 401/403 conservan el contrato transversal congelado.

## 4. Resumen derivado

`preview` y `POST` calculan el mismo resumen sobre el periodo indicado:

1. **Consultas**: interacciones ligadas directamente a la captación o a
   cualquiera de sus oportunidades. Se deduplican por id.
2. **Visitas**: visitas en estado REALIZADA de las oportunidades de la
   captación.
3. **Objeciones**: motivos de no continuidad de las oportunidades de la
   captación, agrupados por descripción y ordenados por frecuencia
   descendente. Sin motivos, viaja la cadena vacía.

El rango es inclusivo. Un extremo nulo deja el rango abierto. Un registro sin
fecha no cuenta.

POST **ignora** `consultasReportadas`, `visitasReportadas` y
`objecionesFrecuentes` enviados por el cliente: los tres se reemplazan por el
resumen derivado de la actividad real. `ajustesRecomendados` sí es manual.

## 5. Alta y validaciones

- Cuerpo nulo:
  `"Los datos del reporte son obligatorios."`.
- Fin anterior al inicio:
  `"El fin del periodo no puede ser anterior al inicio."`.
- Fecha de query inválida:
  `"Fecha no valida: {valor}"`.
- Canal presente e inválido:
  `"Canal de envío no válido: {valor}"`.
- Canal nulo o vacío: EMAIL (`E`).
- Códigos admitidos: `L`, `W`, `E`, `P`, `R`, `T`, `O`.
- `fechaReporte` es la fecha local del alta.
- `fechaCreacion` se devuelve en el 201.

La tabla V9 garantiza cantidades no negativas, periodo válido, canal válido y
FK compuestas por organización.

## 6. Orden e integración con F7

- El listado se ordena por `fechaReporte DESC`, igual que la v1.
- Cada alta actualiza de forma natural la fecha del último reporte que consume
  el disparador 6 de `/tareas`.
- La cadencia de F7 sigue siendo 15 días desde el último reporte, o desde la
  fecha de captación cuando todavía no existe ninguno.
- `reporte_propietario` no es `Transicionable` y no escribe
  `historial_estado`.

## 7. Decisiones de implementación v2

1. Se reutilizan la tabla y la entidad creadas en V9; E2 no necesita una nueva
   migración Flyway.
2. Conteos y agrupaciones bajan a SQL, siempre con `organizacion_id`.
3. El alcance se resuelve con `Alcances` y la supervisión vigente.
4. El servicio devuelve `LocalDateTime` para `fechaCreacion`, convirtiendo el
   `TIMESTAMPTZ` a hora local como el resto del cable.
5. Jasper permanece fuera del corte y, desde **D-F5-1**, fuera de la migración entera: el PDF
   de este reporte no se porta.

## 8. Gate de corte

- [x] Tests de comportamiento del service: **11/11**.
- [x] Reactor completo verde: **332 pruebas**.
- [x] `verificacion/e2e-reportes-propietario.ps1`: **50/50** contra PostgreSQL.
- [x] Roles y aislamiento de organización verificados; el tenant temporal se retira.
- [x] La lectura de último reporte de F7 completa la tarea vencida y reinicia sus 15 días.
