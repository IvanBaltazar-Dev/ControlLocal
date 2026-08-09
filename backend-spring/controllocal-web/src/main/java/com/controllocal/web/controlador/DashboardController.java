package com.controllocal.web.controlador;

import com.controllocal.service.IndicadorService;
import com.controllocal.service.TareaService;
import com.controllocal.web.dto.DashboardResponse;
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
 *   <li>{@code tamano} por defecto es <b>5</b>, no 10, y la fuente ya viene
 *       cortada en 10 por el service (regla de F7), asi que {@code totalRecords}
 *       es el tamano de ESA fuente, no el total historico.</li>
 * </ul>
 */
@RestController
@RequestMapping("dashboard")
public class DashboardController {

    private final IndicadorService indicadores;
    private final TareaService tareas;

    public DashboardController(IndicadorService indicadores, TareaService tareas) {
        this.indicadores = indicadores;
        this.tareas = tareas;
    }

    @GetMapping
    public DashboardResponse cargar(@RequestParam(required = false) String periodo,
                                    @RequestParam(defaultValue = "5") int tamano) {
        var actor = SesionActual.actor();
        IndicadoresResponse resumen = IndicadoresResponse.desde(
                indicadores.resumen(periodo, actor));
        int tamanoValido = Math.max(1, Math.min(100, tamano));

        if (!actor.esAgente()) {
            return new DashboardResponse(resumen,
                    new PageResponse<>(List.of(), 0, 1, tamanoValido));
        }
        List<TareaResponse> fuente = tareas.bandejaDe(actor).stream()
                .map(TareaResponse::desde)
                .toList();
        int hasta = Math.min(tamanoValido, fuente.size());
        return new DashboardResponse(resumen, new PageResponse<>(
                fuente.subList(0, hasta), fuente.size(), 1, tamanoValido));
    }
}
