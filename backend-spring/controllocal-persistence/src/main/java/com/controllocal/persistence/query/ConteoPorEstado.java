package com.controllocal.persistence.query;

/**
 * Cuantas filas hay de cada estado, resuelto en la BASE DE DATOS con un solo
 * {@code group by}.
 *
 * <p>Es deliberadamente generico —{@code estado} es el codigo de una letra del
 * cable, sin interpretar— para que lo reusen todos los listados con KPI:
 * locales, prospecciones, captaciones, oportunidades, solicitudes. Cada
 * service traduce esos pares a su propio DTO de resumen; la consulta y la
 * proyeccion no se reescriben por vertical.
 *
 * <p>Lo que evita: contar en memoria sobre las filas que el cliente descargo.
 * Un KPI calculado asi solo cuenta la pagina visible y miente en cuanto hay
 * mas de una.
 */
public interface ConteoPorEstado {

    String getEstado();

    long getTotal();
}
