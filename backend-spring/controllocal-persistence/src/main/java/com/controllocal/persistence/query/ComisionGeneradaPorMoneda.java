package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Cuantía de las evaluaciones vigentes; una suma parcial conserva su cobertura. */
public interface ComisionGeneradaPorMoneda {
    String getMoneda();
    BigDecimal getMonto();
    BigDecimal getSaldoInformativoSobreCalculo();
    Long getDeterminados();
    Long getPendientes();
    Long getSaldoDeterminados();
    Long getSaldoPendientes();
}
