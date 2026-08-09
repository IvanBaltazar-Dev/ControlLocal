package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoFactorAutenticacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * Segundo factor de una credencial (Bloque 6, V37).
 *
 * <p>El secreto se guarda <b>cifrado y reversible</b>, no hasheado: un TOTP hay
 * que poder recalcularlo. Esa es la diferencia con una contrasena, y por eso la
 * clave de cifrado vive fuera de la base (ver {@code CifradoSecretos}).
 *
 * <p>{@code ultimoPaso} es el anti-replay (D-S0-31). <b>No se compara desde
 * aqui</b>: la validacion es un {@code UPDATE} condicional en el repositorio,
 * porque dos peticiones simultaneas que lean este campo aceptarian el mismo
 * codigo antes de que ninguna escriba.
 */
@Entity
@Table(name = "factor_autenticacion")
public class FactorAutenticacion extends EntidadDeOrganizacion {

    public static final String TIPO_TOTP = "TOTP";

    public static final String PENDIENTE = Codigos.FactorAutenticacion.PENDIENTE;
    public static final String ACTIVO = Codigos.FactorAutenticacion.ACTIVO;
    public static final String REVOCADO = Codigos.FactorAutenticacion.REVOCADO;

    /** Un enrolamiento a medias no se queda vivo para siempre. */
    public static final int MINUTOS_PENDIENTE = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factor")
    private Long id;

    @Column(name = "id_credencial", nullable = false)
    private Long idCredencial;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo = TIPO_TOTP;

    @Column(name = "secreto_cifrado", nullable = false)
    private byte[] secretoCifrado;

    @Column(name = "nonce", nullable = false)
    private byte[] nonce;

    @Column(name = "version_clave", nullable = false)
    private short versionClave;

    @Column(name = "algoritmo", nullable = false, length = 20)
    private String algoritmo;

    @Column(name = "digitos", nullable = false)
    private short digitos;

    @Column(name = "periodo", nullable = false)
    private short periodo;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = PENDIENTE;

    @Column(name = "ultimo_paso")
    private Long ultimoPaso;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "activado_en")
    private OffsetDateTime activadoEn;

    @Column(name = "revocado_en")
    private OffsetDateTime revocadoEn;

    @Column(name = "ultimo_uso_en")
    private OffsetDateTime ultimoUsoEn;

    public boolean estaActivo() {
        return ACTIVO.equals(estado);
    }

    /**
     * Un enrolamiento sin confirmar caduca. Sin esto quedan secretos a medio
     * enrolar que nadie sabe si valen.
     */
    public boolean pendienteCaducado(OffsetDateTime ahora) {
        return PENDIENTE.equals(estado) && creadoEn != null
                && creadoEn.plusMinutes(MINUTOS_PENDIENTE).isBefore(ahora);
    }

    public Long getId() {
        return id;
    }

    public Long getIdCredencial() {
        return idCredencial;
    }

    public void setIdCredencial(Long idCredencial) {
        this.idCredencial = idCredencial;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public byte[] getSecretoCifrado() {
        return secretoCifrado;
    }

    public void setSecretoCifrado(byte[] secretoCifrado) {
        this.secretoCifrado = secretoCifrado;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public short getVersionClave() {
        return versionClave;
    }

    public void setVersionClave(short versionClave) {
        this.versionClave = versionClave;
    }

    public String getAlgoritmo() {
        return algoritmo;
    }

    public void setAlgoritmo(String algoritmo) {
        this.algoritmo = algoritmo;
    }

    public short getDigitos() {
        return digitos;
    }

    public void setDigitos(short digitos) {
        this.digitos = digitos;
    }

    public short getPeriodo() {
        return periodo;
    }

    public void setPeriodo(short periodo) {
        this.periodo = periodo;
    }

    public String getEstado() {
        return estado;
    }

    /** Vista tipada del codigo persistido (convencion de EstadosDominio). */
    @Transient
    public EstadoFactorAutenticacion estadoTipado() {
        return estado == null ? null : EstadoFactorAutenticacion.desde(estado);
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getUltimoPaso() {
        return ultimoPaso;
    }

    /**
     * <b>Solo para la ACTIVACION</b>, donde el paso con el que se confirmo el
     * enrolamiento se sella junto al cambio de estado. Un factor ya ACTIVO
     * <b>no</b> se sella por aqui: ahi manda el {@code UPDATE} condicional del
     * repositorio, que es lo unico que impide que dos peticiones simultaneas
     * acepten el mismo codigo.
     */
    public void setUltimoPaso(Long ultimoPaso) {
        this.ultimoPaso = ultimoPaso;
    }

    public void setUltimoUsoEn(OffsetDateTime ultimoUsoEn) {
        this.ultimoUsoEn = ultimoUsoEn;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActivadoEn() {
        return activadoEn;
    }

    public void setActivadoEn(OffsetDateTime activadoEn) {
        this.activadoEn = activadoEn;
    }

    public OffsetDateTime getRevocadoEn() {
        return revocadoEn;
    }

    public void setRevocadoEn(OffsetDateTime revocadoEn) {
        this.revocadoEn = revocadoEn;
    }

    public OffsetDateTime getUltimoUsoEn() {
        return ultimoUsoEn;
    }
}
