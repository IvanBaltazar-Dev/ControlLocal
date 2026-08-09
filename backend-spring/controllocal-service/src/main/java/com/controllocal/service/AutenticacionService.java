package com.controllocal.service;

/**
 * Caso de uso de autenticacion sobre el modelo Party-Role.
 */
public interface AutenticacionService {

    /**
     * Identidad resuelta tras autenticar. Mapea al contrato congelado asi:
     * <ul>
     *   <li>{@code idUsuario} = persona.id — identidad UNICA del actor (Doc 5 §3);
     *       reemplaza al id_usuario_interno de la v1.</li>
     *   <li>{@code idDominio} = persona_rol.id del rol operativo (BROKER/AGENTE);
     *       reemplaza a id_broker/id_agente de la v1.</li>
     *   <li>{@code rol} = banda del cable: ADMIN | BROKER | AGENTE.</li>
     * </ul>
     */
    record SesionAutenticada(long idUsuario, long idDominio, String rol, String nombre, String nombreUsuario) {
    }

    /**
     * Valida credenciales y resuelve la identidad operativa.
     * Lanza CredencialesInvalidasException ante cualquier rechazo (sin
     * distinguir usuario inexistente de clave erronea).
     */
    SesionAutenticada autenticar(String nombreUsuario, char[] contrasena);

    /**
     * Identidad de una persona <b>ya autenticada por otro camino</b>: hoy, el
     * segundo paso del login con MFA (D-S0-22).
     *
     * <p><b>No comprueba credenciales y no debe llamarse sin haberlas
     * comprobado antes.</b> Existe porque el desafio ya probo la contrasena en
     * el paso 1 y el codigo en el paso 2; volver a pedir la clave para armar la
     * sesion obligaria a guardarla entre pasos, que es justo lo que no se hace.
     */
    SesionAutenticada identidadDe(long idOrganizacion, long idPersona);

    /**
     * Lo que el filtro JWT comprueba en cada request autenticado, en una sola
     * lectura: si las sesiones de esa persona fueron invalidadas (D-S0-12) y si
     * su cuenta esta capada por contrasena temporal (§4.5).
     * <p>
     * Devuelve {@link EstadoDeAcceso#SIN_RESTRICCIONES} cuando la persona no
     * tiene credencial en el tenant, que es lo mismo que "nada que restringir".
     *
     * @param idPersona {@code persona.id}, que es lo que el token lleva como
     *                  {@code idUsuario}
     */
    EstadoDeAcceso estadoDeAcceso(long idOrganizacion, long idPersona);

    /**
     * Logout real: <b>invalida TODAS las sesiones vivas de esa persona</b>, no
     * solo la del navegador que llama. Sesiones individuales exigirian un
     * {@code jti} que no cabe en el token congelado, y prometer lo contrario
     * seria peor que decirlo.
     * <p>
     * Idempotente: llamarlo dos veces solo adelanta la marca.
     *
     * @return {@code false} si esa persona no tiene credencial en el tenant
     */
    boolean invalidarSesiones(long idOrganizacion, long idPersona);
}
