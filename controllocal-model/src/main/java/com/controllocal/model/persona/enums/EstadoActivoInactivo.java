package com.controllocal.model.persona.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoActivoInactivo implements CodigoEnum {
    ACTIVO("A", "Activo"),
    INACTIVO("I", "Inactivo");

    private final String codigo;
    private final String descripcion;

    EstadoActivoInactivo(String codigo, String descripcion) {
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

    public static EstadoActivoInactivo fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoActivoInactivo.class, codigo);
    }
}
