package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoTarea implements CodigoEnum {
    SEGUIMIENTO, LLAMADA, VISITA, ENVIO_INFO, RECONTACTO, REPORTE_PROPIETARIO,
    ENVIAR_REVISION, SUBIR_DOCUMENTOS, REGISTRAR_CAPTACION, REGISTRAR_INTERACCION,
    // Etapa 8 (reservado): proponer una oportunidad prellenada a partir de una coincidencia de cartera.
    PROPONER_OPORTUNIDAD,
    OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
