package com.controllocal.persistence.query;

import java.math.BigDecimal;

/**
 * Lo que puede firmarse este mes, agrupado <b>por moneda</b>.
 *
 * <p>La moneda viaja porque el importe no se convierte. Sumar soles y dolares
 * necesita un tipo de cambio, y un tipo de cambio que nadie declaro es un numero
 * inventado dentro de una cifra que se presenta como hecho.
 */
public interface CierrePosible {

    String getMoneda();

    int getOperaciones();

    BigDecimal getImporte();
}
