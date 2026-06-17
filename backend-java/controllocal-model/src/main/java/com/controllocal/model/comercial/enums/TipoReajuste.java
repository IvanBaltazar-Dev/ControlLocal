package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoReajuste implements CodigoEnum {
    NINGUNO, ANUAL_FIJO, INDEXADO_IPC, OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
