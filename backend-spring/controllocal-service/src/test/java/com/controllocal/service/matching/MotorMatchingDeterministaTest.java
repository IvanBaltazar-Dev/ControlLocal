package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

class MotorMatchingDeterministaTest {
    private static final Instant CORTE = Instant.parse("2026-10-28T15:00:00Z");
    private static final ContextoValor SIMPLE = new ContextoValor(null, null, null, null, null, null);
    private final MotorMatchingDeterminista motor = new MotorMatchingDeterminista();

    @ParameterizedTest
    @CsvSource({
            "CUMPLE,CUMPLE,CUMPLE,CUMPLE", "CUMPLE,INCUMPLE,INCUMPLE,CUMPLE",
            "CUMPLE,NO_DETERMINADO,NO_DETERMINADO,CUMPLE", "INCUMPLE,CUMPLE,INCUMPLE,CUMPLE",
            "INCUMPLE,INCUMPLE,INCUMPLE,INCUMPLE", "INCUMPLE,NO_DETERMINADO,INCUMPLE,NO_DETERMINADO",
            "NO_DETERMINADO,CUMPLE,NO_DETERMINADO,CUMPLE", "NO_DETERMINADO,INCUMPLE,INCUMPLE,NO_DETERMINADO",
            "NO_DETERMINADO,NO_DETERMINADO,NO_DETERMINADO,NO_DETERMINADO"})
    void tablasD1(Conclusion a, Conclusion b, Conclusion esperadoY, Conclusion esperadoO) {
        assertEquals(esperadoY, MotorMatchingDeterminista.y(a, b));
        assertEquals(esperadoO, MotorMatchingDeterminista.o(a, b));
    }

    @ParameterizedTest
    @CsvSource({"CUMPLE,INCUMPLE", "INCUMPLE,CUMPLE", "NO_DETERMINADO,NO_DETERMINADO"})
    void exclusionNoConvierteDesconocidoEnAusencia(Conclusion entrada, Conclusion esperado) {
        assertEquals(esperado, MotorMatchingDeterminista.no(entrada));
    }

    @Test void codigosExplicitosNoSonOrdinalNiPuntaje() {
        assertEquals(0, NO_DETERMINADO.codigo()); assertEquals(1, CUMPLE.codigo()); assertEquals(2, INCUMPLE.codigo());
        assertEquals(INCUMPLE, Conclusion.desdeCodigo((short) 2));
        assertThrows(IllegalArgumentException.class, () -> Conclusion.desdeCodigo((short) 3));
    }

    @Test void pesosYDenominadorConservanPreferenciaDesconocida() {
        var criterios = List.of(preferencia("a", Prioridad.IMPORTANTE), preferencia("b", null), preferencia("c", Prioridad.COMPLEMENTARIA));
        var e = motor.evaluar(consulta(criterios), oferta("r", List.of(bool("a", true), bool("c", false))));
        assertEquals(3, e.s()); assertEquals(2, e.u()); assertEquals(6, e.w());
        assertEquals(Categoria.COMERCIAL_CONFIRMADA, e.categoria());
        assertEquals(Causa.DATO_FALTANTE, e.criterios().get(1).resultado().causa());
    }

    @Test void obligatoriaNoSumaYNoLaRescatanLasPreferencias() {
        var q = consulta(List.of(obligatoria("acceso"), preferencia("preferida", Prioridad.IMPORTANTE)));
        var falsa = motor.evaluar(q, oferta("r", List.of(bool("acceso", false), bool("preferida", true))));
        assertEquals(Categoria.INCOMPATIBLE, falsa.categoria()); assertEquals(3, falsa.s()); assertEquals(3, falsa.w());
        var pendiente = motor.evaluar(q, oferta("r", List.of(bool("preferida", true))));
        assertEquals(Categoria.OBLIGATORIAS_PENDIENTES, pendiente.categoria()); assertEquals(NO_DETERMINADO, pendiente.h());
    }

    @Test void ceroPreferenciasNoSignificaIncompatibilidadNiCeroPorCiento() {
        var e = motor.evaluar(consulta(List.of()), oferta("r", List.of()));
        assertEquals(CUMPLE, e.h()); assertEquals(0, e.w()); assertEquals(Categoria.COMERCIAL_CONFIRMADA, e.categoria());
    }

