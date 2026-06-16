package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.EvaluacionSolicitudBusinessLogic;
import com.controllocal.bl.impl.EvaluacionSolicitudBusinessLogicImpl;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("evaluaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EvaluacionRest {

    private final EvaluacionSolicitudBusinessLogic evaluaciones =
            new EvaluacionSolicitudBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.EvaluacionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        List<EvaluacionSolicitud> fuente = evaluacionesDelUsuario(
                SeguridadRest.exigirRol(request, "BROKER", "ADMIN"));
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.EvaluacionResponse> items = fuente.subList(desde, hasta).stream()
                .map(Dtos.EvaluacionResponse::desde)
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.EvaluacionResponse obtener(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        return evaluacionesDelUsuario(usuario).stream()
                .filter(item -> item.getIdEvaluacion() != null && item.getIdEvaluacion() == id)
                .findFirst()
                .map(Dtos.EvaluacionResponse::desde)
                .orElseThrow(() -> ApiException.noEncontrado("Evaluacion"));
    }

    @POST
    public Response registrar(Dtos.EvaluacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        if (dto == null || dto.idSolicitud() == null) {
            throw ApiException.badRequest("Los datos de la evaluacion son obligatorios.");
        }
        Long id = evaluaciones.registrar(dto.aEntidad(usuario.idDominio()));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.EvaluacionResponse.desde(
                        evaluaciones.buscarPorId(id)
                                .orElseThrow(() -> ApiException.noEncontrado("Evaluacion"))))
                .build();
    }

    private List<EvaluacionSolicitud> evaluacionesDelUsuario(UsuarioAutenticado usuario) {
        if (usuario.tieneRol("ADMIN")) {
            return evaluaciones.listarTodos();
        }
        return evaluaciones.listarPorBroker(usuario.idDominio());
    }
}
