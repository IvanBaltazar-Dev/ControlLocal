package com.controllocal.persistence.query;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Los cuatro renglones del expediente comercial de una propiedad, en una fila
 * (D-E2-1 §10.3, E2.4).
 *
 * <p>Los datos existían repartidos —{@code captacion}, {@code propiedad},
 * {@code historico_precio}, {@code visita}, el propietario— y lo que faltaba era
 * <b>la vista que los junta</b>. Esto es esa vista: una consulta por lote de
 * propiedades, no cuatro por asunto.
 *
 * <p>La {@code serie} de la renta no viene aquí: es una colección y rompería la
 * fila. Se pide aparte, también por lote, a {@code historico_precio} — que es lo
 * único de los cuatro que ya existía cerrado (E0).
 */
public interface ExpedienteDeLaPropiedad {

    Long getIdPropiedad();

    // --- Renglón 1 · Encargo -----------------------------------------

    LocalDate getInicioVigencia();

    LocalDate getFinVigencia();

    LocalDate getFechaCaptacion();

    // --- Renglón 2 · Renta -------------------------------------------

    BigDecimal getRenta();

    String getMoneda();

    /** Cuándo se fijó la renta vigente; con ella se dice «sin cambios desde». */
    LocalDate getRentaDesde();

    // --- Renglón 3 · Actividad ---------------------------------------

    Long getVisitasRealizadas();

    Long getVisitasTotales();

    // --- Renglón 4 · Propietario -------------------------------------

    String getPropietario();

    String getDireccion();

    String getDistrito();
}
