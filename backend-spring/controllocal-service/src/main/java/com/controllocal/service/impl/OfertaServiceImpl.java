package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.negociacion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.controllocal.service.negociacion.ContenidoNegociacion.*;

/** CAT-E22/E23, RF046–049: registra manifestaciones externas, nunca acepta por las partes. */
@Service
public class OfertaServiceImpl implements OfertaService {
    private static final String OFERTA="presentar_oferta", CONTRA="registrar_contraoferta",
        ACEPTAR="registrar_aceptacion_oferta", RECHAZAR="registrar_rechazo_oferta", RETIRAR="registrar_retirada_oferta";
    private static final Set<String> CAPACIDADES=Set.of(OFERTA,CONTRA,ACEPTAR,RECHAZAR,RETIRAR);
    private final OfertaRepository ofertas;
    private final MesaNegociacionRepository mesas;
    private final AccesoOportunidad acceso;
    private final ContextosDeActividad contextos;
    private final EncargoService encargos;
    private final PropiedadRepository propiedades;
    private final AutoridadDePropiedad autoridadFisica;
    private final CaptacionRepository captaciones;
    private final FuentePrecioNegociacion precios;
    private final ComandoIdempotenteRepository recibos;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final Transiciones transiciones;
    private final EventoDominioRepository eventos;
    private final SupervisionAgenteRepository supervisiones;
    private final EntityManager entidades;
    public OfertaServiceImpl(OfertaRepository ofertas,MesaNegociacionRepository mesas,
            AccesoOportunidad acceso,ContextosDeActividad contextos,EncargoService encargos,
            PropiedadRepository propiedades,FuentePrecioNegociacion precios,ComandoIdempotenteRepository recibos,
            ComandosIdempotentes comandos,Documentos documentos,Transiciones transiciones,
            EventoDominioRepository eventos,SupervisionAgenteRepository supervisiones,EntityManager entidades,
            CaptacionRepository captaciones,AutoridadDePropiedad autoridadFisica) {
        this.ofertas=ofertas;this.mesas=mesas;this.acceso=acceso;this.contextos=contextos;
        this.encargos=encargos;this.propiedades=propiedades;this.precios=precios;this.recibos=recibos;
        this.comandos=comandos;this.documentos=documentos;this.transiciones=transiciones;this.eventos=eventos;
        this.captaciones=captaciones;
        this.autoridadFisica=autoridadFisica;
        this.supervisiones=supervisiones;this.entidades=entidades;
    }
    public record PropuestaPersistida(DatosRegistroOferta propuesta,Long anterior,Long versionAnterior,Procedencia procedencia) { }
    public record ContenidoAcuerdo(PropuestaPersistida propuestaExacta,long oferta,long versionOfertaAceptada,
                                    DatosAceptacion manifestacion,Long operador,Procedencia procedencia) { }
    @Override @Transactional(readOnly=true)
    public Optional<AcuerdoAceptado> consultarAcuerdoAceptado(long oportunidad,Instant corte,Actor actor){
        var p=acceso.contextoParaNegociacion(oportunidad,actor);
        return mesas.acuerdoDeLaMesa(actor.idOrganizacion(),oportunidad,corte==null?contextos.ahora():corte).map(a->{
            var c=documentos.comoObjeto(a.contenido(),ContenidoAcuerdo.class);
            if(c.oferta()!=a.oferta()||c.versionOfertaAceptada()!=a.versionOferta()
                    ||c.propuestaExacta().propuesta().oportunidad()!=oportunidad)
                throw new IllegalStateException("El acuerdo no conserva la propuesta aceptada exacta.");
            reautorizarPropuesta(c.propuestaExacta().propuesta(),actor);
            reautorizarParte(c.manifestacion().parte(),p,c.manifestacion().fechaHecho(),actor);
            return new AcuerdoAceptado(a.id(),a.version(),oportunidad,a.oferta(),a.versionOferta(),
                c.propuestaExacta().propuesta(),c.manifestacion(),c.operador(),c.procedencia(),a.registradoEn());
        });
    }
    private record Decision(long oportunidad,long versionOportunidad,long versionMesa,long versionEncargo,
                            long oferta,long versionOferta,ParteReal parte,LocalDate fecha,String evidencia,String motivo) { }
    private record Comando(String capacidad,DatosRegistroOferta propuesta,Decision decision,
                           Long anterior,Long versionAnterior,Object normalizado) {
        long oportunidad(){return propuesta!=null?propuesta.oportunidad():decision.oportunidad();}
        long versionOportunidad(){return propuesta!=null?propuesta.versionOportunidad():decision.versionOportunidad();}
        long versionMesa(){return propuesta!=null?propuesta.versionMesa():decision.versionMesa();}
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaMesa(long id,Actor actor){acceso.contextoParaNegociacion(id,actor);}
    @Override @Transactional(readOnly=true)
    public List<FichaOferta> listar(long id,Actor actor){return listar(id,contextos.ahora(),actor);}
    @Override @Transactional(readOnly=true)
    public List<FichaOferta> listar(long id,Instant corte,Actor actor){
        exigirLecturaMesa(id,actor);if(corte==null)throw new ReglaNegocioException("Indica el corte de la consulta.");
        return mesas.historia(actor.idOrganizacion(),id,corte).stream().map(j->{
            var ficha=documentos.comoObjeto(j,FichaOferta.class);
            if(!Objects.equals(ficha.idOportunidad(),id))throw new IllegalStateException("Historia de otra mesa.");
            reautorizarObjeto(ficha.objeto(),actor);return ficha;
        }).toList();
    }
    @Override @Transactional(readOnly=true)
    public CapacidadesDeLaMesa capacidades(long id,Actor actor){
        var p=acceso.contextoParaNegociacion(id,actor);
        boolean opera=actor.esAgente()&&p.responsableActual()==actor.idRolOperativo();
        boolean abierta=Set.of(OportunidadComercial.ABIERTA,OportunidadComercial.SOLICITUD_CREADA).contains(p.estado());
        var viva=ofertas.vivaDe(p.organizacion(),id);var aceptada=ofertas.aceptadaDe(p.organizacion(),id);
        boolean negocia=opera&&abierta&&aceptada.isEmpty();
        return new CapacidadesDeLaMesa(negocia,negocia&&viva.isPresent(),negocia&&viva.isPresent(),
            negocia&&viva.isPresent(),negocia&&viva.isPresent(),opera&&OportunidadComercial.ABIERTA.equals(p.estado()),mesas.version(p.organizacion(),id));
    }
    @Override @Transactional public DatosRegistroOferta validarRegistroOferta(DatosRegistroOferta d,Actor a){return (DatosRegistroOferta)validar(OFERTA,d,a).normalizado();}
    @Override @Transactional public DatosContraoferta validarContraoferta(DatosContraoferta d,Actor a){return (DatosContraoferta)validar(CONTRA,d,a).normalizado();}
    @Override @Transactional public DatosAceptacion validarAceptacion(DatosAceptacion d,Actor a){return (DatosAceptacion)validar(ACEPTAR,d,a).normalizado();}
    @Override @Transactional public DatosRechazo validarRechazo(DatosRechazo d,Actor a){return (DatosRechazo)validar(RECHAZAR,d,a).normalizado();}
    @Override @Transactional public DatosRetirada validarRetirada(DatosRetirada d,Actor a){return (DatosRetirada)validar(RETIRAR,d,a).normalizado();}
    private Comando validar(String cap,Object datos,Actor actor){
        var c=normalizar(cap,datos);contextos.exigirAgente(actor);
        comprobar(c,acceso.contextoParaNegociacion(c.oportunidad(),actor),actor);return c;
    }
    @Override @Transactional
    public Map<String,Object> previsualizarActuacion(String cap,Map<String,Object> datos,Actor actor){
        Class<?> tipo=switch(cap){case OFERTA->DatosRegistroOferta.class;case CONTRA->DatosContraoferta.class;
            case ACEPTAR->DatosAceptacion.class;case RECHAZAR->DatosRechazo.class;case RETIRAR->DatosRetirada.class;
            default->throw new ReglaNegocioException("La actuación no corresponde a negociación.");};
        var c=validar(cap,documentos.comoObjeto(documentos.objetoCanonico(datos),tipo),actor);
        var vista=new LinkedHashMap<String,Object>();vista.put("datos",c.normalizado());
        if(c.decision()!=null){var o=leerOferta(c.oportunidad(),c.decision().oferta(),actor);
            vista.put("ofertaExacta",ficha(o,c.versionMesa()));vista.put("propuestaExacta",propuesta(o));}
        else if(c.anterior()!=null){var o=leerOferta(c.oportunidad(),c.anterior(),actor);vista.put("ofertaAnterior",ficha(o,c.versionMesa()));}
        return Collections.unmodifiableMap(vista);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoNegociacion registrarOferta(DatosRegistroOferta d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(OFERTA,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoNegociacion registrarContraoferta(DatosContraoferta d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(CONTRA,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoNegociacion registrarAceptacion(DatosAceptacion d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(ACEPTAR,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoNegociacion registrarRechazo(DatosRechazo d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(RECHAZAR,d,a,s,k,p);}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoNegociacion registrarRetirada(DatosRetirada d,Actor a,ContextoSesion s,String k,Procedencia p){return ejecutar(RETIRAR,d,a,s,k,p);}
    @Override @Transactional(readOnly=true)
    public Optional<ResultadoNegociacion> consultarResultado(String cap,String clave,Actor actor){
        if(!CAPACIDADES.contains(cap))throw new ReglaNegocioException("Capacidad de negociación desconocida.");
        contextos.exigirOperador(actor);
        return comandos.consultarResultado(actor,clave,cap).map(r->restaurar(r.getResultado(),actor));
    }
    private ResultadoNegociacion restaurar(String json,Actor actor){
        var r=documentos.comoObjeto(json,ResultadoNegociacion.class);exigirLecturaMesa(r.oportunidad(),actor);
        var original=propuesta(leerOferta(r.oportunidad(),r.oferta(),actor)).propuesta();
        reautorizarPropuesta(original,actor);return r;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaActuacion(String cap,Map<String,Object> datos,Actor actor){
        Class<?> tipo=switch(cap){case OFERTA->DatosRegistroOferta.class;case CONTRA->DatosContraoferta.class;
            case ACEPTAR->DatosAceptacion.class;case RECHAZAR->DatosRechazo.class;case RETIRAR->DatosRetirada.class;
            default->throw new ReglaNegocioException("Actuación de negociación desconocida.");};
        var c=normalizar(cap,documentos.comoObjeto(documentos.objetoCanonico(datos),tipo));
        var p=acceso.contextoParaNegociacion(c.oportunidad(),actor);
        if(c.propuesta()!=null)reautorizarPropuesta(c.propuesta(),actor);
        if(c.anterior()!=null)reautorizarPropuesta(propuesta(leerOferta(c.oportunidad(),c.anterior(),actor)).propuesta(),actor);
        if(c.decision()!=null){
            reautorizarPropuesta(propuesta(leerOferta(c.oportunidad(),c.decision().oferta(),actor)).propuesta(),actor);
            reautorizarParte(c.decision().parte(),p,c.decision().fecha(),actor);
        }
    }
    private void reautorizarPropuesta(DatosRegistroOferta d,Actor actor){
        var p=acceso.contextoParaNegociacion(d.oportunidad(),actor);
        reautorizarObjeto(d.objeto(),actor);reautorizarParte(d.proponente(),p,d.fechaHecho(),actor);
    }
    private void reautorizarParte(ParteReal parte,AccesoOportunidad.PermisoOportunidad p,LocalDate fecha,Actor actor){
        var actual=encargos.consultar(p.idEncargo(),actor);
        try{validarParte(parte,p,actual.version(),fecha,actor);}
        catch(ReglaNegocioException ex){throw new AccesoNoAutorizadoException();}
    }
    private ResultadoNegociacion ejecutar(String cap,Object datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia){
        var c=normalizar(cap,datos);String k=Idempotencia.exigirClave(clave);
        if(procedencia==null)throw new ReglaNegocioException("Conserva la procedencia del hecho.");
        contextos.iniciar(actor,sesion);
        String hash=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",c.normalizado(),"procedencia",procedencia)));
        String lock=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+k);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(lock.substring(0,16),16));
        Optional<com.controllocal.domain.auditoria.ComandoIdempotente> previo;
        try{previo=comandos.buscar(actor,k,cap,hash);}
        catch(ReglaNegocioException ex){throw new ConflictoException("La clave identifica una actuación de otro contenido.");}
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        bloquearObjetoActual(c,actor);
        var contexto=acceso.bloquearParaNegociacion(c.oportunidad(),c.versionOportunidad(),actor,sesion);
        long vm=mesas.bloquear(actor.idOrganizacion(),c.oportunidad());versionExacta(c.versionMesa(),vm,"mesa");
        comprobar(c,contexto,actor);
        Instant ahora=contextos.ahora();long siguiente=vm+1;Oferta o;Long acuerdo=null;
        if(c.propuesta()!=null){
            if(c.anterior()!=null){var anterior=leerOferta(c.oportunidad(),c.anterior(),actor);
                if(anterior.estaViva()){
                    transiciones.aplicar(anterior,anterior.getId(),Oferta.SUPERADA,actor,"Superada por la nueva propuesta vinculada.");
                    ofertas.saveAndFlush(anterior);historizar(anterior,siguiente,ahora);
                }}
            o=crearOferta(c,contexto,procedencia,actor);
            mesas.propuesta(actor.idOrganizacion(),o.getId(),documentos.objetoCanonico(new PropuestaPersistida(c.propuesta(),c.anterior(),c.versionAnterior(),procedencia)),ahora);
            precios.registrarHitoOfrecido(c.propuesta().objeto(),c.propuesta().importe(),o.getId(),c.propuesta().fechaHecho(),procedencia,actor);
            historizar(o,siguiente,ahora);
        } else {
            o=leerOferta(c.oportunidad(),c.decision().oferta(),actor);
            String estado=ACEPTAR.equals(cap)?Oferta.ACEPTADA:RECHAZAR.equals(cap)?Oferta.RECHAZADA:Oferta.RETIRADA;
            transiciones.aplicar(o,o.getId(),estado,actor,c.decision().motivo()==null?Oferta.rotuloDelEstado(estado):c.decision().motivo());
            ofertas.saveAndFlush(o);historizar(o,siguiente,ahora);
            if(ACEPTAR.equals(cap)){
                var contenido=new LinkedHashMap<String,Object>();contenido.put("propuestaExacta",propuesta(o));
                contenido.put("oferta",o.getId());contenido.put("versionOfertaAceptada",c.decision().versionOferta());
                contenido.put("manifestacion",c.normalizado());contenido.put("operador",actor.idRolOperativo());
                contenido.put("procedencia",procedencia);
                acuerdo=mesas.acuerdo(actor.idOrganizacion(),o.getId(),c.decision().versionOferta(),documentos.objetoCanonico(contenido),ahora);
            }
        }
        mesas.avanzar(actor.idOrganizacion(),c.oportunidad(),vm);
        var resultado=new ResultadoNegociacion(c.oportunidad(),siguiente,o.getId(),o.getVersion(),o.estadoActual(),acuerdo,acuerdo==null?null:1L,
                LocalDateTime.ofInstant(ahora,ZoneId.of("America/Lima")));
        var carga=new LinkedHashMap<String,Object>();carga.put("oportunidad",c.oportunidad());carga.put("encargo",o.getIdCaptacion());
        carga.put("oferta",o.getId());carga.put("versionOferta",o.getVersion());carga.put("versionMesa",siguiente);
        carga.put("hecho",cap);carga.put("acuerdo",acuerdo);carga.put("ocurridoEn",ahora);carga.put("responsable",contexto.responsableActual());
        carga.put("notificacionBroker",OFERTA.equals(cap)?"NB-04":CONTRA.equals(cap)?"NB-05":"NB-06");
        carga.put("avisoBroker",new com.controllocal.service.soporte.AvisoComercialBroker.Marca(OFERTA.equals(cap)?"NB-04":CONTRA.equals(cap)?"NB-05":"NB-06","ENCARGO",o.getIdCaptacion(),o.getVersion(),List.of("PROPUESTA_NEGOCIACION")));
        supervisiones.buscarActivaPorAgente(actor.idOrganizacion(),contexto.responsableActual()).ifPresent(s->carga.put("broker",s.getIdRolBroker()));
        eventos.saveAndFlush(procedencia.sinPeticion().sellar(EventoDominio.de(actor.idOrganizacion(),"NEGOCIACION_REGISTRADA",Oferta.ENTIDAD_TIPO,o.getId(),actor.idRolOperativo(),procedencia.canal())).con(documentos.objetoCanonico(carga)));
        String json=documentos.objetoCanonico(resultado);
        comandos.registrar(actor,k,cap,hash,Oferta.ENTIDAD_TIPO,o.getId(),procedencia,json);return documentos.comoObjeto(json,ResultadoNegociacion.class);
    }
    private void comprobar(Comando c,AccesoOportunidad.PermisoOportunidad p,Actor actor){
        contextos.exigirAgente(actor);versionExacta(c.versionOportunidad(),p.version(),"oportunidad");
        if(p.responsableActual()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        versionExacta(c.versionMesa(),mesas.version(p.organizacion(),p.id()),"mesa");
        if(!OportunidadComercial.ABIERTA.equals(p.estado())&&!OportunidadComercial.SOLICITUD_CREADA.equals(p.estado()))
            throw new ReglaNegocioException("La oportunidad cerrada no admite nuevas propuestas o decisiones ordinarias.");
        if(ofertas.aceptadaDe(p.organizacion(),p.id()).isPresent())throw new ConflictoException("La mesa ya conserva una oferta aceptada.");
        if(c.propuesta()!=null){
            var d=c.propuesta();validarObjeto(d.objeto(),p,actor);validarParte(d.proponente(),p,d.objeto().versionEncargo(),d.fechaHecho(),actor);
            var viva=ofertas.vivaDe(p.organizacion(),p.id());
            if(c.anterior()==null){if(viva.isPresent())throw new ConflictoException("Ya hay una propuesta vigente; registra su respuesta o una contraoferta vinculada.");}
            else {var anterior=leerOferta(p.id(),c.anterior(),actor);versionExacta(c.versionAnterior(),anterior.getVersion(),"oferta anterior");
                if(CONTRA.equals(c.capacidad())){
                    if(!anterior.estaViva()||viva.isEmpty()||!Objects.equals(viva.get().getId(),anterior.getId()))
                        throw new ConflictoException("La contraoferta responde a la propuesta presentada actual.");
                    if(!Oferta.ladoContrario(anterior.getPresentadaPor()).equals(d.proponente().lado()))
                        throw new ReglaNegocioException("La contraoferta identifica a la parte que responde, no a la que formuló la propuesta anterior.");
                }else if(viva.isPresent()){
                    if(!Objects.equals(viva.get().getId(),anterior.getId()))throw new ConflictoException("Identifica la propuesta vigente que se reemplaza.");
                    if(!anterior.getPresentadaPor().equals(d.proponente().lado()))
                        throw new ReglaNegocioException("Una respuesta del lado opuesto se registra como contraoferta.");
                }
            }
        } else {
            var d=c.decision();var o=leerOferta(p.id(),d.oferta(),actor);versionExacta(d.versionOferta(),o.getVersion(),"oferta");
            if(!o.estaViva())throw new ConflictoException("La propuesta seleccionada ya tiene una decisión o fue superada.");
            var original=propuesta(o).propuesta().objeto();
            // La manifestación acepta el objeto original. El CAS actual del Encargo viene del comando,
            // sin reescribir la propuesta ni inferir que una versión privada alteró sus unidades.
            reautorizarObjeto(original,actor);
            if(ACEPTAR.equals(c.capacidad()))validarObjeto(new ObjetoExacto(original.tipo(),original.encargo(),d.versionEncargo(),
                original.propiedad(),original.versionFisica(),original.composicion(),original.versionComposicion(),original.unidades()),p,actor);
            validarParte(d.parte(),p,d.versionEncargo(),d.fecha(),actor);
            String ladoEsperado=RETIRAR.equals(c.capacidad())?o.getPresentadaPor():Oferta.ladoContrario(o.getPresentadaPor());
            if(!ladoEsperado.equals(d.parte().lado()))throw new ReglaNegocioException("La parte manifestante no corresponde a esta decisión sobre la propuesta.");
        }
    }
    /** Cuenta/SID/clave -> propiedades autorizadas ordenadas -> Encargo -> Oportunidad -> Mesa.
     * C2 modifica físico y términos en ese orden; no tomar Propiedad después de Mesa.
     */
    private void bloquearObjetoActual(Comando c,Actor actor){
        var contexto=acceso.contextoParaNegociacion(c.oportunidad(),actor);
        if(contexto.responsableActual()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        var actual=encargos.consultar(contexto.idEncargo(),actor);
        if(!Objects.equals(actual.responsable(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        var ids=new TreeSet<Long>();ids.add(contexto.idPropiedad());
        if(actual.composicionActual()!=null)
            encargos.consultarComposicion(contexto.idEncargo(),null,actor).unidades().forEach(u->ids.add(u.idPropiedad()));
        if(c.decision()!=null){
            var original=propuesta(leerOferta(c.oportunidad(),c.decision().oferta(),actor)).propuesta().objeto();
            reautorizarObjeto(original,actor);
            if(original.unidades()!=null)ids.addAll(original.unidades());
        }
        for(long id:ids){var p=propiedades.bloquearParaEscritura(actor.idOrganizacion(),id)
            .orElseThrow(()->new NoEncontradoException("Propiedad"));
            entidades.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);}
        var cap=captaciones.bloquearParaEscritura(actor.idOrganizacion(),contexto.idEncargo())
            .orElseThrow(()->new NoEncontradoException("Encargo"));
        entidades.refresh(cap,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    private void reautorizarObjeto(ObjetoExacto original,Actor actor){
        if(original==null)throw new IllegalStateException("No se conserva el objeto de la propuesta.");
        encargos.consultar(original.encargo(),actor);
        if(original.tipo()==TipoObjeto.COMPOSICION){
            var historica=encargos.consultarComposicion(original.encargo(),original.versionComposicion(),actor);
            if(!Objects.equals(historica.id(),original.composicion()))throw new IllegalStateException("Composición histórica inconsistente.");
        }else autoridadFisica.buscarParaLectura(actor,original.propiedad()).orElseThrow(()->new NoEncontradoException("Propiedad"));
    }
    private void validarObjeto(ObjetoExacto o,AccesoOportunidad.PermisoOportunidad p,Actor actor){
        if(o.encargo()!=p.idEncargo())throw new ReglaNegocioException("La propuesta debe corresponder al Encargo de la oportunidad.");
        var e=encargos.consultar(p.idEncargo(),actor);versionExacta(o.versionEncargo(),e.version(),"Encargo");
        if(e.responsable()==null||e.responsable()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(o.tipo()==TipoObjeto.UNIDAD){
            if(e.composicionActual()!=null)throw new ReglaNegocioException("El Encargo tiene una composición; identifica su versión y unidades exactas.");
            if(o.propiedad()!=p.idPropiedad())throw new ReglaNegocioException("La unidad ofrecida no corresponde al Encargo.");
            var propiedad=autoridadFisica.buscarParaLectura(actor,o.propiedad()).orElseThrow(()->new NoEncontradoException("Propiedad"));
            versionExacta(o.versionFisica(),propiedad.getVersionFisica(),"propiedad física");
        } else {
            if(e.composicionActual()==null)throw new ReglaNegocioException("El Encargo no conserva la composición declarada.");
            var composicion=encargos.consultarComposicion(p.idEncargo(),o.versionComposicion(),actor);
            if(!Objects.equals(o.composicion(),composicion.id())||!Objects.equals(e.composicionActual(),composicion.id()))
                throw new ConflictoException("La composición identificada no es la versión actual del Encargo.");
            var unidades=composicion.unidades().stream().map(EncargoService.Unidad::idPropiedad).toList();
            if(unidades.size()!=o.unidades().size()||!new HashSet<>(unidades).equals(new HashSet<>(o.unidades())))
                throw new ReglaNegocioException("Las unidades no corresponden a la versión exacta de composición.");
        }
    }
    private void validarParte(ParteReal parte,AccesoOportunidad.PermisoOportunidad p,long versionEncargo,LocalDate fecha,Actor actor){
        var conocidas=encargos.consultarPartesNegociacion(p.idEncargo(),versionEncargo,fecha,actor);
        if("I".equals(parte.lado())){
            if(p.interesado()==null||p.interesado().idPersona()!=parte.persona())throw new ReglaNegocioException("La persona manifestante no corresponde al interesado de esta oportunidad.");
            return;
        }
        boolean identificada=conocidas.partes().stream().anyMatch(v->Objects.equals(v.persona(),parte.persona())
            ||(Objects.equals(v.representante(),parte.persona())&&v.evidenciaRepresentacion()!=null&&texto(v.alcanceRepresentacion())!=null
                &&(v.desdeRepresentacion()==null||!fecha.isBefore(v.desdeRepresentacion()))
                &&(v.hastaRepresentacion()==null||!fecha.isAfter(v.hastaRepresentacion()))));
        if(!identificada)throw new ReglaNegocioException("La persona manifestante no corresponde a una parte o representación documentada del Encargo.");
    }
    private Oferta leerOferta(long oportunidad,long id,Actor actor){
        var o=ofertas.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Oferta"));
        if(!Objects.equals(o.getIdOportunidad(),oportunidad))throw new NoEncontradoException("Oferta");
        entidades.refresh(o);return o;
    }
    private Oferta crearOferta(Comando c,AccesoOportunidad.PermisoOportunidad p,Procedencia procedencia,Actor actor){
        var d=c.propuesta();var o=new Oferta();o.setOrganizacionId(p.organizacion());o.setIdOportunidad(p.id());o.setIdCaptacion(p.idEncargo());
        Integer ronda=ofertas.ultimaRondaDe(p.organizacion(),p.id());o.setRonda(ronda==null?1:ronda+1);
        o.setIdOfertaAnterior(c.anterior());o.setPresentadaPor(d.proponente().lado());o.setMonto(d.importe().importe());
        o.setMoneda(d.importe().moneda());o.setCondiciones(d.condiciones());o.setFecha(d.fechaHecho());
        o.setIdRolRegistra(actor.idRolOperativo());o.setOrigen(procedencia.canal());
        var referencia=precios.congelarReferencia(d.objeto(),d.importe(),actor);
        if(referencia!=null&&referencia.idPrecio()!=null)o.conReferencia(referencia.hito(),referencia.idPrecio(),referencia.monto(),referencia.moneda());
        transiciones.iniciar(o,Oferta.PRESENTADA);ofertas.saveAndFlush(o);
        entidades.refresh(o);return o;
    }
    private PropuestaPersistida propuesta(Oferta oferta){
        return documentos.comoObjeto(mesas.propuesta(oferta.getOrganizacionId(),oferta.getId())
            .orElseThrow(()->new IllegalStateException("No se conserva la propuesta exacta de la oferta.")),PropuestaPersistida.class);
    }
    private FichaOferta ficha(Oferta o,long versionMesa){
        var d=propuesta(o).propuesta();ReferenciaDeLaOferta referencia=o.tieneReferencia()
            ?new ReferenciaDeLaOferta(o.getHitoReferencia(),PrecioPropiedad.rotuloDelHito(o.getHitoReferencia()),o.getIdPrecioReferencia(),o.getMontoReferencia(),o.getMonedaReferencia()):null;
        return new FichaOferta(o.getId(),o.getIdOportunidad(),o.getIdCaptacion(),o.getRonda(),o.getPresentadaPor(),Oferta.rotuloDelLado(o.getPresentadaPor()),
            o.getMonto(),o.getMoneda(),o.estadoActual(),Oferta.rotuloDelEstado(o.estadoActual()),o.getCondiciones(),o.getFecha(),o.getIdRolRegistra(),o.getOrigen(),referencia,
            o.getFechaCreacion()==null?null:o.getFechaCreacion().atZoneSameInstant(ZoneId.of("America/Lima")).toLocalDateTime(),
            o.getVersion(),versionMesa,d.proponente(),d.objeto(),d.importe(),o.getIdOfertaAnterior());
    }
    private void historizar(Oferta o,long versionMesa,Instant fecha){
        mesas.historia(o.getOrganizacionId(),o.getId(),o.getVersion(),o.getIdOportunidad(),versionMesa,documentos.objetoCanonico(ficha(o,versionMesa)),fecha);
    }
    private static void versionExacta(long observada,long actual,String recurso){if(observada!=actual)throw new ConflictoException("Cambió la versión de "+recurso+"; revisa la actuación exacta.");}
    private LocalDate fechaDeclarada(LocalDate valor){return ContenidoNegociacion.fecha(valor);}
    private DatosRegistroOferta normalizarOferta(DatosRegistroOferta d){
        if(d==null)throw new ReglaNegocioException("Aporta la propuesta comercial.");
        return new DatosRegistroOferta(identidad(d.oportunidad(),"la oportunidad"),version(d.versionOportunidad(),"oportunidad"),version(d.versionMesa(),"mesa"),
            parte(d.proponente()),dinero(d.importe()),objeto(d.objeto()),texto(d.condiciones()),fechaDeclarada(d.fechaHecho()),texto(d.evidencia()),
            d.ofertaAnterior()==null?null:identidad(d.ofertaAnterior(),"la propuesta anterior"),
            d.versionOfertaAnterior()==null?null:version(d.versionOfertaAnterior(),"propuesta anterior"));
    }
    private Decision decision(Long op,Long vop,Long mesa,Long enc,Long oferta,Long vof,ParteReal parte,LocalDate fecha,String evidencia,String motivo){
        return new Decision(identidad(op,"la oportunidad"),version(vop,"oportunidad"),version(mesa,"mesa"),version(enc,"Encargo"),
            identidad(oferta,"la oferta"),version(vof,"oferta"),parte(parte),fechaDeclarada(fecha),texto(evidencia),texto(motivo));
    }
    private Comando normalizar(String cap,Object bruto){
        if(bruto==null)throw new ReglaNegocioException("Aporta los datos de negociación.");
        return switch(cap){
            case OFERTA->{var d=normalizarOferta((DatosRegistroOferta)bruto);
                if((d.ofertaAnterior()==null)!=(d.versionOfertaAnterior()==null))throw new ReglaNegocioException("Aporta antecedente y versión observada juntos.");
                yield new Comando(cap,d,null,d.ofertaAnterior(),d.versionOfertaAnterior(),d);}
            case CONTRA->{var d=(DatosContraoferta)bruto;
                var p=normalizarOferta(new DatosRegistroOferta(d.oportunidad(),d.versionOportunidad(),d.versionMesa(),d.proponente(),d.importe(),d.objeto(),d.condiciones(),d.fechaHecho(),d.evidencia(),d.ofertaAnterior(),d.versionOferta()));
                long anterior=identidad(d.ofertaAnterior(),"la oferta anterior"),va=version(d.versionOferta(),"oferta anterior");
                var n=new DatosContraoferta(p.oportunidad(),p.versionOportunidad(),p.versionMesa(),anterior,va,p.proponente(),p.importe(),p.objeto(),p.condiciones(),p.fechaHecho(),p.evidencia());
                yield new Comando(cap,p,null,anterior,va,n);}
            case ACEPTAR->{var d=(DatosAceptacion)bruto;var n=decision(d.oportunidad(),d.versionOportunidad(),d.versionMesa(),d.versionEncargo(),d.oferta(),d.versionOferta(),d.parte(),d.fechaHecho(),d.evidencia(),d.motivo());
                if(n.evidencia()==null)throw new ReglaNegocioException("Conserva la evidencia declarada de la aceptación manifestada.");
                yield new Comando(cap,null,n,null,null,new DatosAceptacion(n.oportunidad(),n.versionOportunidad(),n.versionMesa(),n.versionEncargo(),n.oferta(),n.versionOferta(),n.parte(),n.fecha(),n.evidencia(),n.motivo()));}
            case RECHAZAR->{var d=(DatosRechazo)bruto;var n=decision(d.oportunidad(),d.versionOportunidad(),d.versionMesa(),d.versionEncargo(),d.oferta(),d.versionOferta(),d.parte(),d.fechaHecho(),d.evidencia(),d.motivo());
                yield new Comando(cap,null,n,null,null,new DatosRechazo(n.oportunidad(),n.versionOportunidad(),n.versionMesa(),n.versionEncargo(),n.oferta(),n.versionOferta(),n.parte(),n.fecha(),n.evidencia(),n.motivo()));}
            case RETIRAR->{var d=(DatosRetirada)bruto;var n=decision(d.oportunidad(),d.versionOportunidad(),d.versionMesa(),d.versionEncargo(),d.oferta(),d.versionOferta(),d.parte(),d.fechaHecho(),d.evidencia(),d.motivo());
                yield new Comando(cap,null,n,null,null,new DatosRetirada(n.oportunidad(),n.versionOportunidad(),n.versionMesa(),n.versionEncargo(),n.oferta(),n.versionOferta(),n.parte(),n.fecha(),n.evidencia(),n.motivo()));}
            default->throw new ReglaNegocioException("La actuación no corresponde a negociación.");
        };
    }
}
