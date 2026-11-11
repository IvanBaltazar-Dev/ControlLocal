package com.controllocal.service.materialia;

import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Referencias privadas servidor. Sin configuración aprobada no hay perfil por defecto ni cifras inventadas. */
@Component
public class PerfilesMaterialIa {
    public record Perfil(String id,String version) { }
    private final Environment entorno;
    public PerfilesMaterialIa(Environment entorno){this.entorno=entorno;}
    public Perfil de(Accion accion){
        String prefijo="controllocal.ia.material."+accion.name().toLowerCase(java.util.Locale.ROOT)+".";
        String id=entorno.getProperty(prefijo+"perfil"),version=entorno.getProperty(prefijo+"version");
        if(id==null||id.isBlank()||id.length()>60||version==null||version.isBlank()||version.length()>80)
            throw new CapacidadDeshabilitadaException();
        return new Perfil(id,version);
    }
    public boolean reconoce(String perfil){
        for(Accion accion:Accion.values())try{if(de(accion).id().equals(perfil))return true;}
        catch(CapacidadDeshabilitadaException pendiente){/* La modalidad no tiene configuración operativa. */}
        return false;
    }
}
