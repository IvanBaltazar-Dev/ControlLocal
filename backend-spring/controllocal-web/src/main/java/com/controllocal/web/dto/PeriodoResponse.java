package com.controllocal.web.dto;

import com.controllocal.service.soporte.PeriodoCalendario;

import java.time.LocalDate;

/**
 * El mes de calendario contra el que se mide la meta, <b>explicito en el cable</b>.
 *
 * <p>Viajan los cinco datos porque el frontend no calcula ninguno: ni deduce el
 * fin de mes, ni cuenta los dias transcurridos, ni decide si el mes ya cerro. El
 * 19 de agosto de 2026 el corte es <b>19 de 31</b> y eso lo dice el backend.
 *
 * <p>No confundir con el parametro {@code periodo} de
 * {@code /indicadores/resumen}, que es una <b>ventana movil</b> (7d/15d/1m/3m/1y)
 * y sigue gobernando las series y los agregados. Son dos cosas distintas y por
 * eso tienen dos nombres: {@code metaEsperadaAHoy} sobre una ventana movil es
 * tautologica, porque los transcurridos serian siempre los totales.
 *
 * @param codigo            {@code AAAA-MM}, que es como se pide y como vuelve
 * @param desde             primer dia del mes
 * @param hasta             ultimo dia del mes
 * @param diasTranscurridos incluyendo hoy: el trabajo de hoy ya cuenta en el numerador
 * @param diasTotales       los del mes
 * @param enCurso           si el corte cae dentro; falso para un mes cerrado o futuro
 */
public record PeriodoResponse(String codigo, LocalDate desde, LocalDate hasta,
                              int diasTranscurridos, int diasTotales, boolean enCurso) {

    public static PeriodoResponse desde(PeriodoCalendario p) {
        return new PeriodoResponse(p.codigo(), p.desde(), p.hasta(),
                p.diasTranscurridos(), p.diasTotales(), p.enCurso());
    }
}
