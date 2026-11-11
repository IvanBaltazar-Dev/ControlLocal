package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ValorLogico;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;

/**
 * Aprobación30/10: selecciona alternativas económicas sin modificar consulta, H ni S/U/W.
 * No lee repositorios, completa precios, concede permisos ni crea hechos comerciales.
 */
@Component
public class AlternativasPrecioMatching {
    private static final String BASE="base:presupuesto";
    /** Sólo precisión del porcentaje descriptivo; no redondea dinero ni participa en el orden. */
    private static final int ESCALA_PORCENTAJE_DESCRIPTIVO=8;
    private final MotorMatchingDeterminista motor;

    public AlternativasPrecioMatching(MotorMatchingDeterminista motor) {this.motor=Objects.requireNonNull(motor);}

    /** Resultado interno; el coordinador proyecta exclusivamente los campos autorizados. */
    public record Alternativa(Evaluacion original,BigDecimal precio,BigDecimal maximoOriginal,
                              String moneda,String concepto,String periodo,BigDecimal excesoAbsoluto,
                              BigDecimal excesoPorcentaje) { }

    private record Presupuesto(Criterio criterio,Condicion condicion,BigDecimal maximo,
                               ValorLogico piso,String moneda) { }

    /** Valida también con universo vacío: la falta de presupuesto no significa cero opciones de mercado. */
    public void validarExploracion(Consulta consulta,BigDecimal limite,String monedaLimite) {
        Objects.requireNonNull(consulta,"Consulta normalizada.");
        String moneda=monedaLimite(limite,monedaLimite);
        if(consulta.modalidad()!=Modalidad.COMERCIAL||consulta.coberturaCriterios()!=CoberturaCriterios.COMPLETA)
            throw regla("La exploración económica requiere una consulta comercial con todas sus obligaciones autorizadas.");
        var b=presupuesto(consulta).orElseThrow(()->regla(
                "Declara un presupuesto máximo comparable, con operación, moneda, concepto y período vigentes, para explorar alternativas."));
        validarLimite(b,limite,moneda);
    }

    public Optional<Alternativa> evaluar(Consulta consulta,Oferta oferta,Evaluacion original,
                                         BigDecimal limite,String monedaLimite) {
        Objects.requireNonNull(consulta,"Consulta normalizada.");Objects.requireNonNull(oferta,"Oferta autorizada.");
        Objects.requireNonNull(original,"Evaluación original.");
        if(!oferta.accesoAutorizado())throw new AccesoNoAutorizadoException("No puedes consultar este recurso.");
        exigirCorrespondencia(consulta,oferta,original);
        String moneda=monedaLimite(limite,monedaLimite);
        var posible=presupuesto(consulta);
        if(posible.isEmpty())return Optional.empty();
        var b=posible.get();validarLimite(b,limite,moneda);
        if(consulta.modalidad()!=Modalidad.COMERCIAL||consulta.coberturaCriterios()!=CoberturaCriterios.COMPLETA
                ||original.g()!=CUMPLE||original.h()!=INCUMPLE||original.categoria()!=Categoria.INCOMPATIBLE)
            return Optional.empty();
        // No se recalcula H ni se modifica su categoría: cada otra obligación original sigue exigida.
        for(var c:consulta.criterios())if(c!=b.criterio()&&c.exigencia()==Exigencia.OBLIGATORIO
                &&motor.evaluarCriterio(c,oferta.datos(),consulta.corte()).conclusion()!=CUMPLE)return Optional.empty();
        if(comparar(b,OperadorCriterio.MAXIMO,importe(b.maximo(),b.moneda()),oferta,consulta)!=INCUMPLE)
            return Optional.empty();
        if(b.piso()!=null&&comparar(b,OperadorCriterio.MINIMO,b.piso(),oferta,consulta)!=CUMPLE)return Optional.empty();
        if(limite!=null&&comparar(b,OperadorCriterio.MAXIMO,importe(limite,b.moneda()),oferta,consulta)!=CUMPLE)
            return Optional.empty();
        var punto=precioExacto(b,oferta,consulta);
        if(punto.isEmpty())return Optional.empty();
        BigDecimal precio=punto.get(),exceso=precio.subtract(b.maximo());
        BigDecimal porcentaje=b.maximo().signum()>0?exceso.multiply(new BigDecimal("100"))
                .divide(b.maximo(),ESCALA_PORCENTAJE_DESCRIPTIVO,RoundingMode.HALF_EVEN):null;
        return Optional.of(new Alternativa(original,precio,b.maximo(),b.moneda(),b.condicion().contexto().concepto(),
                b.condicion().contexto().periodo(),exceso,porcentaje));
    }

