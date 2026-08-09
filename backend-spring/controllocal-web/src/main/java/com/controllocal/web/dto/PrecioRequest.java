package com.controllocal.web.dto;

import com.controllocal.service.PrecioLocalService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato CONGELADO: espejo de Dtos.PrecioRequest de la v1. */
public record PrecioRequest(String hito, String moneda, BigDecimal monto, LocalDate fecha) {

    public PrecioLocalService.DatosPrecio aDatos() {
        return new PrecioLocalService.DatosPrecio(hito, moneda, monto, fecha);
    }
}
