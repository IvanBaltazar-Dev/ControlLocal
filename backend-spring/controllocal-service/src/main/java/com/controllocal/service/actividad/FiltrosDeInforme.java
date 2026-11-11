package com.controllocal.service.actividad;

import com.controllocal.domain.comun.EstadosDominio.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.CasoInforme;
import com.controllocal.service.InteraccionService.MedicionInteraccion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.actividad.CatalogoInteracciones.TipoActividad;
import java.util.*;

/** Selección de proyecciones ya autorizadas; no modifica universos de mediciones ni estados. */
public final class FiltrosDeInforme {
    private FiltrosDeInforme() { }
    public enum HechoEconomico { CALCULO_ACTUAL,DEVENGO_ACTUAL,EXIGIBILIDAD_ACTUAL,COBRO_EN_PERIODO }
    public record Situacion(String recurso,String codigo) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo de situación no admitido.");}
    }
    public record Filtros(String operacion,Long responsable,TipoActividad tipoActividad,Situacion situacion,HechoEconomico hechoEconomico) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Filtro de informe no admitido.");}
        public static Filtros ninguno(){return new Filtros(null,null,null,null,null);}
    }
    public static Filtros validar(Filtros f,Collection<Long> permitidos){
        if(f==null)return Filtros.ninguno();
        if(f.responsable()!=null&&!permitidos.contains(f.responsable()))throw new NoEncontradoException("Responsable del informe");
        if(f.operacion()!=null&&!Set.of("VENTA","ALQUILER").contains(f.operacion()))throw new ReglaNegocioException("Selecciona venta o alquiler.");
        if(f.situacion()!=null){var s=f.situacion();boolean valida=s.recurso()!=null&&s.codigo()!=null&&switch(s.recurso()){
            case "ENCARGO"->Arrays.stream(EstadoCaptacion.values()).anyMatch(e->e.codigo().equals(s.codigo()));
            case "OPORTUNIDAD"->Arrays.stream(EstadoOportunidad.values()).anyMatch(e->e.codigo().equals(s.codigo()));
            case "PROSPECCION"->Arrays.stream(EstadoProspeccion.values()).anyMatch(e->e.codigo().equals(s.codigo()));
            default->false;};if(!valida)throw new ReglaNegocioException("La situación necesita recurso y código de su catálogo vigente.");}
        return f;
    }
    public static String operacion(CasoInforme c){return c.operacion()==null?null:OperacionInmobiliaria.desde(c.operacion()).name();}
    public static List<CasoInforme> seleccionar(List<CasoInforme> cartera,Filtros f,boolean responsable){
        var padres=new HashMap<Long,CasoInforme>();for(var c:cartera)if(c.tipo().equals("ENCARGO"))padres.put(c.id(),c);
        var acreditada=cartera.stream().map(c->{var padre=c.encargo()==null?null:padres.get(c.encargo());return c.tipo().equals("PROSPECCION")&&c.operacion()==null&&padre!=null?new CasoInforme(c.tipo(),c.id(),c.version(),c.responsable(),padre.operacion(),c.situacion(),c.registrada(),c.encargo()):c;}).toList();
        var porOperacion=acreditada.stream().filter(c->f.operacion()==null||f.operacion().equals(operacion(c))).toList();
        if(f.situacion()!=null){var s=f.situacion();var raices=porOperacion.stream().filter(c->c.tipo().equals(s.recurso())&&c.situacion().equals(s.codigo())).toList();
            var encargos=raices.stream().map(CasoInforme::encargo).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            var claves=raices.stream().map(c->c.tipo()+":"+c.id()).collect(java.util.stream.Collectors.toSet());
            porOperacion=porOperacion.stream().filter(c->c.tipo().equals(s.recurso())?claves.contains(c.tipo()+":"+c.id()):c.encargo()!=null&&encargos.contains(c.encargo())).toList();}
        return porOperacion.stream().filter(c->!responsable||f.responsable()==null||c.responsable()==f.responsable()).toList();
    }
    public static boolean vinculada(MedicionInteraccion i,Set<String> seleccion,Filtros f){boolean directo=f.situacion()!=null&&!f.situacion().recurso().equals("ENCARGO");return i.vinculos().stream().filter(v->!directo||v.tipo().equals(f.situacion().recurso())).anyMatch(v->seleccion.contains(v.tipo()+":"+v.referencia()));}
    public static Set<String> claves(List<CasoInforme> casos){return casos.stream().map(c->c.tipo()+":"+c.id()).collect(java.util.stream.Collectors.toSet());}
}
