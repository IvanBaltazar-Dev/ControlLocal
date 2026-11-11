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
    public record AvanceRequest(String intencion, Long idBorrador, Integer versionEsperada,
                                Map<String, MotorDeCaptura.Respuesta> respuestas,
                                com.controllocal.service.captura.SuficienciaInmueble.Contexto contexto) {
    }

    /**
     * Los campos de un tipo + operación, en sus tres familias.
     *
     * <p>Van separadas y no en una lista plana porque <b>se comportan distinto
     * al cambiar la selección</b>: las comunes sobreviven, las del tipo se
     * descartan al cambiar de tipo —dejarlas ocultas guardaría el rubro de un
     * terreno— y las de la operación cambian hasta de rótulo.
     */
    public record DefinicionResponse(String intencion, String tipoPropiedad,
                                     List<String> operaciones,
                                     List<PreguntaResponse> comunes,
                                     List<PreguntaResponse> delTipo,
                                     List<BloqueOperacionResponse> deLaOperacion) {

        static DefinicionResponse desde(MotorDeCaptura.DefinicionCaptura definicion) {
            return new DefinicionResponse(definicion.intencion(), definicion.tipoPropiedad(),
                    definicion.operaciones(),
                    definicion.comunes().stream().map(PreguntaResponse::desde).toList(),
                    definicion.delTipo().stream().map(PreguntaResponse::desde).toList(),
                    definicion.deLaOperacion().stream().map(BloqueOperacionResponse::desde).toList());
        }
    }

    /**
     * La condición económica de <b>un</b> encargo, con su título ya puesto.
     *
     * <p>Una propiedad que se ofrece para venta y para alquiler devuelve dos de
     * estos: misma forma, distinto rótulo. La pantalla pinta una sección por
     * bloque y no necesita saber cuántos hay ni partir ninguna clave — que es
     * lo que ocurriría con una lista plana de {@code importe:VENTA}.
     */
    public record BloqueOperacionResponse(String operacion, String rotulo,
                                          List<PreguntaResponse> preguntas) {

        static BloqueOperacionResponse desde(MotorDeCaptura.BloqueOperacion bloque) {
            return new BloqueOperacionResponse(bloque.operacion(), bloque.rotulo(),
                    bloque.preguntas().stream().map(PreguntaResponse::desde).toList());
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
    /** Un valor admitido con su nombre: el cliente pinta el rótulo, devuelve el valor. */
    public record OpcionResponse(String valor, String rotulo) {

        static OpcionResponse desde(MotorDeCaptura.Opcion opcion) {
            return new OpcionResponse(opcion.valor(), opcion.rotulo());
        }
    }

    public record PreguntaResponse(String clave, String rotulo, String seccion, String familia,
                                   String control, String tipoDato, String unidad,
                                   List<OpcionResponse> opciones, String exigencia,
                                   boolean obligatoria, String ayuda, int orden,
                                   RestriccionesResponse restricciones, String referenciaCatalogo,String versionDefinicion) {

        static PreguntaResponse desde(MotorDeCaptura.Pregunta pregunta) {
            return new PreguntaResponse(pregunta.clave(), pregunta.rotulo(), pregunta.seccion(),
                    pregunta.familia(), pregunta.control(), pregunta.tipoDato(), pregunta.unidad(),
                    pregunta.opciones() == null ? null
                            : pregunta.opciones().stream().map(OpcionResponse::desde).toList(),
                    pregunta.exigencia(), pregunta.obligatoria(), pregunta.ayuda(),
                    pregunta.orden(), RestriccionesResponse.desde(pregunta.restricciones()),pregunta.referenciaCatalogo(),pregunta.versionDefinicion());
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
                                  LocalDateTime actualizadoEn, int version,
                                  com.controllocal.service.captura.SuficienciaInmueble.Contexto contexto,
                                  Map<String, MotorDeCaptura.MetadatoRespuesta> metadatos,
                                  com.controllocal.service.captura.RetornoCaptura retorno,
                                  com.controllocal.service.captura.SuficienciaInmueble.Evaluacion suficiencia,
                                  List<com.controllocal.service.captura.SuficienciaInmueble.Evaluacion> suficienciasEncargo) {

        static CapturaResponse desde(EstadoCaptura e) {
            return new CapturaResponse(e.idBorrador(), e.codigo(), e.intencion(), e.estado(),
                    e.canal(), e.conocido(), e.faltante(),
                    e.siguiente() == null ? null : PreguntaResponse.desde(e.siguiente()),
                    e.listoParaEjecutar(), e.entidadTipo(), e.idEntidad(), e.actualizadoEn(),
                    e.version(), e.contexto(), e.metadatos(), e.retorno(),e.suficiencia(),e.suficienciasEncargo());
        }
    }

    /**
     * Abre una captura o continua una existente. Sin {@code idBorrador} empieza
     * una nueva; con el, incorpora lo que llegue.
     */
    @PostMapping
    @com.controllocal.web.capacidades.CapacidadReconocida({"abrir_sesion_captura", "responder_captura"})
    public ResponseEntity<CapturaResponse> avanzar(
            @RequestBody(required = false) AvanceRequest dto, HttpServletRequest peticion) {
        AvanceRequest cuerpo = dto == null
                ? new AvanceRequest(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, Map.of(), null) : dto;
        EstadoCaptura estado = motor.avanzar(new MotorDeCaptura.Avance(cuerpo.intencion(), cuerpo.idBorrador(),
                cuerpo.versionEsperada(), cuerpo.respuestas(), cuerpo.contexto()), procedencias.de(peticion), SesionActual.actor());
        return ResponseEntity.status(cuerpo.idBorrador() == null ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CapturaResponse.desde(estado));
    }

    /**
     * Las capturas actualmente autorizadas para el actor; pertenecer al tenant no concede consulta de borradores ajenos.
     */
    @GetMapping
    @com.controllocal.web.capacidades.CapacidadReconocida({"capturas_en_curso"})
    public List<CapturaResponse> enCurso() {
        return motor.enCurso(SesionActual.actor()).stream()
                .map(CapturaResponse::desde)
                .toList();
    }

    /**
     * <b>Qué campos aplican a un tipo + una o dos operaciones.</b> Va ANTES de
     * {@code {id}} porque el router resolvería «definicion» como un id.
     *
     * <p>Es lo que necesita un formulario: la lista completa de una vez, en sus
     * tres familias. {@code POST /captura} sirve a un canal conversacional, que
     * pregunta de una en una; una pantalla las pinta todas.
     *
     * <p>{@code operaciones} admite una lista: {@code VENTA},
     * {@code ALQUILER} o {@code VENTA,ALQUILER}. Con las dos, la ficha física
     * viene una vez y la condición económica dos — una propiedad, dos encargos,
     * que es la afirmación central del modelo universal.
     *
     * <p><b>Existe para que el cliente no tenga su propia matriz.</b> Sin este
     * endpoint, Angular necesitaría una tabla «tipo → campos» y KAIROS otra, y
     * las dos empezarían a divergir del catálogo —que es la real— desde el
     * primer atributo que alguien añada.
     */
    /**
     * <b>Qué hay que decidir antes de poder pedir la definición.</b> Va ANTES
     * de {@code {id}} por el mismo motivo que «definicion»: el router
     * resolvería «apertura» como un id.
     *
     * <p>Son el tipo y la operación, y vienen con sus opciones y sus rótulos.
     * Existe para que ni la pantalla ni KAIROS escriban «primero el tipo y
     * luego la operación»: lo dice el motor, y el cliente pinta lo que le
     * llega, en el orden en que le llega.
     */
    @GetMapping("apertura")
    public List<PreguntaResponse> apertura(
            @RequestParam(defaultValue = "REGISTRAR_PROPIEDAD") String intencion) {
        return motor.apertura(intencion, SesionActual.actor()).stream()
                .map(PreguntaResponse::desde)
                .toList();
    }

    @GetMapping("definicion")
    @com.controllocal.web.capacidades.CapacidadReconocida({"catalogo_de_captura"})
    public DefinicionResponse definicion(
            @RequestParam(defaultValue = "REGISTRAR_PROPIEDAD") String intencion,
            @RequestParam String tipoPropiedad,
            @RequestParam(required = false) String operaciones,
            @RequestParam(required = false) String jurisdiccion) {
        return DefinicionResponse.desde(
                motor.definicion(intencion, tipoPropiedad, operaciones, jurisdiccion, SesionActual.actor()));
    }

    @GetMapping("{id}")
    @com.controllocal.web.capacidades.CapacidadReconocida("consultar_captura")
    public CapturaResponse consultar(@PathVariable long id) {
        return CapturaResponse.desde(motor.consultar(id, SesionActual.actor()));
    }

    /**
     * Corre el caso de uso con lo que el borrador sabe. Falla con la lista de
     * lo que falta si todavia no hay suficiente: el alta no se ejecuta a medias.
     */
    /** UC-K-07: leer el resultado original no emite otro comando ni reconstruye hechos posteriores. */
    @GetMapping("{id}/resultado")
    @PreAuthorize("hasRole('AGENTE')")
    @com.controllocal.web.capacidades.CapacidadReconocida("resultado_de_captura")
    public MotorDeCaptura.ResultadoConsultado resultado(@PathVariable long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia) {
        return motor.consultarResultado(id, claveIdempotencia, SesionActual.actor());
    }

    /** Abandonada a proposito. No se borra: que alguien la empezara tambien es un hecho. */
    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    @com.controllocal.web.capacidades.CapacidadReconocida("descartar_captura")
    public CapturaResponse descartar(@PathVariable long id, @RequestParam Integer versionEsperada,
                                     HttpServletRequest peticion) {
        return CapturaResponse.desde(motor.descartar(id, versionEsperada, procedencias.de(peticion), SesionActual.actor()));
    }

    public record PosposicionRequest(Integer versionEsperada, java.time.LocalDate hasta, String motivo) { }
    @PostMapping("{id}/revision/posponer")
    @PreAuthorize("hasRole('AGENTE')")
    @com.controllocal.web.capacidades.CapacidadReconocida("posponer_revision_captura")
    public CapturaResponse posponer(@PathVariable long id, @RequestBody PosposicionRequest datos,
                                    HttpServletRequest peticion) {
        if(datos==null)throw new com.controllocal.service.excepcion.ReglaNegocioException("Faltan los datos de la posposición.");
        return CapturaResponse.desde(motor.posponerRevision(id,datos.versionEsperada(),datos.hasta(),datos.motivo(),procedencias.de(peticion),SesionActual.actor()));
    }
}
