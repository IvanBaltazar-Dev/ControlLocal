package com.controllocal.domain.colaboracion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** CAT-E19/figura47b: la solicitud guarda las dos partes; no concede acceso por existir. */
@Entity @Table(name="solicitud_colaboracion")
public class SolicitudColaboracion extends EntidadDeOrganizacion implements Transicionable {
    public static final String TIPO="SOLICITUD_COLABORACION";
    public static final String PENDIENTE="P",APROBADA="A",RECHAZADA="R";
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_solicitud_colaboracion") private Long id;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="organizacion_encargo_id",nullable=false) private Long organizacionEncargo;
    @Column(name="id_requerimiento") private Long idDemanda;
    @Column(name="id_captacion",nullable=false) private Long idEncargo;
    @Column(name="id_responsable_demanda") private Long responsableDemanda;
    @Column(name="id_responsable_encargo",nullable=false) private Long responsableEncargo;
    @Column(name="id_rol_solicitante",nullable=false) private Long solicitante;
    @Column(name="organizacion_solicitante_id",nullable=false) private Long organizacionSolicitante;
    @Column(name="version_demanda") private Long versionDemanda;
    @Column(name="version_encargo",nullable=false) private long versionEncargo;
    @Column(name="version_oferta") private Long versionOferta;
    @Column(name="oferta_ref",columnDefinition="text") private String ofertaRef;
    @Column(name="id_responsable_invitado") private Long responsableInvitado;
    @Column(name="version_invitado") private Long versionInvitado;
    @Column(name="id_revision_permiso") private Long revisionPermiso;
    @Column(name="clausula_permiso") private String clausulaPermiso;
    @Column(name="id_decision_invitado") private Long decisionInvitado;
    @Column(name="id_canal_invitado") private Long canalInvitado;
    @Column(name="alcance",nullable=false,columnDefinition="text") private String alcance;
    @Column(name="huella_alcance",nullable=false,length=64) private String huellaAlcance;
    @Column(name="version_alcance",nullable=false) private long versionAlcance;
    @Column(name="estado",nullable=false,length=1) private String estado;
    @Column(name="creada_en",nullable=false) private Instant creadaEn;
    @Column(name="id_decision_demanda") private Long decisionDemanda;
    @Column(name="id_decision_encargo") private Long decisionEncargo;
    @Column(name="id_canal_demanda") private Long canalDemanda;
    @Column(name="id_canal_encargo") private Long canalEncargo;
    protected SolicitudColaboracion() { }
    public static SolicitudColaboracion nueva(long orgD,long orgE,long demanda,long encargo,
            long responsableD,long responsableE,long solicitante,long versionD,long versionE,
            long versionOferta,String oferta,String alcance,String huella,Instant ahora) {
        var s=new SolicitudColaboracion();s.setOrganizacionId(orgD);s.organizacionEncargo=orgE;
        s.idDemanda=demanda;s.idEncargo=encargo;s.responsableDemanda=responsableD;
        s.responsableEncargo=responsableE;s.solicitante=solicitante;s.versionDemanda=versionD;
        s.versionEncargo=versionE;s.versionOferta=versionOferta;s.ofertaRef=oferta;s.alcance=alcance;s.huellaAlcance=huella;
        s.organizacionSolicitante=orgD;s.creadaEn=ahora;s.referencia=UUID.randomUUID();return s;
    }
    public void registrarDecision(LadoColaboracion parte,long idDecision) {
        exigirLado(parte);if(parte==LadoColaboracion.DEMANDA)decisionDemanda=idDecision;else if(parte==LadoColaboracion.INVITADO)decisionInvitado=idDecision;else decisionEncargo=idDecision;
    }
    public void registrarCanal(LadoColaboracion lado,long idCanal){exigirLado(lado);if(lado==LadoColaboracion.DEMANDA)canalDemanda=idCanal;else if(lado==LadoColaboracion.INVITADO)canalInvitado=idCanal;else canalEncargo=idCanal;}
    public Long canalDe(LadoColaboracion lado){exigirLado(lado);return lado==LadoColaboracion.DEMANDA?canalDemanda:lado==LadoColaboracion.INVITADO?canalInvitado:canalEncargo;}
    public Long decisionDe(LadoColaboracion lado){exigirLado(lado);return lado==LadoColaboracion.DEMANDA?decisionDemanda:lado==LadoColaboracion.INVITADO?decisionInvitado:decisionEncargo;}
    public boolean esInvitacion(){return idDemanda==null;}
    public LadoColaboracion ladoContraparte(){return esInvitacion()?LadoColaboracion.INVITADO:LadoColaboracion.DEMANDA;}
    public Long responsableContraparte(){return esInvitacion()?responsableInvitado:responsableDemanda;}
    public Long versionContraparte(){return esInvitacion()?versionInvitado:versionDemanda;}
    public Long decisionContraparte(){return esInvitacion()?decisionInvitado:decisionDemanda;}
    public void exigirLado(LadoColaboracion lado){if(lado!=LadoColaboracion.ENCARGO&&lado!=ladoContraparte())throw new IllegalArgumentException("El lado no pertenece a esta colaboración.");}
    public static SolicitudColaboracion invitacion(long orgInv,long orgE,long encargo,long responsableInv,long responsableE,long solicitante,
            long versionInv,long versionE,long revisionPermiso,String clausula,String alcance,String huella,Instant ahora){
        var s=new SolicitudColaboracion();s.setOrganizacionId(orgInv);s.organizacionEncargo=orgE;s.idEncargo=encargo;
        s.responsableInvitado=responsableInv;s.versionInvitado=versionInv;s.responsableEncargo=responsableE;s.solicitante=solicitante;
        s.versionEncargo=versionE;s.revisionPermiso=revisionPermiso;s.clausulaPermiso=clausula;s.alcance=alcance;s.huellaAlcance=huella;
        s.organizacionSolicitante=orgE;s.creadaEn=ahora;s.referencia=UUID.randomUUID();return s;
    }
    public Long getRevisionPermiso(){return revisionPermiso;}public String getClausulaPermiso(){return clausulaPermiso;}
    @Override public String entidadTipo(){return TIPO;}
    @Override public String estadoActual(){return estado;}
    @Override public void transicionarA(String nuevo){
        if(!java.util.Set.of(PENDIENTE,APROBADA,RECHAZADA).contains(nuevo))throw new IllegalArgumentException("Estado de colaboración desconocido.");
        estado=nuevo;
    }
    public String estadoNombre(){return switch(estado){case PENDIENTE->"PENDIENTE";case APROBADA->"APROBADA";case RECHAZADA->"RECHAZADA";default->throw new IllegalStateException("Solicitud sin estado.");};}
    public Long getId(){return id;} public UUID getReferencia(){return referencia;} public long getVersion(){return version;}
    public Long getOrganizacionEncargo(){return organizacionEncargo;} public Long getIdDemanda(){return idDemanda;}
    public Long getIdEncargo(){return idEncargo;} public Long getResponsableDemanda(){return responsableDemanda;}
    public Long getResponsableEncargo(){return responsableEncargo;} public Long getSolicitante(){return solicitante;}
    public Long getVersionDemanda(){return versionDemanda;} public long getVersionEncargo(){return versionEncargo;}
    public Long getVersionOferta(){return versionOferta;}
    public String getOfertaRef(){return ofertaRef;} public String getAlcance(){return alcance;}
    public String getHuellaAlcance(){return huellaAlcance;} public long getVersionAlcance(){return versionAlcance;}
    public Instant getCreadaEn(){return creadaEn;} public Long getDecisionDemanda(){return decisionDemanda;}
    public Long getDecisionEncargo(){return decisionEncargo;}
}
