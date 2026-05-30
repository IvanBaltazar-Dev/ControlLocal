package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Etapa del embudo de prospeccion (pre-captacion): el agente persigue al
 * propietario hasta captar el local. Es el espejo, del lado de la oferta, de
 * {@link EstadoOportunidadComercial} (lado de la demanda).
 */
public enum EstadoProspeccion implements CodigoEnum {
    PROSPECTO("P", "Prospecto"),
    CONTACTADO("C", "Contactado"),
    REUNION("R", "Reunion"),
    PROPUESTA_ENTREGADA("E", "Propuesta entregada"),
    EN_SEGUIMIENTO("S", "En seguimiento"),
    CAPTADO("T", "Captado"),
    DESCARTADO("D", "Descartado");

    private final String codigo;
    private final String descripcion;

    EstadoProspeccion(String codigo, String descripcion) {
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

    public static EstadoProspeccion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(EstadoProspeccion.class, codigo);
    }

    /** Sigue viva (ni captada ni descartada): admite avanzar el embudo. */
    public boolean enProceso() {
        return this != CAPTADO && this != DESCARTADO;
    }
}
