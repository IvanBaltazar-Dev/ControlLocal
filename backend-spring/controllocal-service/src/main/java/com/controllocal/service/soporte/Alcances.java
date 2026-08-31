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

    /**
     * <b>¿El actor alcanza a un recurso cuyo dueno es el rol de agente dado?</b>
     *
     * <p><b>Sin dueno, NO.</b> Y no es un descuido que haya que arreglar: cinco
     * llamadores se apoyan en ello a proposito —{@code CoincidenciaServiceImpl}
     * (dos veces), {@code InteraccionServiceImpl}, {@code EvaluacionServiceImpl}
     * y {@code AccesoSolicitud}— porque para <b>sus</b> recursos "sin agente"
     * significa que no hay a quien atribuirlo, y el lado seguro es negar.
     *
     * <p>Para el <b>inventario sin dueno</b> la respuesta es la contraria, y por
     * eso vive en un metodo aparte y con su nombre:
     * {@link #alcanzaIncluidoSinDueno}. Dos preguntas parecidas con respuestas
     * distintas no se arreglan con un booleano mas — se separan, o la siguiente
     * superficie hereda la que no le tocaba.
     */
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

    /**
     * <b>Lo mismo, pero admitiendo que el recurso NO tenga dueno</b> (C5).
     *
     * <p>La decision del titular, con su porque: <b>gobernar el inventario sin
     * dueno es trabajo de broker</b> — es justo lo que tiene que mirar para
     * decidir a quien asignarlo. La regla "sus supervisados vigentes" existe
     * para <b>no cruzar equipos</b>; sin responsable <b>no hay a quien
     * supervisar</b>, asi que esa regla no tiene sobre que aplicarse y el limite
     * efectivo vuelve a ser el que va siempre delante: <b>el tenant</b>.
     *
     * <pre>
     *   con dueno  -&gt; como alcanza(): el BROKER, solo si lo supervisa
     *   SIN dueno  -&gt; cualquier BROKER o TENANT_ADMIN del mismo tenant
     *   AGENTE     -&gt; nunca: un inmueble sin responsable no es suyo, y
     *                 "de nadie" no es "de todos"
     * </pre>
     *
     * <p><b>La frontera de tenant no se comprueba aqui</b>, y no es un hueco:
     * este componente solo conoce la organizacion del ACTOR, no la del recurso.
     * La comprueba quien carga la fila —por {@code (organizacion, id)}, con 404
     * si no aparece— y por eso va <b>antes</b>. Aqui se decide el alcance
     * <b>dentro</b> del tenant, que es la segunda pregunta y nunca la primera.
     *
     * <p>Vive en {@code Alcances} y no en el llamador, a proposito. La primera
     * version de C5 resolvio este caso con una rama en
     * {@code AutoridadDePropiedad}: funcionaba, y dejaba la decision <b>fuera</b>
     * del sitio que decide alcances — de modo que la siguiente superficie que
     * preguntara por un recurso sin dueno volveria a heredar la respuesta
     * equivocada, en silencio.
     */
    public boolean alcanzaIncluidoSinDueno(Actor actor, Long idRolAgenteDueno) {
        if (idRolAgenteDueno == null) {
            return !actor.esAgente();
        }
        return alcanza(actor, idRolAgenteDueno);
    }

    public List<Long> supervisados(long idOrganizacion, long idRolBroker) {
        return supervisiones.agentesSupervisados(idOrganizacion, idRolBroker);
    }
}
