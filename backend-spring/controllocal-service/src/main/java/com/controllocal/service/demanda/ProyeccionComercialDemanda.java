package com.controllocal.service.demanda;

import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ContratoMatching;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.network.PoliticaProyeccionNetworkV1;
import com.controllocal.service.soporte.ValorLogico;

import java.time.LocalDate;
import java.util.*;

/** Representación única del subset comercial aprobado; no obtiene permisos ni lee datos. */
public final class ProyeccionComercialDemanda {
    private ProyeccionComercialDemanda() { }
    public static final Set<String> CAMPOS=Set.of("operacion","tiposAdmitidos","zona","finalidad","estado","vigencia","criterios","version");
    /** Derivada de la lista positiva vigente, sin precio ni otra condición económica. */
    public static final Set<String> SELECTORES_FISICOS=PoliticaProyeccionNetworkV1.CAMPOS.stream()
            .filter(s->s.startsWith("PROPIEDAD:")||Set.of("ENCARGO:se_ofrece_amoblado","ENCARGO:se_entrega_implementado").contains(s))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    public record Base(long version,String operacion,List<String> tipos,List<String> zonas,
                       String finalidad,String estado,LocalDate desde,LocalDate hasta) {
        public Base {tipos=tipos==null?null:List.copyOf(tipos);zonas=zonas==null?null:List.copyOf(zonas);}
    }
    public static void exigirSelectores(Set<String> selectores) {
        if(selectores==null||!SELECTORES_FISICOS.containsAll(selectores))
            throw new ReglaNegocioException("El alcance debe enumerar selectores físicos autorizados de la definición real.");
    }
    /** La misma interpretación de operación/tipos OR/distritos para consulta propia y física reducida. */
    public static List<Criterio> criteriosBase(String operacion,List<String> tipos,List<String> zonas) {
        var contexto=new ContextoValor(null,null,null,null,operacion,null);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        List<Criterio> salida=new ArrayList<>();
        salida.add(base("operacion",List.of(new Condicion(ContratoMatching.OPERACION,TipoDato.LISTA,OperadorCriterio.IGUAL,
                ValorLogico.deTexto(operacion),null,false,contexto,meta))));
        if(!tipos.isEmpty())salida.add(base("tiposAdmitidos",tipos.stream().map(t->new Condicion(ContratoMatching.TIPO_PROPIEDAD,
                TipoDato.LISTA,OperadorCriterio.IGUAL,ValorLogico.deTexto(t),null,false,contexto,meta)).toList()));
        if(!zonas.isEmpty())salida.add(base("zona",zonas.stream().map(z->new Condicion(ContratoMatching.DISTRITO,
                TipoDato.LISTA,OperadorCriterio.IGUAL,ValorLogico.deTexto(z),null,false,contexto,meta)).toList()));
        return List.copyOf(salida);
    }
    private static Criterio base(String clave,List<Condicion> alternativas) {
        return new Criterio("base:"+clave,"base:"+clave,Exigencia.OBLIGATORIO,null,alternativas);
    }
    public static boolean permitido(Criterio criterio,Set<String> selectores) {
        exigirSelectores(selectores);
        return !criterio.alternativas().isEmpty()&&criterio.alternativas().stream().allMatch(a->
                selectores.contains(a.claveDato())&&a.tipo()!=TipoDato.IMPORTE
                &&a.contexto().moneda()==null&&a.contexto().configuracion()==null&&a.contexto().periodo()==null
                &&(a.valor()==null||a.valor().moneda()==null)&&(a.hasta()==null||a.hasta().moneda()==null));
    }
    public static Map<String,Object> campos(Base base,Set<String> campos,List<Criterio> criterios,Set<String> selectores) {
        if(campos==null||!CAMPOS.containsAll(campos))throw new ReglaNegocioException("Campos ajenos a la proyección comercial de Demanda.");
        var salida=new LinkedHashMap<String,Object>();
        for(String campo:campos)switch(campo) {
            case "operacion"->salida.put(campo,base.operacion());
            case "tiposAdmitidos"->salida.put(campo,Objects.requireNonNull(base.tipos(),"Tipos no consultados en el alcance.").stream().sorted().toList());
            case "zona"->salida.put(campo,Objects.requireNonNull(base.zonas(),"Zonas no consultadas en el alcance.").stream().sorted().toList());
            case "finalidad"->salida.put(campo,base.finalidad());
            case "estado"->salida.put(campo,base.estado());
            case "vigencia"->{var v=new LinkedHashMap<String,Object>();v.put("desde",base.desde());v.put("hasta",base.hasta());salida.put(campo,v);}
            case "version"->salida.put(campo,base.version());
            case "criterios"->{exigirSelectores(selectores);salida.put(campo,criterios.stream().filter(c->permitido(c,selectores))
                    .sorted(Comparator.comparing(Criterio::clave)).map(ProyeccionComercialDemanda::criterio).toList());}
            default->throw new ReglaNegocioException("Campo no reconocido.");
        }
        return Collections.unmodifiableMap(salida);
    }
    private static Map<String,Object> criterio(Criterio c) {
        var m=new LinkedHashMap<String,Object>();m.put("clave",c.clave());m.put("exigencia",c.exigencia());m.put("prioridad",c.prioridad());
        m.put("alternativas",c.alternativas().stream().map(a->{var v=new LinkedHashMap<String,Object>();
            v.put("operador",a.operador());v.put("valor",a.valor());v.put("hasta",a.hasta());v.put("negada",a.negada());
            v.put("unidad",a.contexto().unidad());v.put("concepto",a.contexto().concepto());
            v.put("jurisdiccion",a.contexto().jurisdiccion());v.put("versionFuente",a.contexto().versionFuente());
            v.put("estado",a.metadatos().estado());return v;}).toList());return m;
    }
}
