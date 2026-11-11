package com.controllocal.web.seguridad;

import com.controllocal.service.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso a la sesion del request (el principal que publica
 * {@link FiltroAutenticacionJwt}). Solo tiene sentido en endpoints
 * protegidos: si no hay sesion, el filtro ya corto con 401.
 */
public final class SesionActual {

    private SesionActual() {
    }

    public static SesionDeRequest sesion() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof SesionDeRequest sesion) {
            return sesion;
        }
        throw new IllegalStateException("No hay una sesion autenticada en el request.");
    }

    /**
     * Actor para los casos de uso: organizacion (frontera de tenant) + persona
     * (identidad local) + rol con el que firma + atribución seleccionada en Core.
     * La cuenta global no se utiliza como persona ni como rol operativo.
     */
    public static Actor actor() {
        SesionDeRequest sesion = sesion();
        if (sesion.idOrganizacion() <= 0 || "IDENTIDAD".equals(sesion.rolEfectivo())) {
            throw new com.controllocal.service.excepcion.ConflictoException("Selecciona un contexto autorizado antes de operar.");
        }
        return new Actor(sesion.idOrganizacion(), sesion.token().idUsuario(),
                sesion.token().idDominio(), sesion.rolEfectivo());
    }

    public static long idCuenta() { return sesion().idCuenta(); }
}
