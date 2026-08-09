package com.controllocal.service.soporte;

/**
 * Puerto de entrega de tokens de identidad (D-S0-11).
 *
 * <p><b>Es un puerto, no un proveedor.</b> El correo dejo de bloquear S0
 * precisamente porque el diseno de {@code token_acceso} y del canje <b>no
 * cambia</b> con la implementacion: lo unico que se difiere es el
 * <b>transporte</b>.
 *
 * <ul>
 *   <li><b>Hoy</b>: {@link NotificadorFueraDeBanda} — no envia nada. El token
 *       se devuelve <b>una sola vez</b> a quien lo emitio para que lo entregue
 *       por su cuenta. Cubre la operacion normal de una corredora, donde
 *       administrador y agente se conocen.</li>
 *   <li><b>Manana</b>: SMTP contra un relay autenticado, cuando exista
 *       infraestructura productiva y dominio propio. Autohospedar correo esta
 *       descartado por entregabilidad.</li>
 * </ul>
 */
public interface NotificadorIdentidad {

    /** A quien va dirigido. El correo puede ser nulo: muchas personas no lo tienen cargado. */
    record Destino(long idPersona, String nombre, String correo) {
    }

    /**
     * Token recien emitido. <b>Lleva el token en claro</b> porque es el unico
     * instante en el que existe: en la base solo queda su hash.
     */
    record TokenEmitido(String token, java.time.OffsetDateTime expiraEn) {
    }

    /** ¿Se entrego al titular por un canal propio? {@code false} = hay que entregarlo a mano. */
    boolean entregaAlTitular();

    void enviarRecuperacion(Destino destino, TokenEmitido token);

    void enviarInvitacion(Destino destino, TokenEmitido token);
}
