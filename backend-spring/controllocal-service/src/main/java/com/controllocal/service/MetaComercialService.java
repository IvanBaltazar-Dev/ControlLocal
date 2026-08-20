package com.controllocal.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fijar, proponer y revisar las metas mensuales.
 *
 * <h2>Quién decide, y por qué así</h2>
 *
 * <p><b>Un agente no baja su meta porque va perdiendo.</b> Si pudiera, el
 * indicador sería manipulable: voy al 60 %, bajo la meta y vuelvo a verde. Pero
 * una meta inmutable tampoco sirve —vacaciones, altas a mitad de mes, cambios de
 * cartera, bajas—, así que la regla no es «nadie la toca» sino «la toca quien
 * dirige»:
 *
 * <ul>
 *   <li><b>AGENTE</b> — ve las suyas y <b>propone</b> un ajuste, con motivo.</li>
 *   <li><b>BROKER</b> — las <b>fija</b> y <b>decide</b> sobre lo que le proponen.</li>
 *   <li><b>TENANT_ADMIN</b> — <b>no fija objetivos comerciales.</b> Administrar
 *       usuarios no es dirigir producción, y es la misma frontera entre gobernar
 *       y operar que el resto del sistema ya respeta. Puede <b>leerlas</b>,
 *       porque gobierno sin visibilidad no puede responder por qué un equipo
 *       está en rojo. Un administrador que además dirija comercialmente lo hace
 *       con su rol de broker, no con el de gobierno.</li>
 * </ul>
 *
 * <h2>Y ninguna revisión sobrescribe a la anterior</h2>
 *
 * <p>Toda escritura deja su rastro en la serie: de cuánto a cuánto, cuándo,
 * quién y por qué. Sin eso, dentro de tres meses la base diría que la meta
 * siempre fue 6 y el gráfico de cumplimiento mentiría.
 */
public interface MetaComercialService {

    /**
     * Las metas del mes que el actor alcanza, con su propuesta viva —si la
     * hay— y su historial de revisiones.
     */
    List<MetaDeAgente> del(String mes, Actor actor);

    /**
     * Fija —o revisa— metas. <b>Solo el broker.</b>
     *
     * <p>Es idempotente por (agente, KPI, mes) y **exige motivo**: es lo único
     * que quedará para entender el cambio dentro de seis meses. Una meta ausente
     * en la petición no se borra; se deja como estaba.
     */
    List<MetaDeAgente> fijar(String mes, List<Asignacion> asignaciones, Actor actor);

    /**
     * El agente propone un ajuste de <b>su</b> meta. No la cambia: queda en
     * espera de que el broker decida.
     *
     * <p>Como mucho una propuesta viva por KPI y mes: insistir no debe llegarle
     * al broker como diez avisos de lo mismo.
     */
    List<MetaDeAgente> proponer(String mes, Propuesta propuesta, Actor actor);

    /** Lo que el broker tiene pendiente de decidir sobre su equipo. */
    List<PropuestaPendiente> propuestasPendientes(Actor actor);

    /**
     * El broker acepta o rechaza una propuesta. Aceptar aplica el valor
     * propuesto; rechazar deja la meta como estaba. En los dos casos queda
     * escrito quién decidió y por qué.
     */
    List<MetaDeAgente> resolver(long idRevision, boolean acepta, String motivo, Actor actor);

    /**
     * Una meta con quien la tiene, lo que se propuso sobre ella y cómo se llegó
     * a su valor.
     *
     * @param valor {@code null} si nadie la fijó. No es cero: cero significaría
     *              que este mes no se pide ese resultado, que es una decisión
     *              distinta de no haber decidido
     */
    record MetaDeAgente(long idRolAgente, String nombreAgente, String kpi, String rotuloKpi,
                        Integer valor, PropuestaPendiente propuesta, List<Revision> historial) {
    }

    /** Un ajuste que el agente pidió y el broker todavía no ha resuelto. */
    record PropuestaPendiente(long idRevision, long idRolAgente, String nombreAgente,
                              String kpi, String rotuloKpi, Integer valorVigente,
                              int valorPropuesto, String motivo, OffsetDateTime fecha) {
    }

    /**
     * Un paso del historial, ya redactado para poder leerlo de corrido:
     * «Meta inicial 8 → revisada a 6 el 18 de agosto · agente incorporado tarde».
     */
    record Revision(long id, String origen, String estado, Integer valorAnterior,
                    int valorPropuesto, String motivo, String autor, OffsetDateTime fecha,
                    String decisor, String motivoDecision) {
    }

    /** Lo que el broker fija: a quién, qué KPI, cuánto y por qué. */
    record Asignacion(long idRolAgente, String kpi, int valor, String motivo) {
    }

    /** Lo que el agente propone sobre su propia meta. */
    record Propuesta(String kpi, int valor, String motivo) {
    }
}
