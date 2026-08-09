package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/** Tramo del embudo de conversion: valor absoluto + porcentaje sobre la base. */
public record IndicadorEmbudoResponse(String etapa, int valor, int porcentaje) {

    public static IndicadorEmbudoResponse desde(IndicadorService.Embudo embudo) {
        return new IndicadorEmbudoResponse(embudo.etapa(), embudo.valor(), embudo.porcentaje());
    }
}
