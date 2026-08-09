package com.controllocal.service;

import java.time.LocalDateTime;

/**
 * Casos de uso de la oportunidad comercial: la entidad HUB del proceso. Los
 * records espejan el contrato CONGELADO (Dtos.OportunidadRequest/Response v1).
 *
 * <p>Maquina: A Abierta -> S Solicitud creada -> {F exitosa | X no favorable};
 * A -> N No continua. F3 solo produce A y N; S/F/X los produce la vertical de
 * solicitudes (F4). Toda transicion pasa por soporte/Transiciones.
 *
 * <p>Alcance (§4): AGENTE = las suyas; ADMIN = todas las del tenant;
 * BROKER = las de SUS CAPTACIONES, no las de sus agentes. Es distinto del
 * alcance de interacciones a proposito.
 */
public interface OportunidadService {

    /** Espejo de OportunidadRequest. Si codigoOportunidad viene vacio se autogenera. */
    record DatosOportunidad(String codigoOportunidad, Long idCliente, Long idCaptacion,
                            String observaciones, Long idPublicacionOrigen) {
    }

    /** Espejo de OportunidadResponse. */
    record FichaOportunidad(Long id, String codigoOportunidad, Long idCliente, String clienteNombre,
                            Long idCaptacion, String codigoCaptacion, String direccionLocal,
                            String distritoLocal, Long idAgente, String agenteNombre, String estado,
                            LocalDateTime fechaRegistro, String motivoCierre, String observaciones,
                            LocalDateTime fechaCierre, LocalDateTime fechaActualizacion,
                            Long idPublicacionOrigen) {
    }

    /**
     * KPI de la bandeja por estado. Extension aditiva del v2: los cubos se
     * cuentan en la BASE sobre el mismo conjunto que pagina {@link #listar},
     * porque derivarlos de una pagina solo cuenta lo visible.
     *
     * <p>No recibe {@code estado} a proposito: es lo que devuelve, no lo que
     * filtra.
     */
    record ResumenOportunidades(long total, long abiertas, long conSolicitud, long noContinuan,
                                long exitosas, long noFavorables) {
    }

    Pagina<FichaOportunidad> listar(int pagina, int tamano, Long idCaptacion, Long idCliente,
                                    String estado, String query, Actor actor);

    ResumenOportunidades resumen(Long idCaptacion, Long idCliente, String query, Actor actor);

    FichaOportunidad obtener(long id, Actor actor);

    /** Alta: la captacion indicada debe ser del agente que registra (si no, 403). */
    FichaOportunidad registrar(DatosOportunidad datos, Actor actor);

    /** Cierre A->N con la razon tipificada (MotivoNoContinuidad) y el agente. */
    FichaOportunidad noContinuidad(long id, String razon, String observaciones, Actor actor);

    /**
     * Existe en el cable y SIEMPRE responde 400: el cierre exitoso lo produce
     * la solicitud aprobada (F4). Se replica tal cual, no se "arregla".
     */
    FichaOportunidad cierreExitoso(long id, Actor actor);
}
