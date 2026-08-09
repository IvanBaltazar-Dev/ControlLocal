# Diagnóstico de estados, valores económicos y contaminación E2E

**Corte:** 2026-08-01  
> ## ⚠️ FOTOGRAFÍA HISTÓRICA — NO es el estado actual (nota de 2026-08-06)
>
> Este documento describe el sistema **antes del Bloque 7** y varios de sus hallazgos ya no
> aplican. En particular:
>
> - **H-04 y H-05 están resueltos.** El ciclo jurídico tiene productor: `en-proceso`, `firmar`,
>   `activar`, `finalizar`, `rescindir`, `anular` y `renovar` existen como casos de uso y como
>   endpoints, y la renovación crea un **contrato sucesor enlazado** al anterior.
> - **El grafo se valida, y siempre se validó.** Lo declara `MaquinasEstado` y lo aplica
>   `Transiciones` antes de mutar; está fijado por `CicloContratoTest`. La frase «el código actual
>   no implementa este grafo» del §3.1 es la que quedó obsoleta.
> - **Lo que sí seguía roto y se corrigió en el Bloque 7**: finalizar y rescindir fallaban con 409
>   porque la tarea de revisión del inmueble usaba un `entidad_tipo` que el `CHECK` no admite; y
>   repetir una transición terminal respondía 200 sin hacer nada.
>
> Lo que **se mantiene deliberadamente**: terminar un contrato **no** libera el inmueble. Deja una
> tarea de revisión para que una persona confirme si puede volver al mercado (§3 de las
> indicaciones del Bloque 7). Ciclo jurídico y disponibilidad comercial son dos ciclos distintos.

**Alcance:** backend Spring, contrato congelado/legacy, Angular, Blazor, migraciones Flyway,
tests y PostgreSQL local.  
**Naturaleza:** diagnóstico de solo lectura. No se cambiaron estados, fórmulas, datos, seeds,
transiciones, DTO, endpoints ni etiquetas funcionales como parte de este análisis.

## 1. Resumen ejecutivo

El modelo actual no tiene un único “estado del alquiler”. Conviven, y deben seguir explicándose
por separado, al menos estos tres ciclos:

1. **Ciclo jurídico del contrato:** firma, vigencia, renovación, finalización, rescisión o
   anulación.
2. **Ciclo económico de la comisión:** comisión bruta generada, reparto interno, cobro o
   anulación.
3. **Disponibilidad comercial del local:** disponible, no disponible o inactivo.

Hoy `POST /contratos` encadena los tres ciclos al crear el contrato, pero después de esa operación
no existe un caso de uso que continúe el ciclo jurídico ni que reponga la disponibilidad del local.
La fecha final se deriva para lectura; no vence el contrato, no reabre el local y no reactiva sus
publicaciones.

Hallazgos principales:

| Id | Severidad | Hallazgo comprobado |
|---|---|---|
| H-01 | Crítica | Los tres cierres E3 fueron insertados directamente por SQL. Tienen contrato `V`, oportunidad `F` y solicitud `C`, pero no tienen liquidación; además la captación sigue `A`, el local `D` y la publicación `P`. El fixture eludió la cascada transaccional de `ContratoServiceImpl`. |
| H-02 | Alta | `comisionPactada` es porcentaje, pero 16 de las 36 captaciones actuales llevan valores de 3,000 %, 3,600 %, 5,000 % o 5,500 %. Por su escala y el precedente confirmado de V12, son compatibles con importes cargados en un campo porcentual; esto es una inferencia, no una unidad persistida. La única restricción es `>= 0`; no hay máximo. |
| H-03 | Alta | La moneda tiene tres verdades incompatibles: `propiedad.precio_referencial` no tiene moneda; los hitos de edición se guardan en `PEN`; la ficha Angular rotula la referencia como `USD`; la comisión contractual también se fuerza a `USD`. |
| H-04 | Alta | El contrato admite siete códigos, pero el API solo permite nacer en `D` o `V`; no hay endpoint de transición posterior. `P`, `R`, `F`, `S` y `A` son códigos válidos pero no producibles por el servicio actual. |
| H-05 | Alta | Finalizar, rescindir, anular o llegar a la fecha final del contrato no tiene efecto operativo. El local queda `N` indefinidamente; la captación queda `C` y las publicaciones `C`. No existe reserva como estado del local. |
| H-06 | Media | `Transiciones` audita, pero no valida grafos. Cada service debe validar el origen. `EvaluacionServiceImpl` no exige que la solicitud esté `E`; por código puede mover una solicitud accesible desde cualquier estado a `A`, `R` u `O`, salvo el único de evaluación final. |
| H-07 | Media | El KPI “Comisión por liquidar” es un **conteo** de liquidaciones exactamente `PENDIENTE`, no un importe. Excluye `PARCIAL`. “Comisión generada” suma el bruto de todos los estados, incluso una futura `ANULADA`, y los contratos sin liquidación aportan cero silenciosamente. |
| H-08 | Alta | Los E2E principales usan el mismo tenant/base de desarrollo y dejan datos persistentes. El inventario por familias contiene 41 locales incluyendo seed/manual, 35 captaciones no-seed y 14 contratos; solo los fixtures temporales de segundo tenant se limpian. |
| H-09 | UX | “Ficha de propiedad” y “Ver local” representaban objetos distintos pero con nombres solapados. El nombre inequívoco recomendado es **Resumen comercial** para la captación y **Datos del local** para el registro físico/técnico. Angular ya usa esos textos en los puntos inspeccionados; Blazor aún conserva “Ficha de propiedad” en su pantalla y accesos. |
| H-10 | Media | Los KPI de cierres no representan necesariamente los filtros visibles: Angular envía al resumen solo `texto`, no distrito ni agente; Blazor suma `_todos` sin aplicar sus filtros. Los KPI son del alcance/carga general, no siempre de la tabla filtrada. |

