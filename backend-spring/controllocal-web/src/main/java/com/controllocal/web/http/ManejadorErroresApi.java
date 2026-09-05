package com.controllocal.web.http;

import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.ErrorMfaException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Equivalente Spring del ApiExceptionMapper Jakarta: todos los errores salen
 * como {"error": mensaje} con los mismos textos del contrato congelado.
 */
@RestControllerAdvice
public class ManejadorErroresApi {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresApi.class);

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException error) {
        return respuesta(HttpStatus.UNAUTHORIZED, error.getMessage());
    }

    @ExceptionHandler(DemasiadasSolicitudesException.class)
    public ResponseEntity<ErrorResponse> demasiadasSolicitudes(DemasiadasSolicitudesException error) {
        // El cuerpo es el CONGELADO; `Retry-After` es aditivo (D-S0-21) y dice
        // la espera real, que con el bloqueo progresivo puede pasar del minuto
        // que menciona el mensaje.
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(error.reintentarEnSegundos()))
                .body(new ErrorResponse(error.getMessage()));
    }

    @ExceptionHandler({ReglaNegocioException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> reglaNegocio(RuntimeException error) {
        return respuesta(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    /**
     * Mismo 400 y mismo {@code error} que cualquier otra regla de negocio; lo
     * que aniade es el {@code codigo} ESTABLE del fallo de segundo factor. El
     * SPA tiene que reaccionar distinto a "el codigo esta mal", "ese codigo ya
     * se uso" y "el desafio caduco", y sin esto solo podia distinguirlos
     * comparando el texto en español — que es traducible y se retoca sin
     * pensar que algo depende de el.
     *
     * <p>Va en su propio handler y no dentro del anterior porque Spring elige
     * el mas especifico: {@link ErrorMfaException} extiende
     * {@code ReglaNegocioException} y aterriza aqui sin tocar el resto.
     */
    @ExceptionHandler(ErrorMfaException.class)
    public ResponseEntity<ErrorResponse> errorMfa(ErrorMfaException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(error.getMessage(), error.getCodigo()));
    }

    // 404/403 desde la capa web (locales) y desde la capa service (F2: el
    // scope se impone en el service). Ambos pares producen el mismo cuerpo.
    @ExceptionHandler({RecursoNoEncontradoException.class, NoEncontradoException.class})
    public ResponseEntity<ErrorResponse> recursoNoEncontrado(RuntimeException error) {
        return respuesta(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler({AccesoDenegadoException.class, AccesoNoAutorizadoException.class})
    public ResponseEntity<ErrorResponse> accesoDenegado(RuntimeException error) {
        return respuesta(HttpStatus.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(ErrorAlmacenException.class)
    public ResponseEntity<ErrorResponse> errorAlmacen(ErrorAlmacenException error) {
        return respuesta(HttpStatus.BAD_GATEWAY, error.getMessage());
    }

    /**
     * @PreAuthorize lanza AccessDeniedException DENTRO del metodo del
     * controlador: sin este handler la atraparia el catch-all como 500 en
     * vez del 403 del contrato (mismo texto que el AccessDeniedHandler).
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegadoSeguridad(
            org.springframework.security.access.AccessDeniedException error) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permisos para esta operacion.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException error) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud es obligatorio o tiene un formato inválido.");
    }

    /**
     * Violacion de UNIQUE (documento/correo repetido, etc.): 409 nombrando el
     * dato en conflicto, igual que el mapper Jakarta.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> unicidadViolada(DataIntegrityViolationException error) {
        String mensajeSql = error.getMostSpecificCause().getMessage();
        return respuesta(HttpStatus.CONFLICT, mensajeDuplicado(mensajeSql));
    }

    /**
     * Mismo 409 que el de arriba, pero cuando el caso de uso detecta el choque
     * ANTES de llegar a la base de datos y puede decir que dato corregir. El
     * codigo de estado es el del cable; solo el texto es mas concreto que el
     * generico de la v1, y el texto de un 409 no esta congelado (si lo estan
     * los de 401/403/429).
     */
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoException error) {
        return respuesta(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> recursoInexistente(NoResourceFoundException error) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
    }

    /**
     * Metodo o media type equivocados. En la v1 son NotAllowedException /
     * NotSupportedException, es decir WebApplicationException, y el mapper
     * Jakarta conserva SU estado (405/415). Sin estos handlers caerian en el
     * catch-all como 500 y el contrato dejaria de cuadrar en el codigo.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException error) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "HTTP 405 Method Not Allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> medioNoSoportado(HttpMediaTypeNotSupportedException error) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "HTTP 415 Unsupported Media Type");
    }

    /**
     * <b>Un parametro con el tipo equivocado es un error de quien llama, no del
     * servidor</b> (2026-09-02).
     *
     * <p>Sin este handler, {@code ?page=abc} respondia <b>500</b>: Spring lanza
     * {@link MethodArgumentTypeMismatchException}, que desciende de
     * {@code BeansException} y <b>no</b> de {@code IllegalArgumentException}, asi
     * que se escapaba del 400 de las reglas de negocio y caia en el catch-all.
     * Y el catch-all adjunta {@code Detalle: <causa raiz>}, de modo que el
     * cuerpo publicaba el mensaje interno de la conversion —el nombre del tipo
     * Java incluido— a cualquiera que escribiera mal un numero.
     *
     * <p>Es transversal a proposito: el defecto no era de un listado, era de
     * todos los endpoints con un parametro tipado. Arreglarlo en un controlador
     * habria dejado el mismo 500 en los otros veinticinco.
     *
     * <p>El mensaje nombra el parametro y lo que se esperaba, y <b>nada mas</b>:
     * quien se equivoca escribiendo una URL necesita saber que corregir, no como
     * se llama la clase que fallo al convertirlo.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroConTipoEquivocado(
            MethodArgumentTypeMismatchException error) {
        Class<?> esperado = error.getRequiredType();
        String tipo = esperado != null && Number.class.isAssignableFrom(esperado)
                ? "un numero entero" : "otro tipo de valor";
        return respuesta(HttpStatus.BAD_REQUEST,
                "El parametro \"" + error.getName() + "\" espera " + tipo + ".");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorNoControlado(Exception error) {
        LOG.error("[ControlLocal API v2] Error no controlado", error);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo completar la operacion." + detalleCausaRaiz(error));
    }

    private static String mensajeDuplicado(String mensajeSql) {
        String m = mensajeSql == null ? "" : mensajeSql.toLowerCase();
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

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse(mensaje));
    }
}
