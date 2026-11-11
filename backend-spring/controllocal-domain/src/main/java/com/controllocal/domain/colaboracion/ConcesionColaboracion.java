package com.controllocal.domain.colaboracion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Dos decisiones de lados distintos y su alcance común; no es permiso de cartera. */
@Entity @Table(name="concesion_colaboracion")
public class ConcesionColaboracion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_concesion_colaboracion") private Long id;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="organizacion_encargo_id",nullable=false) private Long organizacionEncargo;
    @Column(name="id_solicitud_colaboracion",nullable=false,unique=true) private Long solicitud;
    @Column(name="id_decision_demanda") private Long decisionDemanda;
    @Column(name="id_decision_invitado") private Long decisionInvitado;
    @Column(name="id_decision_encargo",nullable=false) private Long decisionEncargo;
    @Column(name="version_alcance",nullable=false) private long versionAlcance;
    @Column(name="alcance",nullable=false,columnDefinition="text") private String alcance;
    @Column(name="concedida_en",nullable=false) private Instant concedidaEn;
    @Column(name="revocada_en") private Instant revocadaEn;
    @Enumerated(EnumType.STRING) @Column(name="lado_revocador",length=16) private LadoColaboracion ladoRevocador;
    @Column(name="organizacion_revocadora_id") private Long organizacionRevocadora;
    @Column(name="id_rol_revocador") private Long revocador;
    @Column(name="motivo_revocacion",columnDefinition="text") private String motivoRevocacion;
    protected ConcesionColaboracion() { }
    public static ConcesionColaboracion nueva(long org,long orgE,long solicitud,long decisionD,long decisionE,
            long versionAlcance,String alcance,Instant ahora) {
        var c=new ConcesionColaboracion();c.setOrganizacionId(org);c.organizacionEncargo=orgE;c.solicitud=solicitud;
        c.decisionDemanda=decisionD;c.decisionEncargo=decisionE;c.versionAlcance=versionAlcance;c.alcance=alcance;
        c.concedidaEn=ahora;c.referencia=UUID.randomUUID();return c;
    }
    public void revocar(LadoColaboracion lado,long org,long actor,String motivo,Instant ahora) {
        if(revocadaEn!=null)throw new IllegalStateException("La concesión ya fue revocada.");
        ladoRevocador=lado;organizacionRevocadora=org;revocador=actor;motivoRevocacion=motivo;revocadaEn=ahora;
    }
    public ConcesionColaboracion paraInvitado(){if(id!=null||decisionInvitado!=null)throw new IllegalStateException("La contraparte original no se reescribe.");decisionInvitado=decisionDemanda;decisionDemanda=null;return this;}
    public Long decisionContraparte(){return decisionInvitado==null?decisionDemanda:decisionInvitado;}
    public Long getId(){return id;} public UUID getReferencia(){return referencia;} public long getVersion(){return version;}
    public Long getSolicitud(){return solicitud;} public String getAlcance(){return alcance;}
    public long getVersionAlcance(){return versionAlcance;} public Long getOrganizacionEncargo(){return organizacionEncargo;}
    public Long getDecisionDemanda(){return decisionDemanda;} public Long getDecisionEncargo(){return decisionEncargo;}
    public Instant getConcedidaEn(){return concedidaEn;} public Instant getRevocadaEn(){return revocadaEn;}
}
