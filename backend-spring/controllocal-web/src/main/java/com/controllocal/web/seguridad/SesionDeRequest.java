package com.controllocal.web.seguridad;

/**
 * Principal publicado por {@link FiltroAutenticacionJwt}: JWT V2 contrastado con
 * la sesión Core actual y su contexto. IDENTIDAD carece de ámbito comercial.
 * La persona local del contexto se distingue de la identidad global de acceso.
 */
public record SesionDeRequest(TokenService.Sesion token, com.controllocal.service.ContextosService.SesionAccesoDTO acceso) {
    public long idOrganizacion() { return acceso.activo() == null ? 0 : acceso.activo().idOrganizacion(); }
    public String rolEfectivo() { return acceso.activo() == null ? "IDENTIDAD" : acceso.activo().rol(); }
    public long idCuenta() { return acceso.identidad().idCuenta(); }
}
