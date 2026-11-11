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
 * <p>Las causas C7 conservan aquí el agente al detectar el hecho, como
 * procedencia inmutable. La entrega vuelve a resolver el responsable y la
 * autoridad actuales del recurso; reasignar no crea otro aviso ni cambia su
 * fecha. R27 conserva su vínculo y su conciliación propios.
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
    @Column(name="marca_causa",length=64)
    private String marcaCausa;
    @Column(name="huella_causa_c7",length=64)
    private String huellaCausaC7;
    @Column(name="fuente_causa_c7",columnDefinition="text")
    private String fuenteCausaC7;
    @Column(name="estado_causa_c7",length=15)
    private String estadoCausaC7;
    @Column(name="id_antecedente_c7")
    private Long idAntecedenteC7;

    public String getMarcaCausa(){return marcaCausa;}
    public void setMarcaCausa(String valor){marcaCausa=valor;}
    public String getHuellaCausaC7(){return huellaCausaC7;}
    public void setHuellaCausaC7(String valor){huellaCausaC7=valor;}
    public String getFuenteCausaC7(){return fuenteCausaC7;}
    public void setFuenteCausaC7(String valor){fuenteCausaC7=valor;}
    public String getEstadoCausaC7(){return estadoCausaC7;}
    public void setEstadoCausaC7(String valor){estadoCausaC7=valor;}
    public Long getIdAntecedenteC7(){return idAntecedenteC7;}
    public void setIdAntecedenteC7(Long valor){idAntecedenteC7=valor;}

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "severidad", nullable = false, length = 10)
    private String severidad;

    /** Codigo de {@code entidad_tipo}: a que se refiere el aviso. */
    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_agente")
    private DetalleAgente agente;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="id_rol_broker")
    private com.controllocal.domain.persona.DetalleBroker broker;
    @Column(name="nb_evento") private Long eventoBroker;
    @Column(name="nb_codigo",length=5) private String codigoBroker;

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

    /**
     * <b>Cierra el aviso porque su motivo dejo de existir</b>, no porque nadie
     * lo mirara.
     *
     * <p>Es el otro lado de la reconciliacion: si la prospeccion volvio a
     * contactarse, el recontacto ya no esta vencido y el aviso no puede seguir
     * activo diciendo que si. Sin esto la campana y la bandeja divergian — la
     * tarea se auto-completa al reconciliar y la alerta se quedaba viva para
     * siempre, hasta que alguien pulsara «Atender» una cosa que ya no pasaba.
     *
     * <p>Se distingue de {@link #atender()} a proposito. Las dos cierran, pero
     * no dicen lo mismo: una persona decidio, o el mundo cambio. Mezclarlas
     * dejaria la auditoria sin poder responder cual de las dos fue.
     */
    public void descartar() {
        estado = DESCARTADA;
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
