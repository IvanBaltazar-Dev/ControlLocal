package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoOportunidad;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Oportunidad comercial: la entidad HUB del proceso. Existe desde que un
 * cliente muestra interes por un local captado, y sobrevive aunque el
 * interesado nunca llegue a presentar una solicitud formal — por eso es el
 * hilo del que cuelga la trazabilidad (interacciones, visitas y, mas
 * adelante, la solicitud).
 *
 * <p>Maquina (codigos del cable): A Abierta -> S Solicitud creada ->
 * {F Finalizada exitosa | X Finalizada no favorable}; A -> N No continua.
 * S/F/X los produce la vertical de solicitudes (F4), no F3.
 * El estado SOLO muta via Transiciones (auditoria RC-002).
 */
@Entity
@Table(name = "oportunidad_comercial")
public class OportunidadComercial extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "OPORTUNIDAD";

    public static final String ABIERTA = Codigos.Oportunidad.ABIERTA;
    public static final String SOLICITUD_CREADA = Codigos.Oportunidad.SOLICITUD_CREADA;
    public static final String NO_CONTINUA = Codigos.Oportunidad.NO_CONTINUA;
    public static final String FINALIZADA_EXITOSA = Codigos.Oportunidad.FINALIZADA_EXITOSA;
    public static final String FINALIZADA_NO_FAVORABLE = Codigos.Oportunidad.FINALIZADA_NO_FAVORABLE;
    public static final Set<String> ESTADOS = Set.of(ABIERTA, SOLICITUD_CREADA,
            NO_CONTINUA, FINALIZADA_EXITOSA, FINALIZADA_NO_FAVORABLE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oportunidad")
    private Long id;

    @Column(name = "codigo_oportunidad", nullable = false, length = 20)
    private String codigoOportunidad;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_cliente", nullable = false)
    private DetalleCliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_captacion", nullable = false)
    private Captacion captacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    /** Anuncio por el que llego el interesado, si se pudo atribuir. */
    @Column(name = "id_publicacion_origen")
    private Long idPublicacionOrigen;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "motivo_cierre")
    private String motivoCierre;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "fecha_primera_consulta")
    private OffsetDateTime fechaPrimeraConsulta;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    @Column(name = "fecha_actualizacion_estado")
    private OffsetDateTime fechaActualizacionEstado;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    /**
     * El alta fija las fechas de apertura (paridad con {@code abrir()} de la
     * v1). La primera consulta arranca igual al registro: es el momento en
     * que el interesado aparecio.
     */
    @PrePersist
    void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = OffsetDateTime.now();
        }
        if (fechaPrimeraConsulta == null) {
            fechaPrimeraConsulta = fechaRegistro;
        }
    }

    // ------------------------------------------------------------------
    // Transicionable: el estado SOLO muta via el componente Transiciones.
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
        this.estado = EstadoOportunidad.desde(nuevoEstado).codigo();
        this.fechaActualizacionEstado = OffsetDateTime.now();
        this.fechaActualizacion = this.fechaActualizacionEstado;
    }

    @Transient
    public EstadoOportunidad estadoTipado() {
        return estado == null ? null : EstadoOportunidad.desde(estado);
    }

    /** Cierre por no continuidad: guarda el porque y la fecha (la transicion la aplica el service). */
    public void marcarCierre(String motivo) {
        this.motivoCierre = motivo;
        this.fechaCierre = OffsetDateTime.now();
    }

    /**
     * Cierre EXITOSO, el que produce el contrato de alquiler (F4 §6): fija la
     * fecha de cierre y —a diferencia del anterior— NO lleva motivo, porque el
     * desenlace fue el bueno. Calca {@code cerrarExitosa()} de la v1.
     */
    public void marcarCierreExitoso() {
        this.fechaCierre = OffsetDateTime.now();
    }

    public boolean estaAbierta() {
        return ABIERTA.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public String getCodigoOportunidad() {
        return codigoOportunidad;
    }

    public void setCodigoOportunidad(String codigoOportunidad) {
        this.codigoOportunidad = codigoOportunidad;
    }

    public DetalleCliente getCliente() {
        return cliente;
    }

    public void setCliente(DetalleCliente cliente) {
        this.cliente = cliente;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public Long getIdPublicacionOrigen() {
        return idPublicacionOrigen;
    }

    public void setIdPublicacionOrigen(Long idPublicacionOrigen) {
        this.idPublicacionOrigen = idPublicacionOrigen;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getMotivoCierre() {
        return motivoCierre;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(OffsetDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public OffsetDateTime getFechaPrimeraConsulta() {
        return fechaPrimeraConsulta;
    }

    public OffsetDateTime getFechaCierre() {
        return fechaCierre;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
