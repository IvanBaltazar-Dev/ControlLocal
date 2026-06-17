package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Opinion del cliente sobre el precio tras la visita.
 */
public enum OpinionPrecio implements CodigoEnum {
    ALTO("A", "Alto"),
    JUSTO("J", "Justo"),
    BAJO("B", "Bajo");

    private final String codigo;
    private final String descripcion;

    OpinionPrecio(String codigo, String descripcion) {
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

    public static OpinionPrecio fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(OpinionPrecio.class, codigo);
    }
}