Estado real de la base al corte:

| Indicador global | Valor |
|---|---:|
| Captaciones | 36 |
| Contratos | 14 |
| Liquidaciones de comisión | 11 |
| Contratos sin liquidación | 3 (todos E3) |
| Comisión generada que devuelve el resumen | USD 4,150.00 |
| Liquidaciones `PENDIENTE` | 0 |
| Liquidaciones `COBRADA` | 11 |

## 2. Matriz consolidada de estados

La columna “transiciones reales” describe lo que puede producir el código actual, no solo lo que
acepta un `CHECK`. `Transiciones` es el punto de auditoría, pero no contiene un grafo de negocio.

| Proceso | Códigos y significado | Inicial real | Transiciones reales y terminales | Operaciones y roles | Consumidores actuales |
|---|---|---|---|---|---|
| **Local / `Propiedad`** | `D` Disponible; `N` No disponible; `I` Inactivo | `D` si el alta omite estado; el alta también acepta D/N/I | `PUT /locales/{id}` acepta cualquier D/N/I desde cualquiera; `DELETE` lleva a I; alta de contrato lleva a N. No hay “Reservado” ni “Alquilado”. Ninguno es técnicamente terminal porque el `PUT` puede cambiarlo. | Alta/edición/baja: AGENTE. Lectura: los tres roles con tenant/alcance. Contrato: AGENTE y cascada interna. | Angular: `locales`, `local-detail`, `ficha-propiedad`; Blazor: `Locales`, `LocalDetail`, `LocalForm`, fichas/equipo. La etiqueta la deriva el frontend del código. |
| **Publicación** | `B` Sin publicar/borrador; `P` Publicado; `S` Pausado; `C` Cerrado | Alta explícita: estado enviado; si falta, `P`. La sincronización del local también crea publicación principal. | El endpoint de estado admite cualquier B/P/S/C desde cualquiera. El contrato cierra todas en C. C no es terminal técnico: el endpoint permite volver a P/S/B. No usa `Transiciones`; cambia el campo directamente. | `POST/PUT /locales/{id}/publicaciones`, `POST .../{idPublicacion}/estado`: AGENTE. | Angular: `local-detail/editor-publicacion`; Blazor: `LocalDetail` y servicios de locales. |
| **Prospección** | `P` Prospecto; `C` Contactado; `R` Reunión; `E` Propuesta entregada; `S` Seguimiento; `T` Captado; `D` Descartado | `P` | Cualquier estado no terminal puede ir a C/R/S/T/D según la operación; no se exige el orden P→C→R. `propuesta` lleva a **S**, nunca a E. `T` y `D` son terminales para `cargarEnProceso`; `marcar-captado` tiene una ruta menos estricta. | `contactar`, `reunion`, `propuesta`, `seguimiento`, `rechazar`, `descartar`, `captar`, `marcar-captado`: AGENTE. | Angular: `prospecciones`, `prospeccion-detail`, `local-detail`; Blazor: `Prospecciones`, `ProspeccionDetail`, tareas/fichas. |
| **Propuesta al propietario** | Resultado separado: `P` Pendiente; `A` Aceptada; `R` Rechazada; `S` Recontactar | `P` al ejecutar `/{id}/propuesta` | Propuesta: P; captación: A; rechazo: R; descarte puede conservar null/anterior. La BD y Angular admiten S, pero el dominio no declara constante ni el service lo produce. No es máquina auditada separada. | Mismas operaciones de prospección, AGENTE. | Angular `RESULTADO_PROPUESTA` y detalle/revisión; Blazor lo muestra en prospección/captación. |
| **Captación** | `P` Pendiente de revisión; `O` Observada; `R` Rechazada; `A` Activa; `C` Cerrada; `V` Vencida | `P` | P/O→A/O/R por decisión; editar O→P; A→C por cierre manual o contrato. R/C son terminales operativos. V es válido pero no existe operación/job que lo produzca. Reasignar cambia agente, no estado. | Alta/edición: AGENTE. Decisión, cierre y reasignación: BROKER o ADMIN. | Angular: bandeja, listado, detalle, revisión, formulario, resumen comercial/equipo; Blazor: equivalentes y ficha. |
| **Oportunidad** | `A` Abierta; `S` Solicitud creada; `N` No continúa; `F` Finalizada exitosa; `X` Finalizada no favorable | `A` | A→S al crear solicitud; A→N por no continuidad o resultado negativo de visita; A/S→F al registrar contrato. `cierre-exitoso` siempre responde 400. X es válido pero no tiene productor actual. N/F/X son terminales en el flujo actual. | Alta/no continuidad/cierre fijo: AGENTE. | Angular aún no tiene pantalla F3; Blazor: `Oportunidades`, `OportunidadDetail/Form`, fichas. |
| **Visita** | `P` Programada; `G` Reprogramada; `C` Cancelada; `N` No realizada; `R` Realizada | `P` | P/G→G/C/N/R. C/N/R terminales; solo R sin desenlace admite `resultado`. Un desenlace de no continuidad mueve la oportunidad a N. | `POST /visitas`; `PATCH reprogramar/cancelar/realizar/no-realizada/resultado`: AGENTE. | Angular F3 no migrado; Blazor: `Visitas`, `VisitaForm`, perfiles y detalle de agente. |
| **Solicitud** | `G` Registrada; `E` En revisión; `O` Observada; `A` Aprobada; `R` Rechazada; `D` Desistida; `C` Cerrada | `G` y oportunidad pasa A→S | G/O→E al reenviar; evaluación mueve a A/R/O; A→C al crear contrato. D es válido pero no hay endpoint productor. R/C son terminales normales; A queda esperando contrato. La evaluación actual no verifica el estado origen E. | Alta/reenvío: AGENTE. Evaluación: BROKER o ADMIN. Contrato: AGENTE. | Angular F4 no migrado; Blazor: `Solicitudes`, `SolicitudDetail/Form`, `SolicitudesRevisar`, `Evaluacion`. |
| **Evaluación** | No tiene estado; es evento. Resultado `A/R/O`; tipo `P` Preliminar, `O` Observación, `F` Final | El tipo se deriva: resultado O→tipo O; A/R→tipo F | Solo una evaluación F por solicitud. El request acepta tipo P/O/F pero el service lo valida y luego lo reemplaza; P no se produce. El evento mueve la solicitud. | `POST /evaluaciones`: BROKER o ADMIN. | Angular F4 no migrado; Blazor `Evaluacion`, historial de solicitud. |
| **Contrato legal** | `P` En proceso; `D` Firmado; `V` Vigente; `R` Renovado; `F` Finalizado; `S` Rescindido; `A` Anulado | Solo `D` o `V` | No hay endpoint de transición posterior. Por tanto P/R/F/S/A solo pueden venir de seed/SQL/importación futura. El alta no registra historial propio porque “nace”, pero sí audita cuatro efectos laterales. | `POST /contratos`: AGENTE. Lectura: todos con alcance. | Angular: `propiedades-alquiladas`; Blazor: `PropiedadesAlquiladas`, detalles/comisiones. Angular muestra el estado real; Blazor aún rotula filas como “Alquilado”. |
| **Comisión / liquidación** | `PENDIENTE`; `PARCIAL`; `COBRADA`; `ANULADA` | `PENDIENTE` al registrar contrato por API | Asignar reparto no cambia estado. Cobro permite PENDIENTE/PARCIAL→COBRADA o ANULADA. PARCIAL es fuente admitida pero no hay operación que lo produzca. COBRADA/ANULADA terminales. | Asignar y cobrar: solo BROKER, no ADMIN. Lectura: todos; reparto neto solo BROKER/ADMIN. | Angular: columna/KPI de `propiedades-alquiladas`; Blazor: `Comisiones`, `PropiedadesAlquiladas`, alertas. |
| **Tarea** | `PENDIENTE`; `EN_PROCESO`; `COMPLETADA`; `VENCIDA`; `CANCELADA` | `PENDIENTE`, creada al leer/reconciliar | El reconcile crea PENDIENTE y completa; el agente cancela. EN_PROCESO y VENCIDA son válidos pero no tienen productor actual. Abiertas: PENDIENTE/EN_PROCESO. COMPLETADA/CANCELADA terminales; CANCELADA bloquea recreación para siempre. | `GET /tareas` y `/pendientes` reconcilian; `POST /{id}/cancelar`: solo AGENTE. | Angular F7 no migrado; Blazor dashboard/bandeja. `diasSinAccion`, vencimiento, código y ruta son derivados por backend en lectura; no son columnas. |
| **Alerta** | `ACTIVA`; `ATENDIDA`; `DESCARTADA` | `ACTIVA` al emitir o sincronizar recontacto | ACTIVA→ATENDIDA. DESCARTADA es válida pero no hay endpoint productor. La lectura puede crear alertas de recontacto. | GET y POST/PATCH `/{id}/atender`: los tres roles con alcance; el tipo determina quién debe atender, aunque la fila siempre se ata a un agente. | Angular F6 no migrado; Blazor campana/notificaciones. La ruta es derivada por backend. |

