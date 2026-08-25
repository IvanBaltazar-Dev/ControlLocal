package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.CondicionesDeEncargo;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El gate de la procedencia granular</b> (microcorte 4.P, D-4P-1, V83).
 *
 * <h2>Que se prueba, y por que no basta con «los tests pasan»</h2>
 * Lo que este corte promete no es una columna: es que <b>el sistema pueda
 * reconstruir historias reales</b>. Un valor que se escribe, cambia y se borra
 * tiene que poder contarse entero despues, y con la fila vigente ya ausente.
 *
 * <p>Por eso cada prueba de aqui recorre un caso completo y mira la tabla de
 * linaje, no un booleano:
 *
 * <pre>
 *   1  valor simple nuevo    ausente -> A, con la procedencia de A
 *   2  edicion               A -> B: A se conserva, y su procedencia tambien
 *   3  borrado               B -> ausencia vigente; la historia de B sigue
 *   4  LISTA_MULTIPLE        [A,B] -> [B,C]: se conocen los DOS conjuntos
 *   5  ESTRUCTURAL legado    100 -> 105 sin inventar quien origino el 100
 *   6  operacion mixta       tres naturalezas en un guardado, sin contaminarse
 *   7  INFERIDO              no se acepta una inferencia sin autor
 *   8  ENCARGO               la misma garantia que la PROPIEDAD
 * </pre>
 *
 * <h2>Por que contra PostgreSQL</h2>
 * Casi todo lo que se afirma aqui lo garantiza un CHECK o un trigger —el
 * vocabulario de {@code naturaleza}, la completitud del {@code INFERIDO}, el
 * append-only— y eso no lo lee javac, ni Hibernate, ni ArchUnit.
 *
 * <h2>La frontera que este gate NO cruza</h2>
 * Un {@code INSERT} manual en {@code atributo_propiedad} <b>sigue sin dejar
 * linaje</b>, y no es un descuido: seis suites E2E y
 * {@code gate-modelo-universal.sql} escriben asi a proposito para probar los
 * triggers de la base intentando romperlos. Lo que se garantiza es que
 * <b>ninguna operacion del producto</b> escribe un valor sin decir de donde
 * sale, y {@link com.controllocal.arquitectura.LinajeDeTodaEscrituraTest} vigila
 * esa frontera. Aqui se demuestra ademas usando esa misma via para fabricar el
 * legado del caso 5.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProcedenciaDelValorIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired com.controllocal.service.soporte.LectorPorAutoridad lector;
    @Autowired com.controllocal.persistence.repositorio.PropiedadRepository fichaFisica;

    private static final String PROPIEDAD = "PROPIEDAD";
    private static final String ENCARGO = "ENCARGO";

    // ==================================================================
    // CASO 1 - un valor simple nuevo
    // ==================================================================

    @Test
    @DisplayName("1 - un valor nuevo queda con SU procedencia, no solo con la del acto")
    void valorSimpleNuevo() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, new ValorAtributo("torre_bloque", "Torre A"));

        List<Map<String, Object>> historia = historia(actor, PROPIEDAD, idPropiedad, "torre_bloque");
        assertEquals(1, historia.size(), "un alta deja exactamente una fila de linaje");

        Map<String, Object> alta = historia.get(0);
        assertEquals("ALTA", alta.get("verbo"));
        assertEquals("Torre A", alta.get("valor_texto"));
        // Antes no habia nada, asi que no hay nada que constatar como hallado.
        assertNull(alta.get("hallado_texto"), "un ALTA no puede haber encontrado un valor");

        // La procedencia OPERACIONAL esta completa aunque nadie declarara la
        // naturaleza: quien lo escribio, por que canal y cuando.
        assertEquals("SPA", alta.get("canal"));
        assertEquals(actor.idRolOperativo(), ((Number) alta.get("id_persona_rol")).longValue());
        assertEquals("AGENTE", alta.get("rol_actor"));
        assertNotNull(alta.get("registrado_en"));
        // Y la naturaleza AUSENTE, porque el productor no la declaro. Ausente no
        // es una cuarta clase de evidencia: es que no consta como se obtuvo.
        assertNull(alta.get("naturaleza"));
    }

    // ==================================================================
    // CASO 2 - editar no destruye lo anterior
    // ==================================================================

    @Test
    @DisplayName("2 - editar A por B conserva A, la procedencia de A, y registra la de B")
    void editarConservaLoAnterior() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, new ValorAtributo("estado_conservacion", "BUENO"));

        editar(actor, idPropiedad, Procedencia.deCabecera("API"),
                new ValorAtributo("estado_conservacion", "MUY_BUENO", null, null,
                        "OBSERVADO", null, null, null));

        List<Map<String, Object>> historia =
                historia(actor, PROPIEDAD, idPropiedad, "estado_conservacion");
        assertEquals(2, historia.size(), "el alta y la edicion, las dos");

        Map<String, Object> alta = historia.get(0);
        Map<String, Object> edicion = historia.get(1);

        // A SIGUE AHI, con su propia procedencia intacta: es la fila que
        // `uq_atributo_propiedad_clave` impedia tener y que el UPDATE pisaba.
        assertEquals("ALTA", alta.get("verbo"));
        assertEquals("BUENO", alta.get("valor_texto"));
        assertEquals("SPA", alta.get("canal"));
        assertNull(alta.get("naturaleza"));

        // Y B queda con la suya, que es distinta en los dos ejes.
        assertEquals("EDICION", edicion.get("verbo"));
        assertEquals("MUY_BUENO", edicion.get("valor_texto"));
        assertEquals("API", edicion.get("canal"));
        assertEquals("OBSERVADO", edicion.get("naturaleza"));
        // Ademas, la edicion CONSTATA lo que encontro. No es lo mismo que leer
        // la fila anterior: eso solo funciona cuando la hay.
        assertEquals("BUENO", edicion.get("hallado_texto"));

        // Y el estado vigente sigue siendo uno solo.
        assertEquals("MUY_BUENO", vigente(idPropiedad, "estado_conservacion"));
    }

    // ==================================================================
    // CASO 3 - borrar no destruye la historia
    // ==================================================================

    @Test
    @DisplayName("3 - retirado el valor, la clave queda con historia y sin fila vigente")
    void borrarConservaLaHistoria() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, new ValorAtributo("torre_bloque", "Torre B"));

        editar(actor, idPropiedad, Procedencia.deLaPantalla(), List.of(), List.of("torre_bloque"));

        // La fila vigente ya no esta: el borrado es fisico y sigue siendolo.
        assertNull(vigente(idPropiedad, "torre_bloque"));

        List<Map<String, Object>> historia = historia(actor, PROPIEDAD, idPropiedad, "torre_bloque");
        assertEquals(2, historia.size());
        Map<String, Object> retirada = historia.get(1);

        assertEquals("RETIRADA", retirada.get("verbo"));
        // No queda valor -- y el que habia, si.
        assertNull(retirada.get("valor_texto"));
        assertEquals("Torre B", retirada.get("hallado_texto"),
                "una retirada tiene que decir QUE se quito: es la ultima vez que ese dato existe");
        assertNotNull(retirada.get("id_persona_rol"));
        assertNotNull(retirada.get("registrado_en"));

        // Esto es lo que seria imposible si el linaje colgara del id de la fila
        // vigente: la clave queda CON linaje y SIN valor.
        assertEquals("Torre B", historia.get(0).get("valor_texto"));
    }

    // ==================================================================
    // CASO 4 - el multivalor conserva el CONJUNTO anterior, no la diferencia
    // ==================================================================

    @Test
    @DisplayName("4 - [A,B] -> [B,C]: la historia conoce los dos conjuntos enteros")
    void multivalorConservaElConjuntoAnterior() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, ValorAtributo.multiple(
                "vigilancia", List.of("CASETA_24H", "CAMARAS_CCTV")));

        editar(actor, idPropiedad, Procedencia.deLaPantalla(), ValorAtributo.multiple(
                "vigilancia", List.of("CAMARAS_CCTV", "CONTROL_DE_ACCESO")));

        List<Map<String, Object>> historia = historia(actor, PROPIEDAD, idPropiedad, "vigilancia");
        assertEquals(2, historia.size());

        long idAlta = ((Number) historia.get(0).get("id_rastro")).longValue();
        long idEdicion = ((Number) historia.get(1).get("id_rastro")).longValue();

        assertTrue((Boolean) historia.get(0).get("es_multivalor"));
        // El conjunto de partida, entero.
        assertEquals(List.of("CAMARAS_CCTV", "CASETA_24H"), conjunto(idAlta, "ESCRITO"));
        assertEquals(List.of(), conjunto(idAlta, "HALLADO"));

        // Y en la edicion, los DOS conjuntos completos. No un diff: «se quito
        // CASETA_24H» no permite reconstruir que habia si el conjunto anterior
        // fuera legado y nadie lo hubiera escrito nunca.
        assertEquals(List.of("CAMARAS_CCTV", "CASETA_24H"), conjunto(idEdicion, "HALLADO"));
        assertEquals(List.of("CAMARAS_CCTV", "CONTROL_DE_ACCESO"), conjunto(idEdicion, "ESCRITO"));

        // El vigente es el nuevo, y solo el nuevo. Esto es lo que caza el fallo
        // real de esta via: el elemento que esta en LOS DOS conjuntos.
        assertEquals(List.of("CAMARAS_CCTV", "CONTROL_DE_ACCESO"),
                vigenteMultivalor(idPropiedad, "vigilancia"));
    }

    @Test
    @DisplayName("4 bis - vaciar una lista es una escritura, y tiene autor y fecha")
    void vaciarUnMultivalorTambienDejaLinaje() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, ValorAtributo.multiple(
                "vigilancia", List.of("PORTERO_DIURNO")));

        // El conjunto vacio NO es «no respondio»: es «respondio que ninguno», y
        // alguien lo hizo en un momento concreto.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                ValorAtributo.multiple("vigilancia", List.of()));

        Map<String, Object> edicion = ultima(actor, PROPIEDAD, idPropiedad, "vigilancia");
        assertEquals("EDICION", edicion.get("verbo"));
        assertTrue((Boolean) edicion.get("es_multivalor"));
        long idEdicion = ((Number) edicion.get("id_rastro")).longValue();
        assertEquals(List.of("PORTERO_DIURNO"), conjunto(idEdicion, "HALLADO"));
        assertEquals(List.of(), conjunto(idEdicion, "ESCRITO"));
        assertEquals(List.of(), vigenteMultivalor(idPropiedad, "vigilancia"));
    }

    // ==================================================================
    // CASO 5 - la clave ESTRUCTURAL, que NO CREA FILA
    // ==================================================================

    @Test
    @DisplayName("5 - metraje 100 -> 105: el 100 queda constatado, y nadie inventa quien lo puso")
    void estructuralLegadoConservaElValorHallado() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // `metraje_total` es ESTRUCTURAL: su autoridad es `propiedad.metraje` y
        // NO deja fila en `atributo_propiedad`. Se comprueba, porque es la razon
        // por la que ninguna columna en esa tabla podia dar linaje a esta clave.
        assertEquals(0, filasDeAtributo(idPropiedad, "metraje_total"));

        // El LEGADO, fabricado como se fabrica de verdad: una escritura por SQL
        // directo, fuera de la frontera del servicio. Deja el valor y NO deja
        // linaje -- que es exactamente el estado de los datos anteriores a V83.
        jdbc.update("update propiedad set metraje = 100 where id_propiedad = ?", idPropiedad);
        int linajeAntes = historia(actor, PROPIEDAD, idPropiedad, "metraje_total").size();

        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                new ValorAtributo("metraje_total", "105"));

        List<Map<String, Object>> historia =
                historia(actor, PROPIEDAD, idPropiedad, "metraje_total");
        assertEquals(linajeAntes + 1, historia.size(),
                "la edicion de una estructural anade UNA fila de linaje, ni cero ni dos");

        Map<String, Object> edicion = historia.get(historia.size() - 1);
        assertEquals("EDICION", edicion.get("verbo"));
        // Lo que el Core ENCONTRO: 100. No lo que dijera la ultima fila de
        // linaje -- que decia otra cosa --, sino lo que habia en la columna.
        assertEquals(0, new BigDecimal("100")
                .compareTo((BigDecimal) edicion.get("hallado_numero")));
        assertEquals(0, new BigDecimal("105")
                .compareTo((BigDecimal) edicion.get("valor_numero")));

        // Y del 100 NO se afirma nada mas. La fila entera describe la EDICION:
        // su canal, su actor y su instante son los de quien escribio el 105.
        // Del valor anterior solo consta que estaba ahi.
        assertEquals("SPA", edicion.get("canal"));
        assertNull(edicion.get("naturaleza"));
        assertNull(edicion.get("hallado_moneda"));

        // El 105 esta donde tiene que estar: en la columna, no en una fila nueva.
        assertEquals(0, filasDeAtributo(idPropiedad, "metraje_total"));
        assertEquals(0, new BigDecimal("105").compareTo(jdbc.queryForObject(
                "select metraje from propiedad where id_propiedad = ?",
                BigDecimal.class, idPropiedad)));
    }

    @Test
    @DisplayName("5 bis - un valor legado sin linaje no es defecto, y su primera edicion lo rescata")
    void legadoSinLinajeNoEsDefectoAntesDelCutover() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // Una estructural que el alta no escribio, puesta por fuera: el retrato
        // exacto de un dato anterior al cutover.
        jdbc.update("update propiedad set partida_registral = ? where id_propiedad = ?",
                "P-11111111", idPropiedad);
        assertEquals(0, historia(actor, PROPIEDAD, idPropiedad, "partida_registral").size(),
                "antes del cutover puede existir legado SIN linaje, y no es un defecto");

        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                new ValorAtributo("partida_registral", "P-99999999"));

        List<Map<String, Object>> historia =
                historia(actor, PROPIEDAD, idPropiedad, "partida_registral");
        assertEquals(1, historia.size());
        Map<String, Object> edicion = historia.get(0);
        assertEquals("EDICION", edicion.get("verbo"));
        assertEquals("P-11111111", edicion.get("hallado_texto"));
        assertEquals("P-99999999", edicion.get("valor_texto"));

        // La frontera esta definida en el modelo y se puede preguntar, que es lo
        // que permite a un gate decir de que lado cae cada fila.
        assertNotNull(jdbc.queryForObject("select frontera_de_linaje()", java.sql.Timestamp.class));
    }

    // ==================================================================
    // CASO 5 ter - `ubicacion.piso`: la puerta que usa el producto
    //
    // De los nueve huecos de `UbicacionRequest`, `piso` es el UNICO que ademas
    // es una clave del catalogo, declarada ESTRUCTURAL sobre el campo PISO. Y es
    // el unico camino por el que el SPA lo manda: `propiedades.service.ts` lo
    // pone en CAMPOS_DE_UBICACION y el editor lo enruta por `ubicacion`, nunca
    // por `atributos`.
    //
    // Hasta la segunda vuelta de 4.P eso lo escribia `propiedad::setPiso` desde
    // `aplicarUbicacion`, o sea por fuera del enrutador: editar el piso desde la
    // pantalla NO dejaba linaje jamas, y la historia quedaba con una EDICION y
    // una RETIRADA sin ALTA -- el primer valor aparecia de la nada.
    // ==================================================================

    @Test
    @DisplayName("5 ter - el piso mandado dentro de `ubicacion` deja linaje, como cualquier clave")
    void elPisoDeLaUbicacionEsUnaClaveGobernada() {
        Actor actor = actor();
        long idPropiedad = registrarConPiso(actor, "3");

        // El ALTA existe. Sin ella, la historia de este valor empezaria por una
        // edicion y el 3 no habria estado nunca en ninguna parte.
        List<Map<String, Object>> historia = historia(actor, PROPIEDAD, idPropiedad, "piso");
        assertEquals(1, historia.size(), "el piso del alta tiene que dejar su ALTA");
        assertEquals("ALTA", historia.get(0).get("verbo"));
        assertEquals("3", historia.get(0).get("valor_texto"));
        assertEquals("SPA", historia.get(0).get("canal"));
        // Y sigue siendo ESTRUCTURAL: su autoridad es la columna, no una fila.
        assertEquals(0, filasDeAtributo(idPropiedad, "piso"));
        assertEquals("3", pisoDe(idPropiedad));

        // La edicion por el MISMO hueco del cable que usa el SPA.
        propiedades.editar(idPropiedad, new ComandoEdicion(null, Procedencia.deLaPantalla(),
                null, ubicacionConPiso("8"), null, null, null, null), actor);

        Map<String, Object> edicion = ultima(actor, PROPIEDAD, idPropiedad, "piso");
        assertEquals("EDICION", edicion.get("verbo"));
        assertEquals("3", edicion.get("hallado_texto"));
        assertEquals("8", edicion.get("valor_texto"));
        assertEquals("8", pisoDe(idPropiedad));

        // Y la retirada cierra una historia COMPLETA: alta, edicion y retirada.
        // Antes de la correccion sobraba la primera y el 3 no constaba nunca.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(), List.of(), List.of("piso"));
        List<String> verbos = historia(actor, PROPIEDAD, idPropiedad, "piso").stream()
                .map(fila -> (String) fila.get("verbo"))
                .toList();
        assertEquals(List.of("ALTA", "EDICION", "RETIRADA"), verbos);
        assertNull(pisoDe(idPropiedad));
    }

    @Test
    @DisplayName("5 ter bis - el piso por los dos huecos a la vez se rechaza, no se elige")
    void elPisoPorLosDosHuecosSeRechaza() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // El MISMO valor por los dos huecos no es una contradiccion, y no puede
        // serlo: la ficha publica el piso dentro de `ubicacion` Y entre los
        // atributos, asi que un cliente que devuelve lo que el Core le dio manda
        // los dos. Se acepta y se escribe una sola vez.
        propiedades.editar(idPropiedad, new ComandoEdicion(
                null, Procedencia.deLaPantalla(), null, ubicacionConPiso("5"), null,
                List.of(new ValorAtributo("piso", "5")), null, null), actor);
        assertEquals("5", pisoDe(idPropiedad));
        assertEquals(1, historia(actor, PROPIEDAD, idPropiedad, "piso").size(),
                "el mismo valor por dos huecos es UNA escritura, no dos");

        // Con valores DISTINTOS si son dos ordenes contrarias, y se avisa.
        ReglaNegocioException dosVeces = assertThrows(ReglaNegocioException.class,
                () -> propiedades.editar(idPropiedad, new ComandoEdicion(
                        null, Procedencia.deLaPantalla(), null, ubicacionConPiso("6"), null,
                        List.of(new ValorAtributo("piso", "9")), null, null), actor));
        assertTrue(dosVeces.getMessage().contains("valores distintos"), dosVeces.getMessage());

        // Y no escribio ninguna de las dos: el piso sigue siendo el de antes y
        // el linaje no gano ninguna fila.
        assertEquals("5", pisoDe(idPropiedad));
        assertEquals(1, historia(actor, PROPIEDAD, idPropiedad, "piso").size());
    }

    /**
     * <b>Las dos puertas responden lo mismo</b> — decisión del titular, 2026-08-25.
     *
     * <p>`piso` aplica a `D`, `L` y `O` y a nadie más. Antes de 4.P una `CASA`
     * recibía <b>200</b> por {@code ubicacion.piso} y <b>400</b> por
     * {@code atributos.piso}: la misma pregunta con dos respuestas según el hueco
     * por el que entrara, y la permisiva era justamente la puerta <b>no
     * gobernada</b>.
     *
     * <p>Esta prueba fija esa decisión. Hoy la simetría se sostiene porque
     * {@code conElPisoGobernado} mete el valor <b>en el mismo mapa</b> que los
     * atributos, así que la aplicabilidad se comprueba una sola vez y en un solo
     * sitio. Pero nada impide que mañana alguien valide antes de la fusión y las
     * dos puertas vuelvan a divergir <b>sin que nada se ponga rojo</b>. Una
     * decisión recién tomada que ningún test sostiene se pierde en dos cortes.
     *
     * <p>Se exige el <b>mismo mensaje</b>, no sólo que las dos fallen: dos
     * rechazos con motivos distintos serían otra vez dos reglas.
     */
    @Test
    @DisplayName("una CASA rechaza el piso IGUAL por `ubicacion` que por `atributos`")
    void lasDosPuertasDanLaMismaRespuesta() {
        Actor actor = actor();

        // La marca es de ESTA corrida: la suite comete, asi que contar por una
        // descripcion fija mediria tambien lo que dejaron las anteriores.
        String marca = "casa-4p-" + UUID.randomUUID();

        ReglaNegocioException porUbicacion = assertThrows(ReglaNegocioException.class,
                () -> registrarCasa(actor, marca, ubicacionConPiso("2"), List.of()));
        ReglaNegocioException porAtributos = assertThrows(ReglaNegocioException.class,
                () -> registrarCasa(actor, marca, ubicacion(),
                        List.of(new ValorAtributo("piso", "2"))));

        assertEquals(porAtributos.getMessage(), porUbicacion.getMessage(),
                "las dos puertas tienen que dar la MISMA respuesta: si difieren, hay dos reglas");
        assertTrue(porUbicacion.getMessage().contains("no aplica a una propiedad de tipo CASA"),
                porUbicacion.getMessage());

        // Y el rechazo no deja nada a medias: la transaccion revierte entera.
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from propiedad
                 where organizacion_id = ? and descripcion = ?
                """, Integer.class, actor.idOrganizacion(), marca),
                "un 400 no puede dejar media propiedad registrada");

        // El control positivo de la simetria: donde SI aplica, las dos puertas
        // aceptan. Sin esto, un fallo que rechazara siempre pasaria por simetria.
        long porUnaPuerta = registrarConPiso(actor, "2");
        long porLaOtra = registrar(actor, new ValorAtributo("piso", "2"));
        assertEquals("2", pisoDe(porUnaPuerta));
        assertEquals("2", pisoDe(porLaOtra));
        assertEquals("ALTA", ultima(actor, PROPIEDAD, porUnaPuerta, "piso").get("verbo"));
        assertEquals("ALTA", ultima(actor, PROPIEDAD, porLaOtra, "piso").get("verbo"));
    }

    // ==================================================================
    // Lo que NO es un hecho no se anota
    // ==================================================================

    @Test
    @DisplayName("retirar una clave que nunca tuvo valor no escribe una RETIRADA")
    void noSeAnotaUnaRetiradaQueNoOcurrio() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // Nombrar en `atributosABorrar` una clave que no estaba es legitimo: el
        // cliente no siempre sabe si habia valor. Lo que no puede pasar es que
        // eso deje un hecho fechado, con autor y canal, en una tabla que no se
        // puede corregir ni borrar.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(), List.of(),
                List.of("torre_bloque"));

        assertEquals(0, historia(actor, PROPIEDAD, idPropiedad, "torre_bloque").size(),
                "el linaje cuenta lo que paso, no lo que se pidio");

        // Y con valor SI se anota: el control positivo de la regla anterior.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                new ValorAtributo("torre_bloque", "Torre Z"));
        editar(actor, idPropiedad, Procedencia.deLaPantalla(), List.of(),
                List.of("torre_bloque"));
        assertEquals(List.of("ALTA", "RETIRADA"),
                historia(actor, PROPIEDAD, idPropiedad, "torre_bloque").stream()
                        .map(fila -> (String) fila.get("verbo")).toList());
    }

    @Test
    @DisplayName("retirar un multivalor vacio conserva que la clave estaba respondida")
    void retirarUnMultivalorVacioNoLoConfundeConAusencia() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, ValorAtributo.multiple(
                "vigilancia", List.of("NO_TIENE")));
        // Se vacia sin retirar: la clave sigue respondida, con el conjunto vacio.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                ValorAtributo.multiple("vigilancia", List.of()));

        editar(actor, idPropiedad, Procedencia.deLaPantalla(), List.of(), List.of("vigilancia"));

        Map<String, Object> retirada = ultima(actor, PROPIEDAD, idPropiedad, "vigilancia");
        assertEquals("RETIRADA", retirada.get("verbo"));
        // Lo que se retiro era un CONJUNTO --vacio, pero conjunto--: la clave
        // estaba respondida y su ancla existia. Leerlo como escalar habria dicho
        // "aqui no habia nada", que es falso y ademas borraria la retirada.
        assertTrue((Boolean) retirada.get("es_multivalor"),
                "el conjunto vacio es un conjunto, tambien al retirarlo");
        assertEquals(List.of(),
                conjunto(((Number) retirada.get("id_rastro")).longValue(), "HALLADO"));
    }

    // ==================================================================
    // CASO 6 - una operacion, naturalezas distintas
    // ==================================================================

    @Test
    @DisplayName("6 - tres valores con tres naturalezas en un guardado, sin contaminarse")
    void unaOperacionConNaturalezasDistintas() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // El caso que abrio 4.P, literal: lo vi, me lo dijeron, y no consta.
        editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                new ValorAtributo("estado_conservacion", "BUENO", null, null,
                        "OBSERVADO", null, null, null),
                new ValorAtributo("mascotas_reglamento", "true", null, null,
                        "DECLARADO", null, null, null),
                new ValorAtributo("ascensores", "2"));

        assertEquals("OBSERVADO",
                ultima(actor, PROPIEDAD, idPropiedad, "estado_conservacion").get("naturaleza"));
        assertEquals("DECLARADO",
                ultima(actor, PROPIEDAD, idPropiedad, "mascotas_reglamento").get("naturaleza"));
        assertNull(ultima(actor, PROPIEDAD, idPropiedad, "ascensores").get("naturaleza"),
                "el tercero no hereda la naturaleza de sus companeros de peticion");

        // Y el otro eje es el mismo para los tres, porque es del ACTO.
        assertEquals("SPA", ultima(actor, PROPIEDAD, idPropiedad, "ascensores").get("canal"));
        assertEquals("SPA",
                ultima(actor, PROPIEDAD, idPropiedad, "estado_conservacion").get("canal"));
    }

    @Test
    @DisplayName("6 bis - el Core no deduce la naturaleza del canal, del actor ni del endpoint")
    void elCoreNoDeduceLaNaturaleza() {
        Actor actor = actor();

        // El MISMO valor, por dos canales y con dos actores distintos.
        long porLaPantalla = registrar(actor, new ValorAtributo("balcon", "true"));
        Actor otro = otroActor(actor);
        long porElApi = registrarCon(otro, Procedencia.deCabecera("API"),
                new ValorAtributo("balcon", "true"));

        assertNull(ultima(actor, PROPIEDAD, porLaPantalla, "balcon").get("naturaleza"));
        assertNull(ultima(otro, PROPIEDAD, porElApi, "balcon").get("naturaleza"));

        // Los canales SI difieren: es el otro eje, y ese si lo sabe el Core.
        assertEquals("SPA", ultima(actor, PROPIEDAD, porLaPantalla, "balcon").get("canal"));
        assertEquals("API", ultima(otro, PROPIEDAD, porElApi, "balcon").get("canal"));
        assertNotEquals(
                ultima(actor, PROPIEDAD, porLaPantalla, "balcon").get("id_persona_rol"),
                ultima(otro, PROPIEDAD, porElApi, "balcon").get("id_persona_rol"));
    }

    // ==================================================================
    // CASO 7 - INFERIDO no existe sin autor
    // ==================================================================

    @Test
    @DisplayName("7 - una inferencia incompleta se rechaza; una completa guarda modelo y confianza")
    void inferidoExigeAutorModeloVersionYConfianza() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);

        // Sin agente, sin modelo y sin confianza: no es una inferencia, es una
        // afirmacion sin autor.
        ReglaNegocioException sinNada = assertThrows(ReglaNegocioException.class,
                () -> editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                        new ValorAtributo("balcon", "true", null, null,
                                "INFERIDO", null, null, null)));
        assertTrue(sinNada.getMessage().contains("el agente"), sinNada.getMessage());
        assertTrue(sinNada.getMessage().contains("la confianza"), sinNada.getMessage());

        Procedencia deKairos = Procedencia.deAgente("WHATSAPP", "kairos", "vision-brox",
                "v3", "conv-4p", "turno-1", "msg-1", "veo un balcon en la foto");

        // Con agente, modelo y version pero SIN confianza: sigue incompleta.
        ReglaNegocioException sinConfianza = assertThrows(ReglaNegocioException.class,
                () -> editar(actor, idPropiedad, deKairos,
                        new ValorAtributo("balcon", "true", null, null,
                                "INFERIDO", null, null, null)));
        assertTrue(sinConfianza.getMessage().contains("la confianza"), sinConfianza.getMessage());

        // Completa: entra, y con las cuatro piezas guardadas.
        editar(actor, idPropiedad, deKairos,
                new ValorAtributo("balcon", "true", null, null,
                        "INFERIDO", new BigDecimal("0.810"), null, "foto:fachada-3"));

        Map<String, Object> fila = ultima(actor, PROPIEDAD, idPropiedad, "balcon");
        assertEquals("INFERIDO", fila.get("naturaleza"));
        assertEquals("kairos", fila.get("agente"));
        assertEquals("vision-brox", fila.get("agente_modelo"));
        assertEquals("v3", fila.get("agente_modelo_version"));
        assertEquals(0, new BigDecimal("0.810").compareTo((BigDecimal) fila.get("confianza")));
        assertEquals("WHATSAPP", fila.get("canal"));
        assertEquals("foto:fachada-3", fila.get("evidencia_ref"));

        // Y la base sostiene lo mismo por su cuenta: un INFERIDO sin autor no
        // entra ni por SQL directo.
        assertThrows(Exception.class, () -> jdbc.update("""
                insert into rastro_valor_gobernado
                    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, naturaleza)
                values (?, 'PROPIEDAD', ?, 'balcon', 'EDICION', 'x', 'INFERIDO')
                """, actor.idOrganizacion(), idPropiedad),
                "ck_rastro_inferido_completo tiene que rechazarlo en la base, no solo en Java");
    }

    // ==================================================================
    // CASO 8 - el ENCARGO, con la misma garantia
    // ==================================================================

    @Test
    @DisplayName("8 - una condicion del encargo deja linaje igual que un hecho del inmueble")
    void elEncargoTieneLaMismaGarantia() {
        Actor actor = actor();
        long idPropiedad = registrarConAlquiler(actor,
                List.of(new ValorAtributo("garantia_meses", "2"),
                        ValorAtributo.multiple("equipamiento_incluido",
                                List.of("COCINA", "REFRIGERADORA"))));
        long idEncargo = encargoDe(idPropiedad);

        // ALTA de las dos formas, escalar y conjunto.
        List<Map<String, Object>> garantia = historia(actor, ENCARGO, idEncargo, "garantia_meses");
        assertEquals(1, garantia.size());
        assertEquals("ALTA", garantia.get(0).get("verbo"));
        assertEquals(0, new BigDecimal("2")
                .compareTo((BigDecimal) garantia.get(0).get("valor_numero")));
        assertEquals("SPA", garantia.get(0).get("canal"));

        List<Map<String, Object>> equipo =
                historia(actor, ENCARGO, idEncargo, "equipamiento_incluido");
        assertEquals(List.of("COCINA", "REFRIGERADORA"),
                conjunto(((Number) equipo.get(0).get("id_rastro")).longValue(), "ESCRITO"));

        // EDICION, con su naturaleza propia y el hallazgo del valor anterior.
        propiedades.editar(idPropiedad, new ComandoEdicion(null, Procedencia.deLaPantalla(),
                null, null, null, null, null, null,
                List.of(new CondicionesDeEncargo(idEncargo,
                        List.of(new ValorAtributo("garantia_meses", "3", null, null,
                                "DECLARADO", null, null, null),
                                ValorAtributo.multiple("equipamiento_incluido",
                                        List.of("COCINA", "LAVADORA"))),
                        null))), actor);

        Map<String, Object> edicion = ultima(actor, ENCARGO, idEncargo, "garantia_meses");
        assertEquals("EDICION", edicion.get("verbo"));
        assertEquals("DECLARADO", edicion.get("naturaleza"));
        assertEquals(0, new BigDecimal("2")
                .compareTo((BigDecimal) edicion.get("hallado_numero")));

        Map<String, Object> equipoEditado = ultima(actor, ENCARGO, idEncargo, "equipamiento_incluido");
        long idEquipoEdicion = ((Number) equipoEditado.get("id_rastro")).longValue();
        assertEquals(List.of("COCINA", "REFRIGERADORA"), conjunto(idEquipoEdicion, "HALLADO"));
        assertEquals(List.of("COCINA", "LAVADORA"), conjunto(idEquipoEdicion, "ESCRITO"));

        // RETIRADA, con lo que se llevo.
        propiedades.editar(idPropiedad, new ComandoEdicion(null, Procedencia.deLaPantalla(),
                null, null, null, null, null, null,
                List.of(new CondicionesDeEncargo(idEncargo, null,
                        List.of("garantia_meses")))), actor);

        Map<String, Object> retirada = ultima(actor, ENCARGO, idEncargo, "garantia_meses");
        assertEquals("RETIRADA", retirada.get("verbo"));
        assertNull(retirada.get("valor_numero"));
        assertEquals(0, new BigDecimal("3")
                .compareTo((BigDecimal) retirada.get("hallado_numero")));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from atributo_encargo
                 where id_captacion = ? and clave = 'garantia_meses'
                """, Integer.class, idEncargo));
    }

    // ==================================================================
    // Las garantias de la propia tabla
    // ==================================================================

    @Test
    @DisplayName("el linaje es append-only, y lo dice la base")
    void elLinajeNoSeEditaNiSeBorra() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, new ValorAtributo("torre_bloque", "Torre C"));
        long idRastro = ((Number) historia(actor, PROPIEDAD, idPropiedad, "torre_bloque")
                .get(0).get("id_rastro")).longValue();

        assertThrows(Exception.class, () -> jdbc.update(
                "update rastro_valor_gobernado set valor_texto = 'otra cosa' where id_rastro = ?",
                idRastro), "un linaje que se puede corregir no es un linaje");
        assertThrows(Exception.class, () -> jdbc.update(
                "delete from rastro_valor_gobernado where id_rastro = ?", idRastro));

        // Y sigue diciendo lo mismo despues de los dos intentos.
        assertEquals("Torre C", jdbc.queryForObject(
                "select valor_texto from rastro_valor_gobernado where id_rastro = ?",
                String.class, idRastro));
    }

    @Test
    @DisplayName("no existe una cuarta naturaleza, ni por SQL")
    void noHayCuartaNaturaleza() {
        Actor actor = actor();
        long idPropiedad = registrar(actor);
        assertThrows(Exception.class, () -> jdbc.update("""
                insert into rastro_valor_gobernado
                    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, naturaleza)
                values (?, 'PROPIEDAD', ?, 'torre_bloque', 'ALTA', 'x', 'DESCONOCIDO')
                """, actor.idOrganizacion(), idPropiedad),
                "DESCONOCIDO colapsaria «no consta como se supo» con «se supo por inferencia»");

        // Y el Core lo rechaza antes, con un mensaje que lo explica.
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> editar(actor, idPropiedad, Procedencia.deLaPantalla(),
                        new ValorAtributo("torre_bloque", "Torre D", null, null,
                                "DESCONOCIDO", null, null, null)));
        assertTrue(error.getMessage().contains("no consta"), error.getMessage());
    }

    @Test
    @DisplayName("evento_dominio sigue siendo el outbox y no sustituye al linaje")
    void elOutboxSigueSiendoElOutbox() {
        Actor actor = actor();
        long antes = eventos(actor);
        long idPropiedad = registrar(actor, new ValorAtributo("ascensores", "3"));

        // La operacion sigue dejando su evento...
        assertTrue(eventos(actor) > antes, "registrar sigue emitiendo PROPIEDAD_REGISTRADA");
        // ...y ese evento sigue sin decir que clave se escribio. Son dos
        // preguntas distintas, y por eso hacen falta las dos tablas.
        String carga = jdbc.queryForObject("""
                select carga_util from evento_dominio
                 where organizacion_id = ? and entidad_tipo = 'PROPIEDAD' and entidad_id = ?
                 order by id_evento desc limit 1
                """, String.class, actor.idOrganizacion(), idPropiedad);
        assertFalse(carga.contains("ascensores"),
                "el outbox describe la OPERACION; el linaje describe el DATO");
        assertEquals(1, historia(actor, PROPIEDAD, idPropiedad, "ascensores").size());
    }

    /**
     * <b>El conjunto sobrevive aunque alguien lo haya leido antes de guardar.</b>
     *
     * <p>La primera correccion del multivalor —leer el conjunto anterior como
     * texto en vez de como entidades— quitaba el disparo, no la trampa:
     * {@code LectorPorAutoridad} sigue devolviendo entidades, y bastaba que
     * alguien leyera la ficha en la misma transaccion para que el
     * {@code save} posterior volviera a resolverse como {@code merge} sobre una
     * fila ya borrada. La invariante que sostenia el arreglo —«ninguna entidad
     * de este tipo esta en el contexto cuando corre el borrado»— <b>no la fijaba
     * ningun test</b>.
     *
     * <p>Se cerro por construccion, tocando <b>solo lo que cambia</b>: el
     * elemento que esta en los dos conjuntos ya no se borra ni se reinserta, asi
     * que no hay merge que pueda convertirse en un UPDATE de nada. Esta prueba
     * es lo que impide que vuelva: hace exactamente lo que la trampa necesitaba
     * —leer antes de escribir, en la misma transaccion— y exige el conjunto
     * correcto.
     */
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("el conjunto sobrevive aunque la ficha se haya leido antes, en la misma transaccion")
    void elConjuntoSobreviveAunqueAlguienLoHayaLeidoAntes() {
        Actor actor = actor();
        long idPropiedad = registrar(actor, ValorAtributo.multiple(
                "vigilancia", List.of("CASETA_24H", "CAMARAS_CCTV")));

        // Esto es lo que hace un lector cualquiera, y lo que mete las entidades
        // del multivalor en el contexto de persistencia.
        lector.de(actor.idOrganizacion(),
                fichaFisica.findByOrganizacionIdAndId(actor.idOrganizacion(), idPropiedad)
                        .orElseThrow());

        editar(actor, idPropiedad, Procedencia.deLaPantalla(), ValorAtributo.multiple(
                "vigilancia", List.of("CAMARAS_CCTV", "CONTROL_DE_ACCESO")));

        assertEquals(List.of("CAMARAS_CCTV", "CONTROL_DE_ACCESO"),
                vigenteMultivalor(idPropiedad, "vigilancia"),
                "el elemento que estaba en los dos conjuntos se perdio: la escritura del "
                        + "multivalor volvio a depender de que nadie hubiera leido antes");
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private List<Map<String, Object>> historia(Actor actor, String sujeto, long idAgregado,
                                               String clave) {
        return jdbc.queryForList("""
                select * from rastro_valor_gobernado
                 where organizacion_id = ? and sujeto = ? and id_agregado = ? and clave = ?
                 order by id_rastro asc
                """, actor.idOrganizacion(), sujeto, idAgregado, clave);
    }

    private Map<String, Object> ultima(Actor actor, String sujeto, long idAgregado, String clave) {
        List<Map<String, Object>> historia = historia(actor, sujeto, idAgregado, clave);
        assertFalse(historia.isEmpty(), "no hay linaje de " + clave);
        return historia.get(historia.size() - 1);
    }

    /** El conjunto de un multivalor en una escritura, ordenado para poder compararlo. */
    private List<String> conjunto(long idRastro, String momento) {
        return jdbc.queryForList("""
                select valor from rastro_valor_opcion
                 where id_rastro = ? and momento = ?
                 order by valor asc
                """, String.class, idRastro, momento);
    }

    private String vigente(long idPropiedad, String clave) {
        List<String> valores = jdbc.queryForList("""
                select valor_texto from atributo_propiedad
                 where id_propiedad = ? and clave = ?
                """, String.class, idPropiedad, clave);
        return valores.isEmpty() ? null : valores.get(0);
    }

    private List<String> vigenteMultivalor(long idPropiedad, String clave) {
        return jdbc.queryForList("""
                select o.valor from atributo_propiedad_opcion o
                  join atributo_propiedad a using (id_atributo_propiedad)
                 where a.id_propiedad = ? and a.clave = ?
                 order by o.valor asc
                """, String.class, idPropiedad, clave);
    }

    private int filasDeAtributo(long idPropiedad, String clave) {
        return jdbc.queryForObject("""
                select count(*) from atributo_propiedad where id_propiedad = ? and clave = ?
                """, Integer.class, idPropiedad, clave);
    }

    private long eventos(Actor actor) {
        return jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id = ?",
                Long.class, actor.idOrganizacion());
    }

    // ------------------------------------------------------------------

    private long registrar(Actor actor, ValorAtributo... atributos) {
        return registrarCon(actor, Procedencia.deLaPantalla(), atributos);
    }

    private long registrarCon(Actor actor, Procedencia procedencia, ValorAtributo... atributos) {
        List<ValorAtributo> valores = new java.util.ArrayList<>();
        valores.add(new ValorAtributo("metraje_total", "120"));
        valores.add(new ValorAtributo("dormitorios", "3"));
        valores.addAll(List.of(atributos));
        return propiedades.registrar(new ComandoRegistro(null, procedencia, null, "DEPARTAMENTO",
                null, "Caso 4.P", ubicacion(), List.of(new Titular(titular(actor), null, Boolean.TRUE)),
                valores, List.of(), null), actor).idPropiedad();
    }

    private long registrarConAlquiler(Actor actor, List<ValorAtributo> condiciones) {
        return propiedades.registrar(new ComandoRegistro(null, Procedencia.deLaPantalla(), null,
                "DEPARTAMENTO", null, "Caso 4.P encargo", ubicacion(),
                List.of(new Titular(titular(actor), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "120"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null, condiciones)),
                null), actor).idPropiedad();
    }

    private void editar(Actor actor, long idPropiedad, Procedencia procedencia,
                        ValorAtributo... atributos) {
        editar(actor, idPropiedad, procedencia, List.of(atributos), null);
    }

    private void editar(Actor actor, long idPropiedad, Procedencia procedencia,
                        List<ValorAtributo> atributos, List<String> aBorrar) {
        propiedades.editar(idPropiedad, new ComandoEdicion(null, procedencia, null, null, null,
                atributos.isEmpty() ? null : atributos, null, aBorrar), actor);
    }

    /**
     * Una CASA, que es un tipo al que el catalogo NO le aplica {@code piso}.
     * Sus dos claves ALT son {@code metraje_total} y {@code dormitorios}.
     */
    private long registrarCasa(Actor actor, String marca, Ubicacion ubicacion,
                               List<ValorAtributo> extra) {
        List<ValorAtributo> valores = new java.util.ArrayList<>(
                List.of(new ValorAtributo("metraje_total", "180"),
                        new ValorAtributo("dormitorios", "4")));
        valores.addAll(extra);
        return propiedades.registrar(new ComandoRegistro(null, Procedencia.deLaPantalla(), null,
                "CASA", null, marca, ubicacion,
                List.of(new Titular(titular(actor), null, Boolean.TRUE)),
                valores, List.of(), null), actor).idPropiedad();
    }

    private long registrarConPiso(Actor actor, String piso) {
        return propiedades.registrar(new ComandoRegistro(null, Procedencia.deLaPantalla(), null,
                "DEPARTAMENTO", null, "Caso 4.P piso", ubicacionConPiso(piso),
                List.of(new Titular(titular(actor), null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "120"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(), null), actor).idPropiedad();
    }

    /** El mismo hueco del cable que usa el SPA: `piso` dentro de `ubicacion`. */
    private Ubicacion ubicacionConPiso(String piso) {
        return new Ubicacion("Av. Procedencia " + UUID.randomUUID().toString().substring(0, 8),
                "Miraflores", null, null, null, null, piso, null, null);
    }

    private String pisoDe(long idPropiedad) {
        return jdbc.queryForObject("select piso from propiedad where id_propiedad = ?",
                String.class, idPropiedad);
    }

    private Ubicacion ubicacion() {
        return new Ubicacion("Av. Procedencia " + UUID.randomUUID().toString().substring(0, 8),
                "Miraflores", null, null, null, null, null, null, null);
    }

    private long encargoDe(long idPropiedad) {
        return jdbc.queryForObject(
                "select min(id_captacion) from captacion where id_propiedad = ?",
                Long.class, idPropiedad);
    }

    private Long titular(Actor actor) {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 order by a.id_persona_rol asc
                 limit 1
                """).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), "AGENTE");
    }

    /** Otro agente de la MISMA organizacion: el segundo actor de la regla 6 bis. */
    private Actor otroActor(Actor primero) {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 where r.organizacion_id = ? and a.id_persona_rol <> ?
                 order by a.id_persona_rol asc
                 limit 1
                """, primero.idOrganizacion(), primero.idRolOperativo()).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), "AGENTE");
    }
}
