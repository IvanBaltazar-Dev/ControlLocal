package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.MetricasIaB0Repository;
import com.controllocal.persistence.presupuesto.MetricasIaB0Repository.Ambito;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.Instant;
import java.util.*;

/** CAT-R25: métricas agregadas OPS y ledger exacto FIN son proyecciones distintas del mismo B0. */
@Service
public class MetricasIaB0 {
    public record Periodo(Instant desde,Instant hasta,Instant consultadaEn,String criterio) {}
    public record Estado(String estado,long operaciones) {}
    public record EstadoEtapa(String etapa,String estado,long etapas) {}
    /** Son cantidades originales por categoría; un subconjunto no se suma de nuevo a su padre. */
    public record Unidad(String proveedor,String modelo,String versionMedicion,String clase,String unidad,
            String subconjuntoDe,BigDecimal cantidadOriginal,long mediciones) {}
    public record Latencia(long observaciones,BigDecimal mediaNanos,Long p50Nanos,Long p95Nanos) {}
    public record SerieHttp(String proveedor,String modeloSolicitado,String versionConfiguracion,
            ObservacionHttpIa.Resultado resultado,Integer estadoHttp,long observaciones,long enviosIntentados,
            long erroresHttp,Latencia latencia) {}
    public record Cobertura(long operaciones,long operacionesConMedicion,long medicionesConservadas,
            long operacionesConObservacion,long operacionesConObservacionValida,long operacionesSinObservacionValida,
            long observacionesValidas,long observacionesNoInterpretables,long observacionesRepetidas,
            long observacionesContradictorias,long observacionesTardiasIncluidas,long enviosIntentados,
            long erroresHttp,long etapasConservadas,String limite) {}
    public record Tecnica(Periodo periodo,Cobertura cobertura,List<Estado> estados,List<Unidad> unidadesOriginales,
            List<EstadoEtapa> etapas,List<SerieHttp> http,Latencia latencia,String disponibilidadCantidades) {
        public Tecnica{estados=List.copyOf(estados);unidadesOriginales=List.copyOf(unidadesOriginales);etapas=List.copyOf(etapas);http=List.copyOf(http);}
    }
    public record MedicionFinanciera(UUID id,String proveedor,String modelo,String versionTarifa,List<Medicion> unidadesOriginales,
            List<Tarifa> tarifasOriginales,BigDecimal costoUsd,String estadoCosto,String referenciaFuente,
            Instant registradaEn,String claveEtapa) {
        public MedicionFinanciera{unidadesOriginales=List.copyOf(unidadesOriginales);tarifasOriginales=List.copyOf(tarifasOriginales);}
    }
    public record EtapaFinanciera(String etapa,String estado,Instant reclamadaEn,Instant resueltaEn) {}
    public record MovimientoCosto(String hecho,UUID confirmacion,UUID medicion,UUID anterior,BigDecimal costoTotalUsd,BigDecimal diferenciaUsd,
            String documento,String linea,String revisionFuente,String fechaFuente,String evidencia,String causa,Long revision,Long cuenta,UUID concesion,Instant registradaEn){}
    public record ValoracionTecnica(BigDecimal costoCierreOriginalUsd,BigDecimal efectivoUsd,BigDecimal imputadoUsd,
            BigDecimal pendientePositivoUsd,Long revision,List<MovimientoCosto> historia,String cobertura){
        public ValoracionTecnica{historia=List.copyOf(historia);}
    }
    public record Financiera(Periodo periodo,UUID plan,String versionPlan,String finalidad,UUID caso,UUID comando,UUID fondo,
            String estado,Instant admitidaEn,Instant iniciadaEn,BigDecimal maximoTecnicoUsd,BigDecimal costoTecnicoUsd,
            List<MedicionFinanciera> mediciones,List<EtapaFinanciera> etapas,List<SerieHttp> http,
            BigDecimal costoPorTareaCorrecta,String coberturaCalidad,ValoracionTecnica valoracionTecnica) {
        public Financiera{mediciones=List.copyOf(mediciones);etapas=List.copyOf(etapas);http=List.copyOf(http);}
    }
    private record ObservacionFila(UUID operacion,String contenido,Instant registradaEn) {}
    private record MedicionFila(UUID id,String proveedor,String modelo,String versionTarifa,String unidadesOriginales,
            String tarifasOriginales,BigDecimal costoUsd,String estadoCosto,String referenciaFuente,Instant registradaEn,String claveEtapa) {}
    private record Intento(UUID operacion,String clave) {}
    private record Grupo(String proveedor,String modelo,String version,ObservacionHttpIa.Resultado resultado,Integer estadoHttp) {}
    private record LecturaHttp(List<SerieHttp> series,Latencia latencia,long operacionesValidas,long validas,
            long invalidas,long repetidas,long contradictorias,long tardias,long envios,long erroresHttp) {}
    private final AtribucionesPlataforma autoridad;
    private final MetricasIaB0Repository repo;
    private final Documentos json;
    public MetricasIaB0(AtribucionesPlataforma autoridad,MetricasIaB0Repository repo,Documentos json){
        this.autoridad=autoridad;this.repo=repo;this.json=json;
    }
    @Transactional
    public Tecnica tecnica(SesionPlataforma sesion,Instant desde,Instant hasta){
        periodo(desde,hasta);
        var a=resolver(sesion,false);var limite=ambito(a,a.caso(),desde,hasta);
        var f=repo.tecnica(limite);var h=http(f.observaciones(),hasta);
        var resultado=new Tecnica(periodo(desde,hasta,f.consultadaEn()),
            new Cobertura(f.operaciones(),f.conMedicion(),f.mediciones(),f.conObservacion(),h.operacionesValidas(),
                f.operaciones()-h.operacionesValidas(),h.validas(),h.invalidas(),h.repetidas(),h.contradictorias(),h.tardias(),
                h.envios(),h.erroresHttp(),f.etapas(),
                "Medición, observación y envío intentado son hechos distintos. Sin observación no se infiere tiempo ni llamada0; HTTP completo no acredita tarea correcta o facturación."),
            lista(f.estados(),Estado[].class),lista(f.unidades(),Unidad[].class).stream().map(u->new Unidad(u.proveedor(),u.modelo(),u.versionMedicion(),u.clase(),u.unidad(),u.subconjuntoDe(),null,u.mediciones())).toList(),lista(f.estadosEtapa(),EstadoEtapa[].class),h.series(),h.latencia(),"NO_HABILITADAS: política específica de agregación IA pendiente; cantidades nativas sólo FIN.");
        repo.auditar(a.cuenta(),a.concesion(),limite,"TECNICA",null);
        revalidar(sesion,a,false);return resultado;
    }
    @Transactional
    public Financiera financiera(SesionPlataforma sesion,UUID caso,UUID comando,Instant desde,Instant hasta){
        periodo(desde,hasta);
        var a=resolver(sesion,true);
        if(caso==null||comando==null||a.caso()!=null&&!a.caso().equals(caso))throw new AccesoNoAutorizadoException();
        var limite=ambito(a,caso,desde,hasta);
        var f=repo.financiera(limite,comando).orElseThrow(()->new NoEncontradoException("Ledger de evaluación"));
        if(!caso.equals(f.caso())||!comando.equals(f.comando()))throw new IllegalStateException("La lectura financiera conserva otra operación.");
        var medidas=lista(f.mediciones(),MedicionFila[].class).stream().map(m->new MedicionFinanciera(m.id(),m.proveedor(),m.modelo(),
            m.versionTarifa(),lista(m.unidadesOriginales(),Medicion[].class),lista(m.tarifasOriginales(),Tarifa[].class),
            m.costoUsd(),m.estadoCosto(),m.referenciaFuente(),m.registradaEn(),m.claveEtapa())).toList();
        var h=http(f.observaciones(),hasta);
        var resultado=new Financiera(periodo(desde,hasta,f.consultadaEn()),a.plan(),a.versionPlan(),a.finalidad(),caso,comando,a.fondo(),
            f.estado(),f.admitidaEn(),f.iniciadaEn(),f.maximoTecnicoUsd(),f.costoTecnicoUsd(),medidas,
            lista(f.etapas(),EtapaFinanciera[].class),h.series(),null,
            "SIN_REFERENCIA_HUMANA: el control automático o HTTP200 no acredita una tarea correctamente completada.",
            new ValoracionTecnica(f.costoTecnicoUsd(),medidas.isEmpty()&&!"CANCELADA".equals(f.estado())?null:f.costoEfectivo(),f.costoImputado(),f.pendientePositivo(),f.revisionCosto(),
                lista(f.historiaCosto(),MovimientoCosto[].class),f.revisionCosto()==null?"SIN_PROYECCION_RECONSTRUIDA":medidas.isEmpty()&&!"CANCELADA".equals(f.estado())?"SIN_MEDICION_NATIVA: costo efectivo desconocido; provisión original vigente.":"ORIGINAL_Y_AJUSTES_DEL_MISMO_LEDGER"));
        repo.auditar(a.cuenta(),a.concesion(),limite,"FINANCIERA",comando);
        revalidar(sesion,a,true);return resultado;
    }
    private Actual resolver(SesionPlataforma sesion,boolean financiero){
        var a=autoridad.resolver(sesion,financiero?Atribucion.FINANCIERO_PLATFORM:Atribucion.OPERACION_IA_PLATFORM,
            financiero?Accion.LEER_LEDGER:Accion.CONSULTAR_ESTADO,financiero?Campo.LEDGER:Campo.METRICA_TECNICA);
        if(a==null||a.cuenta()<=0||a.concesion()==null||a.plan()==null||a.versionPlan()==null||a.versionPlan().isBlank()
            ||a.finalidad()==null||a.finalidad().isBlank()||a.recursoCompra()!=null
            ||financiero&&a.fondo()==null||!financiero&&a.fondo()!=null)throw new AccesoNoAutorizadoException();
        return a;
    }
    private void revalidar(SesionPlataforma sesion,Actual antes,boolean financiero){
        if(!antes.equals(resolver(sesion,financiero)))throw new AccesoNoAutorizadoException();
    }
    private static Ambito ambito(Actual a,UUID caso,Instant desde,Instant hasta){
        return new Ambito(a.plan(),a.versionPlan(),a.finalidad(),caso,a.fondo(),desde,hasta);
    }
    private static void periodo(Instant desde,Instant hasta){
        if(desde==null||hasta==null||!hasta.isAfter(desde))throw new ReglaNegocioException("Indica un período de admisión desde/hasta válido.");
    }
    private static Periodo periodo(Instant desde,Instant hasta,Instant consulta){
        return new Periodo(desde,hasta,consulta,
            "COHORTE_ADMITIDA_[desde,hasta): estado y todos los hechos visibles en una sentencia SQL; incluye observaciones tardías. No es una serie histórica por fecha de factura.");
    }
    private <T> List<T> lista(String contenido,Class<T[]> tipo){return List.of(json.comoObjeto(contenido,tipo));}
    private LecturaHttp http(String contenido,Instant hasta){
        var todas=lista(contenido,ObservacionFila[].class);
        var unicas=new LinkedHashMap<Intento,ObservacionHttpIa>();var conflictos=new HashSet<Intento>();
        long invalidas=0,repetidas=0,tardias=0;
        for(var f:todas){
            if(f.registradaEn()!=null&&!f.registradaEn().isBefore(hasta))tardias++;
            ObservacionHttpIa o;
            try{
                var campos=json.comoMapa(f.contenido());
                Object duracion=campos.get("duracionNanos");
                if(!(campos.get("envioIntentado") instanceof Boolean)
                    ||!(duracion instanceof Integer||duracion instanceof Long||duracion instanceof BigInteger))
                    throw new IllegalArgumentException("Faltan los primitivos observados; no se infieren cero/false.");
                o=json.comoObjeto(f.contenido(),ObservacionHttpIa.class);
            }
            catch(RuntimeException incompatible){invalidas++;continue;}
            if(f.operacion()==null){invalidas++;continue;}
            var clave=new Intento(f.operacion(),o.claveIntento());
            var previa=unicas.putIfAbsent(clave,o);
            if(previa!=null){if(previa.equals(o))repetidas++;else conflictos.add(clave);}
        }
        conflictos.forEach(unicas::remove);
        var grupos=new TreeMap<Grupo,List<ObservacionHttpIa>>(Comparator.comparing(Grupo::proveedor).thenComparing(Grupo::modelo)
            .thenComparing(Grupo::version).thenComparing(g->g.resultado().name()).thenComparing(Grupo::estadoHttp,Comparator.nullsFirst(Integer::compareTo)));
        var operaciones=new HashSet<UUID>();var duraciones=new ArrayList<Long>();long envios=0,errores=0;
        for(var entrada:unicas.entrySet()){
            var o=entrada.getValue();operaciones.add(entrada.getKey().operacion());duraciones.add(o.duracionNanos());
            if(o.envioIntentado())envios++;if(errorHttp(o))errores++;
            grupos.computeIfAbsent(new Grupo(o.proveedor(),o.modelo(),o.versionConfiguracion(),o.resultado(),o.estadoHttp()),g->new ArrayList<>()).add(o);
        }
        var series=grupos.entrySet().stream().map(e->{var g=e.getKey();var obs=e.getValue();
            return new SerieHttp(g.proveedor(),g.modelo(),g.version(),g.resultado(),g.estadoHttp(),obs.size(),
                obs.stream().filter(ObservacionHttpIa::envioIntentado).count(),obs.stream().filter(MetricasIaB0::errorHttp).count(),
                latencia(obs.stream().map(ObservacionHttpIa::duracionNanos).toList()));}).toList();
        return new LecturaHttp(series,latencia(duraciones),operaciones.size(),unicas.size(),invalidas,repetidas,
            conflictos.size(),tardias,envios,errores);
    }
    private static boolean errorHttp(ObservacionHttpIa o){return o.estadoHttp()!=null&&(o.estadoHttp()<200||o.estadoHttp()>=300);}
    private static Latencia latencia(List<Long> duraciones){
        if(duraciones.isEmpty())return null;
        var ordenadas=duraciones.stream().sorted().toList();BigInteger suma=BigInteger.ZERO;
        for(long n:ordenadas)suma=suma.add(BigInteger.valueOf(n));int n=ordenadas.size();
        // Media técnica en nanos, no aritmética de precio/créditos; percentiles nearest-rank de esta distribución.
        var media=new BigDecimal(suma).divide(BigDecimal.valueOf(n),MathContext.DECIMAL128);
        return new Latencia(n,media,ordenadas.get((n-1)/2),ordenadas.get((int)((95L*n+99)/100-1)));
    }
}