### 2.1 Estados existentes pero no producibles por los casos de uso actuales

> ⚠️ **SUPERADA el 2026-08-08 por `decision-estados-sin-productor.md` (D-B7-1). No usar esta tabla
> para decidir.** Se verificó código por código y **cuatro de sus diez filas ya no eran ciertas**:
> el contrato (`P/R/F/S/A`), la comisión (`PARCIAL`), la evaluación preliminar y el resultado
> `Recontactar` **sí tienen productor** desde el ciclo contractual y el de comisiones. La tabla se
> conserva como registro de lo que se veía en su momento; la lista viva —y verificada por
> `EstadosSinProductorTest`— está en aquel documento.

| Proceso | Estado permitido sin productor actual |
|---|---|
| Prospección | `E` Propuesta entregada |
| Resultado de propuesta | `S` Recontactar |
| Captación | `V` Vencida |
| Oportunidad | `X` Finalizada no favorable |
| Solicitud | `D` Desistida |
| Evaluación | tipo `P` Preliminar |
| Contrato | `P`, `R`, `F`, `S`, `A` |
| Comisión | `PARCIAL` |
| Tarea | `EN_PROCESO`, `VENCIDA` |
| Alerta | `DESCARTADA` |

Esto no implica que deban eliminarse. Implica que hoy son vocabulario de esquema/contrato sin
operación productora y no deben presentarse como un flujo ya implementado.

## 3. Contrato, comisión y disponibilidad: separación obligatoria

### 3.1 Ciclo jurídico del contrato

Los siete códigos actuales pueden ordenarse conceptualmente así, sin que este grafo esté
implementado:

```text
P En proceso -> D Firmado -> V Vigente -> R Renovado -> F Finalizado
       |             |           |              |
       +-----------> A Anulado   +------------> S Rescindido
```

Interpretación candidata a validar con negocio antes de programar:

