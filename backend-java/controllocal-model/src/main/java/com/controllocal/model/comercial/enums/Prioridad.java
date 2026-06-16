package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum Prioridad implements CodigoEnum {
    BAJA, MEDIA, ALTA;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name(); }
}
