package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Parte del agente asignada en liquidaciones no anuladas, por moneda. */
public interface RepartoComisionPorMoneda {
    String getMoneda();
    BigDecimal getParteAgente();
}
