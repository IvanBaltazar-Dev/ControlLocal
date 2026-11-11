package com.controllocal.service.demanda;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Sujeto;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.matching.ContratoMatching;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.CondicionesEconomicas;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/** D1: único adaptador de criterios declarados al contrato tipado del motor. */
@Component
public class NormalizadorCriteriosDemanda {
    public record Normalizado(Criterio criterio,String rotulo,String unidad,String tipoDato) { }
    private final CatalogoAtributoRepository catalogo;
    private final GobiernoCatalogoCapturaService gobierno;
    public NormalizadorCriteriosDemanda(CatalogoAtributoRepository catalogo,GobiernoCatalogoCapturaService gobierno){
        this.catalogo=catalogo;this.gobierno=gobierno;
    }

    /** D1-07/26 y D3-01: cambiar el contexto no reinterpreta criterios conservados. */
    public void validarContextoConservado(List<Normalizado> conservados,Actor actor,
            Set<String> tipos,String operacion) {
        if(conservados.isEmpty())return;
        var pedidos=conservados.stream().map(n->{
            var c=n.criterio();
            var alternativas=c.alternativas().stream().map(a->new AlternativaPedida(a.operador().name(),
                    a.valor(),a.hasta(),a.negada(),a.contexto(),a.metadatos())).toList();
            return new CriterioPedido(c.clave(),null,c.exigencia()==Exigencia.OBLIGATORIO?"I":"D",
                    null,null,null,null,null,null,null,null,c.prioridad()==null?null:c.prioridad().peso(),
                    null,null,false,alternativas);
        }).toList();
        List<Normalizado> validados;
        try {
            validados=normalizar(pedidos,actor,tipos,operacion);
        } catch(ReglaNegocioException e) {
            throw regla("Declara explícitamente los criterios para cambiar la operación o los tipos: "+e.getMessage());
        }
        if(!validados.equals(conservados))
            throw regla("Declara explícitamente los criterios del contexto nuevo; no se adaptan ni retiran los anteriores automáticamente.");
    }

