package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

import java.util.List;

/**
 * Una meta del mes, con su propuesta viva y **cómo se llegó a su valor**.
 *
 * <p>{@code valor} es <b>nulable</b>, y es la mitad de la información: un agente
 * sin meta no tiene meta cero. Cero significa que este mes no se le pide ese
 * resultado —una decisión—; nulo significa que nadie ha decidido. La pantalla
 * necesita distinguirlos para saber a quién le falta, que es exactamente lo que
 * deja al equipo sin semáforo por cobertura incompleta.
 *
 * <p>{@code historial} viaja siempre, aunque esté vacío: es lo que permite leer
 * «Meta inicial 8 → revisada a 6 el 18 de agosto» en vez de un número sin
 * procedencia.
 */
public record MetaResponse(long idRolAgente, String agente, String kpi, String rotulo,
                           Integer valor, PropuestaResponse propuesta,
                           List<RevisionResponse> historial) {

    public static MetaResponse desde(MetaComercialService.MetaDeAgente m) {
        return new MetaResponse(m.idRolAgente(), m.nombreAgente(), m.kpi(), m.rotuloKpi(),
                m.valor(), PropuestaResponse.desde(m.propuesta()),
                m.historial().stream().map(RevisionResponse::desde).toList());
    }
}
