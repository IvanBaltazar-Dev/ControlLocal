package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.IndicadorService.ContactoProspeccion;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.Normalizado;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.math.*;
import java.time.*;
import java.util.*;

/** Catálogo20.7: función declarada para una gestión; jamás acredita titularidad o poder. */
public final class ContactoEfectivoProspeccion {
    public static final String VERSION="CONTACTO-PROSPECCION-20261109-1";
    private ContactoEfectivoProspeccion() { }
    public static boolean comercial(DatosInteraccion d) {
        return d.gestionContacto()!=null&&d.gestionContacto().finalidades().contains(FinalidadContacto.PROSPECCION_COMERCIAL);
    }
    public static boolean gestor(DatosInteraccion d) {
        return d.gestionContacto()!=null&&d.gestionContacto().funcionesInterlocutor().contains(FuncionContacto.GESTION_COMERCIAL);
    }
    public static void validar(DatosInteraccion d) {
        var g=d.gestionContacto();if(g==null)return;
        if(!g.funcionesInterlocutor().isEmpty()&&(d.interlocutor()==null||vacio(g.fuenteFunciones())&&vacio(d.fuente())))
            throw new ReglaNegocioException("Conserva el interlocutor real y la fuente de su función declarada.");
        if(g.continuarConInterlocutor()&&(!comercial(d)||!gestor(d)||!respuesta(d)))
            throw new ReglaNegocioException("Continuar con el interlocutor requiere su función comercial declarada y una respuesta real sobre la gestión.");
        if(g.respondeAIntento()!=null&&(g.respondeAIntento().id()<=0||g.respondeAIntento().version()<0||saliente(d)||!comercial(d)||!recibida(d)))
            throw new ReglaNegocioException("Identifica la versión del intento al que responde esta comunicación entrante real.");
    }
    public static boolean mismoInterlocutor(Normalizado n) {
        var d=n.datos();var a=d.destinatario();var b=d.interlocutor();if(a==null||b==null)return false;
        var x=n.autorizacion().contactos().get("destinatario");var y=n.autorizacion().contactos().get("interlocutor");
        if(x!=null&&y!=null&&x.persona()!=null&&y.persona()!=null)return x.persona().equals(y.persona());
        return !vacio(a.referenciaDeclarada())&&a.referenciaDeclarada().equals(b.referenciaDeclarada());
    }
    public static DatoContactoProspeccion calificar(Normalizado n) {
        var d=n.datos();var g=d.gestionContacto();boolean prospecto=lista(d.vinculos()).stream().anyMatch(v->v.tipo()==TipoContexto.PROSPECCION);
        // La finalidad no exige crear una entidad Prospección ficticia.
        if(g==null||g.finalidades().isEmpty())return dato(prospecto?"POR_PRECISAR":"FUERA_DE_AMBITO","FINALIDAD_NO_DECLARADA",false,null);
        if(!comercial(d))return dato("FUERA_DE_AMBITO","FINALIDAD_NO_COMERCIAL",false,null);
        boolean salida=saliente(d);var ref=g.respondeAIntento();
        if(!salida&&ref==null)return dato("FUERA_DE_AMBITO","SIN_INTENTO_SALIENTE",false,null);
        if(d.resultado()==null||MomentosDeComunicacion.efectiva(d)==null)return dato("POR_PRECISAR","RESULTADO_O_FECHA_DESCONOCIDOS",salida,ref);
        if(d.resultado()==ResultadoInteraccion.SIN_RESPUESTA||d.resultado()==ResultadoInteraccion.COMUNICACION_FALLIDA)
            return dato("NO_EFECTIVO","SIN_RESPUESTA_COMERCIAL",salida,ref);
        if(d.resultado()==ResultadoInteraccion.SIN_INTERCAMBIO&&ref==null)return dato("NO_EFECTIVO","SIN_RECIPROCIDAD",salida,null);
        if(d.interlocutor()==null)return dato("POR_PRECISAR","INTERLOCUTOR_DESCONOCIDO",salida,ref);
        if(g.funcionesInterlocutor().contains(FuncionContacto.POR_PRECISAR)&&!gestor(d))return dato("POR_PRECISAR","FUNCION_DESCONOCIDA",salida,ref);
        boolean funcionConocida=!g.funcionesInterlocutor().isEmpty()&&!g.funcionesInterlocutor().contains(FuncionContacto.POR_PRECISAR);
        boolean titular=mismoInterlocutor(n)&&d.destinatario()!=null&&Set.of("PROPIETARIO","REPRESENTANTE","GESTOR_COMERCIAL").contains(codigo(d.destinatario().tipoDeclarado()));
        if(gestor(d)||!funcionConocida&&titular)return dato("EFECTIVO","RESPUESTA_COMERCIAL",salida,ref);
        return dato(funcionConocida?"NO_EFECTIVO":"POR_PRECISAR",funcionConocida?"SOLO_COORDINACION_O_INFORMACION":"FUNCION_DESCONOCIDA",salida,ref);
    }
    /** Sólo recibe proyecciones minimizadas previamente autorizadas; una respuesta no añade otro intento. */
    public static ContactoProspeccion medir(List<MedicionInteraccion> originales,LocalDate desde,LocalDate hasta,Instant corte) {
        return evaluar(originales,desde,hasta,corte).medicion();
    }
    /** Misma evaluación20.7; permite atribuir cada intento único sin perder respuestas de otro interlocutor/agente. */
    public static Set<Long> efectivos(List<MedicionInteraccion> originales,LocalDate desde,LocalDate hasta,Instant corte){
        return evaluar(originales,desde,hasta,corte).efectivos();
    }
    private record Evaluacion(ContactoProspeccion medicion,Set<Long> efectivos) { }
    private static Evaluacion evaluar(List<MedicionInteraccion> originales,LocalDate desde,LocalDate hasta,Instant corte) {
        var hechos=new LinkedHashMap<Long,MedicionInteraccion>();
        for(var h:originales)if(h.registradoEn()!=null&&!h.registradoEn().isAfter(corte)){
            var previo=hechos.putIfAbsent(h.id(),h);if(previo!=null&&!previo.equals(h))throw new IllegalStateException("Una comunicación tiene versiones contradictorias en el mismo corte.");
        }
        var respondidos=new HashSet<Long>();
        for(var h:hechos.values()){
            var c=h.contactoProspeccion();if(c==null||!"EFECTIVO".equals(c.estado())||c.respondeAIntento()==null||h.fechaHecho()==null||h.fechaHecho().isAfter(hasta))continue;
            var anterior=hechos.get(c.respondeAIntento().id());
            if(anterior!=null&&anterior.version()>=c.respondeAIntento().version()&&anterior.contactoProspeccion()!=null
                    &&c.respondeAIntento().version()>=(anterior.contactoProspeccion().versionHecho()==null?anterior.version():anterior.contactoProspeccion().versionHecho())&&anterior.contactoProspeccion().intentoSaliente()
                    &&anterior.fechaHecho()!=null&&!h.fechaHecho().isBefore(anterior.fechaHecho()))respondidos.add(anterior.id());
        }
        int evaluables=0,efectivos=0,pendientes=0,excluidos=0;var idsEfectivos=new HashSet<Long>();
        for(var h:hechos.values()){
            if(h.fechaHecho()==null){if(h.vinculos().stream().anyMatch(v->"PROSPECCION".equals(v.tipo())))pendientes++;continue;}
            if(h.fechaHecho().isBefore(desde)||h.fechaHecho().isAfter(hasta))continue;
            var c=h.contactoProspeccion();
            if(c==null){if(h.vinculos().stream().anyMatch(v->"PROSPECCION".equals(v.tipo())))pendientes++;continue;}
            if("POR_PRECISAR".equals(c.estado())){pendientes++;continue;}
            if(!c.intentoSaliente()){excluidos++;continue;}
            evaluables++;if("EFECTIVO".equals(c.estado())||respondidos.contains(h.id())){efectivos++;idsEfectivos.add(h.id());}
        }
        var porcentaje=evaluables==0?null:BigDecimal.valueOf(efectivos).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(evaluables),1,RoundingMode.HALF_UP);
        return new Evaluacion(new ContactoProspeccion(VERSION,desde,hasta,corte,efectivos,evaluables,pendientes,excluidos,porcentaje),Set.copyOf(idsEfectivos));
    }
    private static DatoContactoProspeccion dato(String estado,String causa,boolean intento,ReferenciaVersion ref){return new DatoContactoProspeccion(estado,causa,intento,ref);}
    private static boolean saliente(DatosInteraccion d){return lista(d.eventos()).stream().anyMatch(e->e.evento()==EventoComunicacion.LLAMADA_SALIENTE||e.evento()==EventoComunicacion.MENSAJE_ENVIADO);}
    private static boolean recibida(DatosInteraccion d){return lista(d.eventos()).stream().anyMatch(e->e.evento()==EventoComunicacion.LLAMADA_ENTRANTE||e.evento()==EventoComunicacion.MENSAJE_RECIBIDO);}
    private static boolean respuesta(DatosInteraccion d){return d.resultado()==ResultadoInteraccion.INTERCAMBIO_EFECTIVO||d.resultado()==ResultadoInteraccion.CONVERSACION_RECHAZADA||d.gestionContacto()!=null&&d.gestionContacto().respondeAIntento()!=null&&recibida(d);}
    private static boolean vacio(String s){return s==null||s.isBlank();}
    private static String codigo(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);}
    private static <T>List<T> lista(List<T> l){return l==null?List.of():l;}
}
