package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoVisita;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Visita al local con el cliente interesado.
 * Maquina (codigos del cable): P Programada -> G Reprogramada ->
 * {R Realizada | N No realizada | C Cancelada}.
 * El estado SOLO muta via Transiciones (auditoria RC-002).
 *
 * <p>El DESENLACE (resultado, nivel de interes, objecion, opinion de precio
 * y proxima accion) solo tiene sentido sobre una visita REALIZADA, y se
 * registra una sola vez: es la evidencia de lo que opino el cliente al ver
 * el local. Cancelar o marcar no-realizada lo limpia.
 */
@Entity
@Table(name = "visita")
public class Visita extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "VISITA";

    public static final String PROGRAMADA = Codigos.Visita.PROGRAMADA;
    public static final String REPROGRAMADA = Codigos.Visita.REPROGRAMADA;
    public static final String CANCELADA = Codigos.Visita.CANCELADA;
    public static final String NO_REALIZADA = Codigos.Visita.NO_REALIZADA;
    public static final String REALIZADA = Codigos.Visita.REALIZADA;
    public static final Set<String> ESTADOS = Set.of(PROGRAMADA, REPROGRAMADA,
            CANCELADA, NO_REALIZADA, REALIZADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_visita")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad", nullable = false)
    private OportunidadComercial oportunidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "fecha_visita", nullable = false)
    private LocalDate fechaVisita;

    @Column(name = "hora_visita")
    private LocalTime horaVisita;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "observaciones")
    private String observaciones;

    /** Codigo de ResultadoInteraccion: MIXTO en el cable (1 char o palabra). */
    @Column(name = "resultado", length = 30)
    private String resultado;

    @Column(name = "nivel_interes")
    private Integer nivelInteres;

    @Column(name = "objecion_principal", length = 1)
    private String objecionPrincipal;

    @Column(name = "opinion_precio", length = 1)
    private String opinionPrecio;

    @Column(name = "proxima_accion", length = 1)
    private String proximaAccion;

    @Column(name = "razon_no_continuidad", length = 1)
    private String razonNoContinuidad;

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
        this.estado = EstadoVisita.desde(nuevoEstado).codigo();
        this.fechaActualizacion = OffsetDateTime.now();
    }

    @Transient
    public EstadoVisita estadoTipado() {
        return estado == null ? null : EstadoVisita.desde(estado);
    }

    /** Solo una visita viva (programada o reprogramada) admite cambios de agenda. */
    public boolean esModificable() {
        return PROGRAMADA.equals(estado) || REPROGRAMADA.equals(estado);
    }

    public boolean tieneDesenlace() {
        return resultado != null;
    }

    public void moverA(LocalDate nuevaFecha, LocalTime nuevaHora) {
        this.fechaVisita = nuevaFecha;
        this.horaVisita = nuevaHora;
    }

    /**
     * El motivo de cancelar o de no realizar se guarda en observaciones (asi
     * lo hace la v1) y borra cualquier desenlace previo: una visita que no
     * ocurrio no puede dejar opinion del cliente.
     */
    public void registrarMotivoYLimpiarDesenlace(String motivo) {
        this.observaciones = motivo;
        this.resultado = null;
        this.nivelInteres = null;
        this.objecionPrincipal = null;
        this.opinionPrecio = null;
        this.proximaAccion = null;
    }

    public void registrarDesenlace(String resultado, String observaciones, String razonNoContinuidad,
                                   Integer nivelInteres, String objecionPrincipal,
                                   String opinionPrecio, String proximaAccion) {
        this.resultado = resultado;
        if (observaciones != null) {
            this.observaciones = observaciones;
        }
        this.razonNoContinuidad = razonNoContinuidad;
        this.nivelInteres = nivelInteres;
        this.objecionPrincipal = objecionPrincipal;
        this.opinionPrecio = opinionPrecio;
        this.proximaAccion = proximaAccion;
        this.fechaActualizacion = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDate getFechaVisita() {
        return fechaVisita;
    }

    public void setFechaVisita(LocalDate fechaVisita) {
        this.fechaVisita = fechaVisita;
    }

    public LocalTime getHoraVisita() {
        return horaVisita;
    }

    public void setHoraVisita(LocalTime horaVisita) {
        this.horaVisita = horaVisita;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getResultado() {
        return resultado;
    }

    public Integer getNivelInteres() {
        return nivelInteres;
    }

    public String getObjecionPrincipal() {
        return objecionPrincipal;
    }

    public String getOpinionPrecio() {
        return opinionPrecio;
    }

    public String getProximaAccion() {
        return proximaAccion;
    }

    public String getRazonNoContinuidad() {
        return razonNoContinuidad;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
