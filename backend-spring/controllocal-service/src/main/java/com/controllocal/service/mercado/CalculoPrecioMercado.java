package com.controllocal.service.mercado;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.math.*;
import java.util.*;

/** Ampliación6.1/plan9.1. Corpus revisado, no matching ni selección automática de comparables. */
public final class CalculoPrecioMercado {
    private CalculoPrecioMercado() { }
    private static final MathContext PRECISION=new MathContext(50,RoundingMode.HALF_EVEN);
    private static final BigDecimal DOS=BigDecimal.valueOf(2);
    public enum Naturaleza { OFERTA,CIERRE }
    public enum Metodo { MEDIANA,MEDIA_PONDERADA,MEDIANA_PONDERADA }
    public enum Procedimiento { PAQUETE,UNITARIO }
    public enum Escala { IMPORTE,POR_M2 }
    public record Contexto(OperacionInmobiliaria operacion,String tipo,String segmento,String moneda,String periodo,Naturaleza naturaleza,String conceptoArea) {
        public Contexto {if(operacion==null||naturaleza==null||!texto(tipo)||!texto(segmento)||!texto(moneda))throw new ReglaNegocioException("Conserva operación, tipo, segmento, moneda y naturaleza de precio.");}
    }
    public record Dinero(BigDecimal importe,String moneda,String periodo) { }
    /** La recepción conserva el concepto de fuente; metrosCuadrados no equivale a otra superficie. */
    public record Area(BigDecimal metrosCuadrados,String concepto,String fuente) { }
    public record Componente(Dinero valor,boolean ausenciaConfirmada,String fuente) { }
    public record Ajuste(String efecto,String caracteristica,BigDecimal valor,Escala escala,String moneda,
                         String fuente,String autor,String version,String justificacion) { }
    public record Normalizacion(BigDecimal areaMinima,BigDecimal areaMaxima,String fuente,String version) { }
    public record Objetivo(Contexto contexto,Procedimiento procedimiento,Area area,Componente cochera,Componente deposito,
                           String composicionEquivalente,Normalizacion normalizacion) { }
    public record Comparable(String inmuebleVerificado,String referencia,Contexto contexto,Dinero precio,Area area,
                             Componente cochera,Componente deposito,String composicionEquivalente,List<Ajuste> ajustes,
                             BigDecimal peso,String fuentePeso) {
        public Comparable {ajustes=ajustes==null?List.of():List.copyOf(ajustes);}
    }
    public record ValorComparable(String inmueble,String referencia,BigDecimal importeAjustado,BigDecimal importeViviendaPorM2,BigDecimal peso) { }
    public record Exclusion(String referencia,String causa) { }
    public record DescripcionUnitarios(Contexto contexto,BigDecimal promedio,BigDecimal minimo,BigDecimal maximo,
                                       List<ValorComparable> comparables,List<Exclusion> exclusiones) {
        public DescripcionUnitarios {comparables=List.copyOf(comparables);exclusiones=List.copyOf(exclusiones);}
    }
    /** Catálogo20.5: promedio del segmento sin inventar área objetivo ni una tasación. */
    public static DescripcionUnitarios describirUnitarios(Contexto contexto,List<Comparable> corpus,Normalizacion n){
        if(contexto==null||corpus==null)throw new ReglaNegocioException("Conserva contexto y referencias del segmento.");
        var valores=new ArrayList<ValorComparable>();var exclusiones=new ArrayList<Exclusion>();var unicos=new HashSet<String>();
        for(var c:corpus){
            if(c==null||!texto(c.inmuebleVerificado())||!unicos.add(c.inmuebleVerificado()))throw new ReglaNegocioException("Cada inmueble verificado cuenta una vez.");
            try{
                if(!contexto.equals(c.contexto()))throw falta("CONTEXTO_NO_EQUIVALENTE");
                if(n==null||!texto(n.fuente())||!texto(n.version())||n.areaMinima()==null||n.areaMaxima()==null||n.areaMinima().signum()<=0||n.areaMaxima().compareTo(n.areaMinima())<0)throw falta("NORMALIZACION_LOCAL_NO_SUSTENTADA");
                BigDecimal ai=area(c.area(),contexto);if(ai.compareTo(n.areaMinima())<0||ai.compareTo(n.areaMaxima())>0)throw falta("FUERA_DEL_RANGO_DE_NORMALIZACION");
                if(!c.ajustes().isEmpty())throw falta("AJUSTES_REQUIEREN_OBJETIVO_ACREDITADO");
                BigDecimal vivienda=dinero(c.precio(),contexto,"PRECIO").subtract(componente(c.cochera(),contexto,"COCHERA_COMPARABLE")).subtract(componente(c.deposito(),contexto,"DEPOSITO_COMPARABLE"));
                if(vivienda.signum()<=0)throw falta("DESGLOSE_CONTRADICTORIO");
                BigDecimal u=vivienda.divide(ai,PRECISION);valores.add(new ValorComparable(c.inmuebleVerificado(),c.referencia(),u,u,BigDecimal.ONE));
            }catch(Insuficiente e){exclusiones.add(new Exclusion(c.referencia(),e.getMessage()));}
        }
        var orden=valores.stream().map(ValorComparable::importeViviendaPorM2).sorted().toList();
        BigDecimal promedio=orden.isEmpty()?null:orden.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(orden.size()),PRECISION);
        return new DescripcionUnitarios(contexto,promedio,orden.isEmpty()?null:orden.getFirst(),orden.isEmpty()?null:orden.getLast(),valores,exclusiones);
    }
    public record Resultado(Contexto contexto,Metodo metodo,BigDecimal referencia,BigDecimal minimoDescriptivo,
                            BigDecimal maximoDescriptivo,List<ValorComparable> comparables,List<Exclusion> exclusiones,List<String> faltantes) {
        public Resultado {comparables=List.copyOf(comparables);exclusiones=List.copyOf(exclusiones);faltantes=List.copyOf(faltantes);}
    }
    /** La suficiencia procede de la política revisada del segmento; nunca de cinco anuncios universales. */
    public static Resultado calcular(Objetivo objetivo,List<Comparable> corpus,Metodo metodo,int minimoAprobado){
        if(objetivo==null||objetivo.contexto()==null||objetivo.procedimiento()==null||corpus==null||metodo==null||minimoAprobado<=0)throw new ReglaNegocioException("Conserva objetivo, procedimiento y suficiencia aprobados.");
        var seleccion=Collections.unmodifiableList(new ArrayList<>(corpus));
        var identidades=new HashSet<String>();
        for(var c:seleccion)if(c==null||!texto(c.inmuebleVerificado())||!texto(c.referencia())||!identidades.add(c.inmuebleVerificado()))throw new ReglaNegocioException("El corpus debe contener una sola observación seleccionada por inmueble verificado; conserva los demás anuncios como antecedentes.");
        var valores=new ArrayList<ValorComparable>();var excluidas=new ArrayList<Exclusion>();
        for(var c:seleccion){try{valores.add(comparable(objetivo,c));}catch(Insuficiente e){excluidas.add(new Exclusion(c.referencia(),e.getMessage()));}}
        var faltantes=new ArrayList<String>();if(valores.size()<minimoAprobado)faltantes.add("COMPARABLES_PERTINENTES_INSUFICIENTES");
        BigDecimal referencia=faltantes.isEmpty()?agregar(valores,metodo):null;
        var importes=valores.stream().map(ValorComparable::importeAjustado).sorted().toList();
        return new Resultado(objetivo.contexto(),metodo,referencia,importes.isEmpty()?null:importes.getFirst(),importes.isEmpty()?null:importes.getLast(),valores,excluidas,faltantes);
    }
    private static ValorComparable comparable(Objetivo o,Comparable c){
        if(!o.contexto().equals(c.contexto()))throw falta("CONTEXTO_NO_EQUIVALENTE");
        BigDecimal precio=dinero(c.precio(),o.contexto(),"PRECIO"),ajustes=BigDecimal.ZERO;
        if(precio.signum()<=0)throw falta("PRECIO_NO_POSITIVO_REQUIERE_REVISION");
        var efectos=new HashSet<String>();
        for(var a:c.ajustes()){
            if(a==null||!texto(a.efecto())||!texto(a.caracteristica())||a.valor()==null||a.escala()==null||!texto(a.fuente())||!texto(a.autor())||!texto(a.version())||!texto(a.justificacion()))throw falta("AJUSTE_SIN_SUSTENTO");
            if(!efectos.add(a.efecto()))throw falta("EFECTO_AJUSTADO_DOS_VECES");
            if(!o.contexto().moneda().equals(a.moneda()))throw falta("MONEDA_DE_AJUSTE_NO_EQUIVALENTE");
            if(a.escala()!=(o.procedimiento()==Procedimiento.PAQUETE?Escala.IMPORTE:Escala.POR_M2))throw falta("UNIDAD_DE_AJUSTE_NO_EQUIVALENTE");
            ajustes=ajustes.add(a.valor());
        }
        BigDecimal valor,unitario=null;
        if(o.procedimiento()==Procedimiento.PAQUETE){
            if(!texto(o.composicionEquivalente())||!o.composicionEquivalente().equals(c.composicionEquivalente()))throw falta("PAQUETE_SIN_COMPOSICION_EQUIVALENTE");
            valor=precio.add(ajustes);
        }else{
            var n=o.normalizacion();
            if(n==null||!texto(n.fuente())||!texto(n.version())||n.areaMinima()==null||n.areaMinima().signum()<=0||n.areaMaxima()==null||n.areaMaxima().compareTo(n.areaMinima())<0)throw falta("NORMALIZACION_LOCAL_NO_SUSTENTADA");
            BigDecimal ai=area(c.area(),o.contexto()),a0=area(o.area(),o.contexto());
            if(ai.compareTo(n.areaMinima())<0||ai.compareTo(n.areaMaxima())>0||a0.compareTo(n.areaMinima())<0||a0.compareTo(n.areaMaxima())>0)throw falta("FUERA_DEL_RANGO_DE_NORMALIZACION");
            BigDecimal gi=componente(c.cochera(),o.contexto(),"COCHERA_COMPARABLE"),di=componente(c.deposito(),o.contexto(),"DEPOSITO_COMPARABLE");
            BigDecimal g0=componente(o.cochera(),o.contexto(),"COCHERA_OBJETIVO"),d0=componente(o.deposito(),o.contexto(),"DEPOSITO_OBJETIVO");
            if(precio.subtract(gi).subtract(di).signum()<=0)throw falta("DESGLOSE_CONTRADICTORIO");
            unitario=precio.subtract(gi).subtract(di).divide(ai,PRECISION).add(ajustes);
            valor=a0.multiply(unitario).add(g0).add(d0);
        }
        if(valor.signum()<=0||unitario!=null&&unitario.signum()<=0)throw falta("AJUSTES_REQUIEREN_REVISION");
        BigDecimal peso=c.peso()==null?BigDecimal.ONE:c.peso();
        if(peso.signum()<=0||peso.compareTo(BigDecimal.ONE)!=0&&!texto(c.fuentePeso()))throw falta("PESO_NO_SUSTENTADO");
        return new ValorComparable(c.inmuebleVerificado(),c.referencia(),valor,unitario,peso);
    }
    private static BigDecimal dinero(Dinero d,Contexto c,String nombre){
        if(d==null||d.importe()==null)throw falta(nombre+"_FALTANTE");
        if(!c.moneda().equals(d.moneda())||!Objects.equals(c.periodo(),d.periodo()))throw falta(nombre+"_MONEDA_O_PERIODO_NO_EQUIVALENTE");
        return d.importe();
    }
    private static BigDecimal componente(Componente p,Contexto c,String nombre){
        if(p==null||!texto(p.fuente()))throw falta(nombre+"_FALTANTE");
        if(p.ausenciaConfirmada()){
            if(p.valor()!=null&&p.valor().importe()!=null&&p.valor().importe().signum()!=0)throw falta(nombre+"_CONTRADICTORIO");
            return BigDecimal.ZERO;
        }
        BigDecimal n=dinero(p.valor(),c,nombre);if(n.signum()<0)throw falta(nombre+"_NEGATIVO");return n;
    }
    private static BigDecimal area(Area a,Contexto c){
        if(a==null||a.metrosCuadrados()==null||a.metrosCuadrados().signum()<=0||!texto(a.fuente()))throw falta("AREA_FALTANTE");
        if(!texto(c.conceptoArea())||!c.conceptoArea().equals(a.concepto()))throw falta("CONCEPTO_DE_AREA_NO_EQUIVALENTE");return a.metrosCuadrados();
    }
    static BigDecimal agregar(List<ValorComparable> valores,Metodo metodo){
        if(valores==null||valores.isEmpty()||metodo==null)throw new ReglaNegocioException("Un conjunto vacío no produce precio.");
        for(var v:valores)if(v==null||!texto(v.inmueble())||v.importeAjustado()==null||v.importeAjustado().signum()<=0||v.peso()==null||v.peso().signum()<=0)throw new ReglaNegocioException("Valor o peso no válido.");
        var ordenados=valores.stream().sorted(Comparator.comparing(ValorComparable::importeAjustado).thenComparing(ValorComparable::inmueble)).toList();
        if(metodo==Metodo.MEDIANA){int m=ordenados.size()/2;return ordenados.size()%2==1?ordenados.get(m).importeAjustado():ordenados.get(m-1).importeAjustado().add(ordenados.get(m).importeAjustado()).divide(DOS,PRECISION);}
        BigDecimal total=ordenados.stream().map(ValorComparable::peso).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(metodo==Metodo.MEDIA_PONDERADA)return ordenados.stream().map(v->v.importeAjustado().multiply(v.peso())).reduce(BigDecimal.ZERO,BigDecimal::add).divide(total,PRECISION);
        BigDecimal acumulado=BigDecimal.ZERO;
        for(int i=0;i<ordenados.size();i++){acumulado=acumulado.add(ordenados.get(i).peso());int comparacion=acumulado.multiply(DOS).compareTo(total);
            if(comparacion>0)return ordenados.get(i).importeAjustado();
            if(comparacion==0)return ordenados.get(i).importeAjustado().add(ordenados.get(i+1).importeAjustado()).divide(DOS,PRECISION);}
        throw new IllegalStateException("La suma de pesos no conserva su total.");
    }
    public record BaseCosto(Dinero valor,String fuente,String version) { }
    public record NivelCasa(Area area,BaseCosto costoM2,BigDecimal depreciacion,String fuenteDepreciacion) { }
    /** Control alternativo, nunca suma con el comparativo ni coeficientes constantes de una plantilla. */
    public static BigDecimal controlCasa(Contexto contexto,BaseCosto terreno,List<NivelCasa> niveles,BaseCosto complementos){
        if(contexto==null||contexto.operacion()!=OperacionInmobiliaria.VENTA||contexto.periodo()!=null||terreno==null||niveles==null||niveles.isEmpty()||complementos==null)throw new ReglaNegocioException("El control de valor por costos requiere bases de capital acreditadas; no produce una renta mensual.");
        try{
            BigDecimal total=baseCosto(terreno,contexto,"TERRENO").add(baseCosto(complementos,contexto,"COMPLEMENTOS"));
            for(var n:niveles){if(n==null||n.depreciacion()==null||n.depreciacion().signum()<0||n.depreciacion().compareTo(BigDecimal.ONE)>0||!texto(n.fuenteDepreciacion()))throw falta("DEPRECIACION_SIN_SUSTENTO");
                BigDecimal costo=baseCosto(n.costoM2(),contexto,"COSTO_CONSTRUCCION_M2");
                total=total.add(area(n.area(),contexto).multiply(costo).multiply(BigDecimal.ONE.subtract(n.depreciacion())));}
            return total;
        }catch(Insuficiente e){throw new ReglaNegocioException("Control de costos insuficiente: "+e.getMessage());}
    }
    private static BigDecimal baseCosto(BaseCosto b,Contexto c,String nombre){
        if(b==null||!texto(b.fuente())||!texto(b.version()))throw falta(nombre+"_SIN_SUSTENTO");
        BigDecimal n=dinero(b.valor(),c,nombre);if(n.signum()<0)throw falta(nombre+"_NEGATIVO");return n;
    }
    private static boolean texto(String v){return v!=null&&!v.isBlank();}
    private static Insuficiente falta(String causa){return new Insuficiente(causa);}
    private static final class Insuficiente extends RuntimeException {Insuficiente(String causa){super(causa);}}
}
