package com.controllocal.web.controlador;

import com.controllocal.service.RendimientoComercialService;
import com.controllocal.web.dto.AccesoResponse;
import com.controllocal.web.dto.AsuntoDelBrokerResponse;
import com.controllocal.web.dto.DashboardResponse;
import com.controllocal.web.dto.HallazgoResponse;
import com.controllocal.web.dto.IndicadoresResponse;
import com.controllocal.web.dto.RendimientoResponse;
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

    private final com.controllocal.service.FocoService focos;
    private final RendimientoComercialService rendimiento;

    public DashboardController(com.controllocal.service.FocoService focos,
                               RendimientoComercialService rendimiento) {
        this.focos = focos;
        this.rendimiento = rendimiento;
    }

    @GetMapping
    @com.controllocal.web.capacidades.CapacidadReconocida({"que_resolver_hoy"})
    public DashboardResponse cargar(@RequestParam(required = false) String periodo,
                                    @RequestParam(required = false) String mes,
                                    @RequestParam(defaultValue = "5") int tamano,
                                    @org.springframework.web.bind.annotation.ModelAttribute com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta) {
        var actor = SesionActual.actor();
        var foco = focos.de(consulta,periodo, actor);
        IndicadoresResponse resumen = IndicadoresResponse.desde(foco.indicadores(),
                RendimientoResponse.desde(rendimiento.del(mes, actor)));
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        List<TareaResponse> fuente = foco.bandeja().stream().map(TareaResponse::desde).toList();
        int hasta = Math.min(tamanoValido, fuente.size());
        return new DashboardResponse(resumen,
                new PageResponse<>(fuente.subList(0, hasta), fuente.size(), 1, tamanoValido),
                foco.hallazgos().stream().map(HallazgoResponse::desde).toList(),
                foco.asuntosDelBroker().stream().map(AsuntoDelBrokerResponse::desde).toList(),
                AccesoResponse.desde(actor),foco.actuaciones());
    }
}
