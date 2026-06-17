package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoEntidad implements CodigoEnum {
    PROSPECCION, CAPTACION, OPORTUNIDAD, INTERACCION, VISITA,
    SOLICITUD_ALQUILER, INMUEBLE, PUBLICACION, CONTRATO_ALQUILER;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
