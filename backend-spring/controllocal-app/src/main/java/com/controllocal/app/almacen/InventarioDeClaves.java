package com.controllocal.app.almacen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Todas las claves de almacen que PostgreSQL referencia, con su origen.
 *
 * <h3>La trampa que hay que conocer antes de tocar esto</h3>
 *
 * Las tres columnas que guardan una clave <b>se llaman distinto en cada
 * tabla</b>, y una de ellas no se llama "clave":
 *
 * <ul>
 *   <li>{@code persona.foto_clave}</li>
 *   <li>{@code foto_propiedad.clave}</li>
 *   <li>{@code documento_solicitud.ruta_archivo} — el nombre <b>miente</b>: no
 *       es una ruta del sistema de ficheros, es la clave opaca del almacen
 *       ({@code guardado.clave()}). Lo dice el javadoc de
 *       {@code SolicitudesController}, no el nombre de la columna.</li>
 * </ul>
 *
 * <p>Y hay dos columnas que <b>parecen</b> de esta familia y no lo son:
 * {@code evento_seguridad.clave_valor_hash} (auditoria) y
 * {@code comision_movimiento.clave_idempotencia}. Buscar "clave" en el esquema
 * y migrar lo que salga es la forma rapida de romper la auditoria.
 *
 * <p>Por eso la lista es <b>explicita</b> y no se deduce del esquema: una
 * deduccion cazaria las dos de mas, y una columna nueva que se olvide de
 * anadirse aqui deja binarios sin migrar — que es peor, y por eso el
 * conciliador informa siempre de cuantas fuentes miro.
 */
@Component
public class InventarioDeClaves {

    /** Tabla.columna -> etiqueta legible para el informe. */
    private static final Map<String, String> FUENTES = new LinkedHashMap<>();

    static {
        FUENTES.put("persona.foto_clave", "foto de perfil");
        FUENTES.put("foto_propiedad.clave", "foto de local");
        FUENTES.put("documento_solicitud.ruta_archivo", "documento del expediente");
    }

    private final JdbcTemplate jdbc;

    public InventarioDeClaves(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Una clave referenciada y de donde sale. */
    public record Referencia(String clave, String fuente) {
    }

    /**
     * Todas las claves referenciadas, sin repetir.
     *
     * <p>Se filtran las nulas y vacias en SQL: una persona sin foto tiene
     * {@code foto_clave} nula y no es un problema que reportar.
     */
    public List<Referencia> referencias() {
        List<Referencia> referencias = new java.util.ArrayList<>();
        Set<String> vistas = new LinkedHashSet<>();
        FUENTES.forEach((columna, etiqueta) -> {
            String tabla = columna.substring(0, columna.indexOf('.'));
            String campo = columna.substring(columna.indexOf('.') + 1);
            List<String> claves = jdbc.queryForList(
                    "SELECT " + campo + " FROM " + tabla
                            + " WHERE " + campo + " IS NOT NULL AND btrim(" + campo + ") <> ''",
                    String.class);
            for (String clave : claves) {
                if (vistas.add(clave)) {
                    referencias.add(new Referencia(clave, etiqueta));
                }
            }
        });
        return referencias;
    }

    /** Cuantas columnas se estan mirando. El informe lo dice para que se note si alguien anade una y no la registra. */
    public int cuantasFuentes() {
        return FUENTES.size();
    }

    public Set<String> nombresDeFuentes() {
        return FUENTES.keySet();
    }
}
