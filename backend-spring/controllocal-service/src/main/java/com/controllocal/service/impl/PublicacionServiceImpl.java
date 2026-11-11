package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.documentos.SeleccionInmueblePieza;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.publicacion.HistoriadorPublicacion;
import com.controllocal.service.publicacion.ProyeccionPublicacion;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.*;

/** CAT-E16/RF021–027. La historia, el hito y el recibo pertenecen a la misma transacción. */
@Service
public class PublicacionServiceImpl implements PublicacionService {
    private static final Set<String> SENSIBLES=Set.of(PUBLICAR,ACTUALIZAR_VISIBLE,PAUSAR,REANUDAR,FINALIZAR);
    private final PublicacionRepository anuncios;
    private final HistoriaPublicacionRepository historia;
    private final AtributosGobernados fisico;
    private final AtributosDeEncargo condiciones;
    private final AccesoEncargo acceso;
    private final EncargoService ciclo;
    private final ContextosDeActividad contextos;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    private final HistoriadorPublicacion escritor;
    private final Documentos documentos;
    private final EntityManager entidades;
    private final SeleccionInmueblePieza seleccionPieza;
    public PublicacionServiceImpl(PublicacionRepository anuncios,HistoriaPublicacionRepository historia,
            AtributosGobernados fisico,AtributosDeEncargo condiciones,AccesoEncargo acceso,EncargoService ciclo,
            ContextosDeActividad contextos,ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos,
            HistoriadorPublicacion escritor,Documentos documentos,EntityManager entidades,SeleccionInmueblePieza seleccionPieza){
        this.anuncios=anuncios;this.historia=historia;this.fisico=fisico;this.condiciones=condiciones;
        this.acceso=acceso;this.ciclo=ciclo;this.contextos=contextos;this.comandos=comandos;this.recibos=recibos;
        this.escritor=escritor;this.documentos=documentos;this.entidades=entidades;
        this.seleccionPieza=seleccionPieza;
    }

