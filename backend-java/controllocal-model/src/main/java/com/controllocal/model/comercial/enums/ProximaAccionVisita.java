package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Siguiente paso acordado tras la visita.
 */
public enum ProximaAccionVisita implements CodigoEnum {
    NUEVA_VISITA("V", "Nueva visita"),
    OFERTA("O", "Oferta"),
    SEGUIMIENTO("S", "Seguimiento"),
    DESCARTADO("D", "Descartado");

    private final String codigo;
    private final String descripcion;

    ProximaAccionVisita(String codigo, String descripcion) {
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

    public static ProximaAccionVisita fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ProximaAccionVisita.class, codigo);
    }
}
