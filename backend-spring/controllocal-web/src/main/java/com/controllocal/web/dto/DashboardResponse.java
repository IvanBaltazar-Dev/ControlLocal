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
 * <p><b>`bandeja` y `focoDelBroker` no son la misma coleccion vista por dos
 * roles</b> (D-E2-5). La bandeja son las cosas que un AGENTE tiene que hacer y
 * sigue sin acceso de broker ni de admin; el foco del broker son las decisiones
 * que SOLO EL puede tomar. Cada rol ve lo que el tiene que decidir, nunca lo que
 * otro tiene que hacer.
 *
 * <p>`accesos` los decide el DOMINIO, no la pantalla (D-E2-1 §6.1):
 * el agente crea, el broker revisa, decide y reparte. Deducirlos en Angular
 * seria una interpretacion mas en el cliente, y KAIROS necesitaria escribir la
 * suya para ofrecer lo mismo por WhatsApp.
 *
 * <p>`ambito` NO viaja aqui: ya lo publica `indicadores`, y dos campos con el
 * mismo hecho es exactamente la doble verdad que D-E4-3 cerro para los datos
 * de la propiedad. Se corrigio alli, en su unico dueno.
 *
 * <p>La campana (alertas) NO viaja aqui: es chrome global y tiene su propio
 * recurso.
 */
public record DashboardResponse(IndicadoresResponse indicadores,
                                PageResponse<TareaResponse> bandeja,
                                List<HallazgoResponse> hallazgos,
                                List<AsuntoDelBrokerResponse> focoDelBroker,
                                List<AccesoResponse> accesos) {
}
