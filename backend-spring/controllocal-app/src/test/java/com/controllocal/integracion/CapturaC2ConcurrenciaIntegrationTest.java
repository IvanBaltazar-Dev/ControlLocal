package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.Avance;
import com.controllocal.service.captura.MotorDeCaptura.Respuesta;
import com.controllocal.service.captura.PreparacionDeCaptura;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.Contexto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** T4 C2: transacciones y constraints PostgreSQL reales, fuente TEST propia por caso. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.maximum-pool-size=8")
class CapturaC2ConcurrenciaIntegrationTest {
    private static final Procedencia PANTALLA = Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry propiedades) {
        BaseDeDatosDePruebas.registrar(propiedades);
    }
    @Autowired MotorDeCaptura captura;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean EventoDominioRepository eventos;
    @MockitoSpyBean PreparacionDeCaptura preparacion;
    private Actor actor;

    @BeforeEach void fixturePropio() {
        String referencia = UUID.randomUUID().toString().substring(0, 12);
        long organizacion = jdbc.queryForObject("""
                insert into organizacion(codigo,nombre,estado,modalidad) values(?,?,'A','INDIVIDUAL') returning id_organizacion
                """, Long.class, "TEST-C2-CAP-" + referencia, "TEST captura concurrente " + referencia);
        long persona = jdbc.queryForObject("""
                insert into persona(organizacion_id,tipo_persona,tipo_documento,numero_documento,
                                    nombres_o_razon_social,correo,estado)
                values(?,'N','D',?,?,?,'A') returning id_persona
                """, Long.class, organizacion, "78" + String.format("%06d", organizacion % 1000000),
                "TEST profesional de captura", "captura-" + referencia + "@c2.test");
        long rol = jdbc.queryForObject("""
                insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
                values(?,?,'AGENTE',current_date) returning id_persona_rol
                """, Long.class, organizacion, persona);
        jdbc.update("""
                insert into detalle_agente(id_persona_rol,organizacion_id,codigo_agente,fecha_ingreso,estado_operativo)
                values(?,?,?,current_date,'D')
                """, rol, organizacion, "TEST-C2-" + rol);
        actor = new Actor(organizacion, persona, rol, Actor.AGENTE);
    }

    @AfterEach void retirarSabotajes() { reset(eventos, preparacion); }

    @Test void dosAvancesConLaMismaVersionProducenUnEfectoYUnConflicto() throws Exception {
        var inicial = abrir(Map.of("tipoPropiedad", Respuesta.conocida("DEPARTAMENTO"),
                "distrito", Respuesta.conocida("Surco")), null);
        int eventosAntes = eventosDe(inicial.idBorrador());
        List<Callable<Object>> operaciones = List.of(
                () -> intentar(new Avance(null, inicial.idBorrador(), inicial.version(),
                        Map.of("direccion", Respuesta.conocida("Dirección TEST A")), null)),
                () -> intentar(new Avance(null, inicial.idBorrador(), inicial.version(),
                        Map.of("direccion", Respuesta.conocida("Dirección TEST B")), null)));
        var resultados = competir(operaciones);
        assertEquals(1, resultados.stream().filter(MotorDeCaptura.EstadoCaptura.class::isInstance).count(), resultados.toString());
        assertEquals(1, resultados.stream().filter(CapturaC2ConcurrenciaIntegrationTest::esConflicto).count(), resultados.toString());
        var persistido = captura.consultar(inicial.idBorrador(), actor);
        assertEquals(inicial.version() + 1, persistido.version());
        assertTrue(Set.of("Dirección TEST A", "Dirección TEST B").contains(persistido.conocido().get("direccion")));
        assertEquals(eventosAntes + 1, eventosDe(inicial.idBorrador()));
    }

    @Test void aperturasConcurrentesReservanCodigosCapUnicosEnLaMismaOrganizacion() throws Exception {
        List<Callable<MotorDeCaptura.EstadoCaptura>> operaciones = new ArrayList<>();
        for (int i = 0; i < 8; i++) operaciones.add(() -> abrir(Map.of(), null));
        var resultados = competir(operaciones);
        assertEquals(8, new HashSet<>(resultados.stream().map(MotorDeCaptura.EstadoCaptura::idBorrador).toList()).size());
        assertEquals(8, new HashSet<>(resultados.stream().map(MotorDeCaptura.EstadoCaptura::codigo).toList()).size());
        assertTrue(resultados.stream().allMatch(e -> e.codigo().matches("CAP-[0-9]{5}")));
        assertEquals(8, jdbc.queryForObject("select count(*) from borrador_captura where organizacion_id=?", Integer.class, actor.idOrganizacion()));
        assertEquals(8, jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='CAPTURA_AVANZADA'", Integer.class, actor.idOrganizacion()));
    }

    @Test void contextoYMetadatosHacenRoundtripTextSinPerderFuenteConceptoEstadoOVersion() {
        long version = jdbc.queryForObject("select version_definicion from catalogo_atributo where organizacion_id is null and clave='metraje_total'", Long.class);
        var contexto = new Contexto("DEPARTAMENTO", "VIVIENDA", "Unidad TEST 501", "VENTA",
                Map.of(SuficienciaInmueble.UNIDAD_DE_EDIFICIO, SuficienciaInmueble.Aplicabilidad.APLICA), Set.of(), "JURISDICCION-TEST");
        var respuesta = new Respuesta("80", EstadoDato.CONOCIDO, "doc-test:Área \"A\" & sección 2",
                SuficienciaInmueble.AREA_TOTAL, null, Long.toString(version), LocalDate.of(2026, 10, 27), false);
        var inicial = abrir(Map.of("distrito", Respuesta.conocida("Surco"), "interiorUnidad", Respuesta.conocida("501"),
                "direccion", Respuesta.conocida("Av. TEST 123"), "metraje_total", respuesta), contexto);
        var leido = captura.consultar(inicial.idBorrador(), actor);
        assertEquals(inicial.contexto(), leido.contexto());
        assertEquals(inicial.metadatos(), leido.metadatos());
        assertEquals("80", leido.conocido().get("metraje_total"));
        assertEquals(respuesta.evidenciaRef(), leido.metadatos().get("metraje_total").evidenciaRef());
        assertEquals(respuesta.observadoEn(), leido.metadatos().get("metraje_total").observadoEn());
        assertEquals("text", jdbc.queryForObject("select pg_typeof(contexto_captura)::text from borrador_captura where id_borrador=?", String.class, inicial.idBorrador()));
        assertEquals("text", jdbc.queryForObject("select pg_typeof(metadatos_respuestas)::text from borrador_captura where id_borrador=?", String.class, inicial.idBorrador()));
        assertTrue(leido.suficiencia().suficiente());
        assertFalse(leido.suficienciasEncargo().getFirst().suficiente(), "Identificar no acredita mínimos ni revisión del Encargo.");
    }

    @Test void falloDeEventoRevierteValorMetadatosContextoYVersionJuntos() {
        var inicial = abrir(Map.of("tipoPropiedad", Respuesta.conocida("DEPARTAMENTO"),
                "distrito", Respuesta.conocida("Surco")), null);
        int eventosAntes = eventosDe(inicial.idBorrador());
        doThrow(new IllegalStateException("TEST fallo de persistencia del evento")).when(eventos).save(any());
        assertThrows(IllegalStateException.class, () -> captura.avanzar(new Avance(null, inicial.idBorrador(), inicial.version(),
                Map.of("direccion", new Respuesta("Av. TEST revertida", EstadoDato.CONOCIDO,
                        "doc-test:revertido", null, null, null, LocalDate.of(2026, 10, 27), false)),
                new Contexto("DEPARTAMENTO", "VIVIENDA", "Unidad TEST revertida", null, Map.of(), Set.of())), PANTALLA, actor));
        reset(eventos);
        var despues = captura.consultar(inicial.idBorrador(), actor);
        assertEquals(inicial.conocido(), despues.conocido());
        assertEquals(inicial.metadatos(), despues.metadatos());
        assertEquals(inicial.contexto(), despues.contexto());
        assertEquals(inicial.version(), despues.version());
        assertEquals(eventosAntes, eventosDe(inicial.idBorrador()));
    }

    @Test void falloDeRecompensaNoRevierteElAvanceYaGuardadoNiLoDuplicaAlConsultar() {
        var inicial = abrir(Map.of("tipoPropiedad", Respuesta.conocida("DEPARTAMENTO")), null);
        int eventosAntes = eventosDe(inicial.idBorrador());
        doThrow(new IllegalStateException("TEST preparación no disponible")).when(preparacion)
                .preparar(anyMap(), anyMap(), any(), anyList(), any(), anyBoolean());
        var avance = captura.avanzar(new Avance(null, inicial.idBorrador(), inicial.version(),
                Map.of("distrito", Respuesta.conocida("Surco")), null), PANTALLA, actor);
        assertEquals("FALLO_TECNICO", avance.retorno().estado());
        reset(preparacion);
        var despues = captura.consultar(inicial.idBorrador(), actor);
        assertEquals("Surco", despues.conocido().get("distrito"));
        assertEquals(inicial.version() + 1, despues.version());
        assertEquals(eventosAntes + 1, eventosDe(inicial.idBorrador()));
        assertEquals("DISPONIBLE", despues.retorno().estado());
    }

    private MotorDeCaptura.EstadoCaptura abrir(Map<String, Respuesta> respuestas, Contexto contexto) {
        return captura.avanzar(new Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD, null, null, respuestas, contexto), PANTALLA, actor);
    }
    private Object intentar(Avance avance) {
        try { return captura.avanzar(avance, PANTALLA, actor); }
        catch (RuntimeException fallo) { return fallo; }
    }
    private int eventosDe(long idBorrador) {
        return jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='BORRADOR_CAPTURA' and entidad_id=? and tipo='CAPTURA_AVANZADA'",
                Integer.class, actor.idOrganizacion(), idBorrador);
    }
    private static boolean esConflicto(Object resultado) {
        return resultado instanceof ConflictoException || resultado instanceof OptimisticLockingFailureException;
    }
    private static <T> List<T> competir(List<Callable<T>> operaciones) throws Exception {
        var hilos = Executors.newFixedThreadPool(Math.min(operaciones.size(), 4));
        CountDownLatch preparados = new CountDownLatch(Math.min(operaciones.size(), 4));
        CountDownLatch salida = new CountDownLatch(1);
        try {
            var futuros = operaciones.stream().map(operacion -> hilos.submit(() -> {
                preparados.countDown();
                if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("TEST barrera de salida agotada");
                return operacion.call();
            })).toList();
            assertTrue(preparados.await(10, TimeUnit.SECONDS), "Las transacciones deben competir realmente.");
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (var futuro : futuros) resultados.add(futuro.get(20, TimeUnit.SECONDS));
            return resultados;
        } finally { salida.countDown(); hilos.shutdownNow(); }
    }
}
