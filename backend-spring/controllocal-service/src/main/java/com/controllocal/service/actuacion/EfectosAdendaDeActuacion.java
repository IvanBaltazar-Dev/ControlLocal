package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Primera decisión de adenda; no firma de parte ni activación del pacto. */
@Component
public class EfectosAdendaDeActuacion implements EfectoDeActuacion {
    public static final String AUTORIZAR="autorizar_borrador_adenda";
    public record Datos(EncargoService.ReferenciaAdenda referencia,EncargoService.DecisionBorradorAdenda decision) { }
    private final EncargoService encargos;
    private final ObjectMapper json;
    public EfectosAdendaDeActuacion(EncargoService encargos,ObjectMapper json){this.encargos=encargos;this.json=json;}
    public Set<String> capacidades(){return Set.of(AUTORIZAR);}
    public Object validar(String c,Map<String,Object>d,Actor a) {
        var x=datos(c,d);return new Datos(x.referencia(),encargos.validarDecisionBorradorAdenda(x.referencia(),x.decision(),a));
    }
    public Object resumenRevision(String c,Map<String,Object>d,Actor a) {
        var x=datos(c,d);encargos.exigirLecturaDecisionBorradorAdenda(x.referencia(),x.decision(),a);
        return Map.of("decision",x,"adenda",encargos.consultarAdenda(x.referencia(),a));
    }
    public void exigirLectura(String c,Map<String,Object>d,Actor a) {
        var x=datos(c,d);encargos.exigirLecturaDecisionBorradorAdenda(x.referencia(),x.decision(),a);
    }
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p) {
        var x=datos(c,d);return encargos.autorizarBorradorAdenda(x.referencia(),x.decision(),a,s,clave,p);
    }
    public Optional<?> consultarResultado(String c,String clave,Actor a) {
        exigir(c);return encargos.consultarResultadoDecisionBorradorAdenda(clave,a);
    }
    private Datos datos(String c,Map<String,Object>d){exigir(c);return json.convertValue(d,Datos.class);}
    private static void exigir(String c){if(!AUTORIZAR.equals(c))throw new IllegalArgumentException("Actuación de adenda no reconocida.");}
}
