package com.controllocal.web.dto;

import com.controllocal.service.OportunidadService;

/**
 * KPI de la bandeja de oportunidades por etapa. <b>Extension aditiva</b>: no
 * existe en la v1, donde el Blazor descargaba todas las oportunidades del
 * alcance y las agrupaba en memoria — con paginacion real eso solo contaria la
 * pagina visible.
 *
 * <p>Los cinco cubos son los estados de la maquina (A/S/N/F/X) y el total los
 * suma TODOS, no solo esos cinco: si apareciera un estado nuevo, el total
 * seguiria cuadrando con la lista.
 */
public record ResumenOportunidadesResponse(long total, long abiertas, long conSolicitud,
                                           long noContinuan, long exitosas, long noFavorables) {

    public static ResumenOportunidadesResponse desde(OportunidadService.ResumenOportunidades r) {
        return new ResumenOportunidadesResponse(r.total(), r.abiertas(), r.conSolicitud(),
                r.noContinuan(), r.exitosas(), r.noFavorables());
    }
}