- `P`: preparación/elaboración, todavía sin firma.
- `D`: firmado, pero aún no necesariamente iniciado.
- `V`: produce efectos y está dentro de vigencia.
- `R`: renovación acordada; debe decidirse si es estado o un nuevo contrato/versionado.
- `F`: terminó normalmente por plazo.
- `S`: terminó anticipadamente después de entrar en vigor.
- `A`: quedó sin efecto antes de la vigencia o por nulidad.

El código actual no implementa este grafo. Solo crea D o V y luego no vuelve a mutar el contrato.

### 3.2 Ciclo económico de comisión/pago

`COBRADA` no significa “renta pagada”, “contrato pagado” ni “comisión pagada al agente”. Significa
que el broker registró el **cobro de la comisión inmobiliaria** con fecha y forma de pago.

Quién paga/recibe según la evidencia actual:

- `frontend-angular/core/comision.ts` incluye la comisión en el desembolso inicial del
  **inquilino** y declara que la comisión es de la **inmobiliaria**. Esa es la interpretación de
  negocio documentada.
- La base no persiste pagador ni receptor. `comision_liquidacion` solo enlaza contrato, bruto,
  reparto, fecha/forma y estado. Por tanto esa interpretación no está garantizada por datos.
- `montoAgente` es la participación asignada al agente; `montoEmpresa = bruto - montoAgente`.
  No existe `fechaPagoAgente`, `estadoPagoAgente` ni movimiento financiero. Asignar una parte no
  demuestra que se haya pagado.

Si se necesita distinguir cobro de la inmobiliaria y pago al agente, el ciclo candidato no debe
sobrecargar `COBRADA`. Como mínimo necesita conceptos separados, por ejemplo:

```text
Devengo: PENDIENTE -> COBRADA | ANULADA
Reparto: SIN_ASIGNAR -> ASIGNADO
Pago al agente: PENDIENTE -> PAGADO | ANULADO
```

La alternativa robusta es modelar pagos/movimientos, no añadir más significados a un único estado.

### 3.3 Disponibilidad del local

Estados reales: D disponible, N no disponible, I inactivo. No existen estados Reservado ni
Alquilado. `N` agrupa al menos dos causas distintas: retirado del mercado o alquilado.

Efecto del contrato creado por API:

1. local → N;
2. hito de precio `C`, monto de la solicitud y moneda USD;
3. publicaciones → C;
4. captación → C.

Después de finalizar, rescindir, anular o superar la fecha final no ocurre nada porque no hay
operación ni job. El local permanece N. Antes de implementar estados o automatismos debe decidirse:

- si al finalizar normalmente vuelve a D automáticamente o requiere inspección/entrega;
- si una rescisión libera de inmediato o después de una fecha efectiva;
- si una anulación previa a vigencia reabre publicaciones/captación;
- si una renovación mantiene N sin interrupción;
- si “reservado” es disponibilidad del local o etapa de una solicitud/contrato.

## 4. Fórmulas económicas exactas

| Concepto visible | Definición actual | Código/columna |
|---|---|---|
| Comisión pactada | **Porcentaje** sobre una renta mensual. `100 = una renta`, `50 = media renta`, `5 = 5 %`. No es monto fijo. | `captacion.comision_pactada NUMERIC(10,2)`, `CHECK >= 0`; `Captacion.comisionPactada`. |
| Estimación en Resumen comercial | `precioReferencial × comisionPactada / 100`, redondeo a dos decimales. Es cálculo de pantalla, no liquidación. | `frontend-angular/core/comision.ts::comisionSobreRenta`; `FichaPropiedad.renta/comisionEstimada`. |
| Comisión generada al cierre | `solicitud.montoPropuesto × captacion.comisionPactada / 100`, escala 2, `HALF_UP`. Null en alguno de los dos produce 0, aunque las precondiciones normales los exigen. | `ComisionServiceImpl.bruta`; se persiste en `comision_liquidacion.monto`. |
| Moneda de la liquidación | Siempre `USD`, constante; no se hereda de la solicitud ni del precio. | `ComisionLiquidacion.MONEDA`; `ContratoServiceImpl.MONEDA`. |
| Reparto del agente | Monto manual, no porcentaje: `0 <= montoAgente <= bruto`. | `POST /contratos/{id}/comision/asignar`; `monto_agente`. |
| Reparto de empresa | `max(bruto - montoAgente, 0)`. El service impide que agente supere bruto. | `ComisionLiquidacion.asignarMontoAgente`; `monto_empresa`. |
| “Liquidada” | No existe un campo ni fórmula con ese nombre. Se muestra el estado de comisión. `COBRADA` requiere reparto, fecha y forma. | `estado`, `fecha_cobro`, `forma_pago`. |
| “Pendiente” como monto | No existe. Puede derivarse, pero hoy no se calcula. | Sin columna/DTO/KPI monetario. |
| KPI Comisión generada | `sum(com.monto)` sobre el alcance y el filtro de texto, sin filtrar estado; `LEFT JOIN`. Distrito/agente no llegan al resumen actual. | `ContratoAlquilerRepository.resumenCierres`; `ContratoServiceImpl.resumenCierres`. |
| KPI Comisión por liquidar | `count(case when com.estado='PENDIENTE' then 1 end)`. Es cantidad de liquidaciones, no dinero; mismo alcance/texto del KPI anterior. | `ContratoAlquilerRepository.resumenCierres`. |
| Total legacy `PropiedadesAlquiladas` | Blazor descarga hasta el límite del endpoint y suma `ComisionGenerada` en memoria; cuenta estado mapeado `Pendiente`. Los filtros de tabla no cambian esos KPI. Angular usa `/contratos/resumen`, calculado en BD, pero solo le envía `texto`. | `PropiedadesAlquiladas.razor::ComisionTotalTexto/PorLiquidar`; `PropiedadesAlquiladas.comisionTotal`. |

