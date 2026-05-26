    package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoVisita implements CodigoEnum {
    PROGRAMADA("P", "Programada"),
    REPROGRAMADA("G", "Reprogramada"),
    CANCELADA("C", "Cancelada"),
    REALIZADA("R", "Realizada");

    private final String codigo;
    private final String descripcion;

    EstadoVisita(String codigo, String descripcion) {
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

    public static EstadoVisita fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoVisita.class, codigo);
    }
}
