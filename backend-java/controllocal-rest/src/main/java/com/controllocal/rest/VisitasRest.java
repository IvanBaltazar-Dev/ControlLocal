package com.controllocal.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.VisitaBusinessLogic;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.VisitaBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.comercial.enums.MotivoNoContinuidadTipo;
import com.controllocal.model.comercial.enums.ObjecionVisita;
import com.controllocal.model.comercial.enums.OpinionPrecio;
import com.controllocal.model.comercial.enums.ProximaAccionVisita;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.usuario.AgenteInmobiliario;
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
import jakarta.ws.rs.core.Response;

@Path("visitas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VisitasRest {

    private final VisitaBusinessLogic visitas = new VisitaBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades =
            new OportunidadComercialBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.VisitaResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        List<Visita> fuente = visitasDelUsuario(SeguridadRest.usuario(request));
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.VisitaResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.VisitaResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.VisitaResponse obtener(@PathParam("id") long id) {
        return Dtos.VisitaResponse.desde(obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @POST
    public Response programar(Dtos.VisitaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idOportunidad() == null) {
            throw ApiException.badRequest("Los datos de la visita son obligatorios.");
        }
        OportunidadComercial oportunidad = oportunidadConAcceso(dto.idOportunidad(), usuario);

        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());
        Visita visita = new Visita();
        visita.setFechaVisita(dto.fechaVisita());
        visita.setHoraVisita(dto.horaVisita());
        visita.setObservaciones(dto.observaciones());
        visita.setOportunidadComercial(oportunidad);
        visita.setAgenteResponsable(agente);
        visita.programar();
        long id = visitas.registrar(visita);
        visita.setIdVisita(id);

        // El alta ya quedo persistida. Si el refetch (SELECT con varios JOIN) fallara por
        // datos opcionales faltantes, devolvemos la entidad recien creada en lugar de propagar
        // un 500 que en el frontend se veria como un alta que "no persiste".
        Visita persistida = visita;
        try {
            persistida = visitas.buscarPorId(id).orElse(visita);
        } catch (RuntimeException refetchError) {
            persistida = visita;
        }

        return Response.status(Response.Status.CREATED)
                .entity(Dtos.VisitaResponse.desde(persistida))
                .build();
    }

    @PATCH
    @Path("{id}/reprogramar")
    public Dtos.VisitaResponse reprogramar(
            @PathParam("id") long id,
            Dtos.ReprogramarVisitaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("La nueva fecha y hora son obligatorias.");
        }
        visitas.reprogramar(id, dto.fechaVisita(), dto.horaVisita());
        return respuestaActualizada(id);
    }

    @PATCH
    @Path("{id}/cancelar")
    public Dtos.VisitaResponse cancelar(
            @PathParam("id") long id,
            Dtos.CancelarVisitaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("El motivo de cancelacion es obligatorio.");
        }
        visitas.cancelar(id, dto.motivo());
        return respuestaActualizada(id);
    }

    @PATCH
    @Path("{id}/realizar")
    public Dtos.VisitaResponse marcarRealizada(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        visitas.marcarRealizada(id);
        return respuestaActualizada(id);
    }

    @PATCH
    @Path("{id}/no-realizada")
    public Dtos.VisitaResponse marcarNoRealizada(
            @PathParam("id") long id,
            Dtos.NoRealizadaVisitaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.motivo() == null || dto.motivo().isBlank()) {
            throw ApiException.badRequest("El motivo de la visita no realizada es obligatorio.");
        }
        visitas.marcarNoRealizada(id, dto.motivo());
        return respuestaActualizada(id);
    }

    @PATCH
    @Path("{id}/resultado")
    public Dtos.VisitaResponse registrarResultado(
            @PathParam("id") long id,
            Dtos.ResultadoVisitaRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.resultado() == null || dto.resultado().isBlank()) {
            throw ApiException.badRequest("El resultado de la visita es obligatorio.");
        }
        visitas.registrarResultado(
                id,
                ResultadoInteraccion.fromCodigo(dto.resultado()),
                dto.observaciones(),
                enumOpcional(MotivoNoContinuidadTipo.class, dto.razonNoContinuidad()),
                dto.nivelInteres(),
                enumOpcional(ObjecionVisita.class, dto.objecionPrincipal()),
                enumOpcional(OpinionPrecio.class, dto.opinionPrecio()),
                enumOpcional(ProximaAccionVisita.class, dto.proximaAccion()));
        return respuestaActualizada(id);
    }

    private Dtos.VisitaResponse respuestaActualizada(long id) {
        return Dtos.VisitaResponse.desde(
                visitas.buscarPorId(id).orElseThrow(() -> ApiException.noEncontrado("Visita")));
    }

    private Visita obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        Visita visita = visitas.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Visita"));
        boolean permitido = visitasDelUsuario(usuario).stream()
                .anyMatch(item -> item.getIdVisita().equals(visita.getIdVisita()));
        if (!permitido) {
            throw ApiException.prohibido();
        }
        return visita;
    }

    private OportunidadComercial oportunidadConAcceso(long id, UsuarioAutenticado usuario) {
        OportunidadComercial oportunidad = oportunidades.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Oportunidad"));
        if (oportunidad.getAgenteResponsable() == null
                || usuario.idDominio() != oportunidad.getAgenteResponsable().getIdAgente()) {
            throw ApiException.prohibido();
        }
        return oportunidad;
    }

    private List<Visita> visitasDelUsuario(UsuarioAutenticado usuario) {
        // Alcance acotado en SQL (no se escanea toda la tabla):
        // - AGENTE: sus propias visitas.
        // - ADMIN: todas (para consultar el equipo de cualquier broker desde su ficha).
        // - BROKER: las de las captaciones que supervisa (semantica por captacion).
        if ("AGENTE".equals(usuario.rol())) {
            return visitas.listarPorAgentes(List.of(usuario.idDominio()));
        }
        if (usuario.tieneRol("ADMIN")) {
            return visitas.listarTodos();
        }
        if (usuario.tieneRol("BROKER")) {
            List<Long> permitidas = captaciones.listarPorBroker(usuario.idDominio()).stream()
                    .map(Captacion::getIdCaptacion)
                    .toList();
            return visitas.listarPorCaptaciones(permitidas);
        }
        throw ApiException.prohibido();
    }

    private static <E extends Enum<E> & CodigoEnum> E enumOpcional(Class<E> tipo, String codigo) {
        return codigo == null || codigo.isBlank() ? null : CodigoEnum.fromCodigo(tipo, codigo);
    }
}
