package com.controllocal.persistence.query;

/**
 * Un KPI canonico y lo conseguido en el mes, tal como sale de SQL.
 *
 * <p>El codigo es el unitario persistido ({@code C}, {@code P}, {@code S},
 * {@code F}); el nombre visible lo pone el dominio. Aqui no hay rotulos: si el
 * SQL supiera como se llama el indicador en pantalla, cambiar el nombre seria
 * una migracion.
 */
public interface ConteoKpi {

    String getKpi();

    int getCantidad();
}
