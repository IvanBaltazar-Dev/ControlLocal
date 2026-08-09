package com.controllocal.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Segundo factor y reautenticacion reforzada (Bloque 6, V37).
 *
 * <h2>Las tres reglas que atraviesan todo</h2>
 * <ol>
 *   <li><b>Nadie enrola el factor de otro.</b> Un administrador puede
 *       <i>revocar</i> el de un companero; enrolarlo es siempre acto del
 *       titular. Es la misma regla que ya gobierna las contrasenas.</li>
 *   <li><b>Validar y consumir son el mismo acto.</b> Un codigo TOTP se sella
 *       en la operacion que lo acepta (D-S0-31); quien valide sin sellar deja
 *       el codigo reutilizable durante su ventana.</li>
 *   <li><b>Una sesion abierta no basta</b> para tocar el factor (D-S0-34).
 *       Cambiarlo o revocarlo exige probar el factor vigente, porque si
 *       bastara la sesion, robarla equivaldria a quedarse con la cuenta.</li>
 * </ol>
 *
 * <p>El <b>estado de MFA nunca viaja en el token</b> (R1): ni el secreto, ni un
 * indicador que permita derivarlo, ni "se autentico con MFA hace N minutos" —
 * eso ultimo es lo que hace falta el token de elevacion.
 */
public interface MfaService {

    /** Lo que ve el titular de su propio factor. Nunca incluye el secreto. */
    record EstadoFactor(boolean activo, boolean debeEnrolar,
                        long codigosDisponibles, OffsetDateTime activadoEn) {

        /** Con dos o menos conviene avisar antes de que se acaben. */
        public boolean codigosPorAgotarse() {
            return activo && codigosDisponibles <= 2;
        }
    }

    /**
     * Secreto y URI del QR. <b>Es la unica vez que el secreto sale del
     * servidor</b>, y por eso la respuesta que lo lleva va con
     * {@code Cache-Control: no-store} y no existe ningun endpoint que lo relea.
     */
    record Enrolamiento(String secretoBase32, String uri) {
    }

    /** Desafio del segundo paso del login. No autoriza nada por si mismo. */
    record Desafio(String token, OffsetDateTime expiraEn) {
    }

    /** Quien resulto autenticado tras el segundo paso. */
    record Verificacion(long idPersona, boolean porCodigoRespaldo, long codigosRestantes) {
    }

    EstadoFactor estado(Actor actor);

    /**
     * Crea el factor en {@code PENDIENTE}. Caduca a los 15 minutos si no se
     * confirma: sin caducidad quedan secretos a medio enrolar que nadie sabe
     * si valen.
     */
    Enrolamiento iniciar(Actor actor);

    /**
     * Activa el factor y devuelve los codigos de respaldo <b>una sola vez</b>.
     *
     * <p>Son cuatro efectos y no deben separarse: pasar a {@code ACTIVO},
     * generar los codigos, apagar {@code debe_enrolar_mfa} e <b>invalidar las
     * sesiones vivas</b> —que nacieron sin segundo factor—. Si ademas es el
     * primer administrador de la organizacion, enciende
     * {@code mfa_gobierno_exigido}.
     */
    List<String> confirmar(Actor actor, String codigo);

    /** Regenera los codigos e <b>invalida todos los anteriores</b>, usados o no. */
    List<String> regenerarCodigos(Actor actor, char[] contrasena, String codigo);

    /** Revocacion del factor propio. Exige contrasena + TOTP vigente. */
    void revocarPropio(Actor actor, char[] contrasena, String codigo, String ip, String agenteUsuario);

    /**
     * Nivel 2 de la recuperacion: un {@code TENANT_ADMIN} <b>revoca</b> el
     * factor de otra persona de su tenant. No lo fija ni lo ve: la cuenta queda
     * sin factor y obligada a enrolar.
     *
     * <p>Reglas completas: un BROKER no revoca el de nadie, ni el de sus
     * agentes; nadie se lo revoca a si mismo por aqui; y si el afectado es el
     * <b>ultimo administrador operativo</b>, se rechaza — para eso esta el
     * nivel 3, que no se construye en V37.
     */
    void revocarAjeno(Actor actor, long idPersona, String tokenElevacion,
                      String motivo, String ip, String agenteUsuario);

    /**
     * Revocacion aplicada por la <b>concesion de recuperacion</b> (V38, nivel 3).
     *
     * <p>Sin {@code Actor} porque <b>no hay sesion</b>: la concesion no emite
     * token y no hay nadie dentro. Y <b>sin</b> la guarda de gobierno operativo
     * a proposito — esa guarda es justo la que no se puede satisfacer cuando el
     * tenant se quedo sin ningun administrador, que es la situacion que este
     * camino viene a resolver.
     *
     * <p>Vive aqui, y no en el service de recuperacion, porque
     * {@code MfaServiceImpl} es el <b>unico punto de escritura</b> de
     * {@code factor_autenticacion}. Un segundo sitio que revoque factores es
     * como se acaba con dos revocaciones que hacen cosas distintas.
     *
     * @return {@code true} si algo cambio; {@code false} si la cuenta ya estaba
     *         sin factor y ya obligada a enrolar — la accion es idempotente.
     */
    boolean revocarPorRecuperacion(long idOrganizacion, long idPersona);

    /**
     * Token de elevacion (D-S0-34): 5 minutos, un solo uso, ligado a
     * credencial + tenant + <b>accion concreta</b>.
     *
     * <p>Existe porque el token de sesion esta congelado y <b>no lleva</b>
     * cuando se probo el segundo factor. Inferir "MFA reciente" de que la
     * sesion nacio con MFA hace horas seria falso: una sesion robada a media
     * tarde pasaria el control.
     */
    ContrasenaService.TokenEntregado emitirElevacion(Actor actor, char[] contrasena,
                                                     String codigo, String accion);

    // ------------------------------------------------------------- login

    /** ¿Esta cuenta tiene segundo factor activo? Decide si el login es de dos pasos. */
    boolean exigeSegundoFactor(long idOrganizacion, long idPersona);

    /** Emite el desafio del segundo paso. Mata el desafio anterior de esa cuenta. */
    Desafio emitirDesafio(long idOrganizacion, long idPersona);

    /**
     * Canjea el desafio con un codigo TOTP <b>o</b> un codigo de respaldo.
     *
     * <p>Consumir un codigo de respaldo <b>no desactiva el MFA</b>: solo deja
     * entrar. Si lo desactivara, cada codigo seria una llave maestra de un solo
     * uso.
     */
    Verificacion verificarDesafio(String desafio, String codigo, String ip, String agenteUsuario);
}
