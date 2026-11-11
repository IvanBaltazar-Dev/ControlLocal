package com.controllocal.web.seguridad;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;

/** El principal ya comprobado aporta la sesión; Core posee su identidad canónica. */
public final class ContextoDeActuacion {
    private ContextoDeActuacion() { }
    public static ContextoSesion de(SesionDeRequest sesion) { return ContextoSesion.de(sesion.acceso()); }
    /** Identidad opaca de la operación humana, estable aunque se renueve la sesión. */
    public static com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico correlacionDirecta(String clave) {
        return new com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico("WEB",clave,null);
    }
}