    @Override @Transactional(readOnly=true)
    public EvaluacionPublicabilidad evaluarPublicabilidad(long id,String canal,Actor actor){
        return evaluar(id,null,canal,actor);
    }
    private EvaluacionPublicabilidad evaluar(long id,Long propiedadFicha,String canal,Actor actor){
        contextos.exigirOperador(actor);var c=acceso.leer(id,actor);
        String normal=texto(canal,30,"canal");if(normal!=null&&!Publicacion.CANALES.contains(normal))
            throw new ReglaNegocioException("Canal de publicación no reconocido.");
        var sujeto=propiedadFicha==null?null:seleccionPieza.elegir(c,propiedadFicha,actor);
        var p=sujeto==null?c.getPropiedad():sujeto.propiedad();var donde=Comercializacion.de(c,p);
        List<String> f=fisico.rotulosDe(actor.idOrganizacion(),p.getTipoInmueble(),
                fisico.faltantesDePropiedadParaPublicar(actor.idOrganizacion(),p));
        List<String> e=condiciones.rotulosDe(actor.idOrganizacion(),donde,
                condiciones.faltantesDeEncargoParaPublicar(actor.idOrganizacion(),donde));
        List<String> motivos=new ArrayList<>();if(normal==null)motivos.add("Selecciona el canal de publicación.");
        // Una insuficiencia esperada se consulta como dato; capturar la excepción de un
        // servicio REQUIRED marcaría rollback-only la TX de esta lectura explicativa.
        var vigencia=ciclo.consultarVigencia(id,contextos.hoy(),actor);
        if(!vigencia.habilitado())motivos.add("El Encargo no está habilitado: "+String.join(", ",vigencia.faltantes()));
        if(!f.isEmpty())motivos.add("Completa la ficha del inmueble: "+String.join(", ",f));
        if(!e.isEmpty())motivos.add("Completa las condiciones del Encargo: "+String.join(", ",e));
        var reglas=new ArrayList<Object>();
        fisico.aplicablesA(actor.idOrganizacion(),p.getTipoInmueble()).forEach(d->reglas.add(regla(d,p.getTipoInmueble(),null)));
        condiciones.aplicablesA(actor.idOrganizacion(),p.getTipoInmueble(),c.operacion().codigo()).forEach(
                d->reglas.add(regla(d,p.getTipoInmueble(),c.operacion().codigo())));
        String vr="CAT-E16/RF021-027/"+Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("definiciones",reglas)));
        EncargoService.Composicion composicion=sujeto!=null?sujeto.composicion():
                c.getIdComposicionActual()==null?null:ciclo.consultarComposicion(c.getId(),null,actor);
        if(composicion!=null&&(composicion.encargo()!=c.getId()||!Objects.equals(composicion.id(),c.getIdComposicionActual())))
            throw new ConflictoException("La composición no corresponde a la versión del Encargo.");
        var versiones=new LinkedHashMap<String,Long>();versiones.put("encargo",c.getVersion());versiones.put("contenidoEncargo",c.getVersionContenido());versiones.put("fisico",p.getVersionFisica());
        if(composicion!=null)versiones.put("composicion",composicion.version());
        var origenes=new LinkedHashMap<String,Object>();origenes.put("reglas",vr);origenes.put("versiones",versiones);
        origenes.put("canal",normal);origenes.put("fuentes",historia.fuentes(actor.idOrganizacion(),p.getId(),c.getId()));
        origenes.put("composicion",composicion);
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(origenes));
        return new EvaluacionPublicabilidad(motivos.isEmpty(),id,c.getVersion(),p.getId(),p.getVersionFisica(),normal,
                List.copyOf(f),List.copyOf(e),List.copyOf(motivos),vr,Map.copyOf(versiones),huella,contextos.ahora(),
                composicion==null?null:composicion.id(),composicion==null?null:composicion.version());
    }
    private Object regla(CatalogoAtributo d,String tipo,String operacion){
        return Arrays.asList(d.getId(),d.getClave(),d.getVersionDefinicion(),d.getTipoDato(),d.getUnidad(),
                operacion==null?d.exigenciaPara(tipo):d.exigenciaPara(tipo,operacion),
                d.opcionesVigentes().stream().map(o->Arrays.asList(o.getValor(),o.getRotulo(),o.getOrden())).toList());
    }
    @Override @Transactional(readOnly=true)
    public EvaluacionPublicabilidad exigirPublicabilidad(long id,String canal,Actor actor){
        var e=evaluarPublicabilidad(id,canal,actor);if(!e.cumple())throw new ReglaNegocioException(
                "Todavía no se puede publicar: "+String.join("; ",e.motivos()));return e;
    }
    @Override @Transactional(readOnly=true)
    public EvaluacionPublicabilidad exigirPublicabilidadDePieza(long id,long propiedadFicha,String canal,Actor actor){
        var e=evaluar(id,propiedadFicha,canal,actor);if(!e.cumple())throw new ReglaNegocioException(
                "Todavía no se puede difundir esta ficha: "+String.join("; ",e.motivos()));return e;
    }
    @Override @Transactional(readOnly=true)
    public List<FichaPublicacion> listarDeEncargo(long id,Actor actor){
        contextos.exigirOperador(actor);var c=acceso.leer(id,actor);
        Long compVersion=historia.versionComposicion(actor.idOrganizacion(),c.getIdComposicionActual());
        return anuncios.deEncargo(actor.idOrganizacion(),id).stream().map(p->ProyeccionPublicacion.ficha(p,c,compVersion)).toList();
    }
    @Override @Transactional(readOnly=true)
    public FichaPublicacion obtener(long encargo,long id,Actor actor){
        contextos.exigirOperador(actor);var c=acceso.leer(encargo,actor);
        return ProyeccionPublicacion.ficha(leer(encargo,id,actor),c,historia.versionComposicion(actor.idOrganizacion(),c.getIdComposicionActual()));
    }
    @Override @Transactional(readOnly=true)
    public List<VersionPublicada> consultarHistoria(long encargo,long id,Actor actor){
        obtener(encargo,id,actor);
        return historia.consultar(actor.idOrganizacion(),encargo,id).stream().map(v->new VersionPublicada(
                v.numero(),v.actuacion(),v.registradaEn(),contenidoHistorico(v.contenido()),v.motivo())).toList();
    }
    private Map<String,Object> contenidoHistorico(String json){
        var datos=documentos.comoMapa(json);Object legado=datos.get("antecedentePersistido");
        if(!(legado instanceof Map<?,?> m))return datos;
        var salida=new LinkedHashMap<String,Object>();
        Map.ofEntries(Map.entry("id_publicacion","id"),Map.entry("id_captacion","idEncargo"),
                Map.entry("canal","canal"),Map.entry("titulo_anuncio","tituloAnuncio"),
                Map.entry("importe_publicado","importePublicado"),Map.entry("moneda","moneda"),
                Map.entry("estado","estado"),Map.entry("fecha_publicacion","fechaPublicacion"),
                Map.entry("fecha_baja","fechaBaja"),Map.entry("url_publicacion","urlPublicacion"),
                Map.entry("codigo_origen","codigoOrigen"),Map.entry("version_anuncio","version"))
                .forEach((k,v)->salida.put(v,m.get(k)));
        return salida;
    }

    @Override @Transactional
    public FichaPublicacion crearBorradorAnuncio(DatosBorrador d,Actor a,ContextoSesion s,String k,Procedencia p){return borrador(CREAR_BORRADOR,d,a,s,k,p);}
    @Override @Transactional
    public FichaPublicacion editarBorradorAnuncio(DatosBorrador d,Actor a,ContextoSesion s,String k,Procedencia p){return borrador(EDITAR_BORRADOR,d,a,s,k,p);}
    private FichaPublicacion borrador(String cap,DatosBorrador d,Actor a,ContextoSesion s,String clave,Procedencia origen){
        if(d==null||d.encargo()<=0)throw new ReglaNegocioException("Identifica el Encargo del borrador.");
        boolean crear=CREAR_BORRADOR.equals(cap);
        if(!crear&&d.contenido()==null)throw new ReglaNegocioException("Indica el contenido del borrador que deseas guardar.");
        if(crear&&(d.publicacion()!=null||d.versionPublicacion()!=null)||!crear&&(d.publicacion()==null||d.versionPublicacion()==null))
            throw new ReglaNegocioException("Identifica correctamente el borrador y su versión.");
        var normal=new DatosBorrador(d.encargo(),d.versionEncargo(),d.publicacion(),d.versionPublicacion(),normalizar(d.contenido()));
        var inicio=acceso.iniciar(a,s,d.encargo());contextos.exigirAgente(a);
        String k=Idempotencia.exigirClave(clave),hash=huellaComando(cap,normal,origen);
        bloquearClave(a,k);var previo=comandos.buscar(a,k,cap,hash);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),a);
        var c=acceso.escribir(d.encargo(),a,inicio);acceso.exigirResponsable(c,a);version(c.getVersion(),d.versionEncargo(),"Encargo");
        Publicacion anuncio;
        if(crear){anuncio=new Publicacion();anuncio.setOrganizacionId(a.idOrganizacion());anuncio.setIdEncargo(c.getId());
            anuncio.setIdPropiedad(c.getPropiedad().getId());anuncio.setEstado(Publicacion.ESTADO_BORRADOR);anuncio.setVersionAnuncio(1);
        }else{anuncio=bloquear(c.getId(),d.publicacion(),a);version(anuncio.getVersionAnuncio(),d.versionPublicacion(),"anuncio");
            estado(anuncio,Publicacion.ESTADO_BORRADOR);anuncio.setVersionAnuncio(Math.addExact(anuncio.getVersionAnuncio(),1));}
        aplicar(anuncio,normal.contenido());
        var resultado=escritor.guardar(anuncio,c,a,cap,origen,null,false,null);registrar(a,k,cap,hash,origen,resultado);return resultado;
    }

    @Override @Transactional(readOnly=true)
    public DatosCambio validarActuacion(String cap,DatosCambio d,Actor actor){
        if(!SENSIBLES.contains(cap)||d==null||d.encargo()<=0||d.publicacion()<=0)
            throw new ReglaNegocioException("Identifica la actuación de publicación.");
        contextos.exigirAgente(actor);var c=acceso.leer(d.encargo(),actor);acceso.exigirResponsable(c,actor);
        version(c.getVersion(),d.versionEncargo(),"Encargo");var p=leer(d.encargo(),d.publicacion(),actor);
        version(p.getVersionAnuncio(),d.versionPublicacion(),"anuncio");
        String esperado=switch(cap){case PUBLICAR->Publicacion.ESTADO_BORRADOR;case REANUDAR->Publicacion.ESTADO_SUSPENDIDO;
            case ACTUALIZAR_VISIBLE,PAUSAR->Publicacion.ESTADO_PUBLICADO;case FINALIZAR->null;default->throw new IllegalArgumentException();};
        if(esperado!=null)estado(p,esperado);else if(Publicacion.ESTADO_CERRADO.equals(p.getEstado()))
            throw new ConflictoException("La difusión ya está cerrada.");
        boolean visible=expone(cap);DatosContenido contenidoActual=contenido(p);
        DatosContenido contenido=d.contenido()==null?contenidoActual:normalizar(d.contenido());
        if(visible){contenido=tituloConocido(contenido,p);contenidoActual=tituloConocido(contenidoActual,p);}
        if(!ACTUALIZAR_VISIBLE.equals(cap)&&d.contenido()!=null&&!Objects.equals(contenido,contenidoActual))
            throw new ReglaNegocioException("Modifica el contenido por la actuación de edición correspondiente.");
        if(ACTUALIZAR_VISIBLE.equals(cap)&&d.contenido()==null)throw new ReglaNegocioException("Indica el contenido visible completo.");
        EstadoExterno externo=externo(d.estadoExterno());
        String motivo=texto(d.motivo(),2000,"motivo");
        if((PAUSAR.equals(cap)||FINALIZAR.equals(cap))&&motivo==null)
            throw new ReglaNegocioException("Indica el motivo de la pausa o finalización de difusión.");
        String actual;
        if(visible){exigirContenido(contenido);actual=exigirPublicabilidad(c.getId(),contenido.canal(),actor).huellaContexto();}
        else actual=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("encargo",c.getId(),"versionEncargo",c.getVersion(),
                "publicacion",p.getId(),"versionPublicacion",p.getVersionAnuncio(),"estado",p.getEstado())));
        if(d.huellaContexto()!=null&&!d.huellaContexto().equals(actual))throw new ConflictoException("Cambió el contenido o la autorización; revisa nuevamente la actuación.");
        return new DatosCambio(d.encargo(),d.versionEncargo(),d.publicacion(),d.versionPublicacion(),
                visible?contenido:null,externo,actual,motivo);
    }
    @Override @Transactional(readOnly=true)
    public Object previsualizarActuacion(String cap,Map<String,Object> datos,Actor actor){
        var d=validarActuacion(cap,documentos.comoObjeto(documentos.objetoCanonico(datos),DatosCambio.class),actor);
        var salida=new LinkedHashMap<String,Object>();salida.put("actuacion",cap);salida.put("propuesta",d);
        salida.put("anuncioActual",obtener(d.encargo(),d.publicacion(),actor));
        salida.put("noAcreditaEstadoExterno",d.estadoExterno()==null);
        return salida;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaActuacion(String cap,Map<String,Object> datos,Actor actor){
        if(!SENSIBLES.contains(cap))throw new IllegalArgumentException("Actuación de publicación desconocida.");
        var d=documentos.comoObjeto(documentos.objetoCanonico(datos),DatosCambio.class);
        contextos.exigirAgente(actor);var c=acceso.leer(d.encargo(),actor);acceso.exigirResponsable(c,actor);leer(d.encargo(),d.publicacion(),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaPublicacion publicarAnuncio(DatosCambio d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(PUBLICAR,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaPublicacion actualizarAnuncioVisible(DatosCambio d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(ACTUALIZAR_VISIBLE,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaPublicacion pausarAnuncio(DatosCambio d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(PAUSAR,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaPublicacion reanudarAnuncio(DatosCambio d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(REANUDAR,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaPublicacion finalizarDifusionAnuncio(DatosCambio d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(FINALIZAR,d,a,s,k,p);}
    private FichaPublicacion ejecutar(String cap,DatosCambio d,Actor a,ContextoSesion s,String clave,Procedencia origen){
        if(d==null||d.huellaContexto()==null)throw new AccesoNoAutorizadoException();
        var inicio=acceso.iniciar(a,s,d.encargo());contextos.exigirAgente(a);
        String k=Idempotencia.exigirClave(clave),hash=huellaComando(cap,d,origen);bloquearClave(a,k);
        var previo=comandos.buscar(a,k,cap,hash);if(previo.isPresent())return restaurar(previo.get().getResultado(),a);
        var c=acceso.escribir(d.encargo(),a,inicio);acceso.exigirResponsable(c,a);
        var p=bloquear(d.encargo(),d.publicacion(),a);
        if(expone(cap)){entidades.refresh(c.getPropiedad(),LockModeType.PESSIMISTIC_READ);if(c.getCondicionEconomica()!=null)entidades.refresh(c.getCondicionEconomica());}
        var normal=validarActuacion(cap,d,a);var anterior=contenido(p);
        // La fecha antigua de un borrador no prueba exposición: el constructor previo la rellenaba.
        boolean primera=PUBLICAR.equals(cap);
        if(expone(cap))aplicar(p,normal.contenido());
        switch(cap){case PUBLICAR,REANUDAR->p.setEstado(Publicacion.ESTADO_PUBLICADO);
            case PAUSAR->p.setEstado(Publicacion.ESTADO_SUSPENDIDO);
            case FINALIZAR->{p.setEstado(Publicacion.ESTADO_CERRADO);p.setFechaBaja(contextos.ahora().atOffset(ZoneOffset.UTC));}
            case ACTUALIZAR_VISIBLE->{}default->throw new IllegalArgumentException();}
        if(expone(cap)&&primera)p.setFechaPublicacion(contextos.ahora().atOffset(ZoneOffset.UTC));
        if(normal.estadoExterno()!=null){p.setEstadoExterno(normal.estadoExterno().estado());p.setFechaEstadoExterno(normal.estadoExterno().fechaEfectiva().atOffset(ZoneOffset.UTC));
            p.setProcedenciaEstadoExterno(normal.estadoExterno().procedencia());}
        p.setVersionAnuncio(Math.addExact(p.getVersionAnuncio(),1));
        var evaluacion=expone(cap)?exigirPublicabilidad(c.getId(),p.getCanal(),a):null;
        var resultado=escritor.guardar(p,c,a,cap,origen,evaluacion,
                expone(cap)&&(primera||!mismoImporte(anterior,contenido(p))),normal.motivo());
        registrar(a,k,cap,hash,origen,resultado);return resultado;
    }
    @Override @Transactional(readOnly=true)
    public Optional<FichaPublicacion> consultarResultado(String cap,String clave,Actor actor){
        contextos.exigirAgente(actor);return recibos.findByOrganizacionIdAndClaveIdempotencia(actor.idOrganizacion(),Idempotencia.exigirClave(clave))
                .filter(c->Objects.equals(c.getIdPersonaRol(),actor.idRolOperativo())&&cap.equals(c.getTipoComando()))
                .map(c->restaurar(c.getResultado(),actor));
    }
    private FichaPublicacion restaurar(String json,Actor actor){
        var f=documentos.comoObjeto(json,FichaPublicacion.class);var c=acceso.leer(f.idEncargo(),actor);
        acceso.exigirResponsable(c,actor);leer(f.idEncargo(),f.id(),actor);return f;
    }
    private void registrar(Actor a,String k,String cap,String hash,Procedencia p,FichaPublicacion f){comandos.registrar(a,k,cap,hash,"PUBLICACION",f.id(),p,documentos.objetoCanonico(f));}
    private String huellaComando(String cap,Object datos,Procedencia p){
        if(p==null)throw new ReglaNegocioException("Conserva la procedencia de la actuación.");
        return Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("capacidad",cap,"datos",datos,"procedencia",p)));
    }
    private void bloquearClave(Actor a,String k){String h=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));}
    private Publicacion leer(long encargo,long id,Actor actor){return anuncios.findByOrganizacionIdAndId(actor.idOrganizacion(),id)
            .filter(p->Objects.equals(p.getIdEncargo(),encargo)).orElseThrow(()->new NoEncontradoException("Publicación"));}
    private Publicacion bloquear(long encargo,long id,Actor a){return anuncios.bloquear(a.idOrganizacion(),encargo,id).orElseThrow(()->new NoEncontradoException("Publicación"));}
    private static void version(long actual,long esperada,String entidad){if(actual!=esperada)throw new ConflictoException("Cambió la versión de "+entidad+"; vuelve a consultar.");}
    private static void estado(Publicacion p,String esperado){if(!esperado.equals(p.getEstado()))throw new ConflictoException("El anuncio cambió de estado; consulta su situación actual.");}
    private static boolean expone(String cap){return PUBLICAR.equals(cap)||ACTUALIZAR_VISIBLE.equals(cap)||REANUDAR.equals(cap);}
    private static DatosContenido contenido(Publicacion p){return new DatosContenido(p.getCanal(),p.getUrlPublicacion(),p.getImportePublicado(),p.getMoneda(),p.getTituloAnuncio(),p.getCodigoOrigen());}
    /** Conserva el rótulo técnico anterior; aparece en la revisión, sin exigir otra captura. */
    private static DatosContenido tituloConocido(DatosContenido d,Publicacion p){return d.tituloAnuncio()!=null?d:
            new DatosContenido(d.canal(),d.urlPublicacion(),d.importePublicado(),d.moneda(),"Publicacion "+p.getIdPropiedad(),d.codigoOrigen());}
    private static void aplicar(Publicacion p,DatosContenido d){p.setCanal(d.canal());p.setUrlPublicacion(d.urlPublicacion());p.setImportePublicado(d.importePublicado());
        p.setMoneda(d.moneda());p.setTituloAnuncio(d.tituloAnuncio());p.setCodigoOrigen(d.codigoOrigen());}
    private static boolean mismoImporte(DatosContenido a,DatosContenido b){return Objects.equals(a.moneda(),b.moneda())&&a.importePublicado()!=null&&b.importePublicado()!=null&&a.importePublicado().compareTo(b.importePublicado())==0;}
    private static DatosContenido normalizar(DatosContenido d){
        if(d==null)return new DatosContenido(null,null,null,null,null,null);
        String canal=texto(d.canal(),30,"canal");if(canal!=null&&!Publicacion.CANALES.contains(canal))throw new ReglaNegocioException("Canal de publicación no reconocido.");
        String moneda=texto(d.moneda(),3,"moneda");if(moneda!=null)moneda=CondicionesEconomicas.moneda(moneda,"del importe publicado");
        BigDecimal importe=d.importePublicado();if(importe!=null&&(importe.signum()<=0||importe.stripTrailingZeros().scale()>2||importe.compareTo(new BigDecimal("9999999999.99"))>0))
            throw new ReglaNegocioException("El importe publicado debe ser positivo y admitir hasta dos decimales.");
        if(importe!=null)importe=importe.setScale(2);
        String url=texto(d.urlPublicacion(),500,"URL");if(url!=null){try{var u=URI.create(url);if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException ex){throw new ReglaNegocioException("La URL debe ser HTTP(S), sin credenciales.");}}
        return new DatosContenido(canal,url,importe,moneda,texto(d.tituloAnuncio(),200,"título"),texto(d.codigoOrigen(),50,"código de origen"));
    }
    private static void exigirContenido(DatosContenido d){if(d.canal()==null||d.tituloAnuncio()==null||d.importePublicado()==null||d.moneda()==null)
        throw new ReglaNegocioException("Completa canal, título, precio y moneda antes de publicar.");}
    private static EstadoExterno externo(EstadoExterno e){if(e==null)return null;String estado=texto(e.estado(),80,"estado externo"),fuente=texto(e.procedencia(),500,"fuente externa");
        if(estado==null||fuente==null||e.fechaEfectiva()==null)throw new ReglaNegocioException("Un estado externo requiere hecho conocido, fecha efectiva y procedencia.");
        return new EstadoExterno(estado,e.fechaEfectiva().truncatedTo(java.time.temporal.ChronoUnit.MICROS),fuente);}
    private static String texto(String s,int max,String campo){if(s==null||s.isBlank())return null;String n=s.strip();if(n.length()>max)throw new ReglaNegocioException("El "+campo+" excede su longitud permitida.");return n;}
}