    @Test void solicitudFaltantePermaneceEnWPeroCriterioNoMencionadoNoSeInventa() {
        Condicion pendiente = new Condicion("a", TipoDato.BOOLEANO, OperadorCriterio.VERDADERO, null, null, false, SIMPLE, meta(EstadoDato.FALTANTE, null));
        var criterio = new Criterio("a", "a", Exigencia.PREFERENCIA, null, List.of(pendiente));
        var e = motor.evaluar(consulta(List.of(criterio)), oferta("r", List.of(bool("a", true), bool("extra", true))));
        assertEquals(2, e.u()); assertEquals(2, e.w()); assertEquals(1, e.criterios().size());
    }

    @Test void criterioRepetidoPesaUnaVezYContraindicacionesNoSeEligenFavorablemente() {
        Criterio a = preferencia("a", null);
        Criterio copia = new Criterio("otra-fuente", a.clave(), a.exigencia(), a.prioridad(), a.alternativas());
        var q = consulta(List.of(a, copia));
        assertEquals(2, motor.evaluar(q, oferta("r", List.of(bool("a", true)))).w());
        assertThrows(ReglaNegocioException.class, () -> motor.evaluar(consulta(List.of(a, preferencia("a", Prioridad.IMPORTANTE))), oferta("r", List.of())));
    }

    @Test void alternativasORNoSonPaquetesNiNecesitanQueTodasSeanConocidas() {
        var a = condicion("tipo", TipoDato.LISTA, OperadorCriterio.IGUAL, ValorLogico.deTexto("CASA"), null, SIMPLE);
        var b = new Condicion("tipo", TipoDato.LISTA, OperadorCriterio.IGUAL, null, null, false, SIMPLE, meta(EstadoDato.FALTANTE, null));
        var q = consulta(List.of(new Criterio("tipos", "tipos", Exigencia.OBLIGATORIO, null, List.of(a, b))));
        assertEquals(CUMPLE, motor.evaluar(q, oferta("r", List.of(dato("tipo", TipoDato.LISTA, ValorLogico.deTexto("CASA"), SIMPLE)))).h());
        assertThrows(IllegalArgumentException.class, () -> new Criterio("x", "x", Exigencia.OBLIGATORIO, null, List.of()));
    }

    @Test void conflictoActivoNoEsUnIntervaloAunqueTodosLosValoresParecenFavorables() {
        var c = condicion("m2", TipoDato.DECIMAL, OperadorCriterio.MINIMO, numero("80"), null, SIMPLE);
        var datos = List.of(dato("m2", TipoDato.DECIMAL, numero("90"), SIMPLE), dato("m2", TipoDato.DECIMAL, numero("100"), SIMPLE));
        var r = motor.evaluarCriterio(criterio(c), datos, CORTE);
        assertEquals(NO_DETERMINADO, r.conclusion()); assertEquals(Causa.CONFLICTO, r.causa());
    }