Consecuencias importantes:

- Una comisión `ANULADA` seguiría sumando en “Comisión generada”.
- Una comisión `PARCIAL` no cuenta en “por liquidar”.
- Un contrato sin liquidación sí cuenta como cierre, pero aporta cero a la suma y al conteo.
- No existe tratamiento de monto fijo. Los valores con apariencia de importe son interpretados
  siempre como porcentaje. La decisión de soportar comisión fija está documentada como futura.

### 4.1 Explicación exacta de 3,600 % → USD 259,200

En la ficha E3:

```text
precio referencial = 7,200
comisionPactada    = 3,600
estimación          = 7,200 × 3,600 / 100
                    = 7,200 × 36
                    = 259,200
```

Es decir, 3,600 % equivale a **36 rentas mensuales**, no a USD 3,600.

La solicitud E3 usa otra base, 7,100. Si el contrato hubiera pasado por el service, su comisión
contractual habría sido `7,100 × 36 = USD 255,600`. No se generó ninguna: el SQL del fixture
insertó el contrato directamente.

## 5. Inventario completo de captaciones actuales

Convenciones de la tabla:

- `USD*` es la moneda que la ficha/listado Angular **supone** para `precioReferencial`; la columna
  en `propiedad` no tiene moneda.
- `hito PEN/USD/—` es la última señal encontrada en `precio_propiedad`; no convierte ni corrige
  el valor de referencia.
- “Estimada” usa la referencia del local. “Liquidación real” solo existe si se creó contrato por
  la cascada o se insertó la liquidación.
- “Fixture con importe en campo porcentual” clasifica una **apariencia/inferencia por escala**,
  apoyada en el antecedente V12; la tabla no dispone de una unidad original que pruebe intención.

| Captación | Local | Estado | Ref. | Moneda observable | Pactada / interpretación | Estimada sobre ref. | Liquidación real | Origen | Diagnóstico |
|---|---|---:|---:|---|---|---:|---|---|---|
| CAP-0001 | LOC-0001 | P | 8,500 | USD* / hito PEN | 100 % = 1 renta | 8,500 | — | Seed V5, corregido por V12 | Semilla coherente después de migración |
| CAP-0002 | LOC-9249 | A | 5,600 | USD* / hito PEN | 5,000 % = 50 rentas | 280,000 | — | Manual/indeterminado; no aparece en repo | Sospechoso por escala |
| CAP-0003 | LOC-F34986 | A | 9,200 | USD* / hito — | 5,500 % = 55 rentas | 506,000 | — | E2E F3 | Fixture con importe en campo porcentual |
| CAP-0004 | LOC-F35142 | A | 9,200 | USD* / hito — | 5,500 % = 55 rentas | 506,000 | — | E2E F3 | Fixture con importe en campo porcentual |
| CAP-0005 | LOC-F38638 | A | 9,200 | USD* / hito — | 5,500 % = 55 rentas | 506,000 | — | E2E F3 | Fixture con importe en campo porcentual |
| CAP-0006 | LOC-V61526 | C | 9,900 | USD* / hito PEN | 5,500 % = 55 rentas | 544,500 | — | E2E V6 | Fixture con importe en campo porcentual; cierre manual |
| CAP-0007 | LOC-F32569 | A | 9,200 | USD* / hito — | 5,500 % = 55 rentas | 506,000 | — | E2E F3 | Fixture con importe en campo porcentual |
| CAP-0008 | LOC-F46342 | A | 8,000 | USD* / hito — | 5 % | 400 | — | E2E F4 | Coherente; solicitud no cerrada |
| CAP-0009 | LOC-F45892 | A | 8,000 | USD* / hito — | 5 % | 400 | — | E2E F4 | Coherente; solicitud no cerrada |
| CAP-0010 | LOC-F47301 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0011 | LOC-F48578 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0012 | LOC-F46092 | A | 8,000 | USD* / hito — | 5 % | 400 | — | E2E F4 | Coherente; solicitud no cerrada |
| CAP-0013 | LOC-F41833 | A | 8,000 | USD* / hito — | 5 % | 400 | — | E2E F4 | Coherente; solicitud no cerrada |
| CAP-0014 | LOC-F49614 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0015 | LOC-F44211 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0016 | LOC-F62021 | C | 7,000 | USD* / hito USD | 5 % | 350 | 350 COBRADA | E2E F6/F7 | Coherente |
| CAP-0017 | LOC-F65581 | C | 7,000 | USD* / hito USD | 5 % | 350 | 350 COBRADA | E2E F6/F7 | Coherente |
| CAP-D5581 | LOC-F6B5581 | P | 5,000 | USD* / hito — | 4 % | 200 | — | E2E F6/F7 | Coherente; fixture pendiente |
| CAP-0019 | LOC-F68480 | C | 7,000 | USD* / hito USD | 5 % | 350 | 350 COBRADA | E2E F6/F7 | Coherente |
| CAP-D8480 | LOC-F6B8480 | P | 5,000 | USD* / hito — | 4 % | 200 | — | E2E F6/F7 | Coherente; fixture pendiente |
| CAP-0021 | LOC-F62178 | C | 7,000 | USD* / hito USD | 5 % | 350 | 350 COBRADA | E2E F6/F7 | Coherente |
| CAP-D2178 | LOC-F6B2178 | P | 5,000 | USD* / hito — | 4 % | 200 | — | E2E F6/F7 | Coherente; fixture pendiente |
| CAP-0023 | LOC-F42947 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0024 | LOC-F44447 | C | 8,000 | USD* / hito USD | 5 % | 400 | 400 COBRADA | E2E F4 | Coherente |
| CAP-0025 | LOC-F38195 | A | 9,200 | USD* / hito — | 5,500 % = 55 rentas | 506,000 | — | E2E F3 | Fixture con importe en campo porcentual |
| CAP-0026 | LOC-F65351 | C | 7,000 | USD* / hito USD | 5 % | 350 | 350 COBRADA | E2E F6/F7 | Coherente |
| CAP-D5351 | LOC-F6B5351 | P | 5,000 | USD* / hito — | 4 % | 200 | — | E2E F6/F7 | Coherente; fixture pendiente |
| CAP-0028 | LOC-V62888 | C | 9,900 | USD* / hito PEN | 5,500 % = 55 rentas | 544,500 | — | E2E V6 | Fixture con importe en campo porcentual; cierre manual |
| CAP-E2-195932 | LOC-E2-195932 | A | 6,800 | USD* / hito — | 3,000 % = 30 rentas | 204,000 | — | E2E E2 | Fixture con importe en campo porcentual |
| CAP-E2-475385 | LOC-E2-475385 | A | 6,800 | USD* / hito — | 3,000 % = 30 rentas | 204,000 | — | E2E E2 | Fixture con importe en campo porcentual |
| CAP-E2-229978 | LOC-E2-229978 | A | 6,800 | USD* / hito — | 3,000 % = 30 rentas | 204,000 | — | E2E E2 | Fixture con importe en campo porcentual |
| CAP-E2-134703 | LOC-E2-134703 | A | 6,800 | USD* / hito — | 3,000 % = 30 rentas | 204,000 | — | E2E E2 | Fixture con importe en campo porcentual |
| CAP-E2-875890 | LOC-E2-875890 | A | 6,800 | USD* / hito — | 3,000 % = 30 rentas | 204,000 | — | E2E E2 | Fixture con importe en campo porcentual |
| CAP-E3-343686 | LOC-E3-343686 | A | 7,200 | USD* / hito — | 3,600 % = 36 rentas | 259,200 | **∅**, contrato V | E2E E3 + SQL directo | Inconsistencia estructural crítica |
| CAP-E3-226201 | LOC-E3-226201 | A | 7,200 | USD* / hito — | 3,600 % = 36 rentas | 259,200 | **∅**, contrato V | E2E E3 + SQL directo | Inconsistencia estructural crítica |
| CAP-E3-604907 | LOC-E3-604907 | A | 7,200 | USD* / hito — | 3,600 % = 36 rentas | 259,200 | **∅**, contrato V | E2E E3 + SQL directo | Inconsistencia estructural crítica |

