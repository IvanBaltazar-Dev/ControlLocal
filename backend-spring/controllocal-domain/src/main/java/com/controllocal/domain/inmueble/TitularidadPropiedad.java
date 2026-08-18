package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Quien es dueno de que parte de una propiedad, y desde cuando (D-E4-1 M1,
 * V47).
 *
 * <p>Sustituye a {@code propiedad.id_rol_propietario}, que era 1:1 NOT NULL y
 * por tanto no admitia una copropiedad, una sucesion ni una sociedad conyugal
 * — que en el mercado inmobiliario son la norma. La columna vieja SE CONSERVA
 * mientras el cable la publique; esta es la fuente a partir de ahora.
 *
 * <p>Una copropiedad no es "dos duenos": es CUOTAS, un REPRESENTANTE con quien
 * se habla, y una VIGENCIA. Y una venta no borra al titular anterior: le pone
 * {@link #vigenteHasta}. Sin eso el historico de propiedad se pierde en el
 * primer cierre, que es el dato que un sistema inmobiliario no puede perder.
 *
 * <p>Dos invariantes las impone la BASE, no esta clase, porque son de conjunto
 * y ninguna instancia puede verlas: las cuotas vigentes de una propiedad suman
 * 100 (constraint trigger diferido) y exactamente una es representante (indice
 * unico parcial).
 */
@Entity
@Table(name = "titularidad_propiedad")
public class TitularidadPropiedad extends EntidadDeOrganizacion {

    /** Cuota de un titular unico. */
    public static final BigDecimal CUOTA_TOTAL = new BigDecimal("100.000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_titularidad")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /**
     * Rol PROPIETARIO de la persona titular. Igual que en {@link Propiedad}, la
     * columna {@code tipo_rol_propietario} con su DEFAULT, CHECK y FK compuesta
     * vive solo en la BD: es ella la que garantiza que el rol referenciado sea
     * de ese tipo y no otro.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_propietario", nullable = false)
    private com.controllocal.domain.persona.PersonaRol rolPropietario;

    /** Porcentaje de la propiedad. 100 cuando hay un solo titular. */
    @Column(name = "cuota", nullable = false, precision = 6, scale = 3)
    private BigDecimal cuota;

    /** Con quien se habla. No tiene por que ser el de mayor cuota. */
    @Column(name = "es_representante", nullable = false)
    private boolean esRepresentante;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** NULL = vigente. */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "motivo_fin", length = 200)
    private String motivoFin;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    /**
     * Titularidad unica al 100 %: el caso de una propiedad con un solo dueno,
     * que es como llega todo lo migrado de la v1.
     */
    public static TitularidadPropiedad unica(Long idOrganizacion, Long idPropiedad,
                                             com.controllocal.domain.persona.PersonaRol titular,
                                             LocalDate desde) {
        TitularidadPropiedad t = new TitularidadPropiedad();
        t.setOrganizacionId(idOrganizacion);
        t.idPropiedad = idPropiedad;
        t.rolPropietario = titular;
        t.cuota = CUOTA_TOTAL;
        t.esRepresentante = true;
        t.vigenteDesde = desde;
        return t;
    }

    @Transient
    public boolean estaVigente() {
        return vigenteHasta == null;
    }

    /**
     * Cierra la titularidad en vez de borrarla. Es la unica forma de terminar
     * una: la fila se conserva porque es la historia de la propiedad.
     */
    public void cerrar(LocalDate fecha, String motivo) {
        if (fecha == null) {
            throw new IllegalArgumentException("Cerrar una titularidad exige la fecha en que dejo de valer.");
        }
        if (fecha.isBefore(vigenteDesde)) {
            throw new IllegalArgumentException("Una titularidad no puede terminar antes de empezar.");
        }
        this.vigenteHasta = fecha;
        this.motivoFin = motivo;
        this.esRepresentante = false;
    }

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public com.controllocal.domain.persona.PersonaRol getRolPropietario() {
        return rolPropietario;
    }

    public void setRolPropietario(com.controllocal.domain.persona.PersonaRol rolPropietario) {
        this.rolPropietario = rolPropietario;
    }

    public BigDecimal getCuota() {
        return cuota;
    }

    public void setCuota(BigDecimal cuota) {
        this.cuota = cuota;
    }

    public boolean isEsRepresentante() {
        return esRepresentante;
    }

    public void setEsRepresentante(boolean esRepresentante) {
        this.esRepresentante = esRepresentante;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(LocalDate vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public String getMotivoFin() {
        return motivoFin;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
