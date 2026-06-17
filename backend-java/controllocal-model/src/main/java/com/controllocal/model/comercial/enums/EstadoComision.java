package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoComision implements CodigoEnum {
    PENDIENTE, PARCIAL, COBRADA, ANULADA;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name(); }
}
