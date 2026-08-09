package com.controllocal.web.dto;

import com.controllocal.service.VisitaService;

import java.util.List;

/**
 * KPI de la bandeja de visitas por estado + los distritos del alcance.
 * <b>Extension aditiva</b>: no existe en la v1, donde los cinco contadores se
 * derivaban de la agenda entera descargada en memoria.
 *
 * <p>Los distritos viajan aqui para que el selector sea data-driven sin una
 * llamada extra: ofrecer un filtro que no devuelve nada es peor que no
 * ofrecerlo.
 */
public record ResumenVisitasResponse(long total, long programadas, long reprogramadas,
                                     long realizadas, long noRealizadas, long canceladas,
                                     List<String> distritos) {

    public static ResumenVisitasResponse desde(VisitaService.ResumenVisitas r) {
        return new ResumenVisitasResponse(r.total(), r.programadas(), r.reprogramadas(),
                r.realizadas(), r.noRealizadas(), r.canceladas(), r.distritos());
    }
}
