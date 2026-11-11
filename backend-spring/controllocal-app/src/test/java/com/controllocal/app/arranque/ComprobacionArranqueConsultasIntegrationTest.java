package com.controllocal.app.arranque;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.CustodiosConfigurados;
import com.controllocal.web.seguridad.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Ejecuta las guardas SQL reales: el fallback cerrado de contar() no puede ocultar un SQL roto. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ComprobacionArranqueConsultasIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;

    @Test void consultasDeProdOperanSobreCuentaYGobiernoActualSinFallbackTecnico() {
        List<RuntimeException> errores=new ArrayList<>();List<String> consultas=new ArrayList<>();
        var observada=new JdbcTemplate(jdbc.getDataSource()) {
            @Override public <T> T queryForObject(String sql,Class<T> tipo,Object... argumentos) {
                consultas.add(sql);
                try{return super.queryForObject(sql,tipo,argumentos);}
                catch(RuntimeException e){errores.add(e);throw e;}
            }
        };
        var comprobacion=new ComprobacionArranqueSeguridad(new MockEnvironment(),mock(TokenService.class),
                mock(CifradoSecretos.class),mock(CustodiosConfigurados.class),observada);
        ValidadorConfiguracionSeguridad.Entorno foto=ReflectionTestUtils.invokeMethod(comprobacion,"fotoDelEntorno");
        assertNotNull(foto);assertEquals(3,consultas.size());
        assertTrue(errores.isEmpty(),()->"Fallo SQL de guarda: "+errores.stream().map(e->e.getClass().getSimpleName()).toList());
        assertTrue(foto.cuentasConHashPublicoOInseguro()>=0);assertTrue(foto.credencialesConHashCompartido()>=0);
        assertTrue(foto.organizacionesSinAdministrador()>=0);
        assertTrue(consultas.stream().noneMatch(q->q.contains("credencial_usuario")||q.contains("uo.rol = 'TENANT_ADMIN'")));
    }
}
