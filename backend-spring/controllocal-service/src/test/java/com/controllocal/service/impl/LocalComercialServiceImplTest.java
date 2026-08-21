package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DistritoRepository;
import com.controllocal.persistence.repositorio.FotoPropiedadRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
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
import com.controllocal.service.soporte.LectorPorAutoridad;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.ValoresDePropiedad;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final ProspeccionRepository prospeccionesRepo = mock(ProspeccionRepository.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);

    /**
     * Las dos mitades de D-E4-3. Se mockean y no se stubbean con valores porque
     * este test blinda los mensajes y el shaping de /locales, no la resolucion
     * de autoridades: eso lo prueba `AutoridadDelDatoIntegrationTest`, que si
     * pasa por la base.
     */
    private final LectorPorAutoridad lector = mock(LectorPorAutoridad.class);
    private final AtributosGobernados gobierno = mock(AtributosGobernados.class);

    private final LocalComercialServiceImpl service = new LocalComercialServiceImpl(
            propiedades, roles, distritos, fotos, precios, publicaciones, prospecciones,
            captaciones, prospeccionesRepo, new Transiciones(historial), mock(AlertaService.class),
            lector, gobierno);

    /**
     * El lector devuelve "no se sabe nada" en vez de null.
     *
     * <p>No es una comodidad del mock: {@link ValoresDePropiedad#vacio()} es un
     * valor legitimo del dominio -- una propiedad sin ningun gobernado escrito --
     * y este test comprueba precisamente que ese caso no rompe el mapeo. Stubbearlo
     * con valores concretos convertiria este test unitario en una prueba del
     * enrutador, que ya tiene la suya contra PostgreSQL.
     */
    @BeforeEach
    void elLectorNoDevuelveNull() {
        when(lector.de(anyLong(), any())).thenReturn(ValoresDePropiedad.vacio());
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
        when(propiedades.findByOrganizacionIdAndId(ORG, 7L)).thenReturn(Optional.of(propiedad));
        when(prospeccionesRepo.existsByOrganizacionIdAndPropiedadIdAndAgenteId(ORG, 7L, 30L)).thenReturn(true);

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
    void elListadoPasaLosFiltrosALaConsultaYNoFiltraEnMemoria() {
        LocalListado fila = filaListado();
        when(propiedades.buscar(anyLong(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(fila), PageRequest.of(0, 20), 137));
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        Pagina<FichaLocal> pagina = service.listar(new FiltrosLocal(null, "N", 3, 20), agente);

        ArgumentCaptor<Pageable> paginado = ArgumentCaptor.forClass(Pageable.class);
        verify(propiedades).buscar(eq(ORG), eq(null), eq("N"), paginado.capture());
        // Pagina 3 del cable (1-based) = pagina 2 de Spring Data (0-based).
        assertEquals(2, paginado.getValue().getPageNumber());
        assertEquals(20, paginado.getValue().getPageSize());
        // El total es el de la CONSULTA, no el de las filas devueltas.
        assertEquals(137, pagina.total());
        assertEquals(1, pagina.items().size());
        assertEquals("LOC-0100", pagina.items().getFirst().codigoLocal());
    }

    /**
     * Con texto, el listado NO va por el {@code buscar} de siempre: resuelve el
     * conjunto de candidatos (UNION indexable por tabla), pagina EN LA BASE y
     * solo despues carga la proyeccion de esos ids (RC-003).
     */
    @Test
    void conTextoElListadoPaginaElConjuntoDeCandidatosYCargaSoloEsaPagina() {
        // La fila se arma ANTES: filaListado() stubbea su propio mock y, dentro
        // de un thenReturn, Mockito lo leeria como stubbing anidado.
        LocalListado fila = filaListado();
        when(propiedades.idsPorTexto(anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(7L));
        when(propiedades.buscarPorIds(anyLong(), any())).thenReturn(List.of(fila));
        when(propiedades.contarPorTexto(anyLong(), any(), any())).thenReturn(137L);
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        Pagina<FichaLocal> pagina = service.listar(new FiltrosLocal("camana", "N", 3, 20), agente);

        // Pagina 3 de 20 => limite 20, desplazamiento 40, resuelto en SQL.
        verify(propiedades).idsPorTexto(ORG, "camana", "N", 20, 40);
        verify(propiedades).buscarPorIds(ORG, List.of(7L));
        verify(propiedades, never()).buscar(anyLong(), any(), any(), any());
        assertEquals(137, pagina.total());
        assertEquals(1, pagina.items().size());
    }

    /** El total sale del MISMO conjunto que la pagina: no pueden discrepar. */
    @Test
    void elTotalConTextoCuentaElMismoConjuntoQuePaginaConLosMismosArgumentos() {
        when(propiedades.idsPorTexto(anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(propiedades.contarPorTexto(anyLong(), any(), any())).thenReturn(0L);

        service.listar(new FiltrosLocal("  camana  ", "D", 1, 10), agente);

        // Mismo tenant, mismo texto normalizado y mismo estado en las dos.
        verify(propiedades).idsPorTexto(ORG, "camana", "D", 10, 0);
        verify(propiedades).contarPorTexto(ORG, "camana", "D");
        // Sin candidatos no se pide ninguna proyeccion.
        verify(propiedades, never()).buscarPorIds(anyLong(), any());
    }

    @Test
    void unFiltroEnBlancoViajaComoNuloParaQueElWhereLoIgnore() {
        when(propiedades.buscar(anyLong(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        service.listar(new FiltrosLocal("   ", "", 1, 10), agente);

        verify(propiedades).buscar(eq(ORG), eq(null), eq(null), any());
    }

    @Test
    void elTamanoDePaginaSeAcotaAlTopeDelCable() {
        when(propiedades.buscar(anyLong(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        service.listar(new FiltrosLocal(null, null, 0, 5000), agente);

        ArgumentCaptor<Pageable> paginado = ArgumentCaptor.forClass(Pageable.class);
        verify(propiedades).buscar(anyLong(), any(), any(), paginado.capture());
        assertEquals(100, paginado.getValue().getPageSize());
        // Una pagina 0 o negativa cae en la primera, no revienta.
        assertEquals(0, paginado.getValue().getPageNumber());
    }

    @Test
    void unaPaginaVaciaNoConsultaPortadasNiPublicaciones() {
        when(propiedades.buscar(anyLong(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 10), 4));
        when(publicaciones.codigosEstadoPublicacion(any())).thenReturn(Map.of());

        Pagina<FichaLocal> pagina = service.listar(new FiltrosLocal(null, null, 10, 10), agente);

        assertEquals(List.of(), pagina.items());
        assertEquals(4, pagina.total());
        verify(fotos, never()).portadas(any());
        verify(publicaciones, never()).codigosEstadoPublicacion(any());
    }

    @Test
    void elResumenSaleDelGroupByYSuTotalEsLaSumaDeSusPartes() {
        when(propiedades.contarPorEstadoConTexto(ORG, "camana", null))
                .thenReturn(List.of(conteo("D", 31), conteo("N", 11)));

        ResumenLocales resumen = service.resumen("camana", agente);

        assertEquals(31, resumen.disponibles());
        assertEquals(11, resumen.noDisponibles());
        // 'I' no vino en el group by: es cero, no ausente.
        assertEquals(0, resumen.inactivos());
        assertEquals(42, resumen.total());
    }

    @Test
    void elResumenUsaElMismoFiltroDeTextoQueLaListaParaQueCuadren() {
        when(propiedades.contarPorEstadoConTexto(anyLong(), any(), any())).thenReturn(List.of());

        service.resumen("  camana  ", agente);

        // Recortado igual que en el listado y sobre el MISMO conjunto de
        // candidatos; si no, KPI y lista contarian distinto. El estado viaja
        // nulo porque el resumen cuenta los tres cubos, no filtra por uno.
        verify(propiedades).contarPorEstadoConTexto(ORG, "camana", null);
        verify(propiedades, never()).contarPorEstado(anyLong(), any());
    }

    /** Sin texto no hay conjunto que unir: el resumen sigue por el group by simple. */
    @Test
    void elResumenSinTextoNoPasaPorElConjuntoDeCandidatos() {
        when(propiedades.contarPorEstado(anyLong(), any())).thenReturn(List.of());

        service.resumen("   ", agente);

        verify(propiedades).contarPorEstado(ORG, null);
        verify(propiedades, never()).contarPorEstadoConTexto(anyLong(), any(), any());
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
