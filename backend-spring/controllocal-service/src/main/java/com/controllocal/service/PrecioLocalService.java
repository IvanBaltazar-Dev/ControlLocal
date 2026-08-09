package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Historico de precios del local por hito comercial (E/R/U/P/O/A/C).
 */
public interface PrecioLocalService {

    /** Espejo de PrecioRequest (cable congelado). */
    record DatosPrecio(String hito, String moneda, BigDecimal monto, LocalDate fecha) {
    }

    /** Espejo de PrecioResponse (cable congelado). */
    record FichaPrecio(Long id, Long idLocal, String hito, String moneda, BigDecimal monto,
                       LocalDate fecha, LocalDateTime fechaCreacion) {
    }

    List<FichaPrecio> listarPorLocal(long idPropiedad);

    /** El {@code actor} aporta la organizacion que se estampa en el hito nuevo (D-20). */
    FichaPrecio registrar(long idPropiedad, DatosPrecio datos, Actor actor);
}
