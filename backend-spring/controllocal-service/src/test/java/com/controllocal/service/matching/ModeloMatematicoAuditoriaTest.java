package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

/** D1–D4/CAT-E20: oráculos documentales y bordes de la auditoría autorizada del 30/10/2026. */
class ModeloMatematicoAuditoriaTest {
    private static final Instant CORTE = Instant.parse("2026-10-30T15:00:00Z");
    private static final ContextoValor SIMPLE = new ContextoValor(null, null, null, null, null, null);
    private static final ContextoValor MENSUAL = new ContextoValor(
            "TOTAL_MENSUAL_OBLIGATORIO", null, "PEN", "MENSUAL", "ALQUILER", "C");
    private final MotorMatchingDeterminista motor = new MotorMatchingDeterminista();

    @ParameterizedTest
    @CsvSource({
            "CUMPLE,CUMPLE,CUMPLE,CUMPLE", "CUMPLE,INCUMPLE,INCUMPLE,CUMPLE",
            "CUMPLE,NO_DETERMINADO,NO_DETERMINADO,CUMPLE", "INCUMPLE,CUMPLE,INCUMPLE,CUMPLE",
            "INCUMPLE,INCUMPLE,INCUMPLE,INCUMPLE", "INCUMPLE,NO_DETERMINADO,INCUMPLE,NO_DETERMINADO",
            "NO_DETERMINADO,CUMPLE,NO_DETERMINADO,CUMPLE", "NO_DETERMINADO,INCUMPLE,INCUMPLE,NO_DETERMINADO",
            "NO_DETERMINADO,NO_DETERMINADO,NO_DETERMINADO,NO_DETERMINADO"
    })
    void nueveFilasDeLaTablaD1(Conclusion a, Conclusion b, Conclusion esperadoY, Conclusion esperadoO) {
        assertEquals(esperadoY, MotorMatchingDeterminista.y(a, b));
        assertEquals(esperadoO, MotorMatchingDeterminista.o(a, b));
    }

    @ParameterizedTest
    @CsvSource({"CUMPLE,INCUMPLE", "INCUMPLE,CUMPLE", "NO_DETERMINADO,NO_DETERMINADO"})
    void negacionDocumentadaConservaLaIndeterminacion(Conclusion a, Conclusion esperado) {
        assertEquals(esperado, MotorMatchingDeterminista.no(a));
        assertEquals(a, MotorMatchingDeterminista.no(MotorMatchingDeterminista.no(a)));
    }

    @Test void algebraTrivalenteNoSeReduceALogicaBinaria() {
        for (Conclusion a : Conclusion.values()) {
            assertEquals(a, MotorMatchingDeterminista.y(a, CUMPLE));
            assertEquals(a, MotorMatchingDeterminista.o(a, INCUMPLE));
            assertEquals(a, MotorMatchingDeterminista.y(a, a));
            assertEquals(a, MotorMatchingDeterminista.o(a, a));
            for (Conclusion b : Conclusion.values()) {
                assertEquals(MotorMatchingDeterminista.y(a, b), MotorMatchingDeterminista.y(b, a));
                assertEquals(MotorMatchingDeterminista.o(a, b), MotorMatchingDeterminista.o(b, a));
                assertEquals(MotorMatchingDeterminista.no(MotorMatchingDeterminista.y(a, b)),
                        MotorMatchingDeterminista.o(MotorMatchingDeterminista.no(a), MotorMatchingDeterminista.no(b)));
                assertEquals(MotorMatchingDeterminista.no(MotorMatchingDeterminista.o(a, b)),
                        MotorMatchingDeterminista.y(MotorMatchingDeterminista.no(a), MotorMatchingDeterminista.no(b)));
                for (Conclusion c : Conclusion.values()) {
                    assertEquals(MotorMatchingDeterminista.y(MotorMatchingDeterminista.y(a, b), c),
                            MotorMatchingDeterminista.y(a, MotorMatchingDeterminista.y(b, c)));
                    assertEquals(MotorMatchingDeterminista.o(MotorMatchingDeterminista.o(a, b), c),
                            MotorMatchingDeterminista.o(a, MotorMatchingDeterminista.o(b, c)));
                }
            }
        }
        assertEquals(NO_DETERMINADO, MotorMatchingDeterminista.o(NO_DETERMINADO,
                MotorMatchingDeterminista.no(NO_DETERMINADO)));
    }

    @ParameterizedTest
    @CsvSource({"-1,NO_DETERMINADO", "0,NO_DETERMINADO", "1,CUMPLE"})
    void vigenciaDeUnInstanteTerminaEnIgualdadSinModificarFechasDeCalendario(long nanos, Conclusion esperado) {
        var hasta = new MetadatosDato(EstadoDato.CONOCIDO, null, "CAT:D:TEST:1", 1L,
                "fuente TEST", null, null, null, CORTE.plusNanos(nanos));
        var condicion = condicionBooleano("ascensor_existe");
        var dato = new Dato("ascensor_existe", TipoDato.BOOLEANO, ValorLogico.deBooleano(true), null,
                false, SIMPLE, hasta);
        var resultado = motor.evaluarCriterio(obligatoria(condicion), List.of(dato), CORTE);
        assertEquals(esperado, resultado.conclusion());
        if (esperado == NO_DETERMINADO) assertEquals(Causa.VIGENCIA_NO_ACREDITADA, resultado.causa());
    }

