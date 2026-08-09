package com.controllocal.web.dto;

import com.controllocal.service.SeguimientoComercialService;

import java.util.List;

/**
 * Contrato CONGELADO de {@code SeguimientoComercialRest.SeguimientoComercialPageResponse}.
 *
 * <p>Tres conjuntos distintos en una sola respuesta, y no es redundancia:
 * {@code items} lleva TODOS los filtros y la pagina, {@code counts} lleva todos
 * los filtros MENOS el de proceso (para que los KPI sigan siendo clicables sin
 * perder el contexto) y {@code options} no lleva ninguno (los selects ofrecen
 * todo lo que el actor alcanza).
 */
public record SeguimientoComercialPageResponse(
        List<SeguimientoComercialItemResponse> items,
        long totalRecords,
        int page,
        int pageSize,
        Conteos counts,
        Opciones options) {

    public record Conteos(int todos, int prospeccion, int captacion,
                          int oportunidad, int solicitud, int cierre) {
    }

    public record Opciones(List<String> agentes, List<String> propietarios,
                           List<String> estados, List<String> distritos) {
    }

    public static SeguimientoComercialPageResponse desde(SeguimientoComercialService.Resultado r) {
        return new SeguimientoComercialPageResponse(
                r.items().stream().map(SeguimientoComercialItemResponse::desde).toList(),
                r.totalRecords(),
                r.page(),
                r.pageSize(),
                new Conteos(r.counts().todos(), r.counts().prospeccion(), r.counts().captacion(),
                        r.counts().oportunidad(), r.counts().solicitud(), r.counts().cierre()),
                new Opciones(r.options().agentes(), r.options().propietarios(),
                        r.options().estados(), r.options().distritos()));
    }
}
