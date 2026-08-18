package com.controllocal.domain.captura;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * <b>Donde se quedo una captura</b> (D-E4-2, V56).
 *
 * <h2>Lo que esta clase distingue, y que conviene no mezclar</h2>
 * <pre>
 *   historial conversacional   !=   estado transaccional de una captura
 * </pre>
 * El primero es de KAIROS: lo que se dijo, en que orden, con que palabras.
 * Puede perderse sin consecuencias — se vuelve a preguntar. El segundo es de
 * BROX: que datos hay, cuales faltan y a que operacion pertenecen. Ese no se
 * puede volver a preguntar sin hacer al usuario repetir lo que ya dijo, y es el
 * que permite que una captura empezada en WhatsApp se termine en la pantalla.
 *
 * <p>KAIROS necesitara los dos. De este, la fuente de verdad es BROX.
 *
 * <h2>Por que los datos van como JSON</h2>
 * Lo conocido depende de la INTENCION: registrar una propiedad pide tipo,
 * titulares y operacion; registrar un cliente pedira otra cosa. Columnarlo
 * seria una tabla por intencion y una migracion por pregunta nueva — el mismo
 * problema que el catalogo de atributos vino a resolver para el inmueble.
 *
 * <p>Como en {@code EventoDominio}, el JSON llega <b>ya serializado</b> desde
 * la capa de servicio: este modulo depende solo de {@code jakarta.persistence-api}
 * y no tiene con que fabricarlo. Escapar a mano se rompe con la primera
 * comilla en el nombre de una calle.
 *
 * <h2>La version no es decorativa</h2>
 * El caso de uso completo de esta tabla es "empieza KAIROS, sigue Angular".
 * Eso son dos escritores sobre la misma fila, y sin bloqueo optimista el
 * segundo pisa al primero en silencio — que es justo el dato que el usuario
 * acaba de dictar.
 */
@Entity
@Table(name = "borrador_captura")
public class BorradorCaptura extends EntidadDeOrganizacion {

    /** En curso: alguien la empezo y sigue viva. */
    public static final String EN_CURSO = "E";
    /** Ejecutado: el caso de uso corrio y produjo una entidad. */
    public static final String EJECUTADO = "J";
    /** Descartado: se abandono a proposito. */
    public static final String DESCARTADO = "D";

    /** La unica intencion de esta tanda. El vocabulario crece con los casos de uso. */
    public static final String REGISTRAR_PROPIEDAD = "REGISTRAR_PROPIEDAD";
    public static final Set<String> INTENCIONES = Set.of(REGISTRAR_PROPIEDAD);

    /** Mismo vocabulario que {@code evento_dominio.canal}: es la misma pregunta. */
    public static final String CANAL_SPA = "SPA";
    public static final Set<String> CANALES =
            Set.of("SPA", "WHATSAPP", "API", "SISTEMA");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_borrador")
    private Long id;

