package com.controllocal.domain.organizacion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * Pertenencia local de una cuenta global (CAT-E01/R19, C1). El principal
 * AGENTE/BROKER y la capacidad administrativa se ejercen en contextos separados.
 * La persona siempre pertenece a la misma organización; su enlace no amplía permisos.
 */
@Entity
@Table(name = "usuario_organizacion")
public class UsuarioOrganizacion extends EntidadDeOrganizacion {

    /** Atribución de gobierno, ejercida con persona_rol ADMIN y administrar=true. */
    public static final String ROL_TENANT_ADMIN = "TENANT_ADMIN";
    public static final String ROL_BROKER = "BROKER";
    public static final String ROL_AGENTE = "AGENTE";

    /** 'A' activa, 'I' inactiva. */
    public static final String ESTADO_ACTIVA = Codigos.ActivoInactivo.ACTIVO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_organizacion")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Cuenta personal global; no es la identidad de una Persona local. */
    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "rol", length = 20)
    private String rol;

    @Column(name = "administrar", nullable = false)
    private boolean administrar;

    @Column(name = "nombre_visible", length = 150)
    private String nombreVisible;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = ESTADO_ACTIVA;

    /** Persona del MISMO tenant, solo si el usuario tambien es actor del dominio. */
    @Column(name = "id_persona")
    private Long idPersona;

    @Column(name = "fecha_alta", insertable = false, updatable = false)
    private OffsetDateTime fechaAlta;

    /** Inicio real del intervalo aceptado actual; no es una fecha programada. */
    @Column(name = "alta_real_en", nullable = false)
    private OffsetDateTime altaRealEn;

    /** Baja formal efectiva del intervalo actual; nunca es un fin programado. */
    @Column(name = "baja_real_en")
    private OffsetDateTime bajaRealEn;

    public boolean estaActiva() {
        return estadoTipado() == EstadoActivoInactivo.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public long getVersion() { return version; }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getRol() {
        return rol;
    }

    public boolean isAdministrar() { return administrar; }
    public void setAdministrar(boolean valor) { administrar=valor; }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public void setNombreVisible(String nombreVisible) {
        this.nombreVisible = nombreVisible;
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

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public OffsetDateTime getFechaAlta() {
        return fechaAlta;
    }

    public OffsetDateTime getAltaRealEn() { return altaRealEn; }
    public void setAltaRealEn(OffsetDateTime instante) { altaRealEn=instante; }
    public OffsetDateTime getBajaRealEn() { return bajaRealEn; }
    public void setBajaRealEn(OffsetDateTime instante) { bajaRealEn=instante; }
}
