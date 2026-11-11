package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Sabotaje conservador: siempre rollback, incluso si una guarda fallara. No borra fixtures. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ConservacionHistoriasC3IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired DataSource datos;
    @ParameterizedTest
    @ValueSource(strings={"solicitud_colaboracion","decision_colaboracion","concesion_colaboracion","canal_colaboracion",
            "participacion_network","solicitud_participacion_network","decision_participacion_network","version_participacion_network"})
    void truncateNoEsUnCaminoParaBorrarLaHistoria(String tabla) throws Exception {
        try(var c=datos.getConnection()) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                // Valores de tabla cerrados por ValueSource, no entrada de producto.
                var rechazo=assertThrows(SQLException.class,()->s.execute("TRUNCATE TABLE "+tabla+" CASCADE"));
                assertEquals("23514",rechazo.getSQLState());
                assertTrue(rechazo.getMessage().contains("historia"));
            } finally {c.rollback();}
        }
    }
}
