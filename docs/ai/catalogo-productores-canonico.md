# Catálogo canónico de productores — tabla ejecutable

**Este archivo lo parsea el gate.** El razonamiento y las decisiones viven en
[`catalogo-productores-estado.md`](catalogo-productores-estado.md); aquí está
solo la tabla que las dos capas del gate contrastan contra PostgreSQL y contra
el código.

## Formato

```
| tabla.columna | codigo | CLASE | evidencia |
```

`CLASE` es una de cinco: `PRODUCIDO`, `DERIVADO`, `RESERVADO_COMPATIBILIDAD`,
`RESERVADO_FUTURO`, `DEPRECADO`.

**`PRODUCIDO` exige nombrar el productor.** Las demás exigen justificación. Un
`PRODUCIDO` con evidencia vacía rompe el build: sin eso el catálogo solo
diría que alguien afirmó que existe.

## Alcance, declarado y no adivinado

El gate **no intenta parsear PostgreSQL de forma inteligente**. Se limita a
`CHECK` de UNA sola columna con lista `IN`, y el alcance es esta tabla. Dos
consecuencias deliberadas:

- **Las `CHECK` compuestas se declaran a mano.** `ck_evaluacion_tipo_derivado`
  cruza `resultado` y `tipo_evaluacion`; extraerla con una expresión regular
  daba el vocabulario **falso** `A,F,O,P,R` para `tipo_evaluacion`, cuando el
  real es `F,O,P`. Una heurística equivocada es peor que una declaración.
- **Fuera de alcance por decisión**: `alerta.tipo`, `tarea.tipo`,
  `token_acceso.tipo`, `evento_seguridad.tipo/resultado` y
  `accion_recuperacion.tipo/resultado`. Son **taxonomías de evento**, no
  máquinas de estado: no tienen transiciones ni productor único, y meterlas
  aquí diluiría la pregunta que este catálogo responde. `comision_movimiento.tipo`
  **sí** entra, porque ahí `A` fue una decisión de dominio.

## Las cuatro vías de producción

Un productor puede estar en cualquiera de estas cuatro, y buscar solo una
subestima el resultado. La cuarta se añadió después de que
`token_acceso.REVOCADO` pareciera huérfano: lo escribe un `@Modifying` dentro
de una cadena JPQL, invisible para quien busque `setEstado`.

1. `setter/entidad` — asignación en la entidad o `setEstado(...)`
2. `service/Transiciones` — transición formal validada por `MaquinasEstado`
3. `SQL/Flyway/función BD` — migración, seed o cuerpo PL/pgSQL
4. `@Modifying JPQL` — `UPDATE` masivo en un repositorio

## Tabla

