package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * <b>Una vuelta de una negociacion</b> (WP-A, D-N-2).
 *
 * <p>Cuelga de la OPORTUNIDAD —con quien se negocia— y del ENCARGO —sobre que—.
 * Dos interesados por el mismo inmueble son dos oportunidades y dos cadenas de
 * ofertas separadas; colgarlas solo del encargo las mezclaria en una sola linea
 * que ninguna lectura podria volver a separar.
 *
 * <p>Maquina: {@code P} presentada &rarr; {@code A} aceptada | {@code R}
 * rechazada | {@code S} superada por contraoferta | {@code T} retirada. Los
 * cuatro desenlaces son terminales, y el estado SOLO muta via
 * {@code Transiciones} (RC-002), asi que cada vuelta deja su fila en
 * {@code historial_estado} con actor y motivo.
 *
 * <p><b>Rechazar una oferta no cierra la oportunidad.</b> Es la asimetria que
 * esta clase existe para conservar: que el propietario diga que no a una cifra
 * no significa que el interesado se haya ido. La caida de la oportunidad es un
 * acto aparte, con su motivo tipificado
 * ({@link MotivoNoContinuidad}).
 */
@Entity
@Table(name = "oferta")
public class Oferta extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "OFERTA";

    // ------------------------------------------------------------------
    // Estados. Codigos locales de este agregado, como el resto del dominio:
    // una misma letra significa otra cosa en otra tabla.
    // ------------------------------------------------------------------

    /** Viva: es la que hay sobre la mesa ahora mismo. */
    public static final String PRESENTADA = "P";
    public static final String ACEPTADA = "A";
    public static final String RECHAZADA = "R";
    /** La respondio una contraoferta; deja de estar sobre la mesa. */
    public static final String SUPERADA = "S";
    /** La retiro quien la presento antes de que le respondieran. */
    public static final String RETIRADA = "T";

    public static final Set<String> ESTADOS =
            Set.of(PRESENTADA, ACEPTADA, RECHAZADA, SUPERADA, RETIRADA);

    /**
     * Como se llama un estado cuando lo lee una persona. Vive aqui y no en el
     * cliente por la misma razon que {@code PrecioPropiedad.rotuloDelHito}: un
     * {@code switch} que convierta {@code "S"} en «Superada» es la tabla del
     * dominio reescrita en la interfaz, y con dos interfaces —BROX Web y
     * KAIROS— habria dos (D-A-1 §6).
     */
    public static String rotuloDelEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case PRESENTADA -> "Presentada";
            case ACEPTADA -> "Aceptada";
            case RECHAZADA -> "Rechazada";
            case SUPERADA -> "Superada por nueva propuesta";
            case RETIRADA -> "Retirada";
            default -> estado;
        };
    }

    // ------------------------------------------------------------------
    // Quien la presento. NO se deduce de la ronda: una negociacion puede
    // empezar por cualquiera de los dos lados, y deducirlo seria inventar
    // quien hablo primero.
    // ------------------------------------------------------------------

    public static final String LA_PRESENTA_EL_INTERESADO = "I";
    public static final String LA_PRESENTA_EL_PROPIETARIO = "P";
    public static final Set<String> LADOS =
            Set.of(LA_PRESENTA_EL_INTERESADO, LA_PRESENTA_EL_PROPIETARIO);

    public static String rotuloDelLado(String lado) {
        return switch (lado == null ? "" : lado) {
            case LA_PRESENTA_EL_INTERESADO -> "Interesado";
            case LA_PRESENTA_EL_PROPIETARIO -> "Propietario";
            default -> lado;
        };
    }

    /** El otro lado de la mesa: quien responde a una oferta de este. */
    public static String ladoContrario(String lado) {
        return LA_PRESENTA_EL_INTERESADO.equals(lado)
                ? LA_PRESENTA_EL_PROPIETARIO : LA_PRESENTA_EL_INTERESADO;
    }

    /**
     * Contra que precio se congelo la referencia (D-N-1). Son los mismos dos
     * codigos que {@code PrecioPropiedad}: {@code P} el publicado, {@code U} el
     * autorizado. No hay un tercero, y no hay defecto: si el encargo no tiene
     * ninguno de los dos, la referencia entera queda FALTANTE.
     */
    public static final String REFERENCIA_PUBLICADO = "P";
    public static final String REFERENCIA_AUTORIZADO = "U";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oferta")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public long getVersion() { return version; }

    @Column(name = "id_oferta_anterior", updatable = false)
    private Long idOfertaAnterior;
    public Long getIdOfertaAnterior() { return idOfertaAnterior; }
    public void setIdOfertaAnterior(Long idOfertaAnterior) { this.idOfertaAnterior=idOfertaAnterior; }

    @Column(name = "id_oportunidad", nullable = false)
    private Long idOportunidad;

    /**
     * El encargo del que sale. Se guarda ademas de poder deducirse por la
     * oportunidad: es lo que permite leer la serie economica de un encargo sin
     * pasar por la mesa. Un trigger exige que coincida con el de la oportunidad.
     */
    @Column(name = "id_captacion", nullable = false)
    private Long idCaptacion;

    @Column(name = "ronda", nullable = false)
    private Integer ronda;

    @Column(name = "presentada_por", nullable = false, length = 1)
    private String presentadaPor;

    @Column(name = "monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "condiciones", length = 500)
    private String condiciones;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Quien hizo el asiento, no quien ofrecio: eso lo dice {@link #presentadaPor}. */
    @Column(name = "id_rol_registra", nullable = false)
    private Long idRolRegistra;

    @Column(name = "origen", nullable = false, length = 10)
    private String origen;

    // ------------------------------------------------------------------
    // La referencia congelada. Los cuatro campos van juntos o no va ninguno
    // (lo impide ck_oferta_referencia_completa): un monto sin su moneda, o una
    // moneda sin el hito del que salio, es media referencia.
    // ------------------------------------------------------------------

    @Column(name = "hito_referencia", length = 1)
    private String hitoReferencia;

    @Column(name = "id_precio_referencia")
    private Long idPrecioReferencia;

    @Column(name = "monto_referencia", precision = 14, scale = 2)
    private BigDecimal montoReferencia;

    @Column(name = "moneda_referencia", length = 3)
    private String monedaReferencia;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    // ------------------------------------------------------------------
    // Transicionable
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return ENTIDAD_TIPO;
    }

    @Override
    public String estadoActual() {
        return estado;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        if (!ESTADOS.contains(nuevoEstado)) {
            throw new IllegalArgumentException("Estado de oferta no documentado: " + nuevoEstado);
        }
        this.estado = nuevoEstado;
        this.fechaActualizacion = OffsetDateTime.now();
    }

    /** ¿Sigue sobre la mesa? Solo una oferta viva admite respuesta. */
    @Transient
    public boolean estaViva() {
        return PRESENTADA.equals(estado);
    }

    /** ¿Se cerro con ella? Es la unica que un contrato de compraventa puede firmar. */
    @Transient
    public boolean estaAceptada() {
        return ACEPTADA.equals(estado);
    }

    /**
     * ¿Se congelo alguna referencia? {@code false} significa que el encargo no
     * tenia ni hito {@code P} ni {@code U} cuando se presento — un hecho, no un
     * hueco por rellenar.
     */
    @Transient
    public boolean tieneReferencia() {
        return hitoReferencia != null;
    }

    /**
     * La referencia, entera y de una vez. Se fija al presentar y no se vuelve a
     * tocar: un anuncio que baja de precio una semana despues no puede cambiar
     * retroactivamente el descuento con el que se negocio.
     */
    public Oferta conReferencia(String hito, Long idPrecio, BigDecimal monto, String moneda) {
        this.hitoReferencia = hito;
        this.idPrecioReferencia = idPrecio;
        this.montoReferencia = monto;
        this.monedaReferencia = moneda;
        return this;
    }

    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdOportunidad() {
        return idOportunidad;
    }

    public void setIdOportunidad(Long idOportunidad) {
        this.idOportunidad = idOportunidad;
    }

    public Long getIdCaptacion() {
        return idCaptacion;
    }

    public void setIdCaptacion(Long idCaptacion) {
        this.idCaptacion = idCaptacion;
    }

    public Integer getRonda() {
        return ronda;
    }

    public void setRonda(Integer ronda) {
        this.ronda = ronda;
    }

    public String getPresentadaPor() {
        return presentadaPor;
    }

    public void setPresentadaPor(String presentadaPor) {
        this.presentadaPor = presentadaPor;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getCondiciones() {
        return condiciones;
    }

    public void setCondiciones(String condiciones) {
        this.condiciones = condiciones;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Long getIdRolRegistra() {
        return idRolRegistra;
    }

    public void setIdRolRegistra(Long idRolRegistra) {
        this.idRolRegistra = idRolRegistra;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getHitoReferencia() {
        return hitoReferencia;
    }

    public Long getIdPrecioReferencia() {
        return idPrecioReferencia;
    }

    public BigDecimal getMontoReferencia() {
        return montoReferencia;
    }

    public String getMonedaReferencia() {
        return monedaReferencia;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
