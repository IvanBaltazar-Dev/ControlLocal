package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El ciclo juridico del contrato es una maquina, y esto lo demuestra.</b>
 *
 * <p>Existe porque la pregunta «¿se valida el grafo del contrato?» no se podia
 * responder leyendo una clase: {@code ContratoAlquiler.transicionarA} es una
 * asignacion sin comprobaciones, y quien se detiene ahi concluye que no se
 * valida nada. La comprobacion vive un salto mas alla —{@code Transiciones}
 * llama a {@link MaquinasEstado}— y solo se ve siguiendo la llamada.
 *
 * <p>Estas pruebas fijan el grafo aprobado para que la respuesta deje de
 * depender de por donde entre quien lo lea:
 *
 * <pre>
 *   P -> D | A        D -> V | A        V -> F | S | R
 *   R, F, S, A: terminales
 * </pre>
 */
class CicloContratoTest {

    private static final String CONTRATO = "CONTRATO_ALQUILER";

    @Nested
    @DisplayName("transiciones validas del ciclo aprobado")
    class Validas {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "P, D",   // en proceso -> firmado
                "P, A",   // en proceso -> anulado
                "D, V",   // firmado -> vigente
                "D, A",   // firmado -> anulado
                "V, F",   // vigente -> finalizado
                "V, S",   // vigente -> rescindido
                "V, R",   // vigente -> renovado (lo produce la renovacion)
        })
        void seAdmiten(String origen, String destino) {
            assertDoesNotThrow(() -> MaquinasEstado.validarTransicion(CONTRATO, origen, destino));
        }
    }

    @Nested
    @DisplayName("transiciones invalidas: la maquina las rechaza")
    class Invalidas {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                // Saltos hacia adelante: un contrato no nace vigente ni acaba
                // sin haber estado vigente.
                "P, V", "P, F", "P, S", "P, R",
                "D, F", "D, S", "D, R",
                // ANULAR algo que YA produjo efectos. Es la mas importante de
                // la lista: anular es dejar sin efecto lo que nunca lo tuvo;
                // lo que termina un alquiler en curso es rescindir. Confundirlos
                // borraria de la historia un alquiler por el que se cobro
                // comision.
                "V, A",
                // Desde un terminal no se sale. Reabrir un contrato cerrado es
                // reescribir un hecho consumado.
                "A, D", "A, V", "A, F",
                "F, V", "F, S", "F, D",
                "S, V", "S, F",
                "R, V", "R, F",
        })
        void seRechazan(String origen, String destino) {
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> MaquinasEstado.validarTransicion(CONTRATO, origen, destino));
            assertTrue(error.getMessage().contains(origen) && error.getMessage().contains(destino),
                    "el mensaje debe decir de donde a donde: " + error.getMessage());
        }
    }

    @Test
    @DisplayName("un codigo que no pertenece al contrato no se acepta como destino")
    void codigoAjenoRechazado() {
        // 'G' es de solicitud, no de contrato. Sin esta comprobacion, un
        // cliente podria colar cualquier letra que la base admita en OTRA tabla.
        assertThrows(ReglaNegocioException.class,
                () -> MaquinasEstado.validarTransicion(CONTRATO, "V", "G"));
    }

    @Test
    @DisplayName("el alta no transiciona: valida el codigo pero no exige origen")
    void elAltaNoEsUnaTransicion() {
        // origen null = la entidad nace. Es lo que usa `Transiciones.iniciar`.
        assertDoesNotThrow(() -> MaquinasEstado.validarTransicion(CONTRATO, null, "D"));
        assertDoesNotThrow(() -> MaquinasEstado.validarTransicion(CONTRATO, null, "V"));
    }

    /**
     * <b>El unico hueco real del ciclo</b>, y esta prueba lo fija como
     * comportamiento conocido en vez de dejarlo como sorpresa.
     *
     * <p>{@code validarTransicion} devuelve sin error cuando origen y destino
     * coinciden, y {@code Transiciones.aplicar} tampoco hace nada. Repetir una
     * operacion —finalizar dos veces— <b>no falla</b>: no cambia el estado, no
     * escribe historial y responde como si hubiera funcionado.
     *
     * <p>Es deliberado a nivel de {@code Transiciones} (idempotencia para todas
     * las entidades) y por eso el rechazo del contrato se hace un nivel mas
     * arriba, en su caso de uso: cambiarlo aqui afectaria a captacion,
     * solicitud, oportunidad y publicacion a la vez.
     */
    @Test
    @DisplayName("mismo estado: la maquina NO lo rechaza; lo hace el caso de uso")
    void mismoEstadoNoLoRechazaLaMaquina() {
        assertDoesNotThrow(() -> MaquinasEstado.validarTransicion(CONTRATO, "F", "F"));
    }
}
