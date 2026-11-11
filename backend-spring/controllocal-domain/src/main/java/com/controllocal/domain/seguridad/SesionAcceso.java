package com.controllocal.domain.seguridad;

import jakarta.persistence.*;
import java.time.Instant;

/** Sesión por flujo/dispositivo; la revisión invalida el contexto anterior sin elevar permisos. */
@Entity
@Table(name = "sesion_acceso")
public class SesionAcceso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion_acceso") private Long id;
    @Column(name = "referencia_hash", nullable = false, unique = true, length = 64) private String referenciaHash;
    @Column(name = "semilla_contextos", nullable = false, length = 64) private String semillaContextos;
    @Column(name = "id_cuenta_acceso", nullable = false) private Long idCuenta;
    @Column(name = "revision", nullable = false) private long revision;
    @Column(name = "creada_en", nullable = false) private Instant creadaEn;
    @Column(name = "emitida_en", nullable = false) private Instant emitidaEn;
    @Column(name = "vence_en", nullable = false) private Instant venceEn;
    @Column(name = "revocada", nullable = false) private boolean revocada;
    @Column(name = "id_usuario_organizacion") private Long idMembresia;
    @Column(name = "id_persona_rol") private Long idRolOperativo;
    @Column(name = "rol_efectivo", length = 24) private String rolEfectivo;
    @Column(name = "contexto_ref_hash", length = 64) private String contextoRefHash;
    public Long getId() { return id; }
    public String getReferenciaHash() { return referenciaHash; }
    public void setReferenciaHash(String valor) { referenciaHash=valor; }
    public String getSemillaContextos() { return semillaContextos; }
    public void setSemillaContextos(String valor) { semillaContextos=valor; }
    public Long getIdCuenta() { return idCuenta; }
    public void setIdCuenta(Long valor) { idCuenta=valor; }
    public long getRevision() { return revision; }
    public void setRevision(long valor) { revision=valor; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant valor) { creadaEn=valor; }
    public Instant getEmitidaEn() { return emitidaEn; }
    public void setEmitidaEn(Instant valor) { emitidaEn=valor; }
    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant valor) { venceEn=valor; }
    public boolean isRevocada() { return revocada; }
    public void setRevocada(boolean valor) { revocada=valor; }
    public Long getIdMembresia() { return idMembresia; }
    public void setIdMembresia(Long valor) { idMembresia=valor; }
    public Long getIdRolOperativo() { return idRolOperativo; }
    public void setIdRolOperativo(Long valor) { idRolOperativo=valor; }
    public String getRolEfectivo() { return rolEfectivo; }
    public void setRolEfectivo(String valor) { rolEfectivo=valor; }
    public String getContextoRefHash() { return contextoRefHash; }
    public void setContextoRefHash(String valor) { contextoRefHash=valor; }
}
