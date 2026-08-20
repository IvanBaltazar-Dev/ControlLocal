package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

import java.time.OffsetDateTime;

/**
 * Un ajuste que el agente pidió y el broker todavía no ha resuelto.
 *
 * <p>Viaja con el valor vigente al lado del propuesto para que el broker decida
 * viendo el salto —«8 → 6»— y no solo el destino. {@code valorVigente} es nulo
 * cuando el agente propone sobre una meta que aún no le habían fijado.
 */
public record PropuestaResponse(long idRevision, long idRolAgente, String agente,
                                String kpi, String rotulo, Integer valorVigente,
                                int valorPropuesto, String motivo, OffsetDateTime fecha) {

    public static PropuestaResponse desde(MetaComercialService.PropuestaPendiente p) {
        return p == null ? null
                : new PropuestaResponse(p.idRevision(), p.idRolAgente(), p.nombreAgente(),
                p.kpi(), p.rotuloKpi(), p.valorVigente(), p.valorPropuesto(), p.motivo(),
                p.fecha());
    }
}
