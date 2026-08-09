package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorPropietario;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.PropietarioService.DatosPropietario;
import com.controllocal.service.PropietarioService.FichaPropietario;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Autorizaciones;
import com.controllocal.service.soporte.Alcances.Alcance;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda el contrato de {@code PropietariosRest} + {@code PropietarioBusinessLogicImpl}.
 *
 * <p>Lo que hay que no perder de vista:
 * <ul>
 *   <li>el catalogo es <b>compartido</b> para ADMIN y AGENTE; el unico rol
 *       acotado es el BROKER, y lo acota por sus PROPIEDADES (no por
 *       oportunidades, como en clientes);</li>
 *   <li>{@code cantidadLocales} es un contador CON ALCANCE: dos actores ven
 *       numeros distintos del mismo propietario, y eso es correcto;</li>
 *   <li>el PUT responde el contador en <b>0</b> porque el cable v1 no lo
 *       recalcula ({@code PropietarioResponse.desde} sin cantidad);</li>
 *   <li>actualizar reemplaza telefono, correo y nombre <b>tal cual llegan</b>,
 *       incluso a null, y no toca documento ni tipo de persona.</li>
 * </ul>
 */
class PropietarioServiceImplTest {

    private static final long ORG = 1L;
    private static final long PROPIETARIO = 50L;
    /** id que la BD asignaria a la persona recien insertada en el alta. */
    private static final long PERSONA_NUEVA = 501L;
    private static final long ROL_AGENTE = 30L;
    private static final long ROL_BROKER = 20L;

    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final Alcances alcances = mock(Alcances.class);
    // D-27: el alta exige autorizacion. Aqui va simulada para que estos tests
    // sigan comprobando lo suyo; la autorizacion tiene su propia suite.
    private final Autorizaciones autorizaciones = mock(Autorizaciones.class);

