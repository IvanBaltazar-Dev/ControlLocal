package com.controllocal.rest;

import java.util.List;
import java.util.Locale;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.RateLimiter;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("captaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaptacionesRest {

    private static final RateLimiter LIMITADOR_SENSIBLE = new RateLimiter(30);

    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.CaptacionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        List<Captacion> fuente = captacionesDelUsuario(usuario);
        return pagina(fuente, pagina, tamano);
    }

    @GET
    @Path("pendientes")
    public PageResponse<Dtos.CaptacionResponse> pendientes(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        return pagina(captaciones.listPendingReviews(usuario.idDominio()), pagina, tamano);
    }

    @GET
    @Path("{id}")
    public Dtos.CaptacionResponse obtener(@PathParam("id") long id) {
        return Dtos.CaptacionResponse.desde(obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @POST
    public Response registrar(Dtos.CaptacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la captacion son obligatorios.");
        }

        LocalComercial local = new LocalComercial();
        local.setIdLocal(dto.idLocal());
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());

        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion(dto.codigoCaptacion());
        captacion.setFechaCaptacion(dto.fechaCaptacion());
        captacion.setFechaInicioVigencia(dto.fechaInicioVigencia());
        captacion.setFechaFinVigencia(dto.fechaFinVigencia());
        captacion.setComisionPactada(dto.comisionPactada());
        captacion.setObservaciones(dto.observaciones());
        captacion.setMotivoOperacion(operacionAlquiler(dto.motivoOperacion()));
        captacion.setUrgencia(dto.urgencia());
        captacion.setExclusividad(dto.exclusividad());
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);
        long id = captaciones.registrar(captacion);

        return Response.status(Response.Status.CREATED)
                .entity(Dtos.CaptacionResponse.desde(
                        captaciones.buscarPorId(id).orElseThrow(() -> ApiException.noEncontrado("Captacion"))))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.CaptacionResponse actualizar(@PathParam("id") long id, Dtos.CaptacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la captacion son obligatorios.");
        }

        Captacion captacion = obtenerConAcceso(id, usuario);
        if (captacion.getEstado() != com.controllocal.model.comercial.enums.EstadoCaptacion.PENDIENTE_REVISION
                && captacion.getEstado() != com.controllocal.model.comercial.enums.EstadoCaptacion.OBSERVADA) {
            throw ApiException.badRequest("Solo se puede editar una captacion pendiente u observada.");
        }

        captacion.setFechaCaptacion(dto.fechaCaptacion());
        captacion.setFechaInicioVigencia(dto.fechaInicioVigencia());
        captacion.setFechaFinVigencia(dto.fechaFinVigencia());
        captacion.setComisionPactada(dto.comisionPactada());
        captacion.setObservaciones(dto.observaciones());
        captacion.setMotivoOperacion(operacionAlquiler(dto.motivoOperacion()));
        captacion.setUrgencia(dto.urgencia());
        captacion.setExclusividad(dto.exclusividad());
        captaciones.actualizar(captacion);

        return Dtos.CaptacionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/decision")
    public Dtos.CaptacionResponse decidir(@PathParam("id") long id, Dtos.DecisionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.accion() == null) {
            throw ApiException.badRequest("La decision es obligatoria.");
        }

        String accion = dto.accion().trim().toUpperCase(Locale.ROOT);
        switch (accion) {
            case "APROBAR", "A" -> captaciones.aprobarCaptacion(id, usuario.idDominio(), dto.observacion());
            case "OBSERVAR", "O" -> captaciones.observarCaptacion(id, usuario.idDominio(), dto.observacion());
            case "RECHAZAR", "R" -> captaciones.rechazarCaptacion(id, usuario.idDominio(), dto.observacion());
            default -> throw ApiException.badRequest("Decision no valida.");
        }
        return Dtos.CaptacionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/reasignar")
    public Dtos.CaptacionResponse reasignar(@PathParam("id") long id, Dtos.ReasignacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.idAgenteNuevo() == null) {
            throw ApiException.badRequest("El agente destino es obligatorio.");
        }
        captaciones.reasignarCaptacion(id, dto.idAgenteNuevo(), usuario.idDominio(), dto.motivo());
        return Dtos.CaptacionResponse.desde(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/cierre")
    public Dtos.CaptacionResponse cerrar(@PathParam("id") long id, Dtos.CierreRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("El motivo de cierre es obligatorio.");
        }
        captaciones.cerrarCaptacion(id, usuario.idDominio(), dto.motivo());
        return Dtos.CaptacionResponse.desde(obtenerConAcceso(id, usuario));
    }

    private Captacion obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        Captacion captacion = captaciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        boolean permitido = captacionesDelUsuario(usuario).stream()
                .anyMatch(item -> item.getIdCaptacion().equals(captacion.getIdCaptacion()));
        if (!permitido) {
            throw ApiException.prohibido();
        }
        return captacion;
    }

    private List<Captacion> captacionesDelUsuario(UsuarioAutenticado usuario) {
        if ("AGENTE".equals(usuario.rol())) {
            return captaciones.listarPorAgente(usuario.idDominio());
        }
        if (usuario.tieneRol("BROKER", "ADMIN")) {
            return captaciones.listarPorBroker(usuario.idDominio());
        }
        throw ApiException.prohibido();
    }

    private PageResponse<Dtos.CaptacionResponse> pagina(List<Captacion> fuente, int pagina, int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.CaptacionResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.CaptacionResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    private void limitar() {
        String clave = request.getRemoteAddr() + ":" + request.getRequestURI();
        if (!LIMITADOR_SENSIBLE.permitir(clave)) {
            throw ApiException.demasiadasSolicitudes();
        }
    }

    private com.controllocal.model.comercial.enums.OperacionRequerimiento operacionAlquiler(String codigo) {
        if (codigo != null && !codigo.isBlank() && !"A".equals(codigo)) {
            throw ApiException.badRequest("ControlLocal solo admite operaciones de alquiler comercial.");
        }
        return com.controllocal.model.comercial.enums.OperacionRequerimiento.ALQUILER;
    }
}
