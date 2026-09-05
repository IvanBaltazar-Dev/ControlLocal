package com.kairos.herramientas;

import com.kairos.conversacion.Accion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El catalogo de D-K-1 §3, comprobado contra lo que dice el documento.
 *
 * <p>Lo que estas pruebas protegen no es que la lista exista: es que
 * <b>ninguna herramienta pueda declararse sin caso de uso, sin permiso o sin
 * confirmacion</b>, y que las ocho sensibles sigan siendo sensibles. Una tabla
 * en un documento no impide anadir una llamada al Core sin decir por donde
 * entra; esto si.
 */
class HerramientasTest {

    /** Las ocho de D-K-1 §3.3, con el criterio por el que cada una lo es. */
    private static final Set<String> SENSIBLES = Set.of(
            "confirmar_captura", "publicar_encargo", "presentar_oferta", "decidir_captacion",
            "evaluar_solicitud", "cerrar_encargo", "registrar_cobro", "enviar_reporte");

    @Test
    @DisplayName("las ocho sensibles de D-K-1 §3.3 estan, y las ocho exigen confirmacion")
    void lasSensiblesLoSiguenSiendo() {
        for (String nombre : SENSIBLES) {
            Herramienta herramienta = Herramientas.de(nombre).orElseThrow(
                    () -> new AssertionError("falta la herramienta sensible " + nombre));
            assertEquals(Confirmacion.SIEMPRE, herramienta.confirmacion(),
                    nombre + " sale al exterior, firma algo, mueve dinero o no se deshace: la "
                            + "confirma una persona");
            assertTrue(herramienta.laConfirmaUnaPersona());
        }
    }

    @Test
    @DisplayName("ninguna herramienta que NO sea de las ocho exige confirmacion de persona")
    void loQueNoEsSensibleNoPideConfirmacion() {
        Herramientas.todas().stream()
                .filter(h -> !SENSIBLES.contains(h.nombre()))
                .forEach(h -> assertFalse(h.laConfirmaUnaPersona(),
                        h.nombre() + " no esta en D-K-1 §3.3: si de verdad hay que confirmarla, "
                                + "se decide y se escribe alli, no se sube el nivel aqui"));
    }

    @Test
    @DisplayName("toda herramienta declara metodo, ruta y permiso: sin eso no hay caso de uso")
    void ningunaSinCasoDeUso() {
        for (Herramienta herramienta : Herramientas.todas()) {
            assertFalse(herramienta.casoDeUso().isBlank());
            assertTrue(herramienta.ruta().startsWith("/"),
                    herramienta.nombre() + " tiene que nombrar una ruta del Core");
            assertTrue(List.of("GET", "POST", "PUT", "PATCH", "DELETE")
                            .contains(herramienta.metodo()),
                    herramienta.nombre() + " tiene que nombrar un verbo HTTP");
            assertFalse(herramienta.permiso().isBlank(),
                    herramienta.nombre() + " tiene que copiar su celda de la matriz");
        }
    }

