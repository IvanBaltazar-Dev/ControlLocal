package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Catálogo16/D5: bandas y orden dentro de consultas, sin pesos ni siguiente acción global. */
public final class PoliticaDeDespacho {
    private PoliticaDeDespacho() { }
    public static final ZoneId ZONA=ZoneId.of("America/Lima");
    public enum Banda { R0,R1,R2,R3 }
    public enum Vista { TODAS,PROCESO,HOY,VENCIDAS,FUTURAS,SIN_FECHA }
    public record Consulta(Vista vista,String recurso,Long referencia) {
        public Consulta {
            if(vista==null)vista=Vista.TODAS;
            if((recurso==null)!=(referencia==null)||referencia!=null&&referencia<=0||recurso!=null&&recurso.isBlank())
                throw new ReglaNegocioException("El recurso necesita tipo e identidad completos.");
            if(vista==Vista.PROCESO&&recurso==null)throw new ReglaNegocioException("Identifica el proceso o recurso consultado.");
        }
        public static Consulta todas(){return new Consulta(Vista.TODAS,null,null);}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Filtro de Radar no admitido.");}
    }
    /** Fecha y hora sólo si constan; detectadoEn es la primera detección conservada, no la última lectura. */
    public record Asunto(String id,String recurso,long referencia,boolean accionable,LocalDate fecha,
                         LocalTime hora,Instant detectadoEn,boolean fechaEnConflicto,
                         String evidenciaIncertidumbre,boolean riesgoDuplicacion,boolean intervencionHumana,LocalDate revisarEn) {
        public Asunto(String id,String recurso,long referencia,boolean accionable,LocalDate fecha,LocalTime hora,
                      Instant detectadoEn,boolean fechaEnConflicto,String evidenciaIncertidumbre,boolean riesgoDuplicacion,boolean intervencionHumana){
            this(id,recurso,referencia,accionable,fecha,hora,detectadoEn,fechaEnConflicto,evidenciaIncertidumbre,riesgoDuplicacion,intervencionHumana,null);
        }
        public Asunto {
            if(id==null||id.isBlank()||recurso==null||recurso.isBlank()||referencia<=0||fecha==null&&hora!=null)
                throw new IllegalArgumentException("La actuación debe conservar identidad y precisión temporal coherentes.");
        }
    }
    public record Clasificacion(Banda banda,boolean correspondeHoy,boolean plazoVencido,
                                LocalDate fecha,LocalTime hora,Instant detectadoEn,boolean fechaEnConflicto,LocalDate revisarEn) { }
    public static Clasificacion clasificar(Asunto a,Instant ahora){
        Objects.requireNonNull(ahora);LocalDate hoy=ahora.atZone(ZONA).toLocalDate();
        boolean fecha=a.fecha()!=null&&!a.fechaEnConflicto();
        boolean hoyAct=fecha&&a.fecha().equals(hoy)||hoy.equals(a.revisarEn());
        boolean vencido=fecha&&(a.hora()==null?a.fecha().isBefore(hoy):!ahora.isBefore(a.fecha().atTime(a.hora()).atZone(ZONA).toInstant()))
                ||a.revisarEn()!=null&&a.revisarEn().isBefore(hoy);
        Banda banda=null;
        if(a.accionable()){
            if(a.evidenciaIncertidumbre()!=null&&!a.evidenciaIncertidumbre().isBlank()&&a.riesgoDuplicacion()&&a.intervencionHumana())banda=Banda.R0;
            else if(hoyAct||vencido)banda=Banda.R1;
            else if(fecha&&a.fecha().isAfter(hoy)||a.revisarEn()!=null&&a.revisarEn().isAfter(hoy))banda=Banda.R2;
            else banda=Banda.R3;
        }
        return new Clasificacion(banda,hoyAct,vencido,a.fecha(),a.hora(),a.detectadoEn(),a.fechaEnConflicto(),a.revisarEn());
    }
    /** Las consultas pueden solaparse; R0 necesario para actuar sobre un recurso conserva precedencia. */
    public static <T> List<T> despachar(Collection<T> entradas,Function<T,Asunto> datos,Consulta consulta,Instant ahora){
        Consulta q=consulta==null?Consulta.todas():consulta;
        var unicas=new LinkedHashMap<String,T>();
        for(T e:entradas){var a=datos.apply(e);if(unicas.putIfAbsent(a.id(),e)!=null)throw new IllegalStateException("Una actuación no puede duplicarse en el Radar.");}
        var seleccion=new ArrayList<T>();var recursosConActuacion=new HashSet<String>();
        for(T e:unicas.values()){
            var a=datos.apply(e);if(!enRecurso(a,q))continue;
            var c=clasificar(a,ahora);
            boolean incluir=switch(q.vista()){
                case TODAS,PROCESO->true;
                case HOY->c.banda()!=null&&c.correspondeHoy();
                case VENCIDAS->c.banda()!=null&&c.plazoVencido();
                case FUTURAS->c.banda()!=null&&(!c.fechaEnConflicto()&&c.fecha()!=null&&c.fecha().isAfter(ahora.atZone(ZONA).toLocalDate())
                        ||c.revisarEn()!=null&&c.revisarEn().isAfter(ahora.atZone(ZONA).toLocalDate()));
                case SIN_FECHA->c.banda()!=null&&(c.fecha()==null||c.fechaEnConflicto())&&c.revisarEn()==null;
            };
            if(incluir){seleccion.add(e);recursosConActuacion.add(clave(a));}
        }
        for(T e:unicas.values()){
            var a=datos.apply(e);if(!enRecurso(a,q)||seleccion.contains(e)||clasificar(a,ahora).banda()!=Banda.R0)continue;
            if(recursosConActuacion.contains(clave(a)))seleccion.add(e);
        }
        seleccion.sort(comparador(datos,ahora));return List.copyOf(seleccion);
    }
    private static boolean enRecurso(Asunto a,Consulta q){return q.recurso()==null||normalizar(a.recurso()).equals(normalizar(q.recurso()))&&a.referencia()==q.referencia();}
    private static String normalizar(String tipo){return "CAPTACION".equals(tipo)?"ENCARGO":tipo;}
    private static String clave(Asunto a){return normalizar(a.recurso())+":"+a.referencia();}
    public static <T> Comparator<T> comparador(Function<T,Asunto> datos,Instant ahora){
        return (x,y)->{
            Asunto a=datos.apply(x),b=datos.apply(y);Banda ba=clasificar(a,ahora).banda(),bb=clasificar(b,ahora).banda();
            int r=Integer.compare(orden(ba),orden(bb));if(r!=0)return r;
            if(ba==Banda.R1||ba==Banda.R2){
                r=Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()).compare(fechaOrden(a),fechaOrden(b));if(r!=0)return r;
                r=Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()).compare(horaOrden(a),horaOrden(b));if(r!=0)return r;
            }
            r=Comparator.nullsLast(Comparator.<Instant>naturalOrder()).compare(a.detectadoEn(),b.detectadoEn());
            return r!=0?r:a.id().compareTo(b.id());
        };
    }
    private static LocalDate fechaOrden(Asunto a){
        LocalDate original=a.fechaEnConflicto()?null:a.fecha();
        return original==null?a.revisarEn():a.revisarEn()!=null&&a.revisarEn().isBefore(original)?a.revisarEn():original;
    }
    private static LocalTime horaOrden(Asunto a){return !a.fechaEnConflicto()&&Objects.equals(fechaOrden(a),a.fecha())?a.hora():null;}
    private static int orden(Banda b){return b==null?4:switch(b){case R0->0;case R1->1;case R2->2;case R3->3;};}
}
