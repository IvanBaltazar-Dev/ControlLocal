package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValoresGobernados;
import com.controllocal.service.Actor;
import com.controllocal.service.ProspeccionService.DatosProspeccion;
import com.controllocal.service.ProspeccionService.FichaProspeccion;
import com.controllocal.service.ProspeccionService.FiltrosProspeccion;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.AlertaService;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la maquina de estados de la prospeccion y sus MENSAJES (identicos
 * al ProspeccionBusinessLogicImpl v1) mas la mejora MEJ-01: cada transicion
 * emite su fila de historial_estado via Transiciones.
 */
class ProspeccionServiceImplTest {

    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final SupervisionAgenteRepository supervisiones = mock(SupervisionAgenteRepository.class);
    private final AlertaService alertas = mock(AlertaService.class);

    private final ProspeccionServiceImpl service = new ProspeccionServiceImpl(
            prospecciones, captaciones, propiedades, agentes, alcances, new Transiciones(historial),
            supervisiones, alertas, lectorSinGobernados());

    /** Organizacion de legado: el tenant que el backend resuelve para la sesion (V6). */
    private static final long ORG = 1L;

    /** vmora: organizacion 1, persona 3, rol operativo (persona_rol) 30. */
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");

    // ------------------------------------------------------------------
    // Filtros del listado que NO son columnas (paridad v1)
    // ------------------------------------------------------------------

