package com.controllocal.model.inmueble.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoLocalComercial implements CodigoEnum {
    DISPONIBLE("D", "Disponible"),
    NO_DISPONIBLE("N", "No disponible"),
    INACTIVO("I", "Inactivo");

    private final String codigo;
    private final String descripcion;

    EstadoLocalComercial(String codigo, String descripcion) {
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

    public static EstadoLocalComercial fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoLocalComercial.class, codigo);
    }
}
