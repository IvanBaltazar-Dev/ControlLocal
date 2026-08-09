package com.controllocal.web.dto;

import com.controllocal.service.PrecioLocalService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.PrecioResponse de la v1. */
public record PrecioResponse(Long id, Long idLocal, String hito, String moneda, BigDecimal monto,
                             LocalDate fecha, LocalDateTime fechaCreacion) {

    public static PrecioResponse desde(PrecioLocalService.FichaPrecio f) {
        return new PrecioResponse(f.id(), f.idLocal(), f.hito(), f.moneda(), f.monto(),
                f.fecha(), f.fechaCreacion());
    }
}
