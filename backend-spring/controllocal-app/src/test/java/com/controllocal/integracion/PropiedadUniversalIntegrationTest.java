package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.ResultadoRegistro;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Propiedad Universal Operativa + Captura v1, contra PostgreSQL real</b>
 * (D-E4-1, D-E4-2).
 *
 * <h2>Por que estas pruebas COMETEN de verdad</h2>
 * Las demas pruebas de integracion del proyecto son {@code @Transactional} y se
 * deshacen solas. Aqui no se puede, y no es comodidad: cuatro de las invariantes
 * que se comprueban <b>solo existen en el COMMIT</b>.
 * <ul>
 *   <li>las cuotas de una copropiedad las vigila un CONSTRAINT TRIGGER
 *       <b>diferido</b> (V47): con la transaccion abierta no ha corrido todavia;</li>
 *   <li>la idempotencia consiste en que un SEGUNDO comando encuentre lo que el
 *       primero dejo — dentro de una transaccion que se deshace, no hay
 *       primero;</li>
 *   <li>el rollback de un alta fallida no se puede observar desde dentro de la
 *       transaccion que se va a deshacer igual;</li>
 *   <li>venta y alquiler vivos a la vez chocan contra indices unicos parciales,
 *       que se evaluan al escribir pero cuya utilidad se ve al releer.</li>
 * </ul>
 *
 * <h2>Por eso: tenant propio</h2>
 * {@link #prepararTenants()} construye —o reutiliza— dos organizaciones de
 * prueba y borra lo que la corrida anterior dejo. Nada de esto toca el tenant de
 * desarrollo, y la corrida es repetible: es la misma leccion que costo el
 * simulacro de recuperacion, que fallaba en la segunda ejecucion porque leia el
 * estado de una base compartida en vez de construir el suyo.
 *
 * <p>El <b>segundo</b> tenant no es decorativo: sin el, "el aislamiento
 * funciona" es una afirmacion sin prueba.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PropiedadUniversalIntegrationTest {

    private static final String CODIGO_TENANT_A = "E2E-UNIVERSAL-A";
    private static final String CODIGO_TENANT_B = "E2E-UNIVERSAL-B";

    /** La pantalla: una persona tecleando, sin agente ni conversacion detras. */
    private static final Procedencia PANTALLA = Procedencia.deLaPantalla();
    /**
     * Un agente automatico conversando por WhatsApp. Exige agente, conversacion
     * y turno: sin los tres no se puede ni construir.
     */
    private static final Procedencia CONVERSANDO = Procedencia.deAgente(
            "WHATSAPP", "KAIROS", "modelo-de-prueba", "1.0",
            "conv-universal", "turno-1", "wamid-1",
            "registra el local de Torres en alquiler");

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;

    private Actor agenteA;
    private Actor agenteB;
    private long propietarioAna;
    private long propietarioCarlos;
    private long propietarioB;

    // ==================================================================
    // Fixture
    // ==================================================================

    @BeforeEach
    void prepararTenants() {
        long organizacionA = tenant(CODIGO_TENANT_A);
        long organizacionB = tenant(CODIGO_TENANT_B);

        limpiar(organizacionA);
        limpiar(organizacionB);

        agenteA = new Actor(organizacionA, personaDe(organizacionA, "agente"),
                agente(organizacionA, "Agente Universal", "40000001"), Actor.AGENTE);
        agenteB = new Actor(organizacionB, personaDe(organizacionB, "agente"),
                agente(organizacionB, "Agente del otro tenant", "40000002"), Actor.AGENTE);

        propietarioAna = propietario(organizacionA, "Ana Torres", "41000001");
        propietarioCarlos = propietario(organizacionA, "Carlos Torres", "41000002");
        propietarioB = propietario(organizacionB, "Titular del otro tenant", "41000003");
    }

    // ==================================================================
    // Los siete tipos y las dos operaciones
    // ==================================================================

    @Test
    @DisplayName("local + alquiler + un titular: el negocio de siempre, por el modelo nuevo")
    void localEnAlquiler() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2900"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "85.5"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals("L", ficha.tipoPropiedad());
        assertEquals(1, ficha.titulares().size());
        assertEquals(0, new BigDecimal("100").compareTo(ficha.titulares().get(0).cuota()),
                "un titular unico es el 100 % sin tener que declararlo");
        assertTrue(ficha.titulares().get(0).representante());
        assertEquals(1, ficha.encargos().size());
        assertEquals("ALQUILER", ficha.encargos().get(0).operacion());
        assertEquals(0, new BigDecimal("2900").compareTo(ficha.encargos().get(0).importe()));
    }

    @Test
    @DisplayName("departamento + venta: la venta existe de verdad, no como alquiler disfrazado")
    void departamentoEnVenta() {
        ResultadoRegistro alta = propiedades.registrar(comando("DEPARTAMENTO", "VENTA",
                new BigDecimal("180000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals("D", ficha.tipoPropiedad());
        assertEquals("VENTA", ficha.encargos().get(0).operacion());
        assertEquals(0, new BigDecimal("180000").compareTo(ficha.encargos().get(0).importe()));

        // Y en la BD el hito quedo en la serie de VENTA, no en la de alquiler.
        assertEquals("V", jdbc.queryForObject(
                "select operacion from precio_propiedad where id_propiedad = ? order by id_precio limit 1",
                String.class, alta.idPropiedad()),
                "el defecto de alquiler ya no existe: la operacion viaja hasta el hito");
    }

    @Test
    @DisplayName("casa + alquiler: segundo tipo, con sus propios obligatorios")
    void casaEnAlquiler() {
        ResultadoRegistro alta = propiedades.registrar(comando("CASA", "ALQUILER",
                new BigDecimal("4500"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "210"),
                        new ValorAtributo("dormitorios", "4"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertEquals("C", ficha.tipoPropiedad());
        assertEquals("V", ficha.uso(), "una casa es vivienda: el uso se deduce del tipo, no es comercial");
    }

    @Test
    @DisplayName("terreno + venta: pide zonificacion y NO pide dormitorios")
    void terrenoEnVenta() {
        ResultadoRegistro alta = propiedades.registrar(comando("TERRENO", "VENTA",
                new BigDecimal("95000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "300"),
                        new ValorAtributo("zonificacion", "RDM"),
                        new ValorAtributo("area_terreno", "300"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertTrue(ficha.atributos().stream().anyMatch(a -> "zonificacion".equals(a.clave())));

        List<String> preguntas = propiedades.catalogoDe("TERRENO", agenteA).stream()
                .map(PropiedadUniversalService.PreguntaCatalogo::clave).toList();
        assertTrue(preguntas.contains("zonificacion"), "el catalogo pregunta la zonificacion de un terreno");
        assertFalse(preguntas.contains("dormitorios"), "y no le pregunta dormitorios");
    }

    @Test
    @DisplayName("copropiedad 60/40: dos titulares, un representante, cuotas que suman 100")
    void copropiedad() {
        ResultadoRegistro alta = propiedades.registrar(comando("DEPARTAMENTO", "VENTA",
                new BigDecimal("180000"), "USD",
                List.of(new Titular(propietarioAna, new BigDecimal("60"), true),
                        new Titular(propietarioCarlos, new BigDecimal("40"), null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals(2, ficha.titulares().size());
        assertEquals(0, new BigDecimal("60").compareTo(ficha.titulares().get(0).cuota()));
        assertEquals(0, new BigDecimal("40").compareTo(ficha.titulares().get(1).cuota()));
        assertEquals(1, ficha.titulares().stream().filter(
                PropiedadUniversalService.TitularFicha::representante).count(),
                "exactamente uno es con quien se habla");
    }

    @Test
    @DisplayName("un reparto que no suma 100 se rechaza ANTES de escribir nada")
    void cuotasQueNoSuman() {
        long antes = cuantasPropiedades(agenteA.idOrganizacion());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("DEPARTAMENTO", "VENTA", new BigDecimal("180000"), "USD",
                        List.of(new Titular(propietarioAna, new BigDecimal("60"), true),
                                new Titular(propietarioCarlos, new BigDecimal("30"), null)),
                        List.of(new ValorAtributo("metraje_total", "118"),
                                new ValorAtributo("dormitorios", "3"))), agenteA));

        assertTrue(error.getMessage().contains("90"),
                "el mensaje dice cuanto suman, no solo que estan mal: " + error.getMessage());
        assertEquals(antes, cuantasPropiedades(agenteA.idOrganizacion()));
    }

    // ==================================================================
    // La universalidad de verdad: venta Y alquiler sobre la misma propiedad
    // ==================================================================

    @Test
    @DisplayName("venta y alquiler simultaneos: dos encargos, dos precios, dos historicos")
    void ventaYAlquilerALaVez() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "DEPARTAMENTO", null,
                "Disponible para venta o alquiler",
                new Ubicacion("Av. Larco 1234", "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("180000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("2900"), "USD",
                                null, null, null, null, null, null, null)),
                null);

        ResultadoRegistro alta = propiedades.registrar(comando, agenteA);
        assertEquals(2, alta.idsEncargos().size(), "una operacion, un encargo");

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertEquals(2, ficha.encargos().size());

        EncargoFicha venta = encargo(ficha, "VENTA");
        EncargoFicha alquiler = encargo(ficha, "ALQUILER");
        assertEquals(0, new BigDecimal("180000").compareTo(venta.importe()));
        assertEquals(0, new BigDecimal("2900").compareTo(alquiler.importe()));

        // Lo que de verdad prueba la universalidad: cada encargo tiene SU serie.
        assertEquals(1, venta.historico().size(), "el historico de la venta no ve el del alquiler");
        assertEquals(1, alquiler.historico().size());
        assertEquals(0, new BigDecimal("180000").compareTo(venta.historico().get(0).monto()));
        assertEquals(0, new BigDecimal("2900").compareTo(alquiler.historico().get(0).monto()));
    }

    @Test
    @DisplayName("dos veces la misma operacion se rechaza: no son dos encargos, es un error")
    void dosVecesLaMismaOperacion() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "LOCAL", null, null,
                new Ubicacion("Jr. Union 100", "Lima", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2000"), "PEN",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                                null, null, null, null, null, null, null)),
                null);

        assertThrows(ReglaNegocioException.class, () -> propiedades.registrar(comando, agenteA));
    }

    @Test
    @DisplayName("AMBAS y COMPRA no existen, y el error dice por que")
    void vocabularioCongelado() {
        ReglaNegocioException ambas = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("LOCAL", "AMBAS", new BigDecimal("2000"), "PEN",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80"))), agenteA));
        assertTrue(ambas.getMessage().contains("DOS encargos"),
                "el mensaje tiene que ensenar la alternativa: " + ambas.getMessage());

        ReglaNegocioException compra = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("LOCAL", "COMPRA", new BigDecimal("2000"), "PEN",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80"))), agenteA));
        assertTrue(compra.getMessage().contains("perspectiva"),
                "comprar es VENTA vista desde el cliente: " + compra.getMessage());
    }

    // ==================================================================
    // Atributos gobernados
    // ==================================================================

    @Test
    @DisplayName("un atributo que no aplica al tipo se rechaza")
    void atributoNoAplicable() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("TERRENO", "VENTA", new BigDecimal("95000"), "USD",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "300"),
                                new ValorAtributo("zonificacion", "RDM"),
                                new ValorAtributo("dormitorios", "3"))), agenteA));

        assertTrue(error.getMessage().contains("TERRENO"), error.getMessage());
    }

    @Test
    @DisplayName("un atributo obligatorio ausente se declara faltante, con su nombre")
    void atributoObligatorioAusente() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("DEPARTAMENTO", "VENTA", new BigDecimal("180000"), "USD",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "118"))), agenteA));

        assertTrue(error.getMessage().contains("dormitorios"),
                "tiene que decir QUE falta: " + error.getMessage());
    }

    @Test
    @DisplayName("una clave que no esta en el catalogo no se guarda")
    void claveInventada() {
        assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("LOCAL", "ALQUILER", new BigDecimal("2000"), "PEN",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80"),
                                new ValorAtributo("tiene_jacuzzi_de_lujo", "si"))), agenteA));
    }

    @Test
    @DisplayName("una organizacion no puede redefinir un atributo comun de BROX")
    void elCatalogoComunNoSeSombrea() {
        // `dormitorios` es NUMERO en el catalogo comun. Un tenant que lo
        // declarara TEXTO romperia la comparabilidad entre corredoras, que es
        // el activo entero del catalogo.
        assertThrows(Exception.class, () -> jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, del_sistema)
                values (?, 'dormitorios', 'Dormitorios del tenant', 'TEXTO', false)
                """, agenteA.idOrganizacion()));

        assertThrows(Exception.class,
                () -> jdbc.update("delete from catalogo_atributo where clave = 'dormitorios' and del_sistema"),
                "un atributo comun tampoco se borra");

        assertThrows(Exception.class, () -> jdbc.update(
                "update catalogo_atributo set tipo_dato = 'TEXTO' where clave = 'dormitorios' and del_sistema"),
                "ni se le cambia el tipo");
    }

    @Test
    @DisplayName("una clave propia del tenant si se admite, y se puede usar")
    void elTenantPuedeAnadirLoSuyo() {
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, aplica_todos, del_sistema)
                values (?, 'vista_al_mar', 'Vista al mar', 'BOOLEANO', true, false)
                """, agenteA.idOrganizacion());

        ResultadoRegistro alta = propiedades.registrar(comando("DEPARTAMENTO", "VENTA",
                new BigDecimal("240000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "130"),
                        new ValorAtributo("dormitorios", "3"),
                        new ValorAtributo("vista_al_mar", "si"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertTrue(ficha.atributos().stream()
                .anyMatch(a -> "vista_al_mar".equals(a.clave()) && "true".equals(a.valor())));

        // Y el tenant de al lado no lo ve.
        assertFalse(propiedades.catalogoDe("DEPARTAMENTO", agenteB).stream()
                        .anyMatch(p -> "vista_al_mar".equals(p.clave())),
                "un atributo privado no cruza la frontera del tenant");
    }

    // ==================================================================
    // Aislamiento
    // ==================================================================

    @Test
    @DisplayName("el tenant B no lee ni edita una propiedad del tenant A: para el no existe")
    void aislamientoEntreTenants() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2000"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80"))), agenteA);

        // 404 y no 403: decir "existe pero no puedes" ya seria filtrar la
        // cartera del vecino.
        assertThrows(NoEncontradoException.class,
                () -> propiedades.consultar(alta.idPropiedad(), agenteB));
        assertThrows(NoEncontradoException.class,
                () -> propiedades.editar(alta.idPropiedad(),
                        new ComandoEdicion(null, PANTALLA, "intento del vecino", null, null, null, null),
                        agenteB));

        // Y un titular de otro tenant tampoco sirve dentro del propio.
        assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(comando("LOCAL", "ALQUILER", new BigDecimal("2000"), "PEN",
                        List.of(new Titular(propietarioB, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80"))), agenteA));
    }

    // ==================================================================
    // Idempotencia
    // ==================================================================

    @Test
    @DisplayName("el mismo comando dos veces produce UNA propiedad y devuelve la misma")
    void idempotencia() {
        String clave = UUID.randomUUID().toString();
        ComandoRegistro comando = new ComandoRegistro(clave, CONVERSANDO, null, "LOCAL", null, null,
                new Ubicacion("Av. Arequipa 500", "Lince", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2000"), "PEN",
                        null, null, null, null, null, null, null)),
                null);

        long antes = cuantasPropiedades(agenteA.idOrganizacion());
        ResultadoRegistro primera = propiedades.registrar(comando, agenteA);
        ResultadoRegistro segunda = propiedades.registrar(comando, agenteA);

        assertFalse(primera.reintento());
        assertTrue(segunda.reintento(), "el segundo intento se reconoce como reintento");
        assertEquals(primera.idPropiedad(), segunda.idPropiedad());
        assertEquals(primera.codigo(), segunda.codigo());
        assertEquals(primera.idsEncargos(), segunda.idsEncargos(),
                "el reintento devuelve LO MISMO, no un 409");
        assertEquals(antes + 1, cuantasPropiedades(agenteA.idOrganizacion()),
                "una sola propiedad, no dos");
    }

    @Test
    @DisplayName("la misma clave con otro contenido se rechaza: no es un reintento")
    void claveReutilizadaParaOtraCosa() {
        String clave = UUID.randomUUID().toString();
        propiedades.registrar(conClave(clave, comando("LOCAL", "ALQUILER", new BigDecimal("2000"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80")))), agenteA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                propiedades.registrar(conClave(clave, comando("LOCAL", "ALQUILER",
                        new BigDecimal("9999"), "PEN",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80")))), agenteA));

        assertTrue(error.getMessage().contains("clave nueva"), error.getMessage());
    }

    // ==================================================================
    // Todo o nada
    // ==================================================================

    @Test
    @DisplayName("un fallo al final del alta no deja media propiedad")
    void rollbackCompleto() {
        long propiedadesAntes = cuantasPropiedades(agenteA.idOrganizacion());
        long titularidadesAntes = cuantas("titularidad_propiedad", agenteA.idOrganizacion());
        long atributosAntes = cuantas("atributo_propiedad", agenteA.idOrganizacion());
        long eventosAntes = cuantas("evento_dominio", agenteA.idOrganizacion());

        // `tipo_comision` solo admite E, P o F. La propiedad, sus titulares y
        // sus atributos ya estan escritos cuando esto estalla al insertar la
        // condicion economica: es exactamente el fallo "al final" que la
        // transaccion tiene que deshacer entero.
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "LOCAL", null, null,
                new Ubicacion("Av. Brasil 800", "Magdalena", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2000"), "PEN",
                        "Z", null, null, null, null, null, null)),
                null);

        assertThrows(Exception.class, () -> propiedades.registrar(comando, agenteA));

        assertEquals(propiedadesAntes, cuantasPropiedades(agenteA.idOrganizacion()),
                "la propiedad no puede haber sobrevivido al fallo de su encargo");
        assertEquals(titularidadesAntes, cuantas("titularidad_propiedad", agenteA.idOrganizacion()));
        assertEquals(atributosAntes, cuantas("atributo_propiedad", agenteA.idOrganizacion()));
        assertEquals(eventosAntes, cuantas("evento_dominio", agenteA.idOrganizacion()),
                "y tampoco puede quedar un evento anunciando un alta que no ocurrio");
    }

    // ==================================================================
    // Crear -> leer -> editar -> leer
    // ==================================================================

    @Test
    @DisplayName("el ejemplo de aceptacion completo, con el historico intacto")
    void crearLeerEditarLeer() {
        ResultadoRegistro alta = propiedades.registrar(new ComandoRegistro(null, PANTALLA, null,
                "DEPARTAMENTO", null, null,
                new Ubicacion("Av. Pardo 620", "Miraflores", null, null, null, "801", "8", null, null),
                List.of(new Titular(propietarioAna, new BigDecimal("60"), true),
                        new Titular(propietarioCarlos, new BigDecimal("40"), null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("180000"), "USD",
                        null, null, null, null, null, null, null)),
                null), agenteA);

        // --- leer: exactamente lo que se escribio, por las estructuras nuevas
        FichaPropiedadUniversal antes = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertEquals("Miraflores", antes.ubicacion().distrito());
        assertEquals(2, antes.titulares().size());
        assertEquals("3", atributo(antes, "dormitorios"));
        assertEquals("118", atributo(antes, "metraje_total"));
        assertEquals(0, new BigDecimal("180000").compareTo(antes.encargos().get(0).importe()));

        // --- editar el precio: 180 000 -> 175 000
        propiedades.editar(alta.idPropiedad(), new ComandoEdicion(null, PANTALLA, null, null, null, null,
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("175000"), "USD",
                        null, null, null, null, null, null, null))), agenteA);

        // --- leer: el precio nuevo manda y el anterior SIGUE AHI
        FichaPropiedadUniversal despues = propiedades.consultar(alta.idPropiedad(), agenteA);
        EncargoFicha venta = encargo(despues, "VENTA");
        assertEquals(0, new BigDecimal("175000").compareTo(venta.importe()));
        assertEquals(2, venta.historico().size(), "el historico es append-only: dos hitos, no uno");
        assertEquals(0, new BigDecimal("180000").compareTo(venta.historico().get(0).monto()),
                "el precio de salida es el que mide cuanto cedio el titular; no se sobrescribe");
        assertEquals(0, new BigDecimal("175000").compareTo(venta.historico().get(1).monto()));
        assertEquals("U", venta.historico().get(0).hito());
        assertEquals("U", venta.historico().get(1).hito());
    }

    @Test
    @DisplayName("editar los titulares cierra los anteriores en vez de borrarlos")
    void laHistoriaDeTitularidadNoSePierde() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2000"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "80"))), agenteA);

        propiedades.editar(alta.idPropiedad(), new ComandoEdicion(null, PANTALLA, null, null,
                List.of(new Titular(propietarioCarlos, null, true)), null, null), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertEquals(1, ficha.titulares().size());
        assertEquals(propietarioCarlos, ficha.titulares().get(0).idRolPropietario());

        Long historicas = jdbc.queryForObject(
                "select count(*) from titularidad_propiedad where id_propiedad = ?",
                Long.class, alta.idPropiedad());
        assertEquals(2L, historicas, "la titularidad anterior se cierra, no se borra");
    }

    // ==================================================================
    // Trazabilidad
    // ==================================================================

    @Test
    @DisplayName("el agente queda en el evento, y el canal por el que hablo, distinguibles de la pantalla")
    void trazabilidadDelOrigen() {
        ResultadoRegistro porKairos = propiedades.registrar(
                conOrigen(CONVERSANDO, comando("LOCAL", "ALQUILER", new BigDecimal("2000"), "PEN",
                        List.of(new Titular(propietarioAna, null, null)),
                        List.of(new ValorAtributo("metraje_total", "80")))), agenteA);

        Map<String, Object> evento = jdbc.queryForMap("""
                select tipo, canal, agente, entidad_tipo, entidad_id, id_persona_rol, carga_util
                  from evento_dominio
                 where entidad_tipo = 'PROPIEDAD' and entidad_id = ?
                """, porKairos.idPropiedad());

        assertEquals("PROPIEDAD_REGISTRADA", evento.get("tipo"));
        assertEquals("WHATSAPP", evento.get("canal"), "por donde entro");
        assertEquals("KAIROS", evento.get("agente"), "quien la formulo; null seria una persona");
        assertEquals(agenteA.idRolOperativo(), ((Number) evento.get("id_persona_rol")).longValue(),
                "el actor es siempre una persona; el canal y el agente dicen como llego");
        assertTrue(evento.get("carga_util").toString().contains("idsEncargos"));

        // El encargo tambien deja su evento, con la misma procedencia.
        assertEquals(1L, (long) jdbc.queryForObject("""
                select count(*) from evento_dominio
                 where tipo = 'ENCARGO_ABIERTO' and agente = 'KAIROS' and organizacion_id = ?
                   and entidad_id = ?
                """, Long.class, agenteA.idOrganizacion(), porKairos.idsEncargos().get(0)));
    }

    // ==================================================================
    // Captura
    // ==================================================================

    @Test
    @DisplayName("el recorrido completo: el motor pregunta, el borrador aguanta y el alta ejecuta")
    void borradorInterrumpidoYRetomado() {
        // 1. Empiezo. Todavia no se nada, asi que lo primero es el tipo: es lo
        //    que decide todo lo demas.
        MotorDeCaptura.EstadoCaptura vacio =
                captura.avanzar(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, Map.of(), CONVERSANDO, agenteA);
        assertNotNull(vacio.idBorrador());
        assertEquals(GuionRegistroPropiedad.TIPO_PROPIEDAD, vacio.siguiente().clave());
        assertFalse(vacio.listoParaEjecutar());

        // 2. Departamento + venta + Miraflores + 180 000. BROX consulta el
        //    catalogo y ya sabe que le falta -- incluidos los dormitorios, que
        //    no habria pedido para un terreno.
        MotorDeCaptura.EstadoCaptura parcial = captura.avanzar(null, vacio.idBorrador(), Map.of(
                GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO",
                GuionRegistroPropiedad.OPERACION, "VENTA",
                GuionRegistroPropiedad.DISTRITO, "Miraflores",
                GuionRegistroPropiedad.IMPORTE, "180000",
                GuionRegistroPropiedad.MONEDA, "USD"), CONVERSANDO, agenteA);

        assertTrue(parcial.faltante().contains(GuionRegistroPropiedad.TITULARES));
        assertTrue(parcial.faltante().contains(GuionRegistroPropiedad.DIRECCION));
        assertTrue(parcial.faltante().contains("dormitorios"),
                "el catalogo decide que falta, no una lista escrita en el cliente");
        assertTrue(parcial.faltante().contains("metraje_total"));
        assertFalse(parcial.listoParaEjecutar());

        // 3. Se pierde el contexto del modelo. BROX conserva donde se quedo:
        //    otra sesion, por otro canal, lo recupera entero.
        MotorDeCaptura.EstadoCaptura recuperado = captura.consultar(vacio.idBorrador(), agenteA);
        assertEquals("DEPARTAMENTO", recuperado.conocido().get(GuionRegistroPropiedad.TIPO_PROPIEDAD));
        assertEquals("VENTA", recuperado.conocido().get(GuionRegistroPropiedad.OPERACION));
        assertEquals(parcial.faltante(), recuperado.faltante());

        // 4. Se completa lo que falta, ahora desde la pantalla.
        MotorDeCaptura.EstadoCaptura completo = captura.avanzar(null, vacio.idBorrador(), Map.of(
                GuionRegistroPropiedad.TITULARES, propietarioAna + ":60," + propietarioCarlos + ":40",
                GuionRegistroPropiedad.DIRECCION, "Av. Pardo 620",
                "metraje_total", "118",
                "dormitorios", "3"), PANTALLA, agenteA);

        assertTrue(completo.faltante().isEmpty());
        assertTrue(completo.listoParaEjecutar(), "ya hay suficiente para ejecutar el caso de uso");
        assertNull(completo.siguiente());

        // 5. Ejecuta.
        MotorDeCaptura.Ejecucion ejecucion =
                captura.ejecutar(vacio.idBorrador(), UUID.randomUUID().toString(), PANTALLA, agenteA);
        assertNotNull(ejecucion.idPropiedad());

        FichaPropiedadUniversal ficha = propiedades.consultar(ejecucion.idPropiedad(), agenteA);
        assertEquals("D", ficha.tipoPropiedad());
        assertEquals(2, ficha.titulares().size());
        assertEquals("VENTA", ficha.encargos().get(0).operacion());
        assertEquals(0, new BigDecimal("180000").compareTo(ficha.encargos().get(0).importe()));

        // 6. El borrador queda cerrado y dice que produjo, con su canal.
        MotorDeCaptura.EstadoCaptura cerrado = captura.consultar(vacio.idBorrador(), agenteA);
        assertEquals("J", cerrado.estado());
        assertEquals(ejecucion.idPropiedad(), cerrado.idEntidad());
        assertEquals("PROPIEDAD", cerrado.entidadTipo());
        assertEquals("SPA", cerrado.canal(), "se queda el ultimo canal que escribio");
    }

    @Test
    @DisplayName("el motor no deja ejecutar a medias, y dice exactamente que falta")
    void noSeEjecutaAMedias() {
        MotorDeCaptura.EstadoCaptura parcial = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "TERRENO"), CONVERSANDO, agenteA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> captura.ejecutar(parcial.idBorrador(), null, PANTALLA, agenteA));

        assertTrue(error.getMessage().contains("zonificacion"),
                "un terreno exige zonificacion, y el motor lo sabe por el catalogo: "
                        + error.getMessage());
        assertFalse(error.getMessage().contains("dormitorios"),
                "y no pide dormitorios de un terreno: " + error.getMessage());
    }

    @Test
    @DisplayName("un valor invalido se rechaza al ANOTARLO, no al final del recorrido")
    void seValidaAlEntrar() {
        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO"), PANTALLA, agenteA);

        assertThrows(ReglaNegocioException.class, () -> captura.avanzar(null, borrador.idBorrador(),
                Map.of("dormitorios", "unos cuantos"), PANTALLA, agenteA));
        assertThrows(ReglaNegocioException.class, () -> captura.avanzar(null, borrador.idBorrador(),
                Map.of(GuionRegistroPropiedad.OPERACION, "AMBAS"), PANTALLA, agenteA));
    }

    // ==================================================================
    // Utilidades del fixture
    // ==================================================================

    private ComandoRegistro comando(String tipo, String operacion, BigDecimal importe, String moneda,
                                    List<Titular> titulares, List<ValorAtributo> atributos) {
        return new ComandoRegistro(null, PANTALLA, null, tipo, null, null,
                new Ubicacion("Av. de prueba 123", "Miraflores", null, null, null, null, null, null, null),
                titulares, atributos,
                List.of(new OperacionSolicitada(operacion, importe, moneda,
                        null, null, null, null, null, null, null)),
                null);
    }

    private static ComandoRegistro conClave(String clave, ComandoRegistro base) {
        return new ComandoRegistro(clave, base.procedencia(), base.codigo(), base.tipoPropiedad(),
                base.uso(), base.descripcion(), base.ubicacion(), base.titulares(), base.atributos(),
                base.operaciones(), base.idBorrador());
    }

    private static ComandoRegistro conOrigen(Procedencia origen, ComandoRegistro base) {
        return new ComandoRegistro(base.claveIdempotencia(), origen, base.codigo(),
                base.tipoPropiedad(), base.uso(), base.descripcion(), base.ubicacion(),
                base.titulares(), base.atributos(), base.operaciones(), base.idBorrador());
    }

    private static EncargoFicha encargo(FichaPropiedadUniversal ficha, String operacion) {
        return ficha.encargos().stream()
                .filter(e -> operacion.equals(e.operacion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay encargo de " + operacion));
    }

    private static String atributo(FichaPropiedadUniversal ficha, String clave) {
        return ficha.atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(PropiedadUniversalService.AtributoFicha::valor)
                .findFirst()
                .orElse(null);
    }

    private static void assertNull(Object valor) {
        assertTrue(valor == null, "se esperaba null y llego " + valor);
    }

    private long cuantasPropiedades(long idOrganizacion) {
        return cuantas("propiedad", idOrganizacion);
    }

    private long cuantas(String tabla, long idOrganizacion) {
        Long total = jdbc.queryForObject(
                "select count(*) from " + tabla + " where organizacion_id = ?", Long.class, idOrganizacion);
        return total == null ? 0 : total;
    }

    // ------------------------------------------------------------------

    private long tenant(String codigo) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select id_organizacion from organizacion where codigo = ?", codigo);
        if (!filas.isEmpty()) {
            return ((Number) filas.get(0).get("id_organizacion")).longValue();
        }
        return jdbc.queryForObject("""
                insert into organizacion (codigo, nombre, estado) values (?, ?, 'A')
                returning id_organizacion
                """, Long.class, codigo, "Tenant de pruebas " + codigo);
    }

    /**
     * Borra lo que la corrida anterior dejo, en orden de dependencia. <b>Se
     * limpia ANTES y no despues</b>: si una corrida murio a mitad —que es
     * justo cuando esto importa— su limpieza final nunca llego a ejecutarse.
     *
     * <p>Solo toca los dos tenants de prueba. El de desarrollo no se roza.
     */
    private void limpiar(long idOrganizacion) {
        jdbc.update("delete from evento_dominio where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from comando_idempotente where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from borrador_captura where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from precio_propiedad where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from atributo_propiedad where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from titularidad_propiedad where organizacion_id = ?", idOrganizacion);
        jdbc.update("""
                delete from historial_estado where organizacion_id = ?
                """, idOrganizacion);
        // La condicion economica cuelga de la captacion por FK: primero se
        // sueltan las referencias, luego se borran las dos.
        jdbc.update("update captacion set id_condicion_economica = null where organizacion_id = ?",
                idOrganizacion);
        jdbc.update("delete from captacion where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from condicion_economica_captacion where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from prospeccion where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from propiedad where organizacion_id = ?", idOrganizacion);
        jdbc.update("delete from catalogo_atributo where organizacion_id = ?", idOrganizacion);
    }

    /** Persona + rol; devuelve el id de PERSONA. */
    private long personaDe(long idOrganizacion, String sufijo) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select p.id_persona from persona p
                 where p.organizacion_id = ? and p.correo = ?
                """, idOrganizacion, sufijo + "-" + idOrganizacion + "@e2e.test");
        if (!filas.isEmpty()) {
            return ((Number) filas.get(0).get("id_persona")).longValue();
        }
        return jdbc.queryForObject("""
                insert into persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                                     nombres_o_razon_social, correo, estado)
                values (?, 'N', 'D', ?, ?, ?, 'A')
                returning id_persona
                """, Long.class, idOrganizacion, "99" + idOrganizacion + sufijo.hashCode() % 100000,
                "Persona " + sufijo, sufijo + "-" + idOrganizacion + "@e2e.test");
    }

    /**
     * Rol AGENTE + {@code detalle_agente}; devuelve el id de persona_rol, que es
     * el mismo del detalle.
     *
     * <p><b>La busqueda va contra {@code detalle_agente} y no contra
     * {@code persona_rol}</b>: cada sentencia de {@code JdbcTemplate} confirma
     * por su cuenta, asi que una corrida que creara el rol y fallara al crear
     * el detalle dejaria un rol huerfano — y buscar por rol lo daria por bueno
     * en todas las corridas siguientes, con el detalle sin existir nunca.
     */
    private long agente(long idOrganizacion, String nombre, String documento) {
        long idPersona = personaDe(idOrganizacion, "agente");
        List<Map<String, Object>> conDetalle = jdbc.queryForList("""
                select d.id_persona_rol from detalle_agente d
                  join persona_rol r on r.id_persona_rol = d.id_persona_rol
                 where d.organizacion_id = ? and r.id_persona = ?
                """, idOrganizacion, idPersona);
        if (!conDetalle.isEmpty()) {
            return ((Number) conDetalle.get(0).get("id_persona_rol")).longValue();
        }

        List<Map<String, Object>> soloRol = jdbc.queryForList("""
                select id_persona_rol from persona_rol
                 where organizacion_id = ? and id_persona = ? and tipo_rol = 'AGENTE'
                   and vigencia_hasta is null
                """, idOrganizacion, idPersona);
        Long idRol = soloRol.isEmpty()
                ? jdbc.queryForObject("""
                        insert into persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
                        values (?, ?, 'AGENTE', current_date)
                        returning id_persona_rol
                        """, Long.class, idOrganizacion, idPersona)
                : ((Number) soloRol.get(0).get("id_persona_rol")).longValue();

        // `estado_operativo` admite D (disponible), L (licencia) y N (no
        // activo). No hay 'A': el vocabulario de este catalogo no es el de
        // `estado`, aunque las dos columnas lleven una sola letra.
        jdbc.update("""
                insert into detalle_agente (id_persona_rol, organizacion_id, codigo_agente,
                                            fecha_ingreso, estado_operativo)
                values (?, ?, ?, current_date, 'D')
                """, idRol, idOrganizacion, "AG-" + idRol);
        return idRol;
    }

    /** Persona + rol PROPIETARIO; devuelve el id de persona_rol. */
    private long propietario(long idOrganizacion, String nombre, String documento) {
        String correo = documento + "@e2e.test";
        List<Map<String, Object>> existentes = jdbc.queryForList("""
                select r.id_persona_rol from persona_rol r
                  join persona p on p.id_persona = r.id_persona
                 where r.organizacion_id = ? and p.correo = ? and r.tipo_rol = 'PROPIETARIO'
                   and r.vigencia_hasta is null
                """, idOrganizacion, correo);
        if (!existentes.isEmpty()) {
            return ((Number) existentes.get(0).get("id_persona_rol")).longValue();
        }
        List<Map<String, Object>> personas = jdbc.queryForList(
                "select id_persona from persona where organizacion_id = ? and correo = ?",
                idOrganizacion, correo);
        Long idPersona = personas.isEmpty()
                ? jdbc.queryForObject("""
                        insert into persona (organizacion_id, tipo_persona, tipo_documento,
                                             numero_documento, nombres_o_razon_social, correo, estado)
                        values (?, 'N', 'D', ?, ?, ?, 'A')
                        returning id_persona
                        """, Long.class, idOrganizacion, documento, nombre, correo)
                : ((Number) personas.get(0).get("id_persona")).longValue();
        return jdbc.queryForObject("""
                insert into persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
                values (?, ?, 'PROPIETARIO', current_date)
                returning id_persona_rol
                """, Long.class, idOrganizacion, idPersona);
    }
}
