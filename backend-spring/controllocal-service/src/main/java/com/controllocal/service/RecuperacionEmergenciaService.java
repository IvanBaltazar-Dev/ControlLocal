package com.controllocal.service;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * <b>Nivel 3</b> de la recuperacion de acceso (V38, §9 del diseño): devolver el
 * gobierno de <b>un</b> tenant a <b>una</b> persona cuando ya no queda ningun
 * administrador operativo.
 *
 * <h2>Las cuatro cosas que lo definen</h2>
 * <ol>
 *   <li><b>No emite sesion.</b> Ni token, ni JWT, ni cookie. Cada accion es una
 *       llamada suelta que presenta el secreto de la concesion. No hay nada en
 *       lo que «entrar», y por eso esto no es una cuenta de emergencia.</li>
 *   <li><b>Dos aprobaciones estructurales.</b> Dos filas, de custodios
 *       distintos, verificadas por separado contra dos hashes de
 *       configuracion. Con una sola, la concesion sigue PENDIENTE y no
 *       autoriza nada.</li>
 *   <li><b>Tres identidades conservadas</b> (D-S0-52): los dos custodios y el
 *       operador, con la base exigiendo que sean distintas. «Quien ejecuta no
 *       custodia» no se puede aplicar sobre algo que no se guarda.</li>
 *   <li><b>Capacidad que se consume.</b> Tres acciones como maximo, una por
 *       tipo, en 30 minutos, y el consumo es atomico.</li>
 * </ol>
 *
 * <h2>Lo que NO puede hacer, y no por falta de tiempo</h2>
 * No lee ni un dato comercial, no crea personas, no fija contrasenas, no toca
 * otro tenant ni otra persona, no amplia su propio alcance y no escribe en
 * {@code historial_estado} — no produce hechos de negocio.
 */
public interface RecuperacionEmergenciaService {

    /** Lo que hace falta para abrir una concesion. Todo obligatorio. */
    record Emision(long idOrganizacion, long idPersonaObjetivo, String operador, String motivo) {
    }

    /** Estado de una concesion, para que la herramienta sepa que enseñar. */
    record Estado(long id, String estado, int aprobaciones, short accionesConsumidas,
                  short maxAcciones, OffsetDateTime expiraEn) {
    }

    /** Lo que devuelve aplicar una accion. */
    record Resultado(String tipo, boolean cambioAlgo, boolean concesionCerrada,
                     short accionesRestantes) {
    }

    /**
     * Abre la concesion en {@code PENDIENTE}. Todavia <b>no autoriza nada</b>:
     * hasta que existan las dos aprobaciones no hay ni ventana que contar.
     *
     * <p>Se rechaza si el tenant ya tiene una concesion viva —una emergencia es
     * de una persona— o si el operador coincide con alguno de los custodios.
     */
    long emitir(Emision emision);

    /**
     * Registra la aprobacion de un custodio.
     *
     * @return el <b>secreto de la concesion</b> cuando esta es la segunda
     *         aprobacion. Se devuelve <b>una sola vez</b> y no se guarda en
     *         ningun sitio: en la base queda solo su SHA-256.
     */
    Optional<String> aprobar(long idConcesion, String identificadorCustodio, char[] secreto);

    Estado consultar(long idConcesion);

    /**
     * Aplica una de las tres acciones. Consume capacidad <b>aunque el estado ya
     * cumpliera</b> lo que se pedia: lo que se gasta es el intento, no el
     * cambio — si no, la concesion podria sondear el estado de la cuenta sin
     * coste.
     *
     * <p>La accion, el consumo y la auditoria van en una <b>unica
     * transaccion</b>: si la accion falla no se consume capacidad, y si se
     * consume, quedo auditada.
     */
    Resultado aplicar(String secretoConcesion, String tipoAccion);

    /**
     * Barrido de vencidas. <b>No es el unico control de caducidad</b>: cada uso
     * comprueba la ventana por su cuenta, asi que una concesion caduca aunque
     * este barrido no llegue a ejecutarse.
     *
     * @return cuantas se cerraron
     */
    int caducarVencidas();
}
