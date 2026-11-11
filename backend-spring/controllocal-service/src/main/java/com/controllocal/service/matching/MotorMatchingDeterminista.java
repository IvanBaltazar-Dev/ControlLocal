package com.controllocal.service.matching;

import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static com.controllocal.service.matching.ComparadorMatching.*;

/**
 * Único evaluador puro inicial D1–D4. No consulta repositorios, documentos, clientes ni IA,
 * no concede autoridad y no crea hechos comerciales. La entrega/paginación revalida afuera.
 */
@Component
public class MotorMatchingDeterminista {

    public Evaluacion evaluar(Consulta consulta, Oferta oferta) {
        return evaluar(consulta,oferta,Objects.requireNonNull(consulta,"Consulta normalizada.").corte());
    }

    /** D4: los criterios conservan su corte; G/D2 se revalidan al instante actual de entrega. */
    public Evaluacion evaluar(Consulta consulta, Oferta oferta, Instant elegibilidadEn) {
        Objects.requireNonNull(consulta, "Consulta normalizada.");
        Objects.requireNonNull(oferta, "Oferta minimizada.");
        Objects.requireNonNull(elegibilidadEn,"Instante Core de elegibilidad.");
        // Acceso no es habilitación comercial: también precede a todo en PRELIMINAR.
        if (!oferta.accesoAutorizado()) throw new AccesoNoAutorizadoException("No puedes consultar este recurso.");
        if (oferta.datos().stream().anyMatch(d -> d.contexto().configuracion() != null
                && !d.contexto().configuracion().equals(oferta.clave().configuracion())))
            throw new IllegalStateException("La proyección mezcla datos de otra configuración comercial.");
        List<Criterio> unicos = criteriosUnicos(consulta.criterios());
        List<EvaluacionCriterio> resultados = new ArrayList<>();
        Conclusion h = consulta.coberturaCriterios()==CoberturaCriterios.PARCIAL?NO_DETERMINADO:CUMPLE;
        long s = 0, u = 0, w = 0;
        for (Criterio criterio : unicos) {
            ResultadoCondicion resultado = evaluarCriterio(criterio, oferta.datos(), consulta.corte());
            resultados.add(new EvaluacionCriterio(criterio.referencia(), criterio.clave(), criterio.exigencia(), criterio.peso(), resultado));
            if (criterio.exigencia() == Exigencia.OBLIGATORIO) h = y(h, resultado.conclusion());
            else {
                w += criterio.peso();
                if (resultado.conclusion() == CUMPLE) s += criterio.peso();
                if (resultado.conclusion() == NO_DETERMINADO) u += criterio.peso();
            }
        }
        ResultadoCondicion disponibilidad = disponibilidad(oferta.disponibilidad(), elegibilidadEn);
        Conclusion g = y(oferta.habilitacion(), disponibilidad.conclusion());
        return new Evaluacion(consulta.referencia(), consulta.version(), consulta.versionRegla(), consulta.corte(),
                oferta.referenciaEstable(), oferta.clave(), oferta.version(), consulta.modalidad(), g, h,
                categoria(consulta.modalidad(), g, h), resultados, s, u, w, disponibilidad,consulta.coberturaCriterios());
    }

    /** Deduplicación exacta D4. Una versión incoherente es un fallo, nunca elige la favorable. */
    public List<Evaluacion> evaluar(Consulta consulta, List<Oferta> candidatas) {
        Objects.requireNonNull(consulta);
        LinkedHashMap<ClaveCandidato, Oferta> unicas = new LinkedHashMap<>();
        for (Oferta oferta : List.copyOf(candidatas)) {
            if (!oferta.accesoAutorizado()) throw new AccesoNoAutorizadoException("No puedes consultar este recurso.");
            Oferta anterior = unicas.putIfAbsent(oferta.clave(), oferta);
            if (anterior != null && !mismaOferta(anterior, oferta))
                throw new IllegalStateException("La misma oferta/configuración llegó con versiones o proyecciones incompatibles.");
        }
        return unicas.values().stream().map(oferta -> evaluar(consulta, oferta)).toList();
    }

