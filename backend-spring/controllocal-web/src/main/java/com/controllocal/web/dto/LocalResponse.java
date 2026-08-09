package com.controllocal.web.dto;

import com.controllocal.service.LocalComercialService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Contrato CONGELADO: mismos campos y nombres que Dtos.LocalResponse de la
 * v1 (idPropietario = persona_rol.id del rol PROPIETARIO en la semantica v2).
 */
public record LocalResponse(Long id, String codigoLocal, String direccion, String distrito, BigDecimal metraje,
                            BigDecimal precioReferencial, String monedaReferencial,
                            String rubroPermitido, String descripcion, String estado,
                            Long idPropietario, String propietarioNombre, String tipoInmueble, String uso,
                            Integer ambientes, Integer antiguedadAnios, String zonaUrbanizacion, BigDecimal geoLat,
                            BigDecimal geoLong, String estadoPublicacion, BigDecimal frente, String zonificacion,
                            Boolean aptoLicenciaFuncionamiento, BigDecimal cargaElectricaKw,
                            Integer numeroEstacionamientos, BigDecimal cuotaMantenimiento,
                            Long idDistrito, LocalDateTime fechaRegistro, String fotoPortadaClave,
                            String estadoRegistro, String disponibilidadComercial,
                            String interiorUnidad, String piso, String referenciaInterna,
                            String nombreEdificioGaleria) {

    public static LocalResponse desde(LocalComercialService.FichaLocal f) {
        return new LocalResponse(f.id(), f.codigoLocal(), f.direccion(), f.distrito(), f.metraje(),
                f.precioReferencial(), f.monedaReferencial(), f.rubroPermitido(), f.descripcion(), f.estado(),
                f.idPropietario(), f.propietarioNombre(), f.tipoInmueble(), f.uso(),
                f.ambientes(), f.antiguedadAnios(), f.zonaUrbanizacion(), f.geoLat(),
                f.geoLong(), f.estadoPublicacion(), f.frente(), f.zonificacion(),
                f.aptoLicenciaFuncionamiento(), f.cargaElectricaKw(),
                f.numeroEstacionamientos(), f.cuotaMantenimiento(),
                f.idDistrito(), f.fechaRegistro(), f.fotoPortadaClave(), f.estadoRegistro(),
                f.disponibilidadComercial(), f.interiorUnidad(), f.piso(), f.referenciaInterna(),
                f.nombreEdificioGaleria());
    }
}
