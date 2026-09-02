package com.controllocal.service.impl;

import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.ReasignacionAgenteBroker;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.persistence.query.ConteoPorBroker;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.ReasignacionAgenteBrokerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AsignacionService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.UsuariosInternos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AsignacionServiceImpl implements AsignacionService {

    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final SupervisionAgenteRepository supervisiones;
    private final ReasignacionAgenteBrokerRepository reasignaciones;
    private final UsuariosInternos usuarios;

    public AsignacionServiceImpl(DetalleAgenteRepository agentes,
                                 DetalleBrokerRepository brokers,
                                 SupervisionAgenteRepository supervisiones,
                                 ReasignacionAgenteBrokerRepository reasignaciones,
                                 UsuariosInternos usuarios) {
        this.agentes = agentes;
        this.brokers = brokers;
        this.supervisiones = supervisiones;
        this.reasignaciones = reasignaciones;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AsignacionAgente> agentes(Actor actor) {
        validarAdministrador(actor);
        List<DetalleAgente> filas = agentes.listarFichas(actor.idOrganizacion());
        List<Long> idsPersona = filas.stream()
                .map(a -> a.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CredencialUsuario> credenciales =
                usuarios.credencialesPorPersona(actor.idOrganizacion(), idsPersona);
        List<Long> idsAgente = filas.stream().map(DetalleAgente::getId).toList();
        Map<Long, Long> supervisor = supervisiones.activasPorAgentes(
                        actor.idOrganizacion(), idsAgente).stream()
                .collect(Collectors.toMap(SupervisionAgente::getIdRolAgente,
                        SupervisionAgente::getIdRolBroker));
        Map<Long, String> nombresBroker = nombresBroker(actor.idOrganizacion());
        return filas.stream().map(a -> {
            Persona persona = a.getRol().getPersona();
            CredencialUsuario credencial = credenciales.get(persona.getId());
            Long idBroker = supervisor.get(a.getId());
            return new AsignacionAgente(a.getId(),
                    persona.getNombresORazonSocial(),
                    persona.getNumeroDocumento(),
                    credencial != null
                            ? credencial.getEstadoAdministrativo() : null,
                    a.getEstadoOperativo(),
                    idBroker != null ? nombresBroker.get(idBroker) : null);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AsignacionBroker> brokers(Actor actor) {
        validarAdministrador(actor);
        List<DetalleBroker> filas = brokers.listarFichas(actor.idOrganizacion());
        List<Long> idsPersona = filas.stream()
                .map(b -> b.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CredencialUsuario> credenciales =
                usuarios.credencialesPorPersona(actor.idOrganizacion(), idsPersona);
        Map<Long, Integer> conteos = conteosBroker(
                filas.stream().map(DetalleBroker::getId).toList(),
                actor.idOrganizacion());
        return filas.stream().map(b -> {
            Persona persona = b.getRol().getPersona();
            CredencialUsuario credencial = credenciales.get(persona.getId());
            return new AsignacionBroker(b.getId(),
                    persona.getNombresORazonSocial(), b.getZona(),
                    credencial != null
                            ? credencial.getEstadoAdministrativo() : null,
                    b.isEsAdministrador(), conteos.getOrDefault(b.getId(), 0));
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reasignacion> historial(Actor actor) {
        validarAdministrador(actor);
        Map<Long, String> nombresAgente = nombresAgente(actor.idOrganizacion());
        Map<Long, String> nombresBroker = nombresBroker(actor.idOrganizacion());
        return reasignaciones
                .findByOrganizacionIdOrderByIdDesc(actor.idOrganizacion())
                .stream()
                .map(r -> ficha(r, nombresAgente, nombresBroker))
                .toList();
    }

    @Override
    @Transactional
    public Reasignacion reasignar(DatosReasignacion datos, Actor actor) {
        validarAdministrador(actor);
        if (datos == null || datos.idAgente() == null
                || datos.idBrokerDestino() == null) {
            throw new ReglaNegocioException(
                    "El agente y el broker destino son obligatorios.");
        }
        // Misma regla que al reasignar una captacion, y por la misma razon: este
        // texto es lo unico que explica, meses despues, por que un agente cambio
        // de broker.
        String motivo = PoliticaComercial.exigirMotivoDeReasignacion(datos.motivo());
        DetalleBroker destino = validarBrokerActivo(
                datos.idBrokerDestino(), actor.idOrganizacion());
        if (destino.isEsAdministrador()) {
            throw new ReglaNegocioException(
                    "El broker administrador no requiere asignacion "
                            + "de agentes para supervisar.");
        }
        DetalleAgente agente = agentes.buscarFicha(
                        actor.idOrganizacion(), datos.idAgente())
                .orElseThrow(() -> new ReglaNegocioException(
                        "Agente no encontrado."));
        // D-P0-13. Cambiar de supervisor cambia la ELEGIBILIDAD del agente para
        // un BROKER -la sexta condicion de D-P0-7 es «lo supervisas hoy»-, asi
        // que esto se serializa con los traspasos que esten mirandolo por la
        // MISMA fila que bloquea `exigirElegible`. Va antes de comprobar sus
        // estados para que lo que se lea aqui sea lo que siga siendo verdad al
        // escribir las supervisiones.
        agentes.bloquearParaGobierno(actor.idOrganizacion(), agente.getId())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado."));
        CredencialUsuario credencialAgente = usuarios.credencial(
                actor.idOrganizacion(), agente.getRol().getPersona().getId());
        if (!"A".equals(credencialAgente.getEstadoAdministrativo())) {
            throw new ReglaNegocioException("El agente debe estar ACTIVO.");
        }
        if (!"D".equals(agente.getEstadoOperativo())) {
            throw new ReglaNegocioException("El agente debe estar DISPONIBLE.");
        }

        Optional<SupervisionAgente> actual = supervisiones.buscarActivaPorAgente(
                actor.idOrganizacion(), datos.idAgente());
        if (actual.filter(s -> datos.idBrokerDestino().equals(s.getIdRolBroker()))
                .isPresent()) {
            throw new ReglaNegocioException(
                    "El agente ya esta asignado a ese broker supervisor.");
        }
        Long idAnterior = actual.map(SupervisionAgente::getIdRolBroker).orElse(null);
        if (actual.isPresent()) {
            SupervisionAgente supervision = actual.get();
            supervision.setFechaFin(LocalDate.now());
            supervisiones.save(supervision);
            // La unicidad parcial solo admite una fila activa por agente.
            // Forzamos el UPDATE antes del INSERT: Hibernate puede ordenar las
            // inserciones antes que las actualizaciones al vaciar la unidad de
            // trabajo y provocar un 409 aunque la transaccion sea correcta.
            supervisiones.flush();
        }

        SupervisionAgente nueva = new SupervisionAgente();
        nueva.setOrganizacionId(actor.idOrganizacion());
        nueva.setIdRolBroker(destino.getId());
        nueva.setIdRolAgente(agente.getId());
        nueva.setFechaAsignacion(LocalDate.now());
        nueva.setMotivo(motivo);
        supervisiones.save(nueva);

        ReasignacionAgenteBroker evento = new ReasignacionAgenteBroker();
        evento.setOrganizacionId(actor.idOrganizacion());
        evento.setIdRolAgente(agente.getId());
        evento.setIdRolBrokerAnterior(idAnterior);
        evento.setIdRolBrokerNuevo(destino.getId());
        // V36: el autor ya no cabe en una columna que apunta a `detalle_broker`
        // — administrar dejó de ser una variedad de broker—, así que se registra
        // la persona y su banda.
        evento.setIdPersonaActor(actor.idPersona());
        evento.setTipoRolActor(actor.rolEfectivo());
        evento.setMotivo(motivo);
        reasignaciones.save(evento);

        Map<Long, String> nombresAgente = Map.of(
                agente.getId(), agente.getRol().getPersona().getNombresORazonSocial());
        Map<Long, String> nombresBroker = nombresBroker(actor.idOrganizacion());
        return ficha(evento, nombresAgente, nombresBroker);
    }

    /**
     * Gobierno del tenant, comprobado <b>por membresía</b> (D-S0-7, §2.3).
     *
     * <p>Antes exigía además un {@code detalle_broker} con el flag. Ese segundo
     * paso era la fuga que hacía del administrador "un broker con un booleano",
     * y desde el Bloque 5 ni siquiera funciona: el {@code idRolOperativo} de un
     * {@code TENANT_ADMIN} es su rol de gobierno, así que buscarlo entre los
     * brokers responde "Broker no encontrado" y tumba las cuatro operaciones de
     * este recurso, que son suyas.
     */
    private static void validarAdministrador(Actor actor) {
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
    }

    private DetalleBroker validarBrokerActivo(long id, long idOrganizacion) {
        DetalleBroker broker = brokers.buscarFicha(idOrganizacion, id)
                .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."));
        CredencialUsuario credencial = usuarios.credencial(
                idOrganizacion, broker.getRol().getPersona().getId());
        if (!"A".equals(credencial.getEstadoAdministrativo())) {
            throw new ReglaNegocioException("El broker no esta activo.");
        }
        return broker;
    }

    private Map<Long, Integer> conteosBroker(
            Collection<Long> ids, long idOrganizacion) {
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

    private Map<Long, String> nombresAgente(long idOrganizacion) {
        return agentes.listarFichas(idOrganizacion).stream()
                .collect(Collectors.toMap(DetalleAgente::getId,
                        a -> a.getRol().getPersona().getNombresORazonSocial(),
                        (primero, segundo) -> primero));
    }

    private Map<Long, String> nombresBroker(long idOrganizacion) {
        return brokers.listarFichas(idOrganizacion).stream()
                .collect(Collectors.toMap(DetalleBroker::getId,
                        b -> b.getRol().getPersona().getNombresORazonSocial(),
                        (primero, segundo) -> primero));
    }

    private static Reasignacion ficha(
            ReasignacionAgenteBroker evento,
            Map<Long, String> nombresAgente,
            Map<Long, String> nombresBroker) {
        return new Reasignacion(evento.getId(), evento.getIdRolAgente(),
                nombresAgente.get(evento.getIdRolAgente()),
                evento.getIdRolBrokerAnterior(),
                nombresBroker.get(evento.getIdRolBrokerAnterior()),
                evento.getIdRolBrokerNuevo(),
                nombresBroker.get(evento.getIdRolBrokerNuevo()),
                evento.getIdRolBrokerAdministrador(),
                nombresBroker.get(evento.getIdRolBrokerAdministrador()),
                Fechas.local(evento.getFechaCambio()), evento.getMotivo());
    }
}
