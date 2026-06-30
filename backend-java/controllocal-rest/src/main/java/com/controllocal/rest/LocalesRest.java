package com.controllocal.rest;

import java.util.List;

import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.PrecioLocalBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.PublicacionBusinessLogic;
import com.controllocal.bl.impl.LocalComercialBusinessLogicImpl;
import com.controllocal.bl.impl.PrecioLocalBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.bl.impl.PublicacionBusinessLogicImpl;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.inmueble.FotoLocal;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.PrecioLocal;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;
import com.controllocal.model.usuario.AgenteInmobiliario;

import com.controllocal.rest.almacen.AlmacenDocumentos;
import com.controllocal.rest.almacen.AlmacenException;
import com.controllocal.rest.almacen.Almacenes;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;

import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("locales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocalesRest {

    private final LocalComercialBusinessLogic locales = new LocalComercialBusinessLogicImpl();
    private final PublicacionBusinessLogic publicacionBL = new PublicacionBusinessLogicImpl();
    private final PrecioLocalBusinessLogic precios = new PrecioLocalBusinessLogicImpl();
    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    // =========================================================
    // ENDPOINT GLOBAL (Para Administradores)
    // =========================================================
    @GET
    public PageResponse<Dtos.LocalResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        // Podrías agregar SeguridadRest.exigirRol(request, "ADMIN") si lo necesitas
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        List<Dtos.LocalResponse> items = locales
                .listarPagina(tamanoValido, (paginaValida - 1) * tamanoValido)
                .stream()
                .map(this::respuesta)
                .toList();
        return new PageResponse<>(items, locales.contar(), paginaValida, tamanoValido);
    }

    // =========================================================
    // NUEVO ENDPOINT RF-004: Solo mis captaciones
    // =========================================================
    @GET
    @Path("mis-locales")
    public PageResponse<Dtos.LocalResponse> listarMisLocales(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {

        // Exigir rol de Agente y extraer su ID
        UsuarioAutenticado agente = SeguridadRest.exigirRol(request, "AGENTE");

        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);

        List<Dtos.LocalResponse> items = locales
                .listarPaginaPorAgente(agente.idDominio(), tamanoValido, (paginaValida - 1) * tamanoValido)
                .stream()
                .map(this::respuesta)
                .toList();
        
        return new PageResponse<>(items, locales.contar(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.LocalResponse obtener(@PathParam("id") long id) {
        return locales.buscarPorId(id)
                .map(this::respuesta)
                .orElseThrow(() -> ApiException.noEncontrado("Local"));
    }

    @POST
    public Response registrar(Dtos.LocalRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);
        LocalComercial local = dto.aEntidad();
        local.setIdLocal(locales.registrar(local));
        publicacionBL.sincronizar(local, dto.estadoPublicacion());
        registrarProspeccionInicial(local, usuario.idDominio());
        return Response.status(Response.Status.CREATED)
                .entity(respuesta(local))
                .build();
    }

    // =========================================================
    // ACTUALIZADO RF-004: Actualización con trazabilidad
    // =========================================================
    @PUT
    @Path("{id}")
    public Dtos.LocalResponse actualizar(@PathParam("id") long id, Dtos.LocalRequest dto) {
        // Exigimos que sea un agente para validar su propiedad
        UsuarioAutenticado agente = SeguridadRest.exigirRol(request, "AGENTE");
        validarDto(dto);

        // Mapeamos los datos del DTO a la entidad
        LocalComercial cambios = dto.aEntidad();
        cambios.setIdLocal(id); // Importante asegurar que el ID coincida

        // Llamamos al método nuevo que incluye la lógica RF-004 (Trazabilidad y validación de propiedad)
        try {
            locales.actualizar(cambios, agente.idDominio());
        } catch (Exception e) {
            // Capturamos la BusinessException (ej. No es dueño, o validaciones) y la convertimos en ApiException para HTTP
            throw new ApiException(400, e.getMessage());
        }

        publicacionBL.sincronizar(cambios, dto.estadoPublicacion());

        // Para devolver la respuesta actualizada, volvemos a buscar el local
        LocalComercial actualizado = locales.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Local"));

        return respuesta(actualizado);
    }

    // =========================================================
    // ACTUALIZADO RF-004: Desactivación segura
    // =========================================================
    @DELETE
    @Path("{id}")
    public Response eliminar(@PathParam("id") long id) {
        UsuarioAutenticado agente = SeguridadRest.exigirRol(request, "AGENTE");

        try {
            if (!locales.desactivar(id, agente.idDominio())) {
                throw ApiException.noEncontrado("Local");
            }
        } catch (Exception e) {
            throw new ApiException(403, e.getMessage());
        }

        return Response.noContent().build();
    }

    // =========================================================
    // PRECIOS DEL LOCAL (historico)
    // =========================================================
    @GET
    @Path("{id}/precios")
    public List<Dtos.PrecioResponse> listarPrecios(@PathParam("id") long id) {
        return precios.listarPorLocal(id).stream()
                .map(Dtos.PrecioResponse::desde)
                .toList();
    }

    @POST
    @Path("{id}/precios")
    public Response registrarPrecio(@PathParam("id") long id, Dtos.PrecioRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos del precio son obligatorios.");
        }
        PrecioLocal precio = dto.aEntidad(id);
        precios.registrar(precio);
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.PrecioResponse.desde(precio))
                .build();
    }

    // =========================================================
    // PUBLICACIONES DEL LOCAL
    // =========================================================
    @GET
    @Path("{id}/publicaciones")
    public List<Dtos.PublicacionResponse> listarPublicaciones(@PathParam("id") long id) {
        return publicacionBL.listarPorInmueble(id).stream()
                .map(Dtos.PublicacionResponse::desde)
                .toList();
    }

    // Etapa 7: gestion de publicacion del local (publicar / actualizar / pausar / cerrar).
    @POST
    @Path("{id}/publicaciones")
    public Response crearPublicacion(@PathParam("id") long id, Dtos.PublicacionRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la publicacion son obligatorios.");
        }
        var creada = publicacionBL.crear(id, dto.aEntidad());
        return Response.status(Response.Status.CREATED)
                .entity(Dtos.PublicacionResponse.desde(creada))
                .build();
    }

    @PUT
    @Path("{id}/publicaciones/{idPublicacion}")
    public Dtos.PublicacionResponse actualizarPublicacion(@PathParam("id") long id,
            @PathParam("idPublicacion") long idPublicacion, Dtos.PublicacionRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la publicacion son obligatorios.");
        }
        return Dtos.PublicacionResponse.desde(publicacionBL.actualizar(idPublicacion, dto.aEntidad()));
    }

    @POST
    @Path("{id}/publicaciones/{idPublicacion}/estado")
    public Dtos.PublicacionResponse cambiarEstadoPublicacion(@PathParam("id") long id,
            @PathParam("idPublicacion") long idPublicacion, Dtos.EstadoPublicacionRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.estado() == null || dto.estado().isBlank()) {
            throw ApiException.badRequest("El estado de la publicacion es obligatorio.");
        }
        EstadoPublicacion estado;
        try {
            estado = EstadoPublicacion.fromCodigo(dto.estado());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado de publicacion no valido: " + dto.estado());
        }
        return Dtos.PublicacionResponse.desde(publicacionBL.cambiarEstado(idPublicacion, estado));
    }

    // =========================================================
    // GALERIA DE FOTOS DEL LOCAL
    // La imagen llega en base64 (el POST binario rompe el HttpClient de .NET contra
    // GlassFish), se guarda en el almacen y se sirve por "/documento?clave=".
    // =========================================================
    private static final long TAMANO_MAXIMO_FOTO = 5L * 1024 * 1024; // 5 MB

    @GET
    @Path("{id}/fotos")
    public List<FotoLocalResponse> listarFotos(@PathParam("id") long id) {
        return locales.listarFotos(id).stream()
                .map(f -> new FotoLocalResponse(f.getIdFoto(), f.getClave(), f.getNombreArchivo()))
                .toList();
    }

    @POST
    @Path("{id}/fotos")
    public Response subirFoto(@PathParam("id") long id, FotoLocalRequest dto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()
                || dto.contenidoBase64() == null || dto.contenidoBase64().isBlank()) {
            throw ApiException.badRequest("La foto es obligatoria.");
        }
        String contentType = contentTypeImagen(dto.nombreArchivo());
        if (contentType == null) {
            throw ApiException.badRequest("Solo se permiten imagenes PNG o JPG.");
        }
        byte[] contenido;
        try {
            contenido = java.util.Base64.getDecoder().decode(dto.contenidoBase64());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("El contenido de la imagen (base64) es invalido.");
        }
        if (contenido.length == 0) {
            throw ApiException.badRequest("La imagen esta vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO_FOTO) {
            throw ApiException.badRequest(
                    "La imagen supera el maximo de " + (TAMANO_MAXIMO_FOTO / 1024 / 1024) + " MB.");
        }
        // Verifica la firma binaria real (magic bytes), no solo la extension: rechaza
        // archivos no-imagen renombrados a .png/.jpg.
        if (!firmaImagenValida(contenido, contentType)) {
            throw ApiException.badRequest("El archivo no es una imagen PNG o JPG valida.");
        }
        try {
            AlmacenDocumentos.ArchivoGuardado guardado = Almacenes.actual().guardar(
                    "locales/" + id, dto.nombreArchivo(), contenido, contentType);
            long idFoto = locales.agregarFoto(id, guardado.clave(), guardado.nombre());
            return Response.status(Response.Status.CREATED)
                    .entity(new FotoLocalResponse(idFoto, guardado.clave(), guardado.nombre()))
                    .build();
        } catch (AlmacenException error) {
            throw new ApiException(502, "No se pudo guardar la foto en el almacen: " + error.getMessage());
        }
    }

    @DELETE
    @Path("{id}/fotos/{idFoto}")
    public Response eliminarFoto(@PathParam("id") long id, @PathParam("idFoto") long idFoto) {
        SeguridadRest.exigirRol(request, "AGENTE");
        // Recupera la clave del binario ANTES de borrar la fila, para limpiar el almacen.
        String clave = locales.listarFotos(id).stream()
                .filter(f -> f.getIdFoto() != null && f.getIdFoto() == idFoto)
                .map(f -> f.getClave())
                .findFirst().orElse(null);
        if (!locales.eliminarFoto(idFoto)) {
            throw ApiException.noEncontrado("Foto");
        }
        // Borra el objeto del almacen (S3/disco) para no dejar binarios huerfanos. Best-effort:
        // si el binario ya no esta, la operacion no debe fallar.
        if (clave != null && !clave.isBlank()) {
            try {
                Almacenes.actual().eliminar(clave);
            } catch (Exception ignore) {
                // huerfano tolerado: el registro ya se elimino correctamente.
            }
        }
        return Response.noContent().build();
    }

    private static String contentTypeImagen(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }

    // Firma binaria: PNG = 89 50 4E 47 ; JPG = FF D8 FF.
    private static boolean firmaImagenValida(byte[] c, String contentType) {
        if (c == null || c.length < 4) {
            return false;
        }
        if ("image/png".equals(contentType)) {
            return (c[0] & 0xFF) == 0x89 && (c[1] & 0xFF) == 0x50
                    && (c[2] & 0xFF) == 0x4E && (c[3] & 0xFF) == 0x47;
        }
        if ("image/jpeg".equals(contentType)) {
            return (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
        }
        return false;
    }

    public record FotoLocalRequest(String nombreArchivo, String contenidoBase64) {
    }

    public record FotoLocalResponse(Long idFoto, String clave, String nombre) {
    }

    private void validarDto(Dtos.LocalRequest dto) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del local son obligatorios.");
        }
    }

    private Dtos.LocalResponse respuesta(LocalComercial local) {

        List<FotoLocal> fotos = locales.listarFotos(local.getIdLocal());


        String clavePortada = fotos.isEmpty() ? null : fotos.get(0).getClave();


        return Dtos.LocalResponse.desde(
                local,
                publicacionBL.codigoEstadoPublicacion(local.getIdLocal()),
                clavePortada // ¡Aquí pasamos la clave!
        );
    }

    private void registrarProspeccionInicial(LocalComercial local, long idAgente) {
        Prospeccion prospeccion = new Prospeccion();
        prospeccion.setLocalComercial(local);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(idAgente);
        prospeccion.setAgenteResponsable(agente);
        prospecciones.registrar(prospeccion);
    }
}