package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum ResultadoRevisionDocumento implements CodigoEnum {
    PENDIENTE("P", "Pendiente"),
    CONFORME("C", "Conforme"),
    OBSERVADO("O", "Observado");

    private final String codigo;
    private final String descripcion;

    ResultadoRevisionDocumento(String codigo, String descripcion) {
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

    public static ResultadoRevisionDocumento fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoRevisionDocumento.class, codigo);
    }
}
