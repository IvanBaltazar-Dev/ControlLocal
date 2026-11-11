package com.controllocal.persistence.query;

import java.math.BigDecimal;

/** Condiciones del único Encargo ALQUILER vivo y autorizado, sin importes físicos ambiguos. */
public interface AlquilerLocalParaLectura {
    Long getIdPropiedad();
    BigDecimal getImporte();
    String getMoneda();
    String getEstadoPublicacion();
}
