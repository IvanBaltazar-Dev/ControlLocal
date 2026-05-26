package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum ResultadoEvaluacionSolicitud implements CodigoEnum {
    APROBADA("A", "Aprobada"),
    RECHAZADA("R", "Rechazada"),
    OBSERVADA("O", "Observada");

    private final String codigo;
    private final String descripcion;

    ResultadoEvaluacionSolicitud(String codigo, String descripcion) {
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

    public static ResultadoEvaluacionSolicitud fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoEvaluacionSolicitud.class, codigo);
    }
}
