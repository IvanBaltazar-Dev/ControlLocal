package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

import java.util.List;

/**
 * Fijar las metas de un mes.
 *
 * <p><b>Lo que no viene no se borra.</b> Se actualiza lo enviado y se deja lo
 * demas como estaba: un formulario a medio enviar no puede costarle al equipo
 * los objetivos que ya tenia. Para retirar una meta hay que decirlo, no callarla.
 */
public record MetasRequest(String mes, List<Asignacion> metas) {

    /** A quien, que KPI y cuanto. El KPI es el codigo unitario: C, P, S o F. */
    public record Asignacion(long idRolAgente, String kpi, int valor) {
    }

    public List<MetaComercialService.Asignacion> aDatos() {
        return metas == null ? List.of()
                : metas.stream()
                .map(a -> new MetaComercialService.Asignacion(a.idRolAgente(), a.kpi(), a.valor()))
                .toList();
    }
}
