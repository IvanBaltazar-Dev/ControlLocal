package com.controllocal.web.dto;

import com.controllocal.service.MetaComercialService;

/**
 * El agente pide un ajuste de **su** meta.
 *
 * <p>No la cambia: queda en espera de que el broker decida. Es la mitad de la
 * política que impide que el indicador sea manipulable —bajar la meta porque se
 * va perdiendo— sin volverlo inmutable, que tampoco sirve cuando hay vacaciones,
 * altas a mitad de mes o cambios de cartera.
 */
public record PropuestaMetaRequest(String mes, String kpi, int valor, String motivo) {

    public MetaComercialService.Propuesta aDatos() {
        return new MetaComercialService.Propuesta(kpi, valor, motivo);
    }
}
