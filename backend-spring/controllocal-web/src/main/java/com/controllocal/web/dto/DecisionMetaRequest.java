package com.controllocal.web.dto;

/**
 * El broker resuelve una propuesta: la acepta o la rechaza.
 *
 * <p>El motivo se exige **en los dos casos**. Rechazar sin explicar deja al
 * agente con un «no» del que no puede aprender, y en el historial deja un hueco
 * justo donde hubo una decisión.
 */
public record DecisionMetaRequest(boolean acepta, String motivo) {
}
