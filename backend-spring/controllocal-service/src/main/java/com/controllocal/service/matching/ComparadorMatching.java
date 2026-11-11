package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ValorLogico;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;

/** Comparación pura D3; utiliza los tipos y compatibilidad de operadores existentes. */
final class ComparadorMatching {
    private ComparadorMatching() { }

    static ResultadoCondicion comparar(Condicion condicion, List<Dato> datos, Instant corte) {
        if (!condicion.operador().aplicaA(condicion.tipo()))
            throw new ReglaNegocioException("El operador no interpreta el tipo solicitado.");
        Causa pendiente = pendiente(condicion.metadatos(), corte);
        if (pendiente != null) return nd(pendiente);
        validarPedido(condicion);
        List<Dato> mismaClave = datos.stream().filter(d -> d.clave().equals(condicion.claveDato())).toList();
        if (mismaClave.isEmpty()) return nd(Causa.DATO_FALTANTE);
        List<Dato> compatibles = mismaClave.stream().filter(d -> contexto(condicion, d) == null).toList();
        if (compatibles.isEmpty()) return nd(contexto(condicion, mismaClave.getFirst()));
        if (compatibles.stream().anyMatch(d -> d.metadatos().causa() == Causa.CONFLICTO)) return nd(Causa.CONFLICTO);
        List<Dato> conocidos = compatibles.stream().filter(d -> pendienteDato(d, corte) == null).toList();
        if (conocidos.isEmpty()) return nd(pendienteDato(compatibles.getFirst(), corte));
        if (conocidos.stream().anyMatch(d -> !formaDato(d))) return nd(Causa.VALOR_INVALIDO);
        Dato elegido = conocidos.getFirst();
        if (conocidos.stream().anyMatch(d -> !equivalentes(condicion, elegido, d))) return nd(Causa.CONFLICTO);
        ResultadoCondicion resultado = evaluarDato(condicion, elegido);
        if (resultado.conclusion() == NO_DETERMINADO && elegido.metadatos().causa() != null)
            resultado = nd(elegido.metadatos().causa());
        return condicion.negada() ? negar(resultado) : resultado;
    }

    static Causa pendiente(MetadatosDato meta, Instant corte) {
        if (meta.causa() != null) return meta.causa();
        if (meta.estado() == EstadoDato.FALTANTE) return Causa.DATO_FALTANTE;
        if (meta.estado() == EstadoDato.NO_APLICABLE) return Causa.NO_APLICABLE;
        if (meta.vigenteDesde() != null && corte.isBefore(meta.vigenteDesde())
                || meta.vigenteHasta() != null && !corte.isBefore(meta.vigenteHasta()))
            return Causa.VIGENCIA_NO_ACREDITADA;
        return null;
    }

    private static void validarPedido(Condicion c) {
        boolean conjunto = c.operador() == OperadorCriterio.INCLUYE && c.valor() != null
                && c.valor().valores() != null && forma(TipoDato.LISTA_MULTIPLE, c.valor());
        if ((!conjunto && !forma(c.tipo(), c.valor())) || c.operador() == OperadorCriterio.ENTRE && !forma(c.tipo(), c.hasta()))
            throw new ReglaNegocioException("El criterio conocido no tiene el operando tipado requerido.");
        if (c.operador() != OperadorCriterio.ENTRE && c.hasta() != null)
            throw new ReglaNegocioException("El operador no admite un segundo límite.");
        if (c.operador() == OperadorCriterio.ENTRE && compararEscalares(c.valor(), c.hasta(), BigDecimal.ONE) > 0)
            throw new ReglaNegocioException("Los límites de la consulta son contradictorios.");
        if (c.operador() == OperadorCriterio.INCLUYE && conjunto(c.valor()).isEmpty())
            throw new ReglaNegocioException("Una alternativa solicitada no puede ser un conjunto vacío.");
    }