    @Test
    @DisplayName("una herramienta sin caso de uso no se puede construir")
    void sinCasoDeUsoNoCompone() {
        IllegalArgumentException sinRuta = assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", " ", Herramienta.AGENTE,
                        Confirmacion.AVISA, Herramienta.Estado.VIVA));
        assertTrue(sinRuta.getMessage().contains("caso de uso"));

        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", " ", Confirmacion.AVISA,
                        Herramienta.Estado.VIVA));
        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", Herramienta.AGENTE, null,
                        Herramienta.Estado.VIVA));
        assertThrows(IllegalArgumentException.class,
                () -> new Herramienta("inventada", "POST", "/algo", Herramienta.AGENTE,
                        Confirmacion.AVISA, null));
    }

    @Test
    @DisplayName("toda accion nombra una herramienta del catalogo, y esta implementada")
    void ningunaAccionApuntaAlVacio() {
        for (Accion accion : Accion.values()) {
            Herramienta herramienta = Herramientas.de(accion.capacidad()).orElseThrow(
                    () -> new AssertionError(accion + " nombra la capacidad \"" + accion.capacidad()
                            + "\", que no esta en el catalogo"));
            assertTrue(herramienta.implementada(),
                    accion + " apunta a " + herramienta.nombre() + ", declarada y sin implementar");
            assertEquals(herramienta, accion.herramienta());
        }
    }

    @Test
    @DisplayName("las declaradas y no implementadas siguen declaradas: no se decide dos veces")
    void lasDeclaradasSiguenDeclaradas() {
        List<String> pendientes = Herramientas.declaradas().stream()
                .map(Herramienta::nombre)
                .toList();
        assertEquals(List.of("publicar_encargo", "presentar_oferta", "decidir_captacion",
                        "evaluar_solicitud", "cerrar_encargo", "registrar_cobro", "enviar_reporte"),
                pendientes,
                "estas siete tienen su caso de uso y su confirmacion escritos aunque KAIROS "
                        + "todavia no las invoque: el dia que se implementen no se vuelve a "
                        + "decidir si publicar sale al exterior");
    }

    @Test
    @DisplayName("la confirmacion se compone hacia el lado estricto, nunca hacia el comodo")
    void laConfirmacionSeComponeHaciaLoEstricto() {
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.SIEMPRE.mas(Confirmacion.NUNCA));
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.NUNCA.mas(Confirmacion.SIEMPRE));
        assertEquals(Confirmacion.AVISA, Confirmacion.AVISA.mas(Confirmacion.NUNCA));
        assertEquals(Confirmacion.SIEMPRE, Confirmacion.AVISA.mas(Confirmacion.SIEMPRE));
        assertEquals(Confirmacion.AVISA, Confirmacion.AVISA.mas(null),
                "sin nada que componer, manda el catalogo");
    }

    @Test
    @DisplayName("los permisos copiados son celdas reales de la matriz operacion→rol")
    void losPermisosSonCeldasDeLaMatriz() {
        Set<String> celdas = Set.of(Herramienta.TODOS, Herramienta.AGENTE, Herramienta.BROKER);
        Herramientas.todas().stream()
                .filter(h -> !h.sinPuertaEnElCore())
                .forEach(h -> assertTrue(celdas.contains(h.permiso()),
                        h.nombre() + " declara un permiso que no es ninguna celda de la matriz: "
                                + h.permiso()));

        assertEquals(Herramienta.AGENTE, Herramientas.CONFIRMAR_CAPTURA.permiso(),
                "POST /captura/{id}/ejecutar es AGENTE en la matriz; abrir la captura es TODOS");
        assertEquals(Herramienta.TODOS, Herramientas.ABRIR_SESION_CAPTURA.permiso());
        assertEquals(Herramienta.BROKER, Herramientas.EVALUAR_SOLICITUD.permiso(),
                "firmar una evaluacion es del broker, y el TENANT_ADMIN dejo de alcanzarla");
    }

    @Test
    @DisplayName("una ruta que el Core no sirve no copia el permiso de una parecida")
    void loQueElCoreNoSirveNoInventaSuCelda() {
        for (Herramienta herramienta : Herramientas.sinPuertaEnElCore()) {
            assertEquals(Herramienta.SIN_FILA, herramienta.permiso(),
                    herramienta.nombre() + " no tiene fila en la matriz porque el Core no sirve "
                            + herramienta.casoDeUso() + ": copiar la celda de una ruta parecida "
                            + "seria interpretar, y el catalogo copia");
        }
        Herramientas.todas().stream()
                .filter(h -> Herramienta.SIN_FILA.equals(h.permiso()))
                .forEach(h -> assertTrue(h.sinPuertaEnElCore(),
                        h.nombre() + " dice no tener fila en la matriz y el Core si sirve su "
                                + "ruta: las dos cosas se mueven juntas o una de las dos miente"));
    }

    @Test
    @DisplayName("lo que le falta al Core sale del catalogo, no de un documento aparte")
    void loQueLeFaltaAlCoreEstaDicho() {
        assertEquals(List.of("consultar_capacidades", "resultados_de_interaccion"),
                Herramientas.sinPuertaEnElCore().stream().map(Herramienta::nombre).toList(),
                "GET /capacidades responde 404 y GET /interacciones/resultados choca con "
                        + "GET /interacciones/{id} y responde 400. KAIROS las invoca igual y "
                        + "trata la respuesta vacia como «no publicado», nunca como «no puedes»");

        Herramientas.sinPuertaEnElCore().forEach(h -> assertTrue(h.implementada(),
                h.nombre() + " la invoca KAIROS: que la llamada vuelva con un 404 es un hecho "
                        + "del Core, no una carencia de KAIROS, y confundirlos haria que "
                        + "implementarla del lado del Core no cambiara nada aqui"));
    }

    @Test
    @DisplayName("las tres lecturas de contrato estan en el catalogo: si no, viajan sin nombre")
    void lasLecturasDeContratoEstanDeclaradas() {
        for (String nombre : List.of("consultar_capacidades", "catalogo_de_captura",
                "resultados_de_interaccion")) {
            Herramienta herramienta = Herramientas.de(nombre).orElseThrow(
                    () -> new AssertionError("falta la lectura de contrato " + nombre));
            assertEquals("GET", herramienta.metodo(), nombre + " pregunta, no escribe");
            assertEquals(Confirmacion.NUNCA, herramienta.confirmacion());
            assertTrue(herramienta.implementada(),
                    nombre + " sale por el cable en cada turno o en cada frase con tipo");
        }
    }
}
