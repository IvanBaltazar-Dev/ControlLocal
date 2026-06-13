package com.controllocal.rest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.UsuarioInternoBusinessLogic;
import com.controllocal.bl.impl.AgenteBusinessLogicImpl;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.UsuarioInternoBusinessLogicImpl;
import com.controllocal.model.usuario.UsuarioInterno;
import com.controllocal.model.usuario.enums.RolUsuarioInterno;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.PasswordHasher;
import com.controllocal.rest.seguridad.RateLimiter;
import com.controllocal.rest.seguridad.TokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthRest {

    private static final RateLimiter LIMITADOR = new RateLimiter(5);

    private final UsuarioInternoBusinessLogic usuarios = new UsuarioInternoBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final AgenteBusinessLogic agentes = new AgenteBusinessLogicImpl();
    private final TokenService tokens = new TokenService();

    @Context
    private HttpServletRequest request;

    @POST
    @Path("login")
    public Dtos.LoginResponse login(Dtos.LoginRequest credenciales) {
        if (!LIMITADOR.permitir(request.getRemoteAddr())) {
            throw ApiException.demasiadasSolicitudes();
        }
        if (credenciales == null || credenciales.usuario() == null || credenciales.usuario().isBlank()
                || credenciales.contrasena() == null || credenciales.contrasena().isBlank()) {
            throw new ApiException(401, "Credenciales invalidas.");
        }

        char[] password = credenciales.contrasena().toCharArray();
        try {
            UsuarioInterno usuario = usuarios.buscarPorNombreUsuario(credenciales.usuario().trim())
                    .filter(u -> u.autenticar() && PasswordHasher.verificar(password, u.getContrasenaHash()))
                    .orElseThrow(() -> new ApiException(401, "Credenciales invalidas."));

            IdentidadDominio identidad = resolverIdentidad(usuario);
            TokenService.Sesion sesion = tokens.emitir(
                    usuario.getNombreUsuario(),
                    identidad.rol(),
                    usuario.getIdUsuarioInterno(),
                    identidad.idDominio());

            String nombre = usuario.getPersona() != null
                    ? usuario.getPersona().getNombresORazonSocial()
                    : usuario.getNombreUsuario();
            return new Dtos.LoginResponse(
                    tokens.firmar(sesion),
                    TokenService.DURACION_SEGUNDOS,
                    identidad.rol(),
                    usuario.getIdUsuarioInterno(),
                    identidad.idDominio(),
                    nombre,
                    usuario.getNombreUsuario(),
                    LocalDateTime.ofInstant(sesion.expiraEn(), ZoneId.systemDefault()));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private IdentidadDominio resolverIdentidad(UsuarioInterno usuario) {
        if (usuario.getRol() == RolUsuarioInterno.BROKER) {
            return brokers.buscarPorUsuario(usuario.getIdUsuarioInterno())
                    .map(broker -> new IdentidadDominio(
                            broker.isEsAdministrador() ? "ADMIN" : "BROKER",
                            broker.getIdBroker()))
                    .orElseThrow(() -> new ApiException(401, "Credenciales invalidas."));
        }
        return agentes.buscarPorUsuario(usuario.getIdUsuarioInterno())
                .map(agente -> new IdentidadDominio("AGENTE", agente.getIdAgente()))
                .orElseThrow(() -> new ApiException(401, "Credenciales invalidas."));
    }

    private record IdentidadDominio(String rol, long idDominio) {
    }
}
