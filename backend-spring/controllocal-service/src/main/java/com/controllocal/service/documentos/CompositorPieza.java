package com.controllocal.service.documentos;

import java.util.Map;

/** Puerto de composición V08. Recibe sólo proyección Core y activos data:image ya saneados. */
public interface CompositorPieza {
    /** Configuración del adaptador, no una promesa de salud técnica ni un permiso comercial. */
    default boolean configurado(){return true;}
    record Binario(byte[] contenido,String contentType,String extension){
        public Binario { contenido=contenido.clone(); }
        @Override public byte[] contenido(){return contenido.clone();}
    }
    Binario componer(Map<String,Object> proyeccion,Map<String,Object> opciones,String formato);
}
