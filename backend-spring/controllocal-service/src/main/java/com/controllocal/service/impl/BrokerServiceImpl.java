package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorBroker;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.AgenteService;
import com.controllocal.service.Actor;
import com.controllocal.service.BrokerService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.UsuariosInternos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BrokerServiceImpl implements BrokerService {

    private final DetalleBrokerRepository brokers;
    private final DetalleAgenteRepository agentes;
    private final SupervisionAgenteRepository supervisiones;
    private final PersonaRepository personas;
    private final UsuariosInternos usuarios;

    public BrokerServiceImpl(DetalleBrokerRepository brokers,
                             DetalleAgenteRepository agentes,
                             SupervisionAgenteRepository supervisiones,
                             PersonaRepository personas,
                             UsuariosInternos usuarios) {
        this.brokers = brokers;
        this.agentes = agentes;
        this.supervisiones = supervisiones;
        this.personas = personas;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaBroker> listar(int pagina, int tamano, Actor actor) {
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = tamano(tamano);
        Page<DetalleBroker> page = brokers.pagina(actor.idOrganizacion(),
                PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(fichas(page.getContent(), actor.idOrganizacion()),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public FichaBroker obtener(long id, Actor actor) {
        DetalleBroker broker = brokers.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Broker"));
        return ficha(broker,
                usuarios.cuenta(actor.idOrganizacion(), broker.getRol().getPersona().getId()),
                conteos(List.of(id), actor.idOrganizacion()).getOrDefault(id, 0),
                usuarios.estadoLocalDelRol(actor.idOrganizacion(),broker.getRol().getPersona().getId(),"BROKER"),
                usuarios.administracionLocal(actor.idOrganizacion(),broker.getRol().getPersona().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgenteService.FichaAgente> agentes(long id, Actor actor) {
        validarBrokerOperativo(id, actor.idOrganizacion());
        List<Long> ids = supervisiones.agentesSupervisados(actor.idOrganizacion(), id);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<DetalleAgente> filas = agentes.buscarFichas(actor.idOrganizacion(), ids);
        Map<Long, CuentaAcceso> mapa = credenciales(filas, actor.idOrganizacion());
        var estados=usuarios.estadosLocalesDelRol(actor.idOrganizacion(),filas.stream().map(a->a.getRol().getPersona().getId()).toList(),"AGENTE");
        return filas.stream()
                .map(a -> fichaAgente(a,
                        mapa.get(a.getRol().getPersona().getId()),estados.get(a.getRol().getPersona().getId())))
                .toList();
    }

    @Override
    @Transactional
    public FichaBroker actualizar(long id, DatosBroker datos, Actor actor) {
        validarAdministrador(actor);
        DetalleBroker broker = brokers.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Broker"));
        CuentaAcceso credencial = usuarios.cuenta(actor.idOrganizacion(),
                broker.getRol().getPersona().getId());
        if (datos != null) {
            Persona persona = broker.getRol().getPersona();
            if (!UsuariosInternos.vacio(datos.nombre())) {
                persona.setNombresORazonSocial(datos.nombre());
            }
            if (datos.telefono() != null) {
                persona.setTelefono(datos.telefono());
            }
            if (datos.correoLaboral() != null) {
                persona.setCorreo(datos.correoLaboral());
            }
            if (datos.zona() != null) {
                broker.setZona(datos.zona());
            }
            personas.save(persona);
            brokers.save(broker);
        }
        int total = conteos(List.of(id), actor.idOrganizacion()).getOrDefault(id, 0);
        return ficha(broker, credencial, total,usuarios.estadoLocalDelRol(actor.idOrganizacion(),broker.getRol().getPersona().getId(),"BROKER"),
                usuarios.administracionLocal(actor.idOrganizacion(),broker.getRol().getPersona().getId()));
    }

    private List<FichaBroker> fichas(List<DetalleBroker> filas, long idOrganizacion) {
        List<Long> personas = filas.stream()
                .map(b -> b.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CuentaAcceso> usuariosPorPersona =
                usuarios.cuentasPorPersona(idOrganizacion, personas);
        var estados=usuarios.estadosLocalesDelRol(idOrganizacion,personas,"BROKER");
        var administraciones=usuarios.administracionesLocalesPorPersona(idOrganizacion,personas);
        List<Long> ids = filas.stream().map(DetalleBroker::getId).toList();
        Map<Long, Integer> conteos = conteos(ids, idOrganizacion);
        return filas.stream()
                .map(b -> ficha(b,
                        usuariosPorPersona.get(b.getRol().getPersona().getId()),
                        conteos.getOrDefault(b.getId(), 0),estados.get(b.getRol().getPersona().getId()),
                        administraciones.getOrDefault(b.getRol().getPersona().getId(),false)))
                .toList();
    }

    private Map<Long, CuentaAcceso> credenciales(
            List<DetalleAgente> filas, long idOrganizacion) {
        List<Long> personas = filas.stream()
                .map(a -> a.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        return usuarios.cuentasPorPersona(idOrganizacion, personas);
    }

    private Map<Long, Integer> conteos(Collection<Long> ids, long idOrganizacion) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> resultado = new HashMap<>();
        for (ConteoPorBroker fila
                : supervisiones.contarActivasPorBrokers(idOrganizacion, ids)) {
            resultado.put(fila.getIdBroker(), Math.toIntExact(fila.getTotal()));
        }
        return resultado;
    }

    /**
     * Gobierno del tenant, comprobado <b>por membresia</b> (D-S0-7, §2.3).
     *
     * <p>Antes exigia ademas un {@code detalle_broker} con el flag: ese segundo
     * paso era la fuga que hacia del administrador "un broker con un booleano",
     * y hoy ni siquiera funcionaria — el {@code idRolOperativo} de un
     * TENANT_ADMIN es su rol de gobierno, no un rol de broker.
     *
     * <p>Un permiso insuficiente se distingue de una entrada inválida.
     */
    private static void validarAdministrador(Actor actor) {
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
    }

    private DetalleBroker validarBrokerOperativo(long id, long idOrganizacion) {
        DetalleBroker broker = brokers.buscarFicha(idOrganizacion, id)
                .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."));
        CuentaAcceso credencial = usuarios.cuenta(
                idOrganizacion, broker.getRol().getPersona().getId());
        if (!"A".equals(credencial.getEstado()) || !"A".equals(usuarios.estadoLocalDelRol(idOrganizacion,broker.getRol().getPersona().getId(),"BROKER"))) {
            throw new ReglaNegocioException("El broker no esta activo.");
        }
        return broker;
    }

    private static FichaBroker ficha(DetalleBroker broker,
                                     CuentaAcceso credencial,
                                     int agentesACargo,String estadoLocal,boolean administracionLocal) {
        Persona persona = broker.getRol().getPersona();
        return new FichaBroker(broker.getId(), broker.getCodigoBroker(),
                persona.getNombresORazonSocial(), persona.getTipoPersona(),
                persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getTelefono(), persona.getCorreo(),
                credencial != null ? credencial.getNombreUsuario() : null,
                broker.getZona(), broker.getFechaDesignacion(),
                estadoLocal,
                administracionLocal, agentesACargo);
    }

    private static AgenteService.FichaAgente fichaAgente(
            DetalleAgente agente, CuentaAcceso credencial,String estadoLocal) {
        Persona persona = agente.getRol().getPersona();
        return new AgenteService.FichaAgente(agente.getId(), agente.getCodigoAgente(),
                persona.getNombresORazonSocial(), persona.getTipoPersona(),
                persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getTelefono(), persona.getCorreo(),
                credencial != null ? credencial.getNombreUsuario() : null,
                agente.getZonaAsignada(), agente.getFechaIngreso(),
                estadoLocal,
                agente.getEstadoOperativo(), null, null);
    }

    private static int tamano(int valor) {
        return Math.max(1, Math.min(100, valor));
    }
}
