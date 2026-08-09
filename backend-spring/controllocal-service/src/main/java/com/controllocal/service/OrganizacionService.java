package com.controllocal.service;

/**
 * Resuelve el tenant de la sesion (D-20). Existe para que la organizacion la
 * ponga SIEMPRE el backend: el cliente no la envia y la BD no la pone por
 * DEFAULT, asi que este es el unico origen valido del {@code idOrganizacion}
 * de un {@link Actor}.
 */
public interface OrganizacionService {

    /**
     * Organizacion en cuyo nombre se atiende el request.
     *
     * <p>En V6 la plataforma es mono-tenant: siempre devuelve la organizacion
     * de legado ({@code BROX_LEGACY}). Cuando se habilite multi-tenant real
     * (post-corte de GlassFish, con el token ya descongelado) pasara a
     * derivarse de la membresia {@code usuario_organizacion} de la cuenta
     * autenticada, sin que cambie ningun llamador.
     *
     * @throws IllegalStateException si el tenant de legado no existe (la
     *         migracion V6 no se aplico): mejor fallar claro que operar sin
     *         frontera organizacional.
     */
    long idOrganizacionActual();
}
