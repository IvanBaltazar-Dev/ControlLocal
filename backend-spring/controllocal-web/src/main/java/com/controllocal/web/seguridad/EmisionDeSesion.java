package com.controllocal.web.seguridad;

import com.controllocal.service.ContextosService;
import com.controllocal.web.dto.LoginResponse;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

/** Proyección HTTP de la sesión creada por Core; no deduce atribuciones. */
@Component
public class EmisionDeSesion {
    private final TokenService tokens;
    private final ContextosService contextos;
    public EmisionDeSesion(TokenService tokens, ContextosService contextos) {
        this.tokens = tokens; this.contextos = contextos;
    }
    public LoginResponse emitir(ContextosService.SesionAccesoDTO s) {
        var t = tokens.de(s);
        var c = s.activo();
        return new LoginResponse(tokens.firmar(t), Math.max(0, Duration.between(Instant.now(), s.venceEn()).getSeconds()),
                t.rol(), t.idUsuario(), t.idDominio(), s.identidad().nombreProfesionalDeclarado(), s.identidad().nombreUsuario(),
                s.venceEn(), c == null,
                contextos.contextos(s.referencia(), s.revision()), c == null ? 0 : c.idOrganizacion(),
                s.identidad().idCuenta(), s.revision(), c == null ? null : c.modalidad());
    }
}
