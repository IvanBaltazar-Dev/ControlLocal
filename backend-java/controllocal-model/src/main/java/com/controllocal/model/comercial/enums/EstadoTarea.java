package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoTarea implements CodigoEnum {
    PENDIENTE, EN_PROCESO, COMPLETADA, VENCIDA, CANCELADA;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
