package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Resultado técnico append-only; no es otro pacto ni otro libro de movimientos. */
@Entity @Table(name="comision_evaluacion")
public class ComisionEvaluacion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_comision_evaluacion") private Long id;
    @Column(name="id_comision_liquidacion",nullable=false) private long comision;
    @Column(name="id_contexto_economico",nullable=false) private long contexto;
    @Column(name="id_captacion",nullable=false) private long encargo;
    @Column(name="id_revision_pacto",nullable=false) private long revisionPacto;
    @Column(name="version_contenido_pacto",nullable=false) private long versionContenido;
    @Column(name="tipo_fuente") private String tipoFuente;
    @Column(name="id_fuente") private Long fuente;
    @Column(name="revision_fuente") private Long revisionFuente;
    @Column(name="id_hito_causal") private Long hito;
    @Column(name="id_revision_hito_causal") private Long revisionHito;
    @Column(name="huella_entrada",nullable=false,length=64) private String huella;
    @Column(name="entrada",nullable=false,columnDefinition="text") private String entrada;
    @Column(name="resultado",nullable=false,columnDefinition="text") private String resultado;
    @Column(name="registrado_en",nullable=false) private OffsetDateTime registradoEn;
    @Column(name="id_persona_actor",nullable=false) private long persona;
    @Column(name="id_persona_rol_actor",nullable=false) private long rol;
    @Column(name="procedencia",nullable=false,columnDefinition="text") private String procedencia;
    public static ComisionEvaluacion de(long org,long comision,long contexto,long encargo,long revisionPacto,
            long versionContenido,String tipoFuente,Long fuente,Long revisionFuente,Long hito,Long revisionHito,
            String huella,String entrada,String resultado,OffsetDateTime registradoEn,long persona,long rol,String proc){
        var e=new ComisionEvaluacion();e.setOrganizacionId(org);e.comision=comision;e.contexto=contexto;
        e.encargo=encargo;e.revisionPacto=revisionPacto;e.versionContenido=versionContenido;
        e.tipoFuente=tipoFuente;e.fuente=fuente;e.revisionFuente=revisionFuente;e.hito=hito;e.revisionHito=revisionHito;
        e.huella=huella;e.entrada=entrada;e.resultado=resultado;e.registradoEn=registradoEn;e.persona=persona;e.rol=rol;e.procedencia=proc;return e;
    }
    public Long getId(){return id;}
    public long getComision(){return comision;}
    public long getContexto(){return contexto;}
    public long getEncargo(){return encargo;}
    public long getRevisionPacto(){return revisionPacto;}
    public long getVersionContenido(){return versionContenido;}
    public String getTipoFuente(){return tipoFuente;}
    public Long getFuente(){return fuente;}
    public Long getRevisionFuente(){return revisionFuente;}
    public Long getHito(){return hito;}
    public Long getRevisionHito(){return revisionHito;}
    public String getHuella(){return huella;}
    public String getEntrada(){return entrada;}
    public String getResultado(){return resultado;}
    public OffsetDateTime getRegistradoEn(){return registradoEn;}
}
