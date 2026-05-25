package com.controllocal.model.persona.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoPersona implements CodigoEnum {
    NATURAL("N", "Natural"),
    JURIDICA("J", "Juridica");

    private final String codigo;
    private final String descripcion;

    TipoPersona(String codigo, String descripcion) {
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

    public static TipoPersona fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoPersona.class, codigo);
    }
}
