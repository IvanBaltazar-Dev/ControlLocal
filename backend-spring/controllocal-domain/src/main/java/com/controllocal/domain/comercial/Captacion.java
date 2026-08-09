package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Captacion: el encargo formal del propietario, revisado por el broker.
 * Maquina (codigos del cable): P Pendiente de revision -> {A Activa | O Observada |
 * R Rechazada}; O vuelve a P al reenviarse; A -> {C Cerrada | V Vencida}.
 * El estado SOLO muta via Transiciones (auditoria RC-002).
 */
@Entity
@Table(name = "captacion")
public class Captacion extends EntidadDeOrganizacion implements Transicionable {

    public static final String PENDIENTE_REVISION = Codigos.Captacion.PENDIENTE;
    public static final String OBSERVADA = Codigos.Captacion.OBSERVADA;
    public static final String RECHAZADA = Codigos.Captacion.RECHAZADA;
    public static final String ACTIVA = Codigos.Captacion.ACTIVA;
    public static final String CERRADA = Codigos.Captacion.CERRADA;
    public static final String VENCIDA = Codigos.Captacion.VENCIDA;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_captacion")
    private Long id;

    @Column(name = "codigo_captacion", nullable = false, length = 20)
    private String codigoCaptacion;

    @Column(name = "fecha_captacion", nullable = false)
    private LocalDate fechaCaptacion;

    @Column(name = "fecha_inicio_encargo")
    private LocalDate fechaInicioEncargo;

