package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.PropiedadUniversalService.AtributoFicha;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.controllocal.service.soporte.EntradasDeValores.*;

/** Condiciones del episodio: un editor gobernado, una interpretación y ninguna escritura física. */
@Component
public class CondicionesDinamicasEncargo {
    private final AtributosDeEncargo editor;
    private final LectorPorAutoridad lector;
    public CondicionesDinamicasEncargo(AtributosDeEncargo editor,LectorPorAutoridad lector){this.editor=editor;this.lector=lector;}
    public boolean aplicar(Captacion c,List<ValorAtributo> entradas,List<String> retiradas,Actor actor,Procedencia acto){
        var valores=atributosDeEdicion(entradas);var borrar=clavesABorrar(retiradas,valores);
        if(valores.isEmpty()&&borrar.isEmpty())return false;
        var donde=Comercializacion.de(c,c.getPropiedad());var definiciones=editor.definicionesDe(c.getOrganizacionId(),donde);
        validarMetadatos(c.getOrganizacionId(),donde.tipoPropiedad(),donde.tipoOperacion(),valores,definiciones);
        var antes=lector.deEncargo(c.getId()).todos();
        for(var entrada:entrantes(valores,acto))editor.escribir(actor,donde,entrada);
        for(String clave:borrar)if(!editor.retirar(actor,donde,clave,ProcedenciaDelValor.delActo(acto)).gobernada())
            throw new ReglaNegocioException("La condición \""+clave+"\" no está en el catálogo del Encargo.");
        return !antes.equals(lector.deEncargo(c.getId()).todos());
    }
    public ValoresGobernados propuesta(long org,String tipo,String operacion,List<ValorAtributo> entradas,Procedencia acto){
        var valores=atributosDeEdicion(entradas);if(valores.isEmpty())return ValoresGobernados.vacio();
        var definiciones=editor.definicionesDe(org,tipo,operacion);validarMetadatos(org,tipo,operacion,valores,definiciones);
        return editor.interpretarPropuesta(org,tipo,operacion,entrantes(valores,acto));
    }
    private void validarMetadatos(long org,String tipo,String operacion,Map<String,ValorAtributo> entradas,Map<String,CatalogoAtributo> definiciones){
        entradas.forEach((clave,valor)->{
            var d=definiciones.get(clave);if(d==null){
                // Reutiliza la causa del editor: sujeto físico, clave inexistente o tipo/operación.
                editor.definicionAplicable(org,clave,tipo,operacion);
                throw new ConflictoException("La definición editable de la condición cambió; vuelve a consultar su contrato.");
            }
            var meta=valor.captura();if(meta==null)return;
            if(meta.estado()!=SuficienciaInmueble.EstadoDato.CONOCIDO||meta.noSabe()||meta.concepto()!=null||meta.referenciaCatalogo()!=null)
                throw new ReglaNegocioException("Una condición se declara con su valor conocido y definición; un retiro se indica expresamente.");
            if(meta.versionDefinicion()!=null&&!meta.versionDefinicion().equals(Long.toString(d.getVersionDefinicion())))
                throw new ConflictoException("La definición de la condición \""+clave+"\" cambió desde la versión observada.");
        });
    }
    public ValoresGobernados valores(Captacion c){return lector.deEncargo(c.getId());}
    public Map<String,Long> versiones(long org,String tipo,String operacion,Collection<String> claves){
        if(claves.isEmpty())return Map.of();var definiciones=editor.definicionesDe(org,tipo,operacion);Map<String,Long> versiones=new TreeMap<>();
        for(String clave:claves){var d=definiciones.get(clave);if(d!=null)versiones.put(clave,d.getVersionDefinicion());}return versiones;
    }
    public Map<String,Long> versiones(Captacion c){var valores=valores(c);if(valores.claves().isEmpty())return Map.of();
        var defs=editor.definicionesParaLeer(c.getOrganizacionId(),Comercializacion.de(c,c.getPropiedad()),valores.claves());Map<String,Long> versiones=new TreeMap<>();
        valores.claves().forEach(clave->{var d=defs.get(clave);if(d!=null)versiones.put(clave,d.getVersionDefinicion());});return versiones;}
    public Map<String,Object> paraDocumento(long org,String tipo,String operacion,ValoresGobernados valores){
        if(valores.claves().isEmpty())return Map.of();
        var actuales=editor.definicionesDe(org,tipo,operacion);Map<String,Object> resultado=new TreeMap<>();
        valores.todos().forEach((clave,valor)->{
            var d=actuales.get(clave);Map<String,Object> dato=new LinkedHashMap<>();dato.put("rotulo",d==null?clave:d.getRotulo());
            dato.put("tipoDato",d==null?null:d.getTipoDato());dato.put("unidad",d==null?null:d.getUnidad());dato.put("valor",valor);resultado.put(clave,dato);
        });return resultado;
    }
    public Map<String,Object> paraDocumento(Captacion c){
        return paraDocumento(c,valores(c));
    }
    public Map<String,Object> paraDocumento(Captacion c,ValoresGobernados valores){
        if(valores.claves().isEmpty())return Map.of();
        var definiciones=editor.definicionesParaLeer(c.getOrganizacionId(),Comercializacion.de(c,c.getPropiedad()),valores.claves());
        Map<String,Object> salida=new TreeMap<>();valores.todos().forEach((clave,v)->{
            var d=definiciones.get(clave);Map<String,Object> dato=new LinkedHashMap<>();dato.put("rotulo",d==null?clave:d.getRotulo());
            dato.put("tipoDato",d==null?null:d.getTipoDato());dato.put("unidad",d==null?null:d.getUnidad());dato.put("valor",v);salida.put(clave,dato);
        });return salida;
    }
    public Map<String,SuficienciaInmueble.Dato> paraSuficiencia(long org,String tipo,String operacion,
            Map<String,SuficienciaInmueble.Dato> fisicos,ValoresGobernados comerciales){
        Map<String,SuficienciaInmueble.Dato> datos=new TreeMap<>(fisicos);
        editor.definicionesDe(org,tipo,operacion).keySet().forEach(clave->{datos.remove(clave);datos.remove("moneda:"+clave);});
        comerciales.todos().forEach((clave,valor)->{
            Object escalar=valor.numero()!=null?valor.numero():valor.texto()!=null?valor.texto():valor.booleano()!=null?valor.booleano():valor.fecha()!=null?valor.fecha():valor.valores();
            datos.put(clave,SuficienciaInmueble.Dato.conocido(escalar));if(valor.moneda()!=null)datos.put("moneda:"+clave,SuficienciaInmueble.Dato.conocido(valor.moneda()));
        });return datos;
    }
    public List<AtributoFicha> ficha(Captacion c,boolean puedeEditar){
        var valores=valores(c);if(valores.claves().isEmpty())return List.of();
        var donde=Comercializacion.de(c,c.getPropiedad());var defs=editor.definicionesParaLeer(c.getOrganizacionId(),donde,valores.claves());
        List<AtributoFicha> salida=new ArrayList<>();
        for(String clave:valores.claves()){
            var d=defs.get(clave);var sustituta=d==null||d.getSustituidaPor()==null?null:defs.get(d.getSustituidaPor());
            var vigencia=ContratoDeEscritura.deEncargo(d,donde.tipoPropiedad(),donde.tipoOperacion(),sustituta==null?null:sustituta.getRotulo());
            var valor=valores.valor(clave);boolean editable=puedeEditar&&vigencia.editable();
            String motivo=!vigencia.editable()?vigencia.motivoNoEditable():puedeEditar?null:"La edición ordinaria de este Encargo no está autorizada en el contexto actual.";
            salida.add(new AtributoFicha(clave,d==null?clave:d.getRotulo(),d==null?null:d.getTipoDato(),d==null?null:d.getUnidad(),valor.comoTexto(),valor.moneda(),valor.valores(),vigencia.estadoDato(),editable,motivo,d!=null&&!d.isActivo(),d==null?null:d.getSustituidaPor()));
        }
        salida.sort(Comparator.comparing(AtributoFicha::retirado).thenComparingInt(a->defs.containsKey(a.clave())?defs.get(a.clave()).getOrden():Integer.MAX_VALUE).thenComparing(AtributoFicha::clave));return List.copyOf(salida);
    }
}
