package com.controllocal.web.dto;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.demanda.InteresadosDeDemanda.Referencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** RF028/029: datos declarados; la autoridad, SID y procedencia proceden del servidor. */
public record RequerimientoRequest(Long idCliente,String rubro,String tipoInmueble,BigDecimal rentaMin,
    BigDecimal rentaMax,String moneda,BigDecimal metrajeMin,BigDecimal metrajeMax,BigDecimal frenteMinimo,
    String estado,String observaciones,List<String> distritos,String operacion,List<String> tiposInmueble,
    BigDecimal presupuestoMin,BigDecimal presupuestoMax,LocalDate vigenteHasta,
    List<RequerimientoService.CriterioPedido> criterios,Referencia interesado,String finalidad,
    LocalDate vigenteDesde,String conceptoPresupuesto,String periodoPresupuesto,Long versionEsperada,
    Set<String> camposARetirar) {
    public RequerimientoService.DatosRequerimiento aDatos(){
        return new RequerimientoService.DatosRequerimiento(idCliente,rubro,tipoInmueble,rentaMin,rentaMax,
            frenteMinimo,operacion,tiposInmueble,presupuestoMin,presupuestoMax,moneda,metrajeMin,metrajeMax,
            vigenteHasta,estado,observaciones,distritos,criterios,interesado,finalidad,vigenteDesde,
            conceptoPresupuesto,periodoPresupuesto,camposARetirar);
    }
}