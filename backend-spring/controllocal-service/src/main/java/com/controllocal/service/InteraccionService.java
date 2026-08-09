package com.controllocal.service;

import java.time.LocalDateTime;

/**
 * Casos de uso de la bitacora POLIMORFICA de contacto comercial. Los records
 * espejan el contrato CONGELADO (Dtos.InteraccionRequest/Response de la v1).
 *
 * <p>Cada interaccion cuelga de UNA de cuatro entidades segun {@code contexto}
 * (OPORTUNIDAD | PROSPECCION | CAPTACION | CLIENTE) —lo garantiza un CHECK de
 * la BD— y el vocabulario de {@code resultado} depende de ese contexto (§6).
 *
 * <p>Alcance (§6): ADMIN = todo; BROKER = las de sus AGENTES SUPERVISADOS;
 * AGENTE = las suyas. Se filtra por el agente responsable de la interaccion,
 * no por la entidad de la que cuelga — y por eso es una regla distinta de la
 * de oportunidades y visitas, que alcanzan por captacion.
 */
public interface InteraccionService {

    /** Espejo de InteraccionRequest. */
    record DatosInteraccion(String contexto, Long idOportunidad, Long idProspeccion, Long idCaptacion,
                            Long idCliente, String canalContacto, String resultado, String observaciones,
                            String transcripcionNota) {
    }

    /** Filtros del GET / (cable v1): solo se admite UN filtro de entidad a la vez. */
    record FiltrosInteraccion(String contexto, Long idOportunidad, Long idProspeccion, Long idCaptacion,
                              Long idCliente, String grupo, String resultado, String canal, String q,
                              int pagina, int tamano) {
    }

    /** Espejo de InteraccionResponse. */
    record FichaInteraccion(Long id, String contexto, Long idOportunidad, Long idProspeccion,
                            Long idCaptacion, Long idCliente, Long idPropietario, String codigoProspeccion,
                            LocalDateTime fechaHora, String canalContacto, String resultado,
                            String observaciones, String transcripcionNota, String clienteNombre,
                            String propietarioNombre, String personaTipo, String personaNombre,
                            String codigoCaptacion, String agenteNombre) {
    }

    Pagina<FichaInteraccion> listar(FiltrosInteraccion filtros, Actor actor);

    FichaInteraccion obtener(long id, Actor actor);

    /**
     * Alta. Ademas de la bitacora, una interaccion de PROSPECCION hace avanzar
     * el embudo del propietario segun su resultado (cable v1).
     */
    FichaInteraccion registrar(DatosInteraccion datos, Actor actor);

    /** Solo cambia {@code resultado} y {@code observaciones}; el contexto no se mueve. */
    FichaInteraccion actualizar(long id, DatosInteraccion datos, Actor actor);
}
