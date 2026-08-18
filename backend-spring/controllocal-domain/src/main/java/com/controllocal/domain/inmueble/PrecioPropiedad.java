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
    /** La renta que el mercado VE. La escribe la publicacion, no la propiedad. */
    public static final String HITO_PUBLICADO = "P";
    public static final Set<String> HITOS = Set.of("E", "R", "U", "P", "O", "A", "C");

    public static final String MONEDA_PEN = "PEN";
    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    /** 'A' alquiler, 'V' venta (D-E4-1 M3). */
    public static final String OPERACION_ALQUILER = "A";
    public static final String OPERACION_VENTA = "V";
    public static final Set<String> OPERACIONES = Set.of(OPERACION_ALQUILER, OPERACION_VENTA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /**
     * Encargo dueno de esta serie (D-E4-1 M3, V49). NULL en los hitos anteriores
     * a la migracion y en los que se escriben sin encargo identificable.
     *
     * <p>Cuando viene, un trigger comprueba que {@link #operacion} coincida con
     * la del encargo: un hito de venta colgado de un encargo de alquiler es un
     * dato que miente.
     */
    @Column(name = "id_captacion")
    private Long idCaptacion;

    /**
     * De que operacion es esta serie. Sin ella, una propiedad en venta Y en
     * alquiler mezclaria 180.000 y 2.900 en una sola linea, y distinguirlos
     * por magnitud es justo lo que el modelo economico prohibe.
     *
     * <p><b>No tiene valor por defecto, ni aqui ni en la BD.</b> Lo tuvo
     * durante una tanda —{@code = OPERACION_ALQUILER}, con el argumento de que
     * todo lo que el sistema sabia hacer era alquilar— y era un error: un
     * defecto en la entidad es una inferencia silenciosa, y una inferencia
     * silenciosa sobre la operacion escribe un precio de venta en la serie de
     * alquiler sin que ningun CHECK pueda notarlo. La columna es NOT NULL sin
     * DEFAULT, y ahora Java sostiene la misma exigencia: quien escribe un hito
     * <b>declara</b> de que operacion es.
     *
     * <p>Si el productor no lo sabe, la respuesta no es alquiler: es
     * <b>faltante</b>. {@link OperacionInmobiliaria#desde(String)} lo dice con
     * esas palabras.
     */
    @Column(name = "operacion", nullable = false, length = 1)
    private String operacion;

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

    public Long getIdCaptacion() {
        return idCaptacion;
    }

    public void setIdCaptacion(Long idCaptacion) {
        this.idCaptacion = idCaptacion;
    }

    public String getOperacion() {
        return operacion;
    }

    /**
     * <b>Rechaza el nulo en vez de rellenarlo.</b> Antes coalescia a alquiler y
     * ese era el punto exacto por el que entraba la inferencia: quien olvidaba
     * la operacion no se enteraba nunca, porque el objeto se guardaba tan
     * contento. Ahora el olvido estalla aqui, con el nombre del hito delante.
     */
    public void setOperacion(String operacion) {
        this.operacion = OperacionInmobiliaria.desde(operacion).codigo();
    }

    public void setOperacion(OperacionInmobiliaria operacion) {
        if (operacion == null) {
            throw new IllegalArgumentException(
                    "Un hito de precio sin operacion no se puede guardar: declara VENTA o ALQUILER.");
        }
        this.operacion = operacion.codigo();
    }

    /**
     * Un hito completo, con todo lo que la BD exige. Es la forma prevista de
     * construir uno: nombrar los siete datos en una llamada hace evidente el
     * que falta, mientras que seis setters sueltos dejan que el septimo se
     * olvide sin que nada lo diga hasta el INSERT.
     */
    public static PrecioPropiedad hito(Long idOrganizacion, Long idPropiedad,
                                       OperacionInmobiliaria operacion, String hito,
                                       String moneda, BigDecimal monto, LocalDate fecha) {
        PrecioPropiedad precio = new PrecioPropiedad();
        precio.setOrganizacionId(idOrganizacion);
        precio.setIdPropiedad(idPropiedad);
        precio.setOperacion(operacion);
        precio.setHito(hito);
        precio.setMoneda(moneda);
        precio.setMonto(monto);
        precio.setFecha(fecha);
        return precio;
    }

    /** El mismo hito, atado a su encargo. Un trigger comprueba la coherencia. */
    public PrecioPropiedad delEncargo(Long idCaptacion) {
        this.idCaptacion = idCaptacion;
        return this;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
