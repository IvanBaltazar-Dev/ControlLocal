package com.controllocal.service;

import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Liquidacion de la comision del contrato: nace con el cierre y la cobra
 * despues el broker supervisor. Es el unico sitio donde vive la aritmetica de
 * la comision (bruta, reparto agente/empresa).
 *
 * <p>Aqui NO hay alcance por rol: la liquidacion cuelga 1:1 del contrato, y
 * quien decide si el actor puede tocarla es {@link ContratoService}, que es
 * el que conoce la regla de alcance por CAPTACION. Este service asume que el
 * contrato ya viene autorizado, igual que hacia {@code ContratosRest} con su
 * BL.
 *
 * <p>Estado y forma de pago viajan con el <b>NOMBRE</b> del enum
 * (PENDIENTE/PARCIAL/COBRADA/ANULADA, TRANSFERENCIA/...), no como CHAR(1):
 * es una de las dos rupturas de la convencion de esta vertical (§1).
 */
public interface ComisionService {

    /** Proyeccion de la liquidacion; el filtro de montos netos por rol lo aplica ContratoService. */
    record FichaComision(Long id, Long idContrato, BigDecimal monto, String moneda,
                         BigDecimal montoAgente, BigDecimal montoEmpresa, LocalDate fechaCobro,
                         String formaPago, String estado, BigDecimal montoCobrado,
                         BigDecimal saldoCobro, BigDecimal montoPagadoAgente,
                         BigDecimal saldoPagoAgente) {
        public FichaComision(Long id, Long idContrato, BigDecimal monto, String moneda,
                             BigDecimal montoAgente, BigDecimal montoEmpresa, LocalDate fechaCobro,
                             String formaPago, String estado) {
            this(id, idContrato, monto, moneda, montoAgente, montoEmpresa, fechaCobro,
                    formaPago, estado, BigDecimal.ZERO,
                    monto == null ? BigDecimal.ZERO : monto,
                    BigDecimal.ZERO,
                    montoAgente == null ? BigDecimal.ZERO : montoAgente);
        }
    }

    /**
     * Efecto 2 de la cascada del cierre (§6): la liquidacion nace PENDIENTE
     * con la comision BRUTA = {@code comisionPactada} % de un mes de renta, en
     * la moneda declarada de esa renta. El reparto agente/empresa queda para despues.
     */
    FichaComision crearPendiente(ContratoAlquiler contrato, BigDecimal comisionPactada,
                                 BigDecimal renta, String moneda, Actor actor);

    FichaComision crearPendienteNormalizada(ContratoAlquiler contrato,
                                            CondicionEconomicaCaptacion condicion,
                                            BigDecimal renta, String moneda, Actor actor);

    Optional<FichaComision> porContrato(long idContrato, Actor actor);

    /** Una sola lectura para toda una pagina de contratos (sin N+1). */
    Map<Long, FichaComision> porContratos(Collection<Long> idsContrato, Actor actor);

    /** El broker define el monto del agente; el de la empresa se calcula solo. */
    FichaComision asignarMontoAgente(long idContrato, BigDecimal montoAgente, Actor actor);

    /** Desenlace del cobro: COBRADA (con fecha y forma de pago) o ANULADA. */
    FichaComision registrarCobro(long idContrato, String estado, LocalDate fechaCobro,
                                 String formaPago, Actor actor);

    /**
     * Tipos que se aceptan como COMANDO. {@code A} (ajuste) <b>no esta</b>.
     *
     * <p>Nacio en V15 como una cuarta letra del CHECK y nunca tuvo regla: no
     * hay decision aprobada que diga que saldo modifica, si suma o resta,
     * contra que tope, como afecta a {@code P/R/C}, como se revierte, ni como
     * entra en KPI y auditoria. Tampoco existe en la v1 —el backend legado no
     * tiene {@code comision_movimiento}—, asi que no es una obligacion del
     * contrato congelado. Mientras se acepto, se persistia como evidencia
     * economica y no movia ningun saldo: un 200 que no cambiaba nada.
     *
     * <p>Se retira del comando y se responde 400. El CHECK de la base sigue
     * admitiendo {@code 'A'} A PROPOSITO: si aparecieran filas historicas, se
     * conservan. Volvera a aceptarse el dia que exista una definicion
     * funcional inequivoca, no antes.
     */
    Set<String> TIPOS_ACEPTADOS = Set.of("C", "P", "R");

    /**
     * Movimiento real: C cobro, P pago al agente, R reversion.
     *
     * @param claveIdempotencia {@code Idempotency-Key} del comando, o
     *     {@code null}. Con clave, reenviar el MISMO comando devuelve el
     *     resultado original sin insertar otra fila, y reenviarla con un
     *     comando distinto es un 409.
     */
    FichaComision registrarMovimiento(long idContrato, String tipo, BigDecimal monto,
                                      String moneda, LocalDate fecha, String formaPago,
                                      String observacion, String claveIdempotencia, Actor actor);

    /**
     * Cascada del contrato ANULADO. La liquidacion nace al FIRMAR y el contrato
     * firmado todavia se puede anular, asi que sin esta cascada quedaba una
     * comision viva —cobrable y sumando en los KPI de comision generada— de un
     * contrato que ya no existe.
     *
     * <p>Si la comision ya se COBRO no anula nada: <b>rechaza la anulacion del
     * contrato</b>. Es la misma razon por la que el grafo prohibe VIGENTE -&gt;
     * ANULADO: anular es dejar sin efecto lo que nunca lo tuvo, y si hubo
     * dinero de por medio lo que corresponde es rescindir.
     *
     * <p>Sin liquidacion (contrato aun EN_PROCESO) no hace nada.
     */
    void anularPorContratoAnulado(long idContrato, Actor actor);
}
