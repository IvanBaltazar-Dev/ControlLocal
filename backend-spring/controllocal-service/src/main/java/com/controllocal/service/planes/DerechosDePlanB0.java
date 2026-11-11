package com.controllocal.service.planes;

import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.Ciclo;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.soporte.Documentos;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Plaza financiada no es permiso de negocio y no regenera el saldo al incorporar personas. */
@Component
public class DerechosDePlanB0 {
    private final PresupuestoIaRepository repo;private final Documentos json;
    public DerechosDePlanB0(PresupuestoIaRepository repo,Documentos json){this.repo=repo;this.json=json;}
    /** El consumidor ya revalidó SID/rol y bloqueó organización. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void exigirPlazaParaIa(Actor a,Ciclo ciclo,Instant ahora){
        String tipo=a.esBroker()?"BROKER":"AGENTE";
        var plazas=repo.plazas(a.idOrganizacion(),ciclo.id(),tipo);
        if(plazas.stream().anyMatch(p->Objects.equals(p.persona(),a.idPersona())))return;
        LocalDate hoy=ahora.atZone(ZoneId.of("America/Lima")).toLocalDate();
        var disponible=plazas.stream().filter(p->p.persona()==null||!repo.conservaPertenencia(a.idOrganizacion(),p.persona(),tipo,hoy)).findFirst();
        PresupuestoIaRepository.Plaza libre;
        if(disponible.isPresent())libre=disponible.get();
        else if(a.esBroker()&&com.controllocal.domain.planes.PlanBrox.valueOf(ciclo.plan()).empresarial()){
            int numero=1;for(var p:plazas){if(p.numero()==numero)numero++;else if(p.numero()>numero)break;}
            if(numero>1L+ciclo.brokersExtra())throw new CapacidadDeshabilitadaException();
            libre=repo.crearPlazaBroker(a.idOrganizacion(),ciclo.id(),numero);
        }else throw new CapacidadDeshabilitadaException();
        repo.asignarPlaza(a.idOrganizacion(),ciclo.id(),libre,a.idPersona());
        var detalle=new LinkedHashMap<String,Object>();detalle.put("ciclo",ciclo.id());detalle.put("tipo",tipo);detalle.put("plaza",libre.numero());
        detalle.put("personaAnterior",libre.persona());detalle.put("personaActual",a.idPersona());detalle.put("fuente",libre.fuente());
        repo.antecedente(a.idOrganizacion(),null,"PLAZA_ASIGNADA",json.objetoCanonico(detalle),ahora);
    }
}
