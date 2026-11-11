package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;

/** CAT-E29/20.8: declaraciones comunicativas y su proyección mínima, sin crear consultas persistidas aparte. */
public final class AtencionDeConsultas {
    private AtencionDeConsultas(){ }
    public static List<ReferenciaVersion> consultasReferidas(DatosInteraccion d){
        return d.atencionConsultas()==null?null:d.atencionConsultas().respuestas().stream().map(RespuestaComercial::consulta).filter(Objects::nonNull).distinct().toList();
    }
    public static Set<Long> antecedentes(Long anterior,List<ReferenciaVersion> consultas){
        var ids=new LinkedHashSet<Long>();if(anterior!=null)ids.add(anterior);if(consultas!=null)consultas.forEach(r->ids.add(r.id()));return Collections.unmodifiableSet(ids);
    }
    public static void validar(DatosInteraccion d){
        var a=d.atencionConsultas();if(a==null)return;
        if(a.recibida()==null&&a.respuestas().isEmpty())throw new ReglaNegocioException("Indica la consulta recibida o las respuestas realmente comunicadas.");
        if(a.recibida()!=null){
            var e=evento(d,a.recibida().evento());
            boolean llamada=e.evento()==EventoComunicacion.LLAMADA_ENTRANTE||e.evento()==EventoComunicacion.LLAMADA_SALIENTE;
            if(e.evento()!=EventoComunicacion.MENSAJE_RECIBIDO&&!(llamada&&d.resultado()==ResultadoInteraccion.INTERCAMBIO_EFECTIVO))
                throw new ReglaNegocioException("La consulta requiere mensaje recibido o conversación atendida; una llamada perdida no acredita su contenido.");
            validarMomento(e,a.recibida().momento());
        }
        var vistas=new HashSet<String>();
        for(var r:a.respuestas()){
            if(r==null||r.constancia()==null||r.constancia().isBlank())throw new ReglaNegocioException("Conserva qué respuesta comercial se comunicó.");
            if(r.consulta()==null&&a.recibida()==null)throw new ReglaNegocioException("Identifica la consulta a la que responde.");
            if(r.consulta()!=null&&(r.consulta().id()<=0||r.consulta().version()<0))throw new ReglaNegocioException("La consulta conserva su identidad y versión.");
            var e=evento(d,r.evento());
            boolean llamada=e.evento()==EventoComunicacion.LLAMADA_ENTRANTE||e.evento()==EventoComunicacion.LLAMADA_SALIENTE;
            if(e.evento()!=EventoComunicacion.MENSAJE_ENVIADO&&!(llamada&&d.resultado()==ResultadoInteraccion.INTERCAMBIO_EFECTIVO))
                throw new ReglaNegocioException("Responder exige envío real o llamada atendida; preparar, fallar o no contestar no responde la consulta.");
            if(!vistas.add(r.consulta()==null?"ESTA":Long.toString(r.consulta().id())))throw new ReglaNegocioException("La respuesta a esa consulta ya está declarada en esta comunicación.");
            validarMomento(e,r.momento());
            if(r.consulta()==null)exigirCronologia(momento(d,a.recibida().evento(),a.recibida().momento()),momento(d,r.evento(),r.momento()));
        }
    }
    public static DatoAtencionConsultas proyectar(DatosInteraccion d,long version){
        var a=d.atencionConsultas();if(a==null)return null;
        return new DatoAtencionConsultas(a.recibida()!=null,a.recibida()==null?null:momento(d,a.recibida().evento(),a.recibida().momento()),
                a.respuestas().stream().map(r->new DatoRespuestaConsulta(r.consulta(),momento(d,r.evento(),r.momento()))).toList(),version);
    }
    public static Instant instante(MomentoHecho m){
        return m==null||m.fecha()==null||m.hora()==null||m.offsetConocido()==null?null:OffsetDateTime.of(m.fecha(),m.hora(),m.offsetConocido()).toInstant();
    }
    public static IndicadoresIndividuales.PrimeraRespuesta medir(List<MedicionInteraccion> comunicaciones,IndicadoresIndividuales.Ventana ventana){
        var consultas=new LinkedHashMap<Long,MedicionInteraccion>();
        for(var m:comunicaciones)if(m.atencionConsultas()!=null&&m.atencionConsultas().consultaRecibida()){
            var anterior=consultas.putIfAbsent(m.id(),m);
            if(anterior!=null&&!anterior.equals(m))throw new IllegalStateException("Una comunicación tiene dos proyecciones incompatibles.");
        }
        var respuestas=new HashMap<Long,List<IndicadoresIndividuales.Respuesta>>();
        for(var m:comunicaciones){
            var a=m.atencionConsultas();if(a==null)continue;
            for(var r:a.respuestas()){
                long id=r.consulta()==null?m.id():r.consulta().id();var q=consultas.get(id);if(q==null)continue;
                boolean vigente=r.consulta()==null||r.consulta().version()>=q.atencionConsultas().versionHecho()&&r.consulta().version()<=q.version();
                respuestas.computeIfAbsent(id,k->new ArrayList<>()).add(new IndicadoresIndividuales.Respuesta(
                        fuente(m),instante(r.momento()),"INTERACCION:"+id,true,m.tipo()==TipoActividad.MENSAJERIA,m.tipo()==TipoActividad.LLAMADA,vigente));
            }
        }
        var hechos=new ArrayList<IndicadoresIndividuales.Consulta>();
        for(var m:consultas.values()){
            var momento=m.atencionConsultas().momentoConsulta();
            hechos.add(new IndicadoresIndividuales.Consulta(fuente(m),instante(momento),MomentosDeComunicacion.local(momento),respuestas.getOrDefault(m.id(),List.of())));
        }
        return IndicadoresIndividuales.primeraRespuesta(hechos,ventana);
    }
    public static boolean verificable(LecturaIndicadores lectura,IndicadoresIndividuales.Ventana ventana){
        for(var m:lectura.registros())if(lectura.consultasConFuentesNoVerificables().contains(m.id())){
            var a=m.atencionConsultas();if(a==null||!a.consultaRecibida())continue;
            var momento=a.momentoConsulta();var exacto=instante(momento);if(exacto!=null&&exacto.isAfter(ventana.corte()))continue;
            var dia=MomentosDeComunicacion.local(momento);if(dia==null||ventana.contiene(dia))return false;
        }
        return true;
    }
    private static IndicadoresIndividuales.Fuente fuente(MedicionInteraccion m){return new IndicadoresIndividuales.Fuente("INTERACCION",m.id(),m.version(),m.registradoEn(),m.idAgente());}
    public static void exigirCronologia(MomentoHecho consulta,MomentoHecho respuesta){
        var q=MomentosDeComunicacion.local(consulta);var r=MomentosDeComunicacion.local(respuesta);
        if(q!=null&&r!=null&&r.isBefore(q))throw new ReglaNegocioException("La respuesta no puede preceder a la consulta.");
        var qi=instante(consulta);var ri=instante(respuesta);
        if(qi!=null&&ri!=null&&ri.isBefore(qi))throw new ReglaNegocioException("La hora conocida de respuesta precede a la consulta.");
    }
    private static EventoDeclarado evento(DatosInteraccion d,String ref){
        if(ref==null||ref.isBlank())throw new ReglaNegocioException("Identifica el evento real de la consulta o respuesta.");
        var eventos=(d.eventos()==null?List.<EventoDeclarado>of():d.eventos()).stream().filter(e->ref.equals(e.referencia())).toList();
        if(eventos.size()!=1)throw new ReglaNegocioException("El evento debe identificar inequívocamente un hecho de esta comunicación.");
        return eventos.getFirst();
    }
    private static MomentoHecho momento(DatosInteraccion d,String referencia,MomentoHecho declarado){
        if(declarado!=null)return declarado;var e=evento(d,referencia);
        if(e.evento()==EventoComunicacion.MENSAJE_RECIBIDO||e.evento()==EventoComunicacion.MENSAJE_ENVIADO)return e.momento();
        // El comienzo de una llamada no inventa la hora en que se preguntó o respondió.
        return e.momento()==null?null:new MomentoHecho(MomentosDeComunicacion.local(e.momento()),null,null);
    }
    private static void validarMomento(EventoDeclarado e,MomentoHecho m){
        if(m==null)return;
        if(m.fecha()==null&&(m.hora()!=null||m.offsetConocido()!=null)||m.hora()==null&&m.offsetConocido()!=null)
            throw new ReglaNegocioException("La hora u offset declarados necesitan su fecha y hora correspondientes.");
        var fecha=MomentosDeComunicacion.local(e.momento());var declarada=MomentosDeComunicacion.local(m);
        if(fecha!=null&&declarada!=null&&!fecha.equals(declarada))throw new ReglaNegocioException("La consulta o respuesta pertenece al día del evento real declarado.");
        var ei=instante(e.momento());var mi=instante(m);
        if(ei!=null&&mi!=null){
            boolean mensaje=e.evento()==EventoComunicacion.MENSAJE_RECIBIDO||e.evento()==EventoComunicacion.MENSAJE_ENVIADO;
            if(mensaje&&!ei.equals(mi)||!mensaje&&mi.isBefore(ei))throw new ReglaNegocioException("El momento contradice al evento comunicado.");
        }
    }
}