| tabla.columna | codigo | CLASE | evidencia |
|---|---|---|---|
| alerta.estado | A | PRODUCIDO | Alerta.java: `estado = ACTIVA` al emitir |
| alerta.estado | T | PRODUCIDO | Alerta.java: `estado = ATENDIDA` al atender |
| alerta.estado | D | RESERVADO_FUTURO | No existe definicion de en que difiere descartar de atender |
| captacion.estado | P | PRODUCIDO | CaptacionServiceImpl: alta de captacion |
| captacion.estado | O | PRODUCIDO | CaptacionServiceImpl: decision del broker (observar) |
| captacion.estado | R | PRODUCIDO | CaptacionServiceImpl: decision del broker (rechazar) |
| captacion.estado | A | PRODUCIDO | CaptacionServiceImpl: decision del broker (aprobar) |
| captacion.estado | C | PRODUCIDO | ContratoServiceImpl: cascada del cierre |
| captacion.estado | V | RESERVADO_FUTURO | Sin productor. Se implementa en 7.3.3 con el reconciliador de vigencia |
| comision_liquidacion.estado | P | PRODUCIDO | ComisionServiceImpl.crearPendienteNormalizada |
| comision_liquidacion.estado | R | PRODUCIDO | ComisionServiceImpl.registrarMovimiento: derivado del saldo |
| comision_liquidacion.estado | C | PRODUCIDO | ComisionServiceImpl: saldo agotado o gate de cobro |
| comision_liquidacion.estado | A | PRODUCIDO | ComisionServiceImpl.registrarCobro y anularPorContratoAnulado |
| comision_movimiento.tipo | C | PRODUCIDO | ComisionServiceImpl: cobro |
| comision_movimiento.tipo | P | PRODUCIDO | ComisionServiceImpl: pago al agente |
| comision_movimiento.tipo | R | PRODUCIDO | ComisionServiceImpl: reversion |
| comision_movimiento.tipo | A | DEPRECADO | Ajuste retirado en 7.2: sin regla que diga que saldo modifica. CHECK conservado por historico |
| concesion_recuperacion.estado | P | PRODUCIDO | RecuperacionEmergenciaServiceImpl.emitir |
| concesion_recuperacion.estado | V | PRODUCIDO | RecuperacionEmergenciaServiceImpl.aprobar: segunda aprobacion |
| concesion_recuperacion.estado | C | PRODUCIDO | cerrarSiVolvioElGobierno y CierreDeConcesiones |
| concesion_recuperacion.estado | D | PRODUCIDO | CierreDeConcesiones: caducidad por ventana |
| concesion_recuperacion.estado | A | PRODUCIDO | marcarAgotadaSiConsumioSuUltimaAccion (7.3.3) |
| contrato_alquiler.estado_contrato | P | PRODUCIDO | ContratoServiceImpl.iniciarEnProceso |
| contrato_alquiler.estado_contrato | D | PRODUCIDO | ContratoServiceImpl.firmar |
| contrato_alquiler.estado_contrato | V | PRODUCIDO | ContratoServiceImpl.activar y registrar |
| contrato_alquiler.estado_contrato | R | PRODUCIDO | ContratoServiceImpl.renovar |
| contrato_alquiler.estado_contrato | F | PRODUCIDO | ContratoServiceImpl.finalizar |
| contrato_alquiler.estado_contrato | S | PRODUCIDO | ContratoServiceImpl.rescindir |
| contrato_alquiler.estado_contrato | A | PRODUCIDO | ContratoServiceImpl.anular |
| credencial_usuario.estado_administrativo | A | PRODUCIDO | UsuariosInternos.registrar y activacion de cuenta |
| credencial_usuario.estado_administrativo | I | PRODUCIDO | Desactivacion de cuenta |
| detalle_agente.estado_operativo | D | PRODUCIDO | UsuariosInternos.estadoOperativoO: alta y edicion de agente |
| detalle_agente.estado_operativo | L | PRODUCIDO | AgenteServiceImpl: edicion de agente |
| detalle_agente.estado_operativo | N | PRODUCIDO | AgenteServiceImpl: edicion de agente |
| documento_solicitud.estado | R | PRODUCIDO | DocumentoSolicitudServiceImpl: alta del documento |
| documento_solicitud.estado | O | PRODUCIDO | DocumentoSolicitudServiceImpl: revision observada |
| documento_solicitud.estado | V | PRODUCIDO | DocumentoSolicitudServiceImpl: revision conforme |
| documento_solicitud.resultado_revision | P | PRODUCIDO | DocumentoSolicitud: valor por defecto de la entidad |
| documento_solicitud.resultado_revision | C | PRODUCIDO | DocumentoSolicitudServiceImpl.revisar: conforme |
| documento_solicitud.resultado_revision | O | PRODUCIDO | DocumentoSolicitudServiceImpl.revisar: observado |
| evaluacion_solicitud.resultado | A | PRODUCIDO | EvaluacionServiceImpl: aprobar |
| evaluacion_solicitud.resultado | R | PRODUCIDO | EvaluacionServiceImpl: rechazar |
| evaluacion_solicitud.resultado | O | PRODUCIDO | EvaluacionServiceImpl: observar |
| evaluacion_solicitud.tipo_evaluacion | O | PRODUCIDO | EvaluacionServiceImpl: derivado de resultado observada |
| evaluacion_solicitud.tipo_evaluacion | F | PRODUCIDO | EvaluacionServiceImpl: derivado de aprobada o rechazada |
| evaluacion_solicitud.tipo_evaluacion | P | RESERVADO_COMPATIBILIDAD | El request lo exige valido y el service lo pisa; ck_evaluacion_tipo_derivado impide persistirlo. Muere con el cable |
| factor_autenticacion.estado | P | PRODUCIDO | MfaServiceImpl.iniciar |
| factor_autenticacion.estado | A | PRODUCIDO | MfaServiceImpl.confirmar |
| factor_autenticacion.estado | R | PRODUCIDO | MfaServiceImpl: revocacion propia y ajena |
| finalidad_tratamiento.estado | A | PRODUCIDO | Seed de finalidades y alta |
| finalidad_tratamiento.estado | I | PRODUCIDO | Baja de finalidad |
| oportunidad_comercial.estado | A | PRODUCIDO | OportunidadServiceImpl: alta |
| oportunidad_comercial.estado | S | PRODUCIDO | SolicitudServiceImpl: al crear la solicitud |
| oportunidad_comercial.estado | N | PRODUCIDO | OportunidadServiceImpl y VisitaServiceImpl: cierre no continua |
| oportunidad_comercial.estado | F | PRODUCIDO | ContratoServiceImpl: cierre exitoso |
| oportunidad_comercial.estado | X | RESERVADO_FUTURO | Sin productor. Se implementa en 7.3.3 como consecuencia de solicitud R o D |
| organizacion.estado | A | PRODUCIDO | Alta de organizacion |
| organizacion.estado | I | PRODUCIDO | Baja de organizacion |
| persona.estado | A | PRODUCIDO | Personas.nueva: alta de cliente y propietario |
| persona.estado | I | PRODUCIDO | ClienteServiceImpl y PropietarioServiceImpl: desactivacion |
| propiedad.disponibilidad_comercial | D | PRODUCIDO | ContratoServiceImpl.revisarDisponibilidad VOLVER_AL_MERCADO y alta del local |
| propiedad.disponibilidad_comercial | A | PRODUCIDO | ContratoServiceImpl.cerrarLocal |
| propiedad.disponibilidad_comercial | T | PRODUCIDO | ContratoServiceImpl.revisarDisponibilidad RETIRAR_DEL_MERCADO y baja del local |
| propiedad.disponibilidad_comercial | R | RESERVADO_FUTURO | 7.3.2 fijo que un contrato P no es reserva. Falta una operacion real de reserva |
| propiedad.estado_registro | A | PRODUCIDO | LocalComercialServiceImpl: alta y reactivacion |
| propiedad.estado_registro | I | PRODUCIDO | LocalComercialServiceImpl: desactivacion |
| prospeccion.estado | P | PRODUCIDO | ProspeccionServiceImpl: alta |
| prospeccion.estado | C | PRODUCIDO | ProspeccionServiceImpl: contactar |
| prospeccion.estado | R | PRODUCIDO | ProspeccionServiceImpl: reunion |
| prospeccion.estado | S | PRODUCIDO | ProspeccionServiceImpl: propuesta y seguimiento |
| prospeccion.estado | T | PRODUCIDO | ProspeccionServiceImpl: captado |
| prospeccion.estado | D | PRODUCIDO | ProspeccionServiceImpl: rechazar y descartar |
| prospeccion.estado | E | RESERVADO_COMPATIBILIDAD | Propuesta entregada: el endpoint produce S. Legible para filas importadas, retirado de toda accion |
| prospeccion.resultado_propuesta | P | PRODUCIDO | Prospeccion.marcarPropuesta |
| prospeccion.resultado_propuesta | A | PRODUCIDO | Prospeccion.marcarAceptada |
| prospeccion.resultado_propuesta | R | PRODUCIDO | Prospeccion.marcarRechazoDelPropietario |
| prospeccion.resultado_propuesta | S | DEPRECADO | Nunca tuvo productor ni constante. La continuidad la cubre EstadoProspeccion.SEGUIMIENTO |
| publicacion.estado | B | PRODUCIDO | PublicacionServiceImpl: borrador por defecto |
| publicacion.estado | P | PRODUCIDO | PublicacionServiceImpl: publicar |
| publicacion.estado | S | PRODUCIDO | PublicacionServiceImpl.cambiarEstado (setter generico) |
| publicacion.estado | C | PRODUCIDO | ContratoServiceImpl.cerrarLocal: baja al alquilar |
| regularizacion_dato_economico.estado | P | DEPRECADO | Cola de calidad de V15-V17. Sin entidad JPA ni servicio. Se retira en el Bloque 8 |
| regularizacion_dato_economico.estado | R | DEPRECADO | Idem: nunca se produjo, ni siquiera por migracion |
| regularizacion_dato_economico.estado | D | DEPRECADO | Solo lo inserto V16. Se retira en el Bloque 8 |
| requerimiento_cliente.estado | A | PRODUCIDO | RequerimientoServiceImpl: alta |
| requerimiento_cliente.estado | P | PRODUCIDO | RequerimientoServiceImpl.cambiarEstado (setter generico) |
| requerimiento_cliente.estado | C | PRODUCIDO | RequerimientoServiceImpl.cambiarEstado (setter generico) |
| revision_disponibilidad.disponibilidad_nueva | D | PRODUCIDO | ContratoServiceImpl.revisarDisponibilidad VOLVER_AL_MERCADO |
| revision_disponibilidad.disponibilidad_nueva | T | PRODUCIDO | ContratoServiceImpl.revisarDisponibilidad RETIRAR_DEL_MERCADO |
| solicitud_alquiler.estado | G | PRODUCIDO | SolicitudServiceImpl: alta |
| solicitud_alquiler.estado | E | PRODUCIDO | SolicitudServiceImpl.reenviar |
| solicitud_alquiler.estado | O | PRODUCIDO | EvaluacionServiceImpl: DESTINO_SOLICITUD observada |
| solicitud_alquiler.estado | A | PRODUCIDO | EvaluacionServiceImpl: DESTINO_SOLICITUD aprobada |
| solicitud_alquiler.estado | R | PRODUCIDO | EvaluacionServiceImpl: DESTINO_SOLICITUD rechazada |
| solicitud_alquiler.estado | C | PRODUCIDO | ContratoServiceImpl: cascada del cierre |
| solicitud_alquiler.estado | D | RESERVADO_FUTURO | Sin productor. Se implementa en 7.3.3 con POST /solicitudes/{id}/desistir |
| tarea.estado | P | PRODUCIDO | Tarea: estado inicial al derivar la tarea |
| tarea.estado | C | PRODUCIDO | Tarea.completar |
| tarea.estado | A | PRODUCIDO | Tarea.cancelar, desde TareaServiceImpl |
| tarea.estado | E | RESERVADO_FUTURO | Falta una accion funcional iniciar tarea. No se inventa endpoint para llenar el enum |
| tarea.estado | V | DERIVADO | El vencimiento ya se conoce por fecha en lectura. Persistirlo duplicaria la verdad |
| token_acceso.estado | V | PRODUCIDO | TokenAcceso: valor por defecto al emitir |
| token_acceso.estado | C | PRODUCIDO | MfaServiceImpl y ContrasenaServiceImpl: consumo del token |
| token_acceso.estado | R | PRODUCIDO | TokenAccesoRepository.invalidarVivosDe (@Modifying JPQL) |
| token_acceso.estado | A | PRODUCIDO | TokenAcceso y TokenAccesoRepository.sumarIntentoFallido |
| usuario_organizacion.estado | A | PRODUCIDO | UsuariosInternos.registrar: alta de membresia |
| usuario_organizacion.estado | I | PRODUCIDO | Baja de membresia |
| visita.estado | P | PRODUCIDO | VisitaServiceImpl: programar |
| visita.estado | G | PRODUCIDO | VisitaServiceImpl: reprogramar |
| visita.estado | C | PRODUCIDO | VisitaServiceImpl: cancelar |
| visita.estado | N | PRODUCIDO | VisitaServiceImpl: no realizada |
| visita.estado | R | PRODUCIDO | VisitaServiceImpl: realizar |
