package com.controllocal.service.encargo;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.RotulosDeSuperficie;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static com.controllocal.service.encargo.ConstanciasDeFirmaDeEncargo.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** CAT-E12–15/RF015–019. Un episodio, revisiones anexadas y un único efecto de fin. */
@Service
@Transactional
public class EncargoServiceImpl implements EncargoService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");

    public static final String PLANTILLA_VERSION="ENCARGO_MVP_V1";


    private static final String CONTRATO_REFERENCIAS="REFERENCIAS-PETICION-ACTUACION-1";
    private final CaptacionRepository encargos;
    private final PropiedadRepository propiedades;
    private final DetalleAgenteRepository agentes;
    private final DocumentoEncargoRepository documentosEncargo;
    private final ArchivoEncargoRepository archivos;
    private final CargaArchivoContextualRepository cargas;
    private final com.controllocal.service.archivo.AccesoArchivoService accesoArchivos;
    private final PeticionRevisionEncargoRepository peticiones;
    private final SolicitudFinalizacionEncargoRepository solicitudes;
    private final RevisionEncargoRepository revisiones;
    private final ComposicionEncargoRepository composiciones;
    private final EventoDominioRepository eventos;
    private final PrecioPropiedadRepository precios;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final AccesoEncargo acceso;
    private final ReglasDeResponsableEncargo responsables;
    private final AutoridadDePropiedad autoridadFisica;
    private final TitularParaEncargar titularidad;
    private final SuficienciaRegistrada suficiencia;
    private final SuficienciaInmueble reglaSuficiencia;
    private final CondicionesDeEncargo condiciones;
    private final ConstanciasDeFirmaDeEncargo constancias;
    private final PactosDeEncargo pactos;
    private final AtributosGobernados atributosFisicos;
    private final CondicionesDinamicasEncargo dinamicas;
    private final Transiciones transiciones;
    private final Documentos json;
    private final ObjectMapper mapper;
    private final DocumentosEncargo binarios;
    private final EntityManager entidades;
    private final Clock reloj;
    private final org.springframework.context.ApplicationEventPublisher cambiosComerciales;
    private final com.controllocal.service.actividad.SeguimientoDeProspeccion seguimientos;

    @org.springframework.beans.factory.annotation.Autowired
    public EncargoServiceImpl(CaptacionRepository encargos,PropiedadRepository propiedades,
            DetalleAgenteRepository agentes,DocumentoEncargoRepository documentosEncargo,
            ArchivoEncargoRepository archivos,CargaArchivoContextualRepository cargas,com.controllocal.service.archivo.AccesoArchivoService accesoArchivos,PeticionRevisionEncargoRepository peticiones,
            SolicitudFinalizacionEncargoRepository solicitudes,RevisionEncargoRepository revisiones,
            ComposicionEncargoRepository composiciones,EventoDominioRepository eventos,PrecioPropiedadRepository precios,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,AccesoEncargo acceso,ReglasDeResponsableEncargo responsables,
            AutoridadDePropiedad autoridadFisica,TitularParaEncargar titularidad,
            SuficienciaRegistrada suficiencia,SuficienciaInmueble reglaSuficiencia,
            CondicionesDeEncargo condiciones,AtributosGobernados atributosFisicos,CondicionesDinamicasEncargo dinamicas,Transiciones transiciones,
            Documentos json,ObjectMapper mapper,DocumentosEncargo binarios,EntityManager entidades,
            org.springframework.context.ApplicationEventPublisher cambiosComerciales,
            com.controllocal.service.actividad.SeguimientoDeProspeccion seguimientos,ConstanciasDeFirmaDeEncargo constancias,PactosDeEncargo pactos) {
        this(encargos,propiedades,agentes,documentosEncargo,archivos,cargas,accesoArchivos,peticiones,solicitudes,revisiones,
                composiciones,eventos,precios,registros,comandos,acceso,responsables,autoridadFisica,titularidad,suficiencia,
                reglaSuficiencia,condiciones,atributosFisicos,dinamicas,transiciones,json,mapper,binarios,entidades,Clock.system(LIMA),cambiosComerciales,seguimientos,constancias,pactos);
    }
    EncargoServiceImpl(CaptacionRepository encargos,PropiedadRepository propiedades,
            DetalleAgenteRepository agentes,DocumentoEncargoRepository documentosEncargo,
            ArchivoEncargoRepository archivos,CargaArchivoContextualRepository cargas,com.controllocal.service.archivo.AccesoArchivoService accesoArchivos,PeticionRevisionEncargoRepository peticiones,
            SolicitudFinalizacionEncargoRepository solicitudes,RevisionEncargoRepository revisiones,
            ComposicionEncargoRepository composiciones,EventoDominioRepository eventos,PrecioPropiedadRepository precios,
            ComandoIdempotenteRepository registros,ComandosIdempotentes comandos,AccesoEncargo acceso,ReglasDeResponsableEncargo responsables,
            AutoridadDePropiedad autoridadFisica,TitularParaEncargar titularidad,
            SuficienciaRegistrada suficiencia,SuficienciaInmueble reglaSuficiencia,
            CondicionesDeEncargo condiciones,AtributosGobernados atributosFisicos,CondicionesDinamicasEncargo dinamicas,Transiciones transiciones,
            Documentos json,ObjectMapper mapper,DocumentosEncargo binarios,EntityManager entidades,Clock reloj,
            org.springframework.context.ApplicationEventPublisher cambiosComerciales,
            com.controllocal.service.actividad.SeguimientoDeProspeccion seguimientos,ConstanciasDeFirmaDeEncargo constancias,PactosDeEncargo pactos) {
        this.constancias=constancias;
        this.pactos=pactos;
        this.cambiosComerciales=cambiosComerciales;
        this.seguimientos=seguimientos;
        this.encargos=encargos; this.propiedades=propiedades; this.agentes=agentes;
        this.documentosEncargo=documentosEncargo; this.archivos=archivos;this.cargas=cargas;this.accesoArchivos=accesoArchivos; this.peticiones=peticiones;
        this.solicitudes=solicitudes; this.revisiones=revisiones; this.composiciones=composiciones;
        this.eventos=eventos; this.precios=precios; this.registros=registros; this.comandos=comandos; this.acceso=acceso;this.responsables=responsables;
        this.autoridadFisica=autoridadFisica; this.titularidad=titularidad; this.suficiencia=suficiencia;
        this.reglaSuficiencia=reglaSuficiencia; this.condiciones=condiciones;this.atributosFisicos=atributosFisicos;this.dinamicas=dinamicas;
        this.transiciones=transiciones; this.json=json;
        this.mapper=mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.binarios=binarios;
        this.entidades=entidades; this.reloj=reloj;
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Apertura abrir(DatosApertura d,Actor actor) {
        if(d==null || d.idPropiedad()==null || d.operacion()==null || d.idResponsable()==null)
            throw new ReglaNegocioException("El episodio necesita inmueble, operación y responsable declarados.");
        if(!actor.esAgente() || actor.idRolOperativo()!=d.idResponsable()) throw new AccesoNoAutorizadoException();
        Propiedad p=propiedades.bloquearParaEscritura(actor.idOrganizacion(),d.idPropiedad())
                .orElseThrow(()->new NoEncontradoException("Propiedad"));
        autoridadFisica.buscarParaLectura(actor,d.idPropiedad()).orElseThrow(()->new NoEncontradoException("Propiedad"));
        if(encargos.existeEpisodioVivo(actor.idOrganizacion(),d.idPropiedad(),d.operacion().codigo()))
            throw new ReglaNegocioException("Ya existe un Encargo no terminado para "+d.operacion().name()+" en este inmueble.");
        var responsable=agentes.findById(d.idResponsable())
                .filter(a->Objects.equals(a.getOrganizacionId(),actor.idOrganizacion()))
                .orElseThrow(AccesoNoAutorizadoException::new);
        fechas(d.desde(),d.hasta());
        Captacion c=new Captacion(); c.setOrganizacionId(actor.idOrganizacion());
        c.setCodigoCaptacion(texto(d.codigo())==null?"ENC-"+UUID.randomUUID().toString().substring(0,12):d.codigo().trim());
        c.setFechaCaptacion(hoy()); c.setPropiedad(p); c.setAgente(responsable);
        c.setMotivoOperacion(d.operacion().codigo()); c.setFechaInicioVigencia(d.desde());
        c.setFechaFinVigencia(d.hasta()); c.setExclusividad(d.exclusividad()); c.setObservaciones(d.observaciones());
        var origen=d.origen()==null?new OrigenProspeccion(TipoOrigenProspeccion.DESCONOCIDO,null):d.origen();
        comprobarOrigen(origen,c.getPropiedad().getId(),actor);
        c.declararOrigenProspeccion(origen.tipo().name(),origen.prospeccion(),ahora());
        c.setCondicionEconomica(condiciones.nueva(actor.idOrganizacion(),d.operacion(),d.condiciones()));
        condiciones.vincularMensual(c.getCondicionEconomica(),configuracionMensual(c));
        transiciones.iniciar(c,Captacion.EN_PREPARACION); encargos.saveAndFlush(c);
        if(d.origen()!=null)registrarOrigen(c,actor);
        seguimientos.encargoIniciado(c.getId(),actor);
        dinamicas.aplicar(c,d.condicionesDinamicas(),List.of(),actor,procedencia(d.procedencia()));
        evento(c,"ENCARGO_ABIERTO",actor,procedencia(d.procedencia()),Map.of("idPropiedad",p.getId(),
                "idAgente",responsable.getId(),"operacion",d.operacion().name(),
                "condicionesDinamicas",dinamicas.paraDocumento(c),"versionDefinicionesCondiciones",dinamicas.versiones(c)));
        return new Apertura(c.getId(),c.getCodigoCaptacion(),c.estadoTipado().name(),c.getVersion());
    }

    private void comprobarOrigen(OrigenProspeccion origen,long propiedad,Actor actor){
        if(origen==null||origen.tipo()==null||(origen.tipo()==TipoOrigenProspeccion.PROSPECCION)!=(origen.prospeccion()!=null))
            throw new ReglaNegocioException("Declara origen conocido, directo o desconocido sin inventar una prospección.");
        if(origen.prospeccion()!=null){
            if(origen.prospeccion()<=0)throw new ReglaNegocioException("Identifica la prospección de origen.");
            var p=encargos.origenProspeccion(actor.idOrganizacion(),origen.prospeccion())
                .filter(x->Objects.equals(x.getPropiedad(),propiedad)&&Objects.equals(x.getResponsable(),actor.idRolOperativo()))
                .orElseThrow(()->new NoEncontradoException("Prospección de origen"));
            if("D".equals(p.getEstado()))throw new ReglaNegocioException("La prospección terminó; conserva su desenlace y el origen real de esta gestión.");
        }
    }
    private OrigenProspeccion origenDe(Captacion c){return new OrigenProspeccion(TipoOrigenProspeccion.valueOf(c.getTipoOrigenProspeccion()),c.getIdProspeccionOrigen());}
    private void registrarOrigen(Captacion c,Actor actor){
        var dato=mapa("origen",origenDe(c),"registradoEn",c.getOrigenRegistradoEn());
        revision(c,null,null,"DECLARAR_ORIGEN",null,dato,huella(dato),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean vincularOrigenProspeccion(long id,long prospeccion,Actor actor){
        var previo=acceso.leer(id,actor);acceso.exigirResponsable(previo,actor);
        propiedades.bloquearParaEscritura(actor.idOrganizacion(),previo.getPropiedad().getId()).orElseThrow(()->new NoEncontradoException("Propiedad"));
        var c=encargos.bloquearParaEscritura(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Encargo"));
        entidades.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);acceso.exigirResponsable(c,actor);
        comprobarOrigen(new OrigenProspeccion(TipoOrigenProspeccion.PROSPECCION,prospeccion),c.getPropiedad().getId(),actor);
        boolean principal=Objects.equals(c.getIdProspeccionOrigen(),prospeccion);
        if(TipoOrigenProspeccion.DESCONOCIDO.name().equals(c.getTipoOrigenProspeccion())){
            c.declararOrigenProspeccion(TipoOrigenProspeccion.PROSPECCION.name(),prospeccion,ahora());encargos.saveAndFlush(c);registrarOrigen(c,actor);principal=true;
        }else if(!principal)encargos.conservarAntecedenteProspeccion(actor.idOrganizacion(),id,prospeccion,actor.idRolOperativo(),ahora());
        if(principal)seguimientos.encargoIniciado(id,actor);
        return principal;
    }

    @Override @Transactional(readOnly=true)
    public Ficha consultar(long id,Actor actor) { return ficha(acceso.leer(id,actor),actor); }
    @Override @Transactional(readOnly=true)
    public OfertaAutorizada ofertaDeInvitacion(com.controllocal.service.colaboracion.AccesoFuenteDeInvitacion.Permiso permiso){
        Objects.requireNonNull(permiso,"Permiso interno de la invitación");
        var contexto=encargos.contextoActual(permiso.organizacion(),permiso.encargo()).orElseThrow(()->new NoEncontradoException("Encargo de la invitación"));
        if(contexto.getResponsable()!=permiso.responsable()||!Objects.equals(contexto.getRevisionAplicable(),permiso.revision()))throw new AccesoNoAutorizadoException();
        var c=encargos.buscarFicha(permiso.organizacion(),permiso.encargo()).orElseThrow(()->new NoEncontradoException("Encargo de la invitación"));
        boolean mismaRevision=Objects.equals(c.getIdRevisionAplicable(),permiso.revision());
        boolean habilitado=mismaRevision&&vigencia(c,hoy(),permiso.receptor(),LecturaSnapshot.INTEGRIDAD_COMPARTIDA).habilitado();
        boolean precio=habilitado&&permiso.campos().contains("ENCARGO:precio_pedido");var economica=c.getCondicionEconomica();
        var condicionesActuales=precio?condiciones.fichaParaContexto(economica,configuracionMensual(c)):null;
        return new OfertaAutorizada(c.getOrganizacionId(),c.getId(),c.getPropiedad().getId(),c.getVersion(),c.getPropiedad().getVersionFisica(),c.operacion().name(),c.getIdComposicionActual(),
                condicionesActuales==null?null:condicionesActuales.precioPedido(),condicionesActuales==null?null:condicionesActuales.monedaPrecio(),
                condicionesActuales==null?null:condicionesActuales.periodoPrecio(),habilitado);
    }
    @Override @Transactional(readOnly=true)
    public PartesNegociacion consultarPartesNegociacion(long id,long versionEncargo,LocalDate fechaHecho,Actor actor) {
        if(fechaHecho==null)throw new ReglaNegocioException("Conserva la fecha del hecho para consultar sus partes.");
        var c=acceso.leer(id,actor);
        if(c.getVersion()!=versionEncargo)throw new ConflictoException("Cambió la versión del Encargo; consulta sus partes actuales.");
        List<ParteNegociacion> partes;
        if(c.getIdDocumentoActual()!=null) {
            var d=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),c.getIdDocumentoActual())
                    .orElseThrow(()->new IllegalStateException("El documento actual del Encargo no se conserva."));
            if(!Objects.equals(d.getIdEncargo(),c.getId()))throw new IllegalStateException("El documento no pertenece al Encargo.");
            Object valor=json.comoMapa(d.getContenido()).get("partesVersionadas");
            List<Parte> declaradas=valor==null?List.of():mapper.convertValue(valor,new TypeReference<List<Parte>>(){});
            partes=declaradas.stream().map(p->new ParteNegociacion(p.titularidad(),p.persona(),p.representante(),
                    p.evidenciaTitularidad(),p.evidenciaRepresentacion(),p.alcanceRepresentacion(),
                    p.desdeRepresentacion(),p.hastaRepresentacion())).toList();
        } else {
            // Una identidad titular ya registrada no depende de haber presentado un documento del Encargo.
            // Sólo conserva referencias conocidas; no crea representantes ni acredita facultades por inferencia.
            partes=titularidad.partesConocidas(actor.idOrganizacion(),c.getPropiedad().getId(),fechaHecho).stream()
                    .map(p->new ParteNegociacion(p.getId(),p.getPersona(),null,null,null,null,null,null)).toList();
        }
        return new PartesNegociacion(c.getId(),c.getVersion(),c.getVersionContenido(),c.getIdDocumentoActual(),partes);
    }
    @Override @Transactional(readOnly=true)
    public Vigencia consultarVigencia(long id,LocalDate corte,Actor actor) {
        return vigencia(acceso.leer(id,actor),corte==null?hoy():corte,actor,LecturaSnapshot.EXPEDIENTE);
    }
    @Override @Transactional(readOnly=true)
    public void exigirHabilitado(long id,Actor actor) {
        var v=vigencia(acceso.leer(id,actor),hoy(),actor,LecturaSnapshot.EXPEDIENTE);
        if(!v.habilitado()) throw new ReglaNegocioException("El Encargo no está habilitado: "+String.join(", ",v.faltantes()));
    }
    @Override @Transactional(readOnly=true)
    public AptitudRecomendacion habilitacionParaRecomendacionInterna(long id,LocalDate corte,Actor actor) {
        Alcances.exigirOperador(actor);
        var actual=acceso.contexto(id,actor);
        var c=encargos.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Encargo"));
        entidades.refresh(c);entidades.refresh(c.getPropiedad());
        if(c.getCondicionEconomica()!=null)entidades.refresh(c.getCondicionEconomica());
        if(c.getVersion()!=actual.getVersion()||c.getAgente()==null||!Objects.equals(c.getAgente().getId(),actual.getResponsable()))
            throw new ConflictoException("El Encargo cambió durante su comprobación de integridad.");
        var v=vigencia(c,corte==null?hoy():corte,actor,LecturaSnapshot.INTEGRIDAD_L1);
        var despues=acceso.contexto(id,actor);
        if(c.getVersion()!=despues.getVersion()||!Objects.equals(c.getAgente().getId(),despues.getResponsable()))
            throw new ConflictoException("El Encargo cambió durante su comprobación de integridad.");
        return new AptitudRecomendacion(c.getId(),c.getVersion(),c.getVersionContenido(),c.getPropiedad().getId(),
                c.getPropiedad().getVersionFisica(),c.getAgente().getId(),c.operacion().name(),c.getIdComposicionActual(),v.habilitado(),v.faltantes());
    }

    @Override @Transactional(readOnly=true)
    public Composicion consultarComposicion(long id,Long versionComposicion,Actor actor) {
        Captacion c=acceso.leer(id,actor);
        var comp=versionComposicion==null?composicionActual(c):composiciones
                .findByOrganizacionIdAndIdEncargoAndVersionContenido(actor.idOrganizacion(),id,versionComposicion)
                .orElseThrow(()->new NoEncontradoException("Versión de composición del Encargo"));
        if(comp==null) throw new NoEncontradoException("Composición declarada del Encargo");
        exigirUnidades(unidades(comp),actor); return ficha(comp);
    }
    @Override
    public Composicion declararComposicion(long id,DatosComposicion d,Actor actor,ContextoSesion sesion,
                                            String clave,Procedencia procedencia) {
        if(d==null) throw new ReglaNegocioException("Debe declarar la composición.");
        return comando("DECLARAR_COMPOSICION_ENCARGO",id,d,actor,sesion,clave,procedencia,
                Composicion.class,c->acceso.exigirResponsable(c,actor),c->{
            var declarada=validarComposicion(d,actor);
            acceso.modificarContenido(c,d.versionEsperada(),actor,()->true);
            var comp=composiciones.save(ComposicionEncargo.declarar(actor.idOrganizacion(),id,
                    c.getVersionContenido(),escribir(declarada.unidades()),declarada.totalDeclarado(),declarada.moneda(),
                    requerido(declarada.procedencia(),"procedencia"),actor.idRolOperativo(),ahora()));
            c.declararComposicion(comp.getId());
            evento(c,"COMPOSICION_ENCARGO_DECLARADA",actor,procedencia,Map.of("composicion",comp.getId()));
            return ()->ficha(comp);
        });
    }

    @Override
    public Documento prepararDocumento(Preparacion d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null) throw new ReglaNegocioException("Debe aportar los datos para preparar el documento.");
        return comando("PREPARAR_DOCUMENTO_ENCARGO",d.encargo(),d,actor,sesion,clave,proc,Documento.class,
                c->{ if(c!=null) acceso.exigirResponsable(c,actor); else exigirAgente(actor); },
                c->{ var documento=preparar(d,c,actor,sesion,clave,"PREPARAR_DOCUMENTO_ENCARGO",proc); return ()->ficha(documento); });
    }
    @Override
    public Documento regenerarDocumento(long id,Preparacion d,Actor actor,ContextoSesion sesion,
                                          String clave,Procedencia proc) {
        if(d==null || !Objects.equals(d.encargo(),id))
            throw new ReglaNegocioException("La regeneración debe identificar el mismo Encargo.");
        return comando("REGENERAR_DOCUMENTO_ENCARGO",id,d,actor,sesion,clave,proc,Documento.class,
                c->acceso.exigirResponsable(c,actor),c->{
            var documento=preparar(d,c,actor,sesion,clave,"REGENERAR_DOCUMENTO_ENCARGO",proc); return ()->ficha(documento);
        });
    }
    @Override @Transactional(readOnly=true)
    public Documento consultarDocumento(long id,Actor actor) {
        var d=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
        autorizarDocumento(d,actor); return ficha(d);
    }

    @Override
    public Adenda proponerAdenda(long id,DatosAdenda d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null||d.propuesta()==null||d.anterior()==null)
            throw new ReglaNegocioException("La adenda identifica el pacto anterior y el borrador propuesto.");
        // La copia precede a los locks y a la huella: un contenedor externo no cambia el efecto después.
        DatosAdenda entrada=mapper.convertValue(mapper.convertValue(d,new TypeReference<Map<String,Object>>(){}),DatosAdenda.class);
        return comando("PROPONER_ADENDA_ENCARGO",id,entrada,actor,sesion,clave,proc,Adenda.class,
                c->acceso.exigirResponsable(c,actor),c->{
            version(c,entrada.versionEncargoEsperada());
            exigirBaseAdenda(c,entrada.anterior(),actor);
            requerido(entrada.motivo(),"motivo de la adenda");requerido(entrada.alcance(),"alcance de la adenda");
            fechas(entrada.vigenteDesde(),entrada.vigenteHasta());
            if(entrada.antecedentePropuesta()!=null)consultarAdenda(entrada.antecedentePropuesta(),actor);
            if(entrada.antecedentePropuesta()!=null&&entrada.antecedentePropuesta().encargo()!=id)
                throw new ReglaNegocioException("El antecedente debe pertenecer al mismo Encargo.");
            var preparada=componer(preparacionDeAdenda(entrada.propuesta(),c),c,actor,proc,entrada);
            var datos=new DatosAdenda(entrada.anterior(),entrada.versionEncargoEsperada(),preparada.preparacion(),
                    texto(entrada.motivo()),texto(entrada.alcance()),entrada.vigenteDesde(),entrada.vigenteHasta(),entrada.antecedentePropuesta());
            String h=huella(mapa("datos",datos,"huellaMaterial",preparada.huellaMaterial()));
            var r=revision(c,null,null,"PROPONER_ADENDA",datos.motivo(),mapa("datosAdenda",datos,
                    "versionContenidoAnterior",c.getVersionContenido(),"versionContenidoPropuesta",c.getVersionContenido()+1,
                    "huellaPropuesta",h,"huellaMaterial",preparada.huellaMaterial(),"faltantes",preparada.faltantes(),
                    "referenciasPreparacion",referenciasPreparacion(datos.propuesta(),id,null,actor),
                    "composicionMensualExplicita",entrada.propuesta().condiciones()!=null&&entrada.propuesta().condiciones().composicionMensual()!=null,
                    "procedenciaPreparacion",procedencia(proc)),h,actor);
            evento(c,"ADENDA_PROPUESTA",actor,proc,mapa("propuesta",r.getId(),"pactoAnterior",datos.anterior().revisionAprobatoria()));
            return ()->consultarAdenda(new ReferenciaAdenda(id,r.getId()),actor);
        });
    }
    @Override @Transactional(readOnly=true)
    public Adenda consultarAdenda(ReferenciaAdenda ref,Actor actor) {
        var origen=origenAdenda(ref,actor);var s=json.comoMapa(origen.getSnapshot());
        var datos=mapper.convertValue(s.get("datosAdenda"),DatosAdenda.class);
        var ultima=revisiones.ultimaDeAdenda(actor.idOrganizacion(),ref.encargo(),ref.propuesta())
                .orElseThrow(()->new IllegalStateException("Falta la secuencia de la propuesta de adenda."));
        var estado=json.comoMapa(ultima.getSnapshot());
        Long autorizacion="AUTORIZAR_BORRADOR_ADENDA".equals(ultima.getActuacion())?ultima.getId():numeroOpcional(estado,"autorizacionBorrador");
        Long activacion="APROBAR".equals(ultima.getActuacion())?ultima.getId():null;
        var resultado=new Adenda(ref,datos.anterior(),ultima.getId(),numero(s,"versionContenidoAnterior"),
                numero(s,"versionContenidoPropuesta"),Objects.toString(s.get("huellaPropuesta")),datos.propuesta(),
                datos.motivo(),datos.alcance(),datos.vigenteDesde(),datos.vigenteHasta(),autorizacion,
                numeroOpcional(estado,"documento"),ultima.getIdPeticion(),activacion,
                mapper.convertValue(s.get("faltantes"),new TypeReference<List<String>>(){}));
        exigirLecturaAdenda(resultado,actor);return resultado;
    }
    @Override @Transactional(readOnly=true)
    public DecisionBorradorAdenda validarDecisionBorradorAdenda(ReferenciaAdenda ref,DecisionBorradorAdenda d,Actor actor) {
        var c=acceso.leer(ref.encargo(),actor);acceso.exigirDecision(c,actor);
        comprobarBorradorAdenda(c,consultarAdenda(ref,actor),d,actor);
        return new DecisionBorradorAdenda(decision(d.actuacion()),d.ultimaRevisionEsperada(),d.versionEncargoEsperada(),
                d.huellaPropuesta(),texto(d.observaciones()));
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaDecisionBorradorAdenda(ReferenciaAdenda ref,DecisionBorradorAdenda d,Actor actor) {
        var c=acceso.leer(ref.encargo(),actor);acceso.exigirDecision(c,actor);
        var origen=origenAdenda(ref,actor);var s=json.comoMapa(origen.getSnapshot());
        var datos=mapper.convertValue(s.get("datosAdenda"),DatosAdenda.class);
        autorizarReferenciasPreparacion(mapaReferencias(s.get("referenciasPreparacion")),datos.propuesta(),ref.encargo(),actor);
        autorizarReferenciasPreparacion(mapaReferencias(s.get("referenciasPreparacion")),datos.propuesta(),ref.encargo(),actor);
        pactos.consultarPactoFirmado(datos.anterior(),actor);exigirUnidades(lista(datos.propuesta().unidades()),actor);
        if(d==null||!Objects.equals(s.get("huellaPropuesta"),d.huellaPropuesta()))
            throw new ConflictoException("La revisión humana no identifica el borrador original de la adenda.");
    }
    @Override @Transactional(readOnly=true)
    public Optional<Adenda> consultarResultadoDecisionBorradorAdenda(String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        return comandos.consultarResultado(actor,clave,"AUTORIZAR_BORRADOR_ADENDA").map(c->{
            var a=restaurar(c.getResultado(),Adenda.class,actor);
            acceso.exigirDecision(acceso.leer(a.referencia().encargo(),actor),actor);return a;
        });
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Adenda autorizarBorradorAdenda(ReferenciaAdenda ref,DecisionBorradorAdenda d,Actor actor,
                                         ContextoSesion sesion,String clave,Procedencia proc) {
        if(ref==null||d==null)throw new ReglaNegocioException("Identifica el borrador y la decisión competente.");
        return comando("AUTORIZAR_BORRADOR_ADENDA",ref.encargo(),mapa("adenda",ref,"decision",d),actor,sesion,clave,proc,
                Adenda.class,c->acceso.exigirDecision(c,actor),c->{
            var a=consultarAdenda(ref,actor);comprobarBorradorAdenda(c,a,d,actor);
            String accion=decision(d.actuacion());Long documento=null;
            if("APROBAR".equals(accion)) {
                var preparado=componer(preparacionDeAdenda(a.propuesta(),c),c,actor,procedenciaAdenda(ref,actor),datosAdenda(ref,actor));
                String original=Objects.toString(json.comoMapa(origenAdenda(ref,actor).getSnapshot()).get("huellaMaterial"));
                if(!original.equals(preparado.huellaMaterial()))throw new ConflictoException("Cambió el contenido del borrador; conserva una propuesta nueva.");
                documento=guardarDocumento(preparado,c,a.versionContenidoPropuesta(),actor,sesion,clave,"AUTORIZAR_BORRADOR_ADENDA").getId();
            }
            var r=revision(c,null,null,"APROBAR".equals(accion)?"AUTORIZAR_BORRADOR_ADENDA":accion+"_BORRADOR_ADENDA",
                    d.observaciones(),mapa("adenda",ref,"documento",documento,"huellaPropuesta",a.huellaPropuesta(),
                    "pactoAnterior",a.anterior(),"decisionBorrador",d,
                    "referenciasPreparacion",referenciasPreparacion(a.propuesta(),ref.encargo(),documento,actor)),a.huellaPropuesta(),actor);
            evento(c,"ADENDA_BORRADOR_REVISADO",actor,proc,mapa("propuesta",ref.propuesta(),"revision",r.getId(),"actuacion",accion));
            return ()->consultarAdenda(ref,actor);
        });
    }
    private RevisionEncargo origenAdenda(ReferenciaAdenda ref,Actor actor) {
        if(ref==null)throw new ReglaNegocioException("Identifica la propuesta de adenda.");
        acceso.exigirLectura(ref.encargo(),actor);
        return revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),ref.propuesta())
                .filter(r->Objects.equals(r.getIdEncargo(),ref.encargo())&&"PROPONER_ADENDA".equals(r.getActuacion()))
                .orElseThrow(()->new NoEncontradoException("Propuesta de adenda"));
    }
    private void exigirBaseAdenda(Captacion c,PactosDeEncargo.ReferenciaPacto anterior,Actor actor) {
        if(!Captacion.esVivo(c.estadoActual()))throw new ReglaNegocioException("El episodio ya terminó.");
        if(anterior==null||anterior.encargo()!=c.getId()||!Objects.equals(c.getIdRevisionAplicable(),anterior.revisionAprobatoria()))
            throw new ConflictoException("La adenda no parte del instrumento aplicable observado.");
        pactos.consultarPactoFirmado(anterior,actor);
    }
    private Preparacion preparacionDeAdenda(Preparacion p,Captacion c) {
        if(p.encargo()!=null&&!Objects.equals(p.encargo(),c.getId())
                ||p.idPropiedad()!=null&&!Objects.equals(p.idPropiedad(),c.getPropiedad().getId()))
            throw new ReglaNegocioException("La adenda conserva el Encargo y el inmueble de su instrumento anterior.");
        return new Preparacion(c.getId(),c.getVersion(),c.getPropiedad().getId(),p.fisicos(),p.partes(),p.operacion(),
                p.unidades(),p.condiciones(),p.desde(),p.hasta(),p.exclusividad(),p.plantillaVersion(),p.condicionesDinamicas());
    }
    private void comprobarBorradorAdenda(Captacion c,Adenda a,DecisionBorradorAdenda d,Actor actor) {
        if(d==null||d.ultimaRevisionEsperada()==null||d.versionEncargoEsperada()==null)
            throw new ReglaNegocioException("La decisión necesita las versiones observadas de propuesta y Encargo.");
        version(c,d.versionEncargoEsperada());exigirBaseAdenda(c,a.anterior(),actor);
        if(d.ultimaRevisionEsperada()!=a.ultimaRevision()||!Objects.equals(d.huellaPropuesta(),a.huellaPropuesta()))
            throw new ConflictoException("Cambió el borrador de adenda observado.");
        var ultima=revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),a.ultimaRevision()).orElseThrow();
        if(!"PROPONER_ADENDA".equals(ultima.getActuacion()))
            throw new ConflictoException("El borrador ya recibió una decisión; continúa su instrumento o registra otra propuesta vinculada.");
        if(!"APROBAR".equals(decision(d.actuacion())))requerido(d.observaciones(),"observaciones de la decisión");
    }
    private void exigirLecturaAdenda(Adenda a,Actor actor) {
        acceso.exigirLectura(a.referencia().encargo(),actor);pactos.consultarPactoFirmado(a.anterior(),actor);
        exigirUnidades(lista(a.propuesta().unidades()),actor);
        var inicial=json.comoMapa(origenAdenda(a.referencia(),actor).getSnapshot());
        autorizarReferenciasPreparacion(mapaReferencias(inicial.get("referenciasPreparacion")),a.propuesta(),a.referencia().encargo(),actor);
        if(a.documento()!=null) {
            if(a.revisionAutorizacionBorrador()==null)throw new IllegalStateException("El documento no conserva autorización del borrador.");
            var autorizacion=revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),a.revisionAutorizacionBorrador())
                    .filter(r->Objects.equals(r.getIdEncargo(),a.referencia().encargo())&&"AUTORIZAR_BORRADOR_ADENDA".equals(r.getActuacion()))
                    .orElseThrow(()->new IllegalStateException("Falta la autorización original del borrador."));
            var s=json.comoMapa(autorizacion.getSnapshot());
            if(!a.referencia().equals(mapper.convertValue(s.get("adenda"),ReferenciaAdenda.class))
                    ||!Objects.equals(a.documento(),numeroOpcional(s,"documento")))
                throw new IllegalStateException("El documento no pertenece a la autorización de esta propuesta.");
            autorizarReferenciasPreparacion(mapaReferencias(s.get("referenciasPreparacion")),a.propuesta(),a.referencia().encargo(),actor);
        }
    }
    private Map<String,Object> referenciasPreparacion(Preparacion p,long encargo,Long documento,Actor actor) {
        Set<UUID> ids=new TreeSet<>();
        for(var parte:lista(p.partes())) {
            if(parte.evidenciaTitularidad()!=null)ids.add(parte.evidenciaTitularidad());
            if(parte.evidenciaRepresentacion()!=null)ids.add(parte.evidenciaRepresentacion());
        }
        DocumentoEncargo doc=documento==null?null:documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),documento)
                .orElseThrow(()->new NoEncontradoException("Documento de la adenda"));
        if(doc!=null){autorizarDocumento(doc,actor);ids.add(doc.getArchivoRef());}
        List<ArchivoFirma> refs=ids.stream().map(ref->{
            var actual=archivoDePreparacion(ref,encargo,actor);return new ArchivoFirma(ref,actual.getVersion(),actual.getHuella());
        }).toList();
        Map<String,Object> m=mapa("versionContrato","REFERENCIAS-ADENDA-C5-1","encargo",encargo,
                "huellaPreparacion",huellaPreparacionReferenciada(p),"documento",documento,
                "versionDocumento",doc==null?null:doc.getVersion(),"huellaDocumento",doc==null?null:doc.getHuella(),"archivos",refs);
        m.put("huellaReferencias",huella(m));return mapper.convertValue(m,new TypeReference<>(){});
    }
    private ArchivoEncargoRepository.ReferenciaActual archivoDePreparacion(UUID ref,long encargo,Actor actor) {
        var permiso=accesoArchivos.resolver(ref.toString(),actor);
        var actual=archivos.actual(actor.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Archivo del borrador"));
        if(!"ARCHIVO_ENCARGO".equals(permiso.tipo())||permiso.idReferencia()!=actual.getId()
                ||actual.getEncargo()!=null&&!Objects.equals(actual.getEncargo(),encargo))throw new AccesoNoAutorizadoException();
        if(!Objects.equals(actual.getHuella(),permiso.huellaContenido()))throw new ConflictoException("Cambió el archivo del borrador.");
        return actual;
    }
    private void autorizarReferenciasPreparacion(Map<String,Object> refs,Preparacion p,long encargo,Actor actor) {
        if(refs==null)throw new ReglaNegocioException("Faltan las referencias históricas exactas de la propuesta de adenda.");
        Map<String,Object> material=new LinkedHashMap<>(refs);Object hash=material.remove("huellaReferencias");
        if(!"REFERENCIAS-ADENDA-C5-1".equals(material.get("versionContrato"))||numero(material,"encargo")!=encargo
                ||!Objects.equals(hash,huella(material))||!Objects.equals(material.get("huellaPreparacion"),huellaPreparacionReferenciada(p)))
            throw new IllegalStateException("Las referencias no conservan la propuesta original.");
        if(!(material.get("archivos") instanceof List<?> archivosOriginales))throw new IllegalStateException("Faltan las referencias del borrador.");
        for(Object item:archivosOriginales) {
            ArchivoFirma esperado=mapper.convertValue(item,ArchivoFirma.class);
            var actual=archivoDePreparacion(esperado.referencia(),encargo,actor);
            if(actual.getVersion()!=esperado.version()||!Objects.equals(actual.getHuella(),esperado.huella()))
                throw new ConflictoException("El archivo original de la propuesta cambió de versión.");
        }
        Long id=numeroOpcional(material,"documento");
        if(id!=null) {
            var d=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Documento de la adenda"));
            autorizarDocumento(d,actor);
            if(d.getVersion()!=numero(material,"versionDocumento")||!Objects.equals(d.getHuella(),material.get("huellaDocumento")))
                throw new ConflictoException("El documento original de la adenda cambió de versión.");
        }
    }
    private static Long numeroOpcional(Map<String,?> m,String campo) { return m.get(campo)==null?null:numero(m,campo); }
    /** Misma representación durable: un número de Dato no cambia de identidad al leer su JSON. */
    private String huellaPreparacionReferenciada(Preparacion p) {
        return huellaMaterialAdenda(mapa("preparacion",p));
    }
    private String huellaMaterialAdenda(Map<String,?> contenido) {
        return huella(json.comoMapa(escribir(ordenarConjuntos(contenido))));
    }

    @Override
    public CargaTemporal cargarArchivo(CargaArchivo d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null)throw new ReglaNegocioException("Debe aportar la carga y su contexto previsto.");
        finalidadDocumental(d.finalidad());contextoDocumental(d.encargo(),d.documento());
        if(d.contenido()==null || d.contenido().length==0)
            throw new ReglaNegocioException("El archivo no tiene contenido.");
        String huellaBinaria=huella(d.contenido());
        Map<String,Object> entrada=mapa("encargo",d.encargo(),"documento",d.documento(),"finalidad",d.finalidad(),
                "versionDestino",d.versionDestino(),"nombre",d.nombre(),"contentType",d.contentType(),"huella",huellaBinaria,"tamano",d.contenido().length);
        return comando("CARGAR_ARCHIVO_CONTEXTUAL",d.encargo(),entrada,actor,sesion,clave,proc,CargaTemporal.class,
                c->autorizarDestino(c,d.documento(),actor),c->{
            validarVersionDestino(c,d.documento(),d.versionDestino(),actor);
            var origen=new DocumentosEncargo.Origen(operacionArchivo("CARGAR_ARCHIVO_CONTEXTUAL",actor,clave),actor,sesion,
                    "EVIDENCIA_CONTEXTO_ENCARGO_C6",c!=null?"ENCARGO:"+c.getId():"DOCUMENTO:"+d.documento(),d.finalidad(),huella(entrada));
            var b=binarios.guardar(origen,requerido(d.nombre(),"nombre del archivo"),
                    requerido(d.contentType(),"tipo del archivo"),d.contenido());
            validarVersionDestino(c,d.documento(),d.versionDestino(),actor);
            var a=cargas.save(CargaArchivoContextual.cargar(actor.idOrganizacion(),UUID.randomUUID(),actor.idRolOperativo(),
                    d.encargo(),d.documento(),d.finalidad(),d.versionDestino(),b.claveProveedor(),b.nombre(),b.contentType(),
                    huellaBinaria,d.contenido().length,escribir(proc),ahora()));
            return ()->new CargaTemporal(a.getReferencia(),a.getEncargo(),a.getDocumento(),a.getFinalidad(),a.getVersionDestino(),
                    a.getNombre(),a.getContentType(),a.getHuella(),a.getVersion());
        });
    }
    @Override
    public ArchivoRegistrado adjuntarOReutilizar(VinculoArchivo d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc){
        if(d==null||d.referenciaOrigen()==null)throw new ReglaNegocioException("Debe identificar el original autorizado.");
        finalidadDocumental(d.finalidad());contextoDocumental(d.encargo(),d.documento());
        var origen=new java.util.concurrent.atomic.AtomicReference<com.controllocal.service.archivo.AccesoArchivoService.ReferenciaAutorizada>();
        return comando("ADJUNTAR_REUTILIZAR_DOCUMENTO",d.encargo(),d,actor,sesion,clave,proc,ArchivoRegistrado.class,
                c->{autorizarDestino(c,d.documento(),actor);origen.set(accesoArchivos.resolver(d.referenciaOrigen().toString(),actor));},c->{
            validarVersionDestino(c,d.documento(),d.versionDestino(),actor);
            var fuente=origen.get();var b=binarios.abrir(fuente.claveProveedor()).orElseThrow(()->new NoEncontradoException("Contenido del archivo"));
            String integridad=accesoArchivos.verificarContenido(fuente,b.contenido());
            accesoArchivos.revalidar(fuente,actor);validarVersionDestino(c,d.documento(),d.versionDestino(),actor);
            String huellaOrigen=huella(mapa("origen",fuente));
            var a=archivos.save(ArchivoEncargo.registrar(actor.idOrganizacion(),UUID.randomUUID(),d.encargo(),
                    d.documento(),actor.idRolOperativo(),d.finalidad(),fuente.claveProveedor(),b.nombre(),b.contentType(),integridad,ahora())
                    .conOrigen(d.referenciaOrigen(),fuente.tipo(),huellaOrigen,d.versionDestino()));
            if(c!=null)evento(c,"DOCUMENTO_ENCARGO_VINCULADO",actor,proc,mapa("archivo",a.getArchivoRef(),"origen",d.referenciaOrigen(),"finalidad",d.finalidad(),"versionDestino",d.versionDestino()));
            return ()->new ArchivoRegistrado(a.getArchivoRef(),a.getIdEncargo(),a.getIdDocumento(),
                    a.getFinalidad(),a.getNombre(),a.getContentType(),a.getHuella(),a.getVersion());
        });
    }
    private static void finalidadDocumental(String finalidad){
        if(finalidad==null||!Set.of(ArchivoEncargo.ACEPTACION,ArchivoEncargo.TITULARIDAD,ArchivoEncargo.REPRESENTACION,
                ArchivoEncargo.FIN,ArchivoEncargo.HITO,ArchivoEncargo.COBRO).contains(finalidad))
            throw new ReglaNegocioException("Debe declarar una finalidad documental de evidencia definida.");
    }
    private static void contextoDocumental(Long encargo,Long documento){
        if(encargo==null&&documento==null)throw new ReglaNegocioException("La carga necesita un contexto documental existente.");
    }
    private void autorizarDestino(Captacion c,Long documento,Actor actor){
        exigirAgente(actor);if(c!=null)acceso.exigirResponsable(c,actor);
        if(documento!=null){
            var d=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),documento).orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
            autorizarDocumento(d,actor);
            if(d.getIdEncargo()!=null&&(c==null||!d.getIdEncargo().equals(c.getId())))
                throw new ConflictoException("El documento tiene un Encargo; identifica ese contexto y su versión actual.");
            if(d.getIdEncargo()==null&&!Objects.equals(d.getIdAutor(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        }
    }
    private void validarVersionDestino(Captacion c,Long documento,long versionEsperada,Actor actor){
        autorizarDestino(c,documento,actor);
        if(c!=null)version(c,versionEsperada);
        else {
            var actual=documentosEncargo.contextoActual(actor.idOrganizacion(),documento).orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
            if(actual.getVersion()!=versionEsperada)throw new ConflictoException("La versión del contexto documental cambió.");
        }
    }
    @Override @Transactional(readOnly=true)
    public Peticion consultarPeticion(long id,Actor actor) {
        long encargo=peticiones.encargoDe(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        var c=acceso.leer(encargo,actor);
        var p=peticiones.findByOrganizacionIdAndId(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        return ficha(p,c,actor);
    }
    @Override
    public Peticion presentar(long id,Presentacion d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        return comando("PRESENTAR_PETICION_ENCARGO",id,d,actor,sesion,clave,proc,Peticion.class,
                c->acceso.exigirResponsable(c,actor),c->{
            version(c,d.versionEsperada());
            if(!Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
            if(c.getIdPeticionActual()!=null) {
                var actual=peticion(c.getIdPeticionActual(),actor);
                if(Set.of(PeticionRevisionEncargo.PENDIENTE,PeticionRevisionEncargo.OBSERVADA).contains(actual.estadoActual()))
                    throw new ConflictoException("El Encargo ya tiene una petición pendiente u observada; continúa esa petición.");
            }
            var p=PeticionRevisionEncargo.nueva(actor.idOrganizacion(),id,actor.idRolOperativo());
            presentar(c,p,d,actor,false,proc); return ()->ficha(p,c,actor);
        });
    }
    @Override
    public Peticion corregirYReenviar(long id,long versionPeticion,Presentacion d,Actor actor,
            ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null) throw new ReglaNegocioException("Debe aportar la versión corregida.");
        long encargo=peticiones.encargoDe(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        return comando("REENVIAR_PETICION_ENCARGO",encargo,Map.of("id",id,"version",versionPeticion,"datos",d),
                actor,sesion,clave,proc,Peticion.class,c->acceso.exigirResponsable(c,actor),c->{
            var p=peticion(id,actor);
            if(p.getVersion()!=versionPeticion) throw new ConflictoException("La petición cambió.");
            if(!PeticionRevisionEncargo.OBSERVADA.equals(p.estadoActual()))
                throw new ReglaNegocioException("Sólo se corrige y reenvía la petición observada.");
            version(c,d.versionEsperada()); presentar(c,p,d,actor,true,proc); return ()->ficha(p,c,actor);
        });
    }
    @Override
    @Transactional(readOnly=true)
    public Decision validarDecision(long id,Decision d,Actor actor) {
        if(d==null)throw new ReglaNegocioException("Debe indicar la decisión del Encargo.");
        long encargo=peticiones.encargoDe(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        var c=acceso.leer(encargo,actor);acceso.exigirDecision(c,actor);
        var p=peticiones.findByOrganizacionIdAndId(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        comprobarDecision(c,p,d,actor);
        return new Decision(decision(d.actuacion()),d.versionPeticion(),d.versionEncargo(),texto(d.observaciones()),d.revisionFirma());
    }
    @Override
    @Transactional(readOnly=true)
    public void exigirLecturaDecision(long id,Decision d,Actor actor) {
        if(d==null)throw new ReglaNegocioException("Faltan las referencias de la decisión.");
        long encargo=peticiones.encargoDe(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        var c=acceso.leer(encargo,actor);acceso.exigirDecision(c,actor);
        if(d.revisionFirma()!=null) {
            var r=constancias.revisionDeFirma(actor.idOrganizacion(),encargo,id,d.revisionFirma().revisionPresentacion())
                    .orElseThrow(()->new NoEncontradoException("Manifestación original de firma"));
            var m=constancias.constanciaConservada(r);
            if(m==null || !Objects.equals(m.get("huellaManifestacion"),d.revisionFirma().huellaManifestacion()))
                throw new NoEncontradoException("Manifestación original de firma");
            constancias.autorizarConstancia(m,encargo,actor,LecturaSnapshot.EXPEDIENTE);return;
        }
        var p=peticiones.findByOrganizacionIdAndId(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        if(p.getVersion()==d.versionPeticion()) {
            var observada=ficha(p,c,actor);
            autorizarPeticionOriginal(mapper.convertValue(observada,new TypeReference<>(){}),actor,
                    observada.firma()==null?referenciasPeticion(observada,actor):null);return;
        }
        // Se busca la instantánea anterior de ESA versión; no se exige el CAS actual para leerla.
        for(var r:revisiones.findAllByOrganizacionIdAndIdEncargoOrderByIdAsc(actor.idOrganizacion(),encargo)) {
            if(!Objects.equals(r.getIdPeticion(),id))continue;
            var snapshot=json.comoMapa(r.getSnapshot());Object dato=snapshot.get("peticion");
            if(!(dato instanceof Map<?,?>))continue;
            Map<String,Object> original=mapper.convertValue(dato,new TypeReference<>(){});
            if(numero(original,"id")==id && numero(original,"version")==d.versionPeticion()) {
                autorizarPeticionOriginal(original,actor,mapaReferencias(snapshot.get("referenciasPeticion")));return;
            }
        }
        throw new NoEncontradoException("Versión original de la petición");
    }
    @Override
    @Transactional(readOnly=true)
    public Optional<Peticion> consultarResultadoDecision(String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        return comandos.consultarResultado(actor,clave,"DECIDIR_PETICION_ENCARGO").map(c->{
            var resultado=restaurar(c.getResultado(),Peticion.class,actor);
            acceso.exigirDecision(acceso.leer(resultado.encargo(),actor),actor);return resultado;
        });
    }
    private FirmaPresentada comprobarDecision(Captacion c,PeticionRevisionEncargo p,Decision d,Actor actor) {
        version(c,d.versionEncargo());
        if(!Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
        if(p.getVersion()!=d.versionPeticion()) throw new ConflictoException("La petición cambió.");
        if(!PeticionRevisionEncargo.PENDIENTE.equals(p.estadoActual()))
            throw new ReglaNegocioException("La petición no está pendiente de decisión.");
        String accion=decision(d.actuacion());var firma=constancias.firmaPresentada(p,actor,LecturaSnapshot.EXPEDIENTE);
        if("APROBAR".equals(accion)) {
            var candidata=candidataDe(p,actor);
            if(candidata==null) {
                var faltantes=faltantes(c,partes(p),actor);
                if(!faltantes.isEmpty()) throw new ReglaNegocioException("Faltan requisitos para habilitar: "+String.join(", ",faltantes));
                if(p.getVersionPresentada()!=c.getVersionContenido() || !p.getHuellaCobertura().equals(huella(snapshot(c,partes(p),actor,LecturaSnapshot.EXPEDIENTE))))
                    throw new ConflictoException("La revisión no cubre la versión actual de las partes, condiciones o inmueble.");
            } else comprobarActivacionAdenda(c,p,candidata,actor);
            verificarEvidencias(c,p.getIdDocumento(),uuids(p.getEvidencias()),ArchivoEncargo.ACEPTACION,actor,false);
            constancias.exigirRevisionManuscrita(d.revisionFirma(),firma);
        } else requerido(d.observaciones(),"observaciones de la decisión");
        return firma;
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Peticion decidir(long id,Decision d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null) throw new ReglaNegocioException("Debe indicar la decisión y sus versiones.");
        long encargo=peticiones.encargoDe(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Petición del Encargo"));
        return comando("DECIDIR_PETICION_ENCARGO",encargo,Map.of("id",id,"datos",d),actor,sesion,clave,proc,
                Peticion.class,c->acceso.exigirDecision(c,actor),c->{
            var p=peticion(id,actor);FirmaPresentada firma=comprobarDecision(c,p,d,actor);
            String accion=decision(d.actuacion()); String cobertura=p.getHuellaCobertura();
            Adenda adenda=candidataDe(p,actor);
            if(adenda!=null&&"APROBAR".equals(accion))activarAdenda(c,p,adenda,actor,proc);
            String destino=switch(accion) {
                case "APROBAR"->PeticionRevisionEncargo.APROBADA;
                case "OBSERVAR"->PeticionRevisionEncargo.OBSERVADA;
                default->PeticionRevisionEncargo.RECHAZADA; };
            var peticionRevisada=ficha(p,c,actor);
            var contenidoRevision=mapa("peticion",peticionRevisada,
                    "referenciasPeticion",firma==null?referenciasPeticion(peticionRevisada,actor):null,
                    "documento",p.getIdDocumento(),"cobertura",cobertura,
                    "origenFisico",datosFisicos(c.getPropiedad(),c.operacion()),
                    "versionReglaSuficiencia",SuficienciaInmueble.VERSION_REGLA,
                    "versionDefinicionesCondiciones",dinamicas.versiones(c),
                    "inicioFuturoAcreditado","APROBAR".equals(accion)&&c.getFechaInicioVigencia().isAfter(hoy())?mapa("fecha",c.getFechaInicioVigencia(),"fuentes",huellaFuentesDeInicio(c,partes(p))):null,
                    "revisionPresentacionFirma",firma==null?null:firma.revisionPresentacion(),
                    "revisionManuscrita","APROBAR".equals(accion)?d.revisionFirma():null);
            if(adenda!=null) {
                contenidoRevision.put("adenda",adenda.referencia());contenidoRevision.put("autorizacionBorrador",adenda.revisionAutorizacionBorrador());
                contenidoRevision.put("pactoAnterior",adenda.anterior());contenidoRevision.put("huellaPropuesta",adenda.huellaPropuesta());
            }
            var r=revision(c,p.getId(),null,accion,d.observaciones(),contenidoRevision,cobertura,actor);
            p.registrarRevision(r.getId()); transiciones.aplicar(p,p.getId(),destino,actor,d.observaciones());
            if("APROBAR".equals(accion)) {
                c.registrarRevisionAplicable(r.getId());
                if(!Captacion.APROBADO.equals(c.estadoActual()))transiciones.aplicar(c,c.getId(),Captacion.APROBADO,actor,d.observaciones());
                registrarPrecioAutorizado(c,actor,proc);
                if(c.getPropiedad().getDisponibilidadComercial()==null)
                    transiciones.aplicarDisponibilidad(c.getPropiedad(),c.getPropiedad().getId(),
                            DisponibilidadComercial.DISPONIBLE,actor,"Encargo habilitado por revisión competente.");
                encargos.flush();
                if(vigencia(c,hoy(),actor,LecturaSnapshot.EXPEDIENTE).habilitado())
                    encargos.registrarPrimeraHabilitacion(c.getOrganizacionId(),c.getId(),r.getId(),hoy(),ahora());
            }
            evento(c,"PETICION_ENCARGO_DECIDIDA",actor,proc,mapa("peticion",p.getId(),"revision",r.getId(),"decision",accion,
                    "pactoFirmado","APROBAR".equals(accion)?new PactosDeEncargo.ReferenciaPacto(c.getId(),r.getId()):null,
                    "adenda",adenda==null?null:adenda.referencia(),"documento",p.getIdDocumento(),"versionContenido",p.getVersionPresentada()));
            return ()->ficha(p,c,actor);
        });
    }

    @Override
    public Ficha editar(long id,Edicion d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        if(d==null) throw new ReglaNegocioException("Debe indicar los cambios.");
        return comando("EDITAR_CONDICIONES_ENCARGO",id,d,actor,sesion,clave,proc,Ficha.class,
                c->acceso.exigirResponsable(c,actor),c->{
            requerido(d.motivo(),"motivo del cambio"); fechas(d.desde(),d.hasta());
            boolean[] cambiaron={false};
            acceso.modificarContenido(c,d.versionEsperada(),actor,()->{
                boolean material=false;
                if(d.desde()!=null && !Objects.equals(d.desde(),c.getFechaInicioVigencia())) {c.setFechaInicioVigencia(d.desde());material=true;}
                if(d.hasta()!=null && !Objects.equals(d.hasta(),c.getFechaFinVigencia())) {c.setFechaFinVigencia(d.hasta());material=true;}
                fechas(c.getFechaInicioVigencia(),c.getFechaFinVigencia());
                if(d.exclusividad()!=null && !Objects.equals(d.exclusividad(),c.getExclusividad())) {c.setExclusividad(d.exclusividad());material=true;}
                if(d.observaciones()!=null && !Objects.equals(d.observaciones(),c.getObservaciones())) {c.setObservaciones(d.observaciones());cambiaron[0]=true;}
                if(d.condiciones()!=null) {
                    var nueva=condiciones.nueva(actor.idOrganizacion(),c.operacion(),condiciones.completar(c.getCondicionEconomica(),d.condiciones()));
                    if(d.condiciones().composicionMensual()==null) {
                        // Una edición parcial no vuelve a acreditar los cargos de otra base económica.
                        nueva.setComposicionMensual(c.getCondicionEconomica()==null?null:c.getCondicionEconomica().getComposicionMensual());
                    } else condiciones.vincularMensual(nueva,configuracionMensual(c));
                    if(!huella(mapa("condicion",condiciones.fichaParaContexto(nueva,configuracionMensual(c)))).equals(
                            huella(mapa("condicion",condiciones.fichaParaContexto(c.getCondicionEconomica(),configuracionMensual(c)))))) {
                        c.setCondicionEconomica(nueva);material=true;
                    }
                }
                material|=dinamicas.aplicar(c,d.atributos(),d.atributosABorrar(),actor,proc);
                cambiaron[0]|=material; return material;
            });
            if(cambiaron[0]) evento(c,"CONDICIONES_ENCARGO_EDITADAS",actor,proc,Map.of("versionContenido",c.getVersionContenido()));
            return ()->ficha(c,actor);
        });
    }

    @Override
    public SolicitudFin solicitarFin(long id,DatosFin d,Long observada,Long versionSolicitud,Actor actor,
            ContextoSesion sesion,String clave,Procedencia proc) {
        Map<String,Object> entrada=new LinkedHashMap<>(); entrada.put("datos",d); entrada.put("observada",observada);
        entrada.put("versionSolicitud",versionSolicitud);
        return comando("SOLICITAR_FIN_ENCARGO",id,entrada,actor,sesion,clave,proc,SolicitudFin.class,
                c->acceso.exigirResponsable(c,actor),c->{
            validarFin(c,d); SolicitudFinalizacionEncargo s;
            if(observada==null) {
                if(solicitudes.findFirstByOrganizacionIdAndIdEncargoAndEstadoInOrderByIdAsc(
                        actor.idOrganizacion(),id,List.of(SolicitudFinalizacionEncargo.PENDIENTE,
                                SolicitudFinalizacionEncargo.OBSERVADA)).isPresent())
                    throw new ConflictoException("El episodio ya tiene una solicitud pendiente u observada; continúa la misma solicitud.");
                s=SolicitudFinalizacionEncargo.nueva(actor.idOrganizacion(),id,actor.idRolOperativo(),ahora());
                transiciones.iniciar(s,SolicitudFinalizacionEncargo.PENDIENTE);
            } else {
                s=solicitud(observada,actor);
                if(!Objects.equals(s.getIdEncargo(),id) || !Objects.equals(s.getIdSolicitante(),actor.idRolOperativo()))
                    throw new AccesoNoAutorizadoException();
                if(versionSolicitud==null || s.getVersion()!=versionSolicitud)
                    throw new ConflictoException("La solicitud cambió.");
                if(!SolicitudFinalizacionEncargo.OBSERVADA.equals(s.estadoActual()))
                    throw new ReglaNegocioException("Sólo se reenvía la solicitud observada.");
                transiciones.aplicar(s,s.getId(),SolicitudFinalizacionEncargo.PENDIENTE,actor,d.motivo());
            }
            verificarEvidencias(c,null,d.evidencias(),ArchivoEncargo.FIN,actor,false);
            s.solicitar(c.getVersion(),tipoFin(d.tipoFin()),d.fechaEfectiva(),d.motivo(),escribir(lista(d.evidencias())));
            solicitudes.saveAndFlush(s);
            var r=revision(c,null,s.getId(),observada==null?"SOLICITAR_FIN":"REENVIAR_FIN",d.motivo(),
                    mapa("solicitud",ficha(s,c,actor)),huella(mapa("solicitud",ficha(s,c,actor))),actor);
            s.registrarRevision(r.getId()); evento(c,"FIN_ENCARGO_SOLICITADO",actor,proc,Map.of("solicitud",s.getId()));
            return ()->ficha(s,c,actor);
        });
    }
    @Override @Transactional(readOnly=true)
    public SolicitudFin consultarSolicitudFin(long id,Actor actor) {
        var s=solicitudes.findByOrganizacionIdAndId(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Solicitud de finalización"));
        var c=acceso.leer(s.getIdEncargo(),actor); return ficha(s,c,actor);
    }
    @Override
    public SolicitudFin cancelarSolicitudFin(long id,long version,String motivo,Actor actor,
            ContextoSesion sesion,String clave,Procedencia proc) {
        long encargo=solicitudes.encargoDe(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Solicitud de finalización"));
        return comando("CANCELAR_SOLICITUD_FIN_ENCARGO",encargo,mapa("id",id,"version",version,"motivo",motivo),
                actor,sesion,clave,proc,SolicitudFin.class,c->acceso.exigirResponsable(c,actor),c->{
            var s=solicitud(id,actor);
            if(!Objects.equals(s.getIdSolicitante(),actor.idRolOperativo())) throw new AccesoNoAutorizadoException();
            if(s.getVersion()!=version) throw new ConflictoException("La solicitud cambió.");
            if(!Set.of(SolicitudFinalizacionEncargo.PENDIENTE,SolicitudFinalizacionEncargo.OBSERVADA).contains(s.estadoActual()))
                throw new ReglaNegocioException("La solicitud ya tiene una decisión aplicada o fue cancelada.");
            requerido(motivo,"motivo de cancelación");
            transiciones.aplicar(s,id,SolicitudFinalizacionEncargo.CANCELADA,actor,motivo);
            var r=revision(c,null,id,"CANCELAR_FIN",motivo,mapa("solicitud",ficha(s,c,actor)),huella(mapa("solicitud",ficha(s,c,actor))),actor);
            s.registrarRevision(r.getId()); evento(c,"SOLICITUD_FIN_ENCARGO_CANCELADA",actor,proc,Map.of("solicitud",id));
            return ()->ficha(s,c,actor);
        });
    }
    @Override
    public SolicitudFin decidirSolicitudFin(long id,Decision d,Actor actor,ContextoSesion sesion,
            String clave,Procedencia proc) {
        if(d==null) throw new ReglaNegocioException("Debe indicar la decisión y sus versiones.");
        long encargo=solicitudes.encargoDe(actor.idOrganizacion(),id)
                .orElseThrow(()->new NoEncontradoException("Solicitud de finalización"));
        return comando("DECIDIR_SOLICITUD_FIN_ENCARGO",encargo,mapa("id",id,"datos",d),actor,sesion,clave,proc,
                SolicitudFin.class,c->acceso.exigirDecision(c,actor),c->{
            var s=solicitud(id,actor); version(c,d.versionEncargo());
            if(!Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
            if(s.getVersion()!=d.versionPeticion()) throw new ConflictoException("La solicitud cambió.");
            if(!SolicitudFinalizacionEncargo.PENDIENTE.equals(s.estadoActual()))
                throw new ReglaNegocioException("La solicitud no está pendiente.");
            String accion=decision(d.actuacion());
            if("APROBAR".equals(accion) && s.getVersionEncargo()!=c.getVersion())
                throw new ConflictoException("El episodio cambió después de la solicitud.");
            if("APROBAR".equals(accion)) {
                DatosFin fin=new DatosFin(s.getTipoFin(),s.getFechaSolicitada(),s.getMotivo(),c.getVersion(),uuids(s.getEvidencias()));
                aplicarFin(c,fin,actor,proc);
            } else requerido(d.observaciones(),"observaciones de la decisión");
            String destino=switch(accion) { case "APROBAR"->SolicitudFinalizacionEncargo.APROBADA;
                case "OBSERVAR"->SolicitudFinalizacionEncargo.OBSERVADA; default->SolicitudFinalizacionEncargo.RECHAZADA; };
            var r=revision(c,null,id,accion+"_FIN",d.observaciones(),mapa("solicitud",ficha(s,c,actor)),huella(mapa("solicitud",ficha(s,c,actor))),actor);
            s.registrarRevision(r.getId()); transiciones.aplicar(s,id,destino,actor,d.observaciones());
            evento(c,"SOLICITUD_FIN_ENCARGO_DECIDIDA",actor,proc,Map.of("solicitud",id,"decision",accion));
            return ()->ficha(s,c,actor);
        });
    }
    @Override
    public Ficha finalizar(long id,DatosFin d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        return comando("FINALIZAR_ENCARGO",id,d,actor,sesion,clave,proc,Ficha.class,
                c->acceso.exigirDecision(c,actor),c->{ aplicarFin(c,d,actor,proc); return ()->ficha(c,actor); });
    }

    private <T> T comando(String tipo,Long id,Object entrada,Actor actor,ContextoSesion sesion,
            String clave,Procedencia proc,Class<T> salida,Consumer<Captacion> autoridad,
            Function<Captacion,Supplier<T>> efecto) {
        if(entrada==null) throw new ReglaNegocioException("Debe indicar los datos de la actuación.");
        String k=Idempotencia.exigirClave(clave); Procedencia origen=procedencia(proc);
        var inicio=acceso.iniciar(actor,sesion,id);
        String identidad=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+k);
        registros.bloquearIdentidad(Long.parseUnsignedLong(identidad.substring(0,16),16));
        Propiedad propiedadBloqueada=null;
        if(id!=null) {
            var actual=acceso.contexto(id,actor); long propiedad=actual.getPropiedad();
            boolean aprobar="DECIDIR_PETICION_ENCARGO".equals(tipo) && entrada instanceof Map<?,?> e
                    && e.get("datos") instanceof Decision d && "APROBAR".equals(decision(d.actuacion()));
            if(aprobar) propiedadBloqueada=propiedades.bloquearParaEscritura(actor.idOrganizacion(),propiedad)
                    .orElseThrow(()->new NoEncontradoException("Propiedad"));
            else {
                encargos.bloquearInmuebleParaRevision(actor.idOrganizacion(),propiedad)
                        .orElseThrow(()->new NoEncontradoException("Propiedad"));
                propiedadBloqueada=propiedades.findById(propiedad).orElseThrow(()->new NoEncontradoException("Propiedad"));
            }
            entidades.refresh(propiedadBloqueada,aprobar?jakarta.persistence.LockModeType.PESSIMISTIC_WRITE:jakarta.persistence.LockModeType.PESSIMISTIC_READ);
            encargos.bloquearResponsableParaRevision(actor.idOrganizacion(),actual.getResponsable())
                    .orElseThrow(AccesoNoAutorizadoException::new);
            if(aprobar) {
                Set<Long> partes=new TreeSet<>();
                titularidad.partesConocidas(actor.idOrganizacion(),propiedad,hoy()).forEach(p->partes.add(p.getPersona()));
                titularidad.bloquearIdentidades(actor.idOrganizacion(),partes);
            }
        }
        Captacion c=id==null?null:acceso.escribir(id,actor,inicio); autoridad.accept(c);
        if(propiedadBloqueada!=null && !Objects.equals(c.getPropiedad().getId(),propiedadBloqueada.getId()))
            throw new ConflictoException("El objeto del Encargo cambió antes de la revisión.");
        String h=huella(mapa("contrato",1,"tipo",tipo,"actor",actor,"recurso",id,"entrada",entrada));
        Optional<com.controllocal.domain.auditoria.ComandoIdempotente> previo;
        try { previo=comandos.buscar(actor,k,tipo,h); }
        catch(ReglaNegocioException distinto) { throw new ConflictoException("La clave identifica otra entrada; no se modificó el efecto anterior."); }
        if(previo.isPresent()) {
            T original=restaurar(previo.get().getResultado(),salida,actor);
            if(original instanceof Composicion composicion) exigirUnidades(composicion.unidades(),actor);
            return original;
        }
        Supplier<T> resultado=efecto.apply(c); entidades.flush(); T dto=resultado.get();
        Map<String,Object> arbol=mapper.convertValue(dto,new TypeReference<>(){});
        Map<String,Object> sobre=mapa("version",1,"resultado",arbol,"huellaResultado",Idempotencia.huellaExacta(json.objetoCanonico(arbol)));
        if(dto instanceof Peticion p && p.firma()==null)sobre.put("referenciasPeticion",referenciasPeticion(p,actor));
        if(dto instanceof Adenda a)sobre.put("referenciasAdenda",referenciasPreparacion(a.propuesta(),a.referencia().encargo(),a.documento(),actor));
        String snapshot=json.objetoCanonico(sobre);
        Long entidad=c==null?null:c.getId();String tipoEntidad=c==null?"DOCUMENTO_ENCARGO":"CAPTACION";
        if(dto instanceof Documento d){entidad=d.id();tipoEntidad="DOCUMENTO_ENCARGO";}
        if(dto instanceof CargaTemporal a){entidad=cargas.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),a.referencia()).orElseThrow().getId();tipoEntidad="CARGA_ARCHIVO_CONTEXTUAL";}
        if(dto instanceof ArchivoRegistrado a){entidad=archivos.findByOrganizacionIdAndArchivoRef(actor.idOrganizacion(),a.referencia()).orElseThrow().getId();tipoEntidad="ARCHIVO_ENCARGO";}
        comandos.registrar(actor,k,tipo,h,tipoEntidad,entidad,origen,snapshot);
        // Primera entrega y replay usan la misma representación del resultado durable,
        // incluidas las fechas del mismo instante normalizadas por el mapper del contrato.
        return restaurar(snapshot,salida,actor);
    }

    private record ContenidoPreparado(Preparacion preparacion,Map<String,Object> contenido,List<String> faltantes,
                                       Propiedad propiedad,String huellaMaterial) { }
    private DocumentoEncargo preparar(Preparacion d,Captacion c,Actor actor,ContextoSesion sesion,String clave,String capacidad,Procedencia proc) {
        var preparado=componer(d,c,actor,proc,null);
        if(c!=null&&c.isTerminosAceptados()) {
            var anterior=c.getIdDocumentoActual()==null?null:documentosEncargo.findByOrganizacionIdAndId(c.getOrganizacionId(),c.getIdDocumentoActual()).orElse(null);
            if(anterior==null||!anterior.getHuella().equals(preparado.huellaMaterial()))
                throw new ReglaNegocioException("Cambiar lo firmado requiere autorización previa del borrador y la adenda; regenerar no la sustituye.");
        }
        return guardarDocumento(preparado,c,c==null?0:c.getVersionContenido(),actor,sesion,clave,capacidad);
    }
    private DocumentoEncargo guardarDocumento(ContenidoPreparado preparado,Captacion c,long versionContenido,Actor actor,
            ContextoSesion sesion,String clave,String capacidad) {
        UUID ref=operacionArchivo(capacidad,actor,clave);
        // La fuente Core existe en la misma TX antes de admitir bytes; no se anuncia un archivo disponible aún.
        var documento=documentosEncargo.saveAndFlush(DocumentoEncargo.preparar(actor.idOrganizacion(),ref,c==null?null:c.getId(),
                versionContenido,actor.idRolOperativo(),PLANTILLA_VERSION,preparado.huellaMaterial(),
                escribir(preparado.contenido()),escribir(preparado.faltantes()),ahora()));
        var origen=new DocumentosEncargo.Origen(ref,actor,sesion,"DOCUMENTO_ENCARGO_C6",ref.toString(),ArchivoEncargo.PDF,preparado.huellaMaterial());
        var binario=binarios.generar(origen,contenidoVisual(preparado.contenido(),preparado.faltantes(),preparado.propiedad(),actor));
        archivos.save(ArchivoEncargo.registrar(actor.idOrganizacion(),ref,c==null?null:c.getId(),documento.getId(),
                actor.idRolOperativo(),ArchivoEncargo.PDF,binario.claveProveedor(),binario.nombre(),binario.contentType(),binario.huella(),ahora()));
        return documento;
    }
    private static UUID operacionArchivo(String capacidad,Actor actor,String clave){
        String identidad="BROX-DOCUMENTO-C6\u0000"+actor.idOrganizacion()+"\u0000"+actor.idRolOperativo()+"\u0000"+capacidad+"\u0000"+Idempotencia.exigirClave(clave);
        return UUID.nameUUIDFromBytes(identidad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private ContenidoPreparado componer(Preparacion d,Captacion c,Actor actor,Procedencia proc,DatosAdenda adenda) {
        if(c!=null) version(c,d.versionEncargo());
        if(c!=null && !Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
        Propiedad p=c==null?(d.idPropiedad()==null?null:autoridadFisica.buscarParaLectura(actor,d.idPropiedad())
                .orElseThrow(()->new NoEncontradoException("Propiedad"))):c.getPropiedad();
        OperacionInmobiliaria op=d.operacion()==null?(c==null?null:c.operacion()):d.operacion();
        if(c!=null && op!=c.operacion()) throw new ReglaNegocioException("El documento debe conservar la operación del episodio.");
        DatosFisicos fisicos=d.fisicos()!=null?seleccionarFisicos(new DatosFisicos(d.fisicos().contexto(),
                suficiencia.validarDatos(actor.idOrganizacion(),d.fisicos().contexto(),d.fisicos().datos()==null?Map.of():d.fisicos().datos()))):p==null?null:datosFisicos(p,op);
        var composicion=c==null?null:composicionActual(c);
        var unidadesDeclaradas=d.unidades()!=null?lista(d.unidades()):composicion==null?List.<Unidad>of():unidades(composicion);
        boolean mismoObjetoImplicito=composicion==null&&c!=null&&unidadesDeclaradas.size()==1
                &&unidadesDeclaradas.getFirst().idPropiedad()==c.getPropiedad().getId()
                &&unidadesDeclaradas.getFirst().importeDeclarado()==null&&unidadesDeclaradas.getFirst().moneda()==null;
        // Identificar el inmueble ya ofrecido no crea una composición ni un desglose.
        var unidades=mismoObjetoImplicito?List.<Unidad>of():unidadesDeclaradas;
        boolean seleccionNueva=d.unidades()!=null&&!mismoObjetoImplicito&&!huella(mapa("unidades",unidades))
                .equals(huella(mapa("unidades",composicion==null?List.of():unidades(composicion))));
        var comerciales=c==null||seleccionNueva?ValoresGobernados.vacio():dinamicas.valores(c);
        boolean contextoDinamico=fisicos!=null&&fisicos.contexto()!=null&&op!=null;
        if(contextoDinamico&&d.condicionesDinamicas()!=null&&!d.condicionesDinamicas().isEmpty()){
            var propuesta=dinamicas.propuesta(actor.idOrganizacion(),fisicos.contexto().tipoPropiedad(),op.codigo(),d.condicionesDinamicas(),procedencia(proc));
            var mezcla=ValoresGobernados.constructor();comerciales.todos().forEach(mezcla::con);propuesta.todos().forEach(mezcla::con);comerciales=mezcla.construir();
        }
        var partes=lista(d.partes());
        var cond=d.condiciones()!=null?condiciones.ficha(condiciones.nueva(actor.idOrganizacion(),op,d.condiciones())):
                c==null||seleccionNueva?null:condiciones.fichaParaContexto(c.getCondicionEconomica(),configuracionMensual(c));
        LocalDate desde=d.desde()!=null?d.desde():c==null?null:c.getFechaInicioVigencia();
        LocalDate hasta=d.hasta()!=null?d.hasta():c==null?null:c.getFechaFinVigencia();
        Boolean exclusiva=d.exclusividad()!=null?d.exclusividad():c==null?null:c.getExclusividad();
        List<String> f=new ArrayList<>(); if(op==null) f.add("OPERACION");
        if(fisicos==null || fisicos.contexto()==null) f.add("DATOS_DEL_INMUEBLE");
        else reglaSuficiencia.evaluar(SuficienciaInmueble.Accion.CONFIRMAR_ENCARGO,fisicos.contexto(),
                contextoDinamico?dinamicas.paraSuficiencia(actor.idOrganizacion(),fisicos.contexto().tipoPropiedad(),op.codigo(),fisicos.datos(),comerciales):fisicos.datos()).faltantes().forEach(x->f.add(x.clave()));
        if(!contextoDinamico&&d.condicionesDinamicas()!=null&&!d.condicionesDinamicas().isEmpty())f.add("CONTEXTO_CONDICIONES_DINAMICAS");
        if(partes.isEmpty()) f.add("PARTES_Y_REPRESENTACION");
        if(desde==null) f.add("INICIO_VIGENCIA"); if(hasta==null) f.add("FIN_VIGENCIA");
        if(exclusiva==null) f.add("EXCLUSIVIDAD");
        f.addAll(condiciones.faltantes(condiciones.nueva(actor.idOrganizacion(),op,cond),op));
        fechas(desde,hasta);
        Map<String,Object> contenido=contenidoMaterial(fisicos,partes,op,unidades,cond,desde,hasta,exclusiva);
        agregarComposicion(contenido,seleccionNueva?null:composicion);
        if(adenda!=null&&seleccionNueva) {
            contenido.put("procedenciaComposicion",cond==null?null:cond.procedencia());
            if(unidades.isEmpty())f.add("UNIDADES_OFRECIDAS");
        }
        if(adenda!=null)contenido.put("adendaContractual",materialAdenda(adenda));
        agregarUnidades(contenido,unidades,actor,null,LecturaSnapshot.EXPEDIENTE);
        completarNombres(contenido,partes,p,actor);
        @SuppressWarnings("unchecked") var identidades=(List<Map<String,Object>>)contenido.get("partes");
        if(identidades.stream().anyMatch(parte->parte.get("nombreTitular")==null)) f.add("IDENTIFICACION_TITULAR");
        agregarProfesional(contenido,c,actor);
        contenido.put("condicionesAdicionales",!contextoDinamico?Map.of():c==null?dinamicas.paraDocumento(actor.idOrganizacion(),fisicos.contexto().tipoPropiedad(),op.codigo(),comerciales):dinamicas.paraDocumento(c,comerciales));
        // Adenda C5 atraviesa propuesta -> JSON -> revisión antes de generar el PDF.
        // Se compara su representación durable sin alterar huellas históricas C2.
        String huellaContractual=adenda==null?huella(contenido):huellaMaterialAdenda(contenido);
        contenido.put("origenFisico",fisicos);
        contenido.put("partesVersionadas",partes);
        contenido.put("versionContenidoOrigen",c==null?null:c.getVersionContenido());
        contenido.put("unidadesDeclaradasExplicitamente",d.unidades()!=null);
        contenido.put("unidadesPropuestasOrigen",d.unidades());
        contenido.put("reutilizaComposicionVigente",!seleccionNueva);
        contenido.put("composicionVigenteObservada",composicion==null?null:composicion.getId());
        contenido.put("versionComposicionObservada",composicion==null?null:composicion.getVersionContenido());
        contenido.put("metadataOrigen",origenDePropuesta(c,composicion,actor));
        contenido.put("condicionesPropuestasOrigen",d.condicionesDinamicas());
        Map<String,Long> versionesDinamicas=new TreeMap<>();
        if(c!=null&&!seleccionNueva)versionesDinamicas.putAll(dinamicas.versiones(c));
        if(contextoDinamico)versionesDinamicas.putAll(dinamicas.versiones(actor.idOrganizacion(),fisicos.contexto().tipoPropiedad(),op.codigo(),comerciales.claves()));
        contenido.put("versionDefinicionesCondiciones",versionesDinamicas);
        if(texto(d.plantillaVersion())!=null && !PLANTILLA_VERSION.equals(d.plantillaVersion()))
            throw new NoEncontradoException("Versión de plantilla de Encargo");
        Preparacion normalizada=new Preparacion(c==null?null:c.getId(),c==null?d.versionEncargo():Long.valueOf(c.getVersion()),
                p==null?d.idPropiedad():p.getId(),fisicos,partes,op,d.unidades()==null?null:unidades,cond,desde,hasta,
                exclusiva,PLANTILLA_VERSION,d.condicionesDinamicas());
        return new ContenidoPreparado(normalizada,contenido,List.copyOf(f),p,huellaContractual);
    }
    private void presentar(Captacion c,PeticionRevisionEncargo p,Presentacion d,Actor actor,
                            boolean reenvio,Procedencia proc) {
        if(!Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
        var documento=documentosEncargo.bloquear(actor.idOrganizacion(),d.documento())
                .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
        autorizarDocumento(documento,actor);
        if(documento.getIdEncargo()!=null && !Objects.equals(documento.getIdEncargo(),c.getId()))
            throw new ReglaNegocioException("El documento pertenece a otro episodio.");
        Adenda adenda=d.adenda()==null?null:consultarAdenda(d.adenda(),actor);
        if(reenvio&&!Objects.equals(adendaDe(p,actor),d.adenda()))
            throw new ConflictoException("La corrección conserva la propuesta de adenda original de la petición.");
        List<String> faltantes;
        Map<String,Object> actual;long versionPresentada;
        if(adenda==null) {
            faltantes=faltantes(c,lista(d.partes()),actor);
            actual=snapshot(c,lista(d.partes()),actor,LecturaSnapshot.EXPEDIENTE);versionPresentada=c.getVersionContenido();
            if(!documento.getHuella().equals(huella(snapshotDocumento(c,lista(d.partes()),actor,LecturaSnapshot.EXPEDIENTE))))
                throw new ConflictoException("El documento no contiene la versión actual exacta; revisa sus datos de origen.");
        } else {
            exigirBaseAdenda(c,adenda.anterior(),actor);
            if(adenda.revisionAutorizacionBorrador()==null||!Objects.equals(adenda.documento(),documento.getId())
                    ||adenda.revisionActivacion()!=null||adenda.peticion()!=null&&(!reenvio||!Objects.equals(adenda.peticion(),p.getId())))
                throw new ConflictoException("El documento no es la candidata autorizada pendiente de esta adenda.");
            var preparado=contenidoActualAdenda(c,adenda,lista(d.partes()),actor,proc);
            if(!documento.getHuella().equals(preparado.huellaMaterial()))
                throw new ConflictoException("Cambió el contenido autorizado de la candidata; no se sustituye debajo de su primera revisión.");
            validarPartes(c,lista(d.partes()),actor);faltantes=preparado.faltantes();
            actual=coberturaAdenda(adenda.referencia(),adenda.revisionAutorizacionBorrador(),adenda.versionContenidoPropuesta(),documento,lista(d.partes()));versionPresentada=adenda.versionContenidoPropuesta();
        }
        if(!faltantes.isEmpty()) throw new ReglaNegocioException("Faltan requisitos para presentar: "+String.join(", ",faltantes));
        List<FirmaManuscrita> firmas=validarFirmas(d.firmas(),lista(d.partes()));
        List<UUID> evidencias=firmas.stream().map(FirmaManuscrita::documentoFirmado).distinct().toList();
        String cobertura=huella(actual);
        verificarEvidencias(c,documento.getId(),evidencias,ArchivoEncargo.ACEPTACION,actor,true);
        verificarEvidencias(c,null,d.partes().stream().map(Parte::evidenciaTitularidad).distinct().toList(),ArchivoEncargo.TITULARIDAD,actor,true);
        verificarEvidencias(c,null,d.partes().stream().map(Parte::evidenciaRepresentacion).filter(Objects::nonNull).distinct().toList(),ArchivoEncargo.REPRESENTACION,actor,true);
        var pdf=archivos.bloquear(actor.idOrganizacion(),List.of(documento.getArchivoRef()));
        if(pdf.size()!=1 || !ArchivoEncargo.PDF.equals(pdf.getFirst().getFinalidad())
                || !Objects.equals(pdf.getFirst().getIdDocumento(),documento.getId()))
            throw new IllegalStateException("El documento perdió su vínculo privado exacto.");
        pdf.getFirst().vincular(c.getId());
        documento.vincular(c.getId());
        p.presentar(versionPresentada,documento.getId(),escribir(evidencias),escribir(d.partes()),cobertura,ahora());
        if(reenvio) transiciones.aplicar(p,p.getId(),PeticionRevisionEncargo.PENDIENTE,actor,d.respuestaObservaciones());
        else transiciones.iniciar(p,PeticionRevisionEncargo.PENDIENTE);
        peticiones.saveAndFlush(p); if(adenda==null)c.presentarDocumento(documento.getId(),p.getId());
        actual.put("origenFisico",datosFisicos(c.getPropiedad(),c.operacion()));
        actual.put("versionReglaSuficiencia",SuficienciaInmueble.VERSION_REGLA);
        actual.put("versionDefinicionesCondiciones",dinamicas.versiones(c));
        actual.put("firmaManuscrita",constanciaFirma(c,documento,versionPresentada,firmas,actor));
        var r=revisiones.save(RevisionEncargo.registrar(c.getOrganizacionId(),c.getId(),p.getId(),null,versionPresentada,
                actor.idRolOperativo(),reenvio?"REENVIAR":"PRESENTAR",d.respuestaObservaciones(),json.objetoCanonico(actual),cobertura,ahora()));
        p.registrarRevision(r.getId());
        evento(c,reenvio?"PETICION_ENCARGO_REENVIADA":"PETICION_ENCARGO_PRESENTADA",actor,proc,Map.of("peticion",p.getId(),"documento",documento.getId()));
    }
    private Adenda candidataDe(PeticionRevisionEncargo p,Actor actor) {
        var ref=adendaDe(p,actor);return ref==null?null:consultarAdenda(ref,actor);
    }
    private ReferenciaAdenda adendaDe(PeticionRevisionEncargo p,Actor actor) {
        if(p.getIdUltimaRevision()==null)return null;
        var r=revisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),p.getIdUltimaRevision())
                .filter(x->Objects.equals(x.getIdPeticion(),p.getId())&&Objects.equals(x.getIdEncargo(),p.getIdEncargo()))
                .orElseThrow(()->new IllegalStateException("Falta la revisión original de la petición."));
        Object dato=json.comoMapa(r.getSnapshot()).get("adenda");
        return dato==null?null:mapper.convertValue(dato,ReferenciaAdenda.class);
    }
    private ContenidoPreparado contenidoActualAdenda(Captacion c,Adenda a,List<Parte> partes,Actor actor,Procedencia proc) {
        var p=preparacionDeAdenda(a.propuesta(),c);
        var actual=new Preparacion(c.getId(),c.getVersion(),c.getPropiedad().getId(),datosFisicos(c.getPropiedad(),c.operacion()),
                partes,p.operacion(),p.unidades(),p.condiciones(),p.desde(),p.hasta(),p.exclusividad(),p.plantillaVersion(),p.condicionesDinamicas());
        return componer(actual,c,actor,procedenciaAdenda(a.referencia(),actor),datosAdenda(a.referencia(),actor));
    }
    private DatosAdenda datosAdenda(ReferenciaAdenda ref,Actor actor) {
        return mapper.convertValue(json.comoMapa(origenAdenda(ref,actor).getSnapshot()).get("datosAdenda"),DatosAdenda.class);
    }
    private Procedencia procedenciaAdenda(ReferenciaAdenda ref,Actor actor) {
        Object p=json.comoMapa(origenAdenda(ref,actor).getSnapshot()).get("procedenciaPreparacion");
        if(p==null)throw new IllegalStateException("Falta la procedencia original de la propuesta.");
        return mapper.convertValue(p,Procedencia.class);
    }
    private Map<String,Object> materialAdenda(DatosAdenda d) {
        return mapa("pactoAnterior",d.anterior(),"motivo",texto(d.motivo()),"alcance",texto(d.alcance()),
                "vigenteDesde",d.vigenteDesde(),"vigenteHasta",d.vigenteHasta());
    }
    private Map<String,Object> coberturaAdenda(ReferenciaAdenda ref,long autorizacion,long version,DocumentoEncargo documento,List<Parte> partes) {
        // El ID de una composición aún no activada no se inventa como parte de la prueba.
        return mapa("versionCobertura","ADENDA-C5-1","adenda",ref,"autorizacionBorrador",autorizacion,
                "documento",documento.getId(),"huellaMaterial",documento.getHuella(),"versionContenido",version,"partesVersionadas",partes);
    }
    private void comprobarActivacionAdenda(Captacion c,PeticionRevisionEncargo p,Adenda a,Actor actor) {
        exigirBaseAdenda(c,a.anterior(),actor);
        if(a.versionContenidoAnterior()!=c.getVersionContenido()||a.versionContenidoPropuesta()!=c.getVersionContenido()+1
                ||p.getVersionPresentada()!=a.versionContenidoPropuesta()||!Objects.equals(a.peticion(),p.getId())
                ||!Objects.equals(a.documento(),p.getIdDocumento())||a.revisionAutorizacionBorrador()==null||a.revisionActivacion()!=null)
            throw new ConflictoException("La candidata ya no cubre la base ni la petición observadas.");
        if(a.vigenteDesde()!=null&&a.vigenteDesde().isAfter(hoy())||a.vigenteHasta()!=null&&a.vigenteHasta().isBefore(hoy()))
            throw new ReglaNegocioException("El documento se conserva; este caso activa una versión aplicable ahora y no selecciona automáticamente alcances futuros o pasados.");
        var documento=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),p.getIdDocumento()).orElseThrow();
        var preparado=contenidoActualAdenda(c,a,partes(p),actor,procedenciaAdenda(a.referencia(),actor));
        if(!preparado.faltantes().isEmpty())throw new ReglaNegocioException("Faltan requisitos de la candidata: "+String.join(", ",preparado.faltantes()));
        validarPartes(c,partes(p),actor);
        if(!documento.getHuella().equals(preparado.huellaMaterial())||!p.getHuellaCobertura().equals(huella(coberturaAdenda(a.referencia(),a.revisionAutorizacionBorrador(),a.versionContenidoPropuesta(),documento,partes(p)))))
            throw new ConflictoException("Cambió el contenido o la cobertura revisados de la candidata.");
    }
    private void activarAdenda(Captacion c,PeticionRevisionEncargo p,Adenda a,Actor actor,Procedencia proc) {
        var doc=documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),p.getIdDocumento()).orElseThrow();
        var contenido=json.comoMapa(doc.getContenido());var propuesta=a.propuesta();
        boolean cambiaComposicion=!Boolean.TRUE.equals(contenido.get("reutilizaComposicionVigente"));
        var clavesAnteriores=cambiaComposicion?dinamicas.valores(c).claves():Set.<String>of();
        var anterior=c.getCondicionEconomica();c.renovarContenido();
        if(cambiaComposicion) {
            var unidades=lista(propuesta.unidades());
            if(unidades.isEmpty())throw new ReglaNegocioException("La composición propuesta necesita las unidades ofrecidas.");
            var comp=composiciones.save(ComposicionEncargo.declarar(c.getOrganizacionId(),c.getId(),c.getVersionContenido(),
                    escribir(unidades),null,null,requerido(Objects.toString(contenido.get("procedenciaComposicion"),null),"procedencia de la composición"),actor.idRolOperativo(),ahora()));
            c.declararComposicion(comp.getId());
        }
        var nueva=condiciones.nueva(c.getOrganizacionId(),c.operacion(),propuesta.condiciones());
        var origen=json.comoMapa(origenAdenda(a.referencia(),actor).getSnapshot());
        if(Boolean.TRUE.equals(origen.get("composicionMensualExplicita")))condiciones.vincularMensual(nueva,configuracionMensual(c));
        else nueva.setComposicionMensual(anterior==null?null:anterior.getComposicionMensual());
        c.setCondicionEconomica(nueva);c.setFechaInicioVigencia(propuesta.desde());c.setFechaFinVigencia(propuesta.hasta());c.setExclusividad(propuesta.exclusividad());
        var entradas=lista(propuesta.condicionesDinamicas());var clavesNuevas=entradas.stream().map(com.controllocal.service.PropiedadUniversalService.ValorAtributo::clave).collect(java.util.stream.Collectors.toSet());
        dinamicas.aplicar(c,entradas,clavesAnteriores.stream().filter(k->!clavesNuevas.contains(k)).toList(),actor,procedenciaAdenda(a.referencia(),actor));
        Map<String,Object> vigente=snapshotDocumento(c,partes(p),actor,LecturaSnapshot.EXPEDIENTE);
        vigente.put("adendaContractual",materialAdenda(datosAdenda(a.referencia(),actor)));
        if(!doc.getHuella().equals(huellaMaterialAdenda(vigente)))throw new ConflictoException("La materialización no coincide con el instrumento firmado; se conserva la versión anterior.");
        c.presentarDocumento(doc.getId(),p.getId());
    }

    private List<String> faltantes(Captacion c,List<Parte> partes,Actor actor) {
        return faltantes(c,partes,actor,null);
    }
    private List<String> faltantes(Captacion c,List<Parte> partes,Actor actor,LecturaVigencia leidos) { return faltantes(c,partes,actor,leidos,hoy()); }
    private List<String> faltantes(Captacion c,List<Parte> partes,Actor actor,LecturaVigencia leidos,LocalDate fechaActuacion) {
        if(leidos!=null)leidos.exigirAmbito(c);
        List<String> f=new ArrayList<>();
        var fisicos=leidos==null?datosFisicos(c.getPropiedad(),c.operacion()):leidos.fisicos();
        var comerciales=leidos==null?dinamicas.valores(c):leidos.comerciales();
        var materiales=dinamicas.paraSuficiencia(c.getOrganizacionId(),c.getPropiedad().getTipoInmueble(),c.getMotivoOperacion(),fisicos.datos(),comerciales);
        reglaSuficiencia.evaluar(SuficienciaInmueble.Accion.CONFIRMAR_ENCARGO,fisicos.contexto(),materiales)
                .faltantes().forEach(x->f.add(x.clave()));
        f.addAll(condiciones.faltantes(c.getCondicionEconomica(),c.operacion()));
        if(c.getFechaInicioVigencia()==null) f.add("INICIO_VIGENCIA");
        if(c.getFechaFinVigencia()==null) f.add("FIN_VIGENCIA");
        if(c.getExclusividad()==null) f.add("EXCLUSIVIDAD");
        if(partes.isEmpty()) f.add("PARTES_Y_REPRESENTACION");
        else validarPartes(c,partes,actor,fechaActuacion);
        return List.copyOf(f);
    }
    private void validarPartes(Captacion c,List<Parte> partes,Actor actor) { validarPartes(c,partes,actor,hoy()); }
    private void validarPartes(Captacion c,List<Parte> partes,Actor actor,LocalDate fechaActuacion) {
        List<Long> declaradas=partes.stream().map(Parte::titularidad).toList();
        if(declaradas.stream().anyMatch(Objects::isNull))
            throw new ReglaNegocioException("Debe vincular las titularidades acreditadas del inmueble.");
        var actuales=titularidad.partesParaHabilitar(c.getOrganizacionId(),c.getPropiedad().getId(),fechaActuacion,declaradas);
        Map<Long,TitularidadPropiedadRepository.ParteActual> porId=new HashMap<>();
        actuales.forEach(t->porId.put(t.getId(),t));
        var representantes=partes.stream().map(Parte::representante).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var identidades=titularidad.identidades(c.getOrganizacionId(),representantes);
        for(Parte p:partes) {
            var t=porId.get(p.titularidad());
            if(t==null || !Objects.equals(t.getPersona(),p.persona()) || p.cuota()==null || t.getCuota().compareTo(p.cuota())!=0)
                throw new ConflictoException("Las partes o cuotas no corresponden a la titularidad actual.");
            if(texto(t.getNombre())==null || (texto(p.nombreTitularDeclarado())!=null
                    && !t.getNombre().equals(p.nombreTitularDeclarado().trim())))
                throw new ConflictoException("La identificación del titular debe corresponder a la versión documentada.");
            if(p.evidenciaTitularidad()==null) throw new ReglaNegocioException("Falta sustento de la titularidad declarada.");
            if(p.representante()!=null) {
                if(p.evidenciaRepresentacion()==null || texto(p.alcanceRepresentacion())==null || p.desdeRepresentacion()==null
                        || texto(p.nombreRepresentanteDeclarado())==null
                        || p.desdeRepresentacion().isAfter(fechaActuacion) || (p.hastaRepresentacion()!=null && p.hastaRepresentacion().isBefore(fechaActuacion)))
                    throw new ReglaNegocioException("La representación necesita alcance, vigencia y documento válidos para esta actuación.");
                if(!identidades.containsKey(p.representante()) || (texto(identidades.get(p.representante()))!=null
                        && !identidades.get(p.representante()).equals(p.nombreRepresentanteDeclarado().trim())))
                    throw new ConflictoException("La identificación del representante no corresponde a la parte documentada.");
            } else if(p.evidenciaRepresentacion()!=null || texto(p.alcanceRepresentacion())!=null)
                throw new ReglaNegocioException("Un documento de representación debe identificar al representante.");
        }
        verificarEvidencias(c,null,partes.stream().map(Parte::evidenciaTitularidad).distinct().toList(),ArchivoEncargo.TITULARIDAD,actor,false);
        verificarEvidencias(c,null,partes.stream().map(Parte::evidenciaRepresentacion).filter(Objects::nonNull).distinct().toList(),ArchivoEncargo.REPRESENTACION,actor,false);
    }

    private Map<String,Object> snapshot(Captacion c,List<Parte> partes,Actor actor,LecturaSnapshot lectura) {
        return snapshot(c,partes,actor,lectura,null);
    }
    private Map<String,Object> snapshot(Captacion c,List<Parte> partes,Actor actor,LecturaSnapshot lectura,LecturaVigencia leidos) {
        Map<String,Object> s=snapshotDocumento(c,partes,actor,lectura,leidos);
        s.put("partesVersionadas",partes); s.put("versionContenido",c.getVersionContenido());
        s.put("composicionVersion",c.getIdComposicionActual());
        return s;
    }
    private Map<String,Object> snapshotDocumento(Captacion c,List<Parte> partes,Actor actor,LecturaSnapshot lectura) {
        return snapshotDocumento(c,partes,actor,lectura,null);
    }
    private Map<String,Object> snapshotDocumento(Captacion c,List<Parte> partes,Actor actor,LecturaSnapshot lectura,LecturaVigencia leidos) {
        if(leidos!=null)leidos.exigirAmbito(c);
        var comp=composicionActual(c);
        List<Unidad> unidades=comp==null?List.of():unidades(comp);
        var fisicos=leidos==null?datosFisicos(c.getPropiedad(),c.operacion()):leidos.fisicos();
        Map<String,Object> s=contenidoMaterial(fisicos,partes,c.operacion(),unidades,
                condiciones.fichaParaContexto(c.getCondicionEconomica(),configuracionMensual(c)),c.getFechaInicioVigencia(),c.getFechaFinVigencia(),c.getExclusividad());
        completarNombres(s,partes,c.getPropiedad(),actor);
        agregarProfesional(s,c,actor);
        agregarCondicionesAdicionales(s,c,leidos);
        agregarComposicion(s,comp);
        agregarUnidades(s,unidades,actor,c,lectura);
        return s;
    }
    private Map<String,Object> contenidoMaterial(DatosFisicos fisicos,List<Parte> partes,
            OperacionInmobiliaria op,List<Unidad> unidades,Condiciones cond,LocalDate desde,LocalDate hasta,Boolean exclusiva) {
        List<Map<String,Object>> identidades=partes.stream().map(p->mapa("persona",p.persona(),"cuota",p.cuota(),
                "representante",p.representante(),"alcanceRepresentacion",p.alcanceRepresentacion(),
                "desdeRepresentacion",p.desdeRepresentacion(),"hastaRepresentacion",p.hastaRepresentacion(),
                "nombreTitular",texto(p.nombreTitularDeclarado()),"nombreRepresentante",texto(p.nombreRepresentanteDeclarado()))).toList();
        Condiciones pacto=cond==null?null:new Condiciones(cond.precioPedido(),cond.monedaPrecio(),cond.periodoPrecio(),
                cond.tipoComision(),cond.baseCalculo(),cond.valorComision(),cond.monedaComision(),cond.tratamientoIgv(),
                cond.motivoSinComision(),cond.hitoDevengo(),cond.parteObligada(),cond.beneficiario(),cond.exigibilidad(),null,cond.tramos(),cond.composicionMensual(),cond.ejecucion());
        return mapa("fisicos",fisicosContractuales(fisicos),"partes",identidades,"operacion",op,"unidades",unidades,
                "condiciones",pacto,"desde",desde,"hasta",hasta,"exclusividad",exclusiva);
    }
    private DatosFisicos fisicosContractuales(DatosFisicos fisicos) {
        if(fisicos==null || fisicos.contexto()==null) return fisicos;
        var ctx=fisicos.contexto(); Map<String,SuficienciaInmueble.Aplicabilidad> aplicabilidad=new TreeMap<>();
        for(String clave:List.of(SuficienciaInmueble.POTENCIAL_O_CAMBIO_USO,SuficienciaInmueble.EXPLOTACION_COMERCIAL,
                SuficienciaInmueble.EDIFICIO_COMPLETO)) if(ctx.aplicabilidad().containsKey(clave)) aplicabilidad.put(clave,ctx.aplicabilidad().get(clave));
        Set<String> claves=new HashSet<>(List.of("direccion","distrito","interiorUnidad","referenciaInterna",
                "metraje_total","metraje_construido","area_terreno","metraje_arrendable"));
        claves.addAll(ctx.prestacionesOfrecidas());
        if("T".equals(ctx.tipoPropiedad()) || aplicabilidad.values().contains(SuficienciaInmueble.Aplicabilidad.APLICA)) claves.add("zonificacion");
        Map<String,SuficienciaInmueble.Dato> datos=new TreeMap<>();
        if(fisicos.datos()!=null) fisicos.datos().forEach((clave,dato)->{
            if(claves.contains(clave)) datos.put(clave,new SuficienciaInmueble.Dato(dato.valor(),dato.estado(),null,dato.concepto(),null));
        });
        var contexto=new SuficienciaInmueble.Contexto(ctx.tipoPropiedad(),ctx.usoOfrecido(),ctx.objetoOfrecido(),ctx.operacion(),
                aplicabilidad,ctx.prestacionesOfrecidas(),ctx.jurisdiccion());
        return new DatosFisicos(contexto,datos);
    }
    private void agregarCondicionesAdicionales(Map<String,Object> contenido,Captacion c,LecturaVigencia leidos) {
        contenido.put("condicionesAdicionales",c==null?Map.of():leidos==null?dinamicas.paraDocumento(c):dinamicas.paraDocumento(c,leidos.comerciales()));
    }
    private void agregarComposicion(Map<String,Object> contenido,ComposicionEncargo c) {
        contenido.put("totalComposicion",c==null?null:c.getTotalDeclarado());
        contenido.put("monedaComposicion",c==null?null:c.getMoneda());
        // La procedencia se conserva como evidencia, no se sustituye por un precio unitario inferido.
        contenido.put("procedenciaComposicion",c==null?null:c.getProcedencia());
    }
    private Map<String,Object> origenDePropuesta(Captacion c,ComposicionEncargo composicion,Actor actor){
        if(c==null)return Map.of();
        var base=mapa("encargo",c.getId(),"versionEncargo",c.getVersion(),"versionContenido",c.getVersionContenido(),
                "composicion",composicion==null?null:composicion.getId(),
                "versionComposicion",composicion==null?null:composicion.getVersionContenido());
        try {
            if(composicion!=null)exigirUnidades(unidades(composicion),actor);
        } catch(AccesoNoAutorizadoException denegada){
            base.put("permisoBase","PERMISO_INSUFICIENTE");return base;
        }
        base.put("permisoBase","AUTORIZADO");
        if(composicion!=null){base.put("unidades",unidades(composicion));base.put("totalDeclarado",composicion.getTotalDeclarado());
            base.put("moneda",composicion.getMoneda());base.put("procedencia",composicion.getProcedencia());}
        base.put("condiciones",condiciones.fichaParaContexto(c.getCondicionEconomica(),configuracionMensual(c)));
        base.put("condicionesDinamicas",dinamicas.paraDocumento(c));return base;
    }
    private void agregarUnidades(Map<String,Object> contenido,List<Unidad> unidades,Actor actor,Captacion encargo,LecturaSnapshot lectura) {
        Map<Long,Propiedad> porId=lectura!=LecturaSnapshot.EXPEDIENTE
                ?unidadesParaIntegridad(encargo,unidades):propiedadesDeUnidades(unidades,actor);
        contenido.put("referenciasUnidades",unidades.stream().map(u->{var p=porId.get(u.idPropiedad());
            return mapa("direccion",p.getDireccion(),"interior",p.getInteriorUnidad(),"referencia",p.getReferenciaInterna(),
                    "tipo",p.getTipoInmueble());}).toList());
    }
    private DatosFisicos datosFisicos(Propiedad p,OperacionInmobiliaria op) {
        var contexto=suficiencia.contexto(p,op==null?null:op.name(),null);
        return seleccionarFisicos(new DatosFisicos(contexto,suficiencia.datos(p)));
    }
    /** Sólo vive dentro de una evaluación de vigencia; no cruza I/O ni otra invocación. */
    private record LecturaVigencia(long organizacion,long encargo,long propiedad,OperacionInmobiliaria operacion,
            Long composicion,long versionContenido,DatosFisicos fisicos,ValoresGobernados comerciales) {
        private void exigirAmbito(Captacion c) {
            if(organizacion!=c.getOrganizacionId()||encargo!=c.getId()||propiedad!=c.getPropiedad().getId()
                    ||operacion!=c.operacion()||!Objects.equals(composicion,c.getIdComposicionActual())||versionContenido!=c.getVersionContenido())
                throw new IllegalStateException("La lectura de vigencia no corresponde al objeto actual del Encargo.");
        }
    }
    private LecturaVigencia leerVigencia(Captacion c) {
        return new LecturaVigencia(c.getOrganizacionId(),c.getId(),c.getPropiedad().getId(),c.operacion(),
                c.getIdComposicionActual(),c.getVersionContenido(),datosFisicos(c.getPropiedad(),c.operacion()),dinamicas.valores(c));
    }
    private DatosFisicos seleccionarFisicos(DatosFisicos entrada) {
        if(entrada==null || entrada.contexto()==null) return entrada;
        var contexto=entrada.contexto(); var todos=entrada.datos()==null?Map.<String,SuficienciaInmueble.Dato>of():entrada.datos();
        var evaluacion=reglaSuficiencia.evaluar(SuficienciaInmueble.Accion.CONFIRMAR_ENCARGO,contexto,todos);
        Set<String> claves=new HashSet<>(List.of("direccion","distrito","interiorUnidad","referenciaInterna"));
        evaluacion.aplicables().forEach(a->{claves.add(a.clave());claves.addAll(a.alternativas());});
        todos.keySet().stream().filter(k->k.startsWith("contexto.")).forEach(claves::add);
        claves.addAll(contexto.prestacionesOfrecidas());
        Map<String,SuficienciaInmueble.Dato> datos=new TreeMap<>();
        claves.forEach(k->{if(todos.containsKey(k)) datos.put(k,todos.get(k));});
        return new DatosFisicos(contexto,datos);
    }

    @Transactional(readOnly=true)
    public List<CaptacionRepository.InicioPendiente> iniciosPendientes(long despues) { return encargos.iniciosPendientes(hoy(),despues); }

    @Transactional
    public boolean observarInicioAprobado(long organizacion,long id) {
        encargos.bloquearInmuebleParaRevision(organizacion,encargos.contextoActual(organizacion,id).orElseThrow(()->new NoEncontradoException("Encargo")).getPropiedad());
        var c=encargos.bloquearParaEscritura(organizacion,id).orElseThrow(()->new NoEncontradoException("Encargo"));entidades.refresh(c);
        var r=c.getIdRevisionAplicable()==null?null:revisiones.findByOrganizacionIdAndId(organizacion,c.getIdRevisionAplicable()).orElse(null);
        if(r==null||!"APROBAR".equals(r.getActuacion())||c.getFechaInicioVigencia()==null||hoy().isBefore(c.getFechaInicioVigencia()))return false;
        var dato=json.comoMapa(r.getSnapshot()).get("inicioFuturoAcreditado");
        if(!(dato instanceof Map<?,?> inicio)||!c.getFechaInicioVigencia().toString().equals(Objects.toString(inicio.get("fecha"))))return false;
        var p=peticiones.findByOrganizacionIdAndId(organizacion,r.getIdPeticion()).orElseThrow(()->new IllegalStateException("Falta la petición aprobada."));
        // La caída del observador no inventa fechas: exige las mismas fuentes/versiones de aprobación.
        if(!Objects.equals(inicio.get("fuentes"),huellaFuentesDeInicio(c,partes(p))))return false;
        if(!vigencia(c,c.getFechaInicioVigencia(),null,LecturaSnapshot.INTEGRIDAD_CALENDARIO).habilitado())return false;
        return encargos.registrarPrimeraHabilitacion(organizacion,id,r.getId(),c.getFechaInicioVigencia(),ahora())==1;
    }
    private String huellaFuentesDeInicio(Captacion c,List<Parte> partes){
        var inmuebles=new TreeSet<Long>();inmuebles.add(c.getPropiedad().getId());
        if(c.getIdComposicionActual()!=null)unidades(composicionActual(c)).forEach(u->inmuebles.add(u.idPropiedad()));
        var personas=new TreeSet<Long>();personas.add(c.getAgente().getRol().getPersona().getId());
        partes.forEach(p->{personas.add(p.persona());if(p.representante()!=null)personas.add(p.representante());});
        return Idempotencia.huellaExacta(encargos.fuentesDeInicio(c.getOrganizacionId(),c.getId(),inmuebles,personas));
    }

    private Vigencia vigencia(Captacion c,LocalDate corte,Actor actor,LecturaSnapshot lectura) {
        boolean plazo=c.getFechaInicioVigencia()!=null && c.getFechaFinVigencia()!=null
                && !corte.isBefore(c.getFechaInicioVigencia()) && !corte.isAfter(c.getFechaFinVigencia());
        boolean vencido=c.getFechaFinVigencia()!=null && corte.isAfter(c.getFechaFinVigencia());
        boolean fin=c.getFechaCierre()!=null && !corte.isBefore(c.getFechaCierre());
        List<String> f=new ArrayList<>();
        if(!Captacion.APROBADO.equals(c.estadoActual())) f.add("REVISION_APROBATORIA");
        if(!plazo) f.add(vencido?"ENCARGO_VENCIDO":"VIGENCIA_ACTUAL");
        if(fin) f.add("FIN_EFECTIVO");
        if(c.getIdRevisionAplicable()==null || c.getIdPeticionActual()==null) f.add("COBERTURA_REVISION");
        else {
            var p=peticiones.findByOrganizacionIdAndId(c.getOrganizacionId(),c.getIdPeticionActual()).orElse(null);
            var r=revisiones.findByOrganizacionIdAndId(c.getOrganizacionId(),c.getIdRevisionAplicable()).orElse(null);
            if(p==null || r==null || !PeticionRevisionEncargo.APROBADA.equals(p.estadoActual())
                    || !"APROBAR".equals(r.getActuacion()) || !Objects.equals(r.getIdPeticion(),p.getId())) f.add("COBERTURA_REVISION");
            else {
                try {
                    var partes=partes(p);var leidos=leerVigencia(c);
                    f.addAll(faltantes(c,partes,actor,leidos,corte));
                    if(p.getVersionPresentada()!=c.getVersionContenido()||!coberturaVigente(c,p,r,actor,lectura,partes,leidos))f.add("VERSION_REVISADA_CAMBIO");
                    verificarEvidencias(c,p.getIdDocumento(),uuids(p.getEvidencias()),ArchivoEncargo.ACEPTACION,actor,false);
                    var firma=constancias.firmaPresentada(p,actor,lectura);
                    if(firma==null) f.add("FIRMA_MANUSCRITA_NO_ACREDITADA");
                    else {
                        var datos=json.comoMapa(r.getSnapshot());
                        var comprobacion=datos.get("revisionManuscrita")==null?null:
                                mapper.convertValue(datos.get("revisionManuscrita"),RevisionManuscrita.class);
                        constancias.exigirRevisionManuscrita(comprobacion,firma);
                        if(firma.documento()!=p.getIdDocumento() || firma.versionContenido()!=p.getVersionPresentada())
                            f.add("VERSION_FIRMA_CAMBIO");
                    }
                } catch(ReglaNegocioException|ConflictoException falta) { f.add("AUTORIZACION_O_EVIDENCIA_NO_VIGENTE"); }
            }
        }
        return new Vigencia(plazo,vencido,fin,f.isEmpty(),List.copyOf(new LinkedHashSet<>(f)),corte);
    }
    private boolean coberturaVigente(Captacion c,PeticionRevisionEncargo p,RevisionEncargo r,Actor actor,LecturaSnapshot lectura,List<Parte> partes,LecturaVigencia leidos) {
        var s=json.comoMapa(r.getSnapshot());
        if(s.get("adenda")==null)return r.getHuellaCobertura().equals(huella(snapshot(c,partes,actor,lectura,leidos)));
        var ref=mapper.convertValue(s.get("adenda"),ReferenciaAdenda.class);
        var doc=documentosEncargo.findByOrganizacionIdAndId(c.getOrganizacionId(),p.getIdDocumento())
                .orElseThrow(()->new IllegalStateException("Falta el instrumento vigente de la adenda."));
        var actual=snapshotDocumento(c,partes,actor,lectura,leidos);
        Object material=json.comoMapa(doc.getContenido()).get("adendaContractual");
        if(material==null)throw new IllegalStateException("El instrumento de adenda no conserva su alcance.");
        actual.put("adendaContractual",material);
        String cobertura=huella(coberturaAdenda(ref,numero(s,"autorizacionBorrador"),p.getVersionPresentada(),doc,partes));
        return cobertura.equals(r.getHuellaCobertura())&&cobertura.equals(p.getHuellaCobertura())&&doc.getHuella().equals(huellaMaterialAdenda(actual));
    }
    private void aplicarFin(Captacion c,DatosFin d,Actor actor,Procedencia proc) {
        validarFin(c,d); verificarEvidencias(c,null,d.evidencias(),ArchivoEncargo.FIN,actor,false);
        String destino="RESCISION".equals(tipoFin(d.tipoFin()))?Captacion.RESCINDIDO:Captacion.FINALIZADO;
        c.cerrar(d.fechaEfectiva(),"RESCISION".equals(d.tipoFin())?"R":"M",d.motivo());
        transiciones.aplicar(c,c.getId(),destino,actor,d.motivo(),d.fechaEfectiva());
        revision(c,null,null,"FINALIZAR",d.motivo(),mapa("fin",d),huella(mapa("fin",d)),actor);
        evento(c,"ENCARGO_FINALIZADO",actor,proc,mapa("tipoFin",d.tipoFin(),"fechaEfectiva",d.fechaEfectiva()));
    }
    private void validarFin(Captacion c,DatosFin d) {
        if(d==null) throw new ReglaNegocioException("Debe indicar el fin del episodio.");
        version(c,d.versionEsperada()); tipoFin(d.tipoFin()); requerido(d.motivo(),"motivo del fin");
        if(!Captacion.esVivo(c.estadoActual())) throw new ReglaNegocioException("El episodio ya terminó.");
        if(d.fechaEfectiva()==null) throw new ReglaNegocioException("El fin necesita fecha efectiva declarada.");
        if(d.fechaEfectiva().isAfter(hoy()))
            throw new ReglaNegocioException("El MVP registra finales ya efectivos; un acuerdo futuro no termina hoy el episodio.");
    }
    private DatosComposicion validarComposicion(DatosComposicion d,Actor actor) {
        if(lista(d.unidades()).isEmpty()) throw new ReglaNegocioException("Debe declarar las unidades incluidas.");
        exigirUnidades(d.unidades(),actor); requerido(d.procedencia(),"procedencia");
        if(d.totalDeclarado()!=null && (d.totalDeclarado().signum()<0 || texto(d.moneda())==null))
            throw new ReglaNegocioException("El total declarado necesita moneda y no puede ser negativo.");
        if(d.totalDeclarado()==null && texto(d.moneda())!=null) throw new ReglaNegocioException("No hay total al que atribuir esa moneda.");
        String moneda=d.moneda()==null?null:CondicionesEconomicas.moneda(d.moneda(),"del paquete");
        List<Unidad> unidades=new ArrayList<>();
        for(Unidad u:d.unidades()) {
            if(u.importeDeclarado()!=null && (u.importeDeclarado().signum()<0 || texto(u.moneda())==null || texto(u.procedencia())==null))
                throw new ReglaNegocioException("Cada desglose declarado necesita importe, moneda y fuente.");
            if(u.importeDeclarado()==null && u.moneda()!=null) throw new ReglaNegocioException("Una moneda no es un desglose.");
            unidades.add(new Unidad(u.idPropiedad(),u.importeDeclarado(),u.moneda()==null?null:
                    CondicionesEconomicas.moneda(u.moneda(),"de la unidad"),u.procedencia()));
        }
        return new DatosComposicion(List.copyOf(unidades),d.totalDeclarado(),moneda,d.procedencia(),d.versionEsperada());
    }
    private void exigirUnidades(List<Unidad> unidades,Actor actor) {
        propiedadesDeUnidades(unidades,actor);
    }
    private Map<Long,Propiedad> propiedadesDeUnidades(List<Unidad> unidades,Actor actor) {
        if(unidades.isEmpty()) return Map.of();
        var ids=unidades.stream().map(Unidad::idPropiedad).collect(java.util.stream.Collectors.toSet());
        if(ids.size()!=unidades.size() || ids.stream().anyMatch(i->i<=0)) throw new ReglaNegocioException("Las unidades deben identificarse sin repetirlas.");
        var alcance=autoridadFisica.alcanceDeLectura(actor);
        var leidas=propiedades.buscarVariasParaLectura(actor.idOrganizacion(),ids,alcance.paramRolesArray());
        if(leidas.size()!=ids.size()) throw new AccesoNoAutorizadoException();
        return leidas.stream().collect(java.util.stream.Collectors.toMap(Propiedad::getId,Function.identity()));
    }
    /** Sólo integridad del snapshot ya vinculado; no es lector de ficha ni permiso de cartera. */
    private Map<Long,Propiedad> unidadesParaIntegridad(Captacion encargo,List<Unidad> unidades) {
        if(unidades.isEmpty())return Map.of();
        if(encargo==null||encargo.getIdComposicionActual()==null)throw new AccesoNoAutorizadoException();
        var comp=composicionActual(encargo);
        if(comp==null||!Objects.equals(comp.getOrganizacionId(),encargo.getOrganizacionId())||!Objects.equals(comp.getIdEncargo(),encargo.getId())
                ||!unidades(comp).equals(unidades))throw new AccesoNoAutorizadoException();
        var ids=unidades.stream().map(Unidad::idPropiedad).collect(java.util.stream.Collectors.toSet());
        if(ids.size()!=unidades.size()||ids.stream().anyMatch(i->i==null||i<=0))throw new AccesoNoAutorizadoException();
        var leidas=entidades.createQuery("select p from Propiedad p where p.organizacionId=:org and p.id in :ids",Propiedad.class)
                .setParameter("org",encargo.getOrganizacionId()).setParameter("ids",ids).getResultList();
        if(leidas.size()!=ids.size())throw new NoEncontradoException("Unidades del Encargo");
        leidas.forEach(entidades::refresh);
        return leidas.stream().collect(java.util.stream.Collectors.toMap(Propiedad::getId,Function.identity()));
    }
    private void verificarEvidencias(Captacion c,Long documento,List<UUID> refs,String finalidad,Actor actor,boolean vincular) {
        if(refs==null || refs.isEmpty()) return;
        if(new HashSet<>(refs).size()!=refs.size()) throw new ReglaNegocioException("La misma evidencia no se repite.");
        var encontrados=vincular?archivos.bloquear(c.getOrganizacionId(),refs):archivos.findAllByOrganizacionIdAndArchivoRefIn(c.getOrganizacionId(),refs);
        if(encontrados.size()!=refs.size()) throw new NoEncontradoException("Evidencia del Encargo");
        for(var a:encontrados) {
            if(!finalidad.equals(a.getFinalidad()) || (a.getIdEncargo()!=null && !Objects.equals(a.getIdEncargo(),c.getId()))
                    || (a.getIdEncargo()==null && (actor==null || !Objects.equals(a.getIdAutor(),actor.idRolOperativo())))
                    || (documento!=null && !Objects.equals(documento,a.getIdDocumento())))
                throw new AccesoNoAutorizadoException();
            if(vincular) a.vincular(c.getId());
        }
    }
    private List<FirmaManuscrita> validarFirmas(List<FirmaManuscrita> entrada,List<Parte> partes) {
        if(entrada==null || entrada.isEmpty())throw new ReglaNegocioException("Falta el documento con firma manuscrita y sus firmantes.");
        List<FirmaManuscrita> resultado=new ArrayList<>();
        for(var firma:entrada) {
            if(firma==null || firma.firmante()==null || firma.firmante().titularidad()<=0 || firma.firmante().persona()<=0)
                throw new ReglaNegocioException("La firma debe identificar al titular o representante legitimado.");
            Parte parte=partes.stream().filter(p->Objects.equals(p.titularidad(),firma.firmante().titularidad())).findFirst()
                    .orElseThrow(()->new ReglaNegocioException("El firmante no corresponde a una parte del documento."));
            boolean titular=Objects.equals(parte.persona(),firma.firmante().persona());
            boolean representante=Objects.equals(parte.representante(),firma.firmante().persona());
            if(!titular && !representante)throw new ReglaNegocioException("Un contacto o destinatario no firma por las partes del Encargo.");
            if(firma.fechaFirma()==null || firma.fechaFirma().isAfter(hoy()))
                throw new ReglaNegocioException("Indica la fecha real no futura de la firma manuscrita.");
            if(representante && (parte.desdeRepresentacion()==null || firma.fechaFirma().isBefore(parte.desdeRepresentacion())
                    || (parte.hastaRepresentacion()!=null && firma.fechaFirma().isAfter(parte.hastaRepresentacion()))))
                throw new ReglaNegocioException("La representación no cubre la fecha declarada de firma.");
            if(firma.documentoFirmado()==null)throw new ReglaNegocioException("Falta el documento con firma manuscrita.");
            var normalizada=new FirmaManuscrita(firma.firmante(),firma.fechaFirma(),firma.documentoFirmado(),
                    requerido(firma.descripcion(),"descripción del documento firmado"));
            if(resultado.contains(normalizada))throw new ReglaNegocioException("La misma firma no se presenta dos veces.");
            resultado.add(normalizada);
        }
        var cubiertas=resultado.stream().map(f->f.firmante().titularidad()).collect(java.util.stream.Collectors.toSet());
        if(!cubiertas.equals(partes.stream().map(Parte::titularidad).collect(java.util.stream.Collectors.toSet())))
            throw new ReglaNegocioException("La firma debe cubrir las partes documentadas, mediante titular o representante legitimado.");
        return List.copyOf(resultado);
    }
    private Map<String,Object> constanciaFirma(Captacion c,DocumentoEncargo documento,long versionContenido,List<FirmaManuscrita> firmas,Actor actor) {
        List<ArchivoFirma> referencias=firmas.stream().map(FirmaManuscrita::documentoFirmado).distinct()
                .sorted().map(ref->{
                    var actual=constancias.archivoFirmaActual(ref,c.getId(),documento.getId(),actor,LecturaSnapshot.EXPEDIENTE);
                    return new ArchivoFirma(ref,actual.getVersion(),actual.getHuella());
                }).toList();
        Map<String,Object> constancia=mapa("versionContrato",CONTRATO_FIRMA,"modalidad",MODALIDAD_FIRMA,
                "documento",documento.getId(),"versionContenido",versionContenido,
                "huellaMaterial",documento.getHuella(),"firmas",firmas,"archivos",referencias);
        constancia.put("huellaManifestacion",huella(constancia));return constancia;
    }
    private Map<String,Object> referenciasPeticion(Peticion p,Actor actor) {
        List<ArchivoFirma> refs=p.evidencias().stream().distinct().sorted().map(ref->{
            var actual=constancias.archivoFirmaActual(ref,p.encargo(),p.documento(),actor,LecturaSnapshot.EXPEDIENTE);
            return new ArchivoFirma(ref,actual.getVersion(),actual.getHuella());
        }).toList();
        Map<String,Object> m=mapa("versionContrato",CONTRATO_REFERENCIAS,"id",p.id(),"encargo",p.encargo(),
                "documento",p.documento(),"version",p.version(),"archivos",refs);
        m.put("huellaReferencias",huella(m));return mapper.convertValue(m,new TypeReference<>(){});
    }
    private Map<String,Object> mapaReferencias(Object dato) {
        if(dato==null)return null;
        if(!(dato instanceof Map<?,?>))throw new IllegalStateException("Las referencias de la actuación no son legibles.");
        return mapper.convertValue(dato,new TypeReference<>(){});
    }
    private void autorizarPeticionOriginal(Map<String,Object> resultado,Actor actor,Map<String,Object> referencias) {
        long encargo=numero(resultado,"encargo"),peticion=numero(resultado,"id"),documento=numero(resultado,"documento");
        acceso.exigirLectura(encargo,actor);
        Object dato=resultado.get("firma");
        if(dato instanceof Map<?,?>) {
            Map<String,Object> firma=mapper.convertValue(dato,new TypeReference<>(){});
            constancias.autorizarConstancia(firma,encargo,actor,LecturaSnapshot.EXPEDIENTE);
            var r=constancias.revisionDeFirma(actor.idOrganizacion(),encargo,peticion,numero(firma,"revisionPresentacion"))
                    .orElseThrow(()->new IllegalStateException("Falta la presentación original del recibo."));
            var conservada=constancias.constanciaConservada(r);
            if(conservada==null)throw new IllegalStateException("El recibo no tiene constancia original de firma.");
            Map<String,Object> esperado=new LinkedHashMap<>(conservada);esperado.remove("versionContrato");
            Map<String,Object> recibido=new LinkedHashMap<>(firma);recibido.remove("revisionPresentacion");
            if(!huella(esperado).equals(huella(recibido)) || numero(firma,"documento")!=documento)
                throw new IllegalStateException("El recibo no conserva la manifestación original.");
            exigirReferenciasOriginales(resultado,firma);
        } else if(dato!=null)throw new IllegalStateException("La firma del recibo no es legible.");
        else {
            if(referencias==null)throw new ReglaNegocioException("La evidencia histórica del recibo no conserva las versiones originales de sus archivos; no puede restaurarse como resultado acreditado.");
            Map<String,Object> material=new LinkedHashMap<>(referencias);Object hash=material.remove("huellaReferencias");
            if(!CONTRATO_REFERENCIAS.equals(material.get("versionContrato")) || !Objects.equals(hash,huella(material))
                    || numero(material,"id")!=peticion || numero(material,"encargo")!=encargo
                    || numero(material,"documento")!=documento || numero(material,"version")!=numero(resultado,"version"))
                throw new IllegalStateException("Las referencias no corresponden al resultado de esta actuación.");
            constancias.autorizarConstancia(material,encargo,actor,LecturaSnapshot.EXPEDIENTE);
            exigirReferenciasOriginales(resultado,material);
        }
    }
    private void exigirReferenciasOriginales(Map<String,Object> resultado,Map<String,Object> fuente) {
        if(!(resultado.get("evidencias") instanceof List<?> evidencias) || !(fuente.get("archivos") instanceof List<?> referencias))
            throw new IllegalStateException("El recibo perdió los vínculos de aceptación.");
        Set<String> originales=new HashSet<>();
        for(Object item:referencias) {
            if(!(item instanceof Map<?,?> referencia))throw new IllegalStateException("Falta un vínculo del instrumento.");
            originales.add(Objects.toString(referencia.get("referencia"),""));
        }
        if(evidencias.size()!=originales.size() || !originales.equals(evidencias.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalStateException("Las evidencias del recibo no son las de su fuente original.");
    }
    private void autorizarDocumento(DocumentoEncargo d,Actor actor) {
        if(!actor.esAgente() && !actor.esBroker()) throw new AccesoNoAutorizadoException();
        var actual=documentosEncargo.contextoActual(actor.idOrganizacion(),d.getId())
                .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
        if(actual.getEncargo()!=null) acceso.exigirLectura(actual.getEncargo(),actor);
        else if(!Objects.equals(actual.getAutor(),actor.idRolOperativo())) throw new AccesoNoAutorizadoException();
        if(actual.getVersion()!=d.getVersion() || !Objects.equals(actual.getEncargo(),d.getIdEncargo()))
            throw new ConflictoException("El vínculo del documento cambió; consulta su versión actual.");
    }
    private RevisionEncargo revision(Captacion c,Long peticion,Long fin,String accion,String observaciones,
            Map<String,?> snapshot,String huella,Actor actor) {
        return revisiones.save(RevisionEncargo.registrar(c.getOrganizacionId(),c.getId(),peticion,fin,
                c.getVersionContenido(),actor.idRolOperativo(),accion,observaciones,json.objetoCanonico(snapshot),huella,ahora()));
    }
    private void evento(Captacion c,String tipo,Actor actor,Procedencia proc,Map<String,?> carga) {
        Procedencia p=procedencia(proc);
        var evento=p.sellar(EventoDominio.de(c.getOrganizacionId(),tipo,"CAPTACION",c.getId(),
                actor.idRolOperativo(),p.canal())).con(json.objetoCanonico(com.controllocal.service.soporte.AvisoComercialBroker.con(carga,
                    com.controllocal.service.soporte.AvisoComercialBroker.encargo(tipo,c.getId(),c.getVersionContenido()))));
        eventos.save(evento);
        cambiosComerciales.publishEvent(new CambioDeOfertaComercial(CambioDeOfertaComercial.Recurso.ENCARGO,c.getId(),actor,evento));
    }
    private void registrarPrecioAutorizado(Captacion c,Actor actor,Procedencia proc) {
        var condicion=c.getCondicionEconomica();
        var anterior=precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(c.getId(),PrecioPropiedad.HITO_AUTORIZADO);
        if(anterior.filter(p->Objects.equals(p.getMoneda(),condicion.getMonedaReferencia())
                && p.getMonto().compareTo(condicion.getImporteReferencia())==0).isPresent()) return;
        var precio=precios.save(PrecioPropiedad.hito(c.getOrganizacionId(),c.getPropiedad().getId(),c.operacion(),
                PrecioPropiedad.HITO_AUTORIZADO,condicion.getMonedaReferencia(),condicion.getImporteReferencia(),hoy()).delEncargo(c.getId()));
        evento(c,"PRECIO_ENCARGO_AUTORIZADO",actor,proc,Map.of("idPrecio",precio.getId(),"versionContenido",c.getVersionContenido()));
    }
    private Ficha ficha(Captacion c,Actor actor) {
        return new Ficha(c.getId(),c.getCodigoCaptacion(),c.estadoTipado().name(),c.getVersion(),c.getAgente().getId(),
                c.getFechaInicioVigencia(),c.getFechaFinVigencia(),condiciones.fichaParaContexto(c.getCondicionEconomica(),configuracionMensual(c)),
                vigencia(c,hoy(),actor,LecturaSnapshot.EXPEDIENTE),c.getIdPeticionActual(),c.getIdDocumentoActual(),c.getIdComposicionActual(),facultades(c,actor),
                c.getPropiedad().getId(),c.operacion().name(),dinamicas.ficha(c,actor.esAgente()&&Objects.equals(c.getAgente().getId(),actor.idRolOperativo())&&c.editable()),origenDe(c));
    }
    private Facultades facultades(Captacion c,Actor actor) {
        boolean responsable=actor.esAgente() && Objects.equals(c.getAgente().getId(),actor.idRolOperativo());
        boolean vivo=Captacion.esVivo(c.estadoActual()); boolean decisor=acceso.puedeDecidir(c,actor);
        boolean pendiente=c.getIdPeticionActual()!=null && peticiones.findByOrganizacionIdAndId(
                c.getOrganizacionId(),c.getIdPeticionActual()).filter(p->PeticionRevisionEncargo.PENDIENTE.equals(p.estadoActual())).isPresent();
        return new Facultades(responsable&&c.editable(),responsable&&vivo,responsable&&vivo,
                decisor&&pendiente,responsable&&vivo,decisor&&vivo,actor.esBroker()&&responsables.permiteCambioOperativo(c));
    }
    private FacultadesPeticion facultades(PeticionRevisionEncargo p,Captacion c,Actor actor) {
        boolean propia=actor.esAgente() && Objects.equals(c.getAgente().getId(),actor.idRolOperativo());
        boolean viva=Captacion.esVivo(c.estadoActual());
        return new FacultadesPeticion(viva&&propia&&PeticionRevisionEncargo.OBSERVADA.equals(p.estadoActual()),
                viva&&acceso.puedeDecidir(c,actor)&&PeticionRevisionEncargo.PENDIENTE.equals(p.estadoActual()));
    }
    private FacultadesSolicitudFin facultades(SolicitudFinalizacionEncargo s,Captacion c,Actor actor) {
        boolean propia=actor.esAgente() && Objects.equals(c.getAgente().getId(),actor.idRolOperativo())
                && Objects.equals(s.getIdSolicitante(),actor.idRolOperativo());
        boolean pendiente=SolicitudFinalizacionEncargo.PENDIENTE.equals(s.estadoActual());
        boolean observada=SolicitudFinalizacionEncargo.OBSERVADA.equals(s.estadoActual());
        return new FacultadesSolicitudFin(propia&&(pendiente||observada),
                Captacion.esVivo(c.estadoActual())&&pendiente&&acceso.puedeDecidir(c,actor),
                Captacion.esVivo(c.estadoActual())&&propia&&observada);
    }
    private Documento ficha(DocumentoEncargo d) { return new Documento(d.getId(),d.getArchivoRef(),d.getIdEncargo(),
            d.getVersionEncargo(),d.getHuella(),d.getPlantillaVersion(),json.comoLista(d.getFaltantes()),d.getPreparadoEn(),d.getVersion()); }
    private Peticion ficha(PeticionRevisionEncargo p,Captacion c,Actor actor) {
        var adenda=adendaDe(p,actor);
        List<String> faltantes=adenda==null?faltantesDeConsulta(c,partes(p),actor):documentosEncargo.findByOrganizacionIdAndId(actor.idOrganizacion(),p.getIdDocumento())
                .map(d->json.comoLista(d.getFaltantes())).orElseThrow(()->new IllegalStateException("Falta el documento presentado de la adenda."));
        return new Peticion(p.getId(),p.getIdEncargo(),p.getVersionPresentada(),
            p.getVersion(),p.estadoNombre(),p.getIdDocumento(),uuids(p.getEvidencias()),p.getIdUltimaRevision(),facultades(p,c,actor),
            observaciones(p.getOrganizacionId(),p.getIdUltimaRevision()),faltantes,constancias.firmaPresentada(p,actor,LecturaSnapshot.EXPEDIENTE),adenda); }
    private SolicitudFin ficha(SolicitudFinalizacionEncargo s,Captacion c,Actor actor) { return new SolicitudFin(s.getId(),s.getIdEncargo(),
            s.getVersionEncargo(),s.getVersion(),s.estadoNombre(),s.getTipoFin(),s.getFechaSolicitada(),s.getMotivo(),
            uuids(s.getEvidencias()),s.getIdUltimaRevision(),facultades(s,c,actor),observaciones(s.getOrganizacionId(),s.getIdUltimaRevision())); }
    private String observaciones(long org,Long revision) { return revision==null?null:
            revisiones.findByOrganizacionIdAndId(org,revision).map(RevisionEncargo::getObservaciones).orElse(null); }
    private List<String> faltantesDeConsulta(Captacion c,List<Parte> partes,Actor actor) {
        try {return faltantes(c,partes,actor);}
        catch(ReglaNegocioException|ConflictoException desactualizado) {return List.of("PARTES_O_EVIDENCIAS_NO_VIGENTES");}
    }
    private Composicion ficha(ComposicionEncargo c) { return new Composicion(c.getId(),c.getIdEncargo(),c.getVersionContenido(),
            unidades(c),c.getTotalDeclarado(),c.getMoneda(),c.getProcedencia()); }
    private PeticionRevisionEncargo peticion(long id,Actor actor) { return peticiones.bloquear(actor.idOrganizacion(),id)
            .orElseThrow(()->new NoEncontradoException("Petición del Encargo")); }
    private SolicitudFinalizacionEncargo solicitud(long id,Actor actor) { return solicitudes.bloquear(actor.idOrganizacion(),id)
            .orElseThrow(()->new NoEncontradoException("Solicitud de finalización")); }
    private static String configuracionMensual(Captacion c) {
        return c.getIdComposicionActual()==null?"UNIDAD:"+c.getPropiedad().getId():"COMPOSICION:"+c.getIdComposicionActual();
    }
    private ComposicionEncargo composicionActual(Captacion c) { return c.getIdComposicionActual()==null?null:
            composiciones.findByOrganizacionIdAndId(c.getOrganizacionId(),c.getIdComposicionActual())
                    .orElseThrow(()->new IllegalStateException("Falta la composición versionada del episodio.")); }
    private List<Parte> partes(PeticionRevisionEncargo p) { return leer(p.getPartes(),new TypeReference<>(){}); }
    private List<Unidad> unidades(ComposicionEncargo c) { return leer(c.getUnidades(),new TypeReference<>(){}); }
    private List<UUID> uuids(String texto) { return leer(texto,new TypeReference<>(){}); }
    private <T> T leer(String valor,TypeReference<T> tipo) {
        try { return mapper.readValue(valor,tipo); }
        catch(Exception fallo) { throw new IllegalStateException("El episodio conserva datos ilegibles.",fallo); }
    }
    private String escribir(Object valor) {
        try { return mapper.writeValueAsString(valor); }
        catch(Exception fallo) { throw new IllegalStateException("No se pudo conservar la versión del episodio.",fallo); }
    }
    private <T> T restaurar(String snapshot,Class<T> tipo,Actor actor) {
        var mapa=json.comoMapa(snapshot); Object dato=mapa.get("resultado");
        if(!(dato instanceof Map<?,?> resultado)) throw new IllegalStateException("Falta el resultado durable del comando.");
        Map<String,Object> verificado=new TreeMap<>();resultado.forEach((k,v)->verificado.put((String)k,v));
        if(!Objects.equals(mapa.get("huellaResultado"),Idempotencia.huellaExacta(json.objetoCanonico(verificado))))
            throw new IllegalStateException("El resultado durable no conserva su huella.");
        if(tipo==Peticion.class) autorizarPeticionOriginal(verificado,actor,mapaReferencias(mapa.get("referenciasPeticion")));
        if(tipo==Adenda.class) {
            Adenda a=mapper.convertValue(dato,Adenda.class);exigirLecturaAdenda(a,actor);
            autorizarReferenciasPreparacion(mapaReferencias(mapa.get("referenciasAdenda")),a.propuesta(),a.referencia().encargo(),actor);
        }
        return mapper.convertValue(dato,tipo);
    }
    private String huella(Map<String,?> contenido) {
        @SuppressWarnings("unchecked") var canonico=(Map<String,?>)canonico(mapper.convertValue(ordenarConjuntos(contenido),Object.class));
        return Idempotencia.huellaExacta(json.objetoCanonico(canonico));
    }
    /** Un conjunto no adquiere orden contractual por la iteración elegida por otra JVM. */
    private Object ordenarConjuntos(Object valor) {
        if(valor instanceof Set<?> conjunto) return conjunto.stream().map(this::ordenarConjuntos)
                .sorted(Comparator.comparing(this::escribir)).toList();
        if(valor instanceof List<?> lista) return lista.stream().map(this::ordenarConjuntos).toList();
        if(valor instanceof Map<?,?> mapa) {
            Map<String,Object> resultado=new TreeMap<>();mapa.forEach((k,v)->resultado.put((String)k,ordenarConjuntos(v)));return resultado;
        }
        if(valor!=null && valor.getClass().isRecord()) {
            Map<String,Object> resultado=new TreeMap<>();
            try {for(var campo:valor.getClass().getRecordComponents()) {
                Object dato=campo.getAccessor().invoke(valor);
                // Campos opcionales introducidos en C5: su ausencia no cambia una huella anterior.
                boolean nuevoOpcional=((valor instanceof Condiciones || valor instanceof Tramo)&&"ejecucion".equals(campo.getName()))
                        || ((valor instanceof Presentacion || valor instanceof Peticion)&&"adenda".equals(campo.getName()));
                if(dato!=null || !nuevoOpcional)resultado.put(campo.getName(),ordenarConjuntos(dato));
            }}
            catch(ReflectiveOperationException fallo) {throw new IllegalStateException("No se pudo conservar la huella del contrato estructurado.",fallo);}
            return resultado;
        }
        return valor;
    }
    private static Object canonico(Object valor) {
        if(valor instanceof BigDecimal numero) return numero.stripTrailingZeros();
        if(valor instanceof Map<?,?> mapa) {
            Map<String,Object> ordenado=new TreeMap<>();mapa.forEach((k,v)->ordenado.put((String)k,canonico(v)));return ordenado;
        }
        if(valor instanceof List<?> lista) return lista.stream().map(EncargoServiceImpl::canonico).toList();
        return valor;
    }
    private static String huella(byte[] contenido) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contenido)); }
        catch(java.security.NoSuchAlgorithmException imposible) { throw new IllegalStateException(imposible); }
    }
    private static Map<String,Object> mapa(Object... pares) {
        Map<String,Object> resultado=new LinkedHashMap<>();
        for(int i=0;i<pares.length;i+=2) resultado.put((String)pares[i],pares[i+1]); return resultado;
    }
    private static <T> List<T> lista(List<T> valor) { return valor==null?List.of():List.copyOf(valor); }
    private static String texto(String v) { return v==null||v.isBlank()?null:v.trim(); }
    private static String requerido(String v,String concepto) {
        String t=texto(v); if(t==null) throw new ReglaNegocioException("Falta "+concepto+"."); return t;
    }
    private static String decision(String valor) {
        String d=requerido(valor,"decisión").toUpperCase(Locale.ROOT);
        if(!Set.of("APROBAR","OBSERVAR","RECHAZAR").contains(d)) throw new ReglaNegocioException("Decisión no definida."); return d;
    }
    private static String tipoFin(String valor) {
        String t=requerido(valor,"tipo de fin").toUpperCase(Locale.ROOT);
        if(!Set.of("FINALIZACION","RESCISION").contains(t)) throw new ReglaNegocioException("Tipo de fin no definido."); return t;
    }
    private static void version(Captacion c,Long esperada) {
        if(esperada==null)throw new ReglaNegocioException("Debe indicar la versión observada del Encargo.");
        if(c.getVersion()!=esperada) throw new ConflictoException("La versión del Encargo cambió.");
    }
    private static void fechas(LocalDate desde,LocalDate hasta) {
        if(desde!=null && hasta!=null && hasta.isBefore(desde)) throw new ReglaNegocioException("La vigencia termina antes de empezar.");
    }
    private static void exigirAgente(Actor actor) { if(!actor.esAgente()) throw new AccesoNoAutorizadoException(); }
    private static Procedencia procedencia(Procedencia p) {
        if(p==null) throw new IllegalArgumentException("El caso de entrada debe conservar su procedencia."); return p;
    }
    private LocalDate hoy() { return LocalDate.now(reloj); }
    private OffsetDateTime ahora() { return OffsetDateTime.now(reloj); }

    private DocumentosEncargo.Contenido contenidoVisual(Map<String,Object> contenido,List<String> faltantes,
                                                        Propiedad propiedad,Actor actor) {
        List<DocumentosEncargo.Campo> campos=new ArrayList<>();
        if(contenido.get("adendaContractual") instanceof Map<?,?> adenda) {
            poner(campos,"Motivo de la adenda",adenda.get("motivo"));poner(campos,"Alcance de la adenda",adenda.get("alcance"));
            poner(campos,"Alcance desde",adenda.get("vigenteDesde"));poner(campos,"Alcance hasta",adenda.get("vigenteHasta"));
        }
        poner(campos,"Operación",contenido.get("operacion")==OperacionInmobiliaria.ALQUILER?"Alquiler":contenido.get("operacion")==OperacionInmobiliaria.VENTA?"Venta":null);
        if(contenido.get("profesional") instanceof Map<?,?> profesional) poner(campos,"Profesional",profesional.get("nombre"));
        poner(campos,"Organización",contenido.get("organizacion"));
        if(contenido.get("partes") instanceof List<?> partes) for(Object parte:partes) {
            if(parte instanceof Map<?,?> datos) {
                poner(campos,"Titular",datos.get("nombreTitular")); poner(campos,"Cuota declarada (%)",datos.get("cuota"));
                poner(campos,"Representante",datos.get("nombreRepresentante"));
                poner(campos,"Alcance de representación",datos.get("alcanceRepresentacion"));
                poner(campos,"Representación desde",datos.get("desdeRepresentacion"));
                poner(campos,"Representación hasta",datos.get("hastaRepresentacion"));
            }
        }
        DatosFisicos fisicos=(DatosFisicos)contenido.get("fisicos");
        if(fisicos!=null && fisicos.contexto()!=null) {
            poner(campos,"Inmueble",fisicos.contexto().objetoOfrecido());
            poner(campos,"Tipo",AtributosGobernados.rotuloDelTipo(fisicos.contexto().tipoPropiedad()));
            poner(campos,"Uso ofrecido",AtributosGobernados.rotuloDelUso(fisicos.contexto().usoOfrecido()));
            if(fisicos.datos()!=null) {
                var rotulos=atributosFisicos.definicionesParaLeer(actor.idOrganizacion(),fisicos.contexto().tipoPropiedad(),fisicos.datos().keySet());
                for(var entrada:fisicos.datos().entrySet()) {
                    var valor=entrada.getValue(); if(valor.estado()!=SuficienciaInmueble.EstadoDato.CONOCIDO) continue;
                    String rotulo=switch(entrada.getKey()) {
                        case "direccion"->"Dirección"; case "distrito"->"Distrito";
                        case "interiorUnidad"->"Interior / unidad"; case "referenciaInterna"->"Referencia de la unidad";
                        case "metraje_total","metraje_construido","metraje_arrendable","area_terreno"->RotulosDeSuperficie.paraDocumento(valor.concepto());
                        default->rotulos.containsKey(entrada.getKey())?rotulos.get(entrada.getKey()).getRotulo():null;
                    };
                    if(rotulo==null) throw new ReglaNegocioException("La prestación ofrecida necesita su definición antes de integrar el documento.");
                    poner(campos,rotulo,valor.valor() instanceof Boolean b?(b?"Sí":"No"):valor.valor());
                }
            }
        }
        @SuppressWarnings("unchecked") var unidades=(List<Unidad>)contenido.get("unidades");
        @SuppressWarnings("unchecked") var referencias=(List<Map<String,Object>>)contenido.get("referenciasUnidades");
        if(unidades!=null) for(int i=0;i<unidades.size();i++) {
            var unidad=unidades.get(i); var referencia=referencias.get(i);
            String identificacion=java.util.stream.Stream.of(referencia.get("direccion"),referencia.get("interior"),referencia.get("referencia"))
                    .filter(Objects::nonNull).map(Object::toString).collect(java.util.stream.Collectors.joining(" · "));
            poner(campos,"Unidad incluida",AtributosGobernados.rotuloDelTipo((String)referencia.get("tipo"))+" · "+identificacion);
            if(unidad.importeDeclarado()!=null) poner(campos,"Desglose declarado",unidad.importeDeclarado()+" "+unidad.moneda());
            poner(campos,"Fuente del desglose",unidad.procedencia());
        }
        if(contenido.get("totalComposicion")!=null) poner(campos,"Total declarado de la composición",contenido.get("totalComposicion")+" "+contenido.get("monedaComposicion"));
        poner(campos,"Fuente de composición",contenido.get("procedenciaComposicion"));
        Condiciones c=(Condiciones)contenido.get("condiciones");
        if(c!=null) {
            if(c.precioPedido()!=null) poner(campos,contenido.get("operacion")==OperacionInmobiliaria.VENTA?"Precio pedido de venta":"Precio pedido de alquiler",
                    decimal(c.precioPedido())+" "+Objects.toString(c.monedaPrecio(),"(moneda pendiente)")
                    +(c.periodoPrecio()==null?"":" / "+("MENSUAL".equals(c.periodoPrecio())?"mes":c.periodoPrecio())));
            poner(campos,"Tipo de comisión",c.tipoComision()==null?null:switch(c.tipoComision()) {case "E"->"Equivalente de mensualidades";case "P"->"Porcentaje";case "F"->"Importe fijo";default->throw new IllegalStateException("Tipo de comisión no definido.");});
            poner(campos,"Base pactada",c.baseCalculo()==null?null:switch(c.baseCalculo()) {case "R"->"Renta mensual acordada";case "V"->"Precio final de venta acordado";case "N"->"Importe fijo, sin base variable";default->throw new IllegalStateException("Base no definida.");});
            if(c.valorComision()!=null && c.tipoComision()!=null) poner(campos,"Condición de comisión",switch(c.tipoComision()) {
                case "P"->decimal(c.valorComision().multiply(BigDecimal.valueOf(100)))+" %";
                case "E"->decimal(c.valorComision())+" mensualidad(es)";
                case "F"->decimal(c.valorComision())+" "+Objects.toString(c.monedaComision(),"(moneda pendiente)");
                default->throw new IllegalStateException("Tipo de comisión no definido.");});
            poner(campos,"Tratamiento del IGV",c.tratamientoIgv()==null?null:switch(c.tratamientoIgv()) {case "I"->"IGV incluido";case "A"->"IGV adicional";case "N"->"No aplica";default->throw new IllegalStateException("Tratamiento no definido.");});
            poner(campos,"Motivo de comisión cero",c.motivoSinComision());
            poner(campos,"Hito de devengo",c.hitoDevengo()); poner(campos,"Parte obligada",c.parteObligada());
            poner(campos,"Beneficiario",c.beneficiario()); poner(campos,"Exigibilidad",c.exigibilidad());
            mostrarEjecucion(campos,"Pacto",c.ejecucion());
            if(c.tramos()!=null) for(var tramo:c.tramos()) {
                poner(campos,"Tramo declarado",Objects.toString(tramo.hito(),"Hito pendiente")+": "
                        +(tramo.importe()!=null?tramo.importe()+" "+Objects.toString(tramo.moneda(),"(moneda pendiente)"):
                        tramo.porcentaje()!=null?tramo.porcentaje()+" %":"Condición pendiente")+"; "+Objects.toString(tramo.exigibilidad(),"Exigibilidad pendiente"));
                mostrarEjecucion(campos,"Tramo",tramo.ejecucion());
            }
            if(c.composicionMensual()!=null) {
                var mensual=c.composicionMensual();
                poner(campos,"Alcance mensual declarado",Boolean.TRUE.equals(mensual.completa())?"Completo":"Parcial o por precisar");
                if(mensual.componentes()!=null)for(var x:mensual.componentes()) {
                    String cantidad=x.importe()==null?"Importe pendiente":decimal(x.importe());
                    if(x.intervalo())cantidad=(x.importe()==null?"Sin mínimo declarado":cantidad)+" a "+(x.importeHasta()==null?"máximo pendiente":decimal(x.importeHasta()));
                    String rotulo=switch(Objects.toString(x.concepto(),"Componente mensual")) {
                        case "TOTAL_MENSUAL_OBLIGATORIO"->"Total mensual obligatorio";
                        case "PRECIO_ALQUILER"->"Alquiler";case "MANTENIMIENTO"->"Mantenimiento";
                        default->Objects.toString(x.concepto(),"Componente mensual");
                    };
                    poner(campos,rotulo,cantidad+" "+Objects.toString(x.moneda(),"(moneda pendiente)")
                            +" / "+("MENSUAL".equals(x.periodo())?"mes":Objects.toString(x.periodo(),"período pendiente"))
                            +(x.tratamiento()==null?"; tratamiento pendiente":switch(x.tratamiento()) {
                                case EXIGIBLE->"; exigible";case INCLUIDO_EN_OTRO->"; incluido en otro importe";case DESCUENTO_FIRME->"; descuento firme declarado";}));
                    if(x.vigenteDesde()!=null||x.vigenteHasta()!=null)poner(campos,"Vigencia del componente",Objects.toString(x.vigenteDesde(),"inicio pendiente")+" a "+Objects.toString(x.vigenteHasta(),"fin pendiente"));
                }
            }
        }
        if(contenido.get("condicionesAdicionales") instanceof Map<?,?> adicionales) adicionales.values().forEach(v->{
            if(v instanceof Map<?,?> dato && dato.get("valor") instanceof ValorLogico valor){
                String rotulo=Objects.toString(dato.get("rotulo"));String unidad=(String)dato.get("unidad");
                if(valor.moneda()==null&&unidad!=null&&!unidad.isBlank()&&!rotulo.toLowerCase(Locale.ROOT).contains(unidad.toLowerCase(Locale.ROOT)))rotulo+=" ("+unidad+")";
                poner(campos,rotulo,valor.booleano()==null?valor.comoTexto():valor.booleano()?"Sí":"No");
            }
        });
        poner(campos,"Vigencia desde",contenido.get("desde")); poner(campos,"Vigencia hasta",contenido.get("hasta"));
        if(contenido.get("exclusividad") instanceof Boolean exclusiva) poner(campos,"Exclusividad",exclusiva?"Sí":"No");
        // Las identidades técnicas, comentarios internos y contactos de terceros nunca se renderizan.
        return new DocumentosEncargo.Contenido(contenido.containsKey("adendaContractual")?"Adenda al Encargo para revisión":"Preparación del Encargo para revisión",campos,
                faltantes.stream().map(EncargoServiceImpl::rotuloPendiente).distinct().toList());
    }
    private static String decimal(BigDecimal valor) {return valor.stripTrailingZeros().toPlainString();}
    private static String rotuloPendiente(String clave) {
        if(clave.startsWith("TRAMOS")) return "Completar los tramos pactados de comisión.";
        return switch(clave) {
            case "OPERACION"->"Precisar venta o alquiler.";case "DATOS_DEL_INMUEBLE"->"Completar los datos materiales del inmueble.";
            case "PARTES_Y_REPRESENTACION","IDENTIFICACION_TITULAR"->"Identificar a las partes y su representación cuando corresponda.";
            case "CONTEXTO_CONDICIONES_DINAMICAS"->"Precisar tipo de inmueble y operación para interpretar las condiciones declaradas.";
            case "INICIO_VIGENCIA","FIN_VIGENCIA"->"Completar las fechas de vigencia.";case "EXCLUSIVIDAD"->"Precisar la exclusividad.";
            case "PRECIO_PEDIDO","MONEDA_PRECIO","PERIODO_PRECIO"->"Completar precio pedido, moneda y período cuando corresponda.";
            case "CONDICIONES_COMERCIALES","PACTO_COMISION","TIPO_COMISION","BASE_COMISION","VALOR_COMISION","MONEDA_COMISION","TRATAMIENTO_IGV","HITO_DEVENGO","PARTE_OBLIGADA","BENEFICIARIO_COMISION","EXIGIBILIDAD","PROCEDENCIA_PACTO","MOTIVO_SIN_COMISION"->"Completar la condición de comisión y su sustento.";
            case "zonificacion"->"Completar la zonificación documentada aplicable.";case "piso","ascensores"->"Completar piso y ascensores cuando correspondan a la unidad.";
            default->"Completar los datos materiales pendientes de la unidad ofrecida.";
        };
    }
    private void agregarProfesional(Map<String,Object> contenido,Captacion c,Actor actor) {
        var agente=c==null?agentes.findById(actor.idRolOperativo()).orElseThrow(AccesoNoAutorizadoException::new):c.getAgente();
        var persona=agente.getRol().getPersona();
        contenido.put("profesional",mapa("persona",persona.getId(),"nombre",persona.getNombresORazonSocial()));
        contenido.put("responsableFirmado",agente.getId());
        contenido.put("organizacion",c==null||(actor!=null&&c.getOrganizacionId()==actor.idOrganizacion())?acceso.nombreOrganizacion(actor):encargos.contextoActual(c.getOrganizacionId(),c.getId()).orElseThrow(()->new NoEncontradoException("Encargo")).getNombreOrganizacion());
    }
    private void completarNombres(Map<String,Object> contenido,List<Parte> partes,Propiedad p,Actor actor) {
        Map<Long,String> conocidos=new HashMap<>();
        if(p!=null) titularidad.partesConocidas(p.getOrganizacionId(),p.getId(),hoy())
                .forEach(t->conocidos.put(t.getPersona(),t.getNombre()));
        @SuppressWarnings("unchecked") var identidades=(List<Map<String,Object>>)contenido.get("partes");
        for(int i=0;i<partes.size();i++) if(identidades.get(i).get("nombreTitular")==null)
            identidades.get(i).put("nombreTitular",conocidos.get(partes.get(i).persona()));
    }
    private static void mostrarEjecucion(List<DocumentosEncargo.Campo> campos,String alcance,PactosDeEncargo.EjecucionPacto e) {
        if(e==null)return;
        if(e.devengo()!=null)poner(campos,alcance+" · condición de devengo",rotuloHito(e.devengo().hito()));
        if(e.exigibilidad()!=null) {
            var x=e.exigibilidad();poner(campos,alcance+" · hito de exigibilidad",rotuloHito(x.hito()));
            poner(campos,alcance+" · fecha expresa de exigibilidad",x.fechaExpresa());
            poner(campos,alcance+" · cantidad pactada",x.cantidad());poner(campos,alcance+" · unidad pactada",x.unidadExpresa());
            poner(campos,alcance+" · cómputo expreso",x.computoExpreso());
        }
    }
    private static String rotuloHito(TipoHitoContractual h) {
        if(h==null)return null;
        return switch(h) {case MINUTA->"Minuta";case ESCRITURA->"Escritura";case CONTRATO_ALQUILER->"Contrato de alquiler";};
    }
    private static void poner(List<DocumentosEncargo.Campo> campos,String rotulo,Object valor) {
        if(valor!=null) campos.add(new DocumentosEncargo.Campo(rotulo,valor.toString()));
    }
}
