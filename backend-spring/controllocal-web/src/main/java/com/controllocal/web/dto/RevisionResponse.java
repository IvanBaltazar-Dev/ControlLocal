package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

import java.time.OffsetDateTime;

/**
 * Un paso del historial de la meta.
 *
 * <p>Existe porque un {@code fecha_actualizacion} no basta: hace falta saber de
 * cuánto a cuánto, cuándo, quién y **por qué**. Sin esto, dentro de tres meses
 * la base diría que la meta siempre fue 6 y el gráfico de cumplimiento mentiría.
 *
 * @param origen {@code B} la fijó el broker · {@code P} la propuso el agente
 * @param estado {@code A} aplicada · {@code E} en espera · {@code R} rechazada
 * @param valorAnterior nulo la primera vez que se fijó: no había de dónde venir
 * @param motivo obligatorio siempre; es lo único que quedará para entenderlo
 */
public record RevisionResponse(long id, String origen, String estado, Integer valorAnterior,
                               int valorPropuesto, String motivo, String autor,
                               OffsetDateTime fecha, String decisor, String motivoDecision) {

    public static RevisionResponse desde(MetaComercialService.Revision r) {
        return new RevisionResponse(r.id(), r.origen(), r.estado(), r.valorAnterior(),
                r.valorPropuesto(), r.motivo(), r.autor(), r.fecha(), r.decisor(),
                r.motivoDecision());
    }
}
