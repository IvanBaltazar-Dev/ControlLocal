package com.controllocal.model.persona.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoDocumentoIdentidad implements CodigoEnum {
    DNI("D", "DNI"),
    RUC("R", "RUC"),
    CARNET_EXTRANJERIA("C", "Carnet de extranjeria"),
    PASAPORTE("P", "Pasaporte");

    private final String codigo;
    private final String descripcion;

    TipoDocumentoIdentidad(String codigo, String descripcion) {
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

    public static TipoDocumentoIdentidad fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoDocumentoIdentidad.class, codigo);
    }
}
