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
            @QueryParam("idCliente") Long idCliente) {
        List<OportunidadComercial> fuente = oportunidadesDelUsuario(SeguridadRest.usuario(request)).stream()
                .filter(item -> idCaptacion == null
                        || (item.getCaptacion() != null && idCaptacion.equals(item.getCaptacion().getIdCaptacion())))
                .filter(item -> idCliente == null
                        || (item.getClienteInteresado() != null
                                && idCliente.equals(item.getClienteInteresado().getIdCliente())))
                .toList();
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.OportunidadResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.OportunidadResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
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
        boolean permitido = oportunidadesDelUsuario(usuario).stream()
                .anyMatch(item -> item.getIdOportunidad().equals(oportunidad.getIdOportunidad()));
        if (!permitido) {
            throw ApiException.prohibido();
        }
        return oportunidad;
    }

    private List<OportunidadComercial> oportunidadesDelUsuario(UsuarioAutenticado usuario) {
        // Alcance acotado en SQL (no se escanea toda la tabla):
        // - AGENTE: sus propias oportunidades.
        // - BROKER: las de las captaciones que supervisa (semantica por captacion).
        // - ADMIN: todas (gobierno).
        if ("AGENTE".equals(usuario.rol())) {
            return oportunidades.listarPorAgentes(List.of(usuario.idDominio()));
        }
        if ("ADMIN".equals(usuario.rol())) {
            return oportunidades.listarTodos();
        }
        if ("BROKER".equals(usuario.rol())) {
            List<Long> permitidas = captaciones.listarPorBroker(usuario.idDominio()).stream()
                    .map(Captacion::getIdCaptacion)
                    .toList();
            return oportunidades.listarPorCaptaciones(permitidas);
        }
        throw ApiException.prohibido();
    }
}
