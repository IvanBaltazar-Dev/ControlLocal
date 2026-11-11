package com.controllocal.domain.matching;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** D4: metadata técnica privada; no conserva ficha, atributos ni resultados de otro tenant. */
@Entity
@Table(name = "matching_referencia")
public class ReferenciaRecorridoMatching extends EntidadDeOrganizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_referencia", nullable = false)
    private Long id;
    @Column(name = "id_recorrido", nullable = false)
    private UUID idRecorrido;
    @Column(name = "ordinal", nullable = false)
    private int ordinal;
    @Column(name = "capa",nullable = false,length = 2)
    private String capa;
    @Column(name = "tipo_recurso", nullable = false, length = 20)
    private String tipoRecurso;
    @Column(name = "referencia_recurso", nullable = false, length = 160)
    private String referenciaRecurso;
    @Column(name = "version_recurso", nullable = false)
    private long versionRecurso;
    @Column(name = "huella_proyeccion", nullable = false, length = 64)
    private String huellaProyeccion;
    @Column(name = "id_demanda", nullable = false)
    private long idDemanda;
    @Column(name = "version_demanda", nullable = false)
    private long versionDemanda;
    @Column(name = "huella_criterios", nullable = false, length = 64)
    private String huellaCriterios;

    /** Referencia exacta entregada, sin reconstruir configuración histórica. */
    @Column(name="referencia_candidato",length=64)
    private String referenciaCandidato;
    public String getReferenciaCandidato(){return referenciaCandidato;}
    public void setReferenciaCandidato(String valor){referenciaCandidato=valor;}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getIdRecorrido() { return idRecorrido; }
    public void setIdRecorrido(UUID idRecorrido) { this.idRecorrido = idRecorrido; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public String getCapa() { return capa; }
    public void setCapa(String capa) { this.capa = capa; }

    public String getTipoRecurso() { return tipoRecurso; }
    public void setTipoRecurso(String tipoRecurso) { this.tipoRecurso = tipoRecurso; }

    public String getReferenciaRecurso() { return referenciaRecurso; }
    public void setReferenciaRecurso(String referenciaRecurso) { this.referenciaRecurso = referenciaRecurso; }

    public long getVersionRecurso() { return versionRecurso; }
    public void setVersionRecurso(long versionRecurso) { this.versionRecurso = versionRecurso; }

    public String getHuellaProyeccion() { return huellaProyeccion; }
    public void setHuellaProyeccion(String huellaProyeccion) { this.huellaProyeccion = huellaProyeccion; }

    public long getIdDemanda() { return idDemanda; }
    public void setIdDemanda(long idDemanda) { this.idDemanda = idDemanda; }

    public long getVersionDemanda() { return versionDemanda; }
    public void setVersionDemanda(long versionDemanda) { this.versionDemanda = versionDemanda; }

    public String getHuellaCriterios() { return huellaCriterios; }
    public void setHuellaCriterios(String huellaCriterios) { this.huellaCriterios = huellaCriterios; }
}


