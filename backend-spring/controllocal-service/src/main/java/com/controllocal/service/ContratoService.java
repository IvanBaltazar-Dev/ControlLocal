package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso del contrato de alquiler, la vertical que CIERRA el ciclo. Los
 * records espejan el contrato CONGELADO (Dtos.ContratoRequest/Response v1).
 *
 * <p>Registrar el contrato no es un alta mas: es la <b>cascada de siete
 * efectos</b> del §6, en una sola transaccion, y es lo que cierra la
 * oportunidad como exitosa. Por eso
 * {@code POST /oportunidades/{id}/cierre-exitoso} responde 400 para siempre:
 * el cierre no lo produce un boton.
 *
 * <p>Alcance (§7): AGENTE = los suyos (via el agente de la solicitud);
 * <b>BROKER = por CAPTACION supervisada</b> —distinto del de solicitudes, que
 * es por agente—; ADMIN = todo el tenant. No unificar.
 */
public interface ContratoService {

    /**
     * Espejo de ContratoRequest. Las condiciones del trato NO viajan aqui: se
     * leen de la solicitud. El agente solo captura la formalizacion, y los
     * tres campos opcionales tienen default (hoy, VIGENTE, sin incidencias).
     */
    record DatosContrato(Long idSolicitud, LocalDate fechaCierre, String estadoContrato,
                         String incidencias) {
    }

    /**
     * Espejo de ContratoResponse. {@code montoAgente} y {@code montoEmpresa}
     * solo se rellenan para ADMIN/BROKER: el AGENTE no ve el reparto de la
     * liquidacion, solo la comision bruta, su estado y el cobro.
     */
    record FichaContrato(Long id, Long idSolicitud, String codigoSolicitud, Long idOportunidad,
                         String codigoOportunidad, String clienteNombre, String direccionLocal,
                         String distritoLocal, String estadoDisponibilidadLocal,
                         String codigoCaptacion, String agenteNombre,
                         BigDecimal rentaMensual, String moneda, Integer plazoContratoMeses,
                         BigDecimal comisionGenerada, String monedaComision,
                         LocalDate fechaInicioContrato,
                         LocalDate fechaFinContrato, LocalDate fechaCierre, String estadoContrato,
                         String comisionEstado, String incidencias, Long idComision, Long agenteId,
                         Long propietarioId, String propietarioNombre, BigDecimal montoAgente,
                         BigDecimal montoEmpresa, String formaPago, LocalDate fechaCobro,
                         Long idContratoAnterior, BigDecimal montoCobrado, BigDecimal saldoCobro,
                         BigDecimal montoPagadoAgente, BigDecimal saldoPagoAgente) {
    }

    /** Ojo con el cable: el {@code tamano} por defecto de este recurso es 100, no 10. */
    Pagina<FichaContrato> listar(int pagina, int tamano, Actor actor);

    /**
     * Filtros ADITIVOS del listado de cierres. Con todo en null (u {@code orden}
     * ausente) responde exactamente lo que respondia antes de que existieran,
     * asi que el cable congelado de {@code GET /contratos} no cambia.
     *
     * @param orden {@code "cierre"} ordena por fecha de cierre descendente
     *              (lo que la pantalla de cierres necesita); cualquier otro
     *              valor, incluido null, conserva el orden congelado por id.
     */
    record FiltrosContrato(String texto, String distrito, Long idAgente, String orden,
                           int pagina, int tamano) {
    }

    Pagina<FichaContrato> listar(FiltrosContrato filtros, Actor actor);

    /** Los tres KPI de la pantalla de cierres, calculados en la base. */
    record ImportePorMoneda(String moneda, BigDecimal monto) {
    }

    record ResumenCierres(long cierres, List<ImportePorMoneda> comisionesGeneradas,
                          List<ImportePorMoneda> montosCobrados,
                          List<ImportePorMoneda> saldosPendientes,
                          List<ImportePorMoneda> montosPagadosAgente,
                          List<ImportePorMoneda> saldosPendientesAgente,
                          long porLiquidar, long sinLiquidacion) {
    }

