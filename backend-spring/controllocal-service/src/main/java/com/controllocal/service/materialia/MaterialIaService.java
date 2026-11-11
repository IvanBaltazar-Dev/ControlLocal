package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import java.util.*;

/**
 * C6: asistencia privada. Generar no adopta datos, publica ni comparte.
 * El consumidor llama sin TX SQL: termina sus escrituras antes de entrar, sin
 * envolver el procesador en ComandosAutomaticos/EfectoDeActuacion ni suspender un
 * padre para ocultarlo. El registro técnico existente conserva intención/replay.
 */
public interface MaterialIaService {
    enum Accion { REDACTAR_FICHA, ANALIZAR_FOTO, MEJORAR_FOTO }
    /** Metadatos de configuración; no conceden permiso, reserva o admisión. */
    record Disponibilidad(boolean preparacionLocal,boolean solicitud,String motivo) { }
    record Recurso(String tipo,String referencia) { }
    record Solicitud(UUID operacion,Accion accion,Recurso recurso,String versionOrigen,
                     UUID fotoOriginal,String instruccion) { }
    record Preparacion(UUID operacion,Accion accion,Recurso recurso,String versionOrigen,
                       Map<String,Object> utilidadLocal,List<String> pendientes) {
        public Preparacion { utilidadLocal=Map.copyOf(utilidadLocal);pendientes=List.copyOf(pendientes); }
    }
    /** Sólo estado, resultado privado y avisos permitidos; nunca perfil, modelo, tarifa o costo. */
    record Resultado(UUID operacion,String estado,String texto,UUID archivo,List<String> pendientes) {
        public Resultado { pendientes=List.copyOf(pendientes); }
    }
    Preparacion preparar(Solicitud solicitud,Actor actor,ContextoSesion sesion);
    Resultado solicitar(Solicitud solicitud,Actor actor,ContextoSesion sesion);
    Resultado consultar(UUID operacion,Actor actor,ContextoSesion sesion);
    Disponibilidad disponibilidad(Accion accion);
    /** Guarda interna tras contexto actual validado por el consumidor; no reutiliza el SID de creación. */
    void exigirLectura(UUID operacion,Actor actor);
}
