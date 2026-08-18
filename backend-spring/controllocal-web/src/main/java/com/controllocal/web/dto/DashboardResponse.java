package com.controllocal.web.dto;

import com.controllocal.web.http.PageResponse;

import java.util.List;

/**
 * Respuesta agregada del dashboard: los indicadores, la primera pagina de la
 * bandeja y los hallazgos, en una sola llamada, para no pagar tres round-trips
 * al abrir la home.
 *
 * <p><b>`bandeja` y `hallazgos` son dos colecciones y no una filtrada</b>
 * (E2.3). Una tarea dice "hay algo que debes resolver"; un hallazgo dice
 * "encontre algo que vale la pena mirar". Mientras viajaron juntas, la
 * coincidencia de cartera competia por los cinco puestos del foco -- y los
 * ganaba, porque la politica de despacho la trata como ocasion, que lo es.
 *
 * <p>La campana (alertas) NO viaja aqui: es chrome global y tiene su propio
 * recurso.
 */
public record DashboardResponse(IndicadoresResponse indicadores,
                                PageResponse<TareaResponse> bandeja,
                                List<HallazgoResponse> hallazgos) {
}
