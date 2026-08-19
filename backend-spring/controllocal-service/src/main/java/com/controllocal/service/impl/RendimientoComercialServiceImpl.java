package com.controllocal.service.impl;

import com.controllocal.domain.comercial.MetaComercial;
import com.controllocal.persistence.query.ConteoKpi;
import com.controllocal.persistence.query.ConteoKpiPorAgente;
import com.controllocal.persistence.repositorio.MetaComercialRepository;
import com.controllocal.persistence.repositorio.RendimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.RendimientoComercialService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.KpiCanonico;
import com.controllocal.service.soporte.PeriodoCalendario;
import com.controllocal.service.soporte.Ritmo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El rendimiento comercial del mes.
 *
 * <h2>Las tres reglas que gobiernan este archivo</h2>
 *
 * <ol>
 *   <li><b>Nada se rellena.</b> Sin meta no hay cero: hay «sin meta». Sin
 *       cobertura completa no hay semaforo. Es la regla que E2.0 fijo con
 *       {@code conversionPropia} y la unica que hace que un cero de la pantalla
 *       signifique un cero de verdad.</li>
 *   <li><b>Cuenta SQL.</b> Cuatro KPI en una consulta, no cuatro viajes ni una
 *       descarga que se filtra en memoria.</li>
 *   <li><b>El importe conserva su moneda.</b> No hay conversion, porque no hay
 *       tipo de cambio declarado y un tipo de cambio inventado dentro de una
 *       cifra que se presenta como hecho es peor que no dar la cifra.</li>
 * </ol>
 */
@Service
public class RendimientoComercialServiceImpl implements RendimientoComercialService {

    private final RendimientoRepository rendimiento;
    private final MetaComercialRepository metas;
    private final Alcances alcances;

    public RendimientoComercialServiceImpl(RendimientoRepository rendimiento,
                                           MetaComercialRepository metas,
                                           Alcances alcances) {
        this.rendimiento = rendimiento;
        this.metas = metas;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public Rendimiento del(String mes, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        LocalDate hoy = LocalDate.now();
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, hoy);

        // Los agentes del alcance. El ADMIN no trae lista -su alcance es "todos"-
        // asi que hay que preguntarla: sin ella no se puede saber si la meta del
        // equipo esta completa, y una meta incompleta no se compara.
        List<Long> agentes = alcance.global()
                ? rendimiento.agentesVigentes(organizacion, hoy)
                : alcance.rolesAgente();

        Map<KpiCanonico, Integer> actuales = conteos(organizacion, alcance, periodo);
        Map<KpiCanonico, Integer> anteriores = conteos(organizacion, alcance, periodo.anterior(hoy));
        Map<KpiCanonico, MetaDelAlcance> metasDelMes = metasDelAlcance(organizacion, periodo, agentes);

        List<Kpi> kpis = new ArrayList<>();
        for (KpiCanonico canonico : KpiCanonico.TODOS) {
            int actual = actuales.getOrDefault(canonico, 0);
            MetaDelAlcance meta = metasDelMes.get(canonico);
            kpis.add(new Kpi(canonico.codigo(), canonico.rotulo(), canonico.hecho(),
                    ritmoDe(actual, meta, periodo),
                    variacion(actual, anteriores.get(canonico))));
        }

        return new Rendimiento(periodo, OffsetDateTime.now(), kpis,
                cierrePosible(organizacion, alcance, hoy),
                pulso(organizacion, alcance, periodo, agentes));
    }

    // ------------------------------------------------------------------
    // Los conteos
    // ------------------------------------------------------------------

    private Map<KpiCanonico, Integer> conteos(long organizacion, Alcances.Alcance alcance,
                                              PeriodoCalendario periodo) {
        Map<KpiCanonico, Integer> porKpi = new EnumMap<>(KpiCanonico.class);
        if (alcance.vacio()) {
            // Un broker sin agentes supervisados no tiene nada que medir. Y sin
            // este corte, paramRoles() devolveria el centinela -1.
            return porKpi;
        }
        for (ConteoKpi fila : rendimiento.kpisDelPeriodo(organizacion, alcance.global(),
                alcance.paramRoles(), periodo.desde(), periodo.hasta())) {
            porKpi.put(KpiCanonico.porCodigo(fila.getKpi()), fila.getCantidad());
        }
        return porKpi;
    }

    /**
     * La diferencia contra el mismo KPI del mes anterior.
     *
     * <p>Es {@code null} y no cero cuando no hay mes anterior que comparar: un
     * «+0» sugiere que se midio y que no cambio nada, que es distinto de no
     * haber medido.
     */
    private Integer variacion(int actual, Integer anterior) {
        return anterior == null ? null : actual - anterior;
    }

    // ------------------------------------------------------------------
    // Las metas
    // ------------------------------------------------------------------

    /**
     * La meta del alcance para cada KPI, con su cobertura.
     *
     * <p><b>La meta del equipo es la suma de las de sus agentes</b>, nunca una
     * fila propia: asi no puede contradecir a sus sumandos. La contrapartida es
     * que si falta la de alguno, el total no se completa con lo que hay —daria
     * una brecha siempre a favor— y el ritmo queda sin base declarandolo.
     */
    private Map<KpiCanonico, MetaDelAlcance> metasDelAlcance(long organizacion,
                                                             PeriodoCalendario periodo,
                                                             List<Long> agentes) {
        Map<KpiCanonico, MetaDelAlcance> resultado = new EnumMap<>(KpiCanonico.class);
        Map<KpiCanonico, Integer> suma = new EnumMap<>(KpiCanonico.class);
        Map<KpiCanonico, Integer> conMeta = new EnumMap<>(KpiCanonico.class);

        if (!agentes.isEmpty()) {
            for (MetaComercial meta : metas.findByOrganizacionIdAndAnioAndMesAndIdRolAgenteIn(
                    organizacion, periodo.anio(), periodo.mes(), agentes)) {
                KpiCanonico kpi = KpiCanonico.porCodigo(meta.getKpi());
                suma.merge(kpi, meta.getValor(), Integer::sum);
                conMeta.merge(kpi, 1, Integer::sum);
            }
        }

        for (KpiCanonico kpi : KpiCanonico.TODOS) {
            resultado.put(kpi, new MetaDelAlcance(
                    suma.getOrDefault(kpi, 0), conMeta.getOrDefault(kpi, 0), agentes.size()));
        }
        return resultado;
    }

