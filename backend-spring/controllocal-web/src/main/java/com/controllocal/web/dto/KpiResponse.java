package com.controllocal.web.dto;

import com.controllocal.service.RendimientoComercialService;
import com.controllocal.service.soporte.Ritmo;

/**
 * Un KPI canonico, con todo lo que la pantalla necesita y nada que tenga que
 * calcular.
 *
 * <h2>Los nulos son informacion</h2>
 *
 * <p>{@code metaPeriodo}, {@code metaEsperadaAHoy}, {@code porcentajeMeta},
 * {@code faltante} y {@code proyeccionCierre} son <b>nulables a proposito</b>.
 * Sin meta no valen cero: no existen. Un cero diria «tu meta es cero y la
 * cumpliste», que es lo contrario de «nadie te fijo una meta». Es la misma
 * decision que E2.0 tomo con {@code conversionPropia}, y la razon por la que un
 * cero de esta pantalla siempre significa un cero de verdad.
 *
 * <h2>El rotulo viaja; la pantalla no lo inventa</h2>
 *
 * <p>Los cuatro nombres —<i>Propietarios contactados · Propiedades captadas ·
 * Solicitudes ingresadas · Contratos firmados</i>— salen del dominio, iguales
 * para Indicadores y para el pie del Inicio. Cuando cambien, cambiaran en los
 * dos sitios el mismo dia porque solo hay un sitio donde estan escritos.
 *
 * @param codigo               unitario y estable: {@code C}, {@code P}, {@code S}, {@code F}
 * @param rotulo               el nombre visible
 * @param hecho                que fila cuenta exactamente, para que el numero sea auditable
 * @param actual               lo conseguido en el mes
 * @param metaPeriodo          la meta vigente; {@code null} si nadie la fijo
 * @param metaEsperadaAHoy     cuanto deberia llevarse a dia de hoy
 * @param porcentajeMeta       avance sobre la meta
 * @param faltante             cuanto falta
 * @param proyeccionCierre     a donde llega con este ritmo
 * @param porcentajeProyectado esa proyeccion en porcentaje de la meta
 * @param estadoRitmo          {@code EN_RITMO} · {@code ATENCION} · {@code FUERA_DE_RITMO} · {@code SIN_BASE}
 * @param motivoSinBase        por que no concluye: {@code SIN_META},
 *                             {@code COBERTURA_INCOMPLETA} o {@code PERIODO_SIN_RECORRIDO}.
 *                             {@code NINGUNO} cuando el estado si concluye
 * @param sinCadencia          la meta es tan pequena que repartirla por dias inventaria
 *                             una cadencia diaria que el negocio no tiene
 * @param variacionComparable  diferencia contra el mismo KPI del mes anterior
 */
public record KpiResponse(String codigo, String rotulo, String hecho,
                          int actual,
                          Integer metaPeriodo,
                          Integer metaEsperadaAHoy,
                          Integer porcentajeMeta,
                          Integer faltante,
                          Integer proyeccionCierre,
                          Integer porcentajeProyectado,
                          String estadoRitmo,
                          String motivoSinBase,
                          boolean sinCadencia,
                          Integer variacionComparable) {

    public static KpiResponse desde(RendimientoComercialService.Kpi k) {
        Ritmo r = k.ritmo();
        return new KpiResponse(k.codigo(), k.rotulo(), k.hecho(),
                r.actual(), r.metaPeriodo(), r.metaEsperadaAHoy(), r.porcentajeMeta(),
                r.faltante(), r.proyeccionCierre(), r.porcentajeProyectado(),
                r.estado().name(), r.motivo().name(), r.sinCadencia(),
                k.variacionComparable());
    }
}
