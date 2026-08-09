package com.controllocal.web.dto;

/**
 * Cuerpo de la revision de disponibilidad.
 *
 * <p>Viaja el RESULTADO funcional —{@code VOLVER_AL_MERCADO} o
 * {@code RETIRAR_DEL_MERCADO}—, no la letra de disponibilidad: traducir a
 * {@code D}/{@code T} es cosa del backend, y asi el cliente no puede pedir un
 * estado que esta operacion no debe producir (por ejemplo {@code R}).
 */
public record RevisionDisponibilidadRequest(String resultado, String motivo) { }
