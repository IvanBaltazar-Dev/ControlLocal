package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.HechoAsunto;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.PendientesComercialesRadarRepository;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Fuentes actuales D5. No crea estados comerciales ni reutiliza los importes de una campana. */
@Service
public class PendientesComercialesRadar {
    public record Actuacion(String id,String motivo,String recurso,long referencia,long encargo,String operacion,
        HechoAsunto fuente,Instant registradoEn,LocalDate fechaActuacion,String siguiente,boolean accionable,
        PoliticaDeDespacho.Clasificacion orden) { }
    private final PendientesComercialesRadarRepository repo;
    private final ContextosDeActividad contexto;
    private final AccesoEncargo acceso;
    private final AccesoOportunidad oportunidades;
    private final ComisionService comisiones;
    public PendientesComercialesRadar(PendientesComercialesRadarRepository repo,ContextosDeActividad contexto,
        AccesoEncargo acceso,AccesoOportunidad oportunidades,ComisionService comisiones){
        this.repo=repo;this.contexto=contexto;this.acceso=acceso;this.oportunidades=oportunidades;this.comisiones=comisiones;
    }
    @Transactional(readOnly=true)
    public List<Actuacion> de(PoliticaDeDespacho.Consulta consulta,Actor actor){
        if(actor==null)throw new AccesoNoAutorizadoException();
        if(actor.esTenantAdmin())return List.of();
        contexto.exigirOperador(actor);
        var ops=actor.esAgente()?List.of(actor.idRolOperativo()):List.<Long>of();var dec=acceso.paraDecision(actor).rolesAgente();
        var fuentes=repo.fuentes(actor.idOrganizacion(),ops,dec);var economicas=repo.economia(actor.idOrganizacion(),dec);var salida=new ArrayList<Actuacion>();
        for(var f:fuentes){acceso.exigirLectura(f.encargo(),actor);
            if("OFERTA_PENDIENTE".equals(f.motivo()))oportunidades.contextoParaNegociacion(f.oportunidad(),actor);
            salida.add(actuacion(f.motivo(),f.recurso(),f.referencia(),f.encargo(),OperacionInmobiliaria.deCodigo(f.operacion()).name(),new HechoAsunto(f.tipoHecho(),f.hecho(),f.version()),f.registrada(),f.fechaActuacion(),siguiente(f.motivo()),true));
        }
        for(var e:economicas){
            acceso.exigirLectura(e.encargo(),actor);var f=comisiones.gestionParaRadar(new ReferenciaPacto(e.encargo(),e.revisionPacto()),new ReferenciaContextoEconomico(e.contexto()),actor);
            String motivo=null;LocalDate fecha=null;
            if(f.basePendiente())motivo="COMISION_BASE_PENDIENTE";
            else if(Boolean.TRUE.equals(f.saldoExigiblePendiente())){motivo="SALDO_EXIGIBLE_PENDIENTE";fecha=f.revisarEn();}
            if(motivo==null&&f.saldoExigiblePendiente()==null)motivo="GESTION_COMISION_NO_EVALUABLE";
            if(motivo!=null)salida.add(actuacion(motivo,"COMISION_LIQUIDACION",e.comision(),e.encargo(),OperacionInmobiliaria.deCodigo(e.operacion()).name(),new HechoAsunto("COMISION_EVALUACION",e.evaluacion(),null),e.registrada(),fecha,siguiente(motivo),!"GESTION_COMISION_NO_EVALUABLE".equals(motivo)));
        }
        contexto.exigirOperador(actor);var finales=acceso.paraDecision(actor).rolesAgente();
        if(!new HashSet<>(dec).equals(new HashSet<>(finales))||!fuentes.equals(repo.fuentes(actor.idOrganizacion(),ops,finales))||!economicas.equals(repo.economia(actor.idOrganizacion(),finales)))
            throw new ConflictoException("Cambió el contexto o la fuente de las actuaciones del Radar.");
        var q=consulta==null?PoliticaDeDespacho.Consulta.todas():consulta;
        if(Set.of("ENCARGO","CAPTACION").contains(q.recurso()==null?"":q.recurso())){
            long encargoConsultado=q.referencia();
            salida.removeIf(a->a.encargo()!=encargoConsultado);
            q=new PoliticaDeDespacho.Consulta(q.vista()==PoliticaDeDespacho.Vista.PROCESO?PoliticaDeDespacho.Vista.TODAS:q.vista(),null,null);
        }
        return PoliticaDeDespacho.despachar(salida,PendientesComercialesRadar::asunto,q,contexto.ahora());
    }
    private Actuacion actuacion(String motivo,String recurso,long ref,long encargo,String operacion,HechoAsunto fuente,Instant registrada,LocalDate fecha,String siguiente,boolean accionable){
        String id=motivo+":"+fuente.tipo()+":"+fuente.referencia();
        var a=new PoliticaDeDespacho.Asunto(id,recurso,ref,accionable,fecha,null,registrada,false,null,false,false);
        return new Actuacion(id,motivo,recurso,ref,encargo,operacion,fuente,registrada,fecha,siguiente,accionable,PoliticaDeDespacho.clasificar(a,contexto.ahora()));
    }
    private static PoliticaDeDespacho.Asunto asunto(Actuacion a){return new PoliticaDeDespacho.Asunto(a.id(),a.recurso(),a.referencia(),a.accionable(),a.fechaActuacion(),null,a.registradoEn(),false,null,false,false);}
    private static String siguiente(String motivo){return switch(motivo){
        case "OFERTA_PENDIENTE"->"Revisar la propuesta vigente y preparar la siguiente gestión";
        case "EXPEDIENTE_PRESENTADO"->"Revisar la versión presentada y su evidencia";
        case "OBSERVACIONES_EXPEDIENTE"->"Corregir las observaciones y presentar la versión correspondiente";
        case "HITO_INFORMADO"->"Revisar la evidencia y registrar la confirmación competente";
        case "ADENDA_PENDIENTE"->"Revisar el borrador de adenda y sus diferencias";
        case "FIN_ENCARGO_SOLICITADO"->"Revisar la solicitud sin anticipar su efecto";
        case "CONTINUIDAD_ENCARGO"->"Revisar la continuidad del Encargo y preparar la gestión pertinente";
        case "COMISION_BASE_PENDIENTE"->"Completar o revisar la base acreditada; no sustituir el cálculo por cero";
        case "SALDO_EXIGIBLE_PENDIENTE"->"Revisar la gestión o registrar fondos efectivamente recibidos";
        case "GESTION_COMISION_NO_EVALUABLE"->"La evidencia disponible no permite determinar un saldo exigible; consultar los antecedentes autorizados";
        default->throw new IllegalStateException("La fuente no tiene una actuación D5 definida.");};}
}
