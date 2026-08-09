package com.controllocal.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso de la decision del broker sobre la solicitud. Los records
 * espejan el contrato CONGELADO (Dtos.EvaluacionRequest/Response v1).
 *
 * <p>La evaluacion NO es una entidad con estado: es un EVENTO con resultado.
 * Lo que transiciona es la SOLICITUD que ese evento mueve —aprobar, rechazar
 * o devolver observada—, y por eso la transicion sale de aqui.
 *
 * <p>Dos reglas del cable que sorprenden y estan en {@code EvaluacionServiceImpl}:
 * el broker <b>no elige</b> el tipo (se deriva del resultado) y solo cabe
 * <b>una evaluacion FINAL por solicitud</b>.
 *
 * <p>Alcance (§7): el recurso {@code /evaluaciones} es de BROKER/ADMIN —el
 * agente no entra—, y el broker ve solo las que el firmo. El historial de una
 * solicitud, en cambio, lo ve tambien su agente.
 */
public interface EvaluacionService {

    /**
     * Espejo de EvaluacionRequest. Ojo: {@code tipoEvaluacion} se ignora como
     * VALOR (se deriva del resultado) pero el cable lo exige PRESENTE y
     * valido; ver {@code registrar}.
     */
    record DatosEvaluacion(String tipoEvaluacion, String resultado, String observaciones,
                           Long idSolicitud) {
    }

    /** Espejo de EvaluacionResponse. */
    record FichaEvaluacion(Long id, LocalDateTime fechaEvaluacion, String resultado,
                           String observaciones, Long idBroker, String brokerNombre,
                           String tipoEvaluacion, Long idSolicitud) {
    }

    Pagina<FichaEvaluacion> listar(int pagina, int tamano, Actor actor);

    FichaEvaluacion obtener(long id, Actor actor);

    /** Decision del BROKER/ADMIN: crea el evento y mueve la solicitud en la misma transaccion. */
    FichaEvaluacion registrar(DatosEvaluacion datos, Actor actor);

    /** Historial de la solicitud (trazabilidad): lo ve tambien el agente dueno. */
    List<FichaEvaluacion> historialDeSolicitud(long idSolicitud, Actor actor);
}
