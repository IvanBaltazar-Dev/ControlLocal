package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.*;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.ContextosService;
import com.controllocal.service.captura.MotorDeCaptura;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica la fuente TEST; no acredita pagos, gasto IA ni financiación real de producción. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class PlanesFinanciadosDePruebaIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;@Autowired ContextosService contextos;
    @Test void fuenteTeamEsExplicitaIdempotenteYNoRegistraUnaCompraReal(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);
        try{
            long org=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa().agente().idOrganizacion();
            var planes=new PlanesFinanciadosDePrueba(jdbc,gestor);var ciclo=planes.asegurarTeam(org);
            var desde=jdbc.queryForObject("SELECT desde FROM b0_ciclo WHERE organizacion_id=? AND id_ciclo=?",OffsetDateTime.class,org,ciclo).toInstant();
            var hasta=jdbc.queryForObject("SELECT hasta FROM b0_ciclo WHERE organizacion_id=? AND id_ciclo=?",OffsetDateTime.class,org,ciclo).toInstant();
            assertEquals(CalendarioComercial.siguienteMes(desde,desde.atZone(CalendarioComercial.ZONA).getDayOfMonth()),hasta);
            assertEquals("TEAM",jdbc.queryForObject("SELECT plan FROM b0_ciclo WHERE organizacion_id=?",String.class,org));
            assertTrue(jdbc.queryForObject("SELECT evidencia_pago FROM b0_ciclo WHERE organizacion_id=?",String.class,org).contains("SINTETICA TEST"));
            monto(PlanBrox.TEAM.precioUsd(),"SELECT importe_financiado_usd FROM b0_ciclo WHERE organizacion_id=?",org);
            monto(BigDecimal.valueOf(PlanBrox.TEAM.capacidadTotal()),"SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
            assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM b0_plaza WHERE organizacion_id=? AND tipo='AGENTE'",Integer.class,org));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_plaza WHERE organizacion_id=? AND tipo='BROKER'",Integer.class,org));
            monto(BigDecimal.valueOf(PlanBrox.TEAM.bolsaBroker()),"SELECT inicial FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='MENSUAL_BROKER'",org);
            monto(BigDecimal.valueOf(PlanBrox.TEAM.reservaAsignable()),"SELECT inicial FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='RESERVA'",org);
            // No reiniciar consumos/ciclo por volver a preparar otro documento del mismo expediente.
            jdbc.update("UPDATE b0_fuente_capacidad SET retenido=1 WHERE organizacion_id=? AND origen='MENSUAL_AGENTE' AND plaza=1",org);
            assertEquals(ciclo,planes.asegurarTeam(org));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_ciclo WHERE organizacion_id=?",Integer.class,org));
            assertEquals(7,jdbc.queryForObject("SELECT count(*) FROM b0_fuente_capacidad WHERE organizacion_id=?",Integer.class,org));
            monto(BigDecimal.ONE,"SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_acreditacion_pago WHERE organizacion_id=?",Integer.class,org));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_linea_financiada WHERE organizacion_id=?",Integer.class,org));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,org));
        }finally{EvidenciaC4DePrueba.limpiar();}
    }
    private void monto(BigDecimal esperado,String sql,long org){assertEquals(0,esperado.compareTo(jdbc.queryForObject(sql,BigDecimal.class,org)));}
}
