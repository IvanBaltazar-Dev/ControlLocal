package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.*;

/** CAT-E25: autoridad actual común al lector preparatorio y a las actuaciones del contrato. */
@Component
public class AccesoContrato {
    private final ContratoAlquilerRepository contratos;
    private final Alcances alcances;
    public AccesoContrato(ContratoAlquilerRepository contratos,Alcances alcances){this.contratos=contratos;this.alcances=alcances;}
    public ContratoAlquilerRepository.PreparacionActual actualAutorizada(long id,Actor actor) {
        Alcances.exigirOperador(actor);
        var c=contratos.preparacionActual(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Contrato"));
        boolean autorizado=permitePreparacionActual(c.getSolicitud()!=null,c.getAgenteSolicitud(),c.getResponsableEncargo(),actor);
        if(!autorizado)throw new AccesoNoAutorizadoException();
        return c;
    }
    /** Sólo metadata canónica: conserva Solicitud opcional y supervisión por Encargo. */
    public boolean permitePreparacionActual(boolean conSolicitud,Long agenteSolicitud,Long responsableEncargo,Actor actor) {
        return permite(conSolicitud,agenteSolicitud,responsableEncargo,actor,
                actor.esAgente()?Set.of():alcances.de(actor).rolesAgente());
    }
    public record ResponsablesActuales(boolean conSolicitud,Long agenteSolicitud,Long responsableEncargo){ }
    public Set<Long> permitidosActuales(Map<Long,ResponsablesActuales> fuentes,Actor actor){
        var roles=actor.esAgente()?Set.<Long>of():Set.copyOf(alcances.de(actor).rolesAgente());var permitidos=new HashSet<Long>();
        fuentes.forEach((id,c)->{if(permite(c.conSolicitud(),c.agenteSolicitud(),c.responsableEncargo(),actor,roles))permitidos.add(id);});return Set.copyOf(permitidos);
    }
    private static boolean permite(boolean conSolicitud,Long agenteSolicitud,Long responsableEncargo,Actor actor,Collection<Long> roles){
        return actor.esAgente()?Objects.equals(conSolicitud?agenteSolicitud:responsableEncargo,actor.idRolOperativo())
                :responsableEncargo!=null&&roles.contains(responsableEncargo);
    }
    public ContratoAlquiler leer(long id,Actor actor) {
        var actual=actualAutorizada(id,actor);
        var c=contratos.buscarFicha(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Contrato"));
        comprobarVinculos(c,actual);
        return c;
    }
    private static void comprobarVinculos(ContratoAlquiler c,ContratoAlquilerRepository.PreparacionActual actual) {
        if(c.getOportunidad()==null||!Objects.equals(c.getOportunidad().getId(),actual.getOportunidad())
                ||c.getOportunidad().getCaptacion()==null||!Objects.equals(c.getOportunidad().getCaptacion().getId(),actual.getEncargo())
                ||!Objects.equals(c.getSolicitud()==null?null:c.getSolicitud().getId(),actual.getSolicitud()))
            throw new ConflictoException("Cambió el contexto del contrato; consulta su información actual.");
    }
    public void exigir(ContratoAlquiler c,Actor actor) {
        Alcances.exigirOperador(actor);
        if(!Objects.equals(c.getOrganizacionId(),actor.idOrganizacion()))throw new AccesoNoAutorizadoException();
        comprobarVinculos(c,actualAutorizada(c.getId(),actor));
    }
}
