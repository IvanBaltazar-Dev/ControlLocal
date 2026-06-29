package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoAlerta implements CodigoEnum {
    SIN_RESPUESTA, SIN_AVANCE, OFERTA_POR_VENCER, CONTRATO_POR_VENCER,
    VISITA_PROXIMA, CAPTACION_VENCIDA,
    // Flujo de solicitud de alquiler: aviso al broker cuando una solicitud llega o
    // vuelve a evaluacion, y aviso al agente con el resultado de la evaluacion.
    SOLICITUD_REENVIADA, SOLICITUD_EVALUADA,
    // Cambios del agente/broker que generan aviso real (persistido) a la contraparte.
    // Admitidos por ck_alerta_tipo en 01_create_schema_controllocal.sql.
    SOLICITUD_DOCUMENTO,   // agente sube/reemplaza un documento en revision -> broker
    SOLICITUD_DOCUMENTO_REVISADO, // broker observa un documento puntual -> agente
    CAPTACION_CREADA,      // agente registra/reenvia una captacion -> broker
    CAPTACION_REVISADA,    // broker aprueba/observa/rechaza la captacion -> agente
    CAPTACION_CERRADA,     // broker cierra la captacion -> agente
    OPORTUNIDAD_CERRADA,   // agente concreta el alquiler (cierre exitoso) -> broker
    // Avisos de comision al agente. Nunca exponen el monto neto asignado.
    COMISION_ASIGNADA,     // broker supervisor define el monto del agente -> agente (lista para cobro)
    COMISION_COBRADA;      // broker administrador registra el cobro -> agente

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