    private static ResultadoCondicion evaluarDato(Condicion c, Dato d) {
        if (c.tipo() != d.tipo() && !(numeroSinMoneda(c.tipo()) && numeroSinMoneda(d.tipo()))) return nd(Causa.VALOR_INVALIDO);
        if ((!d.intervalo() && !forma(d.tipo(), d.valor()))
                || d.intervalo() && (d.valor() == null && d.hasta() == null
                || d.valor() != null && !forma(d.tipo(), d.valor())
                || d.hasta() != null && !forma(d.tipo(), d.hasta())
                || !(numerico(d.tipo()) || d.tipo() == TipoDato.FECHA))) return nd(Causa.VALOR_INVALIDO);
        BigDecimal factor = factor(d.contexto().unidad(), c.contexto().unidad());
        if (d.intervalo() && d.valor() != null && d.hasta() != null
                && compararEscalares(d.valor(), d.hasta(), BigDecimal.ONE) > 0) return nd(Causa.VALOR_INVALIDO);
        if (numerico(c.tipo()) || c.tipo() == TipoDato.FECHA) return intervalo(c, d, factor);
        if (d.intervalo()) return nd(Causa.VALOR_INVALIDO);
        boolean cumple;
        if (c.operador() == OperadorCriterio.VERDADERO) cumple = d.valor().booleano().equals(c.valor().booleano());
        else if (c.operador() == OperadorCriterio.INCLUYE) {
            Set<String> pedidos = conjunto(c.valor());
            Set<String> actuales = conjunto(d.valor());
            cumple = actuales.stream().anyMatch(pedidos::contains);
            // Un texto libre compuesto no es un código cerrado: no inventar substring/fuzzy.
            if (!cumple && c.tipo() == TipoDato.TEXTO) return nd(Causa.CRITERIO_NO_INTERPRETABLE);
        } else cumple = normal(d.valor().texto()).equals(normal(c.valor().texto()));
        return res(cumple);
    }

    private static Causa pendienteDato(Dato dato, Instant corte) {
        if (dato.metadatos().estado() == EstadoDato.AUSENCIA_DECLARADA && dato.valor() == null && dato.hasta() == null)
            return Causa.AUSENCIA_SIN_VALOR_COMPARABLE;
        // Un límite parcial acreditado no es un conflicto. Puede demostrar exceso aun faltando el resto.
        return dato.intervalo() ? pendienteIntervalo(dato.metadatos(), corte) : pendiente(dato.metadatos(), corte);
    }

    static Causa pendienteIntervalo(MetadatosDato m, Instant corte) {
        if (m.estado() == EstadoDato.CONOCIDO && m.causa() == Causa.DATO_FALTANTE) {
            return pendiente(new MetadatosDato(m.estado(), null, m.referenciaDefinicion(), m.versionDefinicion(),
                    m.evidenciaRef(), m.fuente(), m.observadoEn(), m.vigenteDesde(), m.vigenteHasta()), corte);
        }
        return pendiente(m, corte);
    }

    static boolean formaDato(Dato d) {
        if (!d.intervalo()) return forma(d.tipo(), d.valor());
        return (numerico(d.tipo()) || d.tipo() == TipoDato.FECHA) && (d.valor() != null || d.hasta() != null)
                && (d.valor() == null || forma(d.tipo(), d.valor())) && (d.hasta() == null || forma(d.tipo(), d.hasta()));
    }

