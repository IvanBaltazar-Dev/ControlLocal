package com.controllocal.rest;

import java.time.LocalDateTime;
import java.util.List;

import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.impl.LocalComercialBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.dao.PublicacionDAO;
import com.controllocal.dao.impl.PublicacionDAOImpl;
import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.enums.CanalPublicacion;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;
import com.controllocal.model.usuario.AgenteInmobiliario;

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
    private final PublicacionDAO publicaciones = new PublicacionDAOImpl();
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
        sincronizarPublicacion(local, dto.estadoPublicacion());
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

        sincronizarPublicacion(cambios, dto.estadoPublicacion());

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

    private void validarDto(Dtos.LocalRequest dto) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del local son obligatorios.");
        }
    }

    private Dtos.LocalResponse respuesta(LocalComercial local) {
        String estado = publicaciones.listarPorInmueble(local.getIdLocal()).stream()
                .findFirst()
                .map(Publicacion::getEstado)
                .map(EstadoPublicacion::getCodigo)
                .orElse(EstadoPublicacion.BORRADOR.getCodigo());
        return Dtos.LocalResponse.desde(local, estado);
    }

    private void sincronizarPublicacion(LocalComercial local, String codigoEstado) {
        if (codigoEstado == null || codigoEstado.isBlank()) {
            return;
        }
        final EstadoPublicacion estado;
        try {
            estado = EstadoPublicacion.fromCodigo(codigoEstado);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado de publicacion invalido: " + codigoEstado);
        }

        List<Publicacion> existentes = publicaciones.listarPorInmueble(local.getIdLocal());
        Publicacion publicacion = existentes.isEmpty() ? null : existentes.get(0);
        if (publicacion == null && estado == EstadoPublicacion.BORRADOR) {
            return;
        }
        if (publicacion == null) {
            publicacion = nuevaPublicacionWeb(local);
        }
        publicacion.setEstado(estado);
        publicacion.setRentaPublicada(local.getPrecioReferencial());
        publicacion.setTituloAnuncio("Publicacion " + local.getCodigoLocal());
        publicacion.setFechaBaja(estado == EstadoPublicacion.CERRADO ? LocalDateTime.now() : null);

        if (publicacion.getIdPublicacion() == null) {
            publicaciones.crear(publicacion);
        } else {
            publicaciones.actualizar(publicacion);
        }
    }

    private Publicacion nuevaPublicacionWeb(LocalComercial local) {
        Publicacion publicacion = new Publicacion();
        publicacion.setInmueble(local);
        publicacion.setCanal(CanalPublicacion.WEB_PROPIA);
        publicacion.setVersionAnuncio(1);
        publicacion.setTituloAnuncio("Publicacion " + local.getCodigoLocal());
        publicacion.setRentaPublicada(local.getPrecioReferencial());
        publicacion.setMoneda(Moneda.PEN);
        publicacion.setCodigoOrigen("WEB-" + local.getIdLocal());
        publicacion.setFechaPublicacion(LocalDateTime.now());
        return publicacion;
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