    /**
     * {@code estado=GESTION} es el cubo de las ACTIVAS en el cable v1
     * (ProspeccionesRest.coincideEstado), no un estado. Si se pasa tal cual a
     * la consulta no coincide con nada y la lista sale VACIA en silencio.
     */
    @Test
    void gestionLlegaALaConsultaComoCuboDeActivas() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(30L)));
        when(prospecciones.buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(), any(), anyBoolean(), anyCollection(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(filtros("GESTION", null), agente);

        verify(prospecciones).buscar(eq(ORG), eq(false), anyCollection(), eq("GESTION"), isNull(),
                isNull(), isNull(), isNull(), eq(false), anyCollection(), isNull(),
                any(Pageable.class));
    }

    @Test
    void gestionEnMinusculasValeIgual() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(30L)));
        when(prospecciones.buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(), any(), anyBoolean(), anyCollection(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(filtros("gestion", null), agente);

        verify(prospecciones).buscar(anyLong(), anyBoolean(), anyCollection(), eq("GESTION"),
                any(), any(), any(), any(), anyBoolean(), anyCollection(), any(),
                any(Pageable.class));
    }

    /** `idBrokerSupervisor` acota al equipo de ese broker, ademas del alcance. */
    @Test
    void filtrarPorBrokerAcotaAlEquipoDeEseBroker() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, true, List.of()));
        when(supervisiones.agentesSupervisados(ORG, 23L)).thenReturn(List.of(30L, 31L));
        when(prospecciones.buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(), any(), anyBoolean(), anyCollection(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(filtros(null, 23L), agente);

        verify(prospecciones).buscar(anyLong(), anyBoolean(), anyCollection(), isNull(), any(),
                any(), any(), any(), eq(true), eq(List.of(30L, 31L)), any(), any(Pageable.class));
    }

    /**
     * Un broker sin equipo tiene que dar VACIO, no "sin filtro". Y la lista no
     * puede ir vacia: un IN de JPQL con lista vacia es un error de sintaxis.
     */
    @Test
    void unBrokerSinEquipoNoDesactivaElFiltro() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, true, List.of()));
        when(supervisiones.agentesSupervisados(ORG, 99L)).thenReturn(List.of());
        when(prospecciones.buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(), any(), anyBoolean(), anyCollection(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(filtros(null, 99L), agente);

        verify(prospecciones).buscar(anyLong(), anyBoolean(), anyCollection(), any(), any(), any(),
                any(), any(), eq(true), eq(List.of(-1L)), any(), any(Pageable.class));
    }

    private static FiltrosProspeccion filtros(String estado, Long idBrokerSupervisor) {
        return new FiltrosProspeccion(estado, null, null, null, null, idBrokerSupervisor,
                null, null, 1, 10);
    }

    // ------------------------------------------------------------------
    // Mensajes del contrato (paridad v1)
    // ------------------------------------------------------------------

    @Test
    void registrarSinLocalRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosProspeccion(null, null), agente));
        assertEquals("El local de la prospeccion es obligatorio.", error.getMessage());
    }

    @Test
    void registrarConLocalInexistenteRespondeElMensajeV1() {
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(propiedades.findByOrganizacionIdAndId(ORG, 9L)).thenReturn(Optional.empty());
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosProspeccion(9L, null), agente));
        assertEquals("El local de la prospeccion no existe.", error.getMessage());
    }

    @Test
    void captarConComisionNegativaRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.captar(5L, new BigDecimal("-1"), agente));
        assertEquals("La comision pactada es obligatoria y no puede ser negativa.", error.getMessage());
    }

    @Test
    void noSePuedeContactarUnaProspeccionCaptada() {
        prospeccionEnEstado(Prospeccion.CAPTADO);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.contactar(5L, agente));
        assertEquals("No se puede contactar una prospeccion captado.", error.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void unActorFueraDeAlcanceNoAccede() {
        Prospeccion p = prospeccionEnEstado(Prospeccion.PROSPECTO);
        when(alcances.alcanza(agente, p.getAgente().getId())).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(5L, agente));
    }

    // ------------------------------------------------------------------
    // Maquina de estados + auditoria (MEJ-01)
    // ------------------------------------------------------------------

    @Test
    void elAltaIniciaEnProspectoSinAuditarElAlta() {
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(propiedades.findByOrganizacionIdAndId(ORG, 9L)).thenReturn(Optional.of(propiedad(9L)));
        when(prospecciones.countByOrganizacionId(ORG)).thenReturn(0L);
        when(prospecciones.save(any(Prospeccion.class))).thenAnswer(inv -> {
            Prospeccion guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 5L);
            when(prospecciones.buscarFicha(ORG, 5L)).thenReturn(Optional.of(guardada));
            return guardada;
        });

        FichaProspeccion ficha = service.registrar(new DatosProspeccion(9L, "Local con potencial"), agente);

        assertEquals("P", ficha.estado());
        assertEquals("PRO-0001", ficha.codigoProspeccion());
        assertEquals(30L, ficha.idAgente());
        // El estado inicial NO es una transicion: la v1 tampoco lo registraba.
        verifyNoInteractions(historial);

        ArgumentCaptor<Prospeccion> guardada = ArgumentCaptor.forClass(Prospeccion.class);
        verify(prospecciones).save(guardada.capture());
        assertEquals(ORG, guardada.getValue().getOrganizacionId());
    }

    @Test
    void elCorrelativoDeProspeccionSeNumeraDentroDeLaOrganizacion() {
        // Con el codigo unico POR organizacion (V6.3), el correlativo cuenta
        // solo las filas del tenant: otra corredora puede tener su propio
        // PRO-0001 sin colisionar.
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(propiedades.findByOrganizacionIdAndId(ORG, 9L)).thenReturn(Optional.of(propiedad(9L)));
        when(prospecciones.countByOrganizacionId(ORG)).thenReturn(7L);
        when(prospecciones.save(any(Prospeccion.class))).thenAnswer(inv -> {
            Prospeccion guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 5L);
            when(prospecciones.buscarFicha(ORG, 5L)).thenReturn(Optional.of(guardada));
            return guardada;
        });

        FichaProspeccion ficha = service.registrar(new DatosProspeccion(9L, null), agente);

        assertEquals("PRO-0008", ficha.codigoProspeccion());
    }

    @Test
    void contactarTransicionaYAuditaConElActor() {
        prospeccionEnEstado(Prospeccion.PROSPECTO);

        FichaProspeccion ficha = service.contactar(5L, agente);

        assertEquals("C", ficha.estado());
        assertEquals(LocalDate.now(), ficha.fechaContacto());
        assertEquals(LocalDate.now(), ficha.fechaRecontacto());
        HistorialEstado evento = eventoAuditado();
        assertEquals("PROSPECCION", evento.getEntidadTipo());
        assertEquals("P", evento.getEstadoAnterior());
        assertEquals("C", evento.getEstadoNuevo());
        assertEquals(3L, evento.getIdActor());
        assertEquals("AGENTE", evento.getTipoRolActor());
        assertEquals("Contacto inicial con el propietario.", evento.getMotivo());
    }

    @Test
    void laPropuestaEntregadaQuedaEnSeguimientoComoElCableV1() {
        // La v1 NUNCA usa el estado E: entregarPropuesta deja S y la marca de la
        // propuesta es fechaPropuesta + resultadoPropuesta=P. Guardia de paridad.
        prospeccionEnEstado(Prospeccion.REUNION);

        FichaProspeccion ficha = service.entregarPropuesta(5L, agente);

        assertEquals("S", ficha.estado());
        assertEquals("P", ficha.resultadoPropuesta());
        assertEquals(LocalDate.now(), ficha.fechaPropuesta());
        assertEquals("S", eventoAuditado().getEstadoNuevo());
    }

    /**
     * 3.5, corregido el 2026-08-08. Este metodo construye la captacion a mano en
     * vez de pasar por {@code CaptacionServiceImpl.registrar}, que es donde vive
     * el aviso — asi que el broker <b>casi nunca</b> se enteraba de que tenia una
     * captacion esperando, porque captar desde una prospeccion es el camino
     * NORMAL. Quedaba PENDIENTE_REVISION sin que nadie lo supiera.
     */
    @Test
    void captarAVISAalBrokerDeLaCaptacionQueNace() {
        Prospeccion p = prospeccionEnEstado(Prospeccion.EN_SEGUIMIENTO);
        p.marcarSeguimiento(LocalDate.now());
        when(captaciones.countByOrganizacionId(ORG)).thenReturn(1L);
        when(captaciones.save(any(Captacion.class))).thenAnswer(inv -> {
            Captacion guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 40L);
            return guardada;
        });

        service.captar(5L, new BigDecimal("100.00"), agente);

        ArgumentCaptor<AlertaService.DatosAlerta> aviso =
                ArgumentCaptor.forClass(AlertaService.DatosAlerta.class);
        verify(alertas).emitir(aviso.capture(), eq(agente));
        // Mismo tipo que el otro camino: para quien la recibe es el mismo hecho.
        assertEquals("CAPTACION_CREADA", aviso.getValue().tipo());
        assertEquals("CAPTACION", aviso.getValue().entidadTipo());
        assertEquals(40L, aviso.getValue().entidadId());
        // La alerta cuelga del AGENTE aunque el destinatario sea el broker: no
        // hay columna de destinatario y lo decide el TIPO.
        assertEquals(p.getAgente().getId(), aviso.getValue().idRolAgente());
    }

    @Test
    void captarCreaLaCaptacionPendienteYApagaElReloj() {
        Prospeccion p = prospeccionEnEstado(Prospeccion.EN_SEGUIMIENTO);
        p.marcarSeguimiento(LocalDate.now());
        when(captaciones.countByOrganizacionId(ORG)).thenReturn(1L);
        when(captaciones.save(any(Captacion.class))).thenAnswer(inv -> {
            Captacion guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 40L);
            return guardada;
        });

        FichaProspeccion ficha = service.captar(5L, new BigDecimal("100.00"), agente);

        assertEquals("T", ficha.estado());
        assertEquals("A", ficha.resultadoPropuesta());
        assertEquals(40L, ficha.idCaptacion());
        assertEquals("CAP-0002", ficha.captacionCodigo());
        assertNull(ficha.fechaRecontacto());

        ArgumentCaptor<Captacion> captacion = ArgumentCaptor.forClass(Captacion.class);
        verify(captaciones).save(captacion.capture());
        assertEquals("P", captacion.getValue().estadoActual());
        assertEquals(LocalDate.now(), captacion.getValue().getFechaCaptacion());
        assertEquals(p.getPropiedad(), captacion.getValue().getPropiedad());
        assertEquals(p.getAgente(), captacion.getValue().getAgente());
        assertEquals(ORG, captacion.getValue().getOrganizacionId());
        // El periodo del encargo es obligatorio siempre (decision 2026-08-01):
        // la v1 dejaba nacer el borrador sin fechas y la columna ya no lo admite.
        assertEquals(LocalDate.now(), captacion.getValue().getFechaInicioVigencia());
        assertEquals(LocalDate.now().plusMonths(6), captacion.getValue().getFechaFinVigencia());

        // Y la OPERACION va escrita, no supuesta (D-E4-1). Este camino dependia
        // del defecto `= "A"` de la entidad; al retirarlo dejo de escribirse y
        // `captacion.motivo_operacion` es NOT NULL, asi que captar desde una
        // prospeccion —el camino NORMAL— fallaba entero. No lo vio ningun test
        // de servicio porque con el repositorio simulado nada llega a la BD; lo
        // encontro la suite `f4-solicitud`.
        //
        // Se comprueba contra la condicion economica a proposito: son la misma
        // declaracion, y `tg_captacion_operacion_coherente` (V50) rechaza que
        // difieran.
        assertEquals(CondicionEconomicaCaptacion.ARRENDAMIENTO,
                captacion.getValue().getMotivoOperacion());
        assertEquals(captacion.getValue().getCondicionEconomica().getTipoOperacion(),
                captacion.getValue().getMotivoOperacion());

        // Una sola fila de historial: S->T de la prospeccion (el alta de la
        // captacion es estado inicial, no transicion).
        HistorialEstado evento = eventoAuditado();
        assertEquals("PROSPECCION", evento.getEntidadTipo());
        assertEquals("S", evento.getEstadoAnterior());
        assertEquals("T", evento.getEstadoNuevo());
        assertEquals("Propietario acepto; captacion CAP-0002 creada.", evento.getMotivo());
    }

    @Test
    void rechazarDescartaConResultadoRechazada() {
        prospeccionEnEstado(Prospeccion.EN_SEGUIMIENTO);

        FichaProspeccion ficha = service.rechazar(5L, "El propietario no acepta la comision", agente);

        assertEquals("D", ficha.estado());
        assertEquals("R", ficha.resultadoPropuesta());
        assertNull(ficha.fechaRecontacto());
        assertEquals("El propietario no acepta la comision", eventoAuditado().getMotivo());
    }

    @Test
    void descartarSinMotivoAuditaElMotivoPorDefecto() {
        prospeccionEnEstado(Prospeccion.CONTACTADO);

        FichaProspeccion ficha = service.descartar(5L, "  ", agente);

        assertEquals("D", ficha.estado());
        assertNull(ficha.resultadoPropuesta());
        assertEquals("Prospeccion descartada.", eventoAuditado().getMotivo());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Prospeccion id 5 del agente 30 sobre el local 9, visible para el actor. */
    private Prospeccion prospeccionEnEstado(String estado) {
        Prospeccion p = new Prospeccion();
        p.setOrganizacionId(ORG);
        p.setCodigoProspeccion("PRO-0005");
        p.setPropiedad(propiedad(9L));
        p.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(p, estado);
        ReflectionTestUtils.setField(p, "id", 5L);
        when(prospecciones.buscarFicha(ORG, 5L)).thenReturn(Optional.of(p));
        when(alcances.alcanza(agente, 30L)).thenReturn(true);
        return p;
    }

    private static Propiedad propiedad(long id) {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setCodigo("LOC-0100");
        propiedad.setDireccion("Av. Prueba 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setPrecioReferencial(new BigDecimal("8500.00"));
        propiedad.setMonedaReferencial("PEN");
        propiedad.iniciarDisponible();
        ReflectionTestUtils.setField(propiedad, "id", id);
        return propiedad;
    }

    private static DetalleAgente detalleAgente(long idRol, String nombre) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.AGENTE);
        DetalleAgente detalle = new DetalleAgente();
        detalle.setRol(rol);
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", idRol);
        return detalle;
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }

    /**
     * Un lector que no encuentra ningun valor gobernado.
     *
     * <p>Estas pruebas no afirman nada sobre el rubro; solo necesitan que el
     * servicio pueda construir su ficha. Devolver lotes vacios —y no un mock
     * sin respuestas— es lo correcto: {@code ValoresGobernados.vacio()}
     * significa "no se sabe nada de esta propiedad", que es un estado legitimo
     * del dominio, mientras que un null seria un fallo del andamiaje
     * disfrazado de dato.
     */
    private static LectorPorAutoridad lectorSinGobernados() {
        LectorPorAutoridad lector = mock(LectorPorAutoridad.class);
        lenient().when(lector.de(anyLong(), any())).thenReturn(ValoresGobernados.vacio());
        lenient().when(lector.deVarias(anyLong(), any())).thenReturn(Map.of());
        lenient().when(lector.gobernadosDeVarias(any())).thenReturn(Map.of());
        return lector;
    }
}
