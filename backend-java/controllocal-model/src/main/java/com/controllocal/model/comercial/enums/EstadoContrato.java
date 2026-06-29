package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoContrato implements CodigoEnum {
    EN_PROCESO("P", "En proceso"),
    FIRMADO("D", "Firmado"),
    VIGENTE("V", "Vigente"),
    RENOVADO("R", "Renovado"),
    FINALIZADO("F", "Finalizado"),
    RESCINDIDO("S", "Rescindido"),
    ANULADO("A", "Anulado");

    private final String codigo;
    private final String descripcion;

    EstadoContrato(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    @Override public String getCodigo() { return codigo; }
    @Override public String getDescripcion() { return descripcion; }

    public static EstadoContrato fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoContrato.class, codigo);
    }
}
