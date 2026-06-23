package com.controllocal.rest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.DocumentoSolicitudBusinessLogic;
import com.controllocal.bl.EvaluacionSolicitudBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.DocumentoSolicitudBusinessLogicImpl;
import com.controllocal.bl.impl.EvaluacionSolicitudBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.rest.almacen.AlmacenDocumentos;
import com.controllocal.rest.almacen.AlmacenException;
import com.controllocal.rest.almacen.Almacenes;
import com.controllocal.rest.almacen.NombresArchivo;
import com.controllocal.model.comercial.DocumentoSolicitud;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.TipoDocumentoRequerido;
import com.controllocal.model.comercial.enums.EstadoDocumentoSolicitud;
import com.controllocal.model.comercial.enums.ResultadoRevisionDocumento;
import com.controllocal.model.comercial.enums.TipoDocumentoSolicitud;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("solicitudes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SolicitudesRest {

    private static final DateTimeFormatter CODIGO_FORMATO =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final SolicitudAlquilerBusinessLogic solicitudes =
            new SolicitudAlquilerBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final DocumentoSolicitudBusinessLogic documentos =
            new DocumentoSolicitudBusinessLogicImpl();
    private final EvaluacionSolicitudBusinessLogic evaluaciones =
            new EvaluacionSolicitudBusinessLogicImpl();

    // Tipos que cuentan para el indicador "X/Y" de las pantallas: el checklist
    // estandar de alquiler (mismo conjunto que muestra el frontend).
    private static final Set<String> DOCUMENTOS_REQUERIDOS = Set.of(
            TipoDocumentoSolicitud.DOCUMENTO_IDENTIDAD.getCodigo(),
            TipoDocumentoSolicitud.FICHA_RUC.getCodigo(),
            TipoDocumentoSolicitud.VIGENCIA_PODER.getCodigo(),
            TipoDocumentoSolicitud.SUSTENTO_ECONOMICO.getCodigo(),
            TipoDocumentoSolicitud.GARANTIA.getCodigo(),
            TipoDocumentoSolicitud.DECLARACION_JURADA.getCodigo());

    // Limites de carga de archivos (espejo del validador del frontend).
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(".pdf", ".png", ".jpg", ".jpeg");

    // Subidas por partes en curso, por uploadId; se ensamblan al recibir el ultimo trozo.
    // El frontend sube en trozos chicos porque un POST con cuerpo grande rompe el
    // SocketsHttpHandler de .NET 10 contra GlassFish.
    private static final java.util.Map<String, java.io.ByteArrayOutputStream> CHUNKS_EN_CURSO =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.SolicitudResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        List<SolicitudAlquiler> fuente = solicitudesDelUsuario(SeguridadRest.usuario(request));
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        Map<Long, Set<String>> entregados = entregadosPorSolicitud();
        List<Dtos.SolicitudResponse> items = fuente.subList(desde, hasta).stream()
                .map(item -> aResponse(item, entregados))
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    @GET
    @Path("{id}")
    public Dtos.SolicitudResponse obtener(@PathParam("id") long id) {
        return aResponse(obtenerConAcceso(id, SeguridadRest.usuario(request)), entregadosPorSolicitud());
    }

    @GET
    @Path("codigo/{codigo}")
    public Dtos.SolicitudResponse obtenerPorCodigo(@PathParam("codigo") String codigo) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        return solicitudesDelUsuario(usuario).stream()
                .filter(item -> item.getCodigoSolicitud() != null
                        && item.getCodigoSolicitud().equalsIgnoreCase(codigo))
                .findFirst()
                .map(item -> aResponse(item, entregadosPorSolicitud()))
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"));
    }

    @POST
    public Response registrar(Dtos.SolicitudRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null || dto.idOportunidad() == null) {
            throw ApiException.badRequest("Los datos de la solicitud son obligatorios.");
        }
        SolicitudAlquiler solicitud = dto.aEntidad(usuario.idDominio());
        if (solicitud.getCodigoSolicitud() == null || solicitud.getCodigoSolicitud().isBlank()) {
            solicitud.setCodigoSolicitud(generarCodigoSolicitud());
        }
        Long id = solicitudes.registrar(solicitud);
        return Response.status(Response.Status.CREATED)
                .entity(aResponse(
                        solicitudes.buscarPorId(id)
                                .orElseThrow(() -> ApiException.noEncontrado("Solicitud")),
                        entregadosPorSolicitud()))
                .build();
    }

    @POST
    @Path("{id}/reenviar")
    public Dtos.SolicitudResponse reenviarAEvaluacion(@PathParam("id") long id) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        obtenerConAcceso(id, usuario);
        solicitudes.reenviarAEvaluacion(id);
        return aResponse(
                solicitudes.buscarPorId(id).orElseThrow(() -> ApiException.noEncontrado("Solicitud")),
                entregadosPorSolicitud());
    }

    @GET
    @Path("{id}/documentos")
    public List<Dtos.DocumentoSolicitudResponse> listarDocumentos(@PathParam("id") long id) {
        obtenerConAcceso(id, SeguridadRest.usuario(request));
        return documentos.listarTodos().stream()
                .filter(doc -> perteneceA(doc, id))
                .map(Dtos.DocumentoSolicitudResponse::desde)
                .toList();
    }

    // Revision de un documento individual por el broker: lo deja Validado (conforme) u
    // Observado (con la observacion especifica que el agente debe subsanar). Permite
    // observar un documento concreto, no solo la solicitud completa.
    @PATCH
    @Path("{id}/documentos/{idDoc}/revisar")
    public Dtos.DocumentoSolicitudResponse revisarDocumento(
            @PathParam("id") long id, @PathParam("idDoc") long idDoc, Dtos.RevisionDocumentoRequest dto) {
        SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        if (dto == null || dto.resultado() == null || dto.resultado().isBlank()) {
            throw ApiException.badRequest("El resultado de la revision es obligatorio.");
        }
        ResultadoRevisionDocumento resultado = ResultadoRevisionDocumento.fromCodigo(dto.resultado());
        if (resultado == ResultadoRevisionDocumento.OBSERVADO
                && (dto.observaciones() == null || dto.observaciones().isBlank())) {
            throw ApiException.badRequest("La observacion del documento es obligatoria.");
        }
        DocumentoSolicitud documento = documentos.buscarPorId(idDoc)
                .orElseThrow(() -> ApiException.noEncontrado("Documento"));
        if (!perteneceA(documento, id)) {
            throw ApiException.noEncontrado("Documento");
        }
        documento.actualizarRevision(resultado, dto.observaciones());
        documentos.actualizar(documento);
        return Dtos.DocumentoSolicitudResponse.desde(
                documentos.buscarPorId(idDoc).orElseThrow(() -> ApiException.noEncontrado("Documento")));
    }

    // Historial de evaluaciones de la solicitud (trazabilidad). Accesible al agente
    // dueno, al broker supervisor y al admin (mismo control que el resto del recurso).
    @GET
    @Path("{id}/evaluaciones")
    public List<Dtos.EvaluacionResponse> listarEvaluaciones(@PathParam("id") long id) {
        obtenerConAcceso(id, SeguridadRest.usuario(request));
        return evaluaciones.listarPorSolicitud(id).stream()
                .map(Dtos.EvaluacionResponse::desde)
                .toList();
    }

    @POST
    @Path("{id}/documentos")
    public Response registrarDocumento(@PathParam("id") long id, Dtos.DocumentoSolicitudRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        SolicitudAlquiler solicitudAcceso = obtenerConAcceso(id, usuario);
        if (dto == null || dto.tipoDocumento() == null || dto.tipoDocumento().isBlank()) {
            throw ApiException.badRequest("El tipo de documento es obligatorio.");
        }
        if (dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()) {
            throw ApiException.badRequest("El nombre del archivo es obligatorio.");
        }
        TipoDocumentoSolicitud tipo;
        try {
            tipo = TipoDocumentoSolicitud.fromCodigo(dto.tipoDocumento());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Tipo de documento invalido: " + dto.tipoDocumento());
        }

        String rutaArchivo = dto.rutaArchivo();
        String nombreFinal = dto.nombreArchivo();
        // Si llega el contenido en base64, el backend guarda el archivo en el almacen
        // (S3 o disco) y usa su clave como ruta. (El frontend usa esta via JSON porque el
        // POST binario octet-stream rompe el SocketsHttpHandler de .NET 10 contra GlassFish.)
        if (dto.contenidoBase64() != null && !dto.contenidoBase64().isBlank()) {
            String extension = NombresArchivo.extension(dto.nombreArchivo());
            if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
                throw ApiException.badRequest("Tipo de archivo no permitido (" + extension + ").");
            }
            byte[] contenido;
            try {
                contenido = java.util.Base64.getDecoder().decode(dto.contenidoBase64());
            } catch (IllegalArgumentException error) {
                throw ApiException.badRequest("El contenido del archivo (base64) es invalido.");
            }
            if (contenido.length == 0) {
                throw ApiException.badRequest("El archivo esta vacio.");
            }
            if (contenido.length > TAMANO_MAXIMO) {
                throw ApiException.badRequest(
                        "El archivo supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
            }
            String carpeta = solicitudAcceso.getCodigoSolicitud() != null
                    && !solicitudAcceso.getCodigoSolicitud().isBlank()
                    ? solicitudAcceso.getCodigoSolicitud()
                    : "SOL-" + id;
            try {
                AlmacenDocumentos.ArchivoGuardado guardado = Almacenes.actual().guardar(
                        carpeta, dto.nombreArchivo(), contenido, NombresArchivo.contentType(dto.nombreArchivo()));
                rutaArchivo = guardado.clave();
                nombreFinal = guardado.nombre();
            } catch (AlmacenException error) {
                throw new ApiException(502, "No se pudo guardar el documento en el almacen: " + error.getMessage());
            }
        }

        DocumentoSolicitud documento = new DocumentoSolicitud();
        TipoDocumentoRequerido tipoRequerido = new TipoDocumentoRequerido();
        tipoRequerido.setIdTipoDocumentoRequerido(tipo.getIdCatalogo());
        documento.setTipoDocumentoRequerido(tipoRequerido);
        documento.setNombreArchivo(nombreFinal);
        documento.setRutaArchivo(rutaArchivo);
        documento.setObservaciones(dto.observaciones());
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setIdSolicitud(id);
        documento.setSolicitudAlquiler(solicitud);
        // Queda Registrado con revision Pendiente; el broker luego lo deja Validado u Observado.
        documento.registrarEntrega();

        Long idDocumento = documentos.registrar(documento);
        return Response.status(Response.Status.CREATED)
                .entity(documentos.buscarPorId(idDocumento)
                        .map(Dtos.DocumentoSolicitudResponse::desde)
                        .orElseThrow(() -> ApiException.noEncontrado("Documento")))
                .build();
    }

    // Subida por partes: cada trozo llega como JSON pequeno (base64) y se acumula en
    // memoria por uploadId. Al llegar el ultimo se ensambla y se guarda. Es la via que usa
    // el frontend porque un POST con cuerpo grande rompe el HttpClient de .NET 10.
    @POST
    @Path("{id}/documentos/chunk")
    public Response subirDocumentoPorPartes(@PathParam("id") long id, Dtos.DocumentoChunkRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        SolicitudAlquiler solicitud = obtenerConAcceso(id, usuario);
        if (dto == null || dto.uploadId() == null || dto.uploadId().isBlank()) {
            throw ApiException.badRequest("Falta el identificador de carga.");
        }
        if (dto.tipoDocumento() == null || dto.tipoDocumento().isBlank()) {
            throw ApiException.badRequest("El tipo de documento es obligatorio.");
        }
        if (dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()) {
            throw ApiException.badRequest("El nombre del archivo es obligatorio.");
        }
        String extension = NombresArchivo.extension(dto.nombreArchivo());
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw ApiException.badRequest("Tipo de archivo no permitido (" + extension + ").");
        }
        byte[] parte;
        try {
            parte = java.util.Base64.getDecoder().decode(dto.dataBase64() == null ? "" : dto.dataBase64());
        } catch (IllegalArgumentException error) {
            CHUNKS_EN_CURSO.remove(dto.uploadId());
            throw ApiException.badRequest("El contenido del archivo (base64) es invalido.");
        }
        java.io.ByteArrayOutputStream buffer =
                CHUNKS_EN_CURSO.computeIfAbsent(dto.uploadId(), clave -> new java.io.ByteArrayOutputStream());
        synchronized (buffer) {
            if (buffer.size() + parte.length > TAMANO_MAXIMO) {
                CHUNKS_EN_CURSO.remove(dto.uploadId());
                throw ApiException.badRequest(
                        "El archivo supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
            }
            buffer.writeBytes(parte);
        }
        if (!Boolean.TRUE.equals(dto.ultimo())) {
            return Response.ok().build();
        }
        byte[] contenido = CHUNKS_EN_CURSO.remove(dto.uploadId()).toByteArray();
        TipoDocumentoSolicitud tipo;
        try {
            tipo = TipoDocumentoSolicitud.fromCodigo(dto.tipoDocumento());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Tipo de documento invalido: " + dto.tipoDocumento());
        }
        return guardarYRegistrarDocumento(id, solicitud, tipo, dto.nombreArchivo(), contenido);
    }

    // Subida por "handoff" local: el frontend escribio el archivo en una carpeta temporal
    // compartida (mismo equipo, mismo usuario) y aqui solo llega su NOMBRE en un POST chico.
    // El backend lo lee, lo guarda en el almacen y borra el temporal. Es la via que usa el
    // frontend porque cualquier POST con cuerpo grande rompe el HttpClient de .NET 10.
    @POST
    @Path("{id}/documentos/local")
    public Response subirDocumentoLocal(@PathParam("id") long id, Dtos.DocumentoLocalRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        SolicitudAlquiler solicitud = obtenerConAcceso(id, usuario);
        if (dto == null || dto.tipoDocumento() == null || dto.tipoDocumento().isBlank()) {
            throw ApiException.badRequest("El tipo de documento es obligatorio.");
        }
        if (dto.nombreArchivo() == null || dto.nombreArchivo().isBlank()) {
            throw ApiException.badRequest("El nombre del archivo es obligatorio.");
        }
        if (dto.archivoTemporal() == null || dto.archivoTemporal().isBlank()) {
            throw ApiException.badRequest("Falta el archivo temporal de la carga.");
        }
        // El temporal debe ser un nombre simple (sin rutas): se resuelve contra la carpeta
        // acordada. Asi el backend nunca lee un archivo arbitrario del disco.
        String temporal = dto.archivoTemporal();
        if (temporal.contains("/") || temporal.contains("\\") || temporal.contains("..")) {
            throw ApiException.badRequest("Nombre de archivo temporal invalido.");
        }
        String extension = NombresArchivo.extension(dto.nombreArchivo());
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw ApiException.badRequest("Tipo de archivo no permitido (" + extension + ").");
        }
        TipoDocumentoSolicitud tipo;
        try {
            tipo = TipoDocumentoSolicitud.fromCodigo(dto.tipoDocumento());
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Tipo de documento invalido: " + dto.tipoDocumento());
        }
        java.nio.file.Path carpeta =
                java.nio.file.Path.of(System.getProperty("user.home"), "controllocal", "uploads-tmp");
        java.nio.file.Path archivo = carpeta.resolve(temporal).normalize();
        if (!archivo.startsWith(carpeta)) {
            throw ApiException.badRequest("Ruta de archivo temporal invalida.");
        }
        byte[] contenido;
        try {
            contenido = java.nio.file.Files.readAllBytes(archivo);
        } catch (java.io.IOException error) {
            throw ApiException.badRequest("No se encontro el archivo temporal de la carga.");
        }
        try {
            return guardarYRegistrarDocumento(id, solicitud, tipo, dto.nombreArchivo(), contenido);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(archivo);
            } catch (java.io.IOException ignored) {
                // limpieza best-effort
            }
        }
    }

    // Guarda el contenido en el almacen (S3 o disco) y registra el documento como
    // Registrado/Pendiente. Compartido por las subidas por partes y por handoff local.
    private Response guardarYRegistrarDocumento(long id, SolicitudAlquiler solicitud,
                                                TipoDocumentoSolicitud tipo, String nombreArchivo, byte[] contenido) {
        if (contenido.length == 0) {
            throw ApiException.badRequest("El archivo esta vacio.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw ApiException.badRequest(
                    "El archivo supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
        }
        String carpeta = solicitud.getCodigoSolicitud() != null && !solicitud.getCodigoSolicitud().isBlank()
                ? solicitud.getCodigoSolicitud()
                : "SOL-" + id;
        AlmacenDocumentos.ArchivoGuardado guardado;
        try {
            guardado = Almacenes.actual().guardar(
                    carpeta, nombreArchivo, contenido, NombresArchivo.contentType(nombreArchivo));
        } catch (AlmacenException error) {
            throw new ApiException(502, "No se pudo guardar el documento en el almacen: " + error.getMessage());
        }
        DocumentoSolicitud documento = new DocumentoSolicitud();
        TipoDocumentoRequerido tipoRequerido = new TipoDocumentoRequerido();
        tipoRequerido.setIdTipoDocumentoRequerido(tipo.getIdCatalogo());
        documento.setTipoDocumentoRequerido(tipoRequerido);
        documento.setNombreArchivo(guardado.nombre());
        documento.setRutaArchivo(guardado.clave());
        SolicitudAlquiler ref = new SolicitudAlquiler();
        ref.setIdSolicitud(id);
        documento.setSolicitudAlquiler(ref);
        documento.registrarEntrega();
        Long idDocumento = documentos.registrar(documento);
        return Response.status(Response.Status.CREATED)
                .entity(documentos.buscarPorId(idDocumento)
                        .map(Dtos.DocumentoSolicitudResponse::desde)
                        .orElseThrow(() -> ApiException.noEncontrado("Documento")))
                .build();
    }

    // Sube el archivo real al almacen (S3 o disco, segun el selector hibrido) y persiste
    // sus metadatos. El cuerpo viaja como octet-stream; tipo y nombre como query params.
    @POST
    @Path("{id}/documentos/archivo")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response subirDocumento(@PathParam("id") long id,
                                   @QueryParam("tipoDocumento") String tipoDocumento,
                                   @QueryParam("nombreArchivo") String nombreArchivo,
                                   InputStream cuerpo) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        SolicitudAlquiler solicitud = obtenerConAcceso(id, usuario);
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            throw ApiException.badRequest("El tipo de documento es obligatorio.");
        }
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            throw ApiException.badRequest("El nombre del archivo es obligatorio.");
        }
        String extension = NombresArchivo.extension(nombreArchivo);
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw ApiException.badRequest("Tipo de archivo no permitido (" + extension + ").");
        }
        TipoDocumentoSolicitud tipo;
        try {
            tipo = TipoDocumentoSolicitud.fromCodigo(tipoDocumento);
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest("Tipo de documento invalido: " + tipoDocumento);
        }

        byte[] contenido = leerConLimite(cuerpo, TAMANO_MAXIMO);
        if (contenido.length == 0) {
            throw ApiException.badRequest("El archivo esta vacio.");
        }

        String carpeta = solicitud.getCodigoSolicitud() != null && !solicitud.getCodigoSolicitud().isBlank()
                ? solicitud.getCodigoSolicitud()
                : "SOL-" + id;
        AlmacenDocumentos.ArchivoGuardado guardado;
        try {
            guardado = Almacenes.actual().guardar(
                    carpeta, nombreArchivo, contenido, NombresArchivo.contentType(nombreArchivo));
        } catch (AlmacenException error) {
            throw new ApiException(502, "No se pudo guardar el documento en el almacen: " + error.getMessage());
        }

        DocumentoSolicitud documento = new DocumentoSolicitud();
        TipoDocumentoRequerido tipoRequerido = new TipoDocumentoRequerido();
        tipoRequerido.setIdTipoDocumentoRequerido(tipo.getIdCatalogo());
        documento.setTipoDocumentoRequerido(tipoRequerido);
        documento.setNombreArchivo(guardado.nombre());
        documento.setRutaArchivo(guardado.clave());
        SolicitudAlquiler ref = new SolicitudAlquiler();
        ref.setIdSolicitud(id);
        documento.setSolicitudAlquiler(ref);
        documento.registrarEntrega();

        Long idDocumento = documentos.registrar(documento);
        return Response.status(Response.Status.CREATED)
                .entity(documentos.buscarPorId(idDocumento)
                        .map(Dtos.DocumentoSolicitudResponse::desde)
                        .orElseThrow(() -> ApiException.noEncontrado("Documento")))
                .build();
    }

    private static byte[] leerConLimite(InputStream cuerpo, long maximo) {
        if (cuerpo == null) {
            throw ApiException.badRequest("No se recibio ningun archivo.");
        }
        try (cuerpo) {
            byte[] datos = cuerpo.readNBytes((int) maximo + 1);
            if (datos.length > maximo) {
                throw ApiException.badRequest(
                        "El archivo supera el maximo de " + (maximo / 1024 / 1024) + " MB.");
            }
            return datos;
        } catch (IOException error) {
            throw ApiException.badRequest("No se pudo leer el archivo: " + error.getMessage());
        }
    }

    // Cuenta, por solicitud, cuantos tipos del checklist requerido ya tienen un
    // documento entregado (Registrado o Validado). Una sola lectura para toda la lista.
    private Map<Long, Set<String>> entregadosPorSolicitud() {
        Map<Long, Set<String>> mapa = new HashMap<>();
        for (DocumentoSolicitud doc : documentos.listarTodos()) {
            Long idSolicitud = doc.getSolicitudAlquiler() != null
                    ? doc.getSolicitudAlquiler().getIdSolicitud() : null;
            if (idSolicitud == null) {
                continue;
            }
            if (doc.getEstado() != EstadoDocumentoSolicitud.REGISTRADO
                    && doc.getEstado() != EstadoDocumentoSolicitud.VALIDADO) {
                continue;
            }
            String codigoTipo = codigoTipo(doc);
            if (codigoTipo == null || !DOCUMENTOS_REQUERIDOS.contains(codigoTipo)) {
                continue;
            }
            mapa.computeIfAbsent(idSolicitud, clave -> new HashSet<>()).add(codigoTipo);
        }
        return mapa;
    }

    private Dtos.SolicitudResponse aResponse(SolicitudAlquiler solicitud, Map<Long, Set<String>> entregados) {
        int n = solicitud.getIdSolicitud() != null
                ? entregados.getOrDefault(solicitud.getIdSolicitud(), Set.of()).size()
                : 0;
        return Dtos.SolicitudResponse.desde(solicitud, n, DOCUMENTOS_REQUERIDOS.size());
    }

    private static boolean perteneceA(DocumentoSolicitud doc, long idSolicitud) {
        return doc.getSolicitudAlquiler() != null
                && doc.getSolicitudAlquiler().getIdSolicitud() != null
                && doc.getSolicitudAlquiler().getIdSolicitud() == idSolicitud;
    }

    private static String codigoTipo(DocumentoSolicitud doc) {
        TipoDocumentoRequerido tipo = doc.getTipoDocumentoRequerido();
        if (tipo == null || tipo.getIdTipoDocumentoRequerido() == null) {
            return null;
        }
        TipoDocumentoSolicitud tipoEnum =
                TipoDocumentoSolicitud.porIdCatalogo(tipo.getIdTipoDocumentoRequerido());
        return tipoEnum != null ? tipoEnum.getCodigo() : null;
    }

    private SolicitudAlquiler obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        SolicitudAlquiler solicitud = solicitudes.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Solicitud"));
        if (!puedeVer(usuario, solicitud)) {
            throw ApiException.prohibido();
        }
        return solicitud;
    }

    private List<SolicitudAlquiler> solicitudesDelUsuario(UsuarioAutenticado usuario) {
        return solicitudes.listarTodos().stream()
                .filter(item -> puedeVer(usuario, item))
                .toList();
    }

    private boolean puedeVer(UsuarioAutenticado usuario, SolicitudAlquiler solicitud) {
        AgenteInmobiliario agente = solicitud.getAgenteResponsable();
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if (usuario.tieneRol("ADMIN")) {
            return true;
        }
        if (usuario.tieneRol("AGENTE")) {
            return usuario.idDominio() == agente.getIdAgente();
        }
        if (usuario.tieneRol("BROKER")) {
            return brokers.puedeSupervisarAgente(usuario.idDominio(), agente.getIdAgente());
        }
        return false;
    }

    private static String generarCodigoSolicitud() {
        return "SOL-" + CODIGO_FORMATO.format(LocalDateTime.now());
    }
}
