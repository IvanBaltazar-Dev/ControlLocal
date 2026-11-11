package com.controllocal.service.soporte;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Adaptador local sin envío. No imprime correo, token o enlace ni simula entrega. */
@Component
public class NotificadorFueraDeBanda implements NotificadorIdentidad {
    private static final Logger LOG = LoggerFactory.getLogger(NotificadorFueraDeBanda.class);
    @Override public boolean entregaAlTitular() { return false; }
    @Override public void enviar(Proposito proposito, Destino destino, TokenEmitido token) {
        LOG.info("Solicitud de correo de identidad {} registrada; transporte no configurado.", proposito);
    }
}