    private final PropietarioServiceImpl service =
            new PropietarioServiceImpl(roles, personas, propiedades, alcances, autorizaciones);

    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, ROL_BROKER, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, 10L, "TENANT_ADMIN");

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    void sinDatosRespondeElMensajeV1() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrar(null, agente));

        assertEquals("Los datos del propietario son obligatorios.", error.getMessage());
        verifyNoInteractions(personas);
    }

    @Test
    void losCodigosFueraDelVocabularioRespondenElMensajeDelEnumV1() {
        assertEquals("Valor invalido para tipo de persona: X",
                mensajeDe(datos("X", "D", "12345678", "Ana Ruiz")));
        assertEquals("Valor invalido para tipo de persona: null",
                mensajeDe(datos(null, "D", "12345678", "Ana Ruiz")));
        assertEquals("Valor invalido para tipo de documento: Z",
                mensajeDe(datos("N", "Z", "12345678", "Ana Ruiz")));
    }

    @Test
    void elDniExigeOchoDigitosYElRucOnce() {
        assertEquals("El DNI debe tener 8 digitos.",
                mensajeDe(datos("N", "D", "1234567", "Ana Ruiz")));
        assertEquals("El DNI solo debe contener numeros.",
                mensajeDe(datos("N", "D", "1234567A", "Ana Ruiz")));
        assertEquals("El RUC debe tener 11 digitos.",
                mensajeDe(datos("J", "R", "2010012345", "Inversiones SAC")));
    }

    @Test
    void carneYPasaporteNoTienenLargoExigido() {
        prepararAlta();

        assertEquals("X1234", service.registrar(
                datos("N", "C", "X1234", "Ana Ruiz"), agente).numeroDocumento());
    }

    @Test
    void elDocumentoYElNombreSonObligatorios() {
        assertEquals("El numero de documento es obligatorio.",
                mensajeDe(datos("N", "D", "  ", "Ana Ruiz")));
        assertEquals("El nombre o razon social es obligatorio.",
                mensajeDe(datos("N", "D", "12345678", "  ")));
    }

    @Test
    void elAltaCreaLaPersonaYSuROLPropietarioEnElTenantDelActor() {
        prepararAlta();

        FichaPropietario ficha = service.registrar(
                datos("N", "D", "12345678", "Ana Ruiz"), agente);

        Persona persona = personaGuardada();
        assertEquals(ORG, persona.getOrganizacionId());
        assertEquals("N", persona.getTipoPersona());
        assertEquals("12345678", persona.getNumeroDocumento());

        PersonaRol rol = rolGuardado();
        assertEquals(TipoRol.PROPIETARIO, rol.getTipoRol());
        assertEquals(ORG, rol.getOrganizacionId());
        assertNull(rol.getVigenciaHasta(), "el rol nace vigente");
        assertEquals(PROPIETARIO, ficha.id());
    }

    @Test
    void sinEstadoElAltaQuedaACTIVA() {
        prepararAlta();

        assertEquals("A", service.registrar(datos("N", "D", "12345678", "Ana Ruiz"), agente).estado());
    }

    @Test
    void elAltaNoConsultaElContadorPorqueAunNoTieneLocales() {
        prepararAlta();

        assertEquals(0, service.registrar(datos("N", "D", "12345678", "Ana Ruiz"), agente)
                .cantidadLocales());
        verifyNoInteractions(propiedades);
    }

    // ------------------------------------------------------------------
    // Alcance: solo el BROKER queda acotado
    // ------------------------------------------------------------------

    @Test
    void adminYAgenteVenElCatalogoENTEROSinPasarPorElAlcance() {
        for (Actor actor : List.of(admin, agente)) {
            when(alcances.de(actor)).thenReturn(new Alcance(ORG, actor.esTenantAdmin(), List.of(ROL_AGENTE)));
            when(roles.buscarPropietarios(eq(ORG), anyBoolean(), anyCollection(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(paginaCon(propietario(PROPIETARIO, "Ana Ruiz")));
            when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                    anyCollection(), anyLong())).thenReturn(List.of());

            assertEquals(1, service.listar(1, 10, actor).items().size());
            verify(propiedades, never()).idsPropietarioDelBroker(anyLong(), anyCollection(), anyLong());
        }
    }

    /**
     * El conjunto del BROKER entra como restriccion de ids en la MISMA consulta
     * que usan los demas, no por una rama aparte: asi la paginacion y los
     * filtros bajan a SQL tambien para el.
     */
    @Test
    void elBrokerSoloVeLosPropietariosDeSusPropiedades() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(propiedades.idsPropietarioDelBroker(ORG, List.of(ROL_AGENTE), ROL_BROKER))
                .thenReturn(List.of(PROPIETARIO, 51L));
        when(roles.buscarPropietarios(eq(ORG), eq(false), anyCollection(), isNull(), isNull(),
                any(Pageable.class)))
                .thenReturn(paginaCon(propietario(51L, "Beto Diaz"),
                        propietario(PROPIETARIO, "Ana Ruiz")));
        when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                anyCollection(), anyLong())).thenReturn(List.of());

        Pagina<FichaPropietario> pagina = service.listar(1, 10, broker);

        assertEquals(2, pagina.total());
        ArgumentCaptor<java.util.Collection<Long>> ids = captorDeIds();
        verify(roles).buscarPropietarios(eq(ORG), eq(false), ids.capture(), isNull(), isNull(),
                any(Pageable.class));
        assertEquals(List.of(PROPIETARIO, 51L), List.copyOf(ids.getValue()));
    }

    /**
     * Sin propiedades no se manda una lista vacia —un {@code IN ()} no es SQL
     * valido— sino el centinela, que no casa con ningun id y devuelve vacio.
     */
    @Test
    void unBrokerSinPropiedadesConsultaConElCentinelaYObtieneVacio() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(propiedades.idsPropietarioDelBroker(anyLong(), anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(roles.buscarPropietarios(eq(ORG), eq(false), anyCollection(), isNull(), isNull(),
                any(Pageable.class)))
                .thenReturn(paginaCon());

        Pagina<FichaPropietario> pagina = service.listar(1, 10, broker);

        assertEquals(0, pagina.total());
        assertTrue(pagina.items().isEmpty());
        ArgumentCaptor<java.util.Collection<Long>> ids = captorDeIds();
        verify(roles).buscarPropietarios(eq(ORG), eq(false), ids.capture(), isNull(), isNull(),
                any(Pageable.class));
        assertEquals(List.of(-1L), List.copyOf(ids.getValue()));
    }

    /** Los dos filtros son ADITIVOS y llegan a la consulta ya normalizados. */
    @Test
    void losFiltrosDelCatalogoViajanALaConsulta() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(roles.buscarPropietarios(eq(ORG), eq(true), anyCollection(), eq("ruiz"), eq("A"),
                any(Pageable.class)))
                .thenReturn(paginaCon());
        when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                anyCollection(), anyLong())).thenReturn(List.of());

        service.listar(new PropietarioService.FiltrosPropietario("  ruiz ", "a", 1, 10), agente);

        verify(roles).buscarPropietarios(eq(ORG), eq(true), anyCollection(), eq("ruiz"), eq("A"),
                any(Pageable.class));
    }

    @Test
    void unPropietarioFueraDelAlcanceDelBrokerResponde403NoUn404() {
        // La v1 distingue: si el propietario existe pero no lo alcanza, es
        // prohibido; solo si no existe, no encontrado.
        when(roles.buscarPropietario(ORG, PROPIETARIO))
                .thenReturn(Optional.of(propietario(PROPIETARIO, "Ana Ruiz")));
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(propiedades.idsPropietarioDelBroker(anyLong(), anyCollection(), anyLong()))
                .thenReturn(List.of(51L));

        assertThrows(AccesoNoAutorizadoException.class, () -> service.obtener(PROPIETARIO, broker));
    }

    @Test
    void unPropietarioInexistenteResponde404() {
        when(roles.buscarPropietario(ORG, PROPIETARIO)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.obtener(PROPIETARIO, agente));
    }

    @Test
    void elTamanoDePaginaSeAcotaEntre1Y100() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(roles.buscarPropietarios(eq(ORG), anyBoolean(), anyCollection(), isNull(), isNull(), any(Pageable.class))).thenReturn(paginaCon());
        when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                anyCollection(), anyLong())).thenReturn(List.of());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        service.listar(0, 5000, agente);
        verify(roles).buscarPropietarios(eq(ORG), anyBoolean(), anyCollection(), isNull(),
                isNull(), pageable.capture());

        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    // ------------------------------------------------------------------
    // Contador con alcance
    // ------------------------------------------------------------------

    @Test
    void elContadorLlegaDeLaProyeccionYCaeA0CuandoNoHayFila() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(roles.buscarPropietarios(eq(ORG), anyBoolean(), anyCollection(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(paginaCon(propietario(PROPIETARIO, "Ana Ruiz"), propietario(51L, "Beto Diaz")));
        when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                anyCollection(), anyLong()))
                .thenReturn(List.of(conteo(PROPIETARIO, 3)));

        List<FichaPropietario> items = service.listar(1, 10, agente).items();

        assertEquals(3, items.get(0).cantidadLocales());
        assertEquals(0, items.get(1).cantidadLocales());
    }

    @Test
    void elAdminCuentaSinFiltroDeRolYElBrokerConSuRolDeRevisor() {
        when(alcances.de(admin)).thenReturn(new Alcance(ORG, true, List.of()));
        when(roles.buscarPropietarios(eq(ORG), anyBoolean(), anyCollection(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(paginaCon(propietario(PROPIETARIO, "Ana Ruiz")));
        when(propiedades.contarLocalesEnSeguimiento(anyLong(), anyCollection(), anyBoolean(),
                anyCollection(), anyLong())).thenReturn(List.of());

        service.listar(1, 10, admin);

        // sinScope=true y rolBroker=-1: el ADMIN no filtra por rol dentro del tenant.
        verify(propiedades).contarLocalesEnSeguimiento(eq(ORG), anyCollection(), eq(true),
                eq(List.of(-1L)), eq(-1L));
    }

    // ------------------------------------------------------------------
    // Actualizacion y baja
    // ------------------------------------------------------------------

    @Test
    void actualizarReemplazaTelefonoCorreoYNombreTalCualLleganInclusoANull() {
        PersonaRol propietario = prepararAcceso();
        DatosPropietario datos = new DatosPropietario(null, null, null, "Ana Ruiz Vega",
                null, null, null, null);

        service.actualizar(PROPIETARIO, datos, agente);

        Persona persona = propietario.getPersona();
        assertEquals("Ana Ruiz Vega", persona.getNombresORazonSocial());
        assertNull(persona.getTelefono());
        assertNull(persona.getCorreo());
        // Ni documento ni tipo de persona se tocan aunque lleguen nulos.
        assertEquals("12345678", persona.getNumeroDocumento());
        assertEquals("N", persona.getTipoPersona());
    }

    @Test
    void actualizarRevalidaLaPersonaEnteraAunqueElPutNoTraigaDocumento() {
        prepararAcceso();
        DatosPropietario sinNombre = new DatosPropietario(null, null, null, null,
                "999", "a@b.c", null, null);

        assertEquals("El nombre o razon social es obligatorio.",
                assertThrows(ReglaNegocioException.class,
                        () -> service.actualizar(PROPIETARIO, sinNombre, agente)).getMessage());
    }

    @Test
    void actualizarNoRecalculaElContadorYRespondeCero() {
        // Paridad con el cable: PropietarioResponse.desde(actual) sin cantidad.
        prepararAcceso();

        FichaPropietario ficha = service.actualizar(PROPIETARIO,
                new DatosPropietario(null, null, null, "Ana Ruiz", null, null, null, null), agente);

        assertEquals(0, ficha.cantidadLocales());
        verify(propiedades, never()).contarLocalesEnSeguimiento(anyLong(), anyCollection(),
                anyBoolean(), anyCollection(), anyLong());
    }

    @Test
    void elConsentimientoSoloSePisaSiViene() {
        PersonaRol propietario = prepararAcceso();
        propietario.getPersona().setConsentimientoUsoDato(true);

        service.actualizar(PROPIETARIO,
                new DatosPropietario(null, null, null, "Ana Ruiz", null, null, null, null), agente);

        assertEquals(true, propietario.getPersona().getConsentimientoUsoDato());
    }

    @Test
    void desactivarDejaLaPersonaINACTIVA() {
        PersonaRol propietario = prepararAcceso();

        assertTrue(service.desactivar(PROPIETARIO, agente));
        assertEquals("I", propietario.getPersona().getEstado());
        verify(personas).save(propietario.getPersona());
    }

    @Test
    void desactivarUnoInexistenteDevuelveFalseParaQueElControladorResponda404() {
        when(roles.buscarPropietario(ORG, PROPIETARIO)).thenReturn(Optional.empty());

        assertFalse(service.desactivar(PROPIETARIO, agente));
        verifyNoInteractions(personas);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String mensajeDe(DatosPropietario datos) {
        return assertThrows(ReglaNegocioException.class,
                () -> service.registrar(datos, agente)).getMessage();
    }

    private static DatosPropietario datos(String tipoPersona, String tipoDocumento,
                                          String numeroDocumento, String nombre) {
        return new DatosPropietario(tipoPersona, tipoDocumento, numeroDocumento, nombre,
                "999888777", "duenio@correo.test", true, null);
    }

    private void prepararAlta() {
        // El id lo asigna la BD al insertar (GenerationType.IDENTITY), y el alta
        // lo necesita para registrar la autorizacion (D-27). El mock tiene que
        // imitarlo o el alta reventaria con un NPE que no existe en produccion.
        when(personas.save(any(Persona.class))).thenAnswer(inv -> {
            Persona persona = inv.getArgument(0);
            if (persona.getId() == null) {
                ReflectionTestUtils.setField(persona, "id", PERSONA_NUEVA);
            }
            return persona;
        });
        when(roles.save(any(PersonaRol.class))).thenAnswer(inv -> {
            PersonaRol rol = inv.getArgument(0);
            ReflectionTestUtils.setField(rol, "id", PROPIETARIO);
            return rol;
        });
    }

    /** Propietario existente y alcanzable por el AGENTE (catalogo compartido). */
    private PersonaRol prepararAcceso() {
        PersonaRol propietario = propietario(PROPIETARIO, "Ana Ruiz");
        when(roles.buscarPropietario(ORG, PROPIETARIO)).thenReturn(Optional.of(propietario));
        when(personas.save(any(Persona.class))).thenAnswer(inv -> inv.getArgument(0));
        return propietario;
    }

    private static PersonaRol propietario(long id, String nombre) {
        Persona persona = new Persona();
        persona.setOrganizacionId(ORG);
        persona.setTipoPersona("N");
        persona.setTipoDocumento("D");
        persona.setNumeroDocumento("12345678");
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono("999888777");
        persona.setCorreo("duenio@correo.test");
        persona.setEstado("A");

        PersonaRol rol = new PersonaRol();
        rol.setOrganizacionId(ORG);
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.PROPIETARIO);
        ReflectionTestUtils.setField(rol, "id", id);
        return rol;
    }

    private static ConteoPorPropietario conteo(long idPropietario, int total) {
        return new ConteoPorPropietario() {
            @Override
            public Long getIdPropietario() {
                return idPropietario;
            }

            @Override
            public int getTotal() {
                return total;
            }
        };
    }

    private static Page<PersonaRol> paginaCon(PersonaRol... propietarios) {
        return new PageImpl<>(List.of(propietarios));
    }

    private Persona personaGuardada() {
        ArgumentCaptor<Persona> guardada = ArgumentCaptor.forClass(Persona.class);
        verify(personas).save(guardada.capture());
        return guardada.getValue();
    }

    private PersonaRol rolGuardado() {
        ArgumentCaptor<PersonaRol> guardado = ArgumentCaptor.forClass(PersonaRol.class);
        verify(roles).save(guardado.capture());
        return guardado.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<java.util.Collection<Long>> captorDeIds() {
        return ArgumentCaptor.forClass(java.util.Collection.class);
    }
}
