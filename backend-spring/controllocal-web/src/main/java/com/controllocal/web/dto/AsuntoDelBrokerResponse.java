package com.controllocal.web.dto;

import com.controllocal.service.FocoDelBrokerService;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Interpretacion;

/**
 * Un asunto del broker: algo que <b>él</b> tiene que decidir (D-E2-5, E2.5).
 *
 * <p>Viaja aparte de `bandeja` porque no es la del agente filtrada: son
 * decisiones de otra naturaleza. `GET /tareas` sigue siendo del agente.
 *
 * <p>El `id` lleva el sufijo del rol a propósito — el mismo encargo puede estar
 * en las dos colas y son dos asuntos distintos (D-E2-1 §7.1).
 */
public record AsuntoDelBrokerResponse(String id, String tipo, String entidadTipo, Long entidadId,
                                      String entidadCodigo, String destino, int diasEsperando,
                                      String lado, String paso, Interpretacion interpretacion) {

    public static AsuntoDelBrokerResponse desde(FocoDelBrokerService.AsuntoDelBroker a) {
        return new AsuntoDelBrokerResponse(a.id(), a.tipo(), a.entidadTipo(), a.entidadId(),
                a.entidadCodigo(), a.destino(), a.diasEsperando(), a.lado(), a.paso(),
                a.interpretacion());
    }
}
