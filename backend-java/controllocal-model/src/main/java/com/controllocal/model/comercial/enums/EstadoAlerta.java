package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoAlerta implements CodigoEnum {
    ACTIVA, ATENDIDA, DESCARTADA;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name(); }
}
