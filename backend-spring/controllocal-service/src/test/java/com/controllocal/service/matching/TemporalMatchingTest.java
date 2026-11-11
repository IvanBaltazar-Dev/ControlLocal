package com.controllocal.service.matching;

import com.controllocal.domain.inmueble.Sujeto;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

class TemporalMatchingTest {
    private final MotorMatchingDeterminista motor = new MotorMatchingDeterminista();
    private static final Instant CORTE = Instant.parse("2026-10-28T15:00:00Z");

    @ParameterizedTest
    @CsvSource({"2026-11-01,2026-11-10,CUMPLE", "2026-10-01,2026-11-01,CUMPLE", "2026-11-10,2026-12-01,CUMPLE",
            "2026-10-01,2026-10-31,INCUMPLE", "2026-11-11,2026-12-01,INCUMPLE", "2026-10-01,2026-12-01,CUMPLE"})
    void solapeFechasInclusivoNoEsIntervaloDeIncertidumbre(LocalDate desde, LocalDate hasta, Conclusion esperado) {
        assertEquals(esperado, motor.evaluarVentana(pedido(), ventana(desde, hasta), ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).conclusion());
    }

    @Test void solapeParcialNoAcreditaOcupacionCompleta() {
        var oferta = ventana(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20));
        assertEquals(CUMPLE, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).conclusion());
        assertEquals(INCUMPLE, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
    }

    @Test void coberturaIncluyeAmbosExtremosYNoExigeUnDiaAdicional() {
        assertEquals(CUMPLE, motor.evaluarVentana(pedido(), pedido(), ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
        assertEquals(CUMPLE, motor.evaluarVentana(pedido(), ventana(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1)), ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
    }

    @Test void finDesconocidoNoSignificaDisponibilidadIndefinida() {
        var oferta = ventana(LocalDate.of(2026, 10, 1), null);
        assertEquals(NO_DETERMINADO, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
        assertEquals(NO_DETERMINADO, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).conclusion());
    }

    @Test void unExtremoConfirmadoDentroDeVentanaDemuestraAlMenosUnInicio() {
        var oferta = ventana(LocalDate.of(2026, 11, 1), null);
        assertEquals(CUMPLE, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).conclusion());
        assertEquals(NO_DETERMINADO, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
    }

    @Test void datoParcialNoImpideDemostrarExclusionTemporal() {
        assertEquals(INCUMPLE, motor.evaluarVentana(pedido(), ventana(null, LocalDate.of(2026, 10, 31)), ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).conclusion());
        assertEquals(INCUMPLE, motor.evaluarVentana(pedido(), ventana(LocalDate.of(2026, 11, 2), null), ComparacionTemporal.COBERTURA_OCUPACION, CORTE).conclusion());
    }

    @Test void estimacionNoSatisfaceUnaFechaCuyaConfirmacionNoSeAcredito() {
        var meta = new MetadatosDato(EstadoDato.CONOCIDO, Causa.VIGENCIA_NO_ACREDITADA, null, null, "estimación TEST", null, null, null, null);
        var oferta = new VentanaFechas(pedido().desde(), pedido().hasta(), meta);
        assertEquals(Causa.VIGENCIA_NO_ACREDITADA, motor.evaluarVentana(pedido(), oferta, ComparacionTemporal.COBERTURA_OCUPACION, CORTE).causa());
    }

    @Test void ventanaContradictoriaNoSeCorrigeInvirtiendoFechas() {
        var invertida = ventana(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1));
        assertThrows(ReglaNegocioException.class, () -> motor.evaluarVentana(invertida, pedido(), ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE));
        assertEquals(Causa.VALOR_INVALIDO, motor.evaluarVentana(pedido(), invertida, ComparacionTemporal.SOLAPAMIENTO_INICIO, CORTE).causa());
    }

    @ParameterizedTest
    @CsvSource({"CUMPLE,CUMPLE,COMERCIAL_CONFIRMADA", "CUMPLE,INCUMPLE,INCOMPATIBLE", "CUMPLE,NO_DETERMINADO,OBLIGATORIAS_PENDIENTES",
            "INCUMPLE,CUMPLE,NO_HABILITADA", "INCUMPLE,INCUMPLE,NO_HABILITADA", "INCUMPLE,NO_DETERMINADO,NO_HABILITADA",
            "NO_DETERMINADO,CUMPLE,HABILITACION_PENDIENTE", "NO_DETERMINADO,INCUMPLE,INCOMPATIBLE", "NO_DETERMINADO,NO_DETERMINADO,HABILITACION_PENDIENTE"})
    void matrizGHD1NoCreaEstadoDeEncargo(Conclusion g, Conclusion h, Categoria categoria) {
        var contexto = new ContextoValor(null, null, null, null, null, null);
        var condicion = new Condicion("requisito", com.controllocal.domain.inmueble.TipoDato.BOOLEANO,
                com.controllocal.domain.comercial.OperadorCriterio.VERDADERO,
                com.controllocal.service.soporte.ValorLogico.deBooleano(true), null, false, contexto, meta());
        var criterio = new Criterio("requisito", "requisito", Exigencia.OBLIGATORIO, null, List.of(condicion));
        List<Dato> datos = h == NO_DETERMINADO ? List.of() : List.of(new Dato("requisito", condicion.tipo(),
                com.controllocal.service.soporte.ValorLogico.deBooleano(h == CUMPLE), null, false, contexto, meta()));
        var oferta = new Oferta("ref", new ClaveCandidato("ámbito TEST", "oferta", "una"), 1, true, g,
                new ConfirmacionDisponibilidad(CORTE, false, "confirmación TEST"), datos);
        var consulta = new Consulta("demanda", 1, Modalidad.COMERCIAL, List.of(criterio), CORTE, "D1-D4-V1");
        assertEquals(categoria, motor.evaluar(consulta, oferta).categoria());
    }

    @Test void selectoresSeparanSujetosYNoSuponenSufijosDeCatalogo() {
        assertEquals("PROPIEDAD:tipo_propiedad", TIPO_PROPIEDAD);
        assertEquals("ENCARGO:total_mensual_obligatorio", TOTAL_MENSUAL_OBLIGATORIO);
        assertNotEquals(selector(Sujeto.PROPIEDAD, "amoblado"), selector(Sujeto.ENCARGO, "amoblado"));
        assertEquals("ENCARGO:clave_privada_real", selector(Sujeto.ENCARGO, "clave_privada_real"));
    }

    @Test void ascensorFisicoNoDemuestraRecorridoOperativoHastaUnidad() {
        var existe = new ResultadoCondicion(CUMPLE, null);
        var funciona = new ResultadoCondicion(NO_DETERMINADO, Causa.DATO_FALTANTE);
        assertEquals(NO_DETERMINADO, motor.evaluarRecorridosAcceso(List.of(List.of(existe, funciona)), true).conclusion());
        var falla = new ResultadoCondicion(INCUMPLE, null);
        assertEquals(INCUMPLE, motor.evaluarRecorridosAcceso(List.of(List.of(existe, falla)), true).conclusion());
    }

    @Test void rutaAlternativaValidaBastaPeroListaIncompletaNoPruebaQueNoHayaAcceso() {
        var falla = new ResultadoCondicion(INCUMPLE, null);
        var cumple = new ResultadoCondicion(CUMPLE, null);
        assertEquals(CUMPLE, motor.evaluarRecorridosAcceso(List.of(List.of(falla), List.of(cumple, cumple)), false).conclusion());
        assertEquals(NO_DETERMINADO, motor.evaluarRecorridosAcceso(List.of(List.of(falla)), false).conclusion());
        assertEquals(INCUMPLE, motor.evaluarRecorridosAcceso(List.of(List.of(falla)), true).conclusion());
    }

    @Test void primerPisoOLaAusenciaDeTramosNoInventanUnRecorridoSinEscalones() {
        assertEquals(NO_DETERMINADO, motor.evaluarRecorridosAcceso(List.of(), true).conclusion());
        assertEquals(NO_DETERMINADO, motor.evaluarRecorridosAcceso(List.of(List.of()), true).conclusion());
    }

    private static VentanaFechas pedido() { return ventana(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)); }
    private static VentanaFechas ventana(LocalDate desde, LocalDate hasta) { return new VentanaFechas(desde, hasta, meta()); }
    private static MetadatosDato meta() { return new MetadatosDato(EstadoDato.CONOCIDO, null, null, null, "fuente TEST", null, null, null, null); }
}
