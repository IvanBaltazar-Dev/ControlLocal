package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoPublicacion;
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
import java.util.Set;

/**
 * <b>El anuncio de un ENCARGO en un canal</b> (V70).
 *
 * <h2>Por que cuelga del encargo y no de la propiedad</h2>
 * Un anuncio no anuncia «una propiedad»: anuncia que esta propiedad se ofrece
 * en ESTA operacion a ESTE precio. Con venta y alquiler simultaneos, una
 * publicacion atada solo a {@code id_propiedad} no puede decir cual de las dos
 * publica -- y el hito de precio publicado que genera tampoco.
 *
 * <p><b>{@code idEncargo} es OBLIGATORIO desde V89</b> (D-P0-11). En V70 nacio
 * nullable porque habia anuncios anteriores cuya propiedad tenia varios
 * encargos candidatos y elegir uno habria sido inventar de cual era; el
 * backfill demostrable de V70 los resolvio todos (medido el 2026-09-02: cero
 * nulos en desarrollo y en pruebas) y la columna se cerro en el esquema. Sin
 * encargo un anuncio no sabe que operacion publica, como se llama su importe
 * ni quien responde por el.
 *
 * <p>{@code idPropiedad} se conserva y no es redundante: el listado heredado y
 * el estado de publicacion del local siguen preguntando por inmueble.
 *
 * <p>La publicacion mas reciente por {@code fecha_publicacion} es la
 * "principal": su estado es el {@code estadoPublicacion} del cable heredado
 * (B si no hay ninguna).
 */
@Entity
@Table(name = "publicacion")
public class Publicacion extends EntidadDeOrganizacion {

    /** 'B' sin publicar, 'P' publicado, 'S' pausado, 'C' cerrado. */
    public static final String ESTADO_BORRADOR = Codigos.Publicacion.BORRADOR;
    public static final String ESTADO_PUBLICADO = Codigos.Publicacion.PUBLICADA;
    public static final String ESTADO_SUSPENDIDO = Codigos.Publicacion.SUSPENDIDA;
    public static final String ESTADO_CERRADO = Codigos.Publicacion.CERRADA;
    public static final Set<String> ESTADOS = Set.of(ESTADO_BORRADOR,
            ESTADO_PUBLICADO, ESTADO_SUSPENDIDO, ESTADO_CERRADO);

    public static final String CANAL_WEB_PROPIA = "WEB_PROPIA";
    public static final Set<String> CANALES = Set.of(
            "URBANIA", "ADONDEVIVIR", "PROPERATI", "NEXO_INMOBILIARIO", "FACEBOOK",
            "MARKETPLACE", "INSTAGRAM", "WHATSAPP", "WEB_PROPIA", "REFERIDO", "OTRO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_publicacion")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /**
     * El encargo que se anuncia. Es lo que dice si este anuncio publica la
     * venta o el alquiler -- y, cuando hay varios encargos de la misma
     * operacion a lo largo del tiempo, <b>cual</b> de ellos. Obligatorio
     * (V89, D-P0-11): un anuncio sin encargo no tiene operacion, ni rotulo de
     * importe, ni autoridad que decida quien lo puede tocar.
     */
    @Column(name = "id_captacion", nullable = false)
    private Long idEncargo;

    @Column(name = "canal", nullable = false, length = 30)
    private String canal;

    @Column(name = "url_publicacion", length = 500)
    private String urlPublicacion;

    @Column(name = "version_anuncio", nullable = false)
    private Integer versionAnuncio = 1;

    @Column(name = "titulo_anuncio", nullable = false, length = 200)
    private String tituloAnuncio;

    /**
     * Lo que el mercado VE. Se llamaba {@code rentaPublicada} y en una
     * publicacion de venta eso era sencillamente falso: el nombre viajaba por
     * el cable hasta la pantalla. Como se llama para una persona --«precio de
     * venta» o «renta mensual»-- lo dice la operacion del encargo,
     * {@code OperacionInmobiliaria.nombreDelImporte()}.
     */
    @Column(name = "importe_publicado", nullable = false, precision = 12, scale = 2)
    private BigDecimal importePublicado;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Column(name = "inversion_pauta", precision = 12, scale = 2)
    private BigDecimal inversionPauta;

    @Column(name = "codigo_origen", nullable = false, length = 50)
    private String codigoOrigen;

    @Column(name = "fecha_publicacion", nullable = false)
    private OffsetDateTime fechaPublicacion;

    @Column(name = "fecha_baja")
    private OffsetDateTime fechaBaja;

    @Column(name = "estado", nullable = false, length = 1)
    private String estado;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
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

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getUrlPublicacion() {
        return urlPublicacion;
    }

    public void setUrlPublicacion(String urlPublicacion) {
        this.urlPublicacion = urlPublicacion;
    }

    public Integer getVersionAnuncio() {
        return versionAnuncio;
    }

    public void setVersionAnuncio(Integer versionAnuncio) {
        this.versionAnuncio = versionAnuncio;
    }

    public String getTituloAnuncio() {
        return tituloAnuncio;
    }

    public void setTituloAnuncio(String tituloAnuncio) {
        this.tituloAnuncio = tituloAnuncio;
    }

    public BigDecimal getImportePublicado() {
        return importePublicado;
    }

    public void setImportePublicado(BigDecimal importePublicado) {
        this.importePublicado = importePublicado;
    }

    public Long getIdEncargo() {
        return idEncargo;
    }

    public void setIdEncargo(Long idEncargo) {
        this.idEncargo = idEncargo;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getInversionPauta() {
        return inversionPauta;
    }

    public void setInversionPauta(BigDecimal inversionPauta) {
        this.inversionPauta = inversionPauta;
    }

    public String getCodigoOrigen() {
        return codigoOrigen;
    }

    public void setCodigoOrigen(String codigoOrigen) {
        this.codigoOrigen = codigoOrigen;
    }

    public OffsetDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    public void setFechaPublicacion(OffsetDateTime fechaPublicacion) {
        this.fechaPublicacion = fechaPublicacion;
    }

    public OffsetDateTime getFechaBaja() {
        return fechaBaja;
    }

    public void setFechaBaja(OffsetDateTime fechaBaja) {
        this.fechaBaja = fechaBaja;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoPublicacion.desde(estado).codigo();
    }

    @Transient
    public EstadoPublicacion estadoTipado() {
        return estado == null ? null : EstadoPublicacion.desde(estado);
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