    /** Legible por una persona: es lo que se dice en voz alta por telefono. */
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "id_persona_rol", nullable = false)
    private Long idPersonaRol;

    @Column(name = "intencion", nullable = false, length = 40)
    private String intencion;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = EN_CURSO;

    @Column(name = "entidad_objetivo_tipo", length = 30)
    private String entidadObjetivoTipo;

    @Column(name = "entidad_objetivo_id")
    private Long entidadObjetivoId;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal = CANAL_SPA;

    /** Que agente lo abrio. NULL = lo abrio una persona directamente. */
    @Column(name = "agente", length = 30)
    private String agente;

    /**
     * De que conversacion nacio (V59).
     *
     * <p>{@link #canal} ya decia POR DONDE entro; esto dice DE CUAL. Es
     * lo que permite retomar diciendo <i>"sigamos con lo de ayer"</i> en vez de
     * con un id de borrador: un canal conversacional no tiene donde guardar un
     * numero entre una sesion y la siguiente, pero la conversacion es
     * exactamente lo que si conserva.
     *
     * <p>NULL cuando lo abrio la pantalla, que si tiene donde guardar el id.
     */
    @Column(name = "conversacion_id", length = 64)
    private String conversacionId;

    /** {@code {"tipoPropiedad":"D","operacion":"VENTA"}}, ya serializado. */
    @Column(name = "datos_conocidos", nullable = false)
    private String datosConocidos = "{}";

    /** {@code ["titular","direccion"]}, en el orden en que se van a preguntar. */
    @Column(name = "datos_faltantes", nullable = false)
    private String datosFaltantes = "[]";

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() {
        actualizadoEn = OffsetDateTime.now();
    }

    /**
     * Un borrador recien abierto: sabe quien y para que, y todavia nada mas.
     * El contenido llega despues, pregunta a pregunta.
     */
    public static BorradorCaptura abrir(Long idOrganizacion, String codigo, Long idPersonaRol,
                                        String intencion, String canal, String agente) {
        if (!INTENCIONES.contains(intencion)) {
            throw new IllegalArgumentException(
                    "Intencion de captura desconocida: \"" + intencion + "\". "
                            + "Anadirla exige declararla tambien en ck_borrador_intencion.");
        }
        BorradorCaptura borrador = new BorradorCaptura();
        borrador.setOrganizacionId(idOrganizacion);
        borrador.codigo = codigo;
        borrador.idPersonaRol = idPersonaRol;
        borrador.intencion = intencion;
        borrador.canal = canal == null ? CANAL_SPA : canal;
        borrador.agente = agente;
        return borrador;
    }

    /**
     * Guarda el estado de la captura tras una vuelta de preguntas. Los dos
     * documentos llegan serializados: lo que se sabe y lo que falta.
     */
    public void anotar(String conocidosJson, String faltantesJson) {
        exigirEnCurso();
        this.datosConocidos = (conocidosJson == null || conocidosJson.isBlank()) ? "{}" : conocidosJson;
        this.datosFaltantes = (faltantesJson == null || faltantesJson.isBlank()) ? "[]" : faltantesJson;
        this.actualizadoEn = OffsetDateTime.now();
    }

    /**
     * El caso de uso corrio y produjo algo. A partir de aqui el borrador es
     * historia: dice de donde salio esa propiedad y por que canal entro.
     */
    public void ejecutado(String entidadTipo, Long entidadId) {
        exigirEnCurso();
        if (entidadTipo == null || entidadId == null) {
            throw new IllegalArgumentException(
                    "Un borrador ejecutado tiene que decir que entidad produjo.");
        }
        this.estado = EJECUTADO;
        this.entidadObjetivoTipo = entidadTipo;
        this.entidadObjetivoId = entidadId;
        this.datosFaltantes = "[]";
        this.actualizadoEn = OffsetDateTime.now();
    }

    /** Abandonado a proposito. No se borra: que alguien empezara tambien es un hecho. */
    public void descartar() {
        exigirEnCurso();
        this.estado = DESCARTADO;
        this.actualizadoEn = OffsetDateTime.now();
    }

    @Transient
    public boolean estaEnCurso() {
        return EN_CURSO.equals(estado);
    }

    private void exigirEnCurso() {
        if (!estaEnCurso()) {
            throw new IllegalStateException(
                    "El borrador " + codigo + " ya no esta en curso (" + estado + "): "
                            + "no se puede seguir escribiendo en el.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getIdPersonaRol() {
        return idPersonaRol;
    }

    public String getIntencion() {
        return intencion;
    }

    public String getEstado() {
        return estado;
    }

    public String getEntidadObjetivoTipo() {
        return entidadObjetivoTipo;
    }

    public Long getEntidadObjetivoId() {
        return entidadObjetivoId;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal == null ? CANAL_SPA : canal;
    }

    public String getAgente() {
        return agente;
    }

    public void setAgente(String agente) {
        this.agente = agente;
    }

    public String getConversacionId() {
        return conversacionId;
    }

    /**
     * La conversacion se estampa una vez y no se reescribe.
     *
     * <p>Un borrador nace de una conversacion o no nace de ninguna. Si al
     * retomarlo desde otra se pudiera sobrescribir, el rastro diria que la
     * propiedad salio de la ultima conversacion que lo toco y no de la que la
     * pidio — y esa es justo la frase que V59 existe para poder construir.
     */
    public void nacioEn(String conversacionId) {
        if (this.conversacionId == null && conversacionId != null && !conversacionId.isBlank()) {
            this.conversacionId = conversacionId;
        }
    }

    public String getDatosConocidos() {
        return datosConocidos;
    }

    public String getDatosFaltantes() {
        return datosFaltantes;
    }

    public int getVersion() {
        return version;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