    /** Primer precio real; segundo precio superior sólo si aporta más preferencias confirmadas. */
    public List<Alternativa> escalones(List<Alternativa> alternativas) {
        var entrada=List.copyOf(Objects.requireNonNull(alternativas,"Alternativas evaluadas."));
        if(entrada.isEmpty())return entrada;
        var primera=entrada.getFirst();validarAlternativa(primera);
        var unicas=new LinkedHashMap<String,Alternativa>();
        for(var a:entrada) {
            validarAlternativa(a);exigirMismoContexto(primera,a);
            var previa=unicas.putIfAbsent(a.original().referenciaEstable(),a);
            if(previa!=null&&(!previa.original().equals(a.original())||previa.precio().compareTo(a.precio())!=0
                    ||previa.maximoOriginal().compareTo(a.maximoOriginal())!=0))
                throw regla("La misma oferta llegó con evaluaciones o precios incompatibles.");
        }
        Comparator<Alternativa> orden=Comparator.comparing(Alternativa::precio)
                .thenComparing(Comparator.comparingLong((Alternativa a)->a.original().s()).reversed())
                .thenComparingLong(a->a.original().u()).thenComparing(a->a.original().referenciaEstable());
        var ordenadas=unicas.values().stream().sorted(orden).toList();
        var primero=ordenadas.getFirst();
        var segundo=ordenadas.stream().filter(a->a.precio().compareTo(primero.precio())>0
                &&a.original().s()>primero.original().s()).findFirst();
        return segundo.isPresent()?List.of(primero,segundo.get()):List.of(primero);
    }

    private Optional<Presupuesto> presupuesto(Consulta consulta) {
        var bases=consulta.criterios().stream().filter(c->BASE.equals(c.clave())||BASE.equals(c.referencia())).toList();
        if(bases.size()!=1)return Optional.empty();
        var b=bases.getFirst();
        if(!BASE.equals(b.clave())||!BASE.equals(b.referencia())||b.exigencia()!=Exigencia.OBLIGATORIO
                ||b.alternativas().size()!=1)return Optional.empty();
        var c=b.alternativas().getFirst();var ctx=c.contexto();
        if(c.negada()||c.tipo()!=TipoDato.IMPORTE||c.metadatos().estado()!=EstadoDato.CONOCIDO
                ||ComparadorMatching.pendiente(c.metadatos(),consulta.corte())!=null
                ||ctx.unidad()!=null||!contextoEconomico(c.claveDato(),ctx))return Optional.empty();
        if(c.operador()!=OperadorCriterio.MAXIMO&&c.operador()!=OperadorCriterio.ENTRE)return Optional.empty();
        if(c.operador()==OperadorCriterio.MAXIMO&&c.hasta()!=null)return Optional.empty();
        var forma=new Dato(c.claveDato(),TipoDato.IMPORTE,c.valor(),c.hasta(),c.operador()==OperadorCriterio.ENTRE,ctx,c.metadatos());
        if(c.valor()==null||!ComparadorMatching.formaDato(forma))return Optional.empty();
        String moneda=ComparadorMatching.moneda(ctx.moneda(),c.valor());
        if(moneda==null)return Optional.empty();
        ValorLogico piso=null,techo=c.valor();
        if(c.operador()==OperadorCriterio.ENTRE) {
            if(c.hasta()==null||!moneda.equals(ComparadorMatching.moneda(ctx.moneda(),c.hasta())))return Optional.empty();
            piso=c.valor();techo=c.hasta();
            if(piso.numero().compareTo(techo.numero())>0)throw regla("El presupuesto original tiene límites contradictorios.");
        }
        return Optional.of(new Presupuesto(b,c,techo.numero(),piso,moneda));
    }

    private Optional<BigDecimal> precioExacto(Presupuesto b,Oferta oferta,Consulta consulta) {
        var compatibles=oferta.datos().stream().filter(d->d.clave().equals(b.condicion().claveDato())
                &&ComparadorMatching.contexto(b.condicion(),d)==null).toList();
        if(compatibles.stream().anyMatch(d->d.metadatos().causa()==Causa.CONFLICTO))return Optional.empty();
        for(var d:compatibles) {
            if(d.tipo()!=TipoDato.IMPORTE||d.metadatos().estado()!=EstadoDato.CONOCIDO
                    ||ComparadorMatching.pendiente(d.metadatos(),consulta.corte())!=null
                    ||!ComparadorMatching.formaDato(d)||d.valor()==null)continue;
            if(d.intervalo()&&(d.hasta()==null||d.valor().numero().compareTo(d.hasta().numero())!=0
                    ||!b.moneda().equals(ComparadorMatching.moneda(d.contexto().moneda(),d.hasta()))))continue;
            BigDecimal punto=d.valor().numero();
            // IGUAL sobre TODO el conjunto conserva conflictos, equivalencia, vigencia y comparabilidad.
            if(comparar(b,OperadorCriterio.IGUAL,importe(punto,b.moneda()),oferta,consulta)==CUMPLE)
                return Optional.of(punto);
        }
        return Optional.empty();
    }

