package com.controllocal.service.impl;

import com.controllocal.service.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.ConflictoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Compone metas vigentes con fuentesC4/C5/20.7–20.10; no reinterpreta el rendimiento histórico. */
@Service
public class RendimientoComercialServiceImpl implements RendimientoComercialService {
    private final MetaComercialService metas;private final HechosDeMetas hechos;private final ContextosDeActividad contexto;private final Alcances alcances;
    public RendimientoComercialServiceImpl(MetaComercialService m,HechosDeMetas h,ContextosDeActividad c,Alcances a){metas=m;hechos=h;contexto=c;alcances=a;}
    @Override @Transactional(readOnly=true) public Pulso pulso(String mes,Actor a){contexto.exigirOperador(a);if(!a.esBroker())throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();return del(mes,a).pulso();}
    @Override @Transactional(readOnly=true) public Rendimiento del(String mes,Actor actor){
        contexto.exigirOperador(actor);var corte=contexto.ahora();var hoy=corte.atZone(ZoneId.of("America/Lima")).toLocalDate();var periodo=PeriodoCalendario.desde(mes,hoy);var roles=alcances.de(actor).rolesAgente().stream().distinct().sorted().toList();
        var declaradas=metas.del(periodo.codigo(),actor);var lectura=hechos.leer(periodo.desde(),periodo.hasta(),corte,actor);
        var responsables=new ArrayList<PorResponsable>();var avances=new LinkedHashMap<String,List<Kpi>>();long conMeta=0,sinMeta=0,sinBase=0,sinCalendario=0;
        for(long rol:roles){var propias=new ArrayList<Kpi>();
            for(var meta:declaradas)if(!meta.historica()&&meta.idRolAgente()==rol){
                var k=IndicadorMetaComercial.exigir(meta.kpi());var op=meta.operacion();
                long conocidos=lectura.hechos().stream().filter(h->h.indicador()==k&&h.operacion().equals(op)&&Objects.equals(h.responsable(),rol)).count();
                boolean incompleto=lectura.faltantes().stream().anyMatch(f->f.indicador()==k&&f.operacion().equals(op)&&(f.responsable()==null||Objects.equals(f.responsable(),rol)));
                var av=AvanceDeMeta.de(incompleto?null:conocidos,conocidos,meta.valor()==null?null:meta.valor().longValue(),meta.calendario(),hoy);
                var dato=new Kpi(k.name(),op,k.rotulo(),k.unidad(),"EVENTOS_UNICOS_DEL_PERIODO_AL_CORTE",av);propias.add(dato);avances.computeIfAbsent(k.name()+"|"+op,x->new ArrayList<>()).add(dato);
                if(meta.valor()==null)sinMeta++;else conMeta++;if(av.actual()==null)sinBase++;if(meta.valor()!=null&&(meta.calendario()==null||meta.calendario().isEmpty()))sinCalendario++;
            }
            responsables.add(new PorResponsable(rol,declaradas.stream().filter(m->m.idRolAgente()==rol).map(MetaComercialService.MetaDeAgente::nombreAgente).filter(Objects::nonNull).findFirst().orElse(null),propias));
        }
        var equipo=new ArrayList<Kpi>();
        for(var k:IndicadorMetaComercial.values())for(String op:k.porOperacion()?List.of("VENTA","ALQUILER"):List.of("GENERAL")){
            var filas=avances.getOrDefault(k.name()+"|"+op,List.of());long conocidos=0,objetivo=0;boolean base=!filas.isEmpty(),cobertura=!filas.isEmpty();
            for(var f:filas){conocidos=Math.addExact(conocidos,f.avance().realizadoConocido());base&=f.avance().actual()!=null;if(f.avance().metaPeriodo()==null)cobertura=false;else objetivo=Math.addExact(objetivo,f.avance().metaPeriodo());}
            var metasDelIndicador=declaradas.stream().filter(m->!m.historica()&&m.kpi().equals(k.name())&&m.operacion().equals(op)).toList();
            var calendario=metasDelIndicador.isEmpty()?null:metasDelIndicador.getFirst().calendario();boolean distintos=metasDelIndicador.stream().anyMatch(m->!Objects.equals(m.calendario(),calendario));
            var av=AvanceDeMeta.de(base?conocidos:null,conocidos,cobertura?objetivo:null,distintos?null:calendario,hoy);
            if(distintos&&base&&cobertura){BigDecimal esperado=BigDecimal.ZERO;boolean todos=true;for(var f:filas){if(f.avance().esperadoHastaFechaCorte()==null)todos=false;else esperado=esperado.add(f.avance().esperadoHastaFechaCorte());}
                av=new AvanceDeMeta(av.actual(),av.realizadoConocido(),av.metaPeriodo(),av.faltante(),av.porcentajeMeta(),todos?esperado:null,null,null,null,null,"CALENDARIOS_POR_RESPONSABLE");}
            equipo.add(new Kpi(k.name(),op,k.rotulo(),k.unidad(),"EVENTOS_UNICOS_DEL_PERIODO_AL_CORTE",av));
        }
        contexto.exigirOperador(actor);if(!new HashSet<>(roles).equals(new HashSet<>(alcances.de(actor).rolesAgente()))||!declaradas.equals(metas.del(periodo.codigo(),actor)))throw new ConflictoException("Cambiaron metas o permisos durante el cálculo.");
        long fuera=lectura.hechos().stream().filter(h->h.responsable()!=null&&!roles.contains(h.responsable())).count();
        return new Rendimiento(IndicadorMetaComercial.VERSION,periodo,corte,equipo,responsables,actor.esBroker()?new Pulso(roles.size(),conMeta,sinMeta,sinBase,sinCalendario):null,declaradas.stream().filter(MetaComercialService.MetaDeAgente::historica).toList(),lectura.faltantes().stream().filter(f->f.responsable()==null).count(),fuera,
            "Metas de responsables actualmente autorizados; hechos atribuidos a su responsable histórico. Las fuentes sin atribución se informan una vez en cobertura, sin repartirlas ni inventar logros. El calendario cuenta fechas declaradas hasta la fecha de corte inclusive; el ritmo aritmético usa las posteriores, sin probabilidades ni umbrales.");
    }
}
