package com.controllocal.domain.network;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="decision_participacion_network")
public class DecisionParticipacionNetwork extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_decision_network") private Long id;
    @Column(name="id_solicitud_network",nullable=false) private Long solicitud;
    @Column(name="id_participacion_network",nullable=false) private Long participacion;
    @Column(name="id_rol_decisor",nullable=false) private Long decisor;
    @Column(name="decision",nullable=false,length=8) private String decision;
    @Column(name="version_solicitud_revisada",nullable=false) private long versionSolicitud;
    @Column(name="huella_datos",nullable=false,length=64) private String huellaDatos;
    @Column(name="huella_autoridad",nullable=false,length=64) private String huellaAutoridad;
    @Column(name="motivo",nullable=false,columnDefinition="text") private String motivo;
    @Column(name="registrada_en",nullable=false) private Instant registradaEn;
    protected DecisionParticipacionNetwork() { }
    public static DecisionParticipacionNetwork nueva(SolicitudParticipacionNetwork s,long decisor,String decision,String motivo,Instant ahora) {
        var d=new DecisionParticipacionNetwork();d.setOrganizacionId(s.getOrganizacionId());d.solicitud=s.getId();
        d.participacion=s.getParticipacion();d.decisor=decisor;d.decision=decision;d.versionSolicitud=s.getVersion();
        d.huellaDatos=s.contenido().huellaDatos();d.huellaAutoridad=s.contenido().huellaAutoridad();d.motivo=motivo;d.registradaEn=ahora;return d;
    }
    public Long getId(){return id;}public Long getSolicitud(){return solicitud;}public Long getParticipacion(){return participacion;}
    public Long getDecisor(){return decisor;}public String getDecision(){return decision;}public String getMotivo(){return motivo;}
    public Instant getRegistradaEn(){return registradaEn;}
}
