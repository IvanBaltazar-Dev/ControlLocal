package com.controllocal.service;
import com.controllocal.service.soporte.PeriodoCalendario;
import com.controllocal.service.soporte.AvanceDeMeta;
import java.time.Instant;
import java.util.List;

/** RF076/077 y catálogo20.10: conteos del período, no stock ni KPIhistóricos convertidos. */
public interface RendimientoComercialService {
    Rendimiento del(String mes,Actor actor);
    Pulso pulso(String mes,Actor actor);
    record Kpi(String codigo,String operacion,String rotulo,String unidad,String hecho,AvanceDeMeta avance) { }
    record PorResponsable(long idRolAgente,String nombreProfesional,List<Kpi> indicadores) { public PorResponsable{indicadores=List.copyOf(indicadores);} }
    record Pulso(int responsables,long categoriasConMeta,long categoriasSinMeta,long datosSinBase,long calendariosPendientes) { }
    record Rendimiento(String definicion,PeriodoCalendario periodo,Instant generadoEn,List<Kpi> kpis,
                       List<PorResponsable> responsables,Pulso pulso,List<MetaComercialService.MetaDeAgente> metasHistoricas,
                       long fuentesSinAtribucion,long hechosDeOtrosResponsablesHistoricos,String universo) {
        public Rendimiento{kpis=List.copyOf(kpis);responsables=List.copyOf(responsables);metasHistoricas=List.copyOf(metasHistoricas);}
    }
}
