package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.AvisosComercialesBrokerRepository;
import com.controllocal.persistence.repositorio.AvisosComercialesBrokerRepository.Fuente;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** CAT-R28/NB: hecho privado por recurso y competencia actual, nunca bandeja de tareas del agente. */
@Service
public class NotificacionesBrokerC7 {
    public record Aviso(long evento,String codigo,String recurso,long referencia,long version,
        String entidadHecho,long idHecho,Instant registradoEn,List<String> campos,String accion,String estado,
        boolean atendidoPorActuacionPropia) { }
    private final AvisosComercialesBrokerRepository repo;
    private final ContextosDeActividad contexto;
    private final AccesoEncargo acceso;
    private final ReconfirmacionesDisponibilidad reconfirmaciones;
    private final Documentos json;
    private final com.controllocal.service.colaboracion.ColaboracionService colaboraciones;
    public NotificacionesBrokerC7(AvisosComercialesBrokerRepository repo,ContextosDeActividad contexto,
        AccesoEncargo acceso,ReconfirmacionesDisponibilidad reconfirmaciones,Documentos json,com.controllocal.service.colaboracion.ColaboracionService colaboraciones){
        this.repo=repo;this.contexto=contexto;this.acceso=acceso;this.reconfirmaciones=reconfirmaciones;this.json=json;this.colaboraciones=colaboraciones;
    }
    @Transactional
    public Pagina<Aviso> listar(int pagina,int tamano,boolean historia,Actor actor){
        exigir(actor);var roles=acceso.paraDecision(actor).rolesAgente();
        var fuentes=repo.fuentes(actor.idOrganizacion(),roles);var filas=new ArrayList<Aviso>();
        for(var f:fuentes){var m=marca(f);
            if(!vigente(f,m,actor)){repo.retirar(actor.idOrganizacion(),actor.idRolOperativo(),f.evento(),m.codigo(),contexto.ahora());continue;}
            boolean propia=Objects.equals(f.actor(),actor.idRolOperativo());
            var e=repo.registrar(actor.idOrganizacion(),actor.idRolOperativo(),f,m.codigo(),m.recurso(),m.referencia(),propia);
            if(historia||"A".equals(e.estado()))filas.add(new Aviso(f.evento(),m.codigo(),m.recurso(),m.referencia(),m.version(),f.entidad(),f.id(),f.registrado(),m.campos(),AvisoComercialBroker.accion(m.codigo()),e.estado(),propia));
        }
        revalidar(actor,roles,fuentes);
        int numero=Math.max(1,pagina),cantidad=Math.max(1,Math.min(100,tamano));long inicio=(long)(numero-1)*cantidad;
        return new Pagina<>(inicio>=filas.size()?List.of():filas.subList((int)inicio,(int)Math.min(inicio+cantidad,filas.size())),filas.size());
    }
    @Transactional
    public boolean atender(long evento,Actor actor,ContextoSesion sesion){
        exigir(actor);contexto.iniciarOperador(actor,sesion);var roles=acceso.paraDecision(actor).rolesAgente();
        var fuentes=repo.fuentes(actor.idOrganizacion(),roles);var f=fuentes.stream().filter(e->e.evento()==evento).findFirst().orElseThrow(()->new NoEncontradoException("Aviso"));
        var m=marca(f);if(!vigente(f,m,actor))throw new NoEncontradoException("Aviso");
        repo.registrar(actor.idOrganizacion(),actor.idRolOperativo(),f,m.codigo(),m.recurso(),m.referencia(),Objects.equals(f.actor(),actor.idRolOperativo()));
        boolean atendido=repo.atender(actor.idOrganizacion(),actor.idRolOperativo(),evento,m.codigo(),contexto.ahora());
        revalidar(actor,roles,fuentes);return atendido;
    }
    private AvisoComercialBroker.Marca marca(Fuente f){
        var m=json.comoObjeto(f.marca(),AvisoComercialBroker.Marca.class);
        if(!AvisoComercialBroker.productorAdmitido(m.codigo(),f.tipo(),f.entidad()))throw new IllegalStateException("El aviso no corresponde a un productor comercial comprobado.");
        return m;
    }
    private boolean vigente(Fuente f,AvisoComercialBroker.Marca m,Actor actor){
        if(Set.of("NB-01","NB-02","NB-04","NB-05","NB-07").contains(m.codigo())&&!f.causaPendiente())return false;
        if("NB-14".equals(m.codigo()))return f.solicitud()!=null&&reconfirmaciones.entregable(UUID.fromString(f.solicitud()),actor);
        if("NB-11".equals(m.codigo())){
            try{if(f.solicitud()==null)return false;colaboraciones.consultarMetadatos(f.solicitud(),actor);}
            catch(AccesoNoAutorizadoException|NoEncontradoException revocada){return false;}
        }
        return true;
    }
    private void exigir(Actor a){contexto.exigirOperador(a);if(!a.esBroker())throw new AccesoNoAutorizadoException();}
    private void revalidar(Actor a,Collection<Long> roles,List<Fuente> fuentes){
        exigir(a);var finales=acceso.paraDecision(a).rolesAgente();
        if(!new HashSet<>(roles).equals(new HashSet<>(finales))||!fuentes.equals(repo.fuentes(a.idOrganizacion(),finales)))
            throw new ConflictoException("Cambió el permiso, destinatario o hecho de los avisos; consulta la bandeja actual.");
    }
}
