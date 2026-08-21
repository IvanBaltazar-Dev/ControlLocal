package com.controllocal.web.controlador;

import com.controllocal.service.LocalComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.AlmacenException;
import com.controllocal.web.dto.FotoLocalRequest;
import com.controllocal.web.dto.FotoLocalResponse;
import com.controllocal.web.dto.LocalRequest;
import com.controllocal.web.dto.LocalResponse;
import com.controllocal.web.dto.PrecioRequest;
import com.controllocal.web.dto.PrecioResponse;
import com.controllocal.web.dto.PosibleDuplicadoLocalResponse;
import com.controllocal.web.dto.ResumenLocalesResponse;
import com.controllocal.web.http.AccesoDenegadoException;
import com.controllocal.web.http.ErrorAlmacenException;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.http.RecursoNoEncontradoException;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

/**
 * Contrato CONGELADO del LocalesRest Jakarta: mismas rutas, formas, estados
 * HTTP y mensajes. Gates de rol identicos a la v1 (solo AGENTE escribe;
 * cualquier sesion lee). "mis-locales" y la regla de pertenencia por
 * captacion llegan con el modulo captacion (ver README).
 */
@RestController
@RequestMapping("locales")
public class LocalesController {

    private static final long TAMANO_MAXIMO_FOTO = 5L * 1024 * 1024; // 5 MB

    private final LocalComercialService locales;
    private final PrecioLocalService precios;
    private final AlmacenDocumentos almacen;

    public LocalesController(LocalComercialService locales,
                             PrecioLocalService precios, AlmacenDocumentos almacen) {
        this.locales = locales;
        this.precios = precios;
        this.almacen = almacen;
    }

    // =========================================================
    // Locales (bandeja global + CRUD)
    // =========================================================

    /**
     * Listado de la cartera con filtro, orden y paginacion resueltos en el
     * SERVIDOR. Los filtros son <b>aditivos y opcionales</b>: una llamada sin
     * {@code texto} ni {@code estado} responde exactamente lo mismo que antes
     * de que existieran, asi que el cable congelado no se rompe.
     *
     * <p>{@code page}/{@code page_size} son alias de {@code pagina}/{@code tamano},
     * la misma convencion que E3 dejo en {@code /clientes}.
     */
    @GetMapping
    public PageResponse<LocalResponse> listar(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer pagina,
                                              @RequestParam(name = "page_size", required = false) Integer pageSize,
                                              @RequestParam(required = false) Integer tamano,
                                              @RequestParam(required = false) String texto,
                                              @RequestParam(required = false) String estado) {
        int paginaSolicitada = ClientesController.paginaSolicitada(page, pagina);
        int tamanoSolicitado = pageSize != null ? pageSize : tamano != null ? tamano : 10;
        var resultado = locales.listar(
                new LocalComercialService.FiltrosLocal(texto, estado, paginaSolicitada, tamanoSolicitado),
                SesionActual.actor());
        return pagina(resultado, paginaSolicitada, tamanoSolicitado);
    }

    /**
     * KPI del listado, calculados en la BASE con el mismo {@code texto} que la
     * lista. Existe para que los contadores <b>no se deduzcan de las filas
     * descargadas</b>: un KPI contado en el cliente solo ve la pagina visible.
     *
     * <p>Sin gate de rol, igual que el listado: la cartera es de toda la
     * organizacion y el alcance es el tenant (ver
     * {@code docs/ai/matriz-operacion-rol.md}).
     *
     * <p>No lleva {@code estado} a proposito: el resumen es justamente el
     * desglose POR estado, y filtrarlo por uno dejaria los otros tres en cero.
     */
    @GetMapping("resumen")
    public ResumenLocalesResponse resumen(@RequestParam(required = false) String texto) {
        return ResumenLocalesResponse.desde(locales.resumen(texto, SesionActual.actor()));
    }

    @PostMapping("posibles-duplicados")
    @PreAuthorize("hasRole('AGENTE')")
    public List<PosibleDuplicadoLocalResponse> posiblesDuplicados(
            @RequestBody(required = false) LocalRequest dto,
            @RequestParam(required = false) Long idExcluir) {
        validarDto(dto);
        return locales.posiblesDuplicados(dto.aDatos(), idExcluir, SesionActual.actor()).stream()
                .map(PosibleDuplicadoLocalResponse::desde)
                .toList();
    }

    @GetMapping("mis-locales")
    @PreAuthorize("hasRole('AGENTE')")
    public PageResponse<LocalResponse> misLocales(@RequestParam(defaultValue = "1") int pagina,
                                                  @RequestParam(defaultValue = "10") int tamano) {
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        return pagina(locales.listarMisLocales(tamanoValido, (paginaValida - 1) * tamanoValido,
                SesionActual.actor()), paginaValida, tamanoValido);
    }

