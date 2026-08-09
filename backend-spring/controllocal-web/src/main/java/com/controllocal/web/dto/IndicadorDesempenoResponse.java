package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/** Fila de "Carga del equipo": broker si consulta el ADMIN, agente en los demas casos. */
public record IndicadorDesempenoResponse(String nombre, int captaciones, int cierres, int conversion) {

    public static IndicadorDesempenoResponse desde(IndicadorService.Desempeno fila) {
        return new IndicadorDesempenoResponse(
                fila.nombre(), fila.captaciones(), fila.cierres(), fila.conversion());
    }
}
