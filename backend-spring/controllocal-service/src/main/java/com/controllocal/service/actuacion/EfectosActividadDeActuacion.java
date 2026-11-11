package com.controllocal.service.actuacion;

import com.controllocal.service.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF040–044: conserva la revisión exacta sobre los cuatro casos de actividad que la requieren. */
@Component
public class EfectosActividadDeActuacion implements EfectoDeActuacion {
    public static final String ABRIR="abrir_oportunidad",RECTIFICAR="rectificar_interaccion",
            NO_CONTINUIDAD="registrar_no_continuidad_oportunidad",CANCELAR="cancelar_visita";
    private static final Set<String> CAPACIDADES=Set.of(ABRIR,RECTIFICAR,NO_CONTINUIDAD,CANCELAR);
    private final OportunidadService oportunidades;
    private final InteraccionService interacciones;
    private final VisitaService visitas;
    private final AccesoEncargo encargos;
    private final ObjectMapper json;
    public EfectosActividadDeActuacion(OportunidadService o,InteraccionService i,VisitaService v,AccesoEncargo e,ObjectMapper j){
        oportunidades=o;interacciones=i;visitas=v;encargos=e;json=j;
    }
    public Set<String> capacidades(){return CAPACIDADES;}
    public Object validar(String c,Map<String,Object>d,Actor a){return switch(c){
        case ABRIR->oportunidades.validarApertura(como(d,OportunidadService.DatosOportunidad.class),a);
        case RECTIFICAR->interacciones.validarRectificacion(como(d,InteraccionService.Rectificacion.class),a);
        case NO_CONTINUIDAD->oportunidades.validarNoContinuidad(como(d,OportunidadService.NoContinuidad.class),a);
        case CANCELAR->visitas.validarCancelacion(como(d,VisitaService.Cancelacion.class),a);
        default->throw desconocida();
    };}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){return switch(c){
        case ABRIR->Map.of();
        case RECTIFICAR->Map.of("antecedente",interacciones.obtener(como(d,InteraccionService.Rectificacion.class).idInteraccion(),a));
        case NO_CONTINUIDAD->Map.of("oportunidad",oportunidades.obtener(como(d,OportunidadService.NoContinuidad.class).idOportunidad(),a));
        case CANCELAR->Map.of("programacion",visitas.obtener(como(d,VisitaService.Cancelacion.class).idVisita(),a));
        default->throw desconocida();
    };}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){switch(c){
        case ABRIR->oportunidades.exigirLecturaApertura(como(d,OportunidadService.DatosOportunidad.class),a);
        case RECTIFICAR->interacciones.exigirLecturaRectificacion(como(d,InteraccionService.Rectificacion.class),a);
        case NO_CONTINUIDAD->oportunidades.obtener(como(d,OportunidadService.NoContinuidad.class).idOportunidad(),a);
        case CANCELAR->visitas.obtener(como(d,VisitaService.Cancelacion.class).idVisita(),a);
        default->throw desconocida();
    }}
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){return switch(c){
        case ABRIR->oportunidades.registrar(como(d,OportunidadService.DatosOportunidad.class),a,s,clave,p);
        case RECTIFICAR->interacciones.rectificar(como(d,InteraccionService.Rectificacion.class),a,s,clave,p);
        case NO_CONTINUIDAD->oportunidades.noContinuidad(como(d,OportunidadService.NoContinuidad.class),a,s,clave,p);
        case CANCELAR->visitas.cancelar(como(d,VisitaService.Cancelacion.class),a,s,clave,p);
        default->throw desconocida();
    };}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return switch(c){
        case ABRIR->oportunidades.consultarResultadoApertura(clave,a);
        case RECTIFICAR->interacciones.consultarResultadoRectificacion(clave,a);
        case NO_CONTINUIDAD->oportunidades.consultarResultadoNoContinuidad(clave,a);
        case CANCELAR->visitas.consultarResultadoCancelacion(clave,a);
        default->throw desconocida();
    };}
    private <T>T como(Map<String,Object>d,Class<T>t){return json.convertValue(d,t);}
    private static IllegalArgumentException desconocida(){return new IllegalArgumentException("Actuación de actividad no reconocida.");}
}
