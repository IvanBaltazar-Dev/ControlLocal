package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Cobros netos de reversiones y pagos al agente, sin mezclar monedas. */
public interface MovimientoComisionPorMoneda {
    String getMoneda();
    BigDecimal getMontoCobrado();
    BigDecimal getMontoPagadoAgente();
}
