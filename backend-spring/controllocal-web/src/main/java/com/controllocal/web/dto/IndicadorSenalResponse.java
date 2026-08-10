package com.controllocal.web.dto;

import com.controllocal.service.IndicadorService;

/**
 * Un numero del tablero con su lectura ya hecha (R-07, E1).
 *
 * <p>Hasta E1 el cable llevaba solo el numero y era el componente de Angular
 * quien decidia si eso era grave: ocho ternarios repartidos por rol, que ademas
 * se contradecian entre si, y un {@code > 7} que era la cuarta copia del plazo
 * de recontacto. Ahora la decision viaja resuelta:
 *
 * <ul>
 *   <li>{@code concepto} — nombre estable del dominio
 *       ({@code SOLICITUD_POR_EVALUAR}, {@code RECONTACTO_VENCIDO}…). Es una
 *       clave, no un rotulo: la pantalla nunca lo muestra tal cual.</li>
 *   <li>{@code valor} — el hecho, sin interpretar. Sigue viajando porque al
 *       usuario le importa cuantos son, no solo que urjan.</li>
 *   <li>{@code nivelAtencion} — {@code ALTO}, {@code MEDIO}, {@code INFORMATIVO}
 *       o {@code SIN_PENDIENTES}. El cliente elige el color; nunca el nivel.</li>
 *   <li>{@code requiereAtencion} — atajo de lo anterior para no obligar al
 *       cliente a saber que INFORMATIVO no es un pendiente.</li>
 *   <li>{@code prioridad} — 1 es lo que se atiende primero. Un unico orden para
 *       los tres roles.</li>
 * </ul>
 *
 * <p>La lista viaja <b>completa</b>, con los conceptos en cero incluidos: un
 * cero clasificado es informacion ("no hay nada atrasado"), y omitirlo obligaria
 * al cliente a distinguir "no vino" de "no hay".
 */
public record IndicadorSenalResponse(
        String concepto,
        int valor,
        String nivelAtencion,
        boolean requiereAtencion,
        int prioridad) {

    public static IndicadorSenalResponse desde(IndicadorService.Senal senal) {
        return new IndicadorSenalResponse(
                senal.concepto(),
                senal.valor(),
                senal.nivelAtencion(),
                senal.requiereAtencion(),
                senal.prioridad());
    }
}
