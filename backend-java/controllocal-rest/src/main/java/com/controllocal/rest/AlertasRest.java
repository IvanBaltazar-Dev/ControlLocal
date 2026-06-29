package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.AlertaBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.impl.AlertaBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("alertas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertasRest {

    private final AlertaBusinessLogic alertas = new AlertaBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.AlertaResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("20") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        // Sin planificador: al consultar la campana, materializa las alertas de recontacto
        // vencido (dia 8 sin accion). Best-effort: si falla, la campana igual responde.
        try {
            prospecciones.sincronizarRecontacto();
        } catch (RuntimeException ignored) {
            // no bloquear la campana por el barrido de recontacto
        }
        List<Alerta> fuente = alertasDelUsuario(usuario);
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.AlertaResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.AlertaResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @POST
    @Path("{id}/atender")
    public Dtos.AtenderAlertaResponse atenderPorPost(@PathParam("id") long id) {
        return atender(id);
    }

    @PATCH
    @Path("{id}/atender")
    public Dtos.AtenderAlertaResponse atenderPorPatch(@PathParam("id") long id) {
        return atender(id);
    }

    private Dtos.AtenderAlertaResponse atender(long id) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        boolean visible = alertasDelUsuario(usuario).stream()
                .anyMatch(item -> item.getIdAlerta() != null && item.getIdAlerta() == id);
        if (!visible) {
            throw ApiException.noEncontrado("Alerta");
        }
        return new Dtos.AtenderAlertaResponse(alertas.marcarAtendida(id));
    }

    private List<Alerta> alertasDelUsuario(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("AGENTE")) {
            return alertas.listarActivasPorAgente(usuario.idDominio());
        }
        if (usuario.tieneRol("BROKER")) {
            return alertas.listarActivasPorBroker(usuario.idDominio());
        }
        if (usuario.tieneRol("ADMIN")) {
            return alertas.listarActivas();
        }
        throw ApiException.prohibido();
    }
}
