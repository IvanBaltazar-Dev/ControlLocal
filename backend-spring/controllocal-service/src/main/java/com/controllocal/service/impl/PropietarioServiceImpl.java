package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorPropietario;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Autorizaciones;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Personas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reglas y mensajes calcados de {@code PropietariosRest} +
 * {@code PropietarioBusinessLogicImpl}.
 *
 * <p>Tres cosas del cable que hay que respetar durante la convivencia:
 * <ol>
 *   <li><b>El catalogo es compartido para ADMIN y AGENTE</b> —cualquier agente
 *       ve y edita cualquier propietario—; el unico rol acotado es el BROKER, y
 *       lo acota por sus PROPIEDADES, no por sus oportunidades;</li>
 *   <li>quien registra, edita y da de baja es el <b>AGENTE</b> (el gate vive en
 *       el controlador, igual que en la v1);</li>
 *   <li>{@code cantidadLocales} se calcula SOLO para los propietarios de la
 *       pagina: es un contador con alcance, no un atributo del propietario. Dos
 *       actores distintos ven numeros distintos para el mismo propietario, y eso
 *       es correcto.</li>
 * </ol>
 *
 * <p>La actualizacion replica {@code Propietario.actualizarDatos}: reemplaza
 * telefono, correo y nombre <b>tal cual llegan</b> —incluso a null— y NO toca el
 * documento ni el tipo de persona.
 */
@Service
public class PropietarioServiceImpl implements PropietarioService {

    /** Centinela de los IN de SQL, misma convencion que Alcances.paramRoles(). */
    private static final long SIN_ROL = -1L;

    private final PersonaRolRepository roles;
    private final PersonaRepository personas;
    private final PropiedadRepository propiedades;
    private final Alcances alcances;
    private final Autorizaciones autorizaciones;

