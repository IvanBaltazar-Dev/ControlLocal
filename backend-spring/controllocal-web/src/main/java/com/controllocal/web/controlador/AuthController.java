package com.controllocal.web.controlador;

import com.controllocal.service.ContextosService;
import com.controllocal.service.CuentaService;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.BloqueoAccesos;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.web.dto.ContrasenaDtos.CanjeRequest;
import com.controllocal.web.dto.ContrasenaDtos.RecuperacionRequest;
import com.controllocal.web.dto.LoginRequest;
import com.controllocal.web.dto.LoginResponse;
import com.controllocal.web.dto.CuentaDtos.*;
import com.controllocal.web.dto.MfaDtos.DesafioRequest;
import com.controllocal.web.dto.MfaDtos.DesafioResponse;
import com.controllocal.web.dto.MfaDtos.VerificacionRequest;
import com.controllocal.web.http.DemasiadasSolicitudesException;
import com.controllocal.web.seguridad.EmisionDeSesion;
import com.controllocal.web.seguridad.IpDelCliente;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.Map;

/** RF-001 / CAT-E01: prueba de identidad global, sin seleccionar organización por defecto. */
@RestController
@RequestMapping("auth")
public class AuthController {
    private final ContextosService contextos;
    private final EmisionDeSesion emision;
    private final BloqueoAccesos bloqueo;
    private final EventosSeguridad auditoria;
    private final IpDelCliente ipDelCliente;
    private final ContrasenaService contrasenas;
    private final CuentaService cuentas;

    public AuthController(ContextosService contextos, EmisionDeSesion emision,
                          BloqueoAccesos bloqueo, EventosSeguridad auditoria,
                          IpDelCliente ipDelCliente, ContrasenaService contrasenas, CuentaService cuentas) {
        this.contextos = contextos; this.emision = emision; this.bloqueo = bloqueo;
        this.auditoria = auditoria; this.ipDelCliente = ipDelCliente;
        this.contrasenas = contrasenas;
        this.cuentas = cuentas;
    }

    @PostMapping("login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest dto, HttpServletRequest request) {
        String usuario = dto == null ? null : dto.correo();
        String ip = ipDelCliente.de(request), agente = request.getHeader("User-Agent");
        comprobarCupo(usuario, ip, agente);
        char[] clave = clave(dto == null ? null : dto.contrasena());
        try {
            var acceso = contextos.iniciarAcceso(usuario, clave, false);
            registrarAcierto(usuario, ip, agente, acceso.sesion().identidad().idCuenta());
            return emision.emitir(acceso.sesion());
        } catch (CredencialesInvalidasException error) {
            registrarFallo(usuario, ip, agente); throw error;
        } finally { Arrays.fill(clave, '\0'); }
    }

    /** El desafío no es una sesión ni una autorización de negocio. */
    @PostMapping("mfa/desafio")
    public ResponseEntity<Object> desafio(@RequestBody(required = false) DesafioRequest dto, HttpServletRequest request) {
        String usuario = dto == null ? null : dto.correo();
        String ip = ipDelCliente.de(request), agente = request.getHeader("User-Agent");
        comprobarCupo(usuario, ip, agente);
        char[] clave = clave(dto == null ? null : dto.contrasena());
        try {
            var acceso = contextos.iniciarAcceso(usuario, clave, true);
            bloqueo.registrar(usuario, ip, true, null, agente);
            if (acceso.sesion() != null) {
                auditoria.registrar("LOGIN_OK", "OK", identidadAuditada(acceso.sesion().identidad().idCuenta(), ip, agente));
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(emision.emitir(acceso.sesion()));
            }
            var reto = acceso.desafio();
            return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                    .body(new DesafioResponse(reto.token(), reto.expiraEn(), "TOTP"));
        } catch (CredencialesInvalidasException error) {
            registrarFallo(usuario, ip, agente); throw error;
        } finally { Arrays.fill(clave, '\0'); }
    }