    ResumenCierres resumenCierres(FiltrosContrato filtros, Actor actor);

    record AgenteConCierres(long id, String nombre) {
    }

    /** Distritos y agentes presentes en los cierres del alcance (filtros data-driven). */
    List<String> distritosDeCierres(FiltrosContrato filtros, Actor actor);

    List<AgenteConCierres> agentesDeCierres(FiltrosContrato filtros, Actor actor);

    FichaContrato obtenerPorOportunidad(long idOportunidad, Actor actor);

    /** Alta del AGENTE sobre una solicitud APROBADA suya: dispara la cascada del §6. */
    FichaContrato registrar(DatosContrato datos, Actor actor);

    /** Alta previa sin cerrar la operacion; luego debe firmarse y activarse. */
    FichaContrato iniciarEnProceso(DatosContrato datos, Actor actor);

    record DatosTransicion(LocalDate fechaEfectiva, String motivo) { }

    record DatosRenovacion(LocalDate fechaInicioContrato, LocalDate fechaFinContrato,
                           BigDecimal rentaContractual, String moneda, String motivo) { }

    FichaContrato firmar(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato activar(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato finalizar(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato rescindir(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato anular(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato renovar(long idContrato, DatosRenovacion datos, Actor actor);

    /** Gate de BROKER (sin ADMIN): define el monto del agente sobre un contrato que supervisa. */
    FichaContrato asignarComision(long idContrato, BigDecimal montoAgente, Actor actor);

    /** Gate de BROKER (sin ADMIN): registra el desenlace del cobro. */
    FichaContrato registrarCobroComision(long idContrato, String estado, LocalDate fechaCobro,
                                         String formaPago, Actor actor);

    /**
     * Comando monetario idempotente. Con {@code Idempotency-Key}, reenviar el
     * MISMO comando devuelve el resultado original sin insertar otra fila.
     */
    FichaContrato registrarMovimientoComision(long idContrato, String tipo, BigDecimal monto,
                                               String moneda, LocalDate fecha, String formaPago,
                                               String observacion, String claveIdempotencia,
                                               Actor actor);

    /**
     * El trabajo transaccional del anterior. Publico en la interfaz porque la
     * relectura tras perder la carrera de idempotencia tiene que entrar por el
     * PROXY para abrir una transaccion nueva: la anterior quedo marcada como
     * rollback-only por la violacion de unicidad. No la llames directamente.
     */
    FichaContrato registrarMovimientoComisionEnTransaccion(
            long idContrato, String tipo, BigDecimal monto, String moneda, LocalDate fecha,
            String formaPago, String observacion, String claveIdempotencia, Actor actor);

    /**
     * Resultado de una revision de disponibilidad. {@code repetida} distingue
     * la respuesta idempotente —el contrato ya se habia revisado con la misma
     * decision— de la que acaba de ejecutarse.
     */
    record FichaRevisionDisponibilidad(Long id, Long idContrato, Long idPropiedad,
                                       String disponibilidadAnterior, String disponibilidadNueva,
                                       String resultado, String motivo, LocalDate fechaRevision,
                                       boolean repetida) { }

    /**
     * <b>§7.3.2 — recuperacion de disponibilidad.</b> Terminar un contrato no
     * devuelve el local al mercado: lo deja ALQUILADO con una tarea de
     * revision, y esta operacion es la decision humana que la cierra.
     *
     * @param resultado {@code VOLVER_AL_MERCADO} (disponibilidad {@code D}) o
     *     {@code RETIRAR_DEL_MERCADO} ({@code T}). El cliente manda el
     *     resultado funcional; la letra la pone el backend.
     * @param motivo obligatorio.
     */
    FichaRevisionDisponibilidad revisarDisponibilidad(long idContrato, String resultado,
                                                      String motivo, Actor actor);
}
