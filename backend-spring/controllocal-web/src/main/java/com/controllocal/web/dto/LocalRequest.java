package com.controllocal.web.dto;

import com.controllocal.service.LocalComercialService;

import java.math.BigDecimal;

/**
 * Contrato CONGELADO: mismos campos y nombres que Dtos.LocalRequest de la
 * v1. Se traduce 1:1 al record del service (la web no ve entidades).
 */
public record LocalRequest(String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                           BigDecimal precioReferencial, String monedaReferencial,
                           String rubroPermitido, String descripcion,
                           Long idPropietario, String estado, String tipoInmueble, String uso, Integer ambientes,
                           Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat, BigDecimal geoLong,
                           String estadoPublicacion, BigDecimal frente, String zonificacion,
                           Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                           Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento,
                           String interiorUnidad, String piso, String referenciaInterna,
                           String nombreEdificioGaleria) {

    public LocalRequest(String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                        BigDecimal precioReferencial, String monedaReferencial,
                        String rubroPermitido, String descripcion, Long idPropietario,
                        String estado, String tipoInmueble, String uso, Integer ambientes,
                        Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat,
                        BigDecimal geoLong, String estadoPublicacion, BigDecimal frente,
                        String zonificacion, Boolean aptoLicenciaFuncionamiento,
                        BigDecimal cargaElectricaKw, Integer numeroEstacionamientos,
                        BigDecimal cuotaMantenimiento) {
        this(codigoLocal, direccion, distrito, metraje, precioReferencial, monedaReferencial,
                rubroPermitido, descripcion, idPropietario, estado, tipoInmueble, uso,
                ambientes, antiguedadAnios, zonaUrbanizacion, geoLat, geoLong,
                estadoPublicacion, frente, zonificacion, aptoLicenciaFuncionamiento,
                cargaElectricaKw, numeroEstacionamientos, cuotaMantenimiento,
                null, null, null, null);
    }

    public LocalComercialService.DatosLocal aDatos() {
        return new LocalComercialService.DatosLocal(codigoLocal, direccion, distrito, metraje,
                precioReferencial, monedaReferencial, rubroPermitido, descripcion,
                idPropietario, estado, tipoInmueble,
                uso, ambientes, antiguedadAnios, zonaUrbanizacion, geoLat, geoLong, estadoPublicacion,
                frente, zonificacion, aptoLicenciaFuncionamiento, cargaElectricaKw,
                numeroEstacionamientos, cuotaMantenimiento, interiorUnidad, piso,
                referenciaInterna, nombreEdificioGaleria);
    }
}
