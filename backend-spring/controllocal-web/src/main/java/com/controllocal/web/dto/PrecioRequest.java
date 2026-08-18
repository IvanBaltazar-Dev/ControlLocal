package com.controllocal.web.dto;

import com.controllocal.service.PrecioLocalService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Hito del historico de precios.
 *
 * <p>{@code operacion} —VENTA o ALQUILER— entra con el contrato v2 descongelado
 * (D-E4-1). Es opcional: si la propiedad tiene un unico encargo vivo, el
 * servidor lee la operacion de ahi. Lo que no hace ya es suponer alquiler
 * cuando no hay de donde deducirla.
 */
public record PrecioRequest(String hito, String moneda, BigDecimal monto, LocalDate fecha,
                            String operacion) {

    public PrecioLocalService.DatosPrecio aDatos() {
        return new PrecioLocalService.DatosPrecio(hito, moneda, monto, fecha, operacion);
    }
}
