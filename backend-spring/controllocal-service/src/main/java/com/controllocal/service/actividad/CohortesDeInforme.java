package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.Trayectoria;
import com.controllocal.service.actividad.IndicadoresIndividuales.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/** Directriz5.3/CAT-E31: composición descriptiva de hitos, no otro indicador de cierre ni pronóstico. */
public final class CohortesDeInforme {
    private CohortesDeInforme() { }
    public record Caso(long id,long version,Long responsableHistorico,LocalDate apertura,LocalDate primeraVisita,
                       LocalDate primeraOferta,LocalDate cierreContractual,LocalDate noContinuidad,
                       String situacionActual,String motivoRegistrado) { }
    public record Antiguedad(long oportunidad,long version,long dias) { }
    public record Duracion(String inicio,String fin,String unidad,int terminadas,BigDecimal mediana,
                           int sinFechasSuficientes,int fechasInconsistentes,List<Antiguedad> pendientes) {
        public Duracion {pendientes=List.copyOf(pendientes);}
    }
    public record Cohorte(String operacion,LocalDate desde,LocalDate hasta,Instant corte,String entrada,String horizonte,
                          int casos,int sinResponsableHistorico,int sinFechaApertura,int conVisita,int conOferta,
                          int cierresContractuales,int sinContinuidad,int abiertas,int resueltasSinFechaOConfirmacion,List<Caso> fuentes,
                          List<Duracion> duraciones,Map<String,Integer> motivosNoContinuidad) {
        public Cohorte {fuentes=List.copyOf(fuentes);duraciones=List.copyOf(duraciones);motivosNoContinuidad=Map.copyOf(motivosNoContinuidad);}
    }
    public static List<Cohorte> componer(List<Trayectoria> entradas,List<Desenlace> desenlaces,Ventana v){
        var identidades=new HashSet<Long>();for(var t:entradas)if(!identidades.add(t.id()))throw new IllegalArgumentException("Una oportunidad no duplica la cohorte.");
        var porId=new HashMap<Long,Desenlace>();for(var d:desenlaces)if(porId.put(d.fuente().id(),d)!=null)throw new IllegalArgumentException("Una oportunidad tiene un solo desenlace vigente.");
        var salida=new ArrayList<Cohorte>();var hoy=v.corte().atZone(ZoneId.of("America/Lima")).toLocalDate();
        for(String operacion:List.of("VENTA","ALQUILER")){
            var filas=entradas.stream().filter(t->operacion.equals(com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(t.operacion()).name())).toList();
            var casos=new ArrayList<Caso>();int sinApertura=0;
            for(var t:filas){
                if(t.apertura()==null){sinApertura++;continue;}
                if(t.apertura().isBefore(v.desde())||t.apertura().isAfter(v.hasta())||t.apertura().isAfter(hoy))continue;
                var d=porId.get(t.id());LocalDate cierre=null,negativa=null;
                if(d!=null&&d.fechaResolucion()!=null&&!d.fechaResolucion().isAfter(hoy)){
                    if(d.favorableConfirmadoC5())cierre=d.fechaResolucion();
                    else if(d.resuelta()&&Set.of("N","X").contains(t.situacion()))negativa=d.fechaResolucion();
                }
                casos.add(new Caso(t.id(),t.version(),t.responsableApertura(),t.apertura(),t.primeraVisita(),t.primeraOferta(),cierre,negativa,t.situacion(),t.motivoRegistrado()));
            }
            var motivos=new TreeMap<String,Integer>();for(var c:casos)if(c.noContinuidad()!=null)motivos.merge(c.motivoRegistrado()==null||c.motivoRegistrado().isBlank()?"SIN_MOTIVO_REGISTRADO":c.motivoRegistrado(),1,Integer::sum);
            var duraciones=List.of(duracion("APERTURA","PRIMERA_VISITA",casos,Caso::apertura,Caso::primeraVisita,hoy),
                duracion("APERTURA","PRIMERA_OFERTA",casos,Caso::apertura,Caso::primeraOferta,hoy),
                duracion("PRIMERA_VISITA","PRIMERA_OFERTA",casos,Caso::primeraVisita,Caso::primeraOferta,hoy),
                duracion("PRIMERA_OFERTA","CIERRE_CONTRACTUAL_C5",casos,Caso::primeraOferta,Caso::cierreContractual,hoy),
                duracion("APERTURA","CIERRE_CONTRACTUAL_C5",casos,Caso::apertura,Caso::cierreContractual,hoy));
            salida.add(new Cohorte(operacion,v.desde(),v.hasta(),v.corte(),"OPORTUNIDADES_ABIERTAS_EN_PERIODO","HASTA_CORTE_NO_PROBABILIDAD",casos.size(),
                (int)casos.stream().filter(c->c.responsableHistorico()==null).count(),sinApertura,(int)casos.stream().filter(c->c.primeraVisita()!=null).count(),
                (int)casos.stream().filter(c->c.primeraOferta()!=null).count(),(int)casos.stream().filter(c->c.cierreContractual()!=null).count(),
                (int)casos.stream().filter(c->c.noContinuidad()!=null).count(),(int)casos.stream().filter(CohortesDeInforme::abierta).count(),
                (int)casos.stream().filter(c->!abierta(c)&&c.cierreContractual()==null&&c.noContinuidad()==null).count(),casos,duraciones,motivos));
        }
        return List.copyOf(salida);
    }
    private static boolean abierta(Caso c){return Set.of("A","S").contains(c.situacionActual());}
    private static Duracion duracion(String de,String a,List<Caso> casos,Function<Caso,LocalDate> inicio,Function<Caso,LocalDate> fin,LocalDate hoy){
        var dias=new ArrayList<Long>();var pendientes=new ArrayList<Antiguedad>();int insuficientes=0,inconsistentes=0;
        for(var c:casos){var x=inicio.apply(c);var y=fin.apply(c);
            if(y!=null){if(x==null){insuficientes++;continue;}if(y.isBefore(x)||y.isAfter(hoy)){inconsistentes++;continue;}dias.add(ChronoUnit.DAYS.between(x,y));}
            else if(abierta(c)){if(x==null)continue;if(x.isAfter(hoy)){inconsistentes++;continue;}pendientes.add(new Antiguedad(c.id(),c.version(),ChronoUnit.DAYS.between(x,hoy)));}
            else if("CIERRE_CONTRACTUAL_C5".equals(a)&&"F".equals(c.situacionActual()))insuficientes++;
        }
        dias.sort(Comparator.naturalOrder());BigDecimal mediana=null;int n=dias.size();
        if(n>0)mediana=n%2==1?BigDecimal.valueOf(dias.get(n/2)):BigDecimal.valueOf(dias.get(n/2-1)).add(BigDecimal.valueOf(dias.get(n/2))).divide(BigDecimal.valueOf(2),1,RoundingMode.UNNECESSARY);
        return new Duracion(de,a,"DIAS_CORRIDOS",n,mediana,insuficientes,inconsistentes,pendientes);
    }
}
