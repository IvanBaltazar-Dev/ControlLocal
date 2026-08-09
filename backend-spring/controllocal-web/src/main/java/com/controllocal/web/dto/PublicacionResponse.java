package com.controllocal.web.dto;

import com.controllocal.service.PublicacionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.PublicacionResponse de la v1. */
public record PublicacionResponse(Long id, String canal, String tituloAnuncio, BigDecimal rentaPublicada,
                                  String moneda, String estado, LocalDateTime fechaPublicacion,
                                  LocalDateTime fechaBaja, String urlPublicacion, String codigoOrigen) {

    public static PublicacionResponse desde(PublicacionService.FichaPublicacion f) {
        return new PublicacionResponse(f.id(), f.canal(), f.tituloAnuncio(), f.rentaPublicada(),
                f.moneda(), f.estado(), f.fechaPublicacion(), f.fechaBaja(),
                f.urlPublicacion(), f.codigoOrigen());
    }
}