    @Test void copiasDelMismoValorNoProducenConflictoNiMasPuntaje() {
        var c = condicion("m2", TipoDato.DECIMAL, OperadorCriterio.MINIMO, numero("80"), null, SIMPLE);
        var datos = List.of(dato("m2", TipoDato.DECIMAL, numero("90.00"), SIMPLE), dato("m2", TipoDato.DECIMAL, numero("90"), SIMPLE));
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), datos, CORTE).conclusion());
    }

    @ParameterizedTest
    @CsvSource({"FALTANTE,DATO_FALTANTE", "NO_APLICABLE,NO_APLICABLE", "AUSENCIA_DECLARADA,AUSENCIA_SIN_VALOR_COMPARABLE"})
    void estadosCapturaNoSeConviertenEnCero(EstadoDato estado, Causa causa) {
        var d = new Dato("a", TipoDato.BOOLEANO, null, null, false, SIMPLE, meta(estado, null));
        var r = motor.evaluarCriterio(obligatoria("a"), List.of(d), CORTE);
        assertEquals(NO_DETERMINADO, r.conclusion()); assertEquals(causa, r.causa());
    }

    @Test void ceroYFalseConocidosSeComparanSinConfundirlosConFaltantes() {
        var c = condicion("ascensores", TipoDato.ENTERO, OperadorCriterio.IGUAL, numero("0"), null, SIMPLE);
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("ascensores", TipoDato.ENTERO, numero("0"), SIMPLE)), CORTE).conclusion());
        var neg = new Condicion("a", TipoDato.BOOLEANO, OperadorCriterio.VERDADERO, ValorLogico.deBooleano(false), null, false, SIMPLE, conocido());
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(neg), List.of(bool("a", false)), CORTE).conclusion());
    }

    @ParameterizedTest
    @CsvSource({"79.99,INCUMPLE", "80,CUMPLE", "120,CUMPLE", "120.01,INCUMPLE"})
    void rangoInclusivoSinRedondeo(String actual, Conclusion esperado) {
        var c = condicion("area", TipoDato.DECIMAL, OperadorCriterio.ENTRE, numero("80"), numero("120"), SIMPLE);
        assertEquals(esperado, motor.evaluarCriterio(criterio(c), List.of(dato("area", TipoDato.DECIMAL, numero(actual), SIMPLE)), CORTE).conclusion());
    }

    @ParameterizedTest
    @CsvSource({"90,110,CUMPLE", "60,70,INCUMPLE", "70,100,NO_DETERMINADO"})
    void intervaloDeclaradoNoSeTrataComoPromedio(String bajo, String alto, Conclusion esperado) {
        var c = condicion("area", TipoDato.DECIMAL, OperadorCriterio.ENTRE, numero("80"), numero("120"), SIMPLE);
        var d = new Dato("area", TipoDato.DECIMAL, numero(bajo), numero(alto), true, SIMPLE, conocido());
        assertEquals(esperado, motor.evaluarCriterio(criterio(c), List.of(d), CORTE).conclusion());
    }

    @Test void limiteParcialPuedeProbarExcesoPeroNoInventarMaximo() {
        var c = condicion("area", TipoDato.DECIMAL, OperadorCriterio.MAXIMO, numero("100"), null, SIMPLE);
        assertEquals(INCUMPLE, motor.evaluarCriterio(criterio(c), List.of(new Dato("area", TipoDato.DECIMAL, numero("110"), null, true, SIMPLE, conocido())), CORTE).conclusion());
        assertEquals(NO_DETERMINADO, motor.evaluarCriterio(criterio(c), List.of(new Dato("area", TipoDato.DECIMAL, numero("90"), null, true, SIMPLE, conocido())), CORTE).conclusion());
    }

    @Test void areaConvierteSoloUnidadExactaYNuncaConceptoDistinto() {
        var pedido = new ContextoValor("AREA_TERRENO", "m2", null, null, null, "ofrecida");
        var actual = new ContextoValor("AREA_TERRENO", "ha", null, null, null, "ofrecida");
        var c = condicion("area", TipoDato.DECIMAL, OperadorCriterio.IGUAL, numero("100"), null, pedido);
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("area", TipoDato.DECIMAL, numero("0.01"), actual)), CORTE).conclusion());
        var construida = new ContextoValor("AREA_CONSTRUIDA", "m2", null, null, null, "ofrecida");
        assertEquals(Causa.CONCEPTO_INCOMPATIBLE, motor.evaluarCriterio(criterio(c), List.of(dato("area", TipoDato.DECIMAL, numero("100"), construida)), CORTE).causa());
    }

    @ParameterizedTest
    @CsvSource({"PEN,MENSUAL,CUMPLE", "USD,MENSUAL,NO_DETERMINADO", "$,MENSUAL,NO_DETERMINADO", "PEN,ANUAL,NO_DETERMINADO"})
    void precioConMonedaPeriodoExactosSinFX(String moneda, String periodo, Conclusion esperado) {
        var pedido = new ContextoValor("RENTA", null, "PEN", "MENSUAL", "ALQUILER", "una");
        var actual = new ContextoValor("RENTA", null, moneda, periodo, "ALQUILER", "una");
        var c = condicion("precio", TipoDato.IMPORTE, OperadorCriterio.MAXIMO, ValorLogico.deImporte(new BigDecimal("2500"), "PEN"), null, pedido);
        assertEquals(esperado, motor.evaluarCriterio(criterio(c), List.of(dato("precio", TipoDato.IMPORTE, ValorLogico.deImporte(new BigDecimal("2500"), moneda), actual)), CORTE).conclusion());
    }

    @Test void noCombinaConfiguracionesNiRedondeaPrecio() {
        var contexto = new ContextoValor("RENTA", null, "PEN", "MENSUAL", "ALQUILER", "una");
        var c = condicion("precio", TipoDato.IMPORTE, OperadorCriterio.MAXIMO, importe("2500"), null, contexto);
        assertEquals(INCUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("precio", TipoDato.IMPORTE, importe("2500.01"), contexto)), CORTE).conclusion());
        var otra = new ContextoValor("RENTA", null, "PEN", "MENSUAL", "ALQUILER", "otra");
        assertEquals(Causa.CONFIGURACION_INCOMPATIBLE, motor.evaluarCriterio(criterio(c), List.of(dato("precio", TipoDato.IMPORTE, importe("1000"), otra)), CORTE).causa());
    }

    @Test void seleccionaContextoCorrectoSinConfundirMonedasDistintasConConflicto() {
        var pen = new ContextoValor("PRECIO", null, "PEN", null, "VENTA", "una");
        var usd = new ContextoValor("PRECIO", null, "USD", null, "VENTA", "una");
        var c = condicion("precio", TipoDato.IMPORTE, OperadorCriterio.MAXIMO, importe("2500"), null, pen);
        var datos = List.of(dato("precio", TipoDato.IMPORTE, ValorLogico.deImporte(new BigDecimal("1000"), "USD"), usd), dato("precio", TipoDato.IMPORTE, importe("2400"), pen));
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), datos, CORTE).conclusion());
    }

    @Test void conjuntosCerradosExactosYExclusionConservaDesconocido() {
        var c = condicion("tipos", TipoDato.LISTA_MULTIPLE, OperadorCriterio.INCLUYE, ValorLogico.deValores(List.of("CASA", "DEPARTAMENTO")), null, SIMPLE);
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("tipos", TipoDato.LISTA_MULTIPLE, ValorLogico.deValores(List.of("CASA")), SIMPLE)), CORTE).conclusion());
        var negada = new Condicion(c.claveDato(), c.tipo(), c.operador(), c.valor(), null, true, SIMPLE, conocido());
        assertEquals(NO_DETERMINADO, motor.evaluarCriterio(criterio(negada), List.of(), CORTE).conclusion());
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(negada), List.of(dato("tipos", TipoDato.LISTA_MULTIPLE, ValorLogico.deConjunto(List.of()), SIMPLE)), CORTE).conclusion());
    }

    @Test void textoCompuestoNoSeInterpretaPorSubstring() {
        var c = condicion("rubro", TipoDato.TEXTO, OperadorCriterio.INCLUYE, ValorLogico.deTexto("cafetería"), null, SIMPLE);
        assertEquals(Causa.CRITERIO_NO_INTERPRETABLE, motor.evaluarCriterio(criterio(c), List.of(dato("rubro", TipoDato.TEXTO, ValorLogico.deTexto("cafetería y panadería"), SIMPLE)), CORTE).causa());
    }

    @Test void fechasSonInclusivasSinInventarHoras() {
        var c = condicion("disponible", TipoDato.FECHA, OperadorCriterio.MAXIMO, ValorLogico.deFecha(LocalDate.of(2026, 11, 1)), null, SIMPLE);
        assertEquals(CUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("disponible", TipoDato.FECHA, ValorLogico.deFecha(LocalDate.of(2026, 11, 1)), SIMPLE)), CORTE).conclusion());
        assertEquals(INCUMPLE, motor.evaluarCriterio(criterio(c), List.of(dato("disponible", TipoDato.FECHA, ValorLogico.deFecha(LocalDate.of(2026, 11, 2)), SIMPLE)), CORTE).conclusion());
    }

    @Test void contratoInvalidoNoSeConvierteEnVacíoYDatoInvalidoNoSeConvierteEnFalse() {
        var invertida = condicion("n", TipoDato.DECIMAL, OperadorCriterio.ENTRE, numero("10"), numero("1"), SIMPLE);
        assertThrows(ReglaNegocioException.class, () -> motor.evaluarCriterio(criterio(invertida), List.of(), CORTE));
        var c = condicion("n", TipoDato.DECIMAL, OperadorCriterio.MINIMO, numero("1"), null, SIMPLE);
        var invalido = new ValorLogico("texto", BigDecimal.TEN, null, null, null, null);
        assertEquals(Causa.VALOR_INVALIDO, motor.evaluarCriterio(criterio(c), List.of(dato("n", TipoDato.DECIMAL, invalido, SIMPLE)), CORTE).causa());
        assertThrows(NullPointerException.class, () -> motor.evaluar(null, oferta("r", List.of())));
    }

    @Test void vigenciaNoAcreditadaNoRecuperaDatoPorSerFavorable() {
        var c = condicion("n", TipoDato.DECIMAL, OperadorCriterio.MINIMO, numero("1"), null, SIMPLE);
        var d = new Dato("n", TipoDato.DECIMAL, numero("100"), null, false, SIMPLE,
                new MetadatosDato(EstadoDato.CONOCIDO, null, "CAT:D/1/1", 1L, "e", "fuente", null, null, CORTE.minusSeconds(1)));
        assertEquals(Causa.VIGENCIA_NO_ACREDITADA, motor.evaluarCriterio(criterio(c), List.of(d), CORTE).causa());
    }

    @Test void frescura37DiasIgualdadVenceYActividadGenericaNoSeConsulta() {
        var c = new ConfirmacionDisponibilidad(CORTE.minus(Duration.ofDays(37)), false, "específica");
        assertEquals(NO_DETERMINADO, motor.disponibilidad(c, CORTE).conclusion());
        assertEquals(CUMPLE, motor.disponibilidad(c, CORTE.minusNanos(1)).conclusion());
        assertEquals(Causa.SIN_CONFIRMACION_DISPONIBILIDAD, motor.disponibilidad(null, CORTE).causa());
        assertEquals(INCUMPLE, motor.disponibilidad(new ConfirmacionDisponibilidad(CORTE, true, "retirada"), CORTE).conclusion());
        assertEquals(Causa.VALOR_INVALIDO, motor.disponibilidad(new ConfirmacionDisponibilidad(CORTE.plusSeconds(1), false, "futuro"), CORTE).causa());
    }

    @Test void caidaSeparaFrescuraYAtencionSinCrearRecordatorio() {
        Instant t0 = CORTE.minus(Duration.ofDays(45)), disponible = t0.plus(Duration.ofDays(40));
        var h = motor.horizontesDisponibilidad(t0, disponible);
        assertEquals(t0.plus(Duration.ofDays(30)), h.solicitarConfirmacionEn());
        assertEquals(t0.plus(Duration.ofDays(37)), h.terminaFrescuraEn());
        assertEquals(t0.plus(Duration.ofDays(46)), h.recordarAtencionEn());
        assertEquals(t0.plus(Duration.ofDays(47)), h.venceAtencionEn());
    }

    @Test void preliminarFlexibilizaEncargoPeroNuncaPermiso() {
        var q = new Consulta("q", 1, Modalidad.PRELIMINAR, List.of(), CORTE, "D1-D4-V1");
        Oferta admitida = new Oferta("r", clave("r"), 1, true, INCUMPLE, null, List.of());
        assertEquals(Categoria.POTENCIAL, motor.evaluar(q, admitida).categoria());
        Oferta prohibida = new Oferta("r", clave("r"), 1, false, INCUMPLE, null, List.of());
        assertThrows(AccesoNoAutorizadoException.class, () -> motor.evaluar(q, prohibida));
    }

    @ParameterizedTest
    @CsvSource({"CUMPLE,COMERCIAL_CONFIRMADA", "NO_DETERMINADO,HABILITACION_PENDIENTE", "INCUMPLE,NO_HABILITADA"})
    void habilitacionNoLaConcedeScore(Conclusion g, Categoria categoria) {
        var o = new Oferta("r", clave("r"), 1, true, g, fresca(), List.of(bool("a", true)));
        assertEquals(categoria, motor.evaluar(consulta(List.of(preferencia("a", Prioridad.IMPORTANTE))), o).categoria());
    }

    @Test void ordenSdescUascReferenciaOpacaSinNombresNiPlanes() {
        var orden = motor.ordenar(List.of(resultado("d", 3, 1, 6), resultado("c", 3, 0, 6), resultado("b", 4, 2, 6), resultado("a", 3, 0, 6)));
        assertEquals(List.of("b", "a", "c", "d"), orden.stream().map(Evaluacion::referenciaEstable).toList());
        assertEquals(List.of("a", "z"), motor.ordenar(List.of(resultado("z", 0, 0, 0), resultado("a", 0, 0, 0))).stream().map(Evaluacion::referenciaEstable).toList());
    }

    @Test void noOrdenaPuntajesDeDemandasCategoriasOVersionesDistintas() {
        var uno = resultado("a", 1, 0, 2);
        var otra = new Evaluacion("otra-demanda", 1, uno.versionRegla(), CORTE, "b", clave("b"), 1,
                Modalidad.COMERCIAL, CUMPLE, CUMPLE, Categoria.COMERCIAL_CONFIRMADA, List.of(), 2, 0, 2, uno.disponibilidad());
        assertThrows(ReglaNegocioException.class, () -> motor.ordenar(List.of(uno, otra)));
        var distintaCategoria = new Evaluacion(uno.consulta(), 1, uno.versionRegla(), CORTE, "b", clave("b"), 1,
                Modalidad.COMERCIAL, CUMPLE, NO_DETERMINADO, Categoria.OBLIGATORIAS_PENDIENTES, List.of(), 1, 0, 2, uno.disponibilidad());
        assertThrows(ReglaNegocioException.class, () -> motor.ordenar(List.of(uno, distintaCategoria)));
    }

    @Test void mismoCandidatoSeDeduplicaPeroOfertasYConfiguracionesDistintasPermanecen() {
        Oferta a = oferta("a", List.of()), b = oferta("b", List.of());
        assertEquals(2, motor.evaluar(consulta(List.of()), List.of(a, a, b)).size());
        Oferta configuracion = new Oferta("c", new ClaveCandidato("ámbito", "a", "otra"), 1, true, CUMPLE, fresca(), List.of());
        assertEquals(2, motor.evaluar(consulta(List.of()), List.of(a, configuracion)).size());
        Oferta version = new Oferta(a.referenciaEstable(), a.clave(), 2, true, CUMPLE, fresca(), List.of());
        assertThrows(IllegalStateException.class, () -> motor.evaluar(consulta(List.of()), List.of(a, version)));
    }

    @Test void proyeccionEsInmutableYValorMultivalorNoComparteListaMutable() {
        var valores = new ArrayList<>(List.of("CASA"));
        var d = dato("tipos", TipoDato.LISTA_MULTIPLE, new ValorLogico(null, null, null, null, null, valores), SIMPLE);
        valores.add("TERRENO");
        assertEquals(List.of("CASA"), d.valor().valores());
        assertThrows(UnsupportedOperationException.class, () -> d.valor().valores().add("OTRO"));
    }

    private static Consulta consulta(List<Criterio> criterios) { return new Consulta("q", 1, Modalidad.COMERCIAL, criterios, CORTE, "D1-D4-V1"); }
    private static Oferta oferta(String ref, List<Dato> datos) { return new Oferta(ref, clave(ref), 1, true, CUMPLE, fresca(), datos); }
    private static ClaveCandidato clave(String ref) { return new ClaveCandidato("ámbito", ref, "única"); }
    private static ConfirmacionDisponibilidad fresca() { return new ConfirmacionDisponibilidad(CORTE.minusSeconds(1), false, "confirmación"); }
    private static MetadatosDato conocido() { return meta(EstadoDato.CONOCIDO, null); }
    private static MetadatosDato meta(EstadoDato estado, Causa causa) { return new MetadatosDato(estado, causa, "CAT:D/1/1", 1L, "evidencia", "fuente TEST", null, null, null); }
    private static ValorLogico numero(String n) { return ValorLogico.deNumero(new BigDecimal(n)); }
    private static ValorLogico importe(String n) { return ValorLogico.deImporte(new BigDecimal(n), "PEN"); }
    private static Dato dato(String clave, TipoDato tipo, ValorLogico valor, ContextoValor contexto) { return new Dato(clave, tipo, valor, null, false, contexto, conocido()); }
    private static Dato bool(String clave, boolean valor) { return dato(clave, TipoDato.BOOLEANO, ValorLogico.deBooleano(valor), SIMPLE); }
    private static Condicion condicion(String clave, TipoDato tipo, OperadorCriterio op, ValorLogico valor, ValorLogico hasta, ContextoValor contexto) { return new Condicion(clave, tipo, op, valor, hasta, false, contexto, conocido()); }
    private static Criterio criterio(Condicion c) { return new Criterio("criterio", "criterio", Exigencia.OBLIGATORIO, null, List.of(c)); }
    private static Criterio preferencia(String clave, Prioridad prioridad) { return new Criterio(clave, clave, Exigencia.PREFERENCIA, prioridad, List.of(condicion(clave, TipoDato.BOOLEANO, OperadorCriterio.VERDADERO, ValorLogico.deBooleano(true), null, SIMPLE))); }
    private static Criterio obligatoria(String clave) { var c = preferencia(clave, null); return new Criterio(clave, clave, Exigencia.OBLIGATORIO, null, c.alternativas()); }
    private static Evaluacion resultado(String ref, long s, long u, long w) { return new Evaluacion("q", 1, "D1-D4-V1", CORTE, ref, clave(ref), 1, Modalidad.COMERCIAL, CUMPLE, CUMPLE, Categoria.COMERCIAL_CONFIRMADA, List.of(), s, u, w, new ResultadoCondicion(CUMPLE, null)); }
}
