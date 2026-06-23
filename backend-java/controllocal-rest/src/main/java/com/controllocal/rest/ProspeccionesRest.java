package com.controllocal.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.BrokerAgente;
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

@Path("prospecciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProspeccionesRest {

    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.ProspeccionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        return pagina(prospeccionesDelUsuario(SeguridadRest.usuario(request)), pagina, tamano);
    }

    @GET
    @Path("recontactar")
    public PageResponse<Dtos.ProspeccionResponse> recontactar(
            @QueryParam("dias") @DefaultValue("15") int dias,
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Set<Long> permitidas = prospeccionesDelUsuario(usuario).stream()
                .map(Prospeccion::getIdProspeccion)
                .collect(Collectors.toSet());
        List<Prospeccion> fuente = prospecciones.listarPorRecontactar(dias).stream()
                .filter(item -> permitidas.contains(item.getIdProspeccion()))
                .toList();
        return pagina(fuente, pagina, tamano);
    }

    @GET
    @Path("{id}")
    public Dtos.ProspeccionResponse obtener(@PathParam("id") long id) {
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @POST
    public Response registrar(Dtos.ProspeccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idLocal() == null || dto.idLocal() <= 0) {
            throw ApiException.badRequest("El local de la prospeccion es obligatorio.");
        }

        Prospeccion prospeccion = new Prospeccion();
        LocalComercial local = new LocalComercial();
        local.setIdLocal(dto.idLocal());
        prospeccion.setLocalComercial(local);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());
        prospeccion.setAgenteResponsable(agente);
        prospeccion.setObservaciones(dto.observaciones());

        long id = prospecciones.registrar(prospeccion);
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario)))
                .build();
    }

    @POST
    @Path("{id}/contactar")
    public Dtos.ProspeccionResponse contactar(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.contactar(id);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/reunion")
    public Dtos.ProspeccionResponse registrarReunion(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.registrarReunion(id);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/propuesta")
    public Dtos.ProspeccionResponse entregarPropuesta(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.entregarPropuesta(id);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/recontactar")
    public Dtos.ProspeccionResponse recontactar(@PathParam("id") long id, Dtos.RecontactoRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("La fecha de recontacto es obligatoria.");
        }
        prospecciones.posponer(id, dto.fechaRecontacto());
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/rechazar")
    public Dtos.ProspeccionResponse rechazar(@PathParam("id") long id, Dtos.RechazoProspeccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.rechazar(id, dto != null ? dto.motivo() : null);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/descartar")
    public Dtos.ProspeccionResponse descartar(@PathParam("id") long id, Dtos.RechazoProspeccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.descartar(id, dto != null ? dto.motivo() : null);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/captar")
    public Dtos.ProspeccionResponse captar(@PathParam("id") long id, Dtos.CaptarProspeccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("La comision pactada es obligatoria.");
        }
        prospecciones.captar(id, dto.comisionPactada());
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/marcar-captado")
    public Dtos.ProspeccionResponse marcarCaptado(
            @PathParam("id") long id,
            Dtos.MarcarProspeccionCaptadaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        Prospeccion prospeccion = obtenerConAcceso(id, usuario);
        Captacion captacion = captacionCreadaPorAgente(usuario, dto);
        Long idLocalProspeccion = prospeccion.getLocalComercial() != null
                ? prospeccion.getLocalComercial().getIdLocal()
                : null;
        Long idLocalCaptacion = captacion.getLocalComercial() != null
                ? captacion.getLocalComercial().getIdLocal()
                : null;
        if (idLocalProspeccion == null || !idLocalProspeccion.equals(idLocalCaptacion)) {
            throw ApiException.badRequest("La captacion no corresponde al local de la prospeccion.");
        }

        prospeccion.aceptarPropuesta();
        prospeccion.setCaptacion(captacion);
        prospecciones.actualizar(prospeccion);
        return Dtos.ProspeccionResponse.desde(obtenerConAcceso(id, usuario));
    }

    private Prospeccion obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        Prospeccion prospeccion = prospecciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Prospeccion"));
        boolean permitido = prospeccionesDelUsuario(usuario).stream()
                .anyMatch(item -> item.getIdProspeccion().equals(prospeccion.getIdProspeccion()));
        if (!permitido) {
            throw ApiException.prohibido();
        }
        return prospeccion;
    }

    private List<Prospeccion> prospeccionesDelUsuario(UsuarioAutenticado usuario) {
        List<Prospeccion> todas = prospecciones.listarTodos();
        if ("AGENTE".equals(usuario.rol())) {
            return todas.stream()
                    .filter(item -> item.getAgenteResponsable() != null
                            && usuario.idDominio() == item.getAgenteResponsable().getIdAgente())
                    .toList();
        }
        if ("ADMIN".equals(usuario.rol())) {
            return todas;
        }
        if ("BROKER".equals(usuario.rol())) {
            Set<Long> agentesSupervisados = brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                    .map(BrokerAgente::getIdAgente)
                    .collect(Collectors.toSet());
            return todas.stream()
                    .filter(item -> item.getAgenteResponsable() != null
                            && agentesSupervisados.contains(item.getAgenteResponsable().getIdAgente()))
                    .toList();
        }
        throw ApiException.prohibido();
    }

    private Captacion captacionCreadaPorAgente(
            UsuarioAutenticado usuario,
            Dtos.MarcarProspeccionCaptadaRequest dto) {
        if (dto == null || (dto.idCaptacion() == null && (dto.codigoCaptacion() == null || dto.codigoCaptacion().isBlank()))) {
            throw ApiException.badRequest("La captacion creada es obligatoria.");
        }
        return captaciones.listarPorAgente(usuario.idDominio()).stream()
                .filter(item -> dto.idCaptacion() != null
                        ? dto.idCaptacion().equals(item.getIdCaptacion())
                        : dto.codigoCaptacion().equalsIgnoreCase(item.getCodigoCaptacion()))
                .findFirst()
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
    }

    private PageResponse<Dtos.ProspeccionResponse> pagina(List<Prospeccion> fuente, int pagina, int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.ProspeccionResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.ProspeccionResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }
}
