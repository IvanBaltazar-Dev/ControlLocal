package com.controllocal.domain.auditoria;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * <b>De donde salio ESTA afirmacion concreta</b> (microcorte 4.P, D-4P-1, V83).
 *
 * <h2>El problema que cierra</h2>
 * Hasta V83 la procedencia se registraba <b>del acto</b> y no <b>del dato</b>.
 * Un solo {@code PUT} puede cambiar {@code tipo_acceso} (lo vio el agente),
 * {@code zonificacion} (lo dice un certificado) y {@code vigilancia} (lo dijo el
 * propietario), y el evento de ese {@code PUT} —carga util
 * {@code {"idPropiedad": N}}— no explica ninguna de las tres.
 *
 * <p>Y una edicion o un borrado <b>destruian el pasado</b>:
 * {@code uq_atributo_propiedad_clave} deja una fila por (propiedad, clave), asi
 * que editar es un UPDATE que pisa, y retirar es un DELETE fisico.
 *
 * <h2>La identidad es LOGICA, nunca el id de la fila vigente</h2>
 * <pre>
 *   (organizacion, sujeto, agregado, clave)
 * </pre>
 * Esa eleccion es la que mete las <b>cinco</b> superficies de escritura en un
 * solo mecanismo, incluidas las dos que no tienen fila donde colgarse:
 *
 * <ul>
 *   <li>una clave <b>ESTRUCTURAL</b> ({@code metraje_total}, {@code piso},
 *       {@code partida_registral}, {@code oficina_registral}) escribe una
 *       columna de {@code propiedad} y <b>no crea fila</b> en
 *       {@code atributo_propiedad};</li>
 *   <li>una <b>retirada</b> hace desaparecer la fila vigente — si el linaje
 *       colgara de su id, el borrado se llevaria por delante la constancia de
 *       que ese valor existio.</li>
 * </ul>
 *
 * <h2>Dos ejes, nunca uno</h2>
 * <pre>
 *   naturaleza    DECLARADO | OBSERVADO | INFERIDO   a veces solo lo sabe quien captura
 *   canal/origen  SPA | WHATSAPP | API | SISTEMA     lo sabe el Core, siempre
 * </pre>
 *
 * <p><b>{@code naturaleza == null} NO es una cuarta naturaleza.</b> No existe
 * {@code DESCONOCIDO}, y no es cuestion de nombres: {@code null} significa que
 * <i>no sabemos como se obtuvo el hecho</i>, mientras que {@code INFERIDO}
 * significa que <i>si lo sabemos: por una inferencia</i>. Colapsarlas
 * convertiria «no consta» en un metodo de obtencion.
 *
 * <p>El resto del registro sigue teniendo procedencia <b>operacional</b> —quien,
 * cuando, por que canal—, que es cosa del otro eje. BROX registra quien lo
 * escribio y por donde; <b>no inventa como se conocio</b>.
 *
 * <h2>Append-only, y garantizado por la base</h2>
 * {@code tg_rastro_valor_append_only} lanza ante cualquier UPDATE o DELETE. El
 * trigger <b>no escribe</b>: solo impide. Escribe {@code LinajeDelValor}, en la
 * misma transaccion que el valor.
 */
@Entity
@Table(name = "rastro_valor_gobernado")
public class RastroValorGobernado extends EntidadDeOrganizacion {

    /** Un hecho del inmueble. {@code idAgregado} es {@code id_propiedad}. */
    public static final String SUJETO_PROPIEDAD = "PROPIEDAD";
    /** Una condicion pactada. {@code idAgregado} es {@code id_captacion}. */
    public static final String SUJETO_ENCARGO = "ENCARGO";

    /** La clave no tenia valor y ahora lo tiene. */
    public static final String VERBO_ALTA = "ALTA";
    /** Lo tenia y cambio. La fila conserva el que habia en {@code hallado_*}. */
    public static final String VERBO_EDICION = "EDICION";
    /** Lo tenia y se retiro. No queda valor vigente; el que se quito, si. */
    public static final String VERBO_RETIRADA = "RETIRADA";

