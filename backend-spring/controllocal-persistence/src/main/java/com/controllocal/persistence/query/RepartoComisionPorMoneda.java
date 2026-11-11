package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Reparto histórico en la moneda de su evaluación base, con cobertura conocida. */
public interface RepartoComisionPorMoneda {
    String getMoneda();
    BigDecimal getParteAgente();
    BigDecimal getSaldoPagoAgente();
    Long getDeterminados();
    Long getPendientes();
    Long getSaldoDeterminados();
    Long getSaldoPendientes();
}
