package com.controllocal.web.dto;

/**
 * Contrato CONGELADO: espejo de Dtos.AtenderAlertaResponse de la v1. Un sobre
 * de un solo booleano.
 *
 * <p>{@code atendida = false} <b>no es un error</b>: significa que la alerta ya
 * estaba atendida. El UPDATE de la v1 lleva {@code AND estado = 'ACTIVA'}, asi
 * que la segunda llamada no toca nada y responde 200 con false (D-F6-6).
 */
public record AtenderAlertaResponse(boolean atendida) {
}
