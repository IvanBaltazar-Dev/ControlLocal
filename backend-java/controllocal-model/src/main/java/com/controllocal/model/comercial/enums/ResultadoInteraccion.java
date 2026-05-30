package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum ResultadoInteraccion implements CodigoEnum {
    PENDIENTE("P", "Pendiente"),
    INTERESADO("I", "Interesado"),
    NO_INTERESADO("N", "No interesado"),
    SEGUIMIENTO("S", "Seguimiento"),
    DESCARTADO("D", "Descartado");

    private final String codigo;
    private final String descripcion;

    ResultadoInteraccion(String codigo, String descripcion) {
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

    public static ResultadoInteraccion fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ResultadoInteraccion.class, codigo);
    }

    /**
     * Indica si el resultado mantiene viva la oportunidad y amerita seguimiento.
     * Se comparte entre interacciones y visitas para responder, de forma uniforme,
     * "¿debemos darle seguimiento?".
     */
    public boolean mantieneOportunidadAbierta() {
        return this == PENDIENTE || this == INTERESADO || this == SEGUIMIENTO;
    }

    /**
     * Indica si el resultado implica que el cliente no continua. En ese caso el
     * flujo debe registrar un {@code MotivoNoContinuidad} ligado al origen
     * (interaccion o visita) para cerrar la oportunidad.
     */
    public boolean implicaNoContinuidad() {
        return this == NO_INTERESADO || this == DESCARTADO;
    }
}
