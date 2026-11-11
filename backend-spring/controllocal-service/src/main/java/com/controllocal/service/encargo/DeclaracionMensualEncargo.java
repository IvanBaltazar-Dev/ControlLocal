package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ComposicionMensualMatching;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.soporte.ValorLogico;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static com.controllocal.service.encargo.EncargoService.*;
import static com.controllocal.service.matching.ContratoMatching.*;

/** D1-09/D3: una declaración del mismo pacto; el cálculo sigue en el compositor existente. */
final class DeclaracionMensualEncargo {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private static final String CONCEPTO_TOTAL="TOTAL_MENSUAL_OBLIGATORIO";
    private static final String CONCEPTO_RENTA="PRECIO_ALQUILER", REFERENCIA_RENTA="RENTA_DEL_ENCARGO";
    private record Guardada(int versionEsquema,String configuracion,String huellaBaseEconomica,
                            ComposicionMensual declaracion) { }
    private final ObjectMapper json;
    private final Documentos documentos;
    DeclaracionMensualEncargo(ObjectMapper json){this.json=json;this.documentos=new Documentos(json);}

    String preparar(ComposicionMensual declaracion,String fuentePacto) {
        if(declaracion==null)return null;
        String fuente=texto(declaracion.fuente());if(fuente==null)fuente=texto(fuentePacto);
        List<ImporteMensual> componentes=new ArrayList<>();Map<String,ImporteMensual> referencias=new HashMap<>();
        for(var c:declaracion.componentes()==null?List.<ImporteMensual>of():declaracion.componentes()) {
            if(c==null)throw new ReglaNegocioException("La composición mensual contiene una línea sin datos.");
            String ref=texto(c.referencia());if(ref==null)ref=CONCEPTO_RENTA.equals(texto(c.concepto()))?REFERENCIA_RENTA:"componente-"+(componentes.size()+1);
            if(c.importe()!=null&&c.importe().signum()<0||c.importeHasta()!=null&&c.importeHasta().signum()<0)
                throw new ReglaNegocioException("Los componentes conservan importes no negativos; el tratamiento distingue el descuento.");
            if(!c.intervalo()&&c.importeHasta()!=null||c.importe()!=null&&c.importeHasta()!=null&&c.importe().compareTo(c.importeHasta())>0)
                throw new ReglaNegocioException("Los límites mensuales deben conservar un intervalo ordenado explícito.");
            if(c.vigenteDesde()!=null&&c.vigenteHasta()!=null&&c.vigenteHasta().isBefore(c.vigenteDesde()))
                throw new ReglaNegocioException("La vigencia del componente está invertida.");
            String moneda=texto(c.moneda())==null?null:CondicionesEconomicas.moneda(c.moneda(),"del componente mensual");
            var normalizada=new ImporteMensual(ref,texto(c.concepto()),c.importe()==null?null:c.importe().stripTrailingZeros(),c.importeHasta()==null?null:c.importeHasta().stripTrailingZeros(),c.intervalo(),moneda,
                    texto(c.periodo())==null?null:c.periodo().trim().toUpperCase(Locale.ROOT),c.tratamiento(),texto(c.incluidoEn()),
                    texto(c.fuente()),c.vigenteDesde(),c.vigenteHasta());
            var anterior=referencias.putIfAbsent(ref,normalizada);
            if(anterior!=null) {
                if(!anterior.equals(normalizada))throw new ReglaNegocioException("Una referencia mensual identifica una sola línea.");
            } else componentes.add(normalizada);
        }
        Map<String,ImporteMensual> porRef=new HashMap<>();componentes.forEach(c->porRef.put(c.referencia(),c));
        for(var c:componentes) {
            if(c.incluidoEn()!=null) {
                if(c.tratamiento()!=TratamientoMensual.INCLUIDO_EN_OTRO||!porRef.containsKey(c.incluidoEn())&&!REFERENCIA_RENTA.equals(c.incluidoEn()))
                    throw new ReglaNegocioException("La inclusión debe identificar la línea que cubre ese componente.");
                Set<String> camino=new HashSet<>();ImporteMensual actual=c;
                while(actual!=null){if(!camino.add(actual.referencia()))throw new ReglaNegocioException("Las inclusiones mensuales no pueden formar un ciclo.");
                    actual=actual.incluidoEn()==null?null:porRef.get(actual.incluidoEn());}
            }
        }
        if(componentes.stream().anyMatch(c->CONCEPTO_TOTAL.equals(c.concepto())&&c.tratamiento()==TratamientoMensual.EXIGIBLE)
                &&componentes.stream().filter(c->c.tratamiento()==TratamientoMensual.EXIGIBLE).count()>1)
            throw new ReglaNegocioException("Un total declarado ya cubre sus componentes; no se suman otra vez como cargos separados.");
        return escribir(new Guardada(1,null,null,new ComposicionMensual(declaracion.completa(),fuente,List.copyOf(componentes))));
    }
    ComposicionMensual declarada(CondicionEconomicaCaptacion c){var g=leer(c);return g==null?null:g.declaracion();}
    void vincular(CondicionEconomicaCaptacion c,String configuracion) {
        var g=leer(c);if(g==null)return;
        if(g.huellaBaseEconomica()!=null)throw new IllegalStateException("La declaración mensual ya conserva su base original.");
        c.setComposicionMensual(escribir(new Guardada(1,Objects.requireNonNull(configuracion),huella(c,configuracion),g.declaracion())));
    }
    boolean corresponde(CondicionEconomicaCaptacion c,String configuracion) {
        var g=leer(c);return g!=null&&Objects.equals(configuracion,g.configuracion())
                &&g.huellaBaseEconomica()!=null&&g.huellaBaseEconomica().equals(huella(c,configuracion));
    }
    Dato total(CondicionEconomicaCaptacion c,String operacion,String configuracion,LocalDate inicio,
               LocalDate fin,String evidencia,Instant corte,String modalidadPrecio) {
        var g=leer(c);String moneda=c==null?null:c.getMonedaReferencia();
        var contexto=new ContextoValor(CONCEPTO_TOTAL,null,moneda,"MENSUAL",operacion,configuracion);
        if(g==null)return pendiente(contexto,Causa.DATO_FALTANTE,null);
        var d=g.declaracion();
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"D3:TOTAL_MENSUAL_OBLIGATORIO",1L,evidencia,d.fuente(),
                c.getFechaCreacion()==null?null:c.getFechaCreacion().atZoneSameInstant(LIMA).toLocalDate(),instante(inicio),despues(fin));
        if(!corresponde(c,configuracion))return pendiente(contexto,Causa.CONFIGURACION_INCOMPATIBLE,meta);
        if(!CondicionEconomicaCaptacion.ARRENDAMIENTO.equals(c.getTipoOperacion())||!"ALQUILER".equals(operacion))
            return pendiente(contexto,Causa.OPERACION_INCOMPATIBLE,meta);
        if(texto(d.fuente())==null)return pendiente(contexto,Causa.DATO_FALTANTE,meta);
        boolean agregado=d.componentes().stream().anyMatch(x->CONCEPTO_TOTAL.equals(x.concepto())&&x.tratamiento()==TratamientoMensual.EXIGIBLE);
        List<ImporteMensual> entradas=new ArrayList<>(d.componentes());
        if(!agregado) {
            boolean tarifa="POR_M2_AL_MES".equals(modalidadPrecio);
            var rentas=entradas.stream().filter(x->CONCEPTO_RENTA.equals(x.concepto())||REFERENCIA_RENTA.equals(x.referencia())).toList();
            if(tarifa&&rentas.isEmpty())return pendiente(contexto,Causa.UNIDAD_INCOMPATIBLE,meta);
            if(!tarifa&&(c.getImporteReferencia()==null||c.getMonedaReferencia()==null||c.getPeriodoPrecio()==null))
                return pendiente(contexto,Causa.DATO_FALTANTE,meta);
            for(var renta:rentas) {
                if(!CONCEPTO_RENTA.equals(renta.concepto())||renta.tratamiento()!=TratamientoMensual.EXIGIBLE
                        ||renta.incluidoEn()!=null)return pendiente(contexto,Causa.CONFLICTO,meta);
                if(!tarifa&&(renta.importe()!=null&&renta.importe().compareTo(c.getImporteReferencia())!=0
                        ||renta.importeHasta()!=null&&renta.importeHasta().compareTo(c.getImporteReferencia())!=0
                        ||renta.moneda()!=null&&!renta.moneda().equals(c.getMonedaReferencia())
                        ||renta.periodo()!=null&&!renta.periodo().equalsIgnoreCase(c.getPeriodoPrecio())))
                    return pendiente(contexto,Causa.CONFLICTO,meta);
            }
            if(rentas.isEmpty())entradas.add(new ImporteMensual(REFERENCIA_RENTA,CONCEPTO_RENTA,c.getImporteReferencia(),null,false,
                    c.getMonedaReferencia(),c.getPeriodoPrecio(),TratamientoMensual.EXIGIBLE,null,d.fuente(),null,null));
            else {
                // La renta ya conocida no se pide de nuevo ni se cuenta por cada copia de su línea.
                var primera=rentas.getFirst();
                if(rentas.stream().anyMatch(x->!Objects.equals(x.vigenteDesde(),primera.vigenteDesde())||!Objects.equals(x.vigenteHasta(),primera.vigenteHasta())))
                    return pendiente(contexto,Causa.CONFLICTO,meta);
                if(tarifa&&rentas.stream().anyMatch(x->!decimalIgual(x.importe(),primera.importe())||!decimalIgual(x.importeHasta(),primera.importeHasta())
                        ||x.intervalo()!=primera.intervalo()||!Objects.equals(x.moneda(),primera.moneda())||!Objects.equals(x.periodo(),primera.periodo())))
                    return pendiente(contexto,Causa.CONFLICTO,meta);
                Set<String> refs=new HashSet<>();rentas.forEach(x->refs.add(x.referencia()));
                List<ImporteMensual> sinRenta=new ArrayList<>();
                for(var x:entradas)if(!rentas.contains(x))sinRenta.add(new ImporteMensual(x.referencia(),x.concepto(),x.importe(),x.importeHasta(),x.intervalo(),x.moneda(),x.periodo(),x.tratamiento(),
                        refs.contains(x.incluidoEn())?REFERENCIA_RENTA:x.incluidoEn(),x.fuente(),x.vigenteDesde(),x.vigenteHasta()));
                sinRenta.add(new ImporteMensual(REFERENCIA_RENTA,CONCEPTO_RENTA,tarifa?primera.importe():c.getImporteReferencia(),tarifa?primera.importeHasta():null,
                        tarifa&&primera.intervalo(),tarifa?primera.moneda():c.getMonedaReferencia(),tarifa?primera.periodo():c.getPeriodoPrecio(),
                        TratamientoMensual.EXIGIBLE,null,tarifa&&texto(primera.fuente())!=null?primera.fuente():d.fuente(),primera.vigenteDesde(),primera.vigenteHasta()));
                entradas=sinRenta;
            }
        }
        List<ComponenteMensual> componentes=new ArrayList<>();
        Map<String,ImporteMensual> porRef=new HashMap<>();entradas.forEach(x->porRef.put(x.referencia(),x));
        for(var x:entradas) {
            String fuente=texto(x.fuente())==null?d.fuente():x.fuente();
            if(x.tratamiento()==null||x.concepto()==null)return pendiente(contexto,Causa.DATO_FALTANTE,meta);
            if(x.tratamiento()==TratamientoMensual.INCLUIDO_EN_OTRO&&(x.incluidoEn()==null||texto(fuente)==null))
                return pendiente(contexto,Causa.DATO_FALTANTE,meta);
            if(x.tratamiento()==TratamientoMensual.INCLUIDO_EN_OTRO) {
                var cubierta=x;while(cubierta!=null&&cubierta.incluidoEn()!=null)cubierta=porRef.get(cubierta.incluidoEn());
                if(cubierta==null||cubierta.tratamiento()!=TratamientoMensual.EXIGIBLE)
                    return pendiente(contexto,Causa.DATO_FALTANTE,meta);
            }
            // El total ordinario no presenta una inclusión/cargo sólo temporal como constante.
            if(x.vigenteDesde()!=null&&(inicio==null||x.vigenteDesde().isAfter(inicio))
                    ||x.vigenteHasta()!=null&&(fin==null||x.vigenteHasta().isBefore(fin)))
                return pendiente(contexto,Causa.VIGENCIA_NO_ACREDITADA,meta);
            // Una reducción temporal no acredita el gasto ordinario de todo el pacto.
            if(x.tratamiento()==TratamientoMensual.DESCUENTO_FIRME&&(texto(fuente)==null||inicio==null||fin==null))
                return pendiente(contexto,Causa.VIGENCIA_NO_ACREDITADA,meta);
            var estado=x.importe()==null&&x.importeHasta()==null?EstadoDato.FALTANTE:EstadoDato.CONOCIDO;
            var m=new MetadatosDato(estado,null,meta.referenciaDefinicion(),meta.versionDefinicion(),evidencia,fuente,meta.observadoEn(),
                    instante(x.vigenteDesde()==null?inicio:x.vigenteDesde()),despues(x.vigenteHasta()==null?fin:x.vigenteHasta()));
            var valor=new Dato("MENSUAL:"+x.referencia(),TipoDato.IMPORTE,ValorLogico.deImporte(x.importe(),x.moneda()),
                    ValorLogico.deImporte(x.importeHasta(),x.moneda()),x.intervalo(),
                    new ContextoValor(x.concepto(),null,x.moneda(),x.periodo(),operacion,configuracion),m);
            componentes.add(new ComponenteMensual(x.referencia(),valor,x.tratamiento()));
        }
        return ComposicionMensualMatching.total(TOTAL_MENSUAL_OBLIGATORIO,componentes,Boolean.TRUE.equals(d.completa()),contexto,meta,corte);
    }
    private Guardada leer(CondicionEconomicaCaptacion c) {
        if(c==null||c.getComposicionMensual()==null)return null;
        try {var g=json.readValue(c.getComposicionMensual(),Guardada.class);
            if(g.versionEsquema()!=1||g.declaracion()==null||g.declaracion().componentes()==null)throw new IllegalStateException("Formato mensual desconocido.");return g;}
        catch(Exception fallo){throw new IllegalStateException("La declaración mensual conservada es ilegible.",fallo);}
    }
    private String huella(CondicionEconomicaCaptacion c,String config) {
        Map<String,Object> base=new LinkedHashMap<>();base.put("operacion",c.getTipoOperacion());base.put("configuracion",config);
        base.put("precio",c.getImporteReferencia()==null?null:c.getImporteReferencia().stripTrailingZeros());
        base.put("moneda",c.getMonedaReferencia());base.put("periodo",c.getPeriodoPrecio());
        return Idempotencia.huellaExacta(documentos.objetoCanonico(base));
    }
    private static Dato pendiente(ContextoValor ctx,Causa causa,MetadatosDato anterior) {
        return new Dato(TOTAL_MENSUAL_OBLIGATORIO,TipoDato.IMPORTE,null,null,false,ctx,
                new MetadatosDato(EstadoDato.FALTANTE,causa,anterior==null?null:anterior.referenciaDefinicion(),anterior==null?null:anterior.versionDefinicion(),
                        anterior==null?null:anterior.evidenciaRef(),anterior==null?null:anterior.fuente(),anterior==null?null:anterior.observadoEn(),null,null));
    }
    private String escribir(Object valor){try{return json.writeValueAsString(valor);}catch(Exception fallo){throw new IllegalStateException("No se pudo conservar la declaración mensual.",fallo);}}
    private static Instant instante(LocalDate fecha){return fecha==null?null:fecha.atStartOfDay(LIMA).toInstant();}
    private static Instant despues(LocalDate fecha){return fecha==null?null:fecha.plusDays(1).atStartOfDay(LIMA).toInstant();}
    private static String texto(String s){return s==null||s.isBlank()?null:s.trim();}
    private static boolean decimalIgual(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
}
