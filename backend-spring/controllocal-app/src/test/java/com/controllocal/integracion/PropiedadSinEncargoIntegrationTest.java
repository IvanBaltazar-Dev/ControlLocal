package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Una propiedad puede existir sin estar encargada</b> (convergencia del
 * Corte 0C, V75).
 *
 * <h2>La contradiccion que cierra</h2>
 * El modelo tenia congelado que la Propiedad es la cosa fisica y que la
 * operacion pertenece al Encargo (D-E4-1). Pero el alta <b>exigia al menos una
 * operacion</b>, asi que toda propiedad nacia con un encargo vivo. Y el embudo
 * comercial de BROX dice lo contrario:
 *
 * <pre>
 *   propietario  ->  PROSPECCION  ->  ENCARGO  ->  PUBLICACION
 *                    (existe para conseguir el encargo)
 * </pre>
 *
 * <p>Si la prospeccion existe para conseguir el encargo, el encargo no puede
 * tener que existir antes de prospectar. Lo destapo la corrida de cierre: al
 * retirar {@code POST /locales} —que registraba el inmueble Y abria una
 * prospeccion— no quedo ninguna entrada para una propiedad que solo se esta
 * prospectando, y {@code uq_captacion_viva_por_operacion} rechazaba el encargo
 * que {@code captar} intentaba crear encima del que el alta ya habia abierto.
 *
 * <h2>La distincion que congela</h2>
 * <blockquote><b>Propiedad registrada != propiedad comercialmente
 * encargada.</b></blockquote>
 *
 * Una propiedad sin encargos esta en el registro maestro y puede prospectarse,
 * acumulando identidad, ubicacion, titularidad, atributos, duplicados e
 * interacciones mientras se intenta captar. Lo que <b>no</b> es: ofrecida. No
 * tiene precio autorizado, no tiene historico economico, no se publica, y no
 * dice estar «disponible» por el hecho de que nada afirme lo contrario.
 *
 * <p>Y no se deduce al reves: <b>propiedad sin encargo no es lo mismo que
 * prospeccion</b>. Son dos cosas que apuntan a la propiedad, no un estado suyo.
 * Por eso ninguna prueba de aqui infiere una de la otra.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PropiedadSinEncargoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired PublicacionService publicaciones;

    // ==================================================================
    // 1. El alta sin operaciones no deja rastro comercial
    // ==================================================================

    /**
     * <b>La prueba que congela la decision.</b> No basta con que el alta no
     * falle: tiene que no CREAR nada de lo que pertenece al encargo.
     */
    @Test
    @DisplayName("registrar sin operaciones crea la propiedad y nada mas")
    void registrarSinOperacionesNoDejaRastroComercial() {
        long id = registrarSinEncargo();

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from propiedad where id_propiedad = ?", Integer.class, id),
                "la propiedad si existe: esta en el registro maestro");
        assertEquals(0, contar("select count(*) from captacion where id_propiedad = ?", id),
                "cero encargos: nadie ha encargado nada todavia");
        assertEquals(0, contar("""
                select count(*) from condicion_economica_captacion ce
                 where exists (select 1 from captacion c
                                where c.id_condicion_economica = ce.id_condicion_economica
                                  and c.id_propiedad = ?)
                """, id),
                "cero condiciones economicas: no hay trato que condicionar");
        assertEquals(0, contar("select count(*) from precio_propiedad where id_propiedad = ?", id),
                "cero historico: el hito 'U' dice «autorizado en un encargo», y no hay encargo");
        assertEquals(0, contar("select count(*) from publicacion where id_propiedad = ?", id),
                "cero anuncios: publicar es una decision, no un efecto de registrar");
    }

    /**
     * El precio referencial es una <b>proyeccion del encargo</b>. Sin encargo no
     * hay de donde proyectarlo, y rellenarlo con cero seria inventar un dato:
     * un local «de 0 soles» entra en cualquier busqueda por precio maximo.
     */
    @Test
    @DisplayName("una propiedad sin encargo no tiene precio autorizado")
    void sinEncargoNoHayPrecio() {
        long id = registrarSinEncargo();
        Map<String, Object> fila = jdbc.queryForMap(
                "select precio_referencial, moneda_referencial from propiedad where id_propiedad = ?",
                id);
        assertNull(fila.get("precio_referencial"),
                "un precio que nadie autorizo no se rellena con cero");
        assertNull(fila.get("moneda_referencial"),
                "y una moneda sin importe no significa nada");
    }

    /**
     * <b>«Disponible» no se deduce del silencio.</b>
     *
     * <p>Estamparla en el alta era exactamente eso: nada decia lo contrario, asi
     * que se afirmaba. Una propiedad que solo se esta prospectando no esta
     * ofrecida — y el listado comercial, que filtra por esta columna, deja de
     * ensenarla sin que haya que ensenarle una excepcion.
     */
    @Test
    @DisplayName("una propiedad sin encargo no dice estar disponible")
    void sinEncargoNoDiceEstarDisponible() {
        long id = registrarSinEncargo();
        assertNull(jdbc.queryForMap(
                        "select disponibilidad_comercial from propiedad where id_propiedad = ?", id)
                        .get("disponibilidad_comercial"),
                "sin encargo no hay oferta, y sin oferta no hay disponibilidad que declarar");
        assertEquals("A", jdbc.queryForObject(
                "select estado_registro from propiedad where id_propiedad = ?", String.class, id),
                "pero el REGISTRO si esta activo: la propiedad existe y se puede trabajar");
    }

    /**
     * <b>Una propiedad sin encargo no se puede publicar, y ahora es ESTRUCTURAL.</b>
     *
     * <p>Esto se probaba llamando a {@code publicaciones.crear(idPropiedad, ...)},
     * que aceptaba publicar nombrando solo el inmueble y se defendia con
     * {@code exigirAlgunEncargo}. Ese metodo <b>se retiro</b> en el microcorte de
     * las puertas de publicacion --creaba anuncios sin pasar por
     * {@code exigirPublicable}-- y con el desaparecio la unica firma que aceptaba
     * un {@code idPropiedad}.
     *
     * <p>La garantia no se perdio: <b>se hizo mas fuerte</b>. Ya no hay un metodo
     * que lo rechace en tiempo de ejecucion; no hay metodo al que pedirselo. Toda
     * creacion pasa por {@code crearEnEncargo(idEncargo, ...)}, y sin encargo no
     * hay id que pasarle.
     *
     * <p>Se deja constancia aqui --y no solo en el test de arquitectura-- porque
     * lo que hay que conservar es la REGLA, no el metodo que la implementaba.
     */
    @Test
    @DisplayName("una propiedad sin encargo no se puede publicar: no hay por donde")
    void sinEncargoNoSePublica() {
        long id = registrarSinEncargo();
        assertTrue(propiedades.consultar(id, actor()).encargos().isEmpty(),
                "el caso necesita una propiedad sin ningun encargo");

        // Ninguna firma publica acepta publicar nombrando solo el inmueble.
        List<String> porInmueble = Arrays.stream(PublicacionService.class.getMethods())
                .filter(m -> m.getName().equals("crear") || m.getName().equals("sincronizar"))
                .map(Method::getName)
                .toList();
        assertEquals(List.of(), porInmueble,
                "volvio a existir una via que publica por inmueble, sin encargo que la autorice");

        // Y la unica que crea exige el ENCARGO, no la propiedad.
        assertTrue(Arrays.stream(PublicacionService.class.getMethods())
                        .anyMatch(m -> m.getName().equals("crearEnEncargo")),
                "la via canonica tiene que seguir existiendo");
    }

    /** La ficha de una propiedad sin encargos se lee, y ensena que no los tiene. */
    @Test
    @DisplayName("la ficha de una propiedad sin encargo se lee y no inventa ninguno")
    void laFichaSeLeeSinEncargos() {
        long id = registrarSinEncargo();
        var ficha = propiedades.consultar(id, actor());
        assertNotNull(ficha);
        assertTrue(ficha.encargos().isEmpty(), "no hay encargos que ensenar");
        assertTrue(ficha.historia() == null || ficha.historia().linea().isEmpty(),
                "ni historia economica que resumir");
        assertTrue(ficha.atributosQueFaltan() != null,
                "y lo que falta se sigue pudiendo preguntar");
    }

    // ==================================================================
    // 2. Con operaciones, el alta se comporta exactamente igual que antes
    // ==================================================================

    @Test
    @DisplayName("con una operacion, el alta sigue abriendo su encargo con todo lo suyo")
    void conOperacionesElAltaNoCambia() {
        long id = registrarConAlquiler();

        assertEquals(1, contar("select count(*) from captacion where id_propiedad = ?", id),
                "un encargo, como siempre");
        assertEquals(1, contar("""
                select count(*) from precio_propiedad
                 where id_propiedad = ? and hito = 'U'
                """, id),
                "y su hito 'U': el importe autorizado al abrirlo");
        assertNotNull(jdbc.queryForMap(
                        "select precio_referencial from propiedad where id_propiedad = ?", id)
                        .get("precio_referencial"),
                "la proyeccion heredada se sigue escribiendo cuando hay de donde");
        assertEquals("D", jdbc.queryForObject(
                "select disponibilidad_comercial from propiedad where id_propiedad = ?",
                String.class, id),
                "y la propiedad si se declara disponible, porque ya se esta ofreciendo");
    }

    // ==================================================================
    // 3. El Encargo nace al captar, con la operacion declarada
    // ==================================================================

    @Test
    @DisplayName("prospectar una propiedad no le abre ningun encargo")
    void prospectarNoAbreEncargo() {
        long id = registrarSinEncargo();
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(id, "Prospeccion de prueba"),
                actor());
        assertEquals(0, contar("select count(*) from captacion where id_propiedad = ?", id),
                "la prospeccion es la INTENCION del agente; el encargo lo da el propietario");
    }

    /**
     * <b>Aqui nace el Encargo.</b> Y con su operacion dicha, no recuperada de un
     * defecto: el proyecto ya tuvo que retirar precisamente esas inferencias.
     */
    @Test
    @DisplayName("captar con operacion explicita abre el encargo, su condicion y su hito")
    void captarAbreElEncargo() {
        long id = registrarSinEncargo();
        long idProspeccion = prospectar(id);

        prospecciones.captar(idProspeccion, capturaDe("ALQUILER", new BigDecimal("2500"), "PEN"),
                actor());

        assertEquals(1, contar("select count(*) from captacion where id_propiedad = ?", id));
        assertEquals("A", jdbc.queryForObject(
                "select motivo_operacion from captacion where id_propiedad = ?", String.class, id),
                "la operacion que se declaro, no la que se suponia");
        assertEquals(1, contar("""
                select count(*) from condicion_economica_captacion ce
                 where exists (select 1 from captacion c
                                where c.id_condicion_economica = ce.id_condicion_economica
                                  and c.id_propiedad = ?)
                """, id),
                "el encargo nace con su condicion economica");
        assertEquals(0, new BigDecimal("2500").compareTo(jdbc.queryForObject("""
                select ce.importe_referencia from condicion_economica_captacion ce
                  join captacion c on c.id_condicion_economica = ce.id_condicion_economica
                 where c.id_propiedad = ?
                """, BigDecimal.class, id)),
                "con el importe que se pacto al captar, no con el de la propiedad");
    }

    @Test
    @DisplayName("captar sin decir la operacion se rechaza")
    void captarSinOperacionSeRechaza() {
        long id = registrarSinEncargo();
        long idProspeccion = prospectar(id);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> prospecciones.captar(idProspeccion,
                        capturaDe(null, new BigDecimal("2500"), "PEN"), actor()));
        assertTrue(error.getMessage().contains("VENTA") && error.getMessage().contains("ALQUILER"),
                "el error tiene que decir cuales son, no solo que falta: " + error.getMessage());
        assertEquals(0, contar("select count(*) from captacion where id_propiedad = ?", id),
                "y no dejar nada escrito");
    }

    @Test
    @DisplayName("captar sin importe se rechaza: el precio lo trae el encargo")
    void captarSinImporteSeRechaza() {
        long id = registrarSinEncargo();
        long idProspeccion = prospectar(id);

        assertThrows(ReglaNegocioException.class,
                () -> prospecciones.captar(idProspeccion, capturaDe("ALQUILER", null, "PEN"),
                        actor()),
                "una propiedad sin encargo no tiene precio del que tirar");
    }

    // ==================================================================
    // 4. Y el indice unico vuelve a significar lo correcto
    // ==================================================================

    /**
     * {@code uq_captacion_viva_por_operacion} se conserva tal cual. Antes
     * estorbaba —toda propiedad nacia con un encargo vivo, asi que captar
     * chocaba siempre—; ahora vuelve a defender lo que decia: <b>un encargo vivo
     * por operacion</b>.
     */
    @Test
    @DisplayName("un segundo encargo vivo de la misma operacion se rechaza")
    void unSegundoEncargoVivoDeLaMismaOperacionSeRechaza() {
        long id = registrarSinEncargo();
        prospecciones.captar(prospectar(id), capturaDe("ALQUILER", new BigDecimal("2500"), "PEN"),
                actor());

        long segunda = prospectar(id);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> prospecciones.captar(segunda,
                        capturaDe("ALQUILER", new BigDecimal("2800"), "PEN"), actor()));
        assertTrue(error.getMessage().contains("ALQUILER"),
                "el error nombra la operacion ocupada: " + error.getMessage());
        assertEquals(1, contar("select count(*) from captacion where id_propiedad = ?", id),
                "y sigue habiendo uno solo");
    }

    @Test
    @DisplayName("la OTRA operacion si se puede abrir, y es un encargo independiente")
    void laOtraOperacionSiSePuedeAbrir() {
        long id = registrarSinEncargo();
        prospecciones.captar(prospectar(id), capturaDe("ALQUILER", new BigDecimal("2500"), "PEN"),
                actor());
        prospecciones.captar(prospectar(id), capturaDe("VENTA", new BigDecimal("350000"), "USD"),
                actor());

        assertEquals(2, contar("select count(*) from captacion where id_propiedad = ?", id));
        List<String> operaciones = jdbc.queryForList("""
                select motivo_operacion from captacion where id_propiedad = ?
                 order by motivo_operacion
                """, String.class, id);
        assertEquals(List.of("A", "V"), operaciones);

        List<BigDecimal> importes = jdbc.queryForList("""
                select ce.importe_referencia from condicion_economica_captacion ce
                  join captacion c on c.id_condicion_economica = ce.id_condicion_economica
                 where c.id_propiedad = ? order by c.motivo_operacion
                """, BigDecimal.class, id);
        assertEquals(2, importes.size());
        assertNotEquals(0, importes.get(0).compareTo(importes.get(1)),
                "dos episodios, dos economias: el alquiler no lleva el precio de venta");
    }

    // ==================================================================
    // La invariante que ata las dos mitades
    // ==================================================================

    /**
     * <b>Toda propiedad con un encargo vivo declara su disponibilidad.</b>
     *
     * <p>Es la otra mitad de la regla, y hace falta escribirla: las pruebas de
     * arriba comprueban que sin encargo NO se declara, y ninguna comprobaria
     * que con encargo SI. Sin esta, una regresion que dejara de llamar a
     * «entrar en oferta» pasaria en verde y la propiedad captada de verdad
     * desapareceria del matcher de cartera y de las coincidencias, que exigen
     * disponibilidad {@code 'D'}.
     *
     * <p>Se mide sobre los datos REALES del repositorio, no sobre un caso
     * fabricado: una invariante que solo se comprueba en su propio fixture
     * comprueba el fixture.
     */
    @Test
    @DisplayName("invariante: una propiedad con encargo vivo nunca se queda sin disponibilidad")
    void conEncargoVivoSiempreHayDisponibilidad() {
        List<Long> mudas = jdbc.queryForList("""
                select distinct p.id_propiedad
                  from propiedad p
                  join captacion c on c.id_propiedad = p.id_propiedad
                 where c.estado in ('P', 'O', 'A')
                   and p.disponibilidad_comercial is null
                """, Long.class);
        assertTrue(mudas.isEmpty(),
                "estas propiedades tienen un encargo vivo y no dicen si se ofrecen: " + mudas
                        + ". Abrir el encargo es lo que las pone en el mercado; sin eso "
                        + "desaparecen del matcher aunque esten captadas.");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private long registrarSinEncargo() {
        return alta(List.of());
    }

    private long registrarConAlquiler() {
        return alta(List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                null, null, null, null, null, null, null)));
    }

    private long alta(List<OperacionSolicitada> operaciones) {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, "LOCAL", null,
                "Prospectada, todavia sin encargo",
                new Ubicacion("Av. Sin Encargo "
                        + java.util.UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                // `tipo_acceso` es ALT en LOCAL desde V81, y ALT es obligatorio
                // en el ALTA: sin el, `registrar` corta antes de llegar a lo que
                // esta clase prueba. Se registra el dato; no se relaja la regla.
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE")),
                operaciones, null), actor).idPropiedad();
    }

    private long prospectar(long idPropiedad) {
        return prospecciones.registrar(
                new ProspeccionService.DatosProspeccion(idPropiedad, "Intento de captacion"),
                actor()).id();
    }

    private static ProspeccionService.DatosCaptura capturaDe(String operacion, BigDecimal importe,
                                                             String moneda) {
        return new ProspeccionService.DatosCaptura(operacion, importe, moneda,
                new BigDecimal("100"), null, null, null, null, null, null);
    }

    private PublicacionService.DatosPublicacion publicacionDePrueba() {
        return new PublicacionService.DatosPublicacion("WEB_PROPIA", null, new BigDecimal("2500"),
                "PEN", "Anuncio de prueba", null, null);
    }

    private int contar(String sql, Object... parametros) {
        Integer n = jdbc.queryForObject(sql, Integer.class, parametros);
        return n == null ? 0 : n;
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), "AGENTE");
    }
}
