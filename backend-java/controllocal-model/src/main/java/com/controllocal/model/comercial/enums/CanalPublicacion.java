package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum CanalPublicacion implements CodigoEnum {
    URBANIA, ADONDEVIVIR, PROPERATI, NEXO_INMOBILIARIO, FACEBOOK,
    MARKETPLACE, INSTAGRAM, WHATSAPP, WEB_PROPIA, REFERIDO, OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
