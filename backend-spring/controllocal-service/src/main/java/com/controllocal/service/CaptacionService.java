package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso de captacion (F2). Records espejo del contrato CONGELADO
 * (Dtos.CaptacionRequest/CaptacionResponse/ReasignacionCaptacionResponse v1).
 * Maquina P->{A|O|R}, O->(editar)->P, A->{C|V} sobre soporte/Transiciones
 * (auditada); la reasignacion es un evento de actor (tabla-evento), no una
 * transicion. El alcance por rol se aplica en el service.
 */
public interface CaptacionService {

    /** Espejo de CaptacionRequest. idLocal = propiedad; idAgente lo fija el actor (agente). */
    record DatosCaptacion(String codigoCaptacion, LocalDate fechaCaptacion, LocalDate fechaInicioVigencia,
                          LocalDate fechaFinVigencia, BigDecimal comisionPactada, String observaciones,
                          Long idLocal, Long idAgente, String motivoOperacion, Integer urgencia,
                          Boolean exclusividad, String tipoOperacion, BigDecimal importeReferencia,
                          String monedaReferencia, String tipoComision, String baseCalculo,
                          BigDecimal valorComision, String monedaComision, String tratamientoIgv,
                          String motivoSinComision) {
        public DatosCaptacion(String codigoCaptacion, LocalDate fechaCaptacion,
                              LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                              BigDecimal comisionPactada, String observaciones, Long idLocal,
                              Long idAgente, String motivoOperacion, Integer urgencia,
                              Boolean exclusividad) {
            this(codigoCaptacion, fechaCaptacion, fechaInicioVigencia, fechaFinVigencia,
                    comisionPactada, observaciones, idLocal, idAgente, motivoOperacion,
                    urgencia, exclusividad, null, null, null, null, null, null, null, null, null);
        }
    }

    /** Espejo de CaptacionResponse (idAgente/idBrokerRevisor = persona_rol.id de esos roles). */
    record FichaCaptacion(Long id, String codigoCaptacion, LocalDate fechaCaptacion,
                          LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                          BigDecimal comisionPactada, String observaciones, String estado,
                          String motivoOperacion, Integer urgencia, Boolean exclusividad,
                          String observacionRevision, LocalDateTime fechaRevision, Long idLocal,
                          String direccionLocal, String distritoLocal, BigDecimal areaM2, String rubro,
                          String propietarioNombre, Long idAgente, String agenteNombre,
                          Long idBrokerRevisor, String fotoPortadaClave, String tipoOperacion,
                          BigDecimal importeReferencia, String monedaReferencia,
                          String tipoComision, String baseCalculo, BigDecimal valorComision,
                          String monedaComision, String tratamientoIgv, String motivoSinComision,
                          LocalDate fechaCierre, String motivoCierre, String detalleMotivoCierre) {
    }

    /** Espejo de ReasignacionCaptacionResponse. */
    record FichaReasignacion(Long idReasignacion, Long idCaptacion, String codigoCaptacion,
                             String direccionLocal, Long idAgenteAnterior, String agenteAnteriorNombre,
                             Long idAgenteNuevo, String agenteNuevoNombre, Long idBroker,
                             String brokerNombre, LocalDateTime fechaCambio, String motivo) {
    }

    /**
     * Filtros opcionales del listado Angular. Son una extension aditiva: si
     * se omiten, {@code GET /captaciones} conserva exactamente el resultado
     * del cable v1 (mismo alcance, orden y paginacion).
     */
    record FiltrosCaptacion(String estado, Long idAgente, String q,
                            int pagina, int tamano) {
    }

    /**
     * Filtros aditivos de la bandeja de revision. Sin filtros conserva el
     * cable v1: todas las P/O dentro del alcance del broker o administrador.
     */
    record FiltrosPendientes(String estado, Long idAgente, String q,
                             int pagina, int tamano) {
    }

    Pagina<FichaCaptacion> listar(FiltrosCaptacion filtros, Actor actor);

    /**
     * Cartera del equipo vista POR INMUEBLE: una fila por propiedad captada,
     * con los datos de su captacion mas reciente.
     *
     * <p>Extension aditiva (no toca el contrato congelado). Existe porque la
     * pantalla del broker necesita deduplicar por inmueble, y eso no se puede
     * hacer sobre una pagina de captaciones: obligaria a descargar todas.
     * Filtro, orden, deduplicacion, paginacion y conteo bajan a SQL.
     */
    record PropiedadEquipo(Long idPropiedad, Long idCaptacion, String codigoCaptacion, String estado,
                           String codigoLocal, String direccion, String distrito, String rubro,
                           BigDecimal areaM2, Long idAgente, String agenteNombre) {
    }

    record ResumenEquipo(long propiedades, long conCaptacionActiva, long agentesConCartera,
                         long distritos) {
    }

    record FiltrosEquipo(String texto, String distrito, int pagina, int tamano) {
    }

    Pagina<PropiedadEquipo> carteraDelEquipo(FiltrosEquipo filtros, Actor actor);

    ResumenEquipo resumenCarteraDelEquipo(String texto, Actor actor);

    /** Distritos presentes en la cartera del equipo, para el filtro data-driven. */
    List<String> distritosDelEquipo(String texto, Actor actor);

    /** Bandeja del broker/admin: captaciones pendientes de revision (P u O). */
    Pagina<FichaCaptacion> pendientes(FiltrosPendientes filtros, Actor actor);

    /** Captaciones ACTIVAS en el alcance del broker/admin (candidatas a reasignar). */
    Pagina<FichaCaptacion> reasignables(int pagina, int tamano, String q, Actor actor);

    FichaCaptacion obtener(long id, Actor actor);

    FichaCaptacion obtenerPorCodigo(String codigo, Actor actor);

    FichaCaptacion registrar(DatosCaptacion datos, Actor actor);

    FichaCaptacion actualizar(long id, DatosCaptacion datos, Actor actor);

    /** Decision del broker: accion APROBAR/OBSERVAR/RECHAZAR (o A/O/R). */
    FichaCaptacion decidir(long id, String accion, String observacion, Actor actor);

    FichaCaptacion reasignar(long id, Long idAgenteNuevo, String motivo, Actor actor);

    FichaCaptacion cerrar(long id, String motivo, Actor actor);

    /** Cierre causal usado por la cascada contractual. */
    FichaCaptacion cerrarPorContrato(long id, LocalDate fecha, Actor actor, String detalle);

    /** Historial de reasignaciones (gobierno broker/admin), el mas reciente primero. */
    List<FichaReasignacion> listarReasignaciones(Actor actor);
}
