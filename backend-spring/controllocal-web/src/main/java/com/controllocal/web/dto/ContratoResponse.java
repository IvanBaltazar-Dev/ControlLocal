package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;
import com.controllocal.service.ComisionService;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Una única proyección financiera Core conserva cálculo, devengo, exigibilidad y cobro separados. */
public record ContratoResponse(Long id,Long idSolicitud,String codigoSolicitud,Long idOportunidad,
        String codigoOportunidad,String clienteNombre,String direccionLocal,String distritoLocal,
        String estadoDisponibilidadLocal,String codigoCaptacion,String agenteNombre,
        BigDecimal rentaMensual,String moneda,Integer plazoContratoMeses,
        LocalDate fechaInicioContrato,LocalDate fechaFinContrato,LocalDate fechaCierre,String estadoContrato,
        String incidencias,Long agenteId,Long propietarioId,String propietarioNombre,
        Long idContratoAnterior,ComisionService.FichaComision comision) {
    public static ContratoResponse desde(ContratoService.FichaContrato f) {
        return new ContratoResponse(f.id(),f.idSolicitud(),f.codigoSolicitud(),f.idOportunidad(),
            f.codigoOportunidad(),f.clienteNombre(),f.direccionLocal(),f.distritoLocal(),
            f.estadoDisponibilidadLocal(),f.codigoCaptacion(),f.agenteNombre(),f.rentaMensual(),f.moneda(),
            f.plazoContratoMeses(),f.fechaInicioContrato(),f.fechaFinContrato(),f.fechaCierre(),f.estadoContrato(),
            f.incidencias(),f.agenteId(),f.propietarioId(),f.propietarioNombre(),f.idContratoAnterior(),f.comision());
    }
}
