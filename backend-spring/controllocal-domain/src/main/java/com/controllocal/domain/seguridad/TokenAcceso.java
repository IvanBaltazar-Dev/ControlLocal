package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
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

/**
 * Secreto de un solo uso, hasheado (Plan S0 §4.3, V31; ampliado en V37).
 *
 * <p>Cuatro flujos, una sola mecanica:
 * <ul>
 *   <li>{@link #TIPO_RECUPERACION} — lo pide el propio titular.</li>
 *   <li>{@link #TIPO_INVITACION} — lo emite el gobierno del tenant.</li>
 *   <li>{@link #TIPO_DESAFIO_MFA} — el segundo paso del login (D-S0-22).</li>
 *   <li>{@link #TIPO_ELEVACION} — reautenticacion reforzada (D-S0-34).</li>
 * </ul>
 *
 * <p><b>EL TIPO ES OBLIGATORIO EN TODA OPERACION</b> — emision, busqueda,
 * consumo e invalidacion (D-S0-23). Es la condicion que hace segura la
 * reutilizacion de la tabla: sin ella, un desafio de MFA podria canjearse como
 * si fuera una recuperacion de contrasena. El indice unico parcial tambien es
 * por {@code (credencial, tipo)}, para que emitir un desafio no mate una
 * recuperacion pendiente.
 *
 * <p><b>La regla que manda sobre las dos:</b> un administrador nunca ve, fija
 * ni recupera la contrasena de otra persona. El token es el unico camino y el
 * titular define su clave al canjearlo. Por eso aqui se guarda el
 * <b>hash</b> del token y no el token: quien lea esta tabla no puede usar lo
 * que encuentre.
 *
 * <p>A diferencia de {@link IntentoAcceso}, esto <b>si</b> es privado de una
 * organizacion: cuelga de una credencial concreta, que ya vive en un tenant.
 */
@Entity
@Table(name = "token_acceso")
public class TokenAcceso extends EntidadDeOrganizacion {

    public static final String TIPO_RECUPERACION = "RECUPERACION";
    public static final String TIPO_INVITACION = "INVITACION";
    public static final String TIPO_DESAFIO_MFA = "DESAFIO_MFA";
    public static final String TIPO_ELEVACION = "ELEVACION";

    public static final String VIGENTE = Codigos.TokenAcceso.VIGENTE;
    public static final String CONSUMIDO = Codigos.TokenAcceso.CONSUMIDO;
    public static final String REVOCADO = Codigos.TokenAcceso.REVOCADO;
    public static final String AGOTADO = Codigos.TokenAcceso.AGOTADO;

    /** Intentos fallidos admitidos contra UN desafio antes de matarlo (D-S0-32). */
    public static final int INTENTOS_MAXIMOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Long id;

    @Column(name = "id_credencial", nullable = false)
    private Long idCredencial;

    @Column(name = "tipo", nullable = false, length = 15)
    private String tipo;

    /** SHA-256 hex del token entregado. Nunca el token. */
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    /** Sellado dentro de la MISMA transaccion que cambia la clave. */
    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    /** Lo mata la emision de un token nuevo para la misma credencial. */
    @Column(name = "invalidado_en")
    private OffsetDateTime invalidadoEn;

    /** Quien lo emitio. {@code null} = lo pidio el propio titular. */
    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Fallos contra ESTE token. Solo uno de los tres controles (D-S0-32). */
    @Column(name = "intentos", nullable = false)
    private short intentos;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado = VIGENTE;

    /**
     * Un token sirve una sola vez, no ha caducado, nadie lo ha reemplazado y no
     * agoto sus intentos. Las condiciones se comprueban juntas a proposito:
     * separarlas invita a que una llamada compruebe tres y se olvide de la
     * cuarta.
     */
    public boolean vigenteEn(OffsetDateTime instante) {
        return usadoEn == null && invalidadoEn == null
                && VIGENTE.equals(estado) && expiraEn.isAfter(instante);
    }

    /**
     * Suma un fallo y devuelve si al token le queda vida. Al agotarse muere:
     * hay que volver a autenticarse con la contrasena, que es lo que impide
     * recorrer el espacio de un TOTP de seis digitos contra un solo desafio.
     */
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

    /** Vista tipada del codigo persistido (convencion de EstadosDominio). */
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
