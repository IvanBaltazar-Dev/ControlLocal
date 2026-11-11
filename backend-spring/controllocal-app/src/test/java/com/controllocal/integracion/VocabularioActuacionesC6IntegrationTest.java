package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.capacidades.RegistroDeCapacidades;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Comprueba las expresiones PostgreSQL efectivamente instaladas, no una copia SQL del test. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class VocabularioActuacionesC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroDeCapacidades registro;

    private static final Set<String> C6=Set.of("publicar_anuncio","actualizar_anuncio_visible",
            "pausar_anuncio","reanudar_anuncio","finalizar_difusion_anuncio","obtener_pieza_para_difusion");

    @Test void capacidadYRecursoConcretoCoincidenConElRegistroHumanoCompleto() {
        Set<String> registradas=registro.todas().stream()
                .filter(d->d.recursos().contains("ACTUACION"))
                .filter(d->d.interaccion()==com.controllocal.service.capacidades.CapacidadesService.Interaccion.CONFIRMA
                        || "confirmar_captura".equals(d.idCore()))
                .map(RegistroDeCapacidades.Definicion::idCore).collect(Collectors.toCollection(LinkedHashSet::new));
        // La decisión del Encargo usa PETICION_ENCARGO para describir su recurso público.
        registradas.add(registro.de("decidir_captacion").idCore());
        assertTrue(registradas.containsAll(C6));
        assertEquals(registradas,literales("ck_operacion_capacidad"));
        assertEquals(registradas,literales("ck_operacion_recurso_concreto"));
        assertEquals(36,registradas.size(),"Treinta capacidades históricas y seis actuaciones C6, sin alias ni borradores automáticos");
    }

    @Test void cadaActuacionC6EsAdmitidaSinBorradorYNoSeDisfrazaComoCaptura() {
        for(String capacidad:C6) {
            assertTrue(evalua("ck_operacion_capacidad",capacidad,null),capacidad);
            assertTrue(evalua("ck_operacion_recurso_concreto",capacidad,null),capacidad);
            assertFalse(evalua("ck_operacion_recurso_concreto",capacidad,1L),capacidad);
        }
        assertTrue(evalua("ck_operacion_recurso_concreto","confirmar_captura",1L));
        assertFalse(evalua("ck_operacion_recurso_concreto","confirmar_captura",null));
    }

    @Test void vocabularioSigueCerradoSinAliasRetiradosNiComandosPrivadosDeComposicion() {
        for(String capacidad:Set.of("publicar_encargo","PUBLICAR_ENCARGO","crear_borrador_anuncio",
                "preparar_pieza_comercial","regenerar_pieza_comercial","capacidad_inventada")) {
            assertFalse(evalua("ck_operacion_capacidad",capacidad,null),capacidad);
            assertFalse(evalua("ck_operacion_recurso_concreto",capacidad,null),capacidad);
        }
    }

    private String expresion(String nombre) {
        return jdbc.queryForObject("""
                SELECT pg_get_expr(c.conbin,c.conrelid)
                FROM pg_constraint c WHERE c.conrelid='operacion_herramienta'::regclass AND c.conname=?
                """,String.class,nombre);
    }
    private Set<String> literales(String nombre) {
        var salida=new LinkedHashSet<String>();
        var coincidencias=Pattern.compile("'([a-z_]+)'").matcher(expresion(nombre));
        while(coincidencias.find())salida.add(coincidencias.group(1));
        return salida;
    }
    private boolean evalua(String nombre,String capacidad,Long borrador) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT "+expresion(nombre)+
                " FROM (SELECT ?::varchar AS capacidad, ?::bigint AS id_borrador) recurso",
                Boolean.class,capacidad,borrador));
    }
}
