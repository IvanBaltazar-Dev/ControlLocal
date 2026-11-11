package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.OportunidadService.InteresadoReferencia;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.*;

/** Autoridad única de Oportunidad. La proyección técnica no revela el expediente de Persona. */
@Component
public class AccesoOportunidad {
    public record PermisoOportunidad(long organizacion,long id,long version,long idEncargo,
            long responsableActual,String estado,String operacion,long idPropiedad,InteresadoReferencia interesado) { }
    private final OportunidadComercialRepository oportunidades;
    private final Alcances alcances;
    private final ContextosDeActividad contextos;
    private final EntityManager entidades;
    public AccesoOportunidad(OportunidadComercialRepository oportunidades,Alcances alcances,
            ContextosDeActividad contextos,EntityManager entidades){
        this.oportunidades=oportunidades;this.alcances=alcances;this.contextos=contextos;this.entidades=entidades;
    }

    @Transactional(readOnly=true)
    public PermisoOportunidad contextoParaNegociacion(long id,Actor actor){
        contextos.exigirOperador(actor);
        var c=oportunidades.contexto(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Oportunidad"));
        if(!permite(c,actor))throw new AccesoNoAutorizadoException();
        return proyeccion(c);
    }

    /**
     * Selección para filtrar población autorizada sin hacer de una denegación esperada
     * una excepción de otro interceptor transaccional. No devuelve contenido personal.
     */
    @Transactional(readOnly=true)
    public Optional<PermisoOportunidad> buscarParaLectura(long id,Actor actor){
        try {
            // exigirOperador no es transaccional; iniciar es la única puerta TX del helper.
            contextos.exigirOperador(actor);
        } catch(AccesoNoAutorizadoException denegada) {
            return Optional.empty();
        }
        return oportunidades.contexto(actor.idOrganizacion(),id).filter(c->permite(c,actor)).map(this::proyeccion);
    }

    private boolean permite(OportunidadComercialRepository.ContextoOportunidad c,Actor actor){
        return permiteResponsablesActuales(c.getResponsable(),c.getResponsableEncargo(),actor);
    }
    /** Metadata servidor de la oportunidad y su Encargo; conserva sus dos autoridades distintas. */
    public boolean permiteResponsablesActuales(Long responsable,Long responsableEncargo,Actor actor){
        return permite(responsable,responsableEncargo,actor,actor.esAgente()?Set.of():alcances.de(actor).rolesAgente());
    }
    public record ResponsablesActuales(Long responsable,Long responsableEncargo){ }
    public Set<Long> permitidasActuales(Map<Long,ResponsablesActuales> fuentes,Actor actor){
        var roles=actor.esAgente()?Set.<Long>of():Set.copyOf(alcances.de(actor).rolesAgente());var permitidas=new HashSet<Long>();
        fuentes.forEach((id,c)->{if(permite(c.responsable(),c.responsableEncargo(),actor,roles))permitidas.add(id);});return Set.copyOf(permitidas);
    }
    private static boolean permite(Long responsable,Long responsableEncargo,Actor actor,Collection<Long> roles){
        return actor.esAgente()?responsable!=null&&responsable==actor.idRolOperativo():responsableEncargo!=null&&roles.contains(responsableEncargo);
    }

    private PermisoOportunidad proyeccion(OportunidadComercialRepository.ContextoOportunidad c){
        return new PermisoOportunidad(c.getOrganizacion(),c.getId(),c.getVersion(),c.getEncargo(),
                c.getResponsable(),c.getEstado(),c.getOperacion()==null?null:com.controllocal.domain.inmueble.OperacionInmobiliaria.desde(c.getOperacion()).name(),
                c.getPropiedad(),c.getPersona()==null?null:new InteresadoReferencia(c.getPersona(),c.getCliente()));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public PermisoOportunidad bloquearParaNegociacion(long id,long versionEsperada,Actor actor,ContextoSesion sesion){
        contextos.iniciar(actor,sesion);
        var o=bloquearActividad(id,versionEsperada,actor);
        if(o.getCaptacion()==null||o.getCaptacion().getAgente()==null
                ||o.getCaptacion().getAgente().getId()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        return contextoParaNegociacion(id,actor);
    }

    @Transactional(readOnly=true)
    public OportunidadComercial conAcceso(long id,Actor actor){
        contextoParaNegociacion(id,actor);
        return oportunidades.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Oportunidad"));
    }

    /** El llamante ya bloqueó Cuenta/SID e identidad de reintento antes del recurso. */
    @Transactional(propagation=Propagation.MANDATORY)
    public OportunidadComercial bloquearActividad(long id,long versionEsperada,Actor actor){
        contextos.exigirAgente(actor);
        var o=oportunidades.bloquear(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Oportunidad"));
        entidades.refresh(o,LockModeType.PESSIMISTIC_WRITE);
        if(o.getAgente()==null||o.getAgente().getId()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(versionEsperada<0||o.getVersion()!=versionEsperada)throw new ConflictoException("Cambió la oportunidad; revisa su versión.");
        return o;
    }

    /** Lector actual usado por consultas ya restringidas por tenant. No concede escritura. */
    public boolean alcanza(OportunidadComercial o,Actor actor){
        if(actor==null||(!actor.esAgente()&&!actor.esBroker()))return false;
        if(o==null||o.getOrganizacionId()==null||o.getOrganizacionId()!=actor.idOrganizacion())return false;
        return permiteResponsablesActuales(o.getAgente()==null?null:o.getAgente().getId(),
                o.getCaptacion()==null||o.getCaptacion().getAgente()==null?null:o.getCaptacion().getAgente().getId(),actor);
    }
}
