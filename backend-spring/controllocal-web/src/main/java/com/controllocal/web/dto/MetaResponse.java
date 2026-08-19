package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

/**
 * Una meta del mes en el cable.
 *
 * <p>{@code valor} es <b>nulable</b>, y es la mitad de la informacion: un agente
 * sin meta no tiene meta cero. Cero significa que este mes no se le pide ese
 * resultado —una decision—; nulo significa que nadie ha decidido. La pantalla
 * necesita distinguirlos para saber a quien le falta, que es exactamente lo que
 * deja al equipo sin semaforo por cobertura incompleta.
 */
public record MetaResponse(long idRolAgente, String agente, String kpi, String rotulo,
                           Integer valor) {

    public static MetaResponse desde(MetaComercialService.MetaDeAgente m) {
        return new MetaResponse(m.idRolAgente(), m.nombreAgente(), m.kpi(), m.rotuloKpi(),
                m.valor());
    }
}
