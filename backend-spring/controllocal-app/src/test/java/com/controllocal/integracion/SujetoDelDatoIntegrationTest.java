package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.CondicionesDeEncargo;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.captura.GuionRegistroPropiedad;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>El gate del sujeto</b> (Corte 0C, D-E4-3 §9).
 *
 * <h2>La pregunta anterior a la autoridad</h2>
 * D-E4-3 respondio <i>donde vive</i> cada dato. Faltaba lo que va antes:
 *
 * <blockquote>¿De quien es?</blockquote>
 *
 * <p>El catalogo presuponia una sola respuesta —{@code atributo -> Propiedad}—
 * y eso es insuficiente. {@code amoblado} lo demuestra: una vivienda puede tener
 * muebles y, con los mismos muebles, venderse sin ellos, alquilarse amoblada, y
 * tener dos encargos en momentos distintos con condiciones distintas. Con un
 * solo sujeto la tercera historia es irrepresentable: el dato se sobrescribe.
 *
 * <h2>La regla que vigila</h2>
 * <pre>
 *   clave  →  vocabulario  →  SUJETO  →  autoridad  →  mecanismo
 * </pre>
 *
 * <p>Cada clave declara <b>exactamente un</b> sujeto, y su aplicabilidad vive
 * donde ese sujeto manda:
 *
 * <pre>
 *   sujeto=PROPIEDAD  →  catalogo_atributo_tipo        →  atributo_propiedad
 *   sujeto=ENCARGO    →  catalogo_atributo_operacion   →  atributo_encargo
 *                        nunca en las dos
 * </pre>
 *
 * <h2>Y la identidad del valor es el ENCARGO, no la operacion</h2>
 * Esta es la parte que se rompe sola si nadie la vigila. Dos alquileres
 * sucesivos de la misma propiedad son <b>dos episodios</b>: comparten operacion
 * y no comparten nada mas. Colgar el valor de {@code (propiedad, operacion)}
 * haria que el alquiler de 2026 heredara la garantia pactada en 2024, y lo haria
 * en silencio -- que es como se pierde un dato sin borrarlo.
 *
 * <h2>Por que contra PostgreSQL</h2>
 * Casi todo lo que se afirma aqui lo garantiza un trigger o un indice unico, y
 * eso no lo lee javac, ni Hibernate, ni ArchUnit.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SujetoDelDatoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired PublicacionService publicaciones;
    @Autowired MotorDeCaptura captura;

    // ==================================================================
    // La estructura que sostiene la regla
    // ==================================================================

    @Test
    @DisplayName("el catalogo declara el sujeto de cada clave")
    void elCatalogoDeclaraElSujeto() {
        assertEquals(1, columnas("catalogo_atributo", "sujeto"),
                "sin `sujeto` no hay forma de decir de quien es un dato");
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from pg_constraint
                 where conname = 'ck_catalogo_sujeto'
                """, Integer.class),
                "el sujeto tiene que estar acotado a PROPIEDAD y ENCARGO");
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from catalogo_atributo where sujeto is null", Integer.class),
                "una clave sin sujeto no se puede enrutar");
    }

    @Test
    @DisplayName("la aplicabilidad del encargo es por tipo Y operacion, no solo por tipo")
    void laAplicabilidadDelEncargoEsPorTipoYOperacion() {
        assertTrue(existeTabla("catalogo_atributo_operacion"),
                "`partida_registral` bloquea una VENTA y es irrelevante en un ALQUILER; "
                        + "`garantia_meses` es al reves. Eso no cabe en una tabla por tipo.");
        assertEquals("id_catalogo_atributo, tipo_propiedad, tipo_operacion", clavePrimaria(
                "catalogo_atributo_operacion"),
                "la aplicabilidad no tiene identidad propia: es una fila DE un atributo");
    }

    /**
     * <b>El valor cuelga del ENCARGO, nunca de la operacion.</b>
     *
     * <p>Dos alquileres sucesivos comparten operacion y no comparten nada mas.
     * Una FK a {@code (propiedad, operacion)} fundiria dos episodios distintos y
     * el segundo heredaria las condiciones del primero.
     */
    @Test
    @DisplayName("el valor de un encargo cuelga de su id, no de su operacion")
    void elValorCuelgaDelEncargo() {
        assertTrue(existeTabla("atributo_encargo"),
                "las condiciones comerciales necesitan donde vivir");
        assertTrue(columnas("atributo_encargo", "id_captacion") == 1,
                "el valor tiene que colgar del encargo concreto");
        assertEquals(0, columnas("atributo_encargo", "motivo_operacion")
                        + columnas("atributo_encargo", "tipo_operacion")
                        + columnas("atributo_encargo", "operacion"),
                "si la operacion fuera parte de la identidad del valor, dos alquileres "
                        + "sucesivos de la misma propiedad compartirian sus condiciones");

        // Y la FK es compuesta, como exige el gate del discriminador de tenant.
        assertTrue(jdbc.queryForObject("""
                select count(*) from pg_constraint
                 where conrelid = 'atributo_encargo'::regclass and contype = 'f'
                   and array_length(conkey, 1) = 2
                """, Integer.class) >= 1,
                "sin FK compuesta, un valor podria apuntar al encargo de otra corredora");
    }

    // ==================================================================
    // La invariante de enrutamiento, en las dos direcciones
    // ==================================================================

    /**
     * Las dos direcciones incorrectas, y ninguna es simetrica de la otra: una
     * clave fisica con aplicabilidad por operacion dice que la cosa cambia
     * segun se venda o se alquile; una clave comercial con aplicabilidad por
     * tipo dice que la condicion es un hecho del inmueble.
     */
    @Test
    @DisplayName("invariante: cada clave declara su aplicabilidad donde manda su sujeto")
    void cadaClaveDeclaraSuAplicabilidadDondeMandaSuSujeto() {
        List<Map<String, Object>> desviadas = jdbc.queryForList("""
                select c.clave, c.sujeto,
                       (select count(*) from catalogo_atributo_tipo t
                         where t.id_catalogo_atributo = c.id_catalogo_atributo) as por_tipo,
                       (select count(*) from catalogo_atributo_operacion o
                         where o.id_catalogo_atributo = c.id_catalogo_atributo) as por_operacion,
                       c.aplica_todos
                  from catalogo_atributo c
                 where c.activo
                """);

        List<String> rotas = new ArrayList<>();
        for (Map<String, Object> fila : desviadas) {
            String clave = (String) fila.get("clave");
            String sujeto = (String) fila.get("sujeto");
            long porTipo = ((Number) fila.get("por_tipo")).longValue();
            long porOperacion = ((Number) fila.get("por_operacion")).longValue();
            boolean aplicaTodos = (Boolean) fila.get("aplica_todos");

            if ("PROPIEDAD".equals(sujeto)) {
                if (porOperacion > 0) {
                    rotas.add(clave + ": es de la PROPIEDAD y declara aplicabilidad por operacion "
                            + "-- la cosa fisica no cambia segun se venda o se alquile");
                }
                if (porTipo == 0 && !aplicaTodos) {
                    rotas.add(clave + ": es de la PROPIEDAD y no dice a que tipos aplica");
                }
            } else {
                if (porTipo > 0) {
                    rotas.add(clave + ": es del ENCARGO y declara aplicabilidad por tipo -- una "
                            + "condicion negociada no es un hecho del inmueble");
                }
                if (porOperacion == 0 && !aplicaTodos) {
                    rotas.add(clave + ": es del ENCARGO y no dice a que (tipo, operacion) aplica");
                }
            }
        }
        if (!rotas.isEmpty()) {
            fail("""
                    Hay claves cuya aplicabilidad no vive donde manda su sujeto.

                      clave -> sujeto -> autoridad -> mecanismo
                      y el sujeto se declara UNA vez.

                      %s
                    """.formatted(String.join("\n      ", rotas)));
        }
    }

    @Test
    @DisplayName("invariante: ningun valor de encargo se guardo como si fuera de la propiedad")
    void ningunValorDeEncargoSeGuardoComoPropiedad() {
        List<String> intrusos = jdbc.queryForList("""
                select distinct a.clave from atributo_propiedad a
                  join catalogo_atributo c on c.clave = a.clave
                                          and (c.organizacion_id is null
                                               or c.organizacion_id = a.organizacion_id)
                 where c.sujeto = 'ENCARGO'
                """, String.class);
        assertTrue(intrusos.isEmpty(),
                "estas claves son del ENCARGO y tienen valores escritos como de la propiedad: "
                        + intrusos + ". Un dato en el sujeto equivocado no falla: miente.");
    }

    @Test
    @DisplayName("invariante: ningun valor de propiedad se guardo como si fuera de un encargo")
    void ningunValorDePropiedadSeGuardoComoEncargo() {
        List<String> intrusos = jdbc.queryForList("""
                select distinct a.clave from atributo_encargo a
                  join catalogo_atributo c on c.clave = a.clave
                                          and (c.organizacion_id is null
                                               or c.organizacion_id = a.organizacion_id)
                 where c.sujeto = 'PROPIEDAD'
                """, String.class);
        assertTrue(intrusos.isEmpty(),
                "estas claves son de la PROPIEDAD y tienen valores colgados de un encargo: "
                        + intrusos);
    }

    // ==================================================================
    // La prueba decisiva: una propiedad, dos encargos
    // ==================================================================

    /**
     * <b>El caso que justifica el corte entero.</b>
     *
     * <p>Una propiedad con la venta y el alquiler abiertos a la vez. La misma
     * clave, dos valores. Antes de V73 no habia donde ponerlos: el segundo
     * pisaba al primero y {@code uq_atributo_propiedad_clave} lo garantizaba.
     */
    @Test
    @DisplayName("dos encargos de la misma propiedad no comparten condiciones")
    void dosEncargosNoComparteCondiciones() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");

            pactar(id, venta, new ValorAtributo(clave, "3"));
            pactar(id, alquiler, new ValorAtributo(clave, "12"));

            assertEquals("3", pactado(id, venta, clave));
            assertEquals("12", pactado(id, alquiler, clave));
            assertNull(atributoDeLaPropiedad(id, clave),
                    "una condicion negociada no puede acabar guardada en el inmueble");
        } finally {
            retirar(clave);
        }
    }

    /**
     * La regla de bloques de 0A, un nivel mas adentro: guardar un bloque que el
     * usuario no esta editando jamas puede modificar, vaciar ni completar por
     * defecto datos de otro bloque.
     */
    @Test
    @DisplayName("editar un encargo deja el otro exactamente como estaba")
    void editarUnEncargoNoTocaElOtro() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");
            pactar(id, venta, new ValorAtributo(clave, "3"));
            pactar(id, alquiler, new ValorAtributo(clave, "12"));

            Map<String, String> antes = retratoDe(id, alquiler);
            pactar(id, venta, new ValorAtributo(clave, "9"));

            assertEquals("9", pactado(id, venta, clave));
            assertEquals(antes, retratoDe(id, alquiler),
                    "el bloque del alquiler no viajaba en la peticion: no se toca");
        } finally {
            retirar(clave);
        }
    }

    @Test
    @DisplayName("borrar en un encargo no borra en el otro")
    void borrarEnUnEncargoNoBorraEnElOtro() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");
            pactar(id, venta, new ValorAtributo(clave, "3"));
            pactar(id, alquiler, new ValorAtributo(clave, "12"));

            propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null, null,
                    null, List.of(new CondicionesDeEncargo(venta, null, List.of(clave)))), actor());

            assertNull(pactado(id, venta, clave), "se pidio retirarla de la venta");
            assertEquals("12", pactado(id, alquiler, clave),
                    "y no se pidio nada del alquiler");
        } finally {
            retirar(clave);
        }
    }

    @Test
    @DisplayName("editar la propiedad no toca ninguna condicion de ningun encargo")
    void editarLaPropiedadNoTocaLosEncargos() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");
            pactar(id, venta, new ValorAtributo(clave, "3"));
            pactar(id, alquiler, new ValorAtributo(clave, "12"));

            Map<String, String> ventaAntes = retratoDe(id, venta);
            Map<String, String> alquilerAntes = retratoDe(id, alquiler);

            propiedades.editar(id, new ComandoEdicion(null, null, "Otra descripcion", null, null,
                    List.of(new ValorAtributo("metraje_total", "95")), null, null), actor());

            assertEquals(ventaAntes, retratoDe(id, venta));
            assertEquals(alquilerAntes, retratoDe(id, alquiler));
        } finally {
            retirar(clave);
        }
    }

    /**
     * <b>Un encargo cerrado conserva lo suyo y no se lo pasa a nadie.</b>
     *
     * <p>Es el caso que un modelo por operacion no puede representar: al cerrar
     * la venta, su garantia no puede aparecer en el alquiler ni desaparecer del
     * historico. El encargo cerrado es el UNICO sitio donde vive lo que se
     * pacto entonces.
     */
    @Test
    @DisplayName("cerrar un encargo no migra sus condiciones a ningun otro")
    void cerrarUnEncargoNoMigraSusCondiciones() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");
            pactar(id, venta, new ValorAtributo(clave, "3"));

            cerrar(venta);

            assertEquals("3", pactado(id, venta, clave),
                    "cerrar no borra lo pactado: es justo donde vive el historico");
            assertNull(pactado(id, alquiler, clave),
                    "y no se lo pasa al alquiler por compartir propiedad");
        } finally {
            retirar(clave);
        }
    }

    /**
     * <b>Dos alquileres sucesivos son dos episodios.</b>
     *
     * <p>Comparten operacion y no comparten nada mas. Si la identidad del valor
     * fuera {@code (propiedad, operacion)} —que es la simplificacion que parece
     * razonable— el alquiler nuevo naceria con la garantia pactada en el
     * anterior, sin que nadie la escribiera y sin que nada fallara.
     */
    @Test
    @DisplayName("un segundo alquiler no hereda lo pactado en el primero")
    void unSegundoAlquilerNoHeredaLoDelPrimero() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long primero = encargo(id, "A");
            pactar(id, primero, new ValorAtributo(clave, "12"));
            cerrar(primero);

            long segundo = abrirOtroEncargo(primero);
            assertTrue(segundo != primero, "el alquiler nuevo tiene que ser otro episodio");
            assertEquals("12", pactado(id, primero, clave),
                    "el cerrado conserva lo que se pacto entonces");
            assertNull(pactado(id, segundo, clave),
                    "y el nuevo nace SIN condiciones: nadie las ha pactado todavia");
        } finally {
            retirar(clave);
        }
    }

    @Test
    @DisplayName("la ficha muestra cada condicion bajo su idEncargo")
    void laFichaMuestraCadaValorBajoSuEncargo() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            long venta = encargo(id, "V");
            long alquiler = encargo(id, "A");
            pactar(id, venta, new ValorAtributo(clave, "3"));
            pactar(id, alquiler, new ValorAtributo(clave, "12"));

            var ficha = propiedades.consultar(id, actor());
            assertTrue(ficha.atributos().stream().noneMatch(a -> clave.equals(a.clave())),
                    "una condicion comercial no es un atributo del inmueble");

            Map<Long, String> porEncargo = new LinkedHashMap<>();
            for (EncargoFicha bloque : ficha.encargos()) {
                bloque.condiciones().stream()
                        .filter(c -> clave.equals(c.clave()))
                        .forEach(c -> porEncargo.put(bloque.idEncargo(), c.valor()));
            }
            assertEquals(Map.of(venta, "3", alquiler, "12"), porEncargo,
                    "cada valor tiene que llegar dentro del bloque del encargo que lo pacto");
        } finally {
            retirar(clave);
        }
    }

    // ==================================================================
    // El enrutamiento se niega en las dos direcciones, con su mensaje
    // ==================================================================

    @Test
    @DisplayName("una clave de la propiedad no se puede pactar como condicion")
    void unaClaveDeLaPropiedadNoSePactaEnUnEncargo() {
        long id = registrarConVentaYAlquiler();
        long venta = encargo(id, "V");
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> pactar(id, venta, new ValorAtributo("metraje_total", "80")));
        assertTrue(error.getMessage().contains("PROPIEDAD"),
                "el mensaje tiene que decir de quien es el dato: " + error.getMessage());
    }

    @Test
    @DisplayName("una condicion del encargo no se puede escribir como atributo del inmueble")
    void unaCondicionNoSeEscribeEnLaPropiedad() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                            List.of(new ValorAtributo(clave, "3")), null, null), actor()));
            assertTrue(error.getMessage().contains("ENCARGO"),
                    "el mensaje tiene que decir de quien es el dato: " + error.getMessage());
        } finally {
            retirar(clave);
        }
    }

    /**
     * La aplicabilidad depende de <b>las dos</b> coordenadas. Una condicion
     * declarada solo para el alquiler no puede pactarse en una venta, y decirlo
     * con el nombre de la operacion es la diferencia entre un error util y un
     * "no aplica" que no explica nada.
     */
    @Test
    @DisplayName("una condicion que solo aplica al alquiler se rechaza en la venta")
    void laAplicabilidadMiraLaOperacion() {
        String clave = sembrarCondicion("zz_solo_alquiler", "ENTERO", List.of("A"));
        try {
            long id = registrarConVentaYAlquiler();
            long alquiler = encargo(id, "A");
            long venta = encargo(id, "V");

            pactar(id, alquiler, new ValorAtributo(clave, "2"));
            assertEquals("2", pactado(id, alquiler, clave));

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> pactar(id, venta, new ValorAtributo(clave, "2")));
            assertTrue(error.getMessage().contains("VENTA"),
                    "el error tiene que nombrar la operacion: " + error.getMessage());
        } finally {
            retirar(clave);
        }
    }

    @Test
    @DisplayName("un bloque de condiciones sin idEncargo se rechaza")
    void unBloqueSinEncargoSeRechaza() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        try {
            long id = registrarConVentaYAlquiler();
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                            null, null, null,
                            List.of(new CondicionesDeEncargo(null,
                                    List.of(new ValorAtributo(clave, "3")), null))), actor()));
            assertTrue(error.getMessage().contains("encargo"),
                    "con dos encargos abiertos, adivinar seria escribir en el equivocado");
        } finally {
            retirar(clave);
        }
    }

    // ==================================================================
    // La composicion: publicar pregunta a los DOS sujetos
    // ==================================================================

    /**
     * <b>Publicar necesita dos respuestas y las dice por separado.</b>
     *
     * <p>El mismo inmueble puede estar listo para alquilarse y no para venderse.
     * Un mensaje que fundiera las dos listas obligaria al corredor a adivinar en
     * que pantalla lo arregla.
     */
    @Test
    @DisplayName("una condicion PUB del encargo impide publicar ESE encargo, y lo dice")
    void laExigenciaDelEncargoImpidePublicar() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A", "V"));
        marcarExigencia(clave, "PUB");
        try {
            long id = registrarConVentaYAlquiler();
            long alquiler = encargo(id, "A");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> publicaciones.crearEnEncargo(alquiler, publicacionDePrueba(), actor()));
            assertTrue(error.getMessage().contains("condiciones de este encargo"),
                    "el mensaje tiene que separar la ficha del encargo: " + error.getMessage());
            assertTrue(error.getMessage().contains("Prueba zz_condicion"),
                    "y nombrar el dato que falta: " + error.getMessage());

            pactar(id, alquiler, new ValorAtributo(clave, "12"));
            assertNotNull(publicaciones.crearEnEncargo(alquiler, publicacionDePrueba(), actor()),
                    "una vez pactada, se publica");
        } finally {
            retirar(clave);
        }
    }

    @Test
    @DisplayName("lo que le falta al encargo viaja dentro de su bloque, no en la ficha")
    void losFaltantesDelEncargoVanEnSuBloque() {
        String clave = sembrarCondicion("zz_condicion", "ENTERO", List.of("A"));
        marcarExigencia(clave, "PUB");
        try {
            long id = registrarConVentaYAlquiler();
            long alquiler = encargo(id, "A");
            long venta = encargo(id, "V");

            var ficha = propiedades.consultar(id, actor());
            assertTrue(ficha.atributosQueFaltan().stream().noneMatch(f -> clave.equals(f.clave())),
                    "no le falta al inmueble: le falta a un encargo concreto");
            assertTrue(bloque(ficha, alquiler).faltanParaPublicar().stream()
                            .anyMatch(f -> clave.equals(f.clave())),
                    "al alquiler si le falta");
            assertTrue(bloque(ficha, venta).faltanParaPublicar().stream()
                            .noneMatch(f -> clave.equals(f.clave())),
                    "y a la venta no, porque alli esa condicion ni siquiera aplica");
        } finally {
            retirar(clave);
        }
    }


    // ==================================================================
    // El motor de captura: se pregunta por bloques y se responde por bloques
    // ==================================================================

    /**
     * <b>La condicion se pregunta DENTRO del bloque de su operacion.</b>
     *
     * <p>Y calificada. Con la venta y el alquiler declarados a la vez, una
     * clave desnuda no diria a cual de los dos encargos pertenece la respuesta,
     * y el Core no puede adivinarlo: adivinarlo seria escribir en el
     * equivocado.
     */
    @Test
    @DisplayName("la definicion pregunta la condicion dentro del bloque de su operacion")
    void laDefinicionPreguntaLaCondicionEnSuBloque() {
        var definicion = captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "DEPARTAMENTO",
                "VENTA,ALQUILER", actor());

        List<String> alquiler = clavesDelBloque(definicion, "ALQUILER");
        List<String> venta = clavesDelBloque(definicion, "VENTA");

        assertTrue(alquiler.contains("garantia_meses:ALQUILER"),
                "la garantia es una condicion del alquiler: " + alquiler);
        assertTrue(venta.stream().noneMatch(c -> c.startsWith("garantia_meses")),
                "y en una venta no significa nada: " + venta);
        assertTrue(java.util.stream.Stream
                        .concat(definicion.comunes().stream(), definicion.delTipo().stream())
                        .noneMatch(p -> p.clave().startsWith("garantia_meses")),
                "y nunca entre lo fisico, que es el saco comun que este corte prohibe");
    }

    /**
     * <b>Lo dictado en un bloque aterriza en el encargo de ese bloque.</b>
     *
     * <p>Cierra el circuito entero del corte por el camino conversacional:
     * KAIROS pregunta lo que el catalogo declara, la persona contesta, y el
     * valor acaba colgado del encargo correcto -- no del inmueble, no del otro
     * encargo.
     */
    @Test
    @DisplayName("lo dictado en el bloque del alquiler se guarda en el encargo de alquiler")
    void loDictadoEnUnBloqueVaASuEncargo() {
        long propietario = unPropietario();
        var vacio = captura.avanzar(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, Map.of(),
                null, actor());
        captura.avanzar(null, vacio.idBorrador(), Map.of(
                GuionRegistroPropiedad.TIPO_PROPIEDAD, "DEPARTAMENTO",
                GuionRegistroPropiedad.OPERACIONES, "VENTA,ALQUILER",
                GuionRegistroPropiedad.IMPORTE + ":VENTA", "350000",
                GuionRegistroPropiedad.MONEDA + ":VENTA", "USD",
                GuionRegistroPropiedad.IMPORTE + ":ALQUILER", "2500",
                GuionRegistroPropiedad.MONEDA + ":ALQUILER", "PEN",
                GuionRegistroPropiedad.TITULARES, String.valueOf(propietario),
                GuionRegistroPropiedad.DIRECCION,
                "Av. Dictada " + java.util.UUID.randomUUID().toString().substring(0, 8),
                "metraje_total", "118",
                "dormitorios", "3"), null, actor());
        // La condicion, en el bloque del alquiler y solo ahi.
        captura.avanzar(null, vacio.idBorrador(), Map.of(
                GuionRegistroPropiedad.DISTRITO, "Miraflores",
                "garantia_meses:ALQUILER", "2"), null, actor());

        var ejecucion = captura.ejecutar(vacio.idBorrador(),
                java.util.UUID.randomUUID().toString(), null, actor());
        long id = ejecucion.idPropiedad();

        assertEquals("2", pactado(id, encargo(id, "A"), "garantia_meses"),
                "lo dictado para el alquiler tiene que estar en el encargo de alquiler");
        assertNull(pactado(id, encargo(id, "V"), "garantia_meses"),
                "y no en el de venta, donde ni siquiera aplica");
        assertNull(atributoDeLaPropiedad(id, "garantia_meses"),
                "ni como hecho del inmueble: es un pacto, no una caracteristica");
    }

    private static List<String> clavesDelBloque(MotorDeCaptura.DefinicionCaptura definicion,
                                                String operacion) {
        return definicion.deLaOperacion().stream()
                .filter(b -> operacion.equals(b.operacion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay bloque de " + operacion))
                .preguntas().stream()
                .map(MotorDeCaptura.Pregunta::clave)
                .toList();
    }

    // ==================================================================
    // V77 - El lenguaje completo del ENCARGO
    // ==================================================================

    /**
     * <b>VENTA deja de estar muda.</b>
     *
     * <p>Hasta V77 el catalogo tenia seis condiciones y todas eran de alquiler
     * menos una: un encargo de venta no podia decir si se entrega desocupado ni
     * si el propietario acepta credito hipotecario, que son dos de las tres
     * preguntas que deciden si la operacion avanza. El mecanismo estaba entero
     * desde 0C; lo que faltaba era el vocabulario.
     */
    @Test
    @DisplayName("V77: la VENTA tiene vocabulario propio en el catalogo")
    void laVentaTieneVocabulario() {
        Integer condiciones = jdbc.queryForObject("""
                select count(distinct c.id_catalogo_atributo)
                  from catalogo_atributo c
                  join catalogo_atributo_operacion o on o.id_catalogo_atributo = c.id_catalogo_atributo
                 where c.sujeto = 'ENCARGO' and c.del_sistema and c.activo
                   and o.tipo_operacion = 'V'
                """, Integer.class);
        assertTrue(condiciones != null && condiciones >= 6,
                "la venta sigue sin condiciones que declarar: " + condiciones);

        // Y las cuatro LISTA tienen vocabulario: una lista sin opciones se
        // degrada a TEXTO y deja de ser una lista sin que nadie avise.
        String sinOpciones = jdbc.queryForObject("""
                select string_agg(c.clave, ', ')
                  from catalogo_atributo c
                 where c.sujeto = 'ENCARGO' and c.activo
                   and c.tipo_dato in ('LISTA', 'LISTA_MULTIPLE')
                   and not exists (select 1 from catalogo_atributo_opcion o
                                    where o.id_catalogo_atributo = c.id_catalogo_atributo)
                """, String.class);
        assertNull(sinOpciones, "listas del encargo sin vocabulario: " + sinOpciones);
    }

    /**
     * <b>Los pares semanticos, TODOS.</b>
     *
     * <p>El guard de V74 nombraba uno solo -- {@code amoblado} -- y eso lo
     * dejaba como una excepcion artesanal. El par es el patron: un hecho del
     * inmueble y la condicion que se pacta sobre el son dos claves y viven en
     * dos sujetos. Que esten relacionadas <b>no permite sustituir una por
     * otra</b>: que el inmueble tenga muebles no dice si este alquiler los
     * incluye.
     *
     * <p>Se comprueba sobre el catalogo real, no sobre claves de prueba: lo que
     * puede romperse es la siembra.
     */
    @Test
    @DisplayName("V77: ningun par hecho/condicion comparte sujeto")
    void losParesSemanticosVivenEnSujetosDistintos() {
        List<String[]> pares = List.of(
                new String[] {"amoblado", "se_ofrece_amoblado"},
                new String[] {"cuota_mantenimiento", "mantenimiento_a_cargo_de"},
                new String[] {"estacionamientos", "estacionamientos_incluidos"},
                new String[] {"rubro_permitido", "rubros_excluidos_por_titular"},
                // `uso` vive como COLUMNA de propiedad, no como clave del
                // catalogo, asi que este par no puede colisionar por
                // construccion. Se comprueba igual: el dia que alguien lo
                // gobierne, la comprobacion ya esta puesta y dira si lo mando
                // al sujeto equivocado.
                new String[] {"uso", "uso_admitido_por_titular"},
                new String[] {"mascotas_reglamento", "mascotas_aceptadas"},
                new String[] {"nivel_implementacion", "se_entrega_implementado"},
                new String[] {"estado_ocupacion", "entrega_desocupado"},
                new String[] {"lote_minimo_normativo", "acepta_venta_fraccionada"});

        List<String> juntos = new ArrayList<>();
        for (String[] par : pares) {
            String sujetoHecho = sujetoDe(par[0]);
            String sujetoCondicion = sujetoDe(par[1]);
            // El lado PROPIEDAD de varios pares todavia no existe -- llega en
            // cortes posteriores --, y eso no invalida la comprobacion: lo que
            // se afirma es que si existen los dos, NO comparten sujeto.
            if (sujetoHecho != null && sujetoHecho.equals(sujetoCondicion)) {
                juntos.add(par[0] + " y " + par[1] + " -> " + sujetoHecho);
            }
            if (sujetoCondicion != null) {
                assertEquals("ENCARGO", sujetoCondicion,
                        par[1] + " es una condicion negociada y tiene que ser del ENCARGO");
            }
            if (sujetoHecho != null) {
                assertEquals("PROPIEDAD", sujetoHecho,
                        par[0] + " es un hecho del inmueble y tiene que ser de la PROPIEDAD");
            }
        }
        assertEquals(List.of(), juntos,
                "un hecho y su condicion acabaron en el mismo sujeto: el pacto vuelve a pisar al hecho");
    }

    /**
     * <b>El enrutamiento del catalogo, en las DOS direcciones.</b>
     *
     * <p>La guarda de la migracion comprobaba una sola: que ninguna clave del
     * ENCARGO declarara su aplicabilidad en la tabla de la PROPIEDAD. La
     * contraria faltaba, y es igual de rompible -- una clave fisica con una
     * fila en {@code catalogo_atributo_operacion} pasaria a preguntarse dentro
     * del bloque de un encargo, que es el mismo desorden visto del otro lado.
     *
     * <p>Cada sujeto declara su aplicabilidad en SU tabla, y en ninguna otra.
     */
    @Test
    @DisplayName("V77: cada sujeto declara su aplicabilidad en su propia tabla, y solo en ella")
    void elEnrutamientoNoSeCruzaEnNingunaDireccion() {
        String encargoConTipo = jdbc.queryForObject("""
                select string_agg(c.clave, ', ')
                  from catalogo_atributo c
                 where c.sujeto = 'ENCARGO' and c.activo and c.del_sistema
                   and exists (select 1 from catalogo_atributo_tipo t
                                where t.id_catalogo_atributo = c.id_catalogo_atributo)
                """, String.class);
        assertNull(encargoConTipo,
                "condiciones del encargo con aplicabilidad por tipo: " + encargoConTipo);

        String propiedadConOperacion = jdbc.queryForObject("""
                select string_agg(c.clave, ', ')
                  from catalogo_atributo c
                 where c.sujeto = 'PROPIEDAD' and c.activo and c.del_sistema
                   and exists (select 1 from catalogo_atributo_operacion o
                                where o.id_catalogo_atributo = c.id_catalogo_atributo)
                """, String.class);
        assertNull(propiedadConOperacion,
                "hechos de la propiedad con aplicabilidad por operacion: " + propiedadConOperacion);
    }

    /**
     * <b>Que dos claves esten emparejadas no permite sustituir una por otra.</b>
     *
     * <p>Es la otra mitad del par, y la que de verdad protege el dato: el
     * enrutamiento tiene que rechazar el cruce en las dos direcciones, no solo
     * clasificar bien en el catalogo.
     */
    @Test
    @DisplayName("V77: el hecho no se puede pactar y la condicion no se puede escribir en la propiedad")
    void elParNoSePuedeCruzar() {
        long id = registrarConVentaYAlquiler();
        long alquiler = encargo(id, "A");

        // El hecho, enviado como condicion de un encargo.
        ReglaNegocioException hechoComoPacto = assertThrows(ReglaNegocioException.class,
                () -> pactar(id, alquiler, new ValorAtributo("amoblado", "true")));
        assertTrue(hechoComoPacto.getMessage().toLowerCase().contains("amoblado"),
                hechoComoPacto.getMessage());

        // Y la condicion, enviada como atributo de la propiedad.
        ReglaNegocioException pactoComoHecho = assertThrows(ReglaNegocioException.class,
                () -> propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                        List.of(new ValorAtributo("se_ofrece_amoblado", "true")),
                        null, null), actor()));
        assertTrue(pactoComoHecho.getMessage().toLowerCase().contains("se_ofrece_amoblado"),
                pactoComoHecho.getMessage());

        // Y despues del doble rechazo, ninguno de los dos existe: un rechazo
        // que dejara escrito la mitad seria peor que aceptar.
        assertNull(atributoDeLaPropiedad(id, "amoblado"));
        assertNull(pactado(id, alquiler, "se_ofrece_amoblado"));
    }

    /**
     * <b>La condicion negociada muere con su encargo, pero su historia no.</b>
     *
     * <p>Es la prueba que da sentido al sujeto entero. La misma propiedad, dos
     * alquileres sucesivos, condiciones opuestas:
     *
     * <pre>
     *   Encargo 2026:  se_ofrece_amoblado = true    mascotas = false
     *   Encargo 2027:  se_ofrece_amoblado = false   mascotas = true
     * </pre>
     *
     * Las dos versiones sobreviven, ninguna pisa a la otra, la propiedad sigue
     * siendo una sola y el hecho fisico no se movio. Con un solo sujeto esto era
     * irrepresentable: el segundo valor sobrescribia al primero y nadie se
     * enteraba.
     */
    @Test
    @DisplayName("V77: dos encargos de la misma propiedad conservan condiciones distintas")
    void dosEpisodiosConservanSusPropiasCondiciones() {
        long id = registrarConVentaYAlquiler();
        long primero = encargo(id, "A");

        // El hecho fisico, aparte y una sola vez: el inmueble TIENE muebles.
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(new ValorAtributo("amoblado", "true")), null, null), actor());

        pactar(id, primero, new ValorAtributo("se_ofrece_amoblado", "true"));
        pactar(id, primero, new ValorAtributo("mascotas_aceptadas", "false"));
        Map<String, String> retratoPrimero = retratoDe(id, primero);

        // Pasa el tiempo: el alquiler termina y se abre otro sobre el MISMO
        // inmueble, con lo contrario pactado.
        cerrar(primero);
        long segundo = abrirOtroEncargo(primero);
        assertEquals(Map.of(), retratoDe(id, segundo),
                "un episodio nuevo nace SIN condiciones: no hereda lo que se pacto en el anterior");

        pactar(id, segundo, new ValorAtributo("se_ofrece_amoblado", "false"));
        pactar(id, segundo, new ValorAtributo("mascotas_aceptadas", "true"));

        // Las dos versiones, vivas a la vez y contrarias.
        assertEquals("true", pactado(id, primero, "se_ofrece_amoblado"));
        assertEquals("false", pactado(id, segundo, "se_ofrece_amoblado"));
        assertEquals("false", pactado(id, primero, "mascotas_aceptadas"));
        assertEquals("true", pactado(id, segundo, "mascotas_aceptadas"));

        // El primero no se movio ni un campo al escribir el segundo.
        assertEquals(retratoPrimero, retratoDe(id, primero),
                "pactar en el encargo nuevo reescribio el historico del anterior");

        // Y el hecho del inmueble sobrevivio a los dos: los muebles siguen ahi
        // aunque el segundo alquiler no los ofrezca.
        assertEquals("true", atributoDeLaPropiedad(id, "amoblado"),
                "el hecho fisico se movio al pactar sobre el");

        // La propiedad sigue siendo UNA, con sus dos episodios.
        assertEquals(1, (int) jdbc.queryForObject(
                "select count(*) from propiedad where id_propiedad = ?", Integer.class, id));
        assertEquals(2, (int) jdbc.queryForObject("""
                select count(*) from captacion
                 where id_propiedad = ? and motivo_operacion = 'A'
                """, Integer.class, id),
                "los dos episodios de alquiler tienen que seguir existiendo");
    }

    /**
     * <b>La ausencia no es un «no».</b>
     *
     * <p>Una condicion que nadie declaro <b>no esta</b>: no viaja como
     * {@code false}, no se rellena y no aparece en el bloque. Es lo que
     * permitira a KAIROS preguntar solo lo que falta en vez de heredar un
     * supuesto que nadie dijo -- y lo que impide que «no sabemos si acepta
     * mascotas» se lea como «no acepta mascotas».
     */
    @Test
    @DisplayName("V77: una condicion sin declarar no existe; no viaja como falso")
    void loQueNadieDeclaroNoEsUnNo() {
        long id = registrarConVentaYAlquiler();
        long alquiler = encargo(id, "A");

        assertEquals(Map.of(), retratoDe(id, alquiler),
                "un encargo recien abierto no tiene ninguna condicion supuesta");
        assertNull(pactado(id, alquiler, "mascotas_aceptadas"),
                "lo que nadie declaro no puede leerse como una respuesta");

        // Declarar una NO inventa las demas.
        pactar(id, alquiler, new ValorAtributo("mascotas_aceptadas", "false"));
        assertEquals("false", pactado(id, alquiler, "mascotas_aceptadas"));
        assertNull(pactado(id, alquiler, "se_ofrece_amoblado"),
                "declarar una condicion no puede rellenar las vecinas");
        assertEquals(1, retratoDe(id, alquiler).size(),
                "el bloque solo tiene lo que alguien dijo");

        // Y ninguna columna de valor lleva DEFAULT: el defecto no puede
        // colarse desde la base tampoco.
        String conDefecto = jdbc.queryForObject("""
                select string_agg(column_name, ', ')
                  from information_schema.columns
                 where table_name = 'atributo_encargo' and column_name like 'valor%'
                   and column_default is not null
                """, String.class);
        assertNull(conDefecto, "columnas de valor con DEFAULT: " + conDefecto);
    }

    /**
     * <b>Un IMPORTE y un multivalor tambien se pactan</b> (V77).
     *
     * <p>Son las dos condiciones que obligaron a ensanchar el cable: un importe
     * lleva cifra <b>y</b> moneda, y un multivalor lleva varios elementos. El
     * DTO web llevaba un solo texto por clave, asi que
     * {@code precio_estacionamiento_adicional} y {@code equipamiento_incluido}
     * habrian quedado sembradas y mudas.
     */
    @Test
    @DisplayName("V77: una condicion IMPORTE y una multivalor se escriben y se releen")
    void elImporteYElMultivalorSePactan() {
        long id = registrarConVentaYAlquiler();
        long alquiler = encargo(id, "A");

        pactar(id, alquiler,
                ValorAtributo.importe("precio_estacionamiento_adicional", "250", "PEN"));
        pactar(id, alquiler, ValorAtributo.multiple("equipamiento_incluido",
                List.of("COCINA", "LAVADORA")));

        EncargoFicha bloque = bloque(propiedades.consultar(id, actor()), alquiler);
        PropiedadUniversalService.AtributoFicha precio = bloque.condiciones().stream()
                .filter(c -> "precio_estacionamiento_adicional".equals(c.clave()))
                .findFirst().orElseThrow();
        // El texto compuesto para leer, y los huecos crudos para poder corregir.
        assertEquals("PEN", precio.moneda(), "la moneda tiene que viajar cruda");
        assertTrue(precio.valor().contains("250"), precio.valor());

        PropiedadUniversalService.AtributoFicha equipamiento = bloque.condiciones().stream()
                .filter(c -> "equipamiento_incluido".equals(c.clave()))
                .findFirst().orElseThrow();
        assertEquals(List.of("COCINA", "LAVADORA"), equipamiento.valores(),
                "los elementos tienen que viajar crudos, no pegados por comas");
    }

    /** El sujeto que el catalogo declara para una clave, o null si no existe. */
    private String sujetoDe(String clave) {
        List<String> sujetos = jdbc.queryForList("""
                select sujeto from catalogo_atributo where clave = ? and activo
                """, String.class, clave);
        return sujetos.isEmpty() ? null : sujetos.get(0);
    }

    private long unPropietario() {
        return jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor().idOrganizacion());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * Una clave del ENCARGO, del tenant, aplicable a los siete tipos en las
     * operaciones que se le digan.
     *
     * <p>Se siembra por caso y se retira en el {@code finally} porque estas
     * claves son globales al tenant: una marcada PUB y olvidada bloquearia la
     * publicacion de todos los casos siguientes, y el fallo aparece en el caso
     * equivocado. Lo aprendio el Corte 0B a base de perseguirlo.
     */
    private String sembrarCondicion(String base, String tipoDato, List<String> operaciones) {
        String clave = base + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        long org = actor().idOrganizacion();
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden, sujeto)
                values (?, ?, ?, ?, false, false, 900, 'ENCARGO')
                """, org, clave, "Prueba " + base, tipoDato);
        for (String operacion : operaciones) {
            jdbc.update("""
                    insert into catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                                             tipo_operacion, exigencia)
                    select c.id_catalogo_atributo, t.tipo, ?, 'OPC'
                      from catalogo_atributo c
                      cross join (values ('L'),('O'),('D'),('C'),('T'),('A'),('X')) as t(tipo)
                     where c.clave = ? and c.organizacion_id = ?
                    """, operacion, clave, org);
        }
        return clave;
    }

    private void marcarExigencia(String clave, String exigencia) {
        jdbc.update("""
                update catalogo_atributo_operacion set exigencia = ?
                 where id_catalogo_atributo = (select id_catalogo_atributo from catalogo_atributo
                                                where clave = ? and organizacion_id = ?)
                """, exigencia, clave, actor().idOrganizacion());
    }

    private void retirar(String clave) {
        jdbc.update("update catalogo_atributo set activo = false where clave = ?", clave);
    }

    /** Escribe una condicion en UN encargo, por la puerta del caso de uso. */
    private void pactar(long idPropiedad, long idEncargo, ValorAtributo valor) {
        propiedades.editar(idPropiedad, new ComandoEdicion(null, null, null, null, null, null,
                null, null,
                List.of(new CondicionesDeEncargo(idEncargo, List.of(valor), null))), actor());
    }

    /** Lo que la FICHA dice que se pacto en ese encargo. Lectura por el API. */
    private String pactado(long idPropiedad, long idEncargo, String clave) {
        return bloque(propiedades.consultar(idPropiedad, actor()), idEncargo).condiciones().stream()
                .filter(c -> clave.equals(c.clave()))
                .map(PropiedadUniversalService.AtributoFicha::valor)
                .findFirst().orElse(null);
    }

    /**
     * Todas las condiciones de un encargo, planas. Es lo que permite decir «el
     * otro bloque quedo IGUAL» en vez de comprobar una clave y dar por hecho el
     * resto -- que es como se cuelan las perdidas silenciosas.
     */
    private Map<String, String> retratoDe(long idPropiedad, long idEncargo) {
        Map<String, String> retrato = new LinkedHashMap<>();
        bloque(propiedades.consultar(idPropiedad, actor()), idEncargo).condiciones()
                .forEach(c -> retrato.put(c.clave(), c.valor()));
        return retrato;
    }

    private static EncargoFicha bloque(PropiedadUniversalService.FichaPropiedadUniversal ficha,
                                       long idEncargo) {
        return ficha.encargos().stream()
                .filter(e -> e.idEncargo() != null && e.idEncargo() == idEncargo)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "La ficha no trae el encargo " + idEncargo));
    }

    private String atributoDeLaPropiedad(long idPropiedad, String clave) {
        return propiedades.consultar(idPropiedad, actor()).atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(PropiedadUniversalService.AtributoFicha::valor)
                .findFirst().orElse(null);
    }

    /**
     * Cierra un encargo por SQL.
     *
     * <p>{@code ck_captacion_cierre} exige fecha y motivo juntos: 'O' es «otro»,
     * el unico codigo que no afirma nada sobre por que se cerro -- y aqui no se
     * cerro por ninguna razon de negocio, se cerro porque lo pide la prueba.
     */
    private void cerrar(long idEncargo) {
        jdbc.update("""
                update captacion
                   set estado = 'C', fecha_cierre = current_date, motivo_cierre = 'O',
                       detalle_motivo_cierre = 'Cierre de prueba del Corte 0C'
                 where id_captacion = ?
                """, idEncargo);
    }

    private long encargo(long idPropiedad, String operacion) {
        return jdbc.queryForObject("""
                select min(id_captacion) from captacion
                 where id_propiedad = ? and motivo_operacion = ?
                """, Long.class, idPropiedad, operacion);
    }

    /**
     * Un SEGUNDO episodio de la misma operacion, cuando el anterior ya cerro.
     *
     * <p>Se abre por SQL porque hoy no hay caso de uso que reabra: {@code editar}
     * con una operacion <b>actualiza el encargo vivo</b> y contesta «esta
     * propiedad no tiene ningun encargo vivo de ALQUILER» cuando no lo hay.
     * <b>Es una capacidad que falta, no un fallo de este corte</b>, y queda
     * anotada como tal.
     *
     * <p>Lo que la prueba afirma no depende de como se abra: que el episodio
     * nuevo <b>nace sin condiciones</b>. Que se cree por SQL lo hace, si acaso,
     * mas exigente -- ni siquiera pasa por el caso de uso que podria limpiarlas.
     */
    private long abrirOtroEncargo(long idEncargoCerrado) {
        return jdbc.queryForObject("""
                insert into captacion (codigo_captacion, fecha_captacion, estado, id_propiedad,
                                       id_rol_agente, motivo_operacion, organizacion_id,
                                       fecha_inicio_encargo, fecha_fin_encargo, exclusividad)
                select left('0C-' || md5(random()::text), 20), current_date, 'P', c.id_propiedad,
                       c.id_rol_agente, c.motivo_operacion, c.organizacion_id,
                       current_date, current_date + 180, c.exclusividad
                  from captacion c where c.id_captacion = ?
                returning id_captacion
                """, Long.class, idEncargoCerrado);
    }

    /** Una propiedad con las DOS operaciones abiertas: el caso que lo prueba todo. */
    private long registrarConVentaYAlquiler() {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso 0C",
                new Ubicacion("Av. Sujeto " + java.util.UUID.randomUUID().toString().substring(0, 8),
                        "Miraflores", null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("350000"), "USD",
                                null, null, null, null, null, null, null),
                        new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                                null, null, null, null, null, null, null)),
                null), actor).idPropiedad();
    }

    private PublicacionService.DatosPublicacion publicacionDePrueba() {
        return new PublicacionService.DatosPublicacion("WEB_PROPIA", null, new BigDecimal("2500"),
                "PEN", "Anuncio de prueba 0C", null, null);
    }

    private Actor actor() {
        Map<String, Object> fila = jdbc.queryForList("""
                select a.id_persona_rol, r.organizacion_id, r.id_persona
                  from detalle_agente a join persona_rol r on r.id_persona_rol = a.id_persona_rol
                 limit 1
                """).get(0);
        return new Actor(((Number) fila.get("organizacion_id")).longValue(),
                ((Number) fila.get("id_persona")).longValue(),
                ((Number) fila.get("id_persona_rol")).longValue(), "AGENTE");
    }

    // ==================================================================

    private long columnas(String tabla, String columna) {
        Long n = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = ? and column_name = ?
                """, Long.class, tabla, columna);
        return n == null ? 0 : n;
    }

    private boolean existeTabla(String tabla) {
        Long n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Long.class, tabla);
        return n != null && n > 0;
    }

    private String clavePrimaria(String tabla) {
        return jdbc.queryForObject("""
                select string_agg(a.attname, ', ' order by k.ord)
                  from pg_constraint c
                  join lateral unnest(c.conkey) with ordinality as k(attnum, ord) on true
                  join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
                 where c.conrelid = ?::regclass and c.contype = 'p'
                """, String.class, tabla);
    }
}
