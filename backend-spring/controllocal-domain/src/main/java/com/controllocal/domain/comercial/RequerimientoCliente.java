package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoRequerimiento;
import com.controllocal.domain.inmueble.Distrito;
import com.controllocal.domain.persona.DetalleCliente;
import jakarta.persistence.Column;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Perfil de busqueda del cliente: que rubro, que tipo de inmueble, en que
 * zonas y con que rangos de renta, area y frente. Es lo que hace posible el
 * matching de cartera (soporte/CoincidenciaCartera).
 *
 * <p>Solo los ACTIVO entran al matching: pausar un requerimiento saca al
 * cliente de las recomendaciones sin perder su historial.
 *
 * <p>El estado viaja como codigo de un caracter; {@code tipoInmueble}
 * conserva el nombre historico independiente de esta maquina.
 */
@Entity
@Table(name = "requerimiento_cliente")
public class RequerimientoCliente extends EntidadDeOrganizacion {

    public static final String ACTIVO = Codigos.Requerimiento.ACTIVO;
    public static final String PAUSADO = Codigos.Requerimiento.PAUSADO;
    public static final String CERRADO = Codigos.Requerimiento.CERRADO;
    public static final Set<String> ESTADOS = Set.of(ACTIVO, PAUSADO, CERRADO);

    public static final Set<String> TIPOS_INMUEBLE = Set.of(
            "LOCAL_COMERCIAL", "OFICINA", "DEPOSITO_ALMACEN",
            "STAND_MODULO", "TERRENO_COMERCIAL", "OTRO");

    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requerimiento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_cliente", nullable = false)
    private DetalleCliente cliente;

    @Column(name = "rubro", nullable = false, length = 150)
    private String rubro;

    @Column(name = "tipo_inmueble", length = 30)
    private String tipoInmueble;

    @Column(name = "renta_min", precision = 12, scale = 2)
    private BigDecimal rentaMin;

    @Column(name = "renta_max", precision = 12, scale = 2)
    private BigDecimal rentaMax;

    @Column(name = "moneda", length = 3)
    private String moneda;

    @Column(name = "metraje_min", precision = 10, scale = 2)
    private BigDecimal metrajeMin;

    @Column(name = "metraje_max", precision = 10, scale = 2)
    private BigDecimal metrajeMax;

    @Column(name = "frente_minimo", precision = 8, scale = 2)
    private BigDecimal frenteMinimo;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = ACTIVO;

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
        return ACTIVO.equals(estado);
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
