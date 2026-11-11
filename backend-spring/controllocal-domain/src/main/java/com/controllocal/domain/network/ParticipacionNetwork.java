package com.controllocal.domain.network;

import com.controllocal.domain.comun.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Estado de participación del Encargo, independiente de su estado comercial. */
@Entity @Table(name="participacion_network")
public class ParticipacionNetwork extends EntidadDeOrganizacion implements Transicionable {
    public static final String TIPO="PARTICIPACION_NETWORK";
    public static final String NO_HABILITADO="N",ACTIVO="A",SUSPENDIDO="S",INACTIVO="I";
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_participacion_network") private Long id;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="referencia",nullable=false,unique=true) private UUID referencia=UUID.randomUUID();
    @Column(name="id_captacion",nullable=false) private Long encargo;
    @Column(name="estado",nullable=false,length=1) private String estado;
    @Column(name="id_version_actual") private Long versionActual;
    @Column(name="numero_version",nullable=false) private long numeroVersion;
    @Column(name="id_solicitud_pendiente") private Long solicitudPendiente;
    @Column(name="causas",nullable=false,columnDefinition="text") private String causas="[]";
    @Column(name="suspendida_en") private Instant suspendidaEn;
    @Column(name="creada_en",nullable=false) private Instant creadaEn;
    protected ParticipacionNetwork() { }
    public static ParticipacionNetwork nueva(long org,long encargo,Instant ahora){var p=new ParticipacionNetwork();p.setOrganizacionId(org);p.encargo=encargo;p.creadaEn=ahora;return p;}
    public void solicitar(long idSolicitud){solicitudPendiente=idSolicitud;}
    public void resolver(long idSolicitud){if(solicitudPendiente!=null && solicitudPendiente==idSolicitud)solicitudPendiente=null;}
    public void aprobarVersion(long idVersion,long numero){versionActual=idVersion;numeroVersion=numero;causas="[]";suspendidaEn=null;}
    public void registrarSuspension(String causas,Instant ahora){this.causas=causas;this.suspendidaEn=ahora;}
    public void limpiarSuspension(){causas="[]";suspendidaEn=null;}
    @Override public String entidadTipo(){return TIPO;} @Override public String estadoActual(){return estado;}
    @Override public void transicionarA(String nuevo){if(!java.util.Set.of(NO_HABILITADO,ACTIVO,SUSPENDIDO,INACTIVO).contains(nuevo))throw new IllegalArgumentException("Estado Network desconocido.");estado=nuevo;}
    public Long getId(){return id;}public long getVersion(){return version;}public Long getEncargo(){return encargo;}
    public UUID getReferencia(){return referencia;}
    public Long getVersionActual(){return versionActual;}public long getNumeroVersion(){return numeroVersion;}
    public Long getSolicitudPendiente(){return solicitudPendiente;}public String getCausas(){return causas;}
    public Instant getSuspendidaEn(){return suspendidaEn;}public Instant getCreadaEn(){return creadaEn;}
}
