package com.controllocal.app.arranque;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Primera guarda del arranque productivo: comprueba que las variables de
 * entorno obligatorias <b>existen</b>, y lo hace <b>antes de que se cree
 * ningun bean</b>.
 * <p>
 * Hace falta que sea tan temprano por una razon concreta: si se deja para mas
 * tarde, el primer bean que revienta es el DataSource y el mensaje que ve el
 * operador es {@code 'url' must start with "jdbc"} — que es verdad, pero
 * <b>no dice que falta DB_URL</b>. Un arranque que falla sin nombrar la
 * variable se acaba resolviendo desactivando la comprobacion, que es
 * exactamente lo que D-S0-2 quiere evitar.
 * <p>
 * Se registra en {@code META-INF/spring.factories} porque en este punto del
 * ciclo de vida todavia no existe el contexto y no puede ser un {@code @Component}.
 * <p>
 * Las comprobaciones que necesitan la base de datos (hashes del seed,
 * organizaciones sin administrador) viven en {@link ComprobacionArranqueSeguridad},
 * que corre cuando el contexto ya esta listo.
 */
public class ComprobacionVariablesObligatorias
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Variable -> para que sirve. El texto se muestra tal cual al operador. */
    private static final Map<String, String> OBLIGATORIAS = new LinkedHashMap<>();

    static {
        OBLIGATORIAS.put("DB_URL", "URL JDBC de PostgreSQL (jdbc:postgresql://host:5432/base)");
        OBLIGATORIAS.put("DB_USER", "usuario de la base de datos");
        OBLIGATORIAS.put("DB_PASSWORD", "contrasena de la base de datos (no la del compose de desarrollo)");
        OBLIGATORIAS.put("API_TOKEN_SECRET", "secreto de firma del JWT, >= 32 caracteres (openssl rand -base64 48)");
        OBLIGATORIAS.put("CORS_ORIGENES", "origen exacto del SPA, sin comodines ni localhost");
        // El texto nombra las DOS variables que puede exigir despues, y no es
        // verborrea: la comprobacion siguiente depende de esta, asi que sin
        // decirlo aqui el operador arreglaria el proveedor, volveria a
        // desplegar y solo entonces se enteraria de que ademas le falta el
        // directorio o el bucket. Un arranque fallido tiene que poder
        // arreglarse de una sola pasada.
        OBLIGATORIAS.put("ALMACEN_PROVEEDOR", "donde viven los binarios: DISCO "
                + "(y entonces tambien ALMACEN_DIR) o S3 (y entonces ALMACEN_S3_BUCKET)");
    }

    /**
     * Lo que exige cada proveedor de almacen, que <b>no</b> es lo mismo.
     *
     * <p>{@code ALMACEN_DIR} dejo de ser obligatorio siempre el dia que hubo un
     * segundo proveedor: exigirlo con {@code S3} obligaria al operador a
     * inventar una ruta que nadie va a leer, y una variable que se rellena por
     * inercia deja de significar nada.
     */
    private static String variableQueExige(String proveedor) {
        return "S3".equalsIgnoreCase(proveedor) ? "ALMACEN_S3_BUCKET" : "ALMACEN_DIR";
    }

    private static String paraQueSirve(String proveedor) {
        return "S3".equalsIgnoreCase(proveedor)
                ? "bucket donde viven los binarios (ALMACEN_PROVEEDOR=S3)"
                : "ruta absoluta del almacen sobre un volumen persistente (ALMACEN_PROVEEDOR=DISCO)";
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
        ConfigurableEnvironment entorno = evento.getEnvironment();
        if (!List.of(entorno.getActiveProfiles()).contains("prod")) {
            return;
        }

        List<String> faltan = new ArrayList<>();
        for (Map.Entry<String, String> variable : OBLIGATORIAS.entrySet()) {
            String valor = entorno.getProperty(variable.getKey());
            if (valor == null || valor.isBlank()) {
                faltan.add(variable.getKey() + "  -> " + variable.getValue());
            }
        }

        // La del almacen depende de la anterior, asi que se resuelve aparte.
        String proveedor = entorno.getProperty("ALMACEN_PROVEEDOR");
        if (proveedor != null && !proveedor.isBlank()) {
            String exigida = variableQueExige(proveedor);
            String valor = entorno.getProperty(exigida);
            if (valor == null || valor.isBlank()) {
                faltan.add(exigida + "  -> " + paraQueSirve(proveedor));
            }
        }

        if (!faltan.isEmpty()) {
            StringBuilder mensaje = new StringBuilder(
                    "El perfil 'prod' esta activo pero faltan " + faltan.size()
                            + " variable(s) de entorno obligatoria(s):");
            for (String f : faltan) {
                mensaje.append("\n  - ").append(f);
            }
            mensaje.append("\n\nEl arranque se detiene a proposito (D-S0-20). ")
                    .append("En 'dev' estas variables tienen valores por defecto; en 'prod' no, ")
                    .append("para que un despliegue mal configurado no levante en silencio.");
            throw new IllegalStateException(mensaje.toString());
        }
    }
}
