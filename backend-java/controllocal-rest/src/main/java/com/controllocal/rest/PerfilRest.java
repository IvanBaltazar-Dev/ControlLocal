package com.controllocal.rest;

import java.util.Base64;

import com.controllocal.bl.UsuarioInternoBusinessLogic;
import com.controllocal.bl.impl.UsuarioInternoBusinessLogicImpl;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.usuario.UsuarioInterno;
import com.controllocal.rest.almacen.AlmacenDocumentos;
import com.controllocal.rest.almacen.AlmacenException;
import com.controllocal.rest.almacen.Almacenes;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Perfil del usuario en sesion (foto y telefono). La foto se guarda en el mismo almacen
 * de archivos que los documentos del expediente y se sirve por la clave opaca a traves del
 * proxy "/documento" del frontend. La imagen llega en base64 dentro del JSON (igual que los
 * documentos) porque el POST binario octet-stream rompe el HttpClient de .NET contra GlassFish.
 */
@Path("perfil")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PerfilRest {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024; // 5 MB

    private final UsuarioInternoBusinessLogic usuarios = new UsuarioInternoBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public PerfilResponse obtener() {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        UsuarioInterno interno = usuarios.buscarPorId(usuario.idUsuario())
                .orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        Persona persona = interno.getPersona();
        String fotoClave = usuarios.obtenerFotoPerfil(usuario.idUsuario()).orElse(null);
        return new PerfilResponse(
                persona != null ? persona.getNombresORazonSocial() : interno.getNombreUsuario(),
                persona != null ? persona.getCorreo() : null,
                persona != null ? persona.getTelefono() : null,
                fotoClave);
    }

    @PATCH
    public PerfilResponse actualizar(PerfilRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        if (dto != null && dto.telefono() != null) {
            String telefono = dto.telefono().trim();
            long digitos = telefono.chars().filter(Character::isDigit).count();
            if (digitos < 6 || digitos > 15) {
                throw ApiException.badRequest("Ingresa un telefono valido de entre 6 y 15 digitos.");
            }
            usuarios.actualizarTelefono(usuario.idUsuario(), telefono);
        }
        return obtener();
    }

    @POST
    @Path("foto")
    public FotoResponse subirFoto(FotoRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        if (dto == null || dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()
                || dto.contenidoBase64() == null || dto.contenidoBase64().isBlank()) {
            throw ApiException.badRequest("La foto es obligatoria.");
        }
        String contentType = contentType(dto.nombreArchivo());
        if (contentType == null) {
            throw ApiException.badRequest("Solo se permiten imagenes PNG o JPG.");
        }
        byte[] contenido;
        try {
            contenido = Base64.getDecoder().decode(dto.contenidoBase64());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("El contenido de la imagen (base64) es invalido.");
        }
        if (contenido.length == 0) {
            throw ApiException.badRequest("La imagen esta vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw ApiException.badRequest("La imagen supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
        }
        try {
            AlmacenDocumentos.ArchivoGuardado guardado = Almacenes.actual().guardar(
                    "perfiles", dto.nombreArchivo(), contenido, contentType);
            usuarios.actualizarFotoPerfil(usuario.idUsuario(), guardado.clave());
            return new FotoResponse(guardado.clave());
        } catch (AlmacenException error) {
            throw new ApiException(502, "No se pudo guardar la foto en el almacen: " + error.getMessage());
        }
    }

    private static String contentType(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }

    public record PerfilRequest(String telefono) {
    }

    public record PerfilResponse(String nombre, String correo, String telefono, String fotoClave) {
    }

    public record FotoRequest(String nombreArchivo, String contenidoBase64) {
    }

    public record FotoResponse(String clave) {
    }
}
