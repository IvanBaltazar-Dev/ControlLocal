package com.controllocal.service.soporte;

import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.Codigo;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import com.controllocal.domain.comun.EstadosDominio.EstadoOperativoAgente;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alta Party-Role compartida por agentes y brokers:
 * persona + rol USUARIO_INTERNO + credencial + rol operativo + <b>membresia</b>.
 *
 * <p>La membresia se crea aqui desde el Bloque 5 porque
 * {@code usuario_organizacion} paso a ser la fuente de verdad de la banda
 * (D-S0-8). Antes nadie la escribia salvo el backfill de V6, y por eso pudo
 * quedarse mal poblada sin que se notara (H-14): una tabla que nadie lee ni
 * mantiene se pudre en silencio. Este es el unico sitio donde nace un usuario
 * interno, asi que es el unico sitio donde puede nacer su membresia.
 *
 * <p>Las validaciones completas de propietario NO viven aqui. Agentes y
 * brokers conservan las validaciones realmente ejecutadas por su cable v1.
 */
@Component
public class UsuariosInternos {

    private static final Set<String> TIPOS_PERSONA = Set.of("N", "J");
    private static final Set<String> TIPOS_DOCUMENTO = Set.of("D", "R", "C", "P");
    /**
     * <b>Derivados del enum, no escritos a mano.</b>
     *
     * <p>Estaban como {@code Set.of("A","I")} y {@code Set.of("D","L","N")}:
     * una copia del vocabulario de {@link EstadosDominio} que nadie obligaba a
     * mantener sincronizada. Si el enum ganara o perdiera un codigo, esta
     * validacion seguiria aceptando el juego viejo sin que nada lo delatara —y
     * el catalogo de productores diria una cosa mientras el validador hace
     * otra—. Derivarlos hace imposible esa deriva.
     *
     * <p>{@code TIPOS_PERSONA} y {@code TIPOS_DOCUMENTO} se quedan literales a
     * proposito: no son estados y no tienen enum en {@code EstadosDominio}.
     */
    private static final Set<String> ESTADOS = codigosDe(EstadoActivoInactivo.values());
    private static final Set<String> OPERATIVOS = codigosDe(EstadoOperativoAgente.values());

