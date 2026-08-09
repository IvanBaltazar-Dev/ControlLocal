package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Suma de comisiones no anuladas, agrupada sin mezclar monedas. */
public interface ComisionGeneradaPorMoneda {
    String getMoneda();
    BigDecimal getMonto();
}
