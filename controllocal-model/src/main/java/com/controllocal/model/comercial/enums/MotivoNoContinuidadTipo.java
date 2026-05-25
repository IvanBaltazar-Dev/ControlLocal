package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum MotivoNoContinuidadTipo implements CodigoEnum {
    PRECIO("P", "Precio"),
    UBICACION("U", "Ubicacion"),
    CONDICIONES_CONTRATO("C", "Condiciones del contrato"),
    LOCAL_NO_ADECUADO("L", "Local no adecuado"),
    CLIENTE_NO_RESPONDE("N", "Cliente no responde"),
    ENCONTRO_OTRA_OPCION("E", "Encontro otra opcion"),
    OTRO("O", "Otro");

    private final String codigo;
    private final String descripcion;

    MotivoNoContinuidadTipo(String codigo, String descripcion) {
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

    public static MotivoNoContinuidadTipo fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(MotivoNoContinuidadTipo.class, codigo);
    }
}