    @Test void limiteMensualAcreditadoNoSeBorraPorFaltarElExtremoSuperior() {
        var parcial = new Dato("cargo", TipoDato.IMPORTE, importe("2600"), null, true, MENSUAL,
                new MetadatosDato(EstadoDato.CONOCIDO, Causa.DATO_FALTANTE, null, null,
                        "cota inferior acreditada TEST", null, null, null, null));
        var total = ComposicionMensualMatching.total("total", List.of(new ComponenteMensual(
                "cargo-exigible", parcial, TratamientoMensual.EXIGIBLE)), true, MENSUAL, conocido(), CORTE);
        assertNotNull(total.valor(), "La cota inferior está acreditada, no es un cero supuesto");
        assertEquals(0, new BigDecimal("2600").compareTo(total.valor().numero()));
        assertNull(total.hasta(), "No se inventa un máximo");
        assertEquals(INCUMPLE, evaluarPresupuesto(total, "2500").conclusion());
    }

    @Test void conflictoMensualNoSeConvierteEnIntervaloAunqueSuNumeroExcedaElPresupuesto() {
        var conflictivo = new Dato("cargo", TipoDato.IMPORTE, importe("2600"), null, true, MENSUAL,
                new MetadatosDato(EstadoDato.CONOCIDO, Causa.CONFLICTO, null, null,
                        "valores en conflicto TEST", null, null, null, null));
        var total = ComposicionMensualMatching.total("total", List.of(new ComponenteMensual(
                "cargo-exigible", conflictivo, TratamientoMensual.EXIGIBLE)), true, MENSUAL, conocido(), CORTE);
        var resultado = evaluarPresupuesto(total, "2500");
        assertEquals(NO_DETERMINADO, resultado.conclusion());
        assertEquals(Causa.CONFLICTO, resultado.causa());
    }

    @ParameterizedTest
    @CsvSource({"CUMPLE,CUMPLE,COMERCIAL_CONFIRMADA", "CUMPLE,INCUMPLE,INCOMPATIBLE",
            "CUMPLE,NO_DETERMINADO,OBLIGATORIAS_PENDIENTES", "INCUMPLE,CUMPLE,NO_HABILITADA",
            "INCUMPLE,INCUMPLE,NO_HABILITADA", "INCUMPLE,NO_DETERMINADO,NO_HABILITADA",
            "NO_DETERMINADO,CUMPLE,HABILITACION_PENDIENTE", "NO_DETERMINADO,INCUMPLE,INCOMPATIBLE",
            "NO_DETERMINADO,NO_DETERMINADO,HABILITACION_PENDIENTE"})
    void nueveFilasDeElegibilidadNoSeCompensanConPreferencias(Conclusion g, Conclusion h, Categoria categoria) {
        var consulta = consulta(List.of(obligatoria(condicionBooleano("obligacion")), preferencia("ventaja", Prioridad.IMPORTANTE)));
        var datos = new ArrayList<Dato>();
        datos.add(booleano("ventaja", true));
        if (h != NO_DETERMINADO) datos.add(booleano("obligacion", h == CUMPLE));
        var oferta = new Oferta("ref", new ClaveCandidato("TEST", "oferta", "C"), 1,
                true, g, fresca(), datos);
        var resultado = motor.evaluar(consulta, oferta);
        assertEquals(g, resultado.g());
        assertEquals(h, resultado.h());
        assertEquals(3, resultado.s());
        assertEquals(categoria, resultado.categoria());
    }

    @Test void ejemploAprobadoCatE20ConservaCincoYUnoFrenteACuatroYCero() {
        var consulta = consulta(List.of(preferencia("ascensor", Prioridad.IMPORTANTE),
                preferencia("cochera", Prioridad.NORMAL), preferencia("balcon", Prioridad.COMPLEMENTARIA)));
        var a = motor.evaluar(consulta, oferta("A", List.of(booleano("ascensor", true), booleano("cochera", true))));
        var b = motor.evaluar(consulta, oferta("B", List.of(booleano("ascensor", true), booleano("cochera", false), booleano("balcon", true))));
        assertEquals(List.of(5L, 1L, 6L), List.of(a.s(), a.u(), a.w()));
        assertEquals(List.of(4L, 0L, 6L), List.of(b.s(), b.u(), b.w()));
        assertEquals(List.of("A", "B"), motor.ordenar(List.of(b, a)).stream().map(Evaluacion::referenciaEstable).toList());
    }

