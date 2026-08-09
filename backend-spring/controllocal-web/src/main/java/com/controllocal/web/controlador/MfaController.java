package com.controllocal.web.controlador;

import com.controllocal.service.MfaService;
import com.controllocal.web.dto.MfaDtos.CodigoRequest;
import com.controllocal.web.dto.MfaDtos.CodigosResponse;
import com.controllocal.web.dto.MfaDtos.ElevacionResponse;
import com.controllocal.web.dto.MfaDtos.EnrolamientoResponse;
import com.controllocal.web.dto.MfaDtos.EstadoMfaResponse;
import com.controllocal.web.dto.MfaDtos.ReautenticacionRequest;
import com.controllocal.web.seguridad.IpDelCliente;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/**
 * Segundo factor del <b>propio</b> titular (V37).
 *
 * <p>Ninguna operacion lleva gate de rol y no es un olvido: su alcance es
 * implicito y no discutible — todo sale de la sesion, asi que <b>solo hablan de
 * quien pregunta</b>. Es el mismo criterio de {@code POST /perfil/contrasena} y
 * {@code GET /sesion}.
 *
 * <p>Las respuestas que llevan secretos van con {@code Cache-Control:
 * no-store}: un QR o unos codigos de respaldo en la cache del navegador
 * sobreviven a la sesion que los pidio.
 */
@RestController
@RequestMapping("perfil")
public class MfaController {

    private final MfaService mfa;
    private final IpDelCliente ipDelCliente;

    public MfaController(MfaService mfa, IpDelCliente ipDelCliente) {
        this.mfa = mfa;
        this.ipDelCliente = ipDelCliente;
    }

    @GetMapping("mfa")
    public EstadoMfaResponse estado() {
        return EstadoMfaResponse.desde(mfa.estado(SesionActual.actor()));
    }

    /** Inicia el enrolamiento. El secreto sale de aqui y de ningun otro sitio. */
    @PostMapping("mfa")
    public ResponseEntity<EnrolamientoResponse> iniciar() {
        MfaService.Enrolamiento enrolamiento = mfa.iniciar(SesionActual.actor());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new EnrolamientoResponse(enrolamiento.secretoBase32(), enrolamiento.uri()));
    }

    /**
     * Confirma con el primer codigo y devuelve los codigos de respaldo.
     * <b>Invalida las sesiones vivas</b>: nacieron sin segundo factor, incluida
     * la que hace esta llamada.
     */
    @PostMapping("mfa/confirmar")
    public ResponseEntity<CodigosResponse> confirmar(
            @RequestBody(required = false) CodigoRequest dto) {
        var codigos = mfa.confirmar(SesionActual.actor(), dto == null ? null : dto.codigo());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CodigosResponse(codigos));
    }

    @PostMapping("mfa/codigos")
    public ResponseEntity<CodigosResponse> regenerar(
            @RequestBody(required = false) ReautenticacionRequest dto) {
        var codigos = mfa.regenerarCodigos(SesionActual.actor(),
                contrasenaDe(dto), dto == null ? null : dto.codigo());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CodigosResponse(codigos));
    }

    /** Revocacion del factor propio. Exige contrasena + codigo vigente. */
    @DeleteMapping("mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@RequestBody(required = false) ReautenticacionRequest dto,
                        HttpServletRequest request) {
        mfa.revocarPropio(SesionActual.actor(), contrasenaDe(dto),
                dto == null ? null : dto.codigo(),
                ipDelCliente.de(request), request.getHeader("User-Agent"));
    }

    /**
     * Token de elevacion (D-S0-34). Lo pide el SPA justo antes de una operacion
     * sensible; dura 5 minutos y sirve <b>una vez y para una accion</b>.
     */
    @PostMapping("elevacion")
    public ResponseEntity<ElevacionResponse> elevar(
            @RequestBody(required = false) ReautenticacionRequest dto) {
        var token = mfa.emitirElevacion(SesionActual.actor(), contrasenaDe(dto),
                dto == null ? null : dto.codigo(), "REVOCAR_MFA_AJENO");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ElevacionResponse(token.token(), token.expiraEn()));
    }

    private static char[] contrasenaDe(ReautenticacionRequest dto) {
        return dto == null || dto.contrasena() == null ? null : dto.contrasena().toCharArray();
    }
}