    @Column(name = "fecha_fin_encargo")
    private LocalDate fechaFinEncargo;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "id_condicion_economica")
    private CondicionEconomicaCaptacion condicionEconomica;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Column(name = "motivo_cierre", length = 1)
    private String motivoCierre;

    @Column(name = "detalle_motivo_cierre", length = 300)
    private String detalleMotivoCierre;

    @Column(name = "fecha_revision")
    private OffsetDateTime fechaRevision;

    @Column(name = "observacion_revision")
    private String observacionRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_propiedad", nullable = false)
    private Propiedad propiedad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_broker_revisor")
    private DetalleBroker brokerRevisor;

    /** Semilla de OperacionComercial: la 1a ola solo admite alquiler ('A'). */
    @Column(name = "motivo_operacion", nullable = false, length = 1)
    private String motivoOperacion = "A";

    @Column(name = "urgencia")
    private Integer urgencia;

    @Column(name = "exclusividad")
    private Boolean exclusividad;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    // ------------------------------------------------------------------
    // Transicionable
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return "CAPTACION";
    }

    @Override
    public String estadoActual() {
        return estado;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estado = EstadoCaptacion.desde(nuevoEstado).codigo();
        touch();
    }

    @Transient
    public EstadoCaptacion estadoTipado() {
        return estado == null ? null : EstadoCaptacion.desde(estado);
    }

    // ------------------------------------------------------------------
    // Efectos laterales (semantica calcada del modelo v1).
    // ------------------------------------------------------------------

    /** Deja constancia de quien reviso y con que observacion (el estado va por Transiciones). */
    public void registrarRevision(DetalleBroker broker, String observacion) {
        this.brokerRevisor = broker;
        this.fechaRevision = OffsetDateTime.now();
        this.observacionRevision = observacion;
        touch();
    }

    /** El cierre fija el fin de vigencia si no estaba pactado. */
    public void marcarFinVigencia(LocalDate hoy) {
        if (fechaFinEncargo == null) {
            fechaFinEncargo = hoy;
        }
        touch();
    }

    public boolean editable() {
        return PENDIENTE_REVISION.equals(estadoActual()) || OBSERVADA.equals(estadoActual());
    }

    private void touch() {
        this.fechaActualizacion = OffsetDateTime.now();
    }

    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getCodigoCaptacion() {
        return codigoCaptacion;
    }

    public void setCodigoCaptacion(String codigoCaptacion) {
        this.codigoCaptacion = codigoCaptacion;
    }

    public LocalDate getFechaCaptacion() {
        return fechaCaptacion;
    }

    public void setFechaCaptacion(LocalDate fechaCaptacion) {
        this.fechaCaptacion = fechaCaptacion;
    }

    public LocalDate getFechaInicioVigencia() {
        return fechaInicioEncargo;
    }

    public void setFechaInicioVigencia(LocalDate fechaInicioVigencia) {
        this.fechaInicioEncargo = fechaInicioVigencia;
    }

    public LocalDate getFechaFinVigencia() {
        return fechaFinEncargo;
    }

    public void setFechaFinVigencia(LocalDate fechaFinVigencia) {
        this.fechaFinEncargo = fechaFinVigencia;
    }

    public BigDecimal getComisionPactada() {
        if (condicionEconomica == null || condicionEconomica.getValorComision() == null) return null;
        if (CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES.equals(condicionEconomica.getTipoComision())) {
            return condicionEconomica.getValorComision().multiply(BigDecimal.valueOf(100));
        }
        return condicionEconomica.getValorComision();
    }

    public void setComisionPactada(BigDecimal comisionPactada) {
        if (condicionEconomica == null) condicionEconomica = new CondicionEconomicaCaptacion();
        condicionEconomica.setTipoComision(CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES);
        condicionEconomica.setBaseCalculo(CondicionEconomicaCaptacion.RENTA_MENSUAL);
        condicionEconomica.setValorComision(comisionPactada == null ? null
                : comisionPactada.divide(BigDecimal.valueOf(100)));
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public OffsetDateTime getFechaRevision() {
        return fechaRevision;
    }

    public String getObservacionRevision() {
        return observacionRevision;
    }

    public void setObservacionRevision(String observacionRevision) {
        this.observacionRevision = observacionRevision;
    }

    public Propiedad getPropiedad() {
        return propiedad;
    }

    public void setPropiedad(Propiedad propiedad) {
        this.propiedad = propiedad;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public DetalleBroker getBrokerRevisor() {
        return brokerRevisor;
    }

    public String getMotivoOperacion() {
        return motivoOperacion;
    }

    /** El dominio soporta alquiler y venta; la interfaz actual solo ofrece alquiler. */
    public void setMotivoOperacion(String motivoOperacion) {
        if (motivoOperacion != null && !"A".equals(motivoOperacion) && !"V".equals(motivoOperacion)) {
            throw new IllegalArgumentException("Tipo de operacion invalido.");
        }
        this.motivoOperacion = motivoOperacion == null ? "A" : motivoOperacion;
    }

    public Integer getUrgencia() {
        return urgencia;
    }

    public void setUrgencia(Integer urgencia) {
        if (urgencia != null && (urgencia < 1 || urgencia > 5)) {
            throw new IllegalArgumentException("La urgencia debe estar entre 1 y 5.");
        }
        this.urgencia = urgencia;
    }

    public Boolean getExclusividad() {
        return exclusividad;
    }

    public void setExclusividad(Boolean exclusividad) {
        this.exclusividad = exclusividad;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public CondicionEconomicaCaptacion getCondicionEconomica() { return condicionEconomica; }
    public void setCondicionEconomica(CondicionEconomicaCaptacion condicionEconomica) {
        this.condicionEconomica = condicionEconomica;
        if (condicionEconomica != null) condicionEconomica.setOrganizacionId(getOrganizacionId());
    }
    public LocalDate getFechaCierre() { return fechaCierre; }
    public String getMotivoCierre() { return motivoCierre; }
    public String getDetalleMotivoCierre() { return detalleMotivoCierre; }
    public void cerrar(LocalDate fecha, String motivo, String detalle) {
        this.fechaCierre = fecha;
        this.motivoCierre = motivo;
        this.detalleMotivoCierre = detalle;
        touch();
    }
}
