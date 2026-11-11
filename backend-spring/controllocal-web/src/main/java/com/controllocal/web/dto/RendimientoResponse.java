package com.controllocal.web.dto;
import com.controllocal.service.RendimientoComercialService;
import com.controllocal.service.MetaComercialService;
import java.time.Instant;
import java.util.List;

/** Proyección autorizada20.10. No adapta las categorías nuevas a C/P/S/F. */
public record RendimientoResponse(String definicion,PeriodoResponse periodo,Instant generadoEn,
        List<RendimientoComercialService.Kpi> kpis,List<RendimientoComercialService.PorResponsable> responsables,
        RendimientoComercialService.Pulso pulso,List<MetaComercialService.MetaDeAgente> metasHistoricas,
        long fuentesSinAtribucion,long hechosDeOtrosResponsablesHistoricos,String universo) {
    public static RendimientoResponse desde(RendimientoComercialService.Rendimiento r){
        return new RendimientoResponse(r.definicion(),PeriodoResponse.desde(r.periodo()),r.generadoEn(),r.kpis(),r.responsables(),r.pulso(),r.metasHistoricas(),r.fuentesSinAtribucion(),r.hechosDeOtrosResponsablesHistoricos(),r.universo());
    }
}
