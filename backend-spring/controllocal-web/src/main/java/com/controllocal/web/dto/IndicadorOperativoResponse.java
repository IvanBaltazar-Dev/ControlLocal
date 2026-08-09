package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/**
 * Indicadores operativos del seguimiento. {@code recontactosAlDia} son los
 * recontactos cuyo proximo contacto todavia no vence (atendidos a tiempo).
 */
public record IndicadorOperativoResponse(
        int recontactosVencidos,
        int recontactosAlDia,
        int diasPromedioSinSeguimiento,
        int visitasPendientes,
        int solicitudesSinCierre,
        int conversionProspeccionCaptacion) {

    public static IndicadorOperativoResponse desde(IndicadorService.Operativo operativo) {
        return new IndicadorOperativoResponse(
                operativo.recontactosVencidos(),
                operativo.recontactosAlDia(),
                operativo.diasPromedioSinSeguimiento(),
                operativo.visitasPendientes(),
                operativo.solicitudesSinCierre(),
                operativo.conversionProspeccionCaptacion());
    }
}
