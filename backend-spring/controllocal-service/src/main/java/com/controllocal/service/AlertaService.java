package com.controllocal.service;

import java.time.OffsetDateTime;

/**
 * Avisos de la campana. Los records espejan el contrato CONGELADO
 * (Dtos.AlertaResponse / AtenderAlertaResponse v1).
 *
 * <p><b>La regla que ordena el modulo</b>: la alerta se ata SIEMPRE a un
 * AGENTE. El agente la ve como propia y su broker supervisor la ve a traves de
 * la supervision, asi que <b>el destinatario lo decide el TIPO, no una
 * columna</b>. No hay ni hace falta un {@code idDestinatario}.
 *
 * <p>Alcance (§6): AGENTE = las suyas; BROKER = las de sus agentes
 * supervisados; ADMIN = todo el tenant. Es el mismo {@code Alcance} del resto
 * del sistema, sin una consulta por rol como hacia la v1.
 *
 * <p>Ademas de leerse, este service es el <b>emisor</b> que usan las nueve
 * bocas del flujo comercial (§4 del contrato): captacion, solicitud,
 * documentos, evaluacion, contrato y comision avisan a traves de
 * {@link #emitir}.
 */
public interface AlertaService {

    /** Espejo de AlertaResponse. {@code ruta} es derivada, no columna. */
    record FichaAlerta(Long id, String tipo, String severidad, String entidadTipo, Long entidadId,
                       Long idAgente, String agenteNombre, String mensaje, String estado,
                       OffsetDateTime fechaGeneracion, OffsetDateTime fechaResolucion, String ruta) {
    }

    /**
     * Lo que necesita una emision. {@code idRolAgente} es el
     * {@code persona_rol.id} del agente al que se ata el aviso — que no es
     * necesariamente quien lo va a leer (ver la regla de arriba).
     */
    record DatosAlerta(String tipo, String severidad, String entidadTipo, Long entidadId,
                       Long idRolAgente, String mensaje) {
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
     * Emite un aviso. La llaman los nueve puntos del flujo comercial DENTRO de
     * su propia transaccion, asi que si la emision falla cae la operacion
     * entera — igual que en la v1. Lo unico que se tolera en silencio es la
     * falta de datos (sin agente o sin entidad no hay a quien avisar).
     */
    void emitir(DatosAlerta datos, Actor actor);

    /**
     * Barrido de recontacto vencido: crea una alerta {@code SIN_RESPUESTA} por
     * cada prospeccion en proceso que lleva 7 dias sin accion y que no tenga ya
     * una activa. Lo dispara la propia lectura de la campana (§2), porque en la
     * v1 no hay planificador.
     *
     * @return cuantas alertas nuevas se crearon
     */
    int sincronizarRecontacto(Actor actor);
}