    private static ResultadoCondicion intervalo(Condicion c, Dato d, BigDecimal factor) {
        ValorLogico inferior = d.valor();
        ValorLogico superior = d.intervalo() ? d.hasta() : d.valor();
        Integer bajo = inferior == null ? null : compararEscalares(inferior, c.valor(), factor);
        Integer alto = superior == null ? null : compararEscalares(superior, c.valor(), factor);
        return switch (c.operador()) {
            case MINIMO -> bajo != null && bajo >= 0 ? res(true)
                    : alto != null && alto < 0 ? res(false) : nd(Causa.LIMITES_INSUFICIENTES);
            case MAXIMO -> alto != null && alto <= 0 ? res(true)
                    : bajo != null && bajo > 0 ? res(false) : nd(Causa.LIMITES_INSUFICIENTES);
            case IGUAL -> bajo != null && alto != null && bajo == 0 && alto == 0 ? res(true)
                    : bajo != null && bajo > 0 || alto != null && alto < 0 ? res(false) : nd(Causa.LIMITES_INSUFICIENTES);
            case ENTRE -> {
                Integer maximoBajo = inferior == null ? null : compararEscalares(inferior, c.hasta(), factor);
                Integer maximoAlto = superior == null ? null : compararEscalares(superior, c.hasta(), factor);
                if (bajo != null && bajo >= 0 && maximoAlto != null && maximoAlto <= 0) yield res(true);
                if (alto != null && alto < 0 || maximoBajo != null && maximoBajo > 0) yield res(false);
                yield nd(Causa.LIMITES_INSUFICIENTES);
            }
            default -> throw new IllegalStateException("Operador incompatible con intervalo validado.");
        };
    }

    static Causa contexto(Condicion c, Dato d) {
        ContextoValor pedido = c.contexto(), actual = d.contexto();
        // Un código municipal repetido no acredita equivalencia entre jurisdicciones o versiones.
        // CAT:P es una referencia privada del proveedor; su PK no se compara entre organizaciones.
        if (ZONIFICACION.equals(c.claveDato()) && (pedido.jurisdiccion() == null || pedido.jurisdiccion().isBlank()
                || pedido.versionFuente() == null || pedido.versionFuente().isBlank()
                || actual.jurisdiccion() == null || actual.jurisdiccion().isBlank()
                || actual.versionFuente() == null || actual.versionFuente().isBlank()
                || !pedido.jurisdiccion().equals(actual.jurisdiccion())
                || !pedido.versionFuente().equals(actual.versionFuente()))) return Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE;
        if (!igualContexto(pedido.concepto(), actual.concepto())) return Causa.CONCEPTO_INCOMPATIBLE;
        if (!igualContexto(pedido.operacion(), actual.operacion())) return Causa.OPERACION_INCOMPATIBLE;
        if (pedido.configuracion() != null && !Objects.equals(pedido.configuracion(), actual.configuracion())) return Causa.CONFIGURACION_INCOMPATIBLE;
        if (!igualContexto(pedido.periodo(), actual.periodo())) return Causa.PERIODO_INCOMPATIBLE;
        if (factor(actual.unidad(), pedido.unidad()) == null) return Causa.UNIDAD_INCOMPATIBLE;
        if (c.tipo() == TipoDato.IMPORTE) {
            String esperada = moneda(pedido.moneda(), c.valor());
            String recibida = moneda(actual.moneda(), d.valor() == null ? d.hasta() : d.valor());
            if (esperada == null || recibida == null || !esperada.equals(recibida)) return Causa.MONEDA_NO_COMPARABLE;
        }
        return null;
    }

    private static boolean equivalentes(Condicion c, Dato a, Dato b) {
        if (a.tipo() != b.tipo()) return false;
        ValorLogico aMax = a.intervalo() ? a.hasta() : a.valor();
        ValorLogico bMax = b.intervalo() ? b.hasta() : b.valor();
        return iguales(a.valor(), b.valor(), factor(a.contexto().unidad(), b.contexto().unidad()))
                && iguales(aMax, bMax, factor(a.contexto().unidad(), b.contexto().unidad()));
    }

    private static boolean iguales(ValorLogico a, ValorLogico b, BigDecimal factor) {
        if (a == null || b == null) return a == b;
        if (a.numero() != null && b.numero() != null) return a.numero().multiply(factor).compareTo(b.numero()) == 0;
        if (a.fecha() != null || b.fecha() != null) return Objects.equals(a.fecha(), b.fecha());
        if (a.booleano() != null || b.booleano() != null) return Objects.equals(a.booleano(), b.booleano());
        return conjunto(a).equals(conjunto(b));
    }

