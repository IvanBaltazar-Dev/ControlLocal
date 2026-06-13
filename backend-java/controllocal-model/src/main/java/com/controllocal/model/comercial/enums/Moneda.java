package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Moneda de los montos comerciales.
 */
public enum Moneda implements CodigoEnum {
    PEN("PEN", "Soles"),
    USD("USD", "Dolares");

    private final String codigo;
    private final String descripcion;

    Moneda(String codigo, String descripcion) {
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

    public static Moneda fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(Moneda.class, codigo);
    }
}
