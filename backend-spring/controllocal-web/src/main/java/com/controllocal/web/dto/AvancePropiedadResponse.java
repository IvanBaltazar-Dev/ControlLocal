package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/**
 * RF-017: avance comercial de UNA propiedad con captacion ACTIVA. Los textos
 * ausentes viajan como cadena vacia, no como null.
 */
public record AvancePropiedadResponse(
        long idCaptacion,
        String codigoCaptacion,
        String direccion,
        String distrito,
        String estadoComercial,
        int oportunidadesTotales,
        int oportunidadesAbiertas,
        int oportunidadesConVisita,
        int oportunidadesConSolicitud,
        int cerradasExitosas,
        int cerradasNoFavorables,
        int cerradasNoContinuidad,
        int interesados,
        int interacciones,
        int visitasProgramadas,
        int visitasConcretadas,
        int solicitudesRecibidas,
        int tasaOportVisita,
        int tasaOportSolicitud,
        String motivoNoContinuidad) {

    public static AvancePropiedadResponse desde(IndicadorService.AvancePropiedad p) {
        return new AvancePropiedadResponse(
                p.idCaptacion(),
                p.codigoCaptacion(),
                p.direccion(),
                p.distrito(),
                p.estadoComercial(),
                p.oportunidadesTotales(),
                p.oportunidadesAbiertas(),
                p.oportunidadesConVisita(),
                p.oportunidadesConSolicitud(),
                p.cerradasExitosas(),
                p.cerradasNoFavorables(),
                p.cerradasNoContinuidad(),
                p.interesados(),
                p.interacciones(),
                p.visitasProgramadas(),
                p.visitasConcretadas(),
                p.solicitudesRecibidas(),
                p.tasaOportVisita(),
                p.tasaOportSolicitud(),
                p.motivoNoContinuidad());
    }
}
