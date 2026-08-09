package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoComision;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Liquidacion de la comision del contrato. Nace PENDIENTE con la comision
 * BRUTA (porcentaje pactado en la captacion x un mes de renta = monto
 * propuesto aprobado). Sin reparto automatico: el broker supervisor define
 * despues el monto del agente y el sistema calcula el de la empresa.
 *
 * <p>El estado persiste como codigo de un caracter; el enum es una vista
 * derivada estricta y el contrato REST conserva ese codigo.
 *
 * <p>La moneda se hereda de la renta final de la solicitud. Una liquidacion
 * nunca elige ni transforma moneda por su cuenta.
 */
@Entity
@Table(name = "comision_liquidacion")
public class ComisionLiquidacion extends EntidadDeOrganizacion implements Transicionable {

    public static final String ENTIDAD_TIPO = "COMISION_LIQUIDACION";

    public static final String PENDIENTE = Codigos.Comision.PENDIENTE;
    public static final String PARCIAL = Codigos.Comision.PARCIAL;
    public static final String COBRADA = Codigos.Comision.COBRADA;
    public static final String ANULADA = Codigos.Comision.ANULADA;
    public static final Set<String> ESTADOS = Set.of(PENDIENTE, PARCIAL, COBRADA, ANULADA);

    /** Desenlaces que el broker puede registrar en el cobro. */
    public static final Set<String> ESTADOS_DE_COBRO = Set.of(COBRADA, ANULADA);

    public static final Set<String> FORMAS_PAGO = Set.of(
            "TRANSFERENCIA", "DEPOSITO_BANCARIO", "EFECTIVO", "CHEQUE", "OTRO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision_liquidacion")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contrato_alquiler", nullable = false)
    private ContratoAlquiler contrato;

    @Column(name = "monto_bruto", nullable = false)
    private BigDecimal montoBruto;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "parte_agente")
    private BigDecimal parteAgente;

    @Column(name = "parte_empresa")
    private BigDecimal parteEmpresa;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    // ------------------------------------------------------------------
    // Transicionable
    // ------------------------------------------------------------------

    @Override
    public String entidadTipo() {
        return ENTIDAD_TIPO;
    }

    @Override
    public String estadoActual() {
        return estado;
    }

    @Override
    public void transicionarA(String nuevoEstado) {
        this.estado = EstadoComision.desde(nuevoEstado).codigo();
    }

    @Transient
    public EstadoComision estadoTipado() {
        return estado == null ? null : EstadoComision.desde(estado);
    }

    /**
     * El broker define el monto del agente y la empresa se queda con el resto
     * de la comision bruta. Nunca deja un monto de empresa negativo.
     */
    public void asignarMontoAgente(BigDecimal montoAgente) {
        this.parteAgente = montoAgente;
        this.parteEmpresa = montoBruto == null || montoAgente == null
                ? null
                : montoBruto.subtract(montoAgente).max(BigDecimal.ZERO);
    }

    public boolean estaPendiente() {
        return PENDIENTE.equals(estadoActual()) || PARCIAL.equals(estadoActual());
    }

    // ------------------------------------------------------------------
    // Accesores
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ContratoAlquiler getContrato() {
        return contrato;
    }

    public void setContrato(ContratoAlquiler contrato) {
        this.contrato = contrato;
    }

    public BigDecimal getMonto() {
        return montoBruto;
    }

    public void setMonto(BigDecimal monto) {
        this.montoBruto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMontoAgente() {
        return parteAgente;
    }

    public BigDecimal getMontoEmpresa() {
        return parteEmpresa;
    }
}
