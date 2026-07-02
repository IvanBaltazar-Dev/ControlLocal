package com.controllocal.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ClienteInteresadoBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.persona.ClienteInteresado;
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

@Path("clientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientesRest {

    private final ClienteInteresadoBusinessLogic clientes = new ClienteInteresadoBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades =
            new OportunidadComercialBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final FichaComercialSupport fichas = new FichaComercialSupport();
    private final CoincidenciaCarteraSupport coincidencias = new CoincidenciaCarteraSupport();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.ClienteResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int offset = (paginaValida - 1) * tamanoValido;
        long total;
        List<ClienteInteresado> paginaClientes;
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())) {
            // Paginacion y conteo REALES en SQL (antes: listarTodos() + subList en memoria).
            total = clientes.contar();
            paginaClientes = clientes.listarPagina(tamanoValido, offset);
        } else {
            // BROKER: el conjunto de clientes permitidos ya viene acotado en SQL (no toda la
            // tabla); se pagina sobre esos ids y se cargan en bloque con listarPorIds.
            List<Long> ids = new ArrayList<>(idsClientesPermitidos(usuario));
            ids.sort(java.util.Comparator.reverseOrder());
            total = ids.size();
            int desde = Math.min(offset, ids.size());
            int hasta = Math.min(desde + tamanoValido, ids.size());
            paginaClientes = clientes.listarPorIds(ids.subList(desde, hasta));
        }
        List<Dtos.ClienteResponse> items = paginaClientes.stream()
                .map(Dtos.ClienteResponse::desde)
                .toList();
        return new PageResponse<>(items, total, paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.ClienteResponse obtener(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        ClienteInteresado cliente = clientes.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        if (!puedeVerCliente(cliente, usuario)) {
            throw ApiException.prohibido();
        }
        return Dtos.ClienteResponse.desde(cliente);
    }

    @GET
    @Path("{id}/ficha-comercial")
    public FichaComercialSupport.ClienteFichaResponse fichaComercial(
            @PathParam("id") long id,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        int tamanoValido = pageSize != null ? pageSize : tamano != null ? tamano : FichaComercialSupport.DEFAULT_PAGE_SIZE;
        return fichas.fichaCliente(id, SeguridadRest.usuario(request), tamanoValido);
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
        return fichas.seccionCliente(id, section, SeguridadRest.usuario(request), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}/coincidencias")
    public CoincidenciaCarteraSupport.CoincidenciasResponse coincidencias(
            @PathParam("id") long id,
            @QueryParam("page") Integer page,
            @QueryParam("pagina") Integer pagina,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        return coincidencias.propiedadesParaCliente(
                id,
                SeguridadRest.usuario(request),
                page != null ? page : pagina != null ? pagina : 1,
                pageSize != null ? pageSize : tamano != null ? tamano : 6);
    }

    @POST
    public Response registrar(Dtos.ClienteRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);
        ClienteInteresado cliente = dto.aEntidad();
        cliente.setIdCliente(clientes.registrar(cliente));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.ClienteResponse.desde(cliente))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.ClienteResponse actualizar(@PathParam("id") long id, Dtos.ClienteRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);
        ClienteInteresado actual = clientes.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        actual.actualizarDatos(dto.telefono(), dto.correo(), dto.nombre());
        actual.setRubroComercial(dto.rubroComercial());
        actual.setConsentimientoContacto(dto.consentimientoContacto());
        actual.setConsentimientoUsoDato(dto.consentimientoUsoDato());
        if (dto.consentimientoUsoDato() != null) {
            actual.getPersona().setConsentimientoUsoDato(dto.consentimientoUsoDato());
        }
        if (dto.estado() != null && !dto.estado().isBlank()) {
            actual.getPersona().setEstado(
                    com.controllocal.model.persona.enums.EstadoActivoInactivo.fromCodigo(dto.estado()));
        }
        clientes.actualizar(actual);
        return Dtos.ClienteResponse.desde(actual);
    }

    @DELETE
    @Path("{id}")
    public Response eliminar(@PathParam("id") long id) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (!clientes.desactivar(id)) {
            throw ApiException.noEncontrado("Cliente");
        }
        return Response.noContent().build();
    }

    private void validarDto(Dtos.ClienteRequest dto) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del cliente son obligatorios.");
        }
    }

    private boolean puedeVerCliente(ClienteInteresado cliente, UsuarioAutenticado usuario) {
        if ("ADMIN".equals(usuario.rol()) || "AGENTE".equals(usuario.rol())) {
            return true;
        }
        return cliente.getIdCliente() != null && idsClientesPermitidos(usuario).contains(cliente.getIdCliente());
    }

    private Set<Long> idsClientesPermitidos(UsuarioAutenticado usuario) {
        // Solo se invoca para BROKER (ADMIN/AGENTE corto-circuitan antes).
        Set<Long> agentesBroker = agentesSupervisados(usuario);
        Set<Long> captacionesBroker = captacionesPermitidasBroker(usuario).stream()
                .map(Captacion::getIdCaptacion)
                .collect(Collectors.toSet());
        // Captaciones cuyo agente pertenece al equipo: cubren la rama captacionPermitida
        // (captacion.agente en el equipo) sin escanear toda la tabla.
        Set<Long> captacionesScope = new HashSet<>(captacionesBroker);
        captaciones.listarPorAgentes(agentesBroker).stream()
                .map(Captacion::getIdCaptacion)
                .filter(id -> id != null)
                .forEach(captacionesScope::add);

        // Origen acotado en SQL (antes se escaneaba TODA la tabla); el filtro de
        // visibilidad autoritativo se mantiene intacto sobre el superconjunto resultante.
        List<OportunidadComercial> ops = new ArrayList<>(oportunidades.listarPorAgentes(agentesBroker));
        ops.addAll(oportunidades.listarPorCaptaciones(captacionesScope));
        Set<Long> desdeOportunidades = ops.stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || captacionPermitida(item.getCaptacion(), usuario, agentesBroker, captacionesBroker))
                .map(OportunidadComercial::getClienteInteresado)
                .filter(cliente -> cliente != null && cliente.getIdCliente() != null)
                .map(ClienteInteresado::getIdCliente)
                .collect(Collectors.toSet());

        List<SolicitudAlquiler> sols = new ArrayList<>(solicitudes.listarPorAgentes(agentesBroker));
        sols.addAll(solicitudes.listarPorCaptaciones(captacionesScope));
        Set<Long> desdeSolicitudes = sols.stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || captacionPermitida(item.getCaptacion(), usuario, agentesBroker, captacionesBroker))
                .map(SolicitudAlquiler::getClienteInteresado)
                .filter(cliente -> cliente != null && cliente.getIdCliente() != null)
                .map(ClienteInteresado::getIdCliente)
                .collect(Collectors.toSet());

        desdeOportunidades.addAll(desdeSolicitudes);
        return desdeOportunidades;
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

    private boolean captacionPermitida(
            Captacion captacion,
            UsuarioAutenticado usuario,
            Set<Long> agentesBroker,
            Set<Long> captacionesBroker) {
        if (captacion == null) {
            return false;
        }
        return permitidoPorAgente(captacion.getAgenteResponsable(), usuario, agentesBroker)
                || (captacion.getIdCaptacion() != null && captacionesBroker.contains(captacion.getIdCaptacion()));
    }
}
