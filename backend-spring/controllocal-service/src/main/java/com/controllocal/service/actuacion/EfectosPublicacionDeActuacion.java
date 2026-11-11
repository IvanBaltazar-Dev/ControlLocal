package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static com.controllocal.service.PublicacionService.*;

/** La revisión humana canónica confirma la misma versión que se preparó. */
@Component
public class EfectosPublicacionDeActuacion implements EfectoDeActuacion {
    private final PublicacionService publicaciones;
    private final ObjectMapper json;
    public EfectosPublicacionDeActuacion(PublicacionService publicaciones,ObjectMapper json){this.publicaciones=publicaciones;this.json=json;}
    public Set<String> capacidades(){return Set.of(PUBLICAR,ACTUALIZAR_VISIBLE,PAUSAR,REANUDAR,FINALIZAR);}
    public Object validar(String cap,Map<String,Object> datos,Actor actor){
        return publicaciones.validarActuacion(cap,json.convertValue(datos,DatosCambio.class),actor);
    }
    public Object resumenRevision(String cap,Map<String,Object> datos,Actor actor){return publicaciones.previsualizarActuacion(cap,datos,actor);}
    public void exigirLectura(String cap,Map<String,Object> datos,Actor actor){publicaciones.exigirLecturaActuacion(cap,datos,actor);}
    public Object ejecutar(String cap,Map<String,Object> datos,Actor a,ActuacionesService.ContextoSesion s,String k,Procedencia p){
        var d=json.convertValue(datos,DatosCambio.class);return switch(cap){
            case PUBLICAR->publicaciones.publicarAnuncio(d,a,s,k,p);
            case ACTUALIZAR_VISIBLE->publicaciones.actualizarAnuncioVisible(d,a,s,k,p);
            case PAUSAR->publicaciones.pausarAnuncio(d,a,s,k,p);
            case REANUDAR->publicaciones.reanudarAnuncio(d,a,s,k,p);
            case FINALIZAR->publicaciones.finalizarDifusionAnuncio(d,a,s,k,p);
            default->throw new IllegalArgumentException("Actuación de publicación desconocida.");
        };
    }
    public Optional<?> consultarResultado(String cap,String clave,Actor a){return publicaciones.consultarResultado(cap,clave,a);}
}
