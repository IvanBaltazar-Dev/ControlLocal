package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.FilaDeValorGobernado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * <b>El valor de una condicion comercial, colgado de SU encargo</b> (V73).
 *
 * <h2>Por que existe un segundo sujeto</h2>
 * Hasta el Corte 0C todo atributo gobernado era un hecho de la cosa fisica,
 * porque {@code atributo_propiedad} cuelga de {@code id_propiedad} y no habia
 * otro sitio. {@code amoblado} lo demuestra: una vivienda puede tener muebles
 * y, con los mismos muebles, venderse sin ellos, alquilarse amoblada, y tener
 * dos encargos en momentos distintos con condiciones distintas. La tercera
 * historia era irrepresentable -- el dato se sobrescribia.
 *
 * <p>La regla del reparto: <b>si al firmar el siguiente alquiler el dato puede
 * cambiar sin que la propiedad haya cambiado, es del ENCARGO.</b>
 *
 * <h2>La identidad es el encargo, jamas la operacion</h2>
 * Cuelga de {@code id_captacion} y no de {@code (propiedad, operacion)}. Dos
 * alquileres sucesivos de la misma propiedad son <b>dos episodios</b>:
 * {@code uq_captacion_viva_por_operacion} (V50) prohibe dos encargos VIVOS de
 * la misma operacion, no que hayan existido varios. Agrupar por operacion haria
 * que el alquiler de 2026 heredara la garantia pactada en 2024, y lo haria sin
 * que nada fallara.
 */
@Entity
@Table(name = "atributo_encargo")
public class AtributoEncargo extends EntidadDeOrganizacion implements FilaDeValorGobernado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atributo_encargo")
    private Long id;

    /** El encargo CONCRETO. No la operacion: eso fundiria dos episodios. */
    @Column(name = "id_captacion", nullable = false)
    private Long idCaptacion;

    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "valor_texto")
    private String valorTexto;

    @Column(name = "valor_numero", precision = 14, scale = 4)
    private BigDecimal valorNumero;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;

    @Column(name = "valor_fecha")
    private LocalDate valorFecha;

    /** La moneda de un IMPORTE: no es un valor, es la unidad de otro. */
    @Column(name = "valor_moneda", length = 3)
    private String valorMoneda;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public static AtributoEncargo deTexto(Long idOrganizacion, Long idCaptacion, String clave,
                                          String valor) {
        AtributoEncargo a = base(idOrganizacion, idCaptacion, clave);
        a.valorTexto = exigir(valor, clave);
        return a;
    }

    public static AtributoEncargo deNumero(Long idOrganizacion, Long idCaptacion, String clave,
                                           BigDecimal valor) {
        AtributoEncargo a = base(idOrganizacion, idCaptacion, clave);
        a.valorNumero = exigir(valor, clave);
        return a;
    }

    public static AtributoEncargo deBooleano(Long idOrganizacion, Long idCaptacion, String clave,
                                             Boolean valor) {
        AtributoEncargo a = base(idOrganizacion, idCaptacion, clave);
        a.valorBooleano = exigir(valor, clave);
        return a;
    }

    public static AtributoEncargo deFecha(Long idOrganizacion, Long idCaptacion, String clave,
                                          LocalDate valor) {
        AtributoEncargo a = base(idOrganizacion, idCaptacion, clave);
        a.valorFecha = exigir(valor, clave);
        return a;
    }

    public static AtributoEncargo deImporte(Long idOrganizacion, Long idCaptacion, String clave,
                                            BigDecimal monto, String moneda) {
        AtributoEncargo a = base(idOrganizacion, idCaptacion, clave);
        a.valorNumero = exigir(monto, clave);
        a.valorMoneda = exigir(moneda, clave);
        return a;
    }

    /** El ancla de un multivalor: sin escalar, con sus valores en la tabla hija. */
    public static AtributoEncargo anclaDeMultivalor(Long idOrganizacion, Long idCaptacion,
                                                    String clave) {
        return base(idOrganizacion, idCaptacion, clave);
    }

    private static AtributoEncargo base(Long idOrganizacion, Long idCaptacion, String clave) {
        AtributoEncargo a = new AtributoEncargo();
        a.setOrganizacionId(idOrganizacion);
        a.idCaptacion = idCaptacion;
        a.clave = clave;
        return a;
    }

    private static <T> T exigir(T valor, String clave) {
        if (valor == null) {
            throw new IllegalArgumentException("El atributo \"" + clave + "\" llego sin valor: "
                    + "un atributo sin responder se omite, no se guarda vacio.");
        }
        return valor;
    }

    @Transient
    public Object valor() {
        if (valorTexto != null) {
            return valorTexto;
        }
        if (valorNumero != null) {
            return valorNumero;
        }
        if (valorFecha != null) {
            return valorFecha;
        }
        return valorBooleano;
    }

    /** Igual que en {@code AtributoPropiedad}: una quinta columna no se olvida. */
    private void limpiarEscalares() {
        this.valorTexto = null;
        this.valorNumero = null;
        this.valorBooleano = null;
        this.valorFecha = null;
        this.valorMoneda = null;
    }

    public void cambiarATexto(String valor) {
        String nuevo = exigir(valor, clave);
        limpiarEscalares();
        this.valorTexto = nuevo;
    }

    public void cambiarANumero(BigDecimal valor) {
        BigDecimal nuevo = exigir(valor, clave);
        limpiarEscalares();
        this.valorNumero = nuevo;
    }

    public void cambiarABooleano(Boolean valor) {
        Boolean nuevo = exigir(valor, clave);
        limpiarEscalares();
        this.valorBooleano = nuevo;
    }

    public void cambiarAFecha(LocalDate valor) {
        LocalDate nuevo = exigir(valor, clave);
        limpiarEscalares();
        this.valorFecha = nuevo;
    }

    public void cambiarAImporte(BigDecimal monto, String moneda) {
        BigDecimal nuevoMonto = exigir(monto, clave);
        String nuevaMoneda = exigir(moneda, clave);
        limpiarEscalares();
        this.valorNumero = nuevoMonto;
        this.valorMoneda = nuevaMoneda;
    }

    public void cambiarAAncla() {
        limpiarEscalares();
    }

    public Long getId() {
        return id;
    }

    public Long getIdCaptacion() {
        return idCaptacion;
    }

    public String getClave() {
        return clave;
    }

    public String getValorTexto() {
        return valorTexto;
    }

    public BigDecimal getValorNumero() {
        return valorNumero;
    }

    public Boolean getValorBooleano() {
        return valorBooleano;
    }

    public LocalDate getValorFecha() {
        return valorFecha;
    }

    public String getValorMoneda() {
        return valorMoneda;
    }
}
