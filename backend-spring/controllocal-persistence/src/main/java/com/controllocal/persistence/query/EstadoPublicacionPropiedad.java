package com.controllocal.persistence.query;

/**
 * Read-DTO: estado de la publicacion principal (la mas reciente) de cada
 * propiedad de un lote. Las propiedades sin publicacion no traen fila:
 * el service las completa con 'B' (borrador), como la v1.
 */
public interface EstadoPublicacionPropiedad {

    Long getIdPropiedad();

    String getEstado();
}
