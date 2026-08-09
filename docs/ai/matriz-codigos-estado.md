# Matriz de códigos de estado

**Estado: vigente desde 2026-08-01.** Esta matriz documenta el único
vocabulario de estados persistidos por ControlLocal v2. La letra siempre se
interpreta dentro de su columna; no existe un significado global de `A`, `C`,
`P` o cualquier otro código.

La fuente ejecutable es `EstadosDominio`: cada enum implementa el conversor
estricto código ↔ enum. Las entidades conservan un solo atributo `String`
persistido y consultable para la compatibilidad JPQL, y exponen el enum con un
accessor `@Transient`. Los aliases `String` del contrato legado apuntan a
`EstadosDominio.Codigos`; no contienen literales independientes. El REST sigue
enviando y recibiendo códigos de un carácter.

| Enum | Código | Texto de interfaz | Persistencia |
|---|---:|---|---|
| `EstadoActivoInactivo` | `A` | Activo | `persona.estado`, `credencial_usuario.estado_administrativo`, `organizacion.estado`, `usuario_organizacion.estado`, `finalidad_tratamiento.estado` |
| `EstadoActivoInactivo` | `I` | Inactivo | mismas columnas |
| `EstadoOperativoAgente` | `D` | Disponible | `detalle_agente.estado_operativo` |
| `EstadoOperativoAgente` | `L` | Licencia | `detalle_agente.estado_operativo` |
| `EstadoOperativoAgente` | `N` | No disponible | `detalle_agente.estado_operativo` |
| `EstadoRegistroPropiedad` | `A` | Activo | `propiedad.estado_registro` |
| `EstadoRegistroPropiedad` | `I` | Inactivo | `propiedad.estado_registro` |
| `DisponibilidadComercial` | `D` | Disponible | `propiedad.disponibilidad_comercial` |
| `DisponibilidadComercial` | `R` | Reservado | `propiedad.disponibilidad_comercial` |
| `DisponibilidadComercial` | `A` | Alquilado | `propiedad.disponibilidad_comercial` |
| `DisponibilidadComercial` | `T` | Retirado del mercado | `propiedad.disponibilidad_comercial` |
| `EstadoPublicacion` | `B` | Sin publicar | `publicacion.estado` |
| `EstadoPublicacion` | `P` | Publicada | `publicacion.estado` |
| `EstadoPublicacion` | `S` | Pausada | `publicacion.estado` |
| `EstadoPublicacion` | `C` | Cerrada | `publicacion.estado` |
| `EstadoProspeccion` | `P` | Prospecto | `prospeccion.estado` |
| `EstadoProspeccion` | `C` | Contactado | `prospeccion.estado` |
| `EstadoProspeccion` | `R` | Reunión | `prospeccion.estado` |
| `EstadoProspeccion` | `E` | Propuesta entregada | `prospeccion.estado` |
| `EstadoProspeccion` | `S` | Seguimiento | `prospeccion.estado` |
| `EstadoProspeccion` | `T` | Captado | `prospeccion.estado` |
| `EstadoProspeccion` | `D` | Descartado | `prospeccion.estado` |
| `EstadoCaptacion` | `P` | Pendiente de revisión | `captacion.estado` |
| `EstadoCaptacion` | `O` | Observada | `captacion.estado` |
| `EstadoCaptacion` | `R` | Rechazada | `captacion.estado` |
| `EstadoCaptacion` | `A` | Activa | `captacion.estado` |
| `EstadoCaptacion` | `C` | Cerrada | `captacion.estado` |
| `EstadoCaptacion` | `V` | Vencida | `captacion.estado` |
| `EstadoOportunidad` | `A` | Abierta | `oportunidad_comercial.estado` |
| `EstadoOportunidad` | `S` | Solicitud creada | `oportunidad_comercial.estado` |
| `EstadoOportunidad` | `N` | No continúa | `oportunidad_comercial.estado` |
| `EstadoOportunidad` | `F` | Finalizada exitosa | `oportunidad_comercial.estado` |
| `EstadoOportunidad` | `X` | Finalizada no favorable | `oportunidad_comercial.estado` |
| `EstadoRequerimiento` | `A` | Activo | `requerimiento_cliente.estado` |
| `EstadoRequerimiento` | `P` | Pausado | `requerimiento_cliente.estado` |
| `EstadoRequerimiento` | `C` | Cerrado | `requerimiento_cliente.estado` |
| `EstadoVisita` | `P` | Programada | `visita.estado` |
| `EstadoVisita` | `G` | Reprogramada | `visita.estado` |
| `EstadoVisita` | `C` | Cancelada | `visita.estado` |
| `EstadoVisita` | `N` | No realizada | `visita.estado` |
| `EstadoVisita` | `R` | Realizada | `visita.estado` |
| `EstadoSolicitud` | `G` | Registrada | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `E` | En revisión | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `O` | Observada | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `A` | Aprobada | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `R` | Rechazada | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `D` | Desistida | `solicitud_alquiler.estado` |
| `EstadoSolicitud` | `C` | Cerrada | `solicitud_alquiler.estado` |
| `EstadoDocumentoSolicitud` | `R` | Registrado | `documento_solicitud.estado` |
| `EstadoDocumentoSolicitud` | `O` | Observado | `documento_solicitud.estado` |
| `EstadoDocumentoSolicitud` | `V` | Validado | `documento_solicitud.estado` |
| `EstadoContrato` | `P` | En proceso | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `D` | Firmado | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `V` | Vigente | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `R` | Renovado | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `F` | Finalizado | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `S` | Rescindido | `contrato_alquiler.estado_contrato` |
| `EstadoContrato` | `A` | Anulado | `contrato_alquiler.estado_contrato` |
| `EstadoComision` | `P` | Pendiente | `comision_liquidacion.estado` |
| `EstadoComision` | `R` | Parcial | `comision_liquidacion.estado` |
| `EstadoComision` | `C` | Cobrada | `comision_liquidacion.estado` |
| `EstadoComision` | `A` | Anulada | `comision_liquidacion.estado` |
| `EstadoTarea` | `P` | Pendiente | `tarea.estado` |
| `EstadoTarea` | `E` | En proceso | `tarea.estado` |
| `EstadoTarea` | `C` | Completada | `tarea.estado` |
| `EstadoTarea` | `V` | Vencida | `tarea.estado` |
| `EstadoTarea` | `A` | Cancelada | `tarea.estado` |
| `EstadoAlerta` | `A` | Activa | `alerta.estado` |
| `EstadoAlerta` | `T` | Atendida | `alerta.estado` |
| `EstadoAlerta` | `D` | Descartada | `alerta.estado` |
| `EstadoRegularizacionEconomica` | `P` | Pendiente | `regularizacion_dato_economico.estado` |
| `EstadoRegularizacionEconomica` | `R` | Resuelta | `regularizacion_dato_economico.estado` |
| `EstadoRegularizacionEconomica` | `D` | Descartada | `regularizacion_dato_economico.estado` |
| `EstadoTokenAcceso` | `V` | Vigente | `token_acceso.estado` |
| `EstadoTokenAcceso` | `C` | Consumido | `token_acceso.estado` |
| `EstadoTokenAcceso` | `R` | Revocado | `token_acceso.estado` |
| `EstadoTokenAcceso` | `A` | Agotado | `token_acceso.estado` |
| `EstadoFactorAutenticacion` | `P` | Pendiente | `factor_autenticacion.estado` |
| `EstadoFactorAutenticacion` | `A` | Activo | `factor_autenticacion.estado` |
| `EstadoFactorAutenticacion` | `R` | Revocado | `factor_autenticacion.estado` |
| `EstadoConcesionRecuperacion` | `P` | Pendiente | `concesion_recuperacion.estado` |
| `EstadoConcesionRecuperacion` | `V` | Vigente | `concesion_recuperacion.estado` |
| `EstadoConcesionRecuperacion` | `C` | Cerrada | `concesion_recuperacion.estado` |
| `EstadoConcesionRecuperacion` | `D` | Caducada | `concesion_recuperacion.estado` |
| `EstadoConcesionRecuperacion` | `A` | Agotada | `concesion_recuperacion.estado` |