    private Conclusion comparar(Presupuesto b,OperadorCriterio operador,ValorLogico valor,Oferta oferta,Consulta consulta) {
        var c=b.condicion();
        var prueba=new Criterio(BASE,BASE,Exigencia.OBLIGATORIO,null,List.of(new Condicion(c.claveDato(),c.tipo(),
                operador,valor,null,false,c.contexto(),c.metadatos())));
        return motor.evaluarCriterio(prueba,oferta.datos(),consulta.corte()).conclusion();
    }

    private static String monedaLimite(BigDecimal limite,String moneda) {
        if(limite==null) {
            if(moneda!=null)throw regla("El límite y su moneda se declaran juntos.");
            return null;
        }
        String canonica=ComparadorMatching.moneda(moneda,null);
        if(canonica==null)throw regla("El límite exploratorio necesita una moneda explícita comparable.");
        return canonica;
    }
    private static void validarLimite(Presupuesto b,BigDecimal limite,String moneda) {
        if(limite!=null&&(!b.moneda().equals(moneda)||limite.compareTo(b.maximo())<=0))
            throw regla("El límite exploratorio debe superar el máximo original y conservar su moneda.");
    }
    private static boolean contextoEconomico(String selector,ContextoValor c) {
        return PRECIO_VENTA.equals(selector)&&"PRECIO_VENTA".equals(c.concepto())&&"VENTA".equals(c.operacion())&&c.periodo()==null
                ||TOTAL_MENSUAL_OBLIGATORIO.equals(selector)&&"TOTAL_MENSUAL_OBLIGATORIO".equals(c.concepto())
                &&"ALQUILER".equals(c.operacion())&&"MENSUAL".equals(c.periodo());
    }
    private static void exigirCorrespondencia(Consulta q,Oferta o,Evaluacion e) {
        if(!Objects.equals(q.referencia(),e.consulta())||q.version()!=e.versionConsulta()
                ||!Objects.equals(q.versionRegla(),e.versionRegla())||!Objects.equals(q.corte(),e.corte())
                ||q.modalidad()!=e.modalidad()||q.coberturaCriterios()!=e.coberturaCriterios()
                ||!Objects.equals(o.referenciaEstable(),e.referenciaEstable())||!Objects.equals(o.clave(),e.candidato())
                ||o.version()!=e.versionOferta())throw regla("La evaluación no corresponde a la consulta y oferta originales.");
        if(o.datos().stream().anyMatch(d->d.contexto().configuracion()!=null
                &&!d.contexto().configuracion().equals(o.clave().configuracion())))
            throw new IllegalStateException("La proyección mezcla otra configuración comercial.");
    }
    private static void validarAlternativa(Alternativa a) {
        if(a==null||a.original()==null||a.precio()==null||a.maximoOriginal()==null||a.excesoAbsoluto()==null
                ||a.moneda()==null||a.concepto()==null)throw regla("Faltan los datos de la alternativa económica evaluada.");
        var e=a.original();
        if(e.g()!=CUMPLE||e.h()!=INCUMPLE||e.categoria()!=Categoria.INCOMPATIBLE
                ||e.modalidad()!=Modalidad.COMERCIAL||e.coberturaCriterios()!=CoberturaCriterios.COMPLETA
                ||e.s()<0||e.u()<0||e.w()<0||e.s()>e.w()||e.u()>e.w()-e.s()
                ||a.precio().compareTo(a.maximoOriginal())<=0||a.precio().subtract(a.maximoOriginal()).compareTo(a.excesoAbsoluto())!=0
                ||!a.moneda().equals(ComparadorMatching.moneda(a.moneda(),null))
                ||!("PRECIO_VENTA".equals(a.concepto())&&a.periodo()==null
                ||"TOTAL_MENSUAL_OBLIGATORIO".equals(a.concepto())&&"MENSUAL".equals(a.periodo())))
            throw regla("La alternativa no conserva una evaluación comercial y un precio comparable fuera del presupuesto.");
    }
    private static void exigirMismoContexto(Alternativa a,Alternativa b) {
        var x=a.original();var y=b.original();
        if(!Objects.equals(x.consulta(),y.consulta())||x.versionConsulta()!=y.versionConsulta()
                ||!Objects.equals(x.versionRegla(),y.versionRegla())||!Objects.equals(x.corte(),y.corte())||x.w()!=y.w()
                ||!Objects.equals(a.moneda(),b.moneda())||!Objects.equals(a.concepto(),b.concepto())
                ||!Objects.equals(a.periodo(),b.periodo())||a.maximoOriginal().compareTo(b.maximoOriginal())!=0)
            throw regla("Los escalones requieren la misma consulta, versión, regla, corte, preferencias y contexto económico.");
    }
    private static ValorLogico importe(BigDecimal valor,String moneda) {return ValorLogico.deImporte(valor,moneda);}
    private static ReglaNegocioException regla(String mensaje) {return new ReglaNegocioException(mensaje);}
}
