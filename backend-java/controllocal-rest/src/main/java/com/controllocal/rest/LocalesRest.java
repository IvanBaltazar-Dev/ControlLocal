package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.impl.LocalComercialBusinessLogicImpl;
import com.controllocal.model.inmueble.LocalComercial;
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

@Path("locales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocalesRest {

    private final LocalComercialBusinessLogic locales = new LocalComercialBusinessLogicImpl();

    @GET
    public PageResponse<Dtos.LocalResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        List<Dtos.LocalResponse> items = locales
                .listarPagina(tamanoValido, (paginaValida - 1) * tamanoValido)
                .stream()
                .map(Dtos.LocalResponse::desde)
                .toList();
        return new PageResponse<>(items, locales.contar(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.LocalResponse obtener(@PathParam("id") long id) {
        return locales.buscarPorId(id)
                .map(Dtos.LocalResponse::desde)
                .orElseThrow(() -> ApiException.noEncontrado("Local"));
    }

    @POST
    public Response registrar(Dtos.LocalRequest dto) {
        validarDto(dto);
        LocalComercial local = dto.aEntidad();
        local.setIdLocal(locales.registrar(local));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.LocalResponse.desde(local))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.LocalResponse actualizar(@PathParam("id") long id, Dtos.LocalRequest dto) {
        validarDto(dto);
        LocalComercial actual = locales.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Local"));
        LocalComercial cambios = dto.aEntidad();
        actual.setCodigoLocal(cambios.getCodigoLocal());
        actual.setDireccion(cambios.getDireccion());
        actual.setDistrito(cambios.getDistrito());
        actual.setMetraje(cambios.getMetraje());
        actual.setPrecioReferencial(cambios.getPrecioReferencial());
        actual.setRubroPermitido(cambios.getRubroPermitido());
        actual.setDescripcion(cambios.getDescripcion());
        actual.setIdPropietario(cambios.getIdPropietario());
        actual.setEstado(cambios.getEstado());
        actual.setTipoInmueble(cambios.getTipoInmueble());
        actual.setUso(cambios.getUso());
        actual.setAmbientes(cambios.getAmbientes());
        actual.setAntiguedadAnios(cambios.getAntiguedadAnios());
        actual.setZonaUrbanizacion(cambios.getZonaUrbanizacion());
        actual.setGeoLat(cambios.getGeoLat());
        actual.setGeoLong(cambios.getGeoLong());
        actual.setEstadoPublicacion(cambios.getEstadoPublicacion());
        locales.actualizar(actual);
        return Dtos.LocalResponse.desde(actual);
    }

    @DELETE
    @Path("{id}")
    public Response eliminar(@PathParam("id") long id) {
        if (!locales.desactivar(id)) {
            throw ApiException.noEncontrado("Local");
        }
        return Response.noContent().build();
    }

    private void validarDto(Dtos.LocalRequest dto) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del local son obligatorios.");
        }
    }
}