    @Test void preferenciasYOrdenCoincidenConPapeletasYUnRangoEnteroIndependiente() {
        // El oráculo cuenta papeletas: tres por importante, dos por normal,
        // una por complementaria. No llama a peso(), al comparador ni al motor
        // para obtener el resultado esperado. El rango entero ordena en un
        // único eje, sin copiar la cadena de comparadores de producción.
        int[] papeletas = {3, 2, 1, 3, 2, 1};
        Prioridad[] prioridades = {Prioridad.IMPORTANTE, Prioridad.NORMAL, Prioridad.COMPLEMENTARIA,
                Prioridad.IMPORTANTE, Prioridad.NORMAL, Prioridad.COMPLEMENTARIA};
        var criterios = new ArrayList<Criterio>();
        for (int i = 0; i < prioridades.length; i++) criterios.add(preferencia("p" + i, prioridades[i]));
        var consulta = consulta(criterios);
        int total = 12, candidatos = 729; // Todas las 3^6 respuestas posibles, no una muestra.
        var resultados = new ArrayList<Evaluacion>();
        var esperadoPorRango = new TreeMap<Long, String>();
        for (int i = 0; i < candidatos; i++) {
            long cumplen = 0, pendientes = 0;
            int vector = i;
            var datos = new ArrayList<Dato>();
            for (int j = 0; j < prioridades.length; j++) {
                int respuesta = vector % 3; // 0 sí, 1 no, 2 desconocido.
                vector /= 3;
                if (respuesta != 2) datos.add(booleano("p" + j, respuesta == 0));
                for (int papeleta = 0; papeleta < papeletas[j]; papeleta++) {
                    if (respuesta == 0) cumplen++;
                    if (respuesta == 2) pendientes++;
                }
            }
            String ref = "ref-%03d".formatted(i);
            var evaluacion = motor.evaluar(consulta, oferta(ref, datos));
            assertEquals(cumplen, evaluacion.s(), ref);
            assertEquals(pendientes, evaluacion.u(), ref);
            assertEquals(total, evaluacion.w(), ref);
            assertEquals(Categoria.COMERCIAL_CONFIRMADA, evaluacion.categoria(), ref);
            long rango = ((total - cumplen) * (total + 1L) + pendientes) * (candidatos + 1L) + i;
            assertNull(esperadoPorRango.put(rango, ref));
            resultados.add(evaluacion);
        }
        Collections.shuffle(resultados, new Random(0xC3L));
        var orden = motor.ordenar(resultados).stream().map(Evaluacion::referenciaEstable).toList();
        assertEquals(List.copyOf(esperadoPorRango.values()), orden);
        Collections.reverse(resultados);
        assertEquals(orden, motor.ordenar(resultados).stream().map(Evaluacion::referenciaEstable).toList());
    }

    @Test void sinPreferenciasElOrdenEsSoloLaReferenciaYNoHayDenominadorFicticio() {
        var consulta = consulta(List.of());
        var resultados = List.of(motor.evaluar(consulta, oferta("C", List.of())),
                motor.evaluar(consulta, oferta("A", List.of())), motor.evaluar(consulta, oferta("B", List.of())));
        for (var resultado : resultados) assertEquals(List.of(0L, 0L, 0L), List.of(resultado.s(), resultado.u(), resultado.w()));
        assertEquals(List.of("A", "B", "C"), motor.ordenar(resultados).stream().map(Evaluacion::referenciaEstable).toList());
    }

    private ResultadoCondicion evaluarPresupuesto(Dato total, String limite) {
        var condicion = new Condicion("total", TipoDato.IMPORTE, OperadorCriterio.MAXIMO,
                importe(limite), null, false, MENSUAL, conocido());
        return motor.evaluarCriterio(obligatoria(condicion), List.of(total), CORTE);
    }
    private static Consulta consulta(List<Criterio> criterios) {
        return new Consulta("consulta-auditoria", 1, Modalidad.COMERCIAL, criterios, CORTE, "D1-D4-V1");
    }
    private static Oferta oferta(String ref, List<Dato> datos) {
        return new Oferta(ref, new ClaveCandidato("TEST", ref, "C"), 1, true, CUMPLE, fresca(), datos);
    }
    private static ConfirmacionDisponibilidad fresca() { return new ConfirmacionDisponibilidad(CORTE, false, "TEST"); }
    private static MetadatosDato conocido() { return new MetadatosDato(EstadoDato.CONOCIDO, null, null, null, "TEST", null, null, null, null); }
    private static ValorLogico importe(String numero) { return ValorLogico.deImporte(new BigDecimal(numero), "PEN"); }
    private static Condicion condicionBooleano(String clave) {
        return new Condicion(clave, TipoDato.BOOLEANO, OperadorCriterio.VERDADERO,
                ValorLogico.deBooleano(true), null, false, SIMPLE, conocido());
    }
    private static Dato booleano(String clave, boolean valor) {
        return new Dato(clave, TipoDato.BOOLEANO, ValorLogico.deBooleano(valor), null, false, SIMPLE, conocido());
    }
    private static Criterio obligatoria(Condicion condicion) {
        return new Criterio(condicion.claveDato(), condicion.claveDato(), Exigencia.OBLIGATORIO, null, List.of(condicion));
    }
    private static Criterio preferencia(String clave, Prioridad prioridad) {
        return new Criterio(clave, clave, Exigencia.PREFERENCIA, prioridad, List.of(condicionBooleano(clave)));
    }
}