    public List<Normalizado> normalizar(List<CriterioPedido> pedidos,Actor actor,
            Set<String> tipos,String operacion) {
        if(pedidos==null)return List.of();
        Map<String,CatalogoAtributo> definiciones=new LinkedHashMap<>();
        for(var d:catalogo.disponiblesPara(actor.idOrganizacion())) {
            var previa=definiciones.get(d.getClave());
            if(previa==null||d.getOrganizacionId()!=null)definiciones.put(d.getClave(),d);
        }
        Map<String,Normalizado> resultado=new LinkedHashMap<>();
        for(var p:pedidos) {
            if(p==null||p.clave()==null||p.clave().isBlank())throw regla("Cada criterio debe identificar su definición.");
            var d=definiciones.get(p.clave());
            if(d==null)throw regla("La definición de "+p.clave()+" no está vigente en este contexto.");
            if(!tipos.isEmpty()&&tipos.stream().noneMatch(t->d.sujeto()==Sujeto.PROPIEDAD
                    ?d.aplicaA(t):d.aplicaA(t,OperacionInmobiliaria.desde(operacion).codigo())))
                throw regla("El criterio "+p.clave()+" no aplica a los tipos solicitados.");
            var actual=(d.sujeto()==Sujeto.PROPIEDAD
                    ?catalogo.bloquearActualParaCaptura(actor.idOrganizacion(),p.clave())
                    :catalogo.bloquearVersionDeEncargo(actor.idOrganizacion(),p.clave()))
                    .orElseThrow(()->new ConflictoException("La definición del criterio cambió."));
            if(!Objects.equals(actual.getId(),d.getId())||actual.getVersion()!=d.getVersionDefinicion())
                throw new ConflictoException("La definición cambió durante la lectura; revisa su identidad y versión antes de guardar.");
            long version=actual.getVersion();
            Exigencia exigencia=exigencia(p.peso());
            Prioridad prioridad=prioridad(p.prioridad(),exigencia);
            List<AlternativaPedida> alternativas;
            if(p.alternativas()!=null) {
                if(p.alternativas().isEmpty())throw regla("El grupo de alternativas está incompleto.");
                if(p.operador()!=null||valor(p)!=null||p.valorNumeroHasta()!=null||p.valorFechaHasta()!=null)
                    throw regla("Declara un valor simple o alternativas, sin dos representaciones del mismo criterio.");
                alternativas=p.alternativas();
            } else alternativas=List.of(new AlternativaPedida(p.operador(),valor(p),hasta(p),
                    p.negada(),p.contexto(),p.metadatos()));
            List<Condicion> condiciones=new ArrayList<>();
            for(var a:alternativas) {
                if(a==null)throw regla("Una alternativa no puede ser nula.");
                OperadorCriterio op;
                try {op=OperadorCriterio.desde(a.operador());}
                catch(IllegalArgumentException e){throw regla(e.getMessage());}
                if(!op.aplicaA(d.tipo()))throw regla("El operador no corresponde al tipo de "+p.clave()+".");
                var meta=a.metadatos();
                if(meta!=null&&meta.versionDefinicion()!=null&&meta.versionDefinicion()!=version)
                    throw new ConflictoException("La definición de "+p.clave()+" cambió desde la versión observada.");
                String referencia="CAT:D:"+d.getId()+":"+version;
                if(meta!=null&&meta.referenciaDefinicion()!=null&&!meta.referenciaDefinicion().equals(referencia))
                    throw new ConflictoException("La referencia de definición no corresponde al criterio vigente.");
                EstadoDato estado=meta==null?(a.valor()==null?EstadoDato.FALTANTE:EstadoDato.CONOCIDO):meta.estado();
                Causa causa=meta==null?null:meta.causa();
                var contexto=a.contexto()==null?new ContextoValor(null,d.getUnidad(),
                        a.valor()==null?null:a.valor().moneda(),null,operacion,null):a.contexto();
                if(contexto.operacion()==null)
                    contexto=new ContextoValor(contexto.concepto(),contexto.unidad(),contexto.moneda(),contexto.periodo(),
                            operacion,contexto.configuracion(),contexto.jurisdiccion(),contexto.referenciaCatalogo(),contexto.versionFuente());
                if(d.tipo()==TipoDato.IMPORTE&&estado==EstadoDato.CONOCIDO&&contexto.moneda()!=null)
                    contexto=new ContextoValor(contexto.concepto(),contexto.unidad(),CondicionesEconomicas.moneda(contexto.moneda(),"del contexto del criterio"),contexto.periodo(),contexto.operacion(),contexto.configuracion(),contexto.jurisdiccion(),contexto.referenciaCatalogo(),contexto.versionFuente());
                if(contexto.operacion()!=null&&!contexto.operacion().equals(operacion))
                    throw regla("La alternativa no pertenece a la operación declarada.");
                if(estado==EstadoDato.CONOCIDO&&a.valor()==null)throw regla("Un criterio conocido necesita el operando declarado.");
                ValorLogico v=monedaCanonica(a.valor(),d.tipo(),estado),h=monedaCanonica(a.hasta(),d.tipo(),estado);
                if(v!=null&&!forma(d.tipo(),v)) {estado=EstadoDato.FALTANTE;causa=Causa.VALOR_INVALIDO;v=null;h=null;}
                if(d.tipo()==TipoDato.IMPORTE&&v!=null&&v.moneda()==null) {estado=EstadoDato.FALTANTE;causa=Causa.MONEDA_NO_COMPARABLE;}
                if(d.tipo()==TipoDato.IMPORTE&&v!=null&&contexto.moneda()!=null&&v.moneda()!=null&&!contexto.moneda().equals(v.moneda()))
                    throw regla("La moneda del operando y su contexto se contradicen.");
                if(op.esRango()&&(h==null||v==null)) {estado=EstadoDato.FALTANTE;causa=Causa.LIMITES_INSUFICIENTES;}
                if(h!=null&&!forma(d.tipo(),h)){estado=EstadoDato.FALTANTE;causa=Causa.VALOR_INVALIDO;h=null;}
                if(h!=null&&v!=null&&comparar(v,h)>0)throw regla("Los límites del criterio son incompatibles.");
                if(!op.esRango()&&h!=null)throw regla("Sólo ENTRE admite un segundo límite.");
                if(op==OperadorCriterio.INCLUYE&&v!=null&&v.valores()!=null&&v.valores().isEmpty()) {
                    estado=EstadoDato.FALTANTE;causa=Causa.CRITERIO_NO_INTERPRETABLE;v=null;
                }
                if(d.sujeto()==Sujeto.PROPIEDAD&&CatalogoAtributo.CLAVE_ZONIFICACION.equals(d.getClave())) {
                    var territorial=validarTerritorio(d,v,estado,causa,contexto,actor);
                    v=territorial.valor();estado=territorial.estado();causa=territorial.causa();contexto=territorial.contexto();
                } else if((d.tipo()==TipoDato.LISTA||d.tipo()==TipoDato.LISTA_MULTIPLE)&&v!=null) {
                    var opciones=d.opcionesVigentes().stream().map(o->o.getValor()).toList();
                    var elegidas=v.valores()!=null?v.valores():v.texto()==null?List.<String>of():List.of(v.texto());
                    if(!opciones.containsAll(elegidas)) {estado=EstadoDato.FALTANTE;causa=Causa.VALOR_INVALIDO;v=null;h=null;}
                }
                var observados=new MetadatosDato(estado,causa,referencia,version,
                        meta==null?null:meta.evidenciaRef(),meta==null?null:meta.fuente(),
                        meta==null?null:meta.observadoEn(),meta==null?null:meta.vigenteDesde(),meta==null?null:meta.vigenteHasta());
                condiciones.add(new Condicion(ContratoMatching.selector(d.sujeto(),d.getClave()),d.tipo(),op,v,h,a.negada(),contexto,observados));
            }
            var c=new Criterio("criterio:"+p.clave(),p.clave(),exigencia,prioridad,
                    List.copyOf(new LinkedHashSet<>(condiciones)));
            var normalizado=new Normalizado(c,d.getRotulo(),d.getUnidad(),d.getTipoDato());
            var anterior=resultado.get(p.clave());
            resultado.put(p.clave(),anterior==null?normalizado:integrar(anterior,normalizado));
        }
        return List.copyOf(resultado.values());
    }
    private record Territorial(ValorLogico valor,EstadoDato estado,Causa causa,ContextoValor contexto) { }
    private Territorial validarTerritorio(CatalogoAtributo d,ValorLogico valor,EstadoDato estado,
            Causa causa,ContextoValor contexto,Actor actor) {
        if(vacio(contexto.jurisdiccion())||vacio(contexto.referenciaCatalogo()))
            return estado==EstadoDato.CONOCIDO
                    ?new Territorial(null,EstadoDato.FALTANTE,Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE,contexto)
                    :new Territorial(valor,estado,causa,contexto);
        var opciones=gobierno.opcionesVigentes(actor.idOrganizacion(),d.getClave(),contexto.jurisdiccion().trim());
        if(opciones.motivo()!=null||vacio(opciones.jurisdiccion())||vacio(opciones.versionFuente()))
            return estado==EstadoDato.CONOCIDO
                    ?new Territorial(null,EstadoDato.FALTANTE,Causa.VIGENCIA_NO_ACREDITADA,contexto)
                    :new Territorial(valor,estado,causa,contexto);
        if(!contexto.referenciaCatalogo().equals(opciones.referenciaCatalogo())
                ||!contexto.jurisdiccion().trim().equals(opciones.jurisdiccion())
                ||!vacio(contexto.versionFuente())&&!contexto.versionFuente().equals(opciones.versionFuente()))
            throw new ConflictoException("El perfil territorial o su fuente cambiaron desde la observación del criterio.");
        var conocido=new ContextoValor(contexto.concepto(),contexto.unidad(),contexto.moneda(),contexto.periodo(),
                contexto.operacion(),contexto.configuracion(),opciones.jurisdiccion(),opciones.referenciaCatalogo(),opciones.versionFuente());
        var elegidas=valor==null?List.<String>of():valor.valores()!=null?valor.valores():valor.texto()==null?List.<String>of():List.of(valor.texto());
        var permitidas=opciones.opciones().stream().map(GobiernoCatalogoCapturaService.OpcionCatalogo::codigo).toList();
        if(valor!=null&&!permitidas.containsAll(elegidas))
            return new Territorial(null,EstadoDato.FALTANTE,Causa.VALOR_INVALIDO,conocido);
        return new Territorial(valor,estado,causa,conocido);
    }
    private static boolean vacio(String s){return s==null||s.isBlank();}
    private static Normalizado integrar(Normalizado a,Normalizado b) {
        var x=a.criterio();var y=b.criterio();
        if(x.exigencia()!=y.exigencia()||x.prioridad()!=y.prioridad())
            throw regla("Resuelve la obligatoriedad o prioridad contradictoria del criterio repetido.");
        if(x.alternativas().equals(y.alternativas())||mismoSignificado(x.alternativas(),y.alternativas()))return a;
        if(x.alternativas().size()==1&&y.alternativas().size()==1) {
            var p=x.alternativas().getFirst();var q=y.alternativas().getFirst();
            if(p.contexto().equals(q.contexto())&&p.tipo()==q.tipo()&&!p.negada()&&!q.negada()
                    &&p.metadatos().estado()==EstadoDato.CONOCIDO&&q.metadatos().estado()==EstadoDato.CONOCIDO
                    &&mismaAdmisibilidad(p.metadatos(),q.metadatos())) {
                var min=p.operador()==OperadorCriterio.MINIMO?p:q.operador()==OperadorCriterio.MINIMO?q:null;
                var max=p.operador()==OperadorCriterio.MAXIMO?p:q.operador()==OperadorCriterio.MAXIMO?q:null;
                if(min!=null&&max!=null&&min.valor()!=null&&max.valor()!=null) {
                    if(comparar(min.valor(),max.valor())>0)throw regla("Las restricciones del criterio se contradicen.");
                    var rango=new Condicion(p.claveDato(),p.tipo(),OperadorCriterio.ENTRE,min.valor(),max.valor(),false,p.contexto(),p.metadatos());
                    return new Normalizado(new Criterio(x.referencia(),x.clave(),x.exigencia(),x.prioridad(),List.of(rango)),a.rotulo(),a.unidad(),a.tipoDato());
                }
            }
        }
        throw regla("Las restricciones repetidas necesitan una interpretación única; no se elige una silenciosamente.");
    }
    private static boolean mismoSignificado(List<Condicion> a,List<Condicion> b) {
        if(a.size()!=b.size())return false;
        for(var x:a)if(b.stream().noneMatch(y->x.claveDato().equals(y.claveDato())&&x.tipo()==y.tipo()
                &&x.operador()==y.operador()&&mismoValor(x.valor(),y.valor())&&mismoValor(x.hasta(),y.hasta())
                &&x.negada()==y.negada()&&x.contexto().equals(y.contexto())
                &&mismaAdmisibilidad(x.metadatos(),y.metadatos())))return false;
        return true; // Todas las fuentes originales también se conservan en el evento del comando.
    }
    /** D1-18/19/25: deduplicar no elige una vigencia ni elimina una causa activa. */
    private static boolean mismaAdmisibilidad(MetadatosDato a,MetadatosDato b) {
        return a.estado()==b.estado()&&a.causa()==b.causa()
                &&Objects.equals(a.vigenteDesde(),b.vigenteDesde())
                &&Objects.equals(a.vigenteHasta(),b.vigenteHasta());
    }
    private static boolean mismoValor(ValorLogico a,ValorLogico b) {
        if(a==null||b==null)return a==b;
        return Objects.equals(a.texto(),b.texto())&&Objects.equals(a.booleano(),b.booleano())
            &&Objects.equals(a.fecha(),b.fecha())&&Objects.equals(a.moneda(),b.moneda())
            &&(a.numero()==null||b.numero()==null?a.numero()==b.numero():a.numero().compareTo(b.numero())==0)
            &&(a.valores()==null||b.valores()==null?a.valores()==b.valores():new HashSet<>(a.valores()).equals(new HashSet<>(b.valores())));
    }
    private static ValorLogico monedaCanonica(ValorLogico v,TipoDato tipo,EstadoDato estado) {
        if(v==null||v.moneda()==null)return v;
        if(tipo!=TipoDato.IMPORTE)throw regla("Una cantidad no monetaria no lleva moneda.");
        String moneda=estado==EstadoDato.CONOCIDO?CondicionesEconomicas.moneda(v.moneda(),"del criterio"):v.moneda();
        return new ValorLogico(v.texto(),v.numero(),v.booleano(),v.fecha(),moneda,v.valores());
    }
    private static boolean forma(TipoDato tipo,ValorLogico v) {
        int presentes=(v.texto()!=null?1:0)+(v.numero()!=null?1:0)+(v.booleano()!=null?1:0)+(v.fecha()!=null?1:0)+(v.valores()!=null?1:0);
        if(presentes!=1)return false;
        return switch(tipo) {
            case TEXTO -> v.texto()!=null&&!v.texto().isBlank();
            case LISTA -> v.texto()!=null||v.valores()!=null;
            case LISTA_MULTIPLE -> v.valores()!=null;
            case ENTERO -> v.numero()!=null&&v.numero().stripTrailingZeros().scale()<=0;
            case DECIMAL,IMPORTE -> v.numero()!=null;
            case BOOLEANO -> v.booleano()!=null;
            case FECHA -> v.fecha()!=null;
        };
    }
    private static int comparar(ValorLogico a,ValorLogico b) {
        if(a.numero()!=null&&b.numero()!=null&&Objects.equals(a.moneda(),b.moneda()))return a.numero().compareTo(b.numero());
        if(a.fecha()!=null&&b.fecha()!=null)return a.fecha().compareTo(b.fecha());
        throw regla("Los límites no comparten concepto tipado y moneda.");
    }
    private static Exigencia exigencia(String valor) {
        if(valor==null)throw regla("Declara si el criterio es obligatorio o preferencia.");
        return switch(valor.toUpperCase(Locale.ROOT)) {
            case "I","INDISPENSABLE","OBLIGATORIO" -> Exigencia.OBLIGATORIO;
            case "D","DESEABLE","PREFERENCIA" -> Exigencia.PREFERENCIA;
            default -> throw regla("Obligatoriedad de criterio no reconocida.");
        };
    }
    private static Prioridad prioridad(Integer valor,Exigencia exigencia) {
        if(exigencia==Exigencia.OBLIGATORIO) {
            if(valor!=null)throw regla("Una obligación no es una preferencia ponderada.");
            return null;
        }
        if(valor==null)return Prioridad.NORMAL;
        return switch(valor){case 3->Prioridad.IMPORTANTE;case 2->Prioridad.NORMAL;case 1->Prioridad.COMPLEMENTARIA;
            default->throw regla("La prioridad debe ser 1, 2 o 3.");};
    }
    private static ValorLogico valor(CriterioPedido p) {
        int n=(p.valorTexto()!=null?1:0)+(p.valorNumero()!=null?1:0)+(p.valorBooleano()!=null?1:0)+(p.valorFecha()!=null?1:0)+(p.valores()!=null?1:0);
        if(n==0)return null;if(n>1)throw regla("Un operando no tiene dos tipos de valor.");
        return new ValorLogico(p.valorTexto(),p.valorNumero(),p.valorBooleano(),p.valorFecha(),p.moneda(),p.valores());
    }
    private static ValorLogico hasta(CriterioPedido p) {
        if(p.valorNumeroHasta()!=null&&p.valorFechaHasta()!=null)throw regla("El límite tiene dos tipos.");
        return p.valorNumeroHasta()!=null?new ValorLogico(null,p.valorNumeroHasta(),null,null,p.moneda(),null):ValorLogico.deFecha(p.valorFechaHasta());
    }
    private static ReglaNegocioException regla(String mensaje){return new ReglaNegocioException(mensaje);}
}
