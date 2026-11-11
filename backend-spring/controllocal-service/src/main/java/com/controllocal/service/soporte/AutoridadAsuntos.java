package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.FuentesAsuntosRepository;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import org.springframework.stereotype.Component;
import java.util.*;

/** Aplica por lote las mismas guardas canónicas. Las cabeceras proceden exclusivamente de Core. */
@Component
public class AutoridadAsuntos {
    public record Consulta(Recurso recurso,String finalidad) { }
    private final FuentesAsuntosRepository fuentes;
    private final ContextosDeActividad contextos;
    private final AccesoSolicitud solicitudes;
    private final AccesoOportunidad oportunidades;
    private final AccesoContrato contratos;
    private final AccesoEncargo encargos;
    public AutoridadAsuntos(FuentesAsuntosRepository fuentes,ContextosDeActividad contextos,
            AccesoSolicitud solicitudes,AccesoOportunidad oportunidades,AccesoContrato contratos,AccesoEncargo encargos){
        this.fuentes=fuentes;this.contextos=contextos;this.solicitudes=solicitudes;
        this.oportunidades=oportunidades;this.contratos=contratos;this.encargos=encargos;
    }
    public Set<Consulta> legibles(Collection<Consulta> consultas,Actor actor){
        contextos.exigirOperador(actor);
        var datos=fuentes.contextos(actor.idOrganizacion(),consultas.stream().map(Consulta::recurso).distinct().toList());
        var s=new HashMap<Long,Long>();var e=new HashMap<Long,Long>();
        var o=new HashMap<Long,AccesoOportunidad.ResponsablesActuales>();
        var k=new HashMap<Long,AccesoContrato.ResponsablesActuales>();var com=new HashMap<Long,Long>();
        for(var q:consultas){var c=datos.get(q.recurso());if(c==null)continue;
            switch(c.recurso().tipo()){
                case "SOLICITUD_ALQUILER" -> s.put(c.recurso().id(),c.responsable());
                case "CAPTACION","ENCARGO" -> e.put(c.recurso().id(),c.responsable());
                case "OPORTUNIDAD" -> o.put(c.recurso().id(),new AccesoOportunidad.ResponsablesActuales(c.responsable(),c.responsableEncargo()));
                case "CONTRATO_ALQUILER" -> {
                    if(esComision(q.finalidad()))com.put(c.recurso().id(),c.responsableEncargo());
                    else k.put(c.recurso().id(),new AccesoContrato.ResponsablesActuales(c.solicitud()!=null,c.responsable(),c.responsableEncargo()));
                }
                default -> { }
            }
        }
        var ps=s.isEmpty()?Set.<Long>of():solicitudes.permitidasActuales(s,actor);
        var pe=e.isEmpty()?Set.<Long>of():encargos.permitidosActuales(e,actor);
        var po=o.isEmpty()?Set.<Long>of():oportunidades.permitidasActuales(o,actor);
        var pk=k.isEmpty()?Set.<Long>of():contratos.permitidosActuales(k,actor);
        var pc=com.isEmpty()?Set.<Long>of():encargos.permitidosActuales(com,actor);
        var salida=new HashSet<Consulta>();
        for(var q:consultas){long id=q.recurso().id();boolean permite=switch(q.recurso().tipo()){
            case "SOLICITUD_ALQUILER" -> ps.contains(id);
            case "CAPTACION","ENCARGO" -> pe.contains(id);
            case "OPORTUNIDAD" -> po.contains(id);
            case "CONTRATO_ALQUILER" -> (esComision(q.finalidad())?pc:pk).contains(id);
            default -> false;
        };if(permite)salida.add(q);}
        contextos.exigirOperador(actor);return Set.copyOf(salida);
    }
    /** El cierre tiene autoridad de decisión sobre su Encargo, sin conceder lectura de la Solicitud. */
    public void exigirDecisionEncargo(long encargo,Actor actor){
        contextos.exigirOperador(actor);
        var recurso=new Recurso("CAPTACION",encargo);
        var c=fuentes.contextos(actor.idOrganizacion(),List.of(recurso)).get(recurso);
        if(c==null||!encargos.paraDecision(actor).rolesAgente().contains(c.responsable()))
            throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
        contextos.exigirOperador(actor);
    }
    private static boolean esComision(String finalidad){return finalidad!=null&&finalidad.startsWith("COMISION_");}
}
