package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.actividad.ProgramasPersonales.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/** Las cuatro familias Pro consumen casos canónicos. Este ejecutor determinista no invoca proveedores. */
@Component
public class GeneradorProgramaPersonal {
    private final EncargoService encargos;
    private final ProspeccionService prospecciones;
    private final AvanceDeReporte reportes;
    private final InformesC7 informes;
    private final CompromisosDeSeguimiento compromisos;
    private final IndicadorService indicadores;
    private final InteraccionService interacciones;
    private final OportunidadService oportunidades;
    private final com.controllocal.persistence.repositorio.ActividadInformeC7Repository actividad;
    private final SeguimientoDeProspeccion seguimiento;
    private final com.controllocal.service.soporte.AccesoOportunidad accesoOportunidad;
    public GeneradorProgramaPersonal(EncargoService e,ProspeccionService p,AvanceDeReporte r,InformesC7 i,IndicadorService in,
            InteraccionService ac,OportunidadService op,com.controllocal.persistence.repositorio.ActividadInformeC7Repository ar,SeguimientoDeProspeccion se,
            com.controllocal.service.soporte.AccesoOportunidad ao,CompromisosDeSeguimiento cs){encargos=e;prospecciones=p;reportes=r;informes=i;indicadores=in;interacciones=ac;oportunidades=op;actividad=ar;seguimiento=se;accesoOportunidad=ao;compromisos=cs;}
    public void verificar(Configuracion q,Actor a){
        if(q.familia()==Familia.REPORTE_PROGRAMADO){
            var r=recurso(q,"ENCARGO");var e=encargos.consultar(r.id(),a);
            if(e.responsable()==null||e.responsable()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
            if(e.version()!=r.version())throw new ConflictoException("Cambió la versión del Encargo autorizado en el mandato.");
        }else if(q.familia()==Familia.RECORDATORIO_PROPUESTA){
            var r=recurso(q,"PROSPECCION");var p=prospecciones.obtener(r.id(),a);
            if(p.idAgente()==null||p.idAgente()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
            if(p.version()!=r.version())throw new ConflictoException("Cambió la versión de la prospección autorizada en el mandato.");
        }else if(q.recurso()!=null)throw regla("Esta familia usa el ámbito propio, sin un recurso adicional implícito.");
        if(q.familia()==Familia.RESUMEN_PROGRAMADO&&(q.informe()==null||q.informe().composicion()==null))throw regla("Selecciona la composición del resumen.");
        if(q.familia()==Familia.RESUMEN_PROGRAMADO){
            if(q.informe().desde()!=null||q.informe().hasta()!=null)throw regla("El período recurrente se declara mediante diasPeriodo; no se ignoran fechas adicionales.");
            informes.validarRecurrencia(q.informe(),q.diasPeriodo(),a);
        }
        if(q.familia()!=Familia.RESUMEN_PROGRAMADO&&q.informe()!=null)throw regla("La composición adicional corresponde al resumen programado.");
        if(q.familia()!=Familia.ALERTA_CONDICIONADA&&q.condicion()!=null)throw regla("La condición adicional corresponde a la familia de alertas.");
    }
    public Produccion producir(Configuracion q,Actor a,Instant corte){
        verificar(q,a);LocalDate hasta=corte.atZone(ZoneId.of("America/Lima")).toLocalDate(),desde=q.diasPeriodo()==null?null:hasta.minusDays(q.diasPeriodo()-1L);
        return switch(q.familia()){
            case REPORTE_PROGRAMADO -> {
                var lectura=reportes.preparar(q.recurso().id(),desde,hasta,a);
                var salida=lectura.avance();
                yield new Produccion(true,Map.of("familia",q.familia(),"finalidad",q.finalidad(),"desde",desde,"hasta",hasta,"recurso",q.recurso(),
                    "reportePreparado",salida,"comunicacion","NO_REALIZADA","alcanceDatos","Actividad registrada y autorizada en BROX; ausencia de registro no prueba ausencia de interés."),new PruebaEntrega(lectura.recursos().stream().map(f->new Recurso(f.tipo(),f.id(),f.version())).toList(),lectura.interacciones(),List.of()));
            }
            case RESUMEN_PROGRAMADO -> {
                var c=q.informe();var consulta=new InformesC7.Consulta(c.composicion(),desde,hasta,c.compararDesde(),c.compararHasta(),c.filtros(),c.operacionCohorte());
                var informe=informes.preparar(consulta,a);
                yield new Produccion(true,informe,new PruebaEntrega(informe.fuentes().stream().map(f->new Recurso(f.tipo(),f.id(),f.version())).toList(),informe.fuentesInteracciones(),referencias(informe.fuentesSeguimientos())));
            }
            case ALERTA_CONDICIONADA -> {
                var fuentes=actividad.fuentesPropias(a.idOrganizacion(),List.of(a.idRolOperativo()),false);
                var fuentesR27=seguimiento.enAlcance(a);
                var referencias=interacciones.indicadores(a).registros().stream().map(i->new InteraccionService.ReferenciaVersion(i.id(),i.version())).distinct().toList();
                var r=indicadores.resumen("1m",a);var condicion=q.condicion();
                int valor=switch(condicion.metrica()){
                    case RECONTACTOS_VENCIDOS -> r.operativo().recontactosVencidos();
                    case OPORTUNIDADES_ABIERTAS -> r.oportunidadesActivas();
                    case VISITAS_PENDIENTES -> r.operativo().visitasPendientes();
                    case SOLICITUDES_SIN_CIERRE -> r.operativo().solicitudesSinCierre();
                };
                boolean cumple=comparar(BigDecimal.valueOf(valor),condicion.comparador(),condicion.umbral());
                if(!fuentes.equals(actividad.fuentesPropias(a.idOrganizacion(),List.of(a.idRolOperativo()),false)))throw new ConflictoException("Cambió el ámbito de la alerta.");
                yield new Produccion(cumple,Map.of("familia",q.familia(),"finalidad",q.finalidad(),"corte",corte,"condicion",condicion,"valorObservado",valor,"cumple",cumple),new PruebaEntrega(fuentes.stream().map(f->new Recurso(f.tipo(),f.id(),f.version())).toList(),referencias,referencias(fuentesR27)));
            }
            case RECORDATORIO_PROPUESTA -> {
                var revisiones=prospecciones.seguimientos(q.recurso().id(),a);
                var salida=new LinkedHashMap<String,Object>();salida.put("familia",q.familia());salida.put("finalidad",q.finalidad());salida.put("recurso",q.recurso());salida.put("seguimientos",revisiones);
                salida.put("propuesta","Revisa el seguimiento autorizado y prepara el próximo contacto. El profesional realiza la actuación y registra su resultado real.");
                yield new Produccion(!revisiones.isEmpty(),salida,new PruebaEntrega(List.of(q.recurso()),List.of(),referencias(revisiones)));
            }
        };
    }
    /** Se comprueba la población original antes de hidratar el resultado privado conservado. */
    public void exigirEntrega(PruebaEntrega p,Actor a){
        if(p==null)throw new AccesoNoAutorizadoException();
        for(var r:p.recursos()){
            long v=switch(r.tipo()){
                case "ENCARGO"->encargos.consultar(r.id(),a).version();
                case "PROSPECCION"->prospecciones.obtener(r.id(),a).version();
                case "OPORTUNIDAD"->oportunidades.obtener(r.id(),a).version();
                case "COMPROMISO_R27"->{compromisos.exigirFuente(r.id(),r.version(),a);yield r.version();}
                case "VISITA","OFERTA"->{var f=actividad.fuenteActividad(a.idOrganizacion(),r.tipo(),r.id()).orElseThrow(()->new NoEncontradoException("Fuente de actividad"));accesoOportunidad.contextoParaNegociacion(f.oportunidad(),a);yield f.version();}
                default->throw new AccesoNoAutorizadoException();
            };
            if(r.version()==null||v!=r.version())throw new ConflictoException("La fuente del resultado requiere nueva revisión.");
        }
        interacciones.exigirVersiones(p.interacciones(),a,InteraccionService.FinalidadLectura.INDICADORES);
        for(var s:p.seguimientos()){prospecciones.obtener(s.idProspeccion(),a);seguimiento.exigirLecturaVersion(s.id(),s.version(),s.idProspeccion(),a);}
    }
    private static List<FuenteSeguimiento> referencias(List<ProspeccionService.RevisionSeguimiento> revisiones){return revisiones.stream().map(r->new FuenteSeguimiento(r.id(),r.version(),r.idProspeccion())).toList();}
    static boolean comparar(BigDecimal valor,Comparador c,BigDecimal umbral){int n=valor.compareTo(umbral);return switch(c){case MAYOR->n>0;case MAYOR_O_IGUAL->n>=0;case IGUAL->n==0;case MENOR_O_IGUAL->n<=0;case MENOR->n<0;};}
    private static Recurso recurso(Configuracion q,String tipo){var r=q.recurso();if(r==null||!tipo.equals(r.tipo())||r.id()<=0||r.version()==null||r.version()<0)throw regla("Identifica el recurso y su versión expresamente autorizada.");return r;}
    private static ReglaNegocioException regla(String s){return new ReglaNegocioException(s);}
}
