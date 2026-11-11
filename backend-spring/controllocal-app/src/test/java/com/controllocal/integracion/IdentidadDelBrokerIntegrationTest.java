package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.SemillaDeIdentidadPruebas;
import com.controllocal.integracion.soporte.SesionesDePrueba;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Identidad y contexto BROKER tras prueba segura. El fixture no certifica un login. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class IdentidadDelBrokerIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired ContextosService contextos;
    @BeforeEach void fixtureDeclarado(){SemillaDeIdentidadPruebas.aplicar(jdbc);}

    @Test void todoBrokerConEquipoActualTieneCuentaPropiaConfirmada() {
        var huerfanos=jdbc.queryForList("""
            select rb.id_persona_rol from detalle_broker b
            join persona_rol rb on rb.id_persona_rol=b.id_persona_rol
            join organizacion o on o.id_organizacion=rb.organizacion_id and o.codigo='BROX_LEGACY'
            where rb.vigencia_hasta is null and exists(
              select 1 from supervision_agente s join persona_rol ra on ra.id_persona_rol=s.id_rol_agente
              join usuario_organizacion a on a.organizacion_id=ra.organizacion_id and a.id_persona=ra.id_persona
                and a.estado='A' and a.rol='AGENTE'
              where s.organizacion_id=rb.organizacion_id and s.id_rol_broker=rb.id_persona_rol and s.fecha_fin is null)
            and not exists(select 1 from usuario_organizacion u join cuenta_acceso c on c.id_cuenta_acceso=u.id_usuario
              where u.organizacion_id=rb.organizacion_id and u.id_persona=rb.id_persona and u.estado='A'
                and u.rol='BROKER' and c.estado='A' and c.correo_confirmado_en is not null)
            """);
        assertEquals(List.of(),huerfanos,"Un equipo vigente requiere supervisor con cuenta y pertenencia actuales.");
    }

    @Test void contextoElegidoEsElBrokerLocalExactoSinPrioridadDeGobierno() {
        var filas=brokersActuales();assertFalse(filas.isEmpty());
        for(var fila:filas) {
            long org=numero(fila,"organizacion_id"),persona=numero(fila,"id_persona"),rol=numero(fila,"id_persona_rol");
            var sesion=SesionesDePrueba.abrir(jdbc,contextos,new Actor(org,persona,rol,Actor.BROKER));
            assertEquals(Actor.BROKER,sesion.rolEfectivo());assertEquals(rol,sesion.acceso().activo().idRolOperativo());
            assertEquals(persona,sesion.acceso().activo().idPersona());assertEquals(org,sesion.idOrganizacion());
            assertEquals(numero(fila,"id_usuario"),sesion.idCuenta());
        }
    }

    @Test void fixtureIncluyeBrokerConEquipoParaDemostrarAlcance() {
        long cantidad=jdbc.queryForObject("""
            select count(*) from usuario_organizacion u join persona_rol rb
              on rb.organizacion_id=u.organizacion_id and rb.id_persona=u.id_persona and rb.tipo_rol='BROKER'
            join organizacion o on o.id_organizacion=u.organizacion_id and o.codigo='BROX_LEGACY'
            where u.estado='A' and u.rol='BROKER' and rb.vigencia_hasta is null and exists(
              select 1 from supervision_agente s join persona_rol ra on ra.id_persona_rol=s.id_rol_agente
              join usuario_organizacion a on a.organizacion_id=ra.organizacion_id and a.id_persona=ra.id_persona
                and a.estado='A' and a.rol='AGENTE'
              where s.organizacion_id=u.organizacion_id and s.id_rol_broker=rb.id_persona_rol and s.fecha_fin is null)
            """,Long.class);
        assertTrue(cantidad>0,"La prueba de alcance necesita un equipo real del banco técnico declarado.");
    }
    private List<Map<String,Object>> brokersActuales(){return jdbc.queryForList("""
        select u.organizacion_id,u.id_persona,u.id_usuario,r.id_persona_rol
        from usuario_organizacion u join persona_rol r on r.organizacion_id=u.organizacion_id
          and r.id_persona=u.id_persona and r.tipo_rol='BROKER' and r.vigencia_hasta is null
        join organizacion o on o.id_organizacion=u.organizacion_id and o.codigo='BROX_LEGACY'
        where u.estado='A' and u.rol='BROKER'
        """);}
    private static long numero(Map<String,Object> fila,String clave){return ((Number)fila.get(clave)).longValue();}
}