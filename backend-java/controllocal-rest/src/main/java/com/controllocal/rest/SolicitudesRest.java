package com.controllocal.rest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.impl.BrokerAgenteDAOImpl;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("solicitudes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SolicitudesRest {

    private static final DateTimeFormatter CODIGO_FORMATO =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final SolicitudAlquilerBusinessLogic solicitudes =
            new SolicitudAlquilerBusinessLogicImpl();
    private final BrokerAgenteDAO brokerAgenteDAO = new BrokerAgenteDAOImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.SolicitudResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        List<SolicitudAlquiler> fuente = solicitudesDelUsuario(SeguridadRest.usuario(request));
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.SolicitudResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.SolicitudResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.SolicitudResponse obtener(@PathParam("id") long id) {
        return Dtos.SolicitudResponse.desde(obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @GET
    @Path("codigo/{codigo}")
    public Dtos.SolicitudResponse obtenerPorCodigo(@PathParam("codigo") String codigo) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        return solicitudesDelUsuario(usuario).stream()
                .filter(item -> item.getCodigoSolicitud() != null
                        && item.getCodigoSolicitud().equalsIgnoreCase(codigo))
                .findFirst()
                .map(Dtos.SolicitudResponse::desde)
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"));
    }

    @POST
    public Response registrar(Dtos.SolicitudRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idOportunidad() == null) {
            throw ApiException.badRequest("Los datos de la solicitud son obligatorios.");
        }
        SolicitudAlquiler solicitud = dto.aEntidad(usuario.idDominio());
        if (solicitud.getCodigoSolicitud() == null || solicitud.getCodigoSolicitud().isBlank()) {
            solicitud.setCodigoSolicitud(generarCodigoSolicitud());
        }
        Long id = solicitudes.registrar(solicitud);
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.SolicitudResponse.desde(
                        solicitudes.buscarPorId(id)
                                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"))))
                .build();
    }

    @POST
    @Path("{id}/reenviar")
    public Dtos.SolicitudResponse reenviarAEvaluacion(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        solicitudes.reenviarAEvaluacion(id);
        return Dtos.SolicitudResponse.desde(
                solicitudes.buscarPorId(id).orElseThrow(() -> ApiException.noEncontrado("Solicitud")));
    }

    private SolicitudAlquiler obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        SolicitudAlquiler solicitud = solicitudes.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"));
        if (!puedeVer(usuario, solicitud)) {
            throw ApiException.prohibido();
        }
        return solicitud;
    }

    private List<SolicitudAlquiler> solicitudesDelUsuario(UsuarioAutenticado usuario) {
        return solicitudes.listarTodos().stream()
                .filter(item -> puedeVer(usuario, item))
                .toList();
    }

    private boolean puedeVer(UsuarioAutenticado usuario, SolicitudAlquiler solicitud) {
        AgenteInmobiliario agente = solicitud.getAgenteResponsable();
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if (usuario.tieneRol("ADMIN")) {
            return true;
        }
        if (usuario.tieneRol("AGENTE")) {
            return usuario.idDominio() == agente.getIdAgente();
        }
        if (usuario.tieneRol("BROKER")) {
            return brokerAgenteDAO.existeAsignacionActiva(usuario.idDominio(), agente.getIdAgente());
        }
        return false;
    }

    private static String generarCodigoSolicitud() {
        return "SOL-" + CODIGO_FORMATO.format(LocalDateTime.now());
    }
}
