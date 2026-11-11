package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.GobiernoOperativo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Baja local formal: nunca cambia la cuenta, contactos, factores o credenciales del titular. */
@Service
public class MembresiasServiceImpl implements MembresiasService {
    private final UsuarioOrganizacionRepository miembros;
    private final CuentaAccesoRepository cuentas;
    private final SesionAccesoRepository sesiones;
    private final PersonaRolRepository roles;
    private final GobiernoOperativo gobierno;
    private final EventosSeguridad eventos;
    private final ContextosService contextos;
    private final EntityManager entidades;
    private final Clock reloj;

    @Autowired
    public MembresiasServiceImpl(UsuarioOrganizacionRepository miembros,CuentaAccesoRepository cuentas,
            SesionAccesoRepository sesiones,PersonaRolRepository roles,GobiernoOperativo gobierno,
            EventosSeguridad eventos,ContextosService contextos,EntityManager entidades) {
        this(miembros,cuentas,sesiones,roles,gobierno,eventos,contextos,entidades,Clock.systemUTC());
    }
    MembresiasServiceImpl(UsuarioOrganizacionRepository miembros,CuentaAccesoRepository cuentas,
            SesionAccesoRepository sesiones,PersonaRolRepository roles,GobiernoOperativo gobierno,
            EventosSeguridad eventos,ContextosService contextos,EntityManager entidades,Clock reloj) {
        this.miembros=miembros;this.cuentas=cuentas;this.sesiones=sesiones;this.roles=roles;
        this.gobierno=gobierno;this.eventos=eventos;this.contextos=contextos;this.entidades=entidades;this.reloj=reloj;
    }

