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
 *
 * <p>`senales` se anadio en E1 (2026-08-10): son los mismos numeros que ya
 * viajaban, pero <b>clasificados por el dominio</b> y ordenados por lo que urge
 * primero. Ver {@link IndicadorSenalResponse}.
 *
 * <p>`rendimiento` se anadio en E2.6 (2026-08-19) y <b>mide contra otro
 * periodo</b>: los campos de arriba salen de la ventana movil del parametro
 * {@code periodo} (7d/15d/1m/3m/1y), y este de un <b>mes de calendario</b>. No es
 * una inconsistencia sino la correccion de una: {@code metaEsperadaAHoy} sobre
 * una ventana movil es tautologica —los dias transcurridos serian siempre los
 * totales— y el semaforo no significaria nada. Dos semanticas distintas, dos
 * nombres distintos.
 *
 * <p>Ahi vive tambien `generadoEn`, y es su <b>unico</b> productor: el Inicio lo
 * lee de aqui en vez de emitir el suyo, igual que hace con `ambito`.
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
        // El unico numerico nulable de la respuesta (E2.0): `null` = no hubo
        // captaciones en el periodo, asi que no hay tasa. Ver IndicadorService.
        Integer conversionPropia,
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
        IndicadorOperativoResponse operativo,
        List<IndicadorSenalResponse> senales,
        int pendientesDeAtencion,
        RendimientoResponse rendimiento) {

    public static IndicadoresResponse desde(IndicadorService.Resumen r,
                                            RendimientoResponse rendimiento) {
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
                IndicadorOperativoResponse.desde(r.operativo()),
                r.senales().stream().map(IndicadorSenalResponse::desde).toList(),
                r.pendientesDeAtencion(),
                rendimiento);
    }
}
