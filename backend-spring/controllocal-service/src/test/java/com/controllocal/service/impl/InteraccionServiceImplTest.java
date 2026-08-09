package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.Prospeccion;
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
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService.DatosInteraccion;
import com.controllocal.service.InteraccionService.FichaInteraccion;
import com.controllocal.service.InteraccionService.FiltrosInteraccion;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la bitacora polimorfica: derivacion del contexto, allow-list de
 * resultado por contexto y los dos efectos que sorprenden del cable real —el
 * resultado es obligatorio en el alta y una interaccion de prospeccion MUEVE
 * el embudo del propietario—.
 */
class InteraccionServiceImplTest {

    private static final long ORG = 1L;

    private final InteraccionComercialRepository interacciones =
            mock(InteraccionComercialRepository.class);
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final DetalleClienteRepository clientes = mock(DetalleClienteRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    private final InteraccionServiceImpl service = new InteraccionServiceImpl(
            interacciones, oportunidades, prospecciones, captaciones, clientes, agentes, alcances,
            new Transiciones(historial), new PlanDeConsulta());

    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");

    // ------------------------------------------------------------------
    // Contexto polimorfico
    // ------------------------------------------------------------------

    @Test
    void registrarSinDatosRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, agente));
        assertEquals("La interaccion es obligatoria.", error.getMessage());
    }

    @Test
    void unContextoInvalidoRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosInteraccion("VISITA", null, null, null, null, "L", null, null, null), agente));
        assertEquals("Contexto de interaccion invalido: VISITA", error.getMessage());
    }

    @Test
    void elContextoSeDerivaDelIdPresenteEnElOrdenDelCable() {
        // Con prospeccion y cliente a la vez gana PROSPECCION (primero en el orden).
        Prospeccion prospeccion = prospeccionEnEstado(Prospeccion.PROSPECTO);
        prepararAlta();

        FichaInteraccion ficha = service.registrar(new DatosInteraccion(
                null, null, 5L, null, 50L, "W", "CONTACTADO", null, null), agente);

        assertEquals("PROSPECCION", ficha.contexto());
        assertEquals(5L, ficha.idProspeccion());
        assertEquals("PRO-0005", ficha.codigoProspeccion());
        assertNull(ficha.idCliente());
        assertEquals(prospeccion.getId(), ficha.idProspeccion());
    }

    @Test
    void sinNingunIdElContextoEsOportunidad() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosInteraccion(null, null, null, null, null, "L", "INTERESADO", null, null), agente));
        assertEquals("La oportunidad de la interaccion es obligatoria.", error.getMessage());
    }

    @Test
    void cadaContextoExigeElIdDeSuEntidad() {
        assertEquals("La prospeccion de la interaccion es obligatoria.",
                mensajeDe(new DatosInteraccion("PROSPECCION", null, null, null, null, "L", "CONTACTADO", null, null)));
        assertEquals("La captacion de la interaccion es obligatoria.",
                mensajeDe(new DatosInteraccion("CAPTACION", null, null, null, null, "L", "DOCS_SOLICITADOS", null, null)));
        assertEquals("El cliente interesado de la interaccion es obligatorio.",
                mensajeDe(new DatosInteraccion("CLIENTE", null, null, null, null, "L", "SEGUIMIENTO", null, null)));
    }

    // ------------------------------------------------------------------
    // Canal y resultado
    // ------------------------------------------------------------------

    @Test
    void elCanalEsObligatorioYSeValida() {
        assertEquals("El canal de contacto es obligatorio.",
                mensajeDe(new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, null, "INTERESADO", null, null)));
        assertEquals("Canal de contacto invalido: Z",
                mensajeDe(new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, "Z", "INTERESADO", null, null)));
    }

    @Test
    void elResultadoEsObligatorioAunqueElDtoLoDeclareOpcional() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, "L", null, null, null), agente));
        assertEquals("La interaccion debe tener canal y resultado.", error.getMessage());
    }

    @Test
    void unResultadoDesconocidoRespondeElMensajeV1() {
        assertEquals("Resultado de interaccion invalido: XYZ",
                mensajeDe(new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, "L", "XYZ", null, null)));
    }

    @Test
    void cadaContextoTieneSuAllowListDeResultado() {
        // CONTACTADO es valido... pero solo en PROSPECCION.
        assertEquals("Resultado no valido para OPORTUNIDAD: CONTACTADO",
                mensajeDe(new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, "L", "CONTACTADO", null, null)));
        assertEquals("Resultado no valido para CLIENTE: INTERESADO",
                mensajeDe(new DatosInteraccion("CLIENTE", null, null, null, 50L, "L", "INTERESADO", null, null)));
    }

    // ------------------------------------------------------------------
    // Efectos sobre la entidad colgada
    // ------------------------------------------------------------------

    @Test
    void noSeRegistraSobreUnaOportunidadCerrada() {
        OportunidadComercial cerrada = oportunidad();
        new Transiciones(mock(HistorialEstadoRepository.class))
                .aplicar(cerrada, 8L, OportunidadComercial.NO_CONTINUA, null, null);
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(cerrada));
        prepararAlta();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosInteraccion("OPORTUNIDAD", 8L, null, null, null, "L", "INTERESADO", null, null),
                agente));
        assertEquals("La oportunidad comercial debe estar ABIERTA.", error.getMessage());
    }

    @Test
    void noSeRegistraSobreUnaProspeccionYaCaptada() {
        prospeccionEnEstado(Prospeccion.CAPTADO);
        prepararAlta();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.registrar(
                new DatosInteraccion("PROSPECCION", null, 5L, null, null, "L", "CONTACTADO", null, null),
                agente));
        assertEquals("La prospeccion debe estar activa y sin captar para registrar interacciones.",
                error.getMessage());
    }

    @Test
    void unaInteraccionDeProspeccionMueveElEmbudoYLaAudita() {
        Prospeccion prospeccion = prospeccionEnEstado(Prospeccion.CONTACTADO);
        prepararAlta();

        service.registrar(new DatosInteraccion(
                "PROSPECCION", null, 5L, null, null, "R", "PROPUESTA_ENVIADA", null, null), agente);

        assertEquals("S", prospeccion.estadoActual());
        assertEquals("P", prospeccion.getResultadoPropuesta());
        HistorialEstado evento = eventoAuditado();
        assertEquals("PROSPECCION", evento.getEntidadTipo());
        assertEquals("C", evento.getEstadoAnterior());
        assertEquals("S", evento.getEstadoNuevo());
        assertEquals(3L, evento.getIdActor());
        assertEquals("Propuesta entregada desde una interaccion.", evento.getMotivo());
    }

    @Test
    void unResultadoSinHitoSoloSacaDelEstadoInicial() {
        // NO_ACEPTA no tiene hito propio: si la prospeccion sigue en P la
        // marca como contactada; desde cualquier otro estado no la mueve.
        Prospeccion prospeccion = prospeccionEnEstado(Prospeccion.REUNION);
        prepararAlta();

        service.registrar(new DatosInteraccion(
                "PROSPECCION", null, 5L, null, null, "L", "NO_ACEPTA", null, null), agente);

        assertEquals("R", prospeccion.estadoActual());
        verifyNoInteractions(historial);
    }

    @Test
    void unaInteraccionDeCaptacionNoTocaNingunEstado() {
        when(captaciones.buscarFicha(ORG, 7L)).thenReturn(Optional.of(captacion()));
        prepararAlta();

        FichaInteraccion ficha = service.registrar(new DatosInteraccion(
                "CAPTACION", null, null, 7L, null, "P", "DOCS_SOLICITADOS", "Pedidos los papeles", null),
                agente);

        assertEquals("CAPTACION", ficha.contexto());
        assertEquals(7L, ficha.idCaptacion());
        assertEquals("CAP-0001", ficha.codigoCaptacion());
        verifyNoInteractions(historial);
    }

    // ------------------------------------------------------------------
    // Forma de la respuesta
    // ------------------------------------------------------------------

    @Test
    void enContextoDePropietarioLaPersonaEsElPropietario() {
        prospeccionEnEstado(Prospeccion.PROSPECTO);
        prepararAlta();

        FichaInteraccion ficha = service.registrar(new DatosInteraccion(
                "PROSPECCION", null, 5L, null, null, "L", "CONTACTADO", null, null), agente);

        assertEquals("Propietario", ficha.personaTipo());
        assertEquals("Hugo Salazar", ficha.personaNombre());
        assertEquals("Hugo Salazar", ficha.propietarioNombre());
        assertEquals(60L, ficha.idPropietario());
        assertEquals("Valentina Mora", ficha.agenteNombre());
    }

    @Test
    void enContextoDeOportunidadLaPersonaEsElCliente() {
        when(oportunidades.buscarFicha(ORG, 8L)).thenReturn(Optional.of(oportunidad()));
        prepararAlta();

        FichaInteraccion ficha = service.registrar(new DatosInteraccion(
                "OPORTUNIDAD", 8L, null, null, null, "W", "INTERESADO", null, null), agente);

        assertEquals("Cliente", ficha.personaTipo());
        assertEquals("Mariana Delgado", ficha.personaNombre());
        assertEquals(50L, ficha.idCliente());
        // La captacion y el propietario se derivan de la oportunidad.
        assertEquals(7L, ficha.idCaptacion());
        assertEquals("Hugo Salazar", ficha.propietarioNombre());
    }

    // ------------------------------------------------------------------
    // Filtros
    // ------------------------------------------------------------------

    @Test
    void soloSeAdmiteUnFiltroDeEntidad() {
        FiltrosInteraccion filtros = new FiltrosInteraccion(null, 8L, 5L, null, null, null, null, null,
                null, 1, 50);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.listar(filtros, agente));
        assertEquals("Filtra por una sola entidad de interaccion.", error.getMessage());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String mensajeDe(DatosInteraccion datos) {
        prepararAlta();
        return assertThrows(ReglaNegocioException.class, () -> service.registrar(datos, agente))
                .getMessage();
    }

    private void prepararAlta() {
        when(agentes.findById(30L)).thenReturn(Optional.of(detalleAgente(30L, "Valentina Mora")));
        when(clientes.buscarFicha(ORG, 50L)).thenReturn(Optional.of(detalleCliente(50L, "Mariana Delgado")));
        when(interacciones.save(any(InteraccionComercial.class))).thenAnswer(inv -> {
            InteraccionComercial guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 11L);
            return guardada;
        });
        when(prospecciones.save(any(Prospeccion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Prospeccion prospeccionEnEstado(String estado) {
        Prospeccion prospeccion = new Prospeccion();
        prospeccion.setOrganizacionId(ORG);
        prospeccion.setCodigoProspeccion("PRO-0005");
        prospeccion.setPropiedad(propiedad());
        prospeccion.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(prospeccion, estado);
        ReflectionTestUtils.setField(prospeccion, "id", 5L);
        when(prospecciones.buscarFicha(ORG, 5L)).thenReturn(Optional.of(prospeccion));
        return prospeccion;
    }

    private static Captacion captacion() {
        Captacion captacion = new Captacion();
        captacion.setOrganizacionId(ORG);
        captacion.setCodigoCaptacion("CAP-0001");
        captacion.setPropiedad(propiedad());
        captacion.setAgente(detalleAgente(30L, "Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(captacion, Captacion.ACTIVA);
        ReflectionTestUtils.setField(captacion, "id", 7L);
        return captacion;
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

    private static Propiedad propiedad() {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setDireccion("Av. Larco 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        PersonaRol propietario = personaRol("Hugo Salazar", TipoRol.PROPIETARIO);
        ReflectionTestUtils.setField(propietario, "id", 60L);
        propiedad.setRolPropietario(propietario);
        ReflectionTestUtils.setField(propiedad, "id", 9L);
        return propiedad;
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
