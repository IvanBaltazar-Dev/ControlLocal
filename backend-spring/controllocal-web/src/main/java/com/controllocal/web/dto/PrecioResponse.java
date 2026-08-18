package com.controllocal.web.dto;

import com.controllocal.service.PrecioLocalService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un hito del historico, con la operacion a la que pertenece.
 *
 * <p>Sin {@code operacion} en la respuesta, una propiedad en venta y en
 * alquiler devolvia las dos series mezcladas y el cliente no tenia forma de
 * separarlas: 180 000 y 2 900 en la misma lista solo se distinguen por
 * magnitud, y eso es adivinar.
 */
public record PrecioResponse(Long id, Long idLocal, String hito, String moneda, BigDecimal monto,
                             LocalDate fecha, LocalDateTime fechaCreacion, String operacion) {

    public static PrecioResponse desde(PrecioLocalService.FichaPrecio f) {
        return new PrecioResponse(f.id(), f.idLocal(), f.hito(), f.moneda(), f.monto(),
                f.fecha(), f.fechaCreacion(), f.operacion());
    }
}
