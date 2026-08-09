package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.OportunidadService.DatosOportunidad;
import com.controllocal.service.OportunidadService.FichaOportunidad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda los mensajes del cable de OportunidadesRest y la maquina A -> N, mas
 * la mejora MEJ-01: el cierre por no continuidad deja fila en historial_estado.
 * Incluye el 400 deliberado de cierre-exitoso, que es cable real.
 */
class OportunidadServiceImplTest {

    private static final long ORG = 1L;

    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final MotivoNoContinuidadRepository motivos = mock(MotivoNoContinuidadRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final DetalleClienteRepository clientes = mock(DetalleClienteRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final OportunidadServiceImpl service = new OportunidadServiceImpl(
            oportunidades, motivos, captaciones, clientes, agentes, alcances, new Transiciones(historial),
            new PlanDeConsulta());

    /** vmora: organizacion 1, persona 3, rol operativo 30. */
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");
    /** rsalas: broker que supervisa al rol 30. */
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Mensajes del contrato (paridad v1)
    // ------------------------------------------------------------------

    @Test
    void registrarSinDatosRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, agente));
        assertEquals("Los datos de la oportunidad son obligatorios.", error.getMessage());
    }

    @Test
    void registrarSinClienteRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosOportunidad(null, null, 7L, null, null), agente));
        assertEquals("Selecciona un cliente interesado.", error.getMessage());
    }

    @Test
    void registrarSinCaptacionRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosOportunidad(null, 50L, null, null, null), agente));
        assertEquals("Selecciona una captacion activa.", error.getMessage());
    }

    @Test
    void unaCaptacionDeOtroAgenteNoSirveParaAbrirOportunidad() {
        Captacion ajena = captacion(7L, 99L, Captacion.ACTIVA);
        when(captaciones.buscarFicha(ORG, 7L)).thenReturn(Optional.of(ajena));

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(new DatosOportunidad(null, 50L, 7L, null, null), agente));
        verifyNoInteractions(historial);
    }

    @Test
    void unaCaptacionInexistenteRespondeIgualQueUnaAjena() {
        // La v1 solo consulta la lista del agente: no distingue "no existe" de
        // "no es tuya", y las dos responden 403.
        when(captaciones.buscarFicha(ORG, 7L)).thenReturn(Optional.empty());

        assertThrows(AccesoNoAutorizadoException.class,
                () -> service.registrar(new DatosOportunidad(null, 50L, 7L, null, null), agente));
    }

    @Test
    void noSePuedeAbrirDosVecesLaMismaOportunidad() {
        prepararAltaValida();
        when(oportunidades.existeAbiertaDe(ORG, 50L, 7L)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(new DatosOportunidad(null, 50L, 7L, null, null), agente));
        assertEquals("Ya existe una oportunidad abierta para el cliente y captacion.", error.getMessage());
    }

    @Test
    void elCierreExitosoSiempreFallaConElMensajeDelCable() {
        oportunidadAbierta();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cierreExitoso(8L, agente));
        assertEquals("El cierre exitoso se registra desde la solicitud aprobada para crear el "
                + "contrato de alquiler.", error.getMessage());
    }

    @Test
    void elCierreExitosoValidaElAccesoAntesDeFallar() {
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, () -> service.cierreExitoso(8L, agente));
    }

    // ------------------------------------------------------------------
    // Maquina de estados + auditoria
    // ------------------------------------------------------------------

    @Test
    void elAltaAbreEnAConCodigoAutogeneradoYSinAuditar() {
        prepararAltaValida();

        FichaOportunidad ficha = service.registrar(
                new DatosOportunidad("  ", 50L, 7L, "Vino por el anuncio", null), agente);

        assertEquals("A", ficha.estado());
        assertTrue(ficha.codigoOportunidad().startsWith("OP-"), ficha.codigoOportunidad());
        assertEquals(50L, ficha.idCliente());
        assertEquals(7L, ficha.idCaptacion());
        assertEquals(30L, ficha.idAgente());
        // El estado inicial no es una transicion: la v1 tampoco lo registraba.
        verifyNoInteractions(historial);

        ArgumentCaptor<OportunidadComercial> guardada =
                ArgumentCaptor.forClass(OportunidadComercial.class);
        verify(oportunidades).save(guardada.capture());
        assertEquals(ORG, guardada.getValue().getOrganizacionId());
    }

    @Test
    void elCodigoQueLlegaEnElRequestSeRespeta() {
        prepararAltaValida();

        FichaOportunidad ficha = service.registrar(
                new DatosOportunidad(" OP-MANUAL ", 50L, 7L, null, null), agente);

        assertEquals("OP-MANUAL", ficha.codigoOportunidad());
    }

    @Test
    void laNoContinuidadCierraEnNGuardaLaRazonYAudita() {
        oportunidadAbierta();
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));

        FichaOportunidad ficha = service.noContinuidad(8L, "P", "Le parecio caro", agente);

        assertEquals("N", ficha.estado());
        // El cable guarda la DESCRIPCION de la razon, no su codigo.
        assertEquals("Precio", ficha.motivoCierre());

        ArgumentCaptor<MotivoNoContinuidad> motivo = ArgumentCaptor.forClass(MotivoNoContinuidad.class);
        verify(motivos).save(motivo.capture());
        assertEquals("P", motivo.getValue().getRazonPrincipal());
        assertEquals("Le parecio caro", motivo.getValue().getObservaciones());
        assertEquals(ORG, motivo.getValue().getOrganizacionId());

        HistorialEstado evento = eventoAuditado();
        assertEquals("OPORTUNIDAD", evento.getEntidadTipo());
        assertEquals("A", evento.getEstadoAnterior());
        assertEquals("N", evento.getEstadoNuevo());
        assertEquals(3L, evento.getIdActor());
        assertEquals("AGENTE", evento.getTipoRolActor());
        assertEquals("Precio", evento.getMotivo());
    }

    @Test
    void laNoContinuidadExigeUnaRazonValida() {
        oportunidadAbierta();

        ReglaNegocioException sinRazon = assertThrows(ReglaNegocioException.class,
                () -> service.noContinuidad(8L, "  ", null, agente));
        assertEquals("El motivo de no continuidad es obligatorio.", sinRazon.getMessage());

        ReglaNegocioException invalida = assertThrows(ReglaNegocioException.class,
                () -> service.noContinuidad(8L, "Z", null, agente));
        assertEquals("Codigo invalido para MotivoNoContinuidadTipo: Z", invalida.getMessage());
        verifyNoInteractions(historial);
    }

    @Test
    void unaOportunidadYaCerradaNoVuelveACerrarse() {
        OportunidadComercial o = oportunidadAbierta();
        new Transiciones(mock(HistorialEstadoRepository.class))
                .aplicar(o, 8L, OportunidadComercial.NO_CONTINUA, null, null);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.noContinuidad(8L, "P", null, agente));
        assertEquals("La oportunidad comercial debe estar ABIERTA.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Alcance: el BROKER alcanza por CAPTACION, no por agente (§4)
    // ------------------------------------------------------------------

    @Test
    void elBrokerAlcanzaLaOportunidadPorLaCaptacionDeSuEquipo() {
        oportunidadAbierta();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(30L));

        assertEquals("A", service.obtener(8L, broker).estado());
    }

    @Test
    void elBrokerNoAlcanzaLaOportunidadDeUnaCaptacionAjena() {
        oportunidadAbierta();
        when(alcances.supervisados(ORG, 20L)).thenReturn(List.of(77L));

        assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(8L, broker));
    }

    @Test
    void otroAgenteNoAlcanzaLaOportunidad() {
        oportunidadAbierta();
        Actor otro = new Actor(ORG, 9L, 31L, "AGENTE");

        assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(8L, otro));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void prepararAltaValida() {
        when(captaciones.buscarFicha(ORG, 7L)).thenReturn(Optional.of(captacion(7L, 30L, Captacion.ACTIVA)));
        when(clientes.buscarFicha(ORG, 50L)).thenReturn(Optional.of(detalleCliente(50L, "Mariana Delgado")));
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(oportunidades.save(any(OportunidadComercial.class))).thenAnswer(inv -> {
            OportunidadComercial guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 8L);
            return guardada;
        });
    }

    /** Oportunidad 8, del agente 30, sobre la captacion 7 (tambien del 30). */
    private OportunidadComercial oportunidadAbierta() {
        OportunidadComercial o = new OportunidadComercial();
        o.setOrganizacionId(ORG);
        o.setCodigoOportunidad("OP-0001");
        o.setCliente(detalleCliente(50L, "Mariana Delgado"));
        o.setCaptacion(captacion(7L, 30L, Captacion.ACTIVA));
        o.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(o, OportunidadComercial.ABIERTA);
        ReflectionTestUtils.setField(o, "id", 8L);
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(o));
        when(oportunidades.save(any(OportunidadComercial.class))).thenAnswer(inv -> inv.getArgument(0));
        return o;
    }

    private static Captacion captacion(long id, long idRolAgente, String estado) {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        ReflectionTestUtils.setField(propiedad, "id", 9L);

        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(ORG);
        captacion.setCodigoCaptacion("CAP-0001");
        captacion.setPropiedad(propiedad);
        captacion.setAgente(detalleAgente(idRolAgente, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(captacion, estado);
        ReflectionTestUtils.setField(captacion, "id", id);
        return captacion;
    }

    private static DetalleCliente detalleCliente(long idRol, String nombre) {
        DetalleCliente cliente = new DetalleCliente();
        cliente.setOrganizacionId(ORG);
        cliente.setRol(personaRol(nombre, TipoRol.CLIENTE));
        ReflectionTestUtils.setField(cliente, "id", idRol);
        return cliente;
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

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
