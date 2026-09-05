package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * <b>El cierre de una VENTA</b> (WP-A, D-N-3).
 *
 * <h2>Por que no es un {@code ContratoAlquiler} con un tipo</h2>
 * {@code contrato_alquiler} no es generico: lleva {@code renta_contractual},
 * {@code fecha_fin_contrato} y {@code id_contrato_anterior} —la renovacion—, y
 * de el cuelga {@code comision_liquidacion} por clave ajena. Una compraventa no
 * se renueva, no tiene plazo y no tiene renta: meterla ahi obligaria a dejar
 * esas columnas en NULL, a debilitar sus CHECK y a que cada consulta del ciclo
 * de alquiler preguntara «¿y si es una venta?».
 *
 * <h2>Las dos cifras, y por que son dos</h2>
 * <pre>
 *   monto_pactado    lo que de verdad se acordo
 *   monto_declarado  lo que consta en la escritura, SOLO si difiere
 * </pre>
 * El declarado es opcional y nace FALTANTE. <b>Nunca</b> se rellena
 * igualandolo al pactado: eso convertiria «nadie ha leido la escritura» en «la
 * escritura dice lo mismo», que es una afirmacion registral que el sistema no
 * puede hacer. Y lleva su propia moneda, porque en este mercado se pacta en
 * dolares y se escritura en soles con frecuencia.
 *
 * <h2>Sin estado, a proposito</h2>
 * Una compraventa no tiene ciclo: no vence, no se renueva y no se rescinde por
 * plazo. Los hitos registrales —minuta, escritura, bloqueo, partida— ya viven
 * en {@code condicion_compraventa} (V51). Una columna de estado sin operacion
 * que la produzca seria vocabulario huerfano.
 */
@Entity
@Table(name = "contrato_compraventa")
public class ContratoCompraventa extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato_compraventa")
    private Long id;

    @Column(name = "id_oportunidad", nullable = false)
    private Long idOportunidad;

    @Column(name = "id_captacion", nullable = false)
    private Long idCaptacion;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /** La oferta ACEPTADA que se firma. Una oferta se firma una sola vez. */
    @Column(name = "id_oferta", nullable = false)
    private Long idOferta;

    /**
     * El comprador, congelado. Es el cliente de la oportunidad en el momento
     * del cierre y no se relee de la cadena viva, por la misma razon que V27
     * congelo la atribucion del alquiler: un cierre es un hecho consumado y su
     * atribucion no debe seguir cambiando detras.
     */
    @Column(name = "id_rol_cliente", nullable = false)
    private Long idRolCliente;

    @Column(name = "id_rol_agente_cierre", nullable = false)
    private Long idRolAgenteCierre;

    /** El broker que supervisaba al agente EN LA FECHA DE FIRMA. Nulo si no tenia. */
    @Column(name = "id_rol_broker_cierre")
    private Long idRolBrokerCierre;

    @Column(name = "monto_pactado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPactado;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "monto_declarado", precision = 14, scale = 2)
    private BigDecimal montoDeclarado;

    @Column(name = "moneda_declarada", length = 3)
    private String monedaDeclarada;

    @Column(name = "fecha_firma", nullable = false)
    private LocalDate fechaFirma;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * ¿Se declaro un importe distinto en la escritura? {@code false} significa
     * «no se declaro», no «coincide»: son dos cosas distintas y el sistema solo
     * sabe la primera.
     */
    @Transient
    public boolean tieneImporteDeclarado() {
        return montoDeclarado != null;
    }

    public Long getId() {
        return id;
    }

    public Long getIdOportunidad() {
        return idOportunidad;
    }

    public void setIdOportunidad(Long idOportunidad) {
        this.idOportunidad = idOportunidad;
    }

    public Long getIdCaptacion() {
        return idCaptacion;
    }

    public void setIdCaptacion(Long idCaptacion) {
        this.idCaptacion = idCaptacion;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public Long getIdOferta() {
        return idOferta;
    }

    public void setIdOferta(Long idOferta) {
        this.idOferta = idOferta;
    }

    public Long getIdRolCliente() {
        return idRolCliente;
    }

    public void setIdRolCliente(Long idRolCliente) {
        this.idRolCliente = idRolCliente;
    }

    public Long getIdRolAgenteCierre() {
        return idRolAgenteCierre;
    }

    public void setIdRolAgenteCierre(Long idRolAgenteCierre) {
        this.idRolAgenteCierre = idRolAgenteCierre;
    }

    public Long getIdRolBrokerCierre() {
        return idRolBrokerCierre;
    }

    public void setIdRolBrokerCierre(Long idRolBrokerCierre) {
        this.idRolBrokerCierre = idRolBrokerCierre;
    }

    public BigDecimal getMontoPactado() {
        return montoPactado;
    }

    public void setMontoPactado(BigDecimal montoPactado) {
        this.montoPactado = montoPactado;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMontoDeclarado() {
        return montoDeclarado;
    }

    public String getMonedaDeclarada() {
        return monedaDeclarada;
    }

    /**
     * Lo declarado en la escritura, con su moneda. Los dos juntos o ninguno: un
     * importe sin moneda no se puede comparar con el pactado, y suponer que van
     * en la misma es justo la inferencia que este modelo prohibe.
     */
    public void declararEnEscritura(BigDecimal monto, String moneda) {
        if ((monto == null) != (moneda == null)) {
            throw new IllegalArgumentException(
                    "El importe declarado en la escritura va con su moneda, o no va: "
                            + "un monto sin moneda no se puede leer.");
        }
        this.montoDeclarado = monto;
        this.monedaDeclarada = moneda;
    }

    public LocalDate getFechaFirma() {
        return fechaFirma;
    }

    public void setFechaFirma(LocalDate fechaFirma) {
        this.fechaFirma = fechaFirma;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
