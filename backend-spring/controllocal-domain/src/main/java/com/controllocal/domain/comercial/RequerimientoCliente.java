package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoRequerimiento;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.persona.DetalleCliente;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>Lo que un cliente busca</b>: que operacion, que tipos de inmueble, en que
 * zonas, con que presupuesto y con que condiciones. Es lo que hace posible el
 * matching contextual ({@code CoincidenciaService} y su motor determinista).
 *
 * <p>RF028–030: ABIERTA participa según vigencia y permiso actual. RETIRADA y
 * FINALIZADA conservan la historia y no cierran oportunidades automáticamente.
 *
 * <h2>El requerimiento habla el mismo idioma que la propiedad</h2>
 * Hasta el Corte 7 esta entidad solo sabia decir <b>alquiler comercial</b>:
 * {@code renta_min}/{@code renta_max}, un {@code rubro} obligatorio y
 * <b>un</b> tipo de inmueble escrito con un vocabulario propio
 * —{@code LOCAL_COMERCIAL}, {@code DEPOSITO_ALMACEN}— distinto del de la
 * propiedad —{@code L}, {@code A}—. La consecuencia no era cosmetica: ALMACEN y
 * {@code DEPOSITO_ALMACEN} son el mismo concepto y el matcher los declaraba no
 * comparables.
 *
 * <pre>
 *   antes                        modelo universal
 *   ---------------------------  ------------------------------------------
 *   (implicito) alquiler         operacion            VENTA | ALQUILER
 *   renta_min / renta_max        presupuestoMin/Max   renta o precio, segun
 *   rubro NOT NULL               rubro (etiqueta) + criterio gobernado
 *   tipo_inmueble (uno, enum)    tipos (varios, L O A D C T X)
 *   frente_minimo                criterio gobernado sobre `frente`
 *   —                            vigenteHasta / fechaRevalidacion
 * </pre>
 *
 * <h2>Las columnas del modelo viejo siguen en la tabla, y no se mapean</h2>
 * {@code renta_min}, {@code renta_max}, {@code frente_minimo} y
 * {@code tipo_inmueble} conservan intacto lo que decia cada requerimiento antes
 * de traducirse —es lo que permite volver a distinguir un {@code STAND_MODULO}
 * de un {@code OTRO}, que se traducen los dos a {@code X}—, pero <b>no tienen
 * campo aqui</b>: sin campo no hay forma de escribirlas, y una segunda columna
 * escribible con el mismo significado es como se llega a que el importe se
 * guarde en un sitio y se lea de otro.
 */
@Entity
@Table(name = "requerimiento_cliente")
public class RequerimientoCliente extends EntidadDeOrganizacion {

    public static final String ABIERTA = "A";
    public static final String RETIRADA = "R";
    public static final String FINALIZADA = "F";
    public static final Set<String> ESTADOS = Set.of(ABIERTA, RETIRADA, FINALIZADA);

    /**
     * Los seis nombres que el modelo viejo admitia en {@code tipo_inmueble}.
     *
     * <p>Se conservan porque la puerta REST sigue aceptandolos —el SPA los
     * envia— y porque la columna de linaje los guarda. <b>No son el
     * vocabulario del requerimiento</b>: ese es el de siete codigos que
     * comparte con la propiedad, y la traduccion vive en un solo sitio
     * ({@code TipoDemandado}), junto a la migración universal.
     */
    public static final Set<String> TIPOS_INMUEBLE_LEGADO = Set.of(
            "LOCAL_COMERCIAL", "OFICINA", "DEPOSITO_ALMACEN",
            "STAND_MODULO", "TERRENO_COMERCIAL", "OTRO");

