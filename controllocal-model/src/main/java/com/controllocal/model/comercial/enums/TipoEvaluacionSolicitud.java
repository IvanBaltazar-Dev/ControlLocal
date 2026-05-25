package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoEvaluacionSolicitud implements CodigoEnum {
    PRELIMINAR("P", "Preliminar"),
    OBSERVACION("O", "Observacion"),
    FINAL("F", "Final");

    private final String codigo;
    private final String descripcion;

    TipoEvaluacionSolicitud(String codigo, String descripcion) {
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

    public static TipoEvaluacionSolicitud fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoEvaluacionSolicitud.class, codigo);
    }
}