Los tres últimos son del bloque de identidad. Nacieron en V31/V37/V38 con la
palabra completa —`'VIGENTE'`, `'ACTIVO'`, `'PENDIENTE'`— porque el gate que
exige el código unitario **se salta en silencio sin `TEST_DB_URL`** y nunca
llegó a ejecutarse contra ellos. V40 los convierte y reconstruye los dos
índices parciales que son invariantes de seguridad, no optimizaciones:
`uq_factor_activo_por_credencial` y `uq_concesion_viva_por_organizacion`.
En `EstadoConcesionRecuperacion`, `CADUCADA` es `D` porque `C` ya la ocupa
`CERRADA`; mismo criterio que en `EstadoContrato` con `RESCINDIDO` = `S`.

`historial_estado.estado_anterior/estado_nuevo` también son `VARCHAR(1)`, pero
no tienen un catálogo global: su enum se determina mediante `entidad_tipo`.
Esto evita atribuir un significado universal a una letra.

## Grafos validados

- Captación: `P → A/O/R`, `O → P/A/R`, `A → C/V`.
- Prospección: avanza desde `P` por `C/R/E/S` hasta `T/D`; los retrocesos de
  seguimiento expresamente admitidos están en `MaquinasEstado`.
- Oportunidad: `A → S/N`, `S → F/X`.
- Visita: `P/G → G/C/N/R`.
- Solicitud: `G → E/D`, `E → O/A/R/D`, `O → E/D`, `A → C`.
- Documento: `R/O → V/O` según la revisión del broker.
- Contrato: `P → D/A`, `D → V/A`, `V → F/S/R`.
- Comisión: el saldo de movimientos deriva `P/R/C`; `P/R → A` es anulación
  expresa. Una marca `R` sin movimientos no es evidencia económica.

`Transiciones` consulta `MaquinasEstado` antes de mutar y solo después registra
estado anterior, estado nuevo, fecha efectiva, usuario, rol y motivo.
