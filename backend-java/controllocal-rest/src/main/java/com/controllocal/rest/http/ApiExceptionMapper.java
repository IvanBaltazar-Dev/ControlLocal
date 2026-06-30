package com.controllocal.rest.http;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.controllocal.bl.BusinessException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    // java.util.logging es el unico sink que GlassFish enruta a server.log;
    // System.err se pierde en el stdout del proceso que arranca el dominio.
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable error) {
        if (error instanceof ApiException api) {
            return respuesta(api.getStatus(), api.getMessage());
        }
        if (error instanceof BusinessException || error instanceof IllegalArgumentException) {
            return respuesta(Response.Status.BAD_REQUEST.getStatusCode(), error.getMessage());
        }
        // Errores de cliente de JAX-RS (body vacío/malformado, no encontrado, etc.):
        // se devuelven con SU código real, no como 500 genérico que oculta la causa.
        if (error instanceof WebApplicationException web) {
            int estado = web.getResponse() != null
                    ? web.getResponse().getStatus()
                    : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            String mensaje = estado == Response.Status.BAD_REQUEST.getStatusCode()
                    ? "El cuerpo de la solicitud es obligatorio o tiene un formato inválido."
                    : (web.getMessage() != null ? web.getMessage() : "No se pudo completar la operacion.");
            return respuesta(estado, mensaje);
        }

        LOG.log(Level.SEVERE, "[ControlLocal API] Error no controlado", error);
        return respuesta(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "No se pudo completar la operacion.");
    }

    private Response respuesta(int estado, String mensaje) {
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(mensaje))
                .build();
    }
}
