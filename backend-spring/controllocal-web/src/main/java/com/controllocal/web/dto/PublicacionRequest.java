package com.controllocal.web.dto;

import com.controllocal.service.PublicacionService;

import java.math.BigDecimal;

/** Contrato CONGELADO: espejo de Dtos.PublicacionRequest de la v1. */
public record PublicacionRequest(String canal, String urlPublicacion, BigDecimal rentaPublicada,
                                 String moneda, String tituloAnuncio, String codigoOrigen, String estado) {

    public PublicacionService.DatosPublicacion aDatos() {
        return new PublicacionService.DatosPublicacion(canal, urlPublicacion, rentaPublicada,
                moneda, tituloAnuncio, codigoOrigen, estado);
    }
}