    /**
     * Cuanto se pide al alcance, y por cuantos de sus agentes esta respaldado.
     *
     * @param valor         la suma de las metas encontradas
     * @param agentesConMeta cuantos agentes del alcance tienen meta de este KPI
     * @param agentesTotal   cuantos agentes tiene el alcance
     */
    private record MetaDelAlcance(int valor, int agentesConMeta, int agentesTotal) {

        boolean nadieTieneMeta() {
            return agentesConMeta == 0;
        }

        boolean incompleta() {
            return agentesConMeta > 0 && agentesConMeta < agentesTotal;
        }
    }

    private Ritmo ritmoDe(int actual, MetaDelAlcance meta, PeriodoCalendario periodo) {
        if (meta == null || meta.nadieTieneMeta()) {
            return Ritmo.sinMeta(actual);
        }
        if (meta.incompleta()) {
            return Ritmo.coberturaIncompleta(actual);
        }
        return Ritmo.de(actual, meta.valor(), periodo);
    }

    // ------------------------------------------------------------------
    // Lo que puede cerrarse
    // ------------------------------------------------------------------

    private CierrePosible cierrePosible(long organizacion, Alcances.Alcance alcance, LocalDate hoy) {
        if (alcance.vacio()) {
            return new CierrePosible(0, BigDecimal.ZERO, null, false, 0);
        }
        List<com.controllocal.persistence.query.CierrePosible> porMoneda =
                rendimiento.cierrePosible(organizacion, alcance.global(), alcance.paramRoles(), hoy);
        int esperanDecision =
                rendimiento.esperanDecision(organizacion, alcance.global(), alcance.paramRoles());

        if (porMoneda.isEmpty()) {
            return new CierrePosible(0, BigDecimal.ZERO, null, false, esperanDecision);
        }
        // La consulta ordena por importe descendente: la primera es la principal.
        // Si hubiera dos monedas NO se suman; se dice que las hay y la pantalla
        // decide como contarlo, con el hecho delante en vez de un total falso.
        var principal = porMoneda.get(0);
        int operaciones = porMoneda.stream()
                .mapToInt(com.controllocal.persistence.query.CierrePosible::getOperaciones).sum();
        return new CierrePosible(operaciones, principal.getImporte(), principal.getMoneda(),
                porMoneda.size() > 1, esperanDecision);
    }

    // ------------------------------------------------------------------
    // El pulso del equipo
    // ------------------------------------------------------------------

    /**
     * Como se reparte el resultado entre los agentes del alcance.
     *
     * <p>Solo para quien supervisa a mas de uno. Para un agente el pulso seria su
     * propio ritmo contado otra vez, que es el diagnostico duplicado que la
     * instruccion 14 de D-E2-2 prohibe.
     *
     * <p>El ritmo de cada agente se mide sobre <b>la suma de sus cuatro KPI
     * contra la suma de sus cuatro metas</b>. Un agente sin ninguna meta cuenta
     * como «sin base», no como fuera de ritmo: no se le puede reprochar una
     * brecha contra un objetivo que nadie le fijo.
     */
    private Pulso pulso(long organizacion, Alcances.Alcance alcance, PeriodoCalendario periodo,
                        List<Long> agentes) {
        if (agentes.size() < 2 || alcance.vacio()) {
            return null;
        }

        Map<Long, Integer> actualPorAgente = new HashMap<>();
        for (ConteoKpiPorAgente fila : rendimiento.kpisPorAgente(organizacion, alcance.global(),
                alcance.paramRoles(), periodo.desde(), periodo.hasta())) {
            if (fila.getIdAgente() != null) {
                actualPorAgente.merge(fila.getIdAgente(), fila.getCantidad(), Integer::sum);
            }
        }

        Map<Long, Integer> metaPorAgente = new HashMap<>();
        for (MetaComercial meta : metas.findByOrganizacionIdAndAnioAndMesAndIdRolAgenteIn(
                organizacion, periodo.anio(), periodo.mes(), agentes)) {
            metaPorAgente.merge(meta.getIdRolAgente(), meta.getValor(), Integer::sum);
        }

        int enRitmo = 0;
        int atencion = 0;
        int fuera = 0;
        int sinBase = 0;
        for (Long agente : agentes) {
            int actual = actualPorAgente.getOrDefault(agente, 0);
            Integer meta = metaPorAgente.get(agente);
            Ritmo ritmo = meta == null ? Ritmo.sinMeta(actual) : Ritmo.de(actual, meta, periodo);
            switch (ritmo.estado()) {
                case EN_RITMO -> enRitmo++;
                case ATENCION -> atencion++;
                case FUERA_DE_RITMO -> fuera++;
                case SIN_BASE -> sinBase++;
            }
        }
        return new Pulso(enRitmo, atencion, fuera, sinBase, agentes.size());
    }
}
