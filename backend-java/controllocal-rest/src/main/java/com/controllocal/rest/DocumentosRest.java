package com.controllocal.rest;

import com.controllocal.rest.almacen.AlmacenDocumentos;
import com.controllocal.rest.almacen.Almacenes;
import com.controllocal.rest.http.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Acceso al almacen de documentos del expediente (S3 o disco, segun el selector hibrido).
 *
 *   GET documentos/salud      -> estado de conectividad del almacen (requiere sesion).
 *   GET documentos/contenido  -> contenido binario de un documento por su clave opaca.
 *
 * "contenido" es publico (la clave es un identificador no adivinable, tipo capability):
 * el frontend lo expone tras su propio proxy "/documento". Asi el visor (iframe) puede
 * cargar el archivo sin propagar el token JWT al navegador. Ver JwtAuthFilter#esPublica.
 */
@Path("documentos")
public class DocumentosRest {

    @Context
    private HttpServletRequest request;

    @GET
    @Path("salud")
    @Produces(MediaType.APPLICATION_JSON)
    public AlmacenDocumentos.EstadoAlmacen salud() {
        SeguridadRest.usuario(request); // exige una sesion valida.
        return Almacenes.actual().verificarConexion();
    }

    @GET
    @Path("contenido")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response contenido(@QueryParam("clave") String clave) {
        if (clave == null || clave.isBlank() || clave.contains("..") || clave.contains("\\")) {
            throw ApiException.badRequest("Clave de documento invalida.");
        }
        AlmacenDocumentos.ArchivoDescargado archivo = Almacenes.actual().abrir(clave)
                .orElseThrow(() -> ApiException.noEncontrado("Documento"));
        String contentType = archivo.contentType() != null
                ? archivo.contentType()
                : MediaType.APPLICATION_OCTET_STREAM;
        return Response.ok(archivo.contenido())
                .type(contentType)
                .header("Content-Disposition", "inline; filename=\"" + archivo.nombre() + "\"")
                .build();
    }
}
