package com.controllocal.rest;

import java.time.LocalDate;
import java.util.List;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;
import com.controllocal.model.usuario.Broker;
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

@Path("brokers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BrokersRest {

    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final AgenteBusinessLogic agenteBL = new AgenteBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.BrokerResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("50") int tamano) {
        SeguridadRest.usuario(request);
        List<Broker> fuente = brokers.listarTodos();
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.BrokerResponse> items = fuente.subList(desde, hasta).stream()
                .map(b -> Dtos.BrokerResponse.desde(b, agentesACargo(b.getIdBroker())))
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.BrokerResponse obtener(@PathParam("id") long id) {
        SeguridadRest.usuario(request);
        Broker broker = brokers.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Broker"));
        return Dtos.BrokerResponse.desde(broker, agentesACargo(id));
    }

    // Agentes supervisados por el broker. Permite que el administrador (o el propio broker)
    // acote en el frontend las solicitudes y la actividad del equipo de ese broker.
    @GET
    @Path("{id}/agentes")
    public List<Dtos.AgenteResponse> agentes(@PathParam("id") long id) {
        SeguridadRest.usuario(request);
        return agenteBL.listarPorBroker(id).stream()
                .map(Dtos.AgenteResponse::desde)
                .toList();
    }

    @POST
    public Response registrar(Dtos.BrokerRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "ADMIN");
        if (dto == null || vacio(dto.nombre()) || vacio(dto.usuario()) || vacio(dto.contrasena())) {
            throw ApiException.badRequest("Nombre, usuario y contrasena del broker son obligatorios.");
        }

        // REST solo arma la entidad (incluye el hash de contrasena, que es de
        // seguridad). La persistencia atomica persona+usuario+broker la hace la BL.
        Persona persona = new Persona();
        persona.setNombresORazonSocial(dto.nombre());
        persona.setTipoPersona(tipo(TipoPersona.class, dto.tipoPersona(), "N"));
        persona.setTipoDocumento(tipo(TipoDocumentoIdentidad.class, dto.tipoDocumento(), "D"));
        persona.setNumeroDocumento(dto.numeroDocumento());
        persona.setTelefono(dto.telefono());
        persona.setCorreo(dto.correo());
        persona.setEstado(EstadoActivoInactivo.ACTIVO);
        persona.setConsentimientoUsoDato(Boolean.TRUE);

        Broker broker = new Broker();
        broker.setPersona(persona);
        broker.setNombreUsuario(dto.usuario());
        broker.setContrasenaHash(PasswordHasher.hash(dto.contrasena().toCharArray()));
        broker.setRol(RolUsuarioInterno.BROKER);
        broker.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        broker.setCodigoBroker(!vacio(dto.codigoBroker()) ? dto.codigoBroker().trim() : generarCodigo());
        broker.setZona(dto.zona());
        broker.setFechaDesignacion(LocalDate.now());
        broker.setEsAdministrador(Boolean.TRUE.equals(dto.esAdministrador()));

        brokers.registrarBrokerCompleto(usuario.idDominio(), broker);

        return Response.status(Response.Status.CREATED)
                .entity(Dtos.BrokerResponse.desde(broker, 0))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.BrokerResponse actualizar(@PathParam("id") long id, Dtos.BrokerRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "ADMIN");
        Broker actual = brokers.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Broker"));
        if (dto != null) {
            Persona persona = actual.getPersona();
            if (persona != null) {
                if (!vacio(dto.nombre())) persona.setNombresORazonSocial(dto.nombre());
                if (dto.telefono() != null) persona.setTelefono(dto.telefono());
                if (dto.correo() != null) persona.setCorreo(dto.correo());
            }
            if (!vacio(dto.estado())) {
                actual.setEstadoAdministrativo(EstadoActivoInactivo.fromCodigo(dto.estado().trim()));
            }
            if (dto.zona() != null) actual.setZona(dto.zona());
            // Edicion atomica persona+usuario+broker en la BL.
            brokers.actualizarBrokerCompleto(usuario.idDominio(), actual);
        }
        Broker refrescado = brokers.buscarPorId(id).orElse(actual);
        return Dtos.BrokerResponse.desde(refrescado, agentesACargo(id));
    }

    private int agentesACargo(Long idBroker) {
        return idBroker == null ? 0 : brokers.listarAgentesSupervisados(idBroker).size();
    }

    private String generarCodigo() {
        return String.format("BRK-%03d", brokers.listarTodos().size() + 1);
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
