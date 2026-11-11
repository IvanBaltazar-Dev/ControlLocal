package com.controllocal.web.controlador;

import com.controllocal.service.DocumentoSolicitudService;
import com.controllocal.service.Actor;
import com.controllocal.service.EvaluacionService;
import com.controllocal.service.Pagina;
import com.controllocal.service.SolicitudService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.AlmacenException;
import com.controllocal.web.almacen.NombresArchivo;
import com.controllocal.web.dto.DocumentoSolicitudResponse;
import com.controllocal.web.dto.EvaluacionResponse;
import com.controllocal.web.dto.ResumenSolicitudesResponse;
import com.controllocal.web.dto.RevisionDocumentoRequest;
import com.controllocal.web.dto.SolicitudRequest;
import com.controllocal.web.dto.SolicitudResponse;
import com.controllocal.web.http.ErrorAlmacenException;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.almacen.CuotaArchivosB0;
import com.controllocal.web.almacen.SalidaBinariaPersistente;
import com.controllocal.service.planes.ClasificadorArchivosExistentesB0;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contrato CONGELADO del SolicitudesRest Jakarta: la solicitud de alquiler y
 * su expediente documental. El alcance por rol vive en el service (AGENTE por
 * agente, BROKER por AGENTE SUPERVISADO — distinto de contratos, §7); aqui
 * quedan los gates de rol y todo lo que toca BINARIOS.
 *
 * <p><b>La frontera con el service</b>: {@code DocumentoSolicitudService} no ve
 * archivos. El binario, la extension, el tamano y el almacen son de esta capa
 * —igual que las fotos de F2—, y al service solo baja el metadato con
 * {@code rutaArchivo} = clave del almacen.
 *
 * <p><b>Hay UNA sola via de subida</b>: {@code POST {id}/documentos/archivo},
 * octet-stream. La v1 tenia cuatro y llegaron a convivir tres en la v2, no por
 * diseno sino rodeando un bug del {@code SocketsHttpHandler} de .NET 10 que
 * sufria el Blazor. Ese cliente se elimino el 2026-08-08 y con el las dos vias
 * que solo existian para el (JSON con base64 y por trozos). Si alguien echa de
 * menos una subida por partes, se disena de nuevo con su limpieza de cargas
 * abandonadas — la que habia se quedaba en memoria hasta reiniciar.
 */
@RestController
@RequestMapping("solicitudes")
public class SolicitudesController {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(".pdf", ".png", ".jpg", ".jpeg");

    private final SolicitudService solicitudes;
    private final DocumentoSolicitudService documentos;
    private final EvaluacionService evaluaciones;
    private final RevalidacionDeSesion sesiones;
    private final SalidaBinariaPersistente salidas;private final CuotaArchivosB0 cuota;private final ClasificadorArchivosExistentesB0 clasificador;

