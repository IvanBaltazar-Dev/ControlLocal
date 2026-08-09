package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.TipoDocumentoRequerido;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DocumentoSolicitudRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.Pagina;
import com.controllocal.service.SolicitudService.DatosSolicitud;
import com.controllocal.service.SolicitudService.FichaSolicitud;
import com.controllocal.service.SolicitudService.FiltrosSolicitud;
import com.controllocal.service.SolicitudService.ResumenSolicitudes;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoSolicitud;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda las reglas de {@code SolicitudesRest} + {@code SolicitudAlquilerBusinessLogicImpl}
 * (contrato congelado F4 §2) y cierra la deuda del §9: era el unico de los cinco
 * services de F4 sin tests de comportamiento.
 *
 * <p>Los cuatro comportamientos que mas cuesta no perder al portar, y que aqui
 * quedan fijados:
 * <ol>
 *   <li>el alta <b>no</b> comprueba que la oportunidad sea del agente — solo lo
 *       fija como responsable (cable real);</li>
 *   <li>transiciona la oportunidad a {@code S} <b>por {@link Transiciones}</b>, asi
 *       que deja fila en historial_estado donde la v1 no dejaba ninguna (MEJ-01);</li>
 *   <li>{@code plazoTentativo} es <b>derivado</b>: {@code "N meses"} pisa lo que
 *       mande el cliente;</li>
 *   <li>el alcance va <b>por AGENTE</b>, y un BROKER sin equipo obtiene lista
 *       VACIA, no 403.</li>
 * </ol>
 */
class SolicitudServiceImplTest {

    private static final long ORG = 1L;
    private static final long SOLICITUD = 5L;
    private static final long OPORTUNIDAD = 7L;
    private static final long ROL_AGENTE = 30L;

    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final DocumentoSolicitudRepository documentos = mock(DocumentoSolicitudRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final SupervisionAgenteRepository supervisiones = mock(SupervisionAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final AccesoSolicitud acceso = mock(AccesoSolicitud.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final AlertaService alertas = mock(AlertaService.class);
    private final PlanDeConsulta plan = mock(PlanDeConsulta.class);

    private final SolicitudServiceImpl service = new SolicitudServiceImpl(
            solicitudes, oportunidades, documentos, agentes, supervisiones,
            alcances, acceso, new Transiciones(historial), alertas, plan);

    /** Filtros sin nada activo: la pagina 1 del cable congelado. */
    private static FiltrosSolicitud filtros() {
        return new FiltrosSolicitud(null, null, null, null, null, null, 1, 20);
    }

    /** vmora: agente cuyo rol operativo es 30. */
    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Validaciones de FORMA (mensajes del cable, orden de BusinessValidations)
    // ------------------------------------------------------------------

    @Test
    void sinDatosOSinOportunidadRespondeElMensajeV1() {
        ReglaNegocioException sinDatos = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, agente));
        assertEquals("Los datos de la solicitud son obligatorios.", sinDatos.getMessage());

        ReglaNegocioException sinOportunidad = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(null, new BigDecimal("9000")), agente));
        assertEquals("Los datos de la solicitud son obligatorios.", sinOportunidad.getMessage());

