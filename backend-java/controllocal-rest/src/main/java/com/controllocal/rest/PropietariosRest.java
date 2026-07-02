package com.controllocal.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.PropietarioBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.PropietarioBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("propietarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PropietariosRest {

    private final PropietarioBusinessLogic propietarios = new PropietarioBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final FichaComercialSupport fichas = new FichaComercialSupport();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.PropietarioResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int offset = (paginaValida - 1) * tamanoValido;
        long total;
        List<Propietario> paginaProps;
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())) {
            // Paginacion y conteo REALES en SQL (antes: listarTodos() + subList en memoria).
            total = propietarios.contar();
            paginaProps = propietarios.listarPagina(tamanoValido, offset);
        } else {
            // BROKER: el conjunto permitido ya viene acotado en SQL; se pagina sobre esos ids.
            List<Long> ids = new ArrayList<>(idsPropietariosPermitidos(usuario));
            ids.sort(java.util.Comparator.reverseOrder());
            total = ids.size();
            int desde = Math.min(offset, ids.size());
            int hasta = Math.min(desde + tamanoValido, ids.size());
            paginaProps = propietarios.listarPorIds(ids.subList(desde, hasta));
        }
        // Conteo de locales en seguimiento SOLO para los propietarios de la pagina (no escanea todo).
        List<Long> idsPagina = paginaProps.stream()
                .map(Propietario::getIdPropietario).filter(Objects::nonNull).toList();
        Map<Long, Integer> localesPorPropietario = localesEnSeguimiento(usuario, idsPagina);
        List<Dtos.PropietarioResponse> items = paginaProps.stream()
                .map(propietario -> Dtos.PropietarioResponse.desde(propietario,
                        localesPorPropietario.getOrDefault(propietario.getIdPropietario(), 0)))
                .toList();
        return new PageResponse<>(items, total, paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.PropietarioResponse obtener(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Propietario propietario = propietarios.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Propietario"));
        if (!puedeVerPropietario(propietario, usuario)) {
            throw ApiException.prohibido();
        }
        return Dtos.PropietarioResponse.desde(
                propietario,
                localesEnSeguimiento(usuario, List.of(propietario.getIdPropietario()))
                        .getOrDefault(propietario.getIdPropietario(), 0));
    }

    @GET
    @Path("{id}/ficha-comercial")
    public FichaComercialSupport.PropietarioFichaResponse fichaComercial(
            @PathParam("id") long id,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        int tamanoValido = pageSize != null ? pageSize : tamano != null ? tamano : FichaComercialSupport.DEFAULT_PAGE_SIZE;
        return fichas.fichaPropietario(id, SeguridadRest.usuario(request), tamanoValido);
    }

    @GET
    @Path("{id}/ficha-comercial/{section}")
    public FichaComercialSupport.FichaSectionResponse fichaComercialSection(
            @PathParam("id") long id,
            @PathParam("section") String section,
            @QueryParam("page") Integer page,
            @QueryParam("pagina") Integer pagina,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        int paginaValida = page != null ? page : pagina != null ? pagina : 1;
        int tamanoValido = pageSize != null ? pageSize : tamano != null ? tamano : FichaComercialSupport.DEFAULT_PAGE_SIZE;
        return fichas.seccionPropietario(id, section, SeguridadRest.usuario(request), paginaValida, tamanoValido);
    }

    @POST
    public Response registrar(Dtos.PropietarioRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);
        Propietario propietario = dto.aEntidad();
        propietario.setIdPropietario(propietarios.registrar(propietario));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.PropietarioResponse.desde(propietario))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.PropietarioResponse actualizar(@PathParam("id") long id, Dtos.PropietarioRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);
        Propietario actual = propietarios.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Propietario"));
        actual.actualizarDatos(dto.telefono(), dto.correo(), dto.nombre());
        if (dto.consentimientoUsoDato() != null) {
            actual.getPersona().setConsentimientoUsoDato(dto.consentimientoUsoDato());
        }
        if (dto.estado() != null && !dto.estado().isBlank()) {
            actual.setEstado(com.controllocal.model.persona.enums.EstadoActivoInactivo.fromCodigo(dto.estado()));
        }
        propietarios.actualizar(actual);
        return Dtos.PropietarioResponse.desde(actual);
    }

    @DELETE
    @Path("{id}")
    public Response eliminar(@PathParam("id") long id) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (!propietarios.desactivar(id)) {
            throw ApiException.noEncontrado("Propietario");
        }
        return Response.noContent().build();
    }

    private void validarDto(Dtos.PropietarioRequest dto) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del propietario son obligatorios.");
        }
    }

    // Conteo de locales en seguimiento por propietario, resuelto en SQL y acotado al alcance del
    // rol, SOLO para los propietarios dados (la pagina). Reemplaza el escaneo en memoria de todas
    // las captaciones/prospecciones que hacia el antiguo contarLocalesPorPropietario.
    private Map<Long, Integer> localesEnSeguimiento(UsuarioAutenticado usuario, List<Long> idsPropietario) {
        if (idsPropietario.isEmpty()) {
            return Map.of();
        }
        if ("ADMIN".equals(usuario.rol())) {
            return propietarios.contarLocalesEnSeguimiento(idsPropietario, null, null);
        }
        if ("AGENTE".equals(usuario.rol())) {
            return propietarios.contarLocalesEnSeguimiento(idsPropietario, List.of(usuario.idDominio()), null);
        }
        // BROKER: su equipo (por agente) union las captaciones que supervisa (por captacion).
        Set<Long> equipo = agentesSupervisados(usuario);
        Set<Long> supervisadas = captacionesPermitidasBroker(usuario).stream()
                .map(Captacion::getIdCaptacion)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return propietarios.contarLocalesEnSeguimiento(idsPropietario, equipo, supervisadas);
    }

    private boolean puedeVerPropietario(Propietario propietario, UsuarioAutenticado usuario) {
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())) {
            return true;
        }
        return propietario.getIdPropietario() != null
                && idsPropietariosPermitidos(usuario).contains(propietario.getIdPropietario());
    }

    private Set<Long> idsPropietariosPermitidos(UsuarioAutenticado usuario) {
        Set<Long> agentesBroker = agentesSupervisados(usuario);
        Set<Long> captacionesBroker = captacionesPermitidasBroker(usuario).stream()
                .map(Captacion::getIdCaptacion)
                .collect(Collectors.toSet());

        Set<Long> desdeProspecciones = prospeccionesEnAlcance(usuario).stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker))
                .map(Prospeccion::getLocalComercial)
                .map(this::propietarioId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Set<Long> desdeCaptaciones = captacionesEnAlcance(usuario).stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || (item.getIdCaptacion() != null && captacionesBroker.contains(item.getIdCaptacion())))
                .map(Captacion::getLocalComercial)
                .map(this::propietarioId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        desdeProspecciones.addAll(desdeCaptaciones);
        return desdeProspecciones;
    }

    // Origen acotado en SQL segun el rol (antes se escaneaba toda la tabla). El filtro de
    // visibilidad autoritativo se mantiene aguas abajo; estas fuentes son su superconjunto.
    private List<Prospeccion> prospeccionesEnAlcance(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("ADMIN")) {
            return prospecciones.listarTodos();
        }
        if (usuario.tieneRol("AGENTE")) {
            return prospecciones.listarPorAgentes(List.of(usuario.idDominio()));
        }
        return prospecciones.listarPorAgentes(agentesSupervisados(usuario));
    }

    private List<Captacion> captacionesEnAlcance(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("ADMIN")) {
            return captaciones.listarTodos();
        }
        if (usuario.tieneRol("AGENTE")) {
            return captaciones.listarPorAgente(usuario.idDominio());
        }
        // BROKER: captaciones de su equipo (por agente) union las que supervisa (por captacion).
        List<Captacion> resultado = new ArrayList<>(captaciones.listarPorAgentes(agentesSupervisados(usuario)));
        resultado.addAll(captacionesPermitidasBroker(usuario));
        return resultado;
    }

    private Long propietarioId(LocalComercial local) {
        return local != null ? local.getIdPropietario() : null;
    }

    private Set<Long> agentesSupervisados(UsuarioAutenticado usuario) {
        if (!"BROKER".equals(usuario.rol())) {
            return Set.of();
        }
        return brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                .map(BrokerAgente::getIdAgente)
                .collect(Collectors.toSet());
    }

    private List<Captacion> captacionesPermitidasBroker(UsuarioAutenticado usuario) {
        return "BROKER".equals(usuario.rol()) ? captaciones.listarPorBroker(usuario.idDominio()) : List.of();
    }

    private boolean permitidoPorAgente(
            com.controllocal.model.usuario.AgenteInmobiliario agente,
            UsuarioAutenticado usuario,
            Set<Long> agentesBroker) {
        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if ("AGENTE".equals(usuario.rol())) {
            return usuario.idDominio() == agente.getIdAgente();
        }
        if ("BROKER".equals(usuario.rol())) {
            return agentesBroker.contains(agente.getIdAgente());
        }
        return false;
    }
}
