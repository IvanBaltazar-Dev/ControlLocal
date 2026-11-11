package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.ComisionService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF064 y reparto existente: preparar no registra fondos; sólo la confirmación ejecuta. */
@Component
public class EfectosComisionDeActuacion implements EfectoDeActuacion {
    public static final String MOVIMIENTO="registrar_cobro",REPARTO="asignar_reparto_comision",
            REPROGRAMAR="reprogramar_gestion_cobro",CONCLUIR="concluir_gestion_cobro";
    private final ComisionService comisiones;
    private final ObjectMapper json;
    public EfectosComisionDeActuacion(ComisionService comisiones,ObjectMapper json){this.comisiones=comisiones;this.json=json;}
    public Set<String> capacidades(){return Set.of(MOVIMIENTO,REPARTO,REPROGRAMAR,CONCLUIR);}
    public Object validar(String c,Map<String,Object>d,Actor a){return switch(c){
        case MOVIMIENTO -> comisiones.validarCobro(json.convertValue(d,ComisionService.DatosCobro.class),a);
        case REPARTO -> comisiones.validarReparto(json.convertValue(d,ComisionService.DatosReparto.class),a);
        case REPROGRAMAR -> comisiones.validarReprogramacion(json.convertValue(d,ComisionService.DatosReprogramacion.class),a);
        case CONCLUIR -> comisiones.validarConclusion(json.convertValue(d,ComisionService.DatosConclusion.class),a);
        default -> throw desconocida();
    };}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){return comisiones.previsualizarActuacion(c,d,a);}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){switch(c){
        case MOVIMIENTO -> comisiones.exigirLecturaCobro(json.convertValue(d,ComisionService.DatosCobro.class),a);
        case REPARTO -> comisiones.exigirLecturaReparto(json.convertValue(d,ComisionService.DatosReparto.class),a);
        case REPROGRAMAR -> comisiones.exigirLecturaReprogramacion(json.convertValue(d,ComisionService.DatosReprogramacion.class),a);
        case CONCLUIR -> comisiones.exigirLecturaConclusion(json.convertValue(d,ComisionService.DatosConclusion.class),a);
        default -> throw desconocida();
    }}
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){return switch(c){
        case MOVIMIENTO -> comisiones.registrarCobro(json.convertValue(d,ComisionService.DatosCobro.class),a,s,clave,p);
        case REPARTO -> comisiones.asignarReparto(json.convertValue(d,ComisionService.DatosReparto.class),a,s,clave,p);
        case REPROGRAMAR -> comisiones.reprogramarGestion(json.convertValue(d,ComisionService.DatosReprogramacion.class),a,s,clave,p);
        case CONCLUIR -> comisiones.concluirGestion(json.convertValue(d,ComisionService.DatosConclusion.class),a,s,clave,p);
        default -> throw desconocida();
    };}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return switch(c){
        case MOVIMIENTO -> comisiones.consultarResultadoCobro(clave,a);
        case REPARTO -> comisiones.consultarResultadoReparto(clave,a);
        case REPROGRAMAR,CONCLUIR -> comisiones.consultarResultadoGestion(c,clave,a);
        default -> throw desconocida();
    };}
    private static IllegalArgumentException desconocida(){return new IllegalArgumentException("Actuación económica no reconocida.");}
}
