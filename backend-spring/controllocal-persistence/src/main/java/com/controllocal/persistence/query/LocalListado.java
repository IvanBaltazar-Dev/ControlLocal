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
 */
public interface LocalListado {

    Long getId();

    String getCodigoLocal();

    String getDireccion();

    String getDistrito();

    BigDecimal getMetraje();

    BigDecimal getPrecioReferencial();

    String getMonedaReferencial();

    String getRubroPermitido();

    String getDescripcion();

    String getEstado();

    Long getIdPropietario();

    String getPropietarioNombre();

    String getTipoInmueble();

    String getUso();

    Integer getAmbientes();

    Integer getAntiguedadAnios();

    String getZonaUrbanizacion();

    BigDecimal getGeoLat();

    BigDecimal getGeoLong();

    BigDecimal getFrente();

    String getZonificacion();

    Boolean getAptoLicenciaFuncionamiento();

    BigDecimal getCargaElectricaKw();

    Integer getNumeroEstacionamientos();

    BigDecimal getCuotaMantenimiento();

    Long getIdDistrito();

    OffsetDateTime getFechaRegistro();
}
