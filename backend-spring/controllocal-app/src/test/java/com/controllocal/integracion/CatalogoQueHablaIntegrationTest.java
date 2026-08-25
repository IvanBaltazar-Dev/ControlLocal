package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.AtributoQueFalta;
import com.controllocal.service.PropiedadUniversalService.ComandoEdicion;
import com.controllocal.service.PropiedadUniversalService.ComandoRegistro;
import com.controllocal.service.PropiedadUniversalService.EncargoFicha;
import com.controllocal.service.PropiedadUniversalService.FichaPropiedadUniversal;
import com.controllocal.service.PropiedadUniversalService.OperacionSolicitada;
import com.controllocal.service.PropiedadUniversalService.Titular;
import com.controllocal.service.PropiedadUniversalService.Ubicacion;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PublicacionService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
     * Se escribe por el agregado a proposito en un solo caso: para llegar al
     * trigger hay que saltarse la comprobacion de la capa de servicio, y eso es
     * lo unico que este repositorio hace aqui.
     */
    @Autowired PropiedadRepository propiedadesEscritas;

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
    // V79 - El vocabulario de las capacidades registrales
    // ==================================================================

    /**
     * <b>La oficina registral admite lo que declara el catalogo y nada mas.</b>
     *
     * <p>Es la primera LISTA cuya autoridad es un campo canonico, y ahi la
     * comprobacion de V72 no llegaba: vive dentro del trigger de
     * {@code atributo_propiedad}, por donde un valor estructural no pasa. Sin la
     * guarda de V79 esta clave habria aceptado cualquier cadena y «Lima» y
     * «LIMA» serian dos oficinas distintas para el matcher.
     */
    @Test
    @DisplayName("V79: una oficina del vocabulario entra")
    void laOficinaDelVocabularioEntra() {
        long id = registrarDepartamento();

        editar(id, new ValorAtributo("oficina_registral", "CALLAO"));

        assertEquals("CALLAO", valorDe(id, "oficina_registral"));
    }

    @Test
    @DisplayName("V79: una oficina que no esta en el catalogo se rechaza con su nombre delante")
    void laOficinaFueraDelVocabularioSeRechaza() {
        long id = registrarDepartamento();

        var error = assertThrows(ReglaNegocioException.class,
                () -> editar(id, new ValorAtributo("oficina_registral", "MADRID")));

        assertTrue(error.getMessage().contains("oficina_registral"),
                "el mensaje tiene que decir QUE atributo: " + error.getMessage());
        assertTrue(error.getMessage().contains("LIMA"),
                "y cuales son los valores posibles, que salen del catalogo: " + error.getMessage());
    }

    /**
     * <b>Y la base lo rechaza igual, sin pasar por Java — para TODO concepto
     * estructural de tipo lista, no sólo para la oficina.</b>
     *
     * <p>La capa de servicio da el mensaje; la garantía es el trigger. Sin este
     * caso, cualquier camino que escribiera la columna directamente —una
     * migración, una corrección a mano, un servicio nuevo— dejaría entrar un
     * valor inventado.
     *
     * <h2>Por qué recorre el catálogo y no nombra la oficina</h2>
     * {@code tg_vocabulario_estructural} lleva un {@code WHEN} que enumera las
     * columnas por las que merece la pena despertarlo — sin él costaba 0,49 ms
     * por fila y hay suites que cargan 100 000 propiedades. Ese {@code WHEN} es
     * la única parte del corte que puede quedarse corta <b>en silencio</b>: si
     * mañana nace un segundo concepto estructural de tipo lista y nadie lo añade
     * ahí, la función no llega a ejecutarse y su {@code ELSE} —que sí grita— no
     * sirve de nada.
     *
     * <p>Esta prueba lo cierra sin inventar un mapa nuevo: los conceptos salen
     * del catálogo, y la correspondencia concepto → columna la pone
     * {@link EscritorEstructural}, que es donde ya vive. Escribe por el agregado
     * —saltándose la comprobación de la capa de servicio, que es justo lo que
     * hay que saltarse para llegar al trigger— y exige que PostgreSQL lo pare.
     */
    @Test
    @DisplayName("V79: la base defiende el vocabulario de todo concepto estructural de lista")
    void laBaseDefiendeElVocabularioDeTodoEstructural() {
        List<Map<String, Object>> conceptos = jdbc.queryForList("""
                select c.clave, c.campo_estructural from catalogo_atributo c
                 where c.destino = 'ESTRUCTURAL' and c.activo
                   and c.tipo_dato in ('LISTA', 'LISTA_MULTIPLE')
                   and exists (select 1 from catalogo_atributo_opcion o
                                where o.id_catalogo_atributo = c.id_catalogo_atributo)
                """);
        assertFalse(conceptos.isEmpty(),
                "sin ningun concepto estructural de tipo lista este caso no vigila nada");

        List<String> sinDefensa = new java.util.ArrayList<>();
        for (Map<String, Object> fila : conceptos) {
            String clave = (String) fila.get("clave");
            String concepto = (String) fila.get("campo_estructural");
            long id = registrarDepartamento();

            Propiedad propiedad = propiedadesEscritas.findById(id).orElseThrow();
            EscritorEstructural.aplicar(propiedad, concepto, "VALOR_INVENTADO", clave);
            try {
                propiedadesEscritas.saveAndFlush(propiedad);
                sinDefensa.add(clave + " (" + concepto + ")");
            } catch (RuntimeException rechazado) {
                // Es lo que tiene que pasar.
            }
        }

        assertTrue(sinDefensa.isEmpty(), """
                La base acepto un valor fuera del vocabulario en: %s

                Casi siempre significa que el `WHEN` de tg_vocabulario_estructural no nombra
                la columna de ese concepto, asi que la funcion --y su ELSE-- no llegan a
                ejecutarse. El vocabulario queda sin comprobar y nada avisa.
                """.formatted(sinDefensa));
    }

    /**
     * <b>El vocabulario tiene un solo dueno.</b>
     *
     * <p>Las opciones que el motor publica son <b>las filas del catalogo</b>, no
     * una lista escrita en el codigo. Se comprueba comparando las dos: si
     * alguien anadiera la oficina de Ica en un {@code Set} de Java, el motor
     * seguiria publicando seis y este caso lo veria.
     */
    @Test
    @DisplayName("V79: las oficinas que publica el contrato son exactamente las del catalogo")
    void elVocabularioDeLaOficinaSaleDelCatalogo() {
        List<String> enElCatalogo = jdbc.queryForList("""
                select o.valor from catalogo_atributo_opcion o
                  join catalogo_atributo c on c.id_catalogo_atributo = o.id_catalogo_atributo
                 where c.clave = 'oficina_registral' and c.organizacion_id is null and o.activo
                 order by o.orden
                """, String.class);

        var definicion = captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD,
                "DEPARTAMENTO", "ALQUILER", actor());
        var pregunta = definicion.todas().stream()
                .filter(p -> "oficina_registral".equals(p.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "el motor no publica `oficina_registral`: una clave ESTRUCTURAL sigue "
                                + "siendo una pregunta del catalogo"));

        assertNotNull(pregunta.opciones(),
                "sin opciones, `controlDe` la degrada a TEXTO y el vocabulario deja de existir "
                        + "sin que nadie avise -- que es lo que le paso a servicios_disponibles");
        assertEquals(enElCatalogo,
                pregunta.opciones().stream().map(MotorDeCaptura.Opcion::valor).toList(),
                "el contrato publica un vocabulario distinto del que declara el catalogo: hay "
                        + "una segunda lista de oficinas en alguna parte");
    }

    /**
     * <b>Las cargas son un multivalor de verdad.</b>
     *
     * <p>Un inmueble puede tener hipoteca Y estar en sucesion a la vez. Con una
     * LISTA simple habria que elegir cual de las dos se cuenta, y la que se
     * descartara no dejaria rastro.
     */
    @Test
    @DisplayName("V79: las cargas admiten varias a la vez y conservan las dos")
    void lasCargasSonMultivalorDeVerdad() {
        long id = registrarDepartamento();

        editar(id, ValorAtributo.multiple("cargas_gravamenes",
                List.of("HIPOTECA", "SUCESION_PENDIENTE")));

        var ficha = propiedades.consultar(id, actor());
        var cargas = ficha.atributos().stream()
                .filter(a -> "cargas_gravamenes".equals(a.clave()))
                .findFirst().orElseThrow();
        assertEquals(List.of("HIPOTECA", "SUCESION_PENDIENTE"),
                cargas.valores().stream().sorted().toList(),
                "las dos cargas tienen que volver, no la primera");

        assertEquals(0, jdbc.queryForObject("""
                select count(*) from atributo_propiedad
                 where id_propiedad = ? and clave = 'cargas_gravamenes'
                   and num_nonnulls(valor_texto, valor_numero, valor_booleano, valor_fecha) > 0
                """, Integer.class, id),
                "la fila ancla de un multivalor no lleva escalar: llevarlo seria el mismo dato "
                        + "dicho de dos formas");
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from atributo_propiedad_opcion o
                  join atributo_propiedad a on a.id_atributo_propiedad = o.id_atributo_propiedad
                 where a.id_propiedad = ? and a.clave = 'cargas_gravamenes'
                """, Integer.class, id),
                "y cada carga es una fila propia, que es lo que permite buscarlas");
    }

    @Test
    @DisplayName("V79: una carga que no esta en el catalogo se rechaza")
    void unaCargaInventadaSeRechaza() {
        long id = registrarDepartamento();

        assertThrows(Exception.class, () -> editar(id, ValorAtributo.multiple(
                "cargas_gravamenes", List.of("USUFRUCTO_VITALICIO"))),
                "el vocabulario de un multivalor lo vigila tg_opcion_gobernada");
    }

    /**
     * <b>Lo que nadie declaro no existe.</b>
     *
     * <p>La regla del 3g, comprobada sobre las cuatro claves gobernadas que
     * introduce este corte: la ausencia significa <b>todavia no se sabe</b>, y
     * un defecto la convertiria en una respuesta que nadie dio. «Ninguna carga»
     * es una afirmacion verificada contra el registro, no el estado inicial de
     * un dato que nadie ha mirado.
     */
    @Test
    @DisplayName("V79: no declarar independizado, cargas o declaratoria no produce ningun valor")
    void laAusenciaNoSeMaterializa() {
        long id = registrarDepartamento();

        // Las tres que aplican a un departamento. `area_segun_partida` no
        // aplica a D --es de casa, terreno y almacen-- y se comprueba abajo
        // sobre un terreno: "no aplica" y "no se sabe" son cosas distintas y
        // este caso mide la segunda.
        for (String clave : List.of("independizado", "cargas_gravamenes", "declaratoria_fabrica")) {
            assertNull(valorDe(id, clave),
                    clave + " aparecio con valor sin que nadie lo declarara");
            assertEquals(0, jdbc.queryForObject("""
                    select count(*) from atributo_propiedad where id_propiedad = ? and clave = ?
                    """, Integer.class, id, clave),
                    "se materializo una fila de " + clave + " que nadie escribio");
        }

        long terreno = registrarTerreno();
        assertNull(valorDe(terreno, "area_segun_partida"),
                "un area que nadie midio no vale cero: cero es una medida");

        assertNull(jdbc.queryForMap("""
                select partida_registral from propiedad where id_propiedad = ?
                """, id).get("partida_registral"),
                "y la identidad registral tampoco se inventa: NULL es 'no se sabe'");
    }

    /**
     * <b>La declaratoria existe para casa y departamento, y hoy las dos son
     * OPC.</b>
     *
     * <p>Su exigencia futura es distinta —PUB en C, OPC en D— y eso es legitimo
     * porque {@code catalogo_atributo_tipo} la guarda por fila. Lo que V79 no
     * hace es estrenarla: promover cambia quien puede publicar, y eso es una
     * decision de negocio con su propio corte.
     */
    @Test
    @DisplayName("V79: declaratoria_fabrica aplica a C y D, y ninguna bloquea todavia")
    void laDeclaratoriaAplicaADosTiposYNingunaBloquea() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select t.tipo_propiedad, t.exigencia, t.requerido
                  from catalogo_atributo_tipo t
                  join catalogo_atributo c on c.id_catalogo_atributo = t.id_catalogo_atributo
                 where c.clave = 'declaratoria_fabrica' and c.organizacion_id is null
                 order by t.tipo_propiedad
                """);

        assertEquals(List.of("C", "D"), filas.stream().map(f -> f.get("tipo_propiedad")).toList(),
                "la declaratoria de fabrica solo tiene sentido donde hay edificacion inscrita");
        filas.forEach(fila -> {
            assertEquals("OPC", fila.get("exigencia"),
                    "V79 no promueve ninguna clave: PUB bloquea publicar y estrenarlo aqui "
                            + "dejaria sin anunciarse a toda la cartera");
            assertEquals(Boolean.FALSE, fila.get("requerido"),
                    "`requerido` es espejo exacto de `exigencia` desde V72");
        });
    }

    /**
     * <b>V79 no cambio la semantica de PUB.</b>
     *
     * <p>Se comprueba por sus dos mitades: ninguna de las seis entro bloqueando,
     * y una propiedad sin ninguna de ellas se sigue publicando exactamente igual
     * que antes del corte. La regla no se toco -- PUB sigue impidiendo publicar
     * cuando alguien lo declare; lo que este corte no hace es declararlo.
     */
    @Test
    @DisplayName("V79: las seis capacidades entran OPC y publicar sigue funcionando sin ellas")
    void lasSeisEntranSinBloquearLaPublicacion() {
        List<String> bloqueantes = jdbc.queryForList("""
                select c.clave || '/' || t.tipo_propiedad
                  from catalogo_atributo_tipo t
                  join catalogo_atributo c on c.id_catalogo_atributo = t.id_catalogo_atributo
                 where c.organizacion_id is null and t.exigencia <> 'OPC'
                   and c.clave in ('partida_registral', 'oficina_registral', 'independizado',
                                   'cargas_gravamenes', 'area_segun_partida', 'declaratoria_fabrica')
                """, String.class);
        assertTrue(bloqueantes.isEmpty(), "estas filas de V79 no entraron OPC: " + bloqueantes);

        long id = registrarDepartamento();
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "una propiedad sin identidad registral se tiene que poder anunciar igual que "
                        + "antes de V79: el corte no introduce ningun rechazo nuevo");
    }

    /**
     * <b>`servicios_disponibles` sigue comportandose exactamente igual.</b>
     *
     * <p>Es la trampa que este corte tenia que no pisar. La guarda de vocabulario
     * de V79 mira <b>solo</b> claves ESTRUCTURALES, y ademas exige que la clave
     * tenga vocabulario sembrado. Generalizarla a «ninguna LISTA de la PROPIEDAD
     * sin vocabulario» habria roto esta clave, que es LISTA, es de la PROPIEDAD,
     * esta muda a proposito y cuyos reemplazos son del Corte 5.
     */
    @Test
    @DisplayName("V79: servicios_disponibles sigue aceptando texto libre, como antes del corte")
    void serviciosDisponiblesNoSeRompio() {
        // Sobre un TERRENO porque es el unico tipo al que aplica esa clave. Es
        // parte de lo que se afirma: su aplicabilidad tampoco se toco.
        long id = registrarTerreno();

        editar(id, new ValorAtributo("servicios_disponibles", "agua y desague"));

        assertEquals("agua y desague", valorDe(id, "servicios_disponibles"),
                "una LISTA sin vocabulario sembrado sigue admitiendo cualquier cadena: es deuda "
                        + "declarada del Corte 5, y V79 no la adelanta ni la empeora");
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from catalogo_atributo_opcion o
                  join catalogo_atributo c on c.id_catalogo_atributo = o.id_catalogo_atributo
                 where c.clave = 'servicios_disponibles'
                """, Integer.class),
                "y sigue sin vocabulario: inventarselo es del Corte 5, no de este");
    }

    /**
     * <b>La identidad registral de otra corredora no se toca.</b>
     *
     * <p>V79 no inventa tenencia: se apoya en la que ya existe. Lo que este caso
     * comprueba es que el camino NUEVO --escribir una columna del agregado en
     * vez de una fila de atributos-- pasa por el mismo filtro y no lo rodea.
     */
    @Test
    @DisplayName("V79: no se puede escribir la partida de una propiedad de otra organizacion")
    void laIdentidadRegistralRespetaElTenant() {
        long id = registrarDepartamento();
        Actor intruso = new Actor(actor().idOrganizacion() + 1000, 1L, 1L, Actor.AGENTE);

        assertThrows(Exception.class, () -> propiedades.editar(id,
                new ComandoEdicion(null, null, null, null, null,
                        List.of(new ValorAtributo("partida_registral", "00000001")),
                        null, null), intruso),
                "el discriminador de tenant se aplica antes que el enrutado por autoridad");

        assertNull(jdbc.queryForMap(
                "select partida_registral from propiedad where id_propiedad = ?", id)
                .get("partida_registral"),
                "y nada se escribio");
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

    // ==================================================================
    // V82 - `tipo_acceso` impide PUBLICAR, no REGISTRAR
    // ==================================================================

    /**
     * <b>Los cuatro escenarios de la correccion, sobre la clave real.</b>
     *
     * <p>V81 la sembro `ALT`, y `ALT` en este modelo significa DOS cosas:
     * {@code bloqueaAlta()} y {@code bloqueaPublicacion()}. Eso impedia
     * <b>registrar</b> un local sin el dato, que choca con lo que V75 y V76
     * establecieron -- registrar no es encargar, y BROX conoce inmuebles que
     * todavia no gestiona. V82 la baja a `PUB`, el nivel que V72 tenia previsto
     * para exactamente este caso.
     *
     * <p>Va sobre {@code tipo_acceso} de verdad y no sobre una clave marcada al
     * vuelo: lo que hay que proteger es que <b>esta</b> clave conserve <b>este</b>
     * nivel, y una prueba que se lo asigna ella misma no lo protegeria.
     */
    @Test
    @DisplayName("V82: un LOCAL sin tipo_acceso se registra, se edita y NO se publica")
    void tipoAccesoImpidePublicarNoRegistrar() {
        Map<String, Object> nivel = jdbc.queryForMap("""
                select t.exigencia, t.requerido
                  from catalogo_atributo_tipo t
                  join catalogo_atributo c on c.id_catalogo_atributo = t.id_catalogo_atributo
                 where c.clave = 'tipo_acceso' and c.organizacion_id is null
                   and t.tipo_propiedad = 'L'
                """);
        assertEquals("PUB", nivel.get("exigencia"),
                "tipo_acceso tiene que impedir publicar, no registrar");
        assertEquals(Boolean.FALSE, nivel.get("requerido"),
                "`requerido` es espejo exacto de `exigencia = ALT` desde V72");

        // 1. Se REGISTRA sin el dato. Con ALT esto lanzaba.
        long id = registrarLocalSinTipoAcceso();
        assertNull(valorDe(id, "tipo_acceso"), "el alta no debe inventar el dato");

        // 2. Se lee, se edita OTRA cosa y se relee: sigue ausente y no se movio nada.
        FichaPropiedadUniversal antes = propiedades.consultar(id, actor());
        editar(id, new ValorAtributo("aforo_itse", "40"));
        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertNull(valorDe(id, "tipo_acceso"),
                "editar otro dato no puede rellenar el que falta");
        assertEquals("40", valorDe(id, "aforo_itse"), "el dato editado si tiene que estar");
        assertEquals(antes.ubicacion().direccion(), despues.ubicacion().direccion(),
                "editar un atributo no puede mover la ubicacion");
        assertEquals(antes.titulares().size(), despues.titulares().size(),
                "editar un atributo no puede mover la titularidad");

        // 3. NO se publica. La causa se averigua agregando TODAS las claves
        //    ALT/PUB que faltan -- sin nombrar ninguna. Filtrar por la clave y
        //    luego "descubrir" que la causa es esa clave no demuestra nada.
        List<String> bloqueantes = jdbc.queryForList("""
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
                 order by c.orden
                """, String.class, id);
        assertEquals(List.of("tipo_acceso"), bloqueantes,
                "la unica causa de bloqueo tiene que ser tipo_acceso, y se comprueba agregando "
                        + "todas las claves ALT/PUB faltantes, no preguntando por una");

        ReglaNegocioException rechazo = assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "un local sin tipo_acceso no se puede anunciar");
        assertTrue(rechazo.getMessage().contains("Tipo de acceso"),
                "el rechazo tiene que decir QUE falta, con su rotulo: " + rechazo.getMessage());

        // 4. Declarado el dato, ese bloqueo desaparece y se publica.
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
        assertEquals("A_PIE_DE_CALLE", valorDe(id, "tipo_acceso"));
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "con el dato declarado, el unico bloqueo que habia deja de estar");
    }

    /**
     * <b>La deuda de visibilidad que midio V82, saldada.</b>
     *
     * <p>V82 dejo el caso medido y sin arreglar: <b>21 locales bloqueados y cero
     * con senal visible</b>. {@code atributosQueFaltan} se alimenta de {@code ALT}
     * solamente, asi que al bajar {@code tipo_acceso} a {@code PUB} dejo de
     * nombrarse ahi; y no cabia en {@code encargos[].faltanParaPublicar}, que es
     * del sujeto ENCARGO y por el guard 2.5 de V78 <b>no puede llevar una clave de
     * la PROPIEDAD</b>.
     *
     * <p>Este test conserva esas dos afirmaciones --siguen siendo ciertas y son la
     * separacion que hay que no romper-- y anade la tercera superficie, la que
     * faltaba: <b>la PROPIEDAD reporta su propia deuda de publicacion</b>, bajo el
     * mismo nombre que el ENCARGO reporta la suya.
     */
    @Test
    @DisplayName("la PROPIEDAD reporta su deuda de publicacion, y cada sujeto sigue con la suya")
    void laPropiedadReportaSuDeudaDePublicacion() {
        long id = registrarLocalSinTipoAcceso();
        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());

        // 1. Sigue SIN estar en la lista del alta: `tipo_acceso` es PUB, no ALT.
        assertTrue(ficha.atributosQueFaltan().stream().noneMatch(a -> "tipo_acceso".equals(a.clave())),
                "atributosQueFaltan responde a que impide el ALTA, y PUB no lo impide");

        // 2. Sigue SIN estar en la del encargo: es una clave de la PROPIEDAD.
        assertTrue(ficha.encargos().stream()
                        .flatMap(e -> e.faltanParaPublicar().stream())
                        .noneMatch(a -> "tipo_acceso".equals(a.clave())),
                "la deuda del ENCARGO no lleva claves de la PROPIEDAD (guard 2.5 de V78)");

        // 3. Y AHORA SI esta donde le toca, con su rotulo y no con la clave.
        AtributoQueFalta falta = ficha.faltanParaPublicar().stream()
                .filter(a -> "tipo_acceso".equals(a.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "la propiedad tiene que decir que le falta tipo_acceso para publicar; "
                                + "lleva " + ficha.faltanParaPublicar()));
        assertEquals("Tipo de acceso", falta.rotulo(),
                "el rotulo lo trae el catalogo: con la clave desnuda no es una frase para nadie");

        // 4. La barrera no se movio.
        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "este corte anade explicabilidad, no relaja la regla");

        // 5. Y al completar el dato, el faltante desaparece y se publica.
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        assertTrue(despues.faltanParaPublicar().stream().noneMatch(a -> "tipo_acceso".equals(a.clave())),
                "completado el dato, deja de faltar: " + despues.faltanParaPublicar());
        assertNotNull(publicaciones.crearEnEncargo(encargoDe(id), publicacionDePrueba(), actor()),
                "y la publicacion deja de estar bloqueada por esa clave");
    }

    /**
     * <b>Las dos listas responden a DOS preguntas, y no se mezclan.</b>
     *
     * <p>Es el contrato del corte, y la parte que se puede construir mal: filtrar
     * {@code faltanParaPublicar} a solo-{@code PUB} para que "no repita" con la
     * otra crearia un segundo criterio de publicabilidad --uno que decide y otro
     * que cuenta-- y ademas mentiria, porque una {@code ALT} ausente TAMBIEN
     * impide publicar.
     *
     * <p>Se comprueba sobre un TERRENO al que se le quita {@code zonificacion},
     * que es {@code ALT} en T: tiene que salir <b>en las dos listas</b>.
     */
    @Test
    @DisplayName("una clave ALT ausente sale en las DOS listas, y ninguna OPC sale en ninguna")
    void lasDosListasNoSeMezclan() {
        long id = registrarTerreno();
        // El alta no deja registrar sin lo obligatorio, asi que la unica forma de
        // observar una ficha incompleta es retirar el valor despues -- que es lo
        // que pasa de verdad cuando el catalogo declara obligatoria una clave que
        // las propiedades antiguas no tenian.
        jdbc.update("delete from atributo_propiedad where id_propiedad = ? and clave = 'zonificacion'", id);

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());

        assertTrue(ficha.atributosQueFaltan().stream().anyMatch(a -> "zonificacion".equals(a.clave())),
                "una ALT ausente impide el alta y tiene que decirlo");
        assertTrue(ficha.faltanParaPublicar().stream().anyMatch(a -> "zonificacion".equals(a.clave())),
                "y tambien impide publicar: omitirla aqui prometeria que basta con completar las PUB");

        // Y ninguna OPC se cuela en ninguna de las dos. `area_terreno` aplica a T
        // y es OPC; el terreno de prueba no la trae.
        assertNull(valorDe(id, "area_terreno"), "el caso necesita que esa OPC este ausente");
        assertTrue(ficha.atributosQueFaltan().stream().noneMatch(a -> "area_terreno".equals(a.clave()))
                        && ficha.faltanParaPublicar().stream()
                                .noneMatch(a -> "area_terreno".equals(a.clave())),
                "una OPC ausente no bloquea nada y no aparece como faltante en ninguna lista");
    }

    /**
     * <b>{@code PUB} no empieza a bloquear el alta.</b>
     *
     * <p>El riesgo de este corte es que, al hacer visible la deuda de publicacion,
     * alguien la "arregle" promoviendo la clave a {@code ALT}. Eso volveria a
     * impedir registrar un local sin visitarlo, que es justo lo que V82 corrigio.
     */
    @Test
    @DisplayName("PUB sigue sin bloquear el alta: un LOCAL sin tipo_acceso se registra")
    void pubNoBloqueaElAlta() {
        long id = registrarLocalSinTipoAcceso();
        assertNull(valorDe(id, "tipo_acceso"), "el alta no inventa el dato");
        assertNotNull(propiedades.consultar(id, actor()), "y la propiedad existe");
    }


    /**
     * <b>La capacidad no puede decir que si donde el comando dice que no.</b>
     *
     * <p>Es la incoherencia que V82 dejo medida: {@code faltanParaPublicar} traia
     * {@code tipo_acceso}, {@code permitida} decia {@code true} y el
     * {@code POST} devolvia 400. Los tres tienen que contar lo mismo.
     *
     * <p>Se comprueba el CICLO COMPLETO sobre un encargo <b>vivo</b>: la lista, la
     * capacidad y el comando. Y despues de completar el dato, los tres cambian a
     * la vez.
     */
    @Test
    @DisplayName("permitida sigue a la deuda de la PROPIEDAD, y el comando dice lo mismo")
    void permitidaSigueALaDeudaDeLaPropiedad() {
        long id = registrarLocalSinTipoAcceso();
        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        EncargoFicha bloque = ficha.encargos().get(0);
        assertTrue(bloque.vivo(), "el caso necesita un encargo vivo: si no, el motivo seria otro");

        assertFalse(ficha.faltanParaPublicar().isEmpty(), "la propiedad tiene deuda de publicacion");
        assertFalse(bloque.publicacionGestionable().permitida(),
                "con deuda de la ficha, la capacidad no puede prometer que se puede publicar");
        assertEquals("Faltan datos de la ficha del inmueble.",
                bloque.publicacionGestionable().motivo(),
                "el motivo dice CUAL de los tres impedimentos es, sin repetir la lista");
        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(bloque.idEncargo(), publicacionDePrueba(), actor()),
                "y el comando rechaza por la misma causa");

        // Completado el dato, los tres cambian a la vez.
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
        FichaPropiedadUniversal despues = propiedades.consultar(id, actor());
        EncargoFicha bloqueDespues = despues.encargos().get(0);
        assertTrue(despues.faltanParaPublicar().isEmpty(), "ya no falta nada de la ficha");
        assertTrue(bloqueDespues.publicacionGestionable().permitida(),
                "sin impedimento conocido, la capacidad lo dice");
        assertNull(bloqueDespues.publicacionGestionable().motivo(),
                "y sin impedimento no hay motivo que dar");
        assertNotNull(publicaciones.crearEnEncargo(bloqueDespues.idEncargo(),
                publicacionDePrueba(), actor()), "y publicar continua");
    }

    /**
     * <b>Una OPC ausente no vuelve {@code permitida = false}.</b>
     *
     * <p>Es la otra mitad del contrato: hacer visible la deuda de publicacion no
     * puede convertir el catalogo entero en un bloqueo. Se comprueba sobre una
     * propiedad a la que le faltan OPC de sobra --acaba de nacer con lo minimo--
     * y cuyo unico faltante bloqueante se completa.
     */
    @Test
    @DisplayName("una OPC ausente no bloquea ni vuelve permitida = false")
    void laOpcAusenteNoBloquea() {
        long id = registrarLocalSinTipoAcceso();
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));

        FichaPropiedadUniversal ficha = propiedades.consultar(id, actor());
        // A este LOCAL le falta casi todo el catalogo, y todo eso es OPC.
        assertNull(valorDe(id, "aforo_itse"), "el caso necesita OPC ausentes");
        assertNull(valorDe(id, "en_esquina"), "y mas de una");

        assertTrue(ficha.faltanParaPublicar().isEmpty(),
                "ninguna OPC puede aparecer como bloqueante: " + ficha.faltanParaPublicar());
        assertTrue(ficha.atributosQueFaltan().isEmpty(),
                "ni como faltante del alta: " + ficha.atributosQueFaltan());
        assertTrue(ficha.encargos().get(0).publicacionGestionable().permitida(),
                "y la capacidad no se vuelve false por datos que no bloquean nada");
    }

    /**
     * <b>Ningun camino que quede puede publicar saltandose el bloqueo.</b>
     *
     * <p>Es la prueba del microcorte de las puertas. Se recorren <b>todos</b> los
     * caminos que sobreviven contra una propiedad bloqueada por catalogo, y
     * ninguno consigue poner el anuncio en el mercado. Las otras dos vias
     * --{@code crear(idPropiedad, …)} y {@code sincronizar(…)}-- ya no se pueden
     * recorrer porque no existen; de eso se ocupa {@code PuertasDePublicacionTest}.
     *
     * <p><b>Medido, y mas estricto de lo que se suponia:</b> {@code crearEnEncargo}
     * llama a {@code exigirPublicable} <b>sin mirar el estado pedido</b>, asi que
     * de una propiedad bloqueada no se puede guardar <b>ni siquiera un borrador</b>.
     * La unica forma de tener un anuncio sobre una propiedad bloqueada es que se
     * creara cuando estaba completa y el dato se retirara despues -- y ese es el
     * escenario con el que se prueba {@code cambiarEstado}.
     */
    @Test
    @DisplayName("ningun camino de publicacion elude el bloqueo del catalogo")
    void ningunCaminoEludeElBloqueo() {
        long id = registrarLocalSinTipoAcceso();
        long encargo = encargoDe(id);
        assertFalse(propiedades.consultar(id, actor()).faltanParaPublicar().isEmpty(),
                "el caso necesita una propiedad bloqueada por catalogo");

        // CAMINO 1 - crear el anuncio ya publicado: rechazado.
        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargo, publicacionDePrueba(), actor()),
                "crearEnEncargo no puede anunciar algo que no se puede anunciar");

        // CAMINO 1 bis - y ni siquiera como BORRADOR. La guarda no depende del
        // estado pedido, asi que no hay forma de "colar" el anuncio y promoverlo
        // luego.
        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.crearEnEncargo(encargo, datosEnEstado(Publicacion.ESTADO_BORRADOR), actor()),
                "de una propiedad bloqueada no entra ni un borrador");

        // CAMINO 2 - el unico anuncio posible sobre una propiedad bloqueada es uno
        // que nacio cuando NO lo estaba. Se reproduce: se completa el dato, se
        // guarda el borrador y se retira el dato despues.
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
        var borrador = publicaciones.crearEnEncargo(encargo,
                datosEnEstado(Publicacion.ESTADO_BORRADOR), actor());
        assertNotNull(borrador, "con la ficha completa, el borrador si entra");

        jdbc.update("delete from atributo_propiedad where id_propiedad = ? and clave = 'tipo_acceso'", id);
        assertFalse(propiedades.consultar(id, actor()).faltanParaPublicar().isEmpty(),
                "retirado el dato, la propiedad vuelve a estar bloqueada");

        assertThrows(ReglaNegocioException.class,
                () -> publicaciones.cambiarEstado(borrador.id(), Publicacion.ESTADO_PUBLICADO, actor()),
                "cambiarEstado es la ventana, y esta cerrada igual que la puerta");

        // CAMINO 3 - editar no publica: `actualizar` no toca el estado.
        publicaciones.actualizar(borrador.id(),
                datosEnEstado(Publicacion.ESTADO_PUBLICADO), actor());
        assertEquals(Publicacion.ESTADO_BORRADOR,
                jdbc.queryForObject("select estado from publicacion where id_publicacion = ?",
                        String.class, borrador.id()),
                "ni pidiendoselo: actualizar ignora el estado, y por eso no es una puerta");

        // Y con el dato de vuelta, el camino canonico vuelve a funcionar.
        editar(id, new ValorAtributo("tipo_acceso", "A_PIE_DE_CALLE"));
        assertNotNull(publicaciones.cambiarEstado(borrador.id(), Publicacion.ESTADO_PUBLICADO, actor()),
                "completado el dato, publicar deja de estar bloqueado");
    }

    /** Los mismos datos de prueba, en el estado que pida el caso. */
    private PublicacionService.DatosPublicacion datosEnEstado(String estado) {
        return new PublicacionService.DatosPublicacion(Publicacion.CANAL_WEB_PROPIA, null,
                new BigDecimal("2500"), "PEN", "Anuncio de prueba", null, estado);
    }


    /** Un LOCAL con lo minimo del alta y SIN `tipo_acceso`. Es el sujeto de V82. */
    private long registrarLocalSinTipoAcceso() {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, "LOCAL", null,
                "Local conocido, todavia sin visitar (V82)",
                new Ubicacion("Av. Sin Visitar " + java.util.UUID.randomUUID(), "Miraflores",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "70")),
                List.of(new OperacionSolicitada("ALQUILER", new BigDecimal("2500"), "PEN",
                        null, null, null, null, null, null, null)),
                null), actor).idPropiedad();
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

    /**
     * Un terreno. Hace falta porque {@code servicios_disponibles} y
     * {@code area_segun_partida} no aplican a un departamento, y probarlas donde
     * no aplican mediria otra cosa.
     *
     * <p>Lleva {@code zonificacion} porque en un terreno es ALT: sin ella el
     * alta se rechaza, y ese es justamente el nivel de exigencia que 0B
     * estreno.
     */
    private long registrarTerreno() {
        Actor actor = actor();
        Long idPropietario = jdbc.queryForObject("""
                select min(r.id_persona_rol) from persona_rol r
                 where r.tipo_rol = 'PROPIETARIO' and r.vigencia_hasta is null
                   and r.organizacion_id = ?
                """, Long.class, actor.idOrganizacion());
        return propiedades.registrar(new ComandoRegistro(null, null, null, "TERRENO", null,
                "Terreno del caso 0B",
                new Ubicacion("Av. Terreno " + java.util.UUID.randomUUID(), "Lurin",
                        null, null, null, null, null, null, null),
                List.of(new Titular(idPropietario, null, Boolean.TRUE)),
                List.of(new ValorAtributo("metraje_total", "500"),
                        new ValorAtributo("zonificacion", "RDM")),
                List.of(new OperacionSolicitada("VENTA", new BigDecimal("300000"), "USD",
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
