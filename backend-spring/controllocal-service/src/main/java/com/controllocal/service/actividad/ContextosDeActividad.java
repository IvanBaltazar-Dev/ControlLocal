package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Cuenta/SID y contexto operativo común de los casos C4; no concede acceso a un recurso. */
@Component
public class ContextosDeActividad {
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ContextosService contextos;
    private final OportunidadComercialRepository oportunidades;
    private final Clock reloj=Clock.systemUTC();
    public ContextosDeActividad(CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,
            ContextosService contextos,OportunidadComercialRepository oportunidades){
        this.cuentas=cuentas;this.miembros=miembros;this.contextos=contextos;this.oportunidades=oportunidades;
    }
    public Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}
    public LocalDate hoy(){return LocalDate.now(reloj.withZone(ZoneId.of("America/Lima")));}
    public void exigirOperador(Actor actor){
        if(actor==null||(!actor.esAgente()&&!actor.esBroker())||!oportunidades.contextoOperativoActual(
                actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),hoy()))
            throw new AccesoNoAutorizadoException();
    }
    public void exigirAgente(Actor actor){exigirOperador(actor);if(!actor.esAgente())throw new AccesoNoAutorizadoException();}
    /** RF041: vocabulario/definición estática, sin consultar hechos ni ampliar autoridad comercial. */
    public void exigirMetadata(Actor actor){
        if(actor!=null&&actor.esTenantAdmin()){
            if(!oportunidades.contextoGobiernoParaMetadata(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),hoy()))
                throw new AccesoNoAutorizadoException();
            return;
        }
        exigirOperador(actor);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciar(Actor actor,ContextoSesion sesion){
        iniciar(actor,sesion,false);
    }
    /** RF075: misma cuenta/SID protegido, para broker competente o independiente, sin relajar C4. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciarOperador(Actor actor,ContextoSesion sesion){
        iniciar(actor,sesion,false,false);
    }
    /** C6 lee identidad/contexto mientras escribe su propio recurso, sin convertir Cuenta/SID a WRITE. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void iniciarConLecturaProtegida(Actor actor,ContextoSesion sesion){
        iniciar(actor,sesion,true);
    }
    private void iniciar(Actor actor,ContextoSesion sesion,boolean lecturaProtegida){
        iniciar(actor,sesion,lecturaProtegida,true);
    }
    private void iniciar(Actor actor,ContextoSesion sesion,boolean lecturaProtegida,boolean soloAgente){
        if(actor==null||(soloAgente?!actor.esAgente():!actor.esAgente()&&!actor.esBroker())||sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        (lecturaProtegida?cuentas.bloquearLecturaContexto(ids.getFirst()):cuentas.bloquearCambioAcceso(ids.getFirst()))
                .orElseThrow(AccesoNoAutorizadoException::new);
        try{var actual=lecturaProtegida?
                contextos.exigirContextoConLecturaProtegida(sesion.referenciaSesion(),sesion.revisionContexto(),actor):
                contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(actual).equals(sesion))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException ex){throw new AccesoNoAutorizadoException();}
        if(soloAgente)exigirAgente(actor);else exigirOperador(actor);
    }
}
