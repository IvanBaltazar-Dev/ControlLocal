package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.comun.EstadosDominio.EstadoRegistroPropiedad;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.RevisionDisponibilidadRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
import com.controllocal.persistence.query.ResumenCierres;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.TareaService;
import com.controllocal.service.ComisionService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.ContratoService.DatosContrato;
import com.controllocal.service.ContratoService.DatosRenovacion;
import com.controllocal.service.ContratoService.DatosTransicion;
import com.controllocal.service.ContratoService.FichaContrato;
import com.controllocal.service.ContratoService.FiltrosContrato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la cascada del cierre (§6) —la operacion mas pesada del sistema— y
 * los mensajes de ContratosRest.
 *
 * <p>Lo que este test protege de verdad: que el cierre siga tocando las
 * SIETE cosas y que las CUATRO transiciones queden auditadas. Es la mejora
 * MEJ-01 sobre la v1, que movia esos cuatro estados a mano y no dejaba
 * rastro de ninguno.
 */
class ContratoServiceImplTest {

    private static final long ORG = 1L;
    private static final long SOLICITUD = 5L;
    private static final long CONTRATO = 40L;

    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);
    private final PublicacionRepository publicaciones = mock(PublicacionRepository.class);
    private final ComisionService comisiones = mock(ComisionService.class);
    private final TareaService tareas = mock(TareaService.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final SupervisionAgenteRepository supervisiones = mock(SupervisionAgenteRepository.class);
    private final RevisionDisponibilidadRepository revisiones = mock(RevisionDisponibilidadRepository.class);

    private final ContratoServiceImpl service = new ContratoServiceImpl(
            contratos, solicitudes, oportunidades, captaciones, propiedades, precios, publicaciones,
            comisiones, alcances, new Transiciones(historial), tareas,
            mock(AlertaService.class), supervisiones, revisiones, null);

    /** vmora: agente responsable de la solicitud 5. */
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Mensajes del contrato (paridad v1)
    // ------------------------------------------------------------------

    @Test
    void sinSolicitudRespondeElMensajeV1() {
        ReglaNegocioException sinDatos = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, agente));
        assertEquals("Selecciona la solicitud aprobada que se va a alquilar.", sinDatos.getMessage());

        ReglaNegocioException cero = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(0L, null, null, null), agente));
        assertEquals("Selecciona la solicitud aprobada que se va a alquilar.", cero.getMessage());
    }

    @Test
    void laSolicitudDeOtroAgenteNoSeCierra() {
        SolicitudAlquiler ajena = solicitud(SolicitudAlquiler.APROBADA, 99L);
        when(solicitudes.buscarFicha(ORG, SOLICITUD)).thenReturn(Optional.of(ajena));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente));
        verifyNoInteractions(historial);
    }

    @Test
    void unEstadoDeContratoDesconocidoRespondeElMensajeV1() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, "Z", null), agente));
        assertEquals("Estado de contrato invalido.", error.getMessage());
    }

    @Test
    void elCierreSoloAdmiteFirmadoOVigente() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);

        // 'P' (En proceso) es un estado valido del enum, pero no de cierre.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, "P", null), agente));
        assertEquals("El cierre solo admite los estados Firmado o Vigente.", error.getMessage());
    }

    @Test
    void laFechaDeCierreNoPuedeSerFutura() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(
                        new DatosContrato(SOLICITUD, LocalDate.now().plusDays(1), null, null), agente));
        assertEquals("La fecha de cierre no puede ser futura.", error.getMessage());
    }

    @Test
    void soloSeAlquilaUnaSolicitudAprobada() {
        prepararCierreValido(SolicitudAlquiler.EN_REVISION, OportunidadComercial.SOLICITUD_CREADA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente));
        assertEquals("Solo se puede registrar el alquiler de una solicitud aprobada.",
                error.getMessage());
    }

    @Test
    void unaOportunidadYaCerradaNoAdmiteContrato() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.FINALIZADA_NO_FAVORABLE);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente));
        assertEquals("La oportunidad ya esta cerrada; no admite un nuevo contrato.", error.getMessage());
    }

    @Test
    void unaOportunidadAbiertaSinSolicitudCreadaTambienAdmiteContrato() {
        // El cable acepta A y S: no exige haber pasado por S.
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.ABIERTA);

        assertEquals(ContratoAlquiler.VIGENTE,
                service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente).estadoContrato());
    }

    @Test
    void unaOportunidadNoAdmiteDosContratos() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        when(contratos.existeDeOportunidad(ORG, 8L)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente));
        assertEquals("Esta operacion ya tiene un contrato de alquiler registrado.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // §6 — la cascada completa
    // ------------------------------------------------------------------

    @Test
    void elCierreDisparaLosSieteEfectosYAuditaLasCuatroTransiciones() {
        SolicitudAlquiler solicitud = prepararCierreValido(
                SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        OportunidadComercial oportunidad = solicitud.getOportunidad();
        Captacion captacion = oportunidad.getCaptacion();
        Propiedad propiedad = captacion.getPropiedad();
        Publicacion publicada = publicacion(Publicacion.ESTADO_PUBLICADO);
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(9L))
                .thenReturn(List.of(publicada));
        LocalDate cierre = LocalDate.now().minusDays(1);

        FichaContrato ficha = service.registrar(
                new DatosContrato(SOLICITUD, cierre, "D", "  Firmado ante notario  "), agente);

        // 1) contrato
        assertEquals(ContratoAlquiler.FIRMADO, ficha.estadoContrato());
        assertEquals(cierre, ficha.fechaCierre());
        assertEquals("Firmado ante notario", ficha.incidencias());
        // 2) comision, con la pactada de la captacion y la renta de la solicitud
        verify(comisiones).crearPendienteNormalizada(any(ContratoAlquiler.class), any(),
                eq(new BigDecimal("9000.00")), eq("PEN"), eq(agente));
        assertEquals(new BigDecimal("450.00"), ficha.comisionGenerada());
        // 3) oportunidad cerrada como exitosa, con fecha y SIN motivo
        assertEquals(OportunidadComercial.FINALIZADA_EXITOSA, oportunidad.estadoActual());
        assertNull(oportunidad.getMotivoCierre());
        // 4) solicitud cerrada
        assertEquals(SolicitudAlquiler.CERRADA, solicitud.estadoActual());
        // 5) el cierre se registra como hecho separado del plazo pactado del encargo
        assertEquals(Captacion.CERRADA, captacion.estadoActual());
        assertEquals(LocalDate.of(2027, 7, 1), captacion.getFechaFinVigencia());
        assertEquals(cierre, captacion.getFechaCierre());
        assertEquals("A", captacion.getMotivoCierre());
        assertEquals("Alquiler concretado con la solicitud SOL-260715103000.",
                captacion.getDetalleMotivoCierre());
        // 6) local fuera del mercado + precio de cierre + publicaciones de baja
        assertEquals(EstadoRegistroPropiedad.ACTIVO, propiedad.estadoRegistroTipado());
        assertEquals(DisponibilidadComercial.ALQUILADO,
                propiedad.disponibilidadComercialTipada());
        assertEquals(Propiedad.LEGADO_NO_DISPONIBLE, propiedad.estadoLegado());
        assertEquals(Publicacion.ESTADO_CERRADO, publicada.getEstado());
        ArgumentCaptor<PrecioPropiedad> precio = ArgumentCaptor.forClass(PrecioPropiedad.class);
        verify(precios).save(precio.capture());
        assertEquals("C", precio.getValue().getHito());
        assertEquals("PEN", precio.getValue().getMoneda());
        assertEquals(new BigDecimal("9000.00"), precio.getValue().getMonto());
        // La v1 fecha el precio de cierre con HOY, no con la fecha de cierre.
        assertEquals(LocalDate.now(), precio.getValue().getFecha());

        // Las CUATRO transiciones quedan auditadas (la v1 no auditaba ninguna).
        ArgumentCaptor<HistorialEstado> eventos = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial, times(4)).save(eventos.capture());
        List<String> tipos = eventos.getAllValues().stream().map(HistorialEstado::getEntidadTipo).toList();
        assertEquals(List.of("OPORTUNIDAD", "SOLICITUD_ALQUILER", "CAPTACION",
                "DISPONIBILIDAD_PROPIEDAD"), tipos);
        eventos.getAllValues().forEach(evento -> {
            assertEquals(3L, evento.getIdActor());
            assertEquals("AGENTE", evento.getTipoRolActor());
            assertEquals("Alquiler concretado con la solicitud SOL-260715103000.", evento.getMotivo());
        });
    }

    // ------------------------------------------------------------------
    // V27 - atribucion historica del cierre
    // ------------------------------------------------------------------

    @Test
    void elCierreCongelaLaAtribucionDelAlquiler() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        when(supervisiones.buscarActivaPorAgente(ORG, 30L))
                .thenReturn(Optional.of(supervision(20L)));

        service.registrar(new DatosContrato(SOLICITUD, LocalDate.now(), "V", null), agente);

        ArgumentCaptor<ContratoAlquiler> guardado = ArgumentCaptor.forClass(ContratoAlquiler.class);
        verify(contratos).save(guardado.capture());
        ContratoAlquiler contrato = guardado.getValue();
        assertEquals(30L, contrato.getIdRolAgenteCierre());
        assertEquals(20L, contrato.getIdRolBrokerCierre());
        assertEquals(7L, contrato.getIdCaptacion());
        assertEquals(9L, contrato.getIdPropiedad());
        assertEquals(50L, contrato.getIdRolCliente());
    }

    /** Sin supervisor no se inventa uno: el hueco es informacion que no existe. */
    @Test
    void sinSupervisorActivoElCierreQuedaSinBrokerAtribuido() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        when(supervisiones.buscarActivaPorAgente(ORG, 30L)).thenReturn(Optional.empty());

        service.registrar(new DatosContrato(SOLICITUD, LocalDate.now(), "V", null), agente);

        ArgumentCaptor<ContratoAlquiler> guardado = ArgumentCaptor.forClass(ContratoAlquiler.class);
        verify(contratos).save(guardado.capture());
        assertEquals(30L, guardado.getValue().getIdRolAgenteCierre());
        assertNull(guardado.getValue().getIdRolBrokerCierre());
    }

    /**
     * El porque de todo V27: una reasignacion posterior NO reescribe a quien se
     * le atribuye un alquiler ya cerrado. Antes la ficha releia el agente de la
     * solicitud, asi que el cierre cambiaba de dueno solo.
     */
    @Test
    void laFichaPublicaElAgenteAtribuidoAunqueLaCadenaCambieDespues() {
        ContratoAlquiler contrato = contratoRegistrado();
        ReflectionTestUtils.setField(contrato, "agenteCierre",
                detalleAgente(30L, "Valentina Mora"));
        // Reasignacion posterior al cierre: la cadena vigente ya dice otra cosa.
        contrato.getSolicitud().setAgente(detalleAgente(31L, "Bruno Salas"));
        Actor admin = new Actor(ORG, 1L, 10L, "TENANT_ADMIN");
        when(comisiones.porContrato(CONTRATO, admin)).thenReturn(Optional.empty());

        FichaContrato ficha = service.obtenerPorOportunidad(8L, admin);

        assertEquals(30L, ficha.agenteId());
        assertEquals("Valentina Mora", ficha.agenteNombre());
    }

    @Test
    void unaPublicacionYaCerradaNoSeVuelveATocar() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(9L))
                .thenReturn(List.of(publicacion(Publicacion.ESTADO_CERRADO)));

        service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente);

        verify(publicaciones, times(0)).save(any(Publicacion.class));
    }

    @Test
    void elAgenteQueCierraNoVeElRepartoDeLaComision() {
        prepararCierreValido(SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);

        FichaContrato ficha = service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente);

        assertNull(ficha.montoAgente());
        assertNull(ficha.montoEmpresa());
        // La bruta y el estado si los ve.
        assertEquals(new BigDecimal("450.00"), ficha.comisionGenerada());
        assertEquals("P", ficha.comisionEstado());
    }

    @Test
    void elPlazoYLaFechaFinSeDerivanDeLaSolicitud() {
        SolicitudAlquiler solicitud = prepararCierreValido(
                SolicitudAlquiler.APROBADA, OportunidadComercial.SOLICITUD_CREADA);
        solicitud.setPlazoContratoMeses(null);
        solicitud.setPlazoTentativo("24 meses");
        solicitud.setFechaInicioContrato(LocalDate.of(2026, 9, 1));

        FichaContrato ficha = service.registrar(new DatosContrato(SOLICITUD, null, null, null), agente);

        // Sin plazo numerico, el cable lo parsea del texto libre.
        assertEquals(24, ficha.plazoContratoMeses());
        assertEquals(LocalDate.of(2028, 9, 1), ficha.fechaFinContrato());
    }

    // ------------------------------------------------------------------
    // Alcance: BROKER por CAPTACION (§7), no por agente
    // ------------------------------------------------------------------

    @Test
    void elBrokerAlcanzaElContratoPorLaCaptacionDeSuEquipo() {
        contratoRegistrado();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(30L));
        when(comisiones.porContrato(CONTRATO, broker)).thenReturn(Optional.empty());

        assertEquals(CONTRATO, service.obtenerPorOportunidad(8L, broker).id());
    }

    @Test
    void elBrokerNoAlcanzaElContratoDeUnaCaptacionAjena() {
        contratoRegistrado();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(77L));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.obtenerPorOportunidad(8L, broker));
    }

    @Test
    void otroAgenteNoAlcanzaElContrato() {
        contratoRegistrado();

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.obtenerPorOportunidad(8L, new Actor(ORG, 9L, 31L, "AGENTE")));
    }

    @Test
    void sinContratoDeEsaOportunidadResponde404() {
        when(contratos.buscarPorOportunidad(ORG, 8L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.obtenerPorOportunidad(8L, agente));
    }

    @Test
    void unBrokerSinEquipoObtieneListaVaciaNoUn403() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of()));

        assertEquals(0, service.listar(1, 100, broker).items().size());
        verifyNoInteractions(contratos);
    }

    @Test
    void elListadoResuelveLasComisionesEnBloque() {
        ContratoAlquiler contrato = contratoRegistrado();
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(30L)));
        when(contratos.buscar(eq(ORG), anyBoolean(), anyBoolean(), anyCollection(),
                isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pagina(contrato));
        when(comisiones.porContratos(anyCollection(), eq(broker)))
                .thenReturn(Map.of(CONTRATO, comision()));

        List<FichaContrato> items = service.listar(1, 100, broker).items();

        assertEquals(1, items.size());
        // El broker SI ve el reparto.
        assertEquals(new BigDecimal("180.00"), items.get(0).montoAgente());
        assertEquals(new BigDecimal("270.00"), items.get(0).montoEmpresa());
        verify(comisiones).porContratos(anyCollection(), eq(broker));
    }

    // ------------------------------------------------------------------
    // Los dos gates de comision
    // ------------------------------------------------------------------

    @Test
    void asignarSinMontoRespondeAntesDeBuscarElContrato() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.asignarComision(CONTRATO, null, broker));
        assertEquals("Indica el monto del agente.", error.getMessage());
        verifyNoInteractions(contratos);
    }

    @Test
    void cobrarSinEstadoRespondeAntesDeBuscarElContrato() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobroComision(CONTRATO, "  ", null, null, broker));
        assertEquals("Indica el estado del cobro (Cobrada o Anulada).", error.getMessage());
        verifyNoInteractions(contratos);
    }

    @Test
    void asignarExigeQueElBrokerSupervisaLaCaptacionDelContrato() {
        contratoRegistrado();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(77L));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.asignarComision(CONTRATO, new BigDecimal("180.00"), broker));
        verifyNoInteractions(comisiones);
    }

    @Test
    void asignarDevuelveElContratoConElRepartoVisible() {
        contratoRegistrado();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(30L));
        when(comisiones.asignarMontoAgente(CONTRATO, new BigDecimal("180.00"), broker))
                .thenReturn(comision());

        FichaContrato ficha = service.asignarComision(CONTRATO, new BigDecimal("180.00"), broker);

        assertEquals(new BigDecimal("180.00"), ficha.montoAgente());
        assertEquals(new BigDecimal("270.00"), ficha.montoEmpresa());
    }

    // ------------------------------------------------------------------
    // Ciclo contractual normalizado
    // ------------------------------------------------------------------

    @Test
    void finalizarConservaElLocalAlquiladoYCreaRevisionExpresa() {
        ContratoAlquiler contrato = contratoRegistrado();
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate efectiva = LocalDate.now();

        FichaContrato ficha = service.finalizar(CONTRATO,
                new DatosTransicion(efectiva, "Entrega de llaves"), agente);

        assertEquals(ContratoAlquiler.FINALIZADO, ficha.estadoContrato());
        assertEquals(DisponibilidadComercial.ALQUILADO,
                contrato.getOportunidad().getCaptacion().getPropiedad()
                        .disponibilidadComercialTipada());
        verify(tareas).crearRevisionInmueble(9L, 30L,
                "Revisar disponibilidad despues de finalizado", CONTRATO, agente);
        HistorialEstado evento = eventoAuditado();
        assertEquals(ContratoAlquiler.VIGENTE, evento.getEstadoAnterior());
        assertEquals(ContratoAlquiler.FINALIZADO, evento.getEstadoNuevo());
        assertEquals(efectiva, evento.getFechaEfectiva());
    }

    @Test
    void anularContratoFirmadoNoCreaRevisionDelInmueble() {
        ContratoAlquiler contrato = contratoEnEstado(ContratoAlquiler.FIRMADO);
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));

        FichaContrato ficha = service.anular(CONTRATO,
                new DatosTransicion(LocalDate.now(), "Firma dejada sin efecto"), agente);

        assertEquals(ContratoAlquiler.ANULADO, ficha.estadoContrato());
        verifyNoInteractions(tareas);
        assertEquals(ContratoAlquiler.ANULADO, contrato.estadoActual());
        // La comision nace al FIRMAR: anular el contrato tiene que arrastrarla,
        // o queda una liquidacion cobrable de un contrato que ya no existe.
        verify(comisiones).anularPorContratoAnulado(CONTRATO, agente);
    }

    @Test
    void anularNoSeConsumaSiLaComisionYaSeCobro() {
        ContratoAlquiler contrato = contratoEnEstado(ContratoAlquiler.FIRMADO);
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ReglaNegocioException("No se puede anular un contrato cuya comision ya fue cobrada."))
                .when(comisiones).anularPorContratoAnulado(CONTRATO, agente);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.anular(CONTRATO, new DatosTransicion(LocalDate.now(), "prueba"), agente));

        assertEquals("No se puede anular un contrato cuya comision ya fue cobrada.", error.getMessage());
        // El caso de uso es @Transactional: el estado del contrato revierte con
        // la excepcion. Lo que se blinda aqui es que la comision manda.
    }

    @Test
    void finalizarYRescindirNoTocanLaComision() {
        ContratoAlquiler contrato = contratoEnEstado(ContratoAlquiler.VIGENTE);
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(comisiones.porContrato(CONTRATO, agente)).thenReturn(Optional.empty());

        service.finalizar(CONTRATO, new DatosTransicion(LocalDate.now(), "fin"), agente);

        // Decision de 7.1, reafirmada aqui desde el lado economico: terminar un
        // alquiler que existio no borra la comision que genero.
        verify(comisiones, never()).anularPorContratoAnulado(anyLong(), any());
        assertEquals(ContratoAlquiler.FINALIZADO, contrato.estadoActual());
    }

    @Test
    void renovarCreaSucesorYNoSobrescribeElContratoAnterior() {
        ContratoAlquiler anterior = contratoRegistrado();
        LocalDate inicioAnterior = anterior.getFechaInicioContrato();
        LocalDate finAnterior = anterior.getFechaFinContrato();
        BigDecimal rentaAnterior = anterior.getRentaContractual();
        when(contratos.existsByOrganizacionIdAndContratoAnteriorId(ORG, CONTRATO)).thenReturn(false);
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> {
            ContratoAlquiler guardado = inv.getArgument(0);
            if (guardado.getId() == null) ReflectionTestUtils.setField(guardado, "id", 41L);
            return guardado;
        });
        when(comisiones.crearPendienteNormalizada(
                any(ContratoAlquiler.class), any(), any(), any(), any()))
                .thenReturn(comisionPendiente());
        LocalDate inicioNuevo = LocalDate.now();
        LocalDate finNuevo = inicioNuevo.plusYears(2);

        FichaContrato ficha = service.renovar(CONTRATO,
                new DatosRenovacion(inicioNuevo, finNuevo,
                        new BigDecimal("9500.00"), "PEN", "Renovacion bianual"), agente);

        assertEquals(ContratoAlquiler.RENOVADO, anterior.estadoActual());
        assertEquals(inicioAnterior, anterior.getFechaInicioContrato());
        assertEquals(finAnterior, anterior.getFechaFinContrato());
        assertEquals(rentaAnterior, anterior.getRentaContractual());
        assertEquals(ContratoAlquiler.FIRMADO, ficha.estadoContrato());
        assertEquals(CONTRATO, ficha.idContratoAnterior());
        assertEquals(inicioNuevo, ficha.fechaInicioContrato());
        assertEquals(finNuevo, ficha.fechaFinContrato());
        assertEquals(new BigDecimal("9500.00"), ficha.rentaMensual());
    }

    /**
     * Con texto, los cuatro agregados salen del conjunto de candidatos
     * (RC-003): las variantes {@code *PorTexto} reciben el alcance como array
     * de la consulta nativa, no como coleccion.
     */
    @Test
    void kpiSeparaGeneradoCobradoPendienteYPagoDelAgentePorMoneda() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, true, List.of()));
        ResumenCierres conteos = mock(ResumenCierres.class);
        when(conteos.getCierres()).thenReturn(4L);
        when(conteos.getPorLiquidar()).thenReturn(2L);
        when(conteos.getSinLiquidacion()).thenReturn(1L);
        when(contratos.resumenCierresPorTexto(eq(ORG), eq(true), eq(false), anyString(),
                eq("larco"), eq("Miraflores"), eq(30L))).thenReturn(conteos);
        List<ComisionGeneradaPorMoneda> generadas = List.of(
                generada("PEN", "1000.00"), generada("USD", "500.00"));
        List<RepartoComisionPorMoneda> repartos = List.of(
                reparto("PEN", "300.00"), reparto("USD", "100.00"));
        List<MovimientoComisionPorMoneda> movimientosKpi = List.of(
                movimientos("PEN", "400.00", "100.00"),
                movimientos("USD", "200.00", "50.00"));
        when(contratos.comisionesGeneradasPorTexto(eq(ORG), eq(true), eq(false), anyString(),
                eq("larco"), eq("Miraflores"), eq(30L))).thenReturn(generadas);
        when(contratos.repartosPorMonedaPorTexto(eq(ORG), eq(true), eq(false), anyString(),
                eq("larco"), eq("Miraflores"), eq(30L))).thenReturn(repartos);
        when(contratos.movimientosPorMonedaPorTexto(eq(ORG), eq(true), eq(false), anyString(),
                eq("larco"), eq("Miraflores"), eq(30L))).thenReturn(movimientosKpi);

        var resumen = service.resumenCierres(
                new FiltrosContrato("larco", "Miraflores", 30L, null, 1, 20), broker);

        assertEquals(List.of("PEN:1000.00", "USD:500.00"), importes(resumen.comisionesGeneradas()));
        assertEquals(List.of("PEN:400.00", "USD:200.00"), importes(resumen.montosCobrados()));
        assertEquals(List.of("PEN:600.00", "USD:300.00"), importes(resumen.saldosPendientes()));
        assertEquals(List.of("PEN:100.00", "USD:50.00"), importes(resumen.montosPagadosAgente()));
        assertEquals(List.of("PEN:200.00", "USD:50.00"), importes(resumen.saldosPendientesAgente()));
        assertEquals(1, resumen.sinLiquidacion());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private SolicitudAlquiler prepararCierreValido(String estadoSolicitud, String estadoOportunidad) {
        SolicitudAlquiler solicitud = solicitud(estadoSolicitud, 30L);
        new Transiciones(mock(HistorialEstadoRepository.class))
                .aplicar(solicitud.getOportunidad(), 8L, estadoOportunidad, null, null);
        when(solicitudes.buscarFicha(ORG, SOLICITUD)).thenReturn(Optional.of(solicitud));
        when(solicitudes.save(any(SolicitudAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(oportunidades.save(any(OportunidadComercial.class))).thenAnswer(inv -> inv.getArgument(0));
        when(captaciones.save(any(Captacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(propiedades.save(any(Propiedad.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contratos.save(any(ContratoAlquiler.class))).thenAnswer(inv -> {
            ContratoAlquiler guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", CONTRATO);
            return guardado;
        });
        when(comisiones.crearPendienteNormalizada(
                any(ContratoAlquiler.class), any(), any(), any(), any()))
                .thenReturn(comisionPendiente());
        return solicitud;
    }

    /** Contrato 40 sobre la solicitud 5 (agente 30) y la oportunidad 8. */
    private ContratoAlquiler contratoRegistrado() {
        return contratoEnEstado(ContratoAlquiler.VIGENTE);
    }

    private ContratoAlquiler contratoEnEstado(String estado) {
        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setOrganizacionId(ORG);
        SolicitudAlquiler solicitud = solicitud(SolicitudAlquiler.CERRADA, 30L);
        contrato.setSolicitud(solicitud);
        contrato.setOportunidad(solicitud.getOportunidad());
        contrato.setFechaCierre(LocalDate.now());
        contrato.setFechaInicioContrato(LocalDate.of(2026, 9, 1));
        contrato.setFechaFinContrato(LocalDate.of(2028, 9, 1));
        contrato.setRentaContractual(new BigDecimal("9000.00"));
        contrato.setMoneda("PEN");
        contrato.getOportunidad().getCaptacion().getPropiedad().marcarAlquilado();
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(contrato, estado);
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);
        when(contratos.buscarPorOportunidad(ORG, 8L)).thenReturn(Optional.of(contrato));
        when(contratos.buscarFicha(ORG, CONTRATO)).thenReturn(Optional.of(contrato));
        return contrato;
    }

    private static Page<ContratoAlquiler> pagina(ContratoAlquiler contrato) {
        return new PageImpl<>(List.of(contrato), Pageable.unpaged(), 1);
    }

    private static FichaComision comisionPendiente() {
        return new FichaComision(60L, CONTRATO, new BigDecimal("450.00"), "PEN",
                null, null, null, null, "P");
    }

    private static FichaComision comision() {
        return new FichaComision(60L, CONTRATO, new BigDecimal("450.00"), "PEN",
                new BigDecimal("180.00"), new BigDecimal("270.00"), null, null, "P");
    }

    /** Solicitud 5 sobre la oportunidad 8 / captacion 7 / propiedad 9. */
    private static SolicitudAlquiler solicitud(String estado, long idRolAgente) {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(ORG);
        solicitud.setCodigoSolicitud("SOL-260715103000");
        solicitud.setFechaRegistro(LocalDate.of(2026, 7, 15));
        solicitud.setMontoPropuesto(new BigDecimal("9000.00"));
        solicitud.setMoneda("PEN");
        solicitud.setPlazoContratoMeses(24);
        solicitud.setFechaInicioContrato(LocalDate.of(2026, 9, 1));
        solicitud.setOportunidad(oportunidad());
        solicitud.setAgente(detalleAgente(idRolAgente, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(solicitud, estado);
        ReflectionTestUtils.setField(solicitud, "id", SOLICITUD);
        return solicitud;
    }

    private static OportunidadComercial oportunidad() {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setOrganizacionId(ORG);
        oportunidad.setCodigoOportunidad("OP-0001");
        oportunidad.setCliente(detalleCliente(50L, "Mariana Delgado"));
        oportunidad.setCaptacion(captacion());
        oportunidad.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(oportunidad, OportunidadComercial.ABIERTA);
        ReflectionTestUtils.setField(oportunidad, "id", 8L);
        return oportunidad;
    }

    private static Captacion captacion() {
        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(ORG);
        captacion.setCodigoCaptacion("CAP-0001");
        captacion.setFechaInicioVigencia(LocalDate.of(2026, 7, 1));
        captacion.setFechaFinVigencia(LocalDate.of(2027, 7, 1));
        captacion.setComisionPactada(new BigDecimal("5.00"));
        captacion.setPropiedad(propiedad());
        captacion.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(captacion, Captacion.ACTIVA);
        ReflectionTestUtils.setField(captacion, "id", 7L);
        return captacion;
    }

    private static Propiedad propiedad() {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setCodigo("LOC-0001");
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setRolPropietario(personaRol("Inversiones Delgado SAC", TipoRol.PROPIETARIO));
        ReflectionTestUtils.setField(propiedad.getRolPropietario(), "id", 40L);
        propiedad.iniciarDisponible();
        ReflectionTestUtils.setField(propiedad, "id", 9L);
        return propiedad;
    }

    private static Publicacion publicacion(String estado) {
        Publicacion publicacion = new Publicacion();
        publicacion.setOrganizacionId(ORG);
        publicacion.setIdPropiedad(9L);
        publicacion.setCanal(Publicacion.CANAL_WEB_PROPIA);
        publicacion.setEstado(estado);
        ReflectionTestUtils.setField(publicacion, "id", 12L);
        return publicacion;
    }

    private static DetalleCliente detalleCliente(long idRol, String nombre) {
        DetalleCliente cliente = new DetalleCliente();
        cliente.setOrganizacionId(ORG);
        cliente.setRol(personaRol(nombre, TipoRol.CLIENTE));
        ReflectionTestUtils.setField(cliente, "id", idRol);
        return cliente;
    }

    private static SupervisionAgente supervision(long idRolBroker) {
        SupervisionAgente supervision = new SupervisionAgente();
        supervision.setOrganizacionId(ORG);
        supervision.setIdRolBroker(idRolBroker);
        supervision.setIdRolAgente(30L);
        supervision.setFechaAsignacion(LocalDate.of(2026, 1, 1));
        supervision.setMotivo("Alta del agente");
        return supervision;
    }

    private static DetalleAgente detalleAgente(long idRol, String nombre) {
        DetalleAgente detalle = new DetalleAgente();
        detalle.setRol(personaRol(nombre, TipoRol.AGENTE));
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private static PersonaRol personaRol(String nombre, TipoRol tipo) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(tipo);
        return rol;
    }

    private static ComisionGeneradaPorMoneda generada(String moneda, String monto) {
        ComisionGeneradaPorMoneda fila = mock(ComisionGeneradaPorMoneda.class);
        when(fila.getMoneda()).thenReturn(moneda);
        when(fila.getMonto()).thenReturn(new BigDecimal(monto));
        return fila;
    }

    private static RepartoComisionPorMoneda reparto(String moneda, String parteAgente) {
        RepartoComisionPorMoneda fila = mock(RepartoComisionPorMoneda.class);
        when(fila.getMoneda()).thenReturn(moneda);
        when(fila.getParteAgente()).thenReturn(new BigDecimal(parteAgente));
        return fila;
    }

    private static MovimientoComisionPorMoneda movimientos(String moneda, String cobrado,
                                                            String pagadoAgente) {
        MovimientoComisionPorMoneda fila = mock(MovimientoComisionPorMoneda.class);
        when(fila.getMoneda()).thenReturn(moneda);
        when(fila.getMontoCobrado()).thenReturn(new BigDecimal(cobrado));
        when(fila.getMontoPagadoAgente()).thenReturn(new BigDecimal(pagadoAgente));
        return fila;
    }

    private static List<String> importes(
            List<com.controllocal.service.ContratoService.ImportePorMoneda> importes) {
        return importes.stream().map(i -> i.moneda() + ":" + i.monto()).toList();
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
