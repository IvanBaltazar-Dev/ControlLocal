package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoTarea;
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
import java.util.Set;

/**
 * Una accion pendiente de la bandeja del agente. <b>NO es Transicionable</b>:
 * {@code entidad_tipo} declara TAREA con {@code auditable = FALSE} desde V2,
 * y con razon — la bandeja se RECONCILIA en cada lectura, asi que auditar cada
 * cambio de estado llenaria {@code historial_estado} de ruido.
 *
 * <p><b>No hay alta manual</b>: las tareas se derivan del estado del flujo
 * (§5.1 del contrato) y se auto-completan cuando su motivo desaparece. El
 * agente solo las resuelve trabajando o las cancela.
 *
 * <p>Los cuatro campos {@code @Transient} de abajo <b>no estan en la tabla</b>:
 * los calcula el service al leer, para que el cliente pueda navegar directo al
 * origen y ordenar por urgencia.
 */
@Entity
@Table(name = "tarea")
public class Tarea extends EntidadDeOrganizacion {

    public static final String ENTIDAD_TIPO = "TAREA";

    /** EstadoTarea. */
    public static final String PENDIENTE = Codigos.Tarea.PENDIENTE;
    public static final String EN_PROCESO = Codigos.Tarea.EN_PROCESO;
    public static final String COMPLETADA = Codigos.Tarea.COMPLETADA;
    public static final String VENCIDA = Codigos.Tarea.VENCIDA;
    public static final String CANCELADA = Codigos.Tarea.CANCELADA;
    /** Las que la bandeja muestra y el reconcile considera "abiertas". */
    public static final Set<String> ABIERTAS = Set.of(PENDIENTE, EN_PROCESO);

    /** Prioridad. */
    public static final String ALTA = "ALTA";
    public static final String MEDIA = "MEDIA";
    public static final String BAJA = "BAJA";

    /** TipoTarea (solo los que algun disparador emite; el resto vive en el CHECK). */
    public static final String SEGUIMIENTO = "SEGUIMIENTO";
    public static final String VISITA = "VISITA";
    public static final String RECONTACTO = "RECONTACTO";
    public static final String REPORTE_PROPIETARIO = "REPORTE_PROPIETARIO";
    public static final String SUBIR_DOCUMENTOS = "SUBIR_DOCUMENTOS";
    public static final String PROPONER_OPORTUNIDAD = "PROPONER_OPORTUNIDAD";
    public static final String REVISION_INMUEBLE = "REVISION_INMUEBLE";

    /**
     * Valor de {@code tarea.entidad_tipo} para un local.
     *
     * <p>Existe como constante porque escribirlo a mano costo que
     * {@code finalizar} y {@code rescindir} un contrato <b>fallaran siempre</b>
     * con un 409: la tarea se creaba con {@code "PROPIEDAD"} y el
     * {@code CHECK ck_tarea_tipo_entidad} declara {@code INMUEBLE}. El
     * compilador no ve la diferencia entre dos cadenas; la base, si.
     */
    public static final String ENTIDAD_INMUEBLE = "INMUEBLE";

