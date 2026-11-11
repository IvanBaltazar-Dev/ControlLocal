package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/**
 * Indicadores operativos del seguimiento. {@code recontactosAlDia} son los
 * recontactos cuyo próximo contacto todavía no vence; no acreditan una actuación cumplida.
 */
public record IndicadorOperativoResponse(
        int recontactosVencidos,
        int recontactosAlDia,
        int visitasPendientes,
        int solicitudesSinCierre) {

    public static IndicadorOperativoResponse desde(IndicadorService.Operativo operativo) {
        return new IndicadorOperativoResponse(
                operativo.recontactosVencidos(),
                operativo.recontactosAlDia(),
                operativo.visitasPendientes(),
                operativo.solicitudesSinCierre());
    }
}
