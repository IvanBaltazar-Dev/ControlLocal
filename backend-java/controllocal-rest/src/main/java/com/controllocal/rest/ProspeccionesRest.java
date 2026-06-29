package com.controllocal.rest;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
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
    private final CoincidenciaCarteraSupport coincidencias = new CoincidenciaCarteraSupport();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.ProspeccionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano,
            @QueryParam("estado") String estado,
            @QueryParam("distrito") String distrito,
            @QueryParam("idCaptacion") Long idCaptacion,
            @QueryParam("idLocal") Long idLocal,
            @QueryParam("idAgente") Long idAgente,
            @QueryParam("idBrokerSupervisor") Long idBrokerSupervisor,
            @QueryParam("q") String query) {
        // null = sin filtro de broker; en otro caso, los agentes que supervisa ese broker.
        Set<Long> agentesDelBroker = agentesDeBroker(idBrokerSupervisor);
        List<Prospeccion> fuente = prospeccionesDelUsuario(SeguridadRest.usuario(request)).stream()
                .filter(item -> coincideEstado(item, estado))
                .filter(item -> coincideDistrito(item, distrito))
                .filter(item -> coincideCaptacion(item, idCaptacion))
                .filter(item -> coincideLocal(item, idLocal))
                .filter(item -> coincideAgente(item, idAgente))
                .filter(item -> coincideBrokerSupervisor(item, agentesDelBroker))
                .filter(item -> coincideBusqueda(item, query))
                .toList();
        return pagina(fuente, pagina, tamano);
    }

    // Agentes supervisados por el broker indicado; null cuando no se filtra por broker.
    private Set<Long> agentesDeBroker(Long idBrokerSupervisor) {
        if (idBrokerSupervisor == null || idBrokerSupervisor <= 0) {
            return null;
        }
        return brokers.listarAgentesSupervisados(idBrokerSupervisor).stream()
                .map(BrokerAgente::getIdAgente)
                .collect(Collectors.toSet());
    }

    private static boolean coincideAgente(Prospeccion p, Long idAgente) {
        if (idAgente == null || idAgente <= 0) {
            return true;
        }
        return p.getAgenteResponsable() != null
                && idAgente.equals(p.getAgenteResponsable().getIdAgente());
    }

    private static boolean coincideBrokerSupervisor(Prospeccion p, Set<Long> agentesDelBroker) {
        if (agentesDelBroker == null) {
            return true;
        }
        return p.getAgenteResponsable() != null
                && agentesDelBroker.contains(p.getAgenteResponsable().getIdAgente());
    }

    @GET
    @Path("recontactar")
    public PageResponse<Dtos.ProspeccionResponse> recontactar(
            @QueryParam("dias") @DefaultValue("7") int dias,
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

    @GET
    @Path("{id}/coincidencias")
    public CoincidenciaCarteraSupport.CoincidenciasResponse coincidencias(
            @PathParam("id") long id,
            @QueryParam("page") Integer page,
            @QueryParam("pagina") Integer pagina,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        return coincidencias.clientesParaProspeccion(
                id,
                SeguridadRest.usuario(request),
                page != null ? page : pagina != null ? pagina : 1,
                pageSize != null ? pageSize : tamano != null ? tamano : 6);
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

    // Etapa 4: una accion de seguimiento (un clic) reinicia el reloj de recontacto a 7 dias
    // y atiende la alerta SIN_RESPUESTA activa. Reemplaza el agendar manual de fecha.
    @POST
    @Path("{id}/seguimiento")
    public Dtos.ProspeccionResponse registrarSeguimiento(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        prospecciones.registrarSeguimiento(id);
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
        // Alcance acotado en SQL (no se escanea toda la tabla):
        // - AGENTE: sus propias prospecciones.
        // - BROKER: las de los agentes que supervisa (semantica por equipo de agentes).
        // - ADMIN: todas (gobierno).
        if ("AGENTE".equals(usuario.rol())) {
            return prospecciones.listarPorAgentes(List.of(usuario.idDominio()));
        }
        if ("ADMIN".equals(usuario.rol())) {
            return prospecciones.listarTodos();
        }
        if ("BROKER".equals(usuario.rol())) {
            List<Long> agentesSupervisados = brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                    .map(BrokerAgente::getIdAgente)
                    .toList();
            return prospecciones.listarPorAgentes(agentesSupervisados);
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

    private static boolean coincideEstado(Prospeccion p, String estado) {
        if (estado != null && "GESTION".equalsIgnoreCase(estado)) {
            return p.getEstado() != null && Set.of("P", "C", "R", "E", "S").contains(p.getEstado().getCodigo());
        }
        return estado == null || estado.isBlank()
                || (p.getEstado() != null && estado.equalsIgnoreCase(p.getEstado().getCodigo()));
    }

    private static boolean coincideDistrito(Prospeccion p, String distrito) {
        if (distrito == null || distrito.isBlank()) {
            return true;
        }
        LocalComercial local = p.getLocalComercial();
        return local != null && normalizar(local.getDistrito()).equals(normalizar(distrito));
    }

    private static boolean coincideCaptacion(Prospeccion p, Long idCaptacion) {
        return idCaptacion == null
                || (p.getCaptacion() != null && idCaptacion.equals(p.getCaptacion().getIdCaptacion()));
    }

    private static boolean coincideLocal(Prospeccion p, Long idLocal) {
        return idLocal == null
                || (p.getLocalComercial() != null && idLocal.equals(p.getLocalComercial().getIdLocal()));
    }

    private static boolean coincideBusqueda(Prospeccion p, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = normalizar(query);
        LocalComercial local = p.getLocalComercial();
        String propietario = local != null && local.getPropietario() != null
                ? local.getPropietario().getNombresORazonSocial()
                : "";
        return normalizar(p.getCodigoProspeccion()).contains(q)
                || (local != null && normalizar(local.getCodigoLocal()).contains(q))
                || (local != null && normalizar(local.getDireccion()).contains(q))
                || normalizar(propietario).contains(q);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }
}
