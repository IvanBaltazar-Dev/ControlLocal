package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;

/**
 * RF058/059/CAT-E25: preparación distinta del hito contractual confirmado.
 * La confirmación competente produce los efectos del alquiler; la comisión
 * conserva su cálculo, devengo y fondos en su único caso de uso.
 */
public interface ContratoService {

    /** Efecto interno de un contrato realmente firmado y confirmado por RF059/060. */
    long confirmarHito(com.controllocal.service.formalizacion.HitosConfirmados.ContextoEconomico contexto,
            com.controllocal.service.formalizacion.HitosConfirmados.FuenteHitoConfirmado hito,
            com.controllocal.service.formalizacion.HitosConfirmados.FuenteEconomicaFinal fuente,
            com.controllocal.service.formalizacion.FormalizacionService.AcuerdoFinalDocumentado revisado,Actor actor,
            PreparacionContrato preparacionRevisada);

    /**
     * Preparación P sobre la solicitud real; no recibe una firma, un estado
     * confirmado ni una fecha de cierre por defecto.
     */
    record DatosContrato(Long idSolicitud,String incidencias) {
    }

    /**
     * Una sola proyección financiera autorizada; no confunde devengo con cobro.
     */
    record FichaContrato(Long id, Long idSolicitud, String codigoSolicitud, Long idOportunidad,
                         String codigoOportunidad, String clienteNombre, String direccionLocal,
                         String distritoLocal, String estadoDisponibilidadLocal,
                         String codigoCaptacion, String agenteNombre,
                         BigDecimal rentaMensual, String moneda, Integer plazoContratoMeses,
                         LocalDate fechaInicioContrato,
                         LocalDate fechaFinContrato, LocalDate fechaCierre, String estadoContrato,
                         String incidencias, Long agenteId,Long propietarioId, String propietarioNombre,
                         Long idContratoAnterior, ComisionService.FichaComision comision) {
    }
    /** P es preparación; la huella describe datos, nunca concede autoridad ni representa una firma. */
    record PreparacionContrato(long id,String estado,long encargo,long oportunidad,Long solicitud,
                                Long contratoAnterior,BigDecimal rentaContractual,String moneda,
                                LocalDate inicio,LocalDate fin,String huella) { }

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
    record ImportePorMoneda(String moneda, BigDecimal monto,long determinados,long pendientes) {
    }

    record ResumenCierres(long cierres,long cierresConLecturaFinanciera,long sinEvaluacion,List<ImportePorMoneda> calculado,
                          List<ImportePorMoneda> devengadoAcreditado,List<ImportePorMoneda> ingresosRealmenteRecibidos,
                          List<ImportePorMoneda> saldoInformativoSobreCalculo) {
    }

    ResumenCierres resumenCierres(FiltrosContrato filtros, Actor actor);

    record AgenteConCierres(long id, String nombre) {
    }

    /** Distritos y agentes presentes en los cierres del alcance (filtros data-driven). */
    List<String> distritosDeCierres(FiltrosContrato filtros, Actor actor);

    List<AgenteConCierres> agentesDeCierres(FiltrosContrato filtros, Actor actor);

    FichaContrato obtenerPorOportunidad(long idOportunidad, Actor actor);

    /** Consulta por identidad del contrato con la misma guarda de sus actuaciones. */
    FichaContrato obtener(long idContrato, Actor actor);

    /** Avance privado sin cierre; el hito se informa y confirma por Formalización. */
    FichaContrato iniciarEnProceso(DatosContrato datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);

    record DatosTransicion(LocalDate fechaEfectiva, String motivo) { }

    record DatosRenovacion(LocalDate fechaInicioContrato, LocalDate fechaFinContrato,
                           BigDecimal rentaContractual, String moneda, String motivo) { }

    FichaContrato activar(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato finalizar(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato rescindir(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato anular(long idContrato, DatosTransicion datos, Actor actor);
    FichaContrato renovar(long idContrato,DatosRenovacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);

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
