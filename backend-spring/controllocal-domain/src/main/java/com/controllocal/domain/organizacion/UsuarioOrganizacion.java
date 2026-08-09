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

import java.time.OffsetDateTime;

/**
 * Membresia de un usuario en una organizacion (D-26): dice CON QUE banda
 * operativa entra una cuenta a un tenant. Separa la CUENTA (global, D-22, que
 * nace en el corte de GlassFish) de su pertenencia a la corredora.
 *
 * <p>Durante la convivencia {@code idUsuario} apunta al {@code persona_rol.id}
 * del usuario interno (1:1 con el login de {@code credencial_usuario}); cuando
 * exista la cuenta global pasara a referenciarla sin tocar esta tabla.
 *
 * <p>{@code idPersona} es OPCIONAL: solo se llena si el usuario ademas es
 * actor del dominio (p. ej. un agente que alquila para si). No otorga permisos.
 */
@Entity
@Table(name = "usuario_organizacion")
public class UsuarioOrganizacion extends EntidadDeOrganizacion {

    /**
     * Banda del usuario dentro del tenant, AUTORITATIVA desde V33 (D-S0-8).
     * El vocabulario lo fija {@code ck_usuario_org_rol}.
     *
     * <p>{@code TENANT_ADMIN} sustituye al {@code ADMIN} de V6 y <b>no es un
     * renombrado</b>: aquel se derivaba de {@code detalle_broker.es_administrador}
     * y arrastraba toda la semantica de broker; este gobierna la organizacion y
     * no opera en el proceso comercial (D-S0-7, matriz D-S0-17).
     *
     * <p>{@code PLATFORM_ADMIN} esta reservado en el vocabulario pero todavia
     * no se emite: su mecanismo es la concesion temporal por tenant, que no
     * entra en este bloque.
     */
    public static final String ROL_TENANT_ADMIN = "TENANT_ADMIN";
    public static final String ROL_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROL_BROKER = "BROKER";
    public static final String ROL_AGENTE = "AGENTE";

    /** 'A' activa, 'I' inactiva. */
    public static final String ESTADO_ACTIVA = Codigos.ActivoInactivo.ACTIVO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_organizacion")
    private Long id;

    /** Cuenta de acceso (transitorio: persona_rol.id del usuario interno). */
    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "rol", nullable = false, length = 20)
    private String rol;

    @Column(name = "nombre_visible", length = 120)
    private String nombreVisible;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = ESTADO_ACTIVA;

    /** Persona del MISMO tenant, solo si el usuario tambien es actor del dominio. */
    @Column(name = "id_persona")
    private Long idPersona;

    @Column(name = "fecha_alta", insertable = false, updatable = false)
    private OffsetDateTime fechaAlta;

    public boolean estaActiva() {
        return estadoTipado() == EstadoActivoInactivo.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getRol() {
        return rol;
    }

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
}
