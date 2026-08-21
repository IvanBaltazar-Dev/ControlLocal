package com.controllocal.persistence.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Proyeccion estrecha de {@code GET /locales}.
 *
 * <p>El listado no necesita entidades administradas: solo los campos del
 * {@code LocalResponse}. Proyectarlos evita inicializar, una por una, las
 * asociaciones LAZY de propietario y detalle comercial al mapear la pagina.
 * La portada y el estado de publicacion se completan con consultas en lote.
 *
 * <p><b>Las seis claves gobernadas no estan aqui</b> (D-E4-3): su autoridad es
 * {@code atributo_propiedad}, y se hidratan por lote para los ids de la pagina.
 * {@code metraje} si esta, porque es el unico estructural y un listado tiene
 * que poder ordenarse por el en SQL.
 */
public interface LocalListado {

    Long getId();

    String getCodigoLocal();

    String getDireccion();

    String getDistrito();

    BigDecimal getMetraje();

    BigDecimal getPrecioReferencial();

    String getMonedaReferencial();

    String getDescripcion();

    String getEstado();

    Long getIdPropietario();

    String getPropietarioNombre();

    String getTipoInmueble();

    String getUso();

    String getZonaUrbanizacion();

    BigDecimal getGeoLat();

    BigDecimal getGeoLong();

    Long getIdDistrito();

    OffsetDateTime getFechaRegistro();
}
