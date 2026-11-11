package com.controllocal.service;

import java.time.Instant;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;

/** CAT-E01 / RF-002 / AC-024–025. Una invitación de pertenencia nunca entrega credenciales. */
public interface InvitacionesMembresiaService {
    /** Sólo datos profesionales de la organización; correo laboral no recupera la cuenta BROX. */
    record PersonaLocal(Long idPersona, String nombre, String tipoPersona, String tipoDocumento,
                        String numeroDocumento, String telefono, String correoLaboral) { }

    /** La cuenta destinataria y sus credenciales no se crean ni se modifican desde gobierno. */
    record DatosInvitacion(String correoCuenta, PersonaLocal persona, String rolPrincipal,
                           boolean administrar, Long idBrokerSupervisor, String codigoInterno,
                           String zona, String estadoOperativo, Instant venceEn, String motivo) { }

    record InvitacionEmitida(String referencia, Instant venceEn, String rolPrincipal,
                             boolean administrar) { }
    record PertenenciaAceptada(String referencia, long idOrganizacion, long idMembresia,
                               long idPersona, Long idRolOperativo, String rolPrincipal,
                               boolean administrar) { }
    record InvitacionRetirada(String referencia, Instant retiradaEn) { }

    InvitacionEmitida emitir(DatosInvitacion datos, Actor actor, ContextoSesion sesion, String claveComando);

    /** La identidad procede de la sesión comprobada. La referencia sólo selecciona la invitación. */
    PertenenciaAceptada aceptar(String referencia, String sesion, long revision);

    /** Gobierno actual de la organización retira la invitación, sin borrar su historia. */
    InvitacionRetirada retirar(String referencia, String motivo, Actor actor, ContextoSesion sesion);
}
