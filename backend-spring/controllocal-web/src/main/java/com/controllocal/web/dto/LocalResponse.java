package com.controllocal.web.dto;

import com.controllocal.service.LocalComercialService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La ficha del inmueble tal como la lee la pantalla del encargo.
 * idPropietario = persona_rol.id del rol PROPIETARIO en la semantica v2.
 *
 * <p><b>{@code responsabilidad} es aditivo</b> (P0-H1) y reutiliza el MISMO
 * tipo de cable que {@code GET /propiedades/{id}}:
 * {@link PropiedadUniversalDtos.ResponsabilidadResponse}. No es ahorro de
 * codigo — es que dos pantallas que preguntan lo mismo tienen que recibir la
 * misma respuesta, con los mismos nombres y el mismo texto. Un segundo record
 * con los mismos campos seria la "segunda manera de escribir esto", y las dos
 * maneras se separan en el primer cambio.
 *
 * <p>Solo viaja en el <b>detalle</b>. En los listados es {@code null} y Jackson
 * ({@code NON_NULL}) no lo emite: en Angular llega {@code undefined}, se compara
 * con {@code == null} y el cliente cae del lado que no ofrece el boton.
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
                            String nombreEdificioGaleria,
                            PropiedadUniversalDtos.ResponsabilidadResponse responsabilidad) {

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
                f.nombreEdificioGaleria(),
                PropiedadUniversalDtos.ResponsabilidadResponse.desde(f.responsabilidad()));
    }
}