    /**
     * Prospección: el seguimiento al propietario <b>antes</b> de que haya
     * captación.
     *
     * <p>Sube a constante por la misma razón que {@link #ENTIDAD_INMUEBLE}: la
     * cadena estaba escrita a mano en cuatro sitios —{@code AlertaServiceImpl}
     * dos veces, {@code TareaServiceImpl} otras dos— y el compilador no ve la
     * diferencia entre dos literales que se separan.
     *
     * <p>Importa además para el Inicio: es el tipo de asunto cuyo expediente
     * <b>no</b> se resuelve por inmueble, porque todavía no hay encargo del que
     * hablar (D-E2-1 §10.3).
     */
    public static final String ENTIDAD_PROSPECCION = "PROSPECCION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Long id;

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    /** Codigo de {@code entidad_tipo}: junto con {@code entidadId} es la clave de dedup. */
    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    /**
     * Contrato que origino la tarea, cuando lo hay.
     *
     * <p>Existe por la revision de inmueble: se guarda contra el INMUEBLE, asi
     * que sin esta columna no habria forma de saber cual de los contratos del
     * local la produjo, y resolverla al revisar seria adivinar. Nulo en las
     * demas clases de tarea, que no tienen contrato origen.
     */
    @Column(name = "id_contrato_origen")
    private Long idContratoOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "fecha_programada", nullable = false)
    private OffsetDateTime fechaProgramada;

    @Column(name = "fecha_recordatorio")
    private OffsetDateTime fechaRecordatorio;

    @Column(name = "fecha_completada")
    private OffsetDateTime fechaCompletada;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "prioridad", nullable = false, length = 10)
    private String prioridad;

    // ------------------------------------------------------------------
    // Derivados al leer (§5.3): NO son columnas.
    // ------------------------------------------------------------------

    /** Codigo legible de la entidad de origen (CAP-0001, SOL-…): lo resuelve el service. */
    @Transient
    private String entidadCodigo;

    /** Ruta exacta de "Resolver": lleva directo al item que disparo la tarea. */
    @Transient
    private String rutaResolver;

    /**
     * Dias desde el plazo REAL de la entidad de origen —no desde que se creo la
     * tarea—. Usar la fecha de la tarea daria siempre 0, y es el error mas facil
     * de cometer al portar esto.
     */
    @Transient
    private Integer diasSinAccion;

    /** Solo cuando la entidad de origen impone plazo (recontacto, oferta, visita, reporte). */
    @Transient
    private LocalDate fechaVencimiento;

    /** Nace PENDIENTE y programada para ahora. */
    public void nacer(String prioridad) {
        this.estado = PENDIENTE;
        this.prioridad = prioridad;
        if (fechaProgramada == null) {
            fechaProgramada = OffsetDateTime.now();
        }
    }

    /** Su motivo desaparecio: el reconcile (o el cierre de F4) la da por hecha. */
    public void completar() {
        estado = COMPLETADA;
        fechaCompletada = OffsetDateTime.now();
    }

    /**
     * Soft-cancel del agente. Ojo: cancelar NO pospone, <b>mata</b> — el
     * reconcile no vuelve a crear una tarea para esa entidad (§5.2, trampa 1).
     */
    public void cancelar() {
        estado = CANCELADA;
    }

    public boolean estaAbierta() {
        return ABIERTAS.contains(getEstado());
    }

    /** Clave de deduplicacion del reconcile: por ENTIDAD, no por tipo de tarea. */
    public String claveEntidad() {
        return claveEntidad(entidadTipo, entidadId);
    }

    public static String claveEntidad(String entidadTipo, Long entidadId) {
        return (entidadTipo == null ? "?" : entidadTipo) + ":" + entidadId;
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

    public Long getIdContratoOrigen() {
        return idContratoOrigen;
    }

    public void setIdContratoOrigen(Long idContratoOrigen) {
        this.idContratoOrigen = idContratoOrigen;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public OffsetDateTime getFechaProgramada() {
        return fechaProgramada;
    }

    public void setFechaProgramada(OffsetDateTime fechaProgramada) {
        this.fechaProgramada = fechaProgramada;
    }

    public OffsetDateTime getFechaRecordatorio() {
        return fechaRecordatorio;
    }

    public OffsetDateTime getFechaCompletada() {
        return fechaCompletada;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoTarea.desde(estado).codigo();
    }

    @Transient
    public EstadoTarea estadoTipado() {
        return estado == null ? null : EstadoTarea.desde(estado);
    }

    public String getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(String prioridad) {
        this.prioridad = prioridad;
    }

    public String getEntidadCodigo() {
        return entidadCodigo;
    }

    public void setEntidadCodigo(String entidadCodigo) {
        this.entidadCodigo = entidadCodigo;
    }

    public String getRutaResolver() {
        return rutaResolver;
    }

    public void setRutaResolver(String rutaResolver) {
        this.rutaResolver = rutaResolver;
    }

    public Integer getDiasSinAccion() {
        return diasSinAccion;
    }

    public void setDiasSinAccion(Integer diasSinAccion) {
        this.diasSinAccion = diasSinAccion;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }
}
