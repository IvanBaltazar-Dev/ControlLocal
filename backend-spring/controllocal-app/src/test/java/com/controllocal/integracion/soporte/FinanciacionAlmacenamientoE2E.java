package com.controllocal.integracion.soporte;

import com.controllocal.domain.planes.PlanBrox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entrada exclusivamente TEST para el contenedor efimero de Invoke-E2E.
 * Reutiliza el montaje aprobado: no PSP, cuenta productiva, seed o bean Main.
 */
public final class FinanciacionAlmacenamientoE2E {
    private FinanciacionAlmacenamientoE2E() { }

    public static void main(String[] argumentos) {
        try {
            var evidencia = financiar(validar(argumentos, System.getenv()));
            System.out.println("E2E_B0_TEST " + new ObjectMapper().writeValueAsString(evidencia));
        } catch (Exception fallo) {
            // No imprimir argumento, URL, credencial, sentencia, causa ni stack.
            System.err.println("No se preparo la financiacion sintetica del fixture E2E.");
            System.exit(2);
        }
    }

    static Configuracion validar(String[] argumentos, Map<String, String> entorno) {
        if (argumentos == null || argumentos.length != 4 || entorno == null)
            throw invalida();
        String corrida = argumentos[0], base = argumentos[1], organizacion = argumentos[2], codigo = argumentos[3];
        if (corrida == null || !corrida.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || base == null || base.length() > 63
                || !base.equals("controllocal_e2e_" + corrida.replace('-', '_'))
                || organizacion == null || !organizacion.matches("[1-9][0-9]{0,18}")
                || codigo == null || !codigo.matches("E2E-[a-f0-9]{20}"))
            throw invalida();
        long id;
        try { id = Long.parseLong(organizacion); } catch (NumberFormatException fallo) { throw invalida(); }
        String url = entorno.get("DB_URL"), usuario = entorno.get("DB_USER"), clave = entorno.get("DB_PASSWORD");
        if (!(("jdbc:postgresql://postgres-e2e:5432/" + base).equals(url))
                || usuario == null || usuario.isBlank() || clave == null || clave.isBlank())
            throw invalida();
        return new Configuracion(corrida, base, id, codigo, url, usuario, clave);
    }

    private static Evidencia financiar(Configuracion c) {
        var datos = new DriverManagerDataSource();
        datos.setDriverClassName("org.postgresql.Driver");
        datos.setUrl(c.url()); datos.setUsername(c.usuario()); datos.setPassword(c.clave());
        var jdbc = new JdbcTemplate(datos);
        var gestor = new JdbcTransactionManager(datos);
        var planes = new PlanesFinanciadosDePrueba(jdbc, gestor);
        return new TransactionTemplate(gestor).execute(t -> {
            if (!c.base().equals(jdbc.queryForObject("SELECT current_database()", String.class)))
                throw invalida();
            var org = jdbc.queryForMap("SELECT codigo,modalidad FROM organizacion WHERE id_organizacion=? FOR NO KEY UPDATE", c.organizacion());
            if (!c.codigo().equals(org.get("codigo")) || !"EMPRESARIAL".equals(org.get("modalidad")))
                throw invalida();
            boolean existente = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM b0_ciclo WHERE organizacion_id=?)", Boolean.class, c.organizacion()));
            long pagos = cantidad(jdbc, "SELECT count(*) FROM b0_acreditacion_pago WHERE organizacion_id=?", c.organizacion());
            long perfiles = cantidad(jdbc, "SELECT count(*) FROM b0_perfil_operativo");
            long fondos = cantidad(jdbc, "SELECT count(*) FROM b0_fondo_tecnico");
            UUID ciclo = planes.asegurarTeam(c.organizacion());
            var periodo = jdbc.queryForObject("SELECT plan,desde,hasta FROM b0_ciclo WHERE organizacion_id=? AND id_ciclo=?",
                    (r,n) -> new Periodo(r.getString("plan"),r.getObject("desde",OffsetDateTime.class).toInstant().toString(),
                            r.getObject("hasta",OffsetDateTime.class).toInstant().toString()), c.organizacion(), ciclo);
            String plan = periodo.plan();
            if (!PlanBrox.valueOf(plan).empresarial()
                    || pagos != cantidad(jdbc, "SELECT count(*) FROM b0_acreditacion_pago WHERE organizacion_id=?", c.organizacion())
                    || perfiles != cantidad(jdbc, "SELECT count(*) FROM b0_perfil_operativo")
                    || fondos != cantidad(jdbc, "SELECT count(*) FROM b0_fondo_tecnico"))
                throw invalida();
            return new Evidencia(c.corrida(), c.organizacion(), c.codigo(), ciclo.toString(), plan,
                    periodo.desde(), periodo.hasta(),
                    cantidad(jdbc, "SELECT count(*) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?", c.organizacion(), ciclo),
                    cantidad(jdbc, "SELECT count(*) FROM b0_plaza WHERE organizacion_id=? AND id_ciclo=?", c.organizacion(), ciclo),
                    existente ? "CONSERVADO" : "CREADO", 0, 0, 0);
        });
    }

    private static long cantidad(JdbcTemplate jdbc, String consulta, Object... parametros) {
        return jdbc.queryForObject(consulta, Long.class, parametros);
    }
    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("El fixture requiere contexto E2E y organizacion empresarial conocidos.");
    }
    record Configuracion(String corrida, String base, long organizacion, String codigo,
                         String url, String usuario, String clave) {
        @Override public String toString() { return "Configuracion privada del fixture E2E"; }
    }
    private record Periodo(String plan, String desde, String hasta) { }
    private record Evidencia(String corrida, long organizacion, String codigo, String ciclo, String plan,
                             String desde, String hasta, long fuentes, long plazas, String accion,
                             int pagosNuevos, int perfilesIaNuevos, int fondosIaNuevos) { }
}
