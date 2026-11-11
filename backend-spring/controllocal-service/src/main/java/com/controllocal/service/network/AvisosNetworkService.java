package com.controllocal.service.network;

import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import java.time.Instant;
import java.util.Set;

/** Bandeja privada de resultados Network; no envía mensajes ni ejecuta tareas. */
public interface AvisosNetworkService {
    record Aviso(long idEvento,String tipo,Instant ocurridoEn,long idEncargo,String codigoEncargo,
                 String participacion,String solicitud,String accion,String estadoSolicitud,
                 String estadoParticipacion,Long versionProyeccion,String motivo,Set<String> causas) { }
    Pagina<Aviso> consultar(Integer pagina,Integer tamano,Actor actor,ContextoSesion sesion);
}