    static BigDecimal factor(String origen, String destino) {
        if (igualContexto(origen, destino)) return BigDecimal.ONE;
        BigDecimal a = area(origen), b = area(destino);
        return a == null || b == null ? null : a.divide(b);
    }
    private static BigDecimal area(String unidad) {
        return switch (normal(unidad)) {
            case "m2", "m²", "m^2" -> BigDecimal.ONE;
            case "ha", "hectarea", "hectareas" -> new BigDecimal("10000");
            default -> null;
        };
    }
    static String moneda(String contexto, ValorLogico valor) {
        String deValor = valor == null ? null : monedaCanonica(valor.moneda());
        String deContexto = monedaCanonica(contexto);
        if (contexto != null && deContexto == null || valor != null && valor.moneda() != null && deValor == null) return null;
        if (deValor != null && deContexto != null && !deValor.equals(deContexto)) return null;
        return deContexto == null ? deValor : deContexto;
    }
    private static String monedaCanonica(String valor) {
        return switch (normal(valor)) {
            case "pen", "s/", "s/.", "sol", "soles" -> "PEN";
            case "usd", "us$", "dolar", "dolares" -> "USD";
            default -> null;
        };
    }
    private static int compararEscalares(ValorLogico actual, ValorLogico pedido, BigDecimal factor) {
        return pedido.numero() != null ? actual.numero().multiply(factor).compareTo(pedido.numero())
                : actual.fecha().compareTo(pedido.fecha());
    }
    private static boolean forma(TipoDato tipo, ValorLogico v) {
        if (v == null) return false;
        int campos = (v.texto() != null ? 1 : 0) + (v.numero() != null ? 1 : 0) + (v.booleano() != null ? 1 : 0)
                + (v.fecha() != null ? 1 : 0) + (v.valores() != null ? 1 : 0);
        if (campos != 1 || tipo != TipoDato.IMPORTE && v.moneda() != null) return false;
        return switch (tipo) {
            case ENTERO -> v.numero() != null && v.numero().stripTrailingZeros().scale() <= 0;
            case DECIMAL, IMPORTE -> v.numero() != null;
            case BOOLEANO -> v.booleano() != null;
            case FECHA -> v.fecha() != null;
            case TEXTO, LISTA -> v.texto() != null && !v.texto().isBlank();
            case LISTA_MULTIPLE -> v.valores() != null && v.valores().stream().allMatch(s -> s != null && !s.isBlank());
        };
    }
    private static boolean numerico(TipoDato tipo) {
        return tipo == TipoDato.ENTERO || tipo == TipoDato.DECIMAL || tipo == TipoDato.IMPORTE;
    }
    private static boolean numeroSinMoneda(TipoDato tipo) { return tipo == TipoDato.ENTERO || tipo == TipoDato.DECIMAL; }
    private static Set<String> conjunto(ValorLogico valor) {
        return (valor.valores() == null ? valor.texto() == null ? List.<String>of() : List.of(valor.texto()) : valor.valores())
                .stream().map(ComparadorMatching::normal).collect(Collectors.toSet());
    }
    private static boolean igualContexto(String a, String b) { return normal(a).equals(normal(b)); }
    private static String normal(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }
    static ResultadoCondicion nd(Causa causa) { return new ResultadoCondicion(NO_DETERMINADO, causa); }
    static ResultadoCondicion res(boolean cumple) { return new ResultadoCondicion(cumple ? CUMPLE : INCUMPLE, null); }
    static ResultadoCondicion negar(ResultadoCondicion resultado) {
        return resultado.conclusion() == NO_DETERMINADO ? resultado : res(resultado.conclusion() == INCUMPLE);
    }
}