    public static final String DECLARADO = "DECLARADO";
    public static final String OBSERVADO = "OBSERVADO";
    public static final String INFERIDO = "INFERIDO";

    /**
     * Las tres, y <b>solo</b> las tres. La ausencia se representa como ausencia:
     * anadir un cuarto valor aqui seria exactamente el {@code DESCONOCIDO} que
     * el modelo prohibe.
     */
    public static final Set<String> NATURALEZAS = Set.of(DECLARADO, OBSERVADO, INFERIDO);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rastro")
    private Long id;

    @Column(name = "sujeto", nullable = false, length = 9)
    private String sujeto;

    @Column(name = "id_agregado", nullable = false)
    private Long idAgregado;

    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "verbo", nullable = false, length = 8)
    private String verbo;

    @Column(name = "valor_texto")
    private String valorTexto;

    @Column(name = "valor_numero", precision = 14, scale = 4)
    private BigDecimal valorNumero;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;

    @Column(name = "valor_fecha")
    private LocalDate valorFecha;

    @Column(name = "valor_moneda", length = 3)
    private String valorMoneda;

    @Column(name = "es_multivalor", nullable = false)
    private boolean esMultivalor;

    @Column(name = "hallado_texto")
    private String halladoTexto;

    @Column(name = "hallado_numero", precision = 14, scale = 4)
    private BigDecimal halladoNumero;

    @Column(name = "hallado_booleano")
    private Boolean halladoBooleano;

    @Column(name = "hallado_fecha")
    private LocalDate halladoFecha;

    @Column(name = "hallado_moneda", length = 3)
    private String halladoMoneda;

    @Column(name = "naturaleza", length = 9)
    private String naturaleza;

    @Column(name = "canal", length = 20)
    private String canal;

    @Column(name = "agente", length = 30)
    private String agente;

    @Column(name = "agente_modelo", length = 60)
    private String agenteModelo;

    @Column(name = "agente_modelo_version", length = 40)
    private String agenteModeloVersion;

    @Column(name = "conversacion_id", length = 64)
    private String conversacionId;

    @Column(name = "turno_id", length = 64)
    private String turnoId;

    @Column(name = "mensaje_id", length = 128)
    private String mensajeId;

    @Column(name = "peticion")
    private String peticion;

    @Column(name = "herramienta", length = 60)
    private String herramienta;

    @Column(name = "id_persona_rol")
    private Long idPersonaRol;

    @Column(name = "rol_actor", length = 20)
    private String rolActor;

    @Column(name = "registrado_en", insertable = false, updatable = false)
    private OffsetDateTime registradoEn;

    @Column(name = "observado_en")
    private LocalDate observadoEn;

    @Column(name = "evidencia_ref", length = 300)
    private String evidenciaRef;

    @Column(name = "confianza", precision = 4, scale = 3)
    private BigDecimal confianza;

    protected RastroValorGobernado() {
        // JPA
    }

    /**
     * Una escritura, identificada por su clave logica.
     *
     * <p>El constructor pide lo que <b>siempre</b> se sabe. Todo lo demas
     * —valor, hallazgo, procedencia, naturaleza— se cuelga despues, porque cada
     * verbo trae un subconjunto distinto y un constructor con veintitantos
     * parametros esconderia cual falta.
     */
    public RastroValorGobernado(Long idOrganizacion, String sujeto, Long idAgregado, String clave,
                                String verbo) {
        setOrganizacionId(idOrganizacion);
        this.sujeto = sujeto;
        this.idAgregado = idAgregado;
        this.clave = clave;
        this.verbo = verbo;
    }

    /** El valor que QUEDA tras la escritura. Ausente en una retirada. */
    public RastroValorGobernado conValor(String texto, BigDecimal numero, Boolean booleano,
                                         LocalDate fecha, String moneda, boolean multivalor) {
        this.valorTexto = texto;
        this.valorNumero = numero;
        this.valorBooleano = booleano;
        this.valorFecha = fecha;
        this.valorMoneda = moneda;
        this.esMultivalor = multivalor;
        return this;
    }

    /**
     * Lo que el Core ENCONTRO justo antes de escribir.
     *
     * <p>Es una <b>constatacion del estado hallado</b>, no una genesis: no dice
     * cuando nacio ese valor, ni quien lo origino, ni por que canal, ni como se
     * conocio. Solo lo unico cierto — «en el momento de esta escritura, aqui
     * habia esto» —, que es lo que la propia operacion presencia.
     *
     * <p>Es lo que hace que la primera modificacion de un valor legado conserve
     * el anterior sin atribuirle una procedencia que nadie puede demostrar.
     */
    public RastroValorGobernado hallando(String texto, BigDecimal numero, Boolean booleano,
                                         LocalDate fecha, String moneda) {
        this.halladoTexto = texto;
        this.halladoNumero = numero;
        this.halladoBooleano = booleano;
        this.halladoFecha = fecha;
        this.halladoMoneda = moneda;
        return this;
    }

    /** Las nueve dimensiones del acto, enganchadas a ESTE valor. */
    public RastroValorGobernado porDondeEntro(String canal, String agente, String modelo,
                                              String modeloVersion, String conversacionId,
                                              String turnoId, String mensajeId, String peticion,
                                              String herramienta) {
        this.canal = canal;
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

    /**
     * Como se conocio el hecho, si consta. {@code null} se queda {@code null}:
     * el Core no lo deduce del canal, del actor ni del endpoint.
     */
    public RastroValorGobernado deNaturaleza(String naturaleza, LocalDate observadoEn,
                                             String evidenciaRef, BigDecimal confianza) {
        this.naturaleza = naturaleza;
        this.observadoEn = observadoEn;
        this.evidenciaRef = evidenciaRef;
        this.confianza = confianza;
        return this;
    }

    /** Quien lo escribio, con que rol. Lo deriva el Core; el usuario no lo teclea. */
    public RastroValorGobernado porActor(Long idPersonaRol, String rolActor) {
        this.idPersonaRol = idPersonaRol;
        this.rolActor = rolActor;
        return this;
    }

    public Long getId() {
        return id;
    }

    public String getSujeto() {
        return sujeto;
    }

    public Long getIdAgregado() {
        return idAgregado;
    }

    public String getClave() {
        return clave;
    }

    public String getVerbo() {
        return verbo;
    }

    public String getValorTexto() {
        return valorTexto;
    }

    public BigDecimal getValorNumero() {
        return valorNumero;
    }

    public Boolean getValorBooleano() {
        return valorBooleano;
    }

    public LocalDate getValorFecha() {
        return valorFecha;
    }

    public String getValorMoneda() {
        return valorMoneda;
    }

    public boolean esMultivalor() {
        return esMultivalor;
    }

    public String getHalladoTexto() {
        return halladoTexto;
    }

    public BigDecimal getHalladoNumero() {
        return halladoNumero;
    }

    public Boolean getHalladoBooleano() {
        return halladoBooleano;
    }

    public LocalDate getHalladoFecha() {
        return halladoFecha;
    }

    public String getHalladoMoneda() {
        return halladoMoneda;
    }

    public String getNaturaleza() {
        return naturaleza;
    }

    public String getCanal() {
        return canal;
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

    public String getConversacionId() {
        return conversacionId;
    }

    public String getTurnoId() {
        return turnoId;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public String getPeticion() {
        return peticion;
    }

    public String getHerramienta() {
        return herramienta;
    }

    public Long getIdPersonaRol() {
        return idPersonaRol;
    }

    public String getRolActor() {
        return rolActor;
    }

    public OffsetDateTime getRegistradoEn() {
        return registradoEn;
    }

    public LocalDate getObservadoEn() {
        return observadoEn;
    }

    public String getEvidenciaRef() {
        return evidenciaRef;
    }

    public BigDecimal getConfianza() {
        return confianza;
    }
}
