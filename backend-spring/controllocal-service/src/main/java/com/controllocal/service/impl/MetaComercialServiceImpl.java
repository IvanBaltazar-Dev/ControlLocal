package com.controllocal.service.impl;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.controllocal.service.soporte.IndicadorMetaComercial.VERSION;

/** CAT-E30/RF074–077: una propuesta no decide; las mutaciones reservadas usan Actuaciones. */
@Service
public class MetaComercialServiceImpl implements MetaComercialService {
    public static final String FIJAR="establecer_meta",DECIDIR="decidir_propuesta_meta",PROPONER="proponer_meta";
    private final MetaComercialRepository metas;private final ContextosDeActividad contexto;private final Alcances alcances;
    private final PresupuestoIaRepository organizaciones;private final IdentidadColaboracionRepository identidades;
    private final Documentos json;private final ComandoIdempotenteRepository locks;private final ComandosIdempotentes comandos;
    public MetaComercialServiceImpl(MetaComercialRepository r,ContextosDeActividad c,Alcances a,PresupuestoIaRepository o,
            IdentidadColaboracionRepository i,Documentos j,ComandoIdempotenteRepository l,ComandosIdempotentes co){metas=r;contexto=c;alcances=a;organizaciones=o;identidades=i;json=j;locks=l;comandos=co;}
    private PeriodoCalendario periodo(String mes){return PeriodoCalendario.desde(mes,contexto.hoy());}
    private List<Long> alcance(Actor a){contexto.exigirOperador(a);return alcances.de(a).rolesAgente().stream().distinct().sorted().toList();}
    @Override @Transactional(readOnly=true) public void exigirLecturaEstablecimiento(Establecer d,Actor a){var roles=alcance(a);if(d==null||d.asignaciones()==null||d.asignaciones().stream().anyMatch(x->x==null||!roles.contains(x.idRolAgente())))throw new NoEncontradoException("Actuación de metas");}
    @Override @Transactional(readOnly=true) public Definicion definicion(Actor a){contexto.exigirMetadata(a);return new Definicion(VERSION,Arrays.stream(IndicadorMetaComercial.values()).map(k->new Categoria(k.name(),k.rotulo(),k.unidad(),k.porOperacion()?List.of("VENTA","ALQUILER"):List.of("GENERAL"))).toList(),"C/P/S/F históricos se conservan sin conversión; las nuevas metas usan exclusivamente estas categorías.");}
    private boolean independiente(Actor a){return a.esAgente()&&organizaciones.organizacionActual(a.idOrganizacion()).filter(x->x.activa()&&"INDIVIDUAL".equals(x.modalidad())).isPresent();}
    @Override @Transactional(readOnly=true) public boolean puedeDecidir(Actor a){contexto.exigirOperador(a);return a.esBroker()||independiente(a);}
    @Override @Transactional(readOnly=true) public CapacidadMeta capacidadSobrePropuesta(long id,Actor a){var roles=alcance(a);var h=metas.cabecera(a.idOrganizacion(),id,false).filter(x->roles.contains(x.agente())).orElseThrow(()->new NoEncontradoException("Propuesta de meta"));return new CapacidadMeta(h.id(),h.version(),h.agente(),h.estado(),h.reemplazadaPor(),VERSION.equals(h.definicion())&&(a.esBroker()||independiente(a)&&h.agente()==a.idRolOperativo()));}
    private void decidirSobre(long rol,Actor a){if(!alcance(a).contains(rol)||(!a.esBroker()&&!(independiente(a)&&rol==a.idRolOperativo())))throw new AccesoNoAutorizadoException();}
    private static void version(Long v){if(v==null||v<0)throw new ReglaNegocioException("Indica la versión observada; ausencia de meta usa versión0, no objetivo0.");}
    private static String motivo(String v){if(v==null||v.trim().length()<PoliticaComercial.MOTIVO_REASIGNACION.valor()||v.trim().length()>300)throw new ReglaNegocioException("Conserva un motivo de 10 a 300 caracteres.");return v.trim();}
    private static int valor(Integer v){if(v==null||v<0)throw new ReglaNegocioException("La meta necesita un conteo entero no negativo.");return v;}
    private static List<LocalDate> calendario(List<LocalDate> c,PeriodoCalendario p){
        if(c==null)return null;var s=new TreeSet<LocalDate>();
        for(var d:c)if(d==null||d.isBefore(p.desde())||d.isAfter(p.hasta())||!s.add(d))throw new ReglaNegocioException("El calendario declara fechas distintas del período; no se completan días automáticamente.");
        return List.copyOf(s);
    }
    private static String calendarioArray(List<LocalDate> c){return c==null?null:c.stream().map(d->"\""+d+"\"").collect(java.util.stream.Collectors.joining(",","[","]"));}
    private List<LocalDate> fechas(String c){if(c==null)return null;var m=json.comoMapa("{\"fechas\":"+c+"}");return ((List<?>)m.get("fechas")).stream().map(x->LocalDate.parse(x.toString())).toList();}
    private Asignacion forma(Asignacion d,PeriodoCalendario p,Actor a){
        if(d==null||d.idRolAgente()==null)throw new ReglaNegocioException("Identifica al profesional de la meta.");
        decidirSobre(d.idRolAgente(),a);var k=IndicadorMetaComercial.exigir(d.kpi());version(d.versionEsperada());
        return new Asignacion(d.idRolAgente(),k.name(),k.operacion(d.operacion()),d.versionEsperada(),valor(d.valor()),calendario(d.calendario(),p),motivo(d.motivo()));
    }
    private Establecer normalizar(Establecer d,Actor a){
        if(d==null||d.mes()==null||d.asignaciones()==null||d.asignaciones().isEmpty())throw new ReglaNegocioException("Selecciona las metas que vas a establecer.");
        var p=periodo(d.mes());var result=new ArrayList<Asignacion>();var claves=new HashSet<String>();
        for(var x:d.asignaciones()){var n=forma(x,p,a);if(!claves.add(clave(n.idRolAgente(),n.kpi(),n.operacion())))throw new ReglaNegocioException("La misma meta aparece dos veces en la decisión.");result.add(n);}
        result.sort(Comparator.comparing((Asignacion x)->clave(x.idRolAgente(),x.kpi(),x.operacion())));return new Establecer(p.codigo(),List.copyOf(result));
    }
    private MetaComercialRepository.Meta observada(long agente,String kpi,String op,long esperada,PeriodoCalendario p,Actor a,boolean lock){
        var m=metas.actual(a.idOrganizacion(),agente,kpi,op,p.anio(),p.mes(),lock).orElse(null);
        if((m==null?0:m.version())!=esperada)throw new ConflictoException("Cambió la meta; vuelve a revisar su versión.");return m;
    }
    @Override @Transactional(readOnly=true) public Establecer validarEstablecimiento(Establecer d,Actor a){
        var n=normalizar(d,a);var p=periodo(n.mes());for(var x:n.asignaciones())observada(x.idRolAgente(),x.kpi(),x.operacion(),x.versionEsperada(),p,a,false);return n;
    }
    @Override @Transactional public Resultado fijar(Establecer d,Actor a,ContextoSesion s,String clave,Procedencia proc){
        contexto.iniciarOperador(a,s);var n=normalizar(d,a);var viejo=previo(FIJAR,n,clave,a);if(viejo.isPresent())return viejo.get();
        var p=periodo(n.mes());for(var x:n.asignaciones())bloquearMeta(p,x.idRolAgente(),x.kpi(),x.operacion(),a);
        var actuales=new ArrayList<MetaComercialRepository.Meta>();for(var x:n.asignaciones())actuales.add(observada(x.idRolAgente(),x.kpi(),x.operacion(),x.versionEsperada(),p,a,true));
        var salida=new ArrayList<MetaDeAgente>();var ahora=contexto.ahora();
        for(int i=0;i<n.asignaciones().size();i++){
            var x=n.asignaciones().get(i);var anterior=actuales.get(i);long id=aplicar(x,p,anterior,a,ahora);
            metas.insertarRevision(metas.siguienteRevision(),a.idOrganizacion(),x.idRolAgente(),x.kpi(),x.operacion(),VERSION,p.anio(),p.mes(),"B","A",anterior==null?null:anterior.valor(),x.valor(),calendarioArray(x.calendario()),x.motivo(),a.idRolOperativo(),ahora,x.versionEsperada(),x.versionEsperada()+1,id,x.valor());
            salida.add(fila(p,x.idRolAgente(),x.kpi(),x.operacion(),a));
        }
        return guardar(FIJAR,n,clave,new Resultado(salida),a,proc);
    }
    private long aplicar(Asignacion x,PeriodoCalendario p,MetaComercialRepository.Meta anterior,Actor a,Instant ahora){
        if(anterior==null)return metas.insertar(a.idOrganizacion(),x.idRolAgente(),x.kpi(),x.operacion(),VERSION,p.anio(),p.mes(),x.valor(),calendarioArray(x.calendario()),a.idRolOperativo(),ahora);
        if(metas.actualizar(a.idOrganizacion(),anterior.id(),x.versionEsperada(),x.valor(),calendarioArray(x.calendario()),a.idRolOperativo(),ahora)!=1)throw new ConflictoException("Cambió la meta.");return anterior.id();
    }
    @Override @Transactional public Resultado proponer(String mes,Propuesta d,Actor a,ContextoSesion s,String clave,Procedencia proc){
        contexto.iniciarOperador(a,s);if(!a.esAgente()||independiente(a))throw new AccesoNoAutorizadoException();
        if(d==null||mes==null)throw new ReglaNegocioException("Declara el período y la propuesta.");var p=periodo(mes);var k=IndicadorMetaComercial.exigir(d.kpi());var op=k.operacion(d.operacion());version(d.versionMetaEsperada());valor(d.valor());motivo(d.motivo());var cal=calendario(d.calendario(),p);
        if((d.propuestaAnterior()==null)!=(d.versionPropuestaAnterior()==null))throw new ReglaNegocioException("Identifica propuesta y versión anteriores juntas.");
        var n=new Propuesta(k.name(),op,d.versionMetaEsperada(),d.propuestaAnterior(),d.versionPropuestaAnterior(),d.valor(),cal,d.motivo().trim());var entrada=Map.of("mes",p.codigo(),"propuesta",n);
        var viejo=previo(PROPONER,entrada,clave,a);if(viejo.isPresent())return viejo.get();bloquearMeta(p,a.idRolOperativo(),k.name(),op,a);
        var actual=observada(a.idRolOperativo(),k.name(),op,n.versionMetaEsperada(),p,a,true);
        var viva=metas.historia(a.idOrganizacion(),p.anio(),p.mes(),List.of(a.idRolOperativo())).stream().filter(r->k.name().equals(r.kpi())&&op.equals(r.operacion())&&"E".equals(r.estado())&&r.reemplazadaPor()==null).findFirst().orElse(null);
        if(viva!=null&&(n.propuestaAnterior()==null||viva.id()!=n.propuestaAnterior()||viva.version()!=n.versionPropuestaAnterior()))throw new ConflictoException("Revisa la propuesta vigente antes de sustituirla.");
        if(viva==null&&n.propuestaAnterior()!=null)throw new ConflictoException("La propuesta anterior ya no está pendiente.");
        long id=metas.siguienteRevision();if(viva!=null&&metas.sustituir(a.idOrganizacion(),viva.id(),viva.version(),id)!=1)throw new ConflictoException("Cambió la propuesta.");
        metas.insertarRevision(id,a.idOrganizacion(),a.idRolOperativo(),k.name(),op,VERSION,p.anio(),p.mes(),"P","E",actual==null?null:actual.valor(),n.valor(),calendarioArray(cal),n.motivo(),a.idRolOperativo(),contexto.ahora(),n.versionMetaEsperada(),null,actual==null?null:actual.id(),null);
        return guardar(PROPONER,entrada,clave,new Resultado(List.of(fila(p,a.idRolOperativo(),k.name(),op,a))),a,proc);
    }
    private MetaComercialRepository.Revision cargar(long id,Actor a,boolean lock){
        var scope=alcance(a);var h=metas.cabecera(a.idOrganizacion(),id,lock).filter(x->scope.contains(x.agente())).orElseThrow(()->new NoEncontradoException("Propuesta de meta"));
        var r=metas.revision(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Propuesta de meta"));if(h.version()!=r.version())throw new ConflictoException("Cambió la propuesta.");return r;
    }
    @Override @Transactional(readOnly=true) public Decision validarDecision(Decision d,Actor a){
        if(d==null||d.propuesta()==null||d.decision()==null)throw new ReglaNegocioException("Identifica la propuesta y su decisión.");version(d.versionEsperada());motivo(d.motivo());var r=cargar(d.propuesta(),a,false);decidirSobre(r.agente(),a);
        if(!VERSION.equals(r.definicion()))throw new ReglaNegocioException("La propuesta histórica conserva su definición; no se convierte en una meta nueva.");
        if(r.version()!=d.versionEsperada()||!"E".equals(r.estado())||r.reemplazadaPor()!=null)throw new ConflictoException("La propuesta cambió o ya tiene desenlace.");
        if(d.decision()!=TipoDecision.APROBAR&&d.valorAprobado()!=null)throw new ReglaNegocioException("Observar o rechazar no aprueba un importe objetivo.");
        if(d.valorAprobado()!=null)valor(d.valorAprobado());var p=periodo(YearMonth.of(r.anio(),r.mes()).toString());if(d.decision()==TipoDecision.APROBAR)observada(r.agente(),r.kpi(),r.operacion(),r.versionMeta(),p,a,false);
        return new Decision(r.id(),r.version(),d.decision(),d.valorAprobado(),d.motivo().trim());
    }
    @Override @Transactional public Resultado resolver(Decision d,Actor a,ContextoSesion s,String clave,Procedencia proc){
        contexto.iniciarOperador(a,s);if(d==null||d.propuesta()==null)throw new ReglaNegocioException("Identifica la propuesta.");
        var h=cargar(d.propuesta(),a,false);decidirSobre(h.agente(),a);var viejo=previo(DECIDIR,d,clave,a);if(viejo.isPresent())return viejo.get();
        var n=validarDecision(d,a);var p=periodo(YearMonth.of(h.anio(),h.mes()).toString());bloquearMeta(p,h.agente(),h.kpi(),h.operacion(),a);var r=cargar(h.id(),a,true);validarDecision(n,a);
        var anterior=n.decision()==TipoDecision.APROBAR?observada(r.agente(),r.kpi(),r.operacion(),r.versionMeta(),p,a,true):null;Long id=r.meta();Long nuevaVersion=null;Integer aprobado=null;
        if(n.decision()==TipoDecision.APROBAR){aprobado=n.valorAprobado()==null?r.propuesto():n.valorAprobado();id=aplicar(new Asignacion(r.agente(),r.kpi(),r.operacion(),r.versionMeta(),aprobado,fechas(r.calendario()),n.motivo()),p,anterior,a,contexto.ahora());nuevaVersion=r.versionMeta()+1;}
        String estado=switch(n.decision()){case APROBAR->"A";case OBSERVAR->"O";case RECHAZAR->"R";};
        if(metas.decidir(a.idOrganizacion(),r.id(),n.versionEsperada(),estado,aprobado,a.idRolOperativo(),n.motivo(),contexto.ahora(),nuevaVersion,id)!=1)throw new ConflictoException("Cambió la decisión de meta.");
        return guardar(DECIDIR,d,clave,new Resultado(List.of(fila(p,r.agente(),r.kpi(),r.operacion(),a))),a,proc);
    }
    @Override @Transactional(readOnly=true) public List<MetaDeAgente> del(String mes,Actor a){var p=periodo(mes);return filas(p,alcance(a),a);}
    private List<MetaDeAgente> filas(PeriodoCalendario p,List<Long> scope,Actor a){
        var actuales=metas.del(a.idOrganizacion(),p.anio(),p.mes(),scope);var historia=metas.historia(a.idOrganizacion(),p.anio(),p.mes(),scope);var out=new ArrayList<MetaDeAgente>();
        for(long rol:scope){String nombreProfesional=nombre(rol,a);for(var k:IndicadorMetaComercial.values())for(String op:k.porOperacion()?List.of("VENTA","ALQUILER"):List.of("GENERAL"))out.add(componer(rol,k.name(),op,actuales,historia,nombreProfesional));
            var historicas=new TreeSet<String>();actuales.stream().filter(x->x.agente()==rol&&x.definicion()==null).forEach(x->historicas.add(x.kpi()));historia.stream().filter(x->x.agente()==rol&&x.definicion()==null).forEach(x->historicas.add(x.kpi()));
            for(var k:historicas)out.add(componer(rol,k,null,actuales,historia,nombreProfesional));}
        contexto.exigirOperador(a);if(!new HashSet<>(scope).equals(new HashSet<>(alcances.de(a).rolesAgente())))throw new ConflictoException("Cambió el ámbito de metas.");return List.copyOf(out);
    }
    private MetaDeAgente fila(PeriodoCalendario p,long rol,String k,String op,Actor a){return componer(rol,k,op,metas.del(a.idOrganizacion(),p.anio(),p.mes(),List.of(rol)),metas.historia(a.idOrganizacion(),p.anio(),p.mes(),List.of(rol)),nombre(rol,a));}
    private MetaDeAgente componer(long rol,String k,String op,List<MetaComercialRepository.Meta> actuales,List<MetaComercialRepository.Revision> historia,String nombreProfesional){
        var m=actuales.stream().filter(x->x.agente()==rol&&x.kpi().equals(k)&&Objects.equals(x.operacion(),op)).findFirst().orElse(null);
        var rs=historia.stream().filter(x->x.agente()==rol&&x.kpi().equals(k)&&Objects.equals(x.operacion(),op)).toList();
        boolean historica=op==null;var indicador=historica?null:IndicadorMetaComercial.exigir(k);var pendiente=rs.stream().filter(x->"E".equals(x.estado())&&x.reemplazadaPor()==null).findFirst().map(x->pendiente(x,m,nombreProfesional)).orElse(null);
        return new MetaDeAgente(rol,nombreProfesional,k,op,historica?KpiCanonico.porCodigo(k).rotulo():indicador.rotulo(),historica?KpiCanonico.porCodigo(k).unidad(2):indicador.unidad(),historica?"HISTORICA_CPSF":VERSION,m==null?null:m.id(),historica?null:m==null?0:m.version(),m==null?null:m.valor(),m==null?null:fechas(m.calendario()),historica,pendiente,rs.stream().map(this::revision).toList());
    }
    private Revision revision(MetaComercialRepository.Revision r){return new Revision(r.id(),r.definicion()==null?null:r.version(),r.origen(),r.estado(),r.anterior(),r.propuesto(),r.motivo(),r.autor(),r.fecha(),r.decisor(),r.motivoDecision(),r.decidida(),r.reemplazadaPor(),r.versionMeta(),fechas(r.calendario()),r.aprobado());}
    private String nombre(long rol,Actor a){return identidades.deResponsable(a.idOrganizacion(),rol,contexto.hoy()).map(x->x.getNombreProfesional()).orElse(null);}
    private PropuestaMeta pendiente(MetaComercialRepository.Revision r,Actor a){return pendiente(r,metas.actual(a.idOrganizacion(),r.agente(),r.kpi(),r.operacion(),r.anio(),r.mes(),false).orElse(null),nombre(r.agente(),a));}
    private PropuestaMeta pendiente(MetaComercialRepository.Revision r,MetaComercialRepository.Meta actual,String nombreProfesional){return new PropuestaMeta(r.id(),r.definicion()==null?null:r.version(),r.agente(),nombreProfesional,r.kpi(),r.operacion(),r.definicion()==null?KpiCanonico.porCodigo(r.kpi()).rotulo():IndicadorMetaComercial.exigir(r.kpi()).rotulo(),actual==null?null:actual.valor(),r.propuesto(),r.versionMeta(),fechas(r.calendario()),r.motivo(),r.fecha(),r.definicion()==null?"HISTORICA_CPSF":r.definicion(),actual==null?null:actual.definicion()==null?null:actual.version(),revision(r));}
    @Override @Transactional(readOnly=true) public List<PropuestaMeta> propuestasPendientes(Actor a){return metas.pendientes(a.idOrganizacion(),alcance(a)).stream().map(r->pendiente(r,a)).toList();}
    @Override @Transactional(readOnly=true) public PropuestaMeta consultarPropuesta(long id,Actor a){return pendiente(cargar(id,a,false),a);}
    private static String clave(long rol,String k,String op){return rol+"|"+k+"|"+op;}
    private void bloquear(String s){var h=Idempotencia.huellaExacta(s);locks.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));}
    private void bloquearMeta(PeriodoCalendario p,long rol,String k,String op,Actor a){bloquear("META|"+a.idOrganizacion()+"|"+p.codigo()+"|"+clave(rol,k,op));}
    private record Recibo(List<Long> responsables,Resultado resultado) { }
    private static Long referenciaRecibo(MetaDeAgente m){if(m.idMeta()!=null)return m.idMeta();if(m.propuesta()!=null)return m.propuesta().idRevision();if(!m.historial().isEmpty())return m.historial().getLast().id();throw new IllegalStateException("El resultado de metas no identifica su hecho conservado.");}
    private Optional<Resultado> previo(String tipo,Object d,String key,Actor a){var k=Idempotencia.exigirClave(key);bloquear("COMANDO_META|"+a.idOrganizacion()+"|"+k);return comandos.buscar(a,k,tipo,Idempotencia.huellaExacta(json.objetoCanonico(d))).map(c->recuperar(c.getResultado(),a));}
    private Resultado guardar(String tipo,Object d,String key,Resultado result,Actor a,Procedencia proc){
        var roles=result.metas().stream().map(MetaDeAgente::idRolAgente).distinct().sorted().toList();var rec=new Recibo(roles,result);contexto.exigirOperador(a);if(!alcances.de(a).rolesAgente().containsAll(roles))throw new AccesoNoAutorizadoException();
        comandos.registrar(a,Idempotencia.exigirClave(key),tipo,Idempotencia.huellaExacta(json.objetoCanonico(d)),result.metas().getFirst().idMeta()==null?"PROPUESTA_META":"META",referenciaRecibo(result.metas().getFirst()),Procedencia.oPantalla(proc),json.objetoCanonico(rec));return result;
    }
    private Resultado recuperar(String contenido,Actor a){var h=json.comoMapa(contenido);var ids=(List<?>)h.get("responsables");if(ids==null||!alcance(a).containsAll(ids.stream().map(x->Long.valueOf(x.toString())).toList()))throw new AccesoNoAutorizadoException();return json.comoObjeto(contenido,Recibo.class).resultado();}
    @Override @Transactional(readOnly=true) public Optional<Resultado> resultado(String capacidad,String key,Actor a){alcance(a);if(!Set.of(FIJAR,DECIDIR,PROPONER).contains(capacidad))throw new IllegalArgumentException("Capacidad de metas desconocida.");return comandos.consultarResultado(a,Idempotencia.exigirClave(key),capacidad).map(c->recuperar(c.getResultado(),a));}
}
