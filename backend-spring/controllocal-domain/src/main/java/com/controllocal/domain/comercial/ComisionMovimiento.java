package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Evidencia economica inmutable de cobro, pago, ajuste o reversion. */
@Entity
@Table(name = "comision_movimiento")
public class ComisionMovimiento extends EntidadDeOrganizacion {
    public static final String COBRO = "C";
    public static final String PAGO_AGENTE = "P";
    public static final String AJUSTE = "A";
    public static final String REVERSION = "R";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comision_movimiento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_comision_liquidacion", nullable = false)
    private ComisionLiquidacion liquidacion;

    @Column(name = "tipo", nullable = false, length = 1)
    private String tipo;
    @Column(name = "monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;
    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;
    @Column(name = "forma_pago", length = 20)
    private String formaPago;
    @Column(name = "id_usuario")
    private Long idUsuario;
    @Column(name = "rol_usuario", length = 20)
    private String rolUsuario;
    @Column(name = "observacion", length = 300)
    private String observacion;
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * Identidad del COMANDO que creo esta fila, no de su contenido: dos abonos
     * iguales el mismo dia son legitimos y solo el cliente sabe si el segundo
     * es un abono nuevo o el reintento del primero. Opcional mientras el
     * contrato legado siga congelado; el indice unico parcial
     * {@code uq_movimiento_idempotencia} es quien cubre la carrera.
     */
    @Column(name = "clave_idempotencia", length = 64)
    private String claveIdempotencia;

    /** SHA-256 del comando. Misma clave con otra huella es un conflicto. */
    @Column(name = "huella_comando", length = 64)
    private String huellaComando;

    public Long getId() { return id; }
    public ComisionLiquidacion getLiquidacion() { return liquidacion; }
    public void setLiquidacion(ComisionLiquidacion liquidacion) { this.liquidacion = liquidacion; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getFormaPago() { return formaPago; }
    public void setFormaPago(String formaPago) { this.formaPago = formaPago; }
    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public String getRolUsuario() { return rolUsuario; }
    public void setRolUsuario(String rolUsuario) { this.rolUsuario = rolUsuario; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public void setClaveIdempotencia(String claveIdempotencia) { this.claveIdempotencia = claveIdempotencia; }
    public String getHuellaComando() { return huellaComando; }
    public void setHuellaComando(String huellaComando) { this.huellaComando = huellaComando; }
}
