package com.controllocal.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.controllocal.bl.TareaBusinessLogic;
import com.controllocal.bl.impl.TareaBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Tarea;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// Etapa 5: bandeja "Acciones Pendientes" del agente. Sin alta manual: las tareas se
// derivan/reconcilian del estado del flujo. El agente solo resuelve (en su pantalla) o cancela.
@Path("tareas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TareasRest {

    private final TareaBusinessLogic tareas = new TareaBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public List<TareaResponse> bandeja() {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        return tareas.bandejaDe(usuario.idDominio()).stream()
                .map(TareaResponse::desde)
                .toList();
    }

    @POST
    @Path("{id}/cancelar")
    public Response cancelar(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        tareas.cancelar(id, usuario.idDominio());
        return Response.noContent().build();
    }

    public record TareaResponse(Long id, String tipo, String entidadTipo, Long entidadId,
                                String entidadCodigo, String rutaResolver,
                                String descripcion, String estado, String prioridad,
                                LocalDateTime fechaProgramada, Integer diasSinAccion,
                                LocalDate fechaVencimiento) {
        public static TareaResponse desde(Tarea t) {
            return new TareaResponse(t.getIdTarea(), codigo(t.getTipo()), codigo(t.getEntidadTipo()),
                    t.getEntidadId(), t.getEntidadCodigo(), t.getRutaResolver(),
                    t.getDescripcion(), codigo(t.getEstado()), codigo(t.getPrioridad()),
                    t.getFechaProgramada(), t.getDiasSinAccion(), t.getFechaVencimiento());
        }

        private static String codigo(CodigoEnum valor) {
            return valor == null ? null : valor.getCodigo();
        }
    }
}
