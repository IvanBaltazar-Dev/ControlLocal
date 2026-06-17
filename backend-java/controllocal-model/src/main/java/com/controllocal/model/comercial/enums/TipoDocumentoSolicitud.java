package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoDocumentoSolicitud implements CodigoEnum {
    DOCUMENTO_IDENTIDAD("I", "Documento de identidad", 1L),
    FICHA_RUC("R", "Ficha o constancia RUC", 2L),
    VIGENCIA_PODER("V", "Vigencia de poder", 3L),
    PODER_REPRESENTACION("P", "Poder de representacion", 4L),
    SUSTENTO_ECONOMICO("E", "Sustento economico", 5L),
    GARANTIA("G", "Documento de garantia", 6L),
    DECLARACION_JURADA("D", "Declaracion jurada", 7L),
    OTRO("O", "Otro", 8L);

    private final String codigo;
    private final String descripcion;
    private final Long idCatalogo;

    TipoDocumentoSolicitud(String codigo, String descripcion, Long idCatalogo) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.idCatalogo = idCatalogo;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public Long getIdCatalogo() {
        return idCatalogo;
    }

    public static TipoDocumentoSolicitud porIdCatalogo(Long idCatalogo) {
        if (idCatalogo == null) {
            return null;
        }
        for (TipoDocumentoSolicitud tipo : values()) {
            if (tipo.idCatalogo.equals(idCatalogo)) {
                return tipo;
            }
        }
        return null;
    }

    public static TipoDocumentoSolicitud fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoDocumentoSolicitud.class, codigo);
    }
}
