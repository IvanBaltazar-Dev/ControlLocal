package com.controllocal.domain.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Correlación técnica de CAT-E41/UC-K-16. Antes de autenticar no hay tenant ni
 * persona supuestos; al completar quedan ligados de forma indivisible. No es
 * una sesión, una aprobación comercial o un token de recuperación de cuenta.
 * Sólo persiste huellas de las referencias, nunca JWT, contraseña o factores.
 */
@Entity
@Table(name = "vinculo_interaccion_segura")
public class VinculoInteraccionSegura {
    @Id
    @Column(name = "hash_flujo", length = 64)
    private String hashFlujo;
    @Column(name = "proposito", nullable = false, length = 12)
    private String proposito;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;
    @Column(name = "completado_en")
    private Instant completadoEn;
    @Column(name = "usado_en")
    private Instant usadoEn;
    @Column(name = "organizacion_id")
    private Long organizacionId;
    @Column(name = "id_persona")
    private Long idPersona;
    @Column(name = "id_rol_operativo")
    private Long idRolOperativo;
    @Column(name = "rol_efectivo", length = 24)
    private String rolEfectivo;
    @Column(name = "contexto_sesion", length = 64)
    private String contextoSesion;
    @Column(name = "sesion_emitida_en")
    private Instant sesionEmitidaEn;
    @Column(name = "sesion_expira_en")
    private Instant sesionExpiraEn;
    @Column(name = "hash_retorno", length = 64)
    private String hashRetorno;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getHashFlujo() { return hashFlujo; }
    public void setHashFlujo(String valor) { hashFlujo = valor; }
    public String getProposito() { return proposito; }
    public void setProposito(String valor) { proposito = valor; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant valor) { creadoEn = valor; }
    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant valor) { venceEn = valor; }
    public Instant getCompletadoEn() { return completadoEn; }
    public void setCompletadoEn(Instant valor) { completadoEn = valor; }
    public Instant getUsadoEn() { return usadoEn; }
    public void setUsadoEn(Instant valor) { usadoEn = valor; }
    public Long getOrganizacionId() { return organizacionId; }
    public void setOrganizacionId(Long valor) { organizacionId = valor; }
    public Long getIdPersona() { return idPersona; }
    public void setIdPersona(Long valor) { idPersona = valor; }
    public Long getIdRolOperativo() { return idRolOperativo; }
    public void setIdRolOperativo(Long valor) { idRolOperativo = valor; }
    public String getRolEfectivo() { return rolEfectivo; }
    public void setRolEfectivo(String valor) { rolEfectivo = valor; }
    public String getContextoSesion() { return contextoSesion; }
    public void setContextoSesion(String valor) { contextoSesion = valor; }
    public Instant getSesionEmitidaEn() { return sesionEmitidaEn; }
    public void setSesionEmitidaEn(Instant valor) { sesionEmitidaEn = valor; }
    public Instant getSesionExpiraEn() { return sesionExpiraEn; }
    public void setSesionExpiraEn(Instant valor) { sesionExpiraEn = valor; }
    public String getHashRetorno() { return hashRetorno; }
    public void setHashRetorno(String valor) { hashRetorno = valor; }
    public long getVersion() { return version; }
}
