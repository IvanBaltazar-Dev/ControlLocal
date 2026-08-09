package com.controllocal.web.dto;

import com.controllocal.web.http.PageResponse;

/**
 * Respuesta agregada del dashboard: los indicadores y la primera pagina de la
 * bandeja en una sola llamada, para no pagar dos round-trips al abrir la home.
 *
 * <p>La campana (alertas) NO viaja aqui: es chrome global y tiene su propio
 * recurso.
 */
public record DashboardResponse(IndicadoresResponse indicadores,
                                PageResponse<TareaResponse> bandeja) {
}
