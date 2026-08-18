package com.controllocal.service.soporte;

import com.controllocal.service.soporte.PoliticaDeDespacho.Asunto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Los seis criterios, uno a uno, cada uno cambiando el ganador.</b>
 *
 * <p>La forma de estos tests no es casual. Un test que fije el orden de una
 * lista concreta pasa a verde con cualquier comparador que dé la casualidad de
 * producirlo, y deja de comprobar la política: comprueba una salida. Aquí cada
 * caso parte de <b>dos asuntos equivalentes</b> y mueve <b>un solo hecho</b>, así
 * que si ese criterio dejara de pesar, el test cae — y solo ese.
 *
 * <p>Es la misma disciplina que el resto del proyecto aplica a las reglas: quien
 * las declara las prueba. El orden del Inicio es una decisión de negocio, y una
 * decisión de negocio sin test es una opinión.
 */
class PoliticaDeDespachoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 18);
    private static final Function<Asunto, Asunto> TAL_CUAL = a -> a;

    /** Dos asuntos idénticos salvo por lo que cada test mueve. */
    private static Asunto base(long id) {
        return new Asunto(id, true, null, false, false, 0);
    }

    private static List<Long> ordenDe(List<Asunto> asuntos, List<Long> ordenPrevio) {
        return PoliticaDeDespacho.despachar(asuntos, TAL_CUAL, ordenPrevio).stream()
                .map(Asunto::id)
                .toList();
    }

    private static long ganador(List<Asunto> asuntos) {
        return ordenDe(asuntos, List.of()).get(0);
    }

    // ==================================================================
    // Criterio 1 · solo compite lo que depende de mí
    // ==================================================================

    @Test
    @DisplayName("1 · lo que espera a otro no ocupa un puesto del foco, aunque pese más")
    void loQueEsperaAOtroNoCompite() {
        // El que espera al broker tiene TODO a favor: vence hoy, es ocasión,
        // desbloquea y lleva un mes esperando. Y aun así va detrás.
        Asunto esperaAlBroker = new Asunto(1, false, 0, true, true, 30);
        Asunto mio = new Asunto(2, true, null, false, false, 0);

        assertEquals(List.of(2L, 1L), ordenDe(List.of(esperaAlBroker, mio), List.of()),
                "lo accionable va primero: un puesto del foco es para lo que se puede hacer ahora");
    }

    @Test
    @DisplayName("1 · pero sigue en la lista: no compite, no desaparece")
    void loQueEsperaAOtroSigueEnLaLista() {
        Asunto esperaAlBroker = new Asunto(1, false, 0, true, true, 30);
        Asunto mio = new Asunto(2, true, null, false, false, 0);

        assertEquals(2, ordenDe(List.of(esperaAlBroker, mio), List.of()).size(),
                "la cola completa lo enseña; esconderlo seria recortar sin decirlo");
    }

    // ==================================================================
    // Criterio 2 · ventana temporal
    // ==================================================================

    @Test
    @DisplayName("2 · con menos margen se adelanta")
    void menosMargenPesaMas() {
        Asunto venceEn12 = new Asunto(1, true, 12, false, false, 0);
        Asunto venceHoy = new Asunto(2, true, 0, false, false, 0);

        assertEquals(2L, ganador(List.of(venceEn12, venceHoy)));
        assertTrue(PoliticaDeDespacho.peso(venceHoy) > PoliticaDeDespacho.peso(venceEn12),
                "hoy pesa mucho mas que dentro de doce dias (D-E2-1 seccion 3)");
    }

    @Test
    @DisplayName("2 · lo vencido no pesa menos que lo que vence hoy")
    void loVencidoNoSeCastiga() {
        Asunto vencidoHaceTres = new Asunto(1, true, -3, false, false, 0);
        Asunto venceHoy = new Asunto(2, true, 0, false, false, 0);

        assertEquals(PoliticaDeDespacho.peso(venceHoy), PoliticaDeDespacho.peso(vencidoHaceTres),
                "un margen negativo no puede pesar menos que cero: lo vencido urge igual o mas");
    }

    @Test
    @DisplayName("2 · sin plazo no es lo mismo que vencer hoy")
    void sinPlazoNoEsVencerHoy() {
        Asunto sinPlazo = new Asunto(1, true, null, false, false, 0);
        Asunto venceHoy = new Asunto(2, true, 0, false, false, 0);

        assertEquals(2L, ganador(List.of(sinPlazo, venceHoy)));
        assertEquals(0, PoliticaDeDespacho.peso(sinPlazo),
                "un asunto sin plazo no gana peso por la ventana; tampoco lo pierde");
    }

    // ==================================================================
    // Criterio 3 · ventana de oportunidad
    // ==================================================================

    @Test
    @DisplayName("3 · una ocasión adelanta a un vencimiento lejano, y es deliberado")
    void laOcasionSuperaAUnVencimientoLejano() {
        Asunto venceEn10 = new Asunto(1, true, 10, false, false, 0);
        Asunto ocasion = new Asunto(2, true, null, true, false, 0);

        assertEquals(2L, ganador(List.of(venceEn10, ocasion)),
                "algo que acaba de moverse caduca; un vencimiento a diez dias, no");
    }

    @Test
    @DisplayName("3 · pero no adelanta a lo que vence hoy")
    void laOcasionNoSuperaLoQueVenceHoy() {
        Asunto venceHoy = new Asunto(1, true, 0, true, false, 0);
        Asunto soloOcasion = new Asunto(2, true, null, true, false, 0);

        assertEquals(1L, ganador(List.of(venceHoy, soloOcasion)),
                "la ocasion suma, no sustituye: lo que vence hoy sigue delante");
    }

    // ==================================================================
    // Criterio 4 · desbloqueo
    // ==================================================================

    @Test
    @DisplayName("4 · lo que desbloquea un proceso detenido sube")
    void desbloquearSube() {
        Asunto normal = new Asunto(1, true, null, false, false, 0);
        Asunto desbloquea = new Asunto(2, true, null, false, true, 0);

        assertEquals(2L, ganador(List.of(normal, desbloquea)));
    }

    // ==================================================================
    // Criterio 5 · antigüedad accionable, con tope
    // ==================================================================

    @Test
    @DisplayName("5 · esperar gana turno poco a poco")
    void laAntiguedadGanaTurno() {
        Asunto reciente = new Asunto(1, true, null, false, false, 0);
        Asunto viejo = new Asunto(2, true, null, false, false, 9);

        assertEquals(2L, ganador(List.of(reciente, viejo)));
    }

    @Test
    @DisplayName("5 · pero con tope: la antigüedad no manda sola")
    void laAntiguedadTieneTope() {
        Asunto esperandoUnAnio = new Asunto(1, true, null, false, false, 365);
        Asunto esperando12 = new Asunto(2, true, null, false, false, 12);

        assertEquals(PoliticaDeDespacho.peso(esperando12), PoliticaDeDespacho.peso(esperandoUnAnio),
                "pasado el tope, esperar mas no suma: si no, lo mas viejo copaba el foco para siempre");

        // Y el tope es lo que permite que un vencimiento cercano lo adelante.
        Asunto venceHoy = new Asunto(3, true, 0, false, false, 0);
        assertEquals(3L, ganador(List.of(esperandoUnAnio, venceHoy)),
                "un asunto de hace un anio no puede tapar lo que vence hoy");
    }

    // ==================================================================
    // Criterio 6 · estabilidad
    // ==================================================================

    @Test
    @DisplayName("6 · ante empate se conserva el orden anterior: el 01-05 no baila")
    void anteEmpateMandaElOrdenPrevio() {
        Asunto a = base(10);
        Asunto b = base(20);

        assertEquals(List.of(20L, 10L), ordenDe(List.of(a, b), List.of(20L, 10L)),
                "con B delante antes, B sigue delante");
        assertEquals(List.of(10L, 20L), ordenDe(List.of(a, b), List.of(10L, 20L)),
                "y con A delante antes, A sigue delante: lo decide el pasado, no el id");
    }

    @Test
    @DisplayName("6 · entrar nuevo no adelanta a quien ya tenía puesto")
    void loNuevoEntraDetrasDeLosEmpatados() {
        Asunto yaEstaba = base(50);
        Asunto reciénLlegado = base(1);

        assertEquals(List.of(50L, 1L), ordenDe(List.of(reciénLlegado, yaEstaba), List.of(50L)),
                "el id menor no adelanta: quien ya ocupaba su puesto lo conserva");
    }

    @Test
    @DisplayName("6 · sin pasado, el desempate es determinista y no depende del reloj")
    void sinPasadoElDesempateEsEstable() {
        List<Asunto> unOrden = List.of(base(3), base(1), base(2));
        List<Asunto> otroOrden = List.of(base(2), base(3), base(1));

        assertEquals(ordenDe(unOrden, List.of()), ordenDe(otroOrden, List.of()),
                "el orden en que la base devolvio las filas no puede cambiar el resultado");
        assertEquals(List.of(1L, 2L, 3L), ordenDe(unOrden, List.of()));
    }

    @Test
    @DisplayName("6 · la estabilidad NO tapa un cambio real de los criterios")
    void laEstabilidadNoCongelaElOrden() {
        Asunto tranquilo = new Asunto(1, true, null, false, false, 0);
        Asunto seVolvioUrgente = new Asunto(2, true, 0, false, false, 0);

        assertEquals(List.of(2L, 1L), ordenDe(List.of(tranquilo, seVolvioUrgente), List.of(1L, 2L)),
                "conservar el orden es para los EMPATES; si un criterio cambia, el orden cambia");
    }

    // ==================================================================
    // La política entera
    // ==================================================================

    @Test
    @DisplayName("el peso no se publica, pero cada criterio deja su huella")
    void cadaCriterioMueveElPeso() {
        Asunto neutro = base(1);
        assertEquals(0, PoliticaDeDespacho.peso(neutro));

        assertNotEquals(0, PoliticaDeDespacho.peso(new Asunto(1, true, 0, false, false, 0)));
        assertNotEquals(0, PoliticaDeDespacho.peso(new Asunto(1, true, null, true, false, 0)));
        assertNotEquals(0, PoliticaDeDespacho.peso(new Asunto(1, true, null, false, true, 0)));
        assertNotEquals(0, PoliticaDeDespacho.peso(new Asunto(1, true, null, false, false, 5)));
    }

    @Test
    @DisplayName("el foco son cinco, y la política no recorta la lista")
    void laPoliticaNoRecorta() {
        List<Asunto> ocho = List.of(base(1), base(2), base(3), base(4),
                base(5), base(6), base(7), base(8));

        assertEquals(8, ordenDe(ocho, List.of()).size(),
                "recortar aqui esconderia asuntos sin decirlo; el tope lo aplica la pantalla");
        assertEquals(5, PoliticaDeDespacho.ASUNTOS_EN_FOCO);
    }

    @Test
    @DisplayName("el margen se mide contra hoy, y no se inventa cuando no hay plazo")
    void elMargenSeMideContraHoy() {
        assertEquals(3, PoliticaDeDespacho.margenHasta(HOY.plusDays(3), HOY));
        assertEquals(-2, PoliticaDeDespacho.margenHasta(HOY.minusDays(2), HOY));
        assertEquals(null, PoliticaDeDespacho.margenHasta(null, HOY),
                "sin plazo se declara faltante, no se rellena con cero");
    }
}
