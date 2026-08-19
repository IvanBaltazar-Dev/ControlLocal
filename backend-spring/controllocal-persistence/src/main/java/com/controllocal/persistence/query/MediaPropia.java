package com.controllocal.persistence.query;

import java.math.BigDecimal;

/**
 * Una media de la propia casa, con la muestra sobre la que se calculo.
 *
 * @see #getBase() el denominador: visitas realizadas, contratos con cronologia
 * valida, intervalos entre contactos. Si es cero, <b>no hay media</b> —y no es
 * que la media valga cero—
 */
public interface MediaPropia {

    /** El denominador. Cero significa que la media no existe. */
    int getBase();

    /** El numerador, cuando la media es una proporcion. */
    int getCasos();

    /** El valor medio, cuando la media es una magnitud. Nulo si no hay base. */
    BigDecimal getValor();
}
