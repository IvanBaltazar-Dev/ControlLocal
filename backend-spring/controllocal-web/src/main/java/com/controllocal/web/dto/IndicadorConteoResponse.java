package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/** Conteo etiqueta/valor del cable: etapas del donut y salud de captaciones. */
public record IndicadorConteoResponse(String nombre, int valor) {

    public static IndicadorConteoResponse desde(IndicadorService.Conteo conteo) {
        return new IndicadorConteoResponse(conteo.nombre(), conteo.valor());
    }
}
