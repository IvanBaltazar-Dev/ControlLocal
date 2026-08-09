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
     * (identidad unica) + rol con el que firma + <b>banda efectiva</b>.
     *
     * <p>La banda sale de {@code SesionDeRequest}, no de {@code token().rol()}:
     * el token dice {@code ADMIN} por compatibilidad con GlassFish (R1) y esa
     * banda heredada no debe llegar a ningun caso de uso.
     */
    public static Actor actor() {
        SesionDeRequest sesion = sesion();
        return new Actor(sesion.idOrganizacion(), sesion.token().idUsuario(),
                sesion.token().idDominio(), sesion.rolEfectivo());
    }
}
