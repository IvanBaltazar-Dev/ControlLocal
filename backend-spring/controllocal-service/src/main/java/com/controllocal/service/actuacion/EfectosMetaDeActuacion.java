package com.controllocal.service.actuacion;
import com.controllocal.service.*;
import com.controllocal.service.impl.MetaComercialServiceImpl;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** RF075 usa la aprobación exacta existente; no incorpora un circuito de autoridad adicional. */
@Component
public class EfectosMetaDeActuacion implements EfectoDeActuacion {
    private final MetaComercialService metas;private final ObjectMapper json;
    public EfectosMetaDeActuacion(MetaComercialService m,ObjectMapper j){metas=m;json=j;}
    public Set<String> capacidades(){return Set.of(MetaComercialServiceImpl.FIJAR,MetaComercialServiceImpl.DECIDIR);}
    public Object validar(String c,Map<String,Object>d,Actor a){return switch(c){
        case MetaComercialServiceImpl.FIJAR->metas.validarEstablecimiento(json.convertValue(d,MetaComercialService.Establecer.class),a);
        case MetaComercialServiceImpl.DECIDIR->metas.validarDecision(json.convertValue(d,MetaComercialService.Decision.class),a);
        default->throw new IllegalArgumentException("Actuación de meta desconocida.");};}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){
        if(MetaComercialServiceImpl.FIJAR.equals(c)){var datos=json.convertValue(d,MetaComercialService.Establecer.class);
            return Map.of("metasObservadas",metas.del(datos.mes(),a).stream().filter(m->!m.historica()&&datos.asignaciones().stream().anyMatch(x->x.idRolAgente()==m.idRolAgente()&&x.kpi().equals(m.kpi())&&x.operacion().equals(m.operacion()))).toList());}
        if(MetaComercialServiceImpl.DECIDIR.equals(c))return Map.of("propuesta",metas.consultarPropuesta(json.convertValue(d,MetaComercialService.Decision.class).propuesta(),a));
        throw new IllegalArgumentException("Actuación de meta desconocida.");
    }
    public void exigirLectura(String c,Map<String,Object>d,Actor a){
        if(MetaComercialServiceImpl.FIJAR.equals(c))metas.exigirLecturaEstablecimiento(json.convertValue(d,MetaComercialService.Establecer.class),a);
        else if(MetaComercialServiceImpl.DECIDIR.equals(c))metas.consultarPropuesta(json.convertValue(d,MetaComercialService.Decision.class).propuesta(),a);
        else throw new IllegalArgumentException("Actuación de meta desconocida.");
    }
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){return switch(c){
        case MetaComercialServiceImpl.FIJAR->metas.fijar(json.convertValue(d,MetaComercialService.Establecer.class),a,s,clave,p);
        case MetaComercialServiceImpl.DECIDIR->metas.resolver(json.convertValue(d,MetaComercialService.Decision.class),a,s,clave,p);
        default->throw new IllegalArgumentException("Actuación de meta desconocida.");};}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return metas.resultado(c,clave,a);}
}
