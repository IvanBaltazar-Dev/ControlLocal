package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum OperacionRequerimiento implements CodigoEnum {
    ALQUILER("A", "Alquiler");

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
