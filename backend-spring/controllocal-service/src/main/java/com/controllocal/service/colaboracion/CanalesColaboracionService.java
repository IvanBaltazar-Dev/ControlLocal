package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.LadoColaboracion;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.util.Optional;

/** Declaración privada del titular para este alcance; no envía ni revela el canal a la contraparte. */
public interface CanalesColaboracionService {
    record DatosDeclaracion(String solicitud,LadoColaboracion lado,Long versionSolicitud,
                            Long versionAlcance,Long versionRecurso,String canalComercial) { }
    record Declaracion(String referencia,String solicitud,LadoColaboracion lado,long versionSolicitud,
                       long versionAlcance,long versionRecurso,String canalComercial,Instant declaradaEn) { }
    DatosDeclaracion validarDeclaracion(DatosDeclaracion datos,Actor actor);
    Declaracion declarar(DatosDeclaracion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<Declaracion> consultarResultado(String clave,Actor actor);
    Optional<Declaracion> consultarDeclaracionPropia(String solicitud,LadoColaboracion lado,Actor actor);
}
