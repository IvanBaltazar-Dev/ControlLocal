package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Historico de precios de la propiedad por hito comercial (fortaleza
 * heredada de la v1: nunca se pierde la evolucion del precio).
 */
@Entity
@Table(name = "precio_propiedad")
public class PrecioPropiedad extends EntidadDeOrganizacion {

    /**
     * 'E' esperado, 'R' recomendado, 'U' autorizado, 'P' publicado,
     * 'O' ofertado, 'A' aceptado, 'C' cerrado.
     */
    public static final String HITO_ESPERADO = "E";
    public static final String HITO_AUTORIZADO = "U";
    public static final Set<String> HITOS = Set.of("E", "R", "U", "P", "O", "A", "C");

    public static final String MONEDA_PEN = "PEN";
    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    @Column(name = "hito", nullable = false, length = 1)
    private String hito;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getHito() {
        return hito;
    }

    public void setHito(String hito) {
        this.hito = hito;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
