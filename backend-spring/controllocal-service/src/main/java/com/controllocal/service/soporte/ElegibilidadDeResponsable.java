package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Quien puede RECIBIR una propiedad o un encargo</b> (D-P0-7), y quien puede
 * ser <b>ofrecido</b> como destino (D-P0-12).
 *
 * <h2>Las dos preguntas son la misma, y por eso viven juntas</h2>
 * <pre>
 *   «que destinos puedo elegir»   -&gt; {@link #candidatos}      (la lista)
 *   «este destino vale»           -&gt; {@link #exigirElegible}  (el POST)
 * </pre>
 *
 * <h2>Y sirve a las DOS autoridades, no solo a la propiedad</h2>
 * Desde el 2026-09-01 la usan tambien el traspaso del <b>ENCARGO</b>
 * ({@code POST /captaciones/&#123;id&#125;/reasignar}) y su lista de candidatos.
 * Un agente que no puede recibir una propiedad tampoco puede recibir un
 * encargo: la pregunta —«¿esta esta persona en condiciones de responder por
 * trabajo comercial hoy?»— es literalmente la misma, y responderla dos veces en
 * dos sitios es como se llega a que un traspaso rechace a quien la otra puerta
 * acepta.
 * Las dos salen del <b>mismo</b> predicado SQL
 * ({@code DetalleAgenteRepository.CONDICION_CANDIDATO}). No es una comodidad:
 * si la lista y la revalidacion fueran dos escrituras de la misma regla, la
 * pantalla acabaria ofreciendo un agente que el POST rechaza —o, peor, dejaria
 * de ofrecer a alguien perfectamente valido— y nadie lo notaria hasta que un
 * broker intentara el traspaso.
 *
 * <h2>Que NO hace este componente</h2>
 * <ul>
 *   <li><b>No sustituye al POST.</b> {@code AutoridadDePropiedad.asignar} sigue
 *       comprobando la banda, la frontera de tenant, el alcance sobre el destino
 *       y el alcance sobre el saliente, y despues llama aqui. La lista es una
 *       ayuda de pantalla; la autorizacion es el comando.</li>
 *   <li><b>No inventa estados.</b> Las cinco condiciones son las autoridades que
 *       ya existen —{@code persona_rol}, {@code credencial_usuario},
 *       {@code usuario_organizacion}, {@code detalle_agente} y
 *       {@code supervision_agente}—, cada una consultada donde vive.</li>
 *   <li><b>No reasigna nada</b> (D-P0-8). Desactivar a un agente lo saca de esta
 *       lista y le impide recibir propiedades nuevas; las que ya tenia se quedan
 *       donde estan, como situacion que un traspaso trazable tiene que resolver
 *       de forma explicita.</li>
 * </ul>
 *
 * <h2>Un solo mensaje, y a proposito</h2>
 * {@link #exigirElegible} no dice <b>cual</b> de las cinco condiciones fallo.
 * Decirlo publicaria el estado administrativo de una cuenta ajena —si esta
 * suspendida, si perdio la membresia, si esta de baja— a quien solo preguntaba
 * por un traspaso. El broker que necesite el detalle lo tiene en la ficha del
 * agente, que es la superficie donde ese dato si le corresponde.
 */
@Component
public class ElegibilidadDeResponsable {

    /**
     * El centinela de "no excluyas a nadie". No es un id: {@code detalle_agente}
     * cuelga de {@code persona_rol}, cuya secuencia es positiva.
     */
    private static final long SIN_EXCLUIR = -1L;

    private final DetalleAgenteRepository agentes;

    public ElegibilidadDeResponsable(DetalleAgenteRepository agentes) {
        this.agentes = agentes;
    }

    /**
     * <b>Los destinos que ESTE actor puede elegir para ESTA propiedad</b>
     * (D-P0-12).
     *
     * <p>Ya vienen filtrados por las cinco condiciones de D-P0-7 y por el
     * alcance del actor, y sin el responsable actual: la lista es de
     * <b>candidatos elegibles</b>, no de agentes del tenant que la pantalla
     * tenga que depurar. Angular no decide autoridad, tampoco por omision.
     *
     * @param texto opcional; busca por nombre de la persona o por codigo de
     *              agente, resuelto en la base (la lista se pagina, asi que
     *              filtrar despues devolveria una pagina incompleta)
     */
    public Page<DetalleAgente> candidatos(Actor actor, Propiedad propiedad, String texto,
                                          Pageable pagina) {
        return candidatosExcluyendo(actor, excluir(propiedad), texto, pagina);
    }

    /**
     * <b>Los mismos candidatos, para un recurso que no es una propiedad</b>
     * (D-P0-12 aplicado al ENCARGO).
     *
     * <p>Las <b>reglas son las mismas</b> y por eso no hay un segundo
     * componente: quien puede <b>recibir</b> una propiedad y quien puede
     * <b>recibir</b> un encargo responden a las mismas cinco condiciones de
     * D-P0-7 mas la supervision del actor. Lo unico que cambia entre los dos
     * casos es <b>a quien hay que sacar de la lista</b> —el responsable actual
     * de la propiedad, o el agente actual del encargo—, y eso es un parametro,
     * no una regla.
     *
     * <p>Escribir un segundo predicado "para encargos" habria sido la forma
     * habitual de que las dos listas terminen ofreciendo cosas distintas; si
     * algun dia las reglas <b>de verdad</b> divergen, lo que corresponde es
     * partir el predicado en la base y decirlo, no dejar que se separen solas.
     *
     * @param idRolExcluido el agente que no se ofrece —porque ya lo lleva—, o
     *                      {@code -1} para no excluir a nadie
     */
    public Page<DetalleAgente> candidatosExcluyendo(Actor actor, long idRolExcluido, String texto,
                                                    Pageable pagina) {
        return agentes.candidatosAResponsable(actor.idOrganizacion(),
                // El TENANT_ADMIN gobierna su organizacion entera y no supervisa
                // a nadie: exigirle supervision le dejaria la lista vacia. Sigue
                // sujeto a las otras cinco -- y a la frontera de tenant, que es
                // el primer parametro.
                actor.esTenantAdmin(),
                actor.idRolOperativo(),
                idRolExcluido,
                enBlancoANulo(texto),
                pagina);
    }

    /**
     * <b>La revalidacion del comando</b> (D-P0-7), y el punto donde deja de
     * haber ventana (D-P0-13).
     *
     * <p>Se pregunta por el <b>id del rol destino</b> y sin excluir a nadie: la
     * exclusion del responsable actual es cosa de la lista —no ofrecer un
     * traspaso que no traspasa—, mientras que aqui esa colision ya la rechaza
     * {@code AutoridadDePropiedad.asignar} con su propio mensaje.
     *
     * <h2>Primero se toma la fila del destino, y despues se pregunta</h2>
     * Comprobar y escribir eran dos momentos, y entre los dos cabia una
     * transaccion que desactivara al destino: la propiedad —o el encargo—
     * acababa en manos de alguien que ya no podia recibirla, con todas las
     * guardas verdes. Por eso lo primero es
     * {@link DetalleAgenteRepository#bloquearParaGobierno}: a partir de ahi,
     * cualquier flujo de gobierno que quiera cambiar la elegibilidad de ese
     * agente <b>espera</b> a que este traspaso termine, y este traspaso decide
     * sobre el estado que va a seguir siendo verdad cuando escriba.
     *
     * <p><b>El orden importa y es este</b>: bloquear, y <b>despues</b>
     * preguntar. Al reves, la respuesta se leeria antes del candado y volveria a
     * poder caducar entre las dos lineas — que es exactamente el defecto.
     *
     * <p>Un destino que no existe —o de otro tenant— sale por el <b>mismo</b>
     * rechazo que uno no elegible, y no por uno nuevo: quien pregunta por un
     * traspaso no tiene por que enterarse de si ese id existe en otra
     * corredora. La frontera de tenant ya la cerro antes el caso de uso, con su
     * propio mensaje.
     *
     * <p>Va {@code @Transactional(MANDATORY)} <b>a proposito</b>: un bloqueo
     * pesimista tomado fuera de una transaccion se soltaria al terminar la
     * consulta y no protegeria nada, asi que se exige que el llamador ya tenga
     * la suya abierta —la del comando— en vez de abrir una propia.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void exigirElegible(Actor actor, long idRolDestino) {
        boolean existe = agentes.bloquearParaGobierno(actor.idOrganizacion(), idRolDestino)
                .isPresent();
        boolean elegible = existe && agentes.esCandidatoAResponsable(actor.idOrganizacion(),
                actor.esTenantAdmin(), actor.idRolOperativo(), SIN_EXCLUIR, null, idRolDestino);
        if (!elegible) {
            throw new AccesoNoAutorizadoException(
                    "Ese agente no puede recibir propiedades hoy: su rol de agente, su cuenta, "
                            + "su pertenencia a la organizacion o su disponibilidad no estan "
                            + "vigentes. Las propiedades que ya lleva no se mueven solas -- si "
                            + "hay que sacarselas, se hace con un traspaso a un agente que si "
                            + "pueda recibirlas.");
        }
    }

    private static long excluir(Propiedad propiedad) {
        return propiedad != null && propiedad.tieneResponsable()
                ? propiedad.getIdRolResponsable() : SIN_EXCLUIR;
    }

    private static String enBlancoANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
