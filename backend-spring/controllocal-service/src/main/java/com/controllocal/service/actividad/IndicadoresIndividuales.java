package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.OrigenActuacion;
import com.controllocal.service.actividad.CatalogoInteracciones.TipoActividad;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** CAT-E29, catálogo20.8. Calcula hechos autorizados; nunca produce actuaciones ni estados. */
public final class IndicadoresIndividuales {
    public static final String VERSION="CAT-E29-20261109-1";
    private IndicadoresIndividuales() { }
    public record Ventana(LocalDate desde,LocalDate hasta,Instant corte) {
        public Ventana {
            Objects.requireNonNull(desde);Objects.requireNonNull(hasta);Objects.requireNonNull(corte);
            if(hasta.isBefore(desde))throw new IllegalArgumentException("Período invertido.");
        }
        boolean contiene(LocalDate fecha){return fecha!=null&&!fecha.isBefore(desde)&&!fecha.isAfter(hasta)&&!fecha.isAfter(corte.atZone(ZoneId.of("America/Lima")).toLocalDate());}
        boolean conocido(Instant registrado){return registrado!=null&&!registrado.isAfter(corte);}
        boolean exigible(LocalDate dia,Instant limiteExacto){
            // Una fecha sin hora no se convierte en un incumplimiento al comenzar ese día.
            return limiteExacto==null?dia.isBefore(corte.atZone(ZoneId.of("America/Lima")).toLocalDate()):!limiteExacto.isAfter(corte);
        }
    }
    /** La referencia identifica el hecho original; su versión permite rectificarlo sin duplicarlo. */
    public record Fuente(String tipo,long id,long version,Instant registrada,Long responsableHistorico) {
        public Fuente {
            if(tipo==null||tipo.isBlank()||id<=0||version<0)throw new IllegalArgumentException("Fuente versionada requerida.");
        }
        String clave(){return tipo+":"+id;}
    }
    public record Tasa(String version,Ventana periodo,int exitos,int evaluables,int pendientes,int resueltosAntesDeVencer,
                       int sinEvidencia,BigDecimal porcentaje,String disponibilidad) { }
    public record Actuacion(Fuente fuente,TipoActividad actividad,LocalDate fecha,String compromiso,
                           boolean hechoReal,boolean acreditaContenido,Instant momentoExacto) {
        public Actuacion(Fuente fuente,TipoActividad actividad,LocalDate fecha,String compromiso,boolean real,boolean contenido){this(fuente,actividad,fecha,compromiso,real,contenido,null);}
    }
    public record CambioPlazo(LocalDate anterior,LocalDate nueva,LocalDate fechaCambio,String motivo,
                              boolean nuevoAcuerdoAcreditado,Instant limiteAnteriorExacto,Instant cambiadoEn,Instant nuevoLimiteExacto,String evidenciaNuevoAcuerdo) {
        public CambioPlazo(LocalDate anterior,LocalDate nueva,LocalDate fechaCambio,String motivo,boolean acuerdo,Instant limiteAnterior,Instant cambiadoEn,Instant nuevoLimite){this(anterior,nueva,fechaCambio,motivo,acuerdo,limiteAnterior,cambiadoEn,nuevoLimite,null);}
        public CambioPlazo(LocalDate anterior,LocalDate nueva,LocalDate fechaCambio,String motivo,boolean acuerdo){this(anterior,nueva,fechaCambio,motivo,acuerdo,null,null,null);}
    }
    public record Compromiso(Fuente fuente,String identidad,OrigenActuacion origen,LocalDate plazo,
                              TipoActividad actividad,List<CambioPlazo> cambios,List<Actuacion> actuaciones,
                              LocalDate resolucionLegitima,String evidenciaResolucion,Instant limiteExacto,Instant resolucionExacta) {
        public Compromiso(Fuente fuente,String identidad,OrigenActuacion origen,LocalDate plazo,TipoActividad actividad,List<CambioPlazo> cambios,List<Actuacion> actuaciones,LocalDate resolucion,String evidencia){this(fuente,identidad,origen,plazo,actividad,cambios,actuaciones,resolucion,evidencia,null,null);}
        public Compromiso(Fuente fuente,String identidad,OrigenActuacion origen,LocalDate plazo,TipoActividad actividad,List<CambioPlazo> cambios,List<Actuacion> actuaciones,LocalDate resolucion,String evidencia,Instant limite){this(fuente,identidad,origen,plazo,actividad,cambios,actuaciones,resolucion,evidencia,limite,null);}
        public Compromiso {cambios=cambios==null?List.of():List.copyOf(cambios);actuaciones=actuaciones==null?List.of():List.copyOf(actuaciones);}
    }
    public record Prospeccion(Fuente fuente,LocalDate fechaResolucionDefinitiva,List<LocalDate> habilitaciones,
                               boolean preparacion,boolean terminoDefinitivo,boolean conversionPorPrecisar) {
        public Prospeccion(Fuente fuente,LocalDate resolucion,List<LocalDate> habilitaciones,boolean preparacion){this(fuente,resolucion,habilitaciones,preparacion,resolucion!=null,false);}
        public Prospeccion(Fuente fuente,LocalDate resolucion,List<LocalDate> habilitaciones,boolean preparacion,boolean termino){this(fuente,resolucion,habilitaciones,preparacion,termino,false);}
        public Prospeccion {habilitaciones=habilitaciones==null?List.of():List.copyOf(habilitaciones);}
    }
    public record Conversion(Tasa tasa,int enPreparacion,int otrasPendientes) { }
    public record Desenlace(Fuente fuente,String operacion,LocalDate fechaResolucion,boolean favorableConfirmadoC5,
                            boolean resuelta,Long revisionHito,Long historial) {
        public Desenlace(Fuente fuente,String operacion,LocalDate fecha,boolean favorable,boolean resuelta){this(fuente,operacion,fecha,favorable,resuelta,null,null);}
    }
    public record Respuesta(Fuente fuente,Instant fecha,String consulta,boolean comercial,boolean enviada,
                            boolean llamadaAtendida,boolean enlaceCausalVerificado) { }
    public record Consulta(Fuente fuente,Instant recibida,LocalDate diaRecibido,List<Respuesta> respuestas) {
        public Consulta {respuestas=respuestas==null?List.of():List.copyOf(respuestas);}
    }
    public record Espera(Fuente consulta,BigDecimal antiguedadHoras) { }
    public record PrimeraRespuesta(String version,Ventana periodo,int recibidas,int respondidas,int esperando,
                                    int sinFechasSuficientes,int sinPeriodoConocido,int sinResponsableHistorico,int porVerificarVinculo,int duracionesEvaluables,BigDecimal medianaHoras,
                                    List<Espera> antiguedades,String disponibilidad) {
        public PrimeraRespuesta {antiguedades=List.copyOf(antiguedades);}
    }
    public static Tasa seguimiento(Collection<Compromiso> hechos,Ventana v){
        int exitos=0,base=0,pendientes=0,resueltos=0,sinEvidencia=0;
        for(var c:ultimos(hechos,Compromiso::fuente,v)){
            if(c.origen()==OrigenActuacion.SUGERENCIA_SISTEMA)continue;
            if(c.origen()==null||c.plazo()==null||c.actividad()==null||c.identidad()==null||c.fuente().responsableHistorico()==null){sinEvidencia++;continue;}
            LocalDate plazo=c.plazo(),planVigente=plazo;Instant limiteExacto=c.limiteExacto();boolean vencido=false,porPrecisar=false;
            for(var cambio:c.cambios()){
                if(cambio.cambiadoEn()!=null&&cambio.cambiadoEn().isAfter(v.corte()))continue;
                if(cambio.fechaCambio()==null||cambio.fechaCambio().isAfter(v.corte().atZone(ZoneId.of("America/Lima")).toLocalDate()))continue;
                if(cambio.anterior()==null||cambio.nueva()==null||cambio.motivo()==null||cambio.motivo().isBlank()
                    ||!planVigente.equals(cambio.anterior())||cambio.nueva().isBefore(cambio.fechaCambio())){throw new IllegalArgumentException("Cambio de plazo sin antecedente, motivo o con nueva fecha retroactiva.");}
                if(c.origen()==OrigenActuacion.ACUERDO_EXTERNO&&!cambio.nuevoAcuerdoAcreditado())
                    throw new IllegalArgumentException("Cambiar un acuerdo exige el nuevo acuerdo.");
                // Un cambio tardío no modifica el plazo exigible ni borra su retraso.
                boolean antes=cambio.fechaCambio().isBefore(planVigente);
                if(cambio.fechaCambio().equals(planVigente)&&!vencido){
                    var limiteAnterior=limiteExacto==null?cambio.limiteAnteriorExacto():limiteExacto;
                    if(limiteAnterior==null)antes=true; // Día calendario inclusivo; no inventar una hora pactada.
                    else{
                        if(cambio.cambiadoEn()==null){porPrecisar=true;break;}
                        antes=cambio.cambiadoEn().isBefore(limiteAnterior);
                    }
                }
                if(!vencido&&antes){plazo=cambio.nueva();limiteExacto=cambio.nuevoLimiteExacto();}else vencido=true;
                planVigente=cambio.nueva();
            }
            if(porPrecisar){sinEvidencia++;continue;}
            if(c.resolucionLegitima()!=null
                &&!c.resolucionLegitima().isAfter(v.corte().atZone(ZoneId.of("America/Lima")).toLocalDate())
                &&(c.resolucionExacta()==null||!c.resolucionExacta().isAfter(v.corte()))
                &&c.evidenciaResolucion()!=null&&!c.evidenciaResolucion().isBlank()){
                boolean anterior=c.resolucionLegitima().isBefore(plazo);
                if(c.resolucionLegitima().equals(plazo)){
                    if(limiteExacto==null)anterior=true;
                    else{
                        if(c.resolucionExacta()==null){sinEvidencia++;continue;}
                        anterior=c.resolucionExacta().isBefore(limiteExacto);
                    }
                }
                if(anterior){if(v.contiene(plazo))resueltos++;continue;}
            }
            if(!v.contiene(plazo)||!v.exigible(plazo,limiteExacto)){
                if(!v.exigible(plazo,limiteExacto))pendientes++;
                continue;
            }
            boolean cumple=false,fechaPorPrecisar=false;
            for(var a:ultimos(c.actuaciones(),Actuacion::fuente,v)){
                if(!a.hechoReal()||!a.acreditaContenido()||!c.identidad().equals(a.compromiso())||c.actividad()!=a.actividad())continue;
                if(a.momentoExacto()!=null&&a.momentoExacto().isAfter(v.corte()))continue;
                if(a.fecha()==null||a.fuente().responsableHistorico()==null){fechaPorPrecisar=true;continue;}
                if(a.fecha().isBefore(plazo)){cumple=true;break;}
                if(a.fecha().equals(plazo)){
                    if(limiteExacto==null){cumple=true;break;}
                    if(a.momentoExacto()==null)fechaPorPrecisar=true;
                    else if(!a.momentoExacto().isAfter(limiteExacto)){cumple=true;break;}
                }
            }
            if(!cumple&&fechaPorPrecisar){sinEvidencia++;continue;}
            base++;if(cumple)exitos++;
        }
        return tasa(v,exitos,base,pendientes,resueltos,sinEvidencia);
    }
    public static Conversion conversion(Collection<Prospeccion> hechos,Ventana v){
        int exitos=0,base=0,pendientes=0,sinEvidencia=0,preparacion=0,otras=0;
        for(var p:ultimos(hechos,Prospeccion::fuente,v)){
            if(p.conversionPorPrecisar()){sinEvidencia++;continue;}
            if(p.fuente().responsableHistorico()==null){sinEvidencia++;continue;}
            var habilitacion=p.habilitaciones().stream().filter(Objects::nonNull)
                    .filter(d->!d.isAfter(v.corte().atZone(ZoneId.of("America/Lima")).toLocalDate())).min(Comparator.naturalOrder()).orElse(null);
            var resolucion=habilitacion==null?p.fechaResolucionDefinitiva():habilitacion;
            if(resolucion==null&&p.terminoDefinitivo()){sinEvidencia++;continue;}
            if(v.contiene(resolucion)){base++;if(habilitacion!=null)exitos++;}
            else if(resolucion==null||resolucion.isAfter(v.corte().atZone(ZoneId.of("America/Lima")).toLocalDate())){pendientes++;if(p.preparacion())preparacion++;else otras++;}
        }
        return new Conversion(tasa(v,exitos,base,pendientes,0,sinEvidencia),preparacion,otras);
    }
    public static Map<String,Tasa> cierres(Collection<Desenlace> hechos,Ventana v){
        var salida=new LinkedHashMap<String,Tasa>();
        var actuales=ultimos(hechos,Desenlace::fuente,v);
        for(var operacion:List.of("VENTA","ALQUILER")){
            int exitos=0,base=0,pendientes=0,sinEvidencia=0;
            for(var d:actuales){
                if(!operacion.equals(d.operacion()))continue;
                if(d.fuente().responsableHistorico()==null){sinEvidencia++;continue;}
                if(!d.resuelta()){pendientes++;continue;}
                if(d.fechaResolucion()==null){sinEvidencia++;continue;}
                if(v.contiene(d.fechaResolucion())){base++;if(d.favorableConfirmadoC5())exitos++;}
            }
            salida.put(operacion,tasa(v,exitos,base,pendientes,0,sinEvidencia));
        }
        return Collections.unmodifiableMap(salida);
    }
    public static PrimeraRespuesta primeraRespuesta(Collection<Consulta> hechos,Ventana v){
        int recibidas=0,respondidas=0,esperando=0,sinFechas=0,sinPeriodo=0,sinResponsable=0,porVerificar=0;
        var duraciones=new ArrayList<BigDecimal>();var esperas=new ArrayList<Espera>();
        for(var c:ultimos(hechos,Consulta::fuente,v)){
            var dia=c.recibida()==null?c.diaRecibido():c.recibida().atZone(ZoneId.of("America/Lima")).toLocalDate();
            if(dia==null){sinPeriodo++;continue;}
            if(!v.contiene(dia))continue;
            if(c.recibida()!=null&&c.recibida().isAfter(v.corte()))continue;
            recibidas++;
            var respuestas=ultimos(c.respuestas(),Respuesta::fuente,v).stream()
                    .filter(r->c.fuente().clave().equals(r.consulta())&&r.comercial()&&(r.enviada()||r.llamadaAtendida()))
                    .filter(r->r.fecha()==null||!r.fecha().isAfter(v.corte())).toList();
            if(respuestas.stream().anyMatch(r->!r.enlaceCausalVerificado())){porVerificar++;continue;}
            var primera=respuestas.stream().filter(r->r.fecha()!=null)
                    .min(Comparator.comparing(Respuesta::fecha)).orElse(null);
            boolean faltaResponsable=c.fuente().responsableHistorico()==null||primera!=null&&primera.fuente().responsableHistorico()==null;
            if(faltaResponsable)sinResponsable++;
            if(primera!=null){
                respondidas++;
                if(c.recibida()==null||primera.fecha().isBefore(c.recibida())||respuestas.stream().anyMatch(r->r.fecha()==null))sinFechas++;
                else if(!faltaResponsable)duraciones.add(horas(Duration.between(c.recibida(),primera.fecha())));
            }else if(!respuestas.isEmpty()){respondidas++;sinFechas++;}
            else{
                esperando++;
                if(c.recibida()==null)sinFechas++;
                else esperas.add(new Espera(c.fuente(),horas(Duration.between(c.recibida(),v.corte()))));
            }
        }
        duraciones.sort(Comparator.naturalOrder());int n=duraciones.size();BigDecimal mediana=null;
        if(n>0)mediana=n%2==1?duraciones.get(n/2):duraciones.get(n/2-1).add(duraciones.get(n/2)).divide(BigDecimal.valueOf(2));
        return new PrimeraRespuesta(VERSION,v,recibidas,respondidas,esperando,sinFechas,sinPeriodo,sinResponsable,porVerificar,n,mediana,esperas,n==0?"SIN_BASE":"EVALUABLE");
    }
    private static BigDecimal horas(Duration d){return BigDecimal.valueOf(d.toMillis()).divide(BigDecimal.valueOf(3_600_000),8,RoundingMode.HALF_UP).stripTrailingZeros();}
    private static Tasa tasa(Ventana v,int exitos,int base,int pendientes,int resueltos,int sinEvidencia){
        return new Tasa(VERSION,v,exitos,base,pendientes,resueltos,sinEvidencia,base==0?null:BigDecimal.valueOf(exitos).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(base),8,RoundingMode.HALF_UP).stripTrailingZeros(),base==0?"SIN_BASE":"EVALUABLE");
    }
    private static <T> List<T> ultimos(Collection<T> hechos,java.util.function.Function<T,Fuente> fuente,Ventana v){
        var porId=new LinkedHashMap<String,T>();
        for(var h:hechos){var f=fuente.apply(h);if(!v.conocido(f.registrada()))continue;
            var anterior=porId.get(f.clave());
            if(anterior==null||fuente.apply(anterior).version()<f.version())porId.put(f.clave(),h);
            else if(fuente.apply(anterior).version()==f.version()&&!anterior.equals(h))throw new IllegalArgumentException("Una versión tiene contenido contradictorio.");
        }
        return List.copyOf(porId.values());
    }
}
