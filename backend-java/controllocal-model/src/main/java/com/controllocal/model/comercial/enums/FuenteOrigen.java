package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum FuenteOrigen implements CodigoEnum {
    PORTAL, REDES_SOCIALES, WHATSAPP, LLAMADA_DIRECTA, REFERIDO,
    CARTERA_PROPIA, WEB_PROPIA, OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
