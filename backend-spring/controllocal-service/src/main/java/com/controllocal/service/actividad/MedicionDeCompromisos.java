package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.CompromisoSeguimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;

/** Proyección de hechos R27, sin contenido privado ni otro cálculo/cadencia. */
@Component
public class MedicionDeCompromisos {
    public record Lectura(Tasa medicion,boolean verificable) { }
    private final CompromisoSeguimientoRepository repo;
    private final CompromisosDeSeguimiento compromisos;
    private final ContextosDeActividad contexto;
    private final Alcances alcances;
    public MedicionDeCompromisos(CompromisoSeguimientoRepository r,CompromisosDeSeguimiento c,ContextosDeActividad x,Alcances a){repo=r;compromisos=c;contexto=x;alcances=a;}
    public Lectura leer(Actor a,Ventana v,LecturaIndicadores actividad){
        return leer(a,v,actividad,null);
    }
    public Lectura leer(Actor a,Ventana v,LecturaIndicadores actividad,Long responsable){
        contexto.exigirOperador(a);var scope=alcances.de(a);
        if(responsable!=null&&!scope.rolesAgente().contains(responsable))throw new AccesoNoAutorizadoException();
        var seleccion=responsable!=null?List.of(responsable):a.esAgente()?List.of(a.idRolOperativo()):null;
        var filas=repo.alCorte(a.idOrganizacion(),scope.rolesAgente(),seleccion,v.corte());
        var anteriores=responsable==null?repo.anterioresSinTipo(a.idOrganizacion(),scope.rolesAgente(),v.corte()):List.<CompromisoSeguimientoRepository.AnteriorSinTipo>of();
        var comunicaciones=new HashMap<Long,MedicionInteraccion>();
        for(var i:actividad.registros())if(i.registradoEn()!=null&&!i.registradoEn().isAfter(v.corte())){
            var prev=comunicaciones.get(i.id());if(prev==null||prev.version()<i.version())comunicaciones.put(i.id(),i);
        }
        var salida=new ArrayList<Compromiso>();boolean verificable=true;
        for(var f:anteriores){
            if(f.plazo().isBefore(v.desde())||f.plazo().isAfter(v.hasta()))continue;
            try{compromisos.exigirAnterior(f,a);}catch(AccesoNoAutorizadoException|NoEncontradoException denegada){verificable=false;continue;}
            salida.add(new Compromiso(new Fuente("R27_SIN_TIPO",f.seguimiento(),f.version(),f.planificada(),null),"R27:"+f.seguimiento(),null,f.plazo(),null,List.of(),List.of(),null,null));
        }
        for(var f:filas){
            CompromisosDeSeguimiento.Contenido c;
            try{c=compromisos.contenidoAutorizado(f,a);}catch(AccesoNoAutorizadoException|NoEncontradoException denegada){verificable=false;continue;}
            var acts=new ArrayList<Actuacion>();boolean sinEvidencia=false;
            for(var link:c.actuaciones()){
                if(link.vinculadaEn().isAfter(v.corte()))continue;
                var i=comunicaciones.get(link.interaccion().id());
                if(i==null){verificable=false;continue;}
                boolean misma=i.version()==link.interaccion().version();
                if(!misma){sinEvidencia=true;continue;} // Una rectificación no hereda la acreditación del contenido anterior.
                boolean vinculada=i.vinculos().stream().anyMatch(x->"PROSPECCION".equals(x.tipo())&&Objects.equals(x.referencia(),f.prospeccion()));
                var fuente=new Fuente("INTERACCION",i.id(),i.version(),i.registradoEn(),i.idAgente());
                boolean real=i.eventos().stream().anyMatch(e->e.evento()==CatalogoInteracciones.EventoComunicacion.LLAMADA_SALIENTE&&i.resultado()!=CatalogoInteracciones.ResultadoInteraccion.COMUNICACION_FALLIDA
                        ||e.evento()==CatalogoInteracciones.EventoComunicacion.MENSAJE_ENVIADO||e.evento()==CatalogoInteracciones.EventoComunicacion.REUNION_REALIZADA);
                acts.add(new Actuacion(fuente,i.tipo(),i.fechaHecho(),f.referencia().toString(),real,vinculada&&misma,null));
            }
            var fuente=new Fuente("COMPROMISO_R27",f.id(),f.version(),f.actualizada(),f.responsableHistorico());
            salida.add(new Compromiso(fuente,f.referencia().toString(),c.origen(),c.plazo(),sinEvidencia?null:c.actividad(),c.cambios(),acts,c.resolucion(),c.evidenciaResolucion(),null,c.resueltaEn()));
        }
        contexto.exigirOperador(a);var permitidos=alcances.de(a).rolesAgente();
        if(!new HashSet<>(permitidos).equals(new HashSet<>(scope.rolesAgente())))throw new ConflictoException("Cambió la supervisión del compromiso.");
        var finales=repo.alCorte(a.idOrganizacion(),permitidos,seleccion,v.corte());
        var anterioresFinales=responsable==null?repo.anterioresSinTipo(a.idOrganizacion(),permitidos,v.corte()):List.<CompromisoSeguimientoRepository.AnteriorSinTipo>of();
        if(!filas.equals(finales)||!anteriores.equals(anterioresFinales))throw new ConflictoException("Cambiaron compromisos o permisos durante la medición.");
        for(var f:filas)try{compromisos.exigirOriginal(f,a);}catch(AccesoNoAutorizadoException|NoEncontradoException denegada){verificable=false;}
        for(var f:anteriores)try{compromisos.exigirAnterior(f,a);}catch(AccesoNoAutorizadoException|NoEncontradoException denegada){verificable=false;}
        return new Lectura(IndicadoresIndividuales.seguimiento(salida,v),verificable);
    }
}
