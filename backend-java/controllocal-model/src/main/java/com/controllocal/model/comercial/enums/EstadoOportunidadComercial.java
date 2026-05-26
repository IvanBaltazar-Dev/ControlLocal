package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoOportunidadComercial implements CodigoEnum {
    ABIERTA("A", "Abierta"),
    SOLICITUD_CREADA("S", "Solicitud creada"),
    NO_CONTINUA("N", "No continua"),
    FINALIZADA_EXITOSA("F", "Finalizada exitosa"),
    FINALIZADA_NO_FAVORABLE("X", "Finalizada no favorable");

    private final String codigo;
    private final String descripcion;

    EstadoOportunidadComercial(String codigo, String descripcion) {
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

    public static EstadoOportunidadComercial fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoOportunidadComercial.class, codigo);
    }
}
