package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Decision del propietario sobre el documento de condiciones y terminos
 * entregado durante la prospeccion.
 * POSPUESTA = "por ahora no": mantiene viva la prospeccion y exige nueva accion de seguimiento.
 */
public enum ResultadoPropuesta implements CodigoEnum {
    PENDIENTE("P", "Pendiente"),
    ACEPTADA("A", "Aceptada"),
    RECHAZADA("R", "Rechazada"),
    POSPUESTA("S", "Recontactar");

    private final String codigo;
    private final String descripcion;

    ResultadoPropuesta(String codigo, String descripcion) {
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

    public static ResultadoPropuesta fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoPropuesta.class, codigo);
    }
}
