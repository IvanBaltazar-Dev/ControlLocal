package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
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
import java.sql.SQLException;
import java.util.ArrayList;
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
 * <b>El gate del Corte 5, subtanda 5B</b> (V85): el suelo, y lo que la norma
 * deja hacer con el.
 *
 * <h2>Que capacidad prueba</h2>
 * Hasta V85 un TERRENO se describia con DIECISEIS caracteristicas —medidas
 * contra el catalogo el 2026-08-29— y <b>ninguna hablaba del suelo como
 * suelo</b>: cinco medidas o identidad que valen para cualquier activo, cuatro
 * registrales, <b>tres</b> de servicios ({@code gas}, {@code agua_desague} y
 * {@code energia_electrica}; {@code servicios_disponibles} ya estaba retirada
 * desde V84), el acceso de vehiculo —que no es un servicio—, la via como texto
 * libre, la ocupacion, y una
 * {@code area_terreno} que repetia la superficie que ya estaba en
 * {@code metraje_total}. Dos lotes de 500 m² en el mismo distrito eran la misma
 * ficha aunque uno fuera urbano habilitado con ocho pisos de altura normativa y
 * el otro un eriazo con CIRA pendiente y una trocha delante. Este corte trae las
 * dieciocho claves que separan esos dos productos, y retira la unica que
 * duplicaba una verdad que ya estaba escrita.
 *
 * <h2>Las dos mitades, y son distintas</h2>
 * <ul>
 *   <li><b>Lo que nace</b>: 18 claves, 17 {@code OPC} y una sola {@code PUB} —
 *       {@code condicion_terreno} (D-3). La columna «nivel» de la auditoria
 *       proponia cinco {@code PUB} mas: es <b>propuesta</b>, no autoridad, y D-1
 *       del titular dice que no se eleva nada mas en este corte.</li>
 *   <li><b>Lo que se retira</b>: {@code area_terreno} deja de aplicar a
 *       {@code T} (D-7), y <b>solo</b> a {@code T}. En {@code A} y {@code C}
 *       sigue viva porque ahi no nombra la misma verdad que
 *       {@code metraje_total}.</li>
 * </ul>
 *
 * <h2>Lo que este gate NO puede medir, y quien lo mide</h2>
 * La <b>conservacion del legado</b> de {@code area_terreno} se demuestra
 * comparando el ANTES con el DESPUES, y el unico sitio que ve el antes es la
 * propia migracion: su bloque 6.8 clasifica contra una foto y aborta si una fila
 * discrepante desaparece. Aqui se prueba el <b>mecanismo</b> que hace segura esa
 * retirada —una fila que queda huerfana se sigue leyendo entera y se puede
 * quitar—, para que la afirmacion no dependa de que la base traiga legado.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SueloYParametrosUrbanisticosIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;
    @Autowired PublicacionService publicaciones;

    /**
     * Las 18 con su forma exacta, tal como las congelo el encargo. Se declara
     * UNA vez y la usan varios casos: dos copias de esta tabla divergen, y la
     * que no se actualice dejara de mirar lo que dice mirar.
     */
    private static final List<String[]> LAS_DIECIOCHO = List.of(
            // clave, tipo_dato, unidad ("" si no lleva), tipos=exigencia
            new String[] {"condicion_terreno",             "LISTA",    "",      "T=PUB"},
            new String[] {"situacion_registral",           "LISTA",    "",      "C=OPC,T=OPC"},
            new String[] {"fondo",                         "DECIMAL",  "m",     "C=OPC,T=OPC"},
            new String[] {"posicion_en_manzana",           "LISTA",    "",      "C=OPC,T=OPC"},
            new String[] {"topografia",                    "LISTA",    "",      "T=OPC"},
            new String[] {"altura_normativa_pisos",        "ENTERO",   "pisos", "C=OPC,T=OPC"},
            new String[] {"coeficiente_edificacion",       "DECIMAL",  "",      "T=OPC"},
            new String[] {"area_libre_minima",             "DECIMAL",  "%",     "T=OPC"},
            new String[] {"retiro_municipal",              "DECIMAL",  "m",     "T=OPC"},
            new String[] {"usos_compatibles",              "TEXTO",    "",      "T=OPC"},
            new String[] {"certificado_parametros_vigente", "BOOLEANO", "",     "T=OPC"},
            new String[] {"lote_minimo_normativo",         "DECIMAL",  "m²",    "T=OPC"},
            new String[] {"tipo_via_acceso",               "LISTA",    "",      "A=OPC,L=OPC,T=OPC"},
            new String[] {"estado_via",                    "LISTA",    "",      "A=OPC,T=OPC"},
            new String[] {"edificacion_existente",         "DECIMAL",  "m²",    "T=OPC"},
            new String[] {"cercado",                       "BOOLEANO", "",      "T=OPC"},
            new String[] {"restriccion_arqueologica",      "LISTA",    "",      "T=OPC"},
            new String[] {"zona_de_riesgo",                "BOOLEANO", "",      "C=OPC,T=OPC"});

    // ==================================================================
    // 1. Las 18 claves, con la forma que el encargo congelo
    // ==================================================================

    /**
     * <b>Nacieron las dieciocho, y con la forma exacta.</b>
     *
     * <p>Se comprueba el CONJUNTO y no el numero: diecisiete claves y una
     * repetida darian dieciocho filas igual, y la que faltara seria justo la que
     * el terreno no puede describir.
     *
     * <p>La <b>unidad</b> entra en la comparacion a proposito. Es lo unico que
     * distingue un {@code area_libre_minima} en por ciento de uno en metros, y
     * un catalogo que lo callara dejaria que un agente escribiera 30 y otro 0,30
     * sin que nada los separase. Ademas fija la convencion: {@code m²} con
     * superindice, y <b>ninguna clave escribe {@code m2}</b>.
     *
     * <p><b>Cuantas la usan NO se escribe aqui</b>, y esa es la enmienda: este
     * javadoc decia «once claves», que era cierto <b>antes</b> de este corte y
     * dejo de serlo <b>por este corte</b> —{@code edificacion_existente} y
     * {@code lote_minimo_normativo} la siembran, asi que son 13—. Una cifra a
     * mano sobre algo que el propio corte mueve envejece a mentira sin que nada
     * avise. La <b>mide el gate</b>, en la columna {@code nota} de
     * «5B la superficie tiene UNA grafia en el catalogo del sistema», igual
     * que se hizo con el suelo de {@code M2}. Lo que si es invariante —y por eso
     * vive en una comprobacion y no en una frase— es que <b>el conjunto sea uno
     * solo</b>: una sola grafia para la unidad de superficie.
     *
     * <p><b>Esta cita estuvo MUERTA una ronda entera.</b> Nacio nombrando
     * «5B ninguna clave del catalogo escribe m2 sin superindice» y la rompio
     * <b>el propio corte</b> al renombrar esa comprobacion para que dijera lo
     * que mide. Citar por NOMBRE en vez de por numero es mejor, pero no basta:
     * un nombre tambien se renombra. Desde la sexta ronda, la pasada de cierre
     * contrasta <b>toda cita de comprobacion contra la lista de nombres de la
     * corrida real</b> del gate, que es lo unico que lo detecta.
     */
    @Test
    @DisplayName("V85: las 18 claves del suelo nacieron con su tipo, su unidad y su exigencia")
    void lasDieciochoNacieronConSuForma() {
        List<String> mal = new ArrayList<>();
        for (String[] esperada : LAS_DIECIOCHO) {
            List<Map<String, Object>> filas = jdbc.queryForList("""
                    select c.tipo_dato, coalesce(c.unidad, '') as unidad, c.destino,
                           c.aplica_todos, c.activo, c.del_sistema, c.sujeto,
                           c.campo_estructural
                      from catalogo_atributo c
                     where c.clave = ? and c.organizacion_id is null
                    """, esperada[0]);
            if (filas.size() != 1) {
                mal.add(esperada[0] + " tiene " + filas.size() + " definiciones del sistema");
                continue;
            }
            Map<String, Object> f = filas.get(0);
            String real = f.get("tipo_dato") + "|" + f.get("unidad") + "|" + f.get("destino")
                    + "|todos=" + f.get("aplica_todos") + "|activo=" + f.get("activo")
                    + "|sistema=" + f.get("del_sistema") + "|" + f.get("sujeto")
                    + "|estructural=" + f.get("campo_estructural");
            String debe = esperada[1] + "|" + esperada[2] + "|ATRIBUTO"
                    + "|todos=false|activo=true|sistema=true|PROPIEDAD|estructural=null";
            if (!debe.equals(real)) {
                mal.add(esperada[0] + ": " + real + "  (tenia que ser " + debe + ")");
            }
            String tipos = String.join(",", exigenciasDe(esperada[0]));
            if (!esperada[3].equals(tipos)) {
                mal.add(esperada[0] + " aplica a [" + tipos + "] y tenia que aplicar a ["
                        + esperada[3] + "]");
            }
        }
        assertEquals(List.of(), mal, """
                El catalogo del suelo no quedo como lo congelo el encargo.

                Las 18 son del sujeto PROPIEDAD, destino ATRIBUTO y con aplicabilidad
                EXPLICITA por tipo. `aplica_todos` era la segunda autoridad que D-5 dejo
                anotada como deuda -- V86 se la quito y lo dejo como resumen de las filas --,
                y una clave que se apoyara en el no podria despues excluir un tipo sin
                cambiar de forma.
                """);
    }

    /**
     * <b>{@code condicion_terreno} es la UNICA {@code PUB} que estrena 5B.</b>
     *
     * <p>Es la comprobacion que sostiene D-1, y por eso mira en las <b>dos</b>
     * direcciones: que entre las 18 no hay otra {@code PUB}, y que el catalogo
     * <b>del sistema</b> entero no tiene mas {@code PUB} que las cuatro que
     * alguien decidio una por una — {@code tipo_acceso} en L (V82),
     * {@code agua_desague} y {@code energia_electrica} en T (V84, D-1) y esta.
     *
     * <p>Sin la segunda mitad, promover una clave <b>vieja</b> pasaria sin
     * ruido, y eso es exactamente lo que D-1 prohibe: la auditoria propone
     * {@code PUB} para muchas claves que <b>ya existen</b> en el catalogo, y
     * ninguna esta autorizada.
     *
     * <p><b>Aqui iba «catorce» y no salia de ninguna medicion.</b> Cruzada la
     * columna «nivel» de {@code auditoria-profundidad-inmobiliaria.md} contra el
     * catalogo vivo, las propuestas {@code PUB} sobre claves ya existentes y que
     * hoy no lo son se cuentan por decenas, no por catorce. <b>La cifra se
     * quita en vez de corregirse</b>, y es deliberado: la mueven el documento y
     * el catalogo, ninguno de los dos vive aqui, y lo que esta prueba fija —la
     * lista <b>exacta</b> de las cuatro— no la necesita. Lo que si es una
     * medicion sobre un universo cerrado es el «cinco» de la asercion de abajo:
     * son las claves de estas 18 a las que §3.8 pone {@code PUB} <b>ademas</b>
     * de {@code condicion_terreno} — {@code situacion_registral},
     * {@code fondo}, {@code posicion_en_manzana},
     * {@code altura_normativa_pisos} y {@code tipo_via_acceso}—, contadas sobre
     * la seccion entera y no sobre un recuerdo.
     */
    @Test
    @DisplayName("V85: condicion_terreno es la UNICA PUB nueva, y el catalogo tiene cuatro")
    void soloUnaClaveNuevaImpidePublicar() {
        assertEquals(List.of("condicion_terreno/T"), jdbc.queryForList("""
                select c.clave || '/' || t.tipo_propiedad
                  from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.organizacion_id is null and t.exigencia = 'PUB'
                   and c.clave = any (?)
                 order by 1
                """, String.class, (Object) LAS_DIECIOCHO.stream().map(k -> k[0]).toArray(String[]::new)),
                "D-1 no autoriza ninguna PUB mas en este corte; la columna `nivel` de la "
                        + "auditoria propone cinco y es PROPUESTA, no autoridad");

        assertEquals(
                List.of("agua_desague/T", "condicion_terreno/T", "energia_electrica/T",
                        "tipo_acceso/L"),
                jdbc.queryForList("""
                        select c.clave || '/' || t.tipo_propiedad
                          from catalogo_atributo c
                          join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                         where c.organizacion_id is null and c.activo and t.exigencia = 'PUB'
                         order by 1
                        """, String.class),
                "cada PUB del catalogo del sistema la decidio el titular una por una; una "
                        + "quinta sin decision seria una puerta de publicacion que nadie abrio");

        // Y NINGUNA es ALT: `condicion_terreno` bajo de ALT a PUB por D-3, y la
        // diferencia es que ALT bloquea TAMBIEN el alta. Un agente tiene que
        // poder registrar un terreno cuya condicion todavia no ha confirmado.
        assertEquals(List.of(), jdbc.queryForList("""
                select c.clave from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.organizacion_id is null and t.exigencia = 'ALT' and c.clave = any (?)
                """, String.class, (Object) LAS_DIECIOCHO.stream().map(k -> k[0]).toArray(String[]::new)),
                "ninguna de las 18 puede impedir REGISTRAR: es literalmente lo que dice D-3");
    }

    /**
     * <b>Las siete {@code LISTA} nacieron con su vocabulario, en la misma
     * migracion.</b>
     *
     * <p>Es la leccion de {@code servicios_disponibles}, que 5A acaba de cerrar:
     * una LISTA sin opciones no es una lista — {@code MotorDeCaptura.controlDe}
     * la degrada a TEXTO y la comprobacion de vocabulario del trigger esta
     * condicionada a que existan opciones, asi que la clave acepta cualquier
     * cadena y nadie se entera. Aquella estuvo muda cuatro cortes.
     *
     * <p>Se comprueba el vocabulario <b>codigo a codigo y en orden</b>, que es
     * el que ve el selector, no solo que «tenga opciones».
     */
    @Test
    @DisplayName("V85: las siete LISTA del suelo nacieron CON vocabulario y en su orden")
    void lasSieteListasNacieronConVocabulario() {
        assertEquals(List.of("URBANO_HABILITADO", "EN_PROCESO_DE_HABILITACION", "RUSTICO_ERIAZO",
                        "ZONA_INFORMAL_SIN_HABILITAR"),
                vocabulario("condicion_terreno"));
        assertEquals(List.of("INSCRITO_EN_SUNARP", "EN_SANEAMIENTO", "NO_INSCRITO_SOLO_POSESION"),
                vocabulario("situacion_registral"));
        assertEquals(List.of("UN_FRENTE", "DOS_FRENTES", "TRES_FRENTES", "CUATRO_FRENTES",
                        "ESQUINA"),
                vocabulario("posicion_en_manzana"));
        assertEquals(List.of("PLANO", "PENDIENTE_LEVE", "PENDIENTE_PRONUNCIADA",
                        "BAJO_NIVEL_DE_VIA", "ACCIDENTADO"),
                vocabulario("topografia"));
        assertEquals(List.of("AVENIDA", "CALLE_O_JIRON", "PASAJE", "CARRETERA", "TROCHA_O_SIN_VIA"),
                vocabulario("tipo_via_acceso"));
        assertEquals(List.of("ASFALTADA", "AFIRMADA", "SIN_AFIRMAR"),
                vocabulario("estado_via"));
        assertEquals(List.of("NO_APLICA", "CIRA_OBTENIDO", "EN_TRAMITE", "REQUERIDO_NO_INICIADO"),
                vocabulario("restriccion_arqueologica"));

        // Y las once que NO son lista no ganaron opciones: una opcion sobre una
        // DECIMAL no la lee nadie y confunde a quien la ve en el catalogo.
        assertEquals(List.of(), jdbc.queryForList("""
                select distinct c.clave from catalogo_atributo c
                  join catalogo_atributo_opcion o on o.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.organizacion_id is null and c.clave = any (?)
                   and c.tipo_dato not in ('LISTA', 'LISTA_MULTIPLE')
                """, String.class, (Object) LAS_DIECIOCHO.stream().map(k -> k[0]).toArray(String[]::new)));
    }

    /**
     * <b>Un valor fuera del vocabulario se rechaza, y hay que exigir CON QUE
     * codigo.</b>
     *
     * <p>Un {@code assertThrows(Exception.class, …)} pasa igual con una
     * {@code NullPointerException}, con un {@code unique_violation} o con la
     * base caida: afirma que algo fallo, no que la invariante existe. Es el
     * mismo liston que el gate SQL de este corte se exige a si mismo —«no basta
     * con exigir que el {@code INSERT} falle: hay que exigir con que codigo
     * falla»—, y esta prueba estaba por debajo de el (auditoria del 2026-08-29,
     * H5).
     *
     * <p>Quien rechaza aqui es {@code exigir_atributo_gobernado}, el trigger, y
     * no una comprobacion en Java: {@code convertir} acota tipo, rango y
     * longitud, y la <b>pertenencia</b> al vocabulario solo la mira la base para
     * una clave gobernada. Por eso lo que se exige es el {@code SQLSTATE}
     * {@code 23514} y el texto del trigger, leidos recorriendo la cadena de
     * causas: el envoltorio que Spring pone encima puede cambiar y el codigo
     * de la base, no.
     */
    @Test
    @DisplayName("V85: un valor fuera del vocabulario del suelo se rechaza con 23514")
    void elVocabularioDelSueloNoAdmiteCualquierCadena() {
        long id = registrarTerreno();

        Exception condicion = assertThrows(Exception.class,
                () -> editar(id, new ValorAtributo("condicion_terreno", "urbano")),
                "es justo lo que una LISTA muda aceptaba");
        assertEquals("23514", sqlStateDe(condicion),
                "tiene que rechazarlo la guarda del vocabulario (check_violation), no otra cosa: "
                        + causasDe(condicion));
        assertTrue(causasDe(condicion).contains("no admite el valor \"urbano\""),
                "y el rechazo dice QUE valor no admite: " + causasDe(condicion));

        Exception topografia = assertThrows(Exception.class,
                () -> editar(id, new ValorAtributo("topografia", "con pendiente")));
        assertEquals("23514", sqlStateDe(topografia), causasDe(topografia));

        assertNull(valorDe(id, "condicion_terreno"), "y el rechazo no deja rastro a medias");
    }

    /** El {@code SQLSTATE} de la primera {@code SQLException} de la cadena, o {@code null}. */
    private static String sqlStateDe(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /**
     * Los mensajes de toda la cadena de causas.
     *
     * <p>El texto del trigger no viaja en el mensaje del envoltorio: viaja en el
     * de la {@code SQLException} de mas abajo. Buscar solo en
     * {@code getMessage()} daria un fallo que dice «no lo encontro» sobre algo
     * que si esta.
     */
    private static String causasDe(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            texto.append(causa.getClass().getSimpleName()).append(": ")
                    .append(causa.getMessage()).append(" | ");
        }
        return texto.toString();
    }

    // ==================================================================
    // 2. La exigencia que estrena el corte
    // ==================================================================

    /**
     * <b>Un terreno sin condicion se registra y se edita, pero no se
     * publica.</b>
     *
     * <p>Las dos mitades de D-3 en un solo recorrido. {@code PUB} no bloquea el
     * alta —eso solo lo hace {@code ALT}, y por eso el titular la bajo— y si
     * bloquea publicar. Y el bloqueo <b>informa</b>: viaja con el rotulo del
     * catalogo, no con la clave desnuda.
     */
    @Test
    @DisplayName("V85: un TERRENO sin condicion se registra y se edita, pero NO se publica")
    void elTerrenoSinCondicionSeRegistraPeroNoSePublica() {
        long id = registrarTerreno();
        assertNull(valorDe(id, "condicion_terreno"), "el alta no inventa la condicion del suelo");

        // Se edita OTRA cosa y lo que falta sigue faltando.
        editar(id, new ValorAtributo("fondo", "25.00"));
        assertEquals(0, new BigDecimal("25.00").compareTo(new BigDecimal(valorDe(id, "fondo"))));
        assertNull(valorDe(id, "condicion_terreno"), "editar otro dato no rellena el que falta");

        assertEquals(List.of("condicion_terreno"), bloqueantesDe(id),
                "el terreno del fixture ya declara agua y luz: la unica causa de bloqueo que "
                        + "queda es la que estrena este corte");

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        AtributoQueFalta falta = ficha.faltanParaPublicar().stream()
                .filter(a -> "condicion_terreno".equals(a.clave())).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "tiene que decir que le falta la condicion para publicar; lleva "
                                + ficha.faltanParaPublicar()));
        assertEquals("Condición del terreno", falta.rotulo(),
                "el rotulo lo trae el catalogo: con la clave desnuda no es una frase para nadie");
        assertTrue(ficha.atributosQueFaltan().stream()
                        .noneMatch(a -> "condicion_terreno".equals(a.clave())),
                "`atributosQueFaltan` responde a que impide el ALTA, y PUB no lo impide (D-3)");

        ReglaNegocioException rechazo = assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "500 m² habilitados y 500 m² rusticos no son la misma oferta");
        assertTrue(rechazo.getMessage().contains("Condición del terreno"),
                "el rechazo dice QUE falta, con su rotulo: " + rechazo.getMessage());

        editar(id, new ValorAtributo("condicion_terreno", "RUSTICO_ERIAZO"));
        assertEquals(List.of(), bloqueantesDe(id));
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "declarada la condicion, el anuncio entra");
    }

    /**
     * <b>Declarar la peor condicion desbloquea igual que declarar la mejor.</b>
     *
     * <p>Es lo que hace legitimo el bloqueo: lo que se exige es el HECHO
     * VERIFICADO, no una condicion favorable. Si {@code RUSTICO_ERIAZO} no
     * desbloqueara, la exigencia empujaria a mentir; si callar desbloqueara, no
     * seria una exigencia.
     */
    @Test
    @DisplayName("V85: declarar RUSTICO_ERIAZO desbloquea igual que URBANO_HABILITADO; callar, no")
    void declararLaPeorCondicionTambienEsDeclarar() {
        long callado = registrarTerreno();
        long malo = registrarTerreno();
        long bueno = registrarTerreno();

        editar(malo, new ValorAtributo("condicion_terreno", "ZONA_INFORMAL_SIN_HABILITAR"));
        editar(bueno, new ValorAtributo("condicion_terreno", "URBANO_HABILITADO"));

        assertEquals(List.of("condicion_terreno"), bloqueantesDe(callado), "callar no desbloquea");
        assertEquals(List.of(), bloqueantesDe(malo), "declarar la peor condicion si");
        assertEquals(List.of(), bloqueantesDe(bueno));
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(malo), publicacionDePrueba(), actor()));
    }

    /**
     * <b>Las diecisiete {@code OPC} no bloquean nada, y aun asi viajan y
     * vuelven.</b>
     *
     * <p>La otra mitad del contrato: sembrar diecisiete claves no puede
     * convertir el terreno en un formulario obligatorio. Se comprueba sobre un
     * terreno al que le faltan las diecisiete.
     */
    @Test
    @DisplayName("V85: las 17 OPC del suelo no bloquean, y el 0 de edificacion es una medida")
    void lasDiecisieteOpcNoBloquean() {
        long id = registrarTerreno();
        editar(id, new ValorAtributo("condicion_terreno", "URBANO_HABILITADO"));

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        assertNull(valorDe(id, "topografia"), "el caso necesita OPC ausentes");
        assertNull(valorDe(id, "coeficiente_edificacion"), "y mas de una");
        assertTrue(ficha.faltanParaPublicar().isEmpty(),
                "ninguna OPC puede aparecer como bloqueante: " + ficha.faltanParaPublicar());

        // CERO NO ES AUSENTE, y es la razon por la que `edificacion_existente`
        // es DECIMAL y no BOOLEANO: «el lote esta vacio» es una medida que
        // alguien tomo, y «no consta» es que nadie fue. Un booleano no sabe
        // decir «hay 80 m² de casa vieja que habra que demoler».
        assertNull(valorDe(id, "edificacion_existente"), "antes de declararlo, no consta");
        editar(id, new ValorAtributo("edificacion_existente", "0"));
        assertEquals(0, new BigDecimal("0").compareTo(
                        new BigDecimal(valorDe(id, "edificacion_existente"))),
                "declarar 0 tiene que quedar escrito como 0, no desaparecer como si fuera vacio");
        assertEquals(1L, contar("select count(*) from atributo_propiedad "
                        + "where id_propiedad = " + id + " and clave = 'edificacion_existente'"),
                "y con su fila: si el 0 se tratara como ausencia, no habria ninguna");
    }

    // ==================================================================
    // 3. D-7: `area_terreno` se retira de T, y SOLO de T
    // ==================================================================

    /**
     * <b>{@code area_terreno} deja de aplicar a {@code T} por las DOS
     * puertas.</b>
     *
     * <p>Alta y edicion, y no una sola: es la asimetria que 4.P tuvo que
     * reparar cuando una CASA se podia registrar con un {@code piso} que despues
     * no se podia corregir nunca. Una puerta que no exija lo mismo que la otra
     * es la puerta permisiva, y por ahi vuelve el dato que el corte retira.
     *
     * <p><b>Y se exige el motivo, no solo el fallo</b> (auditoria del
     * 2026-08-29, H5). Con {@code Exception.class} a secas, las dos puertas
     * saldrian verdes ante un {@code unique_violation} —{@code
     * uq_atributo_propiedad_clave} deja una sola fila por (propiedad, clave)— o
     * ante cualquier averia, que es el modo de fallo contra el que el gate SQL
     * de este mismo corte se blinda comprobando el {@code SQLSTATE}. Aqui el
     * equivalente es la {@link ReglaNegocioException} de
     * {@code AtributosGobernados.exigirQueAplique}, que rechaza <b>en Java y sin
     * llegar a escribir</b>, con el tipo dicho por su nombre y no por su letra.
     */
    @Test
    @DisplayName("V85: un TERRENO ya no admite area_terreno, ni al alta ni al editar")
    void areaTerrenoYaNoEntraPorNingunaPuertaEnUnTerreno() {
        assertEquals(List.of("A=OPC", "C=OPC"), exigenciasDe("area_terreno"),
                "D-7 retira SOLO la fila de T: A y C no se tocan");

        ReglaNegocioException alta = assertThrows(ReglaNegocioException.class,
                () -> registrar("TERRENO",
                        List.of(new ValorAtributo("metraje_total", "500"),
                                new ValorAtributo("zonificacion", "RDM"),
                                new ValorAtributo("area_terreno", "500")),
                        new OperacionSolicitada("VENTA", new BigDecimal("300000"), "USD",
                                null, null, null, null, null, null, null)),
                "el ALTA de un terreno ya no admite la clave duplicada");
        assertTrue(alta.getMessage()
                        .contains("\"area_terreno\" no aplica a una propiedad de tipo TERRENO"),
                "tiene que rechazarla por APLICABILIDAD, no por otra regla: " + alta.getMessage());

        long id = registrarTerreno();
        ReglaNegocioException edicion = assertThrows(ReglaNegocioException.class,
                () -> editar(id, new ValorAtributo("area_terreno", "500")),
                "y la EDICION tampoco: si una de las dos puertas la aceptara, el dato "
                        + "duplicado volveria por ahi");
        assertEquals(alta.getMessage(), edicion.getMessage(),
                "y las dos puertas dan la MISMA respuesta: si difieren, hay dos reglas");

        assertNull(valorDe(id, "area_terreno"), "y el rechazo no deja la fila a medias");
    }

    /**
     * <b>Y sigue viva donde SI dice algo.</b>
     *
     * <p>Una casa se tasa por el PAR (terreno, construida) y una nave tiene
     * patio ademas de techo: ahi {@code area_terreno} y {@code metraje_total} no
     * nombran la misma verdad. Retirarla de los tres tipos habria sido el error
     * simetrico —y mas caro— del que este caso protege.
     */
    @Test
    @DisplayName("V85: area_terreno sigue viva en CASA y ALMACEN, que es donde no duplica nada")
    void areaTerrenoSigueVivaDondeNoDuplica() {
        long casa = registrar("CASA",
                List.of(new ValorAtributo("metraje_total", "210"),
                        new ValorAtributo("dormitorios", "4"),
                        new ValorAtributo("area_terreno", "300")),
                new OperacionSolicitada("VENTA", new BigDecimal("250000"), "USD",
                        null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("300").compareTo(new BigDecimal(valorDe(casa, "area_terreno"))),
                "una casa de 210 m² techados sobre 300 m² de lote son dos hechos, no uno");

        long almacen = registrar("ALMACEN",
                List.of(new ValorAtributo("metraje_total", "800")),
                new OperacionSolicitada("ALQUILER", new BigDecimal("9000"), "PEN",
                        null, null, null, null, null, null, null));
        editar(almacen, new ValorAtributo("area_terreno", "900"));
        assertEquals(0, new BigDecimal("900").compareTo(
                new BigDecimal(valorDe(almacen, "area_terreno"))));

        assertNotNull(jdbc.queryForObject("""
                select rotulo from catalogo_atributo
                 where clave = 'area_terreno' and organizacion_id is null and activo
                """, String.class),
                "la CLAVE no se retira: lo que se retira es su aplicabilidad a T");
    }

    /**
     * <b>Una fila huerfana no se pierde ni queda atrapada.</b>
     *
     * <p>Es el mecanismo que hace segura la retirada de D-7, y el que la
     * migracion no puede probar por si sola: cuando un valor de
     * {@code area_terreno} sobre un TERRENO <b>no</b> coincide con su
     * {@code metraje_total}, {@code V85} lo <b>conserva</b> en vez de borrarlo —
     * no se sabe cual de las dos superficies es la correcta y elegir una seria
     * inventar—. Lo que este caso comprueba es que esa fila conservada:
     *
     * <ol>
     *   <li>se sigue <b>leyendo entera</b>, con su rotulo y su tipo, y no como
     *       una clave desnuda;</li>
     *   <li>se puede <b>retirar</b>, aunque su clave ya no aplique al tipo.
     *       {@code AtributosGobernados.retirar} no exige aplicabilidad y
     *       {@code escribirEnEdicion} si, y esa asimetria es deliberada: sin
     *       ella el desacuerdo quedaria escrito para siempre y sin forma de
     *       resolverlo.</li>
     * </ol>
     *
     * <p>Se siembra por SQL directo <b>y se dice</b>: la puerta normal ya la
     * rechaza, que es precisamente lo que prueba el caso anterior. Que un
     * control necesite saltarse una guarda no es una licencia — es la prueba de
     * que la guarda esta puesta.
     *
     * <h2>Por que la siembra va en UN SOLO bloque {@code DO}, y no en un
     * {@code try/finally}</h2>
     * La primera version de este caso abria la aplicabilidad con un
     * {@code jdbc.update}, escribia por el servicio y la cerraba en un
     * {@code finally}. <b>Eso no es una restauracion: es una intencion.</b> Son
     * tres sentencias con tres transacciones, y si el proceso muere entre la
     * primera y la ultima —o si alguien interrumpe la corrida— la base
     * compartida se queda con <b>D-7 deshecho</b>: {@code area_terreno} vuelve a
     * aplicar a {@code T} para todo el mundo. No es hipotetico: sembrada la fila
     * a mano, este caso aborta con {@code DuplicateKeyException} y arrastra
     * consigo a {@code ConservacionDeLaEdicion.cadaCasoLlevaTodoLoQueSuTipoAdmite}.
     *
     * <p>El gate SQL de este mismo corte ya lo hacia bien —{@code SAVEPOINT
     * repite_5b}, y una comprobacion propia —«5B CONTROL y el savepoint volvio a cerrar la puerta de T»— que exige que la puerta
     * quedara cerrada—, asi que la prueba estaba <b>por debajo del nivel de su
     * propio corte</b>. Un bloque {@code DO} de PL/pgSQL es <b>una sola
     * sentencia</b> y por tanto una sola transaccion: abrir, escribir y cerrar
     * ocurren juntos o no ocurren. Y el cierre se comprueba igual, porque una
     * restauracion que nadie mira es la misma promesa que el {@code finally}.
     *
     * <p>La fila se siembra <b>anterior a la frontera del linaje</b>, y no es un
     * detalle: un huerfano conservado por {@code V85} es, por construccion, un
     * valor <b>legado</b> —escrito antes de que el corte existiera—. Sembrarlo
     * con {@code now()} fabricaria un dato imposible —un valor gobernado
     * posterior al cutover y sin rastro— y envenenaria la comprobacion «4P despues del cutover ningun hecho del inmueble sin linaje» del
     * gate de 4.P desde aqui. Misma leccion que
     * {@code OcupacionYServiciosIntegrationTest.sembrarLegadoAmbiguo}.
     *
     * <h2>Lo que este caso DEJO de cubrir al hacerse atomico, dicho y no
     * disimulado</h2>
     * La version del {@code finally} escribia el huerfano <b>por el servicio</b>
     * ({@code escribirEnEdicion}), asi que de paso ejercitaba esa puerta con la
     * aplicabilidad abierta. La version atomica lo escribe con un {@code INSERT}
     * crudo dentro del bloque. <b>Es una perdida de cobertura real</b>, y se
     * acepta porque lo que este caso viene a probar es la LECTURA y la RETIRADA
     * de un huerfano, no su escritura.
     *
     * <p><b>Lo que se perdio es el camino de ACEPTACION</b> —escribir una clave
     * que SI aplica, por {@code escribirEnEdicion}—, y conviene decirlo bien
     * porque la primera version de este parrafo lo justifico mal: decia que lo
     * cubre {@code areaTerrenoYaNoEntraPorNingunaPuertaEnUnTerreno}, «que exige
     * que el servicio la RECHACE». <b>Un rechazo no cubre una aceptacion.</b>
     *
     * <p>La conclusion era cierta, pero por otros casos: la aceptacion por
     * {@code escribirEnEdicion} la ejercitan
     * {@link #elTerrenoSinCondicionSeRegistraPeroNoSePublica} (con
     * {@code fondo}), {@link #lasDiecisieteOpcNoBloquean} (con
     * {@code edificacion_existente}), {@link #lasDosClavesDeLaViaConviven} (con
     * las tres claves de la via) y {@link #declararLaPeorCondicionTambienEsDeclarar}
     * (con {@code condicion_terreno}). Cubrir ademas ese camino <b>aqui</b>
     * obligaria a dejar la puerta abierta entre dos transacciones, que es
     * exactamente el defecto que se corrige.
     *
     * <p><b>Y la atomicidad no es autocuracion.</b> El bloque garantiza que este
     * caso no deja la puerta abierta; <b>no</b> repara una que ya lo estuviera.
     * Si un residuo previo dejo la fila {@code area_terreno/T} sembrada, el
     * {@code INSERT} de aqui aborta por clave primaria y la deja como estaba —
     * igual que el patron viejo—. La promesa cubre la ventana que este caso
     * crea, no una heredada.
     *
     * <p><b>Efecto colateral, y se ACUMULA — pero NO es sólo de este caso.</b>
     * Retrasar {@code propiedad.fecha_registro} saca a esa propiedad del
     * universo de la comprobacion
     * «4P despues del cutover ninguna columna estructural sin linaje» del gate,
     * y la propiedad <b>se queda</b>.
     *
     * <p><b>Son TRES por corrida, desde DOS clases</b>, y la version anterior de
     * este parrafo se atribuia las tres: una la siembra
     * {@code sembrarHuerfano} de aqui, y <b>dos</b>
     * {@code OcupacionYServiciosIntegrationTest.sembrarLegadoAmbiguo} —de
     * <b>5A</b>, con dos sitios de llamada—, que hace el mismo
     * {@code update ... frontera_de_linaje() - interval '2 days'}. Esa clase ya
     * documenta su parte por su cuenta.
     *
     * <p><b>Se dice el INCREMENTO —tres— y NO el total</b>, y esa es la enmienda
     * de la septima ronda. Para demostrar el incremento, la version anterior de
     * este parrafo escribio el par absoluto «35 -&gt; 38 tras un solo
     * {@code mvn test}» — <b>y ese par tambien habia caducado</b> una ronda
     * despues, medido contra {@code controllocal_repositorios}, exactamente
     * igual que las dos cifras que este mismo javadoc manda no escribir en el
     * parrafo que empieza «Cuantas hay NO se escribe aqui». Ilustrar una regla
     * con un ejemplo que la incumple no la ilustra: la deroga. Quien quiera
     * comprobar el mecanismo <b>cuenta antes y despues en su propia
     * corrida</b>, con la consulta que cierra este javadoc y <b>sin</b> su
     * clausula {@code not exists}.
     *
     * <p><b>El incremento si aguanta, y por eso se escribe:</b> no es una medida
     * de la base —que sube en cada corrida— sino un recuento de <b>sitios de
     * llamada</b>, uno aqui y dos en la clase de 5A, que solo cambia si alguien
     * anade o quita uno. Verificado el 2026-08-29 contra
     * {@code controllocal_repositorios} corriendo las dos clases y contando el
     * universo antes y despues: sube <b>exactamente en tres</b>.
     *
     * <p><b>Y hay que decir contra que base.</b> {@code Verificar-Cierre.ps1}
     * pasa el gate sobre {@code controllocal_dev}, y ahi las acumuladas son
     * <b>CERO</b>: el efecto sólo existe en {@code controllocal_repositorios},
     * que es donde corren las pruebas.
     *
     * <p><b>Cuantas hay NO se escribe aqui</b>, y la razon es la misma que
     * obligo a sacar del javadoc de arriba el recuento de {@code m²}: <b>esta
     * prueba mueve esa cifra cada vez que corre</b>. La ronda que la midio
     * escribio 29 y en la pasada de cierre de esa misma ronda ya eran 32,
     * porque la suite habia vuelto a correr. Una cifra que el propio artefacto
     * incrementa no puede vivir escrita a mano en el artefacto.
     *
     * <p>Lo que si es invariante, y por eso se dice: <b>ninguna de las
     * acumuladas es infractora</b> —todas tienen {@code METRAJE} con rastro—,
     * asi que el efecto <b>estrecha</b> el universo de esa comprobacion pero <b>no oculta
     * ningun defecto</b>. Se comprueba con:
     *
     * <pre>
     *   select count(*) from propiedad p
     *    where p.fecha_registro = frontera_de_linaje() - interval '2 days'
     *      and not exists (select 1 from rastro_valor_gobernado r
     *                       where r.id_agregado = p.id_propiedad
     *                         and r.clave = 'metraje_total');   -- tiene que dar 0
     * </pre>
     *
     * <p>Esa consulta cuenta un <b>superconjunto</b>: incluye tambien lo que
     * siembra 5A, porque las dos clases usan la misma fecha. Se deja asi a
     * proposito —la invariante que importa es que <b>ninguna</b> de las
     * acumuladas sea infractora, vengan de donde vengan—, pero no se puede leer
     * como "las que siembra este caso".
     */
    @Test
    @DisplayName("V85: un area_terreno huerfano sobre un TERRENO se lee entero y se puede retirar")
    void elHuerfanoSeLeeEnteroYSePuedeRetirar() {
        long id = registrarTerreno();
        sembrarHuerfano(id, 777);

        assertEquals(List.of("A=OPC", "C=OPC"), exigenciasDe("area_terreno"),
                "sembrar el huerfano dejo la aplicabilidad REABIERTA: eso deshace D-7 para "
                        + "TODA la base, no solo para este caso. Es la comprobacion «5B CONTROL y el savepoint volvio a cerrar la puerta de T» del "
                        + "gate SQL, dicha desde Java");
        assertEquals(1L, contar("select count(*) from atributo_propiedad where id_propiedad = "
                        + id + " and clave = 'area_terreno'"),
                "la siembra del huerfano no escribio la fila: el caso mediria un universo vacio");
        assertEquals(0L, contar("""
                select count(*) from atributo_propiedad a
                  join propiedad p on p.id_propiedad = a.id_propiedad
                 where a.id_propiedad = %d and a.clave = 'area_terreno'
                   and (a.fecha_creacion < p.fecha_registro
                        or a.fecha_creacion > frontera_de_linaje())
                """.formatted(id)),
                "el huerfano sembrado no cabe en su propia linea de tiempo: tiene que ser "
                        + "posterior a su propiedad y anterior a la frontera del linaje, o "
                        + "envenena la comprobacion «4P despues del cutover ningun hecho del inmueble sin linaje» del gate desde aqui");

        // (1) Se lee ENTERO. Conservar el valor y perder su nombre es conservar
        // a medias: el broker leeria la clave en vez del rotulo.
        PropiedadUniversalService.AtributoFicha leido = fichaDe(id, "area_terreno");
        assertNotNull(leido, "una fila que ya no aplica al tipo se sigue leyendo: el dato existe");
        assertEquals("Área de terreno", leido.rotulo(),
                "con su rotulo del catalogo, no con la clave desnuda");
        assertEquals("DECIMAL", leido.tipoDato(),
                "y con su tipo: un tipoDato nulo cambia lo que el SPA pinta");

        // (2) Y se puede QUITAR, que es la unica forma de resolver el desacuerdo.
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                null, null, List.of("area_terreno")), actor());
        assertNull(valorDe(id, "area_terreno"), "retirar una clave que ya no aplica tiene que "
                + "funcionar: si no, el desacuerdo quedaria escrito para siempre");
        assertEquals(1L, contar("""
                select count(*) from rastro_valor_gobernado
                 where sujeto = 'PROPIEDAD' and id_agregado = %d and clave = 'area_terreno'
                   and verbo = 'RETIRADA' and hallado_numero = 777
                """.formatted(id)),
                "y la retirada deja linaje con lo que quito: sin eso, «este terreno tuvo "
                        + "escrita esa medida» seria una afirmacion que la base no sostiene");
    }

    /**
     * <b>Y si la edicion REENVIA la ficha entera, con el conservado dentro.</b>
     *
     * <p>Este caso no existia y la deuda {@code N33} lo pedia por nombre
     * (auditoria del 2026-08-29; construido en D0 el 2026-08-30). Las dos mitades
     * de {@code D-7} son deliberadas —conservar el {@code area_terreno} que
     * <b>discrepa</b> de {@code metraje_total}, porque no se sabe cual de las dos
     * superficies es la correcta y elegir seria inventar; y cerrar la puerta de
     * escritura para {@code T}, porque sobre un terreno esa clave duplica la
     * superficie canonica—. Lo que nadie habia medido es <b>que pasa cuando las
     * dos se encuentran</b>: un cliente que lee la ficha y la devuelve entera
     * manda tambien el valor conservado, sin cambiarlo.
     *
     * <h2>Lo que se afirma aqui es una DECISION, no una observacion</h2>
     *
     * <p>La primera version de este caso dejaba la eleccion abierta: fijaba lo
     * que hoy ocurre y decia que habia dos comportamientos legitimos. <b>El
     * titular decidio la opcion B el 2026-08-30</b> y esto pasa a afirmarla.
     *
     * <p>La pregunta que el Core se hace <b>no</b> es «¿coincide con lo que ya
     * habia?», sino:
     *
     * <blockquote>¿pertenece esta clave al contrato de escritura de ESTA
     * propiedad?</blockquote>
     *
     * <p>Si la respuesta es no, rechazo — <b>tambien cuando el valor enviado es
     * identico al conservado</b>. Tolerar el reenvio identico abriria la
     * excepcion «una clave no aplicable si puede escribirse si coincide con
     * algo historico», que es una segunda puerta a lo que {@code D-7} cerro; y
     * obligaria a la puerta a comparar VALORES para decidir COMPETENCIAS, que
     * son dos preguntas distintas.
     *
     * <p>El coste se declara y se acepta: mientras el conservado este en la
     * ficha, un cliente que reenvie entero no puede editar nada de ese terreno.
     * El cliente sabe cual excluir porque la ficha se lo dice — desde D0-3 cada
     * atributo viaja con {@code estadoDato} y {@code editable}, y esa senal
     * existe precisamente para que reenviar entero deje de ser a ciegas.
     *
     * <h2>Lo medido</h2>
     *
     * <p>El caso se construye —{@link #sembrarHuerfano} escribe 777 sobre un
     * terreno de 500 m², que es un discrepante de verdad—. Lo medido:
     *
     * <ol>
     *   <li>la ficha <b>devuelve</b> el conservado, asi que un cliente que
     *       reenvie lo que leyo lo incluye sin saberlo;</li>
     *   <li>el reenvio se <b>rechaza por aplicabilidad</b>, aunque el valor sea
     *       <b>identico</b> al que ya estaba: {@code escribirEnEdicion} llama a
     *       {@code exigirQueAplique} antes de mirar si hay algo que cambiar;</li>
     *   <li>el conservado <b>no se pierde</b> —{@code editar} es
     *       {@code @Transactional}, asi que no queda a medias—;</li>
     *   <li><b>pero la edicion legitima que viajaba en la misma carga tampoco
     *       entra</b>. Es el coste real: mientras el conservado este en la ficha,
     *       un cliente que reenvie entero no puede editar <i>nada</i> de ese
     *       terreno;</li>
     *   <li>y sin el conservado en la carga, esa misma edicion entra.</li>
     * </ol>
     *
     * <p>El SPA no provoca el caso —edita por atributo, no reenviando la
     * ficha—, asi que hoy no hay nadie a quien le duela; el dia que un
     * consumidor reenvie entero, este caso dice lo que se va a encontrar.
     */
    @Test
    @DisplayName("N33 opcion B: reenviar la ficha entera con el area_terreno conservado se rechaza, por decision")
    void reenviarLaFichaEnteraConElAreaConservadaSeRechazaPorDecision() {
        long id = registrarTerreno();
        sembrarHuerfano(id, 777);

        assertEquals(0, new BigDecimal("777").compareTo(new BigDecimal(valorDe(id, "area_terreno"))),
                "el caso empieza con un DISCREPANTE conservado -- 777 contra los 500 de "
                        + "metraje_total -- o no habria nada que medir");
        assertNull(valorDe(id, "topografia"),
                "y sin la edicion legitima puesta todavia: si ya estuviera, el paso que mide "
                        + "si se pierde no probaria nada");

        // (1) Lo que un cliente que lee la ficha y la devuelve entera mandaria:
        //     el conservado con su MISMO valor, junto a una edicion legitima.
        ReglaNegocioException reenvio = assertThrows(ReglaNegocioException.class,
                () -> propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                        List.of(new ValorAtributo("topografia", "PLANO"),
                                new ValorAtributo("area_terreno", "777")),
                        null, null), actor()),
                "reenviar la ficha entera con el conservado dentro tiene que dar una respuesta "
                        + "dicha, no un comportamiento sin medir");
        assertTrue(reenvio.getMessage()
                        .contains("\"area_terreno\" no aplica a una propiedad de tipo TERRENO"),
                "y la rechaza por APLICABILIDAD, sin mirar si el valor cambia: " + reenvio.getMessage());

        // (2) El conservado sigue entero: el rechazo no deja la ficha a medias.
        assertEquals(0, new BigDecimal("777").compareTo(new BigDecimal(valorDe(id, "area_terreno"))),
                "el valor que D-7 conserva a proposito no se puede perder por un rechazo");

        // (3) Y este es el coste, medido: la edicion legitima que viajaba con el
        //     tampoco entro. No es una perdida de dato escrito -- es una edicion
        //     que el broker creyo hacer y no ocurrio.
        assertNull(valorDe(id, "topografia"),
                "la edicion legitima cae con el conservado: `editar` es @Transactional y es "
                        + "todo o nada. Este es el coste que N33 pedia medir");

        // (4) Sin el conservado en la carga, la misma edicion entra. Es lo que
        //     hace que la decision sea entre dos comportamientos y no una entre
        //     un defecto y su arreglo.
        editar(id, new ValorAtributo("topografia", "PLANO"));
        assertEquals("PLANO", valorDe(id, "topografia"),
                "excluyendo el conservado, editar ese terreno funciona: la puerta cerrada no "
                        + "bloquea la ficha, bloquea la carga que incluye la clave retirada");

        // (5) Y SE DEJA LA BASE COMO SE ENCONTRO. Sin esto, cada corrida deja un
        //     `area_terreno` sobre un TERRENO en `controllocal_repositorios` --se
        //     midieron dos el 2026-08-30, de las dos primeras pasadas de este
        //     caso-- y esa fila entra en el universo de «5B ningun area_terreno
        //     de un TERRENO repite su metraje canonico». Un gate que mide sobre
        //     el residuo que dejan sus propias pruebas queda atado a una base
        //     concreta, que es el defecto que D0 vino a pagar. El universo de esa
        //     comprobacion lo fabrica su control positivo, no este caso.
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                null, null, List.of("area_terreno")), actor());
        assertNull(valorDe(id, "area_terreno"),
                "el caso tiene que dejar la base como la encontro: el conservado se retira");
    }

    // ==================================================================
    // 4. El par hecho/condicion, y la convivencia de las dos vias
    // ==================================================================

    /**
     * <b>{@code lote_minimo_normativo} llega donde se pacta
     * {@code acepta_venta_fraccionada}.</b>
     *
     * <p>Es la guarda 2.2 de V78 y la razon por la que esta clave estaba
     * esperando a este corte: la condicion comercial se pacta desde V77 y el
     * hecho sobre el que se pacta no existia. Mientras no existe, el unico sitio
     * donde cabe «este lote no baja de 160 m²» es el pacto de un encargo — y un
     * pacto muere con su encargo, mientras que el hecho normativo sobrevive.
     *
     * <p>Se mide en las DOS direcciones para que no salga verde sobre un
     * universo vacio.
     */
    @Test
    @DisplayName("V85: el par lote_minimo_normativo / acepta_venta_fraccionada queda cubierto")
    void elParDelLoteMinimoQuedaCubierto() {
        assertEquals(List.of(), jdbc.queryForList("""
                select distinct o.tipo_propiedad
                  from catalogo_atributo cond
                  join catalogo_atributo_operacion o
                    on o.id_catalogo_atributo = cond.id_catalogo_atributo
                  join catalogo_atributo hecho on hecho.clave = 'lote_minimo_normativo'
                                              and hecho.activo and hecho.organizacion_id is null
                 where cond.clave = 'acepta_venta_fraccionada' and cond.activo
                   and cond.organizacion_id is null
                   and not exists (select 1 from catalogo_atributo_tipo t
                                    where t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                      and t.tipo_propiedad = o.tipo_propiedad)
                """, String.class),
                "hay tipos donde la condicion se pacta y el hecho no se puede escribir");

        assertTrue(contar("""
                select count(distinct o.tipo_propiedad)
                  from catalogo_atributo cond
                  join catalogo_atributo_operacion o
                    on o.id_catalogo_atributo = cond.id_catalogo_atributo
                  join catalogo_atributo hecho on hecho.clave = 'lote_minimo_normativo'
                                              and hecho.activo and hecho.organizacion_id is null
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                               and t.tipo_propiedad = o.tipo_propiedad
                 where cond.clave = 'acepta_venta_fraccionada' and cond.activo
                   and cond.organizacion_id is null
                """) >= 1,
                "un cero aqui significaria que la comprobacion de arriba salio verde sobre un "
                        + "conjunto vacio");

        // Y en sujetos distintos (guarda de V77): el hecho normativo es de la
        // PROPIEDAD, el pacto es del ENCARGO. Juntarlos haria que el segundo
        // encargo heredara lo pactado en el primero.
        assertEquals("PROPIEDAD", sujetoDe("lote_minimo_normativo"));
        assertEquals("ENCARGO", sujetoDe("acepta_venta_fraccionada"));
    }

    /**
     * <b>{@code via_de_acceso} y {@code tipo_via_acceso} CONVIVEN.</b>
     *
     * <p>La primera dice CUAL es la via —«Panamericana Sur km 32»—, la segunda
     * de que CLASE es —avenida, pasaje, trocha—. No es una duplicidad como la de
     * {@code area_terreno}/{@code metraje_total}: alli las dos claves nombraban
     * el mismo numero, aqui nombran hechos distintos, y por eso una NO sustituye
     * a la otra. Nacen contiguas en el {@code orden} (940, 942, 944) para que un
     * agente las lea como la misma conversacion y no rellene una creyendo que es
     * la otra.
     */
    @Test
    @DisplayName("V85: via_de_acceso y tipo_via_acceso conviven, y las dos viajan y vuelven")
    void lasDosClavesDeLaViaConviven() {
        assertTrue(contar("""
                select count(*) from catalogo_atributo
                 where organizacion_id is null and activo
                   and clave in ('via_de_acceso', 'tipo_via_acceso', 'estado_via')
                """) == 3, "las tres tienen que estar vivas: ninguna sustituye a otra");

        long id = registrarTerreno();
        editar(id, new ValorAtributo("via_de_acceso", "Panamericana Sur km 32"));
        editar(id, new ValorAtributo("tipo_via_acceso", "CARRETERA"));
        editar(id, new ValorAtributo("estado_via", "AFIRMADA"));

        assertEquals("Panamericana Sur km 32", valorDe(id, "via_de_acceso"),
                "escribir la clase de via no puede pisar cual es");
        assertEquals("CARRETERA", valorDe(id, "tipo_via_acceso"));
        assertEquals("AFIRMADA", valorDe(id, "estado_via"));

        List<Integer> ordenes = jdbc.queryForList("""
                select orden from catalogo_atributo
                 where organizacion_id is null
                   and clave in ('via_de_acceso', 'tipo_via_acceso', 'estado_via')
                 order by orden
                """, Integer.class);
        assertEquals(List.of(940, 942, 944), ordenes,
                "van contiguas a proposito: separarlas las pondria en dos pantallas y el "
                        + "agente rellenaria una creyendo que es la otra");
    }

    // ==================================================================
    // 5. Web y KAIROS reciben la MISMA definicion del Core
    // ==================================================================

    /**
     * <b>Las 18 llegan al alta y al editor solas, por el motor universal.</b>
     *
     * <p>No se comprueba que Angular las pinte —eso seria pedirle al backend que
     * lea el frontend— sino que <b>la definicion que Angular consume ya las
     * trae</b>, con su tipo y, donde toca, su vocabulario. El SPA no conoce
     * claves: {@code MotorDeCaptura.controlDe} deriva el control del
     * vocabulario. Y KAIROS pide exactamente esta misma definicion —
     * {@code InterpreteDeterminista} llama a {@code ClienteBrox.catalogoDe} y no
     * lleva ninguna lista propia—, asi que este caso es tambien la prueba de que
     * los dos canales reciben la misma definicion del Core.
     *
     * <p>Cada clave se pregunta contra un tipo <b>al que aplica</b>: que no
     * aparezca donde no aplica no es un fallo, es el catalogo funcionando.
     */
    @Test
    @DisplayName("V85: las 18 llegan a la definicion de captura sin tocar ninguna interfaz")
    void lasDieciochoLleganAlMotorSinTocarNingunaInterfaz() {
        List<String> ausentes = new ArrayList<>();
        List<String> sinVocabulario = new ArrayList<>();
        for (String[] clave : LAS_DIECIOCHO) {
            MotorDeCaptura.Pregunta pregunta = captura
                    .definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA", actor())
                    .todas().stream()
                    .filter(p -> clave[0].equals(p.clave()))
                    .findFirst().orElse(null);
            if (pregunta == null) {
                ausentes.add(clave[0]);
                continue;
            }
            if ("LISTA".equals(clave[1]) && (pregunta.opciones() == null
                    || pregunta.opciones().isEmpty())) {
                sinVocabulario.add(clave[0]);
            }
        }
        assertEquals(List.of(), ausentes, """
                El motor de captura no publica estas claves del suelo.

                Son filas de catalogo. Si no salen aqui, la unica forma de que aparezcan
                en una pantalla seria escribirlas en Angular -- y eso es la matriz
                «tipo -> campos» que D-A-1 prohibe y que rompe el build. KAIROS pide esta
                misma definicion, asi que tampoco las veria.
                """);
        assertEquals(List.of(), sinVocabulario,
                "una LISTA que llega al motor sin opciones se pinta como texto libre: es "
                        + "exactamente lo que le pasaba a `servicios_disponibles`");

        // Y `area_terreno` YA NO se pregunta para un terreno, que es la mitad
        // retirada del corte vista desde el motor.
        assertFalse(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "TERRENO", "VENTA", actor())
                        .todas().stream().anyMatch(p -> "area_terreno".equals(p.clave())),
                "el guion del terreno no puede seguir preguntando la superficie dos veces");
        assertTrue(captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "CASA", "VENTA", actor())
                        .todas().stream().anyMatch(p -> "area_terreno".equals(p.clave())),
                "y a una CASA si se le sigue preguntando: ahi no duplica nada");
    }

    // ------------------------------------------------------------------

    /**
     * <b>Un {@code area_terreno} huerfano sobre un TERRENO, sembrado de forma
     * ATOMICA.</b>
     *
     * <p>Abre la aplicabilidad que D-7 retiro, escribe la fila y la vuelve a
     * cerrar <b>dentro de un unico bloque {@code DO}</b> — una sola sentencia,
     * una sola transaccion. Si algo falla en medio, PostgreSQL deshace las tres
     * cosas y la base queda como estaba; no hay ninguna ventana en la que
     * {@code area_terreno} vuelva a aplicar a {@code T} para el resto de las
     * suites que comparten esta base.
     *
     * <p>Es el mismo patron que el gate SQL de este corte (`SAVEPOINT
     * repite_5b`), y por la misma razon: la puerta esta cerrada a proposito y la
     * unica forma de fabricar el caso es abrirla un instante. Que haga falta
     * abrirla <b>es</b> la prueba de que esta cerrada.
     *
     * <p>Y se escribe con {@code frontera_de_linaje() - 1 dia}, retrasando
     * tambien el registro de la propiedad para que la linea de tiempo sea
     * coherente: un huerfano es legado por definicion.
     */
    private void sembrarHuerfano(long idPropiedad, int valor) {
        jdbc.execute("""
                do $huerfano$
                begin
                    insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                        requerido, exigencia)
                    select c.id_catalogo_atributo, 'T', false, 'OPC' from catalogo_atributo c
                     where c.clave = 'area_terreno' and c.organizacion_id is null;

                    update propiedad set fecha_registro = frontera_de_linaje() - interval '2 days'
                     where id_propiedad = %1$d;

                    insert into atributo_propiedad (organizacion_id, id_propiedad, clave,
                                                    valor_numero, fecha_creacion)
                    select organizacion_id, id_propiedad, 'area_terreno', %2$d,
                           frontera_de_linaje() - interval '1 day'
                      from propiedad where id_propiedad = %1$d;

                    delete from catalogo_atributo_tipo t using catalogo_atributo c
                     where c.id_catalogo_atributo = t.id_catalogo_atributo
                       and c.clave = 'area_terreno' and c.organizacion_id is null
                       and t.tipo_propiedad = 'T';
                end $huerfano$;
                """.formatted(idPropiedad, valor));
    }

    private List<String> vocabulario(String clave) {
        return jdbc.queryForList("""
                select o.valor from catalogo_atributo_opcion o
                  join catalogo_atributo c on c.id_catalogo_atributo = o.id_catalogo_atributo
                 where c.clave = ? and c.organizacion_id is null and o.activo
                 order by o.orden
                """, String.class, clave);
    }

    private List<String> exigenciasDe(String clave) {
        return jdbc.queryForList("""
                select t.tipo_propiedad || '=' || t.exigencia
                  from catalogo_atributo c
                  join catalogo_atributo_tipo t on t.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.clave = ? and c.organizacion_id is null
                 order by t.tipo_propiedad
                """, String.class, clave);
    }

    private String sujetoDe(String clave) {
        return jdbc.queryForObject("""
                select sujeto from catalogo_atributo
                 where clave = ? and organizacion_id is null
                """, String.class, clave);
    }

    /**
     * Las claves que hoy impiden publicar esta propiedad, agregadas del catalogo
     * y sin nombrar ninguna. Preguntar por una clave concreta y luego
     * «descubrir» que la causa es esa clave no demuestra nada.
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

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private void editar(long id, ValorAtributo valor) {
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(valor), null, null), actor());
    }

    private String valorDe(long id, String clave) {
        PropiedadUniversalService.AtributoFicha ficha = fichaDe(id, clave);
        return ficha == null ? null : ficha.valor();
    }

    private PropiedadUniversalService.AtributoFicha fichaDe(long id, String clave) {
        return propiedades.consultar(id, actor()).atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .findFirst().orElse(null);
    }

    /**
     * Un terreno registrable y con las DOS {@code PUB} de 5A ya declaradas, para
     * que la unica causa de bloqueo que quede sea la que estrena 5B. Sin esto,
     * los casos de exigencia medirian el trabajo del corte anterior.
     */
    private long registrarTerreno() {
        return registrar("TERRENO",
                List.of(new ValorAtributo("metraje_total", "500"),
                        new ValorAtributo("zonificacion", "RDM"),
                        new ValorAtributo("agua_desague", "CONECTADO"),
                        new ValorAtributo("energia_electrica", "CONECTADO")),
                new OperacionSolicitada("VENTA", new BigDecimal("300000"), "USD",
                        null, null, null, null, null, null, null));
    }

    private long registrar(String tipo, List<ValorAtributo> atributos,
                           OperacionSolicitada operacion) {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, tipo, null,
                "Caso 5B " + tipo,
                new Ubicacion("Av. Suelo " + UUID.randomUUID(), "Lurin",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                atributos, List.of(operacion), null), actor).idPropiedad();
    }

    private long encargoDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select min(id_captacion) from captacion where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    private PublicacionService.DatosPublicacion publicacionDePrueba() {
        return new PublicacionService.DatosPublicacion("WEB_PROPIA", null, new BigDecimal("2500"),
                "PEN", "Anuncio de prueba", null, null);
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }
}
