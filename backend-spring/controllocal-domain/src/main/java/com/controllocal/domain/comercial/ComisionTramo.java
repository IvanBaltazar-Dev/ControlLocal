package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;

/** Realización de una posición expresa del pacto inmutable; depósitos no producen filas. */
@Entity @Table(name="comision_tramo")
public class ComisionTramo extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_comision_tramo") private Long id;
    @Column(name="id_comision_liquidacion",nullable=false) private long comision;
    @Column(name="id_captacion",nullable=false) private long encargo;
    @Column(name="id_revision_pacto",nullable=false) private long revisionPacto;
    @Column(name="indice_pacto",nullable=false) private int indicePacto;
    @Column(name="huella_regla",nullable=false,length=64) private String huellaRegla;
    public static ComisionTramo de(long org,long comision,long encargo,long revisionPacto,int indice,String huella){
        var t=new ComisionTramo();t.setOrganizacionId(org);t.comision=comision;t.encargo=encargo;
        t.revisionPacto=revisionPacto;t.indicePacto=indice;t.huellaRegla=huella;return t;
    }
    public Long getId(){return id;}
    public long getComision(){return comision;}
    public long getEncargo(){return encargo;}
    public long getRevisionPacto(){return revisionPacto;}
    public int getIndicePacto(){return indicePacto;}
    public String getHuellaRegla(){return huellaRegla;}
}
