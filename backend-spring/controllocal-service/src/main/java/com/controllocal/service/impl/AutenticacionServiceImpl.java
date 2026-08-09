package com.controllocal.service.impl;

import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AutenticacionService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.OrganizacionService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.soporte.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AutenticacionServiceImpl implements AutenticacionService {

    private final CredencialUsuarioRepository credenciales;
    private final PersonaRolRepository roles;
    private final DetalleBrokerRepository brokers;
    private final DetalleAgenteRepository agentes;
    private final UsuarioOrganizacionRepository membresias;
    private final OrganizacionService organizaciones;

    public AutenticacionServiceImpl(CredencialUsuarioRepository credenciales,
                                    PersonaRolRepository roles,
                                    DetalleBrokerRepository brokers,
                                    DetalleAgenteRepository agentes,
                                    UsuarioOrganizacionRepository membresias,
                                    OrganizacionService organizaciones) {
        this.credenciales = credenciales;
        this.roles = roles;
        this.brokers = brokers;
        this.agentes = agentes;
        this.membresias = membresias;
        this.organizaciones = organizaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public SesionAutenticada autenticar(String nombreUsuario, char[] contrasena) {
        if (nombreUsuario == null || nombreUsuario.isBlank() || contrasena == null || contrasena.length == 0) {
            throw new CredencialesInvalidasException();
        }

        CredencialUsuario credencial = credenciales.buscarActivaPorNombreUsuario(
                        organizaciones.idOrganizacionActual(), nombreUsuario.trim())
                .filter(c -> c.autenticable() && PasswordHasher.verificar(contrasena, c.getContrasenaHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        return resolverIdentidad(credencial.getRol().getPersona(), credencial.getNombreUsuario());
    }

    @Override
    @Transactional(readOnly = true)
    public SesionAutenticada identidadDe(long idOrganizacion, long idPersona) {
        CredencialUsuario credencial = credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .filter(CredencialUsuario::autenticable)
                .orElseThrow(CredencialesInvalidasException::new);
        return resolverIdentidad(credencial.getRol().getPersona(), credencial.getNombreUsuario());
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoDeAcceso estadoDeAcceso(long idOrganizacion, long idPersona) {
        // La proyeccion de persistencia se traduce aqui: la web no puede
        // depender de persistencia (gate ArquitecturaCapasTest), asi que el
        // record que cruza la frontera es el del service.
        return credenciales.estadoDeAcceso(idOrganizacion, idPersona)
                .map(fila -> new EstadoDeAcceso(
                        fila.sesionesInvalidasDesde(), fila.debeCambiarContrasena(),
                        fila.debeEnrolarMfa(), fila.rolEfectivo()))
                .orElse(EstadoDeAcceso.SIN_RESTRICCIONES);
    }

    @Override
    @Transactional
    public boolean invalidarSesiones(long idOrganizacion, long idPersona) {
        return credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .map(credencial -> {
                    credencial.setSesionesInvalidasDesde(OffsetDateTime.now());
                    credenciales.save(credencial);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Banda con la que entra la persona, resuelta desde su <b>membresia</b> y
     * ya no desde {@code detalle_broker.es_administrador} (D-S0-8, §3.2).
     *
     * <p>El gobierno se decide primero: si la membresia dice
     * {@code TENANT_ADMIN}, el {@code idDominio} del token es su
     * {@code persona_rol} de tipo {@code ADMIN} —el rol con el que firmara— y
     * el cable sigue diciendo {@code ADMIN}, que es el unico valor de gobierno
     * que el token congelado admite (R1). Ese cambio de {@code idDominio} es
     * el que obliga al administrador a volver a entrar una vez tras la
     * migracion (D-S0-10), y es un efecto de una sola vez.
     *
     * <p>Si no hay gobierno, manda el rol operativo vigente: BROKER sobre
     * AGENTE. Una credencial sin ninguno no puede entrar.
     */
    private SesionAutenticada resolverIdentidad(Persona persona, String nombreUsuario) {
        String banda = membresias
                .bandaActivaDePersona(organizaciones.idOrganizacionActual(), persona.getId())
                .orElse(null);

        if (Actor.TENANT_ADMIN.equals(banda)) {
            var rolGobierno = roles.buscarVigente(persona.getId(), TipoRol.ADMIN);
            if (rolGobierno.isPresent()) {
                return new SesionAutenticada(
                        persona.getId(),
                        rolGobierno.get().getId(),
                        "ADMIN",
                        persona.getNombresORazonSocial(),
                        nombreUsuario);
            }
            // Membresia de gobierno sin su persona_rol: dato inconsistente que
            // V33 descarta y que el alta crea siempre en la misma transaccion.
            // Se degrada al rol operativo en vez de rechazar el login: dejar
            // fuera al administrador de una corredora seria un fallo peor que
            // pedirle que recupere su rol de gobierno.
        }

        var rolBroker = roles.buscarVigente(persona.getId(), TipoRol.BROKER);
        if (rolBroker.isPresent()) {
            var detalle = brokers.findById(rolBroker.get().getId())
                    .orElseThrow(CredencialesInvalidasException::new);
            return new SesionAutenticada(
                    persona.getId(),
                    detalle.getId(),
                    "BROKER",
                    persona.getNombresORazonSocial(),
                    nombreUsuario);
        }
        var rolAgente = roles.buscarVigente(persona.getId(), TipoRol.AGENTE)
                .orElseThrow(CredencialesInvalidasException::new);
        var detalle = agentes.findById(rolAgente.getId())
                .orElseThrow(CredencialesInvalidasException::new);
        return new SesionAutenticada(
                persona.getId(),
                detalle.getId(),
                "AGENTE",
                persona.getNombresORazonSocial(),
                nombreUsuario);
    }
}
