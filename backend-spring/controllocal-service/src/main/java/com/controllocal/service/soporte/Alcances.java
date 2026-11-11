package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Alcance comercial vigente por tenant y relación: AGENTE propio, BROKER supervisión actual.
 * TENANT_ADMIN sólo obtiene una proyección de gobierno mediante su puerta específica.
 * CAT-R19 / perfil LECTURA: administrar no abre cartera ni histórico comercial.
 */
@Component
public class Alcances {

    /** global sólo se emite para una actuación de gobierno expresamente autorizada. */
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

    /** La selección comercial no tiene un fallback administrativo ni una banda futura implícita. */
    public Alcance de(Actor actor) {
        exigirOperador(actor);
        long organizacion = actor.idOrganizacion();
        if (actor.esAgente()) return new Alcance(organizacion, false, List.of(actor.idRolOperativo()));
        return new Alcance(organizacion, false,
                List.copyOf(supervisiones.agentesSupervisados(organizacion, actor.idRolOperativo())));
    }

    /** CAT-R19: una atribución de gobierno o identidad no concede operación comercial. */
    public static void exigirOperador(Actor actor) {
        if (actor == null || (!actor.esAgente() && !actor.esBroker()))
            throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
    }

    /** RF-017: metadatos/rastro de reasignación, nunca ficha o condiciones del Encargo. */
    public Alcance paraGobiernoDeReasignacion(Actor actor) {
        if (actor.esTenantAdmin()) return new Alcance(actor.idOrganizacion(), true, List.of());
        if (actor.esBroker()) return de(actor);
        throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
    }

    /** Alcance comercial sobre una relación con responsable conocido. Sin responsable, no concede. */
    public boolean alcanza(Actor actor, Long idRolAgente) {
        if (idRolAgente == null) return false;
        if (actor.esAgente()) return idRolAgente == actor.idRolOperativo();
        return actor.esBroker() && supervisiones
                .agentesSupervisados(actor.idOrganizacion(), actor.idRolOperativo()).contains(idRolAgente);
    }

    /** Extremos de una reasignación de gobierno; el consumidor ya comprobó tenant. */
    public boolean alcanzaParaGobiernoDeReasignacion(Actor actor, Long idRolAgente) {
        return idRolAgente != null && (actor.esTenantAdmin() || (actor.esBroker() && alcanza(actor, idRolAgente)));
    }

    /**
     * Proyección/actuación de traspaso vigente en el adaptador físico legado.
     * Conserva esa guarda sin convertirla en permiso para leer/editar datos del inmueble.
     */
    public boolean alcanzaParaGobiernoDeTraspasoFisico(Actor actor, Long idRolAgente) {
        return actor.esTenantAdmin() || (actor.esBroker() && (idRolAgente == null || alcanza(actor, idRolAgente)));
    }

    public List<Long> supervisados(long idOrganizacion, long idRolBroker) {
        return supervisiones.agentesSupervisados(idOrganizacion, idRolBroker);
    }
}
