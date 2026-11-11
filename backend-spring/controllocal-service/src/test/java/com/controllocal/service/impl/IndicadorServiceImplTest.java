package com.controllocal.service.impl;

import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.query.IndicadorCaptacion;
import com.controllocal.persistence.query.IndicadorContrato;
import com.controllocal.persistence.query.IndicadorOportunidad;
import com.controllocal.persistence.query.IndicadorProspeccion;
import com.controllocal.persistence.query.IndicadorSolicitud;
import com.controllocal.persistence.query.IndicadorVisita;
import com.controllocal.persistence.query.MotivoPorCaptacion;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.ProspeccionService.RevisionSeguimiento;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.InteraccionService.MedicionInteraccion;
import com.controllocal.service.InteraccionService.VinculoFicha;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.IndicadorService.AvanceComercial;
import com.controllocal.service.IndicadorService.AvancePropiedad;
import com.controllocal.service.IndicadorService.Conteo;
import com.controllocal.service.IndicadorService.Desempeno;
import com.controllocal.service.IndicadorService.Embudo;
import com.controllocal.service.IndicadorService.Resumen;
import com.controllocal.service.IndicadorService.Senal;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.PoliticaComercial.Concepto;
import com.controllocal.service.soporte.PoliticaComercial.NivelAtencion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Blinda E4-indicadores: las reglas del cable que no se ven en la forma de la
 * respuesta —donut exclusivo, cohorte de conversion, alcance indirecto del
 * contrato, fallback de prospecciones— y los bugs que se replican a proposito.
 */
class IndicadorServiceImplTest {
    @Test void analisisPreparadosNoSePublicanPorElResumenLegado() throws Exception {
        var salida=service.resumen("7d",agente);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(salida);
        assertFalse(json.contains("\"primeraRespuesta\""));assertFalse(json.contains("\"efectividadDeCierre\""));
        assertTrue(json.contains("\"contactoEfectivoProspeccion\""));
    }
    @Test void permisoActualNoAtribuyeAlNuevoResponsableElCierreHistoricoDeOtraPersona(){
        var hoy=LocalDate.now(ZoneId.of("America/Lima"));
        var f=new com.controllocal.service.actividad.IndicadoresIndividuales.Fuente("OPORTUNIDAD",99L,1L,java.time.Instant.now().minusSeconds(60),OTRO_AGENTE);
        when(desenlaces.leer(org.mockito.ArgumentMatchers.eq(agente),any())).thenReturn(List.of(
                new com.controllocal.service.actividad.IndicadoresIndividuales.Desenlace(f,"VENTA",hoy,true,true)));
        var tasa=service.resumen("7d",agente).efectividadDeCierre().get("VENTA");assertEquals(0,tasa.evaluables());assertEquals(0,tasa.exitos());assertNull(tasa.porcentaje());
    }

    private static final long ORG = 1L;
    private static final long AGENTE = 30L;
    private static final long OTRO_AGENTE = 31L;
    private static final long BROKER = 23L;

    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final OportunidadComercialRepository oportunidades =
            mock(OportunidadComercialRepository.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final MotivoNoContinuidadRepository motivos = mock(MotivoNoContinuidadRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final SeguimientoDeProspeccion seguimiento = mock(SeguimientoDeProspeccion.class);
    private final com.controllocal.service.actividad.DesenlacesDeOportunidad desenlaces = mock(com.controllocal.service.actividad.DesenlacesDeOportunidad.class);
    private final com.controllocal.service.actividad.ConversionDeProspecciones conversiones = mock(com.controllocal.service.actividad.ConversionDeProspecciones.class);

    private final com.controllocal.service.actividad.MedicionDeCompromisos compromisos = mock(com.controllocal.service.actividad.MedicionDeCompromisos.class);

    private final com.controllocal.service.actividad.MedicionesC7 mediciones = new com.controllocal.service.actividad.MedicionesC7(interacciones,desenlaces,conversiones,compromisos,mock(com.controllocal.service.actividad.ContextosDeActividad.class),alcances);

    private final IndicadorServiceImpl service = new IndicadorServiceImpl(
            captaciones, oportunidades, solicitudes, contratos, visitas, interacciones,
            prospecciones, motivos, agentes, alcances, seguimiento,mediciones);

    private final Actor admin = new Actor(ORG, 1L, 20L, "TENANT_ADMIN");
    private final Actor broker = new Actor(ORG, 2L, BROKER, "BROKER");
    private final Actor agente = new Actor(ORG, 3L, AGENTE, "AGENTE");

    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));

