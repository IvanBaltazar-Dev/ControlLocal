package com.controllocal.domain.network;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Publicación V1 privada de Core: original inmutable aprobado, no modelo interno serializado. */
@Entity @Table(name="version_participacion_network")
public class VersionParticipacionNetwork extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_version_network") private Long id;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia;
    @Column(name="id_participacion_network",nullable=false) private Long participacion;
    @Column(name="numero",nullable=false) private long numero;
    @Column(name="id_solicitud_network",nullable=false) private Long solicitud;
    @Column(name="id_decision_network",nullable=false) private Long decision;
    @Column(name="id_responsable",nullable=false) private Long responsable;
    @Column(name="id_broker") private Long broker;
    @Column(name="individual",nullable=false) private boolean individual;
    @Column(name="version_encargo",nullable=false) private long versionEncargo;
    @Column(name="version_fisica",nullable=false) private long versionFisica;
    @Column(name="politica_version",nullable=false,length=80) private String politicaVersion;
    @Column(name="datos",nullable=false,columnDefinition="text") private String datos;
    @Column(name="campos",nullable=false,columnDefinition="text") private String campos;
    @Column(name="finalidad",nullable=false,length=80) private String finalidad;
    @Column(name="desde",nullable=false) private Instant desde;
    @Column(name="hasta",nullable=false) private Instant hasta;
    @Column(name="huella_datos",nullable=false,length=64) private String huellaDatos;
    @Column(name="huella_autoridad",nullable=false,length=64) private String huellaAutoridad;
    @Column(name="creada_en",nullable=false) private Instant creadaEn;
    protected VersionParticipacionNetwork() { }
    public static VersionParticipacionNetwork aprobada(ParticipacionNetwork p,SolicitudParticipacionNetwork s,long decision,Instant ahora) {
        var v=new VersionParticipacionNetwork();var c=s.contenido();v.setOrganizacionId(p.getOrganizacionId());v.participacion=p.getId();
        v.numero=p.getNumeroVersion()+1;v.solicitud=s.getId();v.decision=decision;v.responsable=c.responsable();v.broker=c.broker();
        v.individual=c.individual();v.versionEncargo=c.versionEncargo();v.versionFisica=c.versionFisica();v.politicaVersion=c.politicaVersion();
        v.datos=c.datos();v.campos=c.campos();v.finalidad=c.finalidad();v.desde=c.desde();v.hasta=c.hasta();
        v.huellaDatos=c.huellaDatos();v.huellaAutoridad=c.huellaAutoridad();v.referencia=UUID.randomUUID();v.creadaEn=ahora;return v;
    }
    public ContenidoNetwork contenido(){return new ContenidoNetwork(responsable,broker,individual,versionEncargo,versionFisica,politicaVersion,datos,campos,finalidad,desde,hasta,huellaDatos,huellaAutoridad);}
    public Long getId(){return id;}public UUID getReferencia(){return referencia;}public Long getParticipacion(){return participacion;}
    public long getNumero(){return numero;}public Long getSolicitud(){return solicitud;}public Long getDecision(){return decision;}
    public Instant getCreadaEn(){return creadaEn;}
}
