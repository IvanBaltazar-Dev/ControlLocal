package com.controllocal.service.planes;

import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Directriz5.3 y CAT-E41: profundidad comercial, nunca permiso sobre otra cartera. */
@Component
public class DerechosDeInformesC7 {
    public enum Composicion { OPERACION_ACTUAL,RESUMEN_MENSUAL,EVOLUCION_MEJORA,RESUMEN_OFICINA,SEGUIMIENTO_RESPONSABLES,RITMO_METAS,DIAGNOSTICO_RESPONSABLE }
    public record Derechos(PlanBrox plan,Set<Composicion> composiciones,boolean programasPersonales,UUID ciclo,long versionCiclo) {public Derechos{composiciones=Set.copyOf(composiciones);}}
    private final PresupuestoIaRepository planes;
    private final ContextosDeActividad contexto;
    public DerechosDeInformesC7(PresupuestoIaRepository p,ContextosDeActividad c){planes=p;contexto=c;}
    public Derechos actuales(Actor a,Instant corte){
        contexto.exigirOperador(a);var ciclo=planes.cicloVigente(a.idOrganizacion(),corte).filter(c->!c.revocado()).orElse(null);
        var organizacion=planes.organizacionActual(a.idOrganizacion()).filter(o->o.activa()).orElseThrow(com.controllocal.service.excepcion.AccesoNoAutorizadoException::new);
        var plan=ciclo==null?PlanBrox.FREE:PlanBrox.valueOf(ciclo.plan());
        if(plan!=PlanBrox.FREE&&plan.empresarial()!= "EMPRESARIAL".equals(organizacion.modalidad()))throw new IllegalStateException("El ciclo no corresponde a la modalidad de la organización.");
        var c=EnumSet.of(Composicion.OPERACION_ACTUAL);
        switch(plan){
            case ONE -> c.add(Composicion.RESUMEN_MENSUAL);
            case ONE_PRO -> c.addAll(List.of(Composicion.RESUMEN_MENSUAL,Composicion.EVOLUCION_MEJORA));
            case PRIME -> c.addAll(List.of(Composicion.RESUMEN_OFICINA,Composicion.SEGUIMIENTO_RESPONSABLES,Composicion.RITMO_METAS,Composicion.DIAGNOSTICO_RESPONSABLE));
            case SCALE -> c.addAll(List.of(Composicion.RESUMEN_OFICINA,Composicion.SEGUIMIENTO_RESPONSABLES,Composicion.RITMO_METAS));
            case GROWTH -> c.addAll(List.of(Composicion.RESUMEN_OFICINA,Composicion.SEGUIMIENTO_RESPONSABLES));
            case TEAM -> c.add(Composicion.RESUMEN_OFICINA);
            case FREE -> { }
        }
        return new Derechos(plan,c,plan.proPersonal()&&"INDIVIDUAL".equals(organizacion.modalidad()),ciclo==null?null:ciclo.id(),ciclo==null?0:ciclo.version());
    }
    public Derechos exigir(Composicion c,Actor a,Instant ahora){var d=actuales(a,ahora);if(!d.composiciones().contains(c))throw new CapacidadDeshabilitadaException();return d;}
    public Derechos exigirPro(Actor a,Instant ahora){var d=actuales(a,ahora);if(!d.programasPersonales()||!a.esAgente())throw new CapacidadDeshabilitadaException();return d;}
}
