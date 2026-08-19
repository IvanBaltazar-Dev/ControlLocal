package com.controllocal.web.controlador;

import com.controllocal.service.FocoDelBrokerService;
import com.controllocal.service.HallazgoService;
import com.controllocal.service.IndicadorService;
import com.controllocal.service.TareaService;
import com.controllocal.web.dto.AsuntoDelBrokerResponse;
import com.controllocal.web.dto.DashboardResponse;
import com.controllocal.web.dto.HallazgoResponse;
import com.controllocal.web.dto.IndicadoresResponse;
import com.controllocal.web.dto.TareaResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrato CONGELADO E4 del {@code DashboardRest} Jakarta: una sola llamada
 * devuelve el resumen de indicadores y la primera pagina de la bandeja, para no
 * pagar dos round-trips al abrir la home.
 *
 * <p><b>No tiene logica propia</b> —compone dos casos de uso ya cortados—, y
 * por eso vive en la web: es exactamente lo que hace la v1, donde
 * {@code DashboardRest} instancia {@code IndicadoresRest} y la BL de tareas.
 *
 * <p>Dos formas del cable faciles de perder:
 * <ul>
 *   <li><b>solo el AGENTE tiene bandeja</b>. Para BROKER y ADMIN no es un 403:
 *       es una bandeja <em>vacia</em> con el {@code pageSize} que se pidio;</li>
 *   <li>{@code tamano} por defecto es <b>5</b>, y desde que se retiro el tope
 *       de F7 (2026-08-08) {@code totalRecords} es el <b>total real</b> de
 *       tareas abiertas del agente, no el tamano de una fuente recortada. La
 *       home compone las 5 primeras y el resto se pide con {@code GET /tareas}.</li>
 * </ul>
 */
@RestController
@RequestMapping("dashboard")
public class DashboardController {

    private final IndicadorService indicadores;
    private final TareaService tareas;
    private final HallazgoService hallazgos;
    private final FocoDelBrokerService focoDelBroker;

    public DashboardController(IndicadorService indicadores, TareaService tareas,
                               HallazgoService hallazgos,
                               FocoDelBrokerService focoDelBroker) {
        this.indicadores = indicadores;
        this.tareas = tareas;
        this.hallazgos = hallazgos;
        this.focoDelBroker = focoDelBroker;
    }

    @GetMapping
    public DashboardResponse cargar(@RequestParam(required = false) String periodo,
                                    @RequestParam(defaultValue = "5") int tamano) {
        var actor = SesionActual.actor();
        IndicadoresResponse resumen = IndicadoresResponse.desde(
                indicadores.resumen(periodo, actor));
        int tamanoValido = Math.max(1, Math.min(100, tamano));

        if (!actor.esAgente()) {
            // El broker no recibe bandeja -- esa es del agente y no se comparte --
            // sino SUS asuntos: lo que solo el puede decidir (D-E2-5).
            List<AsuntoDelBrokerResponse> suyos = focoDelBroker.de(actor).stream()
                    .map(AsuntoDelBrokerResponse::desde)
                    .toList();
            // El broker TAMBIEN tiene hallazgos, y son de otra naturaleza: no
            // coincidencias de cartera sino concentracion del equipo (D-E2-1
            // seccion 7.1). Devolver aqui una lista vacia era dejarlo sin
            // hallazgo -- justo el rol al que mas le sirve que BROX encuentre
            // algo que la media esconde.
            List<HallazgoResponse> descubiertos = hallazgos.de(actor).stream()
                    .map(HallazgoResponse::desde)
                    .toList();
            return new DashboardResponse(resumen,
                    new PageResponse<>(List.of(), 0, 1, tamanoValido), descubiertos, suyos);
        }
        List<TareaResponse> fuente = tareas.bandejaDe(actor).stream()
                .map(TareaResponse::desde)
                .toList();
        int hasta = Math.min(tamanoValido, fuente.size());

        // Los hallazgos NO se recortan con `tamano`: ese parametro pagina la
        // bandeja, y aplicarselo tambien seria hacer que dos colecciones
        // distintas compartieran un tope pensado para una sola.
        List<HallazgoResponse> descubiertos = hallazgos.de(actor).stream()
                .map(HallazgoResponse::desde)
                .toList();

        return new DashboardResponse(resumen, new PageResponse<>(
                fuente.subList(0, hasta), fuente.size(), 1, tamanoValido), descubiertos,
                List.of());
    }
}
