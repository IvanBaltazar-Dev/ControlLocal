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
 * Version concreta del anuncio de una propiedad en un canal (MEJ-04: el
 * esquema rico se conserva). La publicacion mas reciente por
 * fecha_publicacion es la "principal": su estado es el estadoPublicacion
 * del cable congelado (B si no hay ninguna).
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

    @Column(name = "canal", nullable = false, length = 30)
    private String canal;

    @Column(name = "url_publicacion", length = 500)
    private String urlPublicacion;

    @Column(name = "version_anuncio", nullable = false)
    private Integer versionAnuncio = 1;

    @Column(name = "titulo_anuncio", nullable = false, length = 200)
    private String tituloAnuncio;

    @Column(name = "renta_publicada", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentaPublicada;

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

    public BigDecimal getRentaPublicada() {
        return rentaPublicada;
    }

    public void setRentaPublicada(BigDecimal rentaPublicada) {
        this.rentaPublicada = rentaPublicada;
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
