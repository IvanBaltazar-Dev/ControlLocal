package com.controllocal.persistence.query;

/**
 * Read-DTO: clave de la foto de portada (primera por orden) de cada
 * propiedad de un lote. Alimenta las tarjetas de las listas sin N+1.
 */
public interface PortadaPropiedad {

    Long getIdPropiedad();

    String getClave();
}
