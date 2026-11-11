package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.planes.DerechosDeInformesC7;
import com.controllocal.service.planes.DerechosDeInformesC7.Composicion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.*;
import com.controllocal.service.encargo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.controllocal.service.actividad.FiltrosDeInforme.*;

/** Directriz5.3: composición determinista con universos explícitos, sin comprar autoridad. */
@Service
public class InformesC7 {
    public static final String VERSION="C7-INFORMES-20261111-2";
    public record Consulta(Composicion composicion,LocalDate desde,LocalDate hasta,LocalDate compararDesde,LocalDate compararHasta,Filtros filtros,String operacionCohorte) {
        public Consulta(Composicion c,LocalDate d,LocalDate h,LocalDate cd,LocalDate ch){this(c,d,h,cd,ch,null,null);}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo no admitido en la composición del informe.");}
    }
    public record AlcanceBloque(String rotulo,LocalDate desde,LocalDate hasta,Instant corte,Long responsable,String operacion,Situacion situacion,
                                 CatalogoInteracciones.TipoActividad tipoActividad,String universo,String cobertura) { }
    public record GrupoSituacion(String recurso,String operacion,String situacion,String procedenciaOperacion,long casos) { }
    public record Pendiente(long compromiso,long version,long prospeccion,long responsable,LocalDate fecha,CatalogoInteracciones.TipoActividad actividad,boolean vencido,boolean evidenciaPorVerificar) { }
    public record EstadoEncargo(long encargo,long version,EncargoService.Vigencia vigencia) { }
    public record OperacionActual(long encargosActivos,long oportunidadesAbiertas,List<GrupoSituacion> situaciones,List<Pendiente> compromisos,List<EstadoEncargo> vigenciaEncargos) { }
    public record ResultadosPeriodo(String operacion,long cierresContractualesConfirmados,long noContinuidadRegistrada,long resueltasSinFechaAcreditada) { }
    public record SeguimientoResponsable(long responsable,String nombreProfesional,Map<String,Long> casosPorRecurso,long interacciones,long visitas,long ofertas,long contraofertas,
                                         long compromisosVencidos,List<Fuente> oportunidadesSinProximaActuacion) { }
    public record Prioridad(String causa,List<Fuente> casos,String recomendacion,String cobertura,AlcanceBloque alcance) { }
    public record FichaResponsable(long responsable,String nombreProfesional,SeguimientoResponsable actividad,MedicionesC7.Lectura mediciones,
                                   List<CohortesDeInforme.Cohorte> cohortes,List<Prioridad> prioridades) { }
    public record MetasPreparadas(String definicion,com.controllocal.service.soporte.PeriodoCalendario periodo,Instant corte,Long responsable,String operacion,
                                  List<RendimientoComercialService.Kpi> indicadores,List<RendimientoComercialService.PorResponsable> personas,
                                  List<MetaComercialService.MetaDeAgente> historia,long fuentesSinAtribucion,long hechosDeOtrosResponsablesHistoricos,String alcance) { }
    public record Informe(String definicion,Composicion composicion,String plan,String ambito,LocalDate desde,LocalDate hasta,Instant corte,List<Long> responsablesAutorizados,Map<String,Object> bloques,
                          @com.fasterxml.jackson.annotation.JsonIgnore List<Fuente> fuentes,
                          @com.fasterxml.jackson.annotation.JsonIgnore List<ReferenciaVersion> fuentesInteracciones,
                          @com.fasterxml.jackson.annotation.JsonIgnore List<ProspeccionService.RevisionSeguimiento> fuentesSeguimientos) {
        public Informe{responsablesAutorizados=List.copyOf(responsablesAutorizados);bloques=Collections.unmodifiableMap(new LinkedHashMap<>(bloques));fuentes=List.copyOf(fuentes);fuentesInteracciones=List.copyOf(fuentesInteracciones);fuentesSeguimientos=List.copyOf(fuentesSeguimientos);}
    }
    private final DerechosDeInformesC7 derechos;private final ContextosDeActividad contexto;private final MedicionesC7 mediciones;
    private final RendimientoComercialService rendimiento;private final ComisionService comisiones;private final Alcances alcances;
    private final ActividadInformeC7Repository actividad;private final InteraccionService interacciones;private final IdentidadColaboracionRepository identidades;
    private final EncargoService encargos;private final SeguimientoDeProspeccion seguimientos;private final DesenlacesDeOportunidad desenlaces;
    private final AccesoOportunidad accesoOportunidad;private final AccesoEncargo accesoEncargo;private final ProspeccionService prospecciones;
    private final CompromisoSeguimientoRepository compromisosRepo;private final CompromisosDeSeguimiento compromisos;
    public InformesC7(DerechosDeInformesC7 d,ContextosDeActividad c,MedicionesC7 m,RendimientoComercialService r,ComisionService co,Alcances a,
                     ActividadInformeC7Repository ar,InteraccionService i,IdentidadColaboracionRepository id,EncargoService en,SeguimientoDeProspeccion s,
                     DesenlacesDeOportunidad de,AccesoOportunidad ao,AccesoEncargo ae,ProspeccionService p,CompromisoSeguimientoRepository cr,CompromisosDeSeguimiento cs){
        derechos=d;contexto=c;mediciones=m;rendimiento=r;comisiones=co;alcances=a;actividad=ar;interacciones=i;identidades=id;encargos=en;seguimientos=s;desenlaces=de;accesoOportunidad=ao;accesoEncargo=ae;prospecciones=p;compromisosRepo=cr;compromisos=cs;}
    @Transactional(readOnly=true) public DerechosDeInformesC7.Derechos derechos(Actor a){return derechos.actuales(a,contexto.ahora());}
    @Transactional(readOnly=true) public MedicionesC7.Lectura consultarMediciones(LocalDate desde,LocalDate hasta,Actor a){contexto.exigirOperador(a);var t=contexto.ahora();var h=t.atZone(ZoneId.of("America/Lima")).toLocalDate();return mediciones.medir(ventana(desde==null?h.withDayOfMonth(1):desde,hasta==null?h:hasta,t),a);}
    @Transactional(readOnly=true) public Informe preparar(Consulta q,Actor a){
        if(q==null||q.composicion()==null)throw regla("Elige la composición del informe.");
        var t=contexto.ahora();var hoy=t.atZone(ZoneId.of("America/Lima")).toLocalDate();var derecho=derechos.exigir(q.composicion(),a,t);var scope=alcances.de(a);var f=validar(q.filtros(),scope.rolesAgente());
        validarConsulta(q,f,derecho,hoy);var v=ventana(q.desde()==null?hoy.withDayOfMonth(1):q.desde(),q.hasta()==null?hoy:q.hasta(),t);
        var cartera=actividad.casos(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t);
        // Autorizar las fuentes antes de obtener contenido y conservarlas también para entregaP0.
        var referencias=new ArrayList<Fuente>();for(var c:cartera){switch(c.tipo()){
            case "ENCARGO"->accesoEncargo.exigirLectura(c.id(),a);case "OPORTUNIDAD"->{if(accesoOportunidad.contextoParaNegociacion(c.id(),a).version()!=c.version())throw conflicto();}
            case "PROSPECCION"->{if(prospecciones.obtener(c.id(),a).version()!=c.version())throw conflicto();}default->throw new IllegalStateException("Fuente de cartera no reconocida.");}referencias.add(new Fuente(c.tipo(),c.id(),c.version()));}
        var actos=actividad.actos(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t);for(var x:actos){accesoOportunidad.contextoParaNegociacion(x.oportunidad(),a);referencias.add(new Fuente(x.tipo(),x.id(),x.version()));}
        var resultados=desenlaces.leer(a,t);
        var lectura=interacciones.indicadores(a);var comunicaciones=ultimas(lectura,t);var fuentesR27=seguimientos.enAlcance(a);
        var cabecerasCompromiso=compromisosRepo.alCorte(a.idOrganizacion(),scope.rolesAgente(),null,t);
        var pendientes=new ArrayList<Pendiente>();for(var c:cabecerasCompromiso){var contenido=compromisos.contenidoAutorizado(c,a);referencias.add(new Fuente("COMPROMISO_R27",c.id(),c.version()));
            if(contenido.resolucion()!=null)continue;
            boolean atendida=contenido.actuaciones().stream().anyMatch(x->comunicaciones.stream().anyMatch(i->i.id()==x.interaccion().id()&&i.version()==x.interaccion().version()));if(atendida)continue;
            var fecha=contenido.cambios().isEmpty()?contenido.plazo():contenido.cambios().getLast().nueva();
            pendientes.add(new Pendiente(c.id(),c.version(),c.prospeccion(),c.responsableHistorico(),fecha,contenido.actividad(),fecha!=null&&fecha.isBefore(hoy),!contenido.actuaciones().isEmpty()));}
        var comerciales=seleccionar(cartera,f,false);var propios=seleccionar(cartera,f,true);var claves=claves(comerciales);
        var oportunidades=comerciales.stream().filter(c->c.tipo().equals("OPORTUNIDAD")).map(CasoInforme::id).collect(java.util.stream.Collectors.toSet());
        boolean filtroLocal=f.operacion()!=null||f.situacion()!=null;
        var comunicadas=comunicaciones.stream().filter(i->f.responsable()==null||i.idAgente()==f.responsable()).filter(i->f.tipoActividad()==null||i.tipo()==f.tipoActividad()).filter(i->!filtroLocal||vinculada(i,claves,f)).toList();
        var actosSeleccionados=actos.stream().filter(x->oportunidades.contains(x.oportunidad())&&(f.responsable()==null||x.responsable()==f.responsable())).toList();
        var idsProspecto=comerciales.stream().filter(c->c.tipo().equals("PROSPECCION")).map(CasoInforme::id).collect(java.util.stream.Collectors.toSet());
        var compromisosSeleccionados=pendientes.stream().filter(p->(f.responsable()==null||p.responsable()==f.responsable())&&(!filtroLocal||idsProspecto.contains(p.prospeccion()))).toList();
        var estadosEncargo=new ArrayList<EstadoEncargo>();for(var c:propios)if(c.tipo().equals("ENCARGO"))estadosEncargo.add(new EstadoEncargo(c.id(),c.version(),encargos.consultarVigencia(c.id(),hoy,a)));
        var grupos=agrupar(propios);var b=new LinkedHashMap<String,Object>();var alc=new LinkedHashMap<String,AlcanceBloque>();
        b.put("operacionActual",new OperacionActual(estadosEncargo.stream().filter(e->e.vigencia().habilitado()).count(),propios.stream().filter(c->c.tipo().equals("OPORTUNIDAD")&&Set.of("A","S").contains(c.situacion())).count(),grupos,compromisosSeleccionados,estadosEncargo));
        alc.put("operacionActual",alcance("Cartera actual seleccionada",v,f,true,true,"Responsable actual de cada caso; situación al corte, no estado histórico.","CASOS_AL_CORTE"));
        b.put("resultadosPeriodo",resultadosDelPeriodo(resultados,oportunidades,cartera,f,v));
        alc.put("resultadosPeriodo",new AlcanceBloque("Desenlaces registrados del período",v.desde(),v.hasta(),t,f.responsable(),f.operacion(),f.situacion(),null,"OPORTUNIDADES_CON_DESENLACE_FECHADO","Cierre contractual C5 confirmado o no continuidad N/X con fecha efectiva. Responsable histórico del hecho; tipoActividad no aplica. Los resueltos sin fecha acreditada se informan aparte y no se imputan al período. No es una tasa20.8."));
        var idsEconomicos=cartera.stream().filter(c->c.tipo().equals("ENCARGO")&&(f.responsable()==null||c.responsable()==f.responsable())).map(CasoInforme::id).collect(java.util.stream.Collectors.toSet());
        var economia=comisiones.resumenDePeriodo(v.desde(),v.hasta(),t,new ComisionService.FiltroEconomico(idsEconomicos,f.operacion()),a);
        var dinero=new LinkedHashMap<String,Object>();if(economia!=null){if(f.hechoEconomico()==null||f.hechoEconomico()==HechoEconomico.CALCULO_ACTUAL)dinero.put("calculadoActual",economia.calculadoActual());if(f.hechoEconomico()==null||f.hechoEconomico()==HechoEconomico.DEVENGO_ACTUAL)dinero.put("devengadoActual",economia.devengadoActual());if(f.hechoEconomico()==null||f.hechoEconomico()==HechoEconomico.COBRO_EN_PERIODO)dinero.put("cobradoEnPeriodo",economia.cobradoEnPeriodo());if(f.hechoEconomico()==null||f.hechoEconomico()==HechoEconomico.EXIGIBILIDAD_ACTUAL)dinero.put("exigibilidadActual",economia.exigibilidadActual());dinero.put("casosDeComisionConsultados",economia.casos());dinero.put("sinCuantiaConfirmada",economia.sinCuantiaConfirmada());}
        b.put("economia",Collections.unmodifiableMap(dinero));alc.put("economia",alcance("Economía por hecho y moneda",v,f,true,false,"Cartera autorizada del responsable actual; situación y tipo de actividad no aplican. Hecho="+f.hechoEconomico()+". Sólo cobros efectivamente recibidos son ingresos.","IMPORTE_POR_HECHO_OPERACION_MONEDA"));
        var ventanaActividad=comunicadas.stream().filter(i->v.contiene(i.fechaHecho())).toList();var actosPeriodo=actosSeleccionados.stream().filter(x->v.contiene(x.fecha())).toList();
        if(q.composicion()!=Composicion.OPERACION_ACTUAL){
            b.put("periodo",Map.of("encargosRegistrados",propios.stream().filter(c->c.tipo().equals("ENCARGO")&&v.contiene(c.registrada())).count(),"oportunidadesRegistradas",propios.stream().filter(c->c.tipo().equals("OPORTUNIDAD")&&v.contiene(c.registrada())).count(),"comunicacionesUnicas",ventanaActividad.size(),"visitasRealizadas",actosPeriodo.stream().filter(x->x.tipo().equals("VISITA")&&x.estado().equals("R")).count(),"visitasSinFecha",actosSeleccionados.stream().filter(x->x.tipo().equals("VISITA")&&x.estado().equals("R")&&x.fecha()==null).count(),"ofertas",actosPeriodo.stream().filter(x->x.tipo().equals("OFERTA")&&!x.contraoferta()).count(),"contraofertas",actosPeriodo.stream().filter(ActoInforme::contraoferta).count()));
            alc.put("periodo",alcance("Actividad registrada del período",v,f,true,true,"Altas en cartera actual. Actividad atribuida al autor histórico; tipoActividad sólo filtra comunicaciones C4. Visitas y propuestas mantienen unidades distintas.","EVENTOS_UNICOS"));
        }
        MedicionesC7.Lectura general=null;
        if(q.composicion()!=Composicion.OPERACION_ACTUAL){general=medir(v,a,lectura,f.responsable());b.put("mediciones",general);alc.put("mediciones",alcance("Indicadores generales del profesional o equipo",v,f,false,false,"Todas las operaciones. Operación/situación/tipoActividad locales NO se aplican. Cierre por venta y alquiler con TODOS sus desenlaces resueltos.","POBLACIONES_20.8"));}
        var comparativa=q.compararDesde()==null?null:ventana(q.compararDesde(),q.compararHasta(),t);
        if(avanzada(q.composicion())){
            if(comparativa!=null){b.put("comparacion",medir(comparativa,a,lectura,f.responsable()));alc.put("comparacion",alcance("Indicadores generales del período comparable",comparativa,f,false,false,"Mismas poblaciones20.8 y responsable; filtros locales no aplican.","POBLACIONES_20.8"));}
            else {b.put("comparacion",Map.of("estado","PERIODO_POR_ELEGIR"));alc.put("comparacion",new AlcanceBloque("Período comparable por elegir",null,null,t,f.responsable(),null,null,null,"SIN_PERIODO_COMPARABLE","No se calcula comparación sin ambos extremos elegidos."));}
        }
        var tabla=new ArrayList<SeguimientoResponsable>();var sinProxima=sinProxima(comerciales,comunicaciones,actos,t);
        var nombres=new HashMap<Long,String>();
        if(equipo(q.composicion())){for(long rol:scope.rolesAgente())if(f.responsable()==null||f.responsable()==rol){String nombre=identidades.deResponsable(a.idOrganizacion(),rol,hoy).map(IdentidadColaboracionRepository.Identidad::getNombreProfesional).orElse(null);nombres.put(rol,nombre);
            tabla.add(new SeguimientoResponsable(rol,nombre,casosDelResponsable(propios,rol),ventanaActividad.stream().filter(i->i.idAgente()==rol).count(),actosPeriodo.stream().filter(x->x.responsable()==rol&&x.tipo().equals("VISITA")&&x.estado().equals("R")).count(),actosPeriodo.stream().filter(x->x.responsable()==rol&&x.tipo().equals("OFERTA")&&!x.contraoferta()).count(),actosPeriodo.stream().filter(x->x.responsable()==rol&&x.contraoferta()).count(),compromisosSeleccionados.stream().filter(p->p.responsable()==rol&&p.vencido()).count(),sinProxima.stream().filter(x->comerciales.stream().anyMatch(c->c.tipo().equals("OPORTUNIDAD")&&c.id()==x.id()&&c.responsable()==rol)).toList()));}b.put("responsables",List.copyOf(tabla));alc.put("responsables",alcance("Seguimiento de responsables",v,f,true,true,"Casos actuales frente a actuaciones del autor histórico; no ranking. Se revisan las próximas actuaciones acreditadas en comunicaciones y visitas autorizadas; una espera sin fecha no se convierte en un vencimiento ni se presume ausencia de gestión externa.","CASOS_Y_ACTUACIONES_DISTINTAS"));}
        List<Trayectoria> trayectorias=List.of();
        if(avanzada(q.composicion())){trayectorias=actividad.trayectorias(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t);for(var x:trayectorias)if(accesoOportunidad.contextoParaNegociacion(x.id(),a).version()!=x.version())throw conflicto();
            var seleccion=trayectorias.stream().filter(x->f.responsable()==null||Objects.equals(x.responsableApertura(),f.responsable())).filter(x->q.operacionCohorte()==null||q.operacionCohorte().equals(com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(x.operacion()).name())).toList();
            var cohortes=CohortesDeInforme.componer(seleccion,resultados,v).stream().filter(x->q.operacionCohorte()==null||x.operacion().equals(q.operacionCohorte())).toList();b.put("cohortes",cohortes);alc.put("cohortes",new AlcanceBloque("Cohorte histórica con criterio propio",v.desde(),v.hasta(),t,f.responsable(),q.operacionCohorte(),null,null,"OPORTUNIDADES_ABIERTAS_EN_PERIODO_HASTA_CORTE","Responsable histórico de apertura. Situación/operación de cartera NO cambian esta cohorte; operacionCohorte es independiente."));
            b.put("prioridades",prioridades(cohortes,general,sinProxima,alc.get("operacionActual"),alc.get("cohortes"),alc.get("mediciones")));alc.put("prioridades",alc.get("cohortes"));
            if(q.composicion()==Composicion.DIAGNOSTICO_RESPONSABLE){var fichas=new ArrayList<FichaResponsable>();for(var fila:tabla){var cs=CohortesDeInforme.componer(seleccion.stream().filter(x->Objects.equals(x.responsableApertura(),fila.responsable())).toList(),resultados,v).stream().filter(x->q.operacionCohorte()==null||x.operacion().equals(q.operacionCohorte())).toList();var mi=medir(v,a,lectura,fila.responsable());fichas.add(new FichaResponsable(fila.responsable(),fila.nombreProfesional(),fila,mi,cs,prioridades(cs,mi,fila.oportunidadesSinProximaActuacion(),individual(alc.get("operacionActual"),fila.responsable()),individual(alc.get("cohortes"),fila.responsable()),individual(alc.get("mediciones"),fila.responsable()))));}b.put("diagnosticos",List.copyOf(fichas));alc.put("diagnosticos",alc.get("cohortes"));}
        }
        if(q.composicion()==Composicion.RITMO_METAS||q.composicion()==Composicion.DIAGNOSTICO_RESPONSABLE){var metas=metas(YearMonth.from(v.hasta()).toString(),f,a);b.put("ritmoYMetas",metas);alc.put("ritmoYMetas",new AlcanceBloque("Metas del mes por dimensión aprobada",metas.periodo().desde(),metas.periodo().hasta(),metas.corte(),f.responsable(),f.operacion(),null,null,"CATEGORIAS_20.10","Situación/tipoActividad no aplican. GENERAL permanece único; cada realizado y objetivo comparte operación. El mes/calendario de metas no se prorratea por la ventana local."));if(comparativa!=null&&YearMonth.from(v.hasta()).equals(YearMonth.from(comparativa.hasta()))){b.put("metasComparacion",Map.of("estado","MISMO_MES_DE_METAS","mes",YearMonth.from(v.hasta()).toString()));alc.put("metasComparacion",alc.get("ritmoYMetas"));}else if(comparativa!=null){var mc=metas(YearMonth.from(comparativa.hasta()).toString(),f,a);b.put("metasComparacion",mc);alc.put("metasComparacion",new AlcanceBloque("Metas del mes de comparación",mc.periodo().desde(),mc.periodo().hasta(),mc.corte(),f.responsable(),f.operacion(),null,null,"CATEGORIAS_20.10",mc.alcance()));}}
        // Toda salida conserva los alcances; exportar este contrato no pierde filtros ni denominadores.
        b.put("alcances",Collections.unmodifiableMap(alc));b.put("filtros",f);b.put("cobertura",Map.of("casosAutorizados",cartera.size(),"casosSeleccionados",propios.size(),"casosSinOperacionPropia",cartera.stream().filter(c->c.operacion()==null).count(),"comunicacionesSinFecha",comunicadas.stream().filter(i->i.fechaHecho()==null).count(),"historia","Fuentes actuales autorizadas; no es el mercado completo ni una reconstrucción de estados pasados."));
        contexto.exigirOperador(a);if(!derecho.equals(derechos.exigir(q.composicion(),a,contexto.ahora()))||!new HashSet<>(scope.rolesAgente()).equals(new HashSet<>(alcances.de(a).rolesAgente())))throw conflicto();
        if(!cartera.equals(actividad.casos(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t))||!actos.equals(actividad.actos(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t))||!comunicaciones.equals(ultimas(interacciones.indicadores(a),t))||!cabecerasCompromiso.equals(compromisosRepo.alCorte(a.idOrganizacion(),scope.rolesAgente(),null,t)))throw conflicto();
        if(!resultados.equals(desenlaces.leer(a,t))||avanzada(q.composicion())&&!trayectorias.equals(actividad.trayectorias(a.idOrganizacion(),scope.rolesAgente(),a.esBroker(),t)))throw conflicto();
        for(var c:cartera){if(c.tipo().equals("ENCARGO"))accesoEncargo.exigirLectura(c.id(),a);else if(c.tipo().equals("OPORTUNIDAD"))accesoOportunidad.contextoParaNegociacion(c.id(),a);else prospecciones.obtener(c.id(),a);}
        interacciones.exigirVersiones(comunicaciones.stream().map(i->new ReferenciaVersion(i.id(),i.version())).toList(),a,InteraccionService.FinalidadLectura.INDICADORES);for(var r:fuentesR27)seguimientos.exigirLecturaOriginal(r,a);for(var r:cabecerasCompromiso)compromisos.exigirOriginal(r,a);
        return new Informe(VERSION,q.composicion(),derecho.plan().name(),a.esBroker()?"EQUIPO":"PROPIO",v.desde(),v.hasta(),t,scope.rolesAgente(),b,referencias,comunicaciones.stream().map(i->new ReferenciaVersion(i.id(),i.version())).toList(),fuentesR27);
    }
    private MedicionesC7.Lectura medir(IndicadoresIndividuales.Ventana v,Actor a,LecturaIndicadores datos,Long rol){return rol==null?mediciones.medir(v,a,datos):mediciones.porResponsable(v,a,datos,rol);}
    static List<ResultadosPeriodo> resultadosDelPeriodo(List<IndicadoresIndividuales.Desenlace> datos,Set<Long> oportunidades,List<CasoInforme> cartera,Filtros f,IndicadoresIndividuales.Ventana v){
        var situaciones=new HashMap<Long,String>();for(var c:cartera)if(c.tipo().equals("OPORTUNIDAD"))situaciones.put(c.id(),c.situacion());
        var salida=new ArrayList<ResultadosPeriodo>();
        for(String op:List.of("VENTA","ALQUILER"))if(f.operacion()==null||op.equals(f.operacion())){
            var casos=datos.stream().filter(d->oportunidades.contains(d.fuente().id())&&op.equals(d.operacion())&&(f.responsable()==null||Objects.equals(d.fuente().responsableHistorico(),f.responsable()))).toList();
            long cierres=casos.stream().filter(d->d.favorableConfirmadoC5()&&v.contiene(d.fechaResolucion())).count();
            long negativas=casos.stream().filter(d->Set.of("N","X").contains(situaciones.get(d.fuente().id()))&&d.resuelta()&&v.contiene(d.fechaResolucion())).count();
            long sinFecha=casos.stream().filter(d->d.resuelta()&&d.fechaResolucion()==null).count();
            salida.add(new ResultadosPeriodo(op,cierres,negativas,sinFecha));
        }
        return List.copyOf(salida);
    }
    private MetasPreparadas metas(String mes,Filtros f,Actor a){var m=rendimiento.del(mes,a);var personas=m.responsables().stream().filter(p->f.responsable()==null||p.idRolAgente()==f.responsable()).map(p->new RendimientoComercialService.PorResponsable(p.idRolAgente(),p.nombreProfesional(),p.indicadores().stream().filter(k->f.operacion()==null||k.operacion().equals("GENERAL")||k.operacion().equals(f.operacion())).toList())).toList();var kpis=f.responsable()==null?m.kpis():personas.isEmpty()?List.<RendimientoComercialService.Kpi>of():personas.getFirst().indicadores();return new MetasPreparadas(m.definicion(),m.periodo(),m.generadoEn(),f.responsable(),f.operacion(),kpis.stream().filter(k->f.operacion()==null||k.operacion().equals("GENERAL")||k.operacion().equals(f.operacion())).toList(),personas,m.metasHistoricas().stream().filter(h->f.responsable()==null||h.idRolAgente()==f.responsable()).toList(),m.fuentesSinAtribucion(),m.hechosDeOtrosResponsablesHistoricos(),"Metas mensuales20.10; GENERAL no se duplica y situación local no altera el realizado.");}
    static List<Fuente> sinProxima(List<CasoInforme> casos,List<MedicionInteraccion> comunicaciones,List<ActoInforme> actos,Instant corte){
        var hoy=corte.atZone(ZoneId.of("America/Lima")).toLocalDate();var salida=new ArrayList<Fuente>();
        for(var c:casos)if(c.tipo().equals("OPORTUNIDAD")&&Set.of("A","S").contains(c.situacion())){
            boolean comunicada=comunicaciones.stream().filter(i->i.vinculos().stream().anyMatch(x->x.tipo().equals("OPORTUNIDAD")&&Objects.equals(x.referencia(),c.id()))).map(MedicionInteraccion::proximaActuacion).filter(Objects::nonNull).anyMatch(p->p.origen()!=null&&p.origen()!=OrigenActuacion.SUGERENCIA_SISTEMA&&(p.fecha()==null||!p.fecha().isBefore(hoy)));
            boolean visita=actos.stream().filter(x->x.oportunidad()==c.id()&&x.tipo().equals("VISITA")).anyMatch(x->Set.of("P","G").contains(x.estado())&&x.programada()!=null&&(x.programada().isAfter(hoy)||x.programada().equals(hoy)&&(x.hora()==null||!x.programada().atTime(x.hora()).atZone(ZoneId.of("America/Lima")).toInstant().isBefore(corte)))||x.estado().equals("R")&&x.proximaAccion()!=null);
            if(!comunicada&&!visita)salida.add(new Fuente(c.tipo(),c.id(),c.version()));
        }return List.copyOf(salida);
    }
    private static Map<String,Long> casosDelResponsable(List<CasoInforme> datos,long rol){var salida=new LinkedHashMap<String,Long>();for(String tipo:List.of("ENCARGO","OPORTUNIDAD","PROSPECCION"))salida.put(tipo,datos.stream().filter(c->c.responsable()==rol&&c.tipo().equals(tipo)).count());return Collections.unmodifiableMap(salida);}
    static List<GrupoSituacion> agrupar(List<CasoInforme> casos){var grupos=new TreeMap<String,Long>();for(var c:casos){String origen=c.operacion()==null?"NO_ACREDITADA":c.tipo().equals("PROSPECCION")?"ENCARGO_VINCULADO":"REGISTRO_DEL_RECURSO";grupos.merge(c.tipo()+"|"+Objects.toString(operacion(c),"SIN_OPERACION_ACREDITADA")+"|"+c.situacion()+"|"+origen,1L,Long::sum);}return grupos.entrySet().stream().map(e->{var k=e.getKey().split("\\|");return new GrupoSituacion(k[0],k[1],k[2],k[3],e.getValue());}).toList();}
    private static List<Prioridad> prioridades(List<CohortesDeInforme.Cohorte> cohortes,MedicionesC7.Lectura m,List<Fuente> sinProxima,AlcanceBloque cartera,AlcanceBloque historica,AlcanceBloque consultas){var out=new ArrayList<Prioridad>();if(!sinProxima.isEmpty())out.add(new Prioridad("PROXIMA_ACTUACION_NO_ACREDITADA",sinProxima,"Revisar estos casos y registrar la siguiente actuación acordada o planificada.","Sin inferir desinterés o una actuación ejecutada.",cartera));var faltantes=cohortes.stream().flatMap(c->c.fuentes().stream()).filter(c->c.responsableHistorico()==null||c.apertura()==null).map(c->new Fuente("OPORTUNIDAD",c.id(),c.version())).toList();if(!faltantes.isEmpty())out.add(new Prioridad("FUENTES_HISTORICAS_INSUFICIENTES",faltantes,"Completar o revisar la evidencia histórica autorizada.","No sustituir responsable histórico por actual.",historica));if(m!=null&&m.primeraRespuesta()!=null&&m.primeraRespuesta().medicion()!=null){var refsConsultas=m.primeraRespuesta().medicion().antiguedades().stream().map(e->new Fuente(e.consulta().tipo(),e.consulta().id(),e.consulta().version())).toList();if(!refsConsultas.isEmpty())out.add(new Prioridad("CONSULTAS_ESPERANDO_RESPUESTA",refsConsultas,"Revisar y responder estas consultas comerciales.","Respuesta real vinculada, sin envío automático.",consultas));}return List.copyOf(out);}
    private static AlcanceBloque individual(AlcanceBloque x,long rol){return new AlcanceBloque(x.rotulo(),x.desde(),x.hasta(),x.corte(),rol,x.operacion(),x.situacion(),x.tipoActividad(),x.universo(),x.cobertura());}
    private static AlcanceBloque alcance(String nombre,IndicadoresIndividuales.Ventana v,Filtros f,boolean operacion,boolean situacion,String cobertura,String unidad){return new AlcanceBloque(nombre,v.desde(),v.hasta(),v.corte(),f.responsable(),operacion?f.operacion():null,situacion?f.situacion():null,nombre.equals("Cartera actual seleccionada")?null:situacion?f.tipoActividad():null,unidad,cobertura);}
    private static boolean avanzada(Composicion c){return Set.of(Composicion.EVOLUCION_MEJORA,Composicion.RITMO_METAS,Composicion.DIAGNOSTICO_RESPONSABLE).contains(c);}
    private static boolean equipo(Composicion c){return Set.of(Composicion.SEGUIMIENTO_RESPONSABLES,Composicion.RITMO_METAS,Composicion.DIAGNOSTICO_RESPONSABLE).contains(c);}
    private static void validarConsulta(Consulta q,Filtros f,DerechosDeInformesC7.Derechos d,LocalDate hoy){var desde=q.desde()==null?hoy.withDayOfMonth(1):q.desde();var hasta=q.hasta()==null?hoy:q.hasta();validarFechas(desde,hasta,hoy);if(f.tipoActividad()!=null&&!equipo(q.composicion()))throw new CapacidadDeshabilitadaException();if(f.responsable()!=null&&!equipo(q.composicion()))throw new CapacidadDeshabilitadaException();if(q.operacionCohorte()!=null&&(!avanzada(q.composicion())||!Set.of("VENTA","ALQUILER").contains(q.operacionCohorte())))throw regla("La operación de cohorte requiere composición avanzada y su propio criterio.");if(d.plan()==com.controllocal.domain.planes.PlanBrox.FREE&&(f.operacion()!=null||f.situacion()!=null||f.hechoEconomico()!=null||!desde.equals(hoy.withDayOfMonth(1))||!hasta.equals(hoy)))throw new CapacidadDeshabilitadaException();if(q.compararDesde()!=null||q.compararHasta()!=null){if(!avanzada(q.composicion()))throw new CapacidadDeshabilitadaException();validarFechas(q.compararDesde(),q.compararHasta(),hoy);if(ChronoUnit.DAYS.between(desde,hasta)!=ChronoUnit.DAYS.between(q.compararDesde(),q.compararHasta()))throw regla("Compara períodos de igual duración.");}}
    public void validarRecurrencia(Consulta plantilla,int dias,Actor a){LocalDate hoy=contexto.hoy(),desde;try{desde=hoy.minusDays(dias-1L);}catch(DateTimeException|ArithmeticException ex){throw regla("El período no puede representarse.");}var q=new Consulta(plantilla.composicion(),desde,hoy,plantilla.compararDesde(),plantilla.compararHasta(),plantilla.filtros(),plantilla.operacionCohorte());var d=derechos.exigir(q.composicion(),a,contexto.ahora());validarConsulta(q,validar(q.filtros(),alcances.de(a).rolesAgente()),d,hoy);}
    private static void validarFechas(LocalDate d,LocalDate h,LocalDate hoy){if(d==null||h==null||d.getYear()<1||h.getYear()>9999||d.isAfter(h)||h.isAfter(hoy))throw regla("Período completo, ordenado y no futuro requerido.");}
    private static IndicadoresIndividuales.Ventana ventana(LocalDate d,LocalDate h,Instant t){validarFechas(d,h,t.atZone(ZoneId.of("America/Lima")).toLocalDate());return new IndicadoresIndividuales.Ventana(d,h,t);}
    private static List<MedicionInteraccion> ultimas(LecturaIndicadores l,Instant t){var r=new LinkedHashMap<Long,MedicionInteraccion>();for(var i:l.registros()){if(i.registradoEn()==null)throw regla("No se puede acreditar el corte de una comunicación sin fecha de registro.");if(i.registradoEn().isAfter(t))throw conflicto();var anterior=r.get(i.id());if(anterior==null||i.version()>anterior.version())r.put(i.id(),i);}return List.copyOf(r.values());}
    private static ReglaNegocioException regla(String s){return new ReglaNegocioException(s);}private static ConflictoException conflicto(){return new ConflictoException("Cambiaron las fuentes, versiones, plan o permisos durante el informe.");}
}
