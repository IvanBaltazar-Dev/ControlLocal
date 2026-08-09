package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso de la solicitud de alquiler: la oferta formal del interesado
 * sobre la oportunidad. Los records espejan el contrato CONGELADO
 * (Dtos.SolicitudRequest/Response v1).
 *
 * <p>Maquina: G Registrada -> E En revision -> {A Aprobada | R Rechazada |
 * O Observada}; la observada vuelve a E al reenviarla y la aprobada termina en
 * C Cerrada cuando el contrato concreta el alquiler. Quien mueve la solicitud
 * a A/R/O es la EVALUACION del broker, no este service.
 *
 * <p>Alcance (§7): AGENTE = las suyas; BROKER = por AGENTE SUPERVISADO (ojo:
 * distinto de contratos, que alcanzan por captacion); ADMIN = todo el tenant.
 */
public interface SolicitudService {

    /** Espejo de SolicitudRequest. Si codigoSolicitud viene vacio se autogenera. */
    record DatosSolicitud(String codigoSolicitud, LocalDate fechaRegistro, BigDecimal montoPropuesto,
                          String moneda,
                          String plazoTentativo, String observaciones, LocalDate fechaVigenciaOferta,
                          Long idOportunidad, Integer plazoMeses, LocalDate fechaInicio,
                          String formaPago, Integer mesesGarantia, Integer mesesAdelanto) {
    }

    /**
     * Espejo de SolicitudResponse. Los dos ultimos campos son el contador
     * "X/Y" del checklist: cuantos de los SEIS tipos requeridos ya tienen
     * documento entregado.
     */
    record FichaSolicitud(Long id, String codigoSolicitud, LocalDate fechaRegistro,
                          BigDecimal montoPropuesto, String moneda,
                          String plazoTentativo, String observaciones,
                          String estado, LocalDateTime fechaActualizacionEstado,
                          LocalDate fechaVigenciaOferta, Long idOportunidad, String codigoOportunidad,
                          Long idCliente, String clienteNombre, Long idCaptacion, String codigoCaptacion,
                          String direccionLocal, String distritoLocal, Long idAgente, String agenteNombre,
                          Integer plazoMeses, LocalDate fechaInicio, String formaPago,
                          Integer mesesGarantia, Integer mesesAdelanto,
                          int documentosEntregados, int documentosRequeridos) {
    }

    /**
     * Filtros del GET /. {@code idOportunidad} e {@code idCaptacion} son del
     * cable v1; los otros cuatro son <b>extension aditiva</b> del v2 y
     * omitidos dejan la respuesta byte a byte igual —incluido el orden
     * congelado por id descendente—.
     *
     * <p>{@code estado = "PENDIENTES"} no es un estado: es el cubo de la
     * bandeja del broker ({@code E} en revision + {@code O} observada), igual
     * que {@code GESTION} en prospecciones.
     */
    record FiltrosSolicitud(Long idOportunidad, Long idCaptacion, Long idAgente, String estado,
                            String distrito, String query, int pagina, int tamano) {
    }

    /** Un agente con solicitudes en el alcance, para el filtro data-driven. */
    record AgenteConSolicitudes(long id, String nombre) {
    }

    /**
     * KPI de la bandeja por estado + distritos y agentes del alcance. Extension
     * aditiva del v2: los contadores se calculan en la BASE sobre el mismo
     * conjunto que pagina {@link #listar}, y las dos listas llegan aqui para
     * que los selectores sean data-driven sin descargar la bandeja. El Blazor
     * derivaba las tres cosas de la cartera entera cargada en memoria.
     *
     * <p>Los filtros {@code estado}, {@code distrito} e {@code idAgente} se
     * ignoran: son justo los que este resumen acota.
     */
    record ResumenSolicitudes(long total, long registradas, long enRevision, long observadas,
                              long aprobadas, long rechazadas, long desistidas, long cerradas,
                              long pendientes, List<String> distritos,
                              List<AgenteConSolicitudes> agentes) {
    }

    Pagina<FichaSolicitud> listar(FiltrosSolicitud filtros, Actor actor);

    ResumenSolicitudes resumen(FiltrosSolicitud filtros, Actor actor);

    FichaSolicitud obtener(long id, Actor actor);

    FichaSolicitud obtenerPorCodigo(String codigo, Actor actor);

    /** Alta del AGENTE sobre una oportunidad suya. Una sola solicitud por oportunidad. */
    FichaSolicitud registrar(DatosSolicitud datos, Actor actor);

    /** El agente (re)envia a evaluacion: solo desde REGISTRADA u OBSERVADA. */
    FichaSolicitud reenviarAEvaluacion(long id, Actor actor);
}
