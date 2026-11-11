package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** C/P/R: fondos efectivos append-only. A sólo se conserva como antecedente técnico. */
@Entity @Table(name="comision_movimiento")
public class ComisionMovimiento extends EntidadDeOrganizacion {
    public static final String COBRO="C", PAGO_AGENTE="P", AJUSTE="A", REVERSION="R";
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_comision_movimiento") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="id_comision_liquidacion",nullable=false) private ComisionLiquidacion liquidacion;
    @Column(name="id_comision_evaluacion") private Long evaluacion;
    @Column(name="id_comision_tramo") private Long tramo;
    @Column(name="id_revision_pacto") private Long revisionPacto;
    @Column(name="tipo",nullable=false,length=1) private String tipo;
    @Column(name="monto",nullable=false,precision=14,scale=2) private BigDecimal monto;
    @Column(name="moneda",nullable=false,length=3) private String moneda;
    @Column(name="fecha",nullable=false) private LocalDate fecha;
    @Column(name="forma_pago",length=20) private String formaPago;
    @Column(name="id_usuario") private Long idUsuario;
    @Column(name="rol_usuario",length=20) private String rolUsuario;
    @Column(name="id_persona_rol_actor") private Long rolActor;
    @Column(name="observacion",length=300) private String observacion;
    @Column(name="fecha_creacion",nullable=false,updatable=false) private OffsetDateTime fechaCreacion;
    @Column(name="clave_idempotencia",length=64) private String claveIdempotencia;
    @Column(name="huella_comando",length=64) private String huellaComando;
    @Column(name="parte_obligada_ref",columnDefinition="text") private String parteObligadaRef;
    @Column(name="parte_obligada_etiqueta",columnDefinition="text") private String parteObligadaEtiqueta;
    @Column(name="beneficiario_ref",columnDefinition="text") private String beneficiarioRef;
    @Column(name="beneficiario_etiqueta",columnDefinition="text") private String beneficiarioEtiqueta;
    @Column(name="destino",columnDefinition="text") private String destino;
    @Column(name="titular_destino_ref",columnDefinition="text") private String titularDestinoRef;
    @Column(name="titular_destino_etiqueta",columnDefinition="text") private String titularDestinoEtiqueta;
    @Column(name="referencia_externa",columnDefinition="text") private String referenciaExterna;
    @Column(name="evidencias",columnDefinition="text") private String evidencias;
    @Column(name="procedencia",columnDefinition="text") private String procedencia;
    public Long getId(){return id;}
    public ComisionLiquidacion getLiquidacion(){return liquidacion;}
    public void setLiquidacion(ComisionLiquidacion c){liquidacion=c;}
    public Long getEvaluacion(){return evaluacion;}
    public void setEvaluacion(Long id){evaluacion=id;}
    public Long getTramo(){return tramo;}
    public void setTramo(Long id){tramo=id;}
    public Long getRevisionPacto(){return revisionPacto;}
    public void setRevisionPacto(Long id){revisionPacto=id;}
    public String getTipo(){return tipo;}
    public void setTipo(String tipo){this.tipo=tipo;}
    public BigDecimal getMonto(){return monto;}
    public void setMonto(BigDecimal monto){this.monto=monto;}
    public String getMoneda(){return moneda;}
    public void setMoneda(String moneda){this.moneda=moneda;}
    public LocalDate getFecha(){return fecha;}
    public void setFecha(LocalDate fecha){this.fecha=fecha;}
    public String getFormaPago(){return formaPago;}
    public void setFormaPago(String forma){formaPago=forma;}
    public Long getIdUsuario(){return idUsuario;}
    public void setIdUsuario(Long id){idUsuario=id;}
    public String getRolUsuario(){return rolUsuario;}
    public void setRolUsuario(String rol){rolUsuario=rol;}
    public Long getRolActor(){return rolActor;}
    public void setRolActor(Long id){rolActor=id;}
    public String getObservacion(){return observacion;}
    public void setObservacion(String observacion){this.observacion=observacion;}
    public OffsetDateTime getFechaCreacion(){return fechaCreacion;}
    public void setFechaCreacion(OffsetDateTime fecha){fechaCreacion=fecha;}
    public String getClaveIdempotencia(){return claveIdempotencia;}
    public void setClaveIdempotencia(String clave){claveIdempotencia=clave;}
    public String getHuellaComando(){return huellaComando;}
    public void setHuellaComando(String huella){huellaComando=huella;}
    public String getParteObligadaRef(){return parteObligadaRef;}
    public void setParteObligadaRef(String ref){parteObligadaRef=ref;}
    public String getParteObligadaEtiqueta(){return parteObligadaEtiqueta;}
    public void setParteObligadaEtiqueta(String etiqueta){parteObligadaEtiqueta=etiqueta;}
    public String getBeneficiarioRef(){return beneficiarioRef;}
    public void setBeneficiarioRef(String ref){beneficiarioRef=ref;}
    public String getBeneficiarioEtiqueta(){return beneficiarioEtiqueta;}
    public void setBeneficiarioEtiqueta(String etiqueta){beneficiarioEtiqueta=etiqueta;}
    public String getDestino(){return destino;}
    public void setDestino(String destino){this.destino=destino;}
    public String getTitularDestinoRef(){return titularDestinoRef;}
    public void setTitularDestinoRef(String ref){titularDestinoRef=ref;}
    public String getTitularDestinoEtiqueta(){return titularDestinoEtiqueta;}
    public void setTitularDestinoEtiqueta(String etiqueta){titularDestinoEtiqueta=etiqueta;}
    public String getReferenciaExterna(){return referenciaExterna;}
    public void setReferenciaExterna(String ref){referenciaExterna=ref;}
    public String getEvidencias(){return evidencias;}
    public void setEvidencias(String refs){evidencias=refs;}
    public String getProcedencia(){return procedencia;}
    public void setProcedencia(String proc){procedencia=proc;}
}
