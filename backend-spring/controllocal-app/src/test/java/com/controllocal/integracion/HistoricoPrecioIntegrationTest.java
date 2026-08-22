package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E0 — el histórico económico contra PostgreSQL real.
 *
 * <p>Cubre lo que los tests de service con mocks <b>no pueden</b> cubrir: la
 * semántica del SQL y el acuerdo entre el vocabulario del dominio y el CHECK de
 * la tabla. Las reglas de negocio (cuándo se escribe cada hito, cuándo se
 * deduplica) viven en {@code LocalComercialServiceImplTest} y
 * {@code PublicacionServiceImplTest}; aquí se prueba el suelo.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HistoricoPrecioIntegrationTest {

    private static final Pattern CODIGO_SQL = Pattern.compile("'([A-Z])'");

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }

    @Autowired
    private PrecioPropiedadRepository precios;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Sin vaciar el contexto, {@code findFirst...} devuelve la MISMA instancia
     * que se acaba de guardar y el test no prueba nada del ida y vuelta a
     * Postgres: la escala vuelve tal como se escribió, no como la almacena la
     * columna. Costó un fallo del gate descubrirlo.
     */
    @PersistenceContext
    private EntityManager em;

    private void releerDesdeLaBase() {
        em.flush();
        em.clear();
    }

    /**
     * El CHECK de la tabla y el vocabulario del dominio tienen que decir lo
     * mismo, en las dos direcciones.
     *
     * <p>Este test existe por un incidente real: V40 estrechó tres columnas
     * {@code estado} y dejó fuera un valor que un productor seguía escribiendo;
     * ni javac ni Hibernate leen un CHECK, así que el fallo apareció en runtime
     * como un 409 y tumbó dos suites enteras. El productor de {@code P} que
     * añade E0.2 depende exactamente de eso: si alguien estrecha
     * {@code ck_precio_hito} sin mirar, publicar una renta empieza a reventar y
     * nada lo avisa hasta que una operación real falla.
     */
    @Test
    void elVocabularioDeHitosDelDominioYElCheckDeLaTablaCoinciden() {
        String definicion = jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint "
                        + "where conrelid = 'precio_propiedad'::regclass and conname = 'ck_precio_hito'",
                String.class);

        Set<String> enLaBase = new LinkedHashSet<>();
        Matcher m = CODIGO_SQL.matcher(definicion == null ? "" : definicion);
        while (m.find()) {
            enLaBase.add(m.group(1));
        }

        assertEquals(PrecioPropiedad.HITOS, enLaBase,
                "El CHECK de precio_propiedad.hito y PrecioPropiedad.HITOS divergieron");
        // Los tres que E0 produce automáticamente, nombrados: si desaparecen del
        // CHECK, el alta, la publicación o el cierre dejan de poder escribir.
        assertTrue(enLaBase.containsAll(Set.of(PrecioPropiedad.HITO_AUTORIZADO,
                PrecioPropiedad.HITO_PUBLICADO, "C")));
    }

    /**
     * La deduplicación de E0.2 pregunta por el ÚLTIMO {@code P} de la propiedad.
     * Varios hitos del mismo día son lo normal —una edición de precio y su
     * propagación a la publicación caen ambas hoy—, así que la fecha sola no
     * ordena nada: solo el id desempata en el orden real de escritura.
     *
     * <p>Un mock no puede probar esto porque el orden lo decide el SQL.
     */
    @Test
    @Transactional
    void elUltimoHitoSeResuelvePorIdCuandoLaFechaEmpata() {
        Encargo encargo = unEncargoVivo();
        Long idPropiedad = encargo.idPropiedad();

        LocalDate hoy = LocalDate.now();
        guardar(encargo, new BigDecimal("5200.00"), hoy);
        guardar(encargo, new BigDecimal("4900.00"), hoy);
        PrecioPropiedad esperado = guardar(encargo, new BigDecimal("4700.00"), hoy);
        releerDesdeLaBase();

        PrecioPropiedad ultimo = precios
                .findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                        idPropiedad, PrecioPropiedad.HITO_PUBLICADO)
                .orElseThrow();

        assertEquals(esperado.getId(), ultimo.getId());
        assertEquals(0, new BigDecimal("4700.00").compareTo(ultimo.getMonto()));
    }

    /**
     * `numeric(12,2)` normaliza la escala al persistir, así que un importe
     * guardado como 5200 vuelve como 5200.00. La deduplicación compara con
     * {@code compareTo} justo por esto: con {@code equals} de BigDecimal el
     * mismo precio se leería como distinto y cada sincronización duplicaría el
     * hito.
     */
    @Test
    @Transactional
    void laEscalaDelImporteNoConvierteElMismoPrecioEnUnoDistinto() {
        Encargo encargo = unEncargoVivo();

        // Se escribe SIN decimales; la columna es numeric(12,2).
        guardar(encargo, new BigDecimal("5200"), LocalDate.now());
        releerDesdeLaBase();

        BigDecimal leido = precios
                .findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                        encargo.idPropiedad(), PrecioPropiedad.HITO_PUBLICADO)
                .orElseThrow()
                .getMonto();

        BigDecimal entrante = new BigDecimal("5200.00");
        assertEquals(0, entrante.compareTo(leido),
                "compareTo debe ver el mismo precio pese a la escala");
        assertEquals(2, leido.scale(),
                "la columna normaliza a dos decimales: por eso equals no sirve para deduplicar");
    }

    /** La propiedad y el encargo del que cuelgan los hitos de esta prueba. */
    private record Encargo(Long idOrganizacion, Long idPropiedad, Long idCaptacion) { }

    /**
     * Un encargo vivo de verdad, y no la primera propiedad que aparezca.
     *
     * <p>Hasta V76 bastaba con `min(id_propiedad)`: un hito de precio podia
     * colgar de una propiedad sin encargo. Ya no, y con razon — un precio es un
     * hecho comercial y alguien tuvo que autorizarlo. Lo que se conoce del
     * mercado sin encargo detras va a `observacion_mercado`, que es una serie
     * distinta a proposito.
     */
    private Encargo unEncargoVivo() {
        return jdbc.queryForObject("""
                select c.organizacion_id, c.id_propiedad, c.id_captacion
                  from captacion c
                 where c.estado in ('P', 'O', 'A')
                 order by c.id_captacion
                 limit 1
                """,
                (fila, i) -> new Encargo(fila.getLong("organizacion_id"),
                        fila.getLong("id_propiedad"), fila.getLong("id_captacion")));
    }

    /**
     * <b>La operacion se declara.</b> Antes no hacia falta porque la entidad la
     * rellenaba con ALQUILER, y ese defecto se retiro en D-E4-1: la columna es
     * NOT NULL sin DEFAULT y ahora Java sostiene la misma exigencia. Estas
     * pruebas son de la serie de una renta, asi que dicen ALQUILER — que es
     * distinto de que alguien lo suponga por ellas.
     *
     * <p>Y desde V76 declara tambien <b>de que encargo</b> es el hito: sin eso
     * `tg_precio_exige_encargo` lo rechaza.
     */
    private PrecioPropiedad guardar(Encargo encargo, BigDecimal monto, LocalDate fecha) {
        return precios.saveAndFlush(PrecioPropiedad.hito(encargo.idOrganizacion(),
                        encargo.idPropiedad(), OperacionInmobiliaria.ALQUILER,
                        PrecioPropiedad.HITO_PUBLICADO, PrecioPropiedad.MONEDA_PEN, monto, fecha)
                .delEncargo(encargo.idCaptacion()));
    }
}
