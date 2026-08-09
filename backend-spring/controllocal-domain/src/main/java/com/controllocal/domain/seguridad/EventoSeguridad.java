package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Auditoria de <b>accesos y privilegios</b> (Plan S0 §6.3). Registro
 * <b>APPEND-ONLY</b>: nunca se actualiza ni se borra una fila, porque un
 * registro de seguridad editable no prueba nada.
 *
 * <p>Se separa de {@code historial_estado} a proposito: aquello audita
 * <b>transiciones de negocio</b> ("la captacion paso de P a A"); esto audita
 * <b>quien entro, quien lo intento y a quien se le dio un privilegio</b>.
 * Mezclarlos volveria inmanejables las dos consultas.
 *
 * <p><b>Regla de higiene, verificada por test:</b> ni contrasenas, ni hashes,
 * ni tokens, ni secretos MFA en {@link #detalleJson}. Una auditoria que filtra
 * secretos es un agujero con sello de calidad.
 */
@Entity
@Table(name = "evento_seguridad")
public class EventoSeguridad extends EntidadDeOrganizacion {

    // --- Sesion ---
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String LOGIN_BLOQUEADO_429 = "LOGIN_BLOQUEADO_429";
    public static final String LOGOUT = "LOGOUT";
    public static final String SESIONES_INVALIDADAS = "SESIONES_INVALIDADAS";

    // --- Bloqueo (D-S0-21) ---
    public static final String CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    public static final String CUENTA_DESBLOQUEADA = "CUENTA_DESBLOQUEADA";

    /** OK | FALLO | BLOQUEADO. */
    public static final String RESULTADO_OK = "OK";
    public static final String RESULTADO_FALLO = "FALLO";
    public static final String RESULTADO_BLOQUEADO = "BLOQUEADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    @Column(name = "fecha", nullable = false)
    private OffsetDateTime fecha;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "resultado", nullable = false, length = 10)
    private String resultado;

    /**
     * Puede ser {@code null}, y ese es el caso mas interesante: un login
     * fallido contra un usuario que no existe no tiene credencial ni persona,
     * y aun asi hay que registrarlo.
     */
    @Column(name = "id_credencial")
    private Long idCredencial;

    @Column(name = "id_persona")
    private Long idPersona;

    @Column(name = "rol_efectivo", length = 20)
    private String rolEfectivo;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "agente_usuario", length = 300)
    private String agenteUsuario;

    /** A quien afecta una accion administrativa. NULL si es sobre uno mismo. */
    @Column(name = "id_objetivo")
    private Long idObjetivo;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Contexto extra. NUNCA secretos: ver la regla de higiene de la clase. */
    @Column(name = "detalle_json")
    private String detalleJson;

    public Long getId() {
        return id;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public void setFecha(OffsetDateTime fecha) {
        this.fecha = fecha;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public Long getIdCredencial() {
        return idCredencial;
    }

    public void setIdCredencial(Long idCredencial) {
        this.idCredencial = idCredencial;
    }

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getRolEfectivo() {
        return rolEfectivo;
    }

    public void setRolEfectivo(String rolEfectivo) {
        this.rolEfectivo = rolEfectivo;
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

    public Long getIdObjetivo() {
        return idObjetivo;
    }

    public void setIdObjetivo(Long idObjetivo) {
        this.idObjetivo = idObjetivo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getDetalleJson() {
        return detalleJson;
    }

    public void setDetalleJson(String detalleJson) {
        this.detalleJson = detalleJson;
    }
}
