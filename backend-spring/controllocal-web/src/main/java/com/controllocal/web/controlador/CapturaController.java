package com.controllocal.web.controlador;

import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.EstadoCaptura;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <b>El motor de captura por el cable</b> (D-E4-2).
 *
 * <h2>Que responde</h2>
 * Las cinco preguntas de una captura, siempre las mismas y siempre en servidor:
 * <pre>
 *   ¿Que intenta hacer el usuario?
 *   ¿Que sabemos?
 *   ¿Que falta?
 *   ¿Que pregunta corresponde ahora?
 *   ¿Tenemos suficiente para ejecutar?
 * </pre>
 *
 * <h2>Quien lo consume</h2>
 * Angular y KAIROS, los dos igual. Ninguno de los dos puede tener una segunda
 * version de estas reglas: la lista de lo que se pregunta para un terreno sale
 * del catalogo, y con dos copias divergiendo, anadir un atributo pasaria a ser
 * un despliegue de tres piezas.
 *
 * <h2>Avanzar y ejecutar estan separados a proposito</h2>
 * {@code POST /captura} anota lo que se sabe y no escribe nada del negocio;
 * {@code POST /captura/{id}/ejecutar} corre el caso de uso. Un canal
 * conversacional necesita poder <b>confirmar antes de escribir</b>, y con una
 * sola llamada que hiciera las dos cosas no habria momento para confirmar.
 */
@RestController
@RequestMapping("captura")
public class CapturaController {

    private final MotorDeCaptura motor;
    private final ProcedenciaDeCabeceras procedencias;

    public CapturaController(MotorDeCaptura motor, ProcedenciaDeCabeceras procedencias) {
        this.motor = motor;
        this.procedencias = procedencias;
    }

    /** Lo que el cliente sabe hasta ahora, mas lo que el motor responde. */
    public record AvanceRequest(String intencion, Long idBorrador, Map<String, String> datos) {
    }

    /**
     * Los campos de un tipo + operación, en sus tres familias.
     *
     * <p>Van separadas y no en una lista plana porque <b>se comportan distinto
     * al cambiar la selección</b>: las comunes sobreviven, las del tipo se
     * descartan al cambiar de tipo —dejarlas ocultas guardaría el rubro de un
     * terreno— y las de la operación cambian hasta de rótulo.
     */
    public record DefinicionResponse(String intencion, String tipoPropiedad, String operacion,
                                     List<PreguntaResponse> comunes,
                                     List<PreguntaResponse> delTipo,
                                     List<PreguntaResponse> deLaOperacion) {

        static DefinicionResponse desde(MotorDeCaptura.DefinicionCaptura definicion) {
            return new DefinicionResponse(definicion.intencion(), definicion.tipoPropiedad(),
                    definicion.operacion(),
                    definicion.comunes().stream().map(PreguntaResponse::desde).toList(),
                    definicion.delTipo().stream().map(PreguntaResponse::desde).toList(),
                    definicion.deLaOperacion().stream().map(PreguntaResponse::desde).toList());
        }
    }

    /**
     * Un campo, con <b>todo lo que hace falta para pintarlo sin conocerlo</b>.
     *
     * <p>Si un cliente acaba escribiendo {@code if (clave === 'piso')}, la
     * matriz «tipo → campos» no se habrá eliminado: se habrá mudado. Por eso
     * cada campo declara su {@code familia}, su {@code control}, su
     * {@code orden} y sus {@code restricciones}, y el cliente solo necesita
     * saber dibujar controles genéricos.
     *
     * <p>{@code control} va aparte de {@code tipoDato} a propósito: aquel es el
     * tipo del DOMINIO —lo que la base guarda— y este es el de la INTERFAZ. Un
     * {@code DECIMAL} con unidad {@code moneda} y otro con unidad {@code m2} se
     * guardan igual y se piden distinto.
     */
    public record PreguntaResponse(String clave, String rotulo, String familia, String control,
                                   String tipoDato, String unidad, List<String> opciones,
                                   boolean obligatoria, String ayuda, int orden,
                                   RestriccionesResponse restricciones) {

        static PreguntaResponse desde(MotorDeCaptura.Pregunta pregunta) {
            return new PreguntaResponse(pregunta.clave(), pregunta.rotulo(), pregunta.familia(),
                    pregunta.control(), pregunta.tipoDato(), pregunta.unidad(),
                    pregunta.opciones(), pregunta.obligatoria(), pregunta.ayuda(),
                    pregunta.orden(), RestriccionesResponse.desde(pregunta.restricciones()));
        }
    }

    /** Los límites de un valor. Todo opcional: el cliente valida lo que se afirme. */
    public record RestriccionesResponse(java.math.BigDecimal minimo, java.math.BigDecimal maximo,
                                        Integer longitudMaxima, Integer decimales) {

        static RestriccionesResponse desde(MotorDeCaptura.Restricciones restricciones) {
            return restricciones == null ? null
                    : new RestriccionesResponse(restricciones.minimo(), restricciones.maximo(),
                            restricciones.longitudMaxima(), restricciones.decimales());
        }
    }

