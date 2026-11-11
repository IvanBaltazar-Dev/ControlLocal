package com.controllocal.domain.seguridad;


import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoTokenAcceso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
@Entity
@Table(name = "token_acceso")
public class TokenAcceso {

    public static final String TIPO_RECUPERACION = "RECUPERACION";
    public static final String TIPO_ALTA_CORREO = "ALTA_CORREO";
    public static final String TIPO_CAMBIAR_CORREO = "CAMBIO_CORREO";
    public static final String TIPO_DESAFIO_MFA = "DESAFIO_MFA";

    public static final String VIGENTE = Codigos.TokenAcceso.VIGENTE;
    public static final String CONSUMIDO = Codigos.TokenAcceso.CONSUMIDO;
    public static final String REVOCADO = Codigos.TokenAcceso.REVOCADO;
    public static final String AGOTADO = Codigos.TokenAcceso.AGOTADO;

    /** Máximo de intentos fallidos de un desafío MFA; no afecta a los enlaces personales. */
    public static final int INTENTOS_MAXIMOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Long id;

    @Column(name = "id_cuenta_acceso")
    private Long idCuenta;

    @Column(name="correo_objetivo",length=320)
    private String correoObjetivo;
    public String getCorreoObjetivo(){return correoObjetivo;}
    public void setCorreoObjetivo(String valor){correoObjetivo=valor;}

    @Column(name = "tipo", nullable = false, length = 15)
    private String tipo;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Column(name = "invalidado_en")
    private OffsetDateTime invalidadoEn;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Column(name = "intentos", nullable = false)
    private short intentos;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = VIGENTE;

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    public boolean vigenteEn(OffsetDateTime instante) {
        return usadoEn == null && invalidadoEn == null
                && VIGENTE.equals(estado) && expiraEn.isAfter(instante);
    }

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    public boolean fallar() {
        intentos++;
        if (intentos >= INTENTOS_MAXIMOS) {
            estado = AGOTADO;
            return false;
        }
        return true;
    }

    public short getIntentos() {
        return intentos;
    }

    public String getEstado() {
        return estado;
    }

    /** Motor único de enlaces personales: alta, recuperación, cambio de correo y desafío MFA. Hash, propósito cerrado, caducidad y consumo atómico; nunca un token de gobierno sobre credencial ajena. */
    @Transient
    public EstadoTokenAcceso estadoTipado() {
        return estado == null ? null : EstadoTokenAcceso.desde(estado);
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public Long getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(Long idCuenta) {
        this.idCuenta = idCuenta;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getHashToken() {
        return hashToken;
    }

    public void setHashToken(String hashToken) {
        this.hashToken = hashToken;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public OffsetDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(OffsetDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public OffsetDateTime getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(OffsetDateTime usadoEn) {
        this.usadoEn = usadoEn;
    }

    public OffsetDateTime getInvalidadoEn() {
        return invalidadoEn;
    }

    public void setInvalidadoEn(OffsetDateTime invalidadoEn) {
        this.invalidadoEn = invalidadoEn;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(Long creadoPor) {
        this.creadoPor = creadoPor;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
