package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.impl.ClienteInteresadoBusinessLogicImpl;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("clientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientesRest {

    private final ClienteInteresadoBusinessLogic clientes = new ClienteInteresadoBusinessLogicImpl();

    @GET
    public PageResponse<Dtos.ClienteResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        List<Dtos.ClienteResponse> items = clientes
                .listarPagina(tamanoValido, (paginaValida - 1) * tamanoValido)
                .stream()
                .map(Dtos.ClienteResponse::desde)
                .toList();
        return new PageResponse<>(items, clientes.contar(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.ClienteResponse obtener(@PathParam("id") long id) {
        return clientes.buscarPorId(id)
                .map(Dtos.ClienteResponse::desde)
                .orElseThrow(() -> ApiException.noEncontrado("Cliente"));
    }

    @POST
    public Response registrar(Dtos.ClienteRequest dto) {
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
}
