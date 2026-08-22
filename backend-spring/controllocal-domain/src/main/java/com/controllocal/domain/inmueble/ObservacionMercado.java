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

/**
 * <b>Lo que se vio del mercado sobre este inmueble</b> (V76).
 *
 * <h2>Una serie distinta, y por que no puede ser la misma</h2>
 * {@code precio_propiedad} guarda los hechos de un ENCARGO: el importe que el
 * propietario autorizo ({@code U}), el que el mercado vio publicado ({@code P}),
 * el que se ofrecio ({@code O}), el de cierre ({@code C}). Los cuatro tienen algo
 * en comun: <b>existen porque hubo una relacion comercial que los autoriza</b>.
 *
 * <p>«Lo vi anunciado a 190 000 dolares» no es ninguno de esos. BROX no lo
 * autorizo, no lo publico y no lo negocio: lo <b>observo</b>. Meterlo en la misma
 * serie convertiria una observacion en un hecho comercial, y eso falsearia
 * cualquier comparable que se construya despues — que es justamente para lo que
 * este dato existe.
 *
 * <blockquote>BROX nunca convierte una observacion de mercado en un hecho
 * comercial ni inventa una relacion para poder conservar conocimiento.</blockquote>
 *
 * <h2>Append-only, y con evidencia</h2>
 * Una observacion es un <b>hecho fechado</b>. Corregirla borraria la muestra que
 * la hace util: lo que se hace cuando el precio cambia es observar otra vez, y
 * las dos filas juntas son las que dicen como se movio. Por eso la tabla no
 * admite UPDATE ni DELETE, y lo impide un trigger — no una costumbre.
 *
 * <p>Y cada fila trae con que responder: <b>cuando</b> se vio, <b>de donde</b>
 * salio y <b>quien</b> la capturo. Sin fuente es un rumor; sin fecha, un precio
 * que no se puede comparar con nada.
 *
 * <h2>Lo que esta fila NO afirma</h2>
 * No dice que el inmueble este en venta con BROX, ni que ese precio siga
 * vigente, ni que el propietario lo acepte. Dice exactamente lo que dice: que en
 * esa fecha, esa fuente pedia ese importe.
 */
@Entity
@Table(name = "observacion_mercado")
public class ObservacionMercado extends EntidadDeOrganizacion {

    /** El mismo vocabulario que el resto del dominio: 'A' alquiler, 'V' venta. */
    public static final String OPERACION_ALQUILER = "A";
    public static final String OPERACION_VENTA = "V";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_observacion")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /**
     * Cuando se vio. No es la fecha de captura: un aviso de hace tres meses
     * anotado hoy vale por su fecha, no por la de quien lo anoto.
     */
    @Column(name = "fecha_observada", nullable = false)
    private LocalDate fechaObservada;

    /** Que se observaba: una venta o un alquiler. El mismo numero significa cosas distintas. */
    @Column(name = "operacion", nullable = false, length = 1)
    private String operacion;

    @Column(name = "importe", nullable = false, precision = 14, scale = 2)
    private BigDecimal importe;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    /**
     * De donde salio. Es lo que separa una observacion de un rumor, y lo que
     * permitira mas adelante pesar unas fuentes mas que otras.
     */
    @Column(name = "fuente", nullable = false, length = 30)
    private String fuente;

    /** El detalle libre: el enlace del aviso, la referencia del cartel, la nota. */
    @Column(name = "detalle", length = 300)
    private String detalle;

    /** Quien respondio por ella. Una evidencia sin autor no es evidencia. */
    @Column(name = "id_rol_actor", nullable = false)
    private Long idRolActor;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    protected ObservacionMercado() {
        // JPA
    }

    public ObservacionMercado(Long idOrganizacion, Long idPropiedad, LocalDate fechaObservada,
                              String operacion, BigDecimal importe, String moneda, String fuente,
                              String detalle, Long idRolActor) {
        setOrganizacionId(idOrganizacion);
        this.idPropiedad = idPropiedad;
        this.fechaObservada = fechaObservada;
        this.operacion = operacion;
        this.importe = importe;
        this.moneda = moneda;
        this.fuente = fuente;
        this.detalle = detalle;
        this.idRolActor = idRolActor;
    }

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public LocalDate getFechaObservada() {
        return fechaObservada;
    }

    public String getOperacion() {
        return operacion;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public String getMoneda() {
        return moneda;
    }

    public String getFuente() {
        return fuente;
    }

    public String getDetalle() {
        return detalle;
    }

    public Long getIdRolActor() {
        return idRolActor;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
