package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoInmuebleComercial implements CodigoEnum {
    LOCAL_COMERCIAL, OFICINA, DEPOSITO_ALMACEN, STAND_MODULO, TERRENO_COMERCIAL, OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
