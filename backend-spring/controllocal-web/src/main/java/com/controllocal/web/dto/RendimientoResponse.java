package com.controllocal.web.dto;

import com.controllocal.service.RendimientoComercialService;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * El rendimiento comercial del mes en el cable: los cuatro KPI canonicos con su
 * meta y su ritmo, lo que puede cerrarse y el pulso del equipo.
 *
 * <p><b>{@code generadoEn} tiene un solo productor en todo el sistema, y es
 * este.</b> El Inicio lo lee de aqui —viaja dentro de {@code indicadores}, igual
 * que {@code ambito}— en vez de emitir el suyo. Dos campos con el mismo hecho es
 * la doble verdad que D-E4-3 cerro para los datos de la propiedad, y la razon
 * por la que el «hace 2 min» del Inicio no puede discrepar del corte con el que
 * se calcularon los KPI.
 *
 * <p>{@code pulso} es {@code null} para un agente. Su pulso seria su propio
 * ritmo contado otra vez, y la instruccion 14 de D-E2-2 prohibe repetir el mismo
 * diagnostico en dos sitios de la misma pantalla.
 */
public record RendimientoResponse(PeriodoResponse periodo,
                                  OffsetDateTime generadoEn,
                                  List<KpiResponse> kpis,
                                  CierrePosibleResponse puedeCerrarse,
                                  PulsoResponse pulso) {

    public static RendimientoResponse desde(RendimientoComercialService.Rendimiento r) {
        return new RendimientoResponse(
                PeriodoResponse.desde(r.periodo()),
                r.generadoEn(),
                r.kpis().stream().map(KpiResponse::desde).toList(),
                CierrePosibleResponse.desde(r.cierrePosible()),
                PulsoResponse.desde(r.pulso()));
    }
}
