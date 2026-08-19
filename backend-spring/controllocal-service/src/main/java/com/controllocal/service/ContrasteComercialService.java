package com.controllocal.service;

import com.controllocal.service.soporte.Contraste;
import com.controllocal.service.soporte.MediasPropias;

import java.math.BigDecimal;

/**
 * Situar un dato contra la operacion de la propia casa.
 *
 * <h2>Lo que este servicio no hace, y no es una limitacion temporal</h2>
 *
 * <p>No dice nada del <b>mercado</b>. Ni del sector, ni de la industria, ni de
 * lo que cobra la competencia. BROX distingue tres escalas —el objeto, el
 * portafolio y el mercado— y aqui viven las dos primeras: este inmueble, contra
 * la cartera de esta corredora. Afirmar la tercera necesitaria una cobertura que
 * no existe, y afirmarla sin ella seria exactamente el salto que el producto
 * existe para no dar.
 *
 * <p>Por eso el contraste degrada en vez de rellenarse. Un rango construido con
 * dos propiedades tiene la misma forma que uno construido con doscientas, y solo
 * la N los distingue: publicar el primero convertiria dos filas propias en una
 * supuesta senal de mercado.
 */
public interface ContrasteComercialService {

    /**
     * Donde cae una renta dentro del rango de <b>nuestras</b> propiedades
     * comparables: misma zona, mismo tramo de metraje y misma moneda.
     *
     * <p>Devuelve un rango solo si hay bastantes propiedades distintas
     * ({@code PoliticaComercial.RANGO_MUESTRA_MINIMA}). Si no, devuelve la
     * degradacion <b>con su N</b>, para poder decir «3 propiedades en Miraflores,
     * pocas para un rango» en vez de un silencio que no explica nada.
     */
    Contraste rangoDeRenta(long idOrganizacion, String zona, BigDecimal metraje,
                           BigDecimal renta, String moneda);

    /**
     * Las tres medias propias, cada una con su muestra y degradando por separado.
     *
     * <p>Que exista una no dice nada sobre las otras dos.
     */
    MediasPropias mediasDe(Actor actor);
}
