package com.controllocal.domain.organizacion;

import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * Tenant: la corredora dueña de sus datos (D-16). Es la raiz de la frontera
 * organizacional; todo lo privado cuelga de ella por
 * {@link com.controllocal.domain.comun.EntidadDeOrganizacion}.
 *
 * <p>En V6 solo existe el tenant de legado {@link #CODIGO_LEGADO}: la
 * plataforma sigue operando como mono-tenant, pero el esquema ya soporta
 * varios (ver el gate #7 del plan). SIVAN es la empresa dueña de la
 * plataforma, NO un tenant.
 */
@Entity
@Table(name = "organizacion")
public class Organizacion {

    /** Organizacion a la que pertenece todo lo migrado en V1..V5. */
    public static final String CODIGO_LEGADO = "BROX_LEGACY";

    /** 'A' activa, 'I' inactiva. */
    public static final String ESTADO_ACTIVA = Codigos.ActivoInactivo.ACTIVO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_organizacion")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = ESTADO_ACTIVA;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * V37: esta organizacion ya cruzo a MFA de gobierno — su primer
     * {@code TENANT_ADMIN} activo un segundo factor.
     *
     * <p>Solo la enciende la confirmacion del enrolamiento, en la misma
     * transaccion, y <b>nunca se apaga</b>. La consulta el trigger del
     * invariante operativo para no exigir lo imposible antes de que nadie haya
     * podido enrolar.
     */
    @jakarta.persistence.Column(name = "mfa_gobierno_exigido", nullable = false)
    private boolean mfaGobiernoExigido;

    public boolean estaActiva() {
        return estadoTipado() == EstadoActivoInactivo.ACTIVO;
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoActivoInactivo.desde(estado).codigo();
    }

    @Transient
    public EstadoActivoInactivo estadoTipado() {
        return estado == null ? null : EstadoActivoInactivo.desde(estado);
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public boolean isMfaGobiernoExigido() {
        return mfaGobiernoExigido;
    }
}
