package com.controllocal.model.inmueble.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Estado de publicacion del inmueble (independiente de su disponibilidad).
 */
public enum EstadoPublicacion implements CodigoEnum {
    BORRADOR("B", "Sin publicar"),
    PUBLICADO("P", "Publicado"),
    PAUSADO("S", "Pausado"),
    CERRADO("C", "Cerrado");

    private final String codigo;
    private final String descripcion;

    EstadoPublicacion(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public static EstadoPublicacion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoPublicacion.class, codigo);
    }
}
