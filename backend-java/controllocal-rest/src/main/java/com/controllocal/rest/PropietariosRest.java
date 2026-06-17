package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.PropietarioBusinessLogic;
import com.controllocal.bl.impl.PropietarioBusinessLogicImpl;
import com.controllocal.model.persona.Propietario;
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

@Path("propietarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PropietariosRest {

    private final PropietarioBusinessLogic propietarios = new PropietarioBusinessLogicImpl();

    @GET
    public PageResponse<Dtos.PropietarioResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        List<Dtos.PropietarioResponse> items = propietarios
                .listarPagina(tamanoValido, (paginaValida - 1) * tamanoValido)
                .stream()
                .map(Dtos.PropietarioResponse::desde)
                .toList();
        return new PageResponse<>(items, propietarios.contar(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.PropietarioResponse obtener(@PathParam("id") long id) {
        return propietarios.buscarPorId(id)
                .map(Dtos.PropietarioResponse::desde)
                .orElseThrow(() -> ApiException.noEncontrado("Propietario"));
    }

    @POST
    public Response registrar(Dtos.PropietarioRequest dto) {
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
}
