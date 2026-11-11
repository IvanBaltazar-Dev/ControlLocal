package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Identidad del episodio contractual; sus presentaciones y revisiones son inmutables. */
@Entity @Table(name="expediente_operacion")
public class ExpedienteOperacion extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_expediente") private Long id;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="id_captacion",nullable=false,updatable=false) private long encargo;
    @Column(name="id_oportunidad",nullable=false,updatable=false) private long oportunidad;
    @Column(name="id_revision_pacto",nullable=false) private long revisionPacto;
    @Column(name="id_acuerdo_c4") private Long acuerdoC4;
    @Column(name="version_acuerdo_c4") private Long versionAcuerdoC4;
    @Column(name="id_solicitud_alquiler",updatable=false) private Long solicitudAlquiler;
    @Column(name="id_contrato_origen",updatable=false) private Long contratoOrigen;
    @Column(name="id_contrato_alquiler") private Long contratoAlquiler;
    @Column(name="id_contrato_venta") private Long contratoVenta;
    @Column(name="id_revision_presentada") private Long revisionPresentada;
    @Column(name="id_revision_aplicable") private Long revisionAplicable;
    @Column(name="id_revision_evaluada") private Long revisionEvaluada;
    @Column(name="id_revision_fuente") private Long revisionFuente;
    @Column(name="secuencia_actividad",nullable=false) private long secuenciaActividad;
    @Column(name="registrado_en",nullable=false,updatable=false) private OffsetDateTime registradoEn;
    protected ExpedienteOperacion() { }
    public static ExpedienteOperacion abrir(long org,long encargo,long oportunidad,long revisionPacto,
            Long solicitudAlquiler,Long contratoOrigen,OffsetDateTime ahora) {
        var e=new ExpedienteOperacion();e.setOrganizacionId(org);e.encargo=encargo;e.oportunidad=oportunidad;
        e.revisionPacto=revisionPacto;e.solicitudAlquiler=solicitudAlquiler;e.contratoOrigen=contratoOrigen;e.registradoEn=ahora;return e;
    }
    public Long getId(){return id;} public long getVersion(){return version;}
    public long getEncargo(){return encargo;} public long getOportunidad(){return oportunidad;}
    public long getRevisionPacto(){return revisionPacto;} public Long getSolicitudAlquiler(){return solicitudAlquiler;}
    public Long getContratoOrigen(){return contratoOrigen;}
    public Long getAcuerdoC4(){return acuerdoC4;} public Long getVersionAcuerdoC4(){return versionAcuerdoC4;}
    public Long getContratoAlquiler(){return contratoAlquiler;} public Long getContratoVenta(){return contratoVenta;}
    public Long getRevisionPresentada(){return revisionPresentada;} public Long getRevisionAplicable(){return revisionAplicable;}
    public Long getRevisionFuente(){return revisionFuente;} public OffsetDateTime getRegistradoEn(){return registradoEn;}
    public Long getRevisionEvaluada(){return revisionEvaluada;}
    public void registrarActividad(){secuenciaActividad=Math.incrementExact(secuenciaActividad);}
    /** Sólo el orquestador, bajo CAS y sin hitos confirmados, selecciona un candidato real. */
    public void seleccionarContratoPreparado(long contrato){contratoAlquiler=contrato;}
    public void presentar(long revision,long pacto,Long acuerdo,Long versionAcuerdo){
        revisionPresentada=revision;revisionPacto=pacto;acuerdoC4=acuerdo;versionAcuerdoC4=versionAcuerdo;
        revisionAplicable=null;revisionFuente=null;
    }
    public void revisar(long revision,boolean aprobada,boolean fuenteNueva){revisionEvaluada=revision;revisionAplicable=aprobada?revision:null;revisionFuente=aprobada&&fuenteNueva?revision:null;}
    public void vincularContratos(Long alquiler,Long venta){
        if(alquiler!=null){if(contratoAlquiler!=null&&!contratoAlquiler.equals(alquiler))throw new IllegalStateException("El episodio ya conserva otro contrato de alquiler.");contratoAlquiler=alquiler;}
        if(venta!=null){if(contratoVenta!=null&&!contratoVenta.equals(venta))throw new IllegalStateException("El episodio ya conserva otro cierre de venta.");contratoVenta=venta;}
    }
}
