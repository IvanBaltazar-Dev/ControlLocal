package com.controllocal.service;

import com.controllocal.service.soporte.Procedencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <b>La negociacion: la oferta, la contraoferta y su desenlace</b> (WP-A,
 * D-N-1/D-N-2).
 *
 * <p>Una mesa es una OPORTUNIDAD —un interesado concreto frente a un encargo
 * concreto—. Dos interesados por el mismo inmueble son dos mesas y dos cadenas
 * separadas.
 *
 * <h2>Autoridad</h2>
 * Escribir en la mesa es <b>operar el encargo</b>, asi que la decide
 * {@code AutoridadDePropiedad.exigirEdicionDelEncargo}: el agente de ESE
 * encargo. No la sustituye ser responsable de la propiedad, y el gobierno del
 * tenant no la hereda —gobernar no es operar—. Leer la mesa va con el alcance
 * de siempre: el agente lo suyo, el broker por captacion supervisada, el
 * TENANT_ADMIN su tenant.
 *
 * <h2>Lo que NO hace</h2>
 * Rechazar la ultima oferta <b>no</b> cierra la oportunidad. Que el propietario
 * diga que no a una cifra no significa que el interesado se haya ido; la caida
 * es un acto aparte, con su motivo tipificado
 * ({@code POST /oportunidades/{id}/no-continuidad}).
 */
public interface OfertaService {

    /**
     * Contra que precio se ofrecio, congelado al presentar (D-N-1).
     *
     * <p>{@code hito} es {@code "P"} —el ultimo publicado del encargo— o
     * {@code "U"} —el ultimo autorizado, cuando no hubo anuncio—. Cuando el
     * encargo no tiene ninguno de los dos, <b>los cuatro campos llegan
     * nulos</b>: la referencia es FALTANTE y se dice, no se rellena con el
     * importe del encargo ni con el de la propia oferta.
     */
    record ReferenciaDeLaOferta(String hito, String hitoRotulo, Long idPrecio,
                                BigDecimal monto, String moneda) {
    }

    /** Espejo de una fila de la mesa, ya rotulada: el cliente no traduce codigos. */
    record FichaOferta(Long id, Long idOportunidad, Long idCaptacion, Integer ronda,
                       String presentadaPor, String presentadaPorRotulo,
                       BigDecimal monto, String moneda,
                       String estado, String estadoRotulo,
                       String condiciones, LocalDate fecha,
                       Long idRolRegistra, String origen,
                       ReferenciaDeLaOferta referencia,
                       LocalDateTime fechaRegistro) {
    }

    /**
     * Que puede hacer el actor sobre ESTA mesa, resuelto en el Core.
     *
     * <p>Cada bandera sale del <b>mismo predicado que despues deniega</b> el
     * comando, no de una segunda tabla de decision: una copia de una regla de
     * autoridad en la pantalla diverge siempre hacia el lado que concede de mas,
     * y un boton activo que el backend rechaza es el peor error de esta
     * superficie.
     */
    record CapacidadesDeLaMesa(boolean puedeOfertar, boolean puedeContraofertar,
                               boolean puedeAceptar, boolean puedeRechazar,
                               boolean puedeRetirar, boolean puedeCerrarVenta,
                               boolean puedeRegistrarCaida) {
    }

    /** Lo que hace falta para presentar una oferta o una contraoferta. */
    record DatosOferta(BigDecimal monto, String moneda, String presentadaPor,
                       String condiciones, LocalDate fecha) {
    }

    /** La cadena completa de una mesa, en el orden en que ocurrio. */
    List<FichaOferta> listar(long idOportunidad, Actor actor);

    /** Las capacidades del actor sobre la mesa, con las guardas de los comandos. */
    CapacidadesDeLaMesa capacidades(long idOportunidad, Actor actor);

    /**
     * La primera cifra de una vuelta nueva. Exige que <b>no</b> haya ninguna
     * oferta viva: mientras hay una sobre la mesa, la siguiente es una
     * contraoferta que la supera y no una segunda oferta paralela.
     */
    FichaOferta presentar(long idOportunidad, DatosOferta datos, Procedencia procedencia, Actor actor);

    /**
     * La respuesta con otra cifra: marca la anterior SUPERADA y abre la ronda
     * siguiente desde el <b>lado contrario</b>, que no se declara porque se
     * deduce de quien presento la que se responde.
     */
    FichaOferta contraofertar(long idOportunidad, long idOferta, DatosOferta datos,
                              Procedencia procedencia, Actor actor);

    /**
     * El si. Una mesa admite <b>una sola</b> oferta aceptada; la segunda
     * responde 409 y lo corta el indice, no solo la comprobacion previa.
     */
    FichaOferta aceptar(long idOportunidad, long idOferta, String motivo, Actor actor);

    /** El no a una cifra. NO cierra la oportunidad. */
    FichaOferta rechazar(long idOportunidad, long idOferta, String motivo, Actor actor);

    /** Quien la presento se echa atras antes de que le respondan. */
    FichaOferta retirar(long idOportunidad, long idOferta, String motivo, Actor actor);
}
