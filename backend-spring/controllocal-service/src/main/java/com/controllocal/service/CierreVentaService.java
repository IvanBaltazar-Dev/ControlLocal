package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** RF059/060: efecto y consulta de compraventa desde la formalizacion confirmada.
 * El hito conserva fecha, fuente y acuerdo revisado. La evaluacion de comision tiene
 * su propia responsabilidad en la misma transaccion; no acredita dinero recibido.
 * No modifica titularidad ni resuelve otras mesas por inferencia.
 */
public interface CierreVentaService {

    /** Espejo del contrato de compraventa, campo a campo. */
    record FichaCierreVenta(Long id, Long idCaptacion, String codigoCaptacion,
                            Long idOportunidad, String codigoOportunidad, Long idOferta,
                            Integer rondaOferta, Long idPropiedad, String direccionPropiedad,
                            Long idRolCliente, String clienteNombre,
                            Long idRolAgenteCierre, Long idRolBrokerCierre,
                            BigDecimal montoPactado, String moneda,
                            BigDecimal montoDeclarado, String monedaDeclarada,
                            LocalDate fechaFirma, String observaciones,
                            LocalDateTime fechaRegistro) {
    }

    /** Efecto interno RF059/060, exclusivo del hito confirmado de formalización. */
    long confirmarHito(com.controllocal.service.formalizacion.HitosConfirmados.ContextoEconomico contexto,
            com.controllocal.service.formalizacion.HitosConfirmados.FuenteHitoConfirmado hito,
            com.controllocal.service.formalizacion.HitosConfirmados.FuenteEconomicaFinal fuente,
            com.controllocal.service.formalizacion.FormalizacionService.AcuerdoFinalDocumentado revisado,Actor actor);

    /** El cierre ya registrado de un encargo, si lo hay. */
    FichaCierreVenta obtenerDelEncargo(long idCaptacion, Actor actor);
}
