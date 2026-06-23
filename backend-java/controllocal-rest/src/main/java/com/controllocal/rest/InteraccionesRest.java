package com.controllocal.rest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controllocal.bl.InteraccionComercialBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.impl.InteraccionComercialBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
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

@Path("interacciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InteraccionesRest {

    private final InteraccionComercialBusinessLogic interacciones = new InteraccionComercialBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades = new OportunidadComercialBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.InteraccionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("50") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Map<Long, OportunidadComercial> ops = mapaOportunidades();
        List<InteraccionComercial> fuente = interacciones.listarTodos().stream()
                .filter(item -> visiblePara(usuario, item))
                .toList();

        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Dtos.InteraccionResponse> items = fuente.subList(desde, hasta).stream()
                .map(item -> Dtos.InteraccionResponse.desde(item, ops.get(idOportunidad(item))))
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.InteraccionResponse obtener(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        InteraccionComercial interaccion = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        if (!visiblePara(usuario, interaccion)) {
            throw ApiException.prohibido();
        }
        return Dtos.InteraccionResponse.desde(interaccion, mapaOportunidades().get(idOportunidad(interaccion)));
    }

    @POST
    public Response registrar(Dtos.InteraccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idOportunidad() == null) {
            throw ApiException.badRequest("La oportunidad de la interaccion es obligatoria.");
        }

        InteraccionComercial interaccion = new InteraccionComercial();
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setIdOportunidad(dto.idOportunidad());
        interaccion.setOportunidadComercial(oportunidad);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());
        interaccion.setAgenteResponsable(agente);
        interaccion.setCanalContacto(canal(dto.canalContacto()));
        if (dto.resultado() != null && !dto.resultado().isBlank()) {
            interaccion.setResultado(resultado(dto.resultado()));
        }
        interaccion.setObservaciones(dto.observaciones());
        interaccion.setTranscripcionNota(dto.transcripcionNota());

        long id = interacciones.registrar(interaccion);
        InteraccionComercial creada = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.InteraccionResponse.desde(creada, mapaOportunidades().get(idOportunidad(creada))))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.InteraccionResponse actualizar(@PathParam("id") long id, Dtos.InteraccionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        InteraccionComercial interaccion = interacciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Interaccion"));
        if (!visiblePara(usuario, interaccion)) {
            throw ApiException.prohibido();
        }
        if (dto != null && dto.resultado() != null && !dto.resultado().isBlank()) {
            interaccion.setResultado(resultado(dto.resultado()));
        }
        if (dto != null && dto.observaciones() != null) {
            interaccion.setObservaciones(dto.observaciones());
        }
        interacciones.actualizar(interaccion);
        return Dtos.InteraccionResponse.desde(interaccion, mapaOportunidades().get(idOportunidad(interaccion)));
    }

    private Map<Long, OportunidadComercial> mapaOportunidades() {
        return oportunidades.listarTodos().stream()
                .filter(item -> item.getIdOportunidad() != null)
                .collect(Collectors.toMap(OportunidadComercial::getIdOportunidad, Function.identity(), (a, b) -> a));
    }

    private boolean visiblePara(UsuarioAutenticado usuario, InteraccionComercial interaccion) {
        if (usuario.tieneRol("BROKER", "ADMIN")) {
            return true;
        }
        Long idAgente = interaccion.getAgenteResponsable() != null
                ? interaccion.getAgenteResponsable().getIdAgente()
                : null;
        return idAgente != null && idAgente == usuario.idDominio();
    }

    private static Long idOportunidad(InteraccionComercial interaccion) {
        return interaccion.getOportunidadComercial() != null
                ? interaccion.getOportunidadComercial().getIdOportunidad()
                : null;
    }

    private static CanalContacto canal(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw ApiException.badRequest("El canal de contacto es obligatorio.");
        }
        try {
            return CanalContacto.fromCodigo(codigo);
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Canal de contacto invalido: " + codigo);
        }
    }

    private static ResultadoInteraccion resultado(String codigo) {
        try {
            return ResultadoInteraccion.fromCodigo(codigo);
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Resultado de interaccion invalido: " + codigo);
        }
    }
}