    @BeforeEach
    void vacio() {
        when(compromisos.leer(any(),any(),any())).thenAnswer(inv->{
            var v=(com.controllocal.service.actividad.IndicadoresIndividuales.Ventana)inv.getArgument(1);
            return new com.controllocal.service.actividad.MedicionDeCompromisos.Lectura(com.controllocal.service.actividad.IndicadoresIndividuales.seguimiento(List.of(),v),true);
        });
        when(alcances.de(broker)).thenReturn(
                new Alcances.Alcance(ORG, false, List.of(AGENTE)));
        when(alcances.de(agente)).thenReturn(
                new Alcances.Alcance(ORG, false, List.of(AGENTE)));
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());
        when(visitas.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());
        when(interacciones.indicadores(any(Actor.class))).thenReturn(new InteraccionService.LecturaIndicadores(List.of(),Set.of()));
        when(conversiones.leer(any(Actor.class),any())).thenReturn(new com.controllocal.service.actividad.ConversionDeProspecciones.Lectura(List.of(),true));
        when(seguimiento.enAlcance(any(Actor.class))).thenReturn(List.of());
        when(seguimiento.vencidas(any(Actor.class))).thenReturn(List.of());
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());
        when(motivos.principalPorCaptacion(anyLong(), anyBoolean(), anyCollection()))
                .thenReturn(List.of());
        when(contratos.indicadores(anyLong())).thenReturn(List.of());
        when(agentes.listarFichas(anyLong())).thenReturn(List.of());
        when(agentes.buscarFichas(anyLong(), anyCollection())).thenReturn(List.of());
        when(agentes.countByOrganizacionId(anyLong())).thenReturn(0L);
    }

    // ---------- periodo y ambito ----------

    @Test
    void unPeriodoDesconocidoCaeEnSeisMesesYSerieMensual() {
        Resumen resumen = service.resumen("gibberish", agente);

        // 180 dias caen fuera del tramo diario (<= 31), asi que la serie es mensual y
        // toca 6 o 7 cubos segun el dia del mes en que se consulte.
        int cubos = resumen.mesesEtiquetas().size();
        assertTrue(cubos == 6 || cubos == 7, "180 dias tocan 6 o 7 meses, no " + cubos);
        String ultima = resumen.mesesEtiquetas().getLast();
        assertTrue(ultima.matches("[A-Z][a-z]{2} \\d{2}"),
                "la etiqueta mensual es 'Mmm YY': " + ultima);
        assertEquals(String.valueOf(hoy.getYear()).substring(2), ultima.substring(4),
                "el ultimo cubo es el mes en curso");
    }

    @Test
    void hastaTreintaYUnDiasLaSerieEsDiariaConEtiquetaDiaMes() {
        Resumen resumen = service.resumen("7d", agente);

        assertEquals(7, resumen.mesesEtiquetas().size());
        assertEquals(String.format("%02d/%02d", hoy.getDayOfMonth(), hoy.getMonthValue()),
                resumen.mesesEtiquetas().getLast());
        assertEquals(resumen.mesesEtiquetas().size(), resumen.cierresPorMes().size());
        assertEquals(resumen.mesesEtiquetas().size(), resumen.captacionesPorPeriodo().size());
    }

    @Test
    void losSinonimosDelPeriodoCoincidenConSuCodigo() {
        assertEquals(30, service.resumen("mes", agente).mesesEtiquetas().size());
        assertEquals(15, service.resumen("15", agente).mesesEtiquetas().size());
        // 1m son 30 dias: sigue siendo serie diaria; 3m ya es mensual.
        assertTrue(service.resumen("3m", agente).mesesEtiquetas().size() <= 4);
    }

    @Test
    void elAmbitoDependeDelRol() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.resumen(null, admin));
        assertEquals("Mi actividad", service.resumen(null, agente).ambito());

        // El broker decia "Reportes de equipo", que se lee como el titulo de una
        // seccion y no como un alcance. D-E2-1 seccion 6.1 lo fija en "Mi
        // equipo", y la diferencia importa: es lo que evita que lea las cifras
        // de su equipo como si fueran su produccion personal -- el malentendido
        // que D-E2-2 seccion 4 cerro al prohibir pedirsela.
        assertEquals("Mi equipo", service.resumen(null, broker).ambito(),
                "el ambito dice de quien habla el Inicio, no como se titula un informe");
    }

    @Test
    void gobiernoNoObtieneIndicadoresAunqueUnMockOfrezcaAlcanceGlobal() {
        when(alcances.de(admin)).thenReturn(new Alcances.Alcance(ORG,true,List.of()));
        assertThrows(AccesoNoAutorizadoException.class,()->service.resumen("7d",admin));
        assertThrows(AccesoNoAutorizadoException.class,()->service.avance(admin));
        verifyNoInteractions(captaciones,oportunidades,solicitudes,contratos,visitas,prospecciones,interacciones);
    }

    // ---------- escalares ----------

    @Test
    void losPillsDelMenuNoDependenDelPeriodo() {
        LocalDate vieja = hoy.minusYears(3);
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "P", vieja, 100L),
                captacion(2L, AGENTE, "O", vieja, 101L),
                captacion(3L, AGENTE, "A", vieja, 102L),
                captacion(4L, AGENTE, "A", vieja, 102L)));
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                solicitud(10L, AGENTE, "E", vieja, 1L, 200L, 300L),
                solicitud(11L, AGENTE, "O", vieja, 1L, 201L, 300L),
                solicitud(12L, AGENTE, "C", vieja, 1L, 202L, 300L)));
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", vieja, 3L, 300L),
                oportunidad(21L, AGENTE, "S", vieja, 3L, 301L),
                oportunidad(22L, AGENTE, "N", vieja, 3L, 302L)));

        Resumen resumen = service.resumen("7d", agente);

        assertEquals(1, resumen.captacionesPorRevisar());
        // `captacionesPendientes` ya no existe: duplicaba el campo de arriba con
        // otro nombre porque la v1 lo emitia asi (retirado el 2026-08-08).
        assertEquals(1, resumen.captacionesObservadas());
        assertEquals(2, resumen.captacionesActivas());
        assertEquals(2, resumen.solicitudesPorEvaluar());
        assertEquals(2, resumen.oportunidadesActivas());
        // Dos captaciones ACTIVAS sobre el MISMO local cuentan una sola propiedad.
        assertEquals(1, resumen.propiedadesEquipo());
        assertEquals(0, resumen.captacionesTotales(), "nada nacio en la ventana de 7 dias");
    }

    @Test
    void agentesYBrokersActivosSeCuentanPorRol() {
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG,false,List.of(AGENTE,OTRO_AGENTE)));
        assertEquals(2,service.resumen(null,broker).agentesActivos());
        assertEquals(1,service.resumen(null,broker).brokersActivos());
        assertEquals(0,service.resumen(null,agente).brokersActivos());
        assertEquals(1,service.resumen(null,agente).agentesActivos());
    }

    @Test
    void elDonutEsUnaParticionExclusivaConPrecedencia() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L),   // sin nada -> Captacion activa
                captacion(2L, AGENTE, "A", hoy, 102L),   // con oportunidad -> Interesados
                captacion(3L, AGENTE, "A", hoy, 103L),   // con solicitud G -> Con solicitud
                captacion(4L, AGENTE, "A", hoy, 104L),   // con solicitud E -> En evaluacion
                captacion(5L, AGENTE, "C", hoy, 105L))); // cerrada con contrato -> Alquilada
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 2L, 300L),
                oportunidad(21L, AGENTE, "S", hoy, 3L, 301L),
                oportunidad(22L, AGENTE, "S", hoy, 4L, 302L)));
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                solicitud(30L, AGENTE, "G", hoy, 3L, 21L, 301L),
                solicitud(31L, AGENTE, "E", hoy, 4L, 22L, 302L)));
        when(contratos.indicadores(anyLong())).thenReturn(List.of(
                contrato(40L, hoy, AGENTE, 5L, AGENTE, 5L)));

        List<Conteo> etapas = service.resumen(null, agente).etapas();

        assertEquals(List.of("Captacion activa", "Clientes interesados", "Con solicitud",
                        "En evaluacion", "Alquilada"),
                etapas.stream().map(Conteo::nombre).toList());
        assertEquals(List.of(1, 1, 1, 1, 1), etapas.stream().map(Conteo::valor).toList());
    }

    @Test
    void unaCaptacionNoActivaSinContratoNoEntraEnNingunaEtapa() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "P", hoy, 101L),
                captacion(2L, AGENTE, "O", hoy, 102L),
                captacion(3L, AGENTE, "R", hoy, 103L),
                captacion(4L, AGENTE, "V", hoy, 104L)));

        assertEquals(0, service.resumen(null, agente).etapas().stream()
                .mapToInt(Conteo::valor).sum());
    }

    @Test
    void elDonutNoSeAcotaAlPeriodoPeroLaSaludSi() {
        LocalDate vieja = hoy.minusYears(2);
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", vieja, 101L),
                captacion(2L, AGENTE, "A", hoy, 102L)));

        Resumen resumen = service.resumen("7d", agente);

        assertEquals(2, resumen.etapas().stream().mapToInt(Conteo::valor).sum());
        assertEquals(1, resumen.captacionesSalud().getFirst().valor());
        assertEquals("Activas", resumen.captacionesSalud().getFirst().nombre());
    }

    @Test
    void laSaludAgrupaRechazadaVencidaYCerradaComoBloqueadas() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "R", hoy, 101L),
                captacion(2L, AGENTE, "V", hoy, 102L),
                captacion(3L, AGENTE, "C", hoy, 103L)));

        List<Conteo> salud = service.resumen("7d", agente).captacionesSalud();

        assertEquals("Bloqueadas/cerradas", salud.getLast().nombre());
        assertEquals(3, salud.getLast().valor());
    }

    // ---------- embudo ----------

    @Test
    void sinOportunidadesElPrimerTramoEsCeroPorCientoYNoCien() {
        // Descongelado 2026-08-08. La v1 pintaba "100 %" en la cabecera de un
        // embudo vacio: la unica lectura posible era "todo va perfecto" en un
        // periodo en el que no paso nada.
        List<Embudo> embudo = service.resumen(null, agente).embudo();

        assertEquals(0, embudo.getFirst().valor());
        assertEquals(0, embudo.getFirst().porcentaje(),
                "sin base no hay porcentaje que pintar");
        assertEquals(0, embudo.get(1).porcentaje());
    }

    @Test
    void conVisitaRealizadaSOLOcuentaLasRealizadas() {
        // Descongelado 2026-08-08. Antes contaba visitas de cualquier estado,
        // canceladas incluidas, e inflaba la conversion justo en el tramo que
        // mide si el equipo esta trabajando.
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 1L, 300L),
                oportunidad(21L, AGENTE, "F", hoy, 1L, 301L)));
        when(visitas.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                visita(50L, AGENTE, "C", hoy, 20L),      // CANCELADA: ya no cuenta
                visita(51L, AGENTE, "R", hoy, 21L)));    // REALIZADA: esta si

        List<Embudo> embudo = service.resumen("7d", agente).embudo();

        assertEquals(2, embudo.getFirst().valor());
        assertEquals(1, embudo.get(1).valor(), "solo la REALIZADA");
        assertEquals(50, embudo.get(1).porcentaje());
        assertEquals(1, embudo.get(2).valor(), "F cuenta como con solicitud creada");
        assertEquals(1, embudo.getLast().valor());
    }

    // ---------- conversion por cohorte ----------

    @Test
    void laConversionPorCohorteNuncaSuperaCienYUsaLaFechaDeCaptacion() {
        // Dos captaciones nacidas hoy, una ya cerrada: 50 % en el cubo de hoy.
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L),
                captacion(2L, AGENTE, "C", hoy, 102L)));
        when(contratos.indicadores(anyLong())).thenReturn(List.of(
                contrato(40L, hoy, AGENTE, 2L, AGENTE, 2L),
                // Un cierre de una cohorte anterior NO infla la conversion de esta.
                contrato(41L, hoy, AGENTE, 99L, AGENTE, 99L)));

        Resumen resumen = service.resumen("7d", agente);

        assertEquals(2, resumen.cierres(), "los dos contratos cerraron en la ventana");
        assertEquals(1, resumen.cierresCohorte());
        assertEquals(50, resumen.conversionPropia());
        assertEquals(50, resumen.conversionPorPeriodo().getLast());
        assertTrue(resumen.conversionPorPeriodo().stream().allMatch(v -> v <= 100));
    }

    // ---------- alcance indirecto del contrato ----------

    @Test
    void elContratoEntraPorSuSolicitudOPorSuOportunidad() {
        when(contratos.indicadores(anyLong())).thenReturn(List.of(
                contrato(40L, hoy, AGENTE, 1L, OTRO_AGENTE, 1L),      // por solicitud
                contrato(41L, hoy, OTRO_AGENTE, 2L, AGENTE, 2L),      // por oportunidad
                contrato(42L, hoy, OTRO_AGENTE, 3L, OTRO_AGENTE, 3L)));// de nadie del alcance

        assertEquals(2, service.resumen("7d", agente).cierres());
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG,false,List.of(AGENTE,OTRO_AGENTE)));
        assertEquals(3, service.resumen("7d", broker).cierres(), "el broker ve ambos agentes que supervisa");
    }

    @Test
    void elDesempenoAgrupaElContratoPorElAgenteDeLaSolicitudCuandoEstaEnAlcance() {
        when(agentes.buscarFichas(anyLong(), anyCollection()))
                .thenReturn(List.of(agente(AGENTE, "Valeria Mora")));
        when(contratos.indicadores(anyLong())).thenReturn(List.of(
                // La solicitud es de OTRO agente: el cierre se agrupa por la oportunidad.
                contrato(40L, hoy, OTRO_AGENTE, 1L, AGENTE, 1L)));

        List<Desempeno> desempeno = service.resumen("7d", agente).desempeno();

        assertEquals(1, desempeno.size());
        assertEquals("Valeria Mora", desempeno.getFirst().nombre());
        assertEquals(1, desempeno.getFirst().cierres());
    }

    // ---------- desempeno ----------



    @Test
    void elDesempenoOmiteLasFilasEnCeroYCortaEnOcho() {
        List<DetalleAgente> fuente = new ArrayList<>();
        List<IndicadorCaptacion> caps = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long id = 100L + i;
            fuente.add(agente(id, "Agente " + i));
            caps.add(captacion(id, id, "A", hoy, 500L + i));
        }
        fuente.add(agente(999L, "Sin actividad"));
        when(agentes.buscarFichas(anyLong(), anyCollection())).thenReturn(fuente);
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(caps);
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG, false,
                fuente.stream().map(DetalleAgente::getId).toList()));

        List<Desempeno> desempeno = service.resumen("7d", broker).desempeno();

        assertEquals(8, desempeno.size());
        assertFalse(desempeno.stream().anyMatch(d -> "Sin actividad".equals(d.nombre())));
    }

    private static RevisionSeguimiento revision(long prospeccion, LocalDate proxima, LocalDate pospuesta) {
        return new RevisionSeguimiento(prospeccion + 1000, 1, prospeccion,
                "PROPIETARIO", "RECONTACTAR_PROPIETARIO", null, 0, proxima, "ACORDADA",
                pospuesta, pospuesta == null ? null : "Posposición propia", false, false, false,null);
    }

    @Test
    void unaEsperaSinFechaNoEsAlDiaYUnaPosposicionConFechaSeRespeta() {
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", hoy, hoy.minusDays(90)),
                prospeccion(61L, AGENTE, "S", hoy, hoy.minusDays(90)),
                prospeccion(62L, AGENTE, "T", hoy, hoy.minusDays(90)),
                prospeccion(63L, AGENTE, "S", hoy, hoy.minusDays(90))));
        var espera = revision(60, null, null);
        var pospuesta = revision(61, hoy.minusDays(10), hoy.plusDays(2));
        var restringida = new RevisionSeguimiento(1063, 1, 63, "PROPIETARIO", "RESTRINGIDO",
                null, 0, hoy.plusDays(2), "ACORDADA", null, null, false, true, false,null);
        // La fila captada ya no forma parte del seguimiento canónico; sus fechas antiguas no mandan.
        when(seguimiento.enAlcance(agente)).thenReturn(List.of(espera, pospuesta, restringida));

        var resultado = service.resumen("7d", agente).operativo();

        assertEquals(0, resultado.recontactosVencidos());
        assertEquals(1, resultado.recontactosAlDia());
        sinLegados(resultado);
    }

    @Test
    void laCohorteNoSeAmpliaPorUnSeguimientoVencidoFueraDelPeriodo() {
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", hoy.minusYears(2), hoy.minusDays(90))));
        when(seguimiento.enAlcance(agente)).thenReturn(List.of(revision(60, hoy.minusDays(1), null)));
        when(seguimiento.vencidas(agente)).thenReturn(List.of(revision(60, hoy.minusDays(1), null)));

        var resultado = service.resumen("7d", agente).operativo();

        assertEquals(0, resultado.recontactosVencidos());
        assertEquals(0, resultado.recontactosAlDia());
    }

    @Test
    void unFalloDelSeguimientoCanonicoNoEsCeroVencidos() {
        when(seguimiento.vencidas(agente)).thenThrow(new IllegalStateException("R27 no disponible"));
        assertThrows(IllegalStateException.class, () -> service.resumen("7d", agente));
    }

    // ---------- operativo ----------

    @Test
    void sinProspeccionesEnLaVentanaElOperativoSaleVACIO() {
        // Descongelado 2026-08-08. La v1 caia a TODAS las del alcance cuando la
        // ventana venia vacia, asi que "ultimos 7 dias" pasaba a significar
        // "desde siempre" sin avisar: estas dos son de hace dos anos y aun asi
        // se contaban, con su recontacto vencido hace 30 dias presentado como
        // si fuera de esta semana.
        LocalDate vieja = hoy.minusYears(2);
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", vieja, hoy.minusDays(30)),
                prospeccion(61L, AGENTE, "T", vieja, null)));

        var operativo = service.resumen("7d", agente).operativo();

        assertEquals(0, operativo.recontactosVencidos(),
                "ninguna de las dos cae en la ventana de 7 dias");
        sinLegados(operativo);
    }

    @Test
    void conProspeccionesEnLaVentanaElOperativoLasCuenta() {
        // La contraparte del anterior: el cambio no apaga el indicador, solo lo
        // acota al periodo que se pidio.
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(62L, AGENTE, "S", hoy, hoy.minusDays(30)),
                prospeccion(63L, AGENTE, "T", hoy, null)));
        when(seguimiento.vencidas(agente)).thenReturn(List.of(revision(62, hoy, null)));

        var operativo = service.resumen("7d", agente).operativo();

        assertEquals(1, operativo.recontactosVencidos());
        sinLegados(operativo);
    }

    @Test
    void elVencimientoEsElDelPuertoR27SinReinterpretarLasFechasAntiguas() {
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", hoy, hoy.minusDays(7)),  // vencido, 7 dias
                prospeccion(61L, AGENTE, "S", hoy, hoy.minusDays(6)),  // aun al dia
                prospeccion(62L, AGENTE, "S", hoy, hoy.minusDays(11)), // vencido, 11 dias
                prospeccion(63L, AGENTE, "T", hoy, hoy.minusDays(30)), // captada: no cuenta
                prospeccion(64L, AGENTE, "D", hoy, hoy.minusDays(30)), // descartada: no cuenta
                prospeccion(65L, AGENTE, "S", hoy, null)));            // sin recontacto
        when(seguimiento.enAlcance(agente)).thenReturn(List.of(
                revision(60, hoy, null), revision(61, hoy.plusDays(2), null),
                revision(62, hoy.minusDays(1), null)));
        when(seguimiento.vencidas(agente)).thenReturn(List.of(
                revision(60, hoy, null), revision(62, hoy.minusDays(1), null)));

        var operativo = service.resumen("7d", agente).operativo();

        assertEquals(2, operativo.recontactosVencidos());
        assertEquals(1, operativo.recontactosAlDia());
        sinLegados(operativo);
    }

    @Test
    void visitasPendientesYSolicitudesSinCierreNoSeAcotanAlPeriodo() {
        LocalDate vieja = hoy.minusYears(2);
        when(visitas.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                visita(50L, AGENTE, "P", vieja, 20L),
                visita(51L, AGENTE, "G", vieja, 21L),
                visita(52L, AGENTE, "R", vieja, 22L)));
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                solicitud(30L, AGENTE, "A", vieja, 1L, 20L, 300L),
                solicitud(31L, AGENTE, "C", vieja, 1L, 21L, 300L)));

        var operativo = service.resumen("7d", agente).operativo();

        assertEquals(2, operativo.visitasPendientes());
        assertEquals(1, operativo.solicitudesSinCierre());
    }

    // ---------- senales: el hecho ya interpretado (E1, R-07) ----------

    @Test
    void lasSenalesLleganCompletasYEnElOrdenDeLaPolitica() {
        List<Senal> senales = service.resumen("7d", agente).senales();

        assertEquals(Concepto.values().length, senales.size(),
                "falta algun concepto: el cliente no puede distinguir 'no vino' de 'no hay'");
        assertEquals(
                Arrays.stream(Concepto.values())
                        .sorted(Comparator.comparingInt(Concepto::prioridad))
                        .map(Enum::name).toList(),
                senales.stream().map(Senal::concepto).toList(),
                "el orden lo fija la politica, no quien pinta");
    }

    /** CAT20.8 sustituye la magnitud antigua sin alterar los pendientes R27. */
    @Test
    void laDemoraNoDefinidaViajaNoEvaluableSinInventarCeroNiClasificacion() {
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", hoy, hoy.minusDays(7)),
                prospeccion(62L, AGENTE, "S", hoy, hoy.minusDays(11))));
        when(seguimiento.vencidas(agente)).thenReturn(List.of(
                revision(60, hoy, null), revision(62, hoy, null)));

        Resumen resumen = service.resumen("7d", agente);
        sinLegados(resumen.operativo());
        assertFalse(resumen.senales().stream().anyMatch(s->"DEMORA_DE_SEGUIMIENTO".equals(s.concepto())));
        assertEquals("SIN_BASE",resumen.cumplimientoDeSeguimiento().estadoCalculo());
        assertNull(resumen.cumplimientoDeSeguimiento().medicion().porcentaje());

        Senal vencidos = senal(resumen, Concepto.RECONTACTO_VENCIDO);
        assertEquals(2, vencidos.valor());
        assertEquals(NivelAtencion.ALTO.name(), vencidos.nivelAtencion());
    }

    @Test
    void sinNadaPendienteLoQueSeAtiendeQuedaSIN_PENDIENTESYLoInformativoSigueSiendoINFORMATIVO() {
        Resumen resumen = service.resumen("7d", agente);

        assertEquals(NivelAtencion.SIN_PENDIENTES.name(),
                senal(resumen, Concepto.SOLICITUD_POR_EVALUAR).nivelAtencion());
        assertEquals(NivelAtencion.SIN_PENDIENTES.name(),
                senal(resumen, Concepto.RECONTACTO_VENCIDO).nivelAtencion());
        assertFalse(senal(resumen, Concepto.SOLICITUD_POR_EVALUAR).requiereAtencion());

        Senal visitas = senal(resumen, Concepto.VISITA_PENDIENTE);
        assertEquals(NivelAtencion.INFORMATIVO.name(), visitas.nivelAtencion(),
                "cero visitas agendadas no es 'todo al dia': es cero");
        assertFalse(visitas.requiereAtencion());
    }

    @Test
    void lasSenalesLeenLosMismosNumerosQueElResumen() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "P", hoy, 101L),
                captacion(2L, AGENTE, "P", hoy, 102L)));
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                solicitud(30L, AGENTE, "E", hoy, 1L, 20L, 300L)));

        Resumen resumen = service.resumen("7d", agente);

        assertEquals(resumen.captacionesPorRevisar(),
                senal(resumen, Concepto.CAPTACION_POR_REVISAR).valor());
        assertEquals(resumen.solicitudesPorEvaluar(),
                senal(resumen, Concepto.SOLICITUD_POR_EVALUAR).valor());
        assertEquals(resumen.cierres(), senal(resumen, Concepto.CIERRE_REGISTRADO).valor());
        assertEquals(resumen.agentesActivos(),
                senal(resumen, Concepto.COBERTURA_DE_AGENTES).valor());
    }

    // ---------- E2.0 / E2.1: conversion no calculable y cabecera de decision ----------

    /**
     * E2.0. Sin captaciones en el periodo no se convirtio nada <b>porque no
     * habia nada que convertir</b>. Emitir 0 hacia indistinguibles ese caso y el
     * de haber trabajado doce sin cerrar ninguna — y el dashboard lo "resolvia"
     * mostrando la conversion del agente que mas cerro.
     */
    @Test
    void sinCohorteLaConversionPropiaNoEsCeroSinoInexistente() {
        assertNull(service.resumen("7d", agente).conversionPropia(),
                "sin captaciones en el periodo no hay tasa que calcular");
    }

    @Test
    void conCohorteLaConversionPropiaEsUnNumero() {
        long idCap = 1L;
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(idCap, AGENTE, "A", hoy, 101L),
                captacion(2L, AGENTE, "A", hoy, 102L)));
        when(contratos.indicadores(anyLong())).thenReturn(List.of(
                contrato(70L, hoy, AGENTE, idCap, AGENTE, idCap)));

        assertEquals(50, service.resumen("7d", agente).conversionPropia(),
                "1 cerrada de 2 captaciones del periodo");
    }

    /**
     * E2.1. La cabecera abre con "N cosas necesitan tu atencion", y ese N no se
     * puede derivar sumando las senales en el cliente: la demora de seguimiento
     * vale DIAS. Con 1 solicitud + 2 recontactos y una demora no evaluable,
     * el titular conserva las 3 cosas conocidas.
     */
    @Test
    void pendientesDeAtencionSumaCosasYNoMagnitudes() {
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                solicitud(30L, AGENTE, "E", hoy, 1L, 20L, 300L)));
        when(prospecciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                prospeccion(60L, AGENTE, "S", hoy, hoy.minusDays(7)),
                prospeccion(62L, AGENTE, "S", hoy, hoy.minusDays(11))));
        when(seguimiento.vencidas(agente)).thenReturn(List.of(
                revision(60, hoy, null), revision(62, hoy, null)));

        Resumen resumen = service.resumen("7d", agente);

        sinLegados(resumen.operativo());
        assertEquals(3, resumen.pendientesDeAtencion(),
                "1 solicitud por evaluar + 2 recontactos vencidos; la demora no aporta un conteo ficticio");
    }

    @Test
    void sinNadaPendienteLaCabeceraDiceCero() {
        assertEquals(0, service.resumen("7d", agente).pendientesDeAtencion());
    }

    /** Lo informativo nunca entra en el titular, por muchas visitas que haya. */
    @Test
    void loInformativoNoCuentaComoPendiente() {
        when(visitas.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                visita(50L, AGENTE, "P", hoy, 20L),
                visita(51L, AGENTE, "G", hoy, 21L)));

        Resumen resumen = service.resumen("7d", agente);

        assertEquals(2, resumen.operativo().visitasPendientes());
        assertEquals(0, resumen.pendientesDeAtencion(),
                "una visita agendada es agenda, no un pendiente que reclame");
    }

    private static Senal senal(Resumen resumen, Concepto concepto) {
        return resumen.senales().stream()
                .filter(s -> concepto.name().equals(s.concepto()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no viajo la senal " + concepto));
    }

    // ---------- avance comercial (RF-017) ----------

    @Test
    void elAvanceSoloMiraCaptacionesActivasYOrdenaPorAbiertasYInteracciones() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince"),
                captacion(2L, AGENTE, "A", hoy, 102L, "CAP-0002", "Av. Dos", "Surco"),
                captacion(3L, AGENTE, "P", hoy, 103L, "CAP-0003", "Av. Tres", "Miraflores")));
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 2L, 300L),
                oportunidad(21L, AGENTE, "A", hoy, 2L, 301L),
                oportunidad(22L, AGENTE, "N", hoy, 1L, 302L)));

        AvanceComercial avance = service.avance(agente);

        assertEquals("Mi avance comercial", avance.ambito());
        assertEquals(2, avance.propiedades(), "la pendiente de revision no entra");
        assertEquals(List.of("CAP-0002", "CAP-0001"),
                avance.detalle().stream().map(AvancePropiedad::codigoCaptacion).toList());
        assertEquals(3, avance.oportunidadesTotales());
        assertEquals(2, avance.oportunidadesAbiertas());
    }

    @Test
    void oportunidadesConSolicitudUsaElRespaldoPorEstado() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince")));
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "S", hoy, 1L, 300L),
                oportunidad(21L, AGENTE, "F", hoy, 1L, 301L)));
        // Ninguna solicitud quedo enlazada; el estado de la oportunidad es el respaldo.
        when(solicitudes.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of());

        AvancePropiedad fila = service.avance(agente).detalle().getFirst();

        assertEquals(0, fila.solicitudesRecibidas());
        assertEquals(2, fila.oportunidadesConSolicitud());
        assertEquals(100, fila.tasaOportSolicitud());
    }

    @Test
    void losInteresadosGlobalesSonClientesDistintosNoLaSumaDeLasFilas() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince"),
                captacion(2L, AGENTE, "A", hoy, 102L, "CAP-0002", "Av. Dos", "Surco")));
        // El MISMO cliente 300 interesado en las dos propiedades.
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 1L, 300L),
                oportunidad(21L, AGENTE, "A", hoy, 2L, 300L)));

        AvanceComercial avance = service.avance(agente);

        assertEquals(1, avance.interesados());
        assertEquals(2, avance.detalle().stream().mapToInt(AvancePropiedad::interesados).sum());
    }

    @Test
    void laInteraccionCuentaPorCaptacionDirectaOPorSuOportunidadSinDuplicar() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince")));
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 1L, 300L)));
        when(interacciones.indicadores(agente)).thenReturn(new InteraccionService.LecturaIndicadores(List.of(
                interaccion(70L, AGENTE, 1L, null),   // directa a la captacion
                interaccion(71L, AGENTE, null, 20L),  // via la oportunidad
                interaccion(72L, AGENTE, 1L, 20L)),Set.of()));  // por las dos: cuenta UNA vez

        assertEquals(3, service.avance(agente).detalle().getFirst().interacciones());
    }

    @Test
    void elTotalGlobalNoSumaContextosSolapadosDeUnaMismaComunicacion() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince"),
                captacion(2L, AGENTE, "A", hoy, 101L, "CAP-0002", "Av. Uno", "Lince")));
        when(interacciones.indicadores(agente)).thenReturn(new InteraccionService.LecturaIndicadores(List.of(new MedicionInteraccion(
                70, 0, AGENTE, hoy, instante(hoy).toInstant(), null, null, List.of(),
                List.of(new VinculoFicha("ENCARGO", 1, null), new VinculoFicha("ENCARGO", 2, null)))),Set.of()));

        AvanceComercial avance = service.avance(agente);

        assertEquals(2, avance.detalle().stream().mapToInt(AvancePropiedad::interacciones).sum());
        assertEquals(1, avance.interacciones());
    }

    @Test
    void fechaDeRegistroNoSeConvierteEnFechaEfectivaParaElIndicadorTemporal() {
        when(interacciones.indicadores(agente)).thenReturn(new InteraccionService.LecturaIndicadores(List.of(new MedicionInteraccion(
                70, 0, AGENTE, null, instante(hoy).toInstant(), null, null, List.of(), List.of())),Set.of()));

        assertEquals(0, service.resumen("7d", agente).interacciones());
    }

    @Test
    void unFalloTecnicoDelLectorAutorizadoNoProduceUnIndicadorVacio() {
        when(interacciones.indicadores(agente)).thenThrow(new IllegalStateException("fallo técnico"));

        assertThrows(IllegalStateException.class, () -> service.resumen("7d", agente));
    }
    @Test void fuenteDeRespuestaRestringidaNoSePresentaComoCeroOConsultaSinResponder(){
        var q=new MedicionInteraccion(70,0,AGENTE,hoy.minusDays(1),java.time.Instant.now(),null,null,List.of(),List.of(),null,
                new InteraccionService.DatoAtencionConsultas(true,new InteraccionService.MomentoHecho(hoy.minusDays(1),java.time.LocalTime.NOON,java.time.ZoneOffset.ofHours(-5)),List.of(),0));
        when(interacciones.indicadores(agente)).thenReturn(new InteraccionService.LecturaIndicadores(List.of(q),Set.of(70L)));
        var r=service.resumen("7d",agente).primeraRespuesta();assertEquals("NO_EVALUABLE",r.estadoCalculo());
        assertTrue(r.causa().contains("Permiso insuficiente"));assertNull(r.medicion());
    }

    @Test
    void elMotivoPrincipalEsElMasFrecuenteYViajaComoDescripcion() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince"),
                captacion(2L, AGENTE, "A", hoy, 102L, "CAP-0002", "Av. Dos", "Surco")));
        when(motivos.principalPorCaptacion(anyLong(), anyBoolean(), anyCollection())).thenReturn(
                List.of(motivo(1L, "P", 3), motivo(1L, "U", 1)));

        Map<String, String> porCodigo = service.avance(agente).detalle().stream()
                .collect(Collectors.toMap(AvancePropiedad::codigoCaptacion,
                        AvancePropiedad::motivoNoContinuidad));

        assertEquals("Precio", porCodigo.get("CAP-0001"));
        assertEquals("", porCodigo.get("CAP-0002"), "sin motivos viaja cadena vacia");
    }

    @Test
    void lasVisitasDelAvanceSeSeparanEnProgramadasYConcretadas() {
        when(captaciones.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                captacion(1L, AGENTE, "A", hoy, 101L, "CAP-0001", "Av. Uno", "Lince")));
        when(oportunidades.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                oportunidad(20L, AGENTE, "A", hoy, 1L, 300L),
                oportunidad(21L, AGENTE, "A", hoy, 1L, 301L)));
        when(visitas.indicadores(anyLong(), anyBoolean(), anyCollection())).thenReturn(List.of(
                visita(50L, AGENTE, "P", hoy, 20L),
                visita(51L, AGENTE, "G", hoy, 20L),
                visita(52L, AGENTE, "R", hoy, 21L),
                visita(53L, AGENTE, "C", hoy, 21L)));

        AvancePropiedad fila = service.avance(agente).detalle().getFirst();

        assertEquals(2, fila.visitasProgramadas());
        assertEquals(1, fila.visitasConcretadas());
        assertEquals(2, fila.oportunidadesConVisita());
        assertEquals(100, fila.tasaOportVisita());
    }

    @Test
    void elAvanceDelAdminYDelBrokerLlevanSuPropioAmbito() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.avance(admin));
        assertEquals("Avance comercial del equipo", service.avance(broker).ambito());
    }

    // ---------- fabricas de proyecciones ----------

    private IndicadorCaptacion captacion(long id, long idAgente, String estado,
                                         LocalDate fecha, Long idPropiedad) {
        return captacion(id, idAgente, estado, fecha, idPropiedad, "CAP-" + id, "Av. " + id, "Lince");
    }

    private IndicadorCaptacion captacion(long id, long idAgente, String estado, LocalDate fecha,
                                         Long idPropiedad, String codigo, String direccion,
                                         String distrito) {
        return new CaptacionFila(id, idAgente, estado, fecha, idPropiedad, codigo, direccion, distrito);
    }

    private IndicadorOportunidad oportunidad(long id, long idAgente, String estado,
                                             LocalDate fecha, Long idCaptacion, Long idCliente) {
        return new OportunidadFila(id, idAgente, estado, instante(fecha), idCaptacion, idCliente);
    }

    private IndicadorSolicitud solicitud(long id, long idAgente, String estado, LocalDate fecha,
                                         Long idCaptacion, Long idOportunidad, Long idCliente) {
        return new SolicitudFila(id, idAgente, estado, fecha, idCaptacion, idOportunidad, idCliente);
    }

    private IndicadorContrato contrato(long id, LocalDate fechaCierre, Long agenteSolicitud,
                                       Long captacionSolicitud, Long agenteOportunidad,
                                       Long captacionOportunidad) {
        return new ContratoFila(id, fechaCierre, agenteSolicitud, captacionSolicitud,
                agenteOportunidad, captacionOportunidad);
    }

    private IndicadorVisita visita(long id, long idAgente, String estado, LocalDate fecha,
                                   Long idOportunidad) {
        return new VisitaFila(id, idAgente, estado, fecha, idOportunidad);
    }

    private MedicionInteraccion interaccion(long id, long idAgente, Long idCaptacion,
                                             Long idOportunidad) {
        List<VinculoFicha> vinculos = new ArrayList<>();
        if (idCaptacion != null) vinculos.add(new VinculoFicha("ENCARGO", idCaptacion, null));
        if (idOportunidad != null) vinculos.add(new VinculoFicha("OPORTUNIDAD", idOportunidad, null));
        return new MedicionInteraccion(id, 0, idAgente, hoy, instante(hoy).toInstant(),
                null, null, List.of(), List.copyOf(vinculos));
    }

    private IndicadorProspeccion prospeccion(long id, long idAgente, String estado,
                                             LocalDate registro, LocalDate recontacto) {
        return new ProspeccionFila(id, idAgente, estado, instante(registro), recontacto);
    }

    private MotivoPorCaptacion motivo(long idCaptacion, String razon, long total) {
        return new MotivoFila(idCaptacion, razon, total);
    }


    private static OffsetDateTime instante(LocalDate fecha) {
        return fecha == null ? null
                : fecha.atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static DetalleAgente agente(long id, String nombre) {
        DetalleAgente detalle = new DetalleAgente();
        ReflectionTestUtils.setField(detalle, "id", id);
        ReflectionTestUtils.setField(detalle, "rol", rol(nombre));
        return detalle;
    }


    private static PersonaRol rol(String nombre) {
        Persona persona = new Persona();
        ReflectionTestUtils.setField(persona, "nombresORazonSocial", nombre);
        PersonaRol rol = new PersonaRol();
        ReflectionTestUtils.setField(rol, "persona", persona);
        return rol;
    }

    private record CaptacionFila(long id, long idAgente, String estado, LocalDate fecha,
                                 Long idPropiedad, String codigo, String direccion, String distrito)
            implements IndicadorCaptacion {
        public Long getId() {
            return id;
        }

        public Long getIdAgente() {
            return idAgente;
        }

        public String getEstado() {
            return estado;
        }

        public LocalDate getFechaCaptacion() {
            return fecha;
        }

        public Long getIdPropiedad() {
            return idPropiedad;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getDireccion() {
            return direccion;
        }

        public String getDistrito() {
            return distrito;
        }
    }

    private record OportunidadFila(long id, long idAgente, String estado, OffsetDateTime registro,
                                   Long idCaptacion, Long idCliente)
            implements IndicadorOportunidad {
        public Long getId() {
            return id;
        }

        public Long getIdAgente() {
            return idAgente;
        }

        public String getEstado() {
            return estado;
        }

        public OffsetDateTime getFechaRegistro() {
            return registro;
        }

        public Long getIdCaptacion() {
            return idCaptacion;
        }

        public Long getIdCliente() {
            return idCliente;
        }
    }

    private record SolicitudFila(long id, long idAgente, String estado, LocalDate registro,
                                 Long idCaptacion, Long idOportunidad, Long idCliente)
            implements IndicadorSolicitud {
        public Long getId() {
            return id;
        }

        public Long getIdAgente() {
            return idAgente;
        }

        public String getEstado() {
            return estado;
        }

        public LocalDate getFechaRegistro() {
            return registro;
        }

        public Long getIdCaptacion() {
            return idCaptacion;
        }

        public Long getIdOportunidad() {
            return idOportunidad;
        }

        public Long getIdCliente() {
            return idCliente;
        }
    }

    private record ContratoFila(long id, LocalDate fechaCierre, Long agenteSolicitud,
                                Long captacionSolicitud, Long agenteOportunidad,
                                Long captacionOportunidad) implements IndicadorContrato {
        public Long getId() {
            return id;
        }

        public LocalDate getFechaCierre() {
            return fechaCierre;
        }

        public Long getIdAgenteSolicitud() {
            return agenteSolicitud;
        }

        public Long getIdCaptacionSolicitud() {
            return captacionSolicitud;
        }

        public Long getIdAgenteOportunidad() {
            return agenteOportunidad;
        }

        public Long getIdCaptacionOportunidad() {
            return captacionOportunidad;
        }
    }

    private record VisitaFila(long id, long idAgente, String estado, LocalDate fecha,
                              Long idOportunidad) implements IndicadorVisita {
        public Long getId() {
            return id;
        }

        public Long getIdAgente() {
            return idAgente;
        }

        public String getEstado() {
            return estado;
        }

        public LocalDate getFechaVisita() {
            return fecha;
        }

        public Long getIdOportunidad() {
            return idOportunidad;
        }
    }

    private record ProspeccionFila(long id, long idAgente, String estado, OffsetDateTime registro,
                                   LocalDate recontacto) implements IndicadorProspeccion {
        public Long getId() {
            return id;
        }

        public Long getIdAgente() {
            return idAgente;
        }

        public String getEstado() {
            return estado;
        }

        public OffsetDateTime getFechaRegistro() {
            return registro;
        }

        public LocalDate getFechaRecontacto() {
            return recontacto;
        }
    }

    private record MotivoFila(long idCaptacion, String razon, long total)
            implements MotivoPorCaptacion {
        public Long getIdCaptacion() {
            return idCaptacion;
        }

        public String getRazon() {
            return razon;
        }

        public long getTotal() {
            return total;
        }
    }

    private static void sinLegados(Object operativo){
        try{var serializado=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(operativo);
            assertFalse(serializado.contains("diasPromedioSinSeguimiento"));assertFalse(serializado.contains("conversionProspeccionCaptacion"));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new AssertionError(e);}
    }
}
