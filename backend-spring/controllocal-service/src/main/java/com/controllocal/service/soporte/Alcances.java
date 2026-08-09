package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Alcance por fila del actor (RC-001, Doc 5 §8):
 * AGENTE ve/opera solo lo suyo; BROKER lo de los agentes que supervisa hoy
 * (supervision_agente con vigencia abierta); ADMIN todo (gobierno).
 * El resultado se pasa como parametro OBLIGATORIO del WHERE de las consultas.
 *
 * <p>Desde V6 el alcance arranca por el TENANT: primero la organizacion,
 * despues el rol. "Todo" de un ADMIN es todo lo de SU organizacion, nunca lo
 * de otra corredora.
 */
@Component
public class Alcances {

    /**
     * {@code global}=true solo para ADMIN, y significa "sin filtro de rol
     * DENTRO de {@code idOrganizacion}" — la frontera de tenant nunca se
     * levanta. {@code paramRoles()} nunca es una lista vacia (el IN de SQL no
     * la admite): cuando no hay roles usa un centinela que no matchea, y el
     * llamador debe cortar antes con {@link Alcance#vacio()}.
     */
    public record Alcance(long idOrganizacion, boolean global, List<Long> rolesAgente) {

        public boolean vacio() {
            return !global && rolesAgente.isEmpty();
        }

        public List<Long> paramRoles() {
            return rolesAgente.isEmpty() ? List.of(-1L) : rolesAgente;
        }

        /**
         * Los mismos roles como literal de array de PostgreSQL ({@code {1,2}}),
         * para las consultas NATIVAS de busqueda por conjunto de candidatos.
         *
         * <p>No es capricho: esas consultas repiten el parametro en cada rama
         * del {@code UNION}, y la expansion de una coleccion en un {@code IN
         * (:roles)} nativo repetido es fragil. Un literal casteado a
         * {@code bigint[]} y comparado con {@code = any(...)} se liga una sola
         * vez y no depende de esa expansion.
         */
        public String paramRolesArray() {
            return paramRoles().stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
    }

    private final SupervisionAgenteRepository supervisiones;

    public Alcances(SupervisionAgenteRepository supervisiones) {
        this.supervisiones = supervisiones;
    }

    public Alcance de(Actor actor) {
        long idOrganizacion = actor.idOrganizacion();
        if (actor.esTenantAdmin()) {
            return new Alcance(idOrganizacion, true, List.of());
        }
        if (actor.esAgente()) {
            return new Alcance(idOrganizacion, false, List.of(actor.idRolOperativo()));
        }
        return new Alcance(idOrganizacion, false,
                supervisiones.agentesSupervisados(idOrganizacion, actor.idRolOperativo()));
    }

    /** ¿El actor alcanza a un recurso cuyo dueno es el rol de agente dado? */
    public boolean alcanza(Actor actor, Long idRolAgenteDueno) {
        if (idRolAgenteDueno == null) {
            return false;
        }
        if (actor.esTenantAdmin()) {
            return true;
        }
        if (actor.esAgente()) {
            return idRolAgenteDueno == actor.idRolOperativo();
        }
        return supervisiones.agentesSupervisados(actor.idOrganizacion(), actor.idRolOperativo())
                .contains(idRolAgenteDueno);
    }

    public List<Long> supervisados(long idOrganizacion, long idRolBroker) {
        return supervisiones.agentesSupervisados(idOrganizacion, idRolBroker);
    }
}
