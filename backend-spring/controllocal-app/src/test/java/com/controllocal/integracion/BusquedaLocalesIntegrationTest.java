package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.query.LocalListado;
import com.controllocal.persistence.busqueda.ConjuntoDeCandidatos;
import com.controllocal.persistence.busqueda.CriterioBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.MotorBusquedaInmobiliaria;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.soporte.FiltrosDeListadoInmobiliario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadRepository propiedades;
    @Autowired MotorBusquedaInmobiliaria motor;

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

    /**
     * <b>La rama del rubro es CANONICA, no de {@code /locales}</b> (C0-a,
     * 2026-09-02).
     *
     * <p>Durante unas horas el motor la ofrecio como configuracion por recurso y
     * el listado universal salio sin ella. Estaba mal: desde V71
     * {@code rubro_permitido} vive en {@code atributo_propiedad} como atributo
     * gobernado de PROPIEDAD, asi que buscarlo es buscar la cartera. Con la rama
     * fuera, un almacen o una oficina buscados por su rubro eran invisibles en
     * el unico listado que el producto usa de verdad.
     */
    @Test
    void elRubroEntraTambienEnElListadoUniversal() {
        List<Long> universal = motor.resolver(FiltrosDeListadoInmobiliario.dePropiedades(
                ORG, "Tienda de ZORZAL", null, null, null, false, false, 1, 100, 100)).ids();

        assertTrue(universal.contains(idPorRubro),
                "el listado universal tiene que encontrar por rubro lo mismo que el heredado");
        assertEquals(idsDelConjunto("Tienda de ZORZAL"), universal.stream().sorted().toList(),
                "y encontrar exactamente lo mismo: la rama es una, no una por recurso");
    }

    /**
     * <b>Control negativo: una rama que no aplica no inventa candidatos.</b>
     *
     * <p>Es la otra mitad de la correccion. Que el rubro entre en el motor
     * universal no puede significar que una propiedad a la que el rubro NO le
     * aplica gane un dato o aparezca por una rama que no le corresponde. Se
     * comprueba por los dos lados: el terreno no sale al buscar el rubro, y la
     * base <b>rechaza</b> escribirle uno -{@code exigir_atributo_gobernado} solo
     * admite {@code rubro_permitido} en A, L y O-, que es lo que garantiza que
     * el caso no pueda existir en produccion.
     */
    @Test
    void unTipoSinRubroNiGanaDatoNiAparecePorEsaRama() {
        Long idTerreno = jdbc.queryForObject("""
                insert into propiedad (codigo, direccion, distrito, metraje, estado_registro,
                                       disponibilidad_comercial, tipo_inmueble, uso,
                                       id_rol_propietario, organizacion_id)
                values ('LOC-ZORZAL-T', 'Av. ZORZAL 700', 'ZORZAL Alto', 500, 'A', 'D', 'T', 'C', ?, ?)
                returning id_propiedad
                """, Long.class, rolPropietarioBuscado, ORG);

        assertTrue(idsDelConjunto("ZORZAL").contains(idTerreno),
                "el terreno si entra por direccion y distrito, que si le aplican");
        assertFalse(idsDelConjunto("Tienda de ZORZAL").contains(idTerreno),
                "pero NO por el rubro, que no le aplica");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into atributo_propiedad (id_propiedad, clave, valor_texto, organizacion_id)
                values (?, 'rubro_permitido', 'Tienda de ZORZAL', ?)
                """, idTerreno, ORG),
                "la base tiene que rechazar un rubro sobre un tipo que no lo admite");
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
        long total = totalDelConjunto("ZORZAL", null);
        List<Long> paginando = idsPaginandoDeDosEnDos("ZORZAL", null);

        assertEquals(total, paginando.size());
        assertEquals(6, total, "los seis del tenant, nunca el del otro");
    }

    @Test
    void elFiltroDeEstadoRecortaIgualLaPaginaYElTotal() {
        jdbc.update("update propiedad set estado_registro='I' where id_propiedad=?", idPorCodigo);

        assertEquals(1, totalDelConjunto("ZORZAL", "I"));
        assertEquals(List.of(idPorCodigo), idsDelConjunto("ZORZAL", "I"));
        assertEquals(5, totalDelConjunto("ZORZAL", "D"));
    }

    /** El KPI del resumen mira ese mismo conjunto, o no cuadraria con la lista. */
    @Test
    void elResumenCuentaElMismoConjuntoQueLaLista() {
        jdbc.update("update propiedad set estado_registro='I' where id_propiedad=?", idPorCodigo);
        jdbc.update("update propiedad set disponibilidad_comercial='A' where id_propiedad=?", idPorDireccion);

        Map<String, Long> porEstado = motor.contarPorEstadoLegado(criterio("ZORZAL", null, 1, 1));

        assertEquals(1L, porEstado.get("I"));
        assertEquals(1L, porEstado.get("N"));
        assertEquals(4L, porEstado.get("D"));
        assertEquals(totalDelConjunto("ZORZAL", null),
                porEstado.values().stream().mapToLong(Long::longValue).sum());
    }

    // ------------------------------------------------------------------
    // 3. Aislamiento y duplicados
    // ------------------------------------------------------------------

    @Test
    void elLocalDeOtraOrganizacionNoEntraNiEnLaPaginaNiEnElTotal() {
        assertFalse(idsDelConjunto("ZORZAL").contains(idOtroTenant));
        assertEquals(6, totalDelConjunto("ZORZAL", null));
        // Y desde el otro tenant se ve exactamente el suyo, no los seis.
        long orgVecina = jdbc.queryForObject(
                "select organizacion_id from propiedad where id_propiedad=?", Long.class, idOtroTenant);
        assertEquals(1, motor.resolver(FiltrosDeListadoInmobiliario.deLocales(
                orgVecina, "ZORZAL", null, 1, 1, 1000)).total());
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
        jdbc.update("""
                update atributo_propiedad set valor_texto='Tienda de GAVIOTA'
                 where clave='rubro_permitido' and id_propiedad=?
                """, idPorRubro);

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
        ConjuntoDeCandidatos candidatos = motor.resolver(criterio("ZORZAL", null, 2, 1));
        List<LocalListado> filas = propiedades.buscarPorIds(ORG, candidatos.ids());

        assertEquals(candidatos.ids().stream().sorted().toList(),
                filas.stream().map(LocalListado::getId).sorted().toList(),
                "se cargan exactamente los ids de la pagina, ni uno mas ni uno menos");
        assertTrue(filas.stream().allMatch(f -> f.getCodigoLocal() != null
                && f.getPropietarioNombre() != null && f.getEstado() != null));
    }

    /**
     * <b>El cargador de la proyeccion NO promete orden, y por eso el conjunto lo
     * restituye</b> (2026-09-02).
     *
     * <p>No es una precaucion teorica: al normalizar la busqueda, esta misma
     * prueba se puso roja devolviendo {@code [117867, 117866]} donde el motor
     * habia pedido {@code [117866, 117867]}. Un {@code where id in (...)} deja
     * el orden a lo que le convenga al plan, y con la pagina cargada asi el
     * listado universal —que publica {@code id DESC}— habria empezado a
     * devolver sus propias filas al reves en cuanto el planificador cambiara de
     * idea. Es el tipo de defecto que con seis filas de fixture no se reproduce.
     */
    @Test
    void elConjuntoRestituyeElOrdenQuePidioElMotor() {
        ConjuntoDeCandidatos candidatos = motor.resolver(criterio("ZORZAL", null, 6, 1));
        List<LocalListado> desordenadas = propiedades.buscarPorIds(ORG, candidatos.ids());

        List<Long> ordenadas = candidatos.ordenadas(desordenadas, LocalListado::getId)
                .stream().map(LocalListado::getId).toList();

        assertEquals(candidatos.ids(), ordenadas,
                "las filas tienen que salir en el orden que decidio el motor");
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
        return motor.resolver(criterio(texto, estado, 1000, 1)).ids();
    }

    private List<Long> idsPaginandoDeDosEnDos(String texto, String estado) {
        return java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(p -> motor.resolver(criterio(texto, estado, 2, p)).ids())
                .flatMap(List::stream)
                .toList();
    }

    /**
     * El criterio de {@code /locales}: con la rama del rubro y ascendente. Se
     * construye con la MISMA autoridad que usa el servicio, no a mano: si el
     * recurso cambiara de configuracion, este gate mediria otra cosa sin
     * enterarse.
     */
    private static CriterioBusquedaInmobiliaria criterio(String texto, String estado,
                                                         int tamano, int pagina) {
        return FiltrosDeListadoInmobiliario.deLocales(ORG, texto, estado, pagina, tamano, 1000);
    }

    private long totalDelConjunto(String texto, String estado) {
        return motor.resolver(criterio(texto, estado, 1, 1)).total();
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
        // El rubro es un atributo gobernado desde V71: la busqueda lo encuentra
        // por su autoridad, no por una tabla por tipo.
        jdbc.update("""
                insert into atributo_propiedad (id_propiedad, clave, valor_texto, organizacion_id)
                values (?, 'rubro_permitido', ?, ?)
                """, id, rubro, organizacion);
        return id;
    }
}
