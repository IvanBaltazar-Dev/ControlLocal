package com.controllocal.web.controlador;

import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.FotoPropiedadService.FotoDePropiedad;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.GaleriaDeFotos;
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

    private final LocalComercialService locales;
    private final PrecioLocalService precios;
    /** La galeria, compartida con {@code /propiedades/{id}/fotos} (WP-C). */
    private final GaleriaDeFotos galeria;

    public LocalesController(LocalComercialService locales,
                             PrecioLocalService precios, GaleriaDeFotos galeria) {
        this.locales = locales;
        this.precios = precios;
        this.galeria = galeria;
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

    /**
     * <b>La serie economica del inmueble, filtrada por quien pregunta</b>
     * (D-P0-6).
     *
     * <p>El actor viaja al servicio y no se queda aqui: hasta este corte esta
     * llamada no lo pasaba, y la coleccion no filtraba ni por tenant — la fila
     * de la matriz decia «se alcanza por el id del padre, que si va filtrado por
     * tenant» y el padre <b>no se cargaba</b>. Cualquier usuario autenticado de
     * otra corredora leia la serie de precios de cualquier propiedad por su id.
     */
    @GetMapping("{id}/precios")
    public List<PrecioResponse> listarPrecios(@PathVariable long id) {
        return precios.listarPorLocal(id, SesionActual.actor()).stream()
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

    /**
     * <b>La misma galeria que {@code /propiedades/{id}/fotos}</b>, con la forma
     * de respuesta de la v1 y su tope de 6 (WP-C).
     *
     * <p>Las tres operaciones delegan en {@link GaleriaDeFotos}, que es el unico
     * sitio donde se valida el binario, y de ahi en
     * {@code FotoPropiedadService}, que es el unico escritor de
     * {@code foto_propiedad}. <b>Una foto subida por aqui se ve por la ruta
     * universal y al reves</b>: es la misma propiedad y la misma tabla.
     *
     * <p>Lo que <b>no</b> cambia es el tope: esta puerta prometia 6 y sigue
     * prometiendo 6. La consecuencia, dicha en vez de descubierta: una
     * propiedad con 6 o mas fotos subidas por la ruta universal rechaza aqui la
     * siguiente, aunque el tope universal sea 12.
     *
     * <p>Y {@code orden} no viaja en esta respuesta, que es la de la v1. Quien
     * lo necesite —o quiera reordenar— usa {@code /propiedades/{id}/fotos}: la
     * puerta heredada conserva su forma, no gana capacidades nuevas.
     */
    @GetMapping("{id}/fotos")
    public List<FotoLocalResponse> listarFotos(@PathVariable long id) {
        return galeria.listar(id, SesionActual.actor()).stream()
                .map(f -> new FotoLocalResponse(f.id(), f.clave(), f.nombreArchivo(), f.proveedor(),f.utilidad()))
                .toList();
    }

    @PostMapping("{id}/fotos")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<FotoLocalResponse> subirFoto(@PathVariable long id,
                                                       @RequestBody(required = false) FotoLocalRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("La foto es obligatoria.");
        }
        Actor actor = SesionActual.actor();
        FotoDePropiedad foto = galeria.agregar(id, dto.nombreArchivo(), dto.contenidoBase64(),
                FotoPropiedadService.TOPE_DEL_LOCAL, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(new FotoLocalResponse(
                foto.id(), foto.clave(), foto.nombreArchivo(), foto.proveedor(),foto.utilidad()));
    }

    @DeleteMapping("{id}/fotos/{idFoto}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminarFoto(@PathVariable long id, @PathVariable long idFoto) {
        galeria.eliminar(idFoto, SesionActual.actor());
        return ResponseEntity.noContent().build();
    }

    private static void validarDto(LocalRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos del local son obligatorios.");
        }
    }
}