    /** Alias de compatibilidad para consumidores que aún leen el cable legado. */
    public static final Set<String> TIPOS_INMUEBLE = TIPOS_INMUEBLE_LEGADO;

    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requerimiento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_cliente")
    private DetalleCliente cliente;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "id_rol_responsable")
    private Long idRolResponsable;

    @Column(name = "id_persona_interesada")
    private Long idPersonaInteresada;

    @Column(name = "interesado_contexto_json")
    private String interesadoContextoJson;

    @Column(name = "finalidad")
    private String finalidad;

    @Column(name = "vigente_desde")
    private LocalDate vigenteDesde;

    @Column(name = "procedencia_json")
    private String procedenciaJson;

    @Column(name = "concepto_presupuesto")
    private String conceptoPresupuesto;

    @Column(name = "periodo_presupuesto")
    private String periodoPresupuesto;

    @Column(name = "fin_tipo", length = 1)
    private String finTipo;

    @Column(name = "fin_fecha")
    private LocalDate finFecha;

    @Column(name = "fin_motivo")
    private String finMotivo;

    @Column(name = "fin_registrado_en")
    private OffsetDateTime finRegistradoEn;

    /**
     * <b>De que operacion habla</b>. Sin ella, «180.000» y «2.900» son el
     * mismo dato con distinta magnitud, y distinguir venta de alquiler por el
     * tamano del numero es lo que el modelo economico prohibe.
     */
    @Column(name = "operacion", nullable = false, length = 1)
    private String operacion;

    /**
     * La ETIQUETA del negocio del cliente («Cafeteria»), que se sigue
     * mostrando. Dejo de ser criterio directo de matching: pedir un rubro es un
     * criterio gobernado sobre {@code rubro_permitido}, con su peso.
     *
     * <p>Y dejo de ser obligatorio, que era lo que obligaba a inventarse uno
     * para poder registrar la busqueda de una vivienda.
     */
    @Column(name = "rubro", length = 150)
    private String rubro;

    /**
     * Campos del cable anterior. Se conservan como linaje y para que las
     * lecturas históricas no pierdan información; el flujo universal escribe
     * {@code presupuesto_*} y {@code tipos}.
     */
    @Column(name = "tipo_inmueble", length = 30)
    private String tipoInmueble;

    @Column(name = "renta_min", precision = 12, scale = 2)
    private BigDecimal rentaMin;

    @Column(name = "renta_max", precision = 12, scale = 2)
    private BigDecimal rentaMax;

    @Column(name = "frente_minimo", precision = 8, scale = 2)
    private BigDecimal frenteMinimo;

    @Column(name = "moneda", length = 3)
    private String moneda;

    /** Renta mensual si la operacion es ALQUILER; precio si es VENTA. */
    @Column(name = "presupuesto_min", precision = 14, scale = 2)
    private BigDecimal presupuestoMin;

    @Column(name = "presupuesto_max", precision = 14, scale = 2)
    private BigDecimal presupuestoMax;

    @Column(name = "metraje_min", precision = 10, scale = 2)
    private BigDecimal metrajeMin;

    @Column(name = "metraje_max", precision = 10, scale = 2)
    private BigDecimal metrajeMax;

    /**
     * Hasta cuando vale lo declarado. NULL es FALTANTE legitimo —muchos
     * clientes no ponen fecha— y <b>no</b> saca al requerimiento del matcher:
     * quien decide que entra al cruce sigue siendo {@link #estado}. Publicar la
     * fecha y filtrar por ella son dos decisiones distintas, y la segunda no se
     * toma de paso.
     */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Cuando se confirmo por ultima vez que el cliente sigue buscando esto. */
    @Column(name = "fecha_revalidacion")
    private OffsetDateTime fechaRevalidacion;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = ABIERTA;

    @Column(name = "observaciones")
    private String observaciones;

    /**
     * Zonas buscadas. El catalogo de distritos es GLOBAL (compartido entre
     * organizaciones), asi que la tabla puente no lleva tenant: lo aporta el
     * requerimiento.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "requerimiento_distrito",
            joinColumns = @JoinColumn(name = "id_requerimiento"),
            inverseJoinColumns = @JoinColumn(name = "id_distrito"))
    private List<Distrito> distritos = new ArrayList<>();

    /**
     * Los tipos que el cliente acepta, en el vocabulario de UN CARACTER que ya
     * usa {@code propiedad.tipo_inmueble}.
     *
     * <p>Varios y no uno porque quien busca un local para su cafeteria acepta
     * casi siempre un local <b>o</b> un stand; obligarle a elegir uno partia la
     * demanda en dos requerimientos que el matcher no relacionaba.
     *
     * <p>Vacio significa <b>cualquier tipo</b>, no «ninguno»: es lo mismo que
     * ya significaba no declarar {@code tipo_inmueble}, y por eso el criterio
     * no aplica en vez de fallar.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "requerimiento_tipo_inmueble",
            joinColumns = @JoinColumn(name = "id_requerimiento"))
    @Column(name = "tipo_inmueble", nullable = false, length = 1)
    private Set<String> tipos = new LinkedHashSet<>();

    @Column(name = "fecha_creacion", updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    /** La escribe la app: el alta devuelve la fecha en la MISMA respuesta. */
    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public boolean estaActivo() {
        return ABIERTA.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public DetalleCliente getCliente() {
        return cliente;
    }

    public void setCliente(DetalleCliente cliente) {
        this.cliente = cliente;
    }

    public long getVersion() { return version; }
    public Long getIdRolResponsable() { return idRolResponsable; }
    public void setIdRolResponsable(Long valor) { idRolResponsable = valor; }
    public Long getIdPersonaInteresada() { return idPersonaInteresada; }
    public void setIdPersonaInteresada(Long valor) { idPersonaInteresada = valor; }
    public String getInteresadoContextoJson() { return interesadoContextoJson; }
    public void setInteresadoContextoJson(String valor) { interesadoContextoJson = valor; }
    public String getFinalidad() { return finalidad; }
    public void setFinalidad(String valor) { finalidad = valor; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDate valor) { vigenteDesde = valor; }
    public String getProcedenciaJson() { return procedenciaJson; }
    public void setProcedenciaJson(String valor) { procedenciaJson = valor; }
    public String getConceptoPresupuesto() { return conceptoPresupuesto; }
    public void setConceptoPresupuesto(String valor) { conceptoPresupuesto = valor; }
    public String getPeriodoPresupuesto() { return periodoPresupuesto; }
    public void setPeriodoPresupuesto(String valor) { periodoPresupuesto = valor; }
    public String getFinTipo() { return finTipo; }
    public LocalDate getFinFecha() { return finFecha; }
    public String getFinMotivo() { return finMotivo; }
    public OffsetDateTime getFinRegistradoEn() { return finRegistradoEn; }
    public void finalizar(String tipo, LocalDate fecha, String motivo, OffsetDateTime registradoEn) {
        if (!ABIERTA.equals(estado) || (!RETIRADA.equals(tipo) && !FINALIZADA.equals(tipo)))
            throw new IllegalStateException("Sólo una demanda abierta puede retirarse o finalizarse.");
        estado = tipo; finTipo = tipo; finFecha = fecha; finMotivo = motivo; finRegistradoEn = registradoEn;
    }

    public String getRubro() {
        return rubro;
    }

    public void setRubro(String rubro) {
        this.rubro = rubro;
    }

    public String getTipoInmueble() {
        return tipoInmueble;
    }

    public void setTipoInmueble(String tipoInmueble) {
        this.tipoInmueble = tipoInmueble;
    }

    public BigDecimal getRentaMin() {
        return rentaMin;
    }

    public void setRentaMin(BigDecimal rentaMin) {
        this.rentaMin = rentaMin;
    }

    public BigDecimal getRentaMax() {
        return rentaMax;
    }

    public void setRentaMax(BigDecimal rentaMax) {
        this.rentaMax = rentaMax;
    }

    public String getOperacion() {
        return operacion;
    }

    /**
     * <b>Rechaza el nulo en vez de rellenarlo.</b> Un requerimiento sin
     * operacion no se puede cruzar con ninguna oferta: una compra no casa con
     * un alquiler, y suponer «alquiler» porque es lo que el sistema sabia hacer
     * es la inferencia silenciosa que {@code OperacionInmobiliaria} existe para
     * impedir.
     */
    public void setOperacion(String operacion) {
        this.operacion = operacion == null || operacion.isBlank()
                ? null : OperacionInmobiliaria.desde(operacion).codigo();
    }

    public void setOperacion(OperacionInmobiliaria operacion) {
        this.operacion = operacion == null ? null : operacion.codigo();
    }

    @Transient
    public OperacionInmobiliaria operacion() {
        return OperacionInmobiliaria.deCodigo(operacion);
    }

    public BigDecimal getPresupuestoMin() {
        return presupuestoMin;
    }

    public void setPresupuestoMin(BigDecimal presupuestoMin) {
        this.presupuestoMin = presupuestoMin;
    }

    public BigDecimal getPresupuestoMax() {
        return presupuestoMax;
    }

    public void setPresupuestoMax(BigDecimal presupuestoMax) {
        this.presupuestoMax = presupuestoMax;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public OffsetDateTime getFechaRevalidacion() {
        return fechaRevalidacion;
    }

    /** La escribe {@code POST /requerimientos/{id}/revalidar} y nada mas. */
    public void revalidar(OffsetDateTime cuando) {
        this.fechaRevalidacion = cuando;
    }

    public Set<String> getTipos() {
        return tipos;
    }

    public void setTipos(Set<String> tipos) {
        this.tipos = tipos == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tipos);
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMetrajeMin() {
        return metrajeMin;
    }

    public void setMetrajeMin(BigDecimal metrajeMin) {
        this.metrajeMin = metrajeMin;
    }

    public BigDecimal getMetrajeMax() {
        return metrajeMax;
    }

    public void setMetrajeMax(BigDecimal metrajeMax) {
        this.metrajeMax = metrajeMax;
    }

    public BigDecimal getFrenteMinimo() {
        return frenteMinimo;
    }

    public void setFrenteMinimo(BigDecimal frenteMinimo) {
        this.frenteMinimo = frenteMinimo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoRequerimiento.desde(estado).codigo();
    }

    @Transient
    public EstadoRequerimiento estadoTipado() {
        return estado == null ? null : EstadoRequerimiento.desde(estado);
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public List<Distrito> getDistritos() {
        return distritos;
    }

    public void setDistritos(List<Distrito> distritos) {
        this.distritos = distritos == null ? new ArrayList<>() : distritos;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
