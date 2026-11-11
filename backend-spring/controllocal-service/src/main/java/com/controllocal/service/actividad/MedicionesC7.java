package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.LecturaIndicadores;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;

/** Composición única de las cuatro definiciones aprobadas; no contiene fórmulas alternativas. */
@Component
public class MedicionesC7 {
    public record Lectura(Ventana periodo,String ambito,IndicadorService.ResultadoSeguimiento seguimiento,
                          IndicadorService.ResultadoConversion conversion,Map<String,Tasa> cierre,
                          IndicadorService.ResultadoPrimeraRespuesta primeraRespuesta) { }
    private final InteraccionService interacciones;
    private final DesenlacesDeOportunidad desenlaces;
    private final ConversionDeProspecciones conversiones;
    private final MedicionDeCompromisos compromisos;
    private final ContextosDeActividad contexto;
    private final Alcances alcances;
    public MedicionesC7(InteraccionService i,DesenlacesDeOportunidad d,ConversionDeProspecciones c,MedicionDeCompromisos s,ContextosDeActividad x,Alcances a){interacciones=i;desenlaces=d;conversiones=c;compromisos=s;contexto=x;alcances=a;}
    public Lectura medir(Ventana v,Actor a){contexto.exigirOperador(a);return medir(v,a,interacciones.indicadores(a));}
    public Lectura medir(Ventana v,Actor a,LecturaIndicadores actividad){
        return medir(v,a,actividad,null);
    }
    public Lectura porResponsable(Ventana v,Actor a,long responsable){return medir(v,a,interacciones.indicadores(a),responsable);}
    public Lectura porResponsable(Ventana v,Actor a,LecturaIndicadores actividad,long responsable){return medir(v,a,actividad,responsable);}
    private Lectura medir(Ventana v,Actor a,LecturaIndicadores actividad,Long responsable){
        contexto.exigirOperador(a);var scope=alcances.de(a);
        if(responsable!=null&&!scope.rolesAgente().contains(responsable))throw new AccesoNoAutorizadoException();
        if(responsable!=null)actividad=new LecturaIndicadores(actividad.registros().stream().filter(i->i.idAgente()==responsable).toList(),actividad.consultasConFuentesNoVerificables());
        var seguimiento=responsable==null?compromisos.leer(a,v,actividad):compromisos.leer(a,v,actividad,responsable);
        var rs=new IndicadorService.ResultadoSeguimiento(seguimiento.verificable()?seguimiento.medicion().disponibilidad():"NO_EVALUABLE",
                seguimiento.verificable()?null:"Permiso insuficiente para verificar las fuentes del compromiso.",seguimiento.verificable()?seguimiento.medicion():null);
        var conversion=conversiones.leer(a,v);IndicadorService.ResultadoConversion rc;
        if(!conversion.verificable())rc=new IndicadorService.ResultadoConversion("NO_EVALUABLE","Permiso insuficiente para verificar los Encargos vinculados.",null);
        else{var datos=conversion.hechos().stream().filter(p->incluye(p.fuente().responsableHistorico(),responsable,a)).toList();var m=IndicadoresIndividuales.conversion(datos,v);rc=new IndicadorService.ResultadoConversion(m.tasa().disponibilidad(),null,m);}
        var hechos=desenlaces.leer(a,v.corte()).stream().filter(d->incluye(d.fuente().responsableHistorico(),responsable,a)).toList();
        IndicadorService.ResultadoPrimeraRespuesta rp;
        if(!AtencionDeConsultas.verificable(actividad,v))rp=new IndicadorService.ResultadoPrimeraRespuesta("NO_EVALUABLE","Permiso insuficiente para verificar las fuentes de consultas.",null);
        else{var m=AtencionDeConsultas.medir(actividad.registros(),v);rp=new IndicadorService.ResultadoPrimeraRespuesta(m.disponibilidad(),null,m);}
        contexto.exigirOperador(a);var finalScope=alcances.de(a);
        if(scope.idOrganizacion()!=finalScope.idOrganizacion()||!new HashSet<>(scope.rolesAgente()).equals(new HashSet<>(finalScope.rolesAgente())))throw new ConflictoException("Cambió el ámbito durante la medición.");
        return new Lectura(v,a.esBroker()?"EQUIPO":"PROPIO",rs,rc,IndicadoresIndividuales.cierres(hechos,v),rp);
    }
    private static boolean incluye(Long historico,Long seleccionado,Actor actor){return seleccionado!=null?Objects.equals(historico,seleccionado):actor.esBroker()||historico==null||historico==actor.idRolOperativo();}
}
