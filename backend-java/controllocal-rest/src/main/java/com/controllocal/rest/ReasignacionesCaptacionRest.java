package com.controllocal.rest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.rest.dto.Dtos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("captaciones/reasignaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReasignacionesCaptacionRest {

    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public List<Dtos.ReasignacionCaptacionResponse> listar() {
        SeguridadRest.exigirRol(request, "BROKER", "ADMIN");

        Map<Long, Captacion> capMap = captaciones.listarTodos().stream()
                .filter(c -> c.getIdCaptacion() != null)
                .collect(Collectors.toMap(Captacion::getIdCaptacion, c -> c, (a, b) -> a));
        Map<Long, String> agenteMap = agentes.listarTodos().stream()
                .filter(a -> a.getIdAgente() != null)
                .collect(Collectors.toMap(AgenteInmobiliario::getIdAgente, ReasignacionesCaptacionRest::nombre, (a, b) -> a));
        Map<Long, String> brokerMap = brokers.listarTodos().stream()
                .filter(b -> b.getIdBroker() != null)
                .collect(Collectors.toMap(Broker::getIdBroker, ReasignacionesCaptacionRest::nombre, (a, b) -> a));

        return captaciones.listarReasignaciones().stream()
                .sorted((a, b) -> Long.compare(b.getIdReasignacion(), a.getIdReasignacion()))
                .map(r -> {
                    Captacion cap = r.getCaptacion() != null ? capMap.get(r.getCaptacion().getIdCaptacion()) : null;
                    String codigo = cap != null ? cap.getCodigoCaptacion() : null;
                    String direccion = cap != null && cap.getLocalComercial() != null
                            ? cap.getLocalComercial().getDireccion() : null;
                    String anterior = r.getAgenteAnterior() != null ? agenteMap.get(r.getAgenteAnterior().getIdAgente()) : null;
                    String nuevo = r.getAgenteNuevo() != null ? agenteMap.get(r.getAgenteNuevo().getIdAgente()) : null;
                    String broker = r.getBrokerResponsable() != null ? brokerMap.get(r.getBrokerResponsable().getIdBroker()) : null;
                    return Dtos.ReasignacionCaptacionResponse.desde(r, codigo, direccion, anterior, nuevo, broker);
                })
                .toList();
    }

    private static String nombre(AgenteInmobiliario agente) {
        return agente.getPersona() != null && agente.getPersona().getNombresORazonSocial() != null
                ? agente.getPersona().getNombresORazonSocial() : "";
    }

    private static String nombre(Broker broker) {
        return broker.getPersona() != null && broker.getPersona().getNombresORazonSocial() != null
                ? broker.getPersona().getNombresORazonSocial() : "";
    }
}
