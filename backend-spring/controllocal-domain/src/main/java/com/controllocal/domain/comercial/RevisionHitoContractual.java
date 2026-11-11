package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Hecho informado/confirmado o rectificacion; revision es identidad inmutable de fila. */
@Entity @Table(name="revision_hito_contractual")
public class RevisionHitoContractual extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_revision_hito") private Long id;
    @Column(name="id_expediente",nullable=false,updatable=false) private long expediente;
    @Column(name="id_hito",nullable=false,updatable=false) private long hito;
    @Column(name="tipo_registro",nullable=false,length=20,updatable=false) private String tipoRegistro;
    @Column(name="fecha_efectiva",updatable=false) private LocalDate fechaEfectiva;
    @Column(name="id_revision_expediente",updatable=false) private Long revisionExpediente;
    @Column(name="antecedente",updatable=false) private Long antecedente;
    @Column(name="id_rol_actor",nullable=false,updatable=false) private long actor;
    @Column(name="contenido_json",nullable=false,updatable=false) private String contenido;
    @Column(name="registrado_en",nullable=false,updatable=false) private OffsetDateTime registradoEn;
    protected RevisionHitoContractual() { }
    public static RevisionHitoContractual anexar(long org,long expediente,long hito,String tipo,LocalDate fecha,
            Long revisionExpediente,Long antecedente,long actor,String contenido,OffsetDateTime ahora){
        var r=new RevisionHitoContractual();r.setOrganizacionId(org);r.expediente=expediente;r.hito=hito;r.tipoRegistro=tipo;
        r.fechaEfectiva=fecha;r.revisionExpediente=revisionExpediente;r.antecedente=antecedente;r.actor=actor;r.contenido=contenido;r.registradoEn=ahora;return r;
    }
    public Long getId(){return id;} public long getExpediente(){return expediente;} public long getHito(){return hito;}
    public String getTipoRegistro(){return tipoRegistro;} public LocalDate getFechaEfectiva(){return fechaEfectiva;}
    public Long getRevisionExpediente(){return revisionExpediente;} public Long getAntecedente(){return antecedente;}
    public long getActor(){return actor;} public String getContenido(){return contenido;}
    public OffsetDateTime getRegistradoEn(){return registradoEn;}
}
