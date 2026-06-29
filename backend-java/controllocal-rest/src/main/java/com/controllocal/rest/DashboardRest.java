package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.TareaBusinessLogic;
import com.controllocal.bl.impl.TareaBusinessLogicImpl;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoint agregado del dashboard: una sola llamada devuelve los indicadores (resumen) y la
 * primera pagina de la bandeja del agente, evitando dos round-trips. Reusa el computo de
 * {@link IndicadoresRest} y de {@link TareaBusinessLogic}; el alcance se resuelve por rol igual
 * que en cada endpoint. La campana (alertas) sigue aparte por ser chrome global.
 */
@Path("dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DashboardRest {

    private final IndicadoresRest indicadores = new IndicadoresRest();
    private final TareaBusinessLogic tareas = new TareaBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public DashboardResponse cargar(
            @QueryParam("periodo") String periodo,
            @QueryParam("tamano") @DefaultValue("5") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Dtos.IndicadoresResponse resumen = indicadores.resumen(usuario, periodo);
        int tamanoValido = SeguridadRest.tamano(tamano);

        // Solo el agente tiene bandeja personal; broker/admin la reciben vacia.
        if (usuario.tieneRol("AGENTE")) {
            List<TareasRest.TareaResponse> todas = tareas.bandejaDe(usuario.idDominio()).stream()
                    .map(TareasRest.TareaResponse::desde)
                    .toList();
            int hasta = Math.min(tamanoValido, todas.size());
            PageResponse<TareasRest.TareaResponse> bandeja =
                    new PageResponse<>(todas.subList(0, hasta), todas.size(), 1, tamanoValido);
            return new DashboardResponse(resumen, bandeja);
        }
        return new DashboardResponse(resumen, new PageResponse<>(List.of(), 0, 1, tamanoValido));
    }

    public record DashboardResponse(Dtos.IndicadoresResponse indicadores,
                                    PageResponse<TareasRest.TareaResponse> bandeja) {
    }
}
