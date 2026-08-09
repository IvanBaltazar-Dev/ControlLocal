package com.controllocal.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lectura del <b>aviso persistente de gobierno</b> (§11 del diseño de MFA).
 *
 * <h2>Por que NO es una alerta de la campana</h2>
 * La tabla {@code alerta} cuelga siempre de un AGENTE ({@code id_rol_agente
 * NOT NULL}) y su {@code CHECK} de tipos es la lista congelada de dieciséis
 * hechos comerciales. Un {@code TENANT_ADMIN} no tiene rol de agente, asi que
 * un aviso de gobierno no cabe ahi sin tocar una tabla del contrato congelado.
 *
 * <p>Pero la razon de fondo es otra y sobrevive a la migracion que algun dia
 * arregle lo anterior: <b>una alerta de la campana se puede ATENDER</b>, y
 * quien mas interes tiene en hacer desaparecer un «se revoco el segundo factor
 * de X» es precisamente quien lo revoco sin permiso. {@code evento_seguridad}
 * es append-only y tiene un unico escritor, asi que un aviso leido de ahi
 * <b>no se puede silenciar</b>. Persistente aqui significa eso: no se va.
 *
 * <p>Solo lectura, y a proposito: el escritor sigue siendo
 * {@code EventosSeguridad} y nadie mas.
 */
public interface SeguridadService {

    /**
     * Los hechos de <b>gobierno</b>: quien tocó accesos, factores o roles. Se
     * filtra a esta lista y no se devuelve la tabla entera porque un tablero
     * ahogado en {@code LOGIN_OK} deja de ser un aviso y pasa a ser un log —
     * y nadie lee un log a diario.
     */
    List<String> TIPOS_DE_GOBIERNO = List.of(
            "MFA_ACTIVADO", "MFA_REVOCADO", "MFA_CODIGOS_REGENERADOS",
            "MFA_CODIGO_RESPALDO_USADO", "ELEVACION_EMITIDA", "ELEVACION_FALLIDA",
            "CUENTA_BLOQUEADA", "CUENTA_DESBLOQUEADA",
            "CUENTA_ACTIVADA", "CUENTA_DESACTIVADA",
            "ROL_OTORGADO", "ROL_REVOCADO",
            "PASSWORD_RESTABLECIDA", "INVITACION_EMITIDA",
            "ACCESO_TENANT_CONCEDIDO", "ACCESO_TENANT_USADO", "BREAK_GLASS_ACTIVADO");

    /**
     * Un hecho de gobierno, ya legible. Lleva <b>nombres</b> y no solo ids
     * porque un aviso que dice «id 7 revocó el factor de id 12» obliga a
     * investigar para entenderlo, y entonces no avisa de nada.
     *
     * <p><b>Nunca lleva secretos</b>: ni el {@code detalle_json} —que ya se
     * filtra al escribir— ni el agente de usuario entran aqui.
     */
    record AvisoDeGobierno(Long id, OffsetDateTime fecha, String tipo, String resultado,
                           Long idActor, String actor,
                           Long idAfectado, String afectado,
                           String motivo, String ip) {
    }

    /** Solo {@code TENANT_ADMIN}, y solo de su propia organización. */
    Pagina<AvisoDeGobierno> avisosDeGobierno(int pagina, int tamano, Actor actor);

    /**
     * Una cuenta del tenant vista por el gobierno.
     *
     * <p><b>Lleva los dos identificadores.</b> Las fichas comerciales del
     * contrato congelado hablan de {@code persona_rol.id}; las operaciones de
     * acceso hablan de la PERSONA. Publicar la correspondencia aquí es lo que
     * permite ofrecer una acción de gobierno sin añadir campos a
     * {@code AgenteResponse} ni a {@code BrokerResponse}, que están congelados.
     *
     * <p>Lo que <b>no</b> lleva, y no por olvido: nada del secreto ni de los
     * códigos. Solo <i>cuántos</i> quedan — que es lo que permite avisar antes
     * de que alguien se quede sin salida.
     */
    record CuentaDeGobierno(long idPersona, long idRol, String nombre, String usuario,
                            String rolDeGobierno, boolean activa,
                            boolean debeCambiarContrasena, boolean debeEnrolarMfa,
                            boolean mfaActivo, long codigosRespaldoDisponibles) {
    }

    /** Padrón de cuentas del propio tenant. Solo {@code TENANT_ADMIN}. */
    List<CuentaDeGobierno> cuentas(Actor actor);
}
