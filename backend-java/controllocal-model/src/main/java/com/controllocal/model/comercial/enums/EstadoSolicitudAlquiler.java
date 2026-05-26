package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoSolicitudAlquiler implements CodigoEnum {
    REGISTRADA("G", "Registrada"),
    EN_REVISION("E", "En revision"),
    OBSERVADA("O", "Observada"),
    APROBADA("A", "Aprobada"),
    RECHAZADA("R", "Rechazada"),
    DESISTIDA("D", "Desistida");

    private final String codigo;
    private final String descripcion;

    EstadoSolicitudAlquiler(String codigo, String descripcion) {
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

    public static EstadoSolicitudAlquiler fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoSolicitudAlquiler.class, codigo);
    }
}
