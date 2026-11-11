package com.controllocal.service.impl;

import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.service.Actor;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.HallazgoService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.HallazgoDeConcentracion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Lecturas de aptitud del único matcher; no producen tareas, estados o prioridad de Radar. */
@Service
public class HallazgoServiceImpl implements HallazgoService {
    private final CaptacionRepository captaciones;
    private final Alcances alcances;
    private final DetalleAgenteRepository agentes;
    private final CoincidenciaService coincidencias;
    public HallazgoServiceImpl(CaptacionRepository captaciones,Alcances alcances,
            DetalleAgenteRepository agentes,CoincidenciaService coincidencias) {
        this.captaciones=captaciones;this.alcances=alcances;this.agentes=agentes;this.coincidencias=coincidencias;
    }
    @Override @Transactional(readOnly=true)
    public List<Hallazgo> de(Actor actor) {
        Alcances.exigirOperador(actor);
        if(actor.esBroker())return concentracionDelEquipo(actor);
        return coincidencias.aptitudesPropias(actor).stream()
                .map(a->new Hallazgo(COINCIDENCIA_DE_CARTERA+":"+a.idDemanda()+":"+a.idEncargo(),
                        COINCIDENCIA_DE_CARTERA,a.codigoEncargo(),null,a.evaluacion(),"",
                        a.idDemanda(),a.idEncargo(),a.codigoEncargo()))
                .sorted(Comparator.comparing(Hallazgo::id)).toList();
    }

    private List<Hallazgo> concentracionDelEquipo(Actor actor) {
        Alcances.Alcance alcance = alcances.de(actor);
        List<Long> equipo = alcance.rolesAgente();
        if (equipo.size() < 3) {
            return List.of();
        }
        Map<Long, Long> carteraPorAgente = new java.util.HashMap<>();
        for (ConteoPorAgente fila : captaciones.contarEnCarteraPorAgentes(
                alcance.idOrganizacion(), equipo)) {
            carteraPorAgente.put(fila.getIdAgente(), fila.getTotal());
        }
        // Los nombres del equipo, en UNA consulta.
        //
        // Estaban pidiendose de uno en uno dentro del bucle: un N+1 pequeno -- el
        // equipo son cuatro -- pero N+1 al fin, y la auditoria de cierre esta
        // justo para que un N+1 pequeno no se quede porque es pequeno.
        Map<Long, String> nombres = new java.util.HashMap<>();
        agentes.findAllById(equipo).forEach(a -> nombres.put(a.getId(), nombreDe(a)));

        List<HallazgoDeConcentracion.Aporte> aportes = new ArrayList<>();
        for (Long idAgente : equipo) {
            aportes.add(new HallazgoDeConcentracion.Aporte(idAgente,
                    nombres.getOrDefault(idAgente, "un agente del equipo"),
                    carteraPorAgente.getOrDefault(idAgente, 0L)));
        }

        HallazgoDeConcentracion.Concentracion concentracion =
                HallazgoDeConcentracion.de(aportes, "cartera activa");
        if (concentracion == null) {
            return List.of();
        }
        return List.of(new Hallazgo(
                CONCENTRACION_DEL_EQUIPO + ":" + concentracion.idRezagado(),
                CONCENTRACION_DEL_EQUIPO,
                concentracion.titulo(),
                concentracion.cuerpo(),
                null,
                "agentes",
                null, null, null));
    }

    private static String nombreDe(com.controllocal.domain.persona.DetalleAgente agente) {
        if (agente.getRol() == null || agente.getRol().getPersona() == null) {
            return "un agente del equipo";
        }
        String nombre = agente.getRol().getPersona().getNombresORazonSocial();
        return nombre == null || nombre.isBlank() ? "un agente del equipo" : nombre;
    }

}
