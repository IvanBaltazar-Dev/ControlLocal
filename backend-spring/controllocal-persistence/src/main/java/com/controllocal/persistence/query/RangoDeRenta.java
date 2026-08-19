package com.controllocal.persistence.query;

import java.math.BigDecimal;

/**
 * El rango de renta de un grupo comparable, con <b>cuantas propiedades lo
 * componen</b>.
 *
 * <p>Las observaciones no son un dato accesorio: sin ellas no se puede decidir
 * si el rango significa algo. Un minimo y un maximo sacados de dos propiedades
 * tienen exactamente la misma forma que los sacados de doscientas, y solo la N
 * los distingue.
 */
public interface RangoDeRenta {

    int getObservaciones();

    BigDecimal getMinimo();

    BigDecimal getMaximo();
}
