package com.controllocal.domain.network;

import com.controllocal.domain.comun.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Propuesta exacta e histórica de activación/modificación/baja/reactivación de un Encargo. */
@Entity @Table(name="solicitud_participacion_network")
public class SolicitudParticipacionNetwork extends EntidadDeOrganizacion implements Transicionable {
    public static final String TIPO="SOLICITUD_NETWORK";
    public static final String PENDIENTE="P",OBSERVADA="O",APROBADA="A",RECHAZADA="R";
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_solicitud_network") private Long id;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="id_participacion_network",nullable=false) private Long participacion;
    @Column(name="id_rol_solicitante",nullable=false) private Long solicitante;
    @Column(name="accion",nullable=false,length=10) private String accion;
    @Column(name="estado",nullable=false,length=1) private String estado;
    @Column(name="version_participacion_presentada",nullable=false) private long versionParticipacion;
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
    @Column(name="diferencias",nullable=false,columnDefinition="text") private String diferencias;
    @Column(name="motivo",nullable=false,columnDefinition="text") private String motivo;
    @Column(name="creada_en",nullable=false) private Instant creadaEn;
    @Column(name="id_ultima_decision") private Long ultimaDecision;
    protected SolicitudParticipacionNetwork() { }
    public static SolicitudParticipacionNetwork nueva(ParticipacionNetwork p,long solicitante,String accion,
            ContenidoNetwork c,String diferencias,String motivo,Instant ahora) {
        var s=new SolicitudParticipacionNetwork();s.setOrganizacionId(p.getOrganizacionId());s.participacion=p.getId();
        s.solicitante=solicitante;s.accion=accion;s.versionParticipacion=p.getVersion();s.responsable=c.responsable();
        s.broker=c.broker();s.individual=c.individual();s.versionEncargo=c.versionEncargo();s.versionFisica=c.versionFisica();
        s.politicaVersion=c.politicaVersion();s.datos=c.datos();s.campos=c.campos();s.finalidad=c.finalidad();
        s.desde=c.desde();s.hasta=c.hasta();s.huellaDatos=c.huellaDatos();s.huellaAutoridad=c.huellaAutoridad();
        s.diferencias=diferencias;s.motivo=motivo;s.creadaEn=ahora;s.referencia=UUID.randomUUID();return s;
    }
    public ContenidoNetwork contenido(){return new ContenidoNetwork(responsable,broker,individual,versionEncargo,versionFisica,politicaVersion,datos,campos,finalidad,desde,hasta,huellaDatos,huellaAutoridad);}
    public void decidir(long idDecision){ultimaDecision=idDecision;}
    @Override public String entidadTipo(){return TIPO;}@Override public String estadoActual(){return estado;}
    @Override public void transicionarA(String nuevo){if(!java.util.Set.of(PENDIENTE,OBSERVADA,APROBADA,RECHAZADA).contains(nuevo))throw new IllegalArgumentException("Estado de solicitud Network desconocido.");estado=nuevo;}
    public String estadoNombre(){return switch(estado){case PENDIENTE->"PENDIENTE";case OBSERVADA->"OBSERVADA";case APROBADA->"APROBADA";case RECHAZADA->"RECHAZADA";default->throw new IllegalStateException("Solicitud sin estado.");};}
    public Long getId(){return id;}public UUID getReferencia(){return referencia;}public long getVersion(){return version;}
    public Long getParticipacion(){return participacion;}public Long getSolicitante(){return solicitante;}public String getAccion(){return accion;}
    public long getVersionParticipacion(){return versionParticipacion;}public String getDiferencias(){return diferencias;}
    public String getMotivo(){return motivo;}public Instant getCreadaEn(){return creadaEn;}public Long getUltimaDecision(){return ultimaDecision;}
}
