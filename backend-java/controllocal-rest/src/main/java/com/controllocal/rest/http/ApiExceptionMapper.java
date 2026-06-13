package com.controllocal.rest.http;

import com.controllocal.bl.BusinessException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable error) {
        if (error instanceof ApiException api) {
            return respuesta(api.getStatus(), api.getMessage());
        }
        if (error instanceof BusinessException || error instanceof IllegalArgumentException) {
            return respuesta(Response.Status.BAD_REQUEST.getStatusCode(), error.getMessage());
        }

        System.err.println("[ControlLocal API] Error no controlado: " + error);
        error.printStackTrace(System.err);
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
