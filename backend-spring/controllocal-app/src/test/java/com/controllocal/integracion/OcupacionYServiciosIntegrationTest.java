package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.AtributoQueFalta;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El gate del Corte 5, subtanda 5A</b> (V84): quien ocupa el inmueble, y que
 * servicios llegan.
 *
 * <h2>Que capacidad prueba</h2>
 * Tres cosas que hasta V84 no existian, y una que existia mal:
 *
 * <ul>
 *   <li><b>Donde registrar quien esta dentro.</b> La condicion comercial
 *       {@code entrega_desocupado} se pacta en los siete tipos desde V77 y el
 *       HECHO sobre el que se pacta no existia. Mientras no existe, el unico
 *       sitio donde cabe «hoy vive el propietario» es el pacto de un encargo —
 *       y un pacto muere con su encargo, mientras que el hecho sobrevive.</li>
 *   <li><b>Si el terreno tiene agua y luz, por separado.</b> En la periferia se
 *       tiene luz y no desague, o al reves: un solo campo agregado escondia
 *       justo la combinacion que decide la compra.</li>
 *   <li><b>El tercer estado de un servicio</b>: conectado no es lo mismo que
 *       con factibilidad aprobada, y {@code gas} pedia esa distincion desde tres
 *       documentos sin tenerla en su vocabulario.</li>
 *   <li><b>La ultima LISTA muda del catalogo, retirada.</b>
 *       {@code servicios_disponibles} era LISTA sin una sola opcion, y por eso
 *       aceptaba cualquier cadena: {@code MotorDeCaptura.controlDe} la degradaba
 *       a TEXTO y la comprobacion de vocabulario del trigger esta condicionada a
 *       que existan opciones.</li>
 * </ul>
 *
 * <h2>Por que contra PostgreSQL real</h2>
 * La exigencia, la aplicabilidad y el vocabulario son <b>filas</b>, y lo que las
 * hace cumplirse son triggers y consultas. Nada de eso lo lee javac, ni
 * Hibernate, ni ArchUnit.
 *
 * <h2>Lo que este gate NO puede medir, y quien lo mide</h2>
 * La <b>conservacion del legado de {@code servicios_disponibles}</b> se
 * demuestra comparando el ANTES con el DESPUES, y el unico sitio que ve el antes
 * es la propia migracion: su bloque 8.8 compara por CONJUNTO contra una foto y
 * aborta si una sola fila cambio. Aqui se prueba el <b>mecanismo</b> que hace
 * segura esa conservacion —retirar una clave no oculta sus valores— sobre una
 * clave de prueba, para que la afirmacion no dependa de que la base traiga
 * legado.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OcupacionYServiciosIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;
    @Autowired PublicacionService publicaciones;
    @Autowired CatalogoAtributoRepository catalogo;

    // ==================================================================
    // 1. La clave transversal: el hecho llega donde llega su condicion
    // ==================================================================

    /**
     * <b>Los siete tipos, con filas explicitas y todas OPC</b> (D-C5-1, D-1).
     *
     * <p>Se compara el CONJUNTO y no el numero: seis tipos y uno repetido darian
     * siete filas igual, y el tipo que faltara seria justo el que no puede
     * registrar la ocupacion.
     */
    @Test
    @DisplayName("V84: estado_ocupacion aplica a los SIETE tipos, y en los siete es OPC")
    void laOcupacionAplicaALosSieteTipos() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select t.tipo_propiedad, t.exigencia, t.requerido
                  from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.clave = 'estado_ocupacion' and c.organizacion_id is null
                 order by t.tipo_propiedad
                """);

        assertEquals(List.of("A", "C", "D", "L", "O", "T", "X"),
                filas.stream().map(f -> f.get("tipo_propiedad")).toList(),
                "un tipo que falte es un tipo donde la ocupacion no se puede registrar");
        filas.forEach(fila -> {
            assertEquals("OPC", fila.get("exigencia"),
                    "D-1: estado_ocupacion no bloquea nada en este corte");
            assertEquals(Boolean.FALSE, fila.get("requerido"),
                    "`requerido` es espejo exacto de `exigencia = ALT` desde V72");
        });

        assertEquals(Boolean.FALSE, jdbc.queryForObject("""
                select aplica_todos from catalogo_atributo
                 where clave = 'estado_ocupacion' and organizacion_id is null
                """, Boolean.class),
                "D-C5-1 pide filas explicitas: una clave `aplica_todos` no puede despues "
                        + "excluir un tipo sin cambiar de forma");
    }

    /**
     * <b>El par, con asercion propia</b> (D-C5-1 §7).
     *
     * <p>Que la clave exista no basta: el guard 2.2 de V78 compara CONJUNTOS de
     * {@code tipo_propiedad}, y un hecho que llegue a seis tipos mientras su
     * condicion se pacta en siete deja un tipo en el que <b>el pacto es el unico
     * sitio donde cabe el hecho</b>.
     *
     * <p>Se mide en las dos direcciones a proposito: los tipos DESCUBIERTOS y los
     * CUBIERTOS. Solo con la primera, el dia que {@code entrega_desocupado}
     * desapareciera del catalogo la consulta daria cero huecos y el test seguiria
     * verde sobre el universo vacio.
     */
    @Test
    @DisplayName("V84: el par estado_ocupacion / entrega_desocupado queda cubierto en los siete")
    void elParQuedaCubiertoEnLosSiete() {
        List<String> sinHecho = jdbc.queryForList("""
                select distinct o.tipo_propiedad
                  from catalogo_atributo cond
                  join catalogo_atributo_operacion o
                    on o.id_catalogo_atributo = cond.id_catalogo_atributo
                  join catalogo_atributo hecho on hecho.clave = 'estado_ocupacion' and hecho.activo
                 where cond.clave = 'entrega_desocupado' and cond.activo
                   and not exists (select 1 from catalogo_atributo_tipo t
                                    where t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                      and t.tipo_propiedad = o.tipo_propiedad)
                 order by 1
                """, String.class);
        assertEquals(List.of(), sinHecho,
                "el pacto llega a estos tipos y el hecho no: " + sinHecho);

        assertEquals(7, jdbc.queryForObject("""
                select count(distinct o.tipo_propiedad)
                  from catalogo_atributo cond
                  join catalogo_atributo_operacion o
                    on o.id_catalogo_atributo = cond.id_catalogo_atributo
                  join catalogo_atributo hecho on hecho.clave = 'estado_ocupacion' and hecho.activo
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                               and t.tipo_propiedad = o.tipo_propiedad
                 where cond.clave = 'entrega_desocupado' and cond.activo
                """, Integer.class),
                "si esto fuera cero, la comprobacion de arriba habria salido verde sin mirar nada");
    }

    /**
     * <b>El vocabulario decidido, y el que se rechazo.</b>
     *
     * <p>{@code CON_EDIFICACION_A_DEMOLER} no pertenece a esta clave (D-C5-1 §3):
     * mezcla dos ejes —quien ocupa y que hay construido— y obliga a elegir entre
     * dos cosas que pueden ser ciertas a la vez. Extendido a un departamento, la
     * lista ofreceria «con edificacion a demoler» como estado de un piso 12.
     *
     * <p>Y las opciones que publica el contrato son <b>las filas del catalogo</b>:
     * se comparan las dos listas, para que una segunda lista escrita en Java no
     * pudiera divergir en silencio.
     */
    @Test
    @DisplayName("V84: la ocupacion tiene CUATRO estados, y ninguno habla de edificacion")
    void elVocabularioDeLaOcupacionEsElDecidido() {
        assertEquals(List.of("DESOCUPADO", "OCUPADO_POR_EL_PROPIETARIO",
                        "OCUPADO_POR_INQUILINO", "OCUPADO_POR_TERCEROS_SIN_TITULO"),
                vocabulario("estado_ocupacion"),
                "el vocabulario es el de D-C5-1, en su orden");

        var pregunta = preguntaDe("TERRENO", "VENTA", "estado_ocupacion");
        assertNotNull(pregunta.opciones(),
                "sin opciones, `controlDe` la degrada a TEXTO y el vocabulario deja de existir "
                        + "sin que nadie avise: es lo que le paso a servicios_disponibles");
        assertEquals(vocabulario("estado_ocupacion"),
                pregunta.opciones().stream().map(MotorDeCaptura.Opcion::valor).toList(),
                "el contrato publica un vocabulario distinto del que declara el catalogo");
    }

    /**
     * <b>OPC significa que no bloquea, y se comprueba publicando.</b>
     *
     * <p>Con una clave nueva en los siete tipos, el riesgo real no es que falte:
     * es que alguien la suba a ALT o PUB «porque siempre se sabe». Un
     * departamento sin declarar la ocupacion tiene que anunciarse igual que antes
     * del corte.
     */
    @Test
    @DisplayName("V84: no declarar la ocupacion no impide registrar ni publicar")
    void laOcupacionNoBloqueaNada() {
        long id = registrarDepartamento();

        assertNull(valorDe(id, "estado_ocupacion"), "el alta no inventa quien vive dentro");
        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        assertTrue(ficha.faltanParaPublicar().stream()
                        .noneMatch(a -> "estado_ocupacion".equals(a.clave())),
                "una OPC ausente no bloquea: " + ficha.faltanParaPublicar());
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "y el anuncio entra sin ella");

        // Y cuando si se declara, viaja y vuelve.
        editar(id, new ValorAtributo("estado_ocupacion", "OCUPADO_POR_INQUILINO"));
        assertEquals("OCUPADO_POR_INQUILINO", valorDe(id, "estado_ocupacion"));
        assertThrows(Exception.class,
                () -> editar(id, new ValorAtributo("estado_ocupacion", "CON_EDIFICACION_A_DEMOLER")),
                "esa opcion se retiro a proposito de esta clave (D-C5-1 §3)");
    }

    // ==================================================================
    // 2. Los servicios del terreno: la exigencia que estrena el corte
    // ==================================================================

    @Test
    @DisplayName("V84: agua y luz nacen CON vocabulario, y con la exigencia que decidio el titular")
    void losServiciosNacenConVocabularioYExigencia() {
        assertEquals(List.of("CONECTADO", "CON_FACTIBILIDAD_APROBADA", "SIN_SERVICIO"),
                vocabulario("agua_desague"),
                "el tercer estado es el motivo de que estas claves existan: sin el, «tiene agua» "
                        + "y «se lo aprobaron» son la misma respuesta");
        assertEquals(List.of("CONECTADO", "CON_FACTIBILIDAD_APROBADA", "SIN_SERVICIO"),
                vocabulario("energia_electrica"));

        assertEquals(List.of("A=OPC", "T=PUB"), exigenciasDe("agua_desague"),
                "D-1: PUB en T y OPC en A, y en ningun tipo mas");
        assertEquals(List.of("T=PUB"), exigenciasDe("energia_electrica"),
                "D-1: PUB en T, y en ningun tipo mas");

        assertEquals(0, jdbc.queryForObject("""
                select count(*) from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.organizacion_id is null
                   and c.clave in ('agua_desague', 'energia_electrica')
                   and t.requerido
                """, Integer.class),
                "ninguna es ALT: `requerido` es espejo exacto de `exigencia = ALT`");
    }

    /**
     * <b>PUB impide PUBLICAR, no REGISTRAR</b> — las dos mitades, sobre las claves
     * reales y en el mismo caso.
     *
     * <p>Es la leccion que costo V82: {@code ALT} bloquea dos puertas y
     * {@code PUB} solo una. Un terreno del que todavia no se sabe si tiene agua
     * <b>se registra, se edita y se conserva como conocido</b>; lo unico que no
     * puede es salir al mercado.
     *
     * <p>La causa del bloqueo se averigua <b>agregando todas las claves ALT/PUB
     * que faltan</b>, sin nombrar ninguna: filtrar por la clave y luego
     * «descubrir» que la causa es esa clave no demuestra nada.
     */
    @Test
    @DisplayName("V84: un TERRENO sin agua ni luz se registra, se edita y NO se publica")
    void elTerrenoSinServiciosSeRegistraPeroNoSePublica() {
        long id = registrarTerreno();
        assertNull(valorDe(id, "agua_desague"), "el alta no inventa el dato");
        assertNull(valorDe(id, "energia_electrica"), "ni el otro");

        // Se edita OTRA cosa y lo que falta sigue faltando. Se compara por VALOR
        // y no por cadena: la ficha devuelve el decimal normalizado («15.5»), y
        // exigir «15.50» mediria el formateo y no la conservacion.
        editar(id, new ValorAtributo("frente", "15.50"));
        assertEquals(0, new BigDecimal("15.50").compareTo(new BigDecimal(valorDe(id, "frente"))),
                "el frente editado tiene que valer lo escrito");
        assertNull(valorDe(id, "agua_desague"), "editar otro dato no rellena el que falta");

        assertEquals(List.of("agua_desague", "energia_electrica"), bloqueantesDe(id),
                "las dos claves nuevas son la unica causa de bloqueo de este terreno");

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        AtributoQueFalta agua = ficha.faltanParaPublicar().stream()
                .filter(a -> "agua_desague".equals(a.clave())).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "la propiedad tiene que decir que le falta agua_desague para publicar; "
                                + "lleva " + ficha.faltanParaPublicar()));
        assertEquals("Agua y desagüe", agua.rotulo(),
                "el rotulo lo trae el catalogo: con la clave desnuda no es una frase para nadie");
        assertTrue(ficha.atributosQueFaltan().stream()
                        .noneMatch(a -> "agua_desague".equals(a.clave())),
                "`atributosQueFaltan` responde a que impide el ALTA, y PUB no lo impide");

        ReglaNegocioException rechazo = assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "un terreno sin decir si tiene agua y luz no es una oferta");
        assertTrue(rechazo.getMessage().contains("Agua y desagüe"),
                "el rechazo dice QUE falta, con su rotulo: " + rechazo.getMessage());

        // Declarados los dos hechos, el bloqueo desaparece. Uno solo no basta:
        // ese es el sentido de que sean DOS claves y no un campo agregado.
        editar(id, new ValorAtributo("agua_desague", "CON_FACTIBILIDAD_APROBADA"));
        assertEquals(List.of("energia_electrica"), bloqueantesDe(id),
                "con agua declarada sigue faltando la luz: en la periferia se tiene una y no la otra");
        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()));

        editar(id, new ValorAtributo("energia_electrica", "SIN_SERVICIO"));
        assertEquals(List.of(), bloqueantesDe(id));
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "declarados los dos hechos, el anuncio entra");
    }

    /**
     * <b>«Sin servicio» es una respuesta, no la ausencia del dato.</b>
     *
     * <p>Es la distincion que hace legitimo el bloqueo: declarar que el terreno
     * no tiene luz <b>desbloquea</b> la publicacion, porque es un hecho
     * verificado. Lo que no desbloquea es callar. Si fuera al reves, la exigencia
     * empujaria a rellenar en vez de a mirar.
     */
    @Test
    @DisplayName("V84: declarar SIN_SERVICIO desbloquea; callar, no")
    void declararQueNoHayServicioTambienEsDeclarar() {
        long callado = registrarTerreno();
        long declarado = registrarTerreno();

        editar(declarado, new ValorAtributo("agua_desague", "SIN_SERVICIO"));
        editar(declarado, new ValorAtributo("energia_electrica", "SIN_SERVICIO"));

        assertFalse(bloqueantesDe(callado).isEmpty(), "callar no desbloquea");
        assertEquals(List.of(), bloqueantesDe(declarado), "declarar la ausencia si");
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(declarado), publicacionDePrueba(),
                actor()));
    }

    @Test
    @DisplayName("V84: un valor fuera del vocabulario de los servicios se rechaza")
    void losServiciosNoAdmitenCualquierCadena() {
        long id = registrarTerreno();
        assertThrows(Exception.class,
                () -> editar(id, new ValorAtributo("agua_desague", "tiene agua")),
                "es justo lo que `servicios_disponibles` aceptaba por no tener vocabulario");
    }

    // ==================================================================
    // 3. `gas` conserva su concepto y gana una opcion (D-2)
    // ==================================================================

    /**
     * <b>Una opcion que faltaba, no una segunda definicion.</b>
     *
     * <p>Tres documentos pedian para {@code gas} un estado
     * {@code CON_FACTIBILIDAD_APROBADA} que su vocabulario no tenia. La
     * correccion es una fila: la clave, el tipo de dato, la aplicabilidad y los
     * valores ya escritos no se tocan. Y la opcion entra <b>entre</b>
     * {@code RED_EN_LA_VIA} e {@code INSTALADO}, que es su sitio por
     * significado: hay tuberia en la calle -> esta aprobado -> esta instalado.
     */
    @Test
    @DisplayName("V84: gas gana la factibilidad, en su sitio, y no cambia de concepto")
    void gasGanaLaFactibilidadSinCambiarDeConcepto() {
        assertEquals(List.of("SIN_RED_CERCANA", "RED_EN_LA_VIA", "CON_FACTIBILIDAD_APROBADA",
                        "INSTALADO", "GLP_TANQUE_EXTERNO", "GLP_BALONES"),
                vocabulario("gas"),
                "la factibilidad va entre la tuberia de la calle y la instalacion en la puerta");

        assertEquals("LISTA", jdbc.queryForObject("""
                select tipo_dato from catalogo_atributo
                 where clave = 'gas' and organizacion_id is null
                """, String.class));
        assertEquals(List.of("A=OPC", "C=OPC", "D=OPC", "L=OPC", "O=OPC", "T=OPC"),
                exigenciasDe("gas"),
                "D-2: gas conserva su aplicabilidad y NO se extiende a X");

        // Los valores que ya se podian escribir se siguen pudiendo escribir, y el
        // nuevo tambien. Una opcion anadida que rompiera las anteriores seria una
        // migracion de concepto disfrazada.
        long id = registrarDepartamento();
        editar(id, new ValorAtributo("gas", "RED_EN_LA_VIA"));
        assertEquals("RED_EN_LA_VIA", valorDe(id, "gas"));
        editar(id, new ValorAtributo("gas", "CON_FACTIBILIDAD_APROBADA"));
        assertEquals("CON_FACTIBILIDAD_APROBADA", valorDe(id, "gas"));
    }

    // ==================================================================
    // 4. `servicios_disponibles`: retirada, no borrada
    // ==================================================================

    /**
     * <b>La ultima LISTA muda, retirada — y el catalogo ya no admite otra.</b>
     *
     * <p>Esta prueba es la sucesora de
     * {@code CatalogoQueHablaIntegrationTest.serviciosDisponiblesNoSeRompio()},
     * que hasta V84 afirmaba lo contrario: que la clave seguia aceptando texto
     * libre y seguia con cero opciones. Era cierto y era deuda declarada; tras
     * 5A es falso <b>por diseno</b>.
     */
    @Test
    @DisplayName("V84: servicios_disponibles queda retirada, y sigue existiendo")
    void serviciosDisponiblesQuedoRetiradaYNoBorrada() {
        Map<String, Object> clave = jdbc.queryForMap("""
                select activo, del_sistema, tipo_dato from catalogo_atributo
                 where clave = 'servicios_disponibles' and organizacion_id is null
                """);
        assertEquals(Boolean.FALSE, clave.get("activo"), "la pregunta se retira");
        assertEquals(Boolean.TRUE, clave.get("del_sistema"),
                "y la clave sigue siendo del sistema: no se apropia ni se borra");

        // Y su aplicabilidad sigue ahi. Borrarla haria imposible saber a que tipo
        // pertenecia el legado que quedo escrito.
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.clave = 'servicios_disponibles' and c.organizacion_id is null
                   and t.tipo_propiedad = 'T'
                """, Integer.class));

        // Deja de preguntarse en el alta y en el editor.
        assertTrue(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA", actor())
                        .todas().stream()
                        .noneMatch(p -> "servicios_disponibles".equals(p.clave())),
                "una clave retirada no se sigue preguntando");

        // Y no admite valores nuevos. Un concepto retirado no sigue capturando.
        //
        // OJO CON LA CAPA: por esta via -- `PropiedadUniversalService.editar`, la
        // misma que usa el PUT -- quien rechaza es el CORE en Java:
        // `AtributosGobernados.definicionDe` -> `CatalogoAtributoRepository.porClave`,
        // cuyo JPQL lleva `and c.activo = true`, y sale una `ReglaNegocioException`
        // que la API mapea a 400. `exigir_atributo_gobernado` exige `activo = true`
        // tambien, pero es la red de atras: solo actua contra SQL directo, y por
        // esta llamada NO llega a ejecutarse. Este comentario decia lo contrario y
        // se corrigio el 2026-08-26 tras medirlo por HTTP (evidencia de 5A, §15).
        //
        // El `assertThrows(Exception.class, ...)` es deliberadamente ancho: fija que
        // el rechazo EXISTE y que no deja rastro, no en que capa ocurre ni con que
        // codigo llega. Que eso se fije es deuda registrada en
        // `pendientes-brox.md` §2.3 ter, y su alcance lo decide CONTROL.
        long id = registrarTerreno();
        assertThrows(Exception.class,
                () -> editar(id, new ValorAtributo("servicios_disponibles", "agua y luz")));
        assertNull(valorDe(id, "servicios_disponibles"), "y no dejo rastro");

        // PERO LA LECTURA SIGUE SABIENDO COMO SE LLAMA. Sobre la clave REAL, y
        // sin depender de que la base traiga legado: en `controllocal_dev` no
        // hay ninguna fila y en la base de pruebas hay las que dejaron las
        // corridas anteriores, asi que un caso que las necesitara seria verde y
        // vacio en la mitad de las bases. El tamano del legado se afirma como
        // INVARIANTE y nunca como cifra —una cifra caduca en cuanto corre una
        // suite—. Lo que se afirma es la consulta que la ficha usa
        // para completar las retiradas.
        List<CatalogoAtributo> paraLeer = catalogo.paraLeer(actor().idOrganizacion(),
                List.of("servicios_disponibles"));
        assertEquals(1, paraLeer.size(),
                "la definicion de una clave retirada tiene que seguir resolviendose para LEER");
        assertEquals("Servicios disponibles", paraLeer.get(0).getRotulo());
        assertEquals("LISTA", paraLeer.get(0).getTipoDato(),
                "sin tipo, el consumidor no sabe como pintar el valor conservado");
        assertFalse(paraLeer.get(0).isActivo(),
                "y sigue retirada: la lectura no la reactiva");
    }

    /**
     * <b>Retirar una clave no oculta lo que ya se sabia</b> — el mecanismo que
     * hace segura la retirada de {@code servicios_disponibles}.
     *
     * <p>Se prueba sobre una clave del tenant creada aqui, y no sobre la real,
     * a proposito: la real ya no admite escrituras —esa es justo la otra mitad
     * del contrato— asi que no hay forma de fabricarle legado despues de V84, y
     * un caso que dependiera del legado que traiga la base seria verde y vacio en
     * una base limpia. El legado real de `controllocal_repositorios` —el que
     * haya; se afirma como invariante y no como cifra, porque el fixture de
     * {@code sembrarLegadoAmbiguo} anade filas en cada corrida— lo conserva la
     * migracion, cuyo bloque 8.8 lo compara por conjunto contra una foto del
     * antes.
     *
     * <p>Lo que se afirma: el valor <b>se sigue leyendo</b> en la ficha despues
     * de retirar su clave. {@code LectorPorAutoridad} lee las filas del inmueble
     * sin preguntar si su clave sigue activa. Si la lectura filtrara por catalogo
     * activo, retirar una clave <b>borraria de la vista</b> todo lo capturado con
     * ella.
     *
     * <h2>Y se lee ENTERO, no solo el valor</h2>
     *
     * <p>Hasta la correccion de 5A esto se cumplia a medias, y la mitad que
     * faltaba la encontro la auditoria: {@code definicionesDe} sale de
     * {@code aplicablesA}, que filtra {@code activo = true}, asi que la ficha
     * devolvia {@code rotulo = "servicios_disponibles"} y {@code tipoDato = null}
     * —la <b>clave desnuda</b>, y al final de la lista—. El broker leia
     * exactamente el defecto que este repositorio ya nombra en
     * {@code propiedad-detail.html}: «falta metraje_total», que no es una frase
     * para nadie. Conservar el valor y perder su nombre es conservar a medias.
     *
     * <p>Las dos mitades se comprueban aqui: la LECTURA resuelve rotulo y tipo
     * aunque la clave este inactiva, y la CAPTURA —el alta y el editor— deja de
     * ofrecerla en el mismo instante.
     */
    @Test
    @DisplayName("V84: retirar una clave conserva sus valores, su rotulo y su tipo — y deja de preguntarla")
    void retirarUnaClaveNoOcultaSusValores() {
        String clave = "zz_retirable_" + UUID.randomUUID().toString().substring(0, 8);
        long org = actor().idOrganizacion();
        // Con FILAS por tipo, no con `aplica_todos`. Nacio con el campo y sin
        // una sola fila -- era la unica clave del arbol que dependia
        // EXCLUSIVAMENTE de el--, y desde V86 el campo no decide aplicabilidad:
        // asi declarada, la clave no se preguntaria en ningun tipo y el caso
        // mediria la retirada de algo que nunca se ofrecio.
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden)
                values (?, ?, 'Clave retirable', 'TEXTO', false, false, 990)
                """, org, clave);
        declararAplicabilidadEnLosSiete(clave, org);
        try {
            long id = registrarTerreno();
            editar(id, new ValorAtributo(clave, "lo que se sabia"));
            assertEquals("lo que se sabia", valorDe(id, clave));
            assertTrue(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA",
                            actor()).todas().stream().anyMatch(p -> clave.equals(p.clave())),
                    "mientras esta activa, se pregunta: si no, el caso no mediria la retirada");

            jdbc.update("update catalogo_atributo set activo = false where clave = ? "
                    + "and organizacion_id = ?", clave, org);

            assertEquals("lo que se sabia", valorDe(id, clave),
                    "retirar la clave no puede borrar de la vista lo que ya se habia capturado: "
                            + "el dato acumula, y la pregunta es lo unico que se retira");
            assertEquals(1, jdbc.queryForObject("""
                    select count(*) from atributo_propiedad where id_propiedad = ? and clave = ?
                    """, Integer.class, id, clave),
                    "y la fila sigue donde estaba");

            // MITAD 1 — la LECTURA resuelve el catalogo aunque la clave este inactiva.
            PropiedadUniversalService.AtributoFicha ficha = fichaDe(id, clave);
            assertNotNull(ficha, "el valor tiene que seguir llegando a la ficha");
            assertEquals("Clave retirable", ficha.rotulo(),
                    "una clave retirada se lee con su nombre, no como clave desnuda: quien lee "
                            + "la ficha no tiene por que saber que la pregunta se retiro");
            assertEquals("TEXTO", ficha.tipoDato(),
                    "y con su tipo: el SPA decide con `tipoDato` como pintar el valor, asi que "
                            + "perderlo degrada la lectura mas alla del rotulo");

            // MITAD 2 — la CAPTURA no la ofrece. La conservacion de la lectura no
            // puede haber reabierto ninguna puerta de escritura.
            assertTrue(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA",
                            actor()).todas().stream().noneMatch(p -> clave.equals(p.clave())),
                    "la clave retirada no se vuelve a preguntar en el alta ni en el editor");
        } finally {
            jdbc.update("update catalogo_atributo set activo = false where clave = ? "
                    + "and organizacion_id = ?", clave, org);
        }
    }

    /**
     * <b>La misma pregunta, en la otra mitad del catalogo</b> — retirar una
     * CONDICION del encargo tampoco puede degradar lo pactado.
     *
     * <p>Esta prueba llega con la correccion de la auditoria del 2026-08-25.
     * {@code AtributosDeEncargo.definicionesParaLeer} nacio en 5A «por simetria»
     * y <b>sin una sola prueba</b>: la clave que 5A retira es de la PROPIEDAD, y
     * una simetria que nadie ejecuta es una simetria que se cree, no una que se
     * sabe. La regla de este repositorio es que el cambio viaja con sus pruebas.
     *
     * <p>Y no es teorico: una condicion pactada vive en encargos que ya se
     * cerraron. Si retirar la pregunta borrara el rotulo, un encargo de 2024 se
     * leeria con la clave desnuda para siempre, sin que nadie pueda ya
     * arreglarlo escribiendo el dato de nuevo — porque el encargo esta cerrado.
     */
    @Test
    @DisplayName("V84: retirar una CONDICION del encargo conserva lo pactado, con su rotulo y su tipo")
    void retirarUnaCondicionNoOcultaLoPactado() {
        String clave = "zz_condicion_" + UUID.randomUUID().toString().substring(0, 8);
        long org = actor().idOrganizacion();
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden, sujeto)
                values (?, ?, 'Condicion retirable', 'TEXTO', false, false, 990, 'ENCARGO')
                """, org, clave);
        jdbc.update("""
                insert into catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                                         tipo_operacion, exigencia)
                select c.id_catalogo_atributo, t.tipo, 'V', 'OPC'
                  from catalogo_atributo c
                  cross join (values ('L'),('O'),('D'),('C'),('T'),('A'),('X')) as t(tipo)
                 where c.clave = ? and c.organizacion_id = ?
                """, clave, org);
        try {
            long id = registrarTerreno();
            long encargo = encargoDe(id);
            propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null,
                    null, null,
                    List.of(new PropiedadUniversalService.CondicionesDeEncargo(encargo,
                            List.of(new ValorAtributo(clave, "lo que se pacto")), null))), actor());
            assertEquals("lo que se pacto", condicionDe(id, encargo, clave).valor(),
                    "mientras la condicion esta activa se pacta: si no, el caso no mediria "
                            + "la retirada");

            jdbc.update("update catalogo_atributo set activo = false where clave = ? "
                    + "and organizacion_id = ?", clave, org);

            PropiedadUniversalService.AtributoFicha pactado = condicionDe(id, encargo, clave);
            assertNotNull(pactado, "lo pactado en un encargo no desaparece porque la condicion "
                    + "deje de pactarse: el encargo ya cerrado no se puede volver a llenar");
            assertEquals("lo que se pacto", pactado.valor());
            assertEquals("Condicion retirable", pactado.rotulo(),
                    "una condicion retirada se lee con su nombre, no como clave desnuda -- "
                            + "que es exactamente el defecto que 5A arreglo en la PROPIEDAD");
            assertEquals("TEXTO", pactado.tipoDato(),
                    "y con su tipo: sin el, el SPA no sabe como pintar lo pactado");

            assertTrue(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA",
                            actor()).todas().stream().noneMatch(p -> clave.equals(p.clave())),
                    "la condicion retirada no se vuelve a pactar: conservar la lectura no "
                            + "reabre la escritura");
        } finally {
            jdbc.update("update catalogo_atributo set activo = false where clave = ? "
                    + "and organizacion_id = ?", clave, org);
        }
    }

    /** Una condicion de UN encargo, leida por la ficha tal como llega al cable. */
    private PropiedadUniversalService.AtributoFicha condicionDe(long idPropiedad, long idEncargo,
                                                                String clave) {
        return propiedades.consultar(idPropiedad, actor()).encargos().stream()
                .filter(e -> e.idEncargo() != null && e.idEncargo() == idEncargo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("el encargo " + idEncargo + " no esta en la ficha"))
                .condiciones().stream()
                .filter(c -> clave.equals(c.clave()))
                .findFirst().orElse(null);
    }

    // ==================================================================
    // 5. El legado y su procedencia
    // ==================================================================

    /**
     * <b>Lo ambiguo permanece FALTANTE, y no se traduce.</b>
     *
     * <p>El legado de {@code servicios_disponibles} es texto libre —la clave era
     * LISTA sin opciones, asi que acepto cualquier cadena— y ninguna de las
     * cadenas medidas dice lo unico que las claves nuevas existen para capturar:
     * si el servicio esta <b>conectado</b> o solo tiene <b>factibilidad
     * aprobada</b>. Traducir «tiene agua» a {@code CONECTADO} seria inventar por
     * el caso frecuente justo la distincion que el campo viejo no sabia hacer.
     *
     * <p>Se afirma como INVARIANTE y nunca como la cifra 0.
     *
     * <h2>Y el caso SE FABRICA SU PROPIO UNIVERSO</h2>
     *
     * <p>Aqui habia escrito que «en la base de pruebas un fixture lo escribe en
     * cada corrida». Era <b>falso</b>, y lo midio la auditoria del 2026-08-25: el
     * unico productor de {@code servicios_disponibles} era el fixture de
     * {@code ConservacionDeLaEdicionIntegrationTest}, y este mismo corte lo
     * retiro al retirar la clave. Las filas que hay en
     * {@code controllocal_repositorios} son <b>residuo historico</b> —cuantas
     * sean; el tamano no se escribe como cifra porque cada corrida de este
     * mismo caso deja dos mas—: sobre una base nueva —CI, otra maquina, un
     * {@code docker volume rm}— el universo es CERO y este caso saldria verde
     * sin haber mirado nada.
     *
     * <p>Por eso el caso <b>siembra</b> lo que necesita mirar —un legado ambiguo
     * y, sobre el mismo inmueble, un servicio declarado por la ruta normal, que
     * deja linaje—, <b>declara</b> cuantas filas vio y ademas comprueba con un
     * <b>control positivo</b> que la consulta caza el caso prohibido. Un cero que
     * no se ha contrastado con un control positivo no es un cero.
     *
     * <h2>El predicado dice «nadie lo afirmo», y no se aproxima con el canal</h2>
     *
     * <p>La primera version exigia un rastro con {@code canal <> 'SISTEMA'}, y
     * eso <b>prohibia el unico mecanismo autorizado</b> para mover legado: el
     * reparto del acta (bloque 5 de {@code V84}) escribe {@code canal = 'SISTEMA'}
     * con su {@code evidencia_ref}. Salia verde solo mientras el acta no
     * resolviera ninguna cadena, y se habria puesto roja el dia que resolviera
     * una —por comportarse bien—. Lo mide la auditoria del 2026-08-25.
     *
     * <p>Lo que separa una traduccion clandestina de un reparto legitimo es el
     * <b>linaje</b>: el acta deja su {@code evidencia_ref}, una persona deja su
     * {@code id_persona_rol} —y su {@code naturaleza} si la sabe—. Un valor
     * sobre un legado ambiguo sin ninguna de las tres no lo afirmo nadie.
     * Predicado <b>identico</b> al de «5A ningun inmueble con legado recibio
     * un servicio sin que nadie lo afirmara», la comprobacion del gate: dos
     * formas distintas de la misma pregunta vuelven a divergir. Se cita por su
     * NOMBRE y no por su posicion en el informe -- una comprobacion nueva mas
     * arriba mueve el numero y deja el puntero senalando a otra cosa (N30).
     */
    @Test
    @DisplayName("V84: ningun inmueble con legado recibio un servicio que nadie afirmo")
    void elLegadoNoSeTradujo() {
        // EL PRODUCTOR. Un terreno con legado ambiguo Y con un servicio nuevo
        // declarado por la ruta normal: es el par que la consulta examina, y sale
        // limpio porque la declaracion dejo su linaje.
        long conLegado = registrarTerreno();
        sembrarLegadoAmbiguo(conLegado);
        propiedades.editar(conLegado, new ComandoEdicion(null, null, null, null, null,
                List.of(new ValorAtributo("agua_desague", "CONECTADO", null, null,
                        "OBSERVADO", null, null, null)),
                null, null), actor());

        // LA COBERTURA, declarada. No se exige una cifra concreta de residuo -
        // eso volveria a atar la prueba a una base concreta -, se exige que el
        // universo que la consulta recorre no este vacio.
        long filasDeLegado = contar("select count(*) from atributo_propiedad "
                + "where clave = 'servicios_disponibles'");
        long pares = contar("""
                select count(*)
                  from atributo_propiedad legado
                  join atributo_propiedad nuevo on nuevo.id_propiedad = legado.id_propiedad
                                               and nuevo.clave in ('agua_desague', 'energia_electrica')
                 where legado.clave = 'servicios_disponibles'
                """);
        String universo = filasDeLegado + " filas de legado, " + pares + " pares legado/servicio";
        assertTrue(pares > 0,
                "el universo de la comprobacion esta VACIO y su verde no significaria nada: "
                        + universo);

        List<String> traducidos = legadoTraducidoSinLinaje();
        assertEquals(List.of(), traducidos,
                "un inmueble cuyo unico dato de servicios era una cadena ambigua no puede "
                        + "aparecer con el hecho ya declarado sin que nadie lo declarara "
                        + "(universo: " + universo + "): " + traducidos);

        // EL CONTROL POSITIVO. El mismo par, pero con el servicio escrito por SQL
        // directo - sin pasar por el Core, asi que sin rastro -. Si la consulta no
        // lo caza, es que esta ciega y su verde no significa nada.
        long sinLinaje = registrarTerreno();
        sembrarLegadoAmbiguo(sinLinaje);
        String codigo = jdbc.queryForObject("select codigo from propiedad where id_propiedad = ?",
                String.class, sinLinaje);
        try {
            jdbc.update("""
                    insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
                    select organizacion_id, id_propiedad, 'agua_desague', 'CONECTADO'
                      from propiedad where id_propiedad = ?
                    """, sinLinaje);
            assertTrue(legadoTraducidoSinLinaje().stream().anyMatch(t -> t.startsWith(codigo + " ->")),
                    "la consulta no caza un servicio escrito sobre un legado ambiguo sin ningun "
                            + "linaje: entonces su verde no significa nada");
        } finally {
            // Se retira: si se quedara, «4P despues del cutover ningun hecho del
            // inmueble sin linaje» veria un defecto que
            // introdujo esta prueba, y envenenaria toda medicion posterior.
            jdbc.update("delete from atributo_propiedad where id_propiedad = ? "
                    + "and clave = 'agua_desague'", sinLinaje);
        }
        assertEquals(List.of(), legadoTraducidoSinLinaje(),
                "el control positivo tiene que dejar la base como la encontro");
    }

    /**
     * Los pares legado/servicio que nadie declaro, en la forma en que se leen.
     *
     * <p>Una sola definicion para la invariante y para su control positivo: dos
     * copias de la misma pregunta divergen, y entonces el control deja de vigilar
     * lo que dice vigilar. Predicado <b>identico</b> al de
     * {@code pg_temp.hay_legado_traducido_sin_linaje()} en el gate SQL.
     */
    private List<String> legadoTraducidoSinLinaje() {
        return jdbc.queryForList("""
                select p.codigo || ' -> ' || nuevo.clave || ' = ' || nuevo.valor_texto
                  from atributo_propiedad legado
                  join propiedad p on p.id_propiedad = legado.id_propiedad
                  join atributo_propiedad nuevo on nuevo.id_propiedad = legado.id_propiedad
                                               and nuevo.clave in ('agua_desague', 'energia_electrica')
                 where legado.clave = 'servicios_disponibles'
                   and not exists (select 1 from rastro_valor_gobernado r
                                    where r.organizacion_id = nuevo.organizacion_id
                                      and r.sujeto = 'PROPIEDAD'
                                      and r.id_agregado = nuevo.id_propiedad
                                      and r.clave = nuevo.clave
                                      and (r.naturaleza is not null
                                        or r.evidencia_ref is not null
                                        or r.id_persona_rol is not null))
                 order by 1
                """, String.class);
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    /**
     * <b>Todo valor de las claves nuevas lleva su linaje</b> (4.P, V83).
     *
     * <p>El reparto del legado se escribe con procedencia, y la escritura normal
     * tambien. Se comprueba lo segundo —que si se puede provocar aqui— y ademas
     * que no existe en la base <b>ningun</b> valor de las dos claves posterior al
     * cutover sin linaje.
     */
    @Test
    @DisplayName("V84: un servicio declarado deja linaje, con su naturaleza y sin inventarla")
    void elServicioDeclaradoDejaLinaje() {
        long id = registrarTerreno();
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(new ValorAtributo("agua_desague", "CONECTADO", null, null,
                        "OBSERVADO", null, null, null)),
                null, null), actor());

        Map<String, Object> rastro = jdbc.queryForMap("""
                select verbo, valor_texto, naturaleza, canal
                  from rastro_valor_gobernado
                 where organizacion_id = ? and sujeto = 'PROPIEDAD' and id_agregado = ?
                   and clave = 'agua_desague'
                 order by id_rastro desc limit 1
                """, actor().idOrganizacion(), id);
        assertEquals("ALTA", rastro.get("verbo"));
        assertEquals("CONECTADO", rastro.get("valor_texto"));
        assertEquals("OBSERVADO", rastro.get("naturaleza"),
                "quien lo capturo dijo que lo vio, y eso no se deduce del canal");

        assertEquals(0, jdbc.queryForObject("""
                select count(*) from atributo_propiedad a
                 where a.clave in ('agua_desague', 'energia_electrica')
                   and a.fecha_creacion > frontera_de_linaje()
                   and not exists (select 1 from rastro_valor_gobernado r
                                    where r.organizacion_id = a.organizacion_id
                                      and r.sujeto = 'PROPIEDAD'
                                      and r.id_agregado = a.id_propiedad
                                      and r.clave = a.clave)
                """, Integer.class),
                "despues del cutover, un valor gobernado sin linaje es un defecto");
    }

    // ==================================================================
    // 6. La guarda que 5A deja puesta
    // ==================================================================

    /**
     * <b>Ninguna LISTA activa se queda muda, ya de ningun sujeto.</b>
     *
     * <p>Es el espejo en Java de la guarda que V84 deja en la migracion y del
     * bloque que entra en {@code gate-modelo-universal.sql}. La palabra que hace
     * legitimo retirar {@code servicios_disponibles} en vez de inventarle
     * vocabulario es <b>activa</b>: una clave retirada no se pregunta, asi que no
     * puede nacer muda.
     */
    @Test
    @DisplayName("V84: ninguna LISTA activa del catalogo se quedo sin vocabulario")
    void ningunaListaActivaSeQuedoMuda() {
        List<String> mudas = jdbc.queryForList("""
                select c.sujeto || '/' || c.clave
                  from catalogo_atributo c
                 where c.activo and c.tipo_dato in ('LISTA', 'LISTA_MULTIPLE')
                   and not exists (select 1 from catalogo_atributo_opcion o
                                    where o.id_catalogo_atributo = c.id_catalogo_atributo
                                      and o.activo)
                 order by 1
                """, String.class);
        assertEquals(List.of(), mudas,
                "una LISTA sin opciones se degrada a TEXTO en el motor de captura y el trigger "
                        + "acepta cualquier cadena: la clave nace muda y nadie lo ve. " + mudas);

        // Control positivo: la consulta de arriba SI caza una lista muda. Sin
        // esto, un `order by` mal puesto o un filtro de mas la dejaria devolviendo
        // siempre vacio y el caso seria verde sin mirar nada.
        String clave = "zz_muda_" + UUID.randomUUID().toString().substring(0, 8);
        long org = actor().idOrganizacion();
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden)
                values (?, ?, 'Lista muda de control', 'LISTA', false, false, 991)
                """, org, clave);
        declararAplicabilidadEnLosSiete(clave, org);
        try {
            assertTrue(jdbc.queryForList("""
                    select c.sujeto || '/' || c.clave
                      from catalogo_atributo c
                     where c.activo and c.tipo_dato in ('LISTA', 'LISTA_MULTIPLE')
                       and not exists (select 1 from catalogo_atributo_opcion o
                                        where o.id_catalogo_atributo = c.id_catalogo_atributo
                                          and o.activo)
                    """, String.class).contains("PROPIEDAD/" + clave),
                    "la consulta no caza una lista muda: entonces su verde no significa nada");
        } finally {
            jdbc.update("update catalogo_atributo set activo = false where clave = ? "
                    + "and organizacion_id = ?", clave, org);
        }
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * <b>El productor del legado, repuesto</b> (correccion de la auditoria del
     * 2026-08-25).
     *
     * <p>Escribe sobre {@code idPropiedad} la cadena ambigua que
     * {@code servicios_disponibles} aceptaba cuando era una LISTA sin opciones.
     * Es la misma que escribia el fixture de
     * {@code ConservacionDeLaEdicionIntegrationTest} hasta {@code V84}, y sin ella
     * ninguna prueba de esta suite tiene nada que mirar sobre una base nueva.
     *
     * <h2>Por que hace falta saltarse la puerta, y por que es seguro</h2>
     *
     * <p>La clave esta {@code activo = false}, asi que
     * {@code exigir_atributo_gobernado} rechaza tambien el INSERT directo —no la
     * encuentra en el catalogo y sale por {@code SQLSTATE 23503}—. Para escribir
     * su legado hay que <b>reactivarla, escribir y volver a retirarla</b>. Las
     * tres sentencias van dentro de un {@code DO}, que es <b>una sola
     * transaccion</b>: ninguna otra sesion llega a ver la clave activa, y si algo
     * falla a mitad no queda reabierta. Que sembrar el legado exija esta maniobra
     * es, en si mismo, la prueba de que la puerta esta cerrada.
     *
     * <h2>Y la fila nace ANTES de la frontera del linaje, porque eso es lo que un
     * legado es</h2>
     *
     * <p>La primera version dejaba el {@code DEFAULT now()}, y la comprobacion
     * «4P despues del cutover ningun hecho del inmueble sin linaje»
     * se puso <b>roja</b> sobre {@code controllocal_repositorios}: seis
     * filas sembradas por este fixture, posteriores a la frontera y sin rastro.
     * Tenia razon. Un valor que aparece <b>despues</b> del cutover sin que nadie
     * lo declare es un defecto real, y fabricarlo aqui habria envenenado la
     * medicion de otro gate con un dato imposible.
     *
     * <p>El legado real es, por definicion, <b>anterior</b> al mecanismo de
     * linaje. Asi que el fixture escribe con esa fecha, y con eso deja de mentir
     * en dos direcciones a la vez. <b>Aqui no va la cifra</b> de cuantas filas de
     * legado hay: la mide el propio caso antes de afirmar nada, y el gate la
     * imprime en la columna {@code nota} de «5A CONTROL el predicado del legado
     * caza una traduccion sin linaje». Escrita aqui como
     * estado presente naceria caducada -- cada corrida de esta suite la mueve --,
     * y esta casa ya pago esa leccion.
     *
     * <h2>Y la propiedad se envejece con ella</h2>
     *
     * <p>La propiedad se acaba de crear por {@code registrarTerreno()}, o sea
     * <b>despues</b> de la frontera. Fechar solo el atributo dejaba la fila del
     * legado <b>anterior a su propia propiedad</b>: un imposible temporal --las
     * unicas cuatro filas asi de toda la tabla el 2026-08-25-- que hoy no mira
     * ningun gate hasta D0, y esquivar el dato imposible que «4P despues del
     * cutover ningun hecho del inmueble sin linaje» <i>si</i> ve fabricando
     * otro que nadie ve no es esquivarlo (auditoria del 2026-08-25, N13). El
     * {@code DO} envejece tambien {@code fecha_registro}, un dia antes que el
     * legado, y el orden queda entero: propiedad -> legado -> frontera.
     *
     * <p><b>Lo que eso cuesta, dicho</b>: la propiedad sembrada sale del universo
     * de «4P despues del cutover ninguna columna estructural sin linaje», que se
     * mide sobre las registradas <i>despues</i> del cutover. Es coherente --una
     * propiedad con legado previo al cutover es, precisamente, anterior al
     * cutover-- y no tapa nada: <b>los demas</b> {@code registrarTerreno()} de
     * esta suite --todos los que no pasan por este fixture-- siguen entrando en
     * ese universo.
     *
     * <h2>Dos cosas que este javadoc afirmaba y no eran ciertas</h2>
     *
     * <p><b>Citaba las dos comprobaciones por su NUMERO DE ORDEN</b> --«la 76»,
     * «la 78»-- y el orden lo decide el fichero del gate: basta con anadir una
     * comprobacion mas arriba para que el puntero senale a otra. D0 le anadio
     * cuatro controles positivos al bloque 4.P y las dos se movieron. Se citan
     * por su NOMBRE, que es lo unico estable, y el gate ancla esos nombres en
     * «INFORME las guardas que otros artefactos citan siguen ahi»: renombrarlos
     * sale rojo en vez de dejar este javadoc apuntando a nada.
     *
     * <p><b>Y decia «los otros SIETE {@code registrarTerreno()}»</b>, que eran
     * ocho: la clase tiene diez sitios de llamada y solo dos pasan por este
     * fixture. La cifra no se corrige por otra --la siguiente llamada que alguien
     * anada la vuelve a caducar, y nada avisa--: se dice «los demas», que es lo
     * que se queria decir y no depende de contar.
     *
     * <p>Las dos son la misma enfermedad -- documentacion viva apoyada en
     * posiciones y en cifras contadas a mano -- y la misma que ya costo `N21`
     * (auditoria del 2026-08-29, `N30`; corregido en D0 el 2026-08-30).
     */
    private void sembrarLegadoAmbiguo(long idPropiedad) {
        jdbc.execute("""
                do $sembrar$
                begin
                    update catalogo_atributo set activo = true
                     where clave = 'servicios_disponibles' and organizacion_id is null;

                    -- La propiedad, ANTES que su legado y los dos antes de la frontera.
                    update propiedad set fecha_registro = frontera_de_linaje() - interval '2 days'
                     where id_propiedad = %1$d;

                    insert into atributo_propiedad (organizacion_id, id_propiedad, clave,
                                                    valor_texto, fecha_creacion)
                    select organizacion_id, id_propiedad, 'servicios_disponibles',
                           'Agua, luz y desague', frontera_de_linaje() - interval '1 day'
                      from propiedad where id_propiedad = %1$d;

                    update catalogo_atributo set activo = false
                     where clave = 'servicios_disponibles' and organizacion_id is null;
                end $sembrar$;
                """.formatted(idPropiedad));

        assertEquals(1L, contar("select count(*) from atributo_propiedad "
                        + "where clave = 'servicios_disponibles' and id_propiedad = " + idPropiedad),
                "el productor del legado no escribio nada: la prueba que dependa de el "
                        + "saldria verde sobre un universo vacio");
        assertEquals(0L, contar("""
                        select count(*) from atributo_propiedad a
                          join propiedad p on p.id_propiedad = a.id_propiedad
                         where a.id_propiedad = %d and a.clave = 'servicios_disponibles'
                           and (a.fecha_creacion < p.fecha_registro
                                or a.fecha_creacion > frontera_de_linaje())
                        """.formatted(idPropiedad)),
                "el legado sembrado no cabe en su propia linea de tiempo: tiene que ser "
                        + "posterior a su propiedad y anterior a la frontera del linaje");
        assertEquals(Boolean.FALSE, jdbc.queryForObject("""
                select activo from catalogo_atributo
                 where clave = 'servicios_disponibles' and organizacion_id is null
                """, Boolean.class),
                "sembrar el legado dejo la clave REABIERTA: eso deshace la retirada de V84");
    }

    /** El vocabulario del sistema para una clave, en el orden que declara. */
    private List<String> vocabulario(String clave) {
        return jdbc.queryForList("""
                select o.valor from catalogo_atributo_opcion o
                  join catalogo_atributo c on c.id_catalogo_atributo = o.id_catalogo_atributo
                 where c.clave = ? and c.organizacion_id is null and o.activo
                 order by o.orden
                """, String.class, clave);
    }

    /** La aplicabilidad del sistema para una clave, como `TIPO=EXIGENCIA`. */
    private List<String> exigenciasDe(String clave) {
        return jdbc.queryForList("""
                select t.tipo_propiedad || '=' || t.exigencia
                  from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.clave = ? and c.organizacion_id is null
                 order by t.tipo_propiedad
                """, String.class, clave);
    }

    /**
     * Las claves que hoy impiden publicar esta propiedad, agregadas del catalogo
     * y sin nombrar ninguna. Preguntar por una clave concreta y luego
     * "descubrir" que la causa es esa clave no demuestra nada.
     */
    private List<String> bloqueantesDe(long idPropiedad) {
        return jdbc.queryForList("""
                select c.clave
                  from propiedad p
                  join catalogo_atributo c
                    on c.activo and c.destino = 'ATRIBUTO'
                   and (c.organizacion_id is null or c.organizacion_id = p.organizacion_id)
                  join catalogo_atributo_tipo t
                    on t.id_catalogo_atributo = c.id_catalogo_atributo
                   and t.tipo_propiedad = p.tipo_inmueble
                 where p.id_propiedad = ?
                   and t.exigencia in ('ALT', 'PUB')
                   and not exists (select 1 from atributo_propiedad a
                                    where a.id_propiedad = p.id_propiedad and a.clave = c.clave)
                 order by c.clave
                """, String.class, idPropiedad);
    }

    private MotorDeCaptura.Pregunta preguntaDe(String tipo, String operacion, String clave) {
        return captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, operacion, actor())
                .todas().stream()
                .filter(p -> clave.equals(p.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "el motor no publica `" + clave + "` para " + tipo));
    }

    private void editar(long id, ValorAtributo valor) {
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(valor), null, null), actor());
    }

    private String valorDe(long id, String clave) {
        PropiedadUniversalService.AtributoFicha ficha = fichaDe(id, clave);
        return ficha == null ? null : ficha.valor();
    }

    /** El atributo ENTERO tal como lo lee un consumidor: rotulo y tipo incluidos. */
    private PropiedadUniversalService.AtributoFicha fichaDe(long id, String clave) {
        return propiedades.consultar(id, actor()).atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .findFirst().orElse(null);
    }

    /**
     * Un terreno con lo minimo del alta y SIN los dos servicios. Lleva
     * {@code zonificacion} porque en un terreno es ALT desde antes de este corte:
     * sin ella el alta se rechazaria y el caso mediria otra cosa.
     *
     * <p><b>Y lleva {@code condicion_terreno} desde `V85`</b>, que es la TERCERA
     * {@code PUB} del terreno (D-3, subtanda 5B). No es una concesion ni un
     * relleno: los casos de esta clase afirman que <b>los dos servicios son la
     * unica causa de bloqueo</b> de este terreno, y esa afirmacion solo se
     * sostiene si lo demas esta satisfecho. Sin esta linea, 5B habria puesto en
     * rojo tres casos de 5A por una razon que no tiene nada que ver con lo que
     * miden — y "arreglarlo" aflojando la lista esperada a "contiene
     * agua_desague" habria convertido una igualdad en una inclusion, que es
     * justo la forma de debilitar una prueba sin que se note.
     *
     * <p>Que este metodo tenga que crecer cada vez que el terreno estrena una
     * {@code PUB} es correcto y es la señal: el dia que alguien anada una cuarta
     * sin tocar esto, estos casos se lo diran.
     */
    private long registrarTerreno() {
        return registrar("TERRENO",
                List.of(new ValorAtributo("metraje_total", "500"),
                        new ValorAtributo("zonificacion", "RDM"),
                        new ValorAtributo("condicion_terreno", "URBANO_HABILITADO")),
                new OperacionSolicitada("VENTA", new BigDecimal("300000"), "USD",
                        null, null, null, null, null, null, null));
    }

    private long registrarDepartamento() {
        return registrar("DEPARTAMENTO",
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null));
    }

    private long registrar(String tipo, List<ValorAtributo> atributos, OperacionSolicitada operacion) {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, tipo, null,
                "Caso 5A " + tipo,
                new Ubicacion("Av. Corte 5 " + UUID.randomUUID(), "Lurin",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                atributos, List.of(operacion), null), actor).idPropiedad();
    }

    private long encargoDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select min(id_captacion) from captacion where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    /**
     * <b>La aplicabilidad de una clave de prueba, declarada como la del
     * catalogo real</b> (V86).
     *
     * <p>Estas dos claves nacian con {@code aplica_todos = true} y sin una sola
     * fila por tipo: era el atajo que la doble autoridad permitia, y eran las
     * unicas del arbol que dependian del campo para aplicar a algo. Con la
     * tabla como unica autoridad, el atajo no existe -- ni aqui, donde ademas
     * habria dejado el caso midiendo la retirada de una pregunta que nunca se
     * hizo.
     */
    private void declararAplicabilidadEnLosSiete(String clave, long org) {
        jdbc.update("""
                insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                    requerido, exigencia)
                select c.id_catalogo_atributo, t.tipo, false, 'OPC'
                  from catalogo_atributo c
                  cross join tipos_de_propiedad() as t(tipo)
                 where c.clave = ? and c.organizacion_id = ?
                """, clave, org);
    }

    private PublicacionService.DatosPublicacion publicacionDePrueba() {
        return new PublicacionService.DatosPublicacion("WEB_PROPIA", null, new BigDecimal("2500"),
                "PEN", "Anuncio de prueba", null, null);
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
