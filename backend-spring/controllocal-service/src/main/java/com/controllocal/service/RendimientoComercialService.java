package com.controllocal.service;

import com.controllocal.service.soporte.PeriodoCalendario;
import com.controllocal.service.soporte.Ritmo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * El rendimiento comercial del mes: los cuatro KPI canonicos con su meta y su
 * ritmo, lo que puede cerrarse, y el pulso del equipo.
 *
 * <h2>Por que es un servicio aparte de {@link IndicadorService}</h2>
 *
 * <p>Porque miden contra <b>periodos distintos</b>, y mezclarlos seria repetir
 * el error que E2.6 vino a corregir. {@code IndicadorService} trabaja con una
 * ventana movil (7d/15d/1m/3m/1y): sirve para series y agregados. El ritmo
 * necesita un mes de calendario con inicio, fin y dias transcurridos, porque
 * {@code metaEsperadaAHoy = meta x transcurridos / dias} es tautologica en una
 * ventana movil —los transcurridos serian siempre los totales—.
 *
 * <p>Dos semanticas bajo el mismo parametro es como se llega a que nadie sepa
 * que mide un numero. Aqui son dos servicios, dos parametros y dos nombres.
 *
 * <h2>Nada se dibuja fuera de aqui</h2>
 *
 * <p>El frontend recibe {@code actual}, {@code metaPeriodo},
 * {@code metaEsperadaAHoy}, {@code porcentajeMeta}, {@code faltante},
 * {@code estadoRitmo} y {@code variacionComparable} ya calculados. No prorratea,
 * no proyecta y no elige el semaforo: misma regla que E1 fijo para los umbrales.
 */
public interface RendimientoComercialService {

    /**
     * El rendimiento de un mes.
     *
     * @param mes   {@code AAAA-MM}; vacio o ilegible es el mes en curso
     * @param actor quien pregunta, que decide el alcance
     */
    Rendimiento del(String mes, Actor actor);

    /**
     * Todo lo que la pantalla necesita para dibujar el bloque, en una lectura.
     *
     * <p>{@code generadoEn} es <b>el unico productor</b> del instante de calculo
     * en todo el sistema. El Inicio lo lee de aqui —viaja dentro de
     * {@code indicadores}, igual que {@code ambito}— y no emite el suyo: dos
     * campos con el mismo hecho es la doble verdad que D-E4-3 cerro para los
     * datos de la propiedad. Es lo que permite decir «hace 2 min» sin que la
     * pantalla se invente el reloj.
     */
    record Rendimiento(PeriodoCalendario periodo,
                       OffsetDateTime generadoEn,
                       List<Kpi> kpis,
                       CierrePosible cierrePosible,
                       Pulso pulso) {
    }

    /**
     * Un KPI canonico con su lectura completa.
     *
     * @param codigo    unitario y estable ({@code C}, {@code P}, {@code S}, {@code F})
     * @param rotulo    el nombre visible, el mismo en Indicadores y en el pie del Inicio
     * @param hecho     que fila cuenta exactamente, para que el numero sea auditable
     * @param ritmo     actual, meta, esperado a hoy, faltante, proyeccion y semaforo
     * @param variacionComparable diferencia contra el mismo KPI del mes anterior;
     *                            {@code null} si ese mes no tiene con que comparar
     */
    record Kpi(String codigo, String rotulo, String hecho, Ritmo ritmo,
               Integer variacionComparable) {
    }

    /**
     * Lo que puede firmarse este mes. <b>Determinista</b>: solicitudes aprobadas,
     * sin contrato y con la oferta vigente. Ni pronostico, ni probabilidad
     * aprendida, ni una oportunidad «que pinta bien».
     *
     * @param operaciones     cuantas cumplen las tres condiciones
     * @param importe         la suma de sus importes, sin convertir
     * @param moneda          la moneda de esa suma; {@code null} si no hay operaciones
     * @param variasMonedas   si hay operaciones en mas de una moneda. Entonces
     *                        {@code importe} trae solo la mayor y la pantalla lo
     *                        dice, en vez de sumar soles con dolares
     * @param esperanDecision cuantas esperan que el broker decida (su palanca)
     */
    record CierrePosible(int operaciones, BigDecimal importe, String moneda,
                         boolean variasMonedas, int esperanDecision) {
    }

    /**
     * Como se reparte el resultado del equipo, que no es lo mismo que el total
     * (D-E2-2 §6.1): meta 20 y resultado 21 puede esconder dos agentes con 17 y
     * tres con cero.
     *
     * <p>Solo tiene sentido para quien supervisa a mas de una persona. Para un
     * agente es {@code null}: su propio pulso es su propio ritmo, y repetirlo
     * seria el diagnostico duplicado que la instruccion 14 prohibe.
     */
    record Pulso(int enRitmo, int atencion, int fueraDeRitmo, int sinBase, int agentes) {
    }
}
