package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.RequerimientoClienteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.HallazgoService;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.CoincidenciaCartera;
import com.controllocal.service.soporte.HallazgoDeConcentracion;
import com.controllocal.service.soporte.CoincidenciaCartera.Evaluacion;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.PoliticaComercial;
import com.controllocal.service.soporte.ValoresGobernados;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <b>El mismo motor que ya existía, con otra salida</b> (E2.3).
 *
 * <h2>De dónde sale este código</h2>
 * Este bucle vivía dentro de {@code TareaServiceImpl} como el séptimo
 * disparador de la bandeja, y producía una <b>tarea</b> por cada coincidencia.
 * Se ha movido entero, sin tocar la evaluación: la evidencia sigue saliendo de
 * {@link CoincidenciaCartera}, con el mismo umbral de
 * {@link PoliticaComercial#valeLaPenaProponer} y la misma deduplicación por par
 * cliente-captación.
 *
 * <p><b>Lo único que cambia es dónde aterriza.</b> Y no es un detalle de
 * presentación: mientras aterrizaba en la bandeja, la política de despacho la
 * trataba como una ocasión —correctamente, porque lo es— y la ponía por delante
 * de todo lo que reclamaba una acción. El agente abría su Inicio y veía
 * sugerencias donde esperaba obligaciones.
 *
 * <h2>Por qué no hay tarea que auto-resolver</h2>
 * Un hallazgo no se «completa». Deja de existir cuando deja de ser cierto —el
 * cliente ya tiene su oportunidad, el local se retiró, el requerimiento cambió—
 * y eso se sabe recalculando, no marcando un estado. Por eso esto no escribe
 * nada: no hay fila que quede mintiendo si alguien cambia el mundo por otro
 * camino.
 */
@Service
public class HallazgoServiceImpl implements HallazgoService {

    private final CaptacionRepository captaciones;
    private final RequerimientoClienteRepository requerimientos;
    private final OportunidadComercialRepository oportunidades;
    private final LectorPorAutoridad lector;
    private final Alcances alcances;
    private final com.controllocal.persistence.repositorio.DetalleAgenteRepository agentes;

    public HallazgoServiceImpl(CaptacionRepository captaciones,
                               RequerimientoClienteRepository requerimientos,
                               OportunidadComercialRepository oportunidades,
                               LectorPorAutoridad lector,
                               Alcances alcances,
                               com.controllocal.persistence.repositorio.DetalleAgenteRepository agentes) {
        this.captaciones = captaciones;
        this.requerimientos = requerimientos;
        this.oportunidades = oportunidades;
        this.lector = lector;
        this.alcances = alcances;
        this.agentes = agentes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Hallazgo> de(Actor actor) {
        if (actor.esBroker()) {
            // MISMO CAMPO, PRODUCTOR DISTINTO (D-E2-1 seccion 7.1).
            //
            // El agente descubre coincidencias de cartera; el broker descubre
            // concentracion. No son dos superficies: es la misma, con lo que cada
            // rol necesita encontrar. Se compartian, y el resultado era que el
            // broker se quedaba SIN hallazgo -- justo el rol al que mas le sirve
            // que BROX encuentre algo que la media esconde.
            return concentracionDelEquipo(actor);
        }
        if (!actor.esAgente()) {
            // El TENANT_ADMIN no descubre nada: audita. Un hallazgo es una
            // invitacion a actuar, y desde D-F4-5 no decide operaciones.
            return List.of();
        }
        long org = actor.idOrganizacion();
        long idAgente = actor.idRolOperativo();

        List<Captacion> disponibles = captaciones.activasConLocalDisponible(org, idAgente);
        if (disponibles.isEmpty()) {
            return List.of();
        }
        List<Long> roles = List.of(idAgente);
        Set<Long> misClientes = new HashSet<>(oportunidades.idsClienteDelEquipo(org, roles));
        if (misClientes.isEmpty()) {
            return List.of();
        }

        // Los atributos gobernados de toda la cartera candidata, en una consulta:
        // `frente` entra en el puntaje y su columna espejo ya no existe (D-E4-3).
        Map<Long, ValoresGobernados> valores = lector.deVarias(org,
                disponibles.stream().map(Captacion::getPropiedad).filter(Objects::nonNull)
                        .distinct().toList());

        Set<String> yaPropuesto = new HashSet<>();
        for (Object[] par : oportunidades.paresClienteCaptacionDelEquipo(org, roles)) {
            yaPropuesto.add(par[0] + "#" + par[1]);
        }

        List<Hallazgo> hallazgos = new ArrayList<>();
        for (RequerimientoCliente requerimiento : requerimientos.listarActivos(org)) {
            Long idCliente = requerimiento.getCliente() == null ? null
                    : requerimiento.getCliente().getId();
            if (idCliente == null || !misClientes.contains(idCliente)) {
                continue;
            }
            Captacion mejor = null;
            Evaluacion mejorEvaluacion = null;
            for (Captacion candidata : disponibles) {
                if (yaPropuesto.contains(idCliente + "#" + candidata.getId())) {
                    continue;
                }
                Propiedad propiedad = candidata.getPropiedad();
                Long idPropiedad = propiedad == null ? null : propiedad.getId();
                Evaluacion evaluacion = CoincidenciaCartera.evaluar(requerimiento, propiedad,
                        LectorPorAutoridad.de(valores, idPropiedad));
                if (!PoliticaComercial.valeLaPenaProponer(evaluacion.puntaje())) {
                    continue;
                }
                if (mejorEvaluacion == null || evaluacion.puntaje() > mejorEvaluacion.puntaje()) {
                    mejor = candidata;
                    mejorEvaluacion = evaluacion;
                }
            }
            if (mejor != null) {
                hallazgos.add(hallazgo(idCliente, mejor, mejorEvaluacion));
            }
        }

        // Del mejor al peor, y con desempate por identidad: dos peticiones
        // seguidas con los mismos datos devuelven el mismo orden.
        hallazgos.sort(Comparator.comparingInt(Hallazgo::puntaje).reversed()
                .thenComparing(Hallazgo::id));
        return List.copyOf(hallazgos);
    }

    // ------------------------------------------------------------------

    /**
     * <b>Donde esta el cuello del equipo</b> (D-E2-2 seccion 9.1).
     *
     * <p>Se mide sobre la cartera activa por agente, que es el dato que el
     * broker ya supervisa. Si no hay concentracion no hay hallazgo: el bloque no
     * existe y no se rellena con "el equipo esta equilibrado".
     */
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
                0, List.of(), List.of(),
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

    private static Hallazgo hallazgo(long idCliente, Captacion captacion, Evaluacion evaluacion) {
        String codigo = captacion.getCodigoCaptacion() == null ? "" : captacion.getCodigoCaptacion();
        String direccion = captacion.getPropiedad() == null ? null
                : captacion.getPropiedad().getDireccion();

        return new Hallazgo(
                identidad(idCliente, captacion.getId()),
                COINCIDENCIA_DE_CARTERA,
                direccion == null || direccion.isBlank() ? codigo : direccion,
                porQue(evaluacion),
                evaluacion.puntaje(),
                evaluacion.cumple(),
                evaluacion.noCumple(),
                "cliente-detail/" + idCliente,
                idCliente,
                captacion.getId(),
                codigo);
    }

    /**
     * La identidad se compone de los DOS extremos que producen la coincidencia.
     *
     * <p>Ni un contador —cambiaria en cada recarga y la pantalla no podria
     * recordar que ya lo miraste— ni un hash del texto: cambiar una palabra del
     * rotulo convertiria el mismo hallazgo en otro distinto.
     */
    private static String identidad(long idCliente, Long idCaptacion) {
        return COINCIDENCIA_DE_CARTERA + ":" + idCliente + ":" + idCaptacion;
    }

    /**
     * <b>Por qué vale la pena mirarlo, redactado aquí.</b>
     *
     * <p>Dice cuántos criterios cruza y no repite el puntaje, que ya viaja
     * aparte: el número mide, la frase explica. Y nombra el pero cuando lo hay,
     * porque un hallazgo que solo presume se decide peor que uno honesto — quien
     * lo lee va a descubrir el pero de todos modos, y mejor antes de llamar al
     * cliente.
     *
     * <p>Si esta frase se compusiera en el cliente, KAIROS tendria que escribir
     * la suya para decir lo mismo por WhatsApp, y las dos empezarian a divergir.
     */
    private static String porQue(Evaluacion evaluacion) {
        int cruza = evaluacion.cumple().size();
        int total = cruza + evaluacion.noCumple().size();
        String base = "Cruza " + cruza + " de " + total
                + (total == 1 ? " criterio" : " criterios");
        if (evaluacion.noCumple().isEmpty()) {
            return base + ", sin ningun pero.";
        }
        return base + "; queda fuera en " + evaluacion.noCumple().get(0).toLowerCase() + ".";
    }
}
