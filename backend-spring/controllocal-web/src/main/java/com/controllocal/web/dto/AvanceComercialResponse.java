package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

import java.util.List;

/**
 * RF-017: agregado + detalle por propiedad. Es una lectura ACUMULADA del estado
 * actual de cada captacion activa, no una ventana temporal, asi que no acepta
 * {@code periodo}.
 *
 * <p>{@code interesados} de la cabecera NO es la suma de la columna: son los
 * clientes distintos a nivel global (un mismo cliente interesado en dos
 * propiedades cuenta una vez arriba y dos abajo).
 */
public record AvanceComercialResponse(
        String ambito,
        int propiedades,
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
        List<AvancePropiedadResponse> detalle) {

    public static AvanceComercialResponse desde(IndicadorService.AvanceComercial a) {
        return new AvanceComercialResponse(
                a.ambito(),
                a.propiedades(),
                a.oportunidadesTotales(),
                a.oportunidadesAbiertas(),
                a.oportunidadesConVisita(),
                a.oportunidadesConSolicitud(),
                a.cerradasExitosas(),
                a.cerradasNoFavorables(),
                a.cerradasNoContinuidad(),
                a.interesados(),
                a.interacciones(),
                a.visitasProgramadas(),
                a.visitasConcretadas(),
                a.solicitudesRecibidas(),
                a.tasaOportVisita(),
                a.tasaOportSolicitud(),
                a.detalle().stream().map(AvancePropiedadResponse::desde).toList());
    }
}
