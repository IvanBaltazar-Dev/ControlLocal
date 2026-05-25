package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoDocumentoSolicitud implements CodigoEnum {
    REGISTRADO("R", "Registrado"),
    OBSERVADO("O", "Observado"),
    VALIDADO("V", "Validado");

    private final String codigo;
    private final String descripcion;

    EstadoDocumentoSolicitud(String codigo, String descripcion) {
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

    public static EstadoDocumentoSolicitud fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoDocumentoSolicitud.class, codigo);
    }
}
