package com.controllocal.service;

import java.time.OffsetDateTime;
import com.controllocal.service.soporte.FuentesAsuntos.Origen;

/**
 * Avisos privados de Core. Conserva la forma pública de ficha y reautoriza
 * causa, recurso y finalidad antes de contar, entregar o atender. La fuente
 * identifica al agente original; la ficha C7 identifica al responsable actual.
 * Administrar una organización no concede operación comercial. R27 utiliza su
 * seguimiento canónico; los productores C7 deben identificar el hecho real.
 */
public interface AlertaService {

    /** Espejo de AlertaResponse. {@code ruta} es derivada, no columna. */
    record FichaAlerta(Long id, String tipo, String severidad, String entidadTipo, Long entidadId,
                       Long idAgente, String agenteNombre, String mensaje, String estado,
                       OffsetDateTime fechaGeneracion, OffsetDateTime fechaResolucion, String ruta) {
    }

    /**
     * Emisión interna en la TX del productor. El agente es el responsable al
     * detectar el hecho; origen identifica una revisión canónica, nunca una
     * fecha de lectura, una versión irrelevante o un ID recibido sin comprobar.
     */
    record DatosAlerta(String tipo, String severidad, String entidadTipo, Long entidadId,
                       Long idRolAgente, String mensaje,Origen origen) {
    }

    Pagina<FichaAlerta> listar(int pagina, int tamano, Actor actor);

    /**
     * Marca el aviso como atendido. Devuelve <b>false</b> si ya lo estaba: el
     * UPDATE de la v1 lleva {@code AND estado = 'ACTIVA'}, asi que atender dos
     * veces no es un error, responde {@code {"atendida": false}} (D-F6-6).
     *
     * <p>Si la alerta no es VISIBLE para el actor, lanza no-encontrado: el
     * cable responde <b>404, no 403</b> (D-F6-3).
     */
    boolean atender(long id, Actor actor);

    /**
     * Emite en la transacción del productor. Un origen incompleto, una fuente
     * ajena o un fallo técnico no se convierten en éxito sin aviso.
     */
    void emitir(DatosAlerta datos, Actor actor);

    /**
     * Proyecta las necesidades vencidas autorizadas de CAT-R27 que acreditan
     * intento fechado sin respuesta. Cada marca material conserva su aviso;
     * atender, posponer, reasignar o recuperar no fabrica otro. No aplica otro
     * plazo ni contacta al propietario. Una posposición no es una resolución.
     *
     * @return cuantas alertas nuevas se crearon
     */
    int sincronizarRecontacto(Actor actor);
}
