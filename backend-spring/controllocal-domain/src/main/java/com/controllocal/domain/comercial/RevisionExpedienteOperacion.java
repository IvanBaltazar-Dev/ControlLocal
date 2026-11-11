package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Presentacion o revision competente exacta; no se actualiza ni elimina para corregirla. */
@Entity @Table(name="revision_expediente_operacion")
public class RevisionExpedienteOperacion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_revision_expediente") private Long id;
    @Column(name="id_expediente",nullable=false,updatable=false) private long expediente;
    @Column(name="version_revisada",nullable=false,updatable=false) private long versionRevisada;
    @Column(name="tipo_registro",nullable=false,length=20,updatable=false) private String tipoRegistro;
    @Column(name="decision",length=20,updatable=false) private String decision;
    @Column(name="id_presentacion",updatable=false) private Long presentacion;
    @Column(name="antecedente",updatable=false) private Long antecedente;
    @Column(name="id_rol_actor",nullable=false,updatable=false) private long actor;
    @Column(name="contenido_json",nullable=false,updatable=false) private String contenido;
    @Column(name="registrado_en",nullable=false,updatable=false) private OffsetDateTime registradoEn;
    protected RevisionExpedienteOperacion() { }
    public static RevisionExpedienteOperacion anexar(long org,long expediente,long version,String tipo,String decision,
            Long presentacion,Long antecedente,long actor,String contenido,OffsetDateTime ahora){
        var r=new RevisionExpedienteOperacion();r.setOrganizacionId(org);r.expediente=expediente;r.versionRevisada=version;
        r.tipoRegistro=tipo;r.decision=decision;r.presentacion=presentacion;r.antecedente=antecedente;r.actor=actor;
        r.contenido=contenido;r.registradoEn=ahora;return r;
    }
    public Long getId(){return id;} public long getExpediente(){return expediente;}
    public long getVersionRevisada(){return versionRevisada;} public String getTipoRegistro(){return tipoRegistro;}
    public String getDecision(){return decision;} public Long getPresentacion(){return presentacion;}
    public Long getAntecedente(){return antecedente;} public long getActor(){return actor;}
    public String getContenido(){return contenido;} public OffsetDateTime getRegistradoEn(){return registradoEn;}
}
