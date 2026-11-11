package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoComision;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

/** Raíz por resultado económico. El estado de cobro no representa devengo. */
@Entity
@Table(name="comision_liquidacion")
public class ComisionLiquidacion extends EntidadDeOrganizacion {
    public static final String ENTIDAD_TIPO="COMISION_LIQUIDACION";
    public static final String PENDIENTE=Codigos.Comision.PENDIENTE;
    public static final String PARCIAL=Codigos.Comision.PARCIAL;
    public static final String COBRADA=Codigos.Comision.COBRADA;
    public static final String ANULADA=Codigos.Comision.ANULADA;
    public static final Set<String> ESTADOS=Set.of(PENDIENTE,PARCIAL,COBRADA,ANULADA);
    public static final Set<String> FORMAS_PAGO=Set.of("TRANSFERENCIA","DEPOSITO_BANCARIO","EFECTIVO","CHEQUE","OTRO");
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_comision_liquidacion") private Long id;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="id_contexto_economico") private Long contextoEconomico;
    @Column(name="id_captacion") private Long encargo;
    @Column(name="id_revision_pacto") private Long revisionPacto;
    @Column(name="version_contenido_pacto") private Long versionContenidoPacto;
    @Column(name="id_ultima_evaluacion") private Long ultimaEvaluacion;
    @Column(name="id_ultimo_movimiento") private Long ultimoMovimiento;
    @Column(name="id_evaluacion_base_reparto") private Long evaluacionBaseReparto;
    @Column(name="gestion_concluida",nullable=false) private boolean gestionConcluida;
    @Column(name="proxima_revision_gestion") private java.time.LocalDate proximaRevisionGestion;
    @Column(name="motivo_gestion",columnDefinition="text") private String motivoGestion;
    @Column(name="gestion_registrada_en") private java.time.OffsetDateTime gestionRegistradaEn;
    @Column(name="clave_ultima_gestion",length=64) private String claveUltimaGestion;
    @OneToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="id_contrato_alquiler") private ContratoAlquiler contrato;
    @Column(name="id_contrato_venta") private Long contratoVenta;
    @Column(name="monto_bruto",precision=14,scale=2) private BigDecimal montoBruto;
    @Column(name="moneda",length=3) private String moneda;
    @Column(name="parte_agente",precision=14,scale=2) private BigDecimal parteAgente;
    @Column(name="parte_empresa",precision=14,scale=2) private BigDecimal parteEmpresa;
    /** Sólo conserva el código histórico. El estado de cobro nuevo se deriva de hechos. */
    @Column(name="estado",length=1) private String estado;
    public String getEstadoLegado(){return estado;}
    @Transient public EstadoComision estadoTipado(){return estado==null?null:EstadoComision.desde(estado);}
    public void asignarMontoAgente(BigDecimal monto){
        if(ultimaEvaluacion==null||montoBruto==null||monto==null||monto.signum()<0||monto.compareTo(montoBruto)>0)
            throw new IllegalArgumentException("Reparto sin bruto conocido o fuera de su importe.");
        parteAgente=monto;parteEmpresa=montoBruto.subtract(monto);evaluacionBaseReparto=ultimaEvaluacion;
    }
    public void registrarGestion(boolean concluida,java.time.LocalDate proxima,String motivo,
            java.time.OffsetDateTime registrado,String clave) {
        gestionConcluida=concluida;proximaRevisionGestion=proxima;motivoGestion=motivo;
        gestionRegistradaEn=registrado;claveUltimaGestion=clave;
    }
    public Long getEvaluacionBaseReparto(){return evaluacionBaseReparto;}
    public boolean isGestionConcluida(){return gestionConcluida;}
    public java.time.LocalDate getProximaRevisionGestion(){return proximaRevisionGestion;}
    public String getMotivoGestion(){return motivoGestion;}
    public java.time.OffsetDateTime getGestionRegistradaEn(){return gestionRegistradaEn;}
    public String getClaveUltimaGestion(){return claveUltimaGestion;}
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public long getVersion(){return version;}
    public Long getContextoEconomico(){return contextoEconomico;}
    public void setContextoEconomico(Long id){contextoEconomico=id;}
    public Long getEncargo(){return encargo;}
    public void setEncargo(Long id){encargo=id;}
    public Long getRevisionPacto(){return revisionPacto;}
    public void setRevisionPacto(Long id){revisionPacto=id;}
    public Long getVersionContenidoPacto(){return versionContenidoPacto;}
    public void setVersionContenidoPacto(Long v){versionContenidoPacto=v;}
    public Long getUltimaEvaluacion(){return ultimaEvaluacion;}
    public void setUltimaEvaluacion(Long id){ultimaEvaluacion=id;}
    public Long getUltimoMovimiento(){return ultimoMovimiento;}
    public void setUltimoMovimiento(Long id){ultimoMovimiento=id;}
    public ContratoAlquiler getContrato(){return contrato;}
    public void setContrato(ContratoAlquiler contrato){this.contrato=contrato;}
    public Long getContratoVenta(){return contratoVenta;}
    public void setContratoVenta(Long id){contratoVenta=id;}
    public BigDecimal getMonto(){return montoBruto;}
    public void setMonto(BigDecimal monto){montoBruto=monto;}
    public String getMoneda(){return moneda;}
    public void setMoneda(String moneda){this.moneda=moneda;}
    public BigDecimal getMontoAgente(){return parteAgente;}
    public BigDecimal getMontoEmpresa(){return parteEmpresa;}
}
