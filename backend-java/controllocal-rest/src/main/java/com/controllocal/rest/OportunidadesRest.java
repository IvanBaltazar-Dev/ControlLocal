package com.controllocal.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.MotivoNoContinuidadBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.MotivoNoContinuidadBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.enums.MotivoNoContinuidadTipo;
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

@Path("oportunidades")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OportunidadesRest {

    private final OportunidadComercialBusinessLogic oportunidades =
            new OportunidadComercialBusinessLogicImpl();
    private final OportunidadComercialDAOImpl oportunidadesDao =
            new OportunidadComercialDAOImpl();
    private final MotivoNoContinuidadBusinessLogic motivos =
            new MotivoNoContinuidadBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.OportunidadResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano,
            @QueryParam("idCaptacion") Long idCaptacion,
            @QueryParam("idCliente") Long idCliente,
            @QueryParam("query") String query) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        Listado<OportunidadComercial> listado = oportunidadesDelUsuarioPagina(
                SeguridadRest.usuario(request), paginaValida, tamanoValido, idCaptacion, idCliente, query);
        List<Dtos.OportunidadResponse> items = listado.items().stream()
                .map(Dtos.OportunidadResponse::desde)
                .toList();
        return new PageResponse<>(items, listado.total(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.OportunidadResponse obtener(@PathParam("id") long id) {
        return Dtos.OportunidadResponse.desde(
                obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @POST
    public Response registrar(Dtos.OportunidadRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la oportunidad son obligatorios.");
        }
        if (dto.idCliente() == null || dto.idCliente() <= 0) {
            throw ApiException.badRequest("Selecciona un cliente interesado.");
        }
        if (dto.idCaptacion() == null || dto.idCaptacion() <= 0) {
            throw ApiException.badRequest("Selecciona una captacion activa.");
        }
        boolean captacionDelAgente = captaciones.listarPorAgente(usuario.idDominio()).stream()
                .anyMatch(item -> item.getIdCaptacion().equals(dto.idCaptacion()));
        if (!captacionDelAgente) {
            throw ApiException.prohibido();
        }

        long id = oportunidades.registrar(dto.aEntidad(usuario.idDominio()));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.OportunidadResponse.desde(
                        oportunidades.buscarPorId(id)
                                .orElseThrow(() -> ApiException.noEncontrado("Oportunidad"))))
                .build();
    }

    @POST
    @Path("{id}/no-continuidad")
    public Dtos.OportunidadResponse cerrarNoContinuidad(
            @PathParam("id") long id,
            Dtos.NoContinuidadRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        OportunidadComercial oportunidad = obtenerConAcceso(id, usuario);
        if (dto == null || dto.razon() == null || dto.razon().isBlank()) {
            throw ApiException.badRequest("El motivo de no continuidad es obligatorio.");
        }

        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setRazonPrincipal(MotivoNoContinuidadTipo.fromCodigo(dto.razon()));
        motivo.setObservaciones(dto.observaciones());
        motivo.setOportunidadComercial(oportunidad);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());
        motivo.setAgenteResponsable(agente);
        motivos.registrar(motivo);

        return Dtos.OportunidadResponse.desde(
                oportunidades.buscarPorId(id)
                        .orElseThrow(() -> ApiException.noEncontrado("Oportunidad")));
    }

    @POST
    @Path("{id}/cierre-exitoso")
    public Dtos.OportunidadResponse cerrarExitoso(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        throw ApiException.badRequest(
                "El cierre exitoso se registra desde la solicitud aprobada para crear el contrato de alquiler.");
    }

    private OportunidadComercial obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        OportunidadComercial oportunidad = oportunidades.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Oportunidad"));
        if (!oportunidadEnAlcance(oportunidad, usuario)) {
            throw ApiException.prohibido();
        }
        return oportunidad;
    }

    private Listado<OportunidadComercial> oportunidadesDelUsuarioPagina(
            UsuarioAutenticado usuario,
            int pagina,
            int tamano,
            Long idCaptacion,
            Long idCliente,
            String query) {
        int offset = (pagina - 1) * tamano;
        if ("AGENTE".equals(usuario.rol())) {
            List<Long> agentes = List.of(usuario.idDominio());
            return new Listado<>(
                    oportunidadesDao.listarPagina(agentes, null, idCaptacion, idCliente, query, offset, tamano),
                    oportunidadesDao.contar(agentes, null, idCaptacion, idCliente, query));
        }
        if ("ADMIN".equals(usuario.rol())) {
            return new Listado<>(
                    oportunidadesDao.listarPagina(null, null, idCaptacion, idCliente, query, offset, tamano),
                    oportunidadesDao.contar(null, null, idCaptacion, idCliente, query));
        }
        if ("BROKER".equals(usuario.rol())) {
            List<Long> permitidas = captaciones.listarPorBroker(usuario.idDominio()).stream()
                    .map(Captacion::getIdCaptacion)
                    .toList();
            return new Listado<>(
                    oportunidadesDao.listarPagina(null, permitidas, idCaptacion, idCliente, query, offset, tamano),
                    oportunidadesDao.contar(null, permitidas, idCaptacion, idCliente, query));
        }
        throw ApiException.prohibido();
    }

    private boolean oportunidadEnAlcance(OportunidadComercial oportunidad, UsuarioAutenticado usuario) {
        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }
        if ("AGENTE".equals(usuario.rol())) {
            return oportunidad.getAgenteResponsable() != null
                    && Long.valueOf(usuario.idDominio())
                            .equals(oportunidad.getAgenteResponsable().getIdAgente());
        }
        if ("BROKER".equals(usuario.rol())) {
            Long idCaptacion = oportunidad.getCaptacion() != null
                    ? oportunidad.getCaptacion().getIdCaptacion()
                    : null;
            return idCaptacion != null
                    && captaciones.listarPorBroker(usuario.idDominio()).stream()
                            .anyMatch(item -> item.getIdCaptacion().equals(idCaptacion));
        }
        return false;
    }

    private record Listado<T>(List<T> items, long total) {
    }
}
