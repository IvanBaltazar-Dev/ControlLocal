package com.controllocal.rest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.enums.EstadoOperativoAgente;
import com.controllocal.model.usuario.enums.RolUsuarioInterno;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.PasswordHasher;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
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

@Path("agentes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AgentesRest {

    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades = new OportunidadComercialBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.AgenteResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("50") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        long idBroker = usuario.idDominio();
        List<AgenteInmobiliario> fuente = agentes.listarPorBroker(idBroker);

        // Conteos REALES por agente, calculados en el servidor (autoritativo): captaciones en
        // cartera (pendiente/observada/activa) y operaciones en curso (abierta/solicitud creada),
        // acotadas al equipo del broker.
        List<Captacion> capsEquipo = captaciones.listarPorBroker(idBroker);
        Set<Long> idsCaptacionEquipo = new HashSet<>();
        Map<Long, Integer> capPorAgente = new HashMap<>();
        for (Captacion c : capsEquipo) {
            if (c.getIdCaptacion() != null) {
                idsCaptacionEquipo.add(c.getIdCaptacion());
            }
            if (c.getAgenteResponsable() != null && c.getAgenteResponsable().getIdAgente() != null
                    && esCaptacionEnCartera(c.getEstado())) {
                capPorAgente.merge(c.getAgenteResponsable().getIdAgente(), 1, Integer::sum);
            }
        }
        Map<Long, Integer> opPorAgente = new HashMap<>();
        for (OportunidadComercial o : oportunidades.listarTodos()) {
            if (o.getCaptacion() == null
                    || !idsCaptacionEquipo.contains(o.getCaptacion().getIdCaptacion())) {
                continue;
            }
            if (o.getAgenteResponsable() != null && o.getAgenteResponsable().getIdAgente() != null
                    && esOperacionActiva(o.getEstado())) {
                opPorAgente.merge(o.getAgenteResponsable().getIdAgente(), 1, Integer::sum);
            }
        }

        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.AgenteResponse> items = fuente.subList(desde, hasta).stream()
                .map(a -> Dtos.AgenteResponse.desde(a,
                        capPorAgente.getOrDefault(a.getIdAgente(), 0),
                        opPorAgente.getOrDefault(a.getIdAgente(), 0)))
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    private static boolean esCaptacionEnCartera(EstadoCaptacion estado) {
        return estado == EstadoCaptacion.PENDIENTE_REVISION
                || estado == EstadoCaptacion.OBSERVADA
                || estado == EstadoCaptacion.ACTIVA;
    }

    private static boolean esOperacionActiva(EstadoOportunidadComercial estado) {
        return estado == EstadoOportunidadComercial.ABIERTA
                || estado == EstadoOportunidadComercial.SOLICITUD_CREADA;
    }

    @POST
    public Response registrar(Dtos.AgenteRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        if (dto == null || vacio(dto.nombre()) || vacio(dto.usuario()) || vacio(dto.contrasena())) {
            throw ApiException.badRequest("Nombre, usuario y contrasena del agente son obligatorios.");
        }

        // REST solo arma la entidad (incluye el hash de contrasena). La persistencia atomica
        // persona + usuario_interno + agente + asignacion al broker la hace la BL.
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setPersona(personaDesde(dto));
        agente.setNombreUsuario(dto.usuario());
        agente.setContrasenaHash(PasswordHasher.hash(dto.contrasena().toCharArray()));
        agente.setRol(RolUsuarioInterno.AGENTE);
        agente.setEstadoAdministrativo(estadoAdmin(dto.estado()));
        agente.setCodigoAgente(!vacio(dto.codigoAgente()) ? dto.codigoAgente().trim() : generarCodigo());
        agente.setZonaAsignada(dto.zona());
        agente.setFechaIngreso(LocalDate.now());
        agente.setEstadoOperativo(estadoOperativo(dto.estadoOperativo()));

        // Alta atomica persona + usuario_interno + agente + asignacion al broker en sesion.
        agentes.registrarAgenteCompleto(usuario.idDominio(), agente);

        return Response.status(Response.Status.CREATED)
                .entity(Dtos.AgenteResponse.desde(agente))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.AgenteResponse actualizar(@PathParam("id") long id, Dtos.AgenteRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        AgenteInmobiliario actual = agentes.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Agente"));
        if (dto != null) {
            Persona persona = actual.getPersona();
            if (persona != null) {
                if (!vacio(dto.nombre())) persona.setNombresORazonSocial(dto.nombre());
                if (dto.telefono() != null) persona.setTelefono(dto.telefono());
                if (dto.correo() != null) persona.setCorreo(dto.correo());
            }
            if (!vacio(dto.estado())) actual.setEstadoAdministrativo(estadoAdmin(dto.estado()));
            if (!vacio(dto.estadoOperativo())) actual.setEstadoOperativo(estadoOperativo(dto.estadoOperativo()));
            if (dto.zona() != null) actual.setZonaAsignada(dto.zona());
            // Edicion atomica persona + usuario_interno + agente (con validacion de alcance).
            agentes.actualizarAgenteCompleto(usuario.idDominio(), actual);
        }
        AgenteInmobiliario refrescado = agentes.buscarPorId(id).orElse(actual);
        return Dtos.AgenteResponse.desde(refrescado);
    }

    private Persona personaDesde(Dtos.AgenteRequest dto) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(dto.nombre());
        persona.setTipoPersona(tipo(TipoPersona.class, dto.tipoPersona(), "N"));
        persona.setTipoDocumento(tipo(TipoDocumentoIdentidad.class, dto.tipoDocumento(), "D"));
        persona.setNumeroDocumento(dto.numeroDocumento());
        persona.setTelefono(dto.telefono());
        persona.setCorreo(dto.correo());
        persona.setEstado(EstadoActivoInactivo.ACTIVO);
        persona.setConsentimientoUsoDato(Boolean.TRUE);
        return persona;
    }

    private String generarCodigo() {
        return String.format("AGE-%03d", agentes.listarTodos().size() + 1);
    }

    private static EstadoActivoInactivo estadoAdmin(String codigo) {
        return vacio(codigo) ? EstadoActivoInactivo.ACTIVO : EstadoActivoInactivo.fromCodigo(codigo.trim());
    }

    private static EstadoOperativoAgente estadoOperativo(String codigo) {
        return vacio(codigo) ? EstadoOperativoAgente.DISPONIBLE : EstadoOperativoAgente.fromCodigo(codigo.trim());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static <E extends Enum<E> & CodigoEnum> E tipo(Class<E> clase, String codigo, String porDefecto) {
        try {
            return CodigoEnum.fromCodigo(clase, vacio(codigo) ? porDefecto : codigo.trim());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Valor invalido: " + codigo);
        }
    }
}
