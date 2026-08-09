package com.controllocal.domain.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Contador del bloqueo por intentos fallidos (D-S0-21). Una fila por intento
 * de entrar, con la clave <b>hasheada</b>.
 *
 * <p><b>Por que NO hereda de {@code EntidadDeOrganizacion}</b>, y por eso esta
 * declarada como global en {@code ArquitecturaTenancyTest}: un intento de
 * acceso es <b>PRE-TENANT</b>. Ocurre antes de saber quien pregunta, y contar
 * solo los intentos contra cuentas que existen convertiria el propio bloqueo
 * en un oraculo del padron de usuarios: bastaria observar quien se bloquea
 * para saber que nombres son reales. {@code organizacionId} queda como dato
 * informativo, nullable.
 *
 * <p>Vive en PostgreSQL y no en memoria: en memoria un reinicio borra el
 * contador y con N instancias el limite efectivo se multiplica por N (H-07).
 * Y no en Redis: es infraestructura nueva que el encargo prohibe sin necesidad
 * demostrada.
 */
@Entity
@Table(name = "intento_acceso")
public class IntentoAcceso {

    /** Se cuenta por cuenta Y por IP: cada dimension frena un ataque distinto. */
    public static final String CLAVE_CUENTA = "CUENTA";
    public static final String CLAVE_IP = "IP";

    /**
     * V37 (D-S0-32): fallos de <b>segundo factor</b> por cuenta.
     *
     * <p>Va aparte de {@link #CLAVE_CUENTA} a proposito. Si compartieran cupo,
     * fallar el codigo TOTP acercaria al bloqueo del login y al reves — dos
     * controles distintos gastandose el uno al otro. Y es la dimension que
     * hace que <b>pedir un desafio nuevo no reinicie el contador</b>: el limite
     * por desafio solo, sin esta, se esquiva pidiendo desafios.
     */
    public static final String CLAVE_MFA_CUENTA = "MFA_CUENTA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_intento")
    private Long id;

    /** Informativo y nullable: ver la nota de la clase. */
    @Column(name = "organizacion_id")
    private Long organizacionId;

    @Column(name = "clave_tipo", nullable = false, length = 10)
    private String claveTipo;

    /** SHA-256 hex del identificador normalizado. Nunca el usuario en claro. */
    @Column(name = "clave_valor_hash", nullable = false, length = 64)
    private String claveValorHash;

    @Column(name = "ocurrido_en", nullable = false)
    private OffsetDateTime ocurridoEn;

    @Column(name = "exito", nullable = false)
    private boolean exito;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "agente_usuario", length = 300)
    private String agenteUsuario;

    public Long getId() {
        return id;
    }

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public void setOrganizacionId(Long organizacionId) {
        this.organizacionId = organizacionId;
    }

    public String getClaveTipo() {
        return claveTipo;
    }

    public void setClaveTipo(String claveTipo) {
        this.claveTipo = claveTipo;
    }

    public String getClaveValorHash() {
        return claveValorHash;
    }

    public void setClaveValorHash(String claveValorHash) {
        this.claveValorHash = claveValorHash;
    }

    public OffsetDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public void setOcurridoEn(OffsetDateTime ocurridoEn) {
        this.ocurridoEn = ocurridoEn;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getAgenteUsuario() {
        return agenteUsuario;
    }

    public void setAgenteUsuario(String agenteUsuario) {
        this.agenteUsuario = agenteUsuario;
    }
}
