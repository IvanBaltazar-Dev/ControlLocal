package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoAlerta implements CodigoEnum {
    SIN_RESPUESTA, SIN_AVANCE, OFERTA_POR_VENCER, CONTRATO_POR_VENCER,
    VISITA_PROXIMA, CAPTACION_VENCIDA,
    // Flujo de solicitud de alquiler: aviso al broker cuando una solicitud llega o
    // vuelve a evaluacion, y aviso al agente con el resultado de la evaluacion.
    SOLICITUD_REENVIADA, SOLICITUD_EVALUADA;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
