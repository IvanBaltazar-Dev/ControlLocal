package com.controllocal.domain.inmueble;

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
public class AtributoPropiedad extends EntidadDeOrganizacion implements FilaDeValorGobernado {

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

    /** Una fecha del calendario, sin hora: disponible desde, entrega (V72). */
    @Column(name = "valor_fecha")
    private LocalDate valorFecha;

    /**
     * La moneda de un IMPORTE (V72). <b>No es un valor</b>, es la unidad de
     * otro: por eso queda fuera del {@code num_nonnulls} de
     * {@code ck_atributo_un_valor} y viaja pegada a {@link #valorNumero}.
     *
     * <p>Separarla en su propia clave habria dejado que retirar el monto
     * abandonara una moneda huerfana perfectamente legal, que nada detecta.
     */
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

    public static AtributoPropiedad deFecha(Long idOrganizacion, Long idPropiedad, String clave,
                                            LocalDate valor) {
        AtributoPropiedad a = base(idOrganizacion, idPropiedad, clave);
        a.valorFecha = exigir(valor, clave);
        return a;
    }

    /**
     * Un importe: monto <b>y</b> moneda, indivisibles. Los dos se exigen aqui
     * porque un numero sin moneda no es dinero -- y el trigger de V72 rechaza
     * la fila si falta cualquiera de los dos, asi que dejarlo pasar en Java solo
     * moveria el fallo mas lejos de su causa.
     */
    public static AtributoPropiedad deImporte(Long idOrganizacion, Long idPropiedad, String clave,
                                              BigDecimal monto, String moneda) {
        AtributoPropiedad a = base(idOrganizacion, idPropiedad, clave);
        a.valorNumero = exigir(monto, clave);
        a.valorMoneda = exigir(moneda, clave);
        return a;
    }

    /**
     * La fila <b>ancla</b> de un LISTA_MULTIPLE: no lleva ningun escalar.
     *
     * <p>Todo su trabajo es decir «esta clave esta respondida» y sostener la FK
     * de la que cuelgan sus valores en {@code atributo_propiedad_opcion}. Sin
     * ella habria que retirar {@code uq_atributo_propiedad_clave}, que es el
     * indice sobre el que V71 apoyo su propia justificacion al borrar la tabla
     * espejo -- y con el se iria la garantia de un valor por propiedad y
     * concepto, que es lo que hace comparables dos carteras.
     */
    public static AtributoPropiedad anclaDeMultivalor(Long idOrganizacion, Long idPropiedad,
                                                      String clave) {
        return base(idOrganizacion, idPropiedad, clave);
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
        if (valorFecha != null) {
            return valorFecha;
        }
        return valorBooleano;
    }

    /**
     * Deja los escalares a null antes de escribir el que toca.
     *
     * <p>Cada mutador la llama en vez de nombrar uno a uno los que no son
     * suyos, y es deliberado: asi era antes, y anadir una quinta columna en
     * V72 habria dejado a los tres mutadores existentes olvidandose de
     * limpiarla -- un valor viejo sobreviviendo bajo uno nuevo, que es
     * exactamente el tipo de resto callado que este corte persigue.
     */
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

    /** Vuelve a ser el ancla de un multivalor: sin escalar, con sus opciones aparte. */
    public void cambiarAAncla() {
        limpiarEscalares();
    }

    public LocalDate getValorFecha() {
        return valorFecha;
    }

    public String getValorMoneda() {
        return valorMoneda;
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
