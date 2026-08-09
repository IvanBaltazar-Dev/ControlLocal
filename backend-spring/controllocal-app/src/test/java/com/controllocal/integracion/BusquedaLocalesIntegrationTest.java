package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate de la busqueda por CONJUNTO DE CANDIDATOS del listado de locales
 * (RC-003). Comprueba contra PostgreSQL real lo que ningun test con mocks
 * puede: que el UNION de las tres ramas devuelve exactamente lo mismo que el
 * OR de siempre —mas el rubro, que es la ampliacion deliberada—, que el total
 * es el del mismo conjunto, que el tenant cierra, que no hay duplicados y que
 * el resultado refleja el dato vivo en cuanto se edita.
 *
 * <p>Todo el fixture vive dentro de la transaccion del test y se deshace al
 * terminar: la base de integracion queda como estaba.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class BusquedaLocalesIntegrationTest {

    private static final long ORG = 1L;

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadRepository propiedades;

    private long idPorCodigo;
    private long idPorDireccion;
    private long idPorDistrito;
    private long idPorRubro;
    private long idPorPropietario;
    private long idPorTodasLasRamas;
    private long idOtroTenant;
    private long rolPropietarioBuscado;

    /**
     * Un local por rama de busqueda, uno que casa por TODAS a la vez y uno en
     * otra organizacion con el mismo texto. El discriminante es un sufijo
     * irrepetible para que el fixture no se cruce con la semilla.
     */
    @BeforeEach
    void fixture() {
        long propietarioSemilla = jdbc.queryForObject(
                "select min(id_persona_rol) from persona_rol where tipo_rol='PROPIETARIO' and organizacion_id=?",
                Long.class, ORG);
        rolPropietarioBuscado = crearPropietario(ORG, "Inversiones ZORZAL SAC");

        idPorCodigo = crearLocal(ORG, "LOC-ZORZAL-1", "Av. Uno 100", "Miraflores",
                "Restaurante", propietarioSemilla);
        idPorDireccion = crearLocal(ORG, "LOC-BUSQ-2", "Calle ZORZAL 200", "Miraflores",
                "Restaurante", propietarioSemilla);
        idPorDistrito = crearLocal(ORG, "LOC-BUSQ-3", "Av. Tres 300", "ZORZAL Alto",
                "Restaurante", propietarioSemilla);
        idPorRubro = crearLocal(ORG, "LOC-BUSQ-4", "Av. Cuatro 400", "Miraflores",
                "Tienda de ZORZAL", propietarioSemilla);
        idPorPropietario = crearLocal(ORG, "LOC-BUSQ-5", "Av. Cinco 500", "Miraflores",
                "Restaurante", rolPropietarioBuscado);
        idPorTodasLasRamas = crearLocal(ORG, "LOC-ZORZAL-6", "Av. ZORZAL 600", "ZORZAL Alto",
                "Rubro ZORZAL", rolPropietarioBuscado);

        long otraOrg = crearOrganizacion("ZORZAL_TEST");
        idOtroTenant = crearLocal(otraOrg, "LOC-ZORZAL-9", "Av. ZORZAL 900", "ZORZAL Alto",
                "Rubro ZORZAL", crearPropietario(otraOrg, "Inversiones ZORZAL SAC"));
    }

    // ------------------------------------------------------------------
    // 1. Misma semantica que la busqueda anterior (mas el rubro)
    // ------------------------------------------------------------------

    /**
     * Para un texto que NO aparece en ningun rubro, el conjunto de candidatos
     * y el OR de siempre tienen que devolver exactamente los mismos ids: la
     * reescritura no cambia lo que el usuario ve.
     */
    @Test
    void devuelveLoMismoQueElOrDeSiempreCuandoElTextoNoTocaElRubro() {
        for (String termino : List.of("zorzal 200", "ZORZAL Alto", "loc-zorzal", "Inversiones ZORZAL")) {
            assertEquals(idsDelOrAnterior(termino), idsDelConjunto(termino),
                    "difieren para el termino '" + termino + "'");
        }
    }

    /** La ampliacion deliberada: el rubro SI entra, y el OR anterior no lo veia. */
    @Test
    void elRubroEsNuevoTerrenoDeBusqueda() {
        List<Long> anterior = idsDelOrAnterior("Tienda de ZORZAL");
        List<Long> ahora = idsDelConjunto("Tienda de ZORZAL");
        assertFalse(anterior.contains(idPorRubro), "el OR anterior no buscaba por rubro");
        assertTrue(ahora.contains(idPorRubro), "el conjunto de candidatos si busca por rubro");
    }

    @Test
    void cadaRamaAportaSuLocalYNingunaSeQuedaFuera() {
        List<Long> ids = idsDelConjunto("ZORZAL");
        assertTrue(ids.containsAll(List.of(idPorCodigo, idPorDireccion, idPorDistrito,
                idPorRubro, idPorPropietario, idPorTodasLasRamas)));
    }

    // ------------------------------------------------------------------
    // 2. El total es el del mismo conjunto que la pagina
    // ------------------------------------------------------------------

    @Test
    void elTotalCoincideConLoQueDevuelvePaginandoElConjuntoEntero() {
        long total = propiedades.contarPorTexto(ORG, "ZORZAL", null);
        List<Long> paginando = idsPaginandoDeDosEnDos("ZORZAL", null);

        assertEquals(total, paginando.size());
        assertEquals(6, total, "los seis del tenant, nunca el del otro");
    }

    @Test
    void elFiltroDeEstadoRecortaIgualLaPaginaYElTotal() {
        jdbc.update("update propiedad set estado_registro='I' where id_propiedad=?", idPorCodigo);

        assertEquals(1, propiedades.contarPorTexto(ORG, "ZORZAL", "I"));
        assertEquals(List.of(idPorCodigo), idsDelConjunto("ZORZAL", "I"));
        assertEquals(5, propiedades.contarPorTexto(ORG, "ZORZAL", "D"));
    }

    /** El KPI del resumen mira ese mismo conjunto, o no cuadraria con la lista. */
    @Test
    void elResumenCuentaElMismoConjuntoQueLaLista() {
        jdbc.update("update propiedad set estado_registro='I' where id_propiedad=?", idPorCodigo);
        jdbc.update("update propiedad set disponibilidad_comercial='A' where id_propiedad=?", idPorDireccion);

        Map<String, Long> porEstado = propiedades.contarPorEstadoConTexto(ORG, "ZORZAL", null).stream()
                .collect(Collectors.toMap(ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));

        assertEquals(1L, porEstado.get("I"));
        assertEquals(1L, porEstado.get("N"));
        assertEquals(4L, porEstado.get("D"));
        assertEquals(propiedades.contarPorTexto(ORG, "ZORZAL", null),
                porEstado.values().stream().mapToLong(Long::longValue).sum());
    }

    // ------------------------------------------------------------------
    // 3. Aislamiento y duplicados
    // ------------------------------------------------------------------

    @Test
    void elLocalDeOtraOrganizacionNoEntraNiEnLaPaginaNiEnElTotal() {
        assertFalse(idsDelConjunto("ZORZAL").contains(idOtroTenant));
        assertEquals(6, propiedades.contarPorTexto(ORG, "ZORZAL", null));
        // Y desde el otro tenant se ve exactamente el suyo, no los seis.
        assertEquals(1, propiedades.contarPorTexto(
                jdbc.queryForObject("select organizacion_id from propiedad where id_propiedad=?",
                        Long.class, idOtroTenant), "ZORZAL", null));
    }

    /**
     * El local que casa por codigo, direccion, distrito, rubro Y propietario a
     * la vez aparece UNA sola vez: por eso el UNION no es UNION ALL.
     */
    @Test
    void casarPorVariasRamasNoDuplicaLaFila() {
        List<Long> ids = idsDelConjunto("ZORZAL");
        assertEquals(1, ids.stream().filter(id -> id == idPorTodasLasRamas).count());
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    // ------------------------------------------------------------------
    // 4. El resultado refleja el dato vivo (no hay nada materializado)
    // ------------------------------------------------------------------

    @Test
    void editarElNombreDelPropietarioCambiaLaBusquedaAlInstante() {
        assertTrue(idsDelConjunto("ZORZAL").contains(idPorPropietario));

        jdbc.update("""
                update persona set nombres_o_razon_social='Inversiones GAVIOTA SAC'
                 where id_persona = (select id_persona from persona_rol where id_persona_rol=?)
                """, rolPropietarioBuscado);

        assertFalse(idsDelConjunto("ZORZAL").contains(idPorPropietario));
        assertTrue(idsDelConjunto("GAVIOTA").contains(idPorPropietario));
    }

    @Test
    void editarCodigoDireccionDistritoORubroCambiaLaBusquedaAlInstante() {
        jdbc.update("update propiedad set codigo='LOC-GAVIOTA-1' where id_propiedad=?", idPorCodigo);
        jdbc.update("update propiedad set direccion='Calle GAVIOTA 200' where id_propiedad=?", idPorDireccion);
        jdbc.update("update propiedad set distrito='GAVIOTA Alto' where id_propiedad=?", idPorDistrito);
        jdbc.update("update detalle_local_comercial set rubro_permitido='Tienda de GAVIOTA' where id_propiedad=?",
                idPorRubro);

        List<Long> gaviota = idsDelConjunto("GAVIOTA");
        assertTrue(gaviota.containsAll(List.of(idPorCodigo, idPorDireccion, idPorDistrito, idPorRubro)));
        List<Long> zorzal = idsDelConjunto("ZORZAL");
        assertFalse(zorzal.contains(idPorCodigo));
        assertFalse(zorzal.contains(idPorDireccion));
        assertFalse(zorzal.contains(idPorDistrito));
        assertFalse(zorzal.contains(idPorRubro));
    }

    // ------------------------------------------------------------------
    // 5. Orden y paginacion
    // ------------------------------------------------------------------

    @Test
    void elOrdenEsPorIdYLasPaginasNiRepitenNiPierdenFilas() {
        List<Long> completo = idsDelConjunto("ZORZAL");
        assertEquals(completo.stream().sorted().toList(), completo, "orden total y estable");
        assertEquals(completo, idsPaginandoDeDosEnDos("ZORZAL", null));
    }

    /** La proyeccion de la pagina se carga SOLO para los ids ya paginados. */
    @Test
    void laProyeccionLlegaCompletaParaLosIdsDeLaPagina() {
        List<Long> ids = propiedades.idsPorTexto(ORG, "ZORZAL", null, 2, 0);
        List<LocalListado> filas = propiedades.buscarPorIds(ORG, ids);

        assertEquals(ids, filas.stream().map(LocalListado::getId).toList());
        assertTrue(filas.stream().allMatch(f -> f.getCodigoLocal() != null
                && f.getPropietarioNombre() != null && f.getEstado() != null));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** El OR anterior, tal cual estaba antes de la reescritura (sin rubro). */
    private List<Long> idsDelOrAnterior(String texto) {
        return jdbc.queryForList("""
                select p.id_propiedad
                  from propiedad p
                  join persona_rol rp on rp.id_persona_rol = p.id_rol_propietario
                  join persona per on per.id_persona = rp.id_persona
                 where p.organizacion_id = ?
                   and (lower(p.codigo)    like lower(concat('%', cast(? as varchar), '%'))
                     or lower(p.direccion) like lower(concat('%', cast(? as varchar), '%'))
                     or lower(p.distrito)  like lower(concat('%', cast(? as varchar), '%'))
                     or lower(per.nombres_o_razon_social) like lower(concat('%', cast(? as varchar), '%')))
                 order by p.id_propiedad
                """, Long.class, ORG, texto, texto, texto, texto);
    }

    private List<Long> idsDelConjunto(String texto) {
        return idsDelConjunto(texto, null);
    }

    private List<Long> idsDelConjunto(String texto, String estado) {
        return propiedades.idsPorTexto(ORG, texto, estado, 1000, 0);
    }

    private List<Long> idsPaginandoDeDosEnDos(String texto, String estado) {
        return java.util.stream.IntStream.range(0, 20)
                .mapToObj(p -> propiedades.idsPorTexto(ORG, texto, estado, 2, p * 2))
                .flatMap(List::stream)
                .toList();
    }

    private long crearOrganizacion(String codigo) {
        return jdbc.queryForObject("""
                insert into organizacion (codigo, nombre) values (?, 'Organizacion de prueba')
                returning id_organizacion
                """, Long.class, codigo);
    }

    private long crearPropietario(long organizacion, String nombre) {
        Long idPersona = jdbc.queryForObject("""
                insert into persona (tipo_persona, tipo_documento, numero_documento,
                                     nombres_o_razon_social, estado, organizacion_id)
                values ('J', 'R', ?, ?, 'A', ?)
                returning id_persona
                """, Long.class, "20" + System.nanoTime() % 100000000L, nombre, organizacion);
        return jdbc.queryForObject("""
                insert into persona_rol (id_persona, tipo_rol, vigencia_desde, organizacion_id)
                values (?, 'PROPIETARIO', current_date, ?)
                returning id_persona_rol
                """, Long.class, idPersona, organizacion);
    }

    private long crearLocal(long organizacion, String codigo, String direccion, String distrito,
                            String rubro, long rolPropietario) {
        Long id = jdbc.queryForObject("""
                insert into propiedad (codigo, direccion, distrito, metraje, precio_referencial,
                                       moneda_referencial, estado_registro, disponibilidad_comercial,
                                       tipo_inmueble, uso, id_rol_propietario, organizacion_id)
                values (?, ?, ?, 100, 5000, 'PEN', 'A', 'D', 'L', 'C', ?, ?)
                returning id_propiedad
                """, Long.class, codigo, direccion, distrito, rolPropietario, organizacion);
        jdbc.update("""
                insert into detalle_local_comercial (id_propiedad, rubro_permitido, organizacion_id)
                values (?, ?, ?)
                """, id, rubro, organizacion);
        return id;
    }
}
