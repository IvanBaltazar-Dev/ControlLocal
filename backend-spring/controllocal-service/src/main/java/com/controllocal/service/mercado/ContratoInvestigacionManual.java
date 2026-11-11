package com.controllocal.service.mercado;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;

/** CAT-R24 aprobado11/11: estructura y comprobaciones de una entrega, no acredita permisos ni publica. */
public final class ContratoInvestigacionManual {
    private ContratoInvestigacionManual() { }
    public enum Fuente { BROX_APROBADA,URBANIA,ADONDEVIVIR,REMAX_PERU,ANUNCIANTE_ORIGINAL,BCRP }
    public enum Etapa { BUSQUEDA,DEPURACION,CORROBORACION,REVISION,ENTREGA }
    public enum TipoTiempo { ACTIVO,ESPERA }
    public enum Cierre { COMPLETA,INTERRUMPIDA,FALLIDA,INCIERTA }
    public enum Resultado { SUFICIENTE,PARCIAL,SIN_EVIDENCIA,INCIDENCIA_TECNICA }
    public enum ConceptoImporte { PRECIO_TOTAL,RENTA_MENSUAL,INICIAL,SEPARACION,OTRO,DESCONOCIDO }
    /** La fuente puede acreditar un día sin acreditar una hora. No se fabrica medianoche. */
    public record FechaFuente(LocalDate dia,Instant instante) {
        public FechaFuente {if(dia==null||instante!=null&&!dia.equals(instante.atZone(ZoneId.of("America/Lima")).toLocalDate()))throw error("Conserva la precisión real de la fecha de fuente.");}
    }
    /** Referencia de configuración: recibirla en un JSON nunca habilita una fuente. El propietario revalida. */
    public record FuentePrevista(Fuente fuente,String versionPermiso,String viaPermitida,String reutilizacionPermitida,String evidenciaPermiso) { }
    public record BaseConsultada(String version,Instant corte,Instant vigenteHasta,String limitacion) { }
    public record Plan(String version,String alcance,String carencia,BaseConsultada base,List<FuentePrevista> fuentes,
                       String condicionParada,Duration limiteActivoPorRonda,Set<Etapa> etapas,int rondas,
                       String entrega,Instant plazoComunicado,String regla,String versionRegla) {
        public Plan {fuentes=fuentes==null?null:List.copyOf(fuentes);etapas=etapas==null?null:Set.copyOf(etapas);}
    }
    public record Tiempo(Etapa etapa,Instant desde,Instant hasta,TipoTiempo tipo,String evidencia) { }
    public record Consulta(UUID identidad,Fuente fuente,String versionPermiso,String consulta,String referenciaEvidencia,
                           Instant realizadaEn,boolean ejecutada,boolean falloTecnico) { }
    /** No se interpreta una dirección como identidad; ésta procede de verificación y conserva su evidencia. */
    public record Referencia(String id,String inmuebleVerificado,String evidenciaIdentidad,Fuente fuente,String origenDatos,
                              String versionFuente,FechaFuente observadaEn,Instant obtenidaEn,boolean incluidaEnCalculo,
                              CalculoPrecioMercado.Comparable comparable,String motivoExclusion,String duplicadaDe,
                              ConceptoImporte conceptoImporte,String evidenciaImporte) { }
    public record Revision(String referenciaDecision,long revisor,Instant revisadaEn,String evidencia) { }
    public record Ronda(UUID identidad,int numero,Cierre cierre,Resultado resultado,Instant iniciadaEn,Instant terminadaEn,
                        String ejecucionAsistidaIa,List<Tiempo> tiempos,List<Consulta> consultas,List<Referencia> referencias,
                        String informe,String limitaciones,Revision revision,Map<Etapa,String> etapasAcreditadas) {
        public Ronda {tiempos=tiempos==null?null:List.copyOf(tiempos);consultas=consultas==null?null:List.copyOf(consultas);referencias=referencias==null?null:List.copyOf(referencias);etapasAcreditadas=etapasAcreditadas==null?null:Map.copyOf(etapasAcreditadas);}
    }
    public record Comprobacion(List<UUID> rondasCompletas,List<UUID> pendientesDeAcreditacion,int inmueblesUnicos,
                               Duration activo,Duration espera,List<UUID> limiteActivoAlcanzado) {
        public Comprobacion {rondasCompletas=List.copyOf(rondasCompletas);pendientesDeAcreditacion=List.copyOf(pendientesDeAcreditacion);limiteActivoAlcanzado=List.copyOf(limiteActivoAlcanzado);}
    }
    /** Pura: fuente, decisión, autor y entrega requieren además acreditación vigente por el caso Core. */
    public static Comprobacion comprobar(Plan p,List<Ronda> rondas,Instant registradaEn){return comprobar(p,rondas,registradaEn,true);}
    /** La entrega del operador no puede atribuirse una revisión competente ni acreditar el cargo. */
    public static Comprobacion examinarEntrega(Plan p,List<Ronda> rondas,Instant registradaEn){
        if(rondas!=null&&rondas.stream().anyMatch(r->r!=null&&r.revision()!=null))throw error("La revisión competente se registra por su propia autoridad, no por la entrega del operador.");
        return comprobar(p,rondas,registradaEn,false);
    }
    private static Comprobacion comprobar(Plan p,List<Ronda> rondas,Instant registradaEn,boolean revisionCompetente){
        validar(p);if(rondas==null||registradaEn==null)throw error("Conserva las rondas y la fecha real de registro.");
        if(p.base()!=null&&p.base().corte().isAfter(registradaEn))throw error("La base consultada no puede proceder de un corte futuro.");
        var ids=new HashSet<UUID>();var numeros=new HashSet<Integer>();var referencias=new HashMap<String,Referencia>();
        var completas=new ArrayList<UUID>();var pendientes=new ArrayList<UUID>();var limite=new ArrayList<UUID>();
        Duration activo=Duration.ZERO,espera=Duration.ZERO;boolean activoCompleto=true,esperaCompleta=true;var consultas=new HashSet<UUID>();
        var permisos=new EnumMap<Fuente,String>(Fuente.class);for(var f:p.fuentes())permisos.put(f.fuente(),f.versionPermiso());
        for(var r:rondas){
            if(r==null||r.identidad()==null||!ids.add(r.identidad())||r.numero()<1||r.numero()>p.rondas()||!numeros.add(r.numero()))throw error("Una ronda conserva identidad y ordinal únicos del trabajo; reenvíos no crean otra unidad.");
            if(r.cierre()==null||r.resultado()==null||r.iniciadaEn()==null||r.iniciadaEn().isAfter(registradaEn)||r.terminadaEn()!=null&&(r.terminadaEn().isBefore(r.iniciadaEn())||r.terminadaEn().isAfter(registradaEn)))throw error("Conserva las fechas efectivas de la ronda, sin sustituirlas por la de registro.");
            if(r.consultas()==null||r.referencias()==null)throw error("Las listas ausentes no equivalen a búsqueda válida sin hallazgos.");
            var etapas=r.etapasAcreditadas()==null?Map.<Etapa,String>of():r.etapasAcreditadas();
            if(etapas.values().stream().anyMatch(v->!texto(v)))throw error("Cada etapa cumplida conserva su evidencia, independiente de medir minutos.");
            Duration ar=Duration.ZERO;var intervalos=new ArrayList<Tiempo>();
            if(r.tiempos()==null||r.tiempos().isEmpty()){activoCompleto=false;esperaCompleta=false;}
            for(var t:r.tiempos()==null?List.<Tiempo>of():r.tiempos()){
                if(t==null||t.etapa()==null||t.desde()==null||t.hasta()==null||t.hasta().isBefore(t.desde())||t.desde().isBefore(r.iniciadaEn())||t.hasta().isAfter(r.terminadaEn()==null?registradaEn:r.terminadaEn())||!texto(t.evidencia())||t.tipo()==null)throw error("Conserva tiempos activos y esperas separados con evidencia y precisión coherentes.");
                var duracion=Duration.between(t.desde(),t.hasta());if(t.tipo()==TipoTiempo.ACTIVO){ar=ar.plus(duracion);intervalos.add(t);}else espera=espera.plus(duracion);
            }
            intervalos.sort(Comparator.comparing(Tiempo::desde));for(int i=1;i<intervalos.size();i++)if(intervalos.get(i).desde().isBefore(intervalos.get(i-1).hasta()))throw error("El mismo tiempo de trabajo no puede contarse dos veces entre etapas activas; las esperas pueden solaparse con trabajo activo.");
            activo=activo.plus(ar);if(ar.compareTo(p.limiteActivoPorRonda())>=0)limite.add(r.identidad());
            boolean busquedaValida=false;
            for(var c:r.consultas()){
                if(c==null||c.identidad()==null||!consultas.add(c.identidad())||c.fuente()==null||!Objects.equals(permisos.get(c.fuente()),c.versionPermiso())||!texto(c.versionPermiso())||!texto(c.consulta())||!texto(c.referenciaEvidencia())||c.realizadaEn()==null||c.realizadaEn().isBefore(r.iniciadaEn())||c.realizadaEn().isAfter(r.terminadaEn()==null?registradaEn:r.terminadaEn()))throw error("Cada consulta efectiva conserva fuente/versión permitida, identidad, fecha y evidencia.");
                if(c.ejecutada()&&!c.falloTecnico()&&c.fuente()!=Fuente.BROX_APROBADA)busquedaValida=true;
            }
            for(var v:r.referencias()){
                if(v==null||!texto(v.id())||referencias.putIfAbsent(v.id(),v)!=null||v.fuente()==null||!permisos.containsKey(v.fuente())||!texto(v.origenDatos())||!texto(v.versionFuente())||v.obtenidaEn()==null||v.obtenidaEn().isAfter(r.terminadaEn()==null?registradaEn:r.terminadaEn()))throw error("Cada referencia conserva procedencia, versión y fechas acreditadas; no se fabrica una observación.");
                if(v.observadaEn()!=null&&(v.observadaEn().dia().isAfter(v.obtenidaEn().atZone(ZoneId.of("America/Lima")).toLocalDate())||v.observadaEn().instante()!=null&&v.observadaEn().instante().isAfter(v.obtenidaEn())))throw error("La observación no puede ser posterior a su obtención.");
                if(v.incluidaEnCalculo()){
                    if(v.fuente()==Fuente.BCRP)throw error("BCRP conserva su función de contexto agregado; no se convierte en un comparable individual.");
                    if(v.observadaEn()==null||v.conceptoImporte()==null||!texto(v.evidenciaImporte())||v.comparable()==null||v.comparable().contexto()==null)throw error("Un importe o fecha ambiguos se conservan, pero no alimentan el cálculo.");
                    var operacion=v.comparable().contexto().operacion();
                    if(operacion==com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA&&v.conceptoImporte()!=ConceptoImporte.PRECIO_TOTAL||operacion==com.controllocal.domain.inmueble.OperacionInmobiliaria.ALQUILER&&v.conceptoImporte()!=ConceptoImporte.RENTA_MENSUAL)throw error("Una inicial, separación u otro importe no sustituye el precio total ni la renta.");
                    if(!texto(v.inmuebleVerificado())||!texto(v.evidenciaIdentidad())||v.comparable()==null||!v.inmuebleVerificado().equals(v.comparable().inmuebleVerificado())||!v.id().equals(v.comparable().referencia())||v.duplicadaDe()!=null||v.motivoExclusion()!=null)throw error("Una referencia ambigua, duplicada o excluida no alimenta el cálculo.");
                }else if(!texto(v.motivoExclusion()))throw error("Conserva el motivo de exclusión o revisión de una referencia.");
            }
            if(r.cierre()==Cierre.COMPLETA){
                if(r.terminadaEn()==null||!texto(r.ejecucionAsistidaIa())||!texto(r.informe())||!texto(r.limitaciones())||!etapas.keySet().containsAll(p.etapas())||!busquedaValida||r.resultado()==Resultado.INCIDENCIA_TECNICA||revisionCompetente&&(r.revision()==null||r.revision().revisor()<=0||!texto(r.revision().referenciaDecision())||!texto(r.revision().evidencia())||r.revision().revisadaEn()==null||r.revision().revisadaEn().isBefore(r.terminadaEn())||r.revision().revisadaEn().isAfter(registradaEn)))throw error("Una ronda completa requiere ejecución, plan cumplido, revisión y entrega acreditables; una declaración de IA no basta.");
                if(r.resultado()==Resultado.SIN_EVIDENCIA&&r.referencias().stream().anyMatch(Referencia::incluidaEnCalculo))throw error("Ausencia de evidencia y comparables incluidos no son el mismo resultado.");
                if(revisionCompetente)completas.add(r.identidad());else pendientes.add(r.identidad());
            }else pendientes.add(r.identidad());
        }
        var unicos=new HashSet<String>();var origenes=new HashSet<String>();
        for(var v:referencias.values()){
            if(v.duplicadaDe()!=null&&(!referencias.containsKey(v.duplicadaDe())||v.id().equals(v.duplicadaDe())))throw error("El duplicado necesita un antecedente real de la misma entrega.");
            var cadena=new HashSet<String>();var actual=v;while(actual.duplicadaDe()!=null){if(!cadena.add(actual.id()))throw error("Las referencias duplicadas no pueden formar ciclos.");actual=referencias.get(actual.duplicadaDe());if(actual==null)throw error("Falta el antecedente del duplicado.");}
            if(v.incluidaEnCalculo()&&(!unicos.add(v.inmuebleVerificado())||!origenes.add(v.origenDatos())))throw error("El mismo inmueble no multiplica la muestra por aparecer en varias fuentes.");
        }
        return new Comprobacion(completas,pendientes,unicos.size(),activoCompleto?activo:null,esperaCompleta?espera:null,limite);
    }
    public static void validar(Plan p){
        if(p==null||!texto(p.version())||!texto(p.alcance())||!texto(p.carencia())||p.fuentes()==null||p.fuentes().isEmpty()||!texto(p.condicionParada())||p.limiteActivoPorRonda()==null||p.limiteActivoPorRonda().isNegative()||p.limiteActivoPorRonda().isZero()||p.etapas()==null||!p.etapas().containsAll(Set.of(Etapa.BUSQUEDA,Etapa.REVISION,Etapa.ENTREGA))||p.rondas()<1||!texto(p.entrega())||p.plazoComunicado()==null||!texto(p.regla())||!texto(p.versionRegla()))throw error("Delimita el plan, carencia, fuentes, parada, esfuerzo, revisión, entrega, plazo y regla antes de comenzar.");
        var fuentes=new HashSet<Fuente>();for(var f:p.fuentes())if(f==null||f.fuente()==null||!fuentes.add(f.fuente())||!texto(f.versionPermiso())||!texto(f.viaPermitida())||!texto(f.reutilizacionPermitida())||!texto(f.evidenciaPermiso()))throw error("La lista de candidatas no sustituye la habilitación y procedencia de cada fuente.");
        if(p.base()!=null&&(!texto(p.base().version())||p.base().corte()==null||p.base().vigenteHasta()==null||!p.base().vigenteHasta().isAfter(p.base().corte())||!texto(p.base().limitacion())))throw error("Conserva corte, vigencia y carencia de la base revisada; una base ausente no es una referencia actual.");
    }
    private static boolean texto(String s){return s!=null&&!s.isBlank();}
    private static ReglaNegocioException error(String s){return new ReglaNegocioException(s);}
}
