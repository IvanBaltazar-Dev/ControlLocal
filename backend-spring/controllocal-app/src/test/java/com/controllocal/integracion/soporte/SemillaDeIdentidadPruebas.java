package com.controllocal.integracion.soporte;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Fuente TEST explícita; nunca se carga desde Main ni migra cuentas reales. */
public final class SemillaDeIdentidadPruebas {
    private SemillaDeIdentidadPruebas() { }
    public static void aplicar(JdbcTemplate jdbc) {
        final String script;
        try {script=new ClassPathResource("fixtures/cuenta-organizacional.sql").getContentAsString(StandardCharsets.UTF_8);}
        catch(IOException e){throw new IllegalStateException("Falta la semilla explicita de identidad de pruebas.",e);}
        // Una sentencia PostgreSQL conserva los bloques DO $$; no se parte SQL por sus punto y coma internos.
        // La conexión actual conserva el rollback de una prueba transaccional.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)conexion->{
            boolean propia=conexion.getAutoCommit();
            if(propia)conexion.setAutoCommit(false);
            try {
                try(var sentencia=conexion.createStatement()){sentencia.execute(script);}
                if(propia)conexion.commit();return null;
            } catch(java.sql.SQLException|RuntimeException e) {if(propia)conexion.rollback();throw e;}
            finally {if(propia)conexion.setAutoCommit(true);}
        });
    }
}
