package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>Un concepto ESTRUCTURAL que se puede escribir tiene que poder leerse</b>
 * (V79).
 *
 * <h2>El fallo que cierra, y por que no lo veia nada</h2>
 * {@link EscritorEstructural} tiene el mismo concepto escrito en cuatro sitios
 * —{@code aplicar}, {@code vaciar}, {@code sabeEscribir} y {@code leerValor}— y
 * nada obligaba a que las cuatro listas coincidieran. Anadir un {@code case} de
 * escritura y olvidar el de lectura <b>no rompe nada visible</b>:
 *
 * <pre>
 *   leerValor(...)                  -> default -> null
 *   ValoresGobernados.Constructor.con(clave, null)  -> descarta la clave
 * </pre>
 *
 * <p>Resultado: el valor se guarda en su columna, desaparece de la ficha y del
 * contrato, y ningun test de ida y vuelta lo nota porque la clave sencillamente
 * <b>no esta</b> en la respuesta. Es la version estructural de la fuga que
 * D-E4-3 cerro para los atributos: se escribe en un sitio y se lee de otro.
 *
 * <h2>Por que es generico y no una lista de casos</h2>
 * Los conceptos se descubren por reflexion sobre las constantes
 * {@code CAMPO_*} de {@link CatalogoAtributo}, que es donde el vocabulario de
 * conceptos se declara. Un concepto nuevo entra en este gate <b>solo</b>
 * con declararlo: no hay una segunda lista que mantener aqui, que es
 * exactamente el problema que el gate persigue.
 *
 * <p>La otra mitad —que ninguna FILA del catalogo declare un concepto que el
 * codigo no conoce— necesita la base y vive en
 * {@code AutoridadDelDatoIntegrationTest}: el catalogo es dato, y un tenant
 * puede escribir en el.
 */
class CadenaEstructuralCompletaTest {

    /**
     * Valores de tanteo, uno por forma de dato. El gate no sabe —ni tiene que
     * saber— de que tipo es cada concepto: prueba hasta que uno entra. Si
     * ninguno entra, el concepto no tiene escritor y eso ya es el fallo.
     */
    private static final List<String> MUESTRAS = List.of("120.50", "3", "LIMA", "11223344");

