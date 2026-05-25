package com.controllocal.model.usuario.enums;

import com.controllocal.model.CodigoEnum;

public enum RolUsuarioInterno implements CodigoEnum {
    BROKER("B", "Broker"),
    AGENTE("A", "Agente");

    private final String codigo;
    private final String descripcion;

    RolUsuarioInterno(String codigo, String descripcion) {
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

    public static RolUsuarioInterno fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(RolUsuarioInterno.class, codigo);
    }
}
