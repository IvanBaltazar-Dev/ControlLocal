package com.controllocal.domain.colaboracion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Declaración inmutable del responsable para una solicitud/lado/alcance concretos. */
@Entity @Table(name="canal_colaboracion")
public class CanalColaboracion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_canal_colaboracion") private Long id;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia=UUID.randomUUID();
    @Column(name="organizacion_demanda_id",nullable=false) private Long organizacionDemanda;
    @Column(name="organizacion_encargo_id",nullable=false) private Long organizacionEncargo;
    @Column(name="id_solicitud_colaboracion",nullable=false) private Long solicitud;
    @Column(name="version_alcance",nullable=false) private long versionAlcance;
    @Enumerated(EnumType.STRING) @Column(name="lado",nullable=false,length=16) private LadoColaboracion lado;
    @Column(name="version_recurso",nullable=false) private long versionRecurso;
    @Column(name="id_rol_declarante",nullable=false) private Long declarante;
    @Column(name="canal_comercial",nullable=false,columnDefinition="text") private String canalComercial;
    @Column(name="declarada_en",nullable=false) private Instant declaradaEn;
    protected CanalColaboracion() { }
    public static CanalColaboracion declarar(SolicitudColaboracion s,LadoColaboracion lado,long organizacion,
            long responsable,long versionRecurso,String canal,Instant ahora) {
        var c=new CanalColaboracion();c.setOrganizacionId(organizacion);c.organizacionDemanda=s.getOrganizacionId();
        c.organizacionEncargo=s.getOrganizacionEncargo();c.solicitud=s.getId();c.versionAlcance=s.getVersionAlcance();
        c.lado=lado;c.versionRecurso=versionRecurso;c.declarante=responsable;c.canalComercial=canal;c.declaradaEn=ahora;return c;
    }
    public Long getId(){return id;} public UUID getReferencia(){return referencia;}
    public Long getOrganizacionDemanda(){return organizacionDemanda;} public Long getOrganizacionEncargo(){return organizacionEncargo;}
    public Long getSolicitud(){return solicitud;} public long getVersionAlcance(){return versionAlcance;}
    public LadoColaboracion getLado(){return lado;} public long getVersionRecurso(){return versionRecurso;}
    public Long getDeclarante(){return declarante;} public String getCanalComercial(){return canalComercial;}
    public Instant getDeclaradaEn(){return declaradaEn;}
}
