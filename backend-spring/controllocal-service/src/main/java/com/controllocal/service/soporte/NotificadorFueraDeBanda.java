package com.controllocal.service.soporte;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacion de S0 del puerto {@link NotificadorIdentidad}: <b>no envia
 * nada</b> (D-S0-11).
 *
 * <p>El token viaja de vuelta a quien lo emitio —una sola vez, en la respuesta
 * del endpoint— y esa persona lo entrega por su cuenta. No hay SMTP, no hay
 * proveedor externo y no hay dependencia nueva.
 *
 * <p><b>Consecuencia que hay que tener presente y esta dicha en la matriz:</b>
 * una recuperacion pedida por el propio titular ({@code POST /auth/recuperacion})
 * emite un token que <b>hoy no llega a nadie</b> — responde 202 y deja su
 * evento, pero el titular no puede leerlo. Mientras no exista transporte, el
 * camino que funciona es la <b>invitacion</b>: el gobierno del tenant emite el
 * token y lo entrega. El endpoint publico existe igualmente para que el dia que
 * haya SMTP no haya que tocar ni el contrato ni el esquema.
 *
 * <p><b>Lo que jamas hace:</b> escribir el token en el log. Un token en un log
 * es un token filtrado; la trazabilidad la da {@code evento_seguridad}, que
 * guarda que se emitio, para quien y cuando — nunca el secreto.
 */
@Component
public class NotificadorFueraDeBanda implements NotificadorIdentidad {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorFueraDeBanda.class);

    @Override
    public boolean entregaAlTitular() {
        return false;
    }

    @Override
    public void enviarRecuperacion(Destino destino, TokenEmitido token) {
        registrar("recuperacion", destino, token);
    }

    @Override
    public void enviarInvitacion(Destino destino, TokenEmitido token) {
        registrar("invitacion", destino, token);
    }

    private void registrar(String flujo, Destino destino, TokenEmitido token) {
        LOG.info("Token de {} emitido para la persona {} (caduca {}). Sin transporte configurado: "
                        + "se entrega fuera de banda.",
                flujo, destino.idPersona(), token.expiraEn());
    }
}
