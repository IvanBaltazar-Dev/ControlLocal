package com.controllocal.service.negociacion;

import com.controllocal.service.OfertaService.*;
import com.controllocal.domain.comercial.Oferta;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;

/** Forma de comandos RF046–049; la autoridad y la suficiencia contextual son del caso de uso. */
public final class ContenidoNegociacion {
    private ContenidoNegociacion() { }
    public static String texto(String valor) { return valor==null||valor.isBlank()?null:valor.trim(); }
    public static long identidad(Long valor,String campo) {
        if(valor==null||valor<=0) throw new ReglaNegocioException("Identifica "+campo+".");
        return valor;
    }
    public static long version(Long valor,String campo) {
        if(valor==null||valor<0) throw new ReglaNegocioException("Aporta la versión observada de "+campo+".");
        return valor;
    }
    public static ParteReal parte(ParteReal parte) {
        if(parte==null)throw new ReglaNegocioException("Identifica la parte real que manifestó el hecho.");
        String lado=texto(parte.lado());
        lado=lado==null?null:lado.toUpperCase(Locale.ROOT);
        if(lado==null||!Oferta.LADOS.contains(lado))throw new ReglaNegocioException("Declara el lado I o P de la parte real.");
        return new ParteReal(lado,identidad(parte.persona(),"la persona manifestante"));
    }
    public static Dinero dinero(Dinero dinero) {
        if(dinero==null||dinero.importe()==null||dinero.importe().signum()<=0)
            throw new ReglaNegocioException("El importe declarado debe ser mayor que cero.");
        // oferta.monto es NUMERIC(14,2): rechazar pérdida de precisión, nunca redondear dinero al guardar.
        var exacto=dinero.importe().stripTrailingZeros();
        if(exacto.scale()>2||exacto.precision()-exacto.scale()>12)
            throw new ReglaNegocioException("El importe no cabe exactamente en la precisión monetaria admitida.");
        String moneda=texto(dinero.moneda());moneda=moneda==null?null:moneda.toUpperCase(Locale.ROOT);
        if(moneda==null||!PrecioPropiedad.MONEDAS.contains(moneda))throw new ReglaNegocioException("Declara una moneda admitida para la oferta.");
        // Base y período son declaraciones; no convertirlos ni completar por operación.
        return new Dinero(dinero.importe(),moneda,texto(dinero.base()),texto(dinero.periodo()));
    }
    public static LocalDate fecha(LocalDate fecha) {
        if(fecha==null)throw new ReglaNegocioException("Declara la fecha efectiva del hecho.");
        return fecha;
    }
    public static ObjetoExacto objeto(ObjetoExacto objeto) {
        if(objeto==null||objeto.tipo()==null)throw new ReglaNegocioException("Identifica el objeto exacto de la propuesta.");
        identidad(objeto.encargo(),"el Encargo");version(objeto.versionEncargo(),"Encargo");
        if(objeto.tipo()==TipoObjeto.UNIDAD) {
            identidad(objeto.propiedad(),"la unidad");version(objeto.versionFisica(),"la unidad física");
            if(objeto.composicion()!=null||objeto.versionComposicion()!=null)
                throw new ReglaNegocioException("La referencia de unidad no es una composición.");
            if(objeto.unidades()!=null&&!objeto.unidades().isEmpty()&&!objeto.unidades().equals(List.of(objeto.propiedad())))
                throw new ReglaNegocioException("La unidad declarada no corresponde al objeto ofrecido.");
        } else {
            identidad(objeto.composicion(),"la composición");version(objeto.versionComposicion(),"composición");
            if(objeto.propiedad()!=null||objeto.versionFisica()!=null)
                throw new ReglaNegocioException("La composición no atribuye su total a una unidad física.");
            if(objeto.unidades()==null||objeto.unidades().isEmpty())
                throw new ReglaNegocioException("Identifica las unidades de la versión de composición.");
            for(Long unidad:objeto.unidades())identidad(unidad,"cada unidad de composición");
            if(new HashSet<>(objeto.unidades()).size()!=objeto.unidades().size())
                throw new ReglaNegocioException("La composición no repite unidades.");
        }
        return objeto;
    }
}
