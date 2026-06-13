package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Operacion solicitada por el cliente (la venta es una extension futura).
 */
public enum OperacionRequerimiento implements CodigoEnum {
    ALQUILER("A", "Alquiler"),
    COMPRA("C", "Compra");

    private final String codigo;
    private final String descripcion;

    OperacionRequerimiento(String codigo, String descripcion) {
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

    public static OperacionRequerimiento fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(OperacionRequerimiento.class, codigo);
    }
}