## 6. Trazabilidad de CAP-E3-604907 y los cierres E3

Los tres sufijos E3 siguen el mismo guion: `343686`, `226201` y `604907`.

### 6.1 Lo que hace el script

`backend-spring/verificacion/e2e-ficha-comercial.ps1`:

1. crea propietario y cliente por API;
2. crea `LOC-E3-{sufijo}` por API: local D, publicación P y prospección inicial P;
3. crea `CAP-E3-{sufijo}` por API con `comisionPactada = 3600` y la aprueba: captación A;
4. inserta por SQL una **segunda** prospección `PRO-E3-{sufijo}` en T;
5. inserta por SQL oportunidad F, visita R, solicitud C por 7,100 y contrato V;
6. no inserta `comision_liquidacion` y no invoca `POST /contratos`;
7. al final comprueba de forma explícita que el fixture principal quedó persistente. Solo limpia
   el tenant temporal usado para probar aislamiento.

### 6.2 Estado actual exacto

| Código | Estado actual |
|---|---|
| Local `LOC-E3-604907` | D Disponible |
| Publicación principal | P Publicada |
| Prospección automática | P Prospecto |
| Prospección SQL `PRO-E3-604907` | T Captada |
| Captación `CAP-E3-604907` | A Activa |
| Oportunidad `OP-E3-604907` | F Finalizada exitosa |
| Solicitud `SOL-E3-604907` | C Cerrada, monto 7,100 |
| Contrato 14 | V Vigente |
| Liquidación | No existe |

Lo mismo ocurre en contratos 12 y 13 de los otros dos sufijos. Por ello esos cierres aparecen en
“Cierres exitosos”, pero con comisión nula/ausente y con oferta todavía publicada/disponible.

### 6.3 Contraste con cierres hechos por API

| Familia | Renta de solicitud | Pactada | Esperado | Persistido | Reparto agente/empresa | Estado |
|---|---:|---:|---:|---:|---:|---|
| F4 | 8,000 | 5 % | 400 | 400 | 250 / 150 | COBRADA |
| F6/F7 | 7,000 | 5 % | 350 | 350 | 250 / 100 | COBRADA |
| E3 SQL | 7,100 | 3,600 % | 255,600 | ∅ | ∅ / ∅ | ∅ |

Los resultados de USD 350 y USD 400 prueban que el backend no trata `5` como importe fijo: aplica
5 % sobre la renta. Los E3 no prueban la fórmula de cierre porque nunca entraron al service.

## 7. Inventario de contaminación E2E

### 7.1 Filas relacionadas por familia

Conteos actuales, agrupados por el código del local:

| Familia | Locales | Publicaciones | Prospecciones | Captaciones | Oportunidades | Visitas | Solicitudes | Contratos | Comisiones |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| E2 | 5 | 5 | 5 | 5 | 5 | 9 | 0 | 0 | 0 |
| E3 | 3 | 3 | 6 | 3 | 3 | 3 | 3 | 3 | 0 |
| F3 | 10 | 10 | 10 | 5 | 8 | 8 | 0 | 0 | 0 |
| F4 | 10 | 19 | 10 | 10 | 9 | 0 | 9 | 6 | 6 |
| F6/F7 | 9 | 9 | 9 | 9 | 5 | 0 | 5 | 5 | 5 |
| V6 | 2 | 4 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |
| Manual/indeterminado (`LOC-9249`) | 1 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| Seed (`LOC-0001`) | 1 | 2 | 1 | 1 | 1 | 1 | 1 | 0 | 0 |

