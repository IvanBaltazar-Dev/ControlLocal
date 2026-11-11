package com.controllocal.web.dto;
import com.controllocal.service.RequerimientoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Ficha propia; una colaboración no serializa esta representación completa. */
public record RequerimientoResponse(Long id,Long idCliente,String rubro,String tipoInmueble,
    BigDecimal rentaMin,BigDecimal rentaMax,String moneda,BigDecimal metrajeMin,BigDecimal metrajeMax,
    BigDecimal frenteMinimo,String estado,String observaciones,List<String> distritos,
    LocalDateTime fechaCreacion,LocalDateTime fechaActualizacion,String operacion,List<String> tiposInmueble,
    BigDecimal presupuestoMin,BigDecimal presupuestoMax,LocalDate vigenteHasta,LocalDateTime fechaRevalidacion,
    List<RequerimientoService.CriterioFicha> criterios,long version,Long idResponsable,Long idPersonaInteresada,
    String finalidad,LocalDate vigenteDesde,String conceptoPresupuesto,String periodoPresupuesto,
    Map<String,Object> contextoInteresado,RequerimientoService.FinDeclarado fin) {
    public static RequerimientoResponse desde(RequerimientoService.FichaRequerimiento f){
        return new RequerimientoResponse(f.id(),f.idCliente(),f.rubro(),f.tipoInmueble(),f.rentaMin(),f.rentaMax(),
            f.moneda(),f.metrajeMin(),f.metrajeMax(),f.frenteMinimo(),f.estado(),f.observaciones(),f.distritos(),
            f.fechaCreacion(),f.fechaActualizacion(),f.operacion(),f.tiposInmueble(),f.presupuestoMin(),
            f.presupuestoMax(),f.vigenteHasta(),f.fechaRevalidacion(),f.criterios(),f.version(),f.idResponsable(),
            f.idPersonaInteresada(),f.finalidad(),f.vigenteDesde(),f.conceptoPresupuesto(),f.periodoPresupuesto(),
            f.contextoInteresado(),f.fin());
    }
}