    @Override @Transactional
    public BajaFormal darBaja(long idMembresia,long versionEsperada,String motivo,Actor actor,ContextoSesion sesion) {
        if(actor==null||!actor.esTenantAdmin())throw new AccesoNoAutorizadoException();
        if(sesion==null)throw new AccesoNoAutorizadoException();
        if(versionEsperada<0)throw new ReglaNegocioException("La version de pertenencia es invalida.");
        if(motivo==null||motivo.isBlank()||motivo.trim().length()>300)
            throw new ReglaNegocioException("Indica el motivo real de la baja, hasta 300 caracteres.");
        String motivoReal=motivo.trim();
        var inicial=actual(actor.idOrganizacion(),idMembresia);
        long cuentaActor=cuentaGobierno(actor);
        // Dos gobiernos que actúan sobre el otro toman las mismas cuentas en idéntico orden.
        for(long id:new TreeSet<>(List.of(cuentaActor,inicial.idCuenta())))
            cuentas.bloquearCambioAcceso(id).orElseThrow(AccesoNoAutorizadoException::new);
        try {
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(actual.identidad().idCuenta()!=cuentaActor||!ContextoSesion.de(actual).equals(sesion))throw new AccesoNoAutorizadoException();
        } catch(CredencialesInvalidasException e) {throw new AccesoNoAutorizadoException();}
        if(cuentaGobierno(actor)!=cuentaActor)throw new AccesoNoAutorizadoException();
        sesiones.revocarDeMembresia(idMembresia);
        var organizacion=entidades.find(Organizacion.class,actor.idOrganizacion(),LockModeType.PESSIMISTIC_WRITE);
        if(organizacion==null)throw new AccesoNoAutorizadoException();
        entidades.refresh(organizacion,LockModeType.PESSIMISTIC_WRITE);
        var gobiernoLocal=miembros.buscarPorCuenta(actor.idOrganizacion(),cuentaActor).orElseThrow(AccesoNoAutorizadoException::new);
        miembros.bloquearParaContexto(gobiernoLocal.getId(),cuentaActor).orElseThrow(AccesoNoAutorizadoException::new);
        roles.bloquearParaContexto(actor.idRolOperativo()).orElseThrow(AccesoNoAutorizadoException::new);
        entidades.find(Persona.class,actor.idPersona(),LockModeType.PESSIMISTIC_READ);
        var m=miembros.bloquearParaBaja(actor.idOrganizacion(),idMembresia).orElseThrow(()->new NoEncontradoException("Pertenencia"));
        entidades.refresh(m,LockModeType.PESSIMISTIC_WRITE);
        var vigente=actual(actor.idOrganizacion(),idMembresia);
        if(vigente.idCuenta()!=inicial.idCuenta()||cuentaGobierno(actor)!=cuentaActor)throw new AccesoNoAutorizadoException();
        if("I".equals(vigente.estado())&&vigente.version()==versionEsperada+1&&vigente.bajaRealEn()!=null)
            return respuesta(vigente);
        if(!"A".equals(vigente.estado())||vigente.version()!=versionEsperada)
            throw new ConflictoException("La pertenencia cambio. Revisa su version actual antes de darla de baja.");
        if(vigente.idPersona()==null||vigente.altaRealEn()==null)throw new AccesoNoAutorizadoException();
        if("A".equals(organizacion.getEstado())&&"EMPRESARIAL".equals(organizacion.getModalidad())) {
            if("BROKER".equals(vigente.rol())) {
                if(miembros.otrosBrokersVigentes(actor.idOrganizacion(),idMembresia,hoy())==0)
                    throw new ReglaNegocioException("Incorpora un broker vigente antes de dar de baja al ultimo broker.");
                if(miembros.agentesActivosDeBroker(actor.idOrganizacion(),vigente.idPersona())>0)
                    throw new ReglaNegocioException("Reasigna los agentes vigentes a un broker competente antes de esta baja.");
            }
            if(vigente.administrar()) {
                if(miembros.otrosAdministradoresVigentes(actor.idOrganizacion(),idMembresia,hoy())==0)
                    throw new ReglaNegocioException("Incorpora un TenantAdmin vigente antes de dar de baja al ultimo administrador.");
                gobierno.exigirQueQuedeGobierno(actor.idOrganizacion(),vigente.idCuenta(),"dar de baja esta pertenencia");
            }
        }
        var baja=OffsetDateTime.ofInstant(reloj.instant().truncatedTo(ChronoUnit.MICROS),ZoneOffset.UTC);
        m.setEstado("I");m.setBajaRealEn(baja);miembros.saveAndFlush(m);
        var aplicada=actual(actor.idOrganizacion(),idMembresia);
        eventos.registrarEnTransaccion("MEMBRESIA_BAJA_FORMAL","OK",
                new EventosSeguridad.Contexto(actor.idOrganizacion(),actor.idPersona(),cuentaActor,actor.rolEfectivo(),null,null),
                motivoReal,vigente.idPersona(),Map.of("idMembresia",idMembresia,"versionAnterior",versionEsperada,
                        "version",aplicada.version(),"estadoAnterior","A","estadoNuevo","I",
                        "altaRealEn",vigente.altaRealEn(),"bajaRealEn",aplicada.bajaRealEn()));
        return respuesta(aplicada);
    }
    private PertenenciaDeBajaFila actual(long org,long id) {
        return miembros.paraBaja(org,id).orElseThrow(()->new NoEncontradoException("Pertenencia"));
    }
    private long cuentaGobierno(Actor actor) {
        return miembros.cuentaDeGobiernoActual(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),hoy())
                .orElseThrow(AccesoNoAutorizadoException::new);
    }
    private LocalDate hoy(){return LocalDate.now(reloj.withZone(ZoneId.of("America/Lima")));}
    private static BajaFormal respuesta(PertenenciaDeBajaFila m) {
        if(m.idPersona()==null||m.altaRealEn()==null||m.bajaRealEn()==null)throw new AccesoNoAutorizadoException();
        return new BajaFormal(m.id(),m.idPersona(),m.altaRealEn().toInstant(),m.bajaRealEn().toInstant(),m.version());
    }
}
