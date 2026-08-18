package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.util.Locale;

/**
 * <b>De donde salio la peticion que produjo un hecho</b> (V59).
 *
 * <h2>Dos preguntas, no una</h2>
 * <pre>
 *   canal   ¿por donde entro?     SPA · WHATSAPP · API · SISTEMA
 *   agente  ¿quien la formulo?    null = una persona, ella misma
 * </pre>
 * Antes viajaba un solo {@code String origen} con los valores
 * {@code UI|KAIROS|API|SISTEMA}, que mezclaba las dos: tres de ellos decian por
 * donde y el cuarto decia quien. Mientras el asistente fuera una parte de este
 * backend, la confusion no costaba nada. Deja de ser gratis en cuanto conversa
 * por WhatsApp desde un sistema aparte: <b>un asistente no es un canal</b>, y
 * con una sola columna "registrado desde WhatsApp por un agente automatico" no
 * se puede escribir sin perder la mitad.
 *
 * <p><b>{@code agente == null} es informacion, no un hueco.</b> Significa que la
 * persona lo pidio ella misma, y es lo que distingue una operacion tecleada de
 * una conversada.
 *
 * <h2>Esta clase no conoce a ningun asistente concreto</h2>
 * No hay aqui ninguna constante con el nombre de un producto ni de un modelo.
 * Un agente se identifica por su nombre y su version cuando llama; este backend
 * lo registra y no necesita saber nada mas de el. Es la misma razon por la que
 * no hay un {@code deWhatsApp()}: el canal es un dato, no una rama de codigo.
 *
 * <h2>Lo que NO cambia</h2>
 * El cable de los clientes actuales. Siguen mandando {@code X-Origen} o nada, y
 * el controlador construye la procedencia a partir de la cabecera.
 *
 * @param canal          por donde entro. Lo unico obligatorio
 * @param agente         que sistema automatico la formulo; {@code null} si fue
 *                       una persona directamente
 * @param modelo         con que modelo razono ese agente
 * @param modeloVersion  y en que version. Irreconstruible despues del despliegue
 * @param conversacionId de que conversacion, si salio de una
 * @param turnoId        de que turno exacto dentro de ella
 * @param mensajeId      el mensaje del canal: el puntero a la evidencia
 * @param peticion       lo que la persona escribio o dicto, literal
 * @param herramienta    la operacion que se invoco
 */
public record Procedencia(String canal, String agente, String modelo, String modeloVersion,
                          String conversacionId, String turnoId, String mensajeId,
                          String peticion, String herramienta) {

    /** {@code evento_dominio.peticion} es TEXT, pero una frase util no ocupa un libro. */
    private static final int MAX_PETICION = 1000;
    private static final int MAX_ID = 64;
    private static final int MAX_MENSAJE = 128;
    private static final int MAX_AGENTE = 30;
    private static final int MAX_MODELO = 60;
    private static final int MAX_VERSION = 40;
    private static final int MAX_HERRAMIENTA = 60;

    public Procedencia {
        canal = canalValidado(canal);
        agente = recorte(agente, MAX_AGENTE);
        modelo = recorte(modelo, MAX_MODELO);
        modeloVersion = recorte(modeloVersion, MAX_VERSION);
        conversacionId = recorte(conversacionId, MAX_ID);
        turnoId = recorte(turnoId, MAX_ID);
        mensajeId = recorte(mensajeId, MAX_MENSAJE);
        peticion = recorte(peticion, MAX_PETICION);
        herramienta = recorte(herramienta, MAX_HERRAMIENTA);
    }

    /**
     * Lo que llega por el cable de siempre: una cabecera de canal y nada mas.
     *
     * <p>{@code X-Origen} es una <b>afirmacion</b> del cliente, no una prueba, y
     * por eso no hay ningun CHECK en la base que persiga una etiqueta mentida:
     * una peticion mal declarada debe producir un dato pobre, no un 500. La
     * garantia esta donde si hay prueba: {@link #deAgente}.
     */
    public static Procedencia deCabecera(String canal) {
        return new Procedencia(canal, null, null, null, null, null, null, null, null);
    }

    /** La pantalla. Una persona tecleando, sin nada automatico de por medio. */
    public static Procedencia deLaPantalla() {
        return deCabecera(EventoDominio.CANAL_SPA);
    }

    /** Lo que hace el sistema por su cuenta: backfills, migraciones, tareas. */
    public static Procedencia delSistema() {
        return deCabecera(EventoDominio.CANAL_SISTEMA);
    }

    /**
     * Lo que produce un agente automatico actuando en nombre de una persona.
     *
     * <p><b>Exige agente, conversacion y turno.</b> Es la invariante puesta
     * donde se puede sostener: un agente no puede invocar un caso de uso sin
     * dejar con que responder despues de que turno salio el hecho. Ponerla como
     * CHECK en la base no serviria — la base solo ve la etiqueta que el cliente
     * eligio mandar.
     */
    public static Procedencia deAgente(String canal, String agente, String modelo,
                                       String modeloVersion, String conversacionId,
                                       String turnoId, String mensajeId, String peticion) {
        if (esVacio(agente) || esVacio(conversacionId) || esVacio(turnoId)) {
            throw new ReglaNegocioException(
                    "Una peticion de un agente automatico tiene que decir que agente es, de que "
                            + "conversacion sale y de que turno: sin eso el hecho queda escrito "
                            + "sin poder responder quien lo decidio.");
        }
        return new Procedencia(canal, agente, modelo, modeloVersion, conversacionId, turnoId,
                mensajeId, peticion, null);
    }

    /** {@code true} si la formulo un sistema automatico y no la persona directamente. */
    public boolean laFormuloUnAgente() {
        return agente != null;
    }

    /** La misma procedencia declarando que operacion se invoco. */
    public Procedencia invocando(String herramienta) {
        return new Procedencia(canal, agente, modelo, modeloVersion, conversacionId, turnoId,
                mensajeId, peticion, herramienta);
    }

    /** Estampa la procedencia en un evento ya construido. */
    public EventoDominio sellar(EventoDominio evento) {
        return evento.porEncargoDe(agente, modelo, modeloVersion, conversacionId, turnoId,
                mensajeId, peticion, herramienta);
    }

    /**
     * La que llegue, o la pantalla si no llego ninguna.
     *
     * <p>No declarar procedencia es el caso de todos los clientes actuales, que
     * no mandan {@code X-Origen}: es la pantalla y no un error.
     */
    public static Procedencia oPantalla(Procedencia procedencia) {
        return procedencia == null ? deLaPantalla() : procedencia;
    }

    private static String canalValidado(String canal) {
        if (esVacio(canal)) {
            return EventoDominio.CANAL_SPA;
        }
        String limpio = canal.trim().toUpperCase(Locale.ROOT);
        // "UI" fue el nombre del SPA hasta V59. Se acepta y se traduce, en vez
        // de romper a un cliente que todavia no se entero del cambio.
        if ("UI".equals(limpio)) {
            return EventoDominio.CANAL_SPA;
        }
        if (!EventoDominio.CANALES.contains(limpio)) {
            throw new ReglaNegocioException(
                    "Canal desconocido: \"" + canal + "\". Son SPA, WHATSAPP, API y SISTEMA.");
        }
        return limpio;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String recorte(String valor, int maximo) {
        if (esVacio(valor)) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
