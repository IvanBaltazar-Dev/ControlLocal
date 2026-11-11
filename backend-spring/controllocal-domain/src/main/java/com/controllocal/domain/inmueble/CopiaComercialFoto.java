package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Copia de salida sin metadatos; no es un retoque IA ni sustituye el original. */
@Entity @Table(name="copia_comercial_foto")
public class CopiaComercialFoto extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_copia") private Long id;
    @Column(name="id_propiedad",nullable=false) private Long propiedad;
    @Column(name="id_foto",nullable=false) private Long foto;
    @Column(name="original_ref",nullable=false,updatable=false) private UUID original;
    @Column(name="archivo_ref",nullable=false,updatable=false) private UUID referencia;
    @Column(name="huella_original",nullable=false,length=64,updatable=false) private String huellaOriginal;
    @Column(name="huella_copia",nullable=false,length=64,updatable=false) private String huella;
    @Column(name="clave_proveedor",nullable=false,updatable=false) private String clave;
    @Column(name="proceso_version",nullable=false,updatable=false) private String proceso;
    @Column(name="ancho",nullable=false,updatable=false) private int ancho;
    @Column(name="alto",nullable=false,updatable=false) private int alto;
    @Column(name="evaluacion",nullable=false,columnDefinition="text",updatable=false) private String evaluacion;
    @Column(name="registrada_en",nullable=false,updatable=false) private Instant registradaEn;
    @Column(name="original_registrado_en",nullable=false,updatable=false) private Instant originalRegistradoEn;
    @Column(name="id_rol_operador",nullable=false,updatable=false) private Long operador;
    protected CopiaComercialFoto() { }
    public static CopiaComercialFoto de(long org,long propiedad,long foto,UUID original,String hashOriginal,
            String hash,String clave,int ancho,int alto,String evaluacion,long operador,Instant originalRegistradoEn,Instant ahora) {
        var c=new CopiaComercialFoto();c.setOrganizacionId(org);c.propiedad=propiedad;c.foto=foto;
        c.original=original;c.referencia=UUID.randomUUID();c.huellaOriginal=hashOriginal;c.huella=hash;
        c.clave=clave;c.proceso="FOTO-LOCAL-1";c.ancho=ancho;c.alto=alto;c.evaluacion=evaluacion;
        c.operador=operador;c.originalRegistradoEn=java.util.Objects.requireNonNull(originalRegistradoEn);c.registradaEn=ahora;return c;
    }
    public Long getId(){return id;} public Long getPropiedad(){return propiedad;} public Long getFoto(){return foto;}
    public UUID getOriginal(){return original;} public UUID getReferencia(){return referencia;}
    public String getHuellaOriginal(){return huellaOriginal;} public String getHuella(){return huella;}
    public String getClave(){return clave;} public String getProceso(){return proceso;}
    public int getAncho(){return ancho;} public int getAlto(){return alto;}
    public String getEvaluacion(){return evaluacion;} public Instant getRegistradaEn(){return registradaEn;}
    public Instant getOriginalRegistradoEn(){return originalRegistradoEn;}
}
