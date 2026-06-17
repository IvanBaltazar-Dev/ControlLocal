package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Desenlace de la oportunidad, derivado de su estado final.
 * No se persiste: se calcula con fromEstado(...).
 */
public enum DesenlaceOportunidad implements CodigoEnum {
    CERRADA_FAVORABLE("F", "Cerrada favorable"),
    CAIDA("X", "Caida");

    private final String codigo;
    private final String descripcion;

    DesenlaceOportunidad(String codigo, String descripcion) {
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

    public static DesenlaceOportunidad fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(DesenlaceOportunidad.class, codigo);
    }

    // FINALIZADA_EXITOSA -> cerrada favorable; FINALIZADA_NO_FAVORABLE -> caida.
    public static DesenlaceOportunidad fromEstado(EstadoOportunidadComercial estado) {
        if (estado == EstadoOportunidadComercial.FINALIZADA_EXITOSA) {
            return CERRADA_FAVORABLE;
        }
        if (estado == EstadoOportunidadComercial.FINALIZADA_NO_FAVORABLE) {
            return CAIDA;
        }
        return null;
    }
}
