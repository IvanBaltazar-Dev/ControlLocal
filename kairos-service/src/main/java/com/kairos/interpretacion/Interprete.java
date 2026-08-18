package com.kairos.interpretacion;

import com.kairos.brox.SesionBrox;
import com.kairos.conversacion.Accion;

import java.util.List;
import java.util.Map;

/**
 * <b>De una frase a pares clave/valor. Nada mas.</b>
 *
 * <h2>Esta interfaz es la frontera del modelo de lenguaje</h2>
 * <pre>
 *   frase ──▶ Interprete ──▶ { accion, datos } ──▶ Kairos ──▶ API de BROX
 *                  ▲
 *          hoy: reglas y lexico
 *          manana: un LLM, sin tocar nada a su derecha
 * </pre>
 * Existe <b>antes</b> que el modelo a proposito, y por eso cambiar de proveedor
 * —o de estrategia entera— es cambiar una implementacion. Escribir el adaptador
 * contra un LLM habria significado que probarlo exigiera una llamada de red por
 * asercion, y que cambiar de modelo obligara a reescribir el adaptador.
 *
 * <h2>Lo que un interprete NO decide, ni siquiera cuando sea un LLM</h2>
 * <ul>
 *   <li><b>Que falta.</b> Lo dice BROX, leyendo su catalogo.</li>
 *   <li><b>Si un valor vale.</b> Lo dice BROX al anotarlo.</li>
 *   <li><b>Si hay suficiente para ejecutar.</b> Lo dice BROX.</li>
 *   <li><b>Si el actor puede.</b> Lo dice BROX, con la sesion de la persona.</li>
 * </ul>
 * Un interprete que decidiera cualquiera de las cuatro seria un segundo motor
 * de registro — y el segundo es el que nadie prueba contra la base.
 *
 * <h2>La regla que un LLM rompe si nadie se lo impide</h2>
 * <b>Un dato que no se sabe se declara faltante.</b> No se rellena con el caso
 * frecuente. Un modelo de lenguaje es, literalmente, una maquina de producir el
 * valor plausible: preguntado por una operacion que la frase no menciona,
 * respondera ALQUILER, porque la mayoria de los avisos son alquileres. Y
 * acertara casi siempre, que es lo que lo hace peligroso — el dia que falle,
 * habra archivado un precio de venta en la serie de alquiler, y 180 000 es un
 * numero perfectamente legal para una renta.
 *
 * <p>Por eso {@link Lectura#datos()} <b>omite</b> lo que no se dijo, y por eso
 * {@link Lectura#noEntendido()} existe: un trozo de frase que no se supo
 * convertir se declara, no se descarta en silencio.
 */
public interface Interprete {

    /**
     * Lo que se saco de una frase.
     *
     * @param accion      la accion reconocida, o {@code null} si ninguna
     * @param datos       pares clave/valor con las claves que BROX publica: solo
     *                    lo que la frase dijo
     * @param noEntendido trozos que parecian significar algo y no se supieron
     *                    convertir. Se declaran para poder preguntarlos
     * @param motivo      por que no hubo accion, cuando no la hubo. Es un
     *                    CODIGO, no una frase para mostrar
     */
    record Lectura(Accion accion, Map<String, String> datos, List<String> noEntendido,
                   String motivo) {

        public static final String SIN_ACCION = "SIN_ACCION_RECONOCIDA";
        public static final String SIN_TEXTO = "SIN_TEXTO";

        public static Lectura nada(String motivo) {
            return new Lectura(null, Map.of(), List.of(), motivo);
        }

        public boolean hayAccion() {
            return accion != null;
        }
    }

    /**
     * Lee una frase.
     *
     * @param sesion la de la persona. Hace falta porque parte del vocabulario es
     *               <b>del tenant</b> —sus distritos, sus atributos de catalogo—
     *               y se pregunta a BROX con sus permisos
     */
    Lectura leer(String texto, SesionBrox sesion);
}
