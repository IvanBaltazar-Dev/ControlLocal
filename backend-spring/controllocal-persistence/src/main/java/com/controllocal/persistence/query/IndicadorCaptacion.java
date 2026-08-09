package com.controllocal.persistence.query;

import java.time.LocalDate;

/**
 * Read-DTO de una captacion para los agregados de E4 (resumen y avance).
 *
 * <p>La v1 cargaba el grafo entero de cada captacion —local, propietario,
 * agente y persona— solo para contar por estado. Aqui viaja lo minimo que
 * necesitan el donut, la salud, las series y la fila de avance (D-E4-2).
 */
public interface IndicadorCaptacion {

    Long getId();

    /** Rol AGENTE responsable: es la unica dimension de alcance de indicadores. */
    Long getIdAgente();

    /** Codigo de 1 caracter: P, O, R, A, C, V. */
    String getEstado();

    LocalDate getFechaCaptacion();

    /** Base de {@code propiedadesEquipo}, que cuenta propiedades distintas. */
    Long getIdPropiedad();

    String getCodigo();

    String getDireccion();

    String getDistrito();
}