    /** Los conceptos declarados, leidos de donde se declaran. */
    private static List<String> conceptosDeclarados() {
        List<String> conceptos = new ArrayList<>();
        for (Field campo : CatalogoAtributo.class.getDeclaredFields()) {
            if (campo.getName().startsWith("CAMPO_")
                    && Modifier.isStatic(campo.getModifiers())
                    && campo.getType() == String.class) {
                try {
                    conceptos.add((String) campo.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError("No se pudo leer " + campo.getName(), e);
                }
            }
        }
        if (conceptos.isEmpty()) {
            fail("No se encontro ninguna constante CAMPO_* en CatalogoAtributo. Si el "
                    + "vocabulario de conceptos estructurales se mudo de sitio, este gate "
                    + "dejo de vigilar nada y hay que reapuntarlo.");
        }
        return conceptos;
    }

    // ==================================================================

    @Test
    @DisplayName("todo concepto estructural declarado tiene escritor")
    void ningunConceptoDeclaradoSeQuedaSinEscritor() {
        List<String> sinEscritor = conceptosDeclarados().stream()
                .filter(concepto -> !EscritorEstructural.sabeEscribir(concepto))
                .toList();

        assertTrue(sinEscritor.isEmpty(), """
                Estos conceptos estructurales estan declarados y no tienen escritor: %s

                Una clave de catalogo que los use se pediria en el alta y su valor no se
                guardaria en ninguna parte: el campo fantasma que D-E4-3 persigue.
                """.formatted(sinEscritor));
    }

    /**
     * <b>La ida y la vuelta, concepto a concepto.</b>
     *
     * <p>Es la comprobacion que CONTROL pidio tras el hallazgo del AUDITOR: no
     * basta con que exista un {@code case} en {@code aplicar}; el valor tiene
     * que volver por {@code leerValor}, que es por donde lo pide la ficha, el
     * listado y el motor de captura.
     */
    @Test
    @DisplayName("lo que el escritor guarda, el lector lo devuelve")
    void loEscritoSeLee() {
        List<String> rotos = new ArrayList<>();

        for (String concepto : conceptosDeclarados()) {
            Propiedad propiedad = new Propiedad();
            String escrito = escribirComoSea(propiedad, concepto);

            if (escrito == null) {
                rotos.add(concepto + ": ningun valor de prueba fue aceptado por `aplicar`");
                continue;
            }
            ValorLogico leido = EscritorEstructural.leerValor(propiedad, concepto);
            if (leido == null) {
                rotos.add(concepto + ": se escribio \"" + escrito + "\" y `leerValor` devolvio "
                        + "null, asi que el dato queda guardado donde nadie lo lee");
                continue;
            }
            if (!EscritorEstructural.tieneValor(propiedad, concepto)) {
                rotos.add(concepto + ": `leerValor` lo devuelve y `tieneValor` dice que falta, "
                        + "asi que se reportaria como faltante teniendolo");
            }
        }

        if (!rotos.isEmpty()) {
            fail("""
                    La cadena estructural esta rota en:

                      %s

                    `EscritorEstructural` tiene el mismo concepto escrito en cuatro switch y
                    nada obliga a que coincidan. El `default` de `leerValor` devuelve null y
                    `ValoresGobernados.Constructor.con` descarta un null EN SILENCIO: por eso
                    un `case` de escritura sin su lectura no rompe ninguna prueba de ida y
                    vuelta -- la clave sencillamente no aparece en la respuesta.
                    """.formatted(String.join("\n  ", rotos)));
        }
    }

    /**
     * <b>Vaciar tambien enruta, y decir «no se puede» es una respuesta valida.</b>
     *
     * <p>Lo que no vale es caer en el {@code default}: eso no significa que el
     * dato no se pueda quitar, significa que <b>nadie lo penso</b>. Las dos
     * cosas se distinguen por {@link EscritorEstructural#SIN_VACIADO_DEFINIDO},
     * que es una constante para que el gate no dependa de como este redactado el
     * mensaje.
     */
    @Test
    @DisplayName("todo concepto sabe que significa quedarse vacio")
    void vaciarEstaDecididoParaTodos() {
        List<String> sinDecidir = new ArrayList<>();

        for (String concepto : conceptosDeclarados()) {
            Propiedad propiedad = new Propiedad();
            if (escribirComoSea(propiedad, concepto) == null) {
                continue; // ya lo reporta el caso de arriba
            }
            try {
                EscritorEstructural.vaciar(propiedad, concepto, "clave_de_prueba");
                assertFalse(EscritorEstructural.tieneValor(propiedad, concepto),
                        "`vaciar` acepto " + concepto + " y el valor sigue ahi");
            } catch (ReglaNegocioException negativa) {
                if (negativa.getMessage().contains(EscritorEstructural.SIN_VACIADO_DEFINIDO)) {
                    sinDecidir.add(concepto);
                }
            }
        }

        assertTrue(sinDecidir.isEmpty(), """
                Estos conceptos caen en el `default` de `vaciar`: %s

                Negarse a vaciar es legitimo --METRAJE lo hace, porque toda propiedad tiene
                metraje-- pero tiene que ser una decision escrita con su motivo. El
                `default` dice otra cosa: que se anadio el concepto y no se penso que
                significa retirarlo, y quien lo intente recibira un mensaje que no le
                explica nada.
                """.formatted(sinDecidir));
    }

    // ------------------------------------------------------------------

    /**
     * Escribe el primer valor de prueba que el concepto acepte, y devuelve cual
     * fue. {@code null} si ninguno entro.
     */
    private static String escribirComoSea(Propiedad propiedad, String concepto) {
        for (String muestra : MUESTRAS) {
            try {
                EscritorEstructural.aplicar(propiedad, concepto, muestra, "clave_de_prueba");
                return muestra;
            } catch (ReglaNegocioException | IllegalStateException noEncaja) {
                // El siguiente. Un concepto numerico rechaza "LIMA" y esta bien.
            }
        }
        return null;
    }
}
