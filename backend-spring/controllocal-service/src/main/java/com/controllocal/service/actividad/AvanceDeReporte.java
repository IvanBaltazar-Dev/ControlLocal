package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.ReportePropietarioService.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.ReporteAvanceRepository;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.Fuente;
import com.controllocal.domain.comercial.Visita;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** RF078: una sola composición de hechos para previsualización, versión persistida y preparación privada P0. */
@Component
public class AvanceDeReporte {
    public record Lectura(VistaPrevia avance,List<ReferenciaVersion> interacciones,List<Fuente> recursos) {
        public Lectura {interacciones=List.copyOf(interacciones);recursos=List.copyOf(recursos);}
    }
    private final ReporteAvanceRepository repo;
    private final EncargoService encargos;
    private final InteraccionService interacciones;
    private final AccesoOportunidad acceso;
    private final ContextosDeActividad contexto;
    private final Alcances alcances;
    public AvanceDeReporte(ReporteAvanceRepository r,EncargoService e,InteraccionService i,AccesoOportunidad a,ContextosDeActividad c,Alcances al){repo=r;encargos=e;interacciones=i;acceso=a;contexto=c;alcances=al;}
    @Transactional(readOnly=true)
    public Lectura preparar(long id,LocalDate desde,LocalDate hasta,Actor a){
        contexto.exigirOperador(a);var corte=contexto.ahora();var hoy=contexto.hoy();
        if(desde==null||hasta==null||desde.getYear()<1||desde.isAfter(hasta)||hasta.isAfter(hoy))throw new ReglaNegocioException("Selecciona un período completo, ordenado y no futuro para el reporte.");
        var e=encargos.consultar(id,a);var scope=alcances.de(a);var padres=repo.oportunidades(a.idOrganizacion(),id,scope.rolesAgente(),a.esBroker());
        var ids=new LinkedHashSet<Long>();var recursos=new ArrayList<Fuente>();recursos.add(new Fuente("ENCARGO",id,e.version()));
        for(var p:padres){var actual=acceso.contextoParaNegociacion(p.id(),a);if(actual.idEncargo()!=id||actual.version()!=p.version())throw conflicto();ids.add(p.id());recursos.add(new Fuente("OPORTUNIDAD",p.id(),p.version()));}
        var actividad=repo.actividad(a.idOrganizacion(),ids);
        var comunicaciones=seleccionar(interacciones.indicadores(a),id,ids,corte);
        var usadas=new ArrayList<ReferenciaVersion>();var resultados=new TreeMap<String,Integer>();int cantidad=0,sinFecha=0,consultas=0;
        for(var m:comunicaciones){
            if(m.fechaHecho()==null){sinFecha++;usadas.add(new ReferenciaVersion(m.id(),m.version()));continue;}
            if(!dentro(m.fechaHecho(),desde,hasta))continue;
            cantidad++;usadas.add(new ReferenciaVersion(m.id(),m.version()));resultados.merge(m.resultado()==null?"Resultado no precisado":m.resultado().rotulo(),1,Integer::sum);
            if(m.atencionConsultas()!=null&&m.atencionConsultas().consultaRecibida()&&dentro(MomentosDeComunicacion.local(m.atencionConsultas().momentoConsulta()),desde,hasta))consultas++;
        }
        int visitas=0,visitasSinFecha=0,ofertas=0,contraofertas=0;var proximas=new ArrayList<String>();
        for(var h:actividad){
            if("VISITA".equals(h.tipo())&&h.registrada()!=null&&h.registrada().isAfter(corte))throw conflicto();
            if(h.registrada()==null||h.registrada().isAfter(corte))continue;
            if("VISITA".equals(h.tipo())){
                if(Visita.REALIZADA.equals(h.estado())){if(h.fecha()==null)visitasSinFecha++;else if(dentro(h.fecha(),desde,hasta))visitas++;}
                if(Set.of(Visita.PROGRAMADA,Visita.REPROGRAMADA).contains(h.estado())&&h.fechaProgramada()!=null&&!h.fechaProgramada().isBefore(hoy))
                    proximas.add("Visita programada: "+h.fechaProgramada()+(h.horaProgramada()==null?"":" "+h.horaProgramada()));
            }else if(dentro(h.fecha(),desde,hasta)){if(h.ofertaAnterior()==null)ofertas++;else contraofertas++;}
            recursos.add(new Fuente(h.tipo(),h.id(),h.version()));
        }
        var hechos=new Hechos(cantidad,sinFecha,consultas,visitas,visitasSinFecha,ofertas,contraofertas,resultados,
            "Actividad registrada y autorizada en BROX. No describe todo el mercado ni convierte ausencia de registro en ausencia de interés. Las fechas desconocidas no se asignan al período.");
        contexto.exigirOperador(a);
        if(!new HashSet<>(scope.rolesAgente()).equals(new HashSet<>(alcances.de(a).rolesAgente()))||e.version()!=encargos.consultar(id,a).version()
            ||!padres.equals(repo.oportunidades(a.idOrganizacion(),id,scope.rolesAgente(),a.esBroker()))||!actividad.equals(repo.actividad(a.idOrganizacion(),ids))
            ||!comunicaciones.equals(seleccionar(interacciones.indicadores(a),id,ids,corte)))throw conflicto();
        for(var p:padres)acceso.contextoParaNegociacion(p.id(),a);
        interacciones.exigirVersiones(usadas,a,FinalidadLectura.REPORTE_PROPIETARIO);
        return new Lectura(new VistaPrevia(id,e.codigo(),e.operacion(),e.estado(),e.version(),desde,hasta,corte,hechos,proximas),usadas,recursos);
    }
    private static List<MedicionInteraccion> seleccionar(LecturaIndicadores lectura,long encargo,Set<Long> oportunidades,Instant corte){
        var salida=new ArrayList<MedicionInteraccion>();
        for(var m:lectura.registros()){
            boolean vinculo=m.vinculos().stream().anyMatch(v->"ENCARGO".equals(v.tipo())&&v.referencia()==encargo||"OPORTUNIDAD".equals(v.tipo())&&oportunidades.contains(v.referencia()));
            if(!vinculo)continue;
            if(m.registradoEn()!=null&&m.registradoEn().isAfter(corte)){if(m.version()>0)throw conflicto();continue;}
            if(m.registradoEn()!=null)salida.add(m);
        }
        return List.copyOf(salida);
    }
    private static boolean dentro(LocalDate f,LocalDate desde,LocalDate hasta){return f!=null&&!f.isBefore(desde)&&!f.isAfter(hasta);}
    private static ConflictoException conflicto(){return new ConflictoException("Cambiaron datos o permisos durante la preparación del reporte; vuelve a consultar.");}
}
