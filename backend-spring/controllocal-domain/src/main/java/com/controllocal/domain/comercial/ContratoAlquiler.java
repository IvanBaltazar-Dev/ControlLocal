package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoContrato;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.persona.DetalleAgente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Contrato de alquiler: VINCULO + formalizacion. Las condiciones del trato
 * (plazo, inicio, forma de pago, garantia, adelanto) viven en la solicitud y
 * NO se copian aqui.
 *
 * <p>Registrarlo es lo que cierra el ciclo: dispara la cascada de siete
 * efectos (§6 del contrato F4) que cierra la oportunidad como exitosa. Por eso
 * {@code POST /oportunidades/{id}/cierre-exitoso} responde 400 para siempre:
 * el cierre no lo produce un boton, lo produce este contrato.
 *
 * <p>Estados del cable: P En proceso, D Firmado, V Vigente, R Renovado,
 * F Finalizado, S Rescindido, A Anulado. El alta solo admite FIRMADO o
 * VIGENTE.
 */
@Entity
@Table(name = "contrato_alquiler")
public class ContratoAlquiler extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "CONTRATO_ALQUILER";

    public static final String EN_PROCESO = Codigos.Contrato.EN_PROCESO;
    public static final String FIRMADO = Codigos.Contrato.FIRMADO;
    public static final String VIGENTE = Codigos.Contrato.VIGENTE;
    public static final String RENOVADO = Codigos.Contrato.RENOVADO;
    public static final String FINALIZADO = Codigos.Contrato.FINALIZADO;
    public static final String RESCINDIDO = Codigos.Contrato.RESCINDIDO;
    public static final String ANULADO = Codigos.Contrato.ANULADO;
    public static final Set<String> ESTADOS = Set.of(EN_PROCESO, FIRMADO, VIGENTE,
            RENOVADO, FINALIZADO, RESCINDIDO, ANULADO);

    /** Unicos estados con los que un contrato puede NACER (cable v1). */
    public static final Set<String> ESTADOS_DE_CIERRE = Set.of(FIRMADO, VIGENTE);

    /**
     * <b>El grafo del ciclo juridico NO vive aqui</b>, y es deliberado: vive en
     * {@code MaquinasEstado}, el registro unico que declara la maquina de TODAS
     * las entidades transicionables, y lo aplica {@code Transiciones} antes de
     * mutar. Una segunda copia en esta clase seria dos fuentes de verdad para
     * la misma regla, y acabarian divergiendo.
     *
     * <pre>
     *   P -> D | A        D -> V | A        V -> F | S | R
     *   R, F, S, A: terminales
     * </pre>
     *
     * <p><b>{@code A} solo antes de la vigencia.</b> Anular es dejar sin efecto
     * algo que nunca lo tuvo; lo que termina un contrato que ya producia
     * efectos es {@code S}. Confundirlos borraria de la historia un alquiler
     * que existio y por el que se cobro comision.
     *
     * <p><b>{@code R} lo produce la renovacion</b>, que crea un contrato NUEVO
     * enlazado por {@link #contratoAnterior}. Un estado de renovacion en la
     * misma fila obligaria a machacar plazo y renta, y con ellos el historico
     * que sostiene la atribucion de comisiones.
     *
     * <p>El grafo esta fijado por {@code CicloContratoTest}.
     */
    public boolean esTerminal() {
        return RENOVADO.equals(estadoContrato) || FINALIZADO.equals(estadoContrato)
                || RESCINDIDO.equals(estadoContrato) || ANULADO.equals(estadoContrato);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato_alquiler")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad", nullable = false)
    private OportunidadComercial oportunidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud")
    private SolicitudAlquiler solicitud;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDate fechaCierre;

    @Column(name = "estado_contrato", nullable = false, length = 1)
    private String estadoContrato;

    @Column(name = "fecha_inicio_contrato")
    private LocalDate fechaInicioContrato;

    @Column(name = "fecha_fin_contrato")
    private LocalDate fechaFinContrato;

    @Column(name = "renta_contractual", precision = 14, scale = 2)
    private BigDecimal rentaContractual;

    @Column(name = "moneda", length = 3)
    private String moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrato_anterior")
    private ContratoAlquiler contratoAnterior;

    @Column(name = "fecha_efectiva_estado")
    private LocalDate fechaEfectivaEstado;

    // ------------------------------------------------------------------
    // Atribucion historica del cierre (V27). Son VALORES, no asociaciones:
    // un cierre es un hecho consumado y su atribucion no debe seguir a la
    // cadena vigente. Antes de V27 el agente, la captacion, el inmueble y el
    // cliente se releian de solicitud/oportunidad en cada consulta, asi que
    // una reasignacion posterior reescribia la historia.
    // ------------------------------------------------------------------

    @Column(name = "id_rol_agente_cierre", nullable = false)
    private Long idRolAgenteCierre;

    /**
     * Vista de SOLO LECTURA de la misma columna, para que la ficha publique el
     * nombre del agente atribuido sin releer la cadena vigente. La escritura va
     * por {@link #atribuir} y el id; esta asociacion nunca inserta ni actualiza.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_agente_cierre", insertable = false, updatable = false)
    private DetalleAgente agenteCierre;

    /** Supervisor del agente EN LA FECHA DE CIERRE. Nulo si no tenia. */
    @Column(name = "id_rol_broker_cierre")
    private Long idRolBrokerCierre;

    @Column(name = "id_captacion", nullable = false)
    private Long idCaptacion;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    @Column(name = "id_rol_cliente", nullable = false)
    private Long idRolCliente;

    @Column(name = "incidencias")
    private String incidencias;

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
        return estadoContrato;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estadoContrato = EstadoContrato.desde(nuevoEstado).codigo();
        this.fechaActualizacion = OffsetDateTime.now();
    }

    @Transient
    public EstadoContrato estadoContratoTipado() {
        return estadoContrato == null ? null : EstadoContrato.desde(estadoContrato);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OportunidadComercial getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(OportunidadComercial oportunidad) {
        this.oportunidad = oportunidad;
    }

    public SolicitudAlquiler getSolicitud() {
        return solicitud;
    }

    public void setSolicitud(SolicitudAlquiler solicitud) {
        this.solicitud = solicitud;
    }

    public LocalDate getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDate fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public String getIncidencias() {
        return incidencias;
    }

    public void setIncidencias(String incidencias) {
        this.incidencias = incidencias;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDate getFechaInicioContrato() { return fechaInicioContrato; }
    public void setFechaInicioContrato(LocalDate fechaInicioContrato) { this.fechaInicioContrato = fechaInicioContrato; }
    public LocalDate getFechaFinContrato() { return fechaFinContrato; }
    public void setFechaFinContrato(LocalDate fechaFinContrato) { this.fechaFinContrato = fechaFinContrato; }
    public BigDecimal getRentaContractual() { return rentaContractual; }
    public void setRentaContractual(BigDecimal rentaContractual) { this.rentaContractual = rentaContractual; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public ContratoAlquiler getContratoAnterior() { return contratoAnterior; }
    public void setContratoAnterior(ContratoAlquiler contratoAnterior) { this.contratoAnterior = contratoAnterior; }
    public LocalDate getFechaEfectivaEstado() { return fechaEfectivaEstado; }
    public void setFechaEfectivaEstado(LocalDate fechaEfectivaEstado) { this.fechaEfectivaEstado = fechaEfectivaEstado; }

    public Long getIdRolAgenteCierre() { return idRolAgenteCierre; }
    public DetalleAgente getAgenteCierre() { return agenteCierre; }
    public Long getIdRolBrokerCierre() { return idRolBrokerCierre; }
    public Long getIdCaptacion() { return idCaptacion; }
    public Long getIdPropiedad() { return idPropiedad; }
    public Long getIdRolCliente() { return idRolCliente; }

    /**
     * Congela la atribucion del cierre. Se llama UNA vez, al crear el contrato:
     * no hay setters sueltos porque los cinco valores forman una sola foto y
     * reescribir uno solo dejaria una atribucion incoherente.
     */
    public void atribuir(Long idRolAgente, Long idRolBroker, Long idCaptacion,
                         Long idPropiedad, Long idRolCliente) {
        this.idRolAgenteCierre = idRolAgente;
        this.idRolBrokerCierre = idRolBroker;
        this.idCaptacion = idCaptacion;
        this.idPropiedad = idPropiedad;
        this.idRolCliente = idRolCliente;
    }
}
