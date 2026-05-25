package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoCaptacion implements CodigoEnum {
    PENDIENTE_REVISION("P", "Pendiente de revision"),
    OBSERVADA("O", "Observada"),
    RECHAZADA("R", "Rechazada"),
    ACTIVA("A", "Activa"),
    CERRADA("C", "Cerrada"),
    VENCIDA("V", "Vencida");

    private final String codigo;
    private final String descripcion;

    EstadoCaptacion(String codigo, String descripcion) {
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

    public static EstadoCaptacion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoCaptacion.class, codigo);
    }
}
