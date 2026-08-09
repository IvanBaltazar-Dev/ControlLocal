package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoSolicitud;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Oferta formal del interesado sobre la oportunidad. UNA por oportunidad.
 *
 * <p>Maquina (codigos del cable): G Registrada -> E En revision ->
 * {A Aprobada | R Rechazada | O Observada}; la observada vuelve a E cuando el
 * agente la reenvia, y la aprobada termina en C Cerrada cuando el contrato
 * concreta el alquiler. D Desistida es la salida del interesado.
 * El estado SOLO muta via Transiciones (auditoria RC-002).
 *
 * <p>Las CONDICIONES del trato (plazo, inicio, forma de pago, garantia y
 * adelanto) viven aqui y no se copian al contrato: el contrato es solo el
 * vinculo y la formalizacion.
 */
@Entity
@Table(name = "solicitud_alquiler")
public class SolicitudAlquiler extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "SOLICITUD_ALQUILER";

    public static final String REGISTRADA = Codigos.Solicitud.REGISTRADA;
    public static final String EN_REVISION = Codigos.Solicitud.EN_REVISION;
    public static final String OBSERVADA = Codigos.Solicitud.OBSERVADA;
    public static final String APROBADA = Codigos.Solicitud.APROBADA;
    public static final String RECHAZADA = Codigos.Solicitud.RECHAZADA;
    public static final String DESISTIDA = Codigos.Solicitud.DESISTIDA;
    public static final String CERRADA = Codigos.Solicitud.CERRADA;
    public static final Set<String> ESTADOS = Set.of(REGISTRADA, EN_REVISION,
            OBSERVADA, APROBADA, RECHAZADA, DESISTIDA, CERRADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long id;

    @Column(name = "codigo_solicitud", nullable = false, length = 20)
    private String codigoSolicitud;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    @Column(name = "monto_propuesto", nullable = false)
    private BigDecimal montoPropuesto;

    /** Moneda de la renta final propuesta; contrato, hito y comision la heredan. */
    @Column(name = "moneda", length = 3)
    private String moneda;

    @Column(name = "plazo_tentativo", length = 50)
    private String plazoTentativo;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "fecha_actualizacion_estado")
    private OffsetDateTime fechaActualizacionEstado;

    @Column(name = "fecha_vigencia_oferta")
    private LocalDate fechaVigenciaOferta;

    @Column(name = "plazo_contrato_meses")
    private Integer plazoContratoMeses;

    @Column(name = "fecha_inicio_contrato")
    private LocalDate fechaInicioContrato;

    /** Nombre del enum FormaPago, no CHAR(1): asi lo emite la v1. */
    @Column(name = "forma_pago", length = 20)
    private String formaPago;

    @Column(name = "meses_garantia")
    private Integer mesesGarantia;

    @Column(name = "meses_adelanto")
    private Integer mesesAdelanto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad", nullable = false)
    private OportunidadComercial oportunidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

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
        this.estado = EstadoSolicitud.desde(nuevoEstado).codigo();
        this.fechaActualizacionEstado = OffsetDateTime.now();
        this.fechaActualizacion = this.fechaActualizacionEstado;
    }

    @Transient
    public EstadoSolicitud estadoTipado() {
        return estado == null ? null : EstadoSolicitud.desde(estado);
    }

    /** Solo una registrada u observada puede (re)enviarse a evaluacion. */
    public boolean puedeEnviarseAEvaluacion() {
        return REGISTRADA.equals(estado) || OBSERVADA.equals(estado);
    }

    /** El contrato solo se registra sobre una solicitud aprobada. */
    public boolean estaAprobada() {
        return APROBADA.equals(estado);
    }

    // ------------------------------------------------------------------
    // Accesores
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public void setCodigoSolicitud(String codigoSolicitud) {
        this.codigoSolicitud = codigoSolicitud;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public BigDecimal getMontoPropuesto() {
        return montoPropuesto;
    }

    public void setMontoPropuesto(BigDecimal montoPropuesto) {
        this.montoPropuesto = montoPropuesto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getPlazoTentativo() {
        return plazoTentativo;
    }

    public void setPlazoTentativo(String plazoTentativo) {
        this.plazoTentativo = plazoTentativo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public OffsetDateTime getFechaActualizacionEstado() {
        return fechaActualizacionEstado;
    }

    public LocalDate getFechaVigenciaOferta() {
        return fechaVigenciaOferta;
    }

    public void setFechaVigenciaOferta(LocalDate fechaVigenciaOferta) {
        this.fechaVigenciaOferta = fechaVigenciaOferta;
    }

    public Integer getPlazoContratoMeses() {
        return plazoContratoMeses;
    }

    public void setPlazoContratoMeses(Integer plazoContratoMeses) {
        this.plazoContratoMeses = plazoContratoMeses;
    }

    public LocalDate getFechaInicioContrato() {
        return fechaInicioContrato;
    }

    public void setFechaInicioContrato(LocalDate fechaInicioContrato) {
        this.fechaInicioContrato = fechaInicioContrato;
    }

    public String getFormaPago() {
        return formaPago;
    }

    public void setFormaPago(String formaPago) {
        this.formaPago = formaPago;
    }

    public Integer getMesesGarantia() {
        return mesesGarantia;
    }

    public void setMesesGarantia(Integer mesesGarantia) {
        this.mesesGarantia = mesesGarantia;
    }

    public Integer getMesesAdelanto() {
        return mesesAdelanto;
    }

    public void setMesesAdelanto(Integer mesesAdelanto) {
        this.mesesAdelanto = mesesAdelanto;
    }

    public OportunidadComercial getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(OportunidadComercial oportunidad) {
        this.oportunidad = oportunidad;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
