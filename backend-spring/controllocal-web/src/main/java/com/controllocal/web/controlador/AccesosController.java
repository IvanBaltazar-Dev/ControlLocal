package com.controllocal.web.controlador;

import com.controllocal.service.ContrasenaService;
import com.controllocal.web.dto.ContrasenaDtos.ContrasenaTemporalResponse;
import com.controllocal.web.dto.ContrasenaDtos.InvitacionResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Devolver el acceso a alguien: invitacion y contrasena temporal (§4.4).
 * <b>Todo aditivo</b> — la v1 no tiene ninguna de las dos.
 *
 * <h2>Por que un recurso propio y no un metodo mas en agentes y brokers</h2>
 * Porque el titular del permiso es distinto. {@code AgentesController} y
 * {@code BrokersController} son {@code BROKER + ADMIN}; esto es <b>solo
 * ADMIN</b>, y meterlo ahi dentro obligaria a repetir la excepcion en cada
 * metodo hasta que alguien se la olvide. Ademas la operacion no es "sobre un
 * agente" ni "sobre un broker": es sobre una <b>cuenta</b>, y el mismo endpoint
 * sirve para las dos.
 *
 * <h2>D-S0-18: invitar es gobierno, no supervision</h2>
 * Un broker ordinario <b>no invita, no activa y no suspende</b> usuarios, ni
 * siquiera de su propio equipo. Hoy ese gobierno lo ejerce el rol
 * <b>ADMIN</b>, que ya es el rol de tenant: no opera el flujo comercial, solo
 * administra usuarios y lee. Cuando el Bloque 5 parta ADMIN en
 * {@code TENANT_ADMIN}, estas dos filas cambian de titular en la matriz y
 * <b>nada mas</b> — ni el esquema ni el contrato se mueven.
 *
 * <h2>La regla que ninguna de las dos rompe</h2>
 * El administrador <b>nunca ve, fija ni recupera la contrasena de otra
 * persona</b>. Con la invitacion, el titular define su clave al canjear. Con
 * la temporal, la clave la <b>genera el sistema</b> —el administrador no la
 * elige— y nace obligada a cambiarse en el primer ingreso. Eso es lo que
 * evita el patron "el jefe conoce la clave del empleado", que es exactamente
 * lo que hoy hace inevitable el seed compartido (H-16).
 */
@RestController
@RequestMapping("accesos")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AccesosController {

    private final ContrasenaService contrasenas;

    private final com.controllocal.service.MfaService mfa;
    private final com.controllocal.service.SeguridadService seguridad;
    private final com.controllocal.web.seguridad.IpDelCliente ipDelCliente;

    public AccesosController(ContrasenaService contrasenas,
                             com.controllocal.service.MfaService mfa,
                             com.controllocal.service.SeguridadService seguridad,
                             com.controllocal.web.seguridad.IpDelCliente ipDelCliente) {
        this.mfa = mfa;
        this.seguridad = seguridad;
        this.ipDelCliente = ipDelCliente;
        this.contrasenas = contrasenas;
    }

    /**
     * <b>Padron de cuentas del tenant.</b> Aditivo (V37).
     *
     * <p>Existe por una razon concreta: las fichas comerciales del contrato
     * congelado identifican a agentes y brokers por {@code persona_rol.id}, y
     * todas las operaciones de este recurso hablan de la <b>persona</b>. Sin un
     * sitio que publique la correspondencia, el SPA no puede ofrecer ninguna
     * accion de gobierno sobre alguien que ve en una ficha — y la alternativa
     * era anadir {@code idPersona} a {@code AgenteResponse} y
     * {@code BrokerResponse}, que estan congelados.
     *
     * <p>De paso responde lo que hay que saber ANTES de tocar un acceso: si la
     * cuenta esta activa, si tiene segundo factor y cuantos codigos le quedan.
     * Nunca el secreto, nunca los codigos.
     */
    @org.springframework.web.bind.annotation.GetMapping
    public java.util.List<com.controllocal.service.SeguridadService.CuentaDeGobierno> cuentas() {
        return seguridad.cuentas(SesionActual.actor());
    }

    /**
     * Emite una invitacion para {@code idPersona}. Devuelve el token
     * <b>una sola vez</b>: no hay ningun endpoint que lo vuelva a mostrar
     * porque en la base solo queda su hash.
     *
     * <p>Emitir una invitacion nueva <b>invalida la anterior</b> de esa cuenta.
     */
    @PostMapping("{idPersona}/invitacion")
    public InvitacionResponse invitar(@PathVariable long idPersona,
                                      @RequestBody(required = false) Map<String, String> cuerpo) {
        var entregado = contrasenas.emitirInvitacion(
                SesionActual.actor(), idPersona, motivoDe(cuerpo));
        return new InvitacionResponse(entregado.token(), entregado.expiraEn(),
                entregado.entregadoAlTitular());
    }

    /**
     * Genera una contrasena temporal para {@code idPersona} y la devuelve
     * <b>una sola vez</b>. La cuenta queda con cambio obligatorio y sus
     * sesiones vivas mueren en el acto.
     */
    @PostMapping("{idPersona}/contrasena-temporal")
    public ContrasenaTemporalResponse contrasenaTemporal(
            @PathVariable long idPersona,
            @RequestBody(required = false) Map<String, String> cuerpo) {
        var temporal = contrasenas.emitirContrasenaTemporal(
                SesionActual.actor(), idPersona, motivoDe(cuerpo));
        return new ContrasenaTemporalResponse(
                temporal.nombreUsuario(), temporal.contrasenaTemporal(), true);
    }

    /**
     * <b>Nivel 2 de la recuperacion del segundo factor (V37).</b> Un
     * {@code TENANT_ADMIN} <b>revoca</b> el factor de otra persona de su
     * tenant: no lo ve ni lo fija — la cuenta queda sin factor y obligada a
     * enrolar, y el titular lo enrola el mismo.
     *
     * <p>Exige un <b>token de elevacion</b> en la cabecera (D-S0-34): quien
     * repone un factor ajeno debe haber probado el suyo hace minutos, no haber
     * abierto sesion esta manana. El motivo es <b>obligatorio</b> aqui, a
     * diferencia de la invitacion: sin motivo escrito, una revocacion legitima
     * y el primer movimiento de un atacante son la misma fila.
     *
     * <p>Si el afectado es el <b>ultimo administrador operativo</b>, se
     * rechaza: esa recuperacion es del nivel 3, que no se construye en V37.
     */
    @DeleteMapping("{idPersona}/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarMfa(@PathVariable long idPersona,
                           @RequestHeader(value = "X-Elevacion", required = false) String elevacion,
                           @RequestBody(required = false) Map<String, String> cuerpo,
                           HttpServletRequest request) {
        mfa.revocarAjeno(SesionActual.actor(), idPersona, elevacion,
                cuerpo == null ? null : cuerpo.get("motivo"),
                ipDelCliente.de(request), request.getHeader("User-Agent"));
    }

    /**
     * El motivo es opcional pero se pide: en una auditoria de accesos, "quien
     * devolvio el acceso a quien" sin el porque no responde la unica pregunta
     * que se hace despues de un incidente.
     */
    private static String motivoDe(Map<String, String> cuerpo) {
        String motivo = cuerpo == null ? null : cuerpo.get("motivo");
        return motivo == null || motivo.isBlank() ? "sin motivo declarado" : motivo.trim();
    }
}
