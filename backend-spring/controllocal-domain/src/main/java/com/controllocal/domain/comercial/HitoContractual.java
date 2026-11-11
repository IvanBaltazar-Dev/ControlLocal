package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;

/** Un hito por tipo dentro del mismo episodio; la rectificacion conserva su identidad. */
@Entity @Table(name="hito_contractual")
public class HitoContractual extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_hito") private Long id;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="id_expediente",nullable=false,updatable=false) private long expediente;
    @Enumerated(EnumType.STRING) @Column(name="tipo",nullable=false,length=30,updatable=false) private TipoHitoContractual tipo;
    @Column(name="id_revision_informada") private Long revisionInformada;
    @Column(name="id_revision_confirmada") private Long revisionConfirmada;
    protected HitoContractual() { }
    public static HitoContractual abrir(long org,long expediente,TipoHitoContractual tipo){var h=new HitoContractual();h.setOrganizacionId(org);h.expediente=expediente;h.tipo=tipo;return h;}
    public Long getId(){return id;} public long getVersion(){return version;} public long getExpediente(){return expediente;}
    public TipoHitoContractual getTipo(){return tipo;} public Long getRevisionInformada(){return revisionInformada;}
    public Long getRevisionConfirmada(){return revisionConfirmada;}
    public void informar(long revision){revisionInformada=revision;} public void confirmar(long revision){revisionConfirmada=revision;}
}