    @PostMapping("mfa/verificar")
    public ResponseEntity<LoginResponse> verificar(@RequestBody(required = false) VerificacionRequest dto,
                                                   HttpServletRequest request) {
        var sesion = contextos.completarSegundoFactor(dto == null ? null : dto.desafio(),
                dto == null ? null : dto.codigo(), ipDelCliente.de(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(emision.emitir(sesion));
    }

    /** Conserva el cierre global de sesiones de la cuenta, incluso sin contexto elegido. */
    @PostMapping("logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var s = SesionActual.sesion();
        contextos.cerrar(s.token().referenciaSesion(), s.token().revisionContexto());
        var auditado = identidadAuditada(s.idCuenta(), ipDelCliente.de(request), request.getHeader("User-Agent"));
        auditoria.registrar("LOGOUT", "OK", auditado);
        auditoria.registrar("SESIONES_INVALIDADAS", "OK", auditado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("renovar")
    public ResponseEntity<LoginResponse> renovar() {
        var t = SesionActual.sesion().token();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(emision.emitir(contextos.renovar(t.referenciaSesion(), t.revisionContexto())));
    }

    /** Respuesta uniforme sin revelar existencia ni organización de la cuenta. */
    @PostMapping("recuperacion")
    public ResponseEntity<Void> recuperacion(@RequestBody(required = false) RecuperacionRequest dto,
                                             HttpServletRequest request) {
        String ip = ipDelCliente.de(request), agente = request.getHeader("User-Agent");
        var v = bloqueo.permitir(null, ip);
        if (v.bloqueado()) throw new DemasiadasSolicitudesException(BloqueoAccesos.esperaSegundos(v.fallos()));
        bloqueo.registrarSoloIp(ip, false, null, agente);
        contrasenas.solicitarRecuperacion(dto == null ? null : dto.correo(), ip, agente);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("recuperacion/canje")
    public ResponseEntity<Void> canjearRecuperacion(@RequestBody(required = false) CanjeRequest dto,
                                                   HttpServletRequest request) {
        if (dto == null) throw new ReglaNegocioException("El token es obligatorio.");
        char[] nueva = clave(dto.contrasenaNueva());
        try {
            contrasenas.canjear(dto.token(), nueva, ipDelCliente.de(request), request.getHeader("User-Agent"));
            return ResponseEntity.noContent().build();
        } finally { Arrays.fill(nueva, '\0'); }
    }

    /** El acuse público no entrega el enlace ni confirma la existencia de otra cuenta. */
    @PostMapping("alta")
    public ResponseEntity<Void> solicitarAlta(@RequestBody(required=false) AltaRequest dto,HttpServletRequest request) {
        String ip=ipDelCliente.de(request),agente=request.getHeader("User-Agent");
        var v=bloqueo.permitir(null,ip);
        if(v.bloqueado()) throw new DemasiadasSolicitudesException(BloqueoAccesos.esperaSegundos(v.fallos()));
        bloqueo.registrarSoloIp(ip,false,null,agente);
        cuentas.solicitarAlta(dto==null ? null : dto.correo());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("alta/confirmar")
    public ResponseEntity<Void> confirmarAlta(@RequestBody(required=false) ConfirmacionAltaRequest dto) {
        if(dto==null) throw new ReglaNegocioException("El enlace y los datos de la cuenta son obligatorios.");
        char[] p=clave(dto.contrasena());
        try {
            cuentas.confirmarAlta(dto.token(),dto.nombreUsuario(),dto.nombreProfesionalDeclarado(),p);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {Arrays.fill(p,'\0');}
    }

    @PostMapping("correo/confirmar")
    public ResponseEntity<Void> confirmarCorreo(@RequestBody(required=false) ConfirmacionCorreoRequest dto) {
        cuentas.confirmarCambioCorreo(dto==null ? null : dto.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static char[] clave(String valor) { return valor == null ? new char[0] : valor.toCharArray(); }
    private static EventosSeguridad.Contexto identidadAuditada(long cuenta, String ip, String agente) {
        return new EventosSeguridad.Contexto(null, null, cuenta, null, ip, agente);
    }
    private void comprobarCupo(String usuario, String ip, String agente) {
        var v = bloqueo.permitir(usuario, ip);
        if (!v.bloqueado()) return;
        auditoria.registrar("LOGIN_BLOQUEADO_429", "BLOQUEADO", EventosSeguridad.Contexto.anonimo(null, ip, agente),
                "bloqueado por " + v.dimension(), null, Map.of("fallos", v.fallos()));
        throw new DemasiadasSolicitudesException(BloqueoAccesos.esperaSegundos(v.fallos()));
    }
    private void registrarAcierto(String usuario, String ip, String agente, long cuenta) {
        bloqueo.registrar(usuario, ip, true, null, agente);
        auditoria.registrar("LOGIN_OK", "OK", identidadAuditada(cuenta, ip, agente));
    }
    private void registrarFallo(String usuario, String ip, String agente) {
        bloqueo.registrar(usuario, ip, false, null, agente);
        var contexto = EventosSeguridad.Contexto.anonimo(null, ip, agente);
        auditoria.registrar("LOGIN_FALLIDO", "FALLO", contexto);
        var v = bloqueo.permitir(usuario, ip);
        if (v.exigeDesbloqueoAdministrativo()) auditoria.registrar("CUENTA_BLOQUEADA", "BLOQUEADO", contexto,
                "umbral administrativo por " + v.dimension(), null, Map.of("fallos", v.fallos()));
    }
}
