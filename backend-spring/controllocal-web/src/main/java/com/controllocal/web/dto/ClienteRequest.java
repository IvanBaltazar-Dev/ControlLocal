package com.controllocal.web.dto;

import com.controllocal.service.ClienteService;

/**
 * Contrato CONGELADO: espejo exacto de Dtos.ClienteRequest de la v1.
 *
 * <p><b>De la autorizacion (D-27) no entra ningun campo nuevo.</b> Lo unico que
 * el usuario aporta es la casilla, que ya viaja como
 * {@code consentimientoUsoDato}; canal, fecha, actor, tenant, version del
 * aviso, base juridica y finalidad los pone el backend. Hubo un
 * {@code canalAutorizacion} aditivo y se retiro: pedirle al agente que
 * describiera la pantalla en la que ya estaba era friccion sin informacion.
 * Un cliente que todavia lo mande no rompe —Jackson ignora lo que no conoce—,
 * simplemente se descarta.
 *
 * <p>Lo que si diverge de la v1 —a proposito, D-27-b— es que en el ALTA
 * {@code consentimientoUsoDato} pasa a ser obligatorio: sin el no se crea la
 * persona. La v1 la creaba igual con el booleano en false.
 */
public record ClienteRequest(String tipoPersona, String tipoDocumento, String numeroDocumento, String nombre,
                             String telefono, String correo, String rubroComercial,
                             Boolean consentimientoContacto, Boolean consentimientoUsoDato, String estado) {

    public ClienteService.DatosCliente aDatos() {
        return new ClienteService.DatosCliente(tipoPersona, tipoDocumento, numeroDocumento, nombre,
                telefono, correo, rubroComercial, consentimientoContacto, consentimientoUsoDato, estado);
    }
}
