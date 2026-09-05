package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <b>El cierre de una VENTA</b> (WP-A, D-N-3).
 *
 * <p>Es el gemelo de {@code ContratoService.registrar} por el lado de la
 * compraventa, y no el mismo caso de uso: el alquiler cierra con
 * {@code solicitud aprobada -> contrato de alquiler} y la venta cierra con
 * {@code oferta aceptada -> contrato de compraventa}. Los dos escriben el hito
 * {@code C} de la serie de su encargo y los dos cierran el encargo; lo que
 * cambia es de donde sale el importe y que documento lo respalda.
 *
 * <h2>Lo que hace, en una transaccion</h2>
 * <ol>
 *   <li>el contrato de compraventa —encargo, mesa, oferta, comprador, importe
 *       pactado y, si difiere, el declarado en la escritura—;</li>
 *   <li>el hito {@code C} en {@code precio_propiedad}, con su encargo y su
 *       oferta;</li>
 *   <li>la oportunidad pasa a FINALIZADA EXITOSA;</li>
 *   <li>el encargo se cierra con motivo {@code V} (vendido);</li>
 *   <li>el inmueble sale del mercado y sus anuncios se cierran.</li>
 * </ol>
 *
 * <h2>Lo que NO hace, y esta escrito porque es una decision</h2>
 * <ul>
 *   <li><b>No cierra las demas oportunidades vivas del encargo.</b> Cada una se
 *       cae con SU motivo, registrado por el agente: cerrarlas en cascada
 *       inventaria siete «se vendio a otro» y perderia el «no consiguio
 *       financiamiento» del que si aporta.</li>
 *   <li><b>No transfiere la titularidad</b> de la propiedad al comprador. Quien
 *       es titular despues de una venta es un hecho registral con su propia
 *       fecha y su propia evidencia; deducirlo de un contrato firmado seria
 *       inventar una inscripcion.</li>
 *   <li><b>No liquida comision.</b> {@code comision_liquidacion} cuelga por
 *       clave ajena de {@code contrato_alquiler}; la comision de una venta se
 *       calcula sobre otra base y es una decision aparte.</li>
 * </ul>
 */
public interface CierreVentaService {

    /**
     * @param idOferta        la oferta ACEPTADA que se firma. Obligatoria: sin
     *                        ella no se sabe que cifra se cerro
     * @param montoPactado    lo que de verdad se acordo. {@code null} = el de la
     *                        oferta aceptada; se declara solo si hubo ajuste
     * @param moneda          la del importe pactado. {@code null} = la de la oferta
     * @param montoDeclarado  lo que consta en la escritura, SOLO si difiere.
     *                        {@code null} = no se declaro, y eso NO es «coincide»
     * @param monedaDeclarada la del importe declarado. Va con el o no va ninguno
     * @param fechaFirma      {@code null} = hoy. No puede ser futura
     */
    record DatosCierreVenta(Long idOferta, BigDecimal montoPactado, String moneda,
                            BigDecimal montoDeclarado, String monedaDeclarada,
                            LocalDate fechaFirma, String observaciones) {
    }

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

    /** Cierra la venta de un encargo con la oferta aceptada de una de sus mesas. */
    FichaCierreVenta cerrar(long idCaptacion, DatosCierreVenta datos, Actor actor);

    /** El cierre ya registrado de un encargo, si lo hay. */
    FichaCierreVenta obtenerDelEncargo(long idCaptacion, Actor actor);
}
