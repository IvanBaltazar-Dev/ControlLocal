package com.controllocal.rest.http;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
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
        // Violacion de restriccion UNIQUE (documento/correo repetido, etc.): el INSERT/UPDATE falla
        // con SQLIntegrityConstraintViolationException envuelta en DAOException. Antes caia al 500
        // generico y el usuario solo veia "No se pudo completar la operacion"; ahora se traduce a un
        // 409 que nombra el dato en conflicto para que sepa exactamente que corregir.
        SQLException duplicado = violacionUnicidad(error);
        if (duplicado != null) {
            return respuesta(Response.Status.CONFLICT.getStatusCode(), mensajeDuplicado(duplicado.getMessage()));
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
                "No se pudo completar la operacion." + detalleCausaRaiz(error));
    }

    // Recorre la cadena de causas buscando una violacion de integridad (llave/UNIQUE duplicada).
    private static SQLException violacionUnicidad(Throwable error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < 20; i++) {
            if (actual instanceof SQLIntegrityConstraintViolationException sql) {
                return sql;
            }
            if (actual instanceof SQLException sql
                    && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return sql;
            }
            if (actual.getCause() == actual) {
                break;
            }
            actual = actual.getCause();
        }
        return null;
    }

    private static String mensajeDuplicado(String sqlMessage) {
        String m = sqlMessage == null ? "" : sqlMessage.toLowerCase();
        if (m.contains("numero_documento") || m.contains("documento")) {
            return "Ya existe un registro con ese número de documento.";
        }
        if (m.contains("correo")) {
            return "Ya existe un registro con ese correo electrónico.";
        }
        if (m.contains("nombre")) {
            return "Ya existe un registro con ese nombre.";
        }
        return "Ya existe un registro con esos datos: un dato único está duplicado.";
    }

    private static String detalleCausaRaiz(Throwable error) {
        Throwable raiz = error;
        for (int i = 0; raiz.getCause() != null && raiz.getCause() != raiz && i < 20; i++) {
            raiz = raiz.getCause();
        }
        String mensaje = raiz.getMessage();
        return mensaje == null || mensaje.isBlank() ? "" : " Detalle: " + mensaje;
    }

    private Response respuesta(int estado, String mensaje) {
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(mensaje))
                .build();
    }
}
