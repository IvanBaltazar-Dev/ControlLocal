package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Procedencia;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Casos tipados de la misma máquina UC-K06/07. No es una puerta HTTP o una aprobación alternativa. */
public interface EfectoDeActuacion {
    Set<String> capacidades();
    Object validar(String capacidad,Map<String,Object> datos,Actor actor);
    /** Detalle pertinente propio/anónimo que el humano revisa; forma parte de la misma huella. */
    default Object resumenRevision(String capacidad,Map<String,Object> datos,Actor actor) { return Map.of(); }
    void exigirLectura(String capacidad,Map<String,Object> datos,Actor actor);
    Object ejecutar(String capacidad,Map<String,Object> datos,Actor actor,
                    ActuacionesService.ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<?> consultarResultado(String capacidad,String clave,Actor actor);
}
