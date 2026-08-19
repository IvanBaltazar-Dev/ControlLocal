package com.controllocal.service.impl;

import com.controllocal.domain.comercial.MetaComercial;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.MetaComercialRepository;
import com.controllocal.persistence.repositorio.RendimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.MetaComercialService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.KpiCanonico;
import com.controllocal.service.soporte.PeriodoCalendario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fijar y consultar las metas del mes.
 *
 * <h2>La comprobacion que no se puede saltar</h2>
 *
 * <p>Un broker solo fija la meta de <b>sus</b> agentes. Sin esa comprobacion,
 * conocer un id bastaria para ponerle objetivos a alguien de otro equipo —o de
 * otra corredora—, y el semaforo de esa persona pasaria a depender de un extrano.
 * El alcance se resuelve con {@link Alcances}, el mismo que usa el resto del
 * sistema, y no con una consulta propia que pudiera divergir.
 */
@Service
public class MetaComercialServiceImpl implements MetaComercialService {

    private final MetaComercialRepository metas;
    private final DetalleAgenteRepository agentes;
    private final RendimientoRepository rendimiento;
    private final Alcances alcances;

    public MetaComercialServiceImpl(MetaComercialRepository metas,
                                    DetalleAgenteRepository agentes,
                                    RendimientoRepository rendimiento,
                                    Alcances alcances) {
        this.metas = metas;
        this.agentes = agentes;
        this.rendimiento = rendimiento;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetaDeAgente> del(String mes, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, LocalDate.now());
        List<Long> visibles = agentesDelAlcance(alcance);
        return filas(alcance.idOrganizacion(), periodo, visibles);
    }

    @Override
    @Transactional
    public List<MetaDeAgente> fijar(String mes, List<Asignacion> asignaciones, Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        long organizacion = alcance.idOrganizacion();
        PeriodoCalendario periodo = PeriodoCalendario.desde(mes, LocalDate.now());
        List<Long> visibles = agentesDelAlcance(alcance);

        for (Asignacion asignacion : asignaciones == null ? List.<Asignacion>of() : asignaciones) {
            if (!visibles.contains(asignacion.idRolAgente())) {
                throw new ReglaNegocioException(
                        "No puedes fijar la meta de un agente que no supervisas.");
            }
            // Rechaza un KPI inventado con el mensaje del dominio, antes de la BD.
            KpiCanonico kpi = KpiCanonico.porCodigo(asignacion.kpi());
            if (asignacion.valor() < 0) {
                throw new ReglaNegocioException(
                        "La meta de " + kpi.rotulo() + " no puede ser negativa. Cero significa "
                                + "que este mes no se pide ese resultado.");
            }

            MetaComercial meta = metas
                    .findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMes(
                            organizacion, asignacion.idRolAgente(), kpi.codigo(),
                            periodo.anio(), periodo.mes())
                    .orElseGet(MetaComercial::new);

            meta.setOrganizacionId(organizacion);
            meta.setIdRolAgente(asignacion.idRolAgente());
            meta.setKpi(kpi.codigo());
            meta.setAnio(periodo.anio());
            meta.setMes(periodo.mes());
            meta.setValor(asignacion.valor());
            meta.setIdRolAutor(actor.idRolOperativo());
            if (meta.getId() != null) {
                meta.setFechaActualizacion(OffsetDateTime.now());
            }
            metas.save(meta);
        }
        return filas(organizacion, periodo, visibles);
    }

    // ------------------------------------------------------------------

    /**
     * Los agentes que el actor alcanza. El administrador no trae lista —su
     * alcance es «todos»— asi que hay que preguntarla.
     */
    private List<Long> agentesDelAlcance(Alcances.Alcance alcance) {
        return alcance.global()
                ? rendimiento.agentesVigentes(alcance.idOrganizacion(), LocalDate.now())
                : alcance.rolesAgente();
    }

    /**
     * Una fila por agente y KPI, <b>incluidas las que no tienen meta</b>, con
     * {@code valor} nulo.
     *
     * <p>Devolver solo las fijadas dejaria la pantalla sin saber a quien le falta
     * —que es justo lo que hay que ver para poder fijarla— y haria invisible la
     * cobertura incompleta que deja al equipo sin semaforo.
     */
    private List<MetaDeAgente> filas(long organizacion, PeriodoCalendario periodo,
                                     List<Long> visibles) {
        if (visibles.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> fijadas = new HashMap<>();
        for (MetaComercial meta : metas.findByOrganizacionIdAndAnioAndMesAndIdRolAgenteIn(
                organizacion, periodo.anio(), periodo.mes(), visibles)) {
            fijadas.put(meta.getIdRolAgente() + "|" + meta.getKpi(), meta.getValor());
        }

        Map<Long, String> nombres = nombresDe(organizacion, visibles);
        List<MetaDeAgente> filas = new ArrayList<>();
        for (Long agente : visibles) {
            for (KpiCanonico kpi : KpiCanonico.TODOS) {
                filas.add(new MetaDeAgente(agente, nombres.getOrDefault(agente, "—"),
                        kpi.codigo(), kpi.rotulo(),
                        fijadas.get(agente + "|" + kpi.codigo())));
            }
        }
        return filas;
    }

    private Map<Long, String> nombresDe(long organizacion, List<Long> visibles) {
        Map<Long, String> nombres = new HashMap<>();
        for (DetalleAgente ficha : agentes.listarFichas(organizacion)) {
            PersonaRol rol = ficha.getRol();
            if (rol == null || !visibles.contains(rol.getId())) {
                continue;
            }
            Persona persona = rol.getPersona();
            String nombre = persona != null ? persona.getNombresORazonSocial() : null;
            nombres.put(rol.getId(), nombre == null || nombre.isBlank() ? "—" : nombre);
        }
        return nombres;
    }
}
