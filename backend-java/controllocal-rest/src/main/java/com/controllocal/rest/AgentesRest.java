package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.model.usuario.AgenteInmobiliario;
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

@Path("agentes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AgentesRest {

    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.AgenteResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("50") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        return pagina(agentes.listarPorBroker(usuario.idDominio()), pagina, tamano);
    }

    private PageResponse<Dtos.AgenteResponse> pagina(List<AgenteInmobiliario> fuente, int pagina, int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.AgenteResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.AgenteResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }
}
