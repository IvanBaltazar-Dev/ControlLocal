package com.controllocal.service.soporte;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Los valores de UNA propiedad, por clave logica, ya resueltos por autoridad.
 *
 * <p>Para quien la recibe existe solo esto:
 *
 * <pre>
 *   metraje_total       = 90
 *   ambientes           = 5
 *   cuota_mantenimiento = 350
 * </pre>
 *
 * sin saber que el primero salio de {@code propiedad.metraje} y los otros dos
 * de {@code atributo_propiedad}. Es la consecuencia directa de D-E4-3: <b>la
 * autoridad fisica cambia, el contrato logico no.</b>
 *
 * <p><b>Nombrar la clave no es saber donde vive.</b> Un consumidor con un campo
 * llamado {@code ambientes} tiene que pedir {@code ambientes} por su nombre —no
 * hay otra forma de pedirlo—, y eso sigue siendo legitimo. Lo que D-E4-3
 * prohibe es lo otro: que el consumidor decida, a partir de la clave, en que
 * tabla buscarla. Aqui pide y recibe; quien enruta es {@link LectorPorAutoridad}.
 */
public final class ValoresDePropiedad {

    private static final ValoresDePropiedad VACIO = new ValoresDePropiedad(Map.of());

    private final Map<String, ValorLogico> porClave;

    ValoresDePropiedad(Map<String, ValorLogico> porClave) {
        this.porClave = porClave;
    }

    /** Un mapa construido a mano: lo usa quien ya tiene los valores, y los tests. */
    public static Constructor constructor() {
        return new Constructor();
    }

    /**
     * Ninguna clave conocida. No es lo mismo que "todo a null por defecto": se
     * devuelve cuando de verdad no se leyo nada, y cada clave sigue diciendo
     * que falta cuando se le pregunta.
     */
    public static ValoresDePropiedad vacio() {
        return VACIO;
    }

    public boolean tiene(String clave) {
        return porClave.containsKey(clave);
    }

    public Set<String> claves() {
        return porClave.keySet();
    }

    public ValorLogico valor(String clave) {
        return porClave.get(clave);
    }

    public String texto(String clave) {
        ValorLogico valor = porClave.get(clave);
        return valor == null ? null : valor.comoTexto();
    }

    public BigDecimal decimal(String clave) {
        ValorLogico valor = porClave.get(clave);
        return valor == null ? null : valor.numero();
    }

    public Integer entero(String clave) {
        ValorLogico valor = porClave.get(clave);
        return valor == null ? null : valor.comoEntero();
    }

    public Boolean booleano(String clave) {
        ValorLogico valor = porClave.get(clave);
        return valor == null ? null : valor.booleano();
    }

    /** Todas las claves conocidas, en el orden en que se resolvieron. */
    public Map<String, ValorLogico> todos() {
        return Map.copyOf(porClave);
    }

    // ------------------------------------------------------------------

    public static final class Constructor {
        private final Map<String, ValorLogico> acumulado = new LinkedHashMap<>();

        /** Un {@code null} NO se guarda: la clave queda declarada como faltante. */
        public Constructor con(String clave, ValorLogico valor) {
            if (valor != null) {
                acumulado.put(clave, valor);
            }
            return this;
        }

        public ValoresDePropiedad construir() {
            return acumulado.isEmpty() ? VACIO : new ValoresDePropiedad(acumulado);
        }
    }
}
