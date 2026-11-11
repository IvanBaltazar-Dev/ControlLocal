package com.controllocal.service.formalizacion;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static com.controllocal.domain.comercial.ArchivoEncargo.HITO;
import static com.controllocal.service.actuacion.EfectosFormalizacionDeActuacion.*;

/** RF051–060: expediente/hito, efectos contractuales y evaluación económica comparten una transacción. */
@Service
@Transactional(readOnly=true)
public class FormalizacionServiceImpl implements FormalizacionService {
    private final ExpedienteOperacionRepository expedientes;
    private final RevisionExpedienteOperacionRepository revisiones;
    private final HitoContractualRepository hitos;
    private final RevisionHitoContractualRepository hechos;
    private final AccesoEncargo acceso;
    private final ContextosDeActividad contextos;
    private final FuentesDeFormalizacion fuentes;
    private final HitosConfirmadosImpl lector;
    private final EvidenciasDeFormalizacion evidencias;
    private final PropiedadRepository propiedades;
    private final EncargoService encargos;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    private final EventoDominioRepository eventos;
    private final EfectosContractualesDeFormalizacion efectos;
    private final ComisionService comisiones;
    private final Documentos json;
    private final ObjectMapper mapper;
    private final EntityManager entidades;
    public FormalizacionServiceImpl(ExpedienteOperacionRepository expedientes,RevisionExpedienteOperacionRepository revisiones,
            HitoContractualRepository hitos,RevisionHitoContractualRepository hechos,AccesoEncargo acceso,
            ContextosDeActividad contextos,FuentesDeFormalizacion fuentes,HitosConfirmadosImpl lector,
            EvidenciasDeFormalizacion evidencias,PropiedadRepository propiedades,EncargoService encargos,
            ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos,EventoDominioRepository eventos,
            EfectosContractualesDeFormalizacion efectos,ComisionService comisiones,Documentos json,
            ObjectMapper mapper,EntityManager entidades){
        this.expedientes=expedientes;this.revisiones=revisiones;this.hitos=hitos;this.hechos=hechos;this.acceso=acceso;
        this.contextos=contextos;this.fuentes=fuentes;this.lector=lector;this.evidencias=evidencias;this.propiedades=propiedades;
        this.encargos=encargos;this.comandos=comandos;this.recibos=recibos;this.eventos=eventos;this.efectos=efectos;
        this.comisiones=comisiones;this.json=json;this.mapper=mapper;this.entidades=entidades;
    }

    @Override public Expediente consultar(long id,Actor actor){return ficha(expediente(id,actor),actor);}

