package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Prospeccion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>{@code resultado_propuesta = 'S'} no lo puede producir nadie.</b>
 *
 * <p>{@code S} existe en {@code ck_prospeccion_resultado} desde V5 y nunca tuvo
 * productor. La continuidad comercial que pretendia expresar ya la cubre
 * {@code EstadoProspeccion.SEGUIMIENTO}, que si se produce, asi que se deprecia
 * en vez de darle un productor nuevo.
 *
 * <p>Pero deprecarlo no bastaba. {@code marcarCierre(motivo, resultado)}
 * recibia el codigo como {@code String} libre: aunque hoy sus dos llamadores
 * pasaran {@code R} y {@code null}, la firma dejaba entrar mañana una
 * {@code X}, una {@code Z} o la propia {@code S}. El {@code CHECK} de
 * PostgreSQL lo habria parado, si, pero convirtiendo un error de DOMINIO en un
 * fallo tardio de persistencia. Por eso el metodo desaparecio en favor de dos
 * que dicen su desenlace.
 *
 * <p>Estas pruebas fijan las dos mitades: que ningun camino escribe {@code S},
 * y que ya no existe una puerta por la que colar un valor arbitrario.
 */
class ProspeccionResultadoPropuestaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 8);

    @Test
    @DisplayName("los tres caminos del embudo producen P, A y R; ninguno S")
    void ningunCaminoFuncionalProduceS() {
        Prospeccion propuesta = nueva();
        propuesta.marcarPropuesta(HOY);
        assertEquals(Prospeccion.RESULTADO_PENDIENTE, propuesta.getResultadoPropuesta());

        Prospeccion aceptada = nueva();
        aceptada.marcarPropuesta(HOY);
        aceptada.marcarAceptada(null);
        assertEquals(Prospeccion.RESULTADO_ACEPTADA, aceptada.getResultadoPropuesta());

        Prospeccion rechazada = nueva();
        rechazada.marcarPropuesta(HOY);
        rechazada.marcarRechazoDelPropietario("El propietario no acepta la comision");
        assertEquals(Prospeccion.RESULTADO_RECHAZADA, rechazada.getResultadoPropuesta());
    }

    @Test
    @DisplayName("descartar no inventa un desenlace que el propietario nunca dio")
    void elDescarteDelAgenteNoEscribeResultado() {
        Prospeccion sinPropuesta = nueva();
        sinPropuesta.marcarDescartePorAgente("No contesta desde hace un mes");
        assertNull(sinPropuesta.getResultadoPropuesta());

        // Y si ya habia propuesta, conserva el PENDIENTE: sigue sin responder.
        Prospeccion conPropuesta = nueva();
        conPropuesta.marcarPropuesta(HOY);
        conPropuesta.marcarDescartePorAgente("Se pierde el contacto");
        assertEquals(Prospeccion.RESULTADO_PENDIENTE, conPropuesta.getResultadoPropuesta());
    }

    /**
     * La puerta cerrada: ningun metodo publico de {@code Prospeccion} acepta el
     * codigo de resultado como parametro. Mientras eso se cumpla, el unico
     * vocabulario posible es el que escriben los tres metodos de arriba.
     */
    @Test
    @DisplayName("ningun metodo permite fijar el resultado desde fuera")
    void noQuedaUnaPuertaParaUnValorArbitrario() {
        List<String> sospechosos = Arrays.stream(Prospeccion.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("marcar") || m.getName().startsWith("set"))
                .filter(m -> m.getName().toLowerCase().contains("resultado")
                        || Arrays.stream(m.getParameters())
                                .anyMatch(p -> p.getName().toLowerCase().contains("resultado")))
                .map(Method::getName)
                .toList();

        assertEquals(List.of(), sospechosos,
                "un metodo vuelve a aceptar el resultado desde fuera: el CHECK de PostgreSQL "
                        + "seria la unica defensa, y eso es un fallo tardio de persistencia");
    }

    @Test
    @DisplayName("S sigue siendo legible como historico, pero marcado como deprecado")
    void elCodigoHistoricoSigueDocumentado() throws NoSuchFieldException {
        assertEquals("S", Prospeccion.RESULTADO_RECONTACTAR_HISTORICO);
        assertTrue(Prospeccion.class.getField("RESULTADO_RECONTACTAR_HISTORICO")
                        .isAnnotationPresent(Deprecated.class),
                "el codigo historico tiene que estar marcado como deprecado");
    }

    private static Prospeccion nueva() {
        Prospeccion p = new Prospeccion();
        p.setOrganizacionId(1L);
        return p;
    }
}
