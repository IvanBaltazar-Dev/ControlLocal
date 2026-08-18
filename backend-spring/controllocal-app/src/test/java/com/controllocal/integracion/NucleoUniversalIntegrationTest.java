package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.TitularidadPropiedad;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.TitularidadPropiedadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Las tres piezas del nucleo universal, contra PostgreSQL de verdad</b>
 * (D-E4-1, V46-V53).
 *
 * <p>Por que no valen mocks aqui: lo que se prueba son invariantes que impone
 * la BASE y consultas que solo tienen sentido contra su indice.
 *
 * <ul>
 *   <li>Las cuotas de titularidad las vigila un CONSTRAINT TRIGGER
 *       <b>diferido</b>: un mock aceptaria cualquier reparto, y lo que importa
 *       es que estalle al COMMIT y no antes -- si estallara antes, escribir una
 *       copropiedad de tres titulares seria imposible.</li>
 *   <li>El gobierno de atributos vive en un trigger que consulta el catalogo:
 *       "dormitorios en un local" solo se rechaza ejecutandolo.</li>
 *   <li>{@code clavesObligatoriasQueFaltan} es la consulta de la que depende el
 *       motor de captura para decir "me falta el metraje" en vez de fallar al
 *       guardar.</li>
 * </ul>
 *
 * <p>Cada prueba es {@code @Transactional}: escribe y deshace, asi que se puede
 * correr contra la base de desarrollo sin dejar rastro.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NucleoUniversalIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TitularidadPropiedadRepository titularidades;
    @Autowired CatalogoAtributoRepository catalogo;
    @Autowired AtributoPropiedadRepository atributos;
    @Autowired EventoDominioRepository eventos;

    private long unaPropiedad() {
        return jdbc.queryForObject("select min(id_propiedad) from propiedad", Long.class);
    }

    private long suOrganizacion(long idPropiedad) {
        return jdbc.queryForObject(
                "select organizacion_id from propiedad where id_propiedad = ?", Long.class, idPropiedad);
    }

    private String suTipo(long idPropiedad) {
        return jdbc.queryForObject(
                "select tipo_inmueble from propiedad where id_propiedad = ?", String.class, idPropiedad);
    }

    // ------------------------------------------------------------------
    // Titularidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("toda propiedad migrada tiene un titular vigente al 100 % y es su representante")
    void elBackfillDejoUnTitularPorPropiedad() {
        long idPropiedad = unaPropiedad();

        List<TitularidadPropiedad> vigentes = titularidades.vigentesDe(idPropiedad);

        assertEquals(1, vigentes.size(), "el backfill de V47 crea exactamente una titularidad");
        assertEquals(0, TitularidadPropiedad.CUOTA_TOTAL.compareTo(vigentes.get(0).getCuota()));
        assertTrue(vigentes.get(0).isEsRepresentante(), "con quien se habla tiene que estar declarado");
        assertTrue(titularidades.representanteDe(idPropiedad).isPresent());
    }

    @Test
    @Transactional
    @DisplayName("cerrar una titularidad conserva la historia en vez de borrarla")
    void cerrarNoBorra() {
        long idPropiedad = unaPropiedad();
        TitularidadPropiedad actual = titularidades.vigentesDe(idPropiedad).get(0);

        actual.cerrar(LocalDate.now(), "prueba de cierre");
        titularidades.saveAndFlush(actual);

        assertTrue(titularidades.vigentesDe(idPropiedad).isEmpty(), "deja de ser vigente");
        assertFalse(titularidades.historicoDe(idPropiedad).isEmpty(), "pero sigue en la historia");
        assertEquals(BigDecimal.ZERO.compareTo(titularidades.cuotaVigenteDe(idPropiedad)),
                0, "y su cuota deja de contar");
    }

    @Test
    @Transactional
    @DisplayName("una cuota que no completa 100 estalla al COMMIT, no antes")
    void lasCuotasTienenQueSumarCien() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);
        TitularidadPropiedad actual = titularidades.vigentesDe(idPropiedad).get(0);

        // Se reparte mal a proposito: 60 y nada mas.
        actual.setCuota(new BigDecimal("60.000"));
        titularidades.saveAndFlush(actual);

        // El trigger es DIFERIDO, asi que hasta aqui la BD no ha protestado: es
        // justo lo que permite escribir una copropiedad en varias sentencias.
        assertThrows(Exception.class, () -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"),
                "al exigir la comprobacion, el reparto incompleto tiene que caer");
    }

    @Test
    @Transactional
    @DisplayName("dos representantes vigentes sobre la misma propiedad se rechazan")
    void soloUnRepresentante() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);
        Long otroTitular = jdbc.queryForObject("""
                select min(pr.id_persona_rol) from persona_rol pr
                 where pr.tipo_rol = 'PROPIETARIO'
                   and pr.id_persona_rol <> (select id_rol_propietario from propiedad where id_propiedad = ?)
                """, Long.class, idPropiedad);
        org.junit.jupiter.api.Assumptions.assumeTrue(otroTitular != null,
                "hace falta un segundo propietario en la base para esta prueba");

        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbc.update("""
                    insert into titularidad_propiedad
                        (organizacion_id, id_propiedad, id_rol_propietario, cuota, es_representante, vigente_desde)
                    values (?, ?, ?, 50, true, current_date)
                    """, idOrganizacion, idPropiedad, otroTitular);
        });
    }

    // ------------------------------------------------------------------
    // Catalogo y atributos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el catalogo del sistema esta y sirve a cualquier organizacion")
    void elCatalogoDelSistemaEstaDisponible() {
        long idOrganizacion = suOrganizacion(unaPropiedad());

        List<CatalogoAtributo> disponibles = catalogo.disponiblesPara(idOrganizacion);

        assertTrue(disponibles.size() >= 19, "los 19 del sistema, como minimo");
        assertTrue(catalogo.porClave(idOrganizacion, "metraje_total").isPresent());
        assertTrue(catalogo.findByDelSistemaTrueOrderByOrdenAscClaveAsc().stream()
                .allMatch(c -> c.getOrganizacionId() == null),
                "los del sistema no pertenecen a ninguna organizacion");
    }

    @Test
    @DisplayName("un terreno no pregunta dormitorios, y un departamento si")
    void elCatalogoFiltraPorTipoDePropiedad() {
        long idOrganizacion = suOrganizacion(unaPropiedad());

        List<String> deTerreno = catalogo.aplicablesA(idOrganizacion, "T").stream()
                .map(CatalogoAtributo::getClave).toList();
        List<String> deDepartamento = catalogo.aplicablesA(idOrganizacion, "D").stream()
                .map(CatalogoAtributo::getClave).toList();

        assertFalse(deTerreno.contains("dormitorios"), "un terreno no tiene dormitorios");
        assertTrue(deTerreno.contains("zonificacion"), "pero si zonificacion");
        assertTrue(deDepartamento.contains("dormitorios"));
        assertFalse(deDepartamento.contains("altura_libre"), "eso es de almacen");
        assertTrue(deTerreno.contains("metraje_total") && deDepartamento.contains("metraje_total"),
                "lo comun aplica a los dos");
    }

    @Test
    @DisplayName("el backfill migro los valores que ya existian en columnas")
    void losAtributosSeMigraron() {
        long idPropiedad = unaPropiedad();

        // `metraje_total` YA NO deja fila aqui: D-E4-3 lo declaro ESTRUCTURAL y
        // V61 retiro sus copias, asi que su autoridad es `propiedad.metraje`.
        // Buscarlo en esta tabla era correcto hasta V60 y ahora seria exigir la
        // doble verdad que se acaba de cerrar.
        assertTrue(atributos.findByIdPropiedadAndClave(idPropiedad, "metraje_total").isEmpty(),
                "el metraje vive en su campo canonico, no como atributo");
        assertNotNull(jdbc.queryForObject(
                        "select metraje from propiedad where id_propiedad = ?", BigDecimal.class, idPropiedad),
                "ninguna propiedad puede haber perdido su metraje");
        assertFalse(atributos.findByIdPropiedadOrderByClaveAsc(idPropiedad).isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("un atributo que no aplica al tipo de la propiedad se rechaza")
    void elGobiernoDelCatalogoSeAplica() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);
        org.junit.jupiter.api.Assumptions.assumeTrue("L".equals(suTipo(idPropiedad)),
                "esta prueba asume que la propiedad de muestra es un local");

        AtributoPropiedad dormitorios =
                AtributoPropiedad.deNumero(idOrganizacion, idPropiedad, "dormitorios", new BigDecimal("3"));

        assertThrows(Exception.class, () -> atributos.saveAndFlush(dormitorios),
                "dormitorios no aplica a un local comercial");
    }

    @Test
    @Transactional
    @DisplayName("una clave que no esta en el catalogo se rechaza")
    void nadieInventaClaves() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);

        AtributoPropiedad inventado =
                AtributoPropiedad.deTexto(idOrganizacion, idPropiedad, "color_de_la_puerta", "verde");

        assertThrows(Exception.class, () -> atributos.saveAndFlush(inventado));
    }

    @Test
    @DisplayName("un atributo sin valor no se construye: se omite")
    void unAtributoVacioNoExiste() {
        assertThrows(IllegalArgumentException.class,
                () -> AtributoPropiedad.deTexto(1L, 1L, "rubro_permitido", null),
                "guardar nulos impediria distinguir 'no aplica' de 'no lo se'");
    }

    @Test
    @DisplayName("se sabe que le falta a una propiedad para poder publicarse")
    void seSabeQueFalta() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);

        List<String> faltan = atributos.clavesObligatoriasQueFaltan(
                idOrganizacion, idPropiedad, suTipo(idPropiedad));

        // El backfill dejo el metraje, que es el unico obligatorio de un local.
        assertFalse(faltan.contains("metraje_total"),
                "el metraje se migro, asi que no puede faltar");
    }

    // ------------------------------------------------------------------
    // Outbox
    // ------------------------------------------------------------------

    /**
     * Si ESE evento sigue sin proyectar, sin depender de cuantos haya delante.
     *
     * <p>Se pregunta a la TABLA y no a {@code findById}: {@code marcarProyectados}
     * es un update masivo de JPQL, que va directo a la base y NO refresca la
     * entidad que el contexto de persistencia ya tiene cargada. Preguntarle al
     * repositorio devolveria el objeto viejo, con {@code proyectadoEn} todavia a
     * null, y el test diria que el marcado no funciono cuando si funciono.
     *
     * <p>El JdbcTemplate comparte la transaccion del test, asi que ve el update
     * aunque no este confirmado.
     */
    private boolean pendiente(long idEvento) {
        Integer sinProyectar = jdbc.queryForObject(
                "select count(*) from evento_dominio where id_evento = ? and proyectado_en is null",
                Integer.class, idEvento);
        return sinProyectar != null && sinProyectar > 0;
    }

    @Test
    @Transactional
    @DisplayName("un evento se escribe, se lee como pendiente y se marca proyectado")
    void elOutboxGuardaYSeConsume() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);

        EventoDominio evento = EventoDominio
                .de(idOrganizacion, "PROPIEDAD_REGISTRADA", "PROPIEDAD", idPropiedad, null,
                        EventoDominio.CANAL_WHATSAPP)
                .con("{\"idPropiedad\":" + idPropiedad + "}");
        eventos.saveAndFlush(evento);

        assertTrue(evento.tieneRelaciones());

        // "Esta pendiente" se pregunta por SU id, no mirando si aparece en la
        // ventana de los 50 mas antiguos.
        //
        // Preguntarlo asi era un falso positivo con fecha de caducidad: la ventana
        // esta ordenada por id ASC a proposito -- un consumidor tiene que drenar el
        // outbox en orden -- asi que el evento recien escrito es el ULTIMO de la
        // cola. Mientras la tabla tuvo menos de 50 filas el test paso; en cuanto el
        // entorno acumulo 52 pendientes (nadie drena el outbox en dev), el evento
        // nuevo cayo fuera de la ventana y el test empezo a fallar por su propio
        // exito. No tenia nada que ver con lo que pretende probar.
        assertTrue(pendiente(evento.getId()), "recien escrito, esta pendiente de proyectar");

        // Y lo que la ventana SI garantiza, comprobado como lo que es: un tope y un
        // orden, para que el primer arranque de un consumidor no se traiga la tabla.
        List<EventoDominio> ventana = eventos.pendientesDeProyectar(Limit.of(50));
        assertTrue(ventana.size() <= 50, "la ventana respeta el tope que pidio el llamante");
        assertTrue(ventana.stream().allMatch(e -> e.getId() <= evento.getId()),
                "y devuelve los mas antiguos primero: el outbox se drena en orden");
        assertFalse(eventos.historiaDe("PROPIEDAD", idPropiedad).isEmpty());
        assertFalse(eventos.porCanal(idOrganizacion, EventoDominio.CANAL_WHATSAPP,
                        OffsetDateTime.now().minusHours(1)).isEmpty(),
                "se puede responder que entro por WhatsApp");

        int marcados = eventos.marcarProyectados(List.of(evento.getId()), OffsetDateTime.now());

        assertEquals(1, marcados);
        assertFalse(pendiente(evento.getId()), "ya proyectado, deja de estar pendiente");
    }

    @Test
    @Transactional
    @DisplayName("la carga util sigue siendo JSON valido aunque se guarde como texto")
    void laCargaUtilEsJson() {
        long idPropiedad = unaPropiedad();
        long idOrganizacion = suOrganizacion(idPropiedad);

        EventoDominio malo = EventoDominio
                .de(idOrganizacion, "PRUEBA", "PROPIEDAD", idPropiedad, null, EventoDominio.CANAL_API)
                .con("esto no es json");

        assertThrows(Exception.class, () -> eventos.saveAndFlush(malo),
                "el CHECK de V53 es lo que permite volver a jsonb con un cast");
    }

    @Test
    @DisplayName("un evento sin organizacion no se graba")
    void elOutboxRespetaElTenant() {
        EventoDominio sinTenant = EventoDominio
                .de(null, "PRUEBA", "PROPIEDAD", 1L, null, EventoDominio.CANAL_SPA);

        assertThrows(Exception.class, () -> eventos.saveAndFlush(sinTenant),
                "EntidadDeOrganizacion falla con un mensaje que dice QUE entidad venia sin tenant");
    }

    // ------------------------------------------------------------------
    // El agujero que `ddl-auto: validate` no tapa
    // ------------------------------------------------------------------

    /**
     * <b>Toda columna NOT NULL sin DEFAULT tiene que estar mapeada por su
     * entidad.</b>
     *
     * <p>Este test existe porque el mismo fallo entro <b>dos veces</b>:
     * <ul>
     *   <li>V49 dejo {@code precio_propiedad.operacion} NOT NULL y la entidad no
     *       la mapeaba: cualquier escritura de un hito de precio fallaba;</li>
     *   <li>V51 dejo {@code solicitud_alquiler.tipo} NOT NULL y paso exactamente
     *       lo mismo: <b>ningun expediente se podia crear</b>.</li>
     * </ul>
     *
     * <p><b>Y `ddl-auto: validate` no lo ve</b>, que es la parte que sorprende:
     * comprueba que las columnas que la entidad MAPEA existan en la tabla, no
     * que la entidad mapee todas las que la tabla EXIGE. Una columna obligatoria
     * y desconocida para JPA arranca perfectamente y revienta en el primer
     * INSERT — y como los tests de humo son GET, el fallo viaja hasta que
     * alguien escribe de verdad.
     *
     * <p>Se salta las columnas con DEFAULT (la base las rellena), las
     * generadas, y las tablas sin entidad — {@code condicion_compraventa} y
     * {@code flyway_schema_history} son legitimas: nadie las escribe por JPA
     * todavia.
     */
    @Test
    @DisplayName("ninguna columna obligatoria queda sin mapear en su entidad")
    void todaColumnaObligatoriaEstaMapeada() throws Exception {
        Map<String, String> fuentePorTabla = fuentesDeEntidadesPorTabla();
        List<String> huerfanas = new java.util.ArrayList<>();

        for (Map<String, Object> fila : jdbc.queryForList("""
                select c.table_name, c.column_name
                  from information_schema.columns c
                  join information_schema.tables t
                    on t.table_name = c.table_name and t.table_schema = c.table_schema
                 where c.table_schema = 'public'
                   and t.table_type = 'BASE TABLE'
                   and c.is_nullable = 'NO'
                   and c.column_default is null
                   and c.is_generated = 'NEVER'
                   and c.identity_generation is null
                 order by c.table_name, c.column_name
                """)) {
            String tabla = (String) fila.get("table_name");
            String columna = (String) fila.get("column_name");
            String fuente = fuentePorTabla.get(tabla);
            if (fuente == null) {
                continue;
            }
            boolean mapeada = fuente.contains("\"" + columna + "\"");
            if (!mapeada) {
                huerfanas.add(tabla + "." + columna);
            }
        }

        assertEquals(List.of(), huerfanas,
                "Estas columnas son NOT NULL sin DEFAULT y su entidad no las mapea: cualquier "
                        + "INSERT por JPA fallara. `ddl-auto: validate` no lo detecta porque solo "
                        + "mira las columnas que la entidad SI declara.");
    }

    /** Mapa {@code tabla -> codigo fuente de su entidad}, leyendo {@code @Table(name=...)}. */
    private static Map<String, String> fuentesDeEntidadesPorTabla() throws Exception {
        Path dominio = Path.of("..", "controllocal-domain", "src", "main", "java").toAbsolutePath()
                .normalize();
        Pattern tabla = Pattern.compile("@Table\\(name\\s*=\\s*\"([a-z_]+)\"");
        Map<String, String> porTabla = new java.util.LinkedHashMap<>();

        // Las columnas heredadas —`organizacion_id` de EntidadDeOrganizacion—
        // no estan en el fichero de la entidad, sino en su @MappedSuperclass.
        // Sin esto, el gate marcaria 48 tablas como sin mapear y solo estaria
        // diciendo que no sabe leer una herencia.
        StringBuilder heredado = new StringBuilder();
        try (Stream<Path> ficheros = Files.walk(dominio)) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(fichero, StandardCharsets.UTF_8);
                if (fuente.contains("@MappedSuperclass")) {
                    heredado.append(fuente);
                }
            }
        }

        try (Stream<Path> ficheros = Files.walk(dominio)) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(fichero, StandardCharsets.UTF_8);
                Matcher encontrada = tabla.matcher(fuente);
                if (encontrada.find()) {
                    // Una entidad y su @Embeddable pueden compartir tabla; se
                    // concatenan para que las columnas de los dos cuenten.
                    porTabla.merge(encontrada.group(1), fuente + heredado, (a, b) -> a + b);
                }
            }
        }
        // `catalogo_atributo_tipo` es una @CollectionTable, no una @Table: sus
        // columnas viven en AplicacionAtributo y en la @CollectionTable del
        // padre. Se junta a mano porque el patron de arriba no la ve.
        Path aplicacion = dominio.resolve(
                Path.of("com", "controllocal", "domain", "inmueble", "AplicacionAtributo.java"));
        if (Files.exists(aplicacion)) {
            porTabla.merge("catalogo_atributo_tipo",
                    Files.readString(aplicacion, StandardCharsets.UTF_8) + "\"id_catalogo_atributo\"",
                    (a, b) -> a + b);
        }
        return porTabla;
    }
}