    @Override public Object validarActuacion(String capacidad,Map<String,Object> datos,Actor actor){
        Object d=convertir(capacidad,datos);return comprobar(capacidad,d,actor,true);
    }
    @Override public Object resumenRevision(String capacidad,Map<String,Object> datos,Actor actor){
        Object d=convertir(capacidad,datos);exigirLectura(capacidad,d,actor);
        var r=new LinkedHashMap<String,Object>();r.put("actuacion",capacidad);r.put("datosExactos",d);
        if(d instanceof DatosPresentacion p){r.put("pacto",p.pacto());r.put("documentos",p.documentos());}
        else {
            var e=expediente(idExpediente(capacidad,d,actor),actor);r.put("expediente",ficha(e,actor));
            if(d instanceof DatosConfirmacionHito x)r.put("hechoInformadoExacto",resumenHito(e,requerido(x.hito(),"hito"),requerido(x.revisionInformada(),"revisión informada"),actor));
            if(d instanceof DatosRectificacionHito x)r.put("hechoConfirmadoAnterior",resumenHito(e,x.confirmado().id(),x.confirmado().revision(),actor));
        }
        return r;
    }
    @Override public void exigirLecturaActuacion(String capacidad,Map<String,Object> datos,Actor actor){
        exigirLectura(capacidad,convertir(capacidad,datos),actor);
    }
    @Override public Optional<?> consultarResultado(String capacidad,String clave,Actor actor){
        if(capacidad==null||!Set.of(PRESENTAR,EVALUAR,INFORMAR,CONFIRMAR,RECTIFICAR).contains(capacidad))throw new ReglaNegocioException("Capacidad de formalización no admitida.");
        contextos.exigirOperador(actor);String k=Idempotencia.exigirClave(clave);
        var r=recibos.findByOrganizacionIdAndClaveIdempotencia(actor.idOrganizacion(),k);
        if(r.isEmpty())return Optional.empty();
        if(!Objects.equals(r.get().getIdPersonaRol(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        if(!capacidad.equals(r.get().getTipoComando()))throw new ConflictoException("La clave pertenece a otra actuación.");
        return Optional.of(restaurar(capacidad,r.get().getResultado(),actor));
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Expediente presentar(DatosPresentacion d,Actor a,ContextoSesion s,String k,Procedencia p){
        return (Expediente)ejecutar(PRESENTAR,d,a,s,k,p,c->{
            comprobar(PRESENTAR,d,a,false);
            ExpedienteOperacion e;
            if(d.expediente()==null){
                var coberturaInicial=fuentes.cubrir(c,d.oportunidad(),d.pacto(),d.acuerdo(),d.versionAcuerdo(),d.documentos(),d.contratoPreparado(),contextos.hoy(),a);
                var contrato=coberturaInicial.contratoPreparado();Long origen=contrato==null?null:contrato.contratoAnterior();
                if((origen==null?expedientes.originalPorOportunidad(a.idOrganizacion(),d.oportunidad()):expedientes.porContratoOrigen(a.idOrganizacion(),origen)).isPresent())
                    throw new ConflictoException("La oportunidad ya tiene expediente; reenvíalo con su ID y versión.");
                var solicitudes=entidades.createQuery("select s.id from SolicitudAlquiler s where s.organizacionId=:org and s.oportunidad.id=:op",Long.class)
                        .setParameter("org",a.idOrganizacion()).setParameter("op",d.oportunidad()).getResultList();
                if(solicitudes.size()>1)throw new IllegalStateException("La oportunidad conserva varias solicitudes de alquiler.");
                e=expedientes.saveAndFlush(ExpedienteOperacion.abrir(a.idOrganizacion(),c.getId(),d.oportunidad(),
                        d.pacto().revisionAprobatoria(),contrato!=null?contrato.solicitud():solicitudes.isEmpty()?null:solicitudes.getFirst(),origen,ahora()));
                if(contrato!=null)e.vincularContratos(contrato.id(),null);
            }else{e=bloquear(d.expediente(),a);version(d.versionEsperada(),e.getVersion(),"expediente");}
            Long contrato=contratoDePresentacion(d,e,a);
            var cobertura=fuentes.cubrir(c,d.oportunidad(),d.pacto(),d.acuerdo(),d.versionAcuerdo(),d.documentos(),contrato,contextos.hoy(),a);
            if(cobertura.contratoPreparado()!=null){
                if(!Objects.equals(e.getContratoOrigen(),cobertura.contratoPreparado().contratoAnterior()))throw new ConflictoException("El contrato no conserva el episodio de origen del expediente.");
                if(!Objects.equals(e.getContratoAlquiler(),cobertura.contratoPreparado().id()))
                    e.seleccionarContratoPreparado(cobertura.contratoPreparado().id());
            }
            var contenido=new ContenidoFormalizacion.Presentacion(d,cobertura,fuentes.fuenteC4(cobertura,d.oportunidad(),a),p);
            var r=revisiones.saveAndFlush(RevisionExpedienteOperacion.anexar(a.idOrganizacion(),e.getId(),e.getVersion(),
                    "PRESENTACION",null,null,e.getRevisionPresentada(),a.idRolOperativo(),json.objetoCanonico(contenido),ahora()));
            e.presentar(r.getId(),d.pacto().revisionAprobatoria(),d.acuerdo(),d.versionAcuerdo());expedientes.saveAndFlush(e);
            return ficha(e,a);
        });
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Expediente evaluar(DatosRevision d,Actor a,ContextoSesion s,String k,Procedencia p){
        return (Expediente)ejecutar(EVALUAR,d,a,s,k,p,c->{
            var e=bloquear(requerido(d.expediente(),"expediente"),a);version(d.versionEsperada(),e.getVersion(),"expediente");
            var validada=(DatosRevision)comprobar(EVALUAR,d,a,false);
            var previa=lector.presentacion(e,a);
            var contenido=new ContenidoFormalizacion.Revision(validada,validada.coberturaEsperada(),previa.fuenteC4(),p);
            var r=revisiones.saveAndFlush(RevisionExpedienteOperacion.anexar(a.idOrganizacion(),e.getId(),e.getVersion(),
                    "REVISION",validada.decision(),e.getRevisionPresentada(),e.getRevisionEvaluada(),a.idRolOperativo(),json.objetoCanonico(contenido),ahora()));
            e.revisar(r.getId(),"APROBAR".equals(validada.decision()),validada.acuerdoFinal()!=null);expedientes.saveAndFlush(e);return ficha(e,a);
        });
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Hito informar(DatosInformacionHito d,Actor a,ContextoSesion s,String k,Procedencia p){
        return (Hito)ejecutar(INFORMAR,d,a,s,k,p,c->{
            var e=bloquear(requerido(d.expediente(),"expediente"),a);comprobar(INFORMAR,d,a,false);
            var h=hitos.porTipo(a.idOrganizacion(),e.getId(),d.tipo()).orElseGet(()->hitos.saveAndFlush(HitoContractual.abrir(a.idOrganizacion(),e.getId(),d.tipo())));
            if(h.getRevisionConfirmada()!=null)throw new ConflictoException("El hito ya fue confirmado; conserva el antecedente mediante rectificación.");
            var contenido=new ContenidoFormalizacion.Hecho(new ReferenciaPacto(e.getEncargo(),e.getRevisionPacto()),null,d.evidencias(),d.descripcion(),p);
            var r=hechos.saveAndFlush(RevisionHitoContractual.anexar(a.idOrganizacion(),e.getId(),h.getId(),"INFORMACION",d.fechaEfectiva(),
                    e.getRevisionAplicable(),h.getRevisionInformada(),a.idRolOperativo(),json.objetoCanonico(contenido),ahora()));
            h.informar(r.getId());hitos.saveAndFlush(h);
            // El avance de la historia también protege las propuestas contra una información simultánea.
            e.registrarActividad();expedientes.saveAndFlush(e);return fichaHito(e,h,a);
        });
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Hito confirmar(DatosConfirmacionHito d,Actor a,ContextoSesion s,String k,Procedencia p){
        return (Hito)ejecutar(CONFIRMAR,d,a,s,k,p,c->{
            var h=buscarHito(requerido(d.hito(),"hito"),a);var e=bloquear(h.getExpediente(),a);entidades.refresh(h,LockModeType.PESSIMISTIC_WRITE);comprobar(CONFIRMAR,d,a,false);
            if(h.getRevisionConfirmada()!=null)throw new ConflictoException("El hito ya fue confirmado; no se confirma una segunda vez.");
            var info=hechos.buscar(a.idOrganizacion(),e.getId(),h.getId(),d.revisionInformada()).orElseThrow(()->new NoEncontradoException("Información del hito"));
            var original=json.comoObjeto(info.getContenido(),ContenidoFormalizacion.Hecho.class);
            var revisada=lector.revisionAprobada(e,d.revisionExpediente(),a);
            return confirmarRevision(e,h,"CONFIRMACION",info.getFechaEfectiva(),d.revisionExpediente(),info.getId(),
                    original.evidencias(),original.descripcion(),revisada,a,s,k,p);
        });
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Hito rectificar(DatosRectificacionHito d,Actor a,ContextoSesion s,String k,Procedencia p){
        return (Hito)ejecutar(RECTIFICAR,d,a,s,k,p,c->{
            var h=buscarHito(d.confirmado().id(),a);var e=bloquear(h.getExpediente(),a);entidades.refresh(h,LockModeType.PESSIMISTIC_WRITE);comprobar(RECTIFICAR,d,a,false);
            var revisada=lector.revisionAprobada(e,e.getRevisionAplicable(),a);
            return confirmarRevision(e,h,"RECTIFICACION",d.fechaEfectiva(),e.getRevisionAplicable(),d.confirmado().revision(),
                    d.evidencias(),d.motivo(),revisada,a,s,k,p);
        });
    }

    private Hito confirmarRevision(ExpedienteOperacion e,HitoContractual h,String tipo,LocalDate fecha,long revision,
            Long antecedente,List<ReferenciaDocumento> evidenciasHito,String descripcion,ContenidoFormalizacion.Revision revisada,
            Actor a,ContextoSesion s,String clave,Procedencia p){
        var fuente=fuentes.fuenteRevision(e.getId(),revision,revisada);
        var contenido=new ContenidoFormalizacion.Hecho(revisada.cobertura().pacto(),fuente==null?null:fuente.referencia(),evidenciasHito,descripcion,p);
        var r=hechos.saveAndFlush(RevisionHitoContractual.anexar(a.idOrganizacion(),e.getId(),h.getId(),tipo,fecha,revision,
                antecedente,a.idRolOperativo(),json.objetoCanonico(contenido),ahora()));
        h.confirmar(r.getId());hitos.saveAndFlush(h);
        e.registrarActividad();expedientes.saveAndFlush(e);
        var contexto=lector.consultarContexto(new ReferenciaContextoEconomico(e.getId()),a);
        var confirmado=contexto.hechosConfirmados().stream().filter(x->x.referencia().id()==h.getId()).findFirst()
                .orElseThrow(()->new IllegalStateException("El hito confirmado no está en su contexto."));
        var vinculos=efectos.aplicar(contexto,confirmado,a,s,clave,p,revisada.datos().acuerdoFinal(),revisada.cobertura().contratoPreparado());
        if(vinculos==null)throw new IllegalStateException("El efecto contractual no devolvió su resultado.");
        e.vincularContratos(vinculos.contratoAlquiler(),vinculos.contratoVenta());expedientes.saveAndFlush(e);
        comisiones.evaluar(contexto.referencia(),confirmado.referencia(),a,s,Idempotencia.huellaExacta("FORMALIZACION-COMISION\u0000"+clave),p);
        return fichaHito(e,h,a);
    }

    private Object ejecutar(String cap,Object datos,Actor a,ContextoSesion s,String clave,Procedencia p,Function<Captacion,Object> accion){
        contextos.exigirOperador(a);String k=Idempotencia.exigirClave(clave);
        long id=encargo(cap,datos,a);var inicio=acceso.iniciar(a,s,id);
        String lock=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+a.idOrganizacion()+"\u0000"+k);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(lock.substring(0,16),16));
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("capacidad",cap,"datos",datos,"procedencia",p)));
        Optional<com.controllocal.domain.auditoria.ComandoIdempotente> previo;
        try{previo=comandos.buscar(a,k,cap,hash);}catch(ReglaNegocioException conflicto){throw new ConflictoException("La clave identifica una actuación de otro contenido.");}
        if(previo.isPresent())return restaurar(cap,previo.get().getResultado(),a);
        var c=bloquearObjeto(id,a,inicio);Object resultado=accion.apply(c);
        String serializado=json.objetoCanonico(resultado);long entidad=resultado instanceof Expediente e?e.id():((Hito)resultado).id();
        String tipo=resultado instanceof Expediente?"EXPEDIENTE_OPERACION":"HITO_CONTRACTUAL";
        eventos.saveAndFlush(p.sinPeticion().sellar(EventoDominio.de(a.idOrganizacion(),"FORMALIZACION_REGISTRADA",tipo,entidad,a.idRolOperativo(),p.canal()))
                .con(json.objetoCanonico(com.controllocal.service.soporte.AvisoComercialBroker.con(Map.of("actuacion",cap,"entidad",entidad),
                    Set.of(PRESENTAR,INFORMAR,CONFIRMAR,RECTIFICAR).contains(cap)?new com.controllocal.service.soporte.AvisoComercialBroker.Marca(
                        Set.of(CONFIRMAR,RECTIFICAR).contains(cap)?"NB-08":"NB-07","ENCARGO",id,
                        resultado instanceof Expediente e?e.version():((Hito)resultado).version(),List.of("FORMALIZACION")):null))));
        comandos.registrar(a,k,cap,hash,tipo,entidad,p,serializado);entidades.flush();return resultado;
    }

    /** Mismo orden que negociación: cuentas/SID, identidad, unidades, Encargo, expediente e hito. */
    private Captacion bloquearObjeto(long id,Actor a,AccesoEncargo.Inicio inicio){
        var previa=acceso.leer(id,a);var ids=new TreeSet<Long>();ids.add(previa.getPropiedad().getId());
        if(previa.getIdComposicionActual()!=null)encargos.consultarComposicion(id,null,a).unidades().forEach(u->ids.add(u.idPropiedad()));
        for(long unidad:ids){var p=propiedades.bloquearParaEscritura(a.idOrganizacion(),unidad).orElseThrow(()->new NoEncontradoException("Propiedad"));entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);}
        var c=acceso.escribir(id,a,inicio);entidades.refresh(c,LockModeType.PESSIMISTIC_WRITE);acceso.exigirLectura(id,a);
        if(!Objects.equals(inicio.responsable(),c.getAgente().getId()))throw new ConflictoException("Cambió el responsable del Encargo.");
        return c;
    }

    private Object comprobar(String cap,Object datos,Actor a,boolean preparando){
        contextos.exigirOperador(a);var c=acceso.leer(encargo(cap,datos,a),a);
        if(Set.of(PRESENTAR,INFORMAR).contains(cap))acceso.exigirResponsable(c,a);else acceso.exigirDecision(c,a);
        if(datos instanceof DatosPresentacion d){
            requerido(d.oportunidad(),"oportunidad");version(d.versionEncargo(),c.getVersion(),"Encargo");
            if((d.expediente()==null)!=(d.versionEsperada()==null))throw new ReglaNegocioException("El reenvío conserva ID y versión del expediente.");
            if(d.expediente()!=null){var e=expediente(d.expediente(),a);version(d.versionEsperada(),e.getVersion(),"expediente");
                if(e.getEncargo()!=c.getId()||e.getOportunidad()!=d.oportunidad())throw new NoEncontradoException("Contexto del expediente");}
            var existente=d.expediente()==null?null:expediente(d.expediente(),a);
            var cobertura=fuentes.cubrir(c,d.oportunidad(),d.pacto(),d.acuerdo(),d.versionAcuerdo(),d.documentos(),contratoDePresentacion(d,existente,a),contextos.hoy(),a);
            exigirPreparacionInicial(cobertura,existente,a);
            fuentes.exigirVersionOportunidad(d.oportunidad(),d.versionOportunidad(),a);
            return d;
        }
        var e=expediente(idExpediente(cap,datos,a),a);
        if(datos instanceof DatosRevision d){
            version(d.versionEsperada(),e.getVersion(),"expediente");
            if(d.decision()==null||!Set.of("APROBAR","OBSERVAR","RECHAZAR").contains(d.decision()))throw new ReglaNegocioException("Decisión de revisión no admitida.");
            var presentada=lector.presentacion(e,a);
            if(d.documentosRevisados()==null||!d.documentosRevisados().containsAll(presentada.cobertura().documentos()))
                throw new ReglaNegocioException("La revisión debe conservar las evidencias de la presentación.");
            var cobertura=fuentes.cubrir(c,e.getOportunidad(),new ReferenciaPacto(e.getEncargo(),e.getRevisionPacto()),
                    e.getAcuerdoC4(),e.getVersionAcuerdoC4(),d.documentosRevisados(),e.getContratoAlquiler(),contextos.hoy(),a);
            exigirPreparacionInicial(cobertura,e,a);
            var acuerdo=d.acuerdoFinal();
            if(preparando&&acuerdo==null)acuerdo=reutilizarPreparacion(cobertura);
            fuentes.validarFinal(acuerdo,c,a);
            if("APROBAR".equals(d.decision())){
                if(!Boolean.TRUE.equals(d.partesYRepresentacionRevisadas())||!Boolean.TRUE.equals(d.composicionYAcuerdoRevisados()))
                    throw new ReglaNegocioException("Confirma la revisión de partes, representación, composición y acuerdo.");
                if(cobertura.partes().partes().isEmpty()||cobertura.interesado()==null)throw new ReglaNegocioException("La revisión aún no identifica sus partes reales.");
            }
            if(!preparando){if(d.coberturaEsperada()==null)throw new ConflictoException("Falta la cobertura de la propuesta revisada.");fuentes.exigirMismaCobertura(d.coberturaEsperada(),cobertura);}
            return new DatosRevision(d.expediente(),d.versionEsperada(),d.decision(),d.observaciones(),d.documentosRevisados(),
                    d.partesYRepresentacionRevisadas(),d.composicionYAcuerdoRevisados(),acuerdo,cobertura);
        }
        if(datos instanceof DatosInformacionHito d){
            version(d.versionExpediente(),e.getVersion(),"expediente");exigirTipo(c,d.tipo());
            evidencias.exigir(e.getEncargo(),d.evidencias(),HITO,a);return d;
        }
        if(datos instanceof DatosConfirmacionHito d){
            var h=buscarHito(requerido(d.hito(),"hito"),a);version(d.versionHito(),h.getVersion(),"hito");version(d.versionExpediente(),e.getVersion(),"expediente");
            exigirTipo(c,h.getTipo());
            if(!Objects.equals(e.getRevisionAplicable(),d.revisionExpediente())||!Objects.equals(h.getRevisionInformada(),d.revisionInformada()))
                throw new ConflictoException("Revisa las versiones exactas del expediente y del hecho informado.");
            var info=hechos.buscar(a.idOrganizacion(),e.getId(),h.getId(),requerido(d.revisionInformada(),"revisión informada"))
                    .orElseThrow(()->new NoEncontradoException("Información del hito"));
            if(info.getFechaEfectiva()==null)throw new ReglaNegocioException("Conserva la fecha efectiva conocida del hecho.");
            var evidencia=json.comoObjeto(info.getContenido(),ContenidoFormalizacion.Hecho.class).evidencias();
            if(evidencia.isEmpty())throw new ReglaNegocioException("Conserva evidencia del hito ocurrido.");evidencias.exigir(e.getEncargo(),evidencia,HITO,a);
            var r=exigirRevisionVigente(e,c,d.revisionExpediente(),d.acuerdoFinal(),a);
            if(!Objects.equals(d.pacto(),r.cobertura().pacto()))throw new ConflictoException("El pacto no coincide con la revisión aprobada.");
            if(!Objects.equals(json.comoObjeto(info.getContenido(),ContenidoFormalizacion.Hecho.class).pacto(),d.pacto()))
                throw new ConflictoException("Conserva el pacto del hecho informado; una adenda no se aplica retroactivamente por defecto.");
            exigirBase(h.getTipo(),r);return d;
        }
        if(datos instanceof DatosRectificacionHito d){
            if(d.confirmado()==null)throw new ReglaNegocioException("Identifica el hecho que rectificas.");var h=buscarHito(d.confirmado().id(),a);
            version(d.versionHito(),h.getVersion(),"hito");version(d.versionExpediente(),e.getVersion(),"expediente");
            exigirTipo(c,h.getTipo());
            if(!Objects.equals(h.getRevisionConfirmada(),d.confirmado().revision()))throw new ConflictoException("El hecho confirmado cambió; consulta su revisión actual.");
            if(d.fechaEfectiva()==null||d.motivo()==null||d.motivo().isBlank()||d.evidencias()==null||d.evidencias().isEmpty())
                throw new ReglaNegocioException("La rectificación conserva fecha efectiva, motivo y evidencia.");
            evidencias.exigir(e.getEncargo(),d.evidencias(),HITO,a);
            var r=exigirRevisionVigente(e,c,e.getRevisionAplicable(),d.acuerdoFinal(),a);
            var anterior=lector.confirmado(e,h,d.confirmado().revision(),a);
            if(!Objects.equals(anterior.pacto(),r.cobertura().pacto()))throw new ConflictoException("La rectificación conserva el pacto histórico del hecho.");
            exigirBase(h.getTipo(),r);return d;
        }
        throw new ReglaNegocioException("Actuación de formalización no admitida.");
    }

    private ContenidoFormalizacion.Revision exigirRevisionVigente(ExpedienteOperacion e,Captacion c,Long id,AcuerdoFinalDocumentado finalDeclarado,Actor a){
        if(id==null||!Objects.equals(e.getRevisionAplicable(),id))throw new ConflictoException("El expediente necesita revisión competente aplicable.");
        var r=lector.revisionAprobada(e,id,a);
        var actual=fuentes.cubrir(c,e.getOportunidad(),new ReferenciaPacto(e.getEncargo(),e.getRevisionPacto()),e.getAcuerdoC4(),e.getVersionAcuerdoC4(),
                r.cobertura().documentos(),r.cobertura().contratoPreparado()==null?null:r.cobertura().contratoPreparado().id(),contextos.hoy(),a);fuentes.exigirMismaCobertura(r.cobertura(),actual);
        if(!mapper.valueToTree(r.datos().acuerdoFinal()).equals(mapper.valueToTree(finalDeclarado)))
            throw new ConflictoException("El acuerdo final difiere de la revisión aprobada; revisa el cambio antes de confirmar.");
        return r;
    }
    private static void exigirBase(TipoHitoContractual tipo,ContenidoFormalizacion.Revision r){
        var f=r.datos().acuerdoFinal();
        if(tipo==TipoHitoContractual.ESCRITURA&&(f==null||f.precioVenta()==null))
            throw new ReglaNegocioException("La escritura necesita el precio final realmente acordado y revisado.");
        if(tipo==TipoHitoContractual.CONTRATO_ALQUILER&&(f==null||f.rentaMensual()==null||f.terminosAlquiler()==null))
            throw new ReglaNegocioException("El contrato firmado necesita renta y fechas contractuales acreditadas y revisadas.");
    }
    private static void exigirTipo(Captacion c,TipoHitoContractual tipo){
        if(tipo==null||c.operacion()==OperacionInmobiliaria.VENTA&&tipo==TipoHitoContractual.CONTRATO_ALQUILER
                ||c.operacion()==OperacionInmobiliaria.ALQUILER&&tipo!=TipoHitoContractual.CONTRATO_ALQUILER)
            throw new ReglaNegocioException("El tipo de hito no corresponde a esta operación.");
    }

    private Object convertir(String cap,Map<String,Object> d){
        if(d==null)throw new ReglaNegocioException("Conserva los datos de la actuación.");
        try{return switch(cap){case PRESENTAR->mapper.convertValue(d,DatosPresentacion.class);case EVALUAR->mapper.convertValue(d,DatosRevision.class);
            case INFORMAR->mapper.convertValue(d,DatosInformacionHito.class);case CONFIRMAR->mapper.convertValue(d,DatosConfirmacionHito.class);
            case RECTIFICAR->mapper.convertValue(d,DatosRectificacionHito.class);default->throw new ReglaNegocioException("Capacidad de formalización no admitida.");};}
        catch(IllegalArgumentException ex){throw new ReglaNegocioException("Los datos no corresponden al contrato de formalización.");}
    }
    private long encargo(String cap,Object d,Actor a){return d instanceof DatosPresentacion p?requerido(p.encargo(),"Encargo"):expediente(idExpediente(cap,d,a),a).getEncargo();}
    private long idExpediente(String cap,Object d,Actor a){
        if(d instanceof DatosRevision x)return requerido(x.expediente(),"expediente");
        if(d instanceof DatosInformacionHito x)return requerido(x.expediente(),"expediente");
        if(d instanceof DatosConfirmacionHito x)return buscarHito(requerido(x.hito(),"hito"),a).getExpediente();
        if(d instanceof DatosRectificacionHito x){if(x.confirmado()==null)throw new ReglaNegocioException("Identifica el hecho confirmado.");return buscarHito(x.confirmado().id(),a).getExpediente();}
        throw new ReglaNegocioException("Identifica el expediente de la actuación.");
    }
    private void exigirLectura(String cap,Object d,Actor a){
        contextos.exigirOperador(a);
        if(d instanceof DatosPresentacion p){var c=acceso.leer(requerido(p.encargo(),"Encargo"),a);
            var existente=p.expediente()==null?null:expediente(p.expediente(),a);
            fuentes.cubrir(c,requerido(p.oportunidad(),"oportunidad"),p.pacto(),p.acuerdo(),p.versionAcuerdo(),p.documentos(),contratoDePresentacion(p,existente,a),contextos.hoy(),a);return;}
        var e=expediente(idExpediente(cap,d,a),a);
        if(d instanceof DatosRevision r&&r.coberturaEsperada()!=null)fuentes.reautorizar(e.getEncargo(),e.getOportunidad(),r.coberturaEsperada(),a);
        if(d instanceof DatosRevision r&&r.acuerdoFinal()!=null)evidencias.exigir(e.getEncargo(),r.acuerdoFinal().documentos(),HITO,a);
        if(d instanceof DatosInformacionHito r)evidencias.exigir(e.getEncargo(),r.evidencias(),HITO,a);
        if(d instanceof DatosConfirmacionHito r){
            resumenHito(e,requerido(r.hito(),"hito"),requerido(r.revisionInformada(),"revisión informada"),a);
            lector.revisionAprobada(e,requerido(r.revisionExpediente(),"revisión de expediente"),a);
            if(r.acuerdoFinal()!=null)evidencias.exigir(e.getEncargo(),r.acuerdoFinal().documentos(),HITO,a);
        }
        if(d instanceof DatosRectificacionHito r){
            evidencias.exigir(e.getEncargo(),r.evidencias(),HITO,a);resumenHito(e,r.confirmado().id(),r.confirmado().revision(),a);
            if(r.acuerdoFinal()!=null)evidencias.exigir(e.getEncargo(),r.acuerdoFinal().documentos(),HITO,a);
        }
    }
    private ExpedienteOperacion expediente(long id,Actor a){return lector.expediente(new ReferenciaContextoEconomico(id),a);}
    private ExpedienteOperacion bloquear(long id,Actor a){
        var e=expedientes.bloquear(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Expediente"));
        entidades.refresh(e,LockModeType.PESSIMISTIC_WRITE);acceso.exigirLectura(e.getEncargo(),a);return e;
    }
    private HitoContractual buscarHito(long id,Actor a){var h=hitos.buscar(a.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Hito"));expediente(h.getExpediente(),a);return h;}
    private RevisionHitoContractual revisionHito(ExpedienteOperacion e,HitoContractual h,long id,Actor a){
        return hechos.buscar(a.idOrganizacion(),e.getId(),h.getId(),id).orElseThrow(()->new NoEncontradoException("Revisión del hito"));
    }
    private Hito fichaHito(ExpedienteOperacion e,HitoContractual h,Actor a){
        if(h.getRevisionInformada()==null)throw new IllegalStateException("El hito no conserva información original.");
        var r=revisionHito(e,h,h.getRevisionConfirmada()==null?h.getRevisionInformada():h.getRevisionConfirmada(),a);
        var c=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Hecho.class);evidencias.exigir(e.getEncargo(),c.evidencias(),HITO,a);
        return new Hito(h.getId(),h.getVersion(),e.getId(),h.getTipo(),r.getFechaEfectiva(),h.getRevisionInformada(),h.getRevisionConfirmada());
    }
    private Object resumenHito(ExpedienteOperacion e,long id,long revision,Actor a){
        var h=buscarHito(id,a);var r=revisionHito(e,h,revision,a);
        var contenido=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Hecho.class);
        fuentes.reautorizarPacto(contenido.pacto(),a);evidencias.exigir(e.getEncargo(),contenido.evidencias(),HITO,a);
        var resumen=new LinkedHashMap<String,Object>();resumen.put("referencia",new ReferenciaHito(id,revision));
        resumen.put("tipo",h.getTipo());resumen.put("fechaEfectiva",r.getFechaEfectiva());resumen.put("registradoEn",r.getRegistradoEn());
        resumen.put("informacion",contenido);return resumen;
    }
    private Long contratoDePresentacion(DatosPresentacion d,ExpedienteOperacion e,Actor a){
        if(d.contratoPreparado()!=null&&e!=null&&e.getContratoAlquiler()!=null&&!Objects.equals(d.contratoPreparado(),e.getContratoAlquiler()))
            if(hitos.delExpediente(a.idOrganizacion(),e.getId()).stream().anyMatch(h->h.getRevisionConfirmada()!=null))
                throw new ConflictoException("El expediente ya confirmó un hito; conserva la identidad contractual original.");
        return d.contratoPreparado()!=null?d.contratoPreparado():e==null?null:e.getContratoAlquiler();
    }
    private void exigirPreparacionInicial(CoberturaRevision cobertura,ExpedienteOperacion e,Actor a){
        var p=cobertura.contratoPreparado();if(p==null)return;
        if(e!=null&&!Objects.equals(e.getContratoOrigen(),p.contratoAnterior()))throw new ConflictoException("El candidato pertenece a otro episodio de renovación.");
        if(acceso.leer(p.encargo(),a).operacion()!=OperacionInmobiliaria.ALQUILER)
            throw new ReglaNegocioException("El contrato preparado corresponde exclusivamente al alquiler.");
        boolean confirmado=e!=null&&hitos.delExpediente(a.idOrganizacion(),e.getId()).stream().anyMatch(h->h.getRevisionConfirmada()!=null);
        if(!confirmado&&!ContratoAlquiler.EN_PROCESO.equals(p.estado()))throw new ConflictoException("El contrato candidato ya no está en preparación.");
    }
    private static AcuerdoFinalDocumentado reutilizarPreparacion(CoberturaRevision c){
        var p=c.contratoPreparado();if(p==null||c.documentos().isEmpty())return null;
        OfertaService.Dinero renta=p.rentaContractual()!=null&&p.moneda()!=null&&!p.moneda().isBlank()
                ?new OfertaService.Dinero(p.rentaContractual(),p.moneda(),null,null):null;
        TerminosAlquiler terminos=p.inicio()!=null&&p.fin()!=null?new TerminosAlquiler(p.inicio(),p.fin()):null;
        return renta==null&&terminos==null?null:new AcuerdoFinalDocumentado(null,renta,null,c.documentos(),null,terminos);
    }
    private Expediente ficha(ExpedienteOperacion e,Actor a){
        var contexto=lector.consultarContexto(new ReferenciaContextoEconomico(e.getId()),a);var p=lector.presentacion(e,a);
        var historia=new ArrayList<RevisionExpediente>();String decision=null;
        for(var r:revisiones.historia(a.idOrganizacion(),e.getId()))if("REVISION".equals(r.getTipoRegistro())){
            var contenido=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Revision.class);fuentes.reautorizar(e.getEncargo(),e.getOportunidad(),contenido.cobertura(),a);
            historia.add(new RevisionExpediente(r.getId(),e.getId(),r.getVersionRevisada(),r.getDecision(),contenido.datos().observaciones(),r.getAntecedente(),
                    r.getRegistradoEn(),contenido.procedencia(),contenido.cobertura()));if(Objects.equals(r.getPresentacion(),e.getRevisionPresentada()))decision=r.getDecision();
        }
        return new Expediente(e.getId(),e.getVersion(),e.getEncargo(),e.getOportunidad(),contexto.pacto(),decision,e.getRevisionAplicable(),contexto.fuenteEconomica(),
                p.cobertura().documentos(),hitos.delExpediente(a.idOrganizacion(),e.getId()).stream().map(h->fichaHito(e,h,a)).toList(),historia);
    }
    private Object restaurar(String cap,String original,Actor a){
        if(Set.of(PRESENTAR,EVALUAR).contains(cap)){
            var r=json.comoObjeto(original,Expediente.class);expediente(r.id(),a);
            // Reautoriza todas las referencias originales antes de entregar el snapshot, sin repetir CAS/efecto.
            acceso.exigirLectura(r.encargo(),a);fuentes.reautorizarPacto(r.pacto(),a);
            for(var rev:r.revisiones())fuentes.reautorizar(r.encargo(),r.oportunidad(),rev.cobertura(),a);
            for(var h:r.hitos())reautorizarHito(h,a);
            if(r.fuenteEconomica()!=null)lector.consultarFuente(new ReferenciaContextoEconomico(r.id()),r.fuenteEconomica().referencia(),a);
            evidencias.exigir(r.encargo(),r.documentos(),HITO,a);return r;
        }
        if(!Set.of(INFORMAR,CONFIRMAR,RECTIFICAR).contains(cap))throw new ReglaNegocioException("Capacidad de formalización no admitida.");
        var r=json.comoObjeto(original,Hito.class);reautorizarHito(r,a);return r;
    }
    private void reautorizarHito(Hito r,Actor a){var e=expediente(r.expediente(),a);var h=buscarHito(r.id(),a);
        long id=r.revisionConfirmada()==null?r.revisionInformada():r.revisionConfirmada();var v=revisionHito(e,h,id,a);
        var contenido=json.comoObjeto(v.getContenido(),ContenidoFormalizacion.Hecho.class);evidencias.exigir(e.getEncargo(),contenido.evidencias(),HITO,a);
        fuentes.reautorizarPacto(contenido.pacto(),a);
        if(r.revisionConfirmada()!=null)lector.confirmado(e,h,r.revisionConfirmada(),a);
    }
    private OffsetDateTime ahora(){return OffsetDateTime.ofInstant(contextos.ahora(),ZoneOffset.UTC);}
    private static long requerido(Long id,String nombre){if(id==null||id<=0)throw new ReglaNegocioException("Identifica "+nombre+".");return id;}
    private static void version(Long esperada,long actual,String nombre){if(esperada==null||esperada<0||esperada!=actual)throw new ConflictoException("Cambió la versión de "+nombre+"; consulta su estado actual.");}
}
