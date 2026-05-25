package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoDocumentoSolicitud implements CodigoEnum {
    DOCUMENTO_IDENTIDAD("I", "Documento de identidad"),
    FICHA_RUC("R", "Ficha o constancia RUC"),
    VIGENCIA_PODER("V", "Vigencia de poder"),
    PODER_REPRESENTACION("P", "Poder de representacion"),
    SUSTENTO_ECONOMICO("E", "Sustento economico"),
    GARANTIA("G", "Documento de garantia"),
    DECLARACION_JURADA("D", "Declaracion jurada"),
    OTRO("O", "Otro");

    private final String codigo;
    private final String descripcion;

    TipoDocumentoSolicitud(String codigo, String descripcion) {
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

    public static TipoDocumentoSolicitud fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoDocumentoSolicitud.class, codigo);
    }
}
