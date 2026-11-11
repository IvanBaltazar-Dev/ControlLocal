package com.controllocal.domain.colaboracion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;

/** Antecedente anexado de una parte sobre una versión exacta del alcance solicitado. */
@Entity @Table(name="decision_colaboracion")
public class DecisionColaboracion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_decision_colaboracion") private Long id;
    @Column(name="organizacion_demanda_id",nullable=false) private Long organizacionDemanda;
    @Column(name="organizacion_encargo_id",nullable=false) private Long organizacionEncargo;
    @Column(name="id_solicitud_colaboracion",nullable=false) private Long solicitud;
    @Enumerated(EnumType.STRING) @Column(name="lado",nullable=false,length=16) private LadoColaboracion lado;
    @Column(name="decision",nullable=false,length=8) private String decision;
    @Column(name="id_rol_decisor",nullable=false) private Long decisor;
    @Column(name="version_alcance",nullable=false) private long versionAlcance;
    @Column(name="version_recurso",nullable=false) private long versionRecurso;
    @Column(name="alcance",columnDefinition="text") private String alcance;
    @Column(name="snapshot_parte",columnDefinition="text") private String snapshotParte;
    @Column(name="motivo",nullable=false,columnDefinition="text") private String motivo;
    @Column(name="registrada_en",nullable=false) private Instant registradaEn;
    protected DecisionColaboracion() { }
    public static DecisionColaboracion nueva(long org,long orgD,long orgE,long solicitud,LadoColaboracion lado,
            String decision,long decisor,long versionAlcance,long versionRecurso,String alcance,String snapshotParte,String motivo,Instant ahora) {
        var d=new DecisionColaboracion();d.setOrganizacionId(org);d.organizacionDemanda=orgD;d.organizacionEncargo=orgE;
        d.solicitud=solicitud;d.lado=lado;d.decision=decision;d.decisor=decisor;d.versionAlcance=versionAlcance;
        d.versionRecurso=versionRecurso;d.alcance=alcance;d.snapshotParte=snapshotParte;d.motivo=motivo;d.registradaEn=ahora;return d;
    }
    public Long getId(){return id;} public Long getSolicitud(){return solicitud;} public LadoColaboracion getLado(){return lado;}
    public String getDecision(){return decision;} public Long getDecisor(){return decisor;}
    public long getVersionAlcance(){return versionAlcance;} public long getVersionRecurso(){return versionRecurso;}
    public String getAlcance(){return alcance;} public String getMotivo(){return motivo;} public Instant getRegistradaEn(){return registradaEn;}
    public String getSnapshotParte(){return snapshotParte;}
}