        verifyNoInteractions(oportunidades);
    }

    @Test
    void elMontoPropuestoDebeSerMayorQueCero() {
        for (BigDecimal invalido : List.of(BigDecimal.ZERO, new BigDecimal("-1"))) {
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrar(datos(OPORTUNIDAD, invalido), agente));
            assertEquals("El monto propuesto debe ser mayor que cero.", error.getMessage());
        }

        ReglaNegocioException nulo = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(OPORTUNIDAD, null), agente));
        assertEquals("El monto propuesto debe ser mayor que cero.", nulo.getMessage());
    }

    @Test
    void laFormaDelDatoSeValidaANTESQueElEstadoDelMundo() {
        // Orden calcado de BusinessValidations.solicitud(): con monto invalido
        // y oportunidad invalida gana el mensaje del MONTO, y no se llega a
        // tocar el repositorio.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(-3L, BigDecimal.ZERO), agente));

        assertEquals("El monto propuesto debe ser mayor que cero.", error.getMessage());
        verifyNoInteractions(oportunidades);
    }

    @Test
    void laOportunidadDebeSerMayorQueCero() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(-3L, new BigDecimal("9000")), agente));

        assertEquals("La oportunidad comercial de la solicitud debe ser mayor que cero.",
                error.getMessage());
        verifyNoInteractions(oportunidades);
    }

    @Test
    void unaFormaDePagoFueraDelVocabularioRespondeElMensajeV1() {
        // FormaPago viaja con el NOMBRE del enum, no con codigo de una letra (§1).
        DatosSolicitud datos = new DatosSolicitud(null, null, new BigDecimal("9000"), "PEN", null, null,
                null, OPORTUNIDAD, null, null, "BITCOIN", null, null);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos, agente));
        assertEquals("Valor invalido para forma de pago: BITCOIN", error.getMessage());
    }

    @Test
    void laFormaDePagoSeNormalizaAMayusculas() {
        prepararAltaValida();
        DatosSolicitud datos = new DatosSolicitud(null, null, new BigDecimal("9000"), "PEN", null, null,
                null, OPORTUNIDAD, null, null, " transferencia ", null, null);

        assertEquals("TRANSFERENCIA", service.registrar(datos, agente).formaPago());
    }

    // ------------------------------------------------------------------
    // Estado del mundo: las dos reglas INVISIBLES del §2
    // ------------------------------------------------------------------

    @Test
    void unaOportunidadInexistenteRespondeElMensajeV1() {
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente));
        assertEquals("Oportunidad comercial no encontrada para solicitud.", error.getMessage());
    }

    @Test
    void unaOportunidadSinCaptacionRespondeElMensajeV1() {
        OportunidadComercial oportunidad = oportunidadAbierta(null);
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.of(oportunidad));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente));
        assertEquals("Captacion no encontrada para solicitud.", error.getMessage());
    }

    @Test
    void laCaptacionDebeEstarActiva() {
        // Regla invisible del §2 nº1: no se ve en el REST y se pierde al portar.
        OportunidadComercial oportunidad = oportunidadAbierta(captacion(Captacion.CERRADA));
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.of(oportunidad));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente));

        assertEquals("La captacion debe estar ACTIVA.", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void laOportunidadDebeEstarAbierta() {
        // Se comprueba DESPUES de la captacion: una oportunidad ya en S con
        // captacion cerrada responde por la captacion, no por la oportunidad.
        OportunidadComercial oportunidad = oportunidad(
                OportunidadComercial.SOLICITUD_CREADA, captacion(Captacion.ACTIVA));
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.of(oportunidad));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente));

        assertEquals("La oportunidad comercial debe estar ABIERTA.", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void elAltaNoComprumebaQueLaOportunidadSeaDelAgenteYEsoDEBEPasar() {
        // CABLE REAL, replicado a proposito: la v1 no valida la propiedad de la
        // oportunidad, solo fija al actor como responsable. El tenant SI acota
        // (buscarFicha lleva la organizacion). Si este test empieza a fallar,
        // alguien "arreglo de paso" el contrato congelado.
        prepararAltaValida();

        FichaSolicitud ficha = service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente);

        assertEquals(ROL_AGENTE, ficha.idAgente());
        verify(alcances, never()).alcanza(any(), any());
        verify(oportunidades).buscarFicha(ORG, OPORTUNIDAD);
    }

    @Test
    void elAgenteDebeExistirEstarActivoYDisponible() {
        OportunidadComercial oportunidad = oportunidadAbierta(captacion(Captacion.ACTIVA));
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.of(oportunidad));

        when(agentes.findById(ROL_AGENTE)).thenReturn(Optional.empty());
        assertEquals("Agente no encontrado para solicitud.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente))
                        .getMessage());

        // "ACTIVO" en Party-Role = rol VIGENTE; la baja del rol vive en persona_rol.
        DetalleAgente deBaja = detalleAgente(ROL_AGENTE, "Valentina Mora");
        deBaja.getRol().setVigenciaHasta(LocalDate.of(2026, 1, 1));
        when(agentes.findById(ROL_AGENTE)).thenReturn(Optional.of(deBaja));
        assertEquals("El agente debe estar ACTIVO.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente))
                        .getMessage());

        DetalleAgente ocupado = detalleAgente(ROL_AGENTE, "Valentina Mora");
        ocupado.setEstadoOperativo("L");
        when(agentes.findById(ROL_AGENTE)).thenReturn(Optional.of(ocupado));
        assertEquals("El agente debe estar DISPONIBLE.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente))
                        .getMessage());
    }

    // ------------------------------------------------------------------
    // Unicidad: 409, igual que el cable (decision de equipo, 2026-07-29)
    // ------------------------------------------------------------------

    @Test
    void unCodigoPropuestoRepetidoEsCONFLICTONoBadRequest() {
        // El cable v1 responde 409 aqui: deja saltar el UNIQUE y su mapper lo
        // traduce. Adelantarse a la BD solo mejora el TEXTO —nombra el codigo
        // en vez del generico "un dato unico esta duplicado"—, nunca el codigo
        // de estado. Por eso es ConflictoException y no ReglaNegocioException,
        // que seria un 400.
        prepararAltaValida();
        when(solicitudes.existeCodigo(ORG, "SOL-0001")).thenReturn(true);
        DatosSolicitud datos = new DatosSolicitud("  SOL-0001  ", null, new BigDecimal("9000"),
                "PEN", null, null, null, OPORTUNIDAD, null, null, null, null, null);

        ConflictoException error = assertThrows(ConflictoException.class,
                () -> service.registrar(datos, agente));
        assertEquals("Ya existe una solicitud con el codigo SOL-0001.", error.getMessage());
    }

    @Test
    void laUnicidadPorOportunidadLaDefiendeElINDICENoElService() {
        // Se quito la comprobacion: era codigo muerto —el E2E probo que la
        // precondicion "oportunidad ABIERTA" corta antes, porque el alta
        // anterior la dejo en S— y ademas respondia 400 donde el cable
        // responde 409. Lo defiende uq_solicitud_oportunidad.
        prepararAltaValida();

        service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente);

        verify(solicitudes, never()).existeDeOportunidad(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------
    // Efectos del alta
    // ------------------------------------------------------------------

    @Test
    void elAltaMueveLaOportunidadASYDejaFilaEnHistorialDondeLaV1NoDejabaNinguna() {
        OportunidadComercial oportunidad = prepararAltaValida();

        FichaSolicitud ficha = service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente);

        assertEquals(SolicitudAlquiler.REGISTRADA, ficha.estado());
        assertEquals(OportunidadComercial.SOLICITUD_CREADA, oportunidad.estadoActual());
        verify(oportunidades).save(oportunidad);

        HistorialEstado evento = eventoAuditado();
        assertEquals("OPORTUNIDAD", evento.getEntidadTipo());
        assertEquals(OPORTUNIDAD, evento.getIdEntidad());
        assertEquals(OportunidadComercial.ABIERTA, evento.getEstadoAnterior());
        assertEquals(OportunidadComercial.SOLICITUD_CREADA, evento.getEstadoNuevo());
        assertEquals(3L, evento.getIdActor());
        assertEquals("AGENTE", evento.getTipoRolActor());
        assertEquals("Solicitud " + ficha.codigoSolicitud() + " registrada.", evento.getMotivo());
    }

    @Test
    void sinCodigoPropuestoSeAutogeneraConPrefijoSOL() {
        // D-F4-4: marca de tiempo (SOL-yyMMddHHmmss), no correlativo como
        // PRO-####/CAP-####. No se consulta el unico porque no hay propuesto.
        prepararAltaValida();

        String codigo = service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente)
                .codigoSolicitud();

        assertTrue(codigo.matches("SOL-\\d{12}"), "codigo autogenerado: " + codigo);
        verify(solicitudes, never()).existeCodigo(anyLong(), any());
    }

    @Test
    void elPlazoTentativoEsDERIVADOYPisaLoQueMandeElCliente() {
        // Dtos.SolicitudRequest.aEntidad: si vienen meses > 0, "N meses" gana.
        prepararAltaValida();
        DatosSolicitud conMeses = new DatosSolicitud(null, null, new BigDecimal("9000"),
                "PEN", "lo que diga el cliente", null, null, OPORTUNIDAD, 24, null, null, null, null);

        assertEquals("24 meses", service.registrar(conMeses, agente).plazoTentativo());
    }

    @Test
    void sinMesesSeRespetaElPlazoTentativoDelCliente() {
        prepararAltaValida();
        DatosSolicitud sinMeses = new DatosSolicitud(null, null, new BigDecimal("9000"),
                "PEN", "a convenir", null, null, OPORTUNIDAD, 0, null, null, null, null);

        assertEquals("a convenir", service.registrar(sinMeses, agente).plazoTentativo());
    }

    @Test
    void laFichaReciénCreadaArrancaElChecklistEn0De6() {
        prepararAltaValida();

        FichaSolicitud ficha = service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente);

        assertEquals(0, ficha.documentosEntregados());
        assertEquals(6, ficha.documentosRequeridos());
        // Recien creada no tiene documentos: no se consulta el repositorio.
        verifyNoInteractions(documentos);
    }

    @Test
    void laFichaArrastraLosDatosDeLaCadenaOportunidadCaptacionPropiedad() {
        prepararAltaValida();

        FichaSolicitud ficha = service.registrar(datos(OPORTUNIDAD, new BigDecimal("9000")), agente);

        assertEquals(OPORTUNIDAD, ficha.idOportunidad());
        assertEquals("OPO-0001", ficha.codigoOportunidad());
        assertEquals("Comercial Andina SAC", ficha.clienteNombre());
        assertEquals("CAP-0001", ficha.codigoCaptacion());
        assertEquals("Av. Larco 1234", ficha.direccionLocal());
        assertEquals("Miraflores", ficha.distritoLocal());
        assertEquals("Valentina Mora", ficha.agenteNombre());
        assertNotNull(ficha.fechaActualizacionEstado());
    }

    // ------------------------------------------------------------------
    // Reenvio a evaluacion
    // ------------------------------------------------------------------

    @Test
    void soloUnaRegistradaUObservadaPuedeEnviarseAEvaluacion() {
        for (String estado : List.of(SolicitudAlquiler.EN_REVISION, SolicitudAlquiler.APROBADA,
                SolicitudAlquiler.RECHAZADA, SolicitudAlquiler.CERRADA)) {
            when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitudEn(estado));

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.reenviarAEvaluacion(SOLICITUD, agente));
            assertEquals("Solo una solicitud registrada u observada puede enviarse a evaluacion.",
                    error.getMessage());
        }
        verifyNoInteractions(historial);
    }

    @Test
    void elReenvioExigeQueElAgenteResponsableTengaBrokerSupervisorActivo() {
        // Regla invisible del §2 nº2: sin supervisor no habria quien evalue.
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitudEn(SolicitudAlquiler.REGISTRADA));
        when(supervisiones.tieneSupervisorActivo(ORG, ROL_AGENTE)).thenReturn(false);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.reenviarAEvaluacion(SOLICITUD, agente));

        assertEquals("El agente responsable no tiene broker supervisor activo.", error.getMessage());
        verifyNoInteractions(historial);
        verifyNoInteractions(alertas);
    }

    @Test
    void reenviarMueveAEnRevisionAuditaYAvisaAlBroker() {
        SolicitudAlquiler solicitud = solicitudEn(SolicitudAlquiler.OBSERVADA);
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud);
        when(supervisiones.tieneSupervisorActivo(ORG, ROL_AGENTE)).thenReturn(true);
        when(solicitudes.save(any(SolicitudAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        FichaSolicitud ficha = service.reenviarAEvaluacion(SOLICITUD, agente);

        assertEquals(SolicitudAlquiler.EN_REVISION, ficha.estado());
        HistorialEstado evento = eventoAuditado();
        assertEquals("SOLICITUD_ALQUILER", evento.getEntidadTipo());
        assertEquals(SolicitudAlquiler.OBSERVADA, evento.getEstadoAnterior());
        assertEquals(SolicitudAlquiler.EN_REVISION, evento.getEstadoNuevo());
        assertEquals("Solicitud enviada a evaluacion del broker.", evento.getMotivo());
    }

    @Test
    void laAlertaDelReenvioCuelgaDelAGENTEResponsableAunqueLaLeaElBroker() {
        // F6 §4: no hay columna de destinatario. Quien la lee lo decide el TIPO;
        // el broker la ve a traves de la supervision. No inventar idDestinatario.
        SolicitudAlquiler solicitud = solicitudEn(SolicitudAlquiler.REGISTRADA);
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud);
        when(supervisiones.tieneSupervisorActivo(ORG, ROL_AGENTE)).thenReturn(true);
        when(solicitudes.save(any(SolicitudAlquiler.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        service.reenviarAEvaluacion(SOLICITUD, agente);

        ArgumentCaptor<AlertaService.DatosAlerta> alerta =
                ArgumentCaptor.forClass(AlertaService.DatosAlerta.class);
        verify(alertas).emitir(alerta.capture(), eq(agente));
        assertEquals(Alerta.SOLICITUD_REENVIADA, alerta.getValue().tipo());
        assertEquals(Alerta.MEDIA, alerta.getValue().severidad());
        assertEquals("SOLICITUD_ALQUILER", alerta.getValue().entidadTipo());
        assertEquals(SOLICITUD, alerta.getValue().entidadId());
        assertEquals(ROL_AGENTE, alerta.getValue().idRolAgente());
        assertEquals("La solicitud SOL-260715103000 fue enviada a evaluacion del broker supervisor.",
                alerta.getValue().mensaje());
    }

    // ------------------------------------------------------------------
    // Alcance por AGENTE (§7) — no por captacion
    // ------------------------------------------------------------------

    @Test
    void unBrokerSinEquipoObtieneListaVACIANoUn403() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of()));

        Pagina<FichaSolicitud> pagina = service.listar(filtros(), broker);

        assertEquals(0, pagina.total());
        assertTrue(pagina.items().isEmpty());
        verifyNoInteractions(solicitudes);
    }

    @Test
    void elBrokerAlcanzaPorAGENTESupervisadoNoPorCaptacion() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE, 31L)));
        when(solicitudes.buscar(eq(ORG), eq(false), eq(List.of(ROL_AGENTE, 31L)),
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(paginaCon(solicitudEn(SolicitudAlquiler.EN_REVISION)));
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        Pagina<FichaSolicitud> pagina = service.listar(filtros(), broker);

        assertEquals(1, pagina.total());
        verify(solicitudes).buscar(eq(ORG), eq(false), eq(List.of(ROL_AGENTE, 31L)),
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void elAdminListaSinFiltroDeRolPeroDENTRODeSuTenant() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, true, List.of()));
        when(solicitudes.buscar(eq(ORG), eq(true), anyList(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(paginaCon());
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        service.listar(filtros(), broker);

        verify(solicitudes).buscar(eq(ORG), eq(true), eq(List.of(-1L)),
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void elTamanoDePaginaSeAcotaEntre1Y100() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(), any(),
                any(), any(Pageable.class)))
                .thenReturn(paginaCon());
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        service.listar(new FiltrosSolicitud(null, null, null, null, null, null, 1, 5000), agente);
        verify(solicitudes).buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(), any(),
                any(), pageable.capture());

        assertEquals(100, pageable.getValue().getPageSize());
        // La pagina del cable es 1-based; PageRequest es 0-based.
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    // ------------------------------------------------------------------
    // Filtros aditivos y resumen (extension del v2 para las dos bandejas)
    // ------------------------------------------------------------------

    @Test
    void elCuboPENDIENTESViajaNormalizadoYNoEsUnEstado() {
        // No existe en ck_solicitud_estado: lo resuelve el repositorio como
        // E + O. Aqui solo se fija que llega en mayusculas y sin recortar.
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(), any(),
                any(), any(Pageable.class)))
                .thenReturn(paginaCon());
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        service.listar(new FiltrosSolicitud(null, null, null, " pendientes ", null, null, 1, 20),
                broker);

        verify(solicitudes).buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(),
                eq("PENDIENTES"), any(), any(Pageable.class));
    }

    @Test
    void conTextoNoSeUsaElListadoJPQLSinoElConjuntoDeCandidatos() {
        // §5 del contrato de listados: el texto NO entra en el JPQL con un OR
        // que cruce tablas. Va por contarPorTexto + idsPorTexto + fichaPorIds.
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.contarPorTexto(anyLong(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), eq("SOL-26"))).thenReturn(3L);
        when(solicitudes.idsPorTexto(anyLong(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), eq("SOL-26"), eq(20), eq(0))).thenReturn(List.of(SOLICITUD));
        when(solicitudes.buscarFichaPorIds(ORG, List.of(SOLICITUD)))
                .thenReturn(List.of(solicitudEn(SolicitudAlquiler.EN_REVISION)));
        when(documentos.porSolicitudes(eq(ORG), anyList())).thenReturn(List.of());

        Pagina<FichaSolicitud> pagina = service.listar(
                new FiltrosSolicitud(null, null, null, null, null, "SOL-26", 1, 20), agente);

        assertEquals(3, pagina.total());
        assertEquals(1, pagina.items().size());
        verify(plan).forzarPersonalizado();
        verify(solicitudes, never()).buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(),
                any(), any(), any(Pageable.class));
    }

    @Test
    void siElConjuntoDeCandidatosEstaVacioNoSePideLaPagina() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.contarPorTexto(anyLong(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(0L);

        Pagina<FichaSolicitud> pagina = service.listar(
                new FiltrosSolicitud(null, null, null, null, null, "no-existe", 1, 20), agente);

        assertEquals(0, pagina.total());
        assertTrue(pagina.items().isEmpty());
        verify(solicitudes, never()).idsPorTexto(anyLong(), anyBoolean(), any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void elResumenCuentaLosCubosYNoFiltraPorNingunoDeLosTres() {
        // estado, distrito e idAgente viajan NULOS: son justo lo que acota.
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.contarPorEstado(anyLong(), anyBoolean(), anyList(), any(), any(), any(),
                any(), any()))
                .thenReturn(List.of(conteo(SolicitudAlquiler.EN_REVISION, 4),
                        conteo(SolicitudAlquiler.OBSERVADA, 2),
                        conteo(SolicitudAlquiler.APROBADA, 1)));

        ResumenSolicitudes resumen = service.resumen(
                new FiltrosSolicitud(null, null, 99L, "A", "Miraflores", null, 1, 20), broker);

        assertEquals(7, resumen.total());
        assertEquals(4, resumen.enRevision());
        assertEquals(2, resumen.observadas());
        // El cubo de la cola del broker se DERIVA de los dos que lo componen.
        assertEquals(6, resumen.pendientes());
        verify(solicitudes).contarPorEstado(eq(ORG), eq(false), anyList(), any(), any(),
                eq(null), eq(null), eq(null));
    }

    @Test
    void elResumenDeUnBrokerSinEquipoNoConsultaNada() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of()));

        ResumenSolicitudes resumen = service.resumen(filtros(), broker);

        assertEquals(0, resumen.total());
        assertTrue(resumen.distritos().isEmpty());
        assertTrue(resumen.agentes().isEmpty());
        verifyNoInteractions(solicitudes);
    }

    // ------------------------------------------------------------------
    // Checklist "X/6": que suma y que NO
    // ------------------------------------------------------------------

    @Test
    void unDocumentoOBSERVADODejaDeContar() {
        SolicitudAlquiler solicitud = solicitudEn(SolicitudAlquiler.EN_REVISION);
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud);
        when(documentos.porSolicitudes(ORG, List.of(SOLICITUD))).thenReturn(List.of(
                documento(solicitud, "I", DocumentoSolicitud.REGISTRADO),
                documento(solicitud, "R", DocumentoSolicitud.VALIDADO),
                documento(solicitud, "V", DocumentoSolicitud.OBSERVADO)));

        assertEquals(2, service.obtener(SOLICITUD, agente).documentosEntregados());
    }

    @Test
    void losTiposPoderYOtroNoSumanAlChecklistAunqueSePuedanSubir() {
        SolicitudAlquiler solicitud = solicitudEn(SolicitudAlquiler.EN_REVISION);
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud);
        when(documentos.porSolicitudes(ORG, List.of(SOLICITUD))).thenReturn(List.of(
                documento(solicitud, "I", DocumentoSolicitud.VALIDADO),
                documento(solicitud, "P", DocumentoSolicitud.VALIDADO),
                documento(solicitud, "O", DocumentoSolicitud.VALIDADO)));

        assertEquals(1, service.obtener(SOLICITUD, agente).documentosEntregados());
    }

    @Test
    void elMismoTipoDosVecesCuentaUnaSola() {
        // El contador es sobre TIPOS distintos, no sobre documentos.
        SolicitudAlquiler solicitud = solicitudEn(SolicitudAlquiler.EN_REVISION);
        when(acceso.conAcceso(SOLICITUD, agente)).thenReturn(solicitud);
        when(documentos.porSolicitudes(ORG, List.of(SOLICITUD))).thenReturn(List.of(
                documento(solicitud, "I", DocumentoSolicitud.REGISTRADO),
                documento(solicitud, "I", DocumentoSolicitud.VALIDADO)));

        assertEquals(1, service.obtener(SOLICITUD, agente).documentosEntregados());
    }

    @Test
    void elChecklistDeLaPaginaSeCuentaEnUNASolaLecturaSinNMasUno() {
        // MEJ-05: una consulta para toda la pagina, no una por solicitud.
        SolicitudAlquiler primera = solicitudEn(SolicitudAlquiler.EN_REVISION);
        SolicitudAlquiler segunda = solicitudEn(SolicitudAlquiler.REGISTRADA);
        ReflectionTestUtils.setField(segunda, "id", 6L);
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(solicitudes.buscar(anyLong(), anyBoolean(), anyList(), any(), any(), any(), any(),
                any(), any(Pageable.class)))
                .thenReturn(paginaCon(primera, segunda));
        when(documentos.porSolicitudes(ORG, List.of(SOLICITUD, 6L))).thenReturn(List.of(
                documento(primera, "I", DocumentoSolicitud.VALIDADO),
                documento(segunda, "I", DocumentoSolicitud.VALIDADO),
                documento(segunda, "R", DocumentoSolicitud.REGISTRADO)));

        List<FichaSolicitud> fichas = service.listar(filtros(), agente).items();

        assertEquals(1, fichas.get(0).documentosEntregados());
        assertEquals(2, fichas.get(1).documentosEntregados());
        verify(documentos).porSolicitudes(ORG, List.of(SOLICITUD, 6L));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static ConteoPorEstado conteo(String estado, long total) {
        return new ConteoPorEstado() {
            @Override
            public String getEstado() {
                return estado;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    /** Deja el alta lista para pasar todas las precondiciones. */
    private OportunidadComercial prepararAltaValida() {
        OportunidadComercial oportunidad = oportunidadAbierta(captacion(Captacion.ACTIVA));
        when(oportunidades.buscarFicha(ORG, OPORTUNIDAD)).thenReturn(Optional.of(oportunidad));
        when(agentes.findById(ROL_AGENTE))
                .thenReturn(Optional.of(detalleAgente(ROL_AGENTE, "Valentina Mora")));
        when(solicitudes.save(any(SolicitudAlquiler.class))).thenAnswer(inv -> {
            SolicitudAlquiler guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", SOLICITUD);
            return guardada;
        });
        return oportunidad;
    }

    private static DatosSolicitud datos(Long idOportunidad, BigDecimal monto) {
        return new DatosSolicitud(null, null, monto, "PEN", null, null, null, idOportunidad,
                null, null, null, null, null);
    }

    private static OportunidadComercial oportunidadAbierta(Captacion captacion) {
        return oportunidad(OportunidadComercial.ABIERTA, captacion);
    }

    private static OportunidadComercial oportunidad(String estado, Captacion captacion) {
        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setOrganizacionId(ORG);
        oportunidad.setCodigoOportunidad("OPO-0001");
        oportunidad.setCaptacion(captacion);
        oportunidad.setCliente(detalleCliente("Comercial Andina SAC"));
        nuevasTransiciones().iniciar(oportunidad, estado);
        ReflectionTestUtils.setField(oportunidad, "id", OPORTUNIDAD);
        return oportunidad;
    }

    private static Captacion captacion(String estado) {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setDireccion("Av. Larco 1234");
        propiedad.setDistrito("Miraflores");

        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(ORG);
        captacion.setCodigoCaptacion("CAP-0001");
        captacion.setPropiedad(propiedad);
        nuevasTransiciones().iniciar(captacion, estado);
        ReflectionTestUtils.setField(captacion, "id", 11L);
        return captacion;
    }

    private static SolicitudAlquiler solicitudEn(String estado) {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(ORG);
        solicitud.setCodigoSolicitud("SOL-260715103000");
        solicitud.setFechaRegistro(LocalDate.of(2026, 7, 15));
        solicitud.setMontoPropuesto(new BigDecimal("9000.00"));
        solicitud.setAgente(detalleAgente(ROL_AGENTE, "Valentina Mora"));
        solicitud.setOportunidad(oportunidadAbierta(captacion(Captacion.ACTIVA)));
        nuevasTransiciones().iniciar(solicitud, estado);
        ReflectionTestUtils.setField(solicitud, "id", SOLICITUD);
        return solicitud;
    }

    private static DocumentoSolicitud documento(SolicitudAlquiler solicitud, String codigoTipo,
                                                String estado) {
        TipoDocumentoRequerido tipo = new TipoDocumentoRequerido();
        tipo.setId(TipoDocumentoRequerido.ID_POR_CODIGO.get(codigoTipo));

        DocumentoSolicitud documento = new DocumentoSolicitud();
        documento.setOrganizacionId(ORG);
        documento.setSolicitud(solicitud);
        documento.setTipoDocumento(tipo);
        nuevasTransiciones().iniciar(documento, estado);
        return documento;
    }

    private static DetalleAgente detalleAgente(long idRol, String nombre) {
        DetalleAgente detalle = new DetalleAgente();
        detalle.setOrganizacionId(ORG);
        detalle.setRol(personaRol(nombre, TipoRol.AGENTE));
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private static DetalleCliente detalleCliente(String nombre) {
        DetalleCliente cliente = new DetalleCliente();
        cliente.setOrganizacionId(ORG);
        cliente.setRol(personaRol(nombre, TipoRol.CLIENTE));
        ReflectionTestUtils.setField(cliente, "id", 40L);
        return cliente;
    }

    private static PersonaRol personaRol(String nombre, TipoRol tipo) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(tipo);
        return rol;
    }

    /** Transiciones de usar y tirar para armar fixtures sin auditar. */
    private static Transiciones nuevasTransiciones() {
        return new Transiciones(mock(HistorialEstadoRepository.class));
    }

    private static Page<SolicitudAlquiler> paginaCon(SolicitudAlquiler... solicitudes) {
        return new PageImpl<>(List.of(solicitudes));
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
