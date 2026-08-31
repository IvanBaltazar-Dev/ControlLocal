package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura.DefinicionCaptura;
import com.controllocal.service.captura.MotorDeCaptura.Pregunta;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.EscritorEstructural;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>El gate de autoridad</b> (D-E4-3).
 *
 * <h2>La regla que vigila</h2>
 * <pre>
 *   Cada clave publicada por /captura/definicion tiene UNA autoridad
 *   persistente declarada.
 *
 *     cero autoridades  = campo fantasma: se pide y no se guarda
 *     dos autoridades   = doble verdad: se guarda dos veces y divergen
 * </pre>
 *
 * <h2>Por que hace falta un test y no basta el cuidado</h2>
 * Este repositorio llego a tener <b>siete</b> conceptos viviendo a la vez como
 * columna de {@code propiedad} y como fila de {@code atributo_propiedad}, con
 * uno solo sincronizado. Nadie lo hizo a proposito: cada mitad se anadio en su
 * momento por una razon buena, y la contradiccion aparecio en medio. Es
 * exactamente la clase de fallo que no se ve leyendo un fichero, porque las dos
 * mitades estan en ficheros distintos.
 *
 * <p>Contra PostgreSQL real porque la autoridad se declara en el CATALOGO, que
 * es dato, no codigo: un tenant puede anadir sus propias claves y este gate
 * tiene que verlas.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AutoridadDelDatoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MotorDeCaptura motor;
    @Autowired PropiedadUniversalService propiedades;

    /**
     * Un departamento con un valor de cada familia de autoridad: `ambientes`
     * gobernado y `metraje_total` estructural. Se escribe por el caso de uso
     * real, no por SQL — lo que se prueba es el ENRUTADOR.
     */
    private long registrarDepartamentoDePrueba() {
        Actor actor = actorAgente();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());

        var resultado = propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(
                null, null, null, "DEPARTAMENTO", null, "Prueba del enrutador de autoridad",
                new PropiedadUniversalService.Ubicacion(
                        "Av. Autoridad " + System.nanoTime() % 100000, "Miraflores",
                        null, null, null, null, null, null, null),
                List.of(new PropiedadUniversalService.Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new PropiedadUniversalService.ValorAtributo("metraje_total", "90"),
                        new PropiedadUniversalService.ValorAtributo("dormitorios", "2"),
                        new PropiedadUniversalService.ValorAtributo("ambientes", "5")),
                List.of(new PropiedadUniversalService.OperacionSolicitada(
                        "VENTA", new java.math.BigDecimal("150000"), "USD",
                        null, null, null, null, null, null, null)),
                null), actor);
        return resultado.idPropiedad();
    }

    private Actor actorAgente() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol limit 1
                """).stream().findFirst().orElseThrow();
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), Actor.AGENTE);
    }

    private static final List<String> TIPOS = List.of(
            "LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO");
    private static final List<String> OPERACIONES = List.of("VENTA", "ALQUILER");

    /**
     * Las columnas espejo que quedaban de antes de D-E4-3. <b>Ya son cero</b>:
     * V62 retiro las seis del esquema y del agregado, despues de que el paso 8
     * comprobara 0 lectores y 0 escritores.
     *
     * <p>El mapa se queda —vacio— y no se borra, porque el test que lo usa
     * cambio de sentido y ahora es mas util: ya no vigila que la deuda encoja,
     * vigila que <b>no vuelva</b>. Una septima columna espejo anadida manana no
     * es deuda heredada; es una doble verdad recien creada.
     */
    private static final Map<String, String> ESPEJOS_PENDIENTES = new LinkedHashMap<>();

    private Actor actor() {
        Long idOrganizacion = jdbc.queryForObject(
                "select min(organizacion_id) from catalogo_atributo where organizacion_id is not null",
                Long.class);
        Long idRol = jdbc.queryForObject(
                "select min(id_persona_rol) from persona_rol where tipo_rol = 'AGENTE'", Long.class);
        return new Actor(idOrganizacion == null ? 1L : idOrganizacion, 1L,
                idRol == null ? 1L : idRol, Actor.AGENTE);
    }

    /** Todas las claves que el motor publica, para los siete tipos y las dos operaciones. */
    private Set<String> clavesPublicadas() {
        Set<String> claves = new TreeSet<>();
        Actor actor = actor();
        for (String tipo : TIPOS) {
            for (String operacion : OPERACIONES) {
                DefinicionCaptura definicion =
                        motor.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, operacion, actor);
                definicion.todas().stream().map(Pregunta::clave).forEach(claves::add);
            }
        }
        return claves;
    }

    // ==================================================================

    @Test
    @DisplayName("cada clave publicada declara exactamente una autoridad persistente")
    void unaAutoridadPorClave() {
        List<String> fantasmas = new ArrayList<>();
        List<String> doblesVerdades = new ArrayList<>();

        for (String clave : clavesPublicadas()) {
            boolean esEstructuralDelGuion = GuionRegistroPropiedad.esEstructural(clave);
            // Una clave publicada puede venir CALIFICADA con su operacion
            // --`garantia_meses:ALQUILER`, `importe:VENTA`--. Lo que se busca en
            // el catalogo es siempre la base: el sufijo dice a que encargo
            // pertenece la respuesta, no que clave es.
            String base = GuionRegistroPropiedad.claveBase(clave);

            Map<String, Object> enCatalogo = jdbc.queryForList("""
                    select destino, campo_estructural, sujeto from catalogo_atributo
                     where clave = ? and activo limit 1
                    """, base).stream().findFirst().orElse(null);

            // La autoridad del guion y la del catalogo NO se suman: si el
            // catalogo declara ESTRUCTURAL, esta cediendo al campo canonico, y
            // eso sigue siendo UNA autoridad.
            boolean autoridadEstructural = esEstructuralDelGuion
                    || (enCatalogo != null && "ESTRUCTURAL".equals(enCatalogo.get("destino")));
            boolean deLaPropiedad = enCatalogo != null
                    && "ATRIBUTO".equals(enCatalogo.get("destino"))
                    && !"ENCARGO".equals(enCatalogo.get("sujeto"));
            // Desde el Corte 0C hay una tercera autoridad legitima, y es UNA:
            // `atributo_encargo`. Sin contarla, cada condicion comercial que el
            // motor publique se leeria aqui como campo fantasma.
            boolean delEncargo = enCatalogo != null
                    && "ENCARGO".equals(enCatalogo.get("sujeto"));

            int autoridades = (autoridadEstructural ? 1 : 0) + (deLaPropiedad ? 1 : 0)
                    + (delEncargo ? 1 : 0);

            if (autoridades == 0) {
                fantasmas.add(clave);
            } else if (autoridades > 1) {
                doblesVerdades.add(clave + " (mas de una autoridad declarada)");
            }
            // Y una condicion del ENCARGO tiene que viajar CALIFICADA. Desnuda
            // no dice a cual de los dos encargos pertenece la respuesta, y con
            // una venta y un alquiler declarados a la vez eso no se puede
            // adivinar: adivinarlo seria escribir en el equivocado.
            if (delEncargo && GuionRegistroPropiedad.operacionDe(clave) == null) {
                doblesVerdades.add(clave + " (es del ENCARGO y se publica sin su operacion)");
            }
        }

        if (!fantasmas.isEmpty() || !doblesVerdades.isEmpty()) {
            fail("""
                    La definicion publica claves sin una autoridad unica.

                    CAMPOS FANTASMA (se piden y no se guardan en ninguna parte): %s
                    DOBLES VERDADES (se guardarian en dos sitios): %s

                    Cada clave publicada tiene que declarar donde vive su valor:
                    o es estructural del agregado, o es un atributo gobernado con
                    destino = ATRIBUTO. Nunca las dos, nunca ninguna (D-E4-3).
                    """.formatted(fantasmas, doblesVerdades));
        }
    }

    @Test
    @DisplayName("un atributo ESTRUCTURAL declara que concepto representa, y uno normal no")
    void laDeclaracionEstaCompleta() {
        // La invariante la impone `ck_catalogo_autoridad_completa`; esto da el
        // mensaje que se entiende, y comprueba ademas el vocabulario.
        List<Map<String, Object>> incompletos = jdbc.queryForList("""
                select clave, destino, campo_estructural from catalogo_atributo
                 where (destino = 'ESTRUCTURAL' and campo_estructural is null)
                    or (destino = 'ATRIBUTO'    and campo_estructural is not null)
                """);
        assertTrue(incompletos.isEmpty(),
                "Un ESTRUCTURAL sin concepto obliga a adivinarlo, y un ATRIBUTO con concepto "
                        + "declara dos sitios para el mismo valor: " + incompletos);

        // Un concepto estructural es del DOMINIO, no una columna de PostgreSQL.
        List<String> conceptos = jdbc.queryForList("""
                select distinct campo_estructural from catalogo_atributo
                 where campo_estructural is not null
                """, String.class);
        for (String concepto : conceptos) {
            assertTrue(concepto.matches("[A-Z_]+"),
                    "El concepto estructural \"" + concepto + "\" parece un nombre fisico. El "
                            + "catalogo no debe conocer la topologia de la base: METRAJE, no "
                            + "propiedad.metraje.");
        }
    }

    /**
     * <b>Ninguna columna espejo puede volver.</b>
     *
     * <p>Con la lista vacia, este test dice que {@code propiedad} no tiene
     * ninguna de las seis. Falla en dos casos, y los dos importan: si alguien
     * repone una "por comodidad de mapeo", y si una futura migracion la recrea
     * y alguien la anade a la lista sin haber medido nada.
     */
    @Test
    @DisplayName("ninguna columna espejo vuelve a propiedad")
    void laDeudaDeColumnasEspejoNoCrece() {
        Set<String> declaradas = new LinkedHashSet<>(ESPEJOS_PENDIENTES.values());

        Set<String> realesEnLaTabla = new TreeSet<>(jdbc.queryForList("""
                select column_name from information_schema.columns
                 where table_schema = 'public' and table_name = 'propiedad'
                   and column_name in ('ambientes','frente','zonificacion','cuota_mantenimiento',
                                       'numero_estacionamientos','antiguedad_anios')
                """, String.class));

        assertEquals(new TreeSet<>(declaradas), realesEnLaTabla,
                "La lista de columnas espejo pendientes dejo de coincidir con la base. Si se "
                        + "retiro una, quitala de ESPEJOS_PENDIENTES; si aparecio una nueva, "
                        + "eso es una doble verdad recien creada (D-E4-3).");
    }

    /**
     * <b>El escritor enruta por autoridad, y los dos caminos se excluyen.</b>
     *
     * <p>Estas son las tres comprobaciones del paso 4 de D-E4-3, y la segunda
     * es la que de verdad importa: mientras existan las 23 copias historicas de
     * {@code metraje_total}, una prueba de LECTURA daria verde aunque el
     * escritor nuevo siguiera generando la copia. Por eso se cuenta cuantas hay
     * antes y despues, en vez de mirar si existe alguna.
     */
    @Test
    @DisplayName("crear enruta cada valor a su autoridad, y solo a una")
    void crearEnrutaPorAutoridad() {
        long copiasAntes = copiasDeMetraje();
        long id = registrarDepartamentoDePrueba();

        // 1 · el gobernado va al atributo, y NO a su columna espejo
        assertEquals("5", valorDeAtributo(id, "ambientes"),
                "un atributo gobernado se guarda en atributo_propiedad");
        // Ya no se comprueba "y NO en la columna espejo": V62 la retiro, asi que la
        // garantia dejo de ser una asercion y paso a ser el esquema.

        // 2 · el estructural va a su campo canonico, y NO deja atributo
        assertEquals("90.00", columna(id, "metraje"),
                "un concepto estructural se guarda en su campo canonico");
        assertEquals(null, valorDeAtributo(id, "metraje_total"),
                "y NO deja copia como atributo");
        assertEquals(copiasAntes, copiasDeMetraje(),
                "el alta no puede aumentar las copias historicas de metraje_total");
    }

    @Test
    @DisplayName("editar mantiene exactamente el mismo reparto que el alta")
    void editarEnrutaIgualQueCrear() {
        long copiasAntes = copiasDeMetraje();
        long id = registrarDepartamentoDePrueba();

        jdbc.update("""
                update atributo_propiedad set valor_numero = 7 where id_propiedad = ? and clave = 'ambientes'
                """, id);

        assertEquals("7", valorDeAtributo(id, "ambientes"));
        assertEquals(copiasAntes, copiasDeMetraje(),
                "ni la edicion puede crear una copia de metraje_total");
    }

    private long copiasDeMetraje() {
        Long n = jdbc.queryForObject(
                "select count(*) from atributo_propiedad where clave = 'metraje_total'", Long.class);
        return n == null ? 0L : n;
    }

    private String valorDeAtributo(long idPropiedad, String clave) {
        return jdbc.queryForList("""
                select coalesce(valor_numero::text, valor_texto, valor_booleano::text) as v
                  from atributo_propiedad where id_propiedad = ? and clave = ?
                """, String.class, idPropiedad, clave).stream()
                .findFirst()
                // Los numericos se guardan con escala; se compara sin ceros de mas.
                .map(v -> v.contains(".") ? v.replaceAll("0+$", "").replaceAll("\\.$", "") : v)
                .orElse(null);
    }

    private String columna(long idPropiedad, String columna) {
        return jdbc.queryForObject(
                "select " + columna + "::text from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    /**
     * <b>`metraje` es el unico estructural, y no debe tener copia.</b>
     *
     * <p>Hoy la tiene: {@code metraje_total} sigue escribiendose en
     * {@code atributo_propiedad}. El paso 5 de D-E4-3 la retira. Mientras
     * tanto, lo que este test exige es que las dos digan lo MISMO — porque una
     * divergencia aqui significaria que la busqueda por metraje y la ficha
     * estan dando numeros distintos.
     */
    @Test
    @DisplayName("metraje no tiene ninguna copia como atributo: su autoridad es el campo canonico")
    void metrajeNoTieneCopia() {
        assertEquals(0L, copiasDeMetraje(),
                "`metraje` se declaro ESTRUCTURAL (D-E4-3) y V61 retiro sus copias. Una fila nueva "
                        + "aqui significa que algun escritor volvio a esquivar el enrutador de "
                        + "autoridad, y con ella vuelve la doble verdad.");
    }

    /**
     * <b>Ninguna propiedad puede reportar el metraje como faltante por el sitio
     * equivocado.</b>
     *
     * <p>Al retirar las copias, la consulta que mide lo que falta —que busca en
     * {@code atributo_propiedad}— habria dado `metraje_total` como ausente en
     * TODAS las propiedades, aunque su campo canonico estuviera relleno. Es el
     * efecto colateral directo de mover la autoridad, y por eso se comprueba
     * aqui y no en un comentario.
     */
    @Test
    @DisplayName("lo que falta se mide contra la autoridad de cada clave, no contra una tabla")
    void loQueFaltaMiraLasDosAutoridades() {
        Long propiedadesConMetraje = jdbc.queryForObject(
                "select count(*) from propiedad where metraje is not null", Long.class);
        assertTrue(propiedadesConMetraje != null && propiedadesConMetraje > 0,
                "el escenario exige propiedades con metraje puesto");

        long id = registrarDepartamentoDePrueba();
        var ficha = propiedades.consultar(id, actorAgente());

        assertTrue(ficha.atributosQueFaltan().stream().noneMatch(a -> "metraje_total".equals(a.clave())),
                "una propiedad con metraje en su campo canonico no puede reportarlo como faltante: "
                        + "faltantes = " + ficha.atributosQueFaltan());
    }

    // ==================================================================
    // IDA Y VUELTA: crear -> leer -> editar OTRA cosa -> releer
    //
    // El test mas barato de esta tanda y el que mas cubre. Encontro el fallo que
    // ni el compilador, ni los gates de esquema, ni las pruebas de persistencia
    // podian ver: `metraje` se seguia guardando bien y habia dejado de poder
    // leerse por el API, porque se movio el escritor y no el lector.
    //
    // Y lleva el "editar OTRA cosa" dentro a proposito: casi todo lo que se
    // pierde, se pierde al no tocarlo. Un campo que el usuario edita se nota roto
    // enseguida; uno que solo pasa por el formulario sin que nadie lo mire
    // desaparece en silencio.
    // ==================================================================

    /**
     * <b>El lector devuelve lo que el escritor guardo, y no dice donde estaba.</b>
     *
     * <p>Las dos autoridades en la misma respuesta: {@code metraje_total} vive en
     * el campo canonico y {@code ambientes} en {@code atributo_propiedad}, y la
     * ficha los publica igual. Si el lector volviera a mirar solo la tabla de
     * atributos, el metraje desapareceria de aqui -- que es exactamente lo que
     * paso al mover su autoridad.
     */
    @Test
    @DisplayName("la ficha universal publica las dos autoridades sin distinguirlas")
    void elLectorEnrutaComoElEscritor() {
        long id = registrarDepartamentoDePrueba();
        FichaPropiedadUniversal ficha = propiedades.consultar(id, actorAgente());

        assertEquals("90", atributoDeLaFicha(ficha, "metraje_total"),
                "metraje_total es ESTRUCTURAL y su fila en atributo_propiedad ya no existe; aun "
                        + "asi el cliente tiene que seguir viendolo entre los atributos");
        assertEquals("5", atributoDeLaFicha(ficha, "ambientes"),
                "y el gobernado igual: el consumidor no distingue de donde salio cada uno");
    }

    /**
     * <b>El rango que V4 tenia en un CHECK sigue vigente tras mudarse.</b>
     *
     * <p>Al retirar las columnas espejo, PostgreSQL se llevo cuatro CHECK de rango
     * con ellas -- {@code ambientes > 0} entre otros -- y {@code atributo_propiedad}
     * no tenia con que sustituirlos: su trigger valida el TIPO del valor, no su
     * rango. V62 los mudo a {@code catalogo_atributo.valor_minimo} ANTES del DROP,
     * para que el invariante no dejara de existir en ningun momento.
     */
    @Test
    @DisplayName("el minimo heredado de V4 sigue rechazando un valor fuera de rango")
    void elRangoDeV4SobrevivioAlDrop() {
        long id = registrarDepartamentoDePrueba();

        assertThrows(Exception.class, () -> jdbc.update("""
                update atributo_propiedad set valor_numero = 0
                 where id_propiedad = ? and clave = 'ambientes'
                """, id),
                "`ambientes > 0` era un CHECK de la columna espejo; tras V62 lo impone el "
                        + "trigger contra catalogo_atributo.valor_minimo");

        assertEquals(1L, jdbc.queryForObject("""
                select count(*) from catalogo_atributo
                 where clave = 'ambientes' and organizacion_id is null and valor_minimo = 1
                """, Long.class),
                "y el minimo se declara en el CATALOGO, no en el codigo: la clave la puede "
                        + "anadir un tenant y su rango es parte de lo que la define");
    }

    /**
     * <b>El rango llega al cliente por CONTRATO, no reimplementado.</b>
     *
     * <p>`Restricciones` existia en el contrato desde el principio y viajaba
     * SIEMPRE en null, asi que cada cliente acababa escribiendo su copia: el
     * formulario de locales llevaba `ambientes >= 1` a mano. Una regla con dos
     * duenos es la misma clase de problema que D-E4-3 cerro para los valores,
     * aplicada a las reglas.
     */
    @Test
    @DisplayName("la definicion publica el minimo que declara el catalogo")
    void elContratoPublicaElRango() {
        var definicion = motor.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD,
                "DEPARTAMENTO", "VENTA", actor());

        MotorDeCaptura.Pregunta ambientes = definicion.todas().stream()
                .filter(p -> "ambientes".equals(p.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la definicion no publica `ambientes`"));

        assertNotNull(ambientes.restricciones(),
                "sin restricciones el cliente se inventa las suyas, que es lo que pasaba");
        assertEquals(0, new java.math.BigDecimal("1").compareTo(ambientes.restricciones().minimo()),
                "el minimo sale de catalogo_atributo.valor_minimo, donde V62 mudo el CHECK de V4");
        assertEquals(0, ambientes.restricciones().decimales(),
                "y que no admita decimales se deduce de su tipo, no de su nombre");
    }

    /**
     * <b>El mismo rango, con un mensaje que se entiende.</b>
     *
     * <p>El trigger lo rechazaria igualmente, pero con un error de PostgreSQL a
     * mitad de una transaccion. La base es la garantia; esto es el mensaje.
     */
    @Test
    @DisplayName("un valor bajo el minimo se rechaza con el nombre del atributo delante")
    void elMinimoSeExplicaAntesDeLlegarALaBase() {
        Actor actor = actorAgente();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());

        var comando = new PropiedadUniversalService.ComandoRegistro(
                null, null, null, "DEPARTAMENTO", null, null,
                new PropiedadUniversalService.Ubicacion(
                        "Av. Rango " + System.nanoTime() % 100000, "Miraflores",
                        null, null, null, null, null, null, null),
                List.of(new PropiedadUniversalService.Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new PropiedadUniversalService.ValorAtributo("metraje_total", "90"),
                        new PropiedadUniversalService.ValorAtributo("dormitorios", "2"),
                        new PropiedadUniversalService.ValorAtributo("ambientes", "0")),
                List.of(new PropiedadUniversalService.OperacionSolicitada(
                        "VENTA", new java.math.BigDecimal("150000"), "USD",
                        null, null, null, null, null, null, null)),
                null);

        var error = assertThrows(ReglaNegocioException.class,
                () -> propiedades.registrar(comando, actor));

        assertTrue(error.getMessage().contains("ambientes"),
                "el mensaje tiene que decir QUE atributo: " + error.getMessage());
        assertTrue(error.getMessage().contains("1"),
                "y cual era el minimo, para que se pueda corregir: " + error.getMessage());
    }

    // ==================================================================
    // V79 - La identidad registral, y la cadena estructural entera
    // ==================================================================

    /**
     * <b>La partida y la oficina viven en el agregado, no en un EAV.</b>
     *
     * <p>Es la mitad que define el corte. Escritas por el caso de uso real y por
     * su clave logica —el cliente no sabe donde caen— tienen que aparecer en las
     * columnas de {@code propiedad} y <b>no dejar ni una fila</b> en
     * {@code atributo_propiedad}: dos sitios para el mismo hecho es la doble
     * verdad que D-E4-3 cerro.
     */
    @Test
    @DisplayName("V79: la identidad registral cae en el agregado y no duplica fila EAV")
    void laIdentidadRegistralEsAutoridadEstructural() {
        long id = registrarConIdentidadRegistral("11223344", "LIMA");

        Map<String, Object> fila = jdbc.queryForMap("""
                select partida_registral, oficina_registral from propiedad where id_propiedad = ?
                """, id);
        assertEquals("11223344", fila.get("partida_registral"),
                "la partida tiene que quedar en su campo canonico");
        assertEquals("LIMA", fila.get("oficina_registral"),
                "y la oficina en el suyo");

        assertEquals(0L, jdbc.queryForObject("""
                select count(*) from atributo_propiedad
                 where id_propiedad = ? and clave in ('partida_registral', 'oficina_registral')
                """, Long.class, id),
                "una clave ESTRUCTURAL no deja fila en atributo_propiedad: si la dejara habria "
                        + "dos verdades y divergirian en la primera edicion");
    }

    /**
     * <b>Y aun asi el cliente las ve entre los atributos.</b>
     *
     * <p>La promesa de D-E4-3 dicha al reves: la autoridad fisica es nueva y el
     * contrato logico no se movio. Quien consume la ficha pide
     * {@code partida_registral} y la recibe, sin enterarse de que salio de una
     * columna.
     */
    @Test
    @DisplayName("V79: la ficha publica la identidad registral como un atributo mas")
    void laIdentidadRegistralVuelvePorElContratoLogico() {
        long id = registrarConIdentidadRegistral("11223344", "CALLAO");
        FichaPropiedadUniversal ficha = propiedades.consultar(id, actorAgente());

        assertEquals("11223344", atributoDeLaFicha(ficha, "partida_registral"));
        assertEquals("CALLAO", atributoDeLaFicha(ficha, "oficina_registral"));
    }

    /**
     * <b>Editar otra cosa no toca la identidad</b> — el gate del Corte 0A
     * aplicado a lo que este corte introduce.
     *
     * <pre>
     *   leer -> editar OTRO dato -> releer = identidad identica
     * </pre>
     *
     * <p>Es la comprobacion que encuentra el fallo mas caro de un cambio de
     * autoridad: un editor que reconstruye el agregado desde el cuerpo recibido
     * pone a null lo que el cuerpo no traia, y borra en silencio un dato que
     * nadie pidio borrar.
     */
    @Test
    @DisplayName("V79: editar otro atributo deja la identidad registral intacta")
    void editarOtraCosaConservaLaIdentidad() {
        long id = registrarConIdentidadRegistral("11223344", "HUAURA");

        propiedades.editar(id, new PropiedadUniversalService.ComandoEdicion(
                null, null, null, null, null,
                List.of(new PropiedadUniversalService.ValorAtributo("ambientes", "7")),
                null, null), actorAgente());

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actorAgente());
        assertEquals("11223344", atributoDeLaFicha(ficha, "partida_registral"),
                "la partida se perdio al editar un dato que no tiene nada que ver");
        assertEquals("HUAURA", atributoDeLaFicha(ficha, "oficina_registral"),
                "y la oficina igual");
        assertEquals("7", atributoDeLaFicha(ficha, "ambientes"),
                "y lo que si se pidio cambiar, cambio");
    }

    /**
     * <b>Cambiar la identidad cambia solo lo declarado.</b>
     *
     * <p>La otra mitad de la anterior: se manda la partida nueva y la oficina
     * —que no viajaba— tiene que quedarse donde estaba. Un editor que tratara
     * las dos columnas como un bloque las pisaria juntas.
     */
    @Test
    @DisplayName("V79: cambiar la partida no arrastra la oficina")
    void laEdicionExplicitaCambiaSoloLoDeclarado() {
        long id = registrarConIdentidadRegistral("11223344", "HUARAL");

        propiedades.editar(id, new PropiedadUniversalService.ComandoEdicion(
                null, null, null, null, null,
                List.of(new PropiedadUniversalService.ValorAtributo("partida_registral", "99887766")),
                null, null), actorAgente());

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actorAgente());
        assertEquals("99887766", atributoDeLaFicha(ficha, "partida_registral"));
        assertEquals("HUARAL", atributoDeLaFicha(ficha, "oficina_registral"),
                "la oficina no viajaba en la edicion, asi que no se toca");
    }

    /**
     * <b>Retirar tambien enruta por autoridad.</b>
     *
     * <p>Quien pide quitar la partida dice la clave logica y nada mas. Sin este
     * camino, {@code atributosABorrar} buscaria una fila en
     * {@code atributo_propiedad} que no existe y la peticion se perderia sin
     * error: la regla «clave -> autoridad» valdria para escribir y leer, y no
     * para borrar.
     *
     * <p>Y tiene que poder hacerse: una partida se teclea mal, y la unica forma
     * de decir «esto que puse no es cierto» sin inventar otra es quitarla.
     */
    @Test
    @DisplayName("V79: retirar la partida la deja vacia, no en un valor inventado")
    void retirarLaPartidaLaDejaVacia() {
        long id = registrarConIdentidadRegistral("11223344", "BARRANCA");

        propiedades.editar(id, new PropiedadUniversalService.ComandoEdicion(
                null, null, null, null, null, null, null,
                List.of("partida_registral")), actorAgente());

        assertNull(jdbc.queryForMap(
                "select partida_registral from propiedad where id_propiedad = ?", id)
                .get("partida_registral"),
                "retirar tiene que dejarla NULL --que significa 'no se sabe'-- y no en cadena vacia");
        assertNull(atributoDeLaFicha(propiedades.consultar(id, actorAgente()), "partida_registral"),
                "y la ficha deja de publicarla, en vez de publicar un hueco");
        assertEquals("BARRANCA", atributoDeLaFicha(propiedades.consultar(id, actorAgente()),
                "oficina_registral"),
                "retirar una no retira la otra");
    }

    /**
     * <b>Ningun concepto declarado en el CATALOGO se queda sin escritor ni sin
     * lector</b> — la mitad de la simetria que necesita la base.
     *
     * <p>{@code CadenaEstructuralCompletaTest} recorre los conceptos declarados
     * en el codigo. Este recorre los que declara el catalogo, que es <b>dato</b>:
     * una fila con {@code destino = 'ESTRUCTURAL'} y un {@code campo_estructural}
     * que el codigo no conoce se pediria en el alta y no se guardaria en ninguna
     * parte, y el CHECK de la columna no puede verlo porque el CHECK solo
     * enumera nombres.
     */
    @Test
    @DisplayName("V79: todo concepto estructural del catalogo tiene escritor y lector")
    void elCatalogoNoDeclaraConceptosQueElCodigoNoSabeEscribir() {
        List<Map<String, Object>> declarados = jdbc.queryForList("""
                select clave, campo_estructural from catalogo_atributo
                 where destino = 'ESTRUCTURAL' and activo
                """);
        assertFalse(declarados.isEmpty(),
                "sin ninguna clave estructural este gate no vigila nada");

        List<String> huerfanos = new ArrayList<>();
        for (Map<String, Object> fila : declarados) {
            String concepto = (String) fila.get("campo_estructural");
            if (!EscritorEstructural.sabeEscribir(concepto)) {
                huerfanos.add(fila.get("clave") + " -> " + concepto);
            }
        }

        assertTrue(huerfanos.isEmpty(),
                "el catalogo declara conceptos estructurales que el codigo no sabe escribir: "
                        + huerfanos + ". Se pediran en el alta y su valor no se guardara en "
                        + "ninguna parte.");
    }

    /**
     * <b>La identidad registral es de la PROPIEDAD, y no del encargo.</b>
     *
     * <p>Dos encargos sucesivos sobre el mismo inmueble comparten partida
     * porque el inmueble es el mismo: no es un dato que se pacte. Se comprueba
     * por donde se declara —el sujeto del catalogo— porque es lo que decide en
     * que tabla vive el valor y que trigger lo vigila (V73).
     */
    @Test
    @DisplayName("V79: las seis claves registrales son del sujeto PROPIEDAD")
    void laIdentidadRegistralNoEsUnaCondicionDelEncargo() {
        List<String> claves = List.of("partida_registral", "oficina_registral", "independizado",
                "cargas_gravamenes", "area_segun_partida", "declaratoria_fabrica");

        for (String clave : claves) {
            Map<String, Object> fila = jdbc.queryForMap("""
                    select sujeto, aplica_todos from catalogo_atributo
                     where clave = ? and organizacion_id is null
                    """, clave);
            assertEquals("PROPIEDAD", fila.get("sujeto"),
                    clave + " se declaro del ENCARGO: una partida no se negocia, y si fuera del "
                            + "encargo el segundo alquiler no la heredaria");
            assertEquals(0L, jdbc.queryForObject("""
                    select count(*) from catalogo_atributo_operacion o
                      join catalogo_atributo c on c.id_catalogo_atributo = o.id_catalogo_atributo
                     where c.clave = ? and c.organizacion_id is null
                    """, Long.class, clave),
                    clave + " declara aplicabilidad en la tabla del ENCARGO");
        }
    }

    /**
     * <b>Las seis llegan al alta y al editor solas, por el motor universal.</b>
     *
     * <p>Es la prueba de D-A-1 dicha por el lado del Core: no se comprueba que
     * Angular las pinte —eso seria pedirle al backend que lea el frontend— sino
     * que <b>la definicion que Angular consume ya las trae</b>, con su tipo, su
     * exigencia y, donde toca, su vocabulario. Si el Core las publica y el mismo
     * renderizador que ya existe pinta lo que recibe, no hay nada que anadir en
     * el SPA; y el gate {@code FronteraDeAutoridadEnElSpaTest} impide que
     * alguien lo intente.
     *
     * <p>Cada clave se pregunta contra un tipo <b>al que aplica</b>: una que no
     * aparece donde no aplica no es un fallo, es el catalogo funcionando.
     */
    @Test
    @DisplayName("V79: las seis capacidades llegan a la definicion de captura por si solas")
    void lasSeisLleganAlAltaSinTocarNingunaInterfaz() {
        Map<String, String> dondeAplica = new LinkedHashMap<>();
        dondeAplica.put("partida_registral", "DEPARTAMENTO");
        dondeAplica.put("oficina_registral", "DEPARTAMENTO");
        dondeAplica.put("independizado", "DEPARTAMENTO");
        dondeAplica.put("declaratoria_fabrica", "CASA");
        dondeAplica.put("area_segun_partida", "TERRENO");
        dondeAplica.put("cargas_gravamenes", "DEPARTAMENTO");

        List<String> ausentes = new ArrayList<>();
        dondeAplica.forEach((clave, tipo) -> {
            DefinicionCaptura definicion = motor.definicion(
                    MotorDeCaptura.REGISTRAR_PROPIEDAD, tipo, "VENTA", actor());
            boolean publicada = definicion.todas().stream()
                    .map(Pregunta::clave)
                    .anyMatch(clave::equals);
            if (!publicada) {
                ausentes.add(clave + " en " + tipo);
            }
        });

        assertTrue(ausentes.isEmpty(), """
                El motor de captura no publica: %s

                Las seis son filas de catalogo. Si no salen aqui, la unica forma de que
                aparezcan en una pantalla seria escribirlas en Angular -- y eso es la
                matriz «tipo -> campos» que D-A-1 prohibe y que rompe el build.
                """.formatted(ausentes));
    }

    // ------------------------------------------------------------------

    /** Un departamento registrado con su identidad registral, por el caso de uso real. */
    private long registrarConIdentidadRegistral(String partida, String oficina) {
        Actor actor = actorAgente();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());

        return propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(
                null, null, codigoIrrepetible(), "DEPARTAMENTO", null, "Identidad registral V79",
                new PropiedadUniversalService.Ubicacion(
                        "Av. Registral " + java.util.UUID.randomUUID(), "Miraflores",
                        null, null, null, null, null, null, null),
                List.of(new PropiedadUniversalService.Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new PropiedadUniversalService.ValorAtributo("metraje_total", "90"),
                        new PropiedadUniversalService.ValorAtributo("dormitorios", "2"),
                        new PropiedadUniversalService.ValorAtributo("ambientes", "5"),
                        new PropiedadUniversalService.ValorAtributo("partida_registral", partida),
                        new PropiedadUniversalService.ValorAtributo("oficina_registral", oficina)),
                List.of(new PropiedadUniversalService.OperacionSolicitada(
                        "ALQUILER", new java.math.BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null)),
                null), actor).idPropiedad();
    }

    // ------------------------------------------------------------------

    private static String atributoDeLaFicha(FichaPropiedadUniversal ficha, String clave) {
        return ficha.atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(a -> a.valor())
                .findFirst()
                .orElse(null);
    }

    /**
     * <b>Un codigo que no puede chocar con el de otra corrida.</b>
     *
     * <p>Era {@code "AUT-" + System.nanoTime() % 1000000}, y esta suite
     * <b>comete</b>: las propiedades de cada ejecucion se quedan en la base de
     * pruebas. Con seis digitos el espacio es de un millon, asi que la colision
     * no era improbable sino cuestion de cuantas veces se corriera -- y en
     * Windows la resolucion de {@code nanoTime} agrupa los valores bajos, que
     * lo empeora.
     *
     * <p>Fallaba con {@code uq_propiedad_codigo} y un mensaje de PostgreSQL, no
     * con el fallo que el test vigila: es la peor clase de rojo, porque manda a
     * mirar al sitio equivocado.
     */
    private static String codigoIrrepetible() {
        return "AUT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
