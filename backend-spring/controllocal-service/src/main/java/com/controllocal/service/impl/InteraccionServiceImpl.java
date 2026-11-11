package com.controllocal.service.impl;

import com.controllocal.domain.comercial.InteraccionComercial;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

/** RF041/C4: un hecho comunicativo, versiones privadas y permiso por cada vínculo/campo. */
@Service
public class InteraccionServiceImpl implements InteraccionService {
    public record Contenido(DatosInteraccion datos,List<HechoPosterior> hechosPosteriores,String agenteNombre,List<SeguimientoDeProspeccion.HechoR27> seguimiento,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) DatoContactoProspeccion contactoProspeccion,
                            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) DatoAtencionConsultas atencionConsultas) {
        public Contenido(DatosInteraccion datos,List<HechoPosterior> hechosPosteriores,String agenteNombre,List<SeguimientoDeProspeccion.HechoR27> seguimiento){this(datos,hechosPosteriores,agenteNombre,seguimiento,null,null);}
        public Contenido(DatosInteraccion datos,List<HechoPosterior> hechosPosteriores,String agenteNombre,List<SeguimientoDeProspeccion.HechoR27> seguimiento,DatoContactoProspeccion contacto){this(datos,hechosPosteriores,agenteNombre,seguimiento,contacto,null);}
        public Contenido {hechosPosteriores=hechosPosteriores==null?List.of():List.copyOf(hechosPosteriores);seguimiento=seguimiento==null?List.of():List.copyOf(seguimiento);}
    }
    public record Snapshot(FichaInteraccion ficha,Autorizacion personasOriginalesPorCampo) { }
    public record EventosCapturados(List<EventoDeclarado> eventos,DatoContactoProspeccion contactoProspeccion,DatoAtencionConsultas atencionConsultas,DatoProximaActuacion proximaActuacion) { }
    private record Entrada(InteraccionComercialRepository.Cabecera cabecera,Autorizacion permiso,List<VinculoFicha> vinculos) { }
    private record Poblacion(Map<Long,Entrada> permitidas,Map<Long,Autorizacion> candidatas,Map<Long,Long> versionesCandidatas) { }
    private final InteraccionComercialRepository interacciones;
    private final HistoriaInteraccionRepository historia;
    private final VinculosInteraccionRepository vinculos;
    private final LecturasInteraccionRepository lecturas;
    private final DetalleAgenteRepository agentes;
    private final PersonaRolRepository roles;
    private final ContextosDeActividad contexto;
    private final ContextosDeInteraccion acceso;
    private final SeguimientoDeProspeccion seguimiento;
    private final RecibosDeActividad recibos;
    private final Alcances alcances;
    private final Documentos documentos;
    private final EntityManager entidades;
    private final PlanDeConsulta plan;
    public InteraccionServiceImpl(InteraccionComercialRepository interacciones,HistoriaInteraccionRepository historia,
            VinculosInteraccionRepository vinculos,LecturasInteraccionRepository lecturas,DetalleAgenteRepository agentes,
            PersonaRolRepository roles,ContextosDeActividad contexto,ContextosDeInteraccion acceso,
            SeguimientoDeProspeccion seguimiento,RecibosDeActividad recibos,Alcances alcances,Documentos documentos,
            EntityManager entidades,PlanDeConsulta plan){
        this.interacciones=interacciones;this.historia=historia;this.vinculos=vinculos;this.lecturas=lecturas;
        this.agentes=agentes;this.roles=roles;this.contexto=contexto;this.acceso=acceso;this.seguimiento=seguimiento;
        this.recibos=recibos;this.alcances=alcances;this.documentos=documentos;this.entidades=entidades;this.plan=plan;
    }
    @Override @Transactional(readOnly=true)
    public CatalogoInteracciones.Definicion definicion(Actor actor){contexto.exigirMetadata(actor);return CatalogoInteracciones.definicion();}
    @Override @Transactional(readOnly=true)
    public void verificarContextoRegistro(String tipo,long id,Actor actor){
        contexto.exigirAgente(actor);TipoContexto t;
        try{t=TipoContexto.valueOf(Objects.requireNonNull(tipo).toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException|NullPointerException e){throw new ReglaNegocioException("Contexto de interacción no admitido.");}
        if(t==TipoContexto.CONTACTO)throw new ReglaNegocioException("El contacto requiere una referencia contextual autorizada.");
        validarRegistro(new DatosInteraccion(List.of(new VinculoActividad(t,id,null,null)),null,null,null,null,null,null,null,null,null,null,null,null,null,null),actor);
    }
    @Override @Transactional(readOnly=true)
    public DatosInteraccion validarRegistro(DatosInteraccion d,Actor actor){
        contexto.exigirAgente(actor);validarDatos(d);var n=destinoDeclarado(acceso.normalizar(d,actor,null,false),actor,false);
        exigirAntecedentes(n.autorizacion().antecedentes(),n.autorizacion().consultasOriginales(),null,actor);validarRespuesta(n.datos(),actor);validarConsultas(n,actor);return n.datos();
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaInteraccion registrar(DatosInteraccion d,Actor actor,ContextoSesion sid,Procedencia proc){
        contexto.iniciar(actor,sid);exigirProcedencia(proc);validarRegistro(d,actor);
        var n=destinoDeclarado(acceso.normalizar(d,actor,proc,true),actor,true);var i=new InteraccionComercial();i.setOrganizacionId(actor.idOrganizacion());
        i.setAgente(agentes.getReferenceById(actor.idRolOperativo()));i.setIdAutor(actor.idRolOperativo());
        i.setRegistradoEn(contexto.ahora());i.setVersionDiccionario(CatalogoInteracciones.VERSION);
        i.setProcedenciaJson(documentos.objetoCanonico(proc));
        guardarContenido(i,n,new Contenido(n.datos(),List.of(),roles.nombreDelTitular(actor.idRolOperativo(),actor.idOrganizacion()).orElse(null),seguimiento.proyectar(n,actor)));
        interacciones.saveAndFlush(i);sincronizarVinculos(i,n.datos());
        var f=ficha(i,n.vinculos());anexar(i,f,n.autorizacion(),actor,"REGISTRAR",null,proc);
        seguimiento.actualizarDesde(prospecciones(n.datos()),actor); // El único hecho AUTO y su recibo los escribe ComandosAutomaticos.
        contexto.exigirAgente(actor);acceso.verificar(n.autorizacion(),actor,true);exigirAntecedentes(n.autorizacion().antecedentes(),n.autorizacion().consultasOriginales(),i.getId(),actor);return f;
    }
    @Override @Transactional(readOnly=true)
    public Rectificacion validarRectificacion(Rectificacion r,Actor actor){
        contexto.exigirAgente(actor);if(r==null||r.idInteraccion()<=0||r.versionEsperada()==null||r.versionEsperada()<0||vacio(r.motivo()))throw new ReglaNegocioException("La rectificación conserva identidad, versión explícita y motivo.");
        var f=obtener(r.idInteraccion(),actor);if(f.idAgente()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(f.version()!=r.versionEsperada())throw new ConflictoException("Cambió la interacción; revisa la versión actual.");
        validarRegistro(r.correccion(),actor);exigirAntecedentes(AtencionDeConsultas.antecedentes(r.correccion().interaccionAnterior(),AtencionDeConsultas.consultasReferidas(r.correccion())),AtencionDeConsultas.consultasReferidas(r.correccion()),r.idInteraccion(),actor);
        return r;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaRectificacion(Rectificacion r,Actor actor){
        if(r==null||r.versionEsperada()==null)throw new ReglaNegocioException("Identifica la rectificación y su versión explícita.");
        obtenerVersion(r.idInteraccion(),r.versionEsperada(),actor);validarDatos(r.correccion());
        destinoDeclarado(acceso.normalizar(r.correccion(),actor,null,false),actor,false);exigirAntecedentes(AtencionDeConsultas.antecedentes(r.correccion().interaccionAnterior(),AtencionDeConsultas.consultasReferidas(r.correccion())),AtencionDeConsultas.consultasReferidas(r.correccion()),r.idInteraccion(),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaInteraccion rectificar(Rectificacion r,Actor actor,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(actor,sid);exigirProcedencia(proc);
        var anterior=recibos.buscarOriginal(actor,clave,RecibosDeActividad.RECTIFICAR,r,proc);
        if(anterior.isPresent()){var original=recibos.leer(anterior.get(),FichaInteraccion.class);obtenerVersion(original.id(),original.version(),actor);return original;}
        // iniciar ya conserva el bloqueo de cuenta del autor. La comunicación sólo
        // es modificable por ese autor; no se añade otro bloqueo organizacional.
        validarRectificacion(r,actor);var i=bloquear(r.idInteraccion(),r.versionEsperada(),actor);
        var previo=contenido(i);var n=destinoDeclarado(acceso.normalizar(r.correccion(),actor,proc,true),actor,true);
        validarConsultas(n,actor);
        if(documentos.objetoCanonico(previo.datos()).equals(documentos.objetoCanonico(n.datos())))throw new ReglaNegocioException("La corrección no cambia los datos; un hecho posterior se anota por separado.");
        guardarContenido(i,n,new Contenido(n.datos(),previo.hechosPosteriores(),previo.agenteNombre(),seguimiento.proyectar(n,actor)));interacciones.flush();sincronizarVinculos(i,n.datos());
        var f=ficha(i,n.vinculos());anexar(i,f,n.autorizacion(),actor,"RECTIFICAR",r.motivo(),proc);
        var afectadas=prospecciones(previo.datos());afectadas.addAll(prospecciones(n.datos()));seguimiento.actualizarDesde(afectadas,actor);
        recibos.registrar(actor,clave,RecibosDeActividad.RECTIFICAR,r,proc,"INTERACCION",i.getId(),f);
        acceso.verificar(n.autorizacion(),actor,true);exigirAntecedentes(n.autorizacion().antecedentes(),n.autorizacion().consultasOriginales(),i.getId(),actor);return f;
    }
    @Override @Transactional(readOnly=true)
    public Optional<FichaInteraccion> consultarResultadoRectificacion(String clave,Actor actor){
        contexto.exigirOperador(actor);return recibos.consultar(actor,clave,RecibosDeActividad.RECTIFICAR).map(json->{
            var original=recibos.leer(json,FichaInteraccion.class);obtenerVersion(original.id(),original.version(),actor);return original;});
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaInteraccion vincular(long id,AdicionVinculo d,Actor actor,ContextoSesion sid,Procedencia proc){
        contexto.iniciar(actor,sid);exigirProcedencia(proc);
        if(d==null||d.vinculo()==null||vacio(d.motivo()))throw new ReglaNegocioException("Identifica el vínculo y su motivo.");
        var i=bloquear(id,d.versionEsperada(),actor);var c=contenido(i);var anteriores=new ArrayList<>(lista(c.datos().vinculos()));
        if(anteriores.contains(d.vinculo()))throw new ReglaNegocioException("La comunicación ya conserva ese vínculo.");
        anteriores.add(d.vinculo());var nuevos=copiar(c.datos(),anteriores);validarRegistro(nuevos,actor);
        var n=destinoDeclarado(acceso.normalizar(nuevos,actor,proc,true),actor,true);guardarContenido(i,n,new Contenido(n.datos(),c.hechosPosteriores(),c.agenteNombre(),seguimiento.proyectar(n,actor)));
        interacciones.flush();sincronizarVinculos(i,n.datos());var f=ficha(i,n.vinculos());
        anexar(i,f,n.autorizacion(),actor,"VINCULAR",d.motivo(),proc);seguimiento.actualizarDesde(prospecciones(n.datos()),actor);return f;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaInteraccion observar(long id,ObservacionPosterior o,Actor actor,ContextoSesion sid,Procedencia proc){
        contexto.iniciar(actor,sid);exigirProcedencia(proc);
        if(o==null||vacio(o.detalle()))throw new ReglaNegocioException("Conserva el conocimiento posterior y su fuente.");
        var i=bloquear(id,o.versionEsperada(),actor);var c=contenido(i);var a=autorizacionActual(i);
        var hechos=new ArrayList<>(c.hechosPosteriores());hechos.add(new HechoPosterior(i.getVersion()+1,contexto.ahora(),o,proc));
        i.setContextoDeclaradoJson(documentos.objetoCanonico(new Contenido(c.datos(),hechos,c.agenteNombre(),c.seguimiento(),c.contactoProspeccion(),c.atencionConsultas())));interacciones.flush();
        var f=ficha(i,acceso.verificar(a,actor,true));anexar(i,f,a,actor,"OBSERVAR",o.detalle(),proc);
        return f; // No reemplaza el envío ni el resultado original, ni suma otra comunicación.
    }
    @Override @Transactional(readOnly=true)
    public Pagina<FichaInteraccion> listar(FiltrosInteraccion f,Actor actor){
        contexto.exigirOperador(actor);if(f==null)throw new ReglaNegocioException("Identifica los filtros de consulta.");
        if(f.referencia()!=null&&(f.contexto()==null||f.referencia()<=0))throw new ReglaNegocioException("El filtro requiere contexto y referencia válidos.");
        // El conjunto por organización/roles también necesita su cardinalidad real.
        // Aplicar la política local ya existente antes de ambas lecturas de cabecera.
        if(!vacio(f.q()))plan.forzarPersonalizado();
        var p=poblacion(actor).permitidas();var ids=p.entrySet().stream().filter(e->f.contexto()==null||e.getValue().vinculos().stream()
                .anyMatch(v->v.tipo().equals(f.contexto().name())&&(f.referencia()==null||v.referencia()==f.referencia()))).map(Map.Entry::getKey).toList();
        var pagina=lecturas.buscar(actor.idOrganizacion(),ids,nombre(f.tipo()),nombre(f.resultado()),nombre(f.canal()),f.q(),Math.max(1,f.pagina()),Math.max(1,Math.min(100,f.tamano())));
        var fichas=cargar(pagina.ids(),p,actor);revalidar(p,actor);return new Pagina<>(fichas,pagina.total());
    }
    @Override @Transactional(readOnly=true)
    public FichaInteraccion obtener(long id,Actor actor){
        var e=exigirCabecera(id,actor);exigirAntecedentes(e.permiso().antecedentes(),e.permiso().consultasOriginales(),id,actor);
        var i=interacciones.actual(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Interacción"));
        exigirVersionEstable(i,e.cabecera().getVersion());var f=ficha(i,e.vinculos());
        acceso.verificar(e.permiso(),actor,true);return f;
    }
    @Override @Transactional(readOnly=true)
    public FichaInteraccion obtenerVersion(long id,long version,Actor actor){
        contexto.exigirOperador(actor);if(id<=0||version<0)throw new ReglaNegocioException("Identifica la versión original.");
        var meta=historia.acceso(actor.idOrganizacion(),id,version).orElseThrow(()->new NoEncontradoException("Versión de interacción"));
        var a=documentos.comoObjeto(meta,Autorizacion.class);acceso.verificar(a,actor,true);exigirAntecedentes(a.antecedentes(),a.consultasOriginales(),id,actor);
        var h=historia.version(actor.idOrganizacion(),id,version).orElseThrow(()->new NoEncontradoException("Versión de interacción"));
        var s=documentos.comoObjeto(h.snapshotJson(),Snapshot.class);
        if(s.ficha().id()!=id||s.ficha().version()!=version||!a.equals(s.personasOriginalesPorCampo()))throw new IllegalStateException("La historia no corresponde a la identidad autorizada.");
        acceso.verificar(a,actor,true);return s.ficha();
    }
    @Override @Transactional(readOnly=true)
    public List<Antecedente> historia(long id,Actor actor){
        contexto.exigirOperador(actor);var salida=new ArrayList<Antecedente>();
        for(var m:historia.metadatos(actor.idOrganizacion(),id)){
            try{var f=obtenerVersion(id,m.version(),actor);var h=historia.version(actor.idOrganizacion(),id,m.version()).orElseThrow();
                salida.add(new Antecedente(h.version(),h.registradoEn(),h.operacion(),h.motivo(),f));}
            catch(NoEncontradoException|AccesoNoAutorizadoException denegada){/* Omitir antes de proyectar ese antecedente completo. */}
        }
        if(salida.isEmpty())obtener(id,actor); // No convertir una denegación de historial en éxito vacío.
        return List.copyOf(salida);
    }
    @Override @Transactional(readOnly=true)
    public List<FichaInteraccion> porOportunidad(long id,Instant corte,Actor actor){
        return porVinculo("OPORTUNIDAD",Set.of(id),corte,actor);
    }
    @Override @Transactional(readOnly=true)
    public List<FichaInteraccion> porPropiedad(long id,Actor actor,FinalidadLectura finalidad){exigirFinalidad(finalidad);return porVinculo("PROPIEDAD",Set.of(id),null,actor);}
    @Override @Transactional(readOnly=true)
    public List<FichaInteraccion> porCliente(long id,Actor actor,FinalidadLectura finalidad){exigirFinalidad(finalidad);return porVinculo("CLIENTE",Set.of(id),null,actor);}
    @Override @Transactional(readOnly=true)
    public LecturaIndicadores indicadores(Actor actor){
        var seleccion=poblacion(actor);var p=seleccion.permitidas();var salida=new ArrayList<MedicionInteraccion>();
        for(var m:lecturas.mediciones(actor.idOrganizacion(),p.keySet())){
            var e=p.get(m.id());if(e==null||e.cabecera().getVersion()!=m.version())throw new ConflictoException("Cambió la población de actividad; actualiza la consulta.");
            var datos=documentos.comoObjeto(m.eventos(),EventosCapturados.class);var eventos=datos.eventos();
            salida.add(new MedicionInteraccion(m.id(),m.version(),m.agente(),m.fecha(),m.registrado(),
                    m.tipo()==null?null:TipoActividad.valueOf(m.tipo()),m.resultado()==null?null:ResultadoInteraccion.valueOf(m.resultado()),lista(eventos),e.vinculos(),
                    datos.contactoProspeccion(),datos.atencionConsultas(),datos.proximaActuacion()));
        }
        var actual=revalidar(p,actor);
        if(!actual.versionesCandidatas().equals(seleccion.versionesCandidatas()))throw new ConflictoException("Cambiaron las fuentes de actividad durante la medición.");
        var consultas=salida.stream().filter(m->m.atencionConsultas()!=null&&m.atencionConsultas().consultaRecibida()).map(MedicionInteraccion::id).collect(java.util.stream.Collectors.toSet());
        // Esto es una condición de autorización del cálculo, no un conteo ni una
        // proyección de comunicaciones excluidas. Sus datos nunca salen de Core.
        var noVerificables=new LinkedHashSet<Long>();
        seleccion.candidatas().entrySet().stream().filter(e->!p.containsKey(e.getKey()))
                .forEach(e->lista(e.getValue().consultasOriginales()).stream().filter(r->consultas.contains(r.id())).forEach(r->noVerificables.add(r.id())));
        return new LecturaIndicadores(salida,noVerificables);
    }
    @Override @Transactional(readOnly=true)
    public ConteoReporte contarParaReporte(long id,LocalDate desde,LocalDate hasta,Actor actor){
        if(desde==null||hasta==null||hasta.isBefore(desde))throw new ReglaNegocioException("El reporte requiere un periodo válido.");
        var referencias=indicadores(actor).registros().stream().filter(m->m.fechaHecho()!=null&&!m.fechaHecho().isBefore(desde)&&!m.fechaHecho().isAfter(hasta))
                .filter(m->m.vinculos().stream().anyMatch(v->"ENCARGO".equals(v.tipo())&&v.referencia()==id))
                .map(m->new ReferenciaVersion(m.id(),m.version())).distinct().toList();
        return new ConteoReporte(referencias.size(),referencias);
    }
    @Override @Transactional(readOnly=true)
    public void exigirVersiones(List<ReferenciaVersion> refs,Actor actor,FinalidadLectura finalidad){
        exigirFinalidad(finalidad);contexto.exigirOperador(actor);
        if(refs==null)throw new AccesoNoAutorizadoException();
        for(var r:refs){if(r==null)throw new AccesoNoAutorizadoException();obtenerVersion(r.id(),r.version(),actor);}
    }
    private List<FichaInteraccion> porVinculo(String tipo,Set<Long> ids,Instant corte,Actor actor){
        if(ids==null||ids.stream().anyMatch(i->i==null||i<=0))throw new ReglaNegocioException("Referencia de actividad inválida.");
        var p=poblacion(actor).permitidas();var seleccion=p.entrySet().stream().filter(e->e.getValue().vinculos().stream().anyMatch(v->tipo.equals(v.tipo())&&ids.contains(v.referencia()))).map(Map.Entry::getKey).toList();
        var fichas=cargar(seleccion,p,actor).stream().filter(f->corte==null||f.registradoEn()!=null&&!f.registradoEn().isAfter(corte)).toList();
        revalidar(p,actor);return fichas;
    }
    private Poblacion poblacion(Actor actor){
        contexto.exigirOperador(actor);var alcance=alcances.de(actor);if(alcance.vacio())return new Poblacion(Map.of(),Map.of(),Map.of());
        var cabeceras=lecturas.cabeceras(actor.idOrganizacion(),alcance.rolesAgente());var originales=new LinkedHashMap<Long,Autorizacion>();
        // Se comparte sólo la decodificación del mismo documento inmutable dentro
        // de esta lectura. Los legados dependen de sus propias columnas; la segunda
        // población vuelve a consultar y autorizar todo con los permisos actuales.
        var decodificadas=new HashMap<String,Autorizacion>();
        for(var c:cabeceras){var json=c.getAutorizacion();originales.put(c.getId(),json==null?metadata(c):decodificadas.computeIfAbsent(json,k->metadata(c)));}
        var permitidas=acceso.verificarLote(List.copyOf(new LinkedHashSet<>(originales.values())),actor,true);var resultado=new LinkedHashMap<Long,Entrada>();
        for(var c:cabeceras){var a=originales.get(c.getId());if(permitidas.containsKey(a))resultado.put(c.getId(),new Entrada(c,a,permitidas.get(a)));}
        var consultas=resultado.values().stream().flatMap(e->lista(e.permiso().consultasOriginales()).stream()).distinct().toList();
        var historias=consultas.isEmpty()?Set.<ReferenciaVersion>of():acceso.versionesPermitidas(consultas,actor,null);
        boolean retiro;do{
            retiro=false;
            for(var it=resultado.entrySet().iterator();it.hasNext();){var e=it.next();
                if(!dependenciasPermitidas(e.getValue().permiso(),resultado.keySet(),historias)){it.remove();retiro=true;}}
        }while(retiro);
        var versiones=new LinkedHashMap<Long,Long>();cabeceras.forEach(c->versiones.put(c.getId(),c.getVersion()));
        return new Poblacion(resultado,originales,versiones);
    }
    private static boolean dependenciasPermitidas(Autorizacion a,Set<Long> actuales,Set<ReferenciaVersion> historicas){
        return actuales.containsAll(a.antecedentes())&&historicas.containsAll(lista(a.consultasOriginales()));
    }
    private List<FichaInteraccion> cargar(Collection<Long> ids,Map<Long,Entrada> p,Actor actor){
        if(ids.isEmpty())return List.of();var cargadas=new HashMap<Long,InteraccionComercial>();
        interacciones.actuales(actor.idOrganizacion(),ids).forEach(i->cargadas.put(i.getId(),i));var resultado=new ArrayList<FichaInteraccion>();
        for(var id:ids){var e=p.get(id);var i=cargadas.get(id);if(e==null||i==null)throw new ConflictoException("Cambió la actividad; actualiza la consulta.");
            exigirVersionEstable(i,e.cabecera().getVersion());resultado.add(ficha(i,e.vinculos()));}
        return List.copyOf(resultado);
    }
    private Poblacion revalidar(Map<Long,Entrada> original,Actor actor){
        var seleccion=poblacion(actor);var actual=seleccion.permitidas();if(!actual.keySet().equals(original.keySet()))throw new ConflictoException("Cambió la población autorizada de actividad; actualiza la consulta.");
        for(var e:original.entrySet()){
            var n=actual.get(e.getKey());if(n==null||n.cabecera().getVersion()!=e.getValue().cabecera().getVersion())
                throw new ConflictoException("Cambió el alcance o la actividad; actualiza la consulta.");
        }
        return seleccion;
    }
    private Entrada exigirCabecera(long id,Actor actor){
        contexto.exigirOperador(actor);var a=alcances.de(actor);var c=interacciones.cabecera(actor.idOrganizacion(),id,a.paramRoles()).orElseThrow(()->new NoEncontradoException("Interacción"));
        var m=metadata(c);return new Entrada(c,m,acceso.verificar(m,actor,true));
    }
    private void exigirAntecedentes(Collection<Long> ids,List<ReferenciaVersion> consultas,Long actual,Actor actor){
        var fuentes=new LinkedHashSet<>(lista(consultas));recorrerAntecedentes(ids,actual,actor,new HashSet<>(),new HashSet<>(),fuentes);
        if(!fuentes.isEmpty()&&!acceso.versionesPermitidas(fuentes,actor,actual).containsAll(fuentes))throw new NoEncontradoException("Antecedentes autorizados de consulta");
    }
    private void recorrerAntecedentes(Collection<Long> ids,Long actual,Actor actor,Set<Long> enCurso,Set<Long> comprobados,Set<ReferenciaVersion> fuentes){
        for(var id:ids){
            if(id<=0||Objects.equals(id,actual)||enCurso.contains(id))throw new ReglaNegocioException("Los antecedentes no pueden formar un ciclo de comunicaciones.");
            if(comprobados.contains(id))continue;enCurso.add(id);var e=exigirCabecera(id,actor);
            fuentes.addAll(lista(e.permiso().consultasOriginales()));recorrerAntecedentes(e.permiso().antecedentes(),actual,actor,enCurso,comprobados,fuentes);enCurso.remove(id);comprobados.add(id);
        }
    }
    private InteraccionComercial bloquear(long id,Long version,Actor actor){
        if(id<=0||version==null||version<0)throw new ReglaNegocioException("Identifica la interacción y su versión explícita.");
        var e=exigirCabecera(id,actor);if(e.cabecera().getAgente()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(version<0||e.cabecera().getVersion()!=version)throw new ConflictoException("Cambió la interacción; revisa la versión actual.");
        var i=interacciones.bloquear(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Interacción"));
        entidades.refresh(i,LockModeType.PESSIMISTIC_WRITE);exigirVersionEstable(i,version);acceso.verificar(metadata(e.cabecera()),actor,true);exigirAntecedentes(e.permiso().antecedentes(),e.permiso().consultasOriginales(),id,actor);return i;
    }
    private Autorizacion metadata(InteraccionComercialRepository.Cabecera c){
        return ContextosDeInteraccion.metadatos(c,documentos);
    }
    private Autorizacion autorizacionActual(InteraccionComercial i){
        if(i.getAutorizacionJson()==null)throw new ReglaNegocioException("Rectifica los datos legados antes de añadir conocimiento contextual.");
        return documentos.comoObjeto(i.getAutorizacionJson(),Autorizacion.class);
    }
    private Contenido contenido(InteraccionComercial i){
        if(i.getVersionDiccionario()!=null)return documentos.comoObjeto(i.getContextoDeclaradoJson(),Contenido.class);
        var interlocutor=i.getContactoTipo()==null&&i.getContactoNombre()==null&&i.getContactoTelefono()==null?null:
                new ParticipanteDeclarado(null,i.getContactoTipo(),i.getContactoNombre(),i.getContactoTelefono(),null);
        var links=new ArrayList<VinculoActividad>();
        if(i.getOportunidad()!=null)links.add(new VinculoActividad(TipoContexto.OPORTUNIDAD,i.getOportunidad().getId(),null,null));
        if(i.getProspeccion()!=null)links.add(new VinculoActividad(TipoContexto.PROSPECCION,i.getProspeccion().getId(),null,null));
        if(i.getCaptacion()!=null)links.add(new VinculoActividad(TipoContexto.ENCARGO,i.getCaptacion().getId(),null,null));
        if(i.getCliente()!=null)links.add(new VinculoActividad(TipoContexto.CLIENTE,i.getCliente().getId(),null,null));
        return new Contenido(new DatosInteraccion(links,null,interlocutor,null,null,null,List.of(),null,
                i.getFechaHecho()==null?null:new MomentoHecho(i.getFechaHecho(),null,null),i.getObservaciones(),List.of(),null,null,null,null),List.of(),null,List.of());
    }
    private FichaInteraccion ficha(InteraccionComercial i,List<VinculoFicha> padres){
        var c=contenido(i);var d=c.datos();return new FichaInteraccion(i.getId(),i.getVersion(),i.getAgente().getId(),i.getFechaHecho(),i.getRegistradoEn(),
                d,padres,i.getVersionDiccionario()==null?"LEGADO":i.getVersionDiccionario(),
                i.getProcedenciaJson()==null?null:documentos.comoObjeto(i.getProcedenciaJson(),Procedencia.class),
                i.getResultado(),i.getCanalContacto(),c.agenteNombre(),c.hechosPosteriores(),CatalogoInteracciones.rotulo(d.resultado(),eventos(d)),
                i.getContexto()!=null||i.getTranscripcionNota()!=null||i.getFechaHora()!=null?new DatosLegados(i.getContexto(),i.getTranscripcionNota(),i.getFechaHora()):null,c.contactoProspeccion());
    }
    private void guardarContenido(InteraccionComercial i,Normalizado n,Contenido c){
        var medicion=ContactoEfectivoProspeccion.calificar(n);
        medicion=new DatoContactoProspeccion(medicion.estado(),medicion.causa(),medicion.intentoSaliente(),medicion.respondeAIntento(),i.getId()==null?0:i.getVersion()+1);
        c=new Contenido(c.datos(),c.hechosPosteriores(),c.agenteNombre(),c.seguimiento(),medicion,AtencionDeConsultas.proyectar(n.datos(),i.getId()==null?0:i.getVersion()+1));
        i.setVersionDiccionario(CatalogoInteracciones.VERSION);i.setContextoDeclaradoJson(documentos.objetoCanonico(c));
        i.setAutorizacionJson(documentos.objetoCanonico(n.autorizacion()));i.setFechaHecho(MomentosDeComunicacion.efectiva(n.datos()));
    }
    private void sincronizarVinculos(InteraccionComercial i,DatosInteraccion d){
        var valores=lista(d.vinculos()).stream().filter(v->v.referencia()!=null).map(v->new VinculosInteraccionRepository.Vinculo(v.tipo().name(),v.referencia(),v.versionEsperada())).distinct().toList();
        vinculos.sincronizar(i.getOrganizacionId(),i.getId(),valores,contexto.ahora());
    }
    private void anexar(InteraccionComercial i,FichaInteraccion f,Autorizacion a,Actor actor,String operacion,String motivo,Procedencia proc){
        historia.anexar(actor.idOrganizacion(),i.getId(),i.getVersion(),actor.idRolOperativo(),contexto.ahora(),operacion,motivo,
                documentos.objetoCanonico(new Snapshot(f,a)),documentos.objetoCanonico(proc));
    }
    private static void validarDatos(DatosInteraccion d){
        if(d==null)throw new ReglaNegocioException("Conserva los datos conocidos de la comunicación real.");
        CatalogoInteracciones.validar(d.tipo(),d.canal(),d.medioReal(),eventos(d),d.resultado());
        ContactoEfectivoProspeccion.validar(d);
        AtencionDeConsultas.validar(d);
        if(d.proxima()!=null&&d.proxima().origen()==null)throw new ReglaNegocioException("Distingue acuerdo externo, intención profesional o sugerencia.");
        for(var x:lista(d.declaraciones())){
            if(x==null)throw new ReglaNegocioException("Declaración inválida.");var valor=x.valorDeclarado();
            if(valor!=null&&valor.moneda()!=null&&x.moneda()!=null&&!valor.moneda().trim().equalsIgnoreCase(x.moneda().trim()))
                throw new ReglaNegocioException("Las monedas declaradas son contradictorias; conserva la precisión de cada importe.");
            if(x.confirmacion()!=null&&x.confirmacion().estado()!=null&&!"POR_CONFIRMAR".equals(x.confirmacion().estado()))
                throw new ReglaNegocioException("Registrar una declaración no confirma por sí solo el dato del inmueble o del pacto.");
        }
    }
    private static Set<Long> prospecciones(DatosInteraccion d){var ids=new LinkedHashSet<Long>();lista(d.vinculos()).stream().filter(v->v.tipo()==TipoContexto.PROSPECCION&&v.referencia()!=null).forEach(v->ids.add(v.referencia()));return ids;}
    private static Set<EventoComunicacion> eventos(DatosInteraccion d){
        var eventos=EnumSet.noneOf(EventoComunicacion.class);for(var e:lista(d.eventos())){
            if(e==null||e.evento()==null)throw new ReglaNegocioException("Identifica el evento conocido; no se inventa por el canal.");eventos.add(e.evento());}
        return eventos;
    }
    private void validarRespuesta(DatosInteraccion d,Actor actor){
        var g=d.gestionContacto();if(g==null||g.respondeAIntento()==null)return;var ref=g.respondeAIntento();
        if(!Objects.equals(d.interaccionAnterior(),ref.id()))throw new ReglaNegocioException("La respuesta identifica el mismo antecedente autorizado y su versión.");
        var anterior=obtener(ref.id(),actor);
        long revision=anterior.contactoProspeccion()!=null&&anterior.contactoProspeccion().versionHecho()!=null?anterior.contactoProspeccion().versionHecho():anterior.version();
        if(ref.version()<revision||ref.version()>anterior.version())throw new ConflictoException("Cambió el intento al que responde; revisa su versión.");
        var a=obtenerVersion(ref.id(),ref.version(),actor).datos();if(a==null)throw new ReglaNegocioException("El antecedente no acredita un intento comercial evaluable.");
        boolean saliente=lista(a.eventos()).stream().anyMatch(e->e.evento()==EventoComunicacion.LLAMADA_SALIENTE||e.evento()==EventoComunicacion.MENSAJE_ENVIADO);
        var contextosPrevios=lista(a.vinculos()).stream().filter(v->v.tipo()!=TipoContexto.CONTACTO).toList();
        var contextosActuales=lista(d.vinculos()).stream().filter(v->v.tipo()!=TipoContexto.CONTACTO).toList();
        boolean comun=contextosPrevios.isEmpty()&&contextosActuales.isEmpty()||contextosPrevios.stream().anyMatch(v->contextosActuales.stream().anyMatch(w->v.tipo()==w.tipo()&&Objects.equals(v.referencia(),w.referencia())));
        if(!saliente||!comun||!ContactoEfectivoProspeccion.comercial(a))throw new ReglaNegocioException("La respuesta requiere un intento comercial real de la misma prospección.");
        var fecha=MomentosDeComunicacion.efectiva(d);
        if(fecha!=null&&anterior.fechaHecho()!=null&&fecha.isBefore(anterior.fechaHecho()))throw new ReglaNegocioException("La respuesta no puede preceder al intento declarado.");
        var inicio=instanteComunicacion(a,true);var respuesta=instanteComunicacion(d,false);
        if(inicio!=null&&respuesta!=null&&respuesta.isBefore(inicio))throw new ReglaNegocioException("El instante conocido de respuesta precede al intento.");
    }
    private void validarConsultas(Normalizado n,Actor actor){
        var d=n.datos();var a=d.atencionConsultas();if(a==null)return;
        var proyectada=AtencionDeConsultas.proyectar(d,0);
        for(int k=0;k<a.respuestas().size();k++){
            var r=a.respuestas().get(k);if(r.consulta()==null)continue;var ref=r.consulta();
            var actual=obtener(ref.id(),actor);var original=obtenerVersion(ref.id(),ref.version(),actor);
            if(actual.datos()==null||original.datos()==null||original.datos().atencionConsultas()==null||original.datos().atencionConsultas().recibida()==null)
                throw new ReglaNegocioException("La referencia no acredita una consulta comercial recibida; no se interpreta por su canal o resultado.");
            if(ref.version()>actual.version()||!actual.datos().equals(original.datos()))throw new ConflictoException("Cambió la comunicación de consulta; revisa su versión antes de atribuir la respuesta.");
            boolean comun=n.vinculos().stream().anyMatch(v->original.vinculos().stream().anyMatch(p->v.tipo().equals(p.tipo())&&v.referencia()==p.referencia()));
            if(!comun)throw new ReglaNegocioException("La respuesta debe conservar un contexto autorizado común con la consulta.");
            var q=AtencionDeConsultas.proyectar(original.datos(),ref.version());
            AtencionDeConsultas.exigirCronologia(q.momentoConsulta(),proyectada.respuestas().get(k).momento());
        }
    }
    private static Instant instanteComunicacion(DatosInteraccion d,boolean saliente){
        return lista(d.eventos()).stream().filter(e->saliente?e.evento()==EventoComunicacion.LLAMADA_SALIENTE||e.evento()==EventoComunicacion.MENSAJE_ENVIADO:e.evento()==EventoComunicacion.LLAMADA_ENTRANTE||e.evento()==EventoComunicacion.MENSAJE_RECIBIDO)
                .map(EventoDeclarado::momento).filter(Objects::nonNull).filter(m->m.fecha()!=null&&m.hora()!=null&&m.offsetConocido()!=null)
                .map(m->OffsetDateTime.of(m.fecha(),m.hora(),m.offsetConocido()).toInstant()).min(Comparator.naturalOrder()).orElse(null);
    }
    private static DatosInteraccion copiar(DatosInteraccion d,List<VinculoActividad> links){return new DatosInteraccion(links,d.destinatario(),d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),d.resumen(),d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion(),d.gestionContacto(),d.atencionConsultas());}
    private Normalizado destinoDeclarado(Normalizado n,Actor actor,boolean generar){
        var d=n.datos().destinatario();
        if(d!=null&&d.contacto()==null&&d.referenciaDeclarada()!=null&&(d.referenciaDeclarada().startsWith("R27:")||d.tipoDeclarado()==null&&d.nombre()==null&&d.telefono()==null))return seguimiento.completarDestinoDeclarado(n,actor);
        return generar?SeguimientoDeProspeccion.prepararDestinoDeclarado(n):n;
    }
    private static void exigirVersionEstable(InteraccionComercial i,long version){if(version<0||i.getVersion()!=version)throw new ConflictoException("Cambió la interacción; revisa la versión actual.");}
    private static void exigirFinalidad(FinalidadLectura f){if(f==null)throw new ReglaNegocioException("Identifica la finalidad de lectura.");}
    private static void exigirProcedencia(Procedencia p){if(p==null)throw new ReglaNegocioException("La actuación requiere procedencia.");}
    private static boolean vacio(String s){return s==null||s.isBlank();}
    private static String nombre(Enum<?> e){return e==null?null:e.name();}
    private static <T>List<T> lista(List<T> l){return l==null?List.of():l;}
}
