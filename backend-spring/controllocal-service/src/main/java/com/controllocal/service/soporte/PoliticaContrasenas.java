package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.util.Locale;
import java.util.Set;

/**
 * Politica de contrasenas personales (Plan S0 §4.5).
 *
 * <h2>Lo que exige, y lo que deliberadamente NO exige</h2>
 * <ul>
 *   <li><b>Minimo 12 caracteres</b>, sin tope bajo. La longitud es lo unico
 *       que aporta entropia de verdad.</li>
 *   <li><b>No</b> se exigen mayusculas, digitos y simbolos. Esa regla es la
 *       que fabrica {@code Clave2026!} — que es <i>exactamente</i> el patron
 *       del seed que este bloque viene a retirar.</li>
 *   <li><b>No</b> hay rotacion periodica, por lo mismo: empuja a
 *       {@code Clave2026!}, {@code Clave2027!}…</li>
 *   <li>Se rechaza la contrasena que <b>contiene el nombre de usuario</b> y la
 *       que esta en una lista corta de claves conocidas.</li>
 * </ul>
 *
 * <p>La lista de claves comunes es <b>corta a proposito</b>: no pretende ser un
 * diccionario —eso es trabajo del bloqueo por intentos, no de una validacion de
 * formulario— sino cortar lo que la gente escribe cuando la obligan a cambiar y
 * no quiere pensar.
 */
public final class PoliticaContrasenas {

    public static final int LARGO_MINIMO = 12;

    /** Cuantos hashes anteriores se guardan para impedir reutilizacion (§4.5). */
    public static final int HISTORIAL = 5;

    private static final Set<String> COMUNES = Set.of(
            "contrasena", "contraseña", "password", "passw0rd", "123456", "1234567890",
            "qwerty", "qwertyuiop", "administrador", "administrator", "bienvenido",
            "controllocal", "brox", "cambiar123", "temporal123", "clave12345");

    private PoliticaContrasenas() {
    }

    /**
     * Valida la contrasena nueva. Lanza {@link ReglaNegocioException} (400) con
     * un mensaje que dice <b>que</b> corregir: un "contrasena invalida" a secas
     * se resuelve probando, y probar es lo que hay que evitar.
     */
    public static void exigirValida(char[] nueva, String nombreUsuario) {
        if (nueva == null || nueva.length == 0) {
            throw new ReglaNegocioException("La contrasena nueva es obligatoria.");
        }
        if (nueva.length < LARGO_MINIMO) {
            throw new ReglaNegocioException(
                    "La contrasena debe tener al menos " + LARGO_MINIMO + " caracteres.");
        }
        String texto = new String(nueva).toLowerCase(Locale.ROOT);
        if (COMUNES.contains(texto)) {
            throw new ReglaNegocioException("Esa contrasena es demasiado comun. Elige otra.");
        }
        if (nombreUsuario != null && !nombreUsuario.isBlank()
                && texto.contains(nombreUsuario.trim().toLowerCase(Locale.ROOT))) {
            throw new ReglaNegocioException(
                    "La contrasena no puede contener tu nombre de usuario.");
        }
    }

}
