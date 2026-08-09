package com.controllocal.persistence.query;

import java.math.BigDecimal;

/**
 * Proyeccion de {@code GET /captaciones/propiedades-equipo}: UNA fila por
 * inmueble captado por el equipo, con los datos de su captacion mas reciente.
 *
 * <p>No es la captacion ni la propiedad: es la union de ambas vista "por
 * inmueble", que es como la pantalla del broker mira su cartera. Un local
 * puede acumular varias captaciones (cerradas, rechazadas, vencidas) y solo
 * una ACTIVA a la vez, asi que sin deduplicar el mismo inmueble aparece
 * repetido tantas veces como veces se intento captar.
 */
public interface PropiedadDeEquipo {

    Long getIdPropiedad();

    Long getIdCaptacion();

    String getCodigoCaptacion();

    /** Estado de la captacion mas reciente: P, O, R, A, C o V. */
    String getEstado();

    String getCodigoLocal();

    String getDireccion();

    String getDistrito();

    String getRubro();

    BigDecimal getAreaM2();

    Long getIdAgente();

    String getAgenteNombre();
}
