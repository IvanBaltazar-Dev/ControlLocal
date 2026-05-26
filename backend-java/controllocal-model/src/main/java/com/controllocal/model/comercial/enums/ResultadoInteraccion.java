package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum ResultadoInteraccion implements CodigoEnum {
    PENDIENTE("P", "Pendiente"),
    INTERESADO("I", "Interesado"),
    NO_INTERESADO("N", "No interesado"),
    SEGUIMIENTO("S", "Seguimiento"),
    DESCARTADO("D", "Descartado");

    private final String codigo;
    private final String descripcion;

    ResultadoInteraccion(String codigo, String descripcion) {
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

    public static ResultadoInteraccion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoInteraccion.class, codigo);
    }
}
