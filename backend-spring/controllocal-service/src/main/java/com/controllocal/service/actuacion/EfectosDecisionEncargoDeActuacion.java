package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF015/4.6.2: una sola decisión C2, ejecutada por el dispatch humano verificado de UC-K06/07. */
@Component
public class EfectosDecisionEncargoDeActuacion implements EfectoDeActuacion {
    public static final String CAPACIDAD="decidir_captacion";
    public record Datos(long peticion,EncargoService.Decision decision) { }
    private final EncargoService encargos;
    private final ObjectMapper json;

    public EfectosDecisionEncargoDeActuacion(EncargoService encargos,ObjectMapper json){
        this.encargos=encargos;this.json=json;
    }
    @Override public Set<String> capacidades(){return Set.of(CAPACIDAD);}
    @Override public Object validar(String capacidad,Map<String,Object> datos,Actor actor){
        var d=datos(capacidad,datos);
        return new Datos(d.peticion(),encargos.validarDecision(d.peticion(),d.decision(),actor));
    }
    @Override public Object resumenRevision(String capacidad,Map<String,Object> datos,Actor actor){
        var d=datos(capacidad,datos);
        return Map.of("peticion",encargos.consultarPeticion(d.peticion(),actor));
    }
    @Override public void exigirLectura(String capacidad,Map<String,Object> datos,Actor actor){
        var d=datos(capacidad,datos);
        encargos.exigirLecturaDecision(d.peticion(),d.decision(),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Object ejecutar(String capacidad,Map<String,Object> datos,Actor actor,
            ActuacionesService.ContextoSesion sesion,String clave,Procedencia procedencia){
        var d=datos(capacidad,datos);
        return encargos.decidir(d.peticion(),d.decision(),actor,sesion,clave,procedencia);
    }
    @Override public Optional<?> consultarResultado(String capacidad,String clave,Actor actor){
        exigir(capacidad);
        return encargos.consultarResultadoDecision(clave,actor);
    }
    private Datos datos(String capacidad,Map<String,Object> datos){
        exigir(capacidad);
        var d=json.convertValue(datos,Datos.class);
        if(d==null||d.peticion()<=0||d.decision()==null)
            throw new ReglaNegocioException("Debe identificar la petición y la decisión sobre su versión exacta.");
        return d;
    }
    private static void exigir(String capacidad){
        if(!CAPACIDAD.equals(capacidad))throw new IllegalArgumentException("Decisión de Encargo no reconocida.");
    }
}
