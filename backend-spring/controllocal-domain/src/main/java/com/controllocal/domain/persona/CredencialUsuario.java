package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * Detalle del rol USUARIO_INTERNO: credenciales de acceso al sistema.
 * PK compartida con el rol (composicion, no herencia).
 * El hash usa el formato pbkdf2$iteraciones$sal$hash, compatible con el
 * backend Jakarta durante la convivencia del Strangler.
 */
@Entity
@Table(name = "credencial_usuario")
public class CredencialUsuario extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_persona_rol")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id_persona_rol")
    private PersonaRol rol;

    @Column(name = "nombre_usuario", nullable = false, length = 60)
    private String nombreUsuario;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    /** 'A' activo, 'I' inactivo (suspendido administrativamente). */
    @Column(name = "estado_administrativo", nullable = false, length = 1)
    private String estadoAdministrativo = Codigos.ActivoInactivo.ACTIVO;

    /**
     * D-S0-12 (V29): instante desde el que <b>toda sesion viva de esta cuenta
     * deja de valer</b>. Un token cuyo {@code iat} sea anterior se rechaza con
     * 401.
     * <p>
     * Es la pieza que permite un logout con efecto en servidor <b>sin tocar el
     * formato del token</b>, que sigue congelado mientras GlassFish conviva.
     * {@code null} = nunca se invalido nada.
     * <p>
     * Nada que ver con la autorizacion de datos personales (D-27): aquello es
     * una constancia del alta y no tiene flujo de revocacion.
     */
    @Column(name = "sesiones_invalidas_desde")
    private OffsetDateTime sesionesInvalidasDesde;

    /**
     * V31 (§4.5): la sesion existe pero esta <b>capada</b>. El filtro solo
     * deja pasar {@code GET /sesion} y {@code POST /perfil/contrasena}; todo
     * lo demas responde 403 con un codigo distinguible para que el SPA sepa
     * llevar al usuario a la pantalla de cambio obligatorio en vez de
     * mostrarle un "no tienes permiso" que no explica nada.
     * <p>
     * Lo enciende la contrasena temporal; lo apaga el cambio efectivo.
     */
    @Column(name = "debe_cambiar_contrasena", nullable = false)
    private boolean debeCambiarContrasena;

    /**
     * V37 (D-S0-25): gemelo de {@link #debeCambiarContrasena} para el segundo
     * factor. La sesion existe y queda <b>capada</b> hasta enrolar.
     * <p>
     * Sin esto, exigir MFA a los administradores el dia del despliegue seria
     * una caida de gobierno autoinfligida: todavia no tienen factor, asi que
     * el gate los dejaria fuera de su propia organizacion.
     */
    @Column(name = "debe_enrolar_mfa", nullable = false)
    private boolean debeEnrolarMfa;

    /**
     * V31: cuando se fijo la contrasena vigente. {@code null} = nunca se
     * cambio desde el alta — es el estado de las 21 cuentas del seed, y por
     * eso el nulo se conserva en vez de rellenarse con una fecha inventada.
     */
    @Column(name = "password_actualizada_en")
    private OffsetDateTime passwordActualizadaEn;

    /**
     * V31 (§4.6): permite convivir dos algoritmos durante la migracion
     * progresiva del hash. Hoy solo existe {@code pbkdf2}.
     */
    @Column(name = "algoritmo_hash", nullable = false, length = 20)
    private String algoritmoHash = "pbkdf2";

    /** Solo autentica una credencial administrativamente activa con rol vigente. */
    public boolean autenticable() {
        return estadoAdministrativoTipado() == EstadoActivoInactivo.ACTIVO
                && rol != null && rol.estaVigente();
    }

    public Long getId() {
        return id;
    }

    public PersonaRol getRol() {
        return rol;
    }

    public void setRol(PersonaRol rol) {
        this.rol = rol;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public String getEstadoAdministrativo() {
        return estadoAdministrativo;
    }

    public void setEstadoAdministrativo(String estadoAdministrativo) {
        this.estadoAdministrativo = EstadoActivoInactivo.desde(estadoAdministrativo).codigo();
    }

    public OffsetDateTime getSesionesInvalidasDesde() {
        return sesionesInvalidasDesde;
    }

    public void setSesionesInvalidasDesde(OffsetDateTime sesionesInvalidasDesde) {
        this.sesionesInvalidasDesde = sesionesInvalidasDesde;
    }

    public boolean isDebeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    public void setDebeCambiarContrasena(boolean debeCambiarContrasena) {
        this.debeCambiarContrasena = debeCambiarContrasena;
    }

    public boolean isDebeEnrolarMfa() {
        return debeEnrolarMfa;
    }

    public void setDebeEnrolarMfa(boolean debeEnrolarMfa) {
        this.debeEnrolarMfa = debeEnrolarMfa;
    }

    public OffsetDateTime getPasswordActualizadaEn() {
        return passwordActualizadaEn;
    }

    public void setPasswordActualizadaEn(OffsetDateTime passwordActualizadaEn) {
        this.passwordActualizadaEn = passwordActualizadaEn;
    }

    public String getAlgoritmoHash() {
        return algoritmoHash;
    }

    public void setAlgoritmoHash(String algoritmoHash) {
        this.algoritmoHash = algoritmoHash;
    }

    @Transient
    public EstadoActivoInactivo estadoAdministrativoTipado() {
        return estadoAdministrativo == null ? null : EstadoActivoInactivo.desde(estadoAdministrativo);
    }
}
