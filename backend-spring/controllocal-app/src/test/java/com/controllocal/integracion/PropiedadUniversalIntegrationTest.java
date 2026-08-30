package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.EpisodiosDeOperacion;
import com.controllocal.service.PropiedadUniversalService.HitoDeLaHistoria;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.ResultadoRegistro;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.PublicacionService.DatosPublicacion;
import com.controllocal.service.PublicacionService.FichaPublicacion;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;
    @Autowired PublicacionService publicaciones;

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

        assertEquals("LOCAL", ficha.tipoPropiedad(),
                "la ficha publica el NOMBRE del tipo, no el codigo de almacenamiento");
        assertEquals("Local comercial", ficha.tipoRotulo(),
                "y su rotulo, para que el cliente no traduzca (D-A-1)");
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

        assertEquals("DEPARTAMENTO", ficha.tipoPropiedad());
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
        assertEquals("CASA", ficha.tipoPropiedad());
        assertEquals("V", ficha.uso(), "una casa es vivienda: el uso se deduce del tipo, no es comercial");
    }

    @Test
    @DisplayName("terreno + venta: pide zonificacion y NO pide dormitorios")
    void terrenoEnVenta() {
        ResultadoRegistro alta = propiedades.registrar(comando("TERRENO", "VENTA",
                new BigDecimal("95000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                // `area_terreno` SALE de esta alta con `V85` (D-7): para un
                // TERRENO nombraba la misma verdad que `metraje_total` --que ya
                // esta aqui arriba con el mismo 300-- y el catalogo ya no la
                // admite sobre `T`. Dejarla haria que esta alta se rechazara con
                // `check_violation`, que es el efecto buscado y no un fallo.
                List.of(new ValorAtributo("metraje_total", "300"),
                        new ValorAtributo("zonificacion", "RDM"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertTrue(ficha.atributos().stream().anyMatch(a -> "zonificacion".equals(a.clave())));

        // La definicion sale del motor, que desde 0B es el UNICO productor de
        // "que se pregunta para este tipo". Sin operaciones: la cosa fisica.
        List<String> preguntas = captura
                .definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", null, agenteA)
                .delTipo().stream().map(MotorDeCaptura.Pregunta::clave).toList();
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

    /**
     * <b>La prueba que rompe un {@code groupBy(operacion)}.</b>
     *
     * <p>Venta + alquiler no la detecta: con un encargo de cada, agrupar por
     * operacion y listar por id dan el mismo resultado. Lo que la detecta es la
     * historia -- tres alquileres sucesivos sobre la misma propiedad --, que el
     * modelo permite: {@code uq_captacion_viva_por_operacion} (V50) prohibe dos
     * <b>vivos</b> de la misma operacion, no que hayan existido varios.
     *
     * <p>Agrupados por operacion serian un bloque con tres precios dentro y una
     * sola linea temporal sin significado. Son tres bloques, y cada historico
     * es el de su encargo.
     */
    @Test
    @DisplayName("tres alquileres sucesivos son TRES encargos con TRES historicos, no uno agrupado")
    void variosEncargosHistoricosDeLaMismaOperacion() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);

        // El encargo vivo pasa a cerrado y entran dos anteriores, ya cerrados:
        // es la historia que una propiedad de verdad acumula.
        long vigente = alta.idsEncargos().get(0);
        long anterior2025 = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");
        long anterior2024 = encargoCerrado(alta.idPropiedad(), "2024-02-10",
                new BigDecimal("2200"), "PEN");

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals(3, ficha.encargos().size(),
                "tres encargos de ALQUILER son tres bloques; agrupar por operacion dejaria uno");
        assertEquals(Set.of(vigente, anterior2025, anterior2024),
                ficha.encargos().stream().map(EncargoFicha::idEncargo)
                        .collect(java.util.stream.Collectors.toSet()),
                "la identidad del bloque es el idEncargo");

        // Cada serie es la suya. Con el filtro por OPERACION en vez de por
        // encargo, los tres verian los tres hitos.
        for (EncargoFicha encargo : ficha.encargos()) {
            assertEquals(1, encargo.historico().size(),
                    "el historico del encargo " + encargo.idEncargo()
                            + " se contamino con el de otro alquiler");
        }
        assertEquals(0, new BigDecimal("2600").compareTo(porId(ficha, vigente).historico().get(0).monto()));
        assertEquals(0, new BigDecimal("2400").compareTo(porId(ficha, anterior2025).historico().get(0).monto()));
        assertEquals(0, new BigDecimal("2200").compareTo(porId(ficha, anterior2024).historico().get(0).monto()));

        // Y el que sigue en juego se distingue del que ya no, sin esconder ninguno.
        assertTrue(porId(ficha, vigente).vivo());
        assertFalse(porId(ficha, anterior2025).vivo());
        assertEquals(vigente, ficha.encargos().get(0).idEncargo(), "los vivos van primero");
    }

    /**
     * Un encargo cerrado <b>no desaparece de la ficha</b>: es el unico sitio
     * donde vive su historico economico, y esconderlo borraria una serie entera
     * sin decir que existe. El listado si se queda con los vivos -- su pregunta
     * es "que hay en cartera", no "que ha pasado con esta propiedad".
     */
    @Test
    @DisplayName("el encargo cerrado sigue en la ficha, y fuera del listado")
    void elEncargoCerradoNoSeEsconde() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        // Cerrar exige fecha y motivo: lo impone ck_captacion_cierre, y es
        // correcto -- un encargo cerrado sin decir cuando ni por que no es un
        // cierre, es un estado perdido.
        jdbc.update("""
                update captacion set estado = 'C', fecha_cierre = current_date, motivo_cierre = 'A'
                 where id_captacion = ?
                """, alta.idsEncargos().get(0));

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        assertEquals(1, ficha.encargos().size(), "la ficha conserva el encargo cerrado");
        assertFalse(ficha.encargos().get(0).vivo());
        assertEquals(1, ficha.encargos().get(0).historico().size(),
                "y con el, su historico economico");

        Pagina<PropiedadUniversalService.FilaPropiedad> cartera = propiedades.listar(
                new PropiedadUniversalService.FiltrosPropiedad(null, null, null, null, null, 1, 20),
                agenteA);
        PropiedadUniversalService.FilaPropiedad fila = cartera.items().stream()
                .filter(f -> f.id().equals(alta.idPropiedad()))
                .findFirst()
                .orElseThrow();
        assertTrue(fila.encargos().isEmpty(), "el listado solo ensena lo vivo");
    }

    /**
     * <b>El read model llega listo para leerse.</b> Cada codigo viaja con su
     * rotulo: sin ellos, la unica forma de escribir «Local comercial», «Activa»
     * o «renta mensual» es una tabla de traduccion en cada interfaz, y con dos
     * interfaces serian dos que se separan (D-A-1 §6).
     */
    @Test
    @DisplayName("la ficha publica los rotulos: el cliente no traduce ningun codigo")
    void laFichaLlegaListaParaLeerse() {
        ResultadoRegistro alta = propiedades.registrar(comando("DEPARTAMENTO", "VENTA",
                new BigDecimal("320000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals("DEPARTAMENTO", ficha.tipoPropiedad());
        assertEquals("Departamento", ficha.tipoRotulo());
        assertEquals("Vivienda", ficha.usoRotulo(), "un departamento es vivienda, no comercial");
        assertNotNull(ficha.estadoRegistroRotulo());
        assertNotNull(ficha.disponibilidadRotulo());

        EncargoFicha venta = encargo(ficha, "VENTA");
        assertEquals("Venta", venta.operacionRotulo());
        assertEquals("Pendiente de revision", venta.estadoRotulo(),
                "el encargo nace pendiente: el agente registra, el broker decide");
        // El nombre del importe lo decide la operacion, no la pantalla: "renta"
        // en una ficha de venta es un error de bulto.
        assertEquals("precio de venta", venta.importeRotulo());
        assertNotNull(venta.agenteNombre(), "la ficha dice quien lleva el encargo");
        assertNotNull(venta.exclusividad(), "se escribia y no se devolvia");
    }

    /**
     * El metraje aparece <b>una sola vez</b>, entre los atributos y con su clave
     * logica. Si la ficha ganara ademas un campo suelto, la pantalla tendria que
     * excluirlo de la lista para no ensenarlo dos veces -- y esa exclusion es el
     * primer eslabon de una coleccion de excepciones por clave.
     */
    @Test
    @DisplayName("el metraje no se publica dos veces")
    void elMetrajeApareceUnaSolaVez() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2900"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "85.5"))), agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals(1, ficha.atributos().stream()
                        .filter(a -> "metraje_total".equals(a.clave())).count(),
                "el metraje viaja una vez, con su clave logica (D-E4-3)");
        assertEquals(0, java.util.Arrays.stream(FichaPropiedadUniversal.class.getRecordComponents())
                        .filter(componente -> "metraje".equals(componente.getName())).count(),
                "y sin un campo `metraje` suelto que obligue a excluirlo de la lista");
    }

    /**
     * Lo que falta llega <b>con su nombre</b>. Con la clave desnuda, decir "no
     * se puede publicar sin el metraje" obligaria al cliente a traducir
     * `metraje_total`, que es la matriz del catalogo reescrita en la interfaz.
     */
    @Test
    @DisplayName("lo que falta se dice con la palabra del catalogo, no con la clave")
    void loQueFaltaLlegaConSuRotulo() {
        ResultadoRegistro alta = propiedades.registrar(comando("TERRENO", "VENTA",
                new BigDecimal("95000"), "USD",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "300"),
                        new ValorAtributo("zonificacion", "CZ"))), agenteA);

        // El alta no deja registrar sin lo obligatorio, asi que la unica forma
        // de observar una ficha incompleta es borrar el valor despues -- que es
        // lo que pasa de verdad cuando el catalogo declara obligatoria una
        // clave que las propiedades antiguas no tenian.
        jdbc.update("delete from atributo_propiedad where id_propiedad = ? and clave = 'zonificacion'",
                alta.idPropiedad());

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertFalse(ficha.atributosQueFaltan().isEmpty(),
                "sin zonificacion, un terreno esta incompleto");
        ficha.atributosQueFaltan().forEach(falta -> {
            assertNotNull(falta.clave());
            assertNotNull(falta.rotulo(), "la clave " + falta.clave() + " llego sin rotulo");
            assertFalse(falta.rotulo().equals(falta.clave()),
                    "el rotulo es la palabra del catalogo, no la clave repetida: " + falta.clave());
        });
    }

    // ==================================================================
    // La actividad, y de que encargo viene cada cosa
    // ==================================================================

    /**
     * <b>Ninguna actividad pierde el encargo del que proviene.</b>
     *
     * <p>Es la regla que impide que el tercer bloque de la ficha vuelva a
     * mezclar lo que los dos primeros separaron: una visita de quien quiere
     * comprar y otra de quien quiere alquilar la misma propiedad se leen igual
     * en una lista plana.
     */
    @Test
    @DisplayName("la actividad viaja con su procedencia: cada hecho dice de que encargo nace")
    void laActividadConservaSuProcedencia() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "DEPARTAMENTO", null,
                "Disponible para venta o alquiler",
                new Ubicacion("Av. Larco 4321", "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("320000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("4800"), "USD",
                                null, null, null, null, null, null, null)),
                null);
        ResultadoRegistro alta = propiedades.registrar(comando, agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertNotNull(ficha.actividad(), "la actividad forma parte de la ficha, no de otro viaje");
        // Recien registrada no hay actividad todavia, y eso se dice con listas
        // vacias -- nunca con null, que obligaria al cliente a distinguir "no
        // hay" de "no vino".
        assertNotNull(ficha.actividad().oportunidades());
        assertNotNull(ficha.actividad().visitas());
        assertNotNull(ficha.actividad().interacciones());
        assertNotNull(ficha.actividad().expedientes());
        assertNotNull(ficha.actividad().contratos());

        Set<Long> encargos = Set.copyOf(alta.idsEncargos());
        java.util.stream.Stream.of(ficha.actividad().oportunidades(), ficha.actividad().visitas(),
                        ficha.actividad().interacciones(), ficha.actividad().expedientes(),
                        ficha.actividad().contratos())
                .flatMap(List::stream)
                .forEach(hecho -> {
                    assertNotNull(hecho.idEncargo(),
                            "un hecho sin encargo es un hecho sin procedencia: " + hecho.titulo());
                    assertTrue(encargos.contains(hecho.idEncargo()),
                            "el hecho apunta a un encargo que no es de esta propiedad");
                    assertNotNull(hecho.operacion(),
                            "y dice en palabras de que operacion viene");
                });
    }

    // ==================================================================
    // La memoria del inmueble: idPropiedad como continuidad historica
    // ==================================================================

    /**
     * <b>Los dos niveles, y por que hacen falta los dos.</b>
     *
     * <pre>
     *   idEncargo    la identidad tecnica de UN episodio comercial
     *   idPropiedad  la continuidad historica del inmueble
     * </pre>
     *
     * <p>Los bloques de encargo contestan «que dice este encargo». La historia
     * contesta «que ha pasado con este inmueble», que es la pregunta que un CRM
     * de operaciones vivas no sabe responder.
     */
    @Test
    @DisplayName("la historia cuenta cuantas veces estuvo en alquiler, sin fusionar los historicos")
    void laHistoriaCuentaLosEpisodiosSinFusionarlos() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long vigente = alta.idsEncargos().get(0);
        long anterior2025 = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");
        long anterior2024 = encargoCerrado(alta.idPropiedad(), "2024-02-10",
                new BigDecimal("2200"), "PEN");

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);
        EpisodiosDeOperacion alquiler = episodios(ficha, "ALQUILER");

        // «¿Cuantas veces estuvo en alquiler?»
        assertEquals(3, alquiler.veces());
        assertTrue(alquiler.vivoAhora(), "hoy sigue en alquiler");
        assertTrue(alquiler.hasta() == null, "con un encargo vivo, la historia no se cierra");

        // «¿A cuanto se pide ahora?» -- y con el encargo del que sale, para poder
        // volver de la cifra al episodio.
        assertEquals(0, new BigDecimal("2600").compareTo(alquiler.ultimoPedido().monto()));
        assertEquals(vigente, alquiler.ultimoPedido().idEncargo());

        // La linea atraviesa los tres encargos, del mas reciente al mas antiguo,
        // y cada movimiento conserva SU procedencia. Agregada para leerse, no
        // fusionada.
        assertEquals(3, ficha.historia().linea().size());
        assertEquals(List.of(vigente, anterior2025, anterior2024),
                ficha.historia().linea().stream().map(HitoDeLaHistoria::idEncargo).toList());
        ficha.historia().linea().forEach(hito -> {
            assertNotNull(hito.idEncargo(), "un movimiento sin encargo no se puede auditar");
            assertNotNull(hito.codigoEncargo());
            assertEquals("ALQUILER", hito.operacion());
        });

        // Y los bloques de encargo siguen intactos: los dos niveles conviven.
        assertEquals(3, ficha.encargos().size());
        ficha.encargos().forEach(encargo ->
                assertEquals(1, encargo.historico().size(),
                        "agregar para la historia no puede contaminar la serie de un encargo"));
    }

    /**
     * <b>Lo pedido y lo cerrado son dos numeros distintos.</b>
     *
     * <p>Y cuando no hay cierre, la respuesta correcta es {@code null}. Caer al
     * precio pedido convierte «lo que pediamos» en «lo que vale» sin que nadie
     * lo note -- y esa es justo la cifra que despues se cita en una negociacion.
     */
    @Test
    @DisplayName("el ultimo cierre no se confunde con el ultimo precio pedido")
    void elCierreNoSeConfundeConLoPedido() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long anterior = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");

        // Sin ningun hito de cierre todavia.
        EpisodiosDeOperacion sinCierre = episodios(
                propiedades.consultar(alta.idPropiedad(), agenteA), "ALQUILER");
        assertNotNull(sinCierre.ultimoPedido());
        assertTrue(sinCierre.ultimoCierre() == null,
                "sin cierre se declara faltante; NO se rellena con el precio pedido");

        // El alquiler de 2025 se cerro de verdad, y por debajo de lo pedido.
        jdbc.update("""
                insert into precio_propiedad (organizacion_id, id_propiedad, id_captacion,
                                              operacion, hito, moneda, monto, fecha)
                values (?, ?, ?, 'A', 'C', 'PEN', 2250, cast('2025-03-01' as date))
                """, agenteA.idOrganizacion(), alta.idPropiedad(), anterior);

        EpisodiosDeOperacion conCierre = episodios(
                propiedades.consultar(alta.idPropiedad(), agenteA), "ALQUILER");

        // «¿A cuanto se alquilo la ultima vez?» -- 2 250, no 2 600.
        assertEquals(0, new BigDecimal("2250").compareTo(conCierre.ultimoCierre().monto()));
        assertEquals(anterior, conCierre.ultimoCierre().idEncargo(),
                "y se puede volver al episodio que lo produjo");
        // Lo pedido no se movio: son dos preguntas distintas.
        assertEquals(0, new BigDecimal("2600").compareTo(conCierre.ultimoPedido().monto()));
    }

    /**
     * Venta y alquiler llevan <b>historias separadas</b>. Es la misma regla del
     * bloque de encargo, un nivel mas arriba: agregarlas juntas produciria «el
     * ultimo precio» de un inmueble que se vende Y se alquila, que no significa
     * nada.
     */
    @Test
    @DisplayName("la historia separa venta de alquiler, tambien al agregar")
    void laHistoriaSeparaLasDosOperaciones() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "DEPARTAMENTO", null,
                null,
                new Ubicacion("Av. Larco 9876", "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "118"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("320000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("4800"), "USD",
                                null, null, null, null, null, null, null)),
                null);
        ResultadoRegistro alta = propiedades.registrar(comando, agenteA);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals(2, ficha.historia().porOperacion().size());
        assertEquals(0, new BigDecimal("320000")
                .compareTo(episodios(ficha, "VENTA").ultimoPedido().monto()));
        assertEquals(0, new BigDecimal("4800")
                .compareTo(episodios(ficha, "ALQUILER").ultimoPedido().monto()));
        // Venta primero: es el orden del dominio, no el alfabetico.
        assertEquals("VENTA", ficha.historia().porOperacion().get(0).operacion());
        assertEquals("Venta", ficha.historia().porOperacion().get(0).operacionRotulo());
    }

    @Test
    @DisplayName("una propiedad sin historia lo dice con listas vacias, no con null")
    void sinHistoriaNoHayNull() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        jdbc.update("delete from precio_propiedad where id_propiedad = ?", alta.idPropiedad());

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertNotNull(ficha.historia());
        assertTrue(ficha.historia().linea().isEmpty());
        // El episodio sigue contando aunque no tenga movimientos: existio.
        assertEquals(1, episodios(ficha, "ALQUILER").veces());
        assertNull(episodios(ficha, "ALQUILER").ultimoPedido());
    }

    // ==================================================================
    // La publicacion pertenece al ENCARGO (V70)
    // ==================================================================

    /**
     * <b>El caso que decide el diseño.</b> PROP-0022: venta y alquiler a la vez.
     *
     * <p>Cada encargo tiene su propia gestion de publicacion. El anuncio de la
     * venta no aparece en el alquiler ni lo modifica, y viceversa. Colgada de la
     * propiedad --como estaba hasta V70-- la consulta devolvia los dos juntos sin
     * poder decir cual publicaba que, porque la publicacion no llevaba operacion.
     */
    @Test
    @DisplayName("venta y alquiler: cada encargo publica lo suyo, y no se ven entre si")
    void cadaEncargoPublicaLoSuyo() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "OFICINA", null, null,
                new Ubicacion("Av. La Marina 2450", "San Miguel", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "160")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("320000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("4800"), "USD",
                                null, null, null, null, null, null, null)),
                null);
        ResultadoRegistro alta = propiedades.registrar(comando, agenteA);

        FichaPropiedadUniversal antes = propiedades.consultar(alta.idPropiedad(), agenteA);
        long deVenta = encargo(antes, "VENTA").idEncargo();
        long deAlquiler = encargo(antes, "ALQUILER").idEncargo();

        publicaciones.crearEnEncargo(deVenta, anuncio("URBANIA", new BigDecimal("320000"), "USD"), agenteA);
        publicaciones.crearEnEncargo(deAlquiler, anuncio("FACEBOOK", new BigDecimal("4800"), "USD"), agenteA);

        List<FichaPublicacion> anunciosVenta = publicaciones.listarDeEncargo(deVenta, agenteA);
        List<FichaPublicacion> anunciosAlquiler = publicaciones.listarDeEncargo(deAlquiler, agenteA);

        assertEquals(1, anunciosVenta.size(), "el anuncio del alquiler se colo en la venta");
        assertEquals(1, anunciosAlquiler.size(), "el anuncio de la venta se colo en el alquiler");
        assertEquals("URBANIA", anunciosVenta.get(0).canal());
        assertEquals("FACEBOOK", anunciosAlquiler.get(0).canal());

        // Y el importe se llama como toca en cada uno. Un anuncio de venta
        // rotulado "renta mensual" es el error que V70 vino a quitar.
        assertEquals("precio de venta", anunciosVenta.get(0).importeRotulo());
        assertEquals("renta mensual", anunciosAlquiler.get(0).importeRotulo());

        // El hito de precio PUBLICADO nace atado a SU encargo y con SU operacion.
        // Antes de V70 se escribia suponiendo ALQUILER y sin encargo, asi que no
        // aparecia en ninguna ficha.
        FichaPropiedadUniversal despues = propiedades.consultar(alta.idPropiedad(), agenteA);
        EncargoFicha venta = porId(despues, deVenta);
        EncargoFicha alquiler = porId(despues, deAlquiler);
        assertTrue(venta.historico().stream().anyMatch(h -> "P".equals(h.hito())),
                "publicar la venta no dejo su hito en el historico de la venta");
        assertTrue(alquiler.historico().stream().anyMatch(h -> "P".equals(h.hito())),
                "publicar el alquiler no dejo su hito en el historico del alquiler");
        // Ninguno ve la cifra del otro.
        assertTrue(venta.historico().stream().noneMatch(
                        h -> new BigDecimal("4800").compareTo(h.monto()) == 0),
                "el historico de la venta se contamino con la renta publicada");

        // Y la ficha los cuelga de su bloque, no de la propiedad.
        assertEquals(1, venta.publicaciones().size());
        assertEquals(1, alquiler.publicaciones().size());
        assertEquals("URBANIA", venta.publicaciones().get(0).canal());
    }

    /**
     * La misma regla un nivel mas abajo: con <b>varios encargos de la misma
     * operacion</b>, cada uno conserva sus anuncios. Agrupar por operacion los
     * fundiria, igual que fundiria sus historicos economicos.
     */
    @Test
    @DisplayName("dos alquileres sucesivos conservan cada uno SUS anuncios")
    void losAnunciosNoSeAgrupanPorOperacion() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long vigente = alta.idsEncargos().get(0);
        long anterior = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");

        publicaciones.crearEnEncargo(vigente, anuncio("URBANIA", new BigDecimal("2600"), "PEN"), agenteA);
        // El anterior esta cerrado: se le inserta el anuncio como historia, que
        // es como llego a existir en su dia.
        jdbc.update("""
                insert into publicacion (organizacion_id, id_propiedad, id_captacion, canal,
                                         version_anuncio, titulo_anuncio, importe_publicado,
                                         moneda, codigo_origen, fecha_publicacion, estado)
                values (?, ?, ?, 'ADONDEVIVIR', 1, 'Anuncio 2025', 2400, 'PEN', 'HIST',
                        cast('2025-01-15' as timestamptz), 'C')
                """, agenteA.idOrganizacion(), alta.idPropiedad(), anterior);

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertEquals(1, porId(ficha, vigente).publicaciones().size());
        assertEquals(1, porId(ficha, anterior).publicaciones().size());
        assertEquals("URBANIA", porId(ficha, vigente).publicaciones().get(0).canal());
        assertEquals("ADONDEVIVIR", porId(ficha, anterior).publicaciones().get(0).canal());
    }

    /**
     * <b>No se publica lo que ya no se ofrece.</b> La regla vive en el servicio,
     * no en el boton: el backend la impone aunque la pantalla ofrezca la accion.
     */
    @Test
    @DisplayName("un encargo cerrado no se puede publicar")
    void elEncargoCerradoNoSePublica() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long cerrado = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(cerrado,
                        anuncio("URBANIA", new BigDecimal("2400"), "PEN"), agenteA));

        assertTrue(error.getMessage().contains("no esta vigente"), error.getMessage());
    }

    /** La ficha dice si se puede gestionar la publicacion, y si no, por que. */
    @Test
    @DisplayName("la capacidad de publicar la decide el Core, no la pantalla")
    void laCapacidadDePublicarLaDecideElCore() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long cerrado = encargoCerrado(alta.idPropiedad(), "2025-01-15",
                new BigDecimal("2400"), "PEN");

        FichaPropiedadUniversal ficha = propiedades.consultar(alta.idPropiedad(), agenteA);

        assertTrue(porId(ficha, alta.idsEncargos().get(0)).publicacionGestionable().permitida());
        assertFalse(porId(ficha, cerrado).publicacionGestionable().permitida());
        assertNotNull(porId(ficha, cerrado).publicacionGestionable().motivo(),
                "cuando no se puede, la ficha dice por que");
    }

    /** Un encargo de otro tenant es 404, no una lista vacia. */
    @Test
    @DisplayName("los anuncios de un encargo ajeno no se leen")
    void losAnunciosDeOtroTenantNoSeLeen() {
        ResultadoRegistro alta = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2600"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"))), agenteA);
        long encargo = alta.idsEncargos().get(0);

        assertThrows(NoEncontradoException.class,
                () -> publicaciones.listarDeEncargo(encargo, agenteB));
    }

    private static DatosPublicacion anuncio(String canal, BigDecimal importe, String moneda) {
        return new DatosPublicacion(canal, null, importe, moneda, null, null, "P");
    }

    @Test
    @DisplayName("dos veces la misma operacion se rechaza: no son dos encargos, es un error")
    void dosVecesLaMismaOperacion() {
        ComandoRegistro comando = new ComandoRegistro(null, PANTALLA, null, "LOCAL", null, null,
                new Ubicacion("Jr. Union 100", "Lima", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                // `tipo_acceso` es ALT en LOCAL desde V81: obligatorio en el alta.
                List.of(new ValorAtributo("metraje_total", "80"),
                        new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE")),
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
        // Con sus filas por tipo. Nacio con `aplica_todos = true` y sin
        // ninguna, y desde V86 el campo no decide aplicabilidad: asi declarada,
        // la clave del tenant no se preguntaria en ningun tipo.
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, aplica_todos, del_sistema)
                values (?, 'vista_al_mar', 'Vista al mar', 'BOOLEANO', false, false)
                """, agenteA.idOrganizacion());
        jdbc.update("""
                insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                    requerido, exigencia)
                select c.id_catalogo_atributo, t.tipo, false, 'OPC'
                  from catalogo_atributo c
                  cross join tipos_de_propiedad() as t(tipo)
                 where c.clave = 'vista_al_mar' and c.organizacion_id = ?
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
        assertFalse(captura
                        .definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "DEPARTAMENTO", null, agenteB)
                        .delTipo().stream()
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
                        new ComandoEdicion(null, PANTALLA, "intento del vecino", null, null, null, null, null),
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
                // `tipo_acceso` es ALT en LOCAL desde V81: obligatorio en el alta.
                List.of(new ValorAtributo("metraje_total", "80"),
                        new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE")),
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
                        null, null, null, null, null, null, null)), null), agenteA);

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
                List.of(new Titular(propietarioCarlos, null, true)), null, null, null), agenteA);

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
                GuionRegistroPropiedad.OPERACIONES, "VENTA",
                GuionRegistroPropiedad.DISTRITO, "Miraflores",
                GuionRegistroPropiedad.para(GuionRegistroPropiedad.IMPORTE,
                        OperacionInmobiliaria.VENTA), "180000",
                GuionRegistroPropiedad.para(GuionRegistroPropiedad.MONEDA,
                        OperacionInmobiliaria.VENTA), "USD"), CONVERSANDO, agenteA);

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
        assertEquals("VENTA", recuperado.conocido().get(GuionRegistroPropiedad.OPERACIONES));
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
        assertEquals("DEPARTAMENTO", ficha.tipoPropiedad());
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
                Map.of(GuionRegistroPropiedad.OPERACIONES, "AMBAS"), PANTALLA, agenteA));
    }

    // ==================================================================
    // Captura universal: un motor, siete tipos, dos operaciones
    //
    // Lo de arriba prueba el CASO DE USO. Esto prueba el camino por el que
    // entran de verdad la pantalla y KAIROS, que es donde estaba el
    // estrangulamiento: el borrador solo sabia llevar una operacion, asi que
    // "venta y alquiler" no tenia forma de llegar al caso de uso que si lo
    // admitia desde el primer dia.
    // ==================================================================

    @Test
    @DisplayName("oficina en venta Y alquiler por el motor: una propiedad, dos encargos")
    void ventaYAlquilerPorElMotor() {
        MotorDeCaptura.EstadoCaptura abierto = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null, Map.of(), PANTALLA, agenteA);

        Map<String, String> todo = new LinkedHashMap<>();
        todo.put(GuionRegistroPropiedad.TIPO_PROPIEDAD, "OFICINA");
        todo.put(GuionRegistroPropiedad.OPERACIONES, "VENTA,ALQUILER");
        todo.put(importeDe(OperacionInmobiliaria.VENTA), "540000");
        todo.put(monedaDe(OperacionInmobiliaria.VENTA), "USD");
        todo.put(importeDe(OperacionInmobiliaria.ALQUILER), "6800");
        todo.put(monedaDe(OperacionInmobiliaria.ALQUILER), "USD");
        todo.put(GuionRegistroPropiedad.TITULARES, String.valueOf(propietarioAna));
        todo.put(GuionRegistroPropiedad.DIRECCION, "Av. Rivera Navarrete 501");
        todo.put(GuionRegistroPropiedad.DISTRITO, "San Isidro");
        todo.put("metraje_total", "210");

        MotorDeCaptura.EstadoCaptura completo =
                captura.avanzar(null, abierto.idBorrador(), todo, PANTALLA, agenteA);
        assertTrue(completo.faltante().isEmpty(),
                "con las dos condiciones economicas puestas no falta nada: " + completo.faltante());
        assertTrue(completo.listoParaEjecutar());

        MotorDeCaptura.Ejecucion ejecucion =
                captura.ejecutar(abierto.idBorrador(), UUID.randomUUID().toString(), PANTALLA, agenteA);

        assertEquals(2, ejecucion.idsEncargos().size(), "dos operaciones, dos encargos");

        FichaPropiedadUniversal ficha = propiedades.consultar(ejecucion.idPropiedad(), agenteA);
        assertEquals("OFICINA", ficha.tipoPropiedad());
        assertEquals(2, ficha.encargos().size(), "y UNA sola propiedad, no dos");

        EncargoFicha venta = encargoDe(ficha, "VENTA");
        EncargoFicha alquiler = encargoDe(ficha, "ALQUILER");
        assertEquals(0, new BigDecimal("540000").compareTo(venta.importe()),
                "el precio de venta no se mezcla con la renta");
        assertEquals(0, new BigDecimal("6800").compareTo(alquiler.importe()));
        assertEquals(1, venta.historico().size(), "cada encargo estrena SU serie economica");
        assertEquals(1, alquiler.historico().size());
        assertEquals(0, new BigDecimal("540000").compareTo(venta.historico().get(0).monto()));
        assertEquals(0, new BigDecimal("6800").compareTo(alquiler.historico().get(0).monto()));
    }

    @Test
    @DisplayName("la definicion de dos operaciones trae dos bloques economicos y una sola ficha fisica")
    void definicionConDosOperaciones() {
        MotorDeCaptura.DefinicionCaptura dos = captura.definicion(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, "OFICINA", "VENTA,ALQUILER", agenteA);

        assertEquals(List.of("VENTA", "ALQUILER"), dos.operaciones());
        assertEquals(2, dos.deLaOperacion().size());
        assertEquals("VENTA", dos.deLaOperacion().get(0).operacion());
        assertEquals("ALQUILER", dos.deLaOperacion().get(1).operacion());
        assertTrue(dos.deLaOperacion().get(0).rotulo().toLowerCase(java.util.Locale.ROOT)
                        .contains("venta"),
                "el bloque se titula solo: " + dos.deLaOperacion().get(0).rotulo());

        // El importe se llama distinto en cada bloque. 540 000 y 6 800 no se
        // distinguen por magnitud, se distinguen por como se llaman.
        MotorDeCaptura.Pregunta precio = preguntaDe(dos.deLaOperacion().get(0), "importe");
        MotorDeCaptura.Pregunta renta = preguntaDe(dos.deLaOperacion().get(1), "importe");
        assertEquals(importeDe(OperacionInmobiliaria.VENTA), precio.clave());
        assertEquals(importeDe(OperacionInmobiliaria.ALQUILER), renta.clave());
        assertTrue(precio.rotulo().toLowerCase(java.util.Locale.ROOT).contains("precio"),
                "rotulo del importe de venta: " + precio.rotulo());
        assertTrue(renta.rotulo().toLowerCase(java.util.Locale.ROOT).contains("renta"),
                "rotulo del importe de alquiler: " + renta.rotulo());

        // La ficha fisica se pregunta UNA vez: la propiedad no se duplica
        // porque se encargue dos veces.
        MotorDeCaptura.DefinicionCaptura una = captura.definicion(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, "OFICINA", "VENTA", agenteA);
        assertEquals(una.comunes().size(), dos.comunes().size());
        assertEquals(una.delTipo().size(), dos.delTipo().size());
    }

    /**
     * <b>El gate del encargo universal:</b> lo que se pregunta sale del
     * catalogo, no de una lista escrita en ningun cliente. Si esto pasa, una
     * pantalla que pinte {@code definicion()} tal cual no puede pedirle
     * dormitorios a un terreno aunque quiera.
     */
    @Test
    @DisplayName("el catalogo decide que se pregunta: dormitorios a la vivienda, zonificacion al terreno")
    void elCatalogoDecideQueSePregunta() {
        Map<String, MotorDeCaptura.Pregunta> departamento = porClave("DEPARTAMENTO");
        Map<String, MotorDeCaptura.Pregunta> terreno = porClave("TERRENO");
        Map<String, MotorDeCaptura.Pregunta> local = porClave("LOCAL");
        Map<String, MotorDeCaptura.Pregunta> casa = porClave("CASA");

        assertTrue(departamento.containsKey("dormitorios"), "un departamento pide dormitorios");
        assertTrue(departamento.get("dormitorios").obligatoria());

        assertFalse(terreno.containsKey("dormitorios"), "un terreno no tiene dormitorios");
        assertTrue(terreno.containsKey("zonificacion"), "y si tiene zonificacion");
        assertTrue(terreno.get("zonificacion").obligatoria());

        assertFalse(departamento.containsKey("rubro_permitido"),
                "a una vivienda no se le pregunta el rubro comercial");
        assertFalse(casa.containsKey("rubro_permitido"));
        assertFalse(terreno.containsKey("rubro_permitido"));

        assertTrue(local.containsKey("rubro_permitido"), "un local si puede llevar rubro");
        assertTrue(local.containsKey("carga_electrica_kw"));
        assertFalse(local.get("rubro_permitido").obligatoria(),
                "puede llevarlo; no esta obligado a llevarlo");

        // Y lo estructural que depende del tipo fisico tampoco se pregunta de mas.
        assertFalse(terreno.containsKey("piso"), "un terreno no esta en un piso");
        assertTrue(departamento.containsKey("piso"));
        assertFalse(terreno.containsKey(GuionRegistroPropiedad.EDIFICIO),
                "ni en un edificio o galeria");
    }

    /**
     * <b>Dos preguntas con el mismo rotulo son dos duenos del mismo dato.</b>
     *
     * <p>Esto no es cosmetica. Hasta V67 el motor publicaba {@code pisoUnidad}
     * —del guion— y {@code piso} —del catalogo—: dos claves, un concepto, dos
     * sitios donde guardarlo. D-E4-3 no lo vio porque revisaba cada clave contra
     * su columna, y cada una declaraba una sola autoridad; el defecto era que
     * las claves eran dos.
     *
     * <p>Estuvo invisible mientras la pantalla de alta dibujaba una lista
     * escrita a mano. El alta universal pinta lo que el motor publica, asi que
     * enseno dos campos «Piso» seguidos. Este test lo convierte en un fallo del
     * build en vez de en un hallazgo a ojo.
     */
    @Test
    @DisplayName("el motor no publica dos preguntas con el mismo rotulo para un mismo tipo")
    void ningunDatoSePreguntaDosVeces() {
        for (String tipo : List.of("LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO",
                "ALMACEN", "OTRO")) {
            MotorDeCaptura.DefinicionCaptura definicion = captura.definicion(
                    MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, "VENTA,ALQUILER", agenteA);

            // La ficha FISICA se pregunta una vez: es una sola propiedad.
            sinRotulosRepetidos(tipo + " (ficha fisica)",
                    java.util.stream.Stream.concat(definicion.comunes().stream(),
                            definicion.delTipo().stream()).toList());

            // Y cada condicion economica por separado. Que «Moneda» aparezca en
            // los dos bloques NO es una repeticion: son dos encargos, y cada uno
            // tiene la suya. Comprobarlo sobre la lista entera diria justo lo
            // contrario de lo que el modelo universal afirma.
            for (MotorDeCaptura.BloqueOperacion bloque : definicion.deLaOperacion()) {
                sinRotulosRepetidos(tipo + " / " + bloque.operacion(), bloque.preguntas());
            }
        }
    }

    private static void sinRotulosRepetidos(String donde, List<MotorDeCaptura.Pregunta> preguntas) {
        Map<String, List<String>> porRotulo = new LinkedHashMap<>();
        preguntas.forEach(pregunta -> porRotulo
                .computeIfAbsent(pregunta.rotulo().toLowerCase(java.util.Locale.ROOT),
                        clave -> new java.util.ArrayList<>())
                .add(pregunta.clave()));

        porRotulo.forEach((rotulo, claves) -> assertEquals(1, claves.size(),
                donde + " pregunta \"" + rotulo + "\" " + claves.size() + " veces, con las claves "
                        + claves + ". Dos claves para el mismo concepto son dos autoridades, y la "
                        + "segunda pisa a la primera."));
    }

    @Test
    @DisplayName("el piso se pregunta una vez y aterriza en su unica autoridad")
    void elPisoTieneUnSoloDueno() {
        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO",
                        GuionRegistroPropiedad.OPERACIONES, "ALQUILER",
                        importeDe(OperacionInmobiliaria.ALQUILER), "2400",
                        monedaDe(OperacionInmobiliaria.ALQUILER), "USD",
                        GuionRegistroPropiedad.TITULARES, String.valueOf(propietarioAna),
                        GuionRegistroPropiedad.DIRECCION, "Av. Larco 1200",
                        GuionRegistroPropiedad.DISTRITO, "Miraflores",
                        "metraje_total", "96",
                        "dormitorios", "3",
                        "piso", "7"), PANTALLA, agenteA);

        MotorDeCaptura.Ejecucion alta =
                captura.ejecutar(borrador.idBorrador(), null, PANTALLA, agenteA);

        assertEquals("7", jdbc.queryForObject("select piso from propiedad where id_propiedad = ?",
                String.class, alta.idPropiedad()),
                "V67 declaro `propiedad.piso` como autoridad unica del concepto PISO");
        assertEquals(0L, (long) jdbc.queryForObject("""
                select count(*) from atributo_propiedad
                 where id_propiedad = ? and clave = 'piso'
                """, Long.class, alta.idPropiedad()),
                "y por tanto no queda una segunda copia en la tabla de atributos");

        // Y el contrato logico no se movio: el cliente sigue leyendo `piso`.
        assertTrue(propiedades.consultar(alta.idPropiedad(), agenteA).atributos().stream()
                        .anyMatch(atributo -> "piso".equals(atributo.clave())
                                && "7".equals(atributo.valor())),
                "la ficha devuelve `piso` aunque su fila ya no exista: si el escritor enruta "
                        + "por autoridad, el lector tambien");
    }

    @Test
    @DisplayName("un importe que no dice de que encargo es se rechaza, y el error ensena como")
    void elImporteDiceDeQueEncargoEs() {
        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "CASA",
                        GuionRegistroPropiedad.OPERACIONES, "ALQUILER"), PANTALLA, agenteA);

        ReglaNegocioException suelto = assertThrows(ReglaNegocioException.class,
                () -> captura.avanzar(null, borrador.idBorrador(),
                        Map.of(GuionRegistroPropiedad.IMPORTE, "2900"), PANTALLA, agenteA));
        assertTrue(suelto.getMessage().contains("importe:ALQUILER"),
                "el mensaje tiene que decir como se escribe: " + suelto.getMessage());

        // Y un importe de una operacion que no se declaro tampoco cuela: seria
        // un precio de venta guardado en una propiedad que solo se alquila.
        ReglaNegocioException ajeno = assertThrows(ReglaNegocioException.class,
                () -> captura.avanzar(null, borrador.idBorrador(),
                        Map.of(importeDe(OperacionInmobiliaria.VENTA), "180000"), PANTALLA, agenteA));
        assertTrue(ajeno.getMessage().contains("VENTA"), ajeno.getMessage());
    }

    @Test
    @DisplayName("abandonar la captura no deja ninguna propiedad a medias")
    void abandonarNoDejaPropiedad() {
        long propiedadesAntes = cuantasPropiedades(agenteA);

        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "CASA",
                        GuionRegistroPropiedad.OPERACIONES, "ALQUILER",
                        importeDe(OperacionInmobiliaria.ALQUILER), "3500",
                        monedaDe(OperacionInmobiliaria.ALQUILER), "PEN"), PANTALLA, agenteA);

        MotorDeCaptura.EstadoCaptura descartado =
                captura.descartar(borrador.idBorrador(), agenteA);

        assertEquals(BorradorCaptura.DESCARTADO, descartado.estado(),
                "descartado, no borrado: que alguien lo empezara tambien es un hecho");
        assertNull(descartado.idEntidad());
        assertEquals(propiedadesAntes, cuantasPropiedades(agenteA),
                "nada se escribe hasta confirmar");
        assertThrows(ReglaNegocioException.class,
                () -> captura.ejecutar(borrador.idBorrador(), null, PANTALLA, agenteA));
    }

    @Test
    @DisplayName("confirmar dos veces la misma captura devuelve la misma propiedad, no una segunda")
    void confirmarDosVecesNoDuplica() {
        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "TERRENO",
                        GuionRegistroPropiedad.OPERACIONES, "VENTA",
                        importeDe(OperacionInmobiliaria.VENTA), "95000",
                        monedaDe(OperacionInmobiliaria.VENTA), "USD",
                        GuionRegistroPropiedad.TITULARES, String.valueOf(propietarioAna),
                        GuionRegistroPropiedad.DIRECCION, "Fundo Los Cipreses s/n",
                        GuionRegistroPropiedad.DISTRITO, "Pachacamac",
                        "metraje_total", "1200",
                        "zonificacion", "RDM"), PANTALLA, agenteA);

        String clave = UUID.randomUUID().toString();
        MotorDeCaptura.Ejecucion primera =
                captura.ejecutar(borrador.idBorrador(), clave, PANTALLA, agenteA);
        MotorDeCaptura.Ejecucion segunda =
                captura.ejecutar(borrador.idBorrador(), clave, PANTALLA, agenteA);

        assertEquals(primera.idPropiedad(), segunda.idPropiedad());
        assertEquals(primera.codigoPropiedad(), segunda.codigoPropiedad());
        assertEquals(primera.idsEncargos(), segunda.idsEncargos());
        assertTrue(segunda.reintento(), "el segundo intento se declara reintento");
        assertEquals(1L, (long) jdbc.queryForObject(
                "select count(*) from propiedad where id_propiedad = ?", Long.class,
                primera.idPropiedad()));
    }

    /**
     * El codigo lo pone BROX, no el cliente. El formulario heredado lo generaba
     * en Angular ({@code generarCodigoLocal}), y un codigo generado en el
     * cliente no puede garantizar unicidad: dos pestanas abiertas producen el
     * mismo.
     */
    @Test
    @DisplayName("el codigo de la propiedad lo pone BROX cuando el alta no lo trae")
    void elCodigoLoPoneBrox() {
        MotorDeCaptura.EstadoCaptura borrador = captura.avanzar(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, null,
                Map.of(GuionRegistroPropiedad.TIPO_PROPIEDAD, "ALMACEN",
                        GuionRegistroPropiedad.OPERACIONES, "ALQUILER",
                        importeDe(OperacionInmobiliaria.ALQUILER), "4200",
                        monedaDe(OperacionInmobiliaria.ALQUILER), "PEN",
                        GuionRegistroPropiedad.TITULARES, String.valueOf(propietarioAna),
                        GuionRegistroPropiedad.DIRECCION, "Av. Argentina 2100",
                        GuionRegistroPropiedad.DISTRITO, "Callao",
                        "metraje_total", "800"), PANTALLA, agenteA);

        MotorDeCaptura.Ejecucion alta =
                captura.ejecutar(borrador.idBorrador(), null, PANTALLA, agenteA);

        assertNotNull(alta.codigoPropiedad());
        assertFalse(alta.codigoPropiedad().isBlank());
    }

    // ==================================================================
    // El listado universal
    // ==================================================================

    /**
     * <b>«Venta y alquiler» se filtra con dos EXISTS, no con una igualdad.</b>
     *
     * <p>Es la comprobacion que decide si el listado entendio el modelo. Con un
     * valor {@code AMBAS} almacenado, esto seria un {@code where operacion =
     * 'AMBAS'} y funcionaria... hasta que alguien cerrara uno de los dos
     * encargos y la fila siguiera diciendo que la propiedad esta en las dos
     * cosas. Preguntando por los encargos vivos, la respuesta se corrige sola.
     */
    @Test
    @DisplayName("el listado filtra por operacion mirando los encargos vivos")
    void elListadoFiltraPorOperacion() {
        long soloVenta = propiedades.registrar(comando("DEPARTAMENTO", "VENTA",
                new BigDecimal("180000"), "USD", List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3"))), agenteA).idPropiedad();

        long soloAlquiler = propiedades.registrar(comando("LOCAL", "ALQUILER",
                new BigDecimal("2900"), "PEN", List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "85"))), agenteA).idPropiedad();

        long lasDos = propiedades.registrar(new ComandoRegistro(null, PANTALLA, null, "OFICINA",
                null, null,
                new Ubicacion("Av. Canaval 350", "San Isidro", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "210")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("540000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("6800"), "USD",
                                null, null, null, null, null, null, null)),
                null), agenteA).idPropiedad();

        assertEquals(Set.of(soloVenta, lasDos), idsDe(filtro(null, null, "VENTA")),
                "en venta estan la que solo se vende y la que se vende Y se alquila");
        assertEquals(Set.of(soloAlquiler, lasDos), idsDe(filtro(null, null, "ALQUILER")));
        assertEquals(Set.of(lasDos), idsDe(filtro(null, null, "VENTA,ALQUILER")),
                "«venta y alquiler» son las que tienen LAS DOS, no las que tienen alguna");

        // Y el tipo filtra por su lado, sin mezclarse con la operacion.
        assertEquals(Set.of(soloVenta), idsDe(filtro("DEPARTAMENTO", null, null)));
        assertEquals(Set.of(), idsDe(filtro("TERRENO", null, null)));
    }

    @Test
    @DisplayName("cada fila del listado lleva SUS encargos, con su importe cada uno")
    void elListadoLlevaLosEncargosDeCadaFila() {
        long lasDos = propiedades.registrar(new ComandoRegistro(null, PANTALLA, null, "LOCAL",
                null, null,
                new Ubicacion("Av. La Marina 2450", "San Miguel", null, null, null, null, null, null, null),
                List.of(new Titular(propietarioAna, null, null)),
                // `tipo_acceso` es ALT en LOCAL desde V81: obligatorio en el alta.
                List.of(new ValorAtributo("metraje_total", "160"),
                        new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("320000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("4800"), "USD",
                                null, null, null, null, null, null, null)),
                null), agenteA).idPropiedad();

        PropiedadUniversalService.FilaPropiedad fila = filtro(null, null, null).items().stream()
                .filter(f -> f.id() == lasDos)
                .findFirst()
                .orElseThrow();

        assertEquals(2, fila.encargos().size(), "una fila, dos encargos: no dos filas");
        // El listado publica el NOMBRE del valor y su ROTULO, nunca el codigo
        // de una letra: traducir "L" a «Local comercial» seria la matriz
        // «tipo -> texto» viviendo en el cliente, y con dos clientes habria dos.
        assertEquals("LOCAL", fila.tipoPropiedad());
        assertEquals("Local comercial", fila.tipoRotulo());
        assertEquals(1, fila.titulares());
        assertEquals(0, new BigDecimal("320000").compareTo(
                fila.encargos().stream().filter(e -> "VENTA".equals(e.operacion()))
                        .findFirst().orElseThrow().importe()));
        assertEquals(0, new BigDecimal("4800").compareTo(
                fila.encargos().stream().filter(e -> "ALQUILER".equals(e.operacion()))
                        .findFirst().orElseThrow().importe()));
    }

    @Test
    @DisplayName("el listado es del tenant: la cartera del vecino no se ve ni contando")
    void elListadoNoCruzaTenants() {
        propiedades.registrar(comando("CASA", "ALQUILER", new BigDecimal("3500"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "150"),
                        new ValorAtributo("dormitorios", "4"))), agenteA);

        assertEquals(1L, filtro(null, null, null).total(), "el tenant A ve la suya");
        assertEquals(0L, propiedades.listar(new PropiedadUniversalService.FiltrosPropiedad(
                null, null, null, null, null, 1, 50), agenteB).total(),
                "y el tenant B no ve ninguna, ni en el total: el conteo tambien lleva alcance");
    }

    @Test
    @DisplayName("el filtro solo ofrece distritos que existen en la cartera")
    void losDistritosDelFiltroSalenDeLaCartera() {
        propiedades.registrar(comando("CASA", "ALQUILER", new BigDecimal("3500"), "PEN",
                List.of(new Titular(propietarioAna, null, null)),
                List.of(new ValorAtributo("metraje_total", "150"),
                        new ValorAtributo("dormitorios", "4"))), agenteA);

        List<String> distritos = propiedades.opcionesDeFiltro(agenteA).distritos();
        assertTrue(distritos.contains("Miraflores"), "el de la propiedad recien creada: " + distritos);
        assertEquals(distritos.stream().distinct().toList(), distritos, "sin repetidos");
    }

    private Pagina<PropiedadUniversalService.FilaPropiedad> filtro(String tipo, String distrito,
                                                                   String operaciones) {
        return propiedades.listar(new PropiedadUniversalService.FiltrosPropiedad(
                null, tipo, distrito, null, operaciones, 1, 50), agenteA);
    }

    private static Set<Long> idsDe(Pagina<PropiedadUniversalService.FilaPropiedad> pagina) {
        return pagina.items().stream()
                .map(PropiedadUniversalService.FilaPropiedad::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------

    private static String importeDe(OperacionInmobiliaria operacion) {
        return GuionRegistroPropiedad.para(GuionRegistroPropiedad.IMPORTE, operacion);
    }

    private static String monedaDe(OperacionInmobiliaria operacion) {
        return GuionRegistroPropiedad.para(GuionRegistroPropiedad.MONEDA, operacion);
    }

    private static EncargoFicha encargoDe(FichaPropiedadUniversal ficha, String operacion) {
        return ficha.encargos().stream()
                .filter(encargo -> operacion.equals(encargo.operacion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay encargo de " + operacion));
    }

    private static MotorDeCaptura.Pregunta preguntaDe(MotorDeCaptura.BloqueOperacion bloque,
                                                      String base) {
        return bloque.preguntas().stream()
                .filter(pregunta -> pregunta.clave().startsWith(base + GuionRegistroPropiedad.DE))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "el bloque de " + bloque.operacion() + " no pregunta " + base));
    }

    /** Todo lo que el motor publica para un tipo, por clave. La operacion no altera esto. */
    private Map<String, MotorDeCaptura.Pregunta> porClave(String tipo) {
        Map<String, MotorDeCaptura.Pregunta> preguntas = new LinkedHashMap<>();
        captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, "ALQUILER", agenteA)
                .todas().forEach(pregunta -> preguntas.put(pregunta.clave(), pregunta));
        return preguntas;
    }

    private long cuantasPropiedades(Actor actor) {
        Long total = jdbc.queryForObject("select count(*) from propiedad where organizacion_id = ?",
                Long.class, actor.idOrganizacion());
        return total == null ? 0 : total;
    }

    // ==================================================================
    // Utilidades del fixture
    // ==================================================================

    /**
     * <b>`tipo_acceso` se anade solo cuando el tipo es LOCAL</b> (V81).
     *
     * <p>Desde V81 es `ALT` en `L`, y en este proyecto `ALT` significa
     * <b>obligatorio en el ALTA</b>: `exigirObligatorios` corta el registro, no
     * solo la publicacion. Sin esto, treinta casos de esta clase y de
     * {@code PropiedadSinEncargoIntegrationTest} fallaban con "Faltan atributos
     * obligatorios de LOCAL: tipo_acceso" antes de llegar a lo que probaban.
     *
     * <p>Se registra el dato, que es lo que haria un agente que ha estado en el
     * local. Lo que NO se hace es bajar la exigencia a OPC ni cambiar el tipo a
     * OFICINA para esquivarla: lo primero relaja la regla que decidio el
     * titular, lo segundo cambia lo que el caso dice probar.
     *
     * <p>Solo se anade si el caso no lo trae ya, para que un test que quiera
     * ejercitar la ausencia pueda seguir haciendolo pasando su propia lista.
     */
    private ComandoRegistro comando(String tipo, String operacion, BigDecimal importe, String moneda,
                                    List<Titular> titulares, List<ValorAtributo> atributos) {
        if ("LOCAL".equals(tipo)
                && atributos.stream().noneMatch(a -> "tipo_acceso".equals(a.clave()))) {
            List<ValorAtributo> conAcceso = new ArrayList<>(atributos);
            conAcceso.add(new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
            atributos = List.copyOf(conAcceso);
        }
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

    /** Los episodios de una operacion en la historia del inmueble. */
    private static EpisodiosDeOperacion episodios(FichaPropiedadUniversal ficha, String operacion) {
        return ficha.historia().porOperacion().stream()
                .filter(e -> operacion.equals(e.operacion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la historia no tiene " + operacion));
    }

    /**
     * El encargo por su id, que es como se identifica un bloque de la ficha.
     * Buscarlo por operacion no serviria aqui: es justo lo que estas pruebas
     * niegan que se pueda hacer cuando hay historia.
     */
    private static EncargoFicha porId(FichaPropiedadUniversal ficha, long idEncargo) {
        return ficha.encargos().stream()
                .filter(e -> e.idEncargo() == idEncargo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay encargo " + idEncargo));
    }

    /**
     * Un encargo de alquiler ya CERRADO, con su hito. Se inserta directo porque
     * el caso de uso no sabe abrir encargos historicos --y no deberia: lo que
     * se prueba no es como se llega a esa situacion, sino que la ficha la lee
     * bien cuando existe--.
     *
     * <p>Sin condicion economica a proposito: un encargo antiguo puede no
     * tenerla, y la ficha tiene que sobrevivir a eso.
     */
    private long encargoCerrado(long idPropiedad, String fecha, java.math.BigDecimal monto,
                                String moneda) {
        Long idEncargo = jdbc.queryForObject("""
                insert into captacion (organizacion_id, codigo_captacion, fecha_captacion,
                                       fecha_inicio_encargo, fecha_fin_encargo, estado,
                                       motivo_operacion, fecha_cierre, motivo_cierre,
                                       id_propiedad, id_rol_agente)
                values (?, ?, cast(? as date), cast(? as date),
                        cast(? as date) + interval '11 months', 'C',
                        'A', cast(? as date), 'A', ?, ?)
                returning id_captacion
                """, Long.class, agenteA.idOrganizacion(),
                "HIST-" + UUID.randomUUID().toString().substring(0, 8),
                fecha, fecha, fecha, fecha, idPropiedad, agenteA.idRolOperativo());
        jdbc.update("""
                insert into precio_propiedad (organizacion_id, id_propiedad, id_captacion,
                                              operacion, hito, moneda, monto, fecha)
                values (?, ?, ?, 'A', 'U', ?, ?, cast(? as date))
                """, agenteA.idOrganizacion(), idPropiedad, idEncargo, moneda, monto, fecha);
        return idEncargo;
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
        // Antes que captacion y que propiedad: desde V70 la publicacion
        // referencia las dos, y el borrado en orden equivocado choca contra el FK.
        jdbc.update("delete from publicacion where organizacion_id = ?", idOrganizacion);
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
