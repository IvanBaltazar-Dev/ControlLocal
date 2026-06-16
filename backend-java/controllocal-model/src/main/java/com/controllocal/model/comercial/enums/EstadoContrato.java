package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum EstadoContrato implements CodigoEnum {
    EN_PROCESO, FIRMADO, VIGENTE, RENOVADO, FINALIZADO, RESCINDIDO, ANULADO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
