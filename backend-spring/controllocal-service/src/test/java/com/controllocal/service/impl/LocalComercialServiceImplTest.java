package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.busqueda.ConjuntoDeCandidatos;
import com.controllocal.persistence.busqueda.CriterioBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.MotorBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.OrdenDelListado;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.LocalComercialService.DatosLocal;
import com.controllocal.service.LocalComercialService.FichaLocal;
import com.controllocal.service.LocalComercialService.FiltrosLocal;
import com.controllocal.service.LocalComercialService.PosibleDuplicado;
import com.controllocal.service.LocalComercialService.ResumenLocales;
import com.controllocal.service.Pagina;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.controllocal.service.soporte.AtributosGobernados;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.ValoresGobernados;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Blinda las reglas y los MENSAJES del contrato congelado (identicos a los
 * del backend Jakarta) sin tocar la base: el corte del modulo depende de
 * que estas respuestas no cambien.
 */
class LocalComercialServiceImplTest {

    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);
    private final DistritoRepository distritos = mock(DistritoRepository.class);
    private final FotoPropiedadRepository fotos = mock(FotoPropiedadRepository.class);
    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final ProspeccionService prospecciones = mock(ProspeccionService.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    /**
     * Las dos mitades de D-E4-3. Se mockean y no se stubbean con valores porque
     * este test blinda los mensajes y el shaping de /locales, no la resolucion
     * de autoridades: eso lo prueba `AutoridadDelDatoIntegrationTest`, que si
     * pasa por la base.
     */
    private final LectorPorAutoridad lector = mock(LectorPorAutoridad.class);
    private final AtributosGobernados gobierno = mock(AtributosGobernados.class);

    /**
     * <b>La autoridad va de verdad, no mockeada</b> (P0).
     *
     * <p>Un mock permisivo aqui dejaria pasar exactamente el defecto que la
     * autoridad existe para impedir, y este test seguiria verde con la regla
     * apagada. Se construye con sus repositorios mockeados, que es otra cosa:
     * lo que decide sigue siendo el codigo real.
     */
    private final AutoridadDePropiedad autoridad = new AutoridadDePropiedad(
            mock(com.controllocal.persistence.repositorio.DetalleAgenteRepository.class),
            mock(com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository.class),
            new com.controllocal.service.soporte.Alcances(
                    mock(com.controllocal.persistence.repositorio.SupervisionAgenteRepository.class)),
            // Idem con la elegibilidad del destino (D-P0-7): el codigo real, con
            // su repositorio mockeado. Estas pruebas no traspasan, asi que no la
            // ejercitan; ponerla de mock permisivo seria dejar apagada una regla
            // que este mismo fichero declara que corre de verdad.
            new com.controllocal.service.soporte.ElegibilidadDeResponsable(
                    mock(com.controllocal.persistence.repositorio.DetalleAgenteRepository.class)),
            // El repositorio del compare-and-set del responsable (D-P0-9). Va
            // mockeado por la misma razon que los otros: estas pruebas no
            // traspasan, asi que no lo ejercitan. Que el CAS haga lo que dice se
            // prueba con dos transacciones reales en
            // CausalidadDelTraspasoIntegrationTest, no con un mock.
            propiedades);

    /**
     * El motor va mockeado, y eso es deliberado: lo que esta prueba fija es QUE
     * CRITERIO construye este recurso, no que el SQL funcione. Que el motor
     * devuelva lo que dice se prueba contra PostgreSQL real en
     * BusquedaLocalesIntegrationTest.
     */
    private final MotorBusquedaInmobiliaria motor = mock(MotorBusquedaInmobiliaria.class);

    private final LocalComercialServiceImpl service = new LocalComercialServiceImpl(
            propiedades, roles, distritos, fotos, precios, publicaciones, prospecciones,
            captaciones, new Transiciones(historial), mock(AlertaService.class),
            lector, gobierno, autoridad, motor);

    /**
     * El lector devuelve "no se sabe nada" en vez de null.
     *
     * <p>No es una comodidad del mock: {@link ValoresGobernados#vacio()} es un
     * valor legitimo del dominio -- una propiedad sin ningun gobernado escrito --
     * y este test comprueba precisamente que ese caso no rompe el mapeo. Stubbearlo
     * con valores concretos convertiria este test unitario en una prueba del
     * enrutador, que ya tiene la suya contra PostgreSQL.
     */
    @BeforeEach
    void elLectorNoDevuelveNull() {
        when(lector.de(anyLong(), any())).thenReturn(ValoresGobernados.vacio());
    }

    /** Organizacion de legado: el tenant que el backend resuelve para la sesion (V6). */
    private static final long ORG = 1L;

    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");

    private static DatosLocal datos(String estado, String tipoInmueble, String uso,
                                    BigDecimal metraje, BigDecimal precio) {
        return new DatosLocal("LOC-0100", "Av. Prueba 123", "Miraflores", metraje, precio,
                "PEN", "Comercio minorista", null, 9L, estado, tipoInmueble, uso, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static DatosLocal datosValidos() {
        return datos(null, null, null, new BigDecimal("120.00"), new BigDecimal("8500.00"));
    }

    private PersonaRol rolPropietario(String nombre) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.PROPIETARIO);
        return rol; // sin vigencia_hasta => vigente
    }

    // ------------------------------------------------------------------
    // Mensajes del contrato (paridad v1)
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Alta y trazabilidad
    // ------------------------------------------------------------------

    @Test
    void desactivarTransicionaAInactivoYLoAudita() {
        Propiedad propiedad = propiedadExistente();
        // Retirar la propiedad lo hace SU responsable (P0-1). Antes bastaba con
        // haberla prospectado, y esa era la regla que dejaba a dos agentes
        // distintos ser "duenos" del mismo inmueble a la vez.
        propiedad.responsable(30L);
        // Con la fila TOMADA (F2.10): `desactivar` escribe, asi que la carga que
        // hace es la del candado y no la de lectura.
        when(propiedades.bloquearParaEscritura(ORG, 7L)).thenReturn(Optional.of(propiedad));

        service.desactivar(7L, agente);

        assertEquals("I", propiedad.estadoActual());
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial, org.mockito.Mockito.times(2)).save(evento.capture());
        HistorialEstado registro = evento.getAllValues().get(0);
        HistorialEstado disponibilidad = evento.getAllValues().get(1);
        assertEquals("PROPIEDAD", registro.getEntidadTipo());
        assertEquals("A", registro.getEstadoAnterior());
        assertEquals("I", registro.getEstadoNuevo());
        assertEquals(3L, registro.getIdActor());
        assertEquals("Desactivación de local por el agente", registro.getMotivo());
        assertEquals("DISPONIBILIDAD_PROPIEDAD", disponibilidad.getEntidadTipo());
        assertEquals("D", disponibilidad.getEstadoAnterior());
        assertEquals("T", disponibilidad.getEstadoNuevo());
        // La auditoria hereda el tenant de la entidad auditada, no del actor.
        assertEquals(ORG, registro.getOrganizacionId());
        assertEquals(ORG, disponibilidad.getOrganizacionId());
    }

    // ------------------------------------------------------------------
    // Listado filtrado y resumen: el filtro, el orden, la paginacion y el
    // conteo bajan a SQL. Lo que se comprueba aqui es que el service NO
    // reintroduzca ninguno de ellos en memoria.
    // ------------------------------------------------------------------

    @Test
    void elListadoPideAlMotorElConjuntoYCargaSoloEsaPagina() {
        // La fila se arma ANTES: filaListado() stubbea su propio mock y, dentro
        // de un thenReturn, Mockito lo leeria como stubbing anidado.
        LocalListado fila = filaListado();
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(7L), 137L));
        when(propiedades.buscarPorIds(anyLong(), any())).thenReturn(List.of(fila));
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        Pagina<FichaLocal> pagina = service.listar(new FiltrosLocal("camana", "N", 3, 20), agente);

        verify(propiedades).buscarPorIds(ORG, List.of(7L));
        // El total es el del CONJUNTO, no el de las filas devueltas.
        assertEquals(137, pagina.total());
        assertEquals(1, pagina.items().size());
        assertEquals("LOC-0100", pagina.items().getFirst().codigoLocal());
    }

    /**
     * El criterio es lo unico que distingue a este listado del universal.
     *
     * <p>Lo que se afirma aqui no es "llama al motor" -eso ya lo dice el
     * anterior-, es <b>con que</b>: con el rubro dentro, sin filtros de
     * inmueble, ascendente y con la paginacion del cable. Si alguno de esos
     * cuatro cambiara, {@code /locales} dejaria de responder lo que su contrato
     * promete sin que ninguna otra prueba se enterase.
     */
    @Test
    void elCriterioDeLocalesLlevaElRubroYOrdenaAscendente() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 0L));

        service.listar(new FiltrosLocal("camana", "N", 3, 20), agente);

        ArgumentCaptor<CriterioBusquedaInmobiliaria> criterio =
                ArgumentCaptor.forClass(CriterioBusquedaInmobiliaria.class);
        verify(motor).resolver(criterio.capture());
        CriterioBusquedaInmobiliaria c = criterio.getValue();
        assertEquals(ORG, c.idOrganizacion());
        assertEquals("camana", c.texto());
        assertEquals("N", c.estado());
        assertFalse(c.tieneFiltrosDeInmueble(), "/locales no filtra por tipo ni por operacion");
        assertEquals(OrdenDelListado.ASCENDENTE, c.orden());
        assertEquals(3, c.pagina());
        assertEquals(20, c.tamano());
        // Pagina 3 de 20 => se saltan 40 filas, y eso lo resuelve la base.
        assertEquals(40, c.desplazamiento());
    }

    /** Sin candidatos no se pide ninguna proyeccion: no hay nada que cargar. */
    @Test
    void sinCandidatosNoSePideNingunaProyeccion() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 0L));

        service.listar(new FiltrosLocal("  camana  ", "D", 1, 10), agente);

        verify(propiedades, never()).buscarPorIds(anyLong(), any());
    }

    @Test
    void unFiltroEnBlancoViajaComoNuloParaQueElWhereLoIgnore() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 0L));

        service.listar(new FiltrosLocal("   ", "", 1, 10), agente);

        ArgumentCaptor<CriterioBusquedaInmobiliaria> criterio =
                ArgumentCaptor.forClass(CriterioBusquedaInmobiliaria.class);
        verify(motor).resolver(criterio.capture());
        assertNull(criterio.getValue().texto());
        assertNull(criterio.getValue().estado());
    }

    /**
     * <b>Un estado que no existe es un error de quien llama, no una pagina
     * vacia</b> (2026-09-02).
     *
     * <p>Hasta la normalizacion los dos listados inmobiliarios lo dejaban pasar
     * y contestaban 200 con cero filas, cada uno por su cuenta. Decirle a un
     * cliente "no hay nada que enseñar" cuando lo que pasa es que su filtro no
     * se entendio es una respuesta falsa, y el defecto era transversal: se
     * arregla en la autoridad comun, no en un recurso.
     */
    @Test
    void unEstadoFueraDelVocabularioSeRechaza() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.listar(new FiltrosLocal(null, "ACTIVO", 1, 10), agente));

        assertTrue(error.getMessage().contains("D"), error.getMessage());
        assertTrue(error.getMessage().contains("N"), error.getMessage());
        assertTrue(error.getMessage().contains("I"), error.getMessage());
        // Y no llega a consultar nada: el filtro se rechaza antes de la base.
        verify(motor, never()).resolver(any());
    }

    /** El vocabulario no distingue mayusculas; lo que no admite es otra palabra. */
    @Test
    void elEstadoSeAceptaEnMinusculas() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 0L));

        service.listar(new FiltrosLocal(null, "d", 1, 10), agente);

        ArgumentCaptor<CriterioBusquedaInmobiliaria> criterio =
                ArgumentCaptor.forClass(CriterioBusquedaInmobiliaria.class);
        verify(motor).resolver(criterio.capture());
        assertEquals("D", criterio.getValue().estado());
    }

    /**
     * Un numero fuera de rango se ACOTA, no se rechaza: es lo que este recurso
     * lleva haciendo desde que existe, y es distinto de una palabra que no
     * pertenece a ningun vocabulario.
     */
    @Test
    void elTamanoDePaginaSeAcotaAlTopeDelCable() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 0L));

        service.listar(new FiltrosLocal(null, null, 0, 5000), agente);

        ArgumentCaptor<CriterioBusquedaInmobiliaria> criterio =
                ArgumentCaptor.forClass(CriterioBusquedaInmobiliaria.class);
        verify(motor).resolver(criterio.capture());
        assertEquals(100, criterio.getValue().tamano());
        assertEquals(1, criterio.getValue().pagina());
    }

    @Test
    void unaPaginaVaciaNoConsultaPortadasNiPublicaciones() {
        when(motor.resolver(any())).thenReturn(new ConjuntoDeCandidatos(List.of(), 4L));

        Pagina<FichaLocal> pagina = service.listar(new FiltrosLocal(null, null, 10, 10), agente);

        assertEquals(List.of(), pagina.items());
        assertEquals(4, pagina.total());
        verify(fotos, never()).portadas(any());
        verify(publicaciones, never()).codigosEstadoPublicacion(any());
    }

    @Test
    void elResumenSaleDelGroupByYSuTotalEsLaSumaDeSusPartes() {
        when(motor.contarPorEstadoLegado(any())).thenReturn(Map.of("D", 31L, "N", 11L));

        ResumenLocales resumen = service.resumen("camana", agente);

        assertEquals(31, resumen.disponibles());
        assertEquals(11, resumen.noDisponibles());
        // 'I' no vino en el group by: es cero, no ausente.
        assertEquals(0, resumen.inactivos());
        assertEquals(42, resumen.total());
    }

    /**
     * El KPI y la lista miran el mismo conjunto, y ahora <b>por
     * construccion</b>: mismo motor, mismo criterio y el mismo texto recortado.
     * El estado viaja nulo porque el resumen cuenta los tres cubos, no filtra
     * por uno.
     */
    @Test
    void elResumenUsaElMismoCriterioQueLaListaParaQueCuadren() {
        when(motor.contarPorEstadoLegado(any())).thenReturn(Map.of());

        service.resumen("  camana  ", agente);

        ArgumentCaptor<CriterioBusquedaInmobiliaria> criterio =
                ArgumentCaptor.forClass(CriterioBusquedaInmobiliaria.class);
        verify(motor).contarPorEstadoLegado(criterio.capture());
        assertEquals(ORG, criterio.getValue().idOrganizacion());
        assertEquals("camana", criterio.getValue().texto());
        assertNull(criterio.getValue().estado());
    }

    @Test
    void advierteDuplicadoTecnicoSinBloquearNiCompararOtroPropietario() {
        Propiedad candidato = propiedadExistente();
        candidato.setDireccion("Av. Prueba N.º 123");
        candidato.setInteriorUnidad("Tienda 4");
        candidato.setPiso("1");
        candidato.setMetraje(new BigDecimal("124.90"));
        when(propiedades.findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(ORG, 9L, -1L))
                .thenReturn(List.of(candidato));

        DatosLocal propuesta = new DatosLocal("BORRADOR", "Avenida Prueba 123", "Miraflores",
                new BigDecimal("120.00"), BigDecimal.ZERO, "PEN", "Comercio", null,
                9L, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                "tienda-4", "1", null, null);

        List<PosibleDuplicado> resultado = service.posiblesDuplicados(propuesta, null, agente);

        assertEquals(1, resultado.size());
        assertEquals(7L, resultado.getFirst().id());
        assertEquals(List.of("mismo propietario", "dirección equivalente",
                "misma unidad/interior", "mismo piso", "metraje aproximado"),
                resultado.getFirst().criteriosCoincidentes());
        verify(propiedades).findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(ORG, 9L, -1L);
        verify(propiedades, never()).save(any());
    }

    @Test
    void alEditarExcluyeElRegistroActualDeLosCandidatos() {
        service.posiblesDuplicados(datosValidos(), 7L, agente);

        // datosValidos sí tiene dirección, propietario y metraje: demuestra
        // que al editar se excluye exactamente el registro actual.
        verify(propiedades).findByOrganizacionIdAndRolPropietarioIdAndIdNotOrderById(ORG, 9L, 7L);
    }
    private static LocalListado filaListado() {
        LocalListado fila = mock(LocalListado.class);
        when(fila.getId()).thenReturn(7L);
        when(fila.getCodigoLocal()).thenReturn("LOC-0100");
        when(fila.getDireccion()).thenReturn("Av. Prueba 123");
        when(fila.getDistrito()).thenReturn("Miraflores");
        when(fila.getEstado()).thenReturn("D");
        when(fila.getIdPropietario()).thenReturn(9L);
        when(fila.getPropietarioNombre()).thenReturn("Inmobiliaria Pacifico SAC");
        return fila;
    }

    private Propiedad propiedadExistente() {
        Propiedad propiedad = new Propiedad();
        propiedad.setOrganizacionId(ORG);
        propiedad.setCodigo("LOC-0100");
        propiedad.setDireccion("Av. Prueba 123");
        propiedad.setDistrito("Miraflores");
        propiedad.setMetraje(new BigDecimal("120.00"));
        propiedad.setPrecioReferencial(new BigDecimal("8500.00"));
        propiedad.setRolPropietario(rolPropietario("Inmobiliaria Pacifico SAC"));
        propiedad.iniciarDisponible();
        return conId(propiedad, 7L);
    }

    /** El id lo genera la BD (IDENTITY); en el test se inyecta por reflexion. */
    private static Propiedad conId(Propiedad propiedad, long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(propiedad, "id", id);
        return propiedad;
    }
}
