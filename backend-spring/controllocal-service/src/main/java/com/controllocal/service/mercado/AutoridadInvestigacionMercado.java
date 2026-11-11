package com.controllocal.service.mercado;

import com.controllocal.persistence.repositorio.InvestigacionesMercadoRepository;
import com.controllocal.persistence.repositorio.InvestigacionesMercadoRepository.Trabajo;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.mercado.ContratoInvestigacionManual.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Dueño I0 del recurso B0, sin otro autorizador comercial ni dependencia circular con el financiador. */
@Component
public class AutoridadInvestigacionMercado implements AutorizadorRecursoIa {
    private final InvestigacionesMercadoRepository repo;
    private final ContextosDeActividad contextos;
    private final AutoridadDePropiedad propiedades;
    private final Documentos json;
    public AutoridadInvestigacionMercado(InvestigacionesMercadoRepository repo,ContextosDeActividad contextos,AutoridadDePropiedad propiedades,Documentos json){this.repo=repo;this.contextos=contextos;this.propiedades=propiedades;this.json=json;}
    @Override public boolean atiende(String perfil,String tipo){return PresupuestoIaService.PERFIL_MANUAL.equals(perfil)&&"INVESTIGACION_MERCADO".equals(tipo);}
    @Override public void reautorizar(Solicitud d,Actor a){
        contextos.exigirOperador(a);UUID id=identidad(d.recurso());
        var t=repo.trabajo(a.idOrganizacion(),id,false).orElseThrow(AccesoNoAutorizadoException::new);
        if(!Objects.equals(t.persona(),a.idPersona())||!Objects.equals(t.rol(),a.idRolOperativo()))throw new AccesoNoAutorizadoException();
        exigirRecurso(t,d.recurso());
        if(t.propiedad()!=null&&!propiedades.puedeLeer(a,t.propiedad()))throw new AccesoNoAutorizadoException();
        var p=repo.politica(t.politica(),t.versionPolitica(),true).orElseThrow(CapacidadDeshabilitadaException::new);
        var ahora=repo.ahora();if(!p.habilitada()||p.retirada()!=null||ahora.isBefore(p.desde())||!ahora.isBefore(p.hasta()))throw new CapacidadDeshabilitadaException();
    }
    /** B0 ya conserva el lock de organización/operación; I0 bloquea la prueba hasta la liquidación. */
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void exigirEvidenciaManual(long org,UUID operacion,Recurso recurso,BigDecimal rondas,String evidencia){
        var t=repo.trabajo(org,operacion,true).orElseThrow(AccesoNoAutorizadoException::new);exigirRecurso(t,recurso);
        if(rondas==null)throw new ConflictoException("Falta acreditación de rondas.");
        if("RETIRADA".equals(t.estado())&&t.operador()==null&&t.revision()==null&&rondas.signum()==0&&retiro(t.solicitud()).equals(evidencia))return;
        if(!Set.of("REVISADA","PUBLICADA").contains(t.estado())||t.revision()==null||t.revisor()==null||t.entrega()==null||t.acreditadas()==null||!revision(t.revision()).equals(evidencia))throw new ConflictoException("El trabajo conserva su evidencia pendiente de revisión competente.");
        var rs=List.of(json.comoObjeto(t.entrega(),Ronda[].class));
        for(var r:rs){if(r.revision()==null||!t.revision().toString().equals(r.revision().referenciaDecision())||r.revision().revisor()!=t.revisor())throw new ConflictoException("La ronda no corresponde a la revisión acreditada del trabajo.");
            if(r.cierre()==Cierre.INCIERTA)throw new ConflictoException("La incertidumbre conserva la misma operación y su reserva.");}
        var resultado=ContratoInvestigacionManual.comprobar(json.comoObjeto(t.plan(),Plan.class),rs,repo.ahora());
        var completas=BigDecimal.valueOf(resultado.rondasCompletas().size());
        if(completas.compareTo(t.acreditadas())!=0||completas.compareTo(rondas)!=0)throw new ConflictoException("La conciliación debe conservar las rondas completas acreditadas de esa revisión.");
    }
    public static String revision(UUID id){return "I0:REVISION:"+id;}
    public static String retiro(UUID id){return "I0:RETIRO:"+id;}
    private static UUID identidad(Recurso r){
        if(r==null||!"INVESTIGACION_MERCADO".equals(r.tipo())||!"INVESTIGACION_PUNTUAL".equals(r.finalidad()))throw new AccesoNoAutorizadoException();
        try{return UUID.fromString(r.referencia());}catch(Exception e){throw new AccesoNoAutorizadoException();}
    }
    private static void exigirRecurso(Trabajo t,Recurso r){if(!t.solicitud().equals(identidad(r))||!t.huellaRecurso().equals(r.huellaOrigen()))throw new ConflictoException("La investigación conserva su propio alcance y versión aceptados.");}
}