E3 tiene dos prospecciones por local porque el alta del local crea una automáticamente y luego el
script inserta `PRO-E3-*` manualmente.

### 7.2 Persistencia y limpieza de los scripts

| Script | Fixture principal | Limpieza actual | Resultado |
|---|---|---|---|
| `e2e-ficha-comercial.ps1` | E3 | Limpia solo tenant temporal; afirma que E3 queda | Persistencia intencional |
| `e2e-reportes-propietario.ps1` | E2 | Limpia solo segundo tenant temporal | E2 queda persistente |
| `e2e-f3-demanda.ps1` | F3 | Sin `finally` de fixture principal | Cada corrida acumula |
| `e2e-f4-solicitud.ps1` | F4 | Sin limpieza principal | Cada corrida acumula cierres, documentos, publicaciones, alertas, etc. |
| `e2e-f6-f7-alertas-tareas.ps1` | F6/F7 | Sin limpieza principal | Cada corrida acumula flujo, alertas y tareas |
| `e2e-v6.ps1` | V6 | Sin limpieza principal | Cada corrida acumula |
| `e2e-e4-dashboard.ps1` | E4 | Sí tiene limpieza extensa del fixture principal | No hay E4 actual en el inventario |
| `e2e-locales-listado.ps1` | listado masivo | Sí limpia en `finally` | Aislado por prefijo |

Ningún registro fue eliminado durante este diagnóstico.

### 7.3 Aptitud como seed

- **Apto como demo controlada:** seed Flyway determinista (`LOC-0001`, `CAP-0001`) después de V12.
- **No aptos como seed:** E2, E3, F3, F4, F6/F7 y V6. Son ejecuciones aleatorias, contienen
  datos repetidos, estados intermedios, personas de prueba y, en E2/E3/F3/V6, porcentajes
  económicamente sospechosos por su escala.
- **No clasificable:** `LOC-9249/CAP-0002`; no existe literal en el repositorio. Debe tratarse
  como manual/importado hasta tener evidencia externa.

### 7.4 Estrategia de aislamiento recomendada, no aplicada

1. Ejecutar E2E contra una base exclusiva `controllocal_e2e`, recreada desde Flyway por suite.
2. No usar la base de desarrollo interactiva para pruebas que escriben.
3. Mantener un `run_id`/prefijo por corrida para poder auditar y limpiar en orden de FK.
4. Añadir `try/finally` a cada suite. Una transacción del script no sirve para llamadas HTTP
   independientes; la alternativa es base/schema descartable.
5. Evitar SQL directo para fixtures de procesos con cascada. Si el test es de read model y necesita
   SQL, insertar el agregado consistente completo o aislarlo en su propia base.
6. Hacer que el gate final falle si quedan filas del `run_id`, excepto cuando una suite declare
   expresamente un fixture reusable en una base de demo separada.

## 8. Distribución de estados en PostgreSQL

| Entidad | Distribución actual |
|---|---|
| Local | D 31; N 11 |
| Publicación | P 35; C 17 |
| Prospección | P 13; C 1; R 4; T 27 |
| Resultado propuesta | A 27; null 18 |
| Captación | P 5; A 18; C 13 |
| Oportunidad | A 6; S 3; N 8; F 14 |
| Visita | P 4; R 17 |
| Solicitud | G 3; E 1; C 14 |
| Evaluación resultado/tipo | A/F 11; O/O 11 |
| Contrato | V 14 |
| Comisión | COBRADA 11 |
| Tarea | PENDIENTE 17; COMPLETADA 5; CANCELADA 6 |
| Alerta | ACTIVA 103; ATENDIDA 7 |

La ausencia de un código en esta tabla no invalida el catálogo; muestra qué estados han sido
realmente producidos en este entorno.

## 9. Distinción UX: Resumen comercial vs Datos del local

Los dos destinos no deberían compartir “ficha/local/propiedad” sin contexto:

| Nombre recomendado | Ruta Angular actual | Qué muestra |
|---|---|---|
| **Resumen comercial** | `/captaciones/:codigo/ficha` | Expediente comercial de la captación: galería, comisión pactada/estimada, vigencia del encargo, propietario, agente y condiciones comerciales. |
| **Datos del local** | `/locales/:id` | Registro del inmueble: dirección, distrito, área, características técnicas, estado de disponibilidad, precio referencial, publicaciones e histórico de precios. |

Angular ya presenta “Resumen comercial” y “Datos del local” en los puntos auditados, aunque la
clase/ruta interna conserve `FichaPropiedad`. Blazor todavía usa “Ficha de propiedad”, “Ver ficha”
y `ficha-propiedad/{codigo}`. La corrección funcional debería hacerse después de aprobar este
diagnóstico, conservando las rutas si el contrato/navegación requiere compatibilidad.

## 10. Decisiones necesarias antes de implementar cambios

> **Estado a 2026-08-08 — cinco de las diez ya están tomadas** (cierre del Bloque 7):
> **#4** *Cobro vs pago al agente* → **separados**: existe `ComisionMovimiento.PAGO_AGENTE` con saldo
> propio, no es reparto teórico. **#5** *Grafo legal del contrato* → acordado y con productor para
> los siete estados. **#6** *Disponibilidad al terminar* → **no se libera sola**; genera tarea de
> revisión humana y «Reservado» **no** se añade. **#7** *Estados sin productor* → **D-B7-1**,
> `decision-estados-sin-productor.md`. **#8** *Semántica de KPI* → «por liquidar» es un **conteo**, y
> la pantalla de comisiones no mezcla conteos con importes.
>
> Siguen abiertas la #1, #2, #3, #9 y #10.

