package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

    public enum EstadoRequerimiento implements CodigoEnum {
    ACTIVO, PAUSADO, CERRADO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name(); }
}
