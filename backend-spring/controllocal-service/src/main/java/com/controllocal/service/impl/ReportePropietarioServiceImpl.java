package com.controllocal.service.impl;

import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.*;
import com.controllocal.service.documentos.DocumentosReporteAvance;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.ReporteAvanceRepository.Cabecera;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.Fuente;
import com.controllocal.domain.comercial.ArchivoEncargo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** CAT-E31/RF078–079. Preparación, archivo y comunicación tienen historias distintas. */
@Service
public class ReportePropietarioServiceImpl implements ReportePropietarioService {
    public record FuenteContacto(ReferenciaContactoContextual contacto,long persona) { }
    public record Fuentes(List<ReferenciaVersion> interacciones,List<Fuente> recursos,FuenteContacto destinatario) {
        public Fuentes {interacciones=List.copyOf(interacciones);recursos=List.copyOf(recursos);}
    }
    private record ReciboReporte(Referencia reporte) { }
    private record ReciboComunicacion(UUID referencia,long version) { }
    private record PoblacionAnterior(int versionEsquema,long idEncargo,List<ReferenciaVersion> interacciones) { }
    private final ReporteAvanceRepository repo;
    private final AvanceDeReporte avance;
    private final AccesoEncargo acceso;
    private final ContextosDeActividad contexto;
    private final ContactosDeInteraccion contactos;
    private final InteraccionService interacciones;
    private final AccesoOportunidad oportunidades;
    private final ActividadInformeC7Repository actividad;
    private final Documentos json;
    private final ComandoIdempotenteRepository recibos;
    private final ComandosIdempotentes comandos;
    private final EventoDominioRepository eventos;
    private final ArchivoEncargoRepository archivos;
    private final ObjectProvider<DocumentosReporteAvance> pdf;
    private final ObjectProvider<AccesoArchivoEncargo> accesoArchivos;
    public ReportePropietarioServiceImpl(ReporteAvanceRepository r,AvanceDeReporte av,AccesoEncargo ac,
            ContextosDeActividad c,ContactosDeInteraccion co,InteraccionService i,AccesoOportunidad op,
            ActividadInformeC7Repository ar,Documentos j,ComandoIdempotenteRepository re,ComandosIdempotentes cm,
            EventoDominioRepository ev,ArchivoEncargoRepository f,ObjectProvider<DocumentosReporteAvance> p,ObjectProvider<AccesoArchivoEncargo> aa){
        repo=r;avance=av;acceso=ac;contexto=c;contactos=co;interacciones=i;oportunidades=op;actividad=ar;json=j;
        recibos=re;comandos=cm;eventos=ev;archivos=f;pdf=p;accesoArchivos=aa;
    }
    @Override @Transactional(readOnly=true)
    public List<CabeceraReporte> listar(long id,int pagina,int tamano,Actor a){
        paginar(pagina,tamano);padre(id,a);var filas=repo.listar(a.idOrganizacion(),id,pagina,tamano);var salida=new ArrayList<CabeceraReporte>();
        for(var h:filas){autorizar(h,a);salida.add(new CabeceraReporte(h.referencia()==null?h.id():null,ref(h),id,h.desde(),h.hasta(),h.registrada(),
            h.referencia()==null?"ANTECEDENTE_SIN_VERSION_ACREDITADA":archivo(h,a)==null?"PREPARADO_SIN_ARCHIVO":"ARCHIVO_DISPONIBLE"));}
        for(var h:filas)autorizar(h,a);padre(id,a);return List.copyOf(salida);
    }
    @Override @Transactional(readOnly=true)
    public VistaPrevia preview(long id,LocalDate desde,LocalDate hasta,Actor a){return avance.preparar(id,desde,hasta,a).avance();}
    @Override @Transactional
    public FichaReporte preparar(long id,DatosReporte d,Actor a,ContextoSesion s,String clave,Procedencia p){
        contexto.iniciar(a,s);String tipo="PREPARAR_REPORTE_PROPIETARIO",huella=huella(tipo,id,d,a,p);
        var previo=previo(tipo,clave,huella,a);if(previo.isPresent())return consultar(json.comoObjeto(previo.get(),ReciboReporte.class).reporte(),a);
        var inicio=acceso.iniciar(a,s,id);var padre=acceso.leerParaActuar(id,a,inicio);acceso.exigirResponsable(padre,a);
        if(d==null||d.versionEncargo()==null||d.versionEncargo()!=padre.getVersion()||!PLANTILLA.equals(d.plantillaVersion()))throw new ConflictoException("Identifica la versión del Encargo y la plantilla vigente.");
        texto(d.finalidad(),"Indica la finalidad del reporte.");destinatario(d.destinatarioPrevisto(),a,p,false);
        for(String recomendacion:d.recomendacionesProfesionales())texto(recomendacion,"No incluyas recomendaciones vacías.");
        UUID familia=UUID.randomUUID();long version=0;
        if(d.versionAnterior()!=null){var anterior=cabecera(d.versionAnterior(),a);autorizar(anterior,a);if(anterior.encargo()!=id)throw regla("La nueva versión conserva el mismo Encargo.");
            familia=anterior.referencia();repo.bloquearFamilia(a.idOrganizacion(),familia);
            if(repo.ultima(a.idOrganizacion(),familia).orElseThrow().version().longValue()!=anterior.version().longValue())throw conflicto();version=anterior.version()+1;
        }else repo.bloquearFamilia(a.idOrganizacion(),familia);
        var lectura=avance.preparar(id,d.periodoInicio(),d.periodoFin(),a);
        var contacto=destinatario(d.destinatarioPrevisto(),a,p,true);var referencia=new Referencia(familia,version);
        UUID archivo=UUID.randomUUID();Instant ahora=contexto.ahora();
        var ficha=new FichaReporte(referencia,id,PLANTILLA,persona(d.destinatarioPrevisto(),contacto),d.finalidad().trim(),lectura.avance(),d.recomendacionesProfesionales(),null,ahora);
        var fuentes=new Fuentes(lectura.interacciones(),lectura.recursos(),new FuenteContacto(contacto.referencia(),contacto.persona().getId()));
        repo.crear(a.idOrganizacion(),familia,version,id,a.idRolOperativo(),d.periodoInicio(),d.periodoFin(),lectura.avance().corte(),ficha.finalidad(),
            json.objetoCanonico(ficha),json.objetoCanonico(fuentes),archivo,ficha.avance().hechos().consultasComercialesRegistradas(),ficha.avance().hechos().visitasRealizadas(),ahora);
        var creada=cabecera(referencia,a);autorizar(creada,a);evento(tipo,creada,a,p,Map.of("reporte",referencia));
        comandos.registrar(a,Idempotencia.exigirClave(clave),tipo,huella,"ENCARGO",id,p,json.objetoCanonico(new ReciboReporte(referencia)));return ficha;
    }
    @Override @Transactional(readOnly=true)
    public FichaReporte consultar(Referencia r,Actor a){var h=cabecera(r,a);autorizar(h,a);var f=json.comoObjeto(repo.contenido(a.idOrganizacion(),h.id()).orElseThrow(),FichaReporte.class);autorizar(h,a);
        if(!r.equals(f.reporte())||f.encargo()!=h.encargo())throw new IllegalStateException("La versión conservada no coincide con su cabecera.");
        return new FichaReporte(f.reporte(),f.encargo(),f.plantillaVersion(),f.destinatarioPrevisto(),f.finalidad(),f.avance(),f.recomendacionesProfesionales(),archivo(h,a),f.preparadoEn());}
    @Override @Transactional(readOnly=true)
    public FichaReporte exigirLecturaArchivo(UUID archivo,Actor a){contexto.exigirOperador(a);var h=repo.porArchivo(a.idOrganizacion(),archivo).orElseThrow(()->new NoEncontradoException("Reporte"));return consultar(ref(h),a);}
    @Override @Transactional
    public Archivo obtenerArchivo(Referencia r,Actor a,ContextoSesion s){
        acceso.iniciarConLecturaProtegida(a,s,cabecera(r,a).encargo());var h=cabecera(r,a);repo.bloquearFamilia(a.idOrganizacion(),h.referencia());
        var f=consultar(r,a);var existente=archivo(h,a);if(existente!=null)return existente;
        String fuente=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("reporte",r,"contenido",repo.contenido(a.idOrganizacion(),h.id()).orElseThrow(),"fuentes",h.fuentes())));
        var origen=new DocumentosReporteAvance.Origen(h.archivoPrevisto(),r,h.archivoPrevisto(),h.encargo(),fuente,a,s);
        var guardado=pdf.getObject().generar(origen,contenidoPdf(f));autorizar(h,a);
        archivos.saveAndFlush(ArchivoEncargo.registrar(a.idOrganizacion(),h.archivoPrevisto(),h.encargo(),null,a.idRolOperativo(),ArchivoEncargo.REPORTE,
            guardado.claveProveedor(),guardado.nombre(),guardado.contentType(),guardado.huella(),contexto.ahora().atOffset(ZoneOffset.UTC)));
        return archivo(h,a);
    }
    @Override @Transactional(readOnly=true)
    public AntecedenteReporte consultarAntecedente(long id,Actor a){contexto.exigirOperador(a);var h=repo.porId(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Reporte"));
        if(h.referencia()!=null)throw regla("Consulta la referencia y versión de este reporte.");autorizar(h,a);var f=repo.antecedente(a.idOrganizacion(),id).orElseThrow();autorizar(h,a);
        if(json.comoObjeto(h.fuentes(),PoblacionAnterior.class).interacciones().size()!=f.consultas())throw new IllegalStateException("El contador histórico no coincide con su población conservada.");
        return new AntecedenteReporte(id,h.encargo(),f.fecha(),f.desde(),f.hasta(),f.consultas(),f.visitas(),f.registrada(),"Contadores históricos con su definición original. No acreditan consulta comercial, envío ni recepción; el texto privado antiguo no se difunde.");}
    @Override @Transactional
    public Comunicacion registrarComunicacion(DatosComunicacion d,Actor a,ContextoSesion s,String clave,Procedencia p){
        contexto.iniciar(a,s);String tipo="REGISTRAR_COMUNICACION_REPORTE",huella=huella(tipo,null,d,a,p);var previo=previo(tipo,clave,huella,a);
        if(previo.isPresent())return recuperarComunicacion(json.comoObjeto(previo.get(),ReciboComunicacion.class),a);
        if(d==null)throw regla("Indica la comunicación realizada.");var h=cabecera(d.reporte(),a);var inicio=acceso.iniciar(a,s,h.encargo());acceso.exigirResponsable(acceso.leerParaActuar(h.encargo(),a,inicio),a);
        consultar(d.reporte(),a);validarComunicacion(d.destinatarioReal(),d.fechaReal(),d.canal(),d.medioReal(),d.constancia(),d.evidencias(),h,a,p);
        var contacto=destinatario(d.destinatarioReal(),a,p,true);var ahora=contexto.ahora();UUID referencia=UUID.randomUUID();
        var salida=new Comunicacion(referencia,0,d.reporte(),persona(d.destinatarioReal(),contacto),d.fechaReal(),d.canal(),d.medioReal(),d.constancia(),d.evidencias(),"COMUNICACION_DECLARADA_SIN_ACUSE",ahora,null,null);
        repo.crearComunicacion(a.idOrganizacion(),referencia,h.id(),json.objetoCanonico(salida),json.objetoCanonico(new FuenteContacto(contacto.referencia(),contacto.persona().getId())),a.idRolOperativo(),ahora);
        evento(tipo,h,a,p,Map.of("comunicacion",referencia,"version",0,"reporte",d.reporte()));
        comandos.registrar(a,Idempotencia.exigirClave(clave),tipo,huella,"ENCARGO",h.encargo(),p,json.objetoCanonico(new ReciboComunicacion(referencia,0)));return salida;
    }
    @Override @Transactional(readOnly=true)
    public List<HistorialComunicacion> comunicaciones(long id,int pagina,int tamano,Actor a){
        paginar(pagina,tamano);padre(id,a);var filas=repo.comunicaciones(a.idOrganizacion(),id,pagina,tamano);var salida=new ArrayList<HistorialComunicacion>();
        for(var c:filas){var historia=new ArrayList<Comunicacion>();for(var v:repo.historia(a.idOrganizacion(),c.id()))historia.add(recuperarComunicacion(new ReciboComunicacion(c.referencia(),v.version()),a));
            var vigente=recuperarComunicacion(new ReciboComunicacion(c.referencia(),c.version()),a);salida.add(new HistorialComunicacion(vigente,historia.stream().filter(x->x.version()!=c.version()).toList()));}
        if(!filas.equals(repo.comunicaciones(a.idOrganizacion(),id,pagina,tamano)))throw conflicto();padre(id,a);return List.copyOf(salida);
    }
    @Override @Transactional(readOnly=true)
    public Comunicacion consultarComunicacion(UUID referencia,long version,Actor a){return recuperarComunicacion(new ReciboComunicacion(referencia,version),a);}
    @Override @Transactional(readOnly=true)
    public Rectificacion validarRectificacion(Rectificacion d,Actor a){
        contexto.exigirAgente(a);if(d==null||d.comunicacion()==null||d.versionEsperada()==null)throw regla("Identifica la comunicación y su versión.");
        var c=repo.comunicacion(a.idOrganizacion(),d.comunicacion(),false).orElseThrow(()->new NoEncontradoException("Comunicación"));
        var h=repo.porId(a.idOrganizacion(),c.reporte()).orElseThrow();padre(h.encargo(),a);acceso.exigirResponsable(acceso.leer(h.encargo(),a),a);
        recuperarComunicacion(new ReciboComunicacion(c.referencia(),c.version()),a);if(c.version()!=d.versionEsperada())throw conflicto();texto(d.motivo(),"Explica la rectificación.");
        validarComunicacion(d.destinatarioReal(),d.fechaReal(),d.canal(),d.medioReal(),d.constancia(),d.evidencias(),h,a,Procedencia.deLaPantalla());return d;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaRectificacion(Rectificacion d,Actor a){if(d==null||d.comunicacion()==null||d.versionEsperada()==null)throw regla("Identifica la comunicación y versión.");recuperarComunicacion(new ReciboComunicacion(d.comunicacion(),d.versionEsperada()),a);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Comunicacion rectificar(Rectificacion d,Actor a,ContextoSesion s,String clave,Procedencia p){
        contexto.iniciar(a,s);String tipo="RECTIFICAR_COMUNICACION_REPORTE",huella=huella(tipo,null,d,a,p);var previo=previo(tipo,clave,huella,a);if(previo.isPresent())return recuperarComunicacion(json.comoObjeto(previo.get(),ReciboComunicacion.class),a);
        validarRectificacion(d,a);var c=repo.comunicacion(a.idOrganizacion(),d.comunicacion(),true).orElseThrow();if(c.version()!=d.versionEsperada())throw conflicto();
        var h=repo.porId(a.idOrganizacion(),c.reporte()).orElseThrow();var inicio=acceso.iniciar(a,s,h.encargo());acceso.exigirResponsable(acceso.leerParaActuar(h.encargo(),a,inicio),a);
        var anterior=recuperarComunicacion(new ReciboComunicacion(c.referencia(),c.version()),a);var contacto=destinatario(d.destinatarioReal(),a,p,true);var ahora=contexto.ahora();
        var salida=new Comunicacion(c.referencia(),c.version()+1,anterior.reporte(),persona(d.destinatarioReal(),contacto),d.fechaReal(),d.canal(),d.medioReal(),d.constancia(),d.evidencias(),anterior.naturaleza(),anterior.registradaEn(),ahora,d.motivo());
        repo.rectificar(c,a.idOrganizacion(),json.objetoCanonico(salida),json.objetoCanonico(new FuenteContacto(contacto.referencia(),contacto.persona().getId())),a.idRolOperativo(),ahora,d.motivo()).orElseThrow(ReportePropietarioServiceImpl::conflicto);
        evento(tipo,h,a,p,Map.of("comunicacion",c.referencia(),"version",salida.version()));
        comandos.registrar(a,Idempotencia.exigirClave(clave),tipo,huella,"ENCARGO",h.encargo(),p,json.objetoCanonico(new ReciboComunicacion(c.referencia(),salida.version())));return salida;
    }
    @Override @Transactional(readOnly=true)
    public Optional<Comunicacion> resultadoRectificacion(String clave,Actor a){contexto.exigirOperador(a);return comandos.consultarResultado(a,clave,"RECTIFICAR_COMUNICACION_REPORTE").map(c->recuperarComunicacion(json.comoObjeto(c.getResultado(),ReciboComunicacion.class),a));}
    @Override @Transactional(readOnly=true) public Optional<FichaReporte> resultadoPreparacion(String clave,Actor a){contexto.exigirOperador(a);return comandos.consultarResultado(a,Idempotencia.exigirClave(clave),"PREPARAR_REPORTE_PROPIETARIO").map(c->consultar(json.comoObjeto(c.getResultado(),ReciboReporte.class).reporte(),a));}
    @Override @Transactional(readOnly=true) public Optional<Comunicacion> resultadoComunicacion(String clave,Actor a){contexto.exigirOperador(a);return comandos.consultarResultado(a,Idempotencia.exigirClave(clave),"REGISTRAR_COMUNICACION_REPORTE").map(c->recuperarComunicacion(json.comoObjeto(c.getResultado(),ReciboComunicacion.class),a));}
    private Comunicacion recuperarComunicacion(ReciboComunicacion r,Actor a){contexto.exigirOperador(a);var c=repo.comunicacion(a.idOrganizacion(),r.referencia(),false).orElseThrow(()->new NoEncontradoException("Comunicación"));
        var h=repo.porId(a.idOrganizacion(),c.reporte()).orElseThrow();autorizar(h,a);var version=repo.historia(a.idOrganizacion(),c.id()).stream().filter(v->v.version()==r.version()).findFirst().orElseThrow(()->new NoEncontradoException("Versión de comunicación"));
        var fuente=json.comoObjeto(version.fuenteContacto(),FuenteContacto.class);contactos.comprobar(fuente.contacto(),fuente.persona(),a);
        var salida=json.comoObjeto(repo.contenidoComunicacion(a.idOrganizacion(),c.id(),r.version()).orElseThrow(),Comunicacion.class);
        if(!r.referencia().equals(salida.referencia())||r.version()!=salida.version()||!ref(h).equals(salida.reporte()))throw new IllegalStateException("La comunicación no coincide con su versión conservada.");
        evidencias(salida.evidencias(),h.encargo(),a);contactos.comprobar(fuente.contacto(),fuente.persona(),a);autorizar(h,a);return salida;
    }
    private void validarComunicacion(Destinatario d,LocalDate fecha,CanalComunicacion canal,String medio,String constancia,List<UUID> evidencias,Cabecera h,Actor a,Procedencia p){
        destinatario(d,a,p,false);if(fecha==null||fecha.isAfter(contexto.hoy())||fecha.isBefore(h.registrada().atZone(ZoneId.of("America/Lima")).toLocalDate()))throw regla("Registra una fecha real no futura y posterior a la preparación de esta versión.");
        if(canal==null)throw regla("Indica el canal real de comunicación.");CatalogoInteracciones.validar(null,canal,medio,Set.of(),null);texto(constancia,"Deja constancia de la comunicación realizada; no acredita recepción.");evidencias(evidencias,h.encargo(),a);
    }
    private void evidencias(List<UUID> refs,long encargo,Actor a){if(refs==null||new HashSet<>(refs).size()!=refs.size())throw regla("Las evidencias deben ser referencias distintas.");
        for(var r:refs){if(r==null)throw regla("Identifica cada evidencia.");var m=archivos.actual(a.idOrganizacion(),r).orElseThrow(()->new NoEncontradoException("Evidencia"));
            if(!Objects.equals(m.getEncargo(),encargo))throw new AccesoNoAutorizadoException();accesoArchivos.getObject().autorizar(r,a);}}
    private Cabecera cabecera(Referencia r,Actor a){contexto.exigirOperador(a);if(r==null||r.referencia()==null||r.version()==null||r.version()<0)throw regla("Identifica la referencia y versión del reporte.");return repo.version(a.idOrganizacion(),r.referencia(),r.version()).orElseThrow(()->new NoEncontradoException("Reporte"));}
    private void padre(long id,Actor a){contexto.exigirOperador(a);acceso.exigirLectura(id,a);}
    private void autorizar(Cabecera h,Actor a){padre(h.encargo(),a);
        if(h.referencia()==null){if(h.fuentes()==null)throw new AccesoNoAutorizadoException();var f=json.comoObjeto(h.fuentes(),PoblacionAnterior.class);
            if(f.versionEsquema()!=1||f.idEncargo()!=h.encargo()||f.interacciones()==null||f.interacciones().stream().anyMatch(r->r==null||r.id()<=0||r.version()<0)||f.interacciones().stream().map(ReferenciaVersion::id).distinct().count()!=f.interacciones().size())throw new AccesoNoAutorizadoException();
            interacciones.exigirVersiones(f.interacciones(),a,FinalidadLectura.REPORTE_PROPIETARIO);return;}
        var f=json.comoObjeto(h.fuentes(),Fuentes.class);if(f.destinatario()==null)throw new AccesoNoAutorizadoException();contactos.comprobar(f.destinatario().contacto(),f.destinatario().persona(),a);
        for(var r:f.recursos()){switch(r.tipo()){
            case "ENCARGO" -> acceso.exigirLectura(r.id(),a);
            case "OPORTUNIDAD" -> oportunidades.contextoParaNegociacion(r.id(),a);
            case "VISITA","OFERTA" -> {var actual=actividad.fuenteActividad(a.idOrganizacion(),r.tipo(),r.id()).orElseThrow(()->new NoEncontradoException("Fuente de reporte"));oportunidades.contextoParaNegociacion(actual.oportunidad(),a);}
            default -> throw new AccesoNoAutorizadoException();}}
        interacciones.exigirVersiones(f.interacciones(),a,FinalidadLectura.REPORTE_PROPIETARIO);padre(h.encargo(),a);
    }
    private ContactosDeInteraccion.Resuelto destinatario(Destinatario d,Actor a,Procedencia p,boolean guardar){if(d==null)throw regla("Selecciona el destinatario para esta gestión.");texto(d.funcionDeclarada(),"Indica qué función cumple el contacto en esta gestión.");texto(d.procedenciaDeclaracion(),"Conserva cómo se conoció su función.");return contactos.resolver(d.contacto(),a,p,guardar);}
    private static PersonaDestinataria persona(Destinatario d,ContactosDeInteraccion.Resuelto c){return new PersonaDestinataria(c.persona().getId(),c.persona().getNombresORazonSocial(),d.funcionDeclarada(),d.procedenciaDeclaracion());}
    private Archivo archivo(Cabecera h,Actor a){if(h.archivoPrevisto()==null)return null;return archivos.actual(a.idOrganizacion(),h.archivoPrevisto()).map(f->new Archivo(h.archivoPrevisto(),f.getNombre(),f.getContentType(),f.getHuella())).orElse(null);}
    private Optional<String> previo(String tipo,String clave,String huella,Actor a){String k=Idempotencia.exigirClave(clave);var lock=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);recibos.bloquearIdentidad(Long.parseUnsignedLong(lock.substring(0,16),16));return comandos.buscar(a,k,tipo,huella).map(c->c.getResultado());}
    private String huella(String tipo,Object id,Object d,Actor a,Procedencia p){if(p==null)throw regla("Conserva la procedencia de la actuación.");return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("entrada",Arrays.asList(tipo,id,d,a,p))));}
    private void evento(String tipo,Cabecera h,Actor a,Procedencia p,Map<String,Object> datos){eventos.saveAndFlush(p.sellar(com.controllocal.domain.auditoria.EventoDominio.de(a.idOrganizacion(),tipo,"ENCARGO",h.encargo(),a.idRolOperativo(),p.canal())).con(json.objeto(datos)));}
    private static Referencia ref(Cabecera h){return h.referencia()==null?null:new Referencia(h.referencia(),h.version());}
    private static void texto(String s,String mensaje){if(s==null||s.isBlank())throw regla(mensaje);}
    private static void paginar(int p,int t){if(p<1||t<1||t>100)throw regla("Selecciona página positiva y tamaño entre 1 y 100.");}
    private static ReglaNegocioException regla(String m){return new ReglaNegocioException(m);}
    private static ConflictoException conflicto(){return new ConflictoException("Cambió la versión; revisa el antecedente vigente.");}
    private static DocumentosEncargo.Contenido contenidoPdf(FichaReporte f){var campos=new ArrayList<DocumentosEncargo.Campo>();var v=f.avance();var h=v.hechos();
        campos.add(new DocumentosEncargo.Campo("Encargo",v.codigoEncargo()+" · "+v.operacion()));campos.add(new DocumentosEncargo.Campo("Versión",f.reporte().referencia()+" / "+f.reporte().version()));
        campos.add(new DocumentosEncargo.Campo("Período",v.desde()+" a "+v.hasta()));campos.add(new DocumentosEncargo.Campo("Corte",v.corte().toString()));
        campos.add(new DocumentosEncargo.Campo("Destinatario previsto",f.destinatarioPrevisto().nombre()+" · "+f.destinatarioPrevisto().funcionDeclarada()));campos.add(new DocumentosEncargo.Campo("Finalidad",f.finalidad()));
        campos.add(new DocumentosEncargo.Campo("Situación registrada al corte",v.situacionActual()));campos.add(new DocumentosEncargo.Campo("Comunicaciones registradas",String.valueOf(h.comunicacionesRegistradas())));
        campos.add(new DocumentosEncargo.Campo("Consultas comerciales recibidas",String.valueOf(h.consultasComercialesRegistradas())));campos.add(new DocumentosEncargo.Campo("Visitas realizadas",String.valueOf(h.visitasRealizadas())));
        campos.add(new DocumentosEncargo.Campo("Ofertas / contraofertas",h.ofertasRegistradas()+" / "+h.contraofertasRegistradas()));
        campos.add(new DocumentosEncargo.Campo("Comunicaciones / visitas sin fecha acreditada",h.comunicacionesSinFechaAcreditada()+" / "+h.visitasSinFechaAcreditada()));
        h.resultadosComunicativos().forEach((k,n)->campos.add(new DocumentosEncargo.Campo("Resultado comunicativo: "+k,String.valueOf(n))));
        for(String siguiente:v.proximasActuaciones())campos.add(new DocumentosEncargo.Campo("Próxima actuación registrada",siguiente));
        for(String recomendacion:f.recomendacionesProfesionales())campos.add(new DocumentosEncargo.Campo("Recomendación profesional",recomendacion));
        campos.add(new DocumentosEncargo.Campo("Cobertura",h.cobertura()));campos.add(new DocumentosEncargo.Campo("Comunicación","La preparación y descarga no acreditan envío ni recepción."));
        return new DocumentosEncargo.Contenido("BROX · Reporte de avance",campos,List.of());}
}
