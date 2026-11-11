package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.controllocal.service.soporte.IndicadorMetaComercial.*;

/** Proyección de hechos20.10; reutiliza20.7/primera habilitación/C4/C5, sin otro motor de eventos. */
@Component
public class HechosDeMetas {
    public record Hecho(IndicadorMetaComercial indicador,String operacion,String tipo,long id,long version,LocalDate fecha,Long responsable) { }
    public record Faltante(IndicadorMetaComercial indicador,String operacion,String causa,Long responsable) { }
    public record Lectura(List<Hecho> hechos,List<Faltante> faltantes) {public Lectura{hechos=List.copyOf(hechos);faltantes=List.copyOf(faltantes);}}
    private final InteraccionService interacciones;private final ActividadInformeC7Repository fuentes;
    private final DesenlacesDeOportunidad desenlaces;private final ContextosDeActividad contexto;private final Alcances alcances;
    private final AccesoEncargo encargos;private final AccesoOportunidad oportunidades;
    public HechosDeMetas(InteraccionService i,ActividadInformeC7Repository f,DesenlacesDeOportunidad d,ContextosDeActividad c,Alcances a,AccesoEncargo en,AccesoOportunidad op){interacciones=i;fuentes=f;desenlaces=d;contexto=c;alcances=a;encargos=en;oportunidades=op;}
    @Transactional(readOnly=true)
    public Lectura leer(LocalDate desde,LocalDate hasta,Instant corte,Actor a){
        contexto.exigirOperador(a);var scope=alcances.de(a);var roles=scope.rolesAgente();var hoy=corte.atZone(ZoneId.of("America/Lima")).toLocalDate();
        var actividad=actuales(interacciones.indicadores(a),corte);var ef=ContactoEfectivoProspeccion.efectivos(actividad,desde,hasta,corte);
        var hs=fuentes.habilitaciones(a.idOrganizacion(),roles,corte);var os=fuentes.trayectorias(a.idOrganizacion(),roles,a.esBroker(),corte);var ds=desenlaces.leer(a,corte);
        var hechos=new ArrayList<Hecho>();var pendientes=new ArrayList<Faltante>();
        for(var h:actividad){
            if(h.registradoEn()==null&&h.contactoProspeccion()!=null){pendientes.add(new Faltante(PROSPECCION_EFECTIVA_SALIENTE,"GENERAL","REGISTRO_SIN_FECHA_ACREDITADA",h.idAgente()));continue;}
            var c=h.contactoProspeccion();if(c==null){if(h.vinculos().stream().anyMatch(v->"PROSPECCION".equals(v.tipo()))&&(h.fechaHecho()==null||dentro(h.fechaHecho(),desde,hasta,hoy)))pendientes.add(new Faltante(PROSPECCION_EFECTIVA_SALIENTE,"GENERAL","FINALIDAD_O_RESULTADO_NO_ACREDITADOS",h.idAgente()));continue;}
            if("POR_PRECISAR".equals(c.estado())&&(h.fechaHecho()==null||dentro(h.fechaHecho(),desde,hasta,hoy)))pendientes.add(new Faltante(PROSPECCION_EFECTIVA_SALIENTE,"GENERAL","CONTACTO_NO_EVALUABLE",h.idAgente()));
            if(ef.contains(h.id()))hechos.add(new Hecho(PROSPECCION_EFECTIVA_SALIENTE,"GENERAL","INTERACCION",h.id(),h.version(),h.fechaHecho(),h.idAgente()));
        }
        for(var h:hs){encargos.exigirLectura(h.id(),a);String op=OperacionInmobiliaria.desde(h.operacion()).name();
            if(h.fecha()==null||h.registrada()==null){pendientes.add(new Faltante(ENCARGO_PRIMERA_HABILITACION,op,"PRIMERA_HABILITACION_SIN_EVIDENCIA",null));continue;}
            if(dentro(h.fecha(),desde,hasta,hoy)){if(h.responsableHistorico()==null)pendientes.add(new Faltante(ENCARGO_PRIMERA_HABILITACION,op,"RESPONSABLE_HISTORICO_DESCONOCIDO",null));else hechos.add(new Hecho(ENCARGO_PRIMERA_HABILITACION,op,"ENCARGO",h.id(),h.version(),h.fecha(),h.responsableHistorico()));}
        }
        for(var o:os){var permiso=oportunidades.contextoParaNegociacion(o.id(),a);if(permiso.version()!=o.version())throw new ConflictoException("Cambió una fuente de apertura.");String op=OperacionInmobiliaria.desde(o.operacion()).name();
            if(o.apertura()==null)pendientes.add(new Faltante(OPORTUNIDAD_APERTURA,op,"FECHA_DE_APERTURA_DESCONOCIDA",o.responsableApertura()));
            else if(dentro(o.apertura(),desde,hasta,hoy)){if(o.responsableApertura()==null)pendientes.add(new Faltante(OPORTUNIDAD_APERTURA,op,"RESPONSABLE_HISTORICO_DESCONOCIDO",null));else hechos.add(new Hecho(OPORTUNIDAD_APERTURA,op,"OPORTUNIDAD",o.id(),o.version(),o.apertura(),o.responsableApertura()));}
        }
        for(var d:ds)if(d.favorableConfirmadoC5()){
            if(d.fechaResolucion()==null||d.fuente().responsableHistorico()==null)pendientes.add(new Faltante(CIERRE_CONTRACTUAL_FAVORABLE,d.operacion(),"CIERRE_CON_FECHA_O_ATRIBUCION_INSUFICIENTE",d.fuente().responsableHistorico()));
            else if(dentro(d.fechaResolucion(),desde,hasta,hoy))hechos.add(new Hecho(CIERRE_CONTRACTUAL_FAVORABLE,d.operacion(),"OPORTUNIDAD",d.fuente().id(),d.fuente().version(),d.fechaResolucion(),d.fuente().responsableHistorico()));
        }
        contexto.exigirOperador(a);if(!new HashSet<>(roles).equals(new HashSet<>(alcances.de(a).rolesAgente()))||!hs.equals(fuentes.habilitaciones(a.idOrganizacion(),roles,corte))||!os.equals(fuentes.trayectorias(a.idOrganizacion(),roles,a.esBroker(),corte))||!ds.equals(desenlaces.leer(a,corte))||!actividad.equals(actuales(interacciones.indicadores(a),corte)))throw new ConflictoException("Cambiaron las fuentes o el ámbito durante el conteo de metas.");
        interacciones.exigirVersiones(actividad.stream().map(h->new InteraccionService.ReferenciaVersion(h.id(),h.version())).toList(),a,InteraccionService.FinalidadLectura.INDICADORES);
        var claves=new HashSet<String>();for(var h:hechos)if(!claves.add(h.indicador()+"|"+h.tipo()+"|"+h.id()))throw new IllegalStateException("Un mismo hecho no cuenta dos veces para la categoría.");
        return new Lectura(hechos,pendientes);
    }
    private static boolean dentro(LocalDate d,LocalDate desde,LocalDate hasta,LocalDate hoy){return d!=null&&!d.isBefore(desde)&&!d.isAfter(hasta)&&!d.isAfter(hoy);}
    private static List<InteraccionService.MedicionInteraccion> actuales(InteraccionService.LecturaIndicadores l,Instant corte){
        var m=new LinkedHashMap<Long,InteraccionService.MedicionInteraccion>();for(var h:l.registros()){
            if(h.registradoEn()!=null&&h.registradoEn().isAfter(corte))throw new ConflictoException("Una comunicación cambió después del corte; no se convierte su ausencia histórica en cero.");
            var p=m.get(h.id());if(p==null||p.version()<h.version())m.put(h.id(),h);
        }return List.copyOf(m.values());
    }
}
