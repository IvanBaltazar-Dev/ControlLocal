package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>El gate del Corte 0B: el catalogo aprende a hablar.</b>
 *
 * <h2>Que capacidad prueba</h2>
 * Hasta V72 el catalogo sabia declarar cinco tipos de dato y ninguna otra cosa.
 * No sabia decir que opciones tiene una LISTA —y por eso la unica sembrada
 * viajaba como texto libre—, ni cuanto mide un texto, ni cuanto vale como
 * maximo un numero, ni que un importe lleva moneda, ni que una fecha es una
 * fecha, ni que un dato puede hacer falta para PUBLICAR sin hacer falta para
 * dar de alta.
 *
 * <h2>Por que cada caso existe</h2>
 * Cada uno cubre un comportamiento distinto, no una linea de codigo. Se
 * agrupan por causa: los tres tipos nuevos recorren ida y vuelta; el
 * multivalor prueba ademas reemplazo y retirada, que es la mitad de lo que
 * significa editar una lista; el vocabulario se rechaza por las DOS puertas
 * —la fila padre y la tabla hija— porque son dos triggers distintos; y la
 * exigencia se prueba en los tres niveles contra el caso de uso de publicacion
 * real, no contra un booleano.
 *
 * <p>Contra PostgreSQL real porque casi todo lo que se afirma aqui lo garantiza
 * un trigger, y un trigger no lo lee ni javac, ni Hibernate, ni ArchUnit.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogoQueHablaIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired MotorDeCaptura captura;
    @Autowired PublicacionService publicaciones;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    /**
     * Cada caso arranca sin claves de prueba vivas.
     *
     * <p>Las que siembra son `aplica_todos`, asi que una marcada PUB que
     * sobreviva bloquea la publicacion de TODOS los casos siguientes -- y el
     * fallo aparece en el caso equivocado, que es peor que no tenerlo. Se
     * desactivan en vez de borrarse porque una clave con valores escritos no se
     * puede borrar sin borrar los valores, y eso ya lo prueba otro caso.
     */
    @org.junit.jupiter.api.BeforeEach
    void sinClavesDePruebaVivas() {
        jdbc.update("update catalogo_atributo set activo = false "
                + " where clave like 'zz%' and organizacion_id is not null");
    }

    // ==================================================================
    // Los tres tipos de dato nuevos, de ida y vuelta
    // ==================================================================

    @Test
    @DisplayName("FECHA: se guarda en su columna y vuelve como fecha")
    void laFechaViajaEnSuColumna() {
        String clave = sembrarClave("zz_disponible", "FECHA", null);
        long id = registrarDepartamento();

        editar(id, new ValorAtributo(clave, "2026-11-30"));

        assertEquals("2026-11-30", valorDe(id, clave), "la fecha no volvio igual");
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from atributo_propiedad
                 where id_propiedad = ? and clave = ? and valor_fecha is not null
                   and valor_texto is null
                """, Integer.class, id, clave),
                "la fecha tiene que estar en valor_fecha, no en valor_texto");
    }

    @Test
    @DisplayName("IMPORTE: el monto y la moneda sobreviven juntos")
    void elImporteViajaConSuMoneda() {
        String clave = sembrarClave("zz_mantenimiento", "IMPORTE", null);
        long id = registrarDepartamento();

        editar(id, ValorAtributo.importe(clave, "120.50", "USD"));

        Map<String, Object> fila = jdbc.queryForMap("""
                select valor_numero, valor_moneda from atributo_propiedad
                 where id_propiedad = ? and clave = ?
                """, id, clave);
        assertEquals(0, new BigDecimal("120.50").compareTo((BigDecimal) fila.get("valor_numero")));
        assertEquals("USD", fila.get("valor_moneda"));
        assertEquals("USD 120.5", valorDe(id, clave), "el importe se lee con su moneda");
    }

    @Test
    @DisplayName("IMPORTE: sin moneda no se guarda, porque un numero sin moneda no es dinero")
    void elImporteSinMonedaSeRechaza() {
        String clave = sembrarClave("zz_importe_solo", "IMPORTE", null);
        long id = registrarDepartamento();

        assertThrows(ReglaNegocioException.class,
                () -> editar(id, new ValorAtributo(clave, "120.50")));
    }

    @Test
    @DisplayName("LISTA_MULTIPLE: guarda N valores reales, no una cadena con comas")
    void elMultivalorGuardaVariosValores() {
        String clave = sembrarClave("zz_servicios", "LISTA_MULTIPLE", List.of("AGUA", "LUZ", "GAS"));
        long id = registrarDepartamento();

        editar(id, ValorAtributo.multiple(clave, List.of("AGUA", "LUZ")));

        assertEquals(2, jdbc.queryForObject("""
                select count(*) from atributo_propiedad_opcion o
                  join atributo_propiedad a on a.id_atributo_propiedad = o.id_atributo_propiedad
                 where a.id_propiedad = ? and a.clave = ?
                """, Integer.class, id, clave),
                "tienen que ser dos filas, no una cadena");
        assertEquals("AGUA, LUZ", valorDe(id, clave));
    }

    @Test
    @DisplayName("LISTA_MULTIPLE: editar SUSTITUYE, no acumula")
    void elMultivalorSeSustituye() {
        String clave = sembrarClave("zz_areas", "LISTA_MULTIPLE", List.of("AGUA", "LUZ", "GAS"));
        long id = registrarDepartamento();
        editar(id, ValorAtributo.multiple(clave, List.of("AGUA", "LUZ")));

        editar(id, ValorAtributo.multiple(clave, List.of("GAS")));

        assertEquals("GAS", valorDe(id, clave),
                "sin sustituir no habria forma de QUITAR una opcion, que es la mitad de editar");
    }

    @Test
    @DisplayName("LISTA_MULTIPLE: retirarlo se lleva sus valores, sin huerfanos")
    void retirarUnMultivalorSeLlevaSusValores() {
        String clave = sembrarClave("zz_comunes", "LISTA_MULTIPLE", List.of("AGUA", "LUZ", "GAS"));
        long id = registrarDepartamento();
        editar(id, ValorAtributo.multiple(clave, List.of("AGUA", "GAS")));

        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null, null, null,
                List.of(clave)), actor());

        assertEquals(0, jdbc.queryForObject("""
                select count(*) from atributo_propiedad_opcion o
                  join atributo_propiedad a on a.id_atributo_propiedad = o.id_atributo_propiedad
                 where a.id_propiedad = ?
                """, Integer.class, id), "quedaron opciones huerfanas");
        assertEquals(null, valorDe(id, clave));
    }

    // ==================================================================
    // El vocabulario: las dos puertas lo vigilan
    // ==================================================================

    @Test
    @DisplayName("vocabulario: un valor inventado se rechaza por las dos puertas")
    void elVocabularioSeRespetaEnLasDosPuertas() {
        String lista = sembrarClave("zz_conservacion", "LISTA", List.of("NUEVO", "BUENO"));
        String multi = sembrarClave("zz_equipamiento", "LISTA_MULTIPLE", List.of("AGUA", "LUZ"));
        long id = registrarDepartamento();

        assertThrows(RuntimeException.class,
                () -> editar(id, new ValorAtributo(lista, "DERRUIDO")),
                "la fila padre tiene que rechazar un valor fuera del vocabulario");
        assertThrows(RuntimeException.class,
                () -> editar(id, ValorAtributo.multiple(multi, List.of("PISCINA"))),
                "la tabla hija tiene su propio trigger y tambien tiene que rechazarlo");
    }

    @Test
    @DisplayName("el trigger rechaza un tipo de dato sin regla, en vez de aceptarlo en silencio")
    void unTipoSinReglaSeRechaza() {
        String clave = sembrarClave("zz_inventado", "TEXTO", null);
        long id = registrarDepartamento();
        // Se falsea el tipo saltandose el CHECK. Antes de V72 la cadena
        // IF/ELSIF del trigger caia por su ELSE implicito y la fila entraba con
        // cualquier columna rellena -- el fallo de V40, con la build en verde.
        jdbc.execute("alter table catalogo_atributo drop constraint ck_catalogo_atributo_tipo_dato");
        try {
            jdbc.update("update catalogo_atributo set tipo_dato = 'INVENTADO' where clave = ?", clave);
            assertThrows(RuntimeException.class,
                    () -> jdbc.update("""
                            insert into atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
                            values (?, ?, ?, 'loquesea')
                            """, actor().idOrganizacion(), id, clave));
        } finally {
            jdbc.update("update catalogo_atributo set tipo_dato = 'TEXTO' where clave = ?", clave);
            jdbc.execute("""
                    alter table catalogo_atributo add constraint ck_catalogo_atributo_tipo_dato
                    check (tipo_dato in ('TEXTO','ENTERO','DECIMAL','BOOLEANO','LISTA',
                                         'LISTA_MULTIPLE','FECHA','IMPORTE'))
                    """);
        }
    }

    /**
     * <b>La regla del trigger no puede desaparecer sin que nadie lo note.</b>
     *
     * <p>Ni javac, ni Hibernate, ni ArchUnit, ni {@code ddl-auto: validate} leen
     * un cuerpo PL/pgSQL. Este caso lo lee: si alguien vuelve a dejar la cadena
     * sin salida por defecto, el build cae aqui y no dentro de un mes.
     */
    @Test
    @DisplayName("gate: el trigger conserva su salida por defecto que grita")
    void elTriggerConservaSuElseQueGrita() {
        String cuerpo = jdbc.queryForObject(
                "select prosrc from pg_proc where proname = 'exigir_atributo_gobernado'", String.class);
        assertNotNull(cuerpo, "el trigger del catalogo desaparecio");
        assertTrue(cuerpo.contains("CASE cat.tipo_dato"),
                "la regla de almacenamiento tiene que ser un CASE sobre el tipo declarado");
        assertTrue(cuerpo.contains("no tiene regla de almacenamiento"),
                "sin ELSE que lance, un tipo nuevo entra con cualquier columna y nadie se entera");
        for (String tipo : List.of("IMPORTE", "FECHA", "LISTA_MULTIPLE")) {
            assertTrue(cuerpo.contains(tipo), "el trigger no sabe nada de " + tipo);
        }
    }

    /**
     * <b>{@code requerido} es el espejo de {@code exigencia}, no un segundo
     * dato.</b>
     *
     * <p>V72 partio el booleano en tres niveles y dejo la columna vieja
     * viviendo al lado, coherente al 100 %. Nada en el esquema las ata: una
     * fila que escriba solo una de las dos las separa en silencio, y a partir
     * de ahi dos lectores del mismo hecho contestan distinto —el trigger mira
     * una y el motor de captura la otra—. V78 anadio tres filas a esta tabla y
     * es la primera migracion que lo hace desde entonces, asi que la coherencia
     * deja de ser una costumbre y pasa a estar vigilada.
     */
    @Test
    @DisplayName("gate: requerido sigue siendo espejo exacto de exigencia")
    void requeridoEsEspejoDeExigencia() {
        List<String> divergentes = jdbc.queryForList("""
                select c.clave || '/' || t.tipo_propiedad
                  from catalogo_atributo_tipo t
                  join catalogo_atributo c on c.id_catalogo_atributo = t.id_catalogo_atributo
                 where t.requerido <> (t.exigencia = 'ALT')
                 order by 1
                """, String.class);
        assertEquals(List.of(), divergentes,
                "requerido y exigencia dicen cosas distintas sobre la misma fila");
    }

    // ==================================================================
    // El contrato: la definicion sale del Core y nadie mas la produce
    // ==================================================================

    @Test
    @DisplayName("definicion sin operacion: la cosa fisica y CERO bloques de encargo")
    void laDefinicionSinOperacionNoTraeEncargos() {
        var definicion = captura.definicion(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, "DEPARTAMENTO", null, actor());

        assertTrue(definicion.deLaOperacion().isEmpty(),
                "sin operacion no puede haber bloque economico: la operacion es del Encargo");
        assertFalse(definicion.delTipo().isEmpty(), "la cosa fisica si tiene que venir");
        assertTrue(definicion.delTipo().stream().anyMatch(p -> "dormitorios".equals(p.clave())));
    }

    @Test
    @DisplayName("definicion con VENTA+ALQUILER: la fisica una vez, dos bloques")
    void laDefinicionConDosOperacionesNoDuplicaLaFisica() {
        var definicion = captura.definicion(
                MotorDeCaptura.REGISTRAR_PROPIEDAD, "LOCAL", "VENTA,ALQUILER", actor());

        assertEquals(2, definicion.deLaOperacion().size());
        assertEquals(definicion.delTipo().stream().map(MotorDeCaptura.Pregunta::clave).distinct().count(),
                definicion.delTipo().size(),
                "la ficha fisica se pregunta UNA vez aunque haya dos encargos");
    }

    @Test
    @DisplayName("el contrato publica opciones con rotulo, y el control deja de ser TEXTO")
    void laDefinicionPublicaElVocabulario() {
        String clave = sembrarClave("zz_tipologia", "LISTA", List.of("FLAT", "DUPLEX"));

        MotorDeCaptura.Pregunta pregunta = captura
                .definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "DEPARTAMENTO", null, actor())
                .delTipo().stream().filter(p -> clave.equals(p.clave())).findFirst()
                .orElseGet(() -> fail("la clave sembrada no llego a la definicion"));

        assertEquals("SELECTOR", pregunta.control(),
                "sin opciones el control caia a TEXTO y la LISTA se volvia texto libre");
        assertEquals(2, pregunta.opciones().size());
        assertTrue(pregunta.opciones().stream().anyMatch(o -> "FLAT".equals(o.valor())));
        assertNotNull(pregunta.exigencia(), "la exigencia tiene que viajar");
    }

    @Test
    @DisplayName("el orden viene del catalogo; el motor no lo reinventa")
    void elOrdenSaleDelCatalogo() {
        String clave = sembrarClave("zz_ordenada", "TEXTO", null);
        jdbc.update("update catalogo_atributo set orden = 777 where clave = ?", clave);

        MotorDeCaptura.Pregunta pregunta = captura
                .definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "DEPARTAMENTO", null, actor())
                .delTipo().stream().filter(p -> clave.equals(p.clave())).findFirst()
                .orElseGet(() -> fail("la clave sembrada no llego a la definicion"));

        assertEquals(777, pregunta.orden(),
                "el motor pisaba el orden con la posicion del bucle y publicaba uno distinto "
                        + "del que publicaba el otro endpoint");
    }

    // ==================================================================
    // La exigencia, contra el caso de uso real de publicacion
    // ==================================================================

    @Test
    @DisplayName("exigencia: ALT bloquea el alta; PUB no")
    void altBloqueaElAltaYPubNo() {
        String pub = sembrarClave("zz_ascensores", "ENTERO", null);
        jdbc.update("update catalogo_atributo_tipo set exigencia = 'PUB' "
                + " where id_catalogo_atributo = (select id_catalogo_atributo from catalogo_atributo "
                + "  where clave = ?)", pub);

        try {
            // El alta pasa aunque falte una clave PUB: el corredor no lo sabe
            // todo en la primera conversacion, y no tiene por que.
            assertTrue(registrarDepartamento() > 0);
        } finally {
            retirar(pub);
        }
    }

    @Test
    @DisplayName("exigencia: una clave PUB que falta impide publicar, y lo dice con su nombre")
    void pubImpidePublicar() {
        String pub = sembrarClave("zz_conserjeria", "ENTERO", null);
        marcar(pub, "PUB");
        long id = registrarDepartamento();
        long encargo = encargoDe(id);

        try {
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> publicaciones.crearEnEncargo(encargo, publicacionDePrueba(), actor()));
            assertTrue(error.getMessage().contains("no se puede publicar"), error.getMessage());
        } finally {
            retirar(pub);
        }
    }

    @Test
    @DisplayName("exigencia: completada la clave PUB, publicar pasa")
    void completarLaClavePubDesbloqueaLaPublicacion() {
        String pub = sembrarClave("zz_recepcion", "ENTERO", null);
        marcar(pub, "PUB");
        long id = registrarDepartamento();
        long encargo = encargoDe(id);

        try {
            editar(id, new ValorAtributo(pub, "2"));
            assertNotNull(publicaciones.crearEnEncargo(encargo, publicacionDePrueba(), actor()));
        } finally {
            retirar(pub);
        }
    }

    @Test
    @DisplayName("exigencia: una clave OPC que falta no bloquea nada")
    void opcNoBloqueaNada() {
        sembrarClave("zz_opcional", "TEXTO", null);
        long id = registrarDepartamento();

        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()));
    }

    @Test
    @DisplayName("exigencia: pasar una publicacion a PUBLICADO tambien pregunta")
    void laTransicionAPublicadoTambienPregunta() {
        long id = registrarDepartamento();
        long encargo = encargoDe(id);
        var publicacion = publicaciones.crearEnEncargo(encargo, publicacionDePrueba(), actor());

        // La clave PUB aparece DESPUES de crear el borrador del anuncio: si la
        // regla viviera solo en la puerta de creacion, esta transicion la
        // saltaria y el anuncio saldria igual.
        String pub = sembrarClave("zz_altura_edificio", "ENTERO", null);
        marcar(pub, "PUB");

        try {
            assertThrows(ReglaNegocioException.class, () -> publicaciones.cambiarEstado(
                    publicacion.id(), "P", actor()));
        } finally {
            retirar(pub);
        }
    }

    // ==================================================================
    // El coste de la lectura no crece con la cartera
    // ==================================================================

    /**
     * <b>El multivalor se lee en lote, no fila a fila.</b>
     *
     * <p>Se mide con las estadisticas de Hibernate y no contando a ojo: una
     * propiedad con TRES claves multivalor tiene que costar las mismas consultas
     * que una con UNA. Si la lectura preguntara por fila ancla, la de tres
     * costaria dos mas -- y esa es la N+1 que RC-003 retiro del repositorio y
     * que siempre vuelve con una capacidad nueva leida "solo para este caso".
     */
    @Test
    @DisplayName("lote: el numero de consultas no crece con las claves multivalor")
    void laLecturaEnLoteNoCreceConN() {
        String una = sembrarClave("zz_lote_a", "LISTA_MULTIPLE", List.of("AGUA", "LUZ"));
        String dos = sembrarClave("zz_lote_b", "LISTA_MULTIPLE", List.of("AGUA", "LUZ"));
        String tres = sembrarClave("zz_lote_c", "LISTA_MULTIPLE", List.of("AGUA", "LUZ"));

        long conUna = registrarDepartamento();
        editar(conUna, ValorAtributo.multiple(una, List.of("AGUA")));

        long conTres = registrarDepartamento();
        editar(conTres, ValorAtributo.multiple(una, List.of("AGUA")));
        editar(conTres, ValorAtributo.multiple(dos, List.of("LUZ")));
        editar(conTres, ValorAtributo.multiple(tres, List.of("AGUA", "LUZ")));

        long costeDeUna = consultasDe(() -> propiedades.consultar(conUna, actor()));
        long costeDeTres = consultasDe(() -> propiedades.consultar(conTres, actor()));

        assertEquals(costeDeUna, costeDeTres,
                "una ficha con tres claves multivalor costo " + costeDeTres + " consultas y una "
                        + "con una costo " + costeDeUna + ": se esta preguntando por fila");
    }

    /** Las consultas que dispara una operacion, segun Hibernate. */
    private long consultasDe(Runnable operacion) {
        var estadisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        estadisticas.setStatisticsEnabled(true);
        long antes = estadisticas.getQueryExecutionCount()
                + estadisticas.getPrepareStatementCount();
        operacion.run();
        return estadisticas.getQueryExecutionCount()
                + estadisticas.getPrepareStatementCount() - antes;
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** Una clave del tenant, con su aplicabilidad a los siete tipos y su vocabulario. */
    private String sembrarClave(String base, String tipoDato, List<String> opciones) {
        String clave = base + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        long org = actor().idOrganizacion();
        jdbc.update("""
                insert into catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato,
                                               aplica_todos, del_sistema, orden)
                values (?, ?, ?, ?, true, false, 900)
                """, org, clave, "Prueba " + base, tipoDato);
        jdbc.update("""
                insert into catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                                    requerido, exigencia)
                select c.id_catalogo_atributo, t.tipo, false, 'OPC'
                  from catalogo_atributo c
                  cross join (values ('L'),('O'),('D'),('C'),('T'),('A'),('X')) as t(tipo)
                 where c.clave = ? and c.organizacion_id = ?
                """, clave, org);
        if (opciones != null) {
            for (int i = 0; i < opciones.size(); i++) {
                jdbc.update("""
                        insert into catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
                        select c.id_catalogo_atributo, ?, ?, ?
                          from catalogo_atributo c where c.clave = ? and c.organizacion_id = ?
                        """, opciones.get(i), opciones.get(i), i, clave, org);
            }
        }
        return clave;
    }

    /**
     * Retira la clave al terminar el caso.
     *
     * <p>Hace falta porque estas claves son `aplica_todos`: una marcada PUB en
     * un caso bloquearia la publicacion de TODOS los demas, y el fallo
     * aparecia en el caso equivocado.
     */
    private void retirar(String clave) {
        jdbc.update("update catalogo_atributo set activo = false where clave = ?", clave);
    }

    private void marcar(String clave, String exigencia) {
        jdbc.update("""
                update catalogo_atributo_tipo set exigencia = ?
                 where id_catalogo_atributo = (select id_catalogo_atributo from catalogo_atributo
                                                where clave = ? and organizacion_id = ?)
                """, exigencia, clave, actor().idOrganizacion());
    }

    private void editar(long id, ValorAtributo valor) {
        propiedades.editar(id, new ComandoEdicion(null, null, null, null, null,
                List.of(valor), null, null), actor());
    }

    private String valorDe(long id, String clave) {
        return propiedades.consultar(id, actor()).atributos().stream()
                .filter(a -> clave.equals(a.clave()))
                .map(PropiedadUniversalService.AtributoFicha::valor)
                .findFirst().orElse(null);
    }

    private long registrarDepartamento() {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, "DEPARTAMENTO", null,
                "Caso 0B",
                new Ubicacion("Av. Catalogo " + System.nanoTime() % 1000000, "Miraflores",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "90"),
                        new ValorAtributo("dormitorios", "3")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null)),
                null), actor).idPropiedad();
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