    /** D4 ordena UNA consulta y UNA categoría. No crea una prioridad entre categorías/búsquedas. */
    public List<Evaluacion> ordenar(List<Evaluacion> evaluaciones) {
        List<Evaluacion> entrada = List.copyOf(evaluaciones);
        if (entrada.isEmpty()) return entrada;
        Evaluacion primera = entrada.getFirst();
        for (Evaluacion otra : entrada) {
            if (!primera.consulta().equals(otra.consulta()) || primera.versionConsulta() != otra.versionConsulta()
                    || !primera.versionRegla().equals(otra.versionRegla()) || !primera.corte().equals(otra.corte())
                    || primera.modalidad() != otra.modalidad() || primera.categoria() != otra.categoria()
                    || primera.w() != otra.w() || primera.coberturaCriterios()!=otra.coberturaCriterios())
                throw new ReglaNegocioException("No se comparan puntajes de búsquedas o categorías diferentes.");
        }
        Comparator<Evaluacion> orden = primera.w() == 0 ? Comparator.comparing(Evaluacion::referenciaEstable)
                : Comparator.comparingLong(Evaluacion::s).reversed().thenComparingLong(Evaluacion::u)
                .thenComparing(Evaluacion::referenciaEstable);
        return entrada.stream().sorted(orden).toList();
    }

    public ResultadoCondicion evaluarCriterio(Criterio criterio, List<Dato> datos, Instant corte) {
        ResultadoCondicion resultado = res(false);
        Causa causa = null;
        for (Condicion alternativa : criterio.alternativas()) {
            ResultadoCondicion actual = comparar(alternativa, datos, corte);
            if (actual.conclusion() == CUMPLE) return actual;
            if (actual.conclusion() == NO_DETERMINADO && causa == null) causa = actual.causa();
            resultado = new ResultadoCondicion(o(resultado.conclusion(), actual.conclusion()),
                    o(resultado.conclusion(), actual.conclusion()) == NO_DETERMINADO ? causa : null);
        }
        return resultado;
    }

    /** D2: 37 días exactos de24h; igualdad vence, sin depender del job o del reloj de atención. */
    public ResultadoCondicion disponibilidad(ConfirmacionDisponibilidad confirmacion, Instant corte) {
        Objects.requireNonNull(corte);
        if (confirmacion != null && confirmacion.indeterminacion() != null) return nd(confirmacion.indeterminacion());
        if (confirmacion != null && confirmacion.indisponibilidadConocida()) return res(false);
        if (confirmacion == null || confirmacion.confirmadaEn() == null) return nd(Causa.SIN_CONFIRMACION_DISPONIBILIDAD);
        if (confirmacion.confirmadaEn().isAfter(corte)) return nd(Causa.VALOR_INVALIDO);
        return corte.isBefore(confirmacion.confirmadaEn().plus(Duration.ofDays(37)))
                ? res(true) : nd(Causa.CONFIRMACION_NO_VIGENTE);
    }

    /** Sólo aritmética de los relojes D2. No programa una tarea ni un recordatorio. */
    public HorizontesDisponibilidad horizontesDisponibilidad(Instant confirmadaEn, Instant tareaDisponibleEn) {
        return new HorizontesDisponibilidad(
                confirmadaEn == null ? null : confirmadaEn.plus(Duration.ofDays(30)),
                confirmadaEn == null ? null : confirmadaEn.plus(Duration.ofDays(37)),
                tareaDisponibleEn == null ? null : tareaDisponibleEn.plus(Duration.ofDays(6)),
                tareaDisponibleEn == null ? null : tareaDisponibleEn.plus(Duration.ofDays(7)));
    }

    /** D3-52/53: distingue una fecha común de la cobertura de TODA la ocupación. */
    public ResultadoCondicion evaluarVentana(VentanaFechas pedido, VentanaFechas oferta,
                                              ComparacionTemporal modo, Instant corte) {
        Objects.requireNonNull(modo); Objects.requireNonNull(corte);
        Causa causaPedido = ComparadorMatching.pendiente(pedido.metadatos(), corte);
        if (causaPedido != null) return nd(causaPedido);
        if (pedido.desde() == null || pedido.hasta() == null) return nd(Causa.DATO_FALTANTE);
        if (pedido.hasta().isBefore(pedido.desde())) throw new ReglaNegocioException("La ventana solicitada es incoherente.");
        Causa causa = ComparadorMatching.pendienteIntervalo(oferta.metadatos(), corte);
        if (causa != null) return nd(causa);
        if (oferta.desde() != null && oferta.hasta() != null && oferta.hasta().isBefore(oferta.desde())) return nd(Causa.VALOR_INVALIDO);
        if (modo == ComparacionTemporal.COBERTURA_OCUPACION) {
            if (oferta.desde() != null && oferta.desde().isAfter(pedido.desde())
                    || oferta.hasta() != null && oferta.hasta().isBefore(pedido.hasta())) return res(false);
            if (oferta.desde() != null && oferta.hasta() != null) return res(true);
        } else {
            if (oferta.hasta() != null && oferta.hasta().isBefore(pedido.desde())
                    || oferta.desde() != null && oferta.desde().isAfter(pedido.hasta())) return res(false);
            if (oferta.desde() != null && !oferta.desde().isBefore(pedido.desde()) && !oferta.desde().isAfter(pedido.hasta())
                    || oferta.hasta() != null && !oferta.hasta().isBefore(pedido.desde()) && !oferta.hasta().isAfter(pedido.hasta())
                    || oferta.desde() != null && oferta.hasta() != null) return res(true);
        }
        return nd(oferta.metadatos().causa() == null ? Causa.LIMITES_INSUFICIENTES : oferta.metadatos().causa());
    }