1. **Unidad de comisión:** confirmar porcentaje actual y rango admisible. Si existen comisiones
   fijas, agregar tipo, importe y moneda; no reutilizar el mismo número sin unidad.
2. **Moneda del local/renta:** agregar moneda al precio referencial o declarar una única moneda de
   contrato. Hoy PEN y USD divergen.
3. **Pagador y receptor:** persistir quién paga la comisión y quién la recibe si es requisito
   contable; hoy solo se infiere.
4. **Cobro vs pago al agente:** separar ambos hechos o aceptar explícitamente que el sistema solo
   registra cobro de la inmobiliaria y reparto teórico.
5. **Grafo legal del contrato:** acordar transiciones, fechas efectivas y reglas para D/V/R/F/S/A.
6. **Disponibilidad al terminar:** decidir cuándo local/publicación/captación se reabren y si
   “Reservado” es un estado real.
7. **Estados sin productor:** implementar, retirar o documentar cada código; no dejar que la UI
   prometa acciones inexistentes.
8. **Semántica de KPIs:** decidir si “por liquidar” es cantidad o monto y si “generada” debe incluir
   anuladas/parciales.
9. **Saneamiento de datos:** preparar una migración/operación separada después de aprobar reglas.
   No corregir E2E o porcentajes a ciegas.
10. **Aislamiento E2E:** separar la base antes de volver a ejecutar suites acumulativas.

## 11. Evidencia verificada

Fuentes principales inspeccionadas:

- Entidades de dominio: `Propiedad`, `Publicacion`, `Prospeccion`, `Captacion`,
  `OportunidadComercial`, `Visita`, `SolicitudAlquiler`, `EvaluacionSolicitud`,
  `ContratoAlquiler`, `ComisionLiquidacion`, `Tarea`, `Alerta`.
- Services y transiciones: implementaciones correspondientes y
  `service/soporte/Transiciones.java`.
- Endpoints/roles: controllers Spring y `docs/ai/matriz-operacion-rol.md`.
- Esquema/seeds: Flyway V4, V5, V7, V8, V9 y V12; las doce migraciones figuran aplicadas con
  `success=true`.
- DTO: `CaptacionResponse`, `ContratoResponse`, `ResumenCierresResponse` y contratos de service.
- Angular: `core/comision.ts`, `core/api/codigos.ts`, `ficha-propiedad`, `local-detail`,
  `captaciones`, `propiedades-alquiladas`.
- Blazor: `CaptacionForm`, `FichaPropiedad`, `PropiedadesAlquiladas`, servicios/mapeos de códigos,
  pantallas F3/F4/F6/F7.
- E2E: E2, E3, F3, F4, F6/F7, V6, E4 y listado de locales.
- PostgreSQL: consultas `SELECT`/descripción de esquema; no se ejecutaron DML de diagnóstico.

Verificación automatizada ejecutada en este corte:

- Backend `controllocal-service` + dependencias: **389 tests, 0 fallos**.
- Angular focalizado (`comision`, `ficha-propiedad`, `propiedades-alquiladas`):
  **56 tests, 0 fallos**, Edge headless.

Los tests verdes confirman el comportamiento codificado; no convierten en correctas las decisiones
de negocio pendientes ni los fixtures que eluden el service.

## 12. Estabilización aprobada y aplicada (2026-08-01)

Este diagnóstico se conserva como la foto previa y como trazabilidad de la decisión. La
intervención posterior quedó cerrada como **estabilización transversal del dominio económico de
alquiler**, no como una vertical nueva:

- `comisionPactada` sigue siendo porcentaje de una renta mensual y admite 0–200 %. `100` se
  presenta como valor habitual, pero Angular exige que el usuario lo vea y confirme.
- La renta referencial y final exige moneda; la comisión hereda la moneda de la renta final. No
  quedan constantes nuevas USD/PEN en Angular, hitos o liquidación.
- Flyway V13 introduce columnas/controles para escrituras futuras. V14 sanea exclusivamente
  registros identificados por códigos y firmas literales de seeds/fixtures; no modifica datos
  manuales o desconocidos por inferencia. Estos últimos se muestran como “Moneda no definida”.
- E2, E3, F3, V6 y E4 dejaron de cargar importes como porcentaje. E3 crea oportunidad, solicitud
  y contrato por API y verifica la cascada de cierre.
- Las suites de escritura rechazan ejecución directa y sólo corren mediante un orquestador que
  crea una base PostgreSQL/Flyway exclusiva por corrida, con almacenamiento efímero y limpieza en
  `finally`.
- `PropiedadesAlquiladas` separa contrato, disponibilidad y cobro; sus KPI excluyen anuladas,
  aplican los mismos filtros que la tabla y detectan contratos sin liquidación.
- Contrato conserva `Vigente`; no se agregó `Pagado`. Compraventa, comisión fija, IGV, minuta,
  escritura, pagos parciales, renovación completa y contabilidad del pago interno al agente
  permanecen como evolución posterior.

Evidencia de cierre: migraciones V13–V14 aplicadas desde cero y sobre la base manual, reactor
Spring **405/405**, Angular **236/236**, suite aislada **18/18** y comprobación real en navegador
del filtrado compartido y las tres dimensiones de estado. `PropiedadesAlquiladas` queda cerrada y
la migración Angular continúa por **Demanda F3**.
