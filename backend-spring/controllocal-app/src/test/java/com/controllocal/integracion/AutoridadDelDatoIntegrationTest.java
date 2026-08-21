package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.LocalComercialService.DatosLocal;
import com.controllocal.service.LocalComercialService.FichaLocal;
import com.controllocal.service.LocalComercialService;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.captura.GuionRegistroPropiedad;
import com.controllocal.service.captura.MotorDeCaptura.DefinicionCaptura;
import com.controllocal.service.captura.MotorDeCaptura.Pregunta;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Autowired LocalComercialService locales;

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
                 limit 1
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

            Map<String, Object> enCatalogo = jdbc.queryForList("""
                    select destino, campo_estructural from catalogo_atributo
                     where clave = ? and activo limit 1
                    """, clave).stream().findFirst().orElse(null);

            // La autoridad del guion y la del catalogo NO se suman: si el
            // catalogo declara ESTRUCTURAL, esta cediendo al campo canonico, y
            // eso sigue siendo UNA autoridad.
            boolean autoridadEstructural = esEstructuralDelGuion
                    || (enCatalogo != null && "ESTRUCTURAL".equals(enCatalogo.get("destino")));
            boolean autoridadAtributo =
                    enCatalogo != null && "ATRIBUTO".equals(enCatalogo.get("destino"));

            int autoridades = (autoridadEstructural ? 1 : 0) + (autoridadAtributo ? 1 : 0);

            if (autoridades == 0) {
                fantasmas.add(clave);
            } else if (autoridades > 1) {
                doblesVerdades.add(clave + " (estructural del guion Y atributo del catalogo)");
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
     * <b>El ida y vuelta de {@code /locales}, que es el camino que el paso 7 tuvo
     * que migrar por los DOS lados.</b>
     *
     * <p>Este recurso escribia los seis conceptos en columnas de {@code propiedad}
     * y los leia de las mismas columnas: una isla coherente consigo misma y ciega
     * respecto del modelo universal. Migrar solo el lector habria dejado cada PUT
     * escribiendo donde ya nadie lee, y es este test el que lo nota -- porque la
     * edicion del final no menciona ninguno de los seis.
     */
    @Test
    @DisplayName("/locales: los seis gobernados sobreviven a una edicion que no los toca")
    void localesIdaYVuelta() {
        Actor actor = actorAgente();
        String codigo = codigoIrrepetible();

        FichaLocal alta = locales.registrar(datosDePrueba(codigo, actor, new BigDecimal("7000")), actor);
        assertEquals(4, alta.ambientes(),
                "la respuesta del alta ya se lee por autoridad, no del objeto que llego");

        // --- leer por la ficha del detalle
        FichaLocal leida = locales.buscarPorId(alta.id(), actor).orElseThrow();
        assertEquals(4, leida.ambientes());
        assertEquals(12, leida.antiguedadAnios());
        assertEquals(0, new BigDecimal("6.5").compareTo(leida.frente()));
        assertEquals("CZ", leida.zonificacion());
        assertEquals(2, leida.numeroEstacionamientos());
        assertEquals(0, new BigDecimal("350").compareTo(leida.cuotaMantenimiento()));

        // --- leer por el LISTADO, que es otra consulta y otra hidratacion
        FichaLocal enListado = locales.listar(
                        new LocalComercialService.FiltrosLocal(codigo, null, 1, 10), actor)
                .items().stream().filter(f -> codigo.equals(f.codigoLocal())).findFirst()
                .orElseThrow(() -> new AssertionError("el alta no aparece en su propio listado"));
        assertEquals(4, enListado.ambientes(),
                "el listado hidrata los gobernados por lote; si se olvida, la columna sale vacia");
        assertEquals("CZ", enListado.zonificacion());
        assertEquals(0, new BigDecimal("350").compareTo(enListado.cuotaMantenimiento()));
        assertNotNull(enListado.metraje(),
                "y el estructural sigue viniendo de la proyeccion, que es donde se puede ordenar");

        // --- editar SOLO el precio: ninguno de los seis se menciona
        locales.actualizar(alta.id(), datosDePrueba(codigo, actor, new BigDecimal("7500")), actor);

        FichaLocal despues = locales.buscarPorId(alta.id(), actor).orElseThrow();
        assertEquals(0, new BigDecimal("7500").compareTo(despues.precioReferencial()));
        assertEquals(4, despues.ambientes(), "ambientes no se toco y no puede haberse perdido");
        assertEquals("CZ", despues.zonificacion());
        assertEquals(0, new BigDecimal("350").compareTo(despues.cuotaMantenimiento()));
        assertEquals(2, despues.numeroEstacionamientos());
        assertEquals(12, despues.antiguedadAnios());
        assertEquals(0, new BigDecimal("6.5").compareTo(despues.frente()));
    }

    /**
     * <b>Un valor que se retira se retira de verdad.</b>
     *
     * <p>{@code PUT /locales} manda el objeto ENTERO, asi que un campo vacio
     * significa "ya no lo se" y no "no lo toques". Eso lo hacia antes gratis un
     * {@code UPDATE} que ponia la columna a NULL; con la autoridad en
     * {@code atributo_propiedad} hay que retirar la FILA, y olvidarlo dejaria un
     * valor viejo pegado a la propiedad para siempre.
     */
    @Test
    @DisplayName("/locales: vaciar un gobernado retira su fila, no la deja con el valor viejo")
    void vaciarUnGobernadoLoRetira() {
        Actor actor = actorAgente();
        String codigo = codigoIrrepetible();
        FichaLocal alta = locales.registrar(datosDePrueba(codigo, actor, new BigDecimal("7000")), actor);

        DatosLocal sinZonificacion = new DatosLocal(codigo, "Av. Ida y Vuelta " + codigo,
                "Miraflores", new BigDecimal("120"), new BigDecimal("7000"), "PEN", "Cafeteria",
                "Local de prueba del ida y vuelta de autoridad", alta.idPropietario(), "D", "L",
                null, 4, 12, "Zona A", null, null, null, new BigDecimal("6.5"), null,
                Boolean.TRUE, null, 2, new BigDecimal("350"));
        locales.actualizar(alta.id(), sinZonificacion, actor);

        assertEquals(null, locales.buscarPorId(alta.id(), actor).orElseThrow().zonificacion(),
                "la zonificacion llego vacia: su fila tiene que haberse retirado");
        assertEquals(0L, jdbc.queryForObject("""
                select count(*) from atributo_propiedad
                 where id_propiedad = ? and clave = 'zonificacion'
                """, Long.class, alta.id()),
                "y no quedar en la tabla con el valor anterior");
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

    // ------------------------------------------------------------------

    private static String atributoDeLaFicha(FichaPropiedadUniversal ficha, String clave) {
        return ficha.atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(a -> a.valor())
                .findFirst()
                .orElse(null);
    }

    /** Un LOCAL con los seis gobernados puestos. El precio es lo unico que varia. */
    private DatosLocal datosDePrueba(String codigo, Actor actor, BigDecimal precio) {
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return new DatosLocal(codigo, "Av. Ida y Vuelta " + codigo, "Miraflores",
                new BigDecimal("120"), precio, "PEN", "Cafeteria",
                "Local de prueba del ida y vuelta de autoridad", idPropietario, "D", "L",
                null, 4, 12, "Zona A", null, null, null,
                new BigDecimal("6.5"), "CZ", Boolean.TRUE, null, 2,
                new BigDecimal("350"));
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