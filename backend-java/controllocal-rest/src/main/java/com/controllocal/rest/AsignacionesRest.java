package com.controllocal.rest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("asignaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AsignacionesRest {

    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    @Path("agentes")
    public List<Dtos.AsignacionAgenteResponse> agentes() {
        SeguridadRest.exigirRol(request, "ADMIN");
        Map<Long, String> brokerNombre = brokerNombreMap();
        return agentes.listarTodos().stream()
                .filter(a -> a.getIdAgente() != null)
                .map(a -> {
                    String brokerActual = brokers.buscarBrokerActivoDeAgente(a.getIdAgente())
                            .map(ba -> brokerNombre.get(ba.getIdBroker()))
                            .orElse(null);
                    return Dtos.AsignacionAgenteResponse.desde(a, brokerActual);
                })
                .toList();
    }

    @GET
    @Path("brokers")
    public List<Dtos.AsignacionBrokerResponse> brokers() {
        SeguridadRest.exigirRol(request, "ADMIN");
        return brokers.listarTodos().stream()
                .filter(b -> b.getIdBroker() != null)
                .map(b -> Dtos.AsignacionBrokerResponse.desde(
                        b, brokers.listarAgentesSupervisados(b.getIdBroker()).size()))
                .toList();
    }

    @GET
    @Path("historial")
    public List<Dtos.BrokerAgenteResponse> historial() {
        SeguridadRest.exigirRol(request, "ADMIN");
        Map<Long, String> agenteNombre = agenteNombreMap();
        Map<Long, String> brokerNombre = brokerNombreMap();
        return brokers.listarReasignacionesAgenteBroker().stream()
                .sorted((a, b) -> Long.compare(b.getIdReasignacion(), a.getIdReasignacion()))
                .map(r -> mapear(r, agenteNombre, brokerNombre))
                .toList();
    }

    @POST
    @Path("reasignar")
    public Dtos.BrokerAgenteResponse reasignar(Dtos.ReasignarAgenteRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "ADMIN");
        if (dto == null || dto.idAgente() == null || dto.idBrokerDestino() == null) {
            throw ApiException.badRequest("El agente y el broker destino son obligatorios.");
        }
        brokers.asignarAgente(usuario.idDominio(), dto.idBrokerDestino(), dto.idAgente(), dto.motivo());

        Map<Long, String> agenteNombre = agenteNombreMap();
        Map<Long, String> brokerNombre = brokerNombreMap();
        return brokers.listarReasignacionesAgenteBroker().stream()
                .filter(r -> r.getAgente() != null && dto.idAgente().equals(r.getAgente().getIdAgente()))
                .max((a, b) -> Long.compare(a.getIdReasignacion(), b.getIdReasignacion()))
                .map(r -> mapear(r, agenteNombre, brokerNombre))
                .orElseThrow(() -> ApiException.noEncontrado("Reasignacion"));
    }

    private Dtos.BrokerAgenteResponse mapear(
            com.controllocal.model.usuario.ReasignacionAgenteBroker r,
            Map<Long, String> agenteNombre,
            Map<Long, String> brokerNombre) {
        return Dtos.BrokerAgenteResponse.desde(r,
                r.getAgente() != null ? agenteNombre.get(r.getAgente().getIdAgente()) : null,
                r.getBrokerAnterior() != null ? brokerNombre.get(r.getBrokerAnterior().getIdBroker()) : null,
                r.getBrokerNuevo() != null ? brokerNombre.get(r.getBrokerNuevo().getIdBroker()) : null,
                r.getBrokerAdministrador() != null ? brokerNombre.get(r.getBrokerAdministrador().getIdBroker()) : null);
    }

    private Map<Long, String> agenteNombreMap() {
        return agentes.listarTodos().stream()
                .filter(a -> a.getIdAgente() != null)
                .collect(Collectors.toMap(AgenteInmobiliario::getIdAgente, a -> nombre(a.getPersona()), (x, y) -> x));
    }

    private Map<Long, String> brokerNombreMap() {
        return brokers.listarTodos().stream()
                .filter(b -> b.getIdBroker() != null)
                .collect(Collectors.toMap(Broker::getIdBroker, b -> nombre(b.getPersona()), (x, y) -> x));
    }

    private static String nombre(Persona persona) {
        return persona != null && persona.getNombresORazonSocial() != null
                ? persona.getNombresORazonSocial() : "";
    }
}