    /**
     * El estado de la captura.
     *
     * <p>{@code faltante} viene en el ORDEN en que se va a preguntar, y
     * {@code siguiente} es el primero ya resuelto contra el catalogo. El
     * cliente no tiene que decidir por donde seguir.
     */
    public record CapturaResponse(Long idBorrador, String codigo, String intencion, String estado,
                                  String canal, Map<String, Object> conocido,
                                  List<String> faltante, PreguntaResponse siguiente,
                                  boolean listoParaEjecutar, String entidadTipo, Long idEntidad,
                                  LocalDateTime actualizadoEn) {

        static CapturaResponse desde(EstadoCaptura e) {
            return new CapturaResponse(e.idBorrador(), e.codigo(), e.intencion(), e.estado(),
                    e.canal(), e.conocido(), e.faltante(),
                    e.siguiente() == null ? null : PreguntaResponse.desde(e.siguiente()),
                    e.listoParaEjecutar(), e.entidadTipo(), e.idEntidad(), e.actualizadoEn());
        }
    }

    public record EjecucionResponse(Long idBorrador, Long idPropiedad, String codigoPropiedad,
                                    List<Long> idsEncargos, boolean reintento) {
    }

    /**
     * Abre una captura o continua una existente. Sin {@code idBorrador} empieza
     * una nueva; con el, incorpora lo que llegue.
     */
    @PostMapping
    public ResponseEntity<CapturaResponse> avanzar(
            @RequestBody(required = false) AvanceRequest dto, HttpServletRequest peticion) {
        AvanceRequest cuerpo = dto == null
                ? new AvanceRequest(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, Map.of()) : dto;
        EstadoCaptura estado = motor.avanzar(cuerpo.intencion(), cuerpo.idBorrador(),
                cuerpo.datos(), procedencias.de(peticion), SesionActual.actor());
        return ResponseEntity.status(cuerpo.idBorrador() == null ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CapturaResponse.desde(estado));
    }

    /**
     * Lo que el tenant tiene a medias. No filtra por quien lo empezo: el
     * borrador es de la organizacion, que es lo que permite que una captura
     * iniciada por KAIROS la termine otra persona.
     */
    @GetMapping
    public List<CapturaResponse> enCurso() {
        return motor.enCurso(SesionActual.actor()).stream()
                .map(CapturaResponse::desde)
                .toList();
    }

    /**
     * <b>Qué campos aplican a un tipo + operación.</b> Va ANTES de {@code {id}}
     * porque el router resolvería «definicion» como un id.
     *
     * <p>Es lo que necesita un formulario: la lista completa de una vez, en sus
     * tres familias. {@code POST /captura} sirve a un canal conversacional, que
     * pregunta de una en una; una pantalla las pinta todas.
     *
     * <p><b>Existe para que el cliente no tenga su propia matriz.</b> Sin este
     * endpoint, Angular necesitaría una tabla «tipo → campos» y KAIROS otra, y
     * las dos empezarían a divergir del catálogo —que es la real— desde el
     * primer atributo que alguien añada.
     */
    @GetMapping("definicion")
    public DefinicionResponse definicion(
            @RequestParam(defaultValue = "REGISTRAR_PROPIEDAD") String intencion,
            @RequestParam String tipoPropiedad,
            @RequestParam String operacion) {
        return DefinicionResponse.desde(
                motor.definicion(intencion, tipoPropiedad, operacion, SesionActual.actor()));
    }

    @GetMapping("{id}")
    public CapturaResponse consultar(@PathVariable long id) {
        return CapturaResponse.desde(motor.consultar(id, SesionActual.actor()));
    }

    /**
     * Corre el caso de uso con lo que el borrador sabe. Falla con la lista de
     * lo que falta si todavia no hay suficiente: el alta no se ejecuta a medias.
     */
    @PostMapping("{id}/ejecutar")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<EjecucionResponse> ejecutar(
            @PathVariable long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            HttpServletRequest peticion) {
        MotorDeCaptura.Ejecucion resultado = motor.ejecutar(id, claveIdempotencia,
                procedencias.de(peticion), SesionActual.actor());
        EjecucionResponse cuerpo = new EjecucionResponse(resultado.idBorrador(),
                resultado.idPropiedad(), resultado.codigoPropiedad(), resultado.idsEncargos(),
                resultado.reintento());
        return ResponseEntity.status(resultado.reintento() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(cuerpo);
    }

    /** Abandonada a proposito. No se borra: que alguien la empezara tambien es un hecho. */
    @DeleteMapping("{id}")
    public CapturaResponse descartar(@PathVariable long id) {
        return CapturaResponse.desde(motor.descartar(id, SesionActual.actor()));
    }
}
