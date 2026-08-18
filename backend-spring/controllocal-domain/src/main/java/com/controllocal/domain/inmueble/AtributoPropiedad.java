package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Valor de una caracteristica gobernada para una propiedad (D-E4-1 M2, V48).
 *
 * <p>Sustituye a {@code detalle_local_comercial} y a las columnas de subtipo de
 * {@link Propiedad}. Con siete tipos de propiedad, una tabla por tipo serian
 * siete tablas, siete formularios y siete ramas en cada consulta; con esto, el
 * tipo solo decide QUE se pregunta, no DONDE se guarda.
 *
 * <p><b>Un atributo sin responder NO se guarda vacio: se omite.</b> Guardar
 * nulos llenaria la tabla de filas que no dicen nada y el matcher no podria
 * distinguir "no aplica" de "no lo se". Por eso las tres columnas de valor son
 * excluyentes y hay un CHECK en la BD que exige exactamente una.
 *
 * <p>Las fabricas de abajo son la unica forma prevista de construir uno: hacen
 * imposible por construccion el estado que el CHECK rechazaria, y asi el error
 * llega antes del viaje a la base.
 */
@Entity
@Table(name = "atributo_propiedad")
public class AtributoPropiedad extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atributo_propiedad")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /** Del catalogo. La BD rechaza una clave que no este en el (trigger V48). */
    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "valor_texto")
    private String valorTexto;

    @Column(name = "valor_numero", precision = 14, scale = 4)
    private BigDecimal valorNumero;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public static AtributoPropiedad deTexto(Long idOrganizacion, Long idPropiedad, String clave, String valor) {
        AtributoPropiedad a = base(idOrganizacion, idPropiedad, clave);
        a.valorTexto = exigir(valor, clave);
        return a;
    }

    public static AtributoPropiedad deNumero(Long idOrganizacion, Long idPropiedad, String clave, BigDecimal valor) {
        AtributoPropiedad a = base(idOrganizacion, idPropiedad, clave);
        a.valorNumero = exigir(valor, clave);
        return a;
    }

    public static AtributoPropiedad deBooleano(Long idOrganizacion, Long idPropiedad, String clave, Boolean valor) {
        AtributoPropiedad a = base(idOrganizacion, idPropiedad, clave);
        a.valorBooleano = exigir(valor, clave);
        return a;
    }

    private static AtributoPropiedad base(Long idOrganizacion, Long idPropiedad, String clave) {
        AtributoPropiedad a = new AtributoPropiedad();
        a.setOrganizacionId(idOrganizacion);
        a.idPropiedad = idPropiedad;
        a.clave = clave;
        return a;
    }

    private static <T> T exigir(T valor, String clave) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    "El atributo \"" + clave + "\" llego sin valor: un atributo sin responder se omite, no se guarda vacio.");
        }
        return valor;
    }

    /** El valor, sea cual sea la columna en la que este. */
    @Transient
    public Object valor() {
        if (valorTexto != null) {
            return valorTexto;
        }
        if (valorNumero != null) {
            return valorNumero;
        }
        return valorBooleano;
    }

    public void cambiarATexto(String valor) {
        this.valorTexto = exigir(valor, clave);
        this.valorNumero = null;
        this.valorBooleano = null;
    }

    public void cambiarANumero(BigDecimal valor) {
        this.valorNumero = exigir(valor, clave);
        this.valorTexto = null;
        this.valorBooleano = null;
    }

    public void cambiarABooleano(Boolean valor) {
        this.valorBooleano = exigir(valor, clave);
        this.valorTexto = null;
        this.valorNumero = null;
    }

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
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

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
