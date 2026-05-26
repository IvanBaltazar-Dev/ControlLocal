package com.controllocal.model.usuario.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoOperativoAgente implements CodigoEnum {
    DISPONIBLE("D", "Disponible"),
    LICENCIA("L", "Licencia"),
    NO_DISPONIBLE("N", "No disponible");

    private final String codigo;
    private final String descripcion;

    EstadoOperativoAgente(String codigo, String descripcion) {
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

    public static EstadoOperativoAgente fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoOperativoAgente.class, codigo);
    }
}