    public SolicitudesController(SolicitudService solicitudes, DocumentoSolicitudService documentos,
                                 EvaluacionService evaluaciones,RevalidacionDeSesion sesiones,SalidaBinariaPersistente salidas,
                                 CuotaArchivosB0 cuota,ClasificadorArchivosExistentesB0 clasificador) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.evaluaciones = evaluaciones;
        this.sesiones = sesiones;
        this.salidas=salidas;this.cuota=cuota;this.clasificador=clasificador;
    }

    /**
     * {@code idAgente}, {@code estado}, {@code distrito} y {@code texto} son
     * <b>extension aditiva</b> del v2 (no existen en la v1): omitidos, la
     * respuesta es byte a byte la del cable congelado, incluido el orden por id
     * descendente. Existen porque las dos bandejas Angular filtran en la base y
     * no pueden repetir lo que hacia el Blazor —descargar todas las solicitudes
     * del alcance y filtrar en memoria—.
     *
     * <p>{@code estado=PENDIENTES} no es un estado: es el cubo de la cola del
     * broker ({@code E} + {@code O}), como {@code GESTION} en prospecciones.
     */
    @GetMapping
    public PageResponse<SolicitudResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                  @RequestParam(defaultValue = "10") int tamano,
                                                  @RequestParam(required = false) Long idOportunidad,
                                                  @RequestParam(required = false) Long idCaptacion,
                                                  @RequestParam(required = false) Long idAgente,
                                                  @RequestParam(required = false) String estado,
                                                  @RequestParam(required = false) String distrito,
                                                  @RequestParam(required = false) String texto) {
        return pagina(solicitudes.listar(new SolicitudService.FiltrosSolicitud(idOportunidad,
                idCaptacion, idAgente, estado, distrito, texto, pagina, tamano),
                SesionActual.actor()), pagina, tamano);
    }

    /**
     * Extension aditiva: KPI por estado del MISMO conjunto que pagina la lista,
     * mas los distritos y agentes del alcance para que los dos selectores sean
     * data-driven sin llamada extra. No acepta {@code estado}, {@code distrito}
     * ni {@code idAgente} — son justo lo que devuelve.
     */
    @GetMapping("resumen")
    public ResumenSolicitudesResponse resumen(@RequestParam(required = false) Long idOportunidad,
                                              @RequestParam(required = false) Long idCaptacion,
                                              @RequestParam(required = false) String texto) {
        return ResumenSolicitudesResponse.desde(solicitudes.resumen(
                new SolicitudService.FiltrosSolicitud(idOportunidad, idCaptacion, null, null, null,
                        texto, 1, 10),
                SesionActual.actor()));
    }

    @GetMapping("{id}")
    public SolicitudResponse obtener(@PathVariable long id) {
        return SolicitudResponse.desde(solicitudes.obtener(id, SesionActual.actor()));
    }

    @GetMapping("codigo/{codigo}")
    public SolicitudResponse obtenerPorCodigo(@PathVariable String codigo) {
        return SolicitudResponse.desde(solicitudes.obtenerPorCodigo(codigo, SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<SolicitudResponse> registrar(
            @RequestBody(required = false) SolicitudRequest dto) {
        SolicitudResponse creada = SolicitudResponse.desde(
                solicitudes.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /** Subsanar una observada: vuelve a EN_REVISION para que el broker decida. */
    @PostMapping("{id}/reenviar")
    @PreAuthorize("hasRole('AGENTE')")
    public SolicitudResponse reenviarAEvaluacion(@PathVariable long id) {
        return SolicitudResponse.desde(solicitudes.reenviarAEvaluacion(id, SesionActual.actor()));
    }

    /** Historial de decisiones de la solicitud: lo ve tambien el agente dueno. */
    @GetMapping("{id}/evaluaciones")
    public List<EvaluacionResponse> listarEvaluaciones(@PathVariable long id) {
        return evaluaciones.historialDeSolicitud(id, SesionActual.actor()).stream()
                .map(EvaluacionResponse::desde)
                .toList();
    }

    // =========================================================
    // Expediente documental. El binario va al almacen y se sirve
    // por referencia Core y lectura contextual revalidada; la clave del proveedor es privada.
    // =========================================================

    @GetMapping("{id}/documentos")
    public List<DocumentoSolicitudResponse> listarDocumentos(@PathVariable long id) {
        return documentos.listarPorSolicitud(id, SesionActual.actor()).stream()
                .map(DocumentoSolicitudResponse::desde)
                .toList();
    }

    // Aqui vivian DOS altas mas —por JSON con base64 y por trozos— que se
    // borraron el 2026-08-08 al descongelar el contrato. No eran tres formas
    // de subir por diseno: eran un rodeo alrededor de un bug del
    // `SocketsHttpHandler` de .NET 10 que sufria el Blazor. Ese cliente ya no
    // existe y el SPA siempre uso `documentos/archivo`, asi que las otras dos
    // eran superficie de ataque y mantenimiento a cambio de nada.
    //
    // Con ellas se fue el buffer en memoria de las cargas por partes, que
    // ademas tenia una fuga conocida: una carga abandonada a medias **no se
    // liberaba hasta reiniciar el proceso**.

    /** Alta con el binario crudo en el cuerpo; tipo y nombre van como query params. */
    @PostMapping(value = "{id}/documentos/archivo", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasRole('AGENTE')")
    @Transactional
    public ResponseEntity<DocumentoSolicitudResponse> subirDocumento(
            @PathVariable long id,
            @RequestParam(required = false) String tipoDocumento,
            @RequestParam(required = false) String nombreArchivo,
            @RequestBody(required = false) byte[] cuerpo) {
        var actor = sesiones.exigirVigente();
        SolicitudService.FichaSolicitud solicitud = solicitudes.obtener(id, actor);
        if (enBlanco(tipoDocumento)) {
            throw new ReglaNegocioException("El tipo de documento es obligatorio.");
        }
        if (enBlanco(nombreArchivo)) {
            throw new ReglaNegocioException("El nombre del archivo es obligatorio.");
        }
        exigirExtensionPermitida(nombreArchivo);
        if (cuerpo == null) {
            throw new ReglaNegocioException("No se recibio ningun archivo.");
        }
        return guardarYRegistrar(id, solicitud, tipoDocumento, nombreArchivo, null, cuerpo, actor);
    }

    /** Revision individual: Conforme valida, Observado exige el porque. */
    @PatchMapping("{id}/documentos/{idDoc}/revisar")
    @PreAuthorize("hasRole('BROKER')")
    public DocumentoSolicitudResponse revisarDocumento(@PathVariable long id, @PathVariable long idDoc,
                                                       @RequestBody(required = false) RevisionDocumentoRequest dto) {
        // D-F4-5: el cable NO comprueba el alcance del broker sobre la
        // solicitud en esta operacion (conformar en bloque SI). Se replica: no
        // hay obtener() previo, solo el gate de rol.
        return DocumentoSolicitudResponse.desde(documentos.revisar(id, idDoc,
                dto == null ? null : dto.resultado(), dto == null ? null : dto.observaciones(),
                SesionActual.actor()));
    }

    /** Atajo "validar todos": deja conformes los pendientes y devuelve el expediente completo. */
    @PatchMapping("{id}/documentos/conformar")
    @PreAuthorize("hasRole('BROKER')")
    public List<DocumentoSolicitudResponse> conformarDocumentos(@PathVariable long id) {
        return documentos.conformarPendientes(id, SesionActual.actor()).stream()
                .map(DocumentoSolicitudResponse::desde)
                .toList();
    }

    // ------------------------------------------------------------------

    /** Preflight Core, cuota e I/O durable; registro y confirmación comparten su transacción. */
    private ResponseEntity<DocumentoSolicitudResponse> guardarYRegistrar(
            long id, SolicitudService.FichaSolicitud solicitud, String tipoDocumento,
            String nombreArchivo, String observaciones, byte[] contenido, Actor actor) {
        if (contenido.length == 0) {
            throw new ReglaNegocioException("El archivo esta vacio.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException(maximoSuperado());
        }
        var sid=ContextoDeActuacion.de(SesionActual.sesion());
        var c=new CuotaArchivosB0.Contexto(clasificador.preparar(ClasificadorArchivosExistentesB0.SOLICITUD,Long.toString(id),"TIPO:"+tipoDocumento,actor,sid),actor,sid);
        var guardado=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),java.util.UUID.randomUUID(),"DOCUMENTO-SOLICITUD:"+id+":"+tipoDocumento,
                NombresArchivo.extension(nombreArchivo).substring(1),NombresArchivo.contentType(nombreArchivo),contenido,cuota.admision(c));
        if(!actor.equals(sesiones.exigirVigente()))throw new AccesoNoAutorizadoException();
        var registrado=documentos.registrar(id,new DocumentoSolicitudService.DatosDocumento(tipoDocumento,nombreArchivo,guardado.clave(),observaciones),actor);
        cuota.confirmar(c,guardado);return creado(registrado);
    }

    private static void exigirExtensionPermitida(String nombreArchivo) {
        String extension = NombresArchivo.extension(nombreArchivo);
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new ReglaNegocioException("Tipo de archivo no permitido (" + extension + ").");
        }
    }

    private static String maximoSuperado() {
        return "El archivo supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.";
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ResponseEntity<DocumentoSolicitudResponse> creado(
            DocumentoSolicitudService.FichaDocumento documento) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentoSolicitudResponse.desde(documento));
    }

    private static PageResponse<SolicitudResponse> pagina(
            Pagina<SolicitudService.FichaSolicitud> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(SolicitudResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
