package com.controllocal.integracion.soporte;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Frontera previa a abrir JDBC; la financiacion real se prueba con el helper y HTTP. */
class FinanciacionAlmacenamientoE2ETest {
    private static final String CORRIDA = "20261031120000-1234";
    private static final String BASE = "controllocal_e2e_20261031120000_1234";
    private String[] argumentos() { return new String[]{CORRIDA, BASE, "21", "E2E-0123456789abcdef0123"}; }
    private Map<String,String> entorno() {
        return new HashMap<>(Map.of("DB_URL", "jdbc:postgresql://postgres-e2e:5432/"+BASE,
                "DB_USER", "TEST_USUARIO", "DB_PASSWORD", "TEST_CLAVE_FICTICIA"));
    }
    @Test void aceptaSoloBaseExactaDeLaCorridaConIdentidadExplicita() {
        var c = FinanciacionAlmacenamientoE2E.validar(argumentos(), entorno());
        assertEquals(BASE,c.base()); assertEquals(21,c.organizacion()); assertEquals(CORRIDA,c.corrida());
        assertEquals("E2E-0123456789abcdef0123",c.codigo());
    }
    @Test void deniegaOtraBaseAunqueTambienSeaDePrueba() {
        var a=argumentos();a[1]="controllocal_e2e_otra_corrida";
        assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(a,entorno()));
    }
    @Test void deniegaDesarrolloHostAjenoParametrosYCredencialesEnUrl() {
        for(String url: new String[]{"jdbc:postgresql://postgres:5432/controllocal_dev",
                "jdbc:postgresql://otro-host:5432/"+BASE,"jdbc:postgresql://postgres-e2e:5432/"+BASE+"?user=otro",
                "jdbc:postgresql://usuario:clave@postgres-e2e:5432/"+BASE}) {
            var e=entorno();e.put("DB_URL",url);
            var fallo=assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(argumentos(),e));
            assertFalse(fallo.getMessage().contains(url));
        }
    }
    @Test void deniegaIdentidadOrganizacionalNoExplicitaOMalformada() {
        for(String id: new String[]{"0","-1","1 OR 1=1","9223372036854775808"}) {
            var a=argumentos();a[2]=id;
            assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(a,entorno()));
        }
        var a=argumentos();a[3]="EMPRESA_REAL";
        assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(a,entorno()));
    }
    @Test void deniegaCredencialesAusentesSinPublicarLaConfiguracion() {
        for(String campo:new String[]{"DB_URL","DB_USER","DB_PASSWORD"}) {
            var e=entorno();e.remove(campo);
            assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(argumentos(),e));
        }
        String texto=FinanciacionAlmacenamientoE2E.validar(argumentos(),entorno()).toString();
        assertFalse(texto.contains("TEST_CLAVE_FICTICIA"));assertFalse(texto.contains("jdbc:"));
    }
    @Test void deniegaFaltaDeArgumentosArgumentosExtraYCorridaInvalida() {
        assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(new String[]{CORRIDA},entorno()));
        assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(new String[]{CORRIDA,BASE,"21","E2E-0123456789abcdef0123","extra"},entorno()));
        var a=argumentos();a[0]="corrida;otra";
        assertThrows(IllegalArgumentException.class,()->FinanciacionAlmacenamientoE2E.validar(a,entorno()));
    }
}
