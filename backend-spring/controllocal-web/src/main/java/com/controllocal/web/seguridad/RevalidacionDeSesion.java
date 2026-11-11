package com.controllocal.web.seguridad;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Misma resolución en filtro y entregas privadas; ninguna decisión se conserva en caché. */
@Component
public class RevalidacionDeSesion {
    private final ContextosService contextos;
    public RevalidacionDeSesion(ContextosService contextos) { this.contextos = contextos; }

    public SesionDeRequest comprobar(TokenService.Sesion token) {
        var actual = contextos.comprobarSesion(token.referenciaSesion(), token.revisionContexto(),
                token.emitidoEn(), token.expiraEn());
        var esperado = new TokenService.Sesion(actual.identidad().nombreUsuario(),
                actual.activo() == null ? "IDENTIDAD" : actual.activo().rol(),
                actual.activo() == null ? 0 : actual.activo().idPersona(),
                actual.activo() == null ? 0 : actual.activo().idRolOperativo(),
                actual.emitidaEn(), actual.venceEn(), actual.referencia(), actual.revision());
        if (!esperado.equals(token)) throw new CredencialesInvalidasException();
        return new SesionDeRequest(token, actual);
    }

    public Actor exigirVigente() {
        var anterior = SesionActual.sesion();
        var actual = comprobar(anterior.token());
        if (actual.idCuenta() != anterior.idCuenta() || actual.idOrganizacion() != anterior.idOrganizacion()
                || !Objects.equals(actual.rolEfectivo(), anterior.rolEfectivo())) throw new CredencialesInvalidasException();
        var restriccion = actual.acceso().restricciones();
        if (restriccion.debeCambiarContrasena() || restriccion.debeEnrolarMfa()) throw new AccesoNoAutorizadoException();
        return SesionActual.actor();
    }
}
