package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.CompromisoSeguimientoRepository.Fila;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.ProspeccionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Constancias expresas CAT20.8 sobre R27. No crea otro seguimiento ni una comunicación. */
@Service
public class CompromisosDeSeguimiento {
    public record Plan(Long versionSeguimiento,UUID compromiso,Long versionCompromiso,OrigenActuacion origen,
                       LocalDate fecha,TipoActividad actividad,String detalle,boolean nuevoAcuerdo,String evidenciaAcuerdo,String motivoCambio) { }
    public record Acreditacion(Long versionCompromiso,ReferenciaVersion interaccion,String constanciaContenido) { }
    public record Resolucion(Long versionCompromiso,LocalDate fecha,String motivo,String evidencia) { }
    public record ActuacionAcreditada(ReferenciaVersion interaccion,String constanciaContenido,Instant vinculadaEn) { }
    public record Contenido(long versionSeguimiento,OrigenActuacion origen,LocalDate plazo,TipoActividad actividad,String detalle,
                            List<IndicadoresIndividuales.CambioPlazo> cambios,List<ActuacionAcreditada> actuaciones,
                            LocalDate resolucion,String evidenciaResolucion,Instant resueltaEn) {
        public Contenido {cambios=List.copyOf(cambios);actuaciones=List.copyOf(actuaciones);}
    }
    public record Vista(UUID referencia,long version,long prospeccion,long seguimiento,long responsableHistorico,Contenido compromiso) { }
    private record Recibo(UUID referencia,long version,long prospeccion) { }
    private final CompromisoSeguimientoRepository repo;
    private final SeguimientoProspeccionRepository fuentes;
    private final SeguimientoDeProspeccion seguimiento;
    private final ContextosDeActividad contexto;
    private final ContextosDeInteraccion acceso;
    private final InteraccionComercialRepository comunicaciones;
    private final Alcances alcances;
    private final Documentos json;
    private final ComandoIdempotenteRepository recibos;
    private final ComandosIdempotentes comandos;
    private final EventoDominioRepository eventos;
    public CompromisosDeSeguimiento(CompromisoSeguimientoRepository r,SeguimientoProspeccionRepository f,SeguimientoDeProspeccion s,
            ContextosDeActividad c,ContextosDeInteraccion a,InteraccionComercialRepository i,Alcances al,Documentos j,
            ComandoIdempotenteRepository re,ComandosIdempotentes co,EventoDominioRepository e){
        repo=r;fuentes=f;seguimiento=s;contexto=c;acceso=a;comunicaciones=i;alcances=al;json=j;recibos=re;comandos=co;eventos=e;
    }
    @Transactional(readOnly=true)
    public List<Vista> listar(long prospeccion,Actor actor){
        padre(prospeccion,actor,false);var salida=new ArrayList<Vista>();
        // Los compromisos conservan historia aunque RE07 retire la cadencia automática R27.
        var cabeceras=repo.deProspeccion(actor.idOrganizacion(),prospeccion);
        for(var f:cabeceras){exigirOriginal(f,actor);var completa=repo.obtener(actor.idOrganizacion(),f.referencia(),false).orElseThrow(()->new NoEncontradoException("Compromiso"));version(f.version(),completa);salida.add(vista(completa));}
        for(var f:cabeceras)exigirOriginal(f,actor);
        if(!cabeceras.equals(repo.deProspeccion(actor.idOrganizacion(),prospeccion)))throw new ConflictoException("Cambiaron las constancias durante la consulta.");
        return List.copyOf(salida);
    }
    @Transactional
    public Vista planificar(long prospeccion,long idSeguimiento,Plan d,Actor actor,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(actor,sid);String tipo="COMPROMISO_R27_PLANIFICAR";String h=huella(tipo,prospeccion,idSeguimiento,d,actor,proc);
        var previo=previo(tipo,clave,h,actor);if(previo.isPresent())return recuperar(previo.get(),actor);
        var p=padre(prospeccion,actor,true);
        if(d==null||d.versionSeguimiento()==null||d.origen()==null||d.origen()==OrigenActuacion.SUGERENCIA_SISTEMA
                ||d.fecha()==null||d.fecha().isBefore(contexto.hoy())||d.actividad()==null||vacio(d.detalle()))
            throw new ReglaNegocioException("Asumir un compromiso exige fecha vigente, actividad, detalle y origen expreso.");
        var visible=seguimiento.revisar(prospeccion,actor).stream().filter(r->r.id()==idSeguimiento).findFirst().orElseThrow(()->new NoEncontradoException("Seguimiento"));
        if(visible.version()!=d.versionSeguimiento())throw new ConflictoException("Cambió el seguimiento.");
        Fila anterior=d.compromiso()==null?null:exigir(d.compromiso(),prospeccion,actor,true);
        if(anterior!=null&&(anterior.seguimiento()!=idSeguimiento||d.versionCompromiso()==null||anterior.version()!=d.versionCompromiso()))throw new ConflictoException("Cambió el compromiso.");
        if(anterior==null&&repo.tienePendiente(actor.idOrganizacion(),idSeguimiento))
            throw new ConflictoException("Reprograma el compromiso pendiente identificando su versión; no lo dupliques.");
        var cambios=new ArrayList<IndicadoresIndividuales.CambioPlazo>();Contenido antes=anterior==null?null:contenido(anterior);
        if(antes!=null){
            if(antes.resolucion()!=null||!antes.actuaciones().isEmpty())throw new ReglaNegocioException("El compromiso atendido o resuelto conserva su historia; declara uno nuevo.");
            if(antes.actividad()!=d.actividad()||antes.origen()!=d.origen()||!antes.detalle().equals(d.detalle()))
                throw new ReglaNegocioException("Reprogramar conserva lo comprometido y su origen.");
            cambios.addAll(antes.cambios());LocalDate anteriorPlazo=cambios.isEmpty()?antes.plazo():cambios.getLast().nueva();
            if(anteriorPlazo.equals(d.fecha()))return reciboSinCambio(anterior,actor,tipo,clave,h,proc);
            if(vacio(d.motivoCambio()))throw new ReglaNegocioException("Explica el motivo del cambio de fecha; no se deduce de lo que prometiste hacer.");
            if(antes.origen()==OrigenActuacion.ACUERDO_EXTERNO&&(!d.nuevoAcuerdo()||vacio(d.evidenciaAcuerdo())))
                throw new ReglaNegocioException("Cambiar una fecha acordada requiere constancia del nuevo acuerdo.");
            cambios.add(new IndicadoresIndividuales.CambioPlazo(anteriorPlazo,d.fecha(),contexto.hoy(),d.motivoCambio(),d.nuevoAcuerdo(),null,contexto.ahora(),null,d.evidenciaAcuerdo()));
        }
        var revision=seguimiento.planificar(prospeccion,idSeguimiento,new PlanificacionSeguimiento(d.versionSeguimiento(),d.origen(),d.fecha(),d.detalle()),actor,sid,proc);
        var c=new Contenido(revision.version(),d.origen(),antes==null?d.fecha():antes.plazo(),d.actividad(),d.detalle(),cambios,antes==null?List.of():antes.actuaciones(),null,null,null);
        Fila f=anterior==null?repo.crear(actor.idOrganizacion(),idSeguimiento,prospeccion,p.responsable(),json.objetoCanonico(c),contexto.ahora()):actualizar(anterior,c,actor);
        return conservar(f,actor,tipo,clave,h,proc);
    }
    @Transactional
    public Vista acreditar(long prospeccion,UUID ref,Acreditacion d,Actor actor,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(actor,sid);String tipo="COMPROMISO_R27_ACREDITAR";String h=huella(tipo,prospeccion,ref,d,actor,proc);
        var previo=previo(tipo,clave,h,actor);if(previo.isPresent())return recuperar(previo.get(),actor);
        padre(prospeccion,actor,true);var f=exigir(ref,prospeccion,actor,true);var c=contenido(f);version(d==null?null:d.versionCompromiso(),f);
        if(d.interaccion()==null||vacio(d.constanciaContenido())||c.resolucion()!=null)throw new ReglaNegocioException("Vincula la comunicación real y lo que acredita del compromiso vigente.");
        var r=d.interaccion();if(!acceso.versionesPermitidas(List.of(r),actor,null).contains(r))throw new AccesoNoAutorizadoException();
        var i=comunicaciones.actual(actor.idOrganizacion(),r.id()).orElseThrow(()->new NoEncontradoException("Interacción"));
        if(i.getVersion()!=r.version())throw new ConflictoException("Selecciona la versión vigente de la actuación.");
        var a=ContextosDeInteraccion.metadatos(comunicaciones.antecedentes(actor.idOrganizacion(),List.of(r.id())).stream().filter(x->x.getId()==r.id()).findFirst().orElseThrow(),json);
        if(acceso.verificar(a,actor,false).stream().noneMatch(v->"PROSPECCION".equals(v.tipo())&&Objects.equals(v.referencia(),prospeccion)))throw new ReglaNegocioException("La actuación debe estar vinculada expresamente a esta prospección.");
        var arbol=json.comoObjeto(i.getContextoDeclaradoJson(),com.fasterxml.jackson.databind.JsonNode.class);
        var datos=json.comoObjeto(arbol.path("datos").toString(),DatosInteraccion.class);
        var destino=fuentes.porId(actor.idOrganizacion(),f.seguimiento()).orElseThrow(()->new NoEncontradoException("Seguimiento"));
        boolean mismoDestino=false;
        for(var hecho:arbol.path("seguimiento"))if(hecho.path("prospeccion").asLong()==prospeccion
                &&(destino.destinatario().equals(hecho.path("destinatario").asText())||destino.destinatario().equals(hecho.path("sustituyeDestinatario").asText())))mismoDestino=true;
        if(!mismoDestino)throw new ReglaNegocioException("La actuación debe acreditar el destinatario de este compromiso, no otra gestión del mismo inmueble.");
        if(datos.tipo()!=c.actividad()||!acreditaHecho(datos)||MomentosDeComunicacion.efectiva(datos)==null)
            throw new ReglaNegocioException("La actuación no acredita el tipo de compromiso con fecha real.");
        if(MomentosDeComunicacion.efectiva(datos).isBefore(f.registrada().atZone(ZoneId.of("America/Lima")).toLocalDate()))throw new ReglaNegocioException("La actuación no puede preceder al compromiso asumido.");
        if(c.actuaciones().stream().anyMatch(x->x.interaccion().equals(r)))return reciboSinCambio(f,actor,tipo,clave,h,proc);
        var acts=new ArrayList<>(c.actuaciones());acts.add(new ActuacionAcreditada(r,d.constanciaContenido(),contexto.ahora()));
        var nuevo=new Contenido(c.versionSeguimiento(),c.origen(),c.plazo(),c.actividad(),c.detalle(),c.cambios(),acts,null,null,null);
        return conservar(actualizar(f,nuevo,actor),actor,tipo,clave,h,proc);
    }
    @Transactional
    public Vista resolver(long prospeccion,UUID ref,Resolucion d,Actor actor,ContextoSesion sid,String clave,Procedencia proc){
        contexto.iniciar(actor,sid);String tipo="COMPROMISO_R27_RESOLVER";String h=huella(tipo,prospeccion,ref,d,actor,proc);
        var previo=previo(tipo,clave,h,actor);if(previo.isPresent())return recuperar(previo.get(),actor);
        padre(prospeccion,actor,true);var f=exigir(ref,prospeccion,actor,true);var c=contenido(f);version(d==null?null:d.versionCompromiso(),f);
        if(d.fecha()==null||d.fecha().isAfter(contexto.hoy())||d.fecha().isBefore(f.registrada().atZone(ZoneId.of("America/Lima")).toLocalDate())||vacio(d.motivo())||vacio(d.evidencia())||c.resolucion()!=null)
            throw new ReglaNegocioException("Conserva fecha efectiva no futura, motivo y evidencia de la resolución legítima.");
        var nuevo=new Contenido(c.versionSeguimiento(),c.origen(),c.plazo(),c.actividad(),c.detalle(),c.cambios(),c.actuaciones(),d.fecha(),d.motivo()+": "+d.evidencia(),contexto.ahora());
        return conservar(actualizar(f,nuevo,actor),actor,tipo,clave,h,proc);
    }
    private Fila actualizar(Fila f,Contenido c,Actor a){if(!repo.actualizar(a.idOrganizacion(),f,json.objetoCanonico(c),contexto.ahora()))throw new ConflictoException("Cambió el compromiso.");return repo.obtener(a.idOrganizacion(),f.referencia(),false).orElseThrow();}
    private Vista conservar(Fila f,Actor a,String tipo,String clave,String h,Procedencia proc){
        exigirOriginal(f,a);
        repo.version(a.idOrganizacion(),f,a.idRolOperativo(),contexto.ahora());
        eventos.saveAndFlush(proc.sellar(com.controllocal.domain.auditoria.EventoDominio.de(a.idOrganizacion(),tipo,"PROSPECCION",f.prospeccion(),a.idRolOperativo(),proc.canal())).con(json.objeto(Map.of("compromiso",f.referencia(),"version",f.version()))));
        return reciboSinCambio(f,a,tipo,clave,h,proc);
    }
    private Vista reciboSinCambio(Fila f,Actor a,String tipo,String clave,String h,Procedencia proc){exigirOriginal(f,a);var v=vista(f);comandos.registrar(a,Idempotencia.exigirClave(clave),tipo,h,"PROSPECCION",f.prospeccion(),proc,json.objetoCanonico(new Recibo(f.referencia(),f.version(),f.prospeccion())));return v;}
    private Optional<String> previo(String tipo,String clave,String h,Actor a){
        String k=Idempotencia.exigirClave(clave),id=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(id.substring(0,16),16));return comandos.buscar(a,k,tipo,h).map(x->x.getResultado());
    }
    private String huella(String tipo,Object p,Object ref,Object datos,Actor a,Procedencia proc){if(proc==null)throw new ReglaNegocioException("Procedencia requerida.");return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("entrada",Arrays.asList(tipo,p,ref,datos,a,proc))));}
    private Vista recuperar(String resultado,Actor a){var r=json.comoObjeto(resultado,Recibo.class);var f=repo.cabeceraVersion(a.idOrganizacion(),r.referencia(),r.version()).filter(x->x.prospeccion()==r.prospeccion()).orElseThrow(()->new NoEncontradoException("Compromiso"));var c=contenidoAutorizado(f,a);exigirOriginal(f,a);return new Vista(f.referencia(),f.version(),f.prospeccion(),f.seguimiento(),f.responsableHistorico(),c);}
    private Fila exigir(UUID ref,long p,Actor a,boolean lock){var f=repo.cabecera(a.idOrganizacion(),ref,lock).filter(x->x.prospeccion()==p).orElseThrow(()->new NoEncontradoException("Compromiso"));exigirOriginal(f,a);var completa=repo.obtener(a.idOrganizacion(),ref,false).orElseThrow(()->new NoEncontradoException("Compromiso"));version(f.version(),completa);return completa;}
    public void exigirOriginal(Fila f,Actor a){padre(f.prospeccion(),a,false);var v=json.comoObjeto(f.contenido(),com.fasterxml.jackson.databind.JsonNode.class).path("versionSeguimiento");if(!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<0)throw new IllegalStateException("La constancia no identifica su fuente R27.");seguimiento.exigirLecturaVersion(f.seguimiento(),v.asLong(),f.prospeccion(),a);}
    public void exigirAnterior(CompromisoSeguimientoRepository.AnteriorSinTipo f,Actor a){padre(f.prospeccion(),a,false);seguimiento.exigirLecturaVersion(f.seguimiento(),f.version(),f.prospeccion(),a);}
    private SeguimientoProspeccionRepository.Contexto padre(long id,Actor a,boolean lock){contexto.exigirOperador(a);var p=fuentes.contexto(a.idOrganizacion(),id,lock).orElseThrow(()->new NoEncontradoException("Prospección"));if(!alcances.alcanza(a,p.responsable()))throw new AccesoNoAutorizadoException();if(lock&&(!a.esAgente()||a.idRolOperativo()!=p.responsable()))throw new AccesoNoAutorizadoException();return p;}
    private Contenido contenido(Fila f){return json.comoObjeto(f.contenido(),Contenido.class);}
    @Transactional(readOnly=true) public void exigirFuente(long id,long version,Actor a){contexto.exigirOperador(a);var f=repo.cabeceraPorId(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Compromiso"));exigirOriginal(f,a);if(f.version()!=version)throw new ConflictoException("Cambió la fuente del compromiso.");}
    public Contenido contenidoAutorizado(Fila f,Actor a){exigirOriginal(f,a);return json.comoObjeto(repo.contenidoVersion(a.idOrganizacion(),f.id(),f.version()),Contenido.class);}
    private Vista vista(Fila f){return new Vista(f.referencia(),f.version(),f.prospeccion(),f.seguimiento(),f.responsableHistorico(),contenido(f));}
    private static void version(Long v,Fila f){if(v==null||v!=f.version())throw new ConflictoException("Se requiere la versión explícita del compromiso.");}
    static boolean acreditaHecho(DatosInteraccion d){return d.eventos()!=null&&d.eventos().stream().anyMatch(e->e.evento()==EventoComunicacion.LLAMADA_SALIENTE&&d.resultado()!=ResultadoInteraccion.COMUNICACION_FALLIDA||e.evento()==EventoComunicacion.MENSAJE_ENVIADO||e.evento()==EventoComunicacion.REUNION_REALIZADA);}
    private static boolean vacio(String s){return s==null||s.isBlank();}
}
