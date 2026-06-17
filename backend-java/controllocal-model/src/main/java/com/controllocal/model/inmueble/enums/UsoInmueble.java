package com.controllocal.model.inmueble.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Uso permitido del inmueble.
 */
public enum UsoInmueble implements CodigoEnum {
    COMERCIAL("C", "Comercial"),
    VIVIENDA("V", "Vivienda"),
    INDUSTRIAL("I", "Industrial"),
    MIXTO("M", "Mixto");

    private final String codigo;
    private final String descripcion;

    UsoInmueble(String codigo, String descripcion) {
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

    public static UsoInmueble fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(UsoInmueble.class, codigo);
    }
}
