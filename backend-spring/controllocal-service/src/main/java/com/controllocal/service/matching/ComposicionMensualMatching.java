package com.controllocal.service.matching;

import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static com.controllocal.service.matching.ContratoMatching.*;

/** D3-07–17: total del alcance mensual explícito, sin prorratear cargos iniciales ni convertir monedas. */
public final class ComposicionMensualMatching {
    private ComposicionMensualMatching() { }

    public static Dato total(String clave, List<ComponenteMensual> componentes, boolean componentesCompletos, ContextoValor contexto,
                             MetadatosDato metadatos, Instant corte) {
        String moneda = ComparadorMatching.moneda(contexto.moneda(), null);
        if (moneda == null) return pendiente(clave, contexto, metadatos, Causa.MONEDA_NO_COMPARABLE);
        if (!"MENSUAL".equalsIgnoreCase(contexto.periodo()))
            return pendiente(clave, contexto, metadatos, Causa.PERIODO_INCOMPATIBLE);
        LinkedHashMap<String, ComponenteMensual> unicos = new LinkedHashMap<>();
        for (ComponenteMensual componente : List.copyOf(componentes)) {
            ComponenteMensual anterior = unicos.putIfAbsent(componente.referencia(), componente);
            if (anterior != null && !anterior.equals(componente))
                throw new IllegalStateException("El componente económico se repite con contenido incompatible.");
        }
        if (unicos.isEmpty()) return pendiente(clave, contexto, metadatos, Causa.DATO_FALTANTE);
        if (unicos.values().stream().noneMatch(c -> c.tratamiento() == TratamientoMensual.EXIGIBLE))
            return pendiente(clave, contexto, metadatos, Causa.DATO_FALTANTE);
        // Enumerar sólo los cargos conocidos no acredita que no exista mantenimiento u otro fijo.
        BigDecimal minimo = BigDecimal.ZERO, maximo = componentesCompletos ? BigDecimal.ZERO : null;
        boolean parcial = !componentesCompletos;
        for (ComponenteMensual componente : unicos.values()) {
            if (componente.tratamiento() == TratamientoMensual.INCLUIDO_EN_OTRO) continue;
            Dato d = componente.dato();
            if (!"MENSUAL".equalsIgnoreCase(d.contexto().periodo()))
                return pendiente(clave, contexto, metadatos, Causa.PERIODO_INCOMPATIBLE);
            if (!java.util.Objects.equals(contexto.unidad(), d.contexto().unidad()))
                return pendiente(clave, contexto, metadatos, Causa.UNIDAD_INCOMPATIBLE);
            if (!java.util.Objects.equals(contexto.configuracion(), d.contexto().configuracion())
                    || !java.util.Objects.equals(contexto.operacion(), d.contexto().operacion()))
                return pendiente(clave, contexto, metadatos, Causa.CONFIGURACION_INCOMPATIBLE);
            if (!moneda.equals(ComparadorMatching.moneda(d.contexto().moneda(), d.valor() == null ? d.hasta() : d.valor())))
                return pendiente(clave, contexto, metadatos, Causa.MONEDA_NO_COMPARABLE);
            Causa causa = d.intervalo() ? ComparadorMatching.pendienteIntervalo(d.metadatos(), corte)
                    : ComparadorMatching.pendiente(d.metadatos(), corte);
            if (causa != null && causa != Causa.DATO_FALTANTE) return pendiente(clave, contexto, metadatos, causa);
            boolean desconocido = causa != null || d.valor() == null && d.hasta() == null;
            if (d.tipo() != TipoDato.IMPORTE) return pendiente(clave, contexto, metadatos, Causa.VALOR_INVALIDO);
            if (!desconocido && !ComparadorMatching.formaDato(d)) return pendiente(clave, contexto, metadatos, Causa.VALOR_INVALIDO);
            BigDecimal bajo = desconocido ? BigDecimal.ZERO : d.valor() == null ? BigDecimal.ZERO : d.valor().numero();
            BigDecimal alto = desconocido ? null : d.intervalo() ? d.hasta() == null ? null : d.hasta().numero() : bajo;
            if (bajo == null || bajo.signum() < 0 || alto != null && (alto.signum() < 0 || bajo.compareTo(alto) > 0))
                return pendiente(clave, contexto, metadatos, Causa.VALOR_INVALIDO);
            parcial |= desconocido || alto == null || bajo.compareTo(alto) != 0;
            if (componente.tratamiento() == TratamientoMensual.DESCUENTO_FIRME) {
                minimo = minimo == null || alto == null ? null : minimo.subtract(alto);
                maximo = maximo == null ? null : maximo.subtract(bajo);
            } else {
                minimo = minimo == null ? null : minimo.add(bajo);
                maximo = maximo == null || alto == null ? null : maximo.add(alto);
            }
        }
        if (minimo == null && maximo == null) return pendiente(clave, contexto, metadatos, Causa.DATO_FALTANTE);
        MetadatosDato meta = new MetadatosDato(EstadoDato.CONOCIDO, parcial ? Causa.DATO_FALTANTE : null,
                metadatos.referenciaDefinicion(), metadatos.versionDefinicion(), metadatos.evidenciaRef(),
                metadatos.fuente(), metadatos.observadoEn(), metadatos.vigenteDesde(), metadatos.vigenteHasta());
        return new Dato(clave, TipoDato.IMPORTE, importe(minimo, moneda), parcial ? importe(maximo, moneda) : null,
                parcial, contexto, meta);
    }

    private static ValorLogico importe(BigDecimal importe, String moneda) {
        return importe == null ? null : ValorLogico.deImporte(importe, moneda);
    }
    private static Dato pendiente(String clave, ContextoValor contexto, MetadatosDato original, Causa causa) {
        MetadatosDato meta = new MetadatosDato(EstadoDato.FALTANTE, causa, original.referenciaDefinicion(),
                original.versionDefinicion(), original.evidenciaRef(), original.fuente(), original.observadoEn(),
                original.vigenteDesde(), original.vigenteHasta());
        return new Dato(clave, TipoDato.IMPORTE, null, null, false, contexto, meta);
    }
}
