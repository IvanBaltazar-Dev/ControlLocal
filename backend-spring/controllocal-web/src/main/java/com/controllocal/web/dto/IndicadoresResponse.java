package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

import java.util.List;

/**
 * Contrato CONGELADO de {@code Dtos.IndicadoresResponse}: una sola lectura
 * alimenta las tarjetas, las graficas, el embudo, la tabla de desempeno y los
 * contadores del menu lateral.
 *
 * <p>El <b>orden de los campos es parte del contrato</b> (la v1 emite un record
 * y el cliente .NET deserializa por nombre, pero el orden se conserva por
 * paridad de la respuesta). Ninguno es nulo: los escalares viajan en 0 y las
 * listas vacias viajan igual.
 *
 * <p>`captacionesPendientes` se retiro el 2026-08-08: repetia `captacionesPorRevisar`
 * con otro nombre porque la v1 lo emitia asi (D-E4-3). Nadie lo pintaba.
 */
public record IndicadoresResponse(
        String ambito,
        int captacionesPorRevisar,
        int solicitudesPorEvaluar,
        int captacionesTotales,
        int captacionesActivas,
        int captacionesObservadas,
        int oportunidadesActivas,
        int interacciones,
        int visitas,
        int cierres,
        int cierresCohorte,
        int conversionPropia,
        int agentesActivos,
        int brokersActivos,
        int propiedadesEquipo,
        List<String> mesesEtiquetas,
        List<Integer> cierresPorMes,
        List<Integer> conversionPorPeriodo,
        List<Integer> captacionesPorPeriodo,
        List<IndicadorConteoResponse> etapas,
        List<IndicadorConteoResponse> captacionesSalud,
        List<IndicadorEmbudoResponse> embudo,
        List<IndicadorDesempenoResponse> desempeno,
        IndicadorOperativoResponse operativo) {

    public static IndicadoresResponse desde(IndicadorService.Resumen r) {
        return new IndicadoresResponse(
                r.ambito(),
                r.captacionesPorRevisar(),
                r.solicitudesPorEvaluar(),
                r.captacionesTotales(),
                r.captacionesActivas(),
                r.captacionesObservadas(),
                r.oportunidadesActivas(),
                r.interacciones(),
                r.visitas(),
                r.cierres(),
                r.cierresCohorte(),
                r.conversionPropia(),
                r.agentesActivos(),
                r.brokersActivos(),
                r.propiedadesEquipo(),
                r.mesesEtiquetas(),
                r.cierresPorMes(),
                r.conversionPorPeriodo(),
                r.captacionesPorPeriodo(),
                r.etapas().stream().map(IndicadorConteoResponse::desde).toList(),
                r.captacionesSalud().stream().map(IndicadorConteoResponse::desde).toList(),
                r.embudo().stream().map(IndicadorEmbudoResponse::desde).toList(),
                r.desempeno().stream().map(IndicadorDesempenoResponse::desde).toList(),
                IndicadorOperativoResponse.desde(r.operativo()));
    }
}
