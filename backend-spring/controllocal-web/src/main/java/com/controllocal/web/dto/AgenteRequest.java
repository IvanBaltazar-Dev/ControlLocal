package com.controllocal.web.dto;

import com.controllocal.service.AgenteService;

/**
 * Contrato congelado de {@code AgentesRest} <b>mas</b> {@code idBrokerSupervisor}
 * (D-S0-17, fila 17).
 *
 * <p>El campo es nuevo y no rompe el cable: es aditivo y ningun consumidor
 * existente lo enviaba. Hace falta porque el alta de agentes cambio de dueno.
 * Antes la creaba un BROKER y el supervisor inicial se deducia de la sesion —
 * el agente quedaba bajo quien lo daba de alta—; ahora la crea el
 * {@code TENANT_ADMIN}, que no supervisa a nadie, asi que el supervisor
 * <b>tiene que venir en la peticion</b>. Sin el, un agente nacería sin
 * supervisor y fuera del alcance de cualquier broker.
 */
public record AgenteRequest(String nombre, String tipoPersona, String tipoDocumento,
                            String numeroDocumento, String telefono, String correo,
                            String usuario, String contrasena, String zona,
                            String codigoAgente, String estado,
                            String estadoOperativo, Long idBrokerSupervisor) {

    public AgenteService.DatosAgente aDatos() {
        return new AgenteService.DatosAgente(nombre, tipoPersona, tipoDocumento,
                numeroDocumento, telefono, correo, usuario, contrasena, zona,
                codigoAgente, estado, estadoOperativo, idBrokerSupervisor);
    }
}
