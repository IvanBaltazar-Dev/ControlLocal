package com.controllocal.domain.disponibilidad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** CAT-R21/D2: confirmaciones específicas conservadas; no es estado contractual ni una tarea. */
@Entity
@Table(name = "confirmacion_disponibilidad_contextual")
public class ConfirmacionDisponibilidadContextual extends EntidadDeOrganizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_confirmacion", nullable = false)
    private Long id;
    @Column(name = "tipo_recurso", nullable = false, length = 10)
    private String tipoRecurso;
    @Column(name = "id_recurso", nullable = false)
    private long idRecurso;
    @Column(name = "operacion", nullable = false, length = 10)
    private String operacion;
    @Column(name = "aspecto", nullable = false, length = 40)
    private String aspecto;
    @Column(name = "contexto", nullable = false, length = 100)
    private String contexto;
    @Column(name = "version_registro", nullable = false)
    private long version;
    @Column(name = "version_recurso_observada", nullable = false)
    private long versionRecursoObservada;
    @Column(name = "disponible", nullable = false)
    private boolean disponible;
    @Column(name = "precision_hecho", nullable = false, length = 8)
    private String precisionHecho;
    @Column(name = "fecha_hecho", nullable = true)
    private Instant fechaHecho;
    @Column(name = "fecha_sin_hora", nullable = true)
    private LocalDate fechaSinHora;
    @Column(name = "base_frescura", nullable = false)
    private Instant baseFrescura;
    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;
    @Column(name = "id_persona_confirmante", nullable = false)
    private long idPersonaConfirmante;
    @Column(name = "id_rol_confirmante", nullable = false)
    private long idRolConfirmante;
    @Column(name = "fuente", nullable = false)
    private String fuente;
    @Column(name = "evidencia_ref", nullable = false)
    private String evidenciaRef;
    @Column(name = "procedencia", nullable = false)
    private String procedencia;
    @Column(name = "huella_sesion", nullable = false, length = 64)
    private String huellaSesion;
    @Column(name = "referencia_sesion_hash", nullable = false, length = 64)
    private String referenciaSesionHash;
    @Column(name = "revision_contexto", nullable = false)
    private long revisionContexto;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipoRecurso() { return tipoRecurso; }
    public void setTipoRecurso(String tipoRecurso) { this.tipoRecurso = tipoRecurso; }

    public long getIdRecurso() { return idRecurso; }
    public void setIdRecurso(long idRecurso) { this.idRecurso = idRecurso; }

    public String getOperacion() { return operacion; }
    public void setOperacion(String operacion) { this.operacion = operacion; }

    public String getAspecto() { return aspecto; }
    public void setAspecto(String aspecto) { this.aspecto = aspecto; }

    public String getContexto() { return contexto; }
    public void setContexto(String contexto) { this.contexto = contexto; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public long getVersionRecursoObservada() { return versionRecursoObservada; }
    public void setVersionRecursoObservada(long versionRecursoObservada) { this.versionRecursoObservada = versionRecursoObservada; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public String getPrecisionHecho() { return precisionHecho; }
    public void setPrecisionHecho(String precisionHecho) { this.precisionHecho = precisionHecho; }

    public Instant getFechaHecho() { return fechaHecho; }
    public void setFechaHecho(Instant fechaHecho) { this.fechaHecho = fechaHecho; }

    public LocalDate getFechaSinHora() { return fechaSinHora; }
    public void setFechaSinHora(LocalDate fechaSinHora) { this.fechaSinHora = fechaSinHora; }

    public Instant getBaseFrescura() { return baseFrescura; }
    public void setBaseFrescura(Instant baseFrescura) { this.baseFrescura = baseFrescura; }

    public Instant getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(Instant registradoEn) { this.registradoEn = registradoEn; }

    public long getIdPersonaConfirmante() { return idPersonaConfirmante; }
    public void setIdPersonaConfirmante(long idPersonaConfirmante) { this.idPersonaConfirmante = idPersonaConfirmante; }

    public long getIdRolConfirmante() { return idRolConfirmante; }
    public void setIdRolConfirmante(long idRolConfirmante) { this.idRolConfirmante = idRolConfirmante; }

    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }

    public String getEvidenciaRef() { return evidenciaRef; }
    public void setEvidenciaRef(String evidenciaRef) { this.evidenciaRef = evidenciaRef; }

    public String getProcedencia() { return procedencia; }
    public void setProcedencia(String procedencia) { this.procedencia = procedencia; }

    public String getHuellaSesion() { return huellaSesion; }
    public void setHuellaSesion(String huellaSesion) { this.huellaSesion = huellaSesion; }

    public String getReferenciaSesionHash() { return referenciaSesionHash; }
    public void setReferenciaSesionHash(String referenciaSesionHash) { this.referenciaSesionHash = referenciaSesionHash; }

    public long getRevisionContexto() { return revisionContexto; }
    public void setRevisionContexto(long revisionContexto) { this.revisionContexto = revisionContexto; }
}

