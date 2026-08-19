package com.controllocal.web.dto;

import com.controllocal.service.RendimientoComercialService;

import java.math.BigDecimal;

/**
 * «Puede cerrarse este mes»: la cifra del pie del Inicio, y es <b>determinista</b>.
 *
 * <p>Suma las solicitudes que ya alcanzaron la fase formal de cierre —aprobadas
 * por el broker—, no tienen contrato todavia y conservan la oferta vigente. Una
 * oportunidad prometedora o una visita que fue bien <b>no entran</b>: no son
 * hechos de cierre, son expectativas, y E2 no introduce probabilidad aprendida
 * ni indices disfrazados.
 *
 * <p><b>El importe conserva su moneda.</b> Si hay operaciones en mas de una,
 * {@code variasMonedas} lo dice y {@code importe} trae solo la principal: sumar
 * soles con dolares necesita un tipo de cambio, y uno que nadie declaro seria un
 * numero inventado dentro de una cifra que se presenta como hecho.
 *
 * <p>{@code esperanDecision} es la palanca del broker (D-E2-2 §8): de esa franja
 * es lo unico sobre lo que actua directamente.
 *
 * @param operaciones     cuantas cumplen las tres condiciones
 * @param importe         la suma, sin convertir; cero si no hay ninguna
 * @param moneda          la moneda de esa suma; {@code null} si no hay operaciones
 * @param variasMonedas   si hay mas de una moneda en juego
 * @param esperanDecision solicitudes en revision u observadas, que esperan al broker
 */
public record CierrePosibleResponse(int operaciones, BigDecimal importe, String moneda,
                                    boolean variasMonedas, int esperanDecision) {

    public static CierrePosibleResponse desde(RendimientoComercialService.CierrePosible c) {
        return new CierrePosibleResponse(c.operaciones(), c.importe(), c.moneda(),
                c.variasMonedas(), c.esperanDecision());
    }
}
