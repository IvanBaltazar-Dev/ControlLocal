package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Condiciones economicas 1:1 del encargo. Cada numero lleva unidad, base y
 * moneda explicitas; ninguna capa puede inferir su significado por magnitud.
 */
@Entity
@Table(name = "condicion_economica_captacion")
public class CondicionEconomicaCaptacion extends EntidadDeOrganizacion {

    public static final String ARRENDAMIENTO = "A";
    public static final String VENTA = "V";
    public static final String EQUIVALENTE_MENSUALIDADES = "E";
    public static final String PORCENTAJE = "P";
    public static final String MONTO_FIJO = "F";
    public static final String RENTA_MENSUAL = "R";
    public static final String PRECIO_VENTA = "V";
    public static final String NO_APLICA = "N";
    public static final String IGV_INCLUIDO = "I";
    public static final String IGV_ADICIONAL = "A";
    public static final String IGV_NO_APLICA = "N";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicion_economica")
    private Long id;

    @Column(name = "tipo_operacion", nullable = false, length = 1)
    private String tipoOperacion;

    @Column(name = "importe_referencia", nullable = false, precision = 14, scale = 2)
    private BigDecimal importeReferencia;

    @Column(name = "moneda_referencia", nullable = false, length = 3)
    private String monedaReferencia;

    @Column(name = "tipo_comision", nullable = false, length = 1)
    private String tipoComision;

    @Column(name = "base_calculo", nullable = false, length = 1)
    private String baseCalculo;

    @Column(name = "valor_comision", nullable = false, precision = 12, scale = 4)
    private BigDecimal valorComision;

    @Column(name = "moneda_comision", length = 3)
    private String monedaComision;

    @Column(name = "tratamiento_igv", nullable = false, length = 1)
    private String tratamientoIgv;

    @Column(name = "motivo_sin_comision", length = 300)
    private String motivoSinComision;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    public Long getId() { return id; }
    public String getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }
    public BigDecimal getImporteReferencia() { return importeReferencia; }
    public void setImporteReferencia(BigDecimal importeReferencia) { this.importeReferencia = importeReferencia; }
    public String getMonedaReferencia() { return monedaReferencia; }
    public void setMonedaReferencia(String monedaReferencia) { this.monedaReferencia = monedaReferencia; }
    public String getTipoComision() { return tipoComision; }
    public void setTipoComision(String tipoComision) { this.tipoComision = tipoComision; }
    public String getBaseCalculo() { return baseCalculo; }
    public void setBaseCalculo(String baseCalculo) { this.baseCalculo = baseCalculo; }
    public BigDecimal getValorComision() { return valorComision; }
    public void setValorComision(BigDecimal valorComision) { this.valorComision = valorComision; }
    public String getMonedaComision() { return monedaComision; }
    public void setMonedaComision(String monedaComision) { this.monedaComision = monedaComision; }
    public String getTratamientoIgv() { return tratamientoIgv; }
    public void setTratamientoIgv(String tratamientoIgv) { this.tratamientoIgv = tratamientoIgv; }
    public String getMotivoSinComision() { return motivoSinComision; }
    public void setMotivoSinComision(String motivoSinComision) { this.motivoSinComision = motivoSinComision; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void touch() { fechaActualizacion = OffsetDateTime.now(); }
}
