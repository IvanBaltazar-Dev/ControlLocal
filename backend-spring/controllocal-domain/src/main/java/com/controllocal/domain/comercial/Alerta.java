package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoAlerta;
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

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Aviso de la campana. <b>NO es Transicionable</b> y no es un olvido:
 * {@code entidad_tipo} declara ALERTA con {@code auditable = FALSE} desde V2,
 * porque auditar cada aviso llenaria {@code historial_estado} de ruido
 * operativo. Su estado se mueve con {@link #atender()}.
 *
 * <p><b>La regla que ordena el modulo</b>: la alerta se ata SIEMPRE a un
 * AGENTE, nunca a un broker. El agente la ve como propia y su broker
 * supervisor la ve a traves de la supervision, asi que <b>quien es el
 * destinatario lo dice el TIPO, no una columna</b>: {@code CAPTACION_CREADA}
 * cuelga del agente pero esta escrita <i>para</i> el broker, y
 * {@code CAPTACION_REVISADA} cuelga del mismo agente y esta escrita
 * <i>para</i> el. No hay {@code id_destinatario} y no hay que inventarlo.
 *
 * <p>El estado persiste como codigo de un caracter. El enum es una vista
 * derivada y estricta; el atributo {@code String} sigue siendo consultable por
 * el contrato JPQL historico.
 */
@Entity
@Table(name = "alerta")
public class Alerta extends EntidadDeOrganizacion {

    public static final String ENTIDAD_TIPO = "ALERTA";

    /** EstadoAlerta. */
    public static final String ACTIVA = Codigos.Alerta.ACTIVA;
    public static final String ATENDIDA = Codigos.Alerta.ATENDIDA;
    public static final String DESCARTADA = Codigos.Alerta.DESCARTADA;

    /** Severidad. */
    public static final String INFO = "INFO";
    public static final String MEDIA = "MEDIA";
    public static final String ALTA = "ALTA";

    /**
     * TipoAlerta. Los cinco primeros <b>no tienen emisor</b> en la v1: estan en
     * el enum y en el CHECK de la BD, y se mantienen por paridad.
     */
    public static final String SIN_AVANCE = "SIN_AVANCE";
    public static final String OFERTA_POR_VENCER = "OFERTA_POR_VENCER";
    public static final String CONTRATO_POR_VENCER = "CONTRATO_POR_VENCER";
    public static final String VISITA_PROXIMA = "VISITA_PROXIMA";
    public static final String CAPTACION_VENCIDA = "CAPTACION_VENCIDA";
    /** Los once que si se emiten (§4 del contrato). */
    public static final String SIN_RESPUESTA = "SIN_RESPUESTA";
    public static final String SOLICITUD_REENVIADA = "SOLICITUD_REENVIADA";
    public static final String SOLICITUD_EVALUADA = "SOLICITUD_EVALUADA";
    public static final String SOLICITUD_DOCUMENTO = "SOLICITUD_DOCUMENTO";
    public static final String SOLICITUD_DOCUMENTO_REVISADO = "SOLICITUD_DOCUMENTO_REVISADO";
    public static final String CAPTACION_CREADA = "CAPTACION_CREADA";
    public static final String CAPTACION_REVISADA = "CAPTACION_REVISADA";
    public static final String CAPTACION_CERRADA = "CAPTACION_CERRADA";
    public static final String OPORTUNIDAD_CERRADA = "OPORTUNIDAD_CERRADA";
    public static final String COMISION_ASIGNADA = "COMISION_ASIGNADA";
    public static final String COMISION_COBRADA = "COMISION_COBRADA";

    public static final Set<String> SEVERIDADES = Set.of(INFO, MEDIA, ALTA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long id;

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "severidad", nullable = false, length = 10)
    private String severidad;

    /** Codigo de {@code entidad_tipo}: a que se refiere el aviso. */
    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "mensaje", nullable = false, length = 300)
    private String mensaje;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    /**
     * La fija el caso de uso al emitir; el DEFAULT now() de la BD queda de red.
     * Con {@code insertable = false} la alerta recien creada viajaria sin fecha.
     */
    @Column(name = "fecha_generacion", nullable = false)
    private OffsetDateTime fechaGeneracion;

    @Column(name = "fecha_resolucion")
    private OffsetDateTime fechaResolucion;

    /** Nace ACTIVA y con su momento sellado (la v1 lo hace en {@code prepararNueva}). */
    public void nacer() {
        if (estado == null) {
            estado = ACTIVA;
        }
        if (fechaGeneracion == null) {
            fechaGeneracion = OffsetDateTime.now();
        }
    }

    /**
     * Marca el aviso como atendido. La fecha de resolucion es obligatoria en
     * cuanto el estado deja de ser ACTIVA (lo exige {@code ck_alerta_resolucion}),
     * asi que se fija aqui y no en el caso de uso.
     */
    public void atender() {
        estado = ATENDIDA;
        fechaResolucion = OffsetDateTime.now();
    }

    public boolean estaActiva() {
        return ACTIVA.equals(getEstado());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getSeveridad() {
        return severidad;
    }

    public void setSeveridad(String severidad) {
        this.severidad = severidad;
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

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoAlerta.desde(estado).codigo();
    }

    @Transient
    public EstadoAlerta estadoTipado() {
        return estado == null ? null : EstadoAlerta.desde(estado);
    }

    public OffsetDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(OffsetDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }

    public OffsetDateTime getFechaResolucion() {
        return fechaResolucion;
    }
}
