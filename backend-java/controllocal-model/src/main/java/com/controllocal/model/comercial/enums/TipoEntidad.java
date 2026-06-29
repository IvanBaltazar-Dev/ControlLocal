package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum TipoEntidad implements CodigoEnum {
    PROSPECCION, CAPTACION, OPORTUNIDAD, INTERACCION, VISITA,
    SOLICITUD_ALQUILER, INMUEBLE, PUBLICACION, CONTRATO_ALQUILER,
    CLIENTE_INTERESADO, PROPIETARIO,
    // Etapa 8 (reservado): perfil de búsqueda del cliente, para tareas de coincidencia de cartera.
    REQUERIMIENTO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
