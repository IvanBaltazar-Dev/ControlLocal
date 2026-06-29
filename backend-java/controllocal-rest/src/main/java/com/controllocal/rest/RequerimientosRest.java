package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.RequerimientoClienteBusinessLogic;
import com.controllocal.bl.impl.ClienteInteresadoBusinessLogicImpl;
import com.controllocal.bl.impl.RequerimientoClienteBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.RequerimientoCliente;
import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Requerimientos de cliente (perfil de busqueda, Etapa 8). Crearlos vincula automaticamente
 * el requerimiento a su cliente y lo activa para la recomendacion de cartera; nunca crea
 * oportunidades. Clientes son catalogo compartido: cualquier AGENTE puede gestionarlos.
 */
@Path("requerimientos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RequerimientosRest {

    private final RequerimientoClienteBusinessLogic requerimientos = new RequerimientoClienteBusinessLogicImpl();
    private final ClienteInteresadoBusinessLogic clientes = new ClienteInteresadoBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    @Path("cliente/{idCliente}")
    public List<Dtos.RequerimientoResponse> listarPorCliente(@PathParam("idCliente") long idCliente) {
        SeguridadRest.usuario(request);
        return requerimientos.listarPorCliente(idCliente).stream()
                .map(Dtos.RequerimientoResponse::desde)
                .toList();
    }

    @POST
    public Response crear(Dtos.RequerimientoRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        validar(dto, dto != null ? dto.idCliente() : null);
        clientes.buscarPorId(dto.idCliente()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        RequerimientoCliente requerimiento = dto.aEntidad();
        requerimiento.setIdRequerimiento(requerimientos.crear(requerimiento));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.RequerimientoResponse.desde(
                        requerimientos.buscarPorId(requerimiento.getIdRequerimiento())
                                .orElseThrow(() -> ApiException.noEncontrado("Requerimiento"))))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.RequerimientoResponse actualizar(@PathParam("id") long id, Dtos.RequerimientoRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        RequerimientoCliente actual = requerimientos.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Requerimiento"));
        Long idCliente = dto != null && dto.idCliente() != null
                ? dto.idCliente()
                : (actual.getCliente() != null ? actual.getCliente().getIdCliente() : null);
        validar(dto, idCliente);
        RequerimientoCliente requerimiento = dto.aEntidad();
        requerimiento.setIdRequerimiento(id);
        if (requerimiento.getCliente() == null || requerimiento.getCliente().getIdCliente() == null) {
            requerimiento.setCliente(actual.getCliente());
        }
        requerimientos.actualizar(requerimiento);
        return Dtos.RequerimientoResponse.desde(requerimientos.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Requerimiento")));
    }

    @POST
    @Path("{id}/estado")
    public Dtos.RequerimientoResponse cambiarEstado(@PathParam("id") long id, Dtos.EstadoRequerimientoRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.estado() == null || dto.estado().isBlank()) {
            throw ApiException.badRequest("El estado del requerimiento es obligatorio.");
        }
        EstadoRequerimiento estado;
        try {
            estado = CodigoEnum.fromCodigo(EstadoRequerimiento.class, dto.estado());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado de requerimiento no valido: " + dto.estado());
        }
        requerimientos.cambiarEstado(id, estado);
        return Dtos.RequerimientoResponse.desde(requerimientos.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Requerimiento")));
    }

    private void validar(Dtos.RequerimientoRequest dto, Long idCliente) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del requerimiento son obligatorios.");
        }
        if (idCliente == null || idCliente <= 0) {
            throw ApiException.badRequest("El cliente del requerimiento es obligatorio.");
        }
        if (dto.rubro() == null || dto.rubro().isBlank()) {
            throw ApiException.badRequest("El rubro del requerimiento es obligatorio.");
        }
    }
}
