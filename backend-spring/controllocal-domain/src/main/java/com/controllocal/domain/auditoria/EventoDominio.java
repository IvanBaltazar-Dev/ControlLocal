package com.controllocal.domain.auditoria;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Outbox transaccional: la fuente de las proyecciones (D-E4-1, V52).
 *
 * <p><b>Neo4j no entra como dependencia.</b> PostgreSQL sigue siendo la verdad y
 * el grafo sera una proyeccion reconstruible. Pero una proyeccion solo se puede
 * reconstruir si existe el rastro de LO QUE PASO, y ese rastro es imposible de
 * fabricar despues: nadie podra saber en 2027 el orden exacto en que una
 * oportunidad paso por sus estados si nunca se anoto. Escribirlo ahora cuesta
 * una tabla; no escribirlo cuesta la historia.
 *
 * <p><b>Se escribe en la MISMA transaccion que el hecho.</b> Si la transaccion
 * falla no queda un evento anunciando algo que nunca ocurrio; si tiene exito, el
 * evento esta garantizado sin dos fases ni cola externa.
 *
 * <p><b>Que NO es.</b> No sustituye a {@code historial_estado}, que audita
 * transiciones y lo seguira haciendo, ni a {@code evento_seguridad}, que es
 * append-only y tiene su escritor unico. Este alimenta proyecciones: el grafo,
 * el matcher, la inteligencia.
 *
 * <p>{@link #canal} y {@link #agente} son lo que permite responder <b>"quien
 * decidio esto"</b> cuando la peticion no llego tecleada. El actor sigue siendo
 * siempre una persona; el canal dice por donde entro y el agente, si lo hubo,
 * dice que sistema automatico la formulo en su nombre.
 */
@Entity
@Table(name = "evento_dominio")
public class EventoDominio extends EntidadDeOrganizacion {

    /**
     * <b>Por donde entro la peticion</b> (V59). No dice quien la formulo: para
     * eso esta {@link #agente}, y separarlos es lo que permite escribir "una
     * propiedad registrada desde WhatsApp por un agente automatico" sin tener
     * que elegir cual de las dos cosas se pierde.
     */
    public static final String CANAL_SPA = "SPA";
    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_API = "API";
    public static final String CANAL_SISTEMA = "SISTEMA";
    public static final Set<String> CANALES =
            Set.of(CANAL_SPA, CANAL_WHATSAPP, CANAL_API, CANAL_SISTEMA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    /** En pasado y con nombre de negocio: PROPIEDAD_REGISTRADA, ENCARGO_ABIERTO... */
    @Column(name = "tipo", nullable = false, length = 60)
    private String tipo;

    /** Mismo vocabulario que {@code historial_estado.entidad_tipo}. */
    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    /** Cuando ocurrio el HECHO, que puede no ser cuando se escribio la fila. */
    @Column(name = "ocurrido_en", nullable = false)
    private OffsetDateTime ocurridoEn;

    /** Quien. NULL solo si lo produjo el sistema sin actor. */
    @Column(name = "id_persona_rol")
    private Long idPersonaRol;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal = CANAL_SPA;

    /**
     * Que agente automatico formulo la peticion, y con que modelo razono.
     *
     * <p><b>{@code null} significa que la pidio una persona ella misma</b>, y
     * eso es un hecho, no un hueco: es lo que distingue una operacion tecleada
     * de una conversada con un agente.
     */
    @Column(name = "agente", length = 30)
    private String agente;

    @Column(name = "agente_modelo", length = 60)
    private String agenteModelo;

    @Column(name = "agente_modelo_version", length = 40)
    private String agenteModeloVersion;

    /**
     * Los ids de lo relacionado, para proyectar aristas sin volver a consultar:
     * {@code {"idPropiedad":12,"idCaptacion":34}}. El conjunto de relaciones
     * depende del tipo de evento, asi que columnarlo obligaria a una tabla por
     * tipo: va como JSON.
     *
     * <p>Se guarda como TEXTO y no como {@code jsonb} (V53) porque mapear un
     * Map a jsonb exige {@code @JdbcTypeCode} -- una anotacion de Hibernate --
     * y este modulo depende solo de {@code jakarta.persistence-api}. Mantener
     * el dominio independiente del ORM vale mas que un indice GIN que hoy no
     * consulta nadie; el CHECK de la BD garantiza que sigue siendo JSON valido,
     * asi que volver a jsonb es un cast cuando exista el proyector.
     */
    @Column(name = "carga_util", nullable = false)
    private String cargaUtil = "{}";

    /**
     * De que conversacion, de que turno y de que mensaje salio el hecho (V59).
     *
     * <p><b>No es el historial de la conversacion</b>, que pertenece a quien
     * conversa y puede perderse sin consecuencias. Son datos por HECHO: con que
     * frase se pidio, en que turno, de que conversacion, con que mensaje se
     * puede probar y que operacion se invoco.
     *
     * <p>{@link #mensajeId} es un <b>puntero a la evidencia, no la evidencia</b>.
     * El audio del que salio "ofrezco 165 mil" vive en el sistema
     * conversacional con su politica de conservacion; aqui queda con que ir a
     * buscarlo.
     *
     * <p>NULL cuando el hecho no salio de una conversacion, que es el caso de
     * casi todos: la pantalla no tiene turnos.
     */
    @Column(name = "conversacion_id", length = 64)
    private String conversacionId;

    @Column(name = "turno_id", length = 64)
    private String turnoId;

    @Column(name = "mensaje_id", length = 128)
    private String mensajeId;

    /** Lo que la persona escribio o dicto, literal. */
    @Column(name = "peticion")
    private String peticion;

    /**
     * La operacion invocada.
     *
     * <p>Es lo unico que BROX presencio de verdad. <b>Que creyo el agente que le
     * pedian</b> es interpretacion, y se guarda donde se interpreta.
     */
    @Column(name = "herramienta", length = 60)
    private String herramienta;

    /** NULL hasta que un consumidor lo procesa. Reprocesar = ponerlo a NULL. */
    @Column(name = "proyectado_en")
    private OffsetDateTime proyectadoEn;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * Un evento ocurrido AHORA por obra de una persona. Es la forma normal de
     * construirlo; la fecha explicita solo hace falta al reconstruir historia.
     */
    public static EventoDominio de(Long idOrganizacion, String tipo, String entidadTipo, Long entidadId,
                                   Long idPersonaRol, String canal) {
        EventoDominio e = new EventoDominio();
        e.setOrganizacionId(idOrganizacion);
        e.tipo = tipo;
        e.entidadTipo = entidadTipo;
        e.entidadId = entidadId;
        e.idPersonaRol = idPersonaRol;
        e.canal = canal == null ? CANAL_SPA : canal;
        e.ocurridoEn = OffsetDateTime.now();
        return e;
    }

    /**
     * El mismo evento con su carga util ya serializada.
     *
     * <p><b>El dominio no fabrica el JSON, solo lo guarda.</b> Serializarlo aqui
     * significaria escribir escapado a mano -- porque este modulo no tiene
     * Jackson -- y ese es exactamente el tipo de codigo que se rompe con una
     * comilla en el nombre de una calle. Lo arma la capa de servicio, que si
     * tiene un serializador de verdad, y aqui llega hecho.
     */
    public EventoDominio con(String cargaUtilJson) {
        setCargaUtil(cargaUtilJson);
        return this;
    }

    /**
     * De donde salio la peticion, cuando no la tecleo una persona.
     *
     * <p>Van juntos porque juntos son una respuesta y sueltos no lo son: la
     * frase sin el turno no se puede situar, el turno sin la conversacion no se
     * puede reconstruir, y el agente sin su modelo no se puede volver a
     * explicar el dia que su respuesta salga rara. Llegan ya recortados por la
     * capa de servicio — este modulo no valida longitudes de columna.
     *
     * @param agente {@code null} si la peticion la formulo una persona
     *               directamente. No es un hueco: es el hecho de que nadie
     *               automatico intervino
     */
    public EventoDominio porEncargoDe(String agente, String modelo, String modeloVersion,
                                      String conversacionId, String turnoId, String mensajeId,
                                      String peticion, String herramienta) {
        this.agente = agente;
        this.agenteModelo = modelo;
        this.agenteModeloVersion = modeloVersion;
        this.conversacionId = conversacionId;
        this.turnoId = turnoId;
        this.mensajeId = mensajeId;
        this.peticion = peticion;
        this.herramienta = herramienta;
        return this;
    }

    /** ¿Trae alguna relacion? Sirve para no proyectar eventos vacios. */
    @Transient
    public boolean tieneRelaciones() {
        return !"{}".equals(cargaUtil);
    }

    public void marcarProyectado() {
        this.proyectadoEn = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEntidadTipo() {
        return entidadTipo;
    }

    public void setEntidadTipo(String entidadTipo) {
        this.entidadTipo = entidadTipo;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(Long entidadId) {
        this.entidadId = entidadId;
    }

    public OffsetDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public void setOcurridoEn(OffsetDateTime ocurridoEn) {
        this.ocurridoEn = ocurridoEn;
    }

    public Long getIdPersonaRol() {
        return idPersonaRol;
    }

    public void setIdPersonaRol(Long idPersonaRol) {
        this.idPersonaRol = idPersonaRol;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getAgente() {
        return agente;
    }

    public String getAgenteModelo() {
        return agenteModelo;
    }

    public String getAgenteModeloVersion() {
        return agenteModeloVersion;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public String getConversacionId() {
        return conversacionId;
    }

    public String getTurnoId() {
        return turnoId;
    }

    public String getPeticion() {
        return peticion;
    }

    public String getHerramienta() {
        return herramienta;
    }

    public String getCargaUtil() {
        return cargaUtil;
    }

    public void setCargaUtil(String cargaUtil) {
        this.cargaUtil = (cargaUtil == null || cargaUtil.isBlank()) ? "{}" : cargaUtil;
    }

    public OffsetDateTime getProyectadoEn() {
        return proyectadoEn;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