    public PropietarioServiceImpl(PersonaRolRepository roles, PersonaRepository personas,
                                  PropiedadRepository propiedades, Alcances alcances,
                                  Autorizaciones autorizaciones) {
        this.roles = roles;
        this.personas = personas;
        this.propiedades = propiedades;
        this.alcances = alcances;
        this.autorizaciones = autorizaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaPropietario> listar(int pagina, int tamano, Actor actor) {
        return listar(new FiltrosPropietario(null, null, pagina, tamano), actor);
    }

    /**
     * Listado con filtros ADITIVOS. Omitidos los dos, responde exactamente lo
     * mismo que antes de que existieran, incluido el orden por id descendente.
     *
     * <p>Los dos caminos —con y sin alcance— van ahora por la MISMA consulta:
     * el conjunto del BROKER entra como restricción de ids y la paginación baja
     * a SQL. Antes se cortaba en memoria la lista entera de sus ids, lo que con
     * un filtro de texto habría filtrado solo la página visible.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaPropietario> listar(FiltrosPropietario filtros, Actor actor) {
        int tamanoValido = tamano(filtros.tamano());
        int paginaValida = Math.max(1, filtros.pagina());
        boolean sinScope = !esBroker(actor);
        Collection<Long> ids = sinScope ? List.of(SIN_ROL) : idsPermitidos(actor);
        Page<PersonaRol> page = roles.buscarPropietarios(actor.idOrganizacion(), sinScope, ids,
                limpiar(filtros.texto()), codigo(filtros.estado()),
                PageRequest.of(paginaValida - 1, tamanoValido));
        return conConteo(page.getContent(), page.getTotalElements(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPropietarios resumen(FiltrosPropietario filtros, Actor actor) {
        boolean sinScope = !esBroker(actor);
        Collection<Long> ids = sinScope ? List.of(SIN_ROL) : idsPermitidos(actor);
        String texto = limpiar(filtros.texto());
        Map<String, Long> porEstado = new HashMap<>();
        for (var fila : roles.contarPropietariosPorEstado(actor.idOrganizacion(), sinScope, ids,
                texto, codigo(filtros.estado()))) {
            if (fila.getEstado() != null) {
                porEstado.merge(fila.getEstado(), fila.getTotal(), Long::sum);
            }
        }
        long activos = porEstado.getOrDefault("A", 0L);
        long inactivos = porEstado.getOrDefault("I", 0L);
        return new ResumenPropietarios(activos + inactivos, activos, inactivos);
    }

    /** Nunca una lista vacía: el centinela mantiene el {@code IN} bien formado. */
    private Collection<Long> idsPermitidos(Actor actor) {
        List<Long> ids = idsDelBroker(actor);
        return ids.isEmpty() ? List.of(SIN_ROL) : ids;
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String codigo(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPropietario obtener(long id, Actor actor) {
        PersonaRol propietario = cargarConAcceso(id, actor);
        return ficha(propietario, conteos(List.of(id), actor).getOrDefault(id, 0));
    }

    @Override
    @Transactional
    public FichaPropietario registrar(DatosPropietario datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del propietario son obligatorios.");
        }
        String tipoPersona = Personas.exigirCodigo(datos.tipoPersona(), Personas.TIPOS_PERSONA,
                "tipo de persona");
        String tipoDocumento = Personas.exigirCodigo(datos.tipoDocumento(), Personas.TIPOS_DOCUMENTO,
                "tipo de documento");
        String estado = Personas.estadoOActivo(datos.estado());
        Personas.validar(tipoDocumento, datos.numeroDocumento(), datos.nombre());

        Persona persona = Personas.nueva(actor.idOrganizacion(), tipoPersona, tipoDocumento,
                datos.numeroDocumento(), datos.nombre(), datos.telefono(), datos.correo(),
                estado, datos.consentimientoUsoDato());
        personas.save(persona);

        // D-27: alta TRANSACCIONAL. Sin autorizacion no se persiste ni la
        // persona ni su rol; tampoco queda una fila diciendo que no autorizo.
        autorizaciones.registrarEnAlta(persona.getId(), datos.consentimientoUsoDato(), actor);

        PersonaRol rol = Personas.nuevoRol(actor.idOrganizacion(), persona, TipoRol.PROPIETARIO);
        // Recien creado no tiene locales: el contador arranca en 0 sin consultar.
        return ficha(roles.save(rol), 0);
    }

    @Override
    @Transactional
    public FichaPropietario actualizar(long id, DatosPropietario datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del propietario son obligatorios.");
        }
        PersonaRol propietario = cargarConAcceso(id, actor);
        Persona persona = propietario.getPersona();

        persona.setTelefono(datos.telefono());
        persona.setCorreo(datos.correo());
        persona.setNombresORazonSocial(datos.nombre());
        if (datos.consentimientoUsoDato() != null) {
            persona.setConsentimientoUsoDato(datos.consentimientoUsoDato());
        }
        if (datos.estado() != null && !datos.estado().isBlank()) {
            persona.setEstado(Personas.exigirCodigo(datos.estado(), Personas.ESTADOS,
                    "estado de la persona"));
        }
        // La v1 revalida la persona entera al guardar: el nombre sigue siendo
        // obligatorio aunque el PUT no toque el documento.
        Personas.validar(persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getNombresORazonSocial());
        personas.save(persona);
        // El cable NO recalcula el contador en el PUT: responde 0 (Response.desde
        // sin cantidad). Se replica.
        return ficha(propietario, 0);
    }

    @Override
    @Transactional
    public boolean desactivar(long id, Actor actor) {
        PersonaRol propietario = roles.buscarPropietario(actor.idOrganizacion(), id).orElse(null);
        if (propietario == null) {
            return false;
        }
        if (!alcanza(id, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        Persona persona = propietario.getPersona();
        persona.setEstado(Personas.INACTIVO);
        personas.save(persona);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Autorizaciones.Constancia autorizacion(long id, Actor actor) {
        // Se consulta por persona.id: la autorizacion la dio la PERSONA una
        // sola vez y cubre todos sus roles, no solo el de propietario.
        PersonaRol propietario = cargarConAcceso(id, actor);
        return autorizaciones.constancia(propietario.getPersona().getId(), actor.idOrganizacion());
    }

    // ------------------------------------------------------------------
    // Alcance: solo el BROKER queda acotado, y por sus PROPIEDADES
    // ------------------------------------------------------------------

    private PersonaRol cargarConAcceso(long id, Actor actor) {
        PersonaRol propietario = roles.buscarPropietario(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Propietario"));
        if (!alcanza(id, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return propietario;
    }

    private boolean alcanza(long idPropietario, Actor actor) {
        return !esBroker(actor) || idsDelBroker(actor).contains(idPropietario);
    }

    private List<Long> idsDelBroker(Actor actor) {
        Alcance alcance = alcances.de(actor);
        // Un broker sin equipo puede seguir revisando captaciones, asi que no se
        // corta en seco por alcance vacio: la consulta lleva su rol de broker.
        return propiedades.idsPropietarioDelBroker(alcance.idOrganizacion(), alcance.paramRoles(),
                actor.idRolOperativo());
    }

    // ------------------------------------------------------------------
    // Contador con alcance
    // ------------------------------------------------------------------

    private Pagina<FichaPropietario> conConteo(List<PersonaRol> filas, long total, Actor actor) {
        List<Long> ids = filas.stream().map(PersonaRol::getId).filter(Objects::nonNull).toList();
        Map<Long, Integer> conteos = conteos(ids, actor);
        return new Pagina<>(
                filas.stream().map(r -> ficha(r, conteos.getOrDefault(r.getId(), 0))).toList(),
                total);
    }

    private Map<Long, Integer> conteos(Collection<Long> idsPropietario, Actor actor) {
        if (idsPropietario.isEmpty()) {
            return Map.of();
        }
        Alcance alcance = alcances.de(actor);
        long rolBroker = esBroker(actor) ? actor.idRolOperativo() : SIN_ROL;
        Map<Long, Integer> conteo = new HashMap<>();
        for (ConteoPorPropietario fila : propiedades.contarLocalesEnSeguimiento(
                actor.idOrganizacion(), idsPropietario, alcance.global(), alcance.paramRoles(),
                rolBroker)) {
            conteo.put(fila.getIdPropietario(), fila.getTotal());
        }
        return conteo;
    }

    private static boolean esBroker(Actor actor) {
        return !actor.esTenantAdmin() && !actor.esAgente();
    }

    private static FichaPropietario ficha(PersonaRol rol, int cantidadLocales) {
        Persona persona = rol.getPersona();
        return new FichaPropietario(
                rol.getId(),
                persona != null ? persona.getTipoPersona() : null,
                persona != null ? persona.getTipoDocumento() : null,
                persona != null ? persona.getNumeroDocumento() : null,
                persona != null ? persona.getNombresORazonSocial() : null,
                persona != null ? persona.getTelefono() : null,
                persona != null ? persona.getCorreo() : null,
                persona != null ? persona.getEstado() : null,
                persona != null ? persona.getConsentimientoUsoDato() : null,
                persona != null ? Fechas.local(persona.getFechaCreacion()) : null,
                Math.max(0, cantidadLocales));
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }
}
