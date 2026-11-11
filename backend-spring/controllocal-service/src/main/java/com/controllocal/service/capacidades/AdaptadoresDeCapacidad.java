package com.controllocal.service.capacidades;

import com.controllocal.service.Actor;

/** Puerto de la puerta pública actual: reutiliza la misma seguridad de método, sin invocar el caso. */
public interface AdaptadoresDeCapacidad {
    record Disponibilidad(boolean reconocido, boolean permitido, boolean habilitado) { }
    Disponibilidad de(String idCore, Actor actor);
}