    /**
     * D3-45–49: Y de tramos de un recorrido, O de recorridos declarados/admitidos.
     * No certifica accesibilidad ni infiere un recorrido por piso/ascensor/fotografía.
     * La falta de cobertura impide una negativa general, no una positiva demostrada.
     */
    public ResultadoCondicion evaluarRecorridosAcceso(List<List<ResultadoCondicion>> recorridos,
                                                       boolean coberturaSuficiente) {
        if (recorridos.isEmpty()) return nd(Causa.DATO_FALTANTE);
        Conclusion acceso = INCUMPLE;
        Causa indeterminacion = null;
        for (List<ResultadoCondicion> recorrido : recorridos) {
            Conclusion tramo = recorrido.isEmpty() ? NO_DETERMINADO : CUMPLE;
            Causa causa = recorrido.isEmpty() ? Causa.DATO_FALTANTE : null;
            for (ResultadoCondicion segmento : recorrido) {
                tramo = y(tramo, segmento.conclusion());
                if (segmento.conclusion() == NO_DETERMINADO && causa == null) causa = segmento.causa();
            }
            acceso = o(acceso, tramo);
            if (acceso == CUMPLE) return res(true);
            if (tramo == NO_DETERMINADO && indeterminacion == null) indeterminacion = causa;
        }
        if (acceso == NO_DETERMINADO) return nd(indeterminacion);
        return coberturaSuficiente ? res(false) : nd(Causa.LIMITES_INSUFICIENTES);
    }

    public static Conclusion y(Conclusion a, Conclusion b) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        if (a == INCUMPLE || b == INCUMPLE) return INCUMPLE;
        return a == NO_DETERMINADO || b == NO_DETERMINADO ? NO_DETERMINADO : CUMPLE;
    }
    public static Conclusion o(Conclusion a, Conclusion b) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        if (a == CUMPLE || b == CUMPLE) return CUMPLE;
        return a == NO_DETERMINADO || b == NO_DETERMINADO ? NO_DETERMINADO : INCUMPLE;
    }
    public static Conclusion no(Conclusion a) {
        Objects.requireNonNull(a);
        return a == NO_DETERMINADO ? NO_DETERMINADO : a == CUMPLE ? INCUMPLE : CUMPLE;
    }

    private static Categoria categoria(Modalidad modalidad, Conclusion g, Conclusion h) {
        if (modalidad == Modalidad.PRELIMINAR) return h == INCUMPLE ? Categoria.INCOMPATIBLE : Categoria.POTENCIAL;
        if (g == INCUMPLE) return Categoria.NO_HABILITADA;
        if (h == INCUMPLE) return Categoria.INCOMPATIBLE;
        if (g == NO_DETERMINADO) return Categoria.HABILITACION_PENDIENTE;
        return h == NO_DETERMINADO ? Categoria.OBLIGATORIAS_PENDIENTES : Categoria.COMERCIAL_CONFIRMADA;
    }

    private static List<Criterio> criteriosUnicos(List<Criterio> criterios) {
        LinkedHashMap<String, Criterio> unicos = new LinkedHashMap<>();
        for (Criterio criterio : criterios) {
            Criterio anterior = unicos.putIfAbsent(criterio.clave(), criterio);
            if (anterior != null && (anterior.exigencia() != criterio.exigencia() || anterior.prioridad() != criterio.prioridad()
                    || !new LinkedHashSet<>(anterior.alternativas()).equals(new LinkedHashSet<>(criterio.alternativas()))))
                throw new ReglaNegocioException("El criterio repetido contiene instrucciones incompatibles; precisa la consulta.");
        }
        return List.copyOf(unicos.values());
    }

    private static boolean mismaOferta(Oferta a, Oferta b) {
        return a.referenciaEstable().equals(b.referenciaEstable()) && a.version() == b.version()
                && a.habilitacion() == b.habilitacion() && Objects.equals(a.disponibilidad(), b.disponibilidad())
                && new LinkedHashSet<>(a.datos()).equals(new LinkedHashSet<>(b.datos()));
    }
}