    /** Sobre de paginacion del cable, uno solo para los dos listados del recurso. */
    private static PageResponse<LocalResponse> pagina(Pagina<LocalComercialService.FichaLocal> pagina,
                                                      int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(LocalResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }

    @GetMapping("{id}")
    public LocalResponse obtener(@PathVariable long id) {
        return locales.buscarPorId(id, SesionActual.actor())
                .map(LocalResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Local"));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<LocalResponse> registrar(@RequestBody(required = false) LocalRequest dto) {
        validarDto(dto);
        LocalResponse creado = LocalResponse.desde(locales.registrar(dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public LocalResponse actualizar(@PathVariable long id, @RequestBody(required = false) LocalRequest dto) {
        validarDto(dto);
        return LocalResponse.desde(locales.actualizar(id, dto.aDatos(), SesionActual.actor()));
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminar(@PathVariable long id) {
        // Paridad v1: cualquier rechazo de la desactivacion sale como 403
        // con el mensaje de la regla (asi respondia el DELETE Jakarta).
        try {
            if (!locales.desactivar(id, SesionActual.actor())) {
                throw new AccesoDenegadoException("Local no encontrado.");
            }
        } catch (ReglaNegocioException error) {
            throw new AccesoDenegadoException(error.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // Precios del local (historico)
    // =========================================================

    @GetMapping("{id}/precios")
    public List<PrecioResponse> listarPrecios(@PathVariable long id) {
        return precios.listarPorLocal(id).stream()
                .map(PrecioResponse::desde)
                .toList();
    }

    @PostMapping("{id}/precios")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<PrecioResponse> registrarPrecio(@PathVariable long id,
                                                          @RequestBody(required = false) PrecioRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos del precio son obligatorios.");
        }
        PrecioResponse creado = PrecioResponse.desde(
                precios.registrar(id, dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // =========================================================
    // Las publicaciones se fueron a /encargos/{idEncargo}/publicaciones (V70).
    //
    // No es un cambio de URL: es la relacion real. Un anuncio publica un
    // ENCARGO -- esta propiedad, en esta operacion, a este precio--, y colgado
    // del local devolvia las series de venta y alquiler juntas sin poder decir
    // cual publicaba que.
    //
    // Se retiran en vez de dejarse como compatibilidad porque no les quedo
    // ningun consumidor: `oportunidad-form` era el ultimo y pregunta ya por su
    // encargo, que ademas es lo que de verdad queria (el anuncio del que salio
    // la oportunidad es el de SU encargo, no cualquiera de la propiedad).
    // =========================================================
    // =========================================================
    // Galeria de fotos: la imagen llega en base64, el binario va al almacen
    // y se sirve por GET /documentos/contenido?clave= (URL tipo capability).
    // =========================================================

    @GetMapping("{id}/fotos")
    public List<FotoLocalResponse> listarFotos(@PathVariable long id) {
        String proveedor = almacen.proveedor();
        return locales.listarFotos(id).stream()
                .map(f -> new FotoLocalResponse(f.idFoto(), f.clave(), f.nombreArchivo(), proveedor))
                .toList();
    }

    @PostMapping("{id}/fotos")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<FotoLocalResponse> subirFoto(@PathVariable long id,
                                                       @RequestBody(required = false) FotoLocalRequest dto) {
        if (dto == null || dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()
                || dto.contenidoBase64() == null || dto.contenidoBase64().isBlank()) {
            throw new ReglaNegocioException("La foto es obligatoria.");
        }
        String contentType = contentTypeImagen(dto.nombreArchivo());
        if (contentType == null) {
            throw new ReglaNegocioException("Solo se permiten imagenes PNG o JPG.");
        }
        byte[] contenido;
        try {
            contenido = Base64.getDecoder().decode(dto.contenidoBase64());
        } catch (IllegalArgumentException error) {
            throw new ReglaNegocioException("El contenido de la imagen (base64) es invalido.");
        }
        if (contenido.length == 0) {
            throw new ReglaNegocioException("La imagen esta vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO_FOTO) {
            throw new ReglaNegocioException(
                    "La imagen supera el maximo de " + (TAMANO_MAXIMO_FOTO / 1024 / 1024) + " MB.");
        }
        // Firma binaria real (magic bytes), no solo la extension: rechaza
        // archivos no-imagen renombrados a .png/.jpg (igual que la v1).
        if (!firmaImagenValida(contenido, contentType)) {
            throw new ReglaNegocioException("El archivo no es una imagen PNG o JPG valida.");
        }

        AlmacenDocumentos.ArchivoGuardado guardado;
        try {
            guardado = almacen.guardar(
                    AlmacenDocumentos.carpetaDeTenant(SesionActual.actor().idOrganizacion(),
                            "locales/" + id),
                    dto.nombreArchivo(), contenido, contentType);
        } catch (AlmacenException error) {
            throw new ErrorAlmacenException("No se pudo guardar la foto en el almacen: " + error.getMessage());
        }
        LocalComercialService.FotoLocal foto;
        try {
            foto = locales.agregarFoto(id, guardado.clave(), guardado.nombre(), SesionActual.actor());
        } catch (RuntimeException error) {
            // El registro fallo (local inexistente, tope de 6): se limpia el
            // binario recien subido para no dejar huerfanos en el almacen.
            almacen.eliminar(guardado.clave());
            throw error;
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FotoLocalResponse(foto.idFoto(), foto.clave(), foto.nombreArchivo(), almacen.proveedor()));
    }

    @DeleteMapping("{id}/fotos/{idFoto}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminarFoto(@PathVariable long id, @PathVariable long idFoto) {
        String clave = locales.eliminarFoto(idFoto, SesionActual.actor())
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto"));
        // Borra el binario del almacen best-effort: si ya no esta, la
        // operacion no debe fallar (el registro ya se elimino).
        try {
            almacen.eliminar(clave);
        } catch (RuntimeException ignorada) {
            // huerfano tolerado.
        }
        return ResponseEntity.noContent().build();
    }

    private static void validarDto(LocalRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos del local son obligatorios.");
        }
    }

    private static String contentTypeImagen(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase();
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
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
}
