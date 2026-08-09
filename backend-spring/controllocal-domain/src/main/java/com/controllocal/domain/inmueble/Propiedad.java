package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.persona.PersonaRol;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Inmueble de la cartera (generaliza el local_comercial de la v1, MEJ-12/31):
 * aqui viven los atributos comunes a cualquier tipo; lo especifico del local
 * comercial esta en {@link DetalleLocalComercial} (composicion, obligatoria
 * para tipo L/O). El propietario es el rol PROPIETARIO de una persona
 * (Party-Role): en el cable congelado idPropietario = persona_rol.id.
 * Los codigos de 1 caracter son el vocabulario heredado del cable; los enums
 * legibles llegan tras el corte del modulo.
 */
@Entity
@Table(name = "propiedad")
public class Propiedad extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "PROPIEDAD";
    public static final String ENTIDAD_DISPONIBILIDAD_TIPO = "DISPONIBILIDAD_PROPIEDAD";

    /** Codigos del adaptador legado; no son columnas del modelo normalizado. */
    public static final String LEGADO_DISPONIBLE = "D";
    public static final String LEGADO_NO_DISPONIBLE = "N";
    public static final String LEGADO_INACTIVO = "I";
    public static final Set<String> ESTADOS = Set.of("D", "N", "I");

    /** 'L' local, 'O' oficina, 'D' departamento, 'C' casa, 'T' terreno, 'X' otro. */
    public static final String TIPO_LOCAL = "L";
    public static final String TIPO_OFICINA = "O";
    public static final Set<String> TIPOS_INMUEBLE = Set.of("L", "O", "D", "C", "T", "X");

    /** 'C' comercial, 'V' vivienda, 'I' industrial, 'M' mixto. */
    public static final String USO_COMERCIAL = "C";
    public static final Set<String> USOS = Set.of("C", "V", "I", "M");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_propiedad")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    /** Nombre de distrito tal como se escribio (cable congelado; ver Distrito). */
    @Column(name = "distrito", nullable = false, length = 100)
    private String distrito;

    /** FK resuelta contra el catalogo; NULL si el nombre no esta catalogado. */
    @Column(name = "id_distrito")
    private Long idDistrito;

    @Column(name = "metraje", nullable = false, precision = 10, scale = 2)
    private BigDecimal metraje;

    @Column(name = "precio_referencial", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioReferencial;

    /** Moneda declarada de la renta referencial; no tiene valor por defecto. */
    @Column(name = "moneda_referencial", length = 3)
    private String monedaReferencial;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "estado_registro", nullable = false, length = 1)
    private String estadoRegistro;

    @Column(name = "disponibilidad_comercial", nullable = false, length = 1)
    private String disponibilidadComercial;

    @Column(name = "interior_unidad", length = 40)
    private String interiorUnidad;

    @Column(name = "piso", length = 30)
    private String piso;

    @Column(name = "referencia_interna", length = 120)
    private String referenciaInterna;

    @Column(name = "nombre_edificio_galeria", length = 150)
    private String nombreEdificioGaleria;

    @Column(name = "tipo_inmueble", nullable = false, length = 1)
    private String tipoInmueble = TIPO_LOCAL;

    @Column(name = "uso", nullable = false, length = 1)
    private String uso = USO_COMERCIAL;

    @Column(name = "ambientes")
    private Integer ambientes;

    @Column(name = "antiguedad_anios")
    private Integer antiguedadAnios;

    @Column(name = "zona_urbanizacion", length = 150)
    private String zonaUrbanizacion;

    @Column(name = "geo_lat", precision = 10, scale = 7)
    private BigDecimal geoLat;

    @Column(name = "geo_long", precision = 10, scale = 7)
    private BigDecimal geoLong;

    @Column(name = "frente", precision = 8, scale = 2)
    private BigDecimal frente;

    @Column(name = "zonificacion", length = 40)
    private String zonificacion;

    @Column(name = "numero_estacionamientos")
    private Integer numeroEstacionamientos;

    @Column(name = "cuota_mantenimiento", precision = 10, scale = 2)
    private BigDecimal cuotaMantenimiento;

    /**
     * Rol PROPIETARIO de la persona duena (la columna tipo_rol_propietario
     * con DEFAULT+CHECK+FK compuesta vive solo en la BD y garantiza que el
     * rol referenciado sea de ese tipo). Se navega desde este lado, LAZY.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_propietario", nullable = false)
    private PersonaRol rolPropietario;

    @OneToOne(mappedBy = "propiedad", cascade = CascadeType.ALL, orphanRemoval = true)
    private DetalleLocalComercial detalleLocal;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    // ------------------------------------------------------------------
    // Maquina de estados (Transicionable): el estado SOLO muta via el
    // componente Transiciones de la capa service (blindado por ArchUnit).
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return ENTIDAD_TIPO;
    }

    @Override
    public String estadoActual() {
        return estadoRegistro;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estadoRegistro = EstadoRegistroPropiedad.desde(nuevoEstado).codigo();
    }

    @Transient
    public EstadoRegistroPropiedad estadoRegistroTipado() {
        return estadoRegistro == null ? null : EstadoRegistroPropiedad.desde(estadoRegistro);
    }

    @Transient
    public DisponibilidadComercial disponibilidadComercialTipada() {
        return disponibilidadComercial == null
                ? null : DisponibilidadComercial.desde(disponibilidadComercial);
    }

    /**
     * Adaptador de frontera para el contrato D/N/I: el modelo interno nunca
     * vuelve a perder la causa de la no disponibilidad.
     */
    public String estadoLegado() {
        if (estadoRegistroTipado() == EstadoRegistroPropiedad.INACTIVO) return LEGADO_INACTIVO;
        return disponibilidadComercialTipada() == DisponibilidadComercial.DISPONIBLE
                ? LEGADO_DISPONIBLE : LEGADO_NO_DISPONIBLE;
    }

    public void iniciarDisponible() {
        estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
        disponibilidadComercial = DisponibilidadComercial.DISPONIBLE.codigo();
    }

    public void aplicarEstadoLegado(String estadoLegado) {
        if (LEGADO_INACTIVO.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.INACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.RETIRADO.codigo();
        } else if (LEGADO_DISPONIBLE.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.DISPONIBLE.codigo();
        } else if (LEGADO_NO_DISPONIBLE.equals(estadoLegado)) {
            estadoRegistro = EstadoRegistroPropiedad.ACTIVO.codigo();
            disponibilidadComercial = DisponibilidadComercial.RETIRADO.codigo();
        } else {
            throw new IllegalArgumentException("Estado legado de local invalido: " + estadoLegado);
        }
    }

    public void marcarAlquilado() {
        cambiarDisponibilidadA(DisponibilidadComercial.ALQUILADO);
    }

    public void retirarDelMercado() {
        cambiarDisponibilidadA(DisponibilidadComercial.RETIRADO);
    }

    public void reactivarDisponibilidad() {
        if (estadoRegistroTipado() != EstadoRegistroPropiedad.ACTIVO) {
            throw new IllegalStateException("Un local inactivo no puede reactivarse comercialmente.");
        }
        cambiarDisponibilidadA(DisponibilidadComercial.DISPONIBLE);
    }

    /** Escritura tipada de la dimension comercial; nunca recibe codigos libres. */
    public void cambiarDisponibilidadA(DisponibilidadComercial nuevaDisponibilidad) {
        if (nuevaDisponibilidad == null) {
            throw new IllegalArgumentException("La disponibilidad comercial es obligatoria.");
        }
        disponibilidadComercial = nuevaDisponibilidad.codigo();
    }

    public String getEstadoRegistro() { return estadoActual(); }
    public String getDisponibilidadComercial() {
        return disponibilidadComercial;
    }

    /**
     * Asigna o actualiza el detalle de local comercial manteniendo la
     * composicion. El detalle es parte del agregado, asi que hereda el tenant
     * de la propiedad: quien crea la propiedad no tiene que acordarse de
     * estampar la organizacion dos veces.
     */
    public void asignarDetalleLocal(String rubroPermitido, Boolean aptoLicenciaFuncionamiento,
                                    BigDecimal cargaElectricaKw) {
        if (detalleLocal == null) {
            detalleLocal = new DetalleLocalComercial(this);
        }
        detalleLocal.setOrganizacionId(getOrganizacionId());
        detalleLocal.setRubroPermitido(rubroPermitido);
        detalleLocal.setAptoLicenciaFuncionamiento(aptoLicenciaFuncionamiento);
        detalleLocal.setCargaElectricaKw(cargaElectricaKw);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }

    public Long getIdDistrito() {
        return idDistrito;
    }

    public void setIdDistrito(Long idDistrito) {
        this.idDistrito = idDistrito;
    }

    public BigDecimal getMetraje() {
        return metraje;
    }

    public void setMetraje(BigDecimal metraje) {
        this.metraje = metraje;
    }

    public BigDecimal getPrecioReferencial() {
        return precioReferencial;
    }

    public void setPrecioReferencial(BigDecimal precioReferencial) {
        this.precioReferencial = precioReferencial;
    }

    public String getMonedaReferencial() {
        return monedaReferencial;
    }

    public void setMonedaReferencial(String monedaReferencial) {
        this.monedaReferencial = monedaReferencial;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoInmueble() {
        return tipoInmueble;
    }

    public void setTipoInmueble(String tipoInmueble) {
        this.tipoInmueble = tipoInmueble;
    }

    public String getUso() {
        return uso;
    }

    public void setUso(String uso) {
        this.uso = uso;
    }

    public Integer getAmbientes() {
        return ambientes;
    }

    public void setAmbientes(Integer ambientes) {
        this.ambientes = ambientes;
    }

    public Integer getAntiguedadAnios() {
        return antiguedadAnios;
    }

    public void setAntiguedadAnios(Integer antiguedadAnios) {
        this.antiguedadAnios = antiguedadAnios;
    }

    public String getZonaUrbanizacion() {
        return zonaUrbanizacion;
    }

    public void setZonaUrbanizacion(String zonaUrbanizacion) {
        this.zonaUrbanizacion = zonaUrbanizacion;
    }

    public BigDecimal getGeoLat() {
        return geoLat;
    }

    public void setGeoLat(BigDecimal geoLat) {
        this.geoLat = geoLat;
    }

    public BigDecimal getGeoLong() {
        return geoLong;
    }

    public void setGeoLong(BigDecimal geoLong) {
        this.geoLong = geoLong;
    }

    public BigDecimal getFrente() {
        return frente;
    }

    public void setFrente(BigDecimal frente) {
        this.frente = frente;
    }

    public String getZonificacion() {
        return zonificacion;
    }

    public void setZonificacion(String zonificacion) {
        this.zonificacion = zonificacion;
    }

    public Integer getNumeroEstacionamientos() {
        return numeroEstacionamientos;
    }

    public void setNumeroEstacionamientos(Integer numeroEstacionamientos) {
        this.numeroEstacionamientos = numeroEstacionamientos;
    }

    public BigDecimal getCuotaMantenimiento() {
        return cuotaMantenimiento;
    }

    public void setCuotaMantenimiento(BigDecimal cuotaMantenimiento) {
        this.cuotaMantenimiento = cuotaMantenimiento;
    }

    public String getInteriorUnidad() { return interiorUnidad; }
    public void setInteriorUnidad(String interiorUnidad) { this.interiorUnidad = interiorUnidad; }
    public String getPiso() { return piso; }
    public void setPiso(String piso) { this.piso = piso; }
    public String getReferenciaInterna() { return referenciaInterna; }
    public void setReferenciaInterna(String referenciaInterna) { this.referenciaInterna = referenciaInterna; }
    public String getNombreEdificioGaleria() { return nombreEdificioGaleria; }
    public void setNombreEdificioGaleria(String nombreEdificioGaleria) { this.nombreEdificioGaleria = nombreEdificioGaleria; }

    public PersonaRol getRolPropietario() {
        return rolPropietario;
    }

    public void setRolPropietario(PersonaRol rolPropietario) {
        this.rolPropietario = rolPropietario;
    }

    public DetalleLocalComercial getDetalleLocal() {
        return detalleLocal;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