    private static Set<String> codigosDe(Codigo[] valores) {
        return Arrays.stream(valores).map(Codigo::codigo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private final PersonaRepository personas;
    private final PersonaRolRepository roles;
    private final CredencialUsuarioRepository credenciales;
    private final UsuarioOrganizacionRepository membresias;

    public UsuariosInternos(PersonaRepository personas, PersonaRolRepository roles,
                            CredencialUsuarioRepository credenciales,
                            UsuarioOrganizacionRepository membresias) {
        this.personas = personas;
        this.roles = roles;
        this.credenciales = credenciales;
        this.membresias = membresias;
    }

    /**
     * Da de alta un usuario interno con su banda <b>operativa</b>. El gobierno
     * no se concede aqui: convertir a alguien en {@code TENANT_ADMIN} es una
     * decision propia, con su propio rastro, y mezclarla con "crear un broker"
     * es justo la herencia que el Bloque 5 desmonta.
     */
    public Alta registrar(long idOrganizacion, TipoRol tipoRolOperativo,
                          String tipoPersona, String tipoDocumento, String numeroDocumento,
                          String nombre, String telefono, String correo,
                          String usuario, String contrasena, String estadoAdministrativo) {
        Persona persona = Personas.nueva(idOrganizacion, tipoPersona, tipoDocumento,
                numeroDocumento, nombre, telefono, correo, Personas.ACTIVO, Boolean.TRUE);
        personas.save(persona);

        PersonaRol rolUsuario = Personas.nuevoRol(
                idOrganizacion, persona, TipoRol.USUARIO_INTERNO);
        roles.save(rolUsuario);

        CredencialUsuario credencial = new CredencialUsuario();
        credencial.setOrganizacionId(idOrganizacion);
        credencial.setRol(rolUsuario);
        credencial.setNombreUsuario(usuario);
        credencial.setContrasenaHash(PasswordHasher.hash(contrasena.toCharArray()));
        credencial.setEstadoAdministrativo(estadoAdministrativo);
        credenciales.save(credencial);

        PersonaRol rolOperativo = Personas.nuevoRol(
                idOrganizacion, persona, tipoRolOperativo);
        roles.save(rolOperativo);

        UsuarioOrganizacion membresia = new UsuarioOrganizacion();
        membresia.setOrganizacionId(idOrganizacion);
        // Durante la convivencia la "cuenta" es el rol USUARIO_INTERNO (D-22).
        membresia.setIdUsuario(rolUsuario.getId());
        membresia.setRol(tipoRolOperativo == TipoRol.BROKER
                ? UsuarioOrganizacion.ROL_BROKER
                : UsuarioOrganizacion.ROL_AGENTE);
        membresia.setNombreVisible(persona.getNombresORazonSocial());
        membresia.setIdPersona(persona.getId());
        membresias.save(membresia);

        return new Alta(persona, rolOperativo, credencial, membresia);
    }

    /**
     * Eleva una membresia a gobierno y crea el {@code persona_rol} de tipo
     * {@code ADMIN} que la sostiene. Los dos efectos van juntos <b>siempre</b>:
     * una membresia {@code TENANT_ADMIN} sin su rol de gobierno deja al titular
     * sin {@code idDominio} con el que firmar, y un rol de gobierno sin
     * membresia no autoriza nada.
     */
    public void concederGobierno(long idOrganizacion, Persona persona,
                                 UsuarioOrganizacion membresia) {
        membresia.setRol(UsuarioOrganizacion.ROL_TENANT_ADMIN);
        membresias.save(membresia);
        if (roles.buscarVigente(persona.getId(), TipoRol.ADMIN).isEmpty()) {
            roles.save(Personas.nuevoRol(idOrganizacion, persona, TipoRol.ADMIN));
        }
    }

    public Map<Long, CredencialUsuario> credencialesPorPersona(
            long idOrganizacion, Collection<Long> idsPersona) {
        if (idsPersona.isEmpty()) {
            return Map.of();
        }
        Map<Long, CredencialUsuario> resultado = new HashMap<>();
        for (CredencialUsuario credencial
                : credenciales.buscarPorPersonas(idOrganizacion, idsPersona)) {
            resultado.put(credencial.getRol().getPersona().getId(), credencial);
        }
        return resultado;
    }

    public CredencialUsuario credencial(long idOrganizacion, long idPersona) {
        return credenciales.buscarPorPersona(idOrganizacion, idPersona)
                .orElseThrow(() -> new NoEncontradoException("Usuario"));
    }

    public static String tipoPersonaO(String valor, String porDefecto) {
        return codigoTipoO(valor, porDefecto, TIPOS_PERSONA);
    }

    public static String tipoDocumentoO(String valor, String porDefecto) {
        return codigoTipoO(valor, porDefecto, TIPOS_DOCUMENTO);
    }

    public static String estadoAdministrativoO(String valor, String porDefecto) {
        String codigo = vacio(valor) ? porDefecto : valor.trim();
        if (!ESTADOS.contains(codigo)) {
            throw new ReglaNegocioException(
                    "Codigo invalido para EstadoActivoInactivo: " + codigo);
        }
        return codigo;
    }

    public static String estadoOperativoO(String valor, String porDefecto) {
        String codigo = vacio(valor) ? porDefecto : valor.trim();
        if (!OPERATIVOS.contains(codigo)) {
            throw new ReglaNegocioException(
                    "Codigo invalido para EstadoOperativoAgente: " + codigo);
        }
        return codigo;
    }

    public static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String codigoTipoO(String valor, String porDefecto, Set<String> validos) {
        String codigo = vacio(valor) ? porDefecto : valor.trim();
        if (!validos.contains(codigo)) {
            throw new ReglaNegocioException("Valor invalido: " + valor);
        }
        return codigo;
    }

    public record Alta(Persona persona, PersonaRol rolOperativo,
                       CredencialUsuario credencial, UsuarioOrganizacion membresia) {
    }
